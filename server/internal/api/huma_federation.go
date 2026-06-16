package api

import (
	"context"
	"crypto/ed25519"
	"encoding/base64"
	"fmt"
	"log/slog"
	"sync"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"gorm.io/gorm"
)

// FederationHandler serves the pairing bootstrap + ping endpoints and the admin
// federation endpoints (epic #1343).
type FederationHandler struct {
	DB       *gorm.DB
	Identity federation.Identity
	Peers    federation.PeerStore
	// Snapshots caches friends' rollups for transitive re-serving (#1347).
	Snapshots federation.SnapshotStore
	// CatalogSnapshots caches friends' game catalogs for discovery (#1348).
	CatalogSnapshots federation.CatalogSnapshotStore
	BaseURL          string // this server's own reachable federation endpoint
	// PairClient performs the outbound pairing callback to a friend. Defaults to
	// httpPairClient when nil; overridden in tests.
	PairClient pairClient
	// Pinger performs the outbound connection-test ping. Defaults to
	// httpPeerPinger when nil; overridden in tests.
	Pinger peerPinger
	// StatsClient fetches a friend's stat rollup. Defaults to httpStatsClient
	// when nil; overridden in tests.
	StatsClient statsClient
	// CatalogClient fetches a friend's catalog. Defaults to httpCatalogClient
	// when nil; overridden in tests.
	CatalogClient catalogClient
	// CoverResolver resolves cover art for a catalog key (consumer-side, via
	// this server's own IGDB client). Nil = no covers (placeholder in the UI).
	CoverResolver coverResolver
	// Imports runs game imports from connected servers (#1350). Nil disables
	// the import endpoints (e.g. when no scraper/queue is configured).
	Imports *ImportService
	// GameDirs / JWTSecret support direct-friend ROM download (#1348 Phase 3b):
	// GameDirs resolves a local game file to serve; JWTSecret authenticates the
	// user-facing download route.
	GameDirs  []string
	JWTSecret string
	// DownloadClient fetches a ROM from a friend. Defaults to httpDownloadClient
	// when nil; overridden in tests.
	DownloadClient downloadClient
	// refreshMu / catalogRefreshMu serialize the respective refreshes so the
	// periodic ticker and an admin trigger can't race the snapshot stores.
	refreshMu        sync.Mutex
	catalogRefreshMu sync.Mutex
}

// pairClient performs the outbound pairing callback to a friend.
type pairClient interface {
	Pair(baseURL string, body PairRequestBody) (PairResponseBody, error)
}

// PairRequestBody is a peer's signed pairing bundle.
type PairRequestBody struct {
	Fingerprint string `json:"fingerprint"`
	PublicKey   string `json:"publicKey"` // base64 std ed25519 public key
	BaseURL     string `json:"baseUrl"`
	Nonce       string `json:"nonce"` // echoes the invite nonce we issued
	Signature   string `json:"sig"`   // base64 std signature over pairBundleBytes
}

// PairResponseBody is our own bundle, returned so the caller can mark us active.
type PairResponseBody struct {
	Fingerprint string `json:"fingerprint"`
	PublicKey   string `json:"publicKey"`
	BaseURL     string `json:"baseUrl"`
	Status      string `json:"status"`
}

// PairInput / PairOutput are the huma request/response envelopes.
type PairInput struct {
	RequestID string `header:"X-Spela-Request-Id"`
	Body      PairRequestBody
}
type PairOutput struct {
	Body PairResponseBody
}

// pairBundleBytes is the canonical signed payload of a pairing bundle.
func pairBundleBytes(fingerprint, publicKey, baseURL, nonce string) []byte {
	return []byte(fmt.Sprintf("spela-pair\nfp=%s\npk=%s\nurl=%s\nnonce=%s",
		fingerprint, publicKey, baseURL, nonce))
}

// HumaPair handles POST /api/federation/pair: a peer that holds an invite we
// issued calls us back with its signed bundle. We verify and store it active,
// recording the interaction in the exchange ledger (#1350).
func (h *FederationHandler) HumaPair(_ context.Context, in *PairInput) (*PairOutput, error) {
	started := time.Now()
	b := in.Body
	reqID := in.RequestID
	if reqID == "" {
		reqID = federation.NewRequestID()
	}

	// Rejections are logged, not written to the ledger: /api/federation/pair is
	// public, so recording a row per failed attempt would let an attacker flood
	// the exchange table. The slog line keeps failures diagnosable during
	// testing (the operator initiating accept also sees the HTTP error).
	fail := func(httpErr error) error {
		slog.Warn("federation: rejected inbound pairing", "component", "federation",
			"request_id", reqID, "peer", federation.ShortFingerprint(b.Fingerprint),
			"error", httpErr.Error())
		return httpErr
	}

	// 1. Verify the bundle signature and fingerprint<->key binding.
	pub, err := base64.StdEncoding.DecodeString(b.PublicKey)
	if err != nil || len(pub) != ed25519.PublicKeySize {
		return nil, fail(huma.Error400BadRequest("invalid public key"))
	}
	if federation.Fingerprint(pub) != b.Fingerprint {
		return nil, fail(huma.Error400BadRequest("fingerprint does not match public key"))
	}
	sig, err := base64.StdEncoding.DecodeString(b.Signature)
	if err != nil {
		return nil, fail(huma.Error400BadRequest("invalid signature encoding"))
	}
	if !federation.Verify(pub, pairBundleBytes(b.Fingerprint, b.PublicKey, b.BaseURL, b.Nonce), sig) {
		return nil, fail(huma.Error401Unauthorized("bundle signature verification failed"))
	}

	// 2. Consume the nonce: it must be one we issued, unexpired, unused.
	if err := h.consumeNonce(b.Nonce); err != nil {
		return nil, fail(huma.Error401Unauthorized("invalid or already-used pairing nonce"))
	}

	// 3. Store the peer as active.
	if err := h.Peers.Upsert(&db.FederationPeer{
		Fingerprint: b.Fingerprint,
		PublicKey:   b.PublicKey,
		BaseURL:     b.BaseURL,
		Status:      db.PeerStatusActive,
	}); err != nil {
		return nil, fail(huma.Error500InternalServerError("failed to store peer"))
	}

	federation.RecordExchange(h.DB, federation.ExchangeRecord{
		RequestID: reqID, PeerFingerprint: b.Fingerprint, Direction: db.ExchangeInbound,
		Operation: "pair", Status: db.ExchangeOK, StartedAt: started,
	})

	return &PairOutput{Body: PairResponseBody{
		Fingerprint: h.Identity.Fingerprint(),
		PublicKey:   base64.StdEncoding.EncodeToString(h.Identity.PublicKey),
		BaseURL:     h.BaseURL,
		Status:      db.PeerStatusActive,
	}}, nil
}

// consumeNonce atomically marks a valid, unexpired, unused nonce as used. The
// single conditional UPDATE (rather than SELECT-then-UPDATE) is race-safe:
// exactly one of two concurrent callers with the same nonce sees RowsAffected==1.
func (h *FederationHandler) consumeNonce(nonce string) error {
	res := h.DB.Model(&db.FederationInviteNonce{}).
		Where("nonce = ? AND used = ? AND expires_at > ?", nonce, false, time.Now()).
		Update("used", true)
	if res.Error != nil {
		return res.Error
	}
	if res.RowsAffected == 0 {
		return fmt.Errorf("nonce not found, already used, or expired")
	}
	return nil
}
