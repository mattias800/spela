package api

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// SecurityEventHandler serves the admin-only security event log endpoints
// that back the /admin/security-events page in the web UI.
type SecurityEventHandler struct {
	DB *gorm.DB
}

// SecurityEventResponse is the JSON shape returned by the API.
//
// Metadata is a parsed map when the stored JSON is well-formed. MetadataRaw
// is an escape hatch populated only when the stored JSON failed to parse so
// the admin UI can still display something useful for forensic purposes.
type SecurityEventResponse struct {
	ID          uint           `json:"id"`
	CreatedAt   time.Time      `json:"createdAt"`
	EventType   string         `json:"eventType"`
	Reason      string         `json:"reason,omitempty"`
	Username    string         `json:"username,omitempty"`
	UserID      *uint          `json:"userId,omitempty"`
	IP          string         `json:"ip,omitempty"`
	Path        string         `json:"path,omitempty"`
	Metadata    map[string]any `json:"metadata,omitempty"`
	MetadataRaw string         `json:"metadataRaw,omitempty"`
}

// SecurityEventsListResponse is the paginated list payload.
type SecurityEventsListResponse struct {
	Data     []SecurityEventResponse `json:"data"`
	Total    int64                   `json:"total"`
	Page     int                     `json:"page"`
	PageSize int                     `json:"pageSize"`
}

// defaultSecurityEventsSince bounds the default list query when no `since`
// parameter is supplied, so an admin browsing the audit log does not trigger
// a full table scan of the `security_events` table. Clients can opt into a
// wider window explicitly with `since=all` or a specific RFC3339 timestamp.
//
// Note: the web UI always sets `since` explicitly (defaulting to `24h`), so
// this default only applies to non-UI callers (curl, scripts, other clients)
// that previously relied on "omit = show all".
const defaultSecurityEventsSince = 30 * 24 * time.Hour

// maxUsernameFilterLength caps the `username` query parameter. The column
// itself is `size:128`, so any match beyond that is pointless, and refusing
// absurdly long input prevents wasted CPU on runaway LIKE scans.
const maxUsernameFilterLength = 128

// toSecurityEventResponse converts a db row to its API representation,
// parsing the metadata JSON blob into a map for easier client consumption.
// When the JSON fails to parse (legacy rows, manual DB edits), log a warning
// and surface the raw string via MetadataRaw so investigators don't lose the
// information silently.
func toSecurityEventResponse(e db.SecurityEvent) SecurityEventResponse {
	r := SecurityEventResponse{
		ID:        e.ID,
		CreatedAt: e.CreatedAt,
		EventType: e.EventType,
		Reason:    e.Reason,
		Username:  e.Username,
		UserID:    e.UserID,
		IP:        e.IP,
		Path:      e.Path,
	}
	if e.Metadata != "" {
		var m map[string]any
		if err := json.Unmarshal([]byte(e.Metadata), &m); err == nil {
			r.Metadata = m
		} else {
			// Include a truncated prefix of the raw blob in the log so a grep
			// over container logs is self-contained — otherwise diagnosing
			// parse failures requires a second DB query.
			slog.Warn("failed to parse security event metadata JSON",
				"id", e.ID,
				"eventType", e.EventType,
				"error", err,
				"rawPrefix", truncateForLog(e.Metadata, 200),
			)
			r.MetadataRaw = e.Metadata
		}
	}
	return r
}

// truncateForLog returns s bounded to max runes, appending "…" when truncated.
// Used to keep oversized values out of log lines.
func truncateForLog(s string, max int) string {
	if len(s) <= max {
		return s
	}
	return s[:max] + "…"
}

// parseSinceParam converts a "since" query parameter to a time.Time along
// with an `explicit` flag indicating whether the caller supplied the param at
// all. Callers that want a default time window when `since` is omitted should
// check `explicit` and fall back on their own default.
//
// Accepts presets ("1h", "24h", "7d", "30d", "all") or an RFC3339 timestamp.
// Returns the zero time when "all" is supplied (no time filter).
func parseSinceParam(raw string) (t time.Time, explicit bool) {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return time.Time{}, false
	}
	switch strings.ToLower(trimmed) {
	case "all":
		return time.Time{}, true
	case "1h":
		return time.Now().Add(-1 * time.Hour), true
	case "24h", "1d":
		return time.Now().Add(-24 * time.Hour), true
	case "7d":
		return time.Now().Add(-7 * 24 * time.Hour), true
	case "30d":
		return time.Now().Add(-30 * 24 * time.Hour), true
	}
	if parsed, err := time.Parse(time.RFC3339, trimmed); err == nil {
		return parsed, true
	}
	// Unparseable input behaves the same as an omitted param — the handler
	// will fall back on the default window.
	return time.Time{}, false
}

// ipFilterPattern restricts the `ip` query parameter to characters that can
// appear in an IPv4 or IPv6 address (or a prefix of one). This is a coarse
// sanity check, not a strict validator: callers can pass partial prefixes
// like "10.0." that would fail net.ParseIP.
//
// CIDR notation (`10.0.0.0/24`) and IPv6 zone identifiers (`fe80::1%eth0`)
// are intentionally not supported — admins filter by address prefix instead.
// The length cap of 45 is the max length of a fully-qualified IPv4-mapped
// IPv6 address (`0000:0000:0000:0000:0000:ffff:255.255.255.255`).
var ipFilterPattern = regexp.MustCompile(`^[0-9a-fA-F.:]{1,45}$`)

