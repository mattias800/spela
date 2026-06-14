package api

import (
	"context"
	"crypto/ed25519"
	"encoding/base64"
	"errors"
	"fmt"
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
	BaseURL  string // this server's own reachable federation endpoint
	// PairClient performs the outbound pairing callback to a friend. Defaults to
	// httpPairClient when nil; overridden in tests.
	PairClient pairClient
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

	fail := func(status string, httpErr error) error {
		federation.RecordExchange(h.DB, federation.ExchangeRecord{
			RequestID: reqID, PeerFingerprint: b.Fingerprint, Direction: db.ExchangeInbound,
			Operation: "pair", Status: status, StartedAt: started, Error: httpErr.Error(),
		})
		return httpErr
	}

	// 1. Verify the bundle signature and fingerprint<->key binding.
	pub, err := base64.StdEncoding.DecodeString(b.PublicKey)
	if err != nil || len(pub) != ed25519.PublicKeySize {
		return nil, fail(db.ExchangeRejected, huma.Error400BadRequest("invalid public key"))
	}
	if federation.Fingerprint(pub) != b.Fingerprint {
		return nil, fail(db.ExchangeRejected, huma.Error400BadRequest("fingerprint does not match public key"))
	}
	sig, err := base64.StdEncoding.DecodeString(b.Signature)
	if err != nil {
		return nil, fail(db.ExchangeRejected, huma.Error400BadRequest("invalid signature encoding"))
	}
	if !federation.Verify(pub, pairBundleBytes(b.Fingerprint, b.PublicKey, b.BaseURL, b.Nonce), sig) {
		return nil, fail(db.ExchangeRejected, huma.Error401Unauthorized("bundle signature verification failed"))
	}

	// 2. Consume the nonce: it must be one we issued, unexpired, unused.
	if err := h.consumeNonce(b.Nonce); err != nil {
		return nil, fail(db.ExchangeRejected, huma.Error401Unauthorized("invalid or already-used pairing nonce"))
	}

	// 3. Store the peer as active.
	if err := h.Peers.Upsert(&db.FederationPeer{
		Fingerprint: b.Fingerprint,
		PublicKey:   b.PublicKey,
		BaseURL:     b.BaseURL,
		Status:      db.PeerStatusActive,
	}); err != nil {
		return nil, fail(db.ExchangeError, huma.Error500InternalServerError("failed to store peer"))
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

// consumeNonce atomically marks a valid, unexpired, unused nonce as used.
func (h *FederationHandler) consumeNonce(nonce string) error {
	return h.DB.Transaction(func(tx *gorm.DB) error {
		var n db.FederationInviteNonce
		if err := tx.Where("nonce = ?", nonce).First(&n).Error; err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				return fmt.Errorf("nonce not found")
			}
			return err
		}
		if n.Used || time.Now().After(n.ExpiresAt) {
			return fmt.Errorf("nonce used or expired")
		}
		return tx.Model(&n).Update("used", true).Error
	})
}
