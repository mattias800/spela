package api

import (
	"context"
	"net/http"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// ListCoresInput is the input for GET /api/cores.
type ListCoresInput struct{}

// ListCoresOutput wraps the libretro core list for the huma response envelope.
// The element type is db.Core directly (matching the raw gin handler's wire
// format — existing tests unmarshal this endpoint as []db.Core).
type ListCoresOutput struct {
	Body []db.Core
}

// RegisterCoreRoutes wires the core list endpoint into the huma API. Other
// core endpoints (download) stay on raw gin for now — this migration covers
// the simple read paths only.
func RegisterCoreRoutes(api huma.API, h *CoreHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)

	huma.Register(api, huma.Operation{
		OperationID: "listCores",
		Method:      http.MethodGet,
		Path:        "/api/cores",
		Summary:     "List libretro cores",
		Description: "Returns all libretro cores known to the server, including display name, supported platforms, and download URL (where applicable).",
		Tags:        []string{"cores"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    []map[string][]string{{"bearer": {}}},
	}, h.HumaListCores)
}

// HumaListCores is the huma handler for GET /api/cores.
func (h *CoreHandler) HumaListCores(_ context.Context, _ *ListCoresInput) (*ListCoresOutput, error) {
	var cores []db.Core
	if err := h.DB.Find(&cores).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch cores")
	}
	return &ListCoresOutput{Body: cores}, nil
}
