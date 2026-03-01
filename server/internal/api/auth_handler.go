package api

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
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

type loginRequest struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required,max=72"`
}

type registerRequest struct {
	Username string `json:"username" binding:"required,min=3,max=64,alphanum"`
	Email    string `json:"email" binding:"required,email"`
	Password string `json:"password" binding:"required,min=8,max=72"`
}

type refreshRequest struct {
	RefreshToken string `json:"refreshToken" binding:"required"`
}

type authResponse struct {
	AccessToken  string       `json:"accessToken"`
	RefreshToken string       `json:"refreshToken"`
	User         UserResponse `json:"user"`
}

// Login authenticates a user and returns tokens.
func (h *AuthHandler) Login(c *gin.Context) {
	var req loginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	// Check account lockout before proceeding
	if h.isLockedOut(req.Username) {
		c.JSON(http.StatusTooManyRequests, gin.H{"error": "account temporarily locked due to too many failed login attempts"})
		return
	}

	var user db.User
	userFound := h.DB.Where("username = ?", req.Username).First(&user).Error == nil

	if !userFound {
		// Run bcrypt against a dummy hash to prevent timing-based username enumeration.
		// Without this, an attacker can distinguish "user not found" (fast) from
		// "wrong password" (slow bcrypt) by measuring response time.
		auth.CheckPassword(req.Password, dummyBcryptHash)
		h.recordFailedLogin(req.Username)
		c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid credentials"})
		return
	}

	if !auth.CheckPassword(req.Password, user.PasswordHash) {
		h.recordFailedLogin(req.Username)
		c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid credentials"})
		return
	}

	if user.Disabled {
		c.JSON(http.StatusForbidden, gin.H{"error": "account is disabled"})
		return
	}

	// Successful login — clear any failed attempts
	h.clearFailedLogins(req.Username)

	accessToken, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, h.JWTSecret, user.TokenVersion)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to generate token"})
		return
	}

	refreshToken, err := auth.GenerateRefreshToken()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to generate refresh token"})
		return
	}

	// Store hashed refresh token with a new token family for replay detection.
	// The raw token is returned to the client; only the hash is persisted.
	rt := db.RefreshToken{
		UserID:      user.ID,
		Token:       auth.HashRefreshToken(refreshToken),
		ExpiresAt:   time.Now().Add(auth.RefreshTokenDuration),
		TokenFamily: generateTokenFamily(),
	}
	h.DB.Create(&rt)

	c.JSON(http.StatusOK, authResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		User:         ToUserResponse(user),
	})
}

// Register creates a new user account.
func (h *AuthHandler) Register(c *gin.Context) {
	var req registerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to hash password"})
		return
	}

	// Use a transaction to atomically check registration eligibility, determine
	// the role (first user becomes owner), and create the user. This prevents a
	// TOCTOU race where two simultaneous registrations could both see userCount=0
	// and both get the owner role.
	var user db.User
	if err := h.DB.Transaction(func(tx *gorm.DB) error {
		// Check if username or email already exists
		var count int64
		tx.Model(&db.User{}).Where("username = ? OR email = ?", req.Username, req.Email).Count(&count)
		if count > 0 {
			c.JSON(http.StatusConflict, gin.H{"error": "username or email already exists"})
			return fmt.Errorf("duplicate")
		}

		// Check if registration is enabled (skip for first user)
		var totalUsers int64
		tx.Model(&db.User{}).Count(&totalUsers)
		if totalUsers > 0 {
			var setting db.ServerSetting
			if err := tx.Where("key = ?", "registration_enabled").First(&setting).Error; err == nil {
				if setting.Value == "false" {
					c.JSON(http.StatusForbidden, gin.H{"error": "registration is disabled"})
					return fmt.Errorf("disabled")
				}
			}
		}

		// First user becomes owner — determined atomically inside the transaction
		role := db.RoleUser
		if totalUsers == 0 {
			role = db.RoleOwner
		}

		user = db.User{
			Username:     req.Username,
			Email:        req.Email,
			PasswordHash: hash,
			Role:         role,
		}

		return tx.Create(&user).Error
	}); err != nil {
		// If the transaction callback already sent an HTTP response, don't double-respond
		if c.Writer.Written() {
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create user"})
		return
	}

	accessToken, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, h.JWTSecret)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to generate token"})
		return
	}

	refreshToken, err := auth.GenerateRefreshToken()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to generate refresh token"})
		return
	}

	rt := db.RefreshToken{
		UserID:      user.ID,
		Token:       auth.HashRefreshToken(refreshToken),
		ExpiresAt:   time.Now().Add(auth.RefreshTokenDuration),
		TokenFamily: generateTokenFamily(),
	}
	h.DB.Create(&rt)

	c.JSON(http.StatusCreated, authResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		User:         ToUserResponse(user),
	})
}

