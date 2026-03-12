package auth

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"fmt"
	"io"
	"strings"
)

// encryptionPrefix is prepended to ciphertext to identify encrypted values.
const encryptionPrefix = "enc:"

// DeriveEncryptionKey derives a 256-bit AES key from the JWT secret.
// This uses a SHA-256 hash to ensure consistent key length regardless of secret length.
//
// Migration path: set the SPELA_ENCRYPTION_KEY environment variable to use a
// separate key. When set, the router passes it directly to handlers instead of
// calling this function. Existing encrypted values remain decryptable because
// AES-GCM ciphertext is key-specific — re-encrypt data after rotating keys.
func DeriveEncryptionKey(jwtSecret string) []byte {
	h := sha256.Sum256([]byte("spela-encryption:" + jwtSecret))
	return h[:]
}

// ValidateEncryptionKey checks that key is a valid AES key size (16, 24, or 32 bytes).
func ValidateEncryptionKey(key []byte) error {
	switch len(key) {
	case 16, 24, 32:
		return nil
	default:
		return fmt.Errorf("invalid AES key size %d bytes; must be exactly 16, 24, or 32 bytes", len(key))
	}
}

// Encrypt encrypts plaintext using AES-GCM with the given key.
// Returns a base64-encoded string prefixed with "enc:" for identification.
func Encrypt(plaintext string, key []byte) (string, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return "", fmt.Errorf("creating cipher: %w", err)
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", fmt.Errorf("creating GCM: %w", err)
	}

	nonce := make([]byte, gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return "", fmt.Errorf("generating nonce: %w", err)
	}

	ciphertext := gcm.Seal(nonce, nonce, []byte(plaintext), nil)
	return encryptionPrefix + base64.StdEncoding.EncodeToString(ciphertext), nil
}

// Decrypt decrypts a value produced by Encrypt.
// If the value is not prefixed with "enc:", it is returned as-is (plaintext migration).
func Decrypt(encrypted string, key []byte) (string, error) {
	if !strings.HasPrefix(encrypted, encryptionPrefix) {
		// Not encrypted (legacy plaintext value) — return as-is
		return encrypted, nil
	}

	data, err := base64.StdEncoding.DecodeString(strings.TrimPrefix(encrypted, encryptionPrefix))
	if err != nil {
		return "", fmt.Errorf("decoding ciphertext: %w", err)
	}

	block, err := aes.NewCipher(key)
	if err != nil {
		return "", fmt.Errorf("creating cipher: %w", err)
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", fmt.Errorf("creating GCM: %w", err)
	}

	nonceSize := gcm.NonceSize()
	if len(data) < nonceSize {
		return "", fmt.Errorf("ciphertext too short")
	}

	nonce, ciphertext := data[:nonceSize], data[nonceSize:]
	plaintext, err := gcm.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		return "", fmt.Errorf("decrypting: %w", err)
	}

	return string(plaintext), nil
}
