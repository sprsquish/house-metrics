package store

import (
	"context"
	"github.com/rs/zerolog"
	"time"
)

type Client interface {
	Init()
	Write(context.Context, time.Time, string, interface{}, map[string]string)
}

type LogClient struct {
	logger *zerolog.Logger
}

func NewLogStore(log *zerolog.Logger) *LogClient {
	storeLog := log.With().Str("store", "log").Logger()
	return &LogClient{&storeLog}
}

func (s *LogClient) Init() {}
func (s *LogClient) Write(ctx context.Context, ts time.Time, name string, val interface{}, tags map[string]string) {
	s.logger.Info().
		Time("ts", ts).
		Str("name", name).
		Interface("val", val).
		Interface("tags", tags).
		Msg("store")
}
