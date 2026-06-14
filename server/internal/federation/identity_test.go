package federation

import (
	"crypto/ed25519"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestGenerateIdentity_ProducesUsableKeypair(t *testing.T) {
	id, err := GenerateIdentity()
	require.NoError(t, err)
	assert.Len(t, id.PublicKey, ed25519.PublicKeySize)
	assert.Len(t, id.PrivateKey, ed25519.PrivateKeySize)
}

func TestFingerprint_StableAndDeterministic(t *testing.T) {
	id, err := GenerateIdentity()
	require.NoError(t, err)

	fp1 := id.Fingerprint()
	fp2 := Fingerprint(id.PublicKey)

	assert.Equal(t, fp1, fp2, "fingerprint must be deterministic for the same key")
	assert.NotEmpty(t, fp1)
	// base32 (no padding) of a 32-byte digest is 52 lowercase chars.
	assert.Len(t, fp1, 52)
}

func TestFingerprint_DiffersAcrossKeys(t *testing.T) {
	a, _ := GenerateIdentity()
	b, _ := GenerateIdentity()
	assert.NotEqual(t, a.Fingerprint(), b.Fingerprint())
}

func TestShortFingerprint(t *testing.T) {
	id, _ := GenerateIdentity()
	short := ShortFingerprint(id.Fingerprint())
	assert.Len(t, short, 10)
	assert.Equal(t, "short", ShortFingerprint("short"))
}
