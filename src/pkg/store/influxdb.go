package store

import (
	"context"
	"time"

	influxdb2 "github.com/influxdata/influxdb-client-go/v2"
	"github.com/influxdata/influxdb-client-go/v2/api"
	"github.com/rs/zerolog"
	"github.com/spf13/pflag"
)

type InfluxClient struct {
	logger *zerolog.Logger

	dest   string
	bucket string
	token  string
	org    string

	client api.WriteAPIBlocking
}

func NewInfluxClient(flags *pflag.FlagSet, logger *zerolog.Logger) *InfluxClient {
	modLog := logger.With().Str("store", "influxdb").Logger()
	c := &InfluxClient{
		logger: &modLog,
	}

	flags.StringVar(&c.dest, "influxdb.dest", "", "database addr")
	flags.StringVar(&c.bucket, "influxdb.bucket", "", "database")
	flags.StringVar(&c.token, "influxdb.token", "", "auth token")
	flags.StringVar(&c.org, "influxdb.org", "", "database org")

	return c
}

func (i *InfluxClient) Init() {
	if i.dest == "" || i.token == "" || i.org == "" || i.bucket == "" {
		panic("trying to init an invalid store")
	}

	c := influxdb2.NewClient(i.dest, i.token)
	i.client = c.WriteAPIBlocking(i.org, i.bucket)
}

func (i *InfluxClient) Write(ctx context.Context, ts time.Time, name string, val any, tags map[string]string) {
	pointVal := map[string]any{"value": val}
	point := influxdb2.NewPoint(name, tags, pointVal, ts)

	i.logger.Debug().
		Time("ts", ts).
		Str("name", name).
		Interface("val", val).
		Interface("tags", tags).
		Msg("write")

	if err := i.client.WritePoint(ctx, point); err != nil {
		i.logger.Error().Err(err).Msg("write error")
	}
}
