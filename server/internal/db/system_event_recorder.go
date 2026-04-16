package db

import (
	"encoding/json"
	"log/slog"
	"strings"
	"sync"
	"time"

	"gorm.io/gorm"
)

// SystemEventInput is the parameter bag accepted by the recording functions.
// Only EventType is required — every other field is optional and is only
// persisted when non-empty / non-nil. UserID is a pointer so a missing user
// is represented as nil instead of 0 (which is a legitimate primary key).
type SystemEventInput struct {
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
// AuthMiddleware writes a SystemEvent row on every failed auth check
// (revoked token, disabled account, stale token version, missing user). A
// malicious client replaying a revoked token in a tight loop would otherwise
// turn each request into a synchronous INSERT on the hot path.
//
// systemEventDedup collapses identical events from the same (type, ip, user)
// tuple within a short window, writing only the first occurrence and dropping
// subsequent duplicates until the window elapses. The slog mirror is always
// emitted so log-tailing is unaffected.

const systemEventDedupWindow = 60 * time.Second

// systemEventDedupKey groups events that should be collapsed into one DB
// row during the dedup window.
type systemEventDedupKey struct {
	eventType string
	ip        string
	hasUserID bool
	userID    uint
	reason    string
}

type systemEventDedup struct {
	mu       sync.Mutex
	lastSeen map[systemEventDedupKey]time.Time
}

var globalSystemEventDedup = &systemEventDedup{
	lastSeen: make(map[systemEventDedupKey]time.Time),
}

// shouldRecord returns true if the event should be written to the database.
// It also opportunistically prunes expired entries to keep the map bounded.
func (d *systemEventDedup) shouldRecord(in SystemEventInput) bool {
	d.mu.Lock()
	defer d.mu.Unlock()

	key := systemEventDedupKey{
		eventType: in.EventType,
		ip:        in.IP,
		reason:    in.Reason,
	}
	if in.UserID != nil {
		key.hasUserID = true
		key.userID = *in.UserID
	}

	now := time.Now()
	if last, ok := d.lastSeen[key]; ok && now.Sub(last) < systemEventDedupWindow {
		return false
	}
	d.lastSeen[key] = now

	// Cheap GC: every call, if the map has grown past a threshold, drop
	// expired entries. Keeps the map roughly bounded under attack without
	// a background goroutine.
	if len(d.lastSeen) > 1024 {
		cutoff := now.Add(-systemEventDedupWindow)
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
// originated anomalies and operational events get the dedup treatment
// because they can flood under attack or during infrastructure issues.
func eventTypeShouldDedup(eventType string) bool {
	switch eventType {
	case SystemEventRevokedTokenUsed,
		SystemEventDisabledAccountToken,
		SystemEventTokenUserMissing,
		SystemEventStaleTokenVersion,
		SystemEventRACircuitBreakerTripped,
		SystemEventScraperRepeatedErrors,
		SystemEventROMFileMissing,
		SystemEventAPICredentialsInvalid:
		return true
	}
	return false
}

// --- Category ID resolution cache ------------------------------------------

var (
	categoryIDCache   map[string]uint
	categoryIDCacheMu sync.Mutex
)

func resolveCategoryID(database *gorm.DB, eventType string) uint {
	categoryIDCacheMu.Lock()
	defer categoryIDCacheMu.Unlock()

	if categoryIDCache == nil {
		categoryIDCache = make(map[string]uint)
		var cats []SystemEventCategory
		database.Find(&cats)
		for _, c := range cats {
			categoryIDCache[c.Code] = c.ID
		}
	}

	code, ok := SystemEventTypeCategory[eventType]
	if !ok {
		code = CategoryOperational // fallback
	}
	return categoryIDCache[code]
}

// --- Recording functions ---------------------------------------------------

// recordSystemEvent persists a system event row and emits a structured slog
// entry. Best-effort: a DB failure is logged but never blocks the caller.
//
// Lives in the db package (not api) so non-HTTP callers — background jobs,
// WebSocket auth handlers, future netplay session auth — can emit events
// without pulling in gin.
func recordSystemEvent(database *gorm.DB, in SystemEventInput) {
	logSystemEvent(in)

	if eventTypeShouldDedup(in.EventType) && !globalSystemEventDedup.shouldRecord(in) {
		return
	}

	var metaJSON string
	if len(in.Metadata) > 0 {
		if b, err := json.Marshal(in.Metadata); err == nil {
			metaJSON = string(b)
		}
	}

	row := SystemEvent{
		CategoryID:    resolveCategoryID(database, in.EventType),
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
		slog.Warn("failed to persist system event",
			"event", in.EventType,
			"error", err,
		)
	}
}

// RecordSecurityEvent records a security-category system event. HTTP handlers
// should use the thin gin adapter in the api package that extracts client IP
// and request path from gin.Context automatically.
func RecordSecurityEvent(database *gorm.DB, in SystemEventInput) {
	recordSystemEvent(database, in)
}

// RecordOperationalEvent records an operational-category system event.
func RecordOperationalEvent(database *gorm.DB, in SystemEventInput) {
	recordSystemEvent(database, in)
}

func logSystemEvent(in SystemEventInput) {
	category := SystemEventTypeCategory[in.EventType]
	logArgs := []any{
		"event", in.EventType,
		"category", category,
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
	if in.EventType == SystemEventLoginSuccess {
		slog.Info("system-event: "+in.EventType, logArgs...)
	} else {
		slog.Warn("system-event: "+in.EventType, logArgs...)
	}
}
