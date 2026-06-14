package federation

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestSignVerify_RoundTrip(t *testing.T) {
	id, err := GenerateIdentity()
	require.NoError(t, err)

	msg := []byte("federation: hello")
	sig := id.Sign(msg)

	assert.True(t, Verify(id.PublicKey, msg, sig), "valid signature must verify")
}

func TestVerify_RejectsTamperedMessage(t *testing.T) {
	id, _ := GenerateIdentity()
	sig := id.Sign([]byte("original"))
	assert.False(t, Verify(id.PublicKey, []byte("tampered"), sig))
}

func TestVerify_RejectsWrongKey(t *testing.T) {
	a, _ := GenerateIdentity()
	b, _ := GenerateIdentity()
	sig := a.Sign([]byte("msg"))
	assert.False(t, Verify(b.PublicKey, []byte("msg"), sig))
}
