package websocket

import (
	"net/http"
	"testing"

	"github.com/stretchr/testify/assert"
)

func makeRequest(origin, host string) *http.Request {
	r := &http.Request{
		Header: make(http.Header),
		Host:   host,
	}
	if origin != "" {
		r.Header.Set("Origin", origin)
	}
	return r
}

func TestCheckOrigin_NoOriginHeader(t *testing.T) {
	// Same-origin request (no Origin header) is always allowed.
	check := checkOrigin(nil)
	assert.True(t, check(makeRequest("", "example.com")))

	check2 := checkOrigin([]string{})
	assert.True(t, check2(makeRequest("", "example.com")))
}

func TestCheckOrigin_NoConfiguredOrigins_MatchingHost(t *testing.T) {
	// No configured origins + Origin host == request Host → allowed.
	// This is the production case: NPM proxies the request and the
	// Origin header carries the same host as the Host header.
	check := checkOrigin(nil)
	assert.True(t, check(makeRequest("https://spela.example.com", "spela.example.com")))
}

func TestCheckOrigin_NoConfiguredOrigins_MismatchedHost(t *testing.T) {
	// No configured origins + Origin host != request Host → rejected.
	check := checkOrigin(nil)
	assert.False(t, check(makeRequest("https://evil.com", "spela.example.com")))
}

func TestCheckOrigin_ExplicitList_Allowed(t *testing.T) {
	check := checkOrigin([]string{"https://spela.example.com"})
	assert.True(t, check(makeRequest("https://spela.example.com", "server:8080")))
}

func TestCheckOrigin_ExplicitList_Rejected(t *testing.T) {
	check := checkOrigin([]string{"https://spela.example.com"})
	assert.False(t, check(makeRequest("https://other.com", "server:8080")))
}

func TestCheckOrigin_Wildcard(t *testing.T) {
	check := checkOrigin([]string{"*"})
	assert.True(t, check(makeRequest("https://anything.com", "server:8080")))
}

func TestCheckOrigin_InvalidOriginURL(t *testing.T) {
	// Malformed Origin URL should be rejected gracefully.
	check := checkOrigin(nil)
	assert.False(t, check(makeRequest("://bad url", "server:8080")))
}
