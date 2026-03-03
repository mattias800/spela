package auth

import (
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestHashPassword(t *testing.T) {
	hash, err := HashPassword("testpassword123")
	require.NoError(t, err)
	assert.NotEmpty(t, hash)
	assert.NotEqual(t, "testpassword123", hash)
}

func TestCheckPassword(t *testing.T) {
	hash, err := HashPassword("testpassword123")
	require.NoError(t, err)

	tests := []struct {
		name     string
		password string
		want     bool
	}{
		{"correct password", "testpassword123", true},
		{"wrong password", "wrongpassword", false},
		{"empty password", "", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := CheckPassword(tt.password, hash)
			assert.Equal(t, tt.want, result)
		})
	}
}

func TestHashPassword_TooLong(t *testing.T) {
	longPass := strings.Repeat("a", 73)
	_, err := HashPassword(longPass)
	assert.Error(t, err, "passwords exceeding 72 bytes should be rejected")

	exactPass := strings.Repeat("a", 72)
	_, err = HashPassword(exactPass)
	assert.NoError(t, err, "72-byte password should be accepted")
}

func TestGenerateAndValidateAccessToken(t *testing.T) {
	secret := "test-secret-key"
	token, err := GenerateAccessToken(1, "testuser", "admin", secret)
	require.NoError(t, err)
	assert.NotEmpty(t, token)

	claims, err := ValidateAccessToken(token, secret)
	require.NoError(t, err)
	assert.Equal(t, uint(1), claims.UserID)
	assert.Equal(t, "testuser", claims.Username)
	assert.Equal(t, "admin", claims.Role)
	assert.Equal(t, "spela", claims.Issuer)
}

func TestValidateAccessToken_InvalidSecret(t *testing.T) {
	token, err := GenerateAccessToken(1, "testuser", "user", "secret1")
	require.NoError(t, err)

	_, err = ValidateAccessToken(token, "secret2")
	assert.Error(t, err)
}

func TestValidateAccessToken_InvalidToken(t *testing.T) {
	_, err := ValidateAccessToken("not.a.valid.token", "secret")
	assert.Error(t, err)
}

func TestTokenDurations(t *testing.T) {
	assert.Equal(t, 1*time.Hour, AccessTokenDuration, "access token should last 1 hour")
	assert.Equal(t, 90*24*time.Hour, RefreshTokenDuration, "refresh token should last 90 days")
}

func TestGenerateRefreshToken(t *testing.T) {
	token1, err := GenerateRefreshToken()
	require.NoError(t, err)
	assert.Len(t, token1, 64) // 32 bytes = 64 hex chars

	token2, err := GenerateRefreshToken()
	require.NoError(t, err)
	assert.NotEqual(t, token1, token2, "refresh tokens should be unique")
}
