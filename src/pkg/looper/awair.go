package looper

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/spf13/pflag"
	hm "github.com/sprsquish/housemetrics/pkg"
	housemetrics "github.com/sprsquish/housemetrics/pkg"
	"github.com/sprsquish/housemetrics/pkg/store"
)

type Awair struct {
	client *hm.HttpClient
	logger *slog.Logger

	token   string
	devices []string

	devURLs map[string]*url.URL
}

type AwairReading struct {
	Data []struct {
		Timestamp string
		Sensors   []struct {
			Comp  string
			Value any
		}
	}
}

func NewAwair(name string, flags *pflag.FlagSet, logger *slog.Logger, client *hm.HttpClient) hm.Looper {
	a := Awair{
		client:  client,
		logger:  logger,
		devURLs: map[string]*url.URL{},
	}

	flags.StringVar(&a.token, fmt.Sprintf("%s.token", name), "", "Access token")
	flags.StringSliceVar(&a.devices, fmt.Sprintf("%s.devices", name), []string{}, "List of devices: 'name:type:id'")

	return &a
}

func (a *Awair) Init() {
	for _, device := range a.devices {
		dev := strings.Split(device, ":")
		if len(dev) != 3 {
			a.logger.Error("bad device", "device", device)
			continue
		}

		devName, devType, devID := dev[0], dev[1], dev[2]

		urlStr := fmt.Sprintf("https://developer-apis.awair.is/v1/users/self/devices/%s/%s/air-data/latest", devType, devID)
		if url, err := url.Parse(urlStr); err != nil {
			a.logger.Error("couldn't parse URL", "err", err, "device", devName)
		} else {
			a.devURLs[devName] = url
		}
	}
}

func (a *Awair) Poll(ctx context.Context, store store.Client) error {
	a.logger.Debug("polling devices")

	for devName, devURL := range a.devURLs {
		var reading AwairReading
		if err := a.client.GetJSON(ctx, a.logger, &reading, func(req *http.Request) {
			req.URL = devURL
			req.Header.Add("Authorization", fmt.Sprintf("Bearer %s", a.token))
		}); err != nil {
			return err
		}

		devTags := map[string]string{"device": devName}
		for _, entry := range reading.Data {
			ts, err := time.Parse("2006-01-02T15:04:05.999Z", entry.Timestamp)
			if err != nil {
				a.logger.Error("could not parse timestamp", "err", err, "reading", reading)
			}

			for _, sensor := range entry.Sensors {
				store.Write(ctx, ts, fmt.Sprintf("awair.%s", sensor.Comp), sensor.Value, devTags)
				if pm25, ok := sensor.Value.(float64); ok && sensor.Comp == "pm25" {
					store.Write(ctx, ts, "awair.pm25_aqi", housemetrics.PM25ToAQI(pm25), devTags)
				}
			}
		}
	}

	return nil
}
