package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/spf13/cobra"
	hm "github.com/sprsquish/housemetrics/pkg"
	"github.com/sprsquish/housemetrics/pkg/endpoint"
	"github.com/sprsquish/housemetrics/pkg/looper"
	"github.com/sprsquish/housemetrics/pkg/store"
	"gitlab.com/greyxor/slogor"
)

type logLeveler struct{ debug bool }

func (l *logLeveler) Level() slog.Level {
	if l.debug {
		return slog.LevelDebug
	}
	return slog.LevelInfo
}

var leveler = &logLeveler{}
var log = slog.New(slogor.NewHandler(
	os.Stdout,
	slogor.SetLevel(leveler),
	slogor.SetTimeFormat(time.RFC3339),
))

var storage store.Client

var mainCmd = &cobra.Command{
	Run:           run,
	SilenceErrors: true,
}

var loopRunners []*hm.LoopRunner
var muxer = http.NewServeMux()
var httpAddr string

func init() {
	flags := mainCmd.Flags()
	flags.StringVar(&httpAddr, "http.addr", ":7777", "Listen address")
	flags.BoolVar(&leveler.debug, "debug", false, "debug mode")

	storage = store.NewInfluxClient(flags, log)

	f := &hm.RunnerFactory{
		Flags:  flags,
		Client: hm.NewHttpClient(),
		Logger: log,
		Store:  storage,
	}

	loopRunners = []*hm.LoopRunner{
		f.MakeLooper("awair", 5*time.Minute, looper.NewAwair),
		f.MakeLooper("ambientWeather", 1*time.Minute, looper.NewAmbientWeather),
		f.MakeLooper("flume", 1*time.Minute, looper.NewFlume),
		f.MakeLooper("particle", 1*time.Minute, looper.NewParticle),
		f.MakeLooper("updatedns", 1*time.Minute, looper.NewUpdateDNS),
		f.MakeLooper("purpleair", 1*time.Minute, looper.NewPurpleAir),
	}

	muxer.Handle("/rainforest", f.MakeHandler("rainforest", endpoint.NewRainforest))
	muxer.Handle("/rachio/webhook", f.MakeHandler("rachio", endpoint.NewRachio))
}

func main() {
	if err := mainCmd.Execute(); err != nil {
		log.Error("failed to start", "err", err)
	}
}

func run(cmd *cobra.Command, args []string) {
	stopChan := make(chan os.Signal, 1)
	signal.Notify(stopChan, syscall.SIGTERM, syscall.SIGINT)

	ctx, done := context.WithCancel(context.TODO())

	storage.Init()

	var wg sync.WaitGroup
	for _, runner := range loopRunners {
		wg.Add(1)
		go func(runner *hm.LoopRunner) {
			defer wg.Done()
			runner.Run(ctx, storage)
		}(runner)
	}

	server := http.Server{
		Addr:    httpAddr,
		Handler: muxer,
	}

	wg.Add(1)
	go func() {
		defer wg.Done()
		log.Info("starting listener", "addr", httpAddr)
		if err := server.ListenAndServe(); err != nil {
			if err != http.ErrServerClosed {
				log.Error("failed to start listener", "err", err)
			}
		}
	}()

	<-stopChan

	log.Info("shutting down")
	server.Close()
	done()

	log.Info("waiting for pollers to stop")
	wg.Wait()

	log.Info("stopped")
}
