package housemetrics

import (
	"context"
	"github.com/rs/zerolog"
	"github.com/sprsquish/housemetrics/internal/store"
	"time"
)

type Looper interface {
	Init()
	Poll(context.Context, store.Client) error
}

type LoopRunner struct {
	name   string
	looper *Looper
	logger *zerolog.Logger

	pollFreq time.Duration
	enabled  bool
}

func (r *LoopRunner) Run(ctx context.Context, store store.Client) {
	if !r.enabled {
		r.logger.Info().Msg("disabled")
		return
	}

	(*r.looper).Init()

	r.logger.Info().Dur("freq", r.pollFreq).Msg("starting")
	ticker := time.NewTicker(r.pollFreq)

	if err := (*r.looper).Poll(ctx, store); err != nil {
		r.logger.Error().Err(err).Msg("poll error")
	}

	for {
		select {
		case <-ctx.Done():
			ticker.Stop()
			r.logger.Info().Msg("stopping")
			return

		case <-ticker.C:
			if err := (*r.looper).Poll(ctx, store); err != nil {
				r.logger.Error().Err(err).Msg("poll error")
			}
		}
	}
}