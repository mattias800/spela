package api

import (
	"encoding/json"
	"net/http"
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
type SecurityEventResponse struct {
	ID        uint           `json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	EventType string         `json:"eventType"`
	Reason    string         `json:"reason,omitempty"`
	Username  string         `json:"username,omitempty"`
	UserID    *uint          `json:"userId,omitempty"`
	IP        string         `json:"ip,omitempty"`
	Path      string         `json:"path,omitempty"`
	Metadata  map[string]any `json:"metadata,omitempty"`
}

// SecurityEventsListResponse is the paginated list payload.
type SecurityEventsListResponse struct {
	Data     []SecurityEventResponse `json:"data"`
	Total    int64                   `json:"total"`
	Page     int                     `json:"page"`
	PageSize int                     `json:"pageSize"`
}

// toSecurityEventResponse converts a db row to its API representation,
// parsing the metadata JSON blob into a map for easier client consumption.
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
		}
	}
	return r
}

// parseSinceParam converts a "since" query parameter to a time.Time.
// Accepts presets ("1h", "24h", "7d", "30d", "all") or an RFC3339 timestamp.
// Returns the zero time when "all" or empty.
func parseSinceParam(raw string) time.Time {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "", "all":
		return time.Time{}
	case "1h":
		return time.Now().Add(-1 * time.Hour)
	case "24h", "1d":
		return time.Now().Add(-24 * time.Hour)
	case "7d":
		return time.Now().Add(-7 * 24 * time.Hour)
	case "30d":
		return time.Now().Add(-30 * 24 * time.Hour)
	}
	if t, err := time.Parse(time.RFC3339, raw); err == nil {
		return t
	}
	return time.Time{}
}

// ListSecurityEvents returns a paginated, filterable list of security events.
//
// Query parameters:
//
//	page       — 1-based page number, default 1
//	pageSize   — rows per page, default 50, max 200
//	eventType  — repeatable; restricts to one or more event types
//	username   — case-insensitive substring match on username
//	ip         — prefix match on the IP field
//	since      — preset (1h, 24h, 7d, 30d, all) or RFC3339 timestamp
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

	q := h.DB.Model(&db.SecurityEvent{})

	if types := c.QueryArray("eventType"); len(types) > 0 {
		q = q.Where("event_type IN ?", types)
	}
	if username := strings.TrimSpace(c.Query("username")); username != "" {
		// Filter uses the denormalized username_lower column so SQLite can
		// serve the query from an index. See SecurityEvent.UsernameLower for
		// why this exists.
		q = q.Where("username_lower LIKE ?", "%"+strings.ToLower(username)+"%")
	}
	if ip := strings.TrimSpace(c.Query("ip")); ip != "" {
		q = q.Where("ip LIKE ?", ip+"%")
	}
	if since := parseSinceParam(c.Query("since")); !since.IsZero() {
		q = q.Where("created_at >= ?", since)
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

// GetSecurityEventTypes returns the catalog of known event types.
func (h *SecurityEventHandler) GetSecurityEventTypes(c *gin.Context) {
	c.JSON(http.StatusOK, SecurityEventTypesResponse{
		Types: []string{
			db.SecurityEventLoginSuccess,
			db.SecurityEventLoginFailed,
			db.SecurityEventLoginLocked,
			db.SecurityEventLoginBlocked,
			db.SecurityEventAccountLocked,
			db.SecurityEventRevokedTokenUsed,
			db.SecurityEventDisabledAccountToken,
			db.SecurityEventTokenUserMissing,
			db.SecurityEventStaleTokenVersion,
		},
	})
}
