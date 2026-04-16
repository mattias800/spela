package api

import (
	"log/slog"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

// GetUserRateLimit returns the current login rate limit status for a user.
func (h *AdminHandler) GetUserRateLimit(c *gin.Context) {
	id := c.Param("id")
	var user db.User
	if err := h.DB.First(&user, id).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "user not found"})
		return
	}

	hashed := hashUsername(user.Username)
	var attempt db.LoginAttempt
	if err := h.DB.Where("username = ?", hashed).First(&attempt).Error; err != nil {
		// No login attempt record means no failed attempts
		c.JSON(http.StatusOK, RateLimitResponse{
			FailedCount: 0,
			LockedUntil: nil,
			IsLockedOut: false,
		})
		return
	}

	// Check if the counter has expired (24h since last failure)
	if time.Since(attempt.UpdatedAt) > loginAttemptMaxAge {
		c.JSON(http.StatusOK, RateLimitResponse{
			FailedCount: 0,
			LockedUntil: nil,
			IsLockedOut: false,
		})
		return
	}

	isLocked := attempt.FailedCount >= maxLoginAttempts && time.Now().Before(attempt.LockedUntil)
	var lockedUntil *time.Time
	if !attempt.LockedUntil.IsZero() {
		lockedUntil = &attempt.LockedUntil
	}

	c.JSON(http.StatusOK, RateLimitResponse{
		FailedCount: attempt.FailedCount,
		LockedUntil: lockedUntil,
		IsLockedOut: isLocked,
	})
}

// ResetUserRateLimit clears the login rate limit for a user.
func (h *AdminHandler) ResetUserRateLimit(c *gin.Context) {
	id := c.Param("id")
	var user db.User
	if err := h.DB.First(&user, id).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "user not found"})
		return
	}

	hashed := hashUsername(user.Username)
	h.DB.Where("username = ?", hashed).Delete(&db.LoginAttempt{})

	adminID, _ := c.Get("userId")
	slog.Info("audit: admin reset rate limit", "admin_id", adminID, "target_user", user.Username)
	c.JSON(http.StatusOK, gin.H{"message": "rate limit reset"})
}
