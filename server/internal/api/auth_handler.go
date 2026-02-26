package api

import (
	"log/slog"
	"net/http"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// loginAttemptTracker tracks failed login attempts per username for account lockout.
type loginAttemptTracker struct {
	mu       sync.Mutex
	attempts map[string]*loginAttemptEntry
}

type loginAttemptEntry struct {
	count     int
	lockedAt  time.Time
	lastTried time.Time
}

const (
	maxLoginAttempts  = 5
	loginLockDuration = 15 * time.Minute
)

var loginTracker = &loginAttemptTracker{
	attempts: make(map[string]*loginAttemptEntry),
}

// recordFailedLogin increments the counter. Returns true if now locked out.
func (t *loginAttemptTracker) recordFailedLogin(username string) bool {
	t.mu.Lock()
	defer t.mu.Unlock()
	entry, ok := t.attempts[username]
	if !ok {
		entry = &loginAttemptEntry{}
		t.attempts[username] = entry
	}
	entry.count++
	entry.lastTried = time.Now()
	if entry.count >= maxLoginAttempts {
		entry.lockedAt = time.Now()
		return true
	}
	return false
}

// isLockedOut returns true if the account is currently locked.
func (t *loginAttemptTracker) isLockedOut(username string) bool {
	t.mu.Lock()
	defer t.mu.Unlock()
	entry, ok := t.attempts[username]
	if !ok {
		return false
	}
	if entry.count < maxLoginAttempts {
		return false
	}
	if time.Since(entry.lockedAt) > loginLockDuration {
		// Lockout expired, reset
		delete(t.attempts, username)
		return false
	}
	return true
}

// clearFailedLogins resets the counter on successful login.
func (t *loginAttemptTracker) clearFailedLogins(username string) {
	t.mu.Lock()
	defer t.mu.Unlock()
	delete(t.attempts, username)
}

// AuthHandler handles authentication endpoints.
type AuthHandler struct {
	DB        *gorm.DB
	JWTSecret string
}

type loginRequest struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required"`
}

type registerRequest struct {
	Username string `json:"username" binding:"required,min=3,max=64"`
	Email    string `json:"email" binding:"required,email"`
	Password string `json:"password" binding:"required,min=8"`
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
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	// Check account lockout before proceeding
	if loginTracker.isLockedOut(req.Username) {
		c.JSON(http.StatusTooManyRequests, gin.H{"error": "account temporarily locked due to too many failed login attempts"})
		return
	}

	var user db.User
	if err := h.DB.Where("username = ?", req.Username).First(&user).Error; err != nil {
		loginTracker.recordFailedLogin(req.Username)
		c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid credentials"})
		return
	}

	if !auth.CheckPassword(req.Password, user.PasswordHash) {
		loginTracker.recordFailedLogin(req.Username)
		c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid credentials"})
		return
	}

	if user.Disabled {
		c.JSON(http.StatusForbidden, gin.H{"error": "account is disabled"})
		return
	}

	// Successful login — clear any failed attempts
	loginTracker.clearFailedLogins(req.Username)

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

	// Store refresh token
	rt := db.RefreshToken{
		UserID:    user.ID,
		Token:     refreshToken,
		ExpiresAt: time.Now().Add(auth.RefreshTokenDuration),
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
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	// Check if username or email already exists
	var count int64
	h.DB.Model(&db.User{}).Where("username = ? OR email = ?", req.Username, req.Email).Count(&count)
	if count > 0 {
		c.JSON(http.StatusConflict, gin.H{"error": "username or email already exists"})
		return
	}

	// Check if registration is enabled (skip for first user)
	var totalUsers int64
	h.DB.Model(&db.User{}).Count(&totalUsers)
	if totalUsers > 0 {
		var setting db.ServerSetting
		if err := h.DB.Where("key = ?", "registration_enabled").First(&setting).Error; err == nil {
			if setting.Value == "false" {
				c.JSON(http.StatusForbidden, gin.H{"error": "registration is disabled"})
				return
			}
		}
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to hash password"})
		return
	}

	// First user becomes owner
	role := db.RoleUser
	var userCount int64
	h.DB.Model(&db.User{}).Count(&userCount)
	if userCount == 0 {
		role = db.RoleOwner
	}

	user := db.User{
		Username:     req.Username,
		Email:        req.Email,
		PasswordHash: hash,
		Role:         role,
	}

	if err := h.DB.Create(&user).Error; err != nil {
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
		UserID:    user.ID,
		Token:     refreshToken,
		ExpiresAt: time.Now().Add(auth.RefreshTokenDuration),
	}
	h.DB.Create(&rt)

	c.JSON(http.StatusCreated, authResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		User:         ToUserResponse(user),
	})
}

