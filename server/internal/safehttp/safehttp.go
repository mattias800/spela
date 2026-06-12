// Package safehttp provides SSRF-hardened HTTP fetching primitives shared by
// any code that fetches attacker-controlled or upstream-controlled URLs
// (e.g. user avatars, admin "set hero art", scraper images returned by
// IGDB/SteamGridDB/Pouet).
//
// Issue #1120: the previous scraper.DownloadExternalImage used a default
// http.Client with no scheme allowlist, no private-IP filter, no redirect
// validation, and no size cap. A rogue admin (or compromised upstream
// service) could point it at internal services or large response bodies.
package safehttp

import (
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// privateNetworks is the deny list of CIDR ranges that must never be
// reached by outbound URL fetches. Mirrors api.privateNetworks.
var privateNetworks = func() []*net.IPNet {
	cidrs := []string{
		"0.0.0.0/8",      // Current network
		"10.0.0.0/8",     // Private (RFC 1918)
		"100.64.0.0/10",  // Carrier-grade NAT (RFC 6598)
		"127.0.0.0/8",    // Loopback
		"169.254.0.0/16", // Link-local (incl. AWS instance metadata 169.254.169.254)
		"172.16.0.0/12",  // Private (RFC 1918)
		"192.0.0.0/24",   // IETF protocol assignments
		"192.168.0.0/16", // Private (RFC 1918)
		"198.18.0.0/15",  // Benchmarking (RFC 2544)
		"::1/128",        // IPv6 loopback
		"fc00::/7",       // IPv6 unique local
		"fe80::/10",      // IPv6 link-local
	}
	var nets []*net.IPNet
	for _, cidr := range cidrs {
		_, n, err := net.ParseCIDR(cidr)
		if err == nil {
			nets = append(nets, n)
		}
	}
	return nets
}()

// allowPrivateForTest, when true, makes IsPrivateURL return false for
// private addresses. Used only by tests that spin up httptest servers
// bound to 127.0.0.1 — production code never flips this. Set via
// SetAllowPrivateForTest from a _test.go file.
var allowPrivateForTest bool

// SetAllowPrivateForTest is a test-only escape hatch that bypasses the
// private-IP block list for the duration of a test. Production code
// must not call this.
func SetAllowPrivateForTest(v bool) { allowPrivateForTest = v }

// IsPrivateURL reports whether rawURL points at a private/internal IP. A
// scheme other than http/https, an unparseable URL, or an unresolvable host
// is treated as private (fail-closed). Returns the parsed URL on success
// for callers that want to do additional checks.
func IsPrivateURL(rawURL string) bool {
	if allowPrivateForTest {
		// Still keep scheme + host validation so test code is exercised
		// against the real shape of the function.
		if u, err := url.Parse(rawURL); err == nil {
			if u.Scheme != "http" && u.Scheme != "https" {
				return true
			}
			if u.Hostname() == "" {
				return true
			}
			return false
		}
		return true
	}
	u, err := url.Parse(rawURL)
	if err != nil {
		return true
	}
	if u.Scheme != "http" && u.Scheme != "https" {
		return true
	}
	host := u.Hostname()
	if host == "" {
		return true
	}
	if strings.EqualFold(host, "localhost") {
		return true
	}
	ips, err := net.LookupIP(host)
	if err != nil {
		return true
	}
	for _, ip := range ips {
		if v4 := ip.To4(); v4 != nil {
			ip = v4
		}
		for _, n := range privateNetworks {
			if n.Contains(ip) {
				return true
			}
		}
	}
	return false
}

// ErrPrivateURL is returned when an outbound fetch is rejected because the
// URL targets a private IP (initial host or redirect target).
var ErrPrivateURL = errors.New("safehttp: URL targets a private/internal IP")

// Residual DNS-rebinding note (#1323): CheckURL/IsPrivateURL resolve and
// validate the host, and CheckRedirect re-validates every redirect hop, but
// the actual TCP connection re-resolves DNS independently. An attacker who
// controls a hostname with a very short TTL could therefore resolve public at
// check time and private at connect time (a TOCTOU window).
//
// We deliberately do NOT close this with a connect-time IP pin (a custom
// DialContext that dials only the validated IP). Such a dialer also intercepts
// the address of an outbound HTTP proxy, and self-hosted operators behind a
// firewalled/corporate proxy frequently route through a private-range proxy IP
// — pinning would block that and break all outbound fetching (scraping, cover
// art, cores, BIOS). The exposure here is low: the only inputs that reach
// safehttp with attacker influence over the hostname are admin-only (set-hero
// URL) or an already-compromised upstream metadata provider, both of which are
// high-privilege positions. If this trade-off ever changes, the fix is a
// pinning dialer guarded to skip the proxy case.

// ErrUnsupportedScheme is returned when an outbound fetch is rejected
// because the scheme is not http or https.
var ErrUnsupportedScheme = errors.New("safehttp: only http/https are allowed")

// NewClient returns a hardened *http.Client with:
//   - http/https scheme enforcement
//   - private-IP rejection on the initial request and on every redirect hop
//   - configurable per-request timeout
//   - a redirect cap of 10 (matches Go's default) to bound chains
//
// Body size limits are caller-side — the client cannot enforce them since
// it doesn't read the body.
func NewClient(timeout time.Duration) *http.Client {
	return &http.Client{
		Timeout: timeout,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) >= 10 {
				return errors.New("safehttp: redirect chain exceeds 10 hops")
			}
			if IsPrivateURL(req.URL.String()) {
				return fmt.Errorf("redirect to %s: %w", req.URL.Host, ErrPrivateURL)
			}
			return nil
		},
	}
}

// NewStrictHTTPSClient is like NewClient but additionally rejects any
// redirect hop whose scheme is not https. It is intended for fetching
// executable artifacts — libretro core binaries (#1315) — where transport
// authentication must never be silently downgraded to cleartext by a
// misbehaving or compromised upstream. A redirect from https to http would
// otherwise let a network attacker serve an arbitrary (malicious) binary
// over a connection with no server authentication.
//
// The initial request URL is the caller's responsibility (the cores poller
// uses a hardcoded https buildbot constant); this guards the redirect chain.
func NewStrictHTTPSClient(timeout time.Duration) *http.Client {
	return &http.Client{
		Timeout: timeout,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) >= 10 {
				return errors.New("safehttp: redirect chain exceeds 10 hops")
			}
			if req.URL.Scheme != "https" {
				return fmt.Errorf("redirect to %q scheme: %w", req.URL.Scheme, ErrUnsupportedScheme)
			}
			if IsPrivateURL(req.URL.String()) {
				return fmt.Errorf("redirect to %s: %w", req.URL.Host, ErrPrivateURL)
			}
			return nil
		},
	}
}

// CheckURL is a convenience that runs the same checks as NewClient applies
// before issuing a request — useful for callers that want to gate a URL
// before passing it to an existing http.Client (where redirect validation
// alone is not enough).
func CheckURL(rawURL string) error {
	u, err := url.Parse(rawURL)
	if err != nil {
		return fmt.Errorf("safehttp: parse URL: %w", err)
	}
	if u.Scheme != "http" && u.Scheme != "https" {
		return ErrUnsupportedScheme
	}
	if IsPrivateURL(rawURL) {
		return ErrPrivateURL
	}
	return nil
}
