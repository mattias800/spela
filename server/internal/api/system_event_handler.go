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

// SystemEventHandler serves the admin-only system event log endpoints
// that back the /admin/system-events page in the web UI.
type SystemEventHandler struct {
	DB *gorm.DB
}

// SystemEventResponse is the JSON shape returned by the API.
type SystemEventResponse struct {
	ID           uint           `json:"id"`
	CreatedAt    time.Time      `json:"createdAt"`
	CategoryCode string         `json:"categoryCode"`
	CategoryName string         `json:"categoryName"`
	EventType    string         `json:"eventType"`
	Reason       string         `json:"reason,omitempty"`
	Username     string         `json:"username,omitempty"`
	UserID       *uint          `json:"userId,omitempty"`
	IP           string         `json:"ip,omitempty"`
	Path         string         `json:"path,omitempty"`
	Metadata     map[string]any `json:"metadata,omitempty"`
	MetadataRaw  string         `json:"metadataRaw,omitempty"`
	DismissedAt  *time.Time     `json:"dismissedAt,omitempty"`
}

// SystemEventsListResponse is the paginated list payload.
type SystemEventsListResponse struct {
	Data     []SystemEventResponse `json:"data"`
	Total    int64                 `json:"total"`
	Page     int                   `json:"page"`
	PageSize int                   `json:"pageSize"`
}

// defaultSystemEventsSince bounds the default list query when no `since`
// parameter is supplied.
const defaultSystemEventsSince = 30 * 24 * time.Hour

// maxUsernameFilterLength caps the `username` query parameter.
const maxUsernameFilterLength = 128