// validateIPFilter returns ok=true if the filter string is empty or passes
// the prefix pattern. Admin-only endpoint, but clear feedback on a typo
// (e.g. "10.o.0.1" with letter-o) beats a silent empty result.
func validateIPFilter(raw string) bool {
	if raw == "" {
		return true
	}
	return ipFilterPattern.MatchString(raw)
}

// likeEscaper escapes the LIKE wildcards `%` and `_` (and the escape char
// `\`) so user-supplied substrings are matched literally. Queries using the
// result MUST include `ESCAPE '\\'` in the SQL.
var likeEscaper = strings.NewReplacer(
	`\`, `\\`,
	`%`, `\%`,
	`_`, `\_`,
)

// ListSecurityEvents returns a paginated, filterable list of security events.
//
// Query parameters:
//
//	page       — 1-based page number, default 1; invalid values clamp to 1
//	pageSize   — rows per page, default 50, capped at 200
//	eventType  — repeatable; restricts to one or more event types
//	username   — case-insensitive substring match on username (LIKE wildcards
//	             in the input are matched literally, not expanded); max 128
//	             characters, longer input returns 400
//	ip         — prefix match on the IP field; must look like an IPv4/IPv6
//	             address or prefix, CIDR and zone IDs unsupported; returns
//	             400 on garbage
//	since      — preset (1h, 24h, 7d, 30d, all) or RFC3339 timestamp;
//	             defaults to 30d when omitted, use `all` to remove the bound
func (h *SecurityEventHandler) ListSecurityEvents(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	if page < 1 {
		page = 1
	}
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "50"))
	if pageSize < 1 {
		pageSize = 50
	}
	if pageSize > 200 {
		pageSize = 200
	}

	// Validate IP filter up front so the client gets clear feedback on a typo
	// instead of a silent empty result set.
	rawIP := strings.TrimSpace(c.Query("ip"))
	if !validateIPFilter(rawIP) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid ip filter: expected an IPv4 or IPv6 address or prefix"})
		return
	}

	// Cap the username filter length up front so a malicious or buggy caller
	// can't submit a megabyte of text and drive a runaway LIKE scan.
	rawUsername := strings.TrimSpace(c.Query("username"))
	if len(rawUsername) > maxUsernameFilterLength {
		c.JSON(http.StatusBadRequest, gin.H{"error": "username filter too long"})
		return
	}

	q := h.DB.Model(&db.SecurityEvent{})

	if types := c.QueryArray("eventType"); len(types) > 0 {
		q = q.Where("event_type IN ?", types)
	}
	if rawUsername != "" {
		// Filter uses the denormalized username_lower column so SQLite can
		// serve the query from an index. See SecurityEvent.UsernameLower.
		// Escape LIKE wildcards so an admin searching for a literal `%` or
		// `_` in a username gets the expected exact-character match.
		escaped := likeEscaper.Replace(strings.ToLower(rawUsername))
		q = q.Where(`username_lower LIKE ? ESCAPE '\'`, "%"+escaped+"%")
	}
	if rawIP != "" {
		escaped := likeEscaper.Replace(rawIP)
		q = q.Where(`ip LIKE ? ESCAPE '\'`, escaped+"%")
	}

	// Time window: default to defaultSecurityEventsSince unless the caller
	// explicitly opted into something else. This keeps the unfiltered default
	// view bounded even when the table grows to millions of rows.
	if since, explicit := parseSinceParam(c.Query("since")); explicit {
		if !since.IsZero() {
			q = q.Where("created_at >= ?", since)
		}
	} else {
		q = q.Where("created_at >= ?", time.Now().Add(-defaultSecurityEventsSince))
	}

	var total int64
	if err := q.Count(&total).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to count security events"})
		return
	}

	var rows []db.SecurityEvent
	if err := q.Order("created_at DESC").
		Limit(pageSize).
		Offset((page - 1) * pageSize).
		Find(&rows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to query security events"})
		return
	}

	out := make([]SecurityEventResponse, 0, len(rows))
	for _, e := range rows {
		out = append(out, toSecurityEventResponse(e))
	}
	c.JSON(http.StatusOK, SecurityEventsListResponse{
		Data:     out,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// GetSecurityEvent returns a single security event by ID.
func (h *SecurityEventHandler) GetSecurityEvent(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid id"})
		return
	}
	var e db.SecurityEvent
	if err := h.DB.First(&e, id).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			c.JSON(http.StatusNotFound, gin.H{"error": "security event not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load security event"})
		return
	}
	c.JSON(http.StatusOK, toSecurityEventResponse(e))
}

// SecurityEventTypesResponse lists every event type the server may emit
// so the UI can render filter chips without hardcoding the catalog.
type SecurityEventTypesResponse struct {
	Types []string `json:"types"`
}

// GetSecurityEventTypes returns the catalog of known event types. The list
// is sourced from db.AllSecurityEventTypes so adding a new event type is a
// one-line change in one place.
func (h *SecurityEventHandler) GetSecurityEventTypes(c *gin.Context) {
	c.JSON(http.StatusOK, SecurityEventTypesResponse{
		Types: db.AllSecurityEventTypes,
	})
}
