package api

import (
	"fmt"
	"net/http"
	"os"
	"strconv"
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

const defaultMaxSaveStorageMB = 1024

// maxSaveStorageBytes returns the configured per-user storage quota in bytes.
func maxSaveStorageBytes() int64 {
	mb := int64(defaultMaxSaveStorageMB)
	if envVal := os.Getenv("SPELA_MAX_SAVE_STORAGE_MB"); envVal != "" {
		if parsed, err := strconv.ParseInt(envVal, 10, 64); err == nil && parsed > 0 {
			mb = parsed
		}
	}
	return mb << 20
}

// checkStorageQuota verifies that adding additionalBytes would not exceed the user's quota.
func checkStorageQuota(database *gorm.DB, userID uint, additionalBytes int64) error {
	var totalBytes int64
	database.Model(&db.SessionSaveState{}).Where("user_id = ?", userID).
		Select("COALESCE(SUM(file_size), 0)").Scan(&totalBytes)
	if totalBytes+additionalBytes > maxSaveStorageBytes() {
		return fmt.Errorf("storage quota exceeded")
	}
	return nil
}

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

		// Fall back to query parameter for WebSocket upgrades and file
		// downloads (EmulatorJS, <img>/<audio>/<video> tags cannot set
		// Authorization headers when loading resources by URL).
		if token == "" {
			token = c.Query("token")
		}

		if token == "" {
			abortWithError(c, http.StatusUnauthorized, "missing authorization", "Please sign in to continue.")
			return
		}

		claims, err := auth.ValidateAccessToken(token, jwtSecret)
		if err != nil {
			abortWithError(c, http.StatusUnauthorized, "invalid or expired token", "Your session has expired. Please sign in again.")
			return
		}

		userID := claims.UserID
		// Reject revoked (logged-out) access tokens
		if IsTokenBlacklisted(database, token) {
			recordSecurityEventCtx(database, c, db.SystemEventInput{
				EventType: db.SystemEventRevokedTokenUsed,
				Username:  claims.Username,
				UserID:    &userID,
			})
			abortWithError(c, http.StatusUnauthorized, "token has been revoked", "Your session has ended. Please sign in again.")
			return
		}

		// Reject disabled users even if their access token is still valid
		var user db.User
		if err := database.Select("id", "disabled", "token_version").First(&user, claims.UserID).Error; err != nil {
			recordSecurityEventCtx(database, c, db.SystemEventInput{
				EventType: db.SystemEventTokenUserMissing,
				Username:  claims.Username,
				UserID:    &userID,
			})
			abortWithError(c, http.StatusUnauthorized, "user not found", "Your account could not be found. Please sign in again.")
			return
		}
		if user.Disabled {
			recordSecurityEventCtx(database, c, db.SystemEventInput{
				EventType: db.SystemEventDisabledAccountToken,
				Username:  claims.Username,
				UserID:    &userID,
			})
			abortWithError(c, http.StatusForbidden, "account is disabled", "Your account has been disabled. Please contact an administrator.")
			return
		}

		// Reject tokens minted before a role/password/disabled change
		if claims.TokenVersion != user.TokenVersion {
			recordSecurityEventCtx(database, c, db.SystemEventInput{
				EventType: db.SystemEventStaleTokenVersion,
				Username:  claims.Username,
				UserID:    &userID,
				Metadata: db.StaleTokenVersionMetadata{
					TokenVersion:   claims.TokenVersion,
					CurrentVersion: user.TokenVersion,
				},
			})
			abortWithError(c, http.StatusUnauthorized, "token has been invalidated", "Your session is no longer valid. Please sign in again.")
			return
		}

		c.Set("userId", claims.UserID)
		c.Set("username", claims.Username)
		c.Set("role", db.UserRole(claims.Role))
		c.Next()
	}
}

