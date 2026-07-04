package api

import (
	"bytes"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return f(req)
}

func captureSlog(t *testing.T) *bytes.Buffer {
	t.Helper()
	var buf bytes.Buffer
	prev := slog.Default()
	slog.SetDefault(slog.New(slog.NewTextHandler(&buf, &slog.HandlerOptions{Level: slog.LevelInfo})))
	t.Cleanup(func() { slog.SetDefault(prev) })
	return &buf
}

func TestDoFederationRequest_DebugDisabledDoesNotLog(t *testing.T) {
	t.Setenv("SPELA_FEDERATION_DEBUG", "")
	logs := captureSlog(t)

	req, err := http.NewRequest(http.MethodGet, "https://peer.example/api/federation/ping", nil)
	require.NoError(t, err)
	client := &http.Client{Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
		return &http.Response{
			StatusCode: http.StatusOK,
			Body:       io.NopCloser(strings.NewReader("{}")),
			Header:     make(http.Header),
			Request:    req,
		}, nil
	})}

	resp, err := doFederationRequest(client, req, "peer-fingerprint")
	require.NoError(t, err)
	require.NoError(t, resp.Body.Close())
	assert.Empty(t, logs.String())
}

func TestDoFederationRequest_DebugLogsRedactedBoundedMetadata(t *testing.T) {
	t.Setenv("SPELA_FEDERATION_DEBUG", "1")
	logs := captureSlog(t)

	req, err := http.NewRequest(
		http.MethodPost,
		"https://peer.example/api/federation/stats?"+strings.Repeat("q", federationDebugQueryMaxBytes+8),
		strings.NewReader("request-body-secret"),
	)
	require.NoError(t, err)
	req.Header.Set(headerFedRequestID, "req-123")
	req.Header.Set(headerFedSignature, "signature-secret")
	req.Header.Set("Content-Type", "application/json")

	client := &http.Client{Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
		return &http.Response{
			StatusCode:    http.StatusAccepted,
			Body:          io.NopCloser(strings.NewReader("response-body-secret")),
			Header:        http.Header{"Content-Type": []string{"application/json"}},
			ContentLength: int64(len("response-body-secret")),
			Request:       req,
		}, nil
	})}

	resp, err := doFederationRequest(client, req, "peer-fingerprint-abcdefghijklmnopqrstuvwxyz")
	require.NoError(t, err)
	body, err := io.ReadAll(resp.Body)
	require.NoError(t, err)
	require.NoError(t, resp.Body.Close())
	assert.Equal(t, "response-body-secret", string(body), "debug logging must not consume response bodies")

	out := logs.String()
	assert.Contains(t, out, "federation-http-request")
	assert.Contains(t, out, "federation-http-response")
	assert.Contains(t, out, "request_id=req-123")
	assert.Contains(t, out, "status=202")
	assert.Contains(t, out, "query_truncated=true")
	assert.Contains(t, out, "signed=true")
	assert.NotContains(t, out, "signature-secret")
	assert.NotContains(t, out, "request-body-secret")
	assert.NotContains(t, out, "response-body-secret")
}

func TestHTTPPairClientSendsRequestID(t *testing.T) {
	var gotRequestID string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotRequestID = r.Header.Get(headerFedRequestID)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"fingerprint":"fp","publicKey":"pk","baseURL":"https://remote","status":"active"}`))
	}))
	defer server.Close()

	_, err := (httpPairClient{}).Pair(server.URL, "req-pair-123", PairRequestBody{})
	require.NoError(t, err)
	assert.Equal(t, "req-pair-123", gotRequestID)
}
