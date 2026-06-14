package federation

import (
	"crypto/ed25519"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"time"
)

// Invite is a signed pairing offer. The operator transmits the encoded form
// out-of-band (paste/QR/link) to the friend they want to pair with.
type Invite struct {
	Fingerprint string `json:"fingerprint"`
	PublicKey   string `json:"publicKey"` // base64 std of ed25519 public key
	BaseURL     string `json:"baseUrl"`
	Nonce       string `json:"nonce"`
	ExpiresUnix int64  `json:"expiresUnix"`
	Signature   string `json:"sig"` // base64 std of signature over canonical bytes
}

// canonicalInviteBytes is the deterministic byte string that gets signed. Field
// order and separators are fixed so signing and verification agree.
func canonicalInviteBytes(fingerprint, publicKey, baseURL, nonce string, expiresUnix int64) []byte {
	return []byte(fmt.Sprintf("spela-invite\nfp=%s\npk=%s\nurl=%s\nnonce=%s\nexp=%d",
		fingerprint, publicKey, baseURL, nonce, expiresUnix))
}

// NewInvite builds and signs an invite from this identity.
func (id Identity) NewInvite(baseURL, nonce string, expiresAt time.Time) Invite {
	pubB64 := base64.StdEncoding.EncodeToString(id.PublicKey)
	fp := id.Fingerprint()
	exp := expiresAt.Unix()
	sig := id.Sign(canonicalInviteBytes(fp, pubB64, baseURL, nonce, exp))
	return Invite{
		Fingerprint: fp,
		PublicKey:   pubB64,
		BaseURL:     baseURL,
		Nonce:       nonce,
		ExpiresUnix: exp,
		Signature:   base64.StdEncoding.EncodeToString(sig),
	}
}

// EncodeInvite serializes an invite to a single base64 string for copy-paste.
func EncodeInvite(inv Invite) string {
	b, _ := json.Marshal(inv)
	return base64.StdEncoding.EncodeToString(b)
}

// DecodeInvite parses an encoded invite string.
func DecodeInvite(s string) (Invite, error) {
	raw, err := base64.StdEncoding.DecodeString(s)
	if err != nil {
		return Invite{}, fmt.Errorf("decoding invite: %w", err)
	}
	var inv Invite
	if err := json.Unmarshal(raw, &inv); err != nil {
		return Invite{}, fmt.Errorf("parsing invite: %w", err)
	}
	return inv, nil
}

// VerifyInvite checks the invite's signature, fingerprint<->key binding, and
// expiry (against now). It does NOT check the nonce against any local store —
// that is the receiver's job during pairing.
func VerifyInvite(inv Invite, now time.Time) (bool, error) {
	pub, err := base64.StdEncoding.DecodeString(inv.PublicKey)
	if err != nil {
		return false, fmt.Errorf("decoding public key: %w", err)
	}
	if len(pub) != ed25519.PublicKeySize {
		return false, nil
	}
	if Fingerprint(pub) != inv.Fingerprint {
		return false, nil // fingerprint must be derived from the embedded key
	}
	if now.Unix() > inv.ExpiresUnix {
		return false, nil
	}
	sig, err := base64.StdEncoding.DecodeString(inv.Signature)
	if err != nil {
		return false, fmt.Errorf("decoding signature: %w", err)
	}
	msg := canonicalInviteBytes(inv.Fingerprint, inv.PublicKey, inv.BaseURL, inv.Nonce, inv.ExpiresUnix)
	return Verify(pub, msg, sig), nil
}
