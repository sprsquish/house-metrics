package looper

import (
	"context"
	"fmt"
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/route53"
	r53types "github.com/aws/aws-sdk-go-v2/service/route53/types"
	"github.com/rs/zerolog"
	"github.com/spf13/pflag"
	hm "github.com/sprsquish/housemetrics/internal"
	"github.com/sprsquish/housemetrics/internal/store"
	"net"
	"net/url"
	"strings"
	"sync"
)

var ipifyURL, _ = url.Parse("https://api.ipify.org?format=json")

type UpdateDNS struct {
	client *hm.HttpClient
	logger *zerolog.Logger

	r53Key      string
	r53Secret   string
	domainsFlag []string

	domains     map[string]string
	credentials aws.Credentials
	r53Client   *route53.Client
}

func NewUpdateDNS(name string, flags *pflag.FlagSet, logger *zerolog.Logger, client *hm.HttpClient) hm.Looper {
	u := UpdateDNS{
		client: client,
		logger: logger,
	}

	flags.StringArrayVar(&u.domainsFlag, fmt.Sprintf("%s.domains", name), nil, "domain=zone pairs")
	flags.StringVar(&u.r53Key, fmt.Sprintf("%s.r53Key", name), "", "route53 access key id")
	flags.StringVar(&u.r53Secret, fmt.Sprintf("%s.r53Secret", name), "", "route53 secret access key")

	return &u
}

func (u *UpdateDNS) Init() {
	u.domains = make(map[string]string, len(u.domainsFlag))
	for _, domain := range u.domainsFlag {
		pairs := strings.Split(domain, "=")
		u.domains[pairs[0]] = pairs[1]
	}

	u.credentials = aws.Credentials{
		AccessKeyID:     u.r53Key,
		SecretAccessKey: u.r53Secret,
	}

	u.r53Client = route53.NewFromConfig(aws.Config{
		Region:      "us-east-1",
		Credentials: u,
	})
}

func (u *UpdateDNS) Poll(ctx context.Context, store store.Client) error {
	curIP, err := u.currentIP(ctx)
	if err != nil {
		return err
	}

	var wg sync.WaitGroup
	for domain, zone := range u.domains {
		wg.Add(1)
		go func(d, z string) {
			defer wg.Done()
			resIPs, err := net.DefaultResolver.LookupIP(ctx, "ip4", d)
			if err != nil || len(resIPs) != 1 {
				u.logger.Error().Err(err).Str("domain", d).Msg("resolver error")
			}
			if curIP.Equal(resIPs[0]) {
				u.logger.Debug().Str("domain", d).Msg("ip is current")
				return
			}
			if err := u.updateEntry(ctx, resIPs[0], curIP, d, z); err != nil {
				u.logger.Error().Err(err).Str("domain", d).Msg("dns update error")
			}
		}(domain, zone)
	}
	wg.Wait()

	return nil
}

// Implement aws.CredentialsProvider interface
func (u *UpdateDNS) Retrieve(ctx context.Context) (aws.Credentials, error) {
	return u.credentials, nil
}

func (u *UpdateDNS) currentIP(ctx context.Context) (net.IP, error) {
	var rep struct{ IP string }
	if err := u.client.GetJSON(ctx, u.logger, &rep, hm.URLOpt(ipifyURL)); err != nil {
		return nil, err
	}
	return net.ParseIP(rep.IP), nil
}

func (u *UpdateDNS) updateEntry(ctx context.Context, oldIP, newIP net.IP, domain, zone string) error {
	oldSet := r53types.ResourceRecordSet{
		Name:            aws.String(domain),
		Type:            r53types.RRTypeA,
		TTL:             aws.Int64(300),
		ResourceRecords: []r53types.ResourceRecord{{Value: aws.String(oldIP.String())}},
	}

	newSet := oldSet
	newSet.ResourceRecords = []r53types.ResourceRecord{{Value: aws.String(newIP.String())}}

	changes := route53.ChangeResourceRecordSetsInput{
		ChangeBatch: &r53types.ChangeBatch{
			Changes: []r53types.Change{
				{Action: r53types.ChangeActionDelete, ResourceRecordSet: &oldSet},
				{Action: r53types.ChangeActionCreate, ResourceRecordSet: &newSet},
			},
		},
		HostedZoneId: aws.String(zone),
	}
	u.r53Client.ChangeResourceRecordSets(ctx, &changes)
	return nil
}
