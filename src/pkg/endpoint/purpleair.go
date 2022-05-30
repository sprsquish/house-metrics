package endpoint

import (
	"github.com/rs/zerolog"
	"github.com/spf13/pflag"
	"github.com/sprsquish/housemetrics/pkg/store"
	"io/ioutil"
	"net/http"
)

type PurpleAir struct {
	logger *zerolog.Logger
	store  store.Client
}

func NewPurpleAir(name string, flags *pflag.FlagSet, logger *zerolog.Logger, store store.Client) http.Handler {
	return &PurpleAir{
		logger: logger,
		store:  store,
	}
}

func (p *PurpleAir) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	bodyBytes, _ := ioutil.ReadAll(req.Body)
	defer req.Body.Close()

	p.logger.Info().Bytes("body", bodyBytes).Msg("msg")

	w.WriteHeader(http.StatusOK)
}
