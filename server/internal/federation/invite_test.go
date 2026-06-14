package federation

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestInvite_EncodeDecodeVerify(t *testing.T) {
	id, _ := GenerateIdentity()
	exp := time.Unix(2_000_000_000, 0)

	inv := id.NewInvite("https://alice.example", "nonce-123", exp)
	encoded := EncodeInvite(inv)
	assert.NotEmpty(t, encoded)

	decoded, err := DecodeInvite(encoded)
	require.NoError(t, err)
	assert.Equal(t, id.Fingerprint(), decoded.Fingerprint)
	assert.Equal(t, "https://alice.example", decoded.BaseURL)
	assert.Equal(t, "nonce-123", decoded.Nonce)

	ok, err := VerifyInvite(decoded, time.Unix(1_900_000_000, 0))
	require.NoError(t, err)
	assert.True(t, ok)
}

func TestVerifyInvite_RejectsExpired(t *testing.T) {
	id, _ := GenerateIdentity()
	inv := id.NewInvite("https://a", "n", time.Unix(1000, 0))
	ok, err := VerifyInvite(inv, time.Unix(2000, 0))
	require.NoError(t, err)
	assert.False(t, ok, "expired invite must not verify")
}

func TestVerifyInvite_RejectsTamperedBaseURL(t *testing.T) {
	id, _ := GenerateIdentity()
	inv := id.NewInvite("https://a", "n", time.Unix(2_000_000_000, 0))
	inv.BaseURL = "https://evil"
	ok, err := VerifyInvite(inv, time.Unix(1000, 0))
	require.NoError(t, err)
	assert.False(t, ok)
}

func TestVerifyInvite_RejectsFingerprintKeyMismatch(t *testing.T) {
	id, _ := GenerateIdentity()
	other, _ := GenerateIdentity()
	inv := id.NewInvite("https://a", "n", time.Unix(2_000_000_000, 0))
	inv.Fingerprint = other.Fingerprint() // fingerprint no longer matches PublicKey
	ok, err := VerifyInvite(inv, time.Unix(1000, 0))
	require.NoError(t, err)
	assert.False(t, ok)
}
