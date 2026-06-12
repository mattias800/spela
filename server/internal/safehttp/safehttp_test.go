package safehttp

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func TestIsPrivateURL(t *testing.T) {
	cases := []struct {
		url     string
		private bool
	}{
		{"http://127.0.0.1/foo", true},
		{"http://localhost/foo", true},
		{"http://169.254.169.254/latest/meta-data/", true}, // AWS metadata
		{"http://10.0.0.1/", true},
		{"http://172.16.0.5/", true},
		{"http://192.168.1.1/", true},
		{"file:///etc/passwd", true}, // wrong scheme — fail closed
		{"javascript:alert(1)", true},
		{"http://[::1]/", true},
		{"https://1.1.1.1/", false},
		{"https://example.com/", false},
	}
	for _, tc := range cases {
		t.Run(tc.url, func(t *testing.T) {
			assert.Equal(t, tc.private, IsPrivateURL(tc.url))
		})
	}
}

func TestCheckURL_RejectsNonHTTPSchemes(t *testing.T) {
	for _, u := range []string{
		"ftp://example.com/",
		"gopher://example.com/",
		"javascript:alert(1)",
		"data:text/html,<script>",
	} {
		err := CheckURL(u)
		assert.Error(t, err, "expected error for scheme in %q", u)
	}
}

// TestNewClient_RedirectToPrivateRejected verifies that a 302 redirect to a
// private IP is refused mid-flight, defeating the classic SSRF
// "public-host -> redirect to 127.0.0.1" trick.
func TestNewClient_RedirectToPrivateRejected(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "http://127.0.0.1:1/", http.StatusFound)
	}))
	defer srv.Close()

	cli := NewClient(5 * time.Second)
	resp, err := cli.Get(srv.URL)
	if resp != nil {
		resp.Body.Close()
	}
	if err == nil {
		t.Fatal("expected error from redirect to private IP")
	}

	// errors.Is may not match because http.Client wraps in *url.Error.
	// Accept either the sentinel or a message reference.
	if !errors.Is(err, ErrPrivateURL) && !strings.Contains(err.Error(), "private") {
		t.Fatalf("expected ErrPrivateURL, got: %v", err)
	}
}

// TestNewStrictHTTPSClient_RejectsRedirectDowngrade verifies that the strict
// client refuses an https->http redirect, so a compromised/MITM'd upstream
// can't downgrade a core-binary fetch to an unauthenticated transport (#1315).
// The redirect target is a public http host; the scheme check fires before
// any DNS lookup, so no real network call is made to it.
func TestNewStrictHTTPSClient_RejectsRedirectDowngrade(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "http://example.com/evil-core.zip", http.StatusFound)
	}))
	defer srv.Close()

	cli := NewStrictHTTPSClient(5 * time.Second)
	resp, err := cli.Get(srv.URL)
	if resp != nil {
		resp.Body.Close()
	}
	if err == nil {
		t.Fatal("expected error from https->http redirect downgrade")
	}
	if !errors.Is(err, ErrUnsupportedScheme) && !strings.Contains(err.Error(), "scheme") {
		t.Fatalf("expected ErrUnsupportedScheme, got: %v", err)
	}
}