// Refresh exchanges a refresh token for new access and refresh tokens.
// Uses token families for replay detection: if a consumed token is replayed,
// all tokens in the family are revoked (indicating token theft).
func (h *AuthHandler) Refresh(c *gin.Context) {
	var req refreshRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	tokenHash := auth.HashRefreshToken(req.RefreshToken)

	// Find and validate refresh token (stored as SHA-256 hash)
	var rt db.RefreshToken
	if err := h.DB.Where("token = ?", tokenHash).First(&rt).Error; err != nil {
		// Token not found — check if it was a consumed token (replay attack detection).
		// Look for any consumed token with this hash to find the family.
		var consumed db.RefreshToken
		if h.DB.Unscoped().Where("token = ? AND consumed = ?", tokenHash, true).First(&consumed).Error == nil {
			// Replay detected! Revoke all tokens in this family.
			slog.Warn("refresh token replay detected, revoking token family",
				"user_id", consumed.UserID, "family", consumed.TokenFamily)
			h.DB.Where("token_family = ? AND user_id = ?", consumed.TokenFamily, consumed.UserID).
				Delete(&db.RefreshToken{})
		}
		c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid refresh token"})
		return
	}

	// Reject already-consumed tokens (shouldn't normally reach here due to the
	// unique index, but defense-in-depth)
	if rt.Consumed {
		slog.Warn("consumed refresh token presented", "user_id", rt.UserID, "family", rt.TokenFamily)
		h.DB.Where("token_family = ? AND user_id = ?", rt.TokenFamily, rt.UserID).
			Delete(&db.RefreshToken{})
		c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid refresh token"})
		return
	}

	if time.Now().After(rt.ExpiresAt) {
		h.DB.Delete(&rt)
		c.JSON(http.StatusUnauthorized, gin.H{"error": "refresh token expired"})
		return
	}

	var user db.User
	if err := h.DB.First(&user, rt.UserID).Error; err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "user not found"})
		return
	}

	if user.Disabled {
		h.DB.Where("token_family = ? AND user_id = ?", rt.TokenFamily, rt.UserID).
			Delete(&db.RefreshToken{})
		c.JSON(http.StatusForbidden, gin.H{"error": "account is disabled"})
		return
	}

	accessToken, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, h.JWTSecret, user.TokenVersion)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to generate token"})
		return
	}

	newRefreshToken, err := auth.GenerateRefreshToken()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to generate refresh token"})
		return
	}

	// Carry forward the same token family; mark old token as consumed
	family := rt.TokenFamily
	if family == "" {
		// Backwards compatibility: assign a new family to legacy tokens without one
		family = generateTokenFamily()
	}

	newRT := db.RefreshToken{
		UserID:      user.ID,
		Token:       auth.HashRefreshToken(newRefreshToken),
		ExpiresAt:   time.Now().Add(auth.RefreshTokenDuration),
		TokenFamily: family,
	}
	if err := h.DB.Transaction(func(tx *gorm.DB) error {
		// Mark old token as consumed (keep it for replay detection)
		if err := tx.Model(&rt).Update("consumed", true).Error; err != nil {
			return err
		}
		return tx.Create(&newRT).Error
	}); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to rotate token"})
		return
	}

	c.JSON(http.StatusOK, authResponse{
		AccessToken:  accessToken,
		RefreshToken: newRefreshToken,
		User:         ToUserResponse(user),
	})
}

// SetupStatus returns whether the server needs initial setup.
func (h *AuthHandler) SetupStatus(c *gin.Context) {
	var userCount int64
	h.DB.Model(&db.User{}).Count(&userCount)

	var gameCount int64
	h.DB.Model(&db.Game{}).Count(&gameCount)

	registrationEnabled := true
	var setting db.ServerSetting
	if err := h.DB.Where("key = ?", "registration_enabled").First(&setting).Error; err == nil {
		registrationEnabled = setting.Value != "false"
	}

	c.JSON(http.StatusOK, gin.H{
		"needsSetup":          userCount == 0,
		"registrationEnabled": registrationEnabled,
		"gameCount":           gameCount,
	})
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
func IsTokenBlacklisted(database *gorm.DB, token string) bool {
	hash := sha256.Sum256([]byte(token))
	var count int64
	database.Model(&db.TokenBlacklist{}).Where("token_hash = ?", hex.EncodeToString(hash[:])).Count(&count)
	return count > 0
}

// Setup creates the initial owner account. Only works when no users exist.
// Wrapped in a transaction to prevent TOCTOU race conditions.
func (h *AuthHandler) Setup(c *gin.Context) {
	var req registerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to hash password"})
		return
	}

	var user db.User
	if err := h.DB.Transaction(func(tx *gorm.DB) error {
		var userCount int64
		tx.Model(&db.User{}).Count(&userCount)
		if userCount > 0 {
			c.JSON(http.StatusForbidden, gin.H{"error": "setup already completed"})
			return fmt.Errorf("setup already completed")
		}

		user = db.User{
			Username:     req.Username,
			Email:        req.Email,
			PasswordHash: hash,
			Role:         db.RoleOwner,
		}
		return tx.Create(&user).Error
	}); err != nil {
		if c.Writer.Written() {
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create user"})
		return
	}

	accessToken, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, h.JWTSecret)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to generate token"})
		return
	}

	refreshToken, err := auth.GenerateRefreshToken()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to generate refresh token"})
		return
	}

	rt := db.RefreshToken{
		UserID:      user.ID,
		Token:       auth.HashRefreshToken(refreshToken),
		ExpiresAt:   time.Now().Add(auth.RefreshTokenDuration),
		TokenFamily: generateTokenFamily(),
	}
	h.DB.Create(&rt)

	c.JSON(http.StatusCreated, authResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		User:         ToUserResponse(user),
	})
}