// toSystemEventResponse converts a db row to its API representation.
func toSystemEventResponse(e db.SystemEvent) SystemEventResponse {
	r := SystemEventResponse{
		ID:           e.ID,
		CreatedAt:    e.CreatedAt,
		CategoryCode: e.Category.Code,
		CategoryName: e.Category.Name,
		EventType:    e.EventType,
		Reason:       e.Reason,
		Username:     e.Username,
		UserID:       e.UserID,
		IP:           e.IP,
		Path:         e.Path,
		DismissedAt:  e.DismissedAt,
	}
	if e.Metadata != "" {
		var m map[string]any
		if err := json.Unmarshal([]byte(e.Metadata), &m); err == nil {
			r.Metadata = m
		} else {
			slog.Warn("failed to parse system event metadata JSON",
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

// truncateForLog returns s bounded to max runes, appending "..." when truncated.
func truncateForLog(s string, max int) string {
	if len(s) <= max {
		return s
	}
	return s[:max] + "..."
}

// parseSinceParam converts a "since" query parameter to a time.Time.
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
	return time.Time{}, false
}

var ipFilterPattern = regexp.MustCompile(`^[0-9a-fA-F.:]{1,45}$`)

func validateIPFilter(raw string) bool {
	if raw == "" {
		return true
	}
	return ipFilterPattern.MatchString(raw)
}

var likeEscaper = strings.NewReplacer(
	`\`, `\\`,
	`%`, `\%`,
	`_`, `\_`,
)

// ListSystemEvents returns a paginated, filterable list of system events.
func (h *SystemEventHandler) ListSystemEvents(c *gin.Context) {
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

	rawIP := strings.TrimSpace(c.Query("ip"))
	if !validateIPFilter(rawIP) {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid ip filter: expected an IPv4 or IPv6 address or prefix"})
		return
	}

	rawUsername := strings.TrimSpace(c.Query("username"))
	if len(rawUsername) > maxUsernameFilterLength {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "username filter too long"})
		return
	}

	q := h.DB.Model(&db.SystemEvent{})

	// Category filter
	if cat := strings.TrimSpace(c.Query("category")); cat != "" {
		var catRow db.SystemEventCategory
		if err := h.DB.Where("code = ?", cat).First(&catRow).Error; err == nil {
			q = q.Where("category_id = ?", catRow.ID)
		}
	}

	// Dismissed filter — default to excluding dismissed events
	showDismissed := strings.TrimSpace(c.Query("dismissed")) == "true"
	if !showDismissed {
		q = q.Where("dismissed_at IS NULL")
	}

	if types := c.QueryArray("eventType"); len(types) > 0 {
		q = q.Where("event_type IN ?", types)
	}
	if rawUsername != "" {
		escaped := likeEscaper.Replace(strings.ToLower(rawUsername))
		q = q.Where(`username_lower LIKE ? ESCAPE '\'`, "%"+escaped+"%")
	}
	if rawIP != "" {
		escaped := likeEscaper.Replace(rawIP)
		q = q.Where(`ip LIKE ? ESCAPE '\'`, escaped+"%")
	}

	if since, explicit := parseSinceParam(c.Query("since")); explicit {
		if !since.IsZero() {
			q = q.Where("created_at >= ?", since)
		}
	} else {
		q = q.Where("created_at >= ?", time.Now().Add(-defaultSystemEventsSince))
	}

	var total int64
	if err := q.Count(&total).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to count system events"})
		return
	}

	var rows []db.SystemEvent
	if err := q.Preload("Category").Order("created_at DESC").
		Limit(pageSize).
		Offset((page - 1) * pageSize).
		Find(&rows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to query system events"})
		return
	}

	out := make([]SystemEventResponse, 0, len(rows))
	for _, e := range rows {
		out = append(out, toSystemEventResponse(e))
	}
	c.JSON(http.StatusOK, SystemEventsListResponse{
		Data:     out,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// GetSystemEvent returns a single system event by ID.
func (h *SystemEventHandler) GetSystemEvent(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid id"})
		return
	}
	var e db.SystemEvent
	if err := h.DB.Preload("Category").First(&e, id).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			c.JSON(http.StatusNotFound, ErrorResponse{Error: "system event not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to load system event"})
		return
	}
	c.JSON(http.StatusOK, toSystemEventResponse(e))
}

// SystemEventTypeInfo pairs an event type with its category.
type SystemEventTypeInfo struct {
	Type     string `json:"type"`
	Category string `json:"category"`
}

// SystemEventTypesResponse lists every event type the server may emit.
type SystemEventTypesResponse struct {
	Types []SystemEventTypeInfo `json:"types"`
}

// GetSystemEventTypes returns the catalog of known event types with categories.
func (h *SystemEventHandler) GetSystemEventTypes(c *gin.Context) {
	types := make([]SystemEventTypeInfo, 0, len(db.AllSystemEventTypes))
	for _, t := range db.AllSystemEventTypes {
		types = append(types, SystemEventTypeInfo{
			Type:     t,
			Category: db.SystemEventTypeCategory[t],
		})
	}
	c.JSON(http.StatusOK, SystemEventTypesResponse{Types: types})
}

// GetSystemEventCategories returns the seeded categories.
func (h *SystemEventHandler) GetSystemEventCategories(c *gin.Context) {
	var cats []db.SystemEventCategory
	if err := h.DB.Find(&cats).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to load categories"})
		return
	}
	out := make([]SystemEventCategoryResponse, 0, len(cats))
	for _, cat := range cats {
		out = append(out, SystemEventCategoryResponse{Code: cat.Code, Name: cat.Name})
	}
	c.JSON(http.StatusOK, out)
}

// DismissSystemEvent sets dismissed_at on a single event.
func (h *SystemEventHandler) DismissSystemEvent(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid id"})
		return
	}
	now := time.Now()
	result := h.DB.Model(&db.SystemEvent{}).Where("id = ?", id).Update("dismissed_at", now)
	if result.Error != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to dismiss event"})
		return
	}
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "event not found"})
		return
	}
	c.JSON(http.StatusOK, gin.H{"dismissed": true})
}

// ReportEmulatorError accepts a client-side emulator error report from
// authenticated users and records it as an operational system event. This
// gives admins visibility into player-facing issues (core download failures,
// WASM compilation errors, missing ROMs) that otherwise only appear in
// the user's browser console.
func (h *SystemEventHandler) ReportEmulatorError(c *gin.Context) {
	var req ReportEmulatorErrorRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request body"})
		return
	}

	// Extract user info from the auth context
	userID, _ := c.Get("userID")
	username, _ := c.Get("username")

	var uid *uint
	if id, ok := userID.(uint); ok {
		uid = &id
	}
	var uname string
	if name, ok := username.(string); ok {
		uname = name
	}

	metadata := map[string]any{
		"error": req.Error,
	}
	if req.GameID != "" {
		metadata["gameId"] = req.GameID
	}
	if req.Core != "" {
		metadata["core"] = req.Core
	}

	db.RecordOperationalEvent(h.DB, db.SystemEventInput{
		EventType: db.SystemEventEmulatorJSLoadFailed,
		Username:  uname,
		UserID:    uid,
		IP:        c.ClientIP(),
		Path:      c.Request.URL.Path,
		Metadata:  metadata,
	})

	c.JSON(http.StatusOK, gin.H{"reported": true})
}
