package housemetrics

import (
	"context"
	"time"

	"github.com/rs/zerolog"
	"github.com/sprsquish/housemetrics/pkg/store"
)

type Looper interface {
	Init()
	Poll(context.Context, store.Client) error
}

type LoopRunner struct {
	name   string
	looper Looper
	logger *zerolog.Logger

	pollFreq time.Duration
	enabled  bool
}

func (r *LoopRunner) Run(ctx context.Context, store store.Client) {
	if !r.enabled {
		r.logger.Info().Msg("disabled")
		return
	}

	r.looper.Init()

	r.logger.Info().Dur("freq", r.pollFreq).Msg("starting")
	ticker := time.NewTicker(r.pollFreq)

	r.poll(ctx, store)

	for {
		select {
		case <-ctx.Done():
			ticker.Stop()
			r.logger.Info().Msg("stopping")
			return

		case <-ticker.C:
			r.poll(ctx, store)
		}
	}
}

func (r *LoopRunner) poll(ctx context.Context, store store.Client) {
	err := r.looper.Poll(ctx, store)
	if err != nil {
		if err != ErrFailedRequest {
			r.logger.Error().Err(err).Msg("poll error")
		} else {
			r.logger.Info().Msg("failed request.. sleeping")
			time.Sleep(1 * time.Minute)
		}
	}
}
