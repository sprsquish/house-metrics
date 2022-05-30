package endpoint

import (
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/rs/zerolog"
	"github.com/spf13/pflag"
	"github.com/sprsquish/housemetrics/pkg/store"
	"io/ioutil"
	"net/http"
	"time"
)

type PurpleAir struct {
	logger *zerolog.Logger
	store  store.Client

	headerName  string
	headerValue string
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
	"current_humidity",
	"current_temp_f",
	"pressure",
}

func NewPurpleAir(name string, flags *pflag.FlagSet, logger *zerolog.Logger, store store.Client) http.Handler {
	p := &PurpleAir{
		logger: logger,
		store:  store,
	}

	flags.StringVar(&p.headerName, fmt.Sprintf("%s.header.name", name), "", "header value to check")
	flags.StringVar(&p.headerValue, fmt.Sprintf("%s.header.value", name), "", "header value to check")

	return p
}

func (p *PurpleAir) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	if req.Method != http.MethodPost {
		w.WriteHeader(http.StatusNotFound)
		return
	}

	if req.Header.Get(p.headerName) != p.headerValue {
		w.WriteHeader(http.StatusNotFound)
		return
	}

	bodyBytes, _ := ioutil.ReadAll(req.Body)
	defer req.Body.Close()

	w.WriteHeader(http.StatusOK)

	p.logger.Debug().Bytes("body", bodyBytes).Msg("msg")

	var reading map[string]interface{}
	if err := json.NewDecoder(bytes.NewReader(bodyBytes)).Decode(&reading); err != nil {
		p.logger.Error().Err(err).Str("body", string(bodyBytes)).Msg("decode error")
		return
	}

	ts := time.Now()

	for _, name := range metricNames {
		if val, ok := reading[name]; ok {
			p.store.Write(req.Context(), ts, name, val, nil)
		}
	}
}
