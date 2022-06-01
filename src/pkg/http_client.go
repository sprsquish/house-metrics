package housemetrics

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"github.com/rs/zerolog"
	"io/ioutil"
	"net/http"
	"net/url"
	"strings"
)

var ErrFailedRequest = errors.New("failed request")

type HttpClient struct {
	client *http.Client
}

type Event struct {
	Type string
	Data string
}

func NewHttpClient() *HttpClient {
	return &HttpClient{
		client: &http.Client{},
	}
}

func URLOpt(u *url.URL) func(req *http.Request) {
	return func(req *http.Request) {
		req.URL = u
	}
}

func (c *HttpClient) SendJSON(ctx context.Context, log *zerolog.Logger, reqData interface{}, repData interface{}, opts func(*http.Request)) error {
	reqBytes, err := json.Marshal(reqData)
	if err != nil {
		return err
	}
	log.Debug().Str("body", string(reqBytes)).Msg("SendJSON req body")

	reqBody := bytes.NewReader(reqBytes)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, "", reqBody)
	if err != nil {
		return err
	}
	req.Header.Add("Content-Type", "application/json")

	opts(req)

	rep, err := c.client.Do(req)
	if err != nil {
		return err
	}

	if rep.StatusCode < 200 || rep.StatusCode >= 300 {
		return ErrFailedRequest
	}

	bodyBytes, _ := ioutil.ReadAll(rep.Body)
	log.Debug().Str("body", string(bodyBytes)).Msg("SendJSON recv body")

	if err := json.NewDecoder(bytes.NewReader(bodyBytes)).Decode(repData); err != nil {
		log.Error().Err(err).Str("body", string(bodyBytes)).Msg("SendJSON decode error")
		return err
	}
	return nil
}

func (c *HttpClient) GetJSON(ctx context.Context, log *zerolog.Logger, data interface{}, opts func(*http.Request)) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "", nil)
	if err != nil {
		return err
	}

	opts(req)

	rep, err := c.client.Do(req)
	if err != nil {
		return err
	}
	defer rep.Body.Close()

	if rep.StatusCode < 200 || rep.StatusCode >= 300 {
		return ErrFailedRequest
	}

	bodyBytes, _ := ioutil.ReadAll(rep.Body)
	log.Debug().Str("body", string(bodyBytes)).Msg("GetJSON body")

	if err := json.NewDecoder(bytes.NewReader(bodyBytes)).Decode(data); err != nil {
		log.Error().Err(err).Str("body", string(bodyBytes)).Msg("GetJSON decode error")
		return err
	}
	return nil
}

func (c *HttpClient) Events(ctx context.Context, log *zerolog.Logger, opts func(*http.Request)) (chan *Event, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "", nil)
	if err != nil {
		return nil, err
	}

	opts(req)

	rep, err := c.client.Do(req)
	if err != nil {
		return nil, err
	}

	eventChan := make(chan *Event)

	go func() {
		<-ctx.Done()
		rep.Body.Close()
		close(eventChan)
	}()

	go func() {
		defer rep.Body.Close()
		reader := bufio.NewReader(rep.Body)

		var event *Event
		for {
			line, err := reader.ReadString('\n')
			if line == "\n" {
				continue
			}

			log.Debug().Str("line", line).Msg("Event line")

			if err != nil {
				log.Error().Err(err).Msg("event stream read error")
				close(eventChan)
				return
			}

			if strings.HasPrefix(line, "data: ") && event != nil {
				event.Data = line[6 : len(line)-1]

				select {
				case eventChan <- event:
				default:
				}

				event = nil
				continue
			}

			if strings.HasPrefix(line, "event: ") {
				event = &Event{Type: line[7 : len(line)-1]}
			}
		}
	}()

	return eventChan, nil
}
