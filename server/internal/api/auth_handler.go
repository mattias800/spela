package api

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// generateTokenFamily creates a random token family ID for refresh token replay detection.
func generateTokenFamily() string {
	b := make([]byte, 16)
	rand.Read(b)
	return hex.EncodeToString(b)
}

const (
	maxLoginAttempts = 5

	// dummyBcryptHash is a pre-computed bcrypt hash used when a login attempt
	// targets a non-existent username. Running bcrypt.CompareHashAndPassword
	// against this hash ensures the response time is indistinguishable from a
	// real password check, preventing timing-based username enumeration.
	dummyBcryptHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
)

// hashUsername returns a SHA-256 hex digest of the username so raw usernames
// are never stored in the login_attempts table.
func hashUsername(username string) string {
	h := sha256.Sum256([]byte(username))
	return hex.EncodeToString(h[:])
}

// lockoutDuration returns an escalating lockout window based on the number of
// failed login attempts.
func lockoutDuration(failedCount int) time.Duration {
	switch {
	case failedCount >= 20:
		return 120 * time.Minute
	case failedCount >= 15:
		return 60 * time.Minute
	case failedCount >= 10:
		return 30 * time.Minute
	default:
		return 15 * time.Minute
	}
}

// loginAttemptMaxAge is the maximum time a failed login counter persists.
// After this duration without new failures, the counter is fully reset to
// prevent indefinite escalation for accounts with occasional typos.
const loginAttemptMaxAge = 24 * time.Hour

// isLockedOut checks whether the account is currently locked in the database.
func (h *AuthHandler) isLockedOut(username string) bool {
	hashed := hashUsername(username)
	var attempt db.LoginAttempt
	if err := h.DB.Where("username = ?", hashed).First(&attempt).Error; err != nil {
		return false
	}
	// Reset counter if the last failure was more than 24 hours ago
	if time.Since(attempt.UpdatedAt) > loginAttemptMaxAge {
		h.DB.Where("username = ?", hashed).Updates(map[string]interface{}{
			"failed_count": 0,
			"locked_until": time.Time{},
		})
		return false
	}
	if attempt.FailedCount < maxLoginAttempts {
		return false
	}
	if time.Now().After(attempt.LockedUntil) {
		// Lockout expired — do NOT reset the counter so subsequent failures
		// escalate the lockout duration (up to loginAttemptMaxAge).
		return false
	}
	return true
}

// recordFailedLogin atomically increments the failed login counter and locks the
// account if the threshold is reached. The transaction prevents concurrent login
// attempts from bypassing the lockout limit.
func (h *AuthHandler) recordFailedLogin(username string) {
	hashed := hashUsername(username)
	h.DB.Transaction(func(tx *gorm.DB) error {
		var attempt db.LoginAttempt
		result := tx.Where("username = ?", hashed).First(&attempt)
		if result.Error != nil {
			attempt = db.LoginAttempt{Username: hashed, FailedCount: 1}
			return tx.Create(&attempt).Error
		}
		attempt.FailedCount++
		if attempt.FailedCount >= maxLoginAttempts {
			attempt.LockedUntil = time.Now().Add(lockoutDuration(attempt.FailedCount))
		}
		return tx.Save(&attempt).Error
	})
}

// clearFailedLogins resets the failed login counter on successful login.
func (h *AuthHandler) clearFailedLogins(username string) {
	hashed := hashUsername(username)
	h.DB.Where("username = ?", hashed).Updates(map[string]interface{}{"failed_count": 0, "locked_until": time.Time{}})
}

// AuthHandler handles authentication endpoints.
type AuthHandler struct {
	DB        *gorm.DB
	JWTSecret string
}

// Login, Register, Refresh, Setup, and SetupStatus have been migrated to huma
// and now live in huma_auth_handlers.go. The gin versions were retained in
// previous releases as a rollback path; they have no remaining callers and
// have been removed.

