package api

import (
	"context"
	"encoding/base64"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// signedBundle builds a peer's pairing bundle echoing the given nonce.
func signedBundle(id federation.Identity, baseURL, nonce string) PairRequestBody {
	pubB64 := base64.StdEncoding.EncodeToString(id.PublicKey)
	fp := id.Fingerprint()
	sig := id.Sign(pairBundleBytes(fp, pubB64, baseURL, nonce))
	return PairRequestBody{
		Fingerprint: fp,
		PublicKey:   pubB64,
		BaseURL:     baseURL,
		Nonce:       nonce,
		Signature:   base64.StdEncoding.EncodeToString(sig),
	}
}

func TestPair_StoresPeerActive_OnValidBundleAndNonce(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	h := &FederationHandler{DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database}, BaseURL: "https://self"}

	// A (self) issued an invite with this nonce.
	require.NoError(t, database.Create(&db.FederationInviteNonce{
		Nonce: "n-good", ExpiresAt: time.Now().Add(time.Hour),
	}).Error)

	caller, _ := federation.GenerateIdentity() // B
	body := signedBundle(caller, "https://b.example", "n-good")

	out, err := h.HumaPair(context.Background(), &PairInput{Body: body})
	require.NoError(t, err)
	assert.Equal(t, selfID.Fingerprint(), out.Body.Fingerprint)
	assert.Equal(t, db.PeerStatusActive, out.Body.Status)

	stored, err := federation.PeerStore{DB: database}.GetByFingerprint(caller.Fingerprint())
	require.NoError(t, err)
	assert.Equal(t, db.PeerStatusActive, stored.Status)

	// An exchange-ledger row was written for the pairing.
	var exchanges int64
	database.Model(&db.FederationExchange{}).Where("operation = ? AND status = ?", "pair", db.ExchangeOK).Count(&exchanges)
	assert.Equal(t, int64(1), exchanges)

	// Nonce is now consumed: a replay must fail.
	_, err = h.HumaPair(context.Background(), &PairInput{Body: body})
	assert.Error(t, err)
}

func TestPair_RejectsUnknownNonce(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	h := &FederationHandler{DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database}, BaseURL: "https://self"}

	caller, _ := federation.GenerateIdentity()
	body := signedBundle(caller, "https://b.example", "n-never-issued")

	_, err := h.HumaPair(context.Background(), &PairInput{Body: body})
	assert.Error(t, err, "pairing without a matching issued nonce must be rejected")

	// Rejections on the public pair endpoint are logged, NOT written to the
	// ledger (anti-flood) — no exchange row should exist.
	var rows int64
	database.Model(&db.FederationExchange{}).Count(&rows)
	assert.Equal(t, int64(0), rows)
}

func TestPair_RejectsBadSignature(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	h := &FederationHandler{DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database}, BaseURL: "https://self"}
	require.NoError(t, database.Create(&db.FederationInviteNonce{
		Nonce: "n", ExpiresAt: time.Now().Add(time.Hour),
	}).Error)

	caller, _ := federation.GenerateIdentity()
	body := signedBundle(caller, "https://b.example", "n")
	body.Signature = base64.StdEncoding.EncodeToString([]byte("not-a-valid-ed25519-signature-of-the-right-length-000000000000000"))

	_, err := h.HumaPair(context.Background(), &PairInput{Body: body})
	assert.Error(t, err)
}

func TestPair_RejectsFingerprintKeyMismatch(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	h := &FederationHandler{DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database}, BaseURL: "https://self"}
	require.NoError(t, database.Create(&db.FederationInviteNonce{
		Nonce: "n", ExpiresAt: time.Now().Add(time.Hour),
	}).Error)

	caller, _ := federation.GenerateIdentity()
	other, _ := federation.GenerateIdentity()
	body := signedBundle(caller, "https://b.example", "n")
	body.Fingerprint = other.Fingerprint() // no longer matches PublicKey

	_, err := h.HumaPair(context.Background(), &PairInput{Body: body})
	assert.Error(t, err)
}
