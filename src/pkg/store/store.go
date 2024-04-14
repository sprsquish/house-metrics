package store

import (
	"context"
	"time"

	"github.com/rs/zerolog"
)

type Client interface {
	Init()
	Write(context.Context, time.Time, string, any, map[string]string)
}

type LogClient struct {
	logger *zerolog.Logger
}

func NewLogStore(log *zerolog.Logger) *LogClient {
	storeLog := log.With().Str("store", "log").Logger()
	return &LogClient{&storeLog}
}

func (s *LogClient) Init() {}
func (s *LogClient) Write(ctx context.Context, ts time.Time, name string, val any, tags map[string]string) {
	s.logger.Info().
		Time("ts", ts).
		Str("name", name).
		Interface("val", val).
		Interface("tags", tags).
		Msg("store")
}
