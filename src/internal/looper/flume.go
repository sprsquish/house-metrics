package looper

import (
	"context"
	"fmt"
	"github.com/rs/zerolog"
	"github.com/spf13/pflag"
	hm "github.com/sprsquish/housemetrics/internal"
	"github.com/sprsquish/housemetrics/internal/store"
	"net/http"
	"net/url"
	"time"
)

var (
	authURL, _ = url.Parse("https://api.flumetech.com/oauth/token")
	tags       = map[string]string{"unit": "gallons"}
	timeFormat = "2006-01-02 15:04:05"
	timeLoc, _ = time.LoadLocation("America/Los_Angeles")
)

type Flume struct {
	client *hm.HttpClient
	logger *zerolog.Logger

	clientID     string
	clientSecret string
	username     string
	password     string
	userID       string
	deviceID     string

	queryURL  *url.URL
	tokenBody map[string]string
	sinceTS   time.Time
}

func NewFlume(name string, flags *pflag.FlagSet, logger *zerolog.Logger, client *hm.HttpClient) hm.Looper {
	f := Flume{
		client: client,
		logger: logger,
	}

	flags.StringVar(&f.clientID, fmt.Sprintf("%s.clientID", name), "", "oAuth client ID")
	flags.StringVar(&f.clientSecret, fmt.Sprintf("%s.clientSecret", name), "", "oAuth client secret")
	flags.StringVar(&f.username, fmt.Sprintf("%s.username", name), "", "username")
	flags.StringVar(&f.password, fmt.Sprintf("%s.password", name), "", "password")
	flags.StringVar(&f.userID, fmt.Sprintf("%s.userID", name), "", "userID")
	flags.StringVar(&f.deviceID, fmt.Sprintf("%s.deviceID", name), "", "deviceID")

	return &f
}

func (f *Flume) Init() {
	f.sinceTS = time.Now()
	f.queryURL, _ = url.Parse(fmt.Sprintf("https://api.flumetech.com/users/%s/devices/%s/query", f.userID, f.deviceID))
	f.tokenBody = map[string]string{
		"grant_type":    "password",
		"client_id":     f.clientID,
		"client_secret": f.clientSecret,
		"username":      f.username,
		"password":      f.password,
	}
}

func (f *Flume) Poll(ctx context.Context, store store.Client) error {
	tkn, err := f.getToken(ctx)
	if err != nil {
		return err
	}

	nowTS := time.Now()
	reqData := map[string]interface{}{
		"queries": []map[string]string{{
			"since_datetime": f.sinceTS.Format(timeFormat),
			"until_datetime": nowTS.Format(timeFormat),
			"request_id":     "query",
			"bucket":         "MIN",
			"units":          "GALLONS",
		}},
	}

	var repData struct {
		Data []struct {
			Query []struct {
				Datetime string
				Value    interface{}
			}
		}
	}

	if err := f.client.SendJSON(ctx, reqData, &repData, func(req *http.Request) {
		req.URL = f.queryURL
		req.Header.Add("Authorization", fmt.Sprintf("Bearer %s", tkn))
	}); err != nil {
		return err
	}

	for _, data := range repData.Data {
		for _, entry := range data.Query {
			ts, err := time.Parse(timeFormat, entry.Datetime)
			if err != nil {
				f.logger.Error().Err(err).Interface("entry", entry).Msg("could not parse timestamp")
			}
			store.Write(ctx, ts, "flume.usage", entry.Value, tags)
		}
	}

	f.sinceTS = nowTS

	return nil
}

func (f *Flume) getToken(ctx context.Context) (tkn string, err error) {
	var tknStruct struct {
		Data []struct {
			Token string `json:"access_token"`
		}
	}
	err = f.client.SendJSON(ctx, f.tokenBody, &tknStruct, hm.URLOpt(authURL))
	if err != nil {
		return
	}

	return tknStruct.Data[0].Token, nil
}