// systemEventRetention is how long admin system event rows are kept.
// Older rows are pruned by StartTokenCleanup.
const systemEventRetention = 90 * 24 * time.Hour

// pruneExpiredSystemEvents deletes system_events rows older than the
// retention window. Extracted from StartTokenCleanup so it can be exercised
// by unit tests without starting the background goroutine.
func pruneExpiredSystemEvents(database *gorm.DB) int64 {
	result := database.Where("created_at < ?", time.Now().Add(-systemEventRetention)).Delete(&db.SystemEvent{})
	return result.RowsAffected
}

// StartTokenCleanup starts a background goroutine that periodically deletes
// expired refresh tokens from the database to prevent unbounded growth.
func StartTokenCleanup(database *gorm.DB, interval time.Duration) {
	go func() {
		for {
			time.Sleep(interval)
			// Delete expired tokens and consumed tokens older than 7 days
			// (consumed tokens are kept briefly for replay detection)
			database.Where("consumed = ? AND created_at < ?", true, time.Now().Add(-7*24*time.Hour)).Delete(&db.RefreshToken{})
			result := database.Where("expires_at < ?", time.Now()).Delete(&db.RefreshToken{})
			if result.RowsAffected > 0 {
				slog.Info("cleaned up expired refresh tokens", "count", result.RowsAffected)
			}
			// Clean up stale login attempt entries (lockout expired and counter reset)
			laResult := database.Where("locked_until < ? AND failed_count = 0", time.Now()).Delete(&db.LoginAttempt{})
			if laResult.RowsAffected > 0 {
				slog.Info("cleaned up stale login attempts", "count", laResult.RowsAffected)
			}
			// Clean up expired token blacklist entries
			blResult := database.Where("expires_at < ?", time.Now()).Delete(&db.TokenBlacklist{})
			if blResult.RowsAffected > 0 {
				slog.Info("cleaned up expired blacklist entries", "count", blResult.RowsAffected)
			}
			// Prune security events older than the retention window
			if count := pruneExpiredSystemEvents(database); count > 0 {
				slog.Info("pruned old system events", "count", count)
			}
		}
	}()
}

// Logout revokes the current access token and deletes the user's refresh tokens.
func (h *AuthHandler) Logout(c *gin.Context) {
	// Extract the raw access token to blacklist it
	var token string
	header := c.GetHeader("Authorization")
	if header != "" {
		parts := strings.SplitN(header, " ", 2)
		if len(parts) == 2 && parts[0] == "Bearer" {
			token = parts[1]
		}
	}
	if token == "" {
		token = c.Query("token")
	}

	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)

	// Blacklist the access token so it cannot be reused for its remaining lifetime
	if token != "" {
		claims, err := auth.ValidateAccessToken(token, h.JWTSecret)
		if err == nil && claims.ExpiresAt != nil {
			hash := sha256.Sum256([]byte(token))
			bl := db.TokenBlacklist{
				TokenHash: hex.EncodeToString(hash[:]),
				ExpiresAt: claims.ExpiresAt.Time,
			}
			h.DB.Create(&bl)
		}
	}

	// Delete all refresh tokens for this user (log out everywhere)
	h.DB.Where("user_id = ?", uid).Delete(&db.RefreshToken{})

	c.JSON(http.StatusOK, gin.H{"message": "logged out"})
}

// IsTokenBlacklisted checks if a JWT access token has been revoked.
//
// Uses an existence-style query (SELECT id ... LIMIT 1) instead of COUNT(*),
// because COUNT scans all matching rows even when only existence is needed.
// On the indexed token_hash column the query short-circuits at the first
// match, keeping middleware overhead minimal even on large blacklist tables.
func IsTokenBlacklisted(database *gorm.DB, token string) bool {
	hash := sha256.Sum256([]byte(token))
	var bl db.TokenBlacklist
	err := database.Select("id").
		Where("token_hash = ?", hex.EncodeToString(hash[:])).
		Limit(1).
		Take(&bl).Error
	return err == nil
}

