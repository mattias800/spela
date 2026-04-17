package api

import (
	"context"
	"net/http"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Input / output types ---

// ListSystemEventsInput is the input for GET /api/admin/system-events.
type ListSystemEventsInput struct {
	Page      int      `query:"page" doc:"1-based page number (defaults to 1)."`
	PageSize  int      `query:"pageSize" doc:"Page size (defaults to 50, clamped to 1-200)."`
	Category  string   `query:"category" doc:"Filter by category code."`
	Dismissed string   `query:"dismissed" doc:"Set to 'true' to include dismissed events."`
	EventType []string `query:"eventType,explode" doc:"Filter by one or more event types (repeatable)."`
	Username  string   `query:"username" doc:"Case-insensitive username substring filter."`
	IP        string   `query:"ip" doc:"IP address prefix filter (IPv4/IPv6)."`
	Since     string   `query:"since" doc:"Time window: 1h, 24h/1d, 7d, 30d, all, or an RFC3339 timestamp. Defaults to the last 30 days."`
}

// ListSystemEventsOutput wraps the paginated list response.
type ListSystemEventsOutput struct {
	Body SystemEventsListResponse
}

// GetSystemEventInput is the input for GET /api/admin/system-events/{id}.
type GetSystemEventInput struct {
	ID string `path:"id" doc:"System event ID."`
}

// GetSystemEventOutput wraps a single system event response.
type GetSystemEventOutput struct {
	Body SystemEventResponse
}

// GetSystemEventTypesInput is the input for GET /api/admin/system-events/types.
type GetSystemEventTypesInput struct{}

// GetSystemEventTypesOutput wraps the types catalog.
type GetSystemEventTypesOutput struct {
	Body SystemEventTypesResponse
}

// GetSystemEventCategoriesInput is the input for GET /api/admin/system-events/categories.
type GetSystemEventCategoriesInput struct{}

// GetSystemEventCategoriesOutput wraps the categories list.
type GetSystemEventCategoriesOutput struct {
	Body []SystemEventCategoryResponse
}

// DismissSystemEventInput is the input for PUT /api/admin/system-events/{id}/dismiss.
type DismissSystemEventInput struct {
	ID string `path:"id" doc:"System event ID."`
}

// DismissedResponse is the wire format for the dismiss endpoint.
type DismissedResponse struct {
	Dismissed bool `json:"dismissed"`
}

// DismissSystemEventOutput wraps the dismiss response.
type DismissSystemEventOutput struct {
	Body DismissedResponse
}

// RegisterSystemEventRoutes wires the admin-only system-event endpoints into
// the huma API.
func RegisterSystemEventRoutes(api huma.API, h *SystemEventHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	requireAdmin := RequireAdmin(api)
	mw := huma.Middlewares{requireAuth, rateLimit, requireAdmin}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "listSystemEvents",
		Method:      http.MethodGet,
		Path:        "/api/admin/system-events",
		Summary:     "List system events",
		Description: "Admin-only. Returns a paginated, filterable list of system events (auth, audit, operational). Supports category, eventType, dismissed, username, ip and since filters.",
		Tags:        []string{"admin"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListSystemEvents)

	huma.Register(api, huma.Operation{
		OperationID: "getSystemEventTypes",
		Method:      http.MethodGet,
		Path:        "/api/admin/system-events/types",
		Summary:     "Get the system event type catalog",
		Description: "Admin-only. Returns every event type the server may emit with its category classification.",
		Tags:        []string{"admin"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetSystemEventTypes)

	huma.Register(api, huma.Operation{
		OperationID: "getSystemEventCategories",
		Method:      http.MethodGet,
		Path:        "/api/admin/system-events/categories",
		Summary:     "Get the system event categories",
		Description: "Admin-only. Returns the seeded set of system event categories.",
		Tags:        []string{"admin"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetSystemEventCategories)

	huma.Register(api, huma.Operation{
		OperationID: "getSystemEvent",
		Method:      http.MethodGet,
		Path:        "/api/admin/system-events/{id}",
		Summary:     "Get a single system event",
		Description: "Admin-only. Returns the full system event record with metadata.",
		Tags:        []string{"admin"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetSystemEvent)

	huma.Register(api, huma.Operation{
		OperationID: "dismissSystemEvent",
		Method:      http.MethodPut,
		Path:        "/api/admin/system-events/{id}/dismiss",
		Summary:     "Dismiss a system event",
		Description: "Admin-only. Marks the event as dismissed (sets dismissed_at). Dismissed events are hidden from the default list view.",
		Tags:        []string{"admin"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDismissSystemEvent)
}

// --- Handlers ---

// HumaListSystemEvents is the huma handler for GET /api/admin/system-events.
func (h *SystemEventHandler) HumaListSystemEvents(_ context.Context, in *ListSystemEventsInput) (*ListSystemEventsOutput, error) {
	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 {
		pageSize = 50
	}
	if pageSize > 200 {
		pageSize = 200
	}

	rawIP := strings.TrimSpace(in.IP)
	if !validateIPFilter(rawIP) {
		return nil, huma.Error400BadRequest("invalid ip filter: expected an IPv4 or IPv6 address or prefix")
	}
	rawUsername := strings.TrimSpace(in.Username)
	if len(rawUsername) > maxUsernameFilterLength {
		return nil, huma.Error400BadRequest("username filter too long")
	}

	q := h.DB.Model(&db.SystemEvent{})

	if cat := strings.TrimSpace(in.Category); cat != "" {
		var catRow db.SystemEventCategory
		if err := h.DB.Where("code = ?", cat).First(&catRow).Error; err == nil {
			q = q.Where("category_id = ?", catRow.ID)
		}
	}

	showDismissed := strings.TrimSpace(in.Dismissed) == "true"
	if !showDismissed {
		q = q.Where("dismissed_at IS NULL")
	}

	if len(in.EventType) > 0 {
		q = q.Where("event_type IN ?", in.EventType)
	}
	if rawUsername != "" {
		escaped := likeEscaper.Replace(strings.ToLower(rawUsername))
		q = q.Where(`username_lower LIKE ? ESCAPE '\'`, "%"+escaped+"%")
	}
	if rawIP != "" {
		escaped := likeEscaper.Replace(rawIP)
		q = q.Where(`ip LIKE ? ESCAPE '\'`, escaped+"%")
	}

	if since, explicit := parseSinceParam(in.Since); explicit {
		if !since.IsZero() {
			q = q.Where("created_at >= ?", since)
		}
	} else {
		q = q.Where("created_at >= ?", time.Now().Add(-defaultSystemEventsSince))
	}

	var total int64
	if err := q.Count(&total).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to count system events")
	}

	var rows []db.SystemEvent
	if err := q.Preload("Category").Order("created_at DESC").
		Limit(pageSize).
		Offset((page - 1) * pageSize).
		Find(&rows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to query system events")
	}

	out := make([]SystemEventResponse, 0, len(rows))
	for _, e := range rows {
		out = append(out, toSystemEventResponse(e))
	}

	return &ListSystemEventsOutput{Body: SystemEventsListResponse{
		Data:     out,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}}, nil
}

// HumaGetSystemEventTypes is the huma handler for GET /api/admin/system-events/types.
func (h *SystemEventHandler) HumaGetSystemEventTypes(_ context.Context, _ *GetSystemEventTypesInput) (*GetSystemEventTypesOutput, error) {
	types := make([]SystemEventTypeInfo, 0, len(db.AllSystemEventTypes))
	for _, t := range db.AllSystemEventTypes {
		types = append(types, SystemEventTypeInfo{
			Type:     t,
			Category: db.SystemEventTypeCategory[t],
		})
	}
	return &GetSystemEventTypesOutput{Body: SystemEventTypesResponse{Types: types}}, nil
}

// HumaGetSystemEventCategories is the huma handler for GET /api/admin/system-events/categories.
func (h *SystemEventHandler) HumaGetSystemEventCategories(_ context.Context, _ *GetSystemEventCategoriesInput) (*GetSystemEventCategoriesOutput, error) {
	var cats []db.SystemEventCategory
	if err := h.DB.Find(&cats).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to load categories")
	}
	out := make([]SystemEventCategoryResponse, 0, len(cats))
	for _, cat := range cats {
		out = append(out, SystemEventCategoryResponse{Code: cat.Code, Name: cat.Name})
	}
	return &GetSystemEventCategoriesOutput{Body: out}, nil
}

// HumaGetSystemEvent is the huma handler for GET /api/admin/system-events/{id}.
func (h *SystemEventHandler) HumaGetSystemEvent(_ context.Context, in *GetSystemEventInput) (*GetSystemEventOutput, error) {
	var e db.SystemEvent
	if err := h.DB.Preload("Category").First(&e, in.ID).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, huma.Error404NotFound("system event not found")
		}
		return nil, huma.Error500InternalServerError("failed to load system event")
	}
	return &GetSystemEventOutput{Body: toSystemEventResponse(e)}, nil
}

// HumaDismissSystemEvent is the huma handler for PUT /api/admin/system-events/{id}/dismiss.
func (h *SystemEventHandler) HumaDismissSystemEvent(_ context.Context, in *DismissSystemEventInput) (*DismissSystemEventOutput, error) {
	now := time.Now()
	result := h.DB.Model(&db.SystemEvent{}).Where("id = ?", in.ID).Update("dismissed_at", now)
	if result.Error != nil {
		return nil, huma.Error500InternalServerError("failed to dismiss event")
	}
	if result.RowsAffected == 0 {
		return nil, huma.Error404NotFound("event not found")
	}
	return &DismissSystemEventOutput{Body: DismissedResponse{Dismissed: true}}, nil
}
