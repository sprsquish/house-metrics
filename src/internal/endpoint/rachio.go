package endpoint

import (
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/rs/zerolog"
	"github.com/spf13/pflag"
	"github.com/sprsquish/housemetrics/internal/store"
	"io"
	"io/ioutil"
	"net/http"
	"time"
)

type Rachio struct {
	logger     *zerolog.Logger
	store      store.Client
	externalID string
}

func NewRachio(name string, flags *pflag.FlagSet, logger *zerolog.Logger, store store.Client) http.Handler {
	r := &Rachio{
		logger: logger,
		store:  store,
	}

	flags.StringVar(&r.externalID, fmt.Sprintf("%s.externalID", name), "", "External ID sent with event")

	return r
}

func (r *Rachio) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	if req.Method != http.MethodPost {
		w.WriteHeader(http.StatusNotFound)
		return
	}

	bodyBytes, _ := ioutil.ReadAll(req.Body)
	defer req.Body.Close()

	var event struct {
		Timestamp    string
		ZoneName     string
		SubType      string
		ZoneRunState string
		ExternalID   string
	}
	err := json.NewDecoder(bytes.NewReader(bodyBytes)).Decode(&event)
	if err != nil {
		if err != io.EOF {
			r.logger.Error().Err(err).Bytes("event", bodyBytes).Msg("event decode error")
		}
		return
	}

	if event.ExternalID != r.externalID {
		w.WriteHeader(http.StatusNotFound)
		return
	}

	var status int
	switch event.SubType {
	case "ZONE_STARTED":
		status = 1
	case "ZONE_COMPLETED":
		status = 0
	default:
		status = -1
		r.logger.Error().Bytes("event", bodyBytes).Msg("invalid event")
	}

	tags := map[string]string{"name": event.ZoneName}
	r.store.Write(req.Context(), time.Now(), "sprinkler", status, tags)

	w.WriteHeader(http.StatusOK)
}
