package api

import (
	"encoding/json"
	"log/slog"
	"regexp"
	"strings"
	"time"

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
	Reason       string         `json:"reason"`
	Username     string         `json:"username"`
	UserID       *uint          `json:"userId"`
	IP           string         `json:"ip"`
	Path         string         `json:"path"`
	Metadata     map[string]any `json:"metadata"`
	MetadataRaw  string         `json:"metadataRaw"`
	DismissedAt  *time.Time     `json:"dismissedAt"`
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

// SystemEventTypeInfo pairs an event type with its category.
type SystemEventTypeInfo struct {
	Type     string `json:"type"`
	Category string `json:"category"`
}

// SystemEventTypesResponse lists every event type the server may emit.
type SystemEventTypesResponse struct {
	Types []SystemEventTypeInfo `json:"types"`
}

