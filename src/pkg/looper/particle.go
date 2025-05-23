package looper

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/spf13/pflag"
	hm "github.com/sprsquish/housemetrics/pkg"
	"github.com/sprsquish/housemetrics/pkg/store"
)

var eventTypes = map[string]struct{}{
	"lum-full-avg": {},
}

type Particle struct {
	client *hm.HttpClient
	logger *slog.Logger

	stream    string
	auth      string
	streamURL *url.URL
}

func NewParticle(name string, flags *pflag.FlagSet, logger *slog.Logger, client *hm.HttpClient) hm.Looper {
	p := Particle{
		client: client,
		logger: logger,
	}

	flags.StringVar(&p.stream, fmt.Sprintf("%s.stream", name), "https://api.particle.io/v1/devices/events", "Stream URL")
	flags.StringVar(&p.auth, fmt.Sprintf("%s.auth", name), "", "Auth code")

	return &p
}

func (p *Particle) Init() {
	streamURL, err := url.Parse(fmt.Sprintf("%s?access_token=%s", p.stream, p.auth))
	if err != nil {
		p.logger.Error("couldn't parse URL", "err", err)
		return
	}
	p.streamURL = streamURL
}

func (p *Particle) Poll(ctx context.Context, store store.Client) error {
	eventChan, err := p.client.Events(ctx, p.logger, hm.URLOpt(p.streamURL))
	if err != nil {
		return err
	}

	for {
		evt := <-eventChan
		p.logger.Debug("event received", "event", evt)

		// channel closed
		if evt == nil {
			p.logger.Info("event channel closed")
			return nil
		}

		if _, ok := eventTypes[evt.Type]; !ok {
			continue
		}

		var data struct {
			Value       string `json:"data"`
			CoreID      string `json:"coreid"`
			PublishedAt string `json:"published_at"`
		}
		if err := json.NewDecoder(strings.NewReader(evt.Data)).Decode(&data); err != nil {
			p.logger.Error("couldn't parse event data", "err", err, "event", evt)
		}

		tags := map[string]string{"coreid": data.CoreID}
		val, err := strconv.ParseFloat(data.Value, 32)
		if err != nil {
			p.logger.Error("could not convert data value", "err", err, "data", data)
			continue
		}

		ts, err := time.Parse("2006-01-02T15:04:05.999Z", data.PublishedAt)
		if err != nil {
			p.logger.Error("could not parse published_at", "err", err, "data", data)
		}

		store.Write(ctx, ts, fmt.Sprintf("particle.%s", evt.Type), val, tags)
	}
}
