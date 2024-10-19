package housemetrics

import (
	"context"
	"log/slog"
	"time"

	"github.com/sprsquish/housemetrics/pkg/store"
)

type Looper interface {
	Init()
	Poll(context.Context, store.Client) error
}

type LoopRunner struct {
	name   string
	looper Looper
	logger *slog.Logger

	pollFreq time.Duration
	enabled  bool
}

func (r *LoopRunner) Run(ctx context.Context, store store.Client) {
	if !r.enabled {
		r.logger.Info("disabled")
		return
	}

	r.looper.Init()

	r.logger.Info("starting", "freq", r.pollFreq)
	ticker := time.NewTicker(r.pollFreq)

	r.poll(ctx, store)

	for {
		select {
		case <-ctx.Done():
			ticker.Stop()
			r.logger.Info("stopping")
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
			r.logger.Error("poll error", "err", err)
		} else {
			r.logger.Info("failed request.. sleeping")
			time.Sleep(1 * time.Minute)
		}
	}
}
