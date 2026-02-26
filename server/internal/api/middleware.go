package api

import (
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"golang.org/x/time/rate"
	"gorm.io/gorm"
)

// maxSaveUploadSize is the maximum allowed save state upload size (64 MB).
const maxSaveUploadSize = 64 << 20

// AuthMiddleware validates JWT tokens on protected routes and rejects disabled users.
func AuthMiddleware(jwtSecret string, database *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		var token string
		header := c.GetHeader("Authorization")
		if header != "" {
			parts := strings.SplitN(header, " ", 2)
			if len(parts) == 2 && parts[0] == "Bearer" {
				token = parts[1]
			}
		}

		// Fall back to query parameter (used by browser WebSocket connections)
		if token == "" {
			token = c.Query("token")
		}

		if token == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "missing authorization"})
			return
		}

		claims, err := auth.ValidateAccessToken(token, jwtSecret)
		if err != nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "invalid or expired token"})
			return
		}

		// Reject revoked (logged-out) access tokens
		if IsTokenBlacklisted(database, token) {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "token has been revoked"})
			return
		}

		// Reject disabled users even if their access token is still valid
		var user db.User
		if err := database.Select("id", "disabled").First(&user, claims.UserID).Error; err != nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "user not found"})
			return
		}
		if user.Disabled {
			c.AbortWithStatusJSON(http.StatusForbidden, gin.H{"error": "account is disabled"})
			return
		}

		c.Set("userId", claims.UserID)
		c.Set("username", claims.Username)
		c.Set("role", claims.Role)
		c.Next()
	}
}

// AdminMiddleware requires the authenticated user to have admin role.
func AdminMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		role, _ := c.Get("role")
		if role != "admin" && role != "owner" {
			c.AbortWithStatusJSON(http.StatusForbidden, gin.H{"error": "admin access required"})
			return
		}
		c.Next()
	}
}

// MaxJSONBodySize limits the request body size for non-multipart JSON endpoints.
// This prevents abuse via oversized payloads. File upload endpoints use their own
// limits via http.MaxBytesReader.
const MaxJSONBodySize = 1 << 20 // 1 MB

// BodySizeLimiter returns middleware that limits the request body to maxBytes.
// Multipart requests (file uploads) are excluded since they have their own limits.
func BodySizeLimiter(maxBytes int64) gin.HandlerFunc {
	return func(c *gin.Context) {
		if c.Request.Body != nil && !strings.HasPrefix(c.ContentType(), "multipart/") {
			c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxBytes)
		}
		c.Next()
	}
}

// RateLimiter implements a sliding window rate limiter using golang.org/x/time/rate.
type RateLimiter struct {
	visitors map[string]*rateLimitEntry
	mu       sync.Mutex
	limit    int
	window   time.Duration
}

type rateLimitEntry struct {
	limiter  *rate.Limiter
	lastSeen time.Time
}

// NewRateLimiter creates a rate limiter that allows `limit` requests per `window`.
func NewRateLimiter(limit int, window time.Duration) *RateLimiter {
	rl := &RateLimiter{
		visitors: make(map[string]*rateLimitEntry),
		limit:    limit,
		window:   window,
	}
	// Cleanup stale entries periodically
	go func() {
		for {
			time.Sleep(window)
			rl.mu.Lock()
			for ip, e := range rl.visitors {
				if time.Since(e.lastSeen) > window {
					delete(rl.visitors, ip)
				}
			}
			rl.mu.Unlock()
		}
	}()
	return rl
}

// RateLimit returns a Gin middleware that rate limits by client IP.
func (rl *RateLimiter) RateLimit() gin.HandlerFunc {
	return func(c *gin.Context) {
		ip := c.ClientIP()

		rl.mu.Lock()
		e, exists := rl.visitors[ip]
		if !exists {
			limiter := rate.NewLimiter(rate.Every(rl.window/time.Duration(rl.limit)), rl.limit)
			e = &rateLimitEntry{limiter: limiter, lastSeen: time.Now()}
			rl.visitors[ip] = e
		}
		e.lastSeen = time.Now()
		rl.mu.Unlock()

		if !e.limiter.Allow() {
			c.AbortWithStatusJSON(http.StatusTooManyRequests, gin.H{"error": "rate limit exceeded"})
			return
		}
		c.Next()
	}
}