// Refresh exchanges a refresh token for new access and refresh tokens.
func (h *AuthHandler) Refresh(c *gin.Context) {
	var req refreshRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	// Find and validate refresh token
	var rt db.RefreshToken
	if err := h.DB.Where("token = ?", req.RefreshToken).First(&rt).Error; err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid refresh token"})
		return
	}

	if time.Now().After(rt.ExpiresAt) {
		h.DB.Delete(&rt)
		c.JSON(http.StatusUnauthorized, gin.H{"error": "refresh token expired"})
		return
	}

	// Delete old refresh token (rotation)
	h.DB.Delete(&rt)

	var user db.User
	if err := h.DB.First(&user, rt.UserID).Error; err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "user not found"})
		return
	}

	if user.Disabled {
		c.JSON(http.StatusForbidden, gin.H{"error": "account is disabled"})
		return
	}

	accessToken, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, h.JWTSecret)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to generate token"})
		return
	}

	newRefreshToken, err := auth.GenerateRefreshToken()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to generate refresh token"})
		return
	}

	newRT := db.RefreshToken{
		UserID:    user.ID,
		Token:     newRefreshToken,
		ExpiresAt: time.Now().Add(auth.RefreshTokenDuration),
	}
	h.DB.Create(&newRT)

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

	registrationEnabled := true
	var setting db.ServerSetting
	if err := h.DB.Where("key = ?", "registration_enabled").First(&setting).Error; err == nil {
		registrationEnabled = setting.Value != "false"
	}

	c.JSON(http.StatusOK, gin.H{
		"needsSetup":          userCount == 0,
		"registrationEnabled": registrationEnabled,
	})
}

// StartTokenCleanup starts a background goroutine that periodically deletes
// expired refresh tokens from the database to prevent unbounded growth.
func StartTokenCleanup(database *gorm.DB, interval time.Duration) {
	go func() {
		for {
			time.Sleep(interval)
			result := database.Where("expires_at < ?", time.Now()).Delete(&db.RefreshToken{})
			if result.RowsAffected > 0 {
				slog.Info("cleaned up expired refresh tokens", "count", result.RowsAffected)
			}
		}
	}()
}

// Setup creates the initial owner account. Only works when no users exist.
func (h *AuthHandler) Setup(c *gin.Context) {
	var userCount int64
	h.DB.Model(&db.User{}).Count(&userCount)
	if userCount > 0 {
		c.JSON(http.StatusForbidden, gin.H{"error": "setup already completed"})
		return
	}

	var req registerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to hash password"})
		return
	}

	user := db.User{
		Username:     req.Username,
		Email:        req.Email,
		PasswordHash: hash,
		Role:         db.RoleOwner,
	}
	if err := h.DB.Create(&user).Error; err != nil {
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
		UserID:    user.ID,
		Token:     refreshToken,
		ExpiresAt: time.Now().Add(auth.RefreshTokenDuration),
	}
	h.DB.Create(&rt)

	c.JSON(http.StatusCreated, authResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		User:         ToUserResponse(user),
	})
}
