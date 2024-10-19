package looper

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/url"
	"time"

	"github.com/spf13/pflag"
	hm "github.com/sprsquish/housemetrics/pkg"
	"github.com/sprsquish/housemetrics/pkg/store"
)

const pTimeFormat = "2006/01/02T15:04:05z"

type PurpleAir struct {
	client *hm.HttpClient
	logger *slog.Logger

	host string

	url *url.URL
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
	"pm2.5_aqi",
	"current_humidity",
	"current_temp_f",
	"pressure",
}

func NewPurpleAir(name string, flags *pflag.FlagSet, logger *slog.Logger, client *hm.HttpClient) hm.Looper {
	p := PurpleAir{
		client: client,
		logger: logger,
	}

	flags.StringVar(&p.host, fmt.Sprintf("%s.host", name), "", "Local device host")

	return &p
}

func (p *PurpleAir) Init() {
	urlStr := fmt.Sprintf("http://%s/json", p.host)
	url, err := url.Parse(urlStr)
	if err != nil {
		p.logger.Error("failed to start", "err", err)
		return
	}

	p.url = url
}

func (p *PurpleAir) Poll(ctx context.Context, store store.Client) error {
	var reading map[string]any
	if err := p.client.GetJSON(ctx, p.logger, &reading, hm.URLOpt(p.url)); err != nil {
		return err
	}

	tsAny, found := reading["DateTime"]
	if !found {
		return errors.New("Did not find DateTime in reading")
	}

	tsStr, ok := tsAny.(string)
	if !ok {
		return fmt.Errorf("DateTime was not a string: %#v", tsAny)
	}

	ts, err := time.Parse(pTimeFormat, tsStr)
	if err != nil {
		return err
	}

	for _, name := range metricNames {
		if val, ok := reading[name]; ok {
			store.Write(ctx, ts, fmt.Sprintf("purpleair.%s", name), val, nil)
		}
	}

	return nil
}
