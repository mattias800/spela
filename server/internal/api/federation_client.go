package api

import (
	"bytes"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"time"

	"github.com/spela/server/internal/federation"
)

// federationHTTPTimeout bounds outbound federation calls.
const federationHTTPTimeout = 15 * time.Second

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

func (httpPairClient) Pair(baseURL string, body PairRequestBody) (PairResponseBody, error) {
	reqBody, _ := json.Marshal(body)
	req, err := http.NewRequest(http.MethodPost, baseURL+"/api/federation/pair", bytes.NewReader(reqBody))
	if err != nil {
		return PairResponseBody{}, err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := fedHTTPClient().Do(req)
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
	resp, err := fedHTTPClient().Do(req)
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
