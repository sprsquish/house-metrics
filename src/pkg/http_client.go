package housemetrics

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
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

func (c *HttpClient) SendJSON(ctx context.Context, log *slog.Logger, reqData any, repData any, opts func(*http.Request)) error {
	reqBytes, err := json.Marshal(reqData)
	if err != nil {
		return err
	}
	log.Debug("SendJSON req body", "body", string(reqBytes))

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

	bodyBytes, _ := io.ReadAll(rep.Body)
	log.Debug("SendJSON recv body", "body", string(bodyBytes))

	if err := json.NewDecoder(bytes.NewReader(bodyBytes)).Decode(repData); err != nil {
		log.Error("SendJSON decode error", "err", err, "body", string(bodyBytes))
		return err
	}
	return nil
}

func (c *HttpClient) GetJSON(ctx context.Context, log *slog.Logger, data any, opts func(*http.Request)) error {
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
		bodyBytes, _ := io.ReadAll(rep.Body)
		log.Error("request error", "code", rep.StatusCode, "rep", bodyBytes)
		return ErrFailedRequest
	}

	bodyBytes, _ := io.ReadAll(rep.Body)
	log.Debug("GetJSON body", "body", string(bodyBytes))

	if err := json.NewDecoder(bytes.NewReader(bodyBytes)).Decode(data); err != nil {
		log.Error("GetJSON decode error", "err", err, "body", string(bodyBytes))
		return err
	}
	return nil
}

func (c *HttpClient) Events(ctx context.Context, log *slog.Logger, opts func(*http.Request)) (chan *Event, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "", nil)
	if err != nil {
		return nil, err
	}

	opts(req)

	rep, err := c.client.Do(req)
	if err != nil {
		return nil, err
	}

	errChan := make(chan error)
	eventChan := make(chan *Event)

	go func() {
		select {
		case <-ctx.Done():
		case err := <-errChan:
			log.Error("event stream read error", "err", err)
		}

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

			log.Debug("Event line", "line", line)

			if err != nil {
				errChan <- err
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
