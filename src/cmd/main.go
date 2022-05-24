package main

import (
	"context"
	"github.com/rs/zerolog"
	"github.com/spf13/cobra"
	hm "github.com/sprsquish/housemetrics/internal"
	"github.com/sprsquish/housemetrics/internal/endpoint"
	"github.com/sprsquish/housemetrics/internal/looper"
	"github.com/sprsquish/housemetrics/internal/store"
	"net/http"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"
)

var storage store.Client

var log = zerolog.New(
	zerolog.ConsoleWriter{
		Out: hm.LevelWriter{
			Std: os.Stdout,
			Err: os.Stderr,
		},
	}).
	Level(zerolog.InfoLevel).
	With().
	Timestamp().
	Logger()

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

	storage = store.NewInfluxClient(flags, &log)

	f := &hm.RunnerFactory{
		Flags:  flags,
		Client: hm.NewHttpClient(),
		Logger: &log,
		Store:  storage,
	}

	loopRunners = []*hm.LoopRunner{
		f.MakeLooper("awair", 5*time.Minute, looper.NewAwair),
		f.MakeLooper("ambientWeather", 1*time.Minute, looper.NewAmbientWeather),
		f.MakeLooper("flume", 1*time.Minute, looper.NewFlume),
		f.MakeLooper("purpleair", 10*time.Second, looper.NewPurpleAir),
		f.MakeLooper("particle", 1*time.Minute, looper.NewParticle),
		f.MakeLooper("updatedns", 1*time.Minute, looper.NewUpdateDNS),
	}

	muxer.Handle("/rainforest", f.MakeHandler("rainforest", endpoint.NewRainforest))
	muxer.Handle("/rachio/webhook", f.MakeHandler("rachio", endpoint.NewRachio))
}

func main() {
	if err := mainCmd.Execute(); err != nil {
		log.Error().Err(err).Msg("failed to start")
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
		log.Info().Str("addr", httpAddr).Msg("starting listener")
		if err := server.ListenAndServe(); err != nil {
			if err != http.ErrServerClosed {
				log.Error().Err(err).Msg("failed to start listener")
			}
		}
	}()

	<-stopChan

	log.Info().Msg("shutting down")
	server.Close()
	done()

	log.Info().Msg("waiting for pollers to stop")
	wg.Wait()

	log.Info().Msg("stopped")
}
