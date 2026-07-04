package api

import (
	"bytes"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"strconv"
	"time"

	"github.com/spela/server/internal/federation"
)

// federationHTTPTimeout bounds outbound federation calls.
const federationHTTPTimeout = 15 * time.Second

const federationDebugQueryMaxBytes = 256

func fedHTTPClient() *http.Client { return &http.Client{Timeout: federationHTTPTimeout} }

// signedFederationHeaders builds the signed headers for an outbound
// server-to-server request, matching what VerifyFederationRequest expects.
// recipientFingerprint binds the request to its intended recipient.
func signedFederationHeaders(id federation.Identity, method, path, requestID string, body []byte, recipientFingerprint string) map[string]string {
	ts := strconv.FormatInt(time.Now().Unix(), 10)
	bodyHash := sha256.Sum256(body)
	sig := id.Sign(signedRequestMessage(method, path, ts, hex.EncodeToString(bodyHash[:]), recipientFingerprint))
	return map[string]string{
		headerFedFingerprint: id.Fingerprint(),
		headerFedTimestamp:   ts,
		headerFedSignature:   base64.StdEncoding.EncodeToString(sig),
		headerFedRequestID:   requestID,
	}
}

// httpPairClient is the production pairClient: POST {baseURL}/api/federation/pair.
type httpPairClient struct{}

func (httpPairClient) Pair(baseURL, requestID string, body PairRequestBody) (PairResponseBody, error) {
	reqBody, _ := json.Marshal(body)
	req, err := http.NewRequest(http.MethodPost, baseURL+"/api/federation/pair", bytes.NewReader(reqBody))
	if err != nil {
		return PairResponseBody{}, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set(headerFedRequestID, requestID)
	resp, err := doFederationRequest(fedHTTPClient(), req, "")
	if err != nil {
		return PairResponseBody{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return PairResponseBody{}, fmt.Errorf("remote returned %d", resp.StatusCode)
	}
	var out PairResponseBody
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		return PairResponseBody{}, err
	}
	return out, nil
}

// PingResult is the outcome of an outbound connection test to a peer (#1350).
type PingResult struct {
	Reachable        bool   `json:"reachable"`
	PeerFingerprint  string `json:"peerFingerprint"`
	FingerprintMatch bool   `json:"fingerprintMatch"`
	PeerUnixTime     int64  `json:"peerUnixTime"`
	ClockSkewSeconds int64  `json:"clockSkewSeconds"`
	LatencyMs        int64  `json:"latencyMs"`
	Error            string `json:"error"`
}

// peerPinger performs an outbound signed ping to a peer (test-connection).
type peerPinger interface {
	Ping(baseURL, requestID string, id federation.Identity, expectFingerprint string) PingResult
}

type httpPeerPinger struct{}

func (httpPeerPinger) Ping(baseURL, requestID string, id federation.Identity, expectFingerprint string) PingResult {
	const path = "/api/federation/ping"
	req, err := http.NewRequest(http.MethodGet, baseURL+path, nil)
	if err != nil {
		return PingResult{Error: err.Error()}
	}
	for k, v := range signedFederationHeaders(id, http.MethodGet, path, requestID, nil, expectFingerprint) {
		req.Header.Set(k, v)
	}
	start := time.Now()
	resp, err := doFederationRequest(fedHTTPClient(), req, expectFingerprint)
	if err != nil {
		return PingResult{Error: err.Error()}
	}
	defer resp.Body.Close()
	latency := time.Since(start).Milliseconds()
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 512))
		return PingResult{LatencyMs: latency, Error: fmt.Sprintf("remote returned %d: %s", resp.StatusCode, string(body))}
	}
	var pong struct {
		Fingerprint string `json:"fingerprint"`
		UnixTime    int64  `json:"unixTime"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&pong); err != nil {
		return PingResult{LatencyMs: latency, Error: "bad pong: " + err.Error()}
	}
	return PingResult{
		Reachable:        true,
		PeerFingerprint:  pong.Fingerprint,
		FingerprintMatch: pong.Fingerprint == expectFingerprint,
		PeerUnixTime:     pong.UnixTime,
		ClockSkewSeconds: time.Now().Unix() - pong.UnixTime,
		LatencyMs:        latency,
	}
}

func doFederationRequest(client *http.Client, req *http.Request, peerFingerprint string) (*http.Response, error) {
	if os.Getenv("SPELA_FEDERATION_DEBUG") != "1" {
		return client.Do(req)
	}

	start := time.Now()
	requestID := req.Header.Get(headerFedRequestID)
	query, queryTruncated := boundedFederationDebugValue(req.URL.RawQuery, federationDebugQueryMaxBytes)
	attrs := []any{
		"component", "federation",
		"request_id", requestID,
		"peer", federation.ShortFingerprint(peerFingerprint),
		"method", req.Method,
		"scheme", req.URL.Scheme,
		"host", req.URL.Host,
		"path", req.URL.Path,
		"query", query,
		"query_truncated", queryTruncated,
		"content_length", req.ContentLength,
		"signed", req.Header.Get(headerFedSignature) != "",
	}
	slog.Info("federation-http-request", attrs...)

	resp, err := client.Do(req)
	durationMs := time.Since(start).Milliseconds()
	if err != nil {
		slog.Warn("federation-http-response",
			append(attrs, "duration_ms", durationMs, "error", err.Error())...)
		return nil, err
	}
	slog.Info("federation-http-response",
		append(attrs,
			"duration_ms", durationMs,
			"status", resp.StatusCode,
			"response_content_length", resp.ContentLength,
			"response_content_type", resp.Header.Get("Content-Type"),
		)...)
	return resp, nil
}

func boundedFederationDebugValue(value string, maxBytes int) (string, bool) {
	if len(value) <= maxBytes {
		return value, false
	}
	return value[:maxBytes], true
}
