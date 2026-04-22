package api

import (
	"context"
	"net/http"
	"time"

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

// CoreManifestInput is the input for GET /api/cores/{id}/manifest.
type CoreManifestInput struct {
	ID uint `path:"id" doc:"Core row ID (not core name)."`
}

// CoreManifestResponse is the lightweight fingerprint payload players fetch
// to decide whether their locally cached binary is still current. Kept narrow
// so the player can poll many cores cheaply without pulling the full Core
// row on every check. See #555 Phase 2.
type CoreManifestResponse struct {
	Sha256    string     `json:"sha256" doc:"Hex sha256 of the cached binary on the server. Empty if the server hasn't served this core yet."`
	SizeBytes int64      `json:"sizeBytes" doc:"Byte length of the cached binary. 0 when no binary has been fetched yet."`
	FetchedAt *time.Time `json:"fetchedAt" doc:"When the server last downloaded (or re-hashed) this core. Null if it has never been fetched."`
	SourceURL string     `json:"sourceUrl" doc:"URL the server pulled this binary from. Empty when the server is still defaulting to the buildbot nightly endpoint."`
}

// CoreManifestOutput is the huma response envelope for the manifest endpoint.
type CoreManifestOutput struct {
	Body CoreManifestResponse
}

// RegisterCoreRoutes wires the core list + manifest endpoints into the huma
// API. Other core endpoints (download) stay on raw gin for now — this
// migration covers the read paths only.
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

	huma.Register(api, huma.Operation{
		OperationID: "getCoreManifest",
		Method:      http.MethodGet,
		Path:        "/api/cores/{id}/manifest",
		Summary:     "Get fingerprint for a core binary",
		Description: "Returns sha256 + size + fetched-at for the server's current cached binary of a core. Players use this to decide whether their locally cached copy is stale without re-downloading the binary itself.",
		Tags:        []string{"cores"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    []map[string][]string{{"bearer": {}}},
	}, h.HumaGetCoreManifest)
}

// HumaListCores is the huma handler for GET /api/cores.
func (h *CoreHandler) HumaListCores(_ context.Context, _ *ListCoresInput) (*ListCoresOutput, error) {
	var cores []db.Core
	if err := h.DB.Find(&cores).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch cores")
	}
	return &ListCoresOutput{Body: cores}, nil
}

// HumaGetCoreManifest is the huma handler for GET /api/cores/{id}/manifest.
func (h *CoreHandler) HumaGetCoreManifest(_ context.Context, in *CoreManifestInput) (*CoreManifestOutput, error) {
	var core db.Core
	if err := h.DB.First(&core, in.ID).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, huma.Error404NotFound("core not found")
		}
		return nil, huma.Error500InternalServerError("failed to fetch core")
	}
	return &CoreManifestOutput{Body: CoreManifestResponse{
		Sha256:    core.Sha256,
		SizeBytes: core.SizeBytes,
		FetchedAt: core.FetchedAt,
		SourceURL: core.SourceURL,
	}}, nil
}
