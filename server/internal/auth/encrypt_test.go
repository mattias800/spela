package auth

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestValidateEncryptionKey(t *testing.T) {
	tests := []struct {
		name    string
		keyLen  int
		wantErr bool
	}{
		{"valid 16 bytes (AES-128)", 16, false},
		{"valid 24 bytes (AES-192)", 24, false},
		{"valid 32 bytes (AES-256)", 32, false},
		{"invalid 0 bytes", 0, true},
		{"invalid 15 bytes", 15, true},
		{"invalid 20 bytes", 20, true},
		{"invalid 48 bytes", 48, true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			key := make([]byte, tt.keyLen)
			err := ValidateEncryptionKey(key)
			if tt.wantErr {
				require.Error(t, err)
				assert.Contains(t, err.Error(), "invalid AES key size")
			} else {
				assert.NoError(t, err)
			}
		})
	}
}

func TestEncrypt_InvalidKeySize(t *testing.T) {
	key := make([]byte, 48)
	_, err := Encrypt("test plaintext", key)
	require.Error(t, err)
	assert.Contains(t, err.Error(), "invalid key size")
}
