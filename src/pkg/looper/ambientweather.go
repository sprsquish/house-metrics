package looper

import (
	"context"
	"fmt"
	"github.com/rs/zerolog"
	"github.com/spf13/pflag"
	hm "github.com/sprsquish/housemetrics/pkg"
	"github.com/sprsquish/housemetrics/pkg/store"
	"net/url"
	"time"
)

type AmbientWeather struct {
	client *hm.HttpClient
	logger *zerolog.Logger

	mac    string
	apiKey string
	appKey string

	url *url.URL
}

type weatherReading struct {
	LastData    *weatherReading `json:"lastData"`
	DateUTC     int64           `json:"dateutc"`
	OutdoorTemp float64         `json:"tempf"`
	OutdoorHum  float64         `json:"humidity"`
	WindDir     float64         `json:"winddir"`
	WindGust    float64         `json:"windgustmph"`
	WindSpeed   float64         `json:"windspeedmph"`
	Radiation   float64         `json:"solarradiation"`
	UV          float64         `json:"uv"`
	RainRate    float64         `json:"hourlyrainin"`
	RainEvent   float64         `json:"eventrainin"`
}

func NewAmbientWeather(name string, flags *pflag.FlagSet, logger *zerolog.Logger, client *hm.HttpClient) hm.Looper {
	a := AmbientWeather{
		client: client,
		logger: logger,
	}

	flags.StringVar(&a.mac, fmt.Sprintf("%s.mac", name), "", "Weather station MAC")
	flags.StringVar(&a.apiKey, fmt.Sprintf("%s.api", name), "", "Ambient Weather API Key")
	flags.StringVar(&a.appKey, fmt.Sprintf("%s.app", name), "", "Ambient Weather App Key")

	return &a
}

func (a *AmbientWeather) Init() {
	urlStr := fmt.Sprintf("https://api.ambientweather.net/v1/devices/%s", a.mac)
	url, err := url.Parse(urlStr)
	if err != nil {
		a.logger.Error().Err(err).Msg("failed to start")
		return
	}

	q := url.Query()
	q.Set("apiKey", a.apiKey)
	q.Set("applicationKey", a.appKey)
	q.Set("limit", "1")
	url.RawQuery = q.Encode()

	a.url = url
}

func (a *AmbientWeather) Poll(ctx context.Context, store store.Client) error {
	var readings []*weatherReading
	if err := a.client.GetJSON(ctx, a.logger, &readings, hm.URLOpt(a.url)); err != nil {
		return err
	}

	for _, r := range readings {
		if r.LastData != nil {
			r = r.LastData
		}

		ts := time.UnixMilli(r.DateUTC)
		store.Write(ctx, ts, "outdoor_temp", r.OutdoorTemp, nil)
		store.Write(ctx, ts, "outdoor_hum", r.OutdoorHum, nil)
		store.Write(ctx, ts, "wind_dir", r.WindDir, nil)
		store.Write(ctx, ts, "wind_gust", r.WindGust, nil)
		store.Write(ctx, ts, "wind_speed", r.WindSpeed, nil)
		store.Write(ctx, ts, "solar_radiation", r.Radiation, nil)
		store.Write(ctx, ts, "uv_index", r.UV, nil)
		store.Write(ctx, ts, "rain_rate_hourly", r.RainRate, nil)
		store.Write(ctx, ts, "rain_event_accum", r.RainEvent, nil)
	}

	return nil
}
