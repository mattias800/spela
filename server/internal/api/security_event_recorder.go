package api

import (
	"encoding/json"
	"log/slog"
	"strings"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// securityEventInput is the parameter bag for RecordSecurityEvent. Username,
// userID, ip, and metadata are all optional — only EventType is required.
type securityEventInput struct {
	EventType string
	Reason    string
	Username  string
	UserID    *uint
	IP        string
	Path      string
	Metadata  map[string]any
}

// --- Middleware write amplification guard ----------------------------------
//
// AuthMiddleware writes a SecurityEvent row on every failed auth check
// (revoked token, disabled account, stale token version, missing user). A
// malicious client replaying a revoked token in a tight loop would otherwise
// turn each request into a synchronous INSERT on the hot path.
//
// securityEventDedup collapses identical events from the same (type, ip, user)
// tuple within a short window, logging only the first occurrence and
// incrementing an in-memory counter for the suppressed ones. The first event
// after a flood still lands in the database; the rest are dropped until the
// window elapses. The slog mirror is always emitted so log-tailing is
// unaffected.

const securityEventDedupWindow = 60 * time.Second

type dedupKey struct {
	eventType string
	ip        string
	userID    uint
	reason    string
}

type securityEventDedup struct {
	mu      sync.Mutex
	lastSeen map[dedupKey]time.Time
}

var globalSecurityEventDedup = &securityEventDedup{
	lastSeen: make(map[dedupKey]time.Time),
}

// shouldRecord returns true if the event should be written to the database.
// It also opportunistically prunes expired entries to keep the map bounded.
func (d *securityEventDedup) shouldRecord(in securityEventInput) bool {
	d.mu.Lock()
	defer d.mu.Unlock()

	key := dedupKey{eventType: in.EventType, ip: in.IP, reason: in.Reason}
	if in.UserID != nil {
		key.userID = *in.UserID
	}

	now := time.Now()
	if last, ok := d.lastSeen[key]; ok && now.Sub(last) < securityEventDedupWindow {
		return false
	}
	d.lastSeen[key] = now

	// Cheap GC: every call, if the map has grown past a threshold, drop
	// expired entries. Keeps the map roughly bounded under attack without
	// a background goroutine.
	if len(d.lastSeen) > 1024 {
		cutoff := now.Add(-securityEventDedupWindow)
		for k, t := range d.lastSeen {
			if t.Before(cutoff) {
				delete(d.lastSeen, k)
			}
		}
	}
	return true
}

// eventTypeShouldDedup decides which events are dedup candidates. Login
// activity (success + failed + locked account access) is always recorded
// because each attempt is independently meaningful for an admin. Middleware-
// originated anomalies (revoked token replay, disabled-account-token,
// stale-version) get the dedup treatment because they can flood under attack.
func eventTypeShouldDedup(eventType string) bool {
	switch eventType {
	case db.SecurityEventRevokedTokenUsed,
		db.SecurityEventDisabledAccountToken,
		db.SecurityEventTokenUserMissing,
		db.SecurityEventStaleTokenVersion:
		return true
	}
	return false
}

// recordSecurityEvent persists a security event row and emits a structured
// slog warning on the same call site. Best-effort: a DB failure is logged
// but never blocks the surrounding auth flow, since refusing to authenticate
// over an audit-write failure would be a self-inflicted denial of service.
func recordSecurityEvent(database *gorm.DB, in securityEventInput) {
	// Always mirror to slog, even when we drop the DB write, so log-tailing
	// workflows still see the burst.
	logEvent(in)

	if eventTypeShouldDedup(in.EventType) && !globalSecurityEventDedup.shouldRecord(in) {
		return
	}

	var metaJSON string
	if len(in.Metadata) > 0 {
		if b, err := json.Marshal(in.Metadata); err == nil {
			metaJSON = string(b)
		}
	}

	row := db.SecurityEvent{
		EventType:     in.EventType,
		Reason:        in.Reason,
		Username:      in.Username,
		UsernameLower: strings.ToLower(in.Username),
		UserID:        in.UserID,
		IP:            in.IP,
		Path:          in.Path,
		Metadata:      metaJSON,
	}
	if err := database.Create(&row).Error; err != nil {
		slog.Warn("failed to persist security event",
			"event", in.EventType,
			"error", err,
		)
	}
}

func logEvent(in securityEventInput) {
	logArgs := []any{
		"event", in.EventType,
		"username", in.Username,
		"ip", in.IP,
	}
	if in.Reason != "" {
		logArgs = append(logArgs, "reason", in.Reason)
	}
	if in.UserID != nil {
		logArgs = append(logArgs, "userId", *in.UserID)
	}
	if in.Path != "" {
		logArgs = append(logArgs, "path", in.Path)
	}
	for k, v := range in.Metadata {
		logArgs = append(logArgs, k, v)
	}
	if in.EventType == db.SecurityEventLoginSuccess {
		slog.Info("security: "+in.EventType, logArgs...)
	} else {
		slog.Warn("security: "+in.EventType, logArgs...)
	}
}

// recordSecurityEventCtx is a thin convenience wrapper that extracts the
// client IP and request path from a gin.Context. Handlers should prefer this
// when they have the context in scope.
func recordSecurityEventCtx(database *gorm.DB, c *gin.Context, in securityEventInput) {
	if in.IP == "" {
		in.IP = c.ClientIP()
	}
	if in.Path == "" && c.Request != nil && c.Request.URL != nil {
		in.Path = c.Request.URL.Path
	}
	recordSecurityEvent(database, in)
}