// AdminMiddleware requires the authenticated user to have admin role.
func AdminMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		role, _ := c.Get("role")
		userRole, _ := role.(db.UserRole)
		if !db.IsAdminOrOwner(userRole) {
			abortWithError(c, http.StatusForbidden, "admin access required", "You don't have permission to access this resource.")
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

// maxRateLimitEntries is the maximum number of tracked IPs/keys in a rate limiter.
// Under a DDoS from many unique IPs, this prevents unbounded memory growth.
const maxRateLimitEntries = 100_000

// RateLimiter implements a sliding window rate limiter using golang.org/x/time/rate.
type RateLimiter struct {
	visitors map[string]*rateLimitEntry
	mu       sync.Mutex
	limit    int
	window   time.Duration
	done     chan struct{}
}

type rateLimitEntry struct {
	limiter  *rate.Limiter
	lastSeen time.Time
}

// NewRateLimiter creates a rate limiter that allows `limit` requests per `window`.
// Call Close() to stop the background cleanup goroutine.
func NewRateLimiter(limit int, window time.Duration) *RateLimiter {
	rl := &RateLimiter{
		visitors: make(map[string]*rateLimitEntry),
		limit:    limit,
		window:   window,
		done:     make(chan struct{}),
	}
	// Cleanup stale entries periodically
	go func() {
		ticker := time.NewTicker(window)
		defer ticker.Stop()
		for {
			select {
			case <-ticker.C:
				rl.mu.Lock()
				for ip, e := range rl.visitors {
					if time.Since(e.lastSeen) > window {
						delete(rl.visitors, ip)
					}
				}
				rl.mu.Unlock()
			case <-rl.done:
				return
			}
		}
	}()
	return rl
}

// Close stops the background cleanup goroutine.
func (rl *RateLimiter) Close() {
	select {
	case <-rl.done:
		// already closed
	default:
		close(rl.done)
	}
}

// RateLimit returns a Gin middleware that rate limits by client IP.
func (rl *RateLimiter) RateLimit() gin.HandlerFunc {
	return func(c *gin.Context) {
		ip := c.ClientIP()

		rl.mu.Lock()
		e, exists := rl.visitors[ip]
		if !exists {
			// Evict oldest entry if we've hit the cap to prevent unbounded growth
			if len(rl.visitors) >= maxRateLimitEntries {
				rl.evictOldest()
			}
			limiter := rate.NewLimiter(rate.Every(rl.window/time.Duration(rl.limit)), rl.limit)
			e = &rateLimitEntry{limiter: limiter, lastSeen: time.Now()}
			rl.visitors[ip] = e
		}
		e.lastSeen = time.Now()
		rl.mu.Unlock()

		if !e.limiter.Allow() {
			abortWithError(c, http.StatusTooManyRequests, "rate limit exceeded", "Too many requests. Please slow down and try again.")
			return
		}
		c.Next()
	}
}

// UserRateLimit returns a Gin middleware that rate limits by authenticated user ID.
// This protects against abuse from compromised accounts making unlimited requests.
func (rl *RateLimiter) UserRateLimit() gin.HandlerFunc {
	return func(c *gin.Context) {
		userID, exists := c.Get("userId")
		if !exists {
			c.Next()
			return
		}
		key := fmt.Sprintf("user:%d", userID.(uint))

		rl.mu.Lock()
		e, found := rl.visitors[key]
		if !found {
			if len(rl.visitors) >= maxRateLimitEntries {
				rl.evictOldest()
			}
			limiter := rate.NewLimiter(rate.Every(rl.window/time.Duration(rl.limit)), rl.limit)
			e = &rateLimitEntry{limiter: limiter, lastSeen: time.Now()}
			rl.visitors[key] = e
		}
		e.lastSeen = time.Now()
		rl.mu.Unlock()

		if !e.limiter.Allow() {
			abortWithError(c, http.StatusTooManyRequests, "rate limit exceeded", "Too many requests. Please slow down and try again.")
			return
		}
		c.Next()
	}
}

// evictOldest removes the oldest entry from the visitors map. Must be called with mu held.
func (rl *RateLimiter) evictOldest() {
	var oldestKey string
	var oldestTime time.Time
	first := true
	for k, v := range rl.visitors {
		if first || v.lastSeen.Before(oldestTime) {
			oldestKey = k
			oldestTime = v.lastSeen
			first = false
		}
	}
	if !first {
		delete(rl.visitors, oldestKey)
	}
}
