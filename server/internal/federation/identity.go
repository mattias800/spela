package federation

import (
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base32"
	"fmt"
	"strings"
)

// Identity is this server's federation identity. The public key's fingerprint
// is the stable, anonymous origin ID used across the mesh; the private key
// signs invites and federation requests.
type Identity struct {
	PrivateKey ed25519.PrivateKey
	PublicKey  ed25519.PublicKey
}

// GenerateIdentity creates a fresh Ed25519 identity.
func GenerateIdentity() (Identity, error) {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return Identity{}, fmt.Errorf("generating ed25519 key: %w", err)
	}
	return Identity{PrivateKey: priv, PublicKey: pub}, nil
}

// fingerprintEncoding is lowercase base32 without padding — URL/header safe and
// case-insensitive friendly.
var fingerprintEncoding = base32.StdEncoding.WithPadding(base32.NoPadding)

// Fingerprint returns the stable, anonymous origin ID for a public key:
// lowercase base32 of SHA-256(pubkey). Recognizable, not contactable.
func Fingerprint(pub ed25519.PublicKey) string {
	sum := sha256.Sum256(pub)
	return strings.ToLower(fingerprintEncoding.EncodeToString(sum[:]))
}

// Fingerprint returns the identity's own fingerprint.
func (id Identity) Fingerprint() string { return Fingerprint(id.PublicKey) }

// ShortFingerprint returns a truncated fingerprint for log lines.
func ShortFingerprint(fp string) string {
	if len(fp) <= 10 {
		return fp
	}
	return fp[:10]
}
