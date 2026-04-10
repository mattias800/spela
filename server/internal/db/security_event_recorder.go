package db

import (
	"encoding/json"
	"log/slog"
	"strings"
	"sync"
	"time"

	"gorm.io/gorm"
)

// SecurityEventInput is the parameter bag accepted by RecordSecurityEvent.
// Only EventType is required — every other field is optional and is only
// persisted when non-empty / non-nil. UserID is a pointer so a missing user
// is represented as nil instead of 0 (which is a legitimate primary key).
type SecurityEventInput struct {
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
// tuple within a short window, writing only the first occurrence and dropping
// subsequent duplicates until the window elapses. The slog mirror is always
// emitted so log-tailing is unaffected.

const securityEventDedupWindow = 60 * time.Second

// securityEventDedupKey groups events that should be collapsed into one DB
// row during the dedup window. Fields:
//
//   - eventType: the most important dimension; different event types never
//     collapse into the same bucket.
//   - ip: distinct source IPs are always distinct events, even for the same
//     user and type — we want to see the horizontal spread of an attack.
//   - hasUserID + userID: hasUserID distinguishes "no known user" from
//     "user ID 0" so that unauthenticated floods don't collapse with
//     authenticated ones that happen to have ID 0. (See SecurityEventInput.)
//   - reason: same type with different reasons (e.g. login_failed
//     "bad_password" vs "unknown_user") represent different attack signals
//     and should not collapse.
//
// NOTE: path is intentionally NOT part of the key. Collapsing a flood that
// hits many protected routes into one row is a deliberate tradeoff: we
// lose per-endpoint visibility but gain protection against a malicious
// client walking every route to amplify writes. The surviving row captures
// the first endpoint hit; the slog mirror still records every endpoint.
type securityEventDedupKey struct {
	eventType string
	ip        string
	hasUserID bool
	userID    uint
	reason    string
}

type securityEventDedup struct {
	mu       sync.Mutex
	lastSeen map[securityEventDedupKey]time.Time
}

var globalSecurityEventDedup = &securityEventDedup{
	lastSeen: make(map[securityEventDedupKey]time.Time),
}

// shouldRecord returns true if the event should be written to the database.
// It also opportunistically prunes expired entries to keep the map bounded.
func (d *securityEventDedup) shouldRecord(in SecurityEventInput) bool {
	d.mu.Lock()
	defer d.mu.Unlock()

	key := securityEventDedupKey{
		eventType: in.EventType,
		ip:        in.IP,
		reason:    in.Reason,
	}
	if in.UserID != nil {
		key.hasUserID = true
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
	case SecurityEventRevokedTokenUsed,
		SecurityEventDisabledAccountToken,
		SecurityEventTokenUserMissing,
		SecurityEventStaleTokenVersion:
		return true
	}
	return false
}

// RecordSecurityEvent persists a security event row and emits a structured
// slog warning on the same call site. Best-effort: a DB failure is logged
// but never blocks the surrounding flow, since refusing to authenticate
// over an audit-write failure would be a self-inflicted denial of service.
//
// Lives in the db package (not api) so non-HTTP callers — background jobs,
// WebSocket auth handlers, future netplay session auth — can emit events
// without pulling in gin. HTTP handlers should use the thin gin adapter
// in the api package that extracts the client IP and request path from
// gin.Context automatically.
func RecordSecurityEvent(database *gorm.DB, in SecurityEventInput) {
	// Always mirror to slog, even when we drop the DB write, so log-tailing
	// workflows still see the burst.
	logSecurityEvent(in)

	if eventTypeShouldDedup(in.EventType) && !globalSecurityEventDedup.shouldRecord(in) {
		return
	}

	var metaJSON string
	if len(in.Metadata) > 0 {
		if b, err := json.Marshal(in.Metadata); err == nil {
			metaJSON = string(b)
		}
	}

	row := SecurityEvent{
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

func logSecurityEvent(in SecurityEventInput) {
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
	if in.EventType == SecurityEventLoginSuccess {
		slog.Info("security: "+in.EventType, logArgs...)
	} else {
		slog.Warn("security: "+in.EventType, logArgs...)
	}
}
