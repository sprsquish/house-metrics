package housemetrics

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/url"
	"strings"
)

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

func (c *HttpClient) SendJSON(ctx context.Context, reqData interface{}, repData interface{}, opts func(*http.Request)) error {
	reqBytes, err := json.Marshal(reqData)
	if err != nil {
		return err
	}

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

	bodyBytes, _ := ioutil.ReadAll(rep.Body)

	return json.NewDecoder(bytes.NewReader(bodyBytes)).Decode(repData)
}

func (c *HttpClient) GetJSON(ctx context.Context, data interface{}, opts func(*http.Request)) error {
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

	bodyBytes, _ := ioutil.ReadAll(rep.Body)

	if err := json.NewDecoder(bytes.NewReader(bodyBytes)).Decode(data); err != nil {
		return fmt.Errorf("%w data: %s", err, string(bodyBytes))
	}
	return nil
}

func (c *HttpClient) Events(ctx context.Context, opts func(*http.Request)) (chan *Event, error) {
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
			if err != nil {
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
