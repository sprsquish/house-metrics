package looper

import (
	"context"
	"fmt"
	"github.com/rs/zerolog"
	"github.com/spf13/pflag"
	hm "github.com/sprsquish/housemetrics/internal"
	"github.com/sprsquish/housemetrics/internal/store"
	"net/url"
	"strconv"
	"time"
)

type PurpleAir struct {
	client *hm.HttpClient
	logger *zerolog.Logger

	sensor int
	url    *url.URL
}

type PurpleAirReading struct {
	Results []map[string]interface{}
}

var metricNames = []string{
	"p_0_3_um",
	"p_0_5_um",
	"p_1_0_um",
	"p_2_5_um",
	"p_5_0_um",
	"p_10_0_um",
	"pm1_0_cf_1",
	"pm2_5_cf_1",
	"pm10_0_cf_1",
	"pm1_0_atm",
	"pm2_5_atm",
	"pm10_0_atm",
	"humidity",
	"temp_f",
	"pressure",
}

func NewPurpleAir(name string, flags *pflag.FlagSet, logger *zerolog.Logger, client *hm.HttpClient) hm.Looper {
	p := PurpleAir{
		client: client,
		logger: logger,
	}

	flags.IntVar(&p.sensor, fmt.Sprintf("%s.sensor", name), 0, "Sensor ID")

	return &p
}

func (p *PurpleAir) Init() {
	p.logger.Debug().Msg("inititalizing")
	sensorURL, err := url.Parse(fmt.Sprintf("https://www.purpleair.com/json?show=%d", p.sensor))
	if err != nil {
		p.logger.Error().Err(err).Msg("couldn't parse URL")
		return
	}
	p.logger.Debug().Interface("url", sensorURL).Msg("setting url")
	p.url = sensorURL
}

func (p *PurpleAir) Poll(ctx context.Context, store store.Client) error {
	var reading PurpleAirReading
	if err := p.client.GetJSON(ctx, p.logger, &reading, hm.URLOpt(p.url)); err != nil {
		return err
	}

	tags := map[string]string{"label": ""}
	ts := time.Now()
	for _, result := range reading.Results {
		tags["label"] = result["Label"].(string)
		for _, name := range metricNames {
			var val interface{}

			switch v := result[name].(type) {
			case string:
				val, _ = strconv.ParseFloat(v, 16)
			default:
				val = v
			}

			if val != nil {
				store.Write(ctx, ts, name, val, tags)
			}
		}
	}

	return nil
}
