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

// defaultMaxSaveUploadMB is the default per-upload save-state cap. Sized
// for uncompressed Dolphin GameCube states which typically run 80–100 MB
// (#805). Smaller systems pay nothing extra — they just don't reach the
// ceiling.
const defaultMaxSaveUploadMB = 256

// maxSaveUploadBytes returns the configured per-upload save-state cap in
// bytes. Defaults to [defaultMaxSaveUploadMB]; override via the
// SPELA_MAX_SAVE_UPLOAD_MB environment variable. Non-positive or
// unparseable values fall back to the default so a typo can't accidentally
// disable all uploads.
func maxSaveUploadBytes() int64 {
	mb := int64(defaultMaxSaveUploadMB)
	if envVal := os.Getenv("SPELA_MAX_SAVE_UPLOAD_MB"); envVal != "" {
		if parsed, err := strconv.ParseInt(envVal, 10, 64); err == nil && parsed > 0 {
			mb = parsed
		}
	}
	return mb << 20
}

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

// userStoredBytes sums every on-disk artifact attributed to a user across all
// the tables that record a file size. Issue #1314: a quota that only counted
// SessionSaveState let shared saves, shared-session saves, and challenge
// starting-saves grow without bound, so any authenticated user could exhaust
// the host disk. Each query is fail-closed — a DB error aborts the check
// rather than silently treating the table as empty.
func userStoredBytes(database *gorm.DB, userID uint) (int64, error) {
	// Each entry: model, the column holding the file size, and the column
	// that attributes the row to a user.
	sources := []struct {
		model    interface{}
		sizeCol  string
		ownerCol string
	}{
		{&db.SessionSaveState{}, "file_size", "user_id"},
		{&db.SharedSaveState{}, "file_size", "user_id"},
		{&db.SharedSessionSave{}, "file_size", "user_id"},
		{&db.Challenge{}, "save_file_size", "creator_id"},
	}
	var total int64
	for _, s := range sources {
		var sum int64
		if err := database.Model(s.model).Where(s.ownerCol+" = ?", userID).
			Select("COALESCE(SUM(" + s.sizeCol + "), 0)").Scan(&sum).Error; err != nil {
			return 0, fmt.Errorf("summing %T for quota: %w", s.model, err)
		}
		total += sum
	}
	return total, nil
}

// checkStorageQuota verifies that adding additionalBytes would not exceed the user's quota.
func checkStorageQuota(database *gorm.DB, userID uint, additionalBytes int64) error {
	// Surface DB errors instead of silently treating them as totalBytes=0,
	// which would let an unbounded payload through whenever the DB is
	// momentarily unavailable. Prefer to fail closed.
	totalBytes, err := userStoredBytes(database, userID)
	if err != nil {
		return fmt.Errorf("checking storage quota: %w", err)
	}
	if totalBytes+additionalBytes > maxSaveStorageBytes() {
		return fmt.Errorf("storage quota exceeded")
	}
	return nil
}

// pathAllowsQueryToken reports whether a request path is allowed to fall
// back to `?token=<jwt>` for authentication. Issue #1117: applying the
// query-token fallback to every protected route leaks JWTs into reverse-
// proxy logs, browser history, Referer headers, etc. The fallback is only
// needed where browsers cannot set Authorization headers — WebSocket
// upgrades and asset URLs loaded by <img>/<audio>/<video>.
func pathAllowsQueryToken(path string) bool {
	switch {
	case path == "/api/ws":
		return true
	case strings.HasPrefix(path, "/api/netplay/sessions/") && strings.HasSuffix(path, "/ws"):
		return true
	case strings.HasPrefix(path, "/api/images/save-screenshots/"):
		return true
	case strings.HasPrefix(path, "/api/bios/"):
		return true
	case strings.HasPrefix(path, "/api/cores/") && strings.Contains(path, "/download"):
		return true
	case strings.HasPrefix(path, "/api/games/") && strings.Contains(path, "/download"):
		return true
	case strings.HasPrefix(path, "/api/games/") && strings.Contains(path, "/discs/") && strings.HasSuffix(path, "/download"):
		return true
	case strings.HasPrefix(path, "/api/games/") && strings.Contains(path, "/shared-saves/") && strings.HasSuffix(path, "/download"):
		return true
	case strings.HasPrefix(path, "/api/sessions/") && strings.Contains(path, "/saves"):
		return true
	case strings.HasPrefix(path, "/api/sessions/") && strings.HasSuffix(path, "/sram"):
		return true
	case strings.HasPrefix(path, "/api/sessions/") && strings.HasSuffix(path, "/save-dir"):
		return true
	case strings.HasPrefix(path, "/api/shared-sessions/") && strings.Contains(path, "/saves"):
		return true
	case strings.HasPrefix(path, "/api/challenges/") && (strings.HasSuffix(path, "/save/download") || strings.HasSuffix(path, "/screenshot")):
		return true
	}
	return false
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

		// Fall back to query parameter only on routes where browsers
		// genuinely can't set the Authorization header (WS upgrades,
		// asset URLs loaded by <img>/<audio>/<video>, file downloads).
		// Restricting the fallback prevents JWTs leaking into proxy
		// access logs / Referer headers from JSON endpoints (#1117).
		if token == "" && pathAllowsQueryToken(c.Request.URL.Path) {
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
