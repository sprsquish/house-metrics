package housemetrics

import (
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/spf13/pflag"
	"github.com/sprsquish/housemetrics/pkg/store"
)

type HandlerFactory = func(string, *pflag.FlagSet, *slog.Logger, store.Client) http.Handler
type LooperFactory = func(string, *pflag.FlagSet, *slog.Logger, *HttpClient) Looper

type RunnerFactory struct {
	Flags  *pflag.FlagSet
	Client *HttpClient
	Logger *slog.Logger
	Store  store.Client
}

func (f *RunnerFactory) MakeLooper(name string, defaultFreq time.Duration, factory LooperFactory) *LoopRunner {
	logger := f.Logger.With("looper", name)
	looper := factory(name, f.Flags, logger, f.Client)

	runner := LoopRunner{
		name:   name,
		logger: logger,
		looper: looper,
	}

	f.Flags.DurationVar(&runner.pollFreq, fmt.Sprintf("%s.freq", name), defaultFreq, "Polling frequency")
	f.Flags.BoolVar(&runner.enabled, fmt.Sprintf("%s.enabled", name), false, "Enable polling")

	return &runner
}

func (f *RunnerFactory) MakeHandler(name string, factory HandlerFactory) http.Handler {
	logger := f.Logger.With("looper", name)
	return factory(name, f.Flags, logger, f.Store)
}
