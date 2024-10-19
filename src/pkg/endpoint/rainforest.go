package endpoint

import (
	"bytes"
	"encoding/xml"
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"github.com/spf13/pflag"
	"github.com/sprsquish/housemetrics/pkg/store"
)

type Rainforest struct {
	logger *slog.Logger
	store  store.Client
}

func NewRainforest(name string, flags *pflag.FlagSet, logger *slog.Logger, store store.Client) http.Handler {
	return &Rainforest{
		logger: logger,
		store:  store,
	}
}

func (r *Rainforest) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	bodyBytes, _ := io.ReadAll(req.Body)
	defer req.Body.Close()

	var reading struct {
		InstantaneousDemand struct {
			TimeStamp string
			Demand    string
		}
	}
	err := xml.NewDecoder(bytes.NewReader(bodyBytes)).Decode(&reading)
	if err != nil {
		r.logger.Error("reading decode error", "err", err, "reading", bodyBytes)
		return
	}

	if reading.InstantaneousDemand.Demand == "" {
		return
	}

	tsStr := reading.InstantaneousDemand.TimeStamp[2:]
	tsInt, _ := strconv.ParseInt(tsStr, 16, 64)
	ts := time.Unix(tsInt, 0).AddDate(30, 0, 0)

	wattsStr := reading.InstantaneousDemand.Demand[2:]
	watts, _ := strconv.ParseInt(wattsStr, 16, 64)

	// int32 overflows to negative and influx expects a float
	val := float32(int32(watts))
	r.store.Write(req.Context(), ts, "watts", val, nil)

	w.WriteHeader(http.StatusOK)
}
