package store

import (
	"context"
	"log/slog"
	"time"
)

type Client interface {
	Init()
	Write(context.Context, time.Time, string, any, map[string]string)
}

type LogClient struct {
	logger *slog.Logger
}

func NewLogStore(log *slog.Logger) *LogClient {
	return &LogClient{log.With("store", "log")}
}

func (s *LogClient) Init() {}
func (s *LogClient) Write(ctx context.Context, ts time.Time, name string, val any, tags map[string]string) {
	s.logger.Info(
		"store",
		"ts", ts,
		"name", name,
		"val", val,
		"tags", tags)
}
