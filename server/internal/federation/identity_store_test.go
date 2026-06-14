package federation

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestLoadOrCreateIdentity_CreatesThenReloadsSameKey(t *testing.T) {
	database := openFedTestDB(t)

	first, err := LoadOrCreateIdentity(database, testAESKey)
	require.NoError(t, err)
	assert.NotEmpty(t, first.Fingerprint())

	// A second call must return the SAME identity, not generate a new one.
	second, err := LoadOrCreateIdentity(database, testAESKey)
	require.NoError(t, err)
	assert.Equal(t, first.Fingerprint(), second.Fingerprint())
	assert.Equal(t, []byte(first.PrivateKey), []byte(second.PrivateKey))
}

func TestLoadOrCreateIdentity_PrivateKeyEncryptedAtRest(t *testing.T) {
	database := openFedTestDB(t)
	_, err := LoadOrCreateIdentity(database, testAESKey)
	require.NoError(t, err)

	stored, err := readSetting(database, settingKeyPrivateKey)
	require.NoError(t, err)
	// auth.Encrypt prefixes ciphertext with "enc:". The raw stored value must
	// not be the plaintext base64 key.
	assert.True(t, len(stored) > 4 && stored[:4] == "enc:", "private key must be stored encrypted")
}
