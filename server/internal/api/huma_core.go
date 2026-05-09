package api

import (
	"context"
	"errors"
	"net/http"
	"strconv"
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
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Core row ID (not core name)."`
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

// RefreshCoreInput is the input for POST /api/cores/{id}/refresh.
type RefreshCoreInput struct {
	ID       string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Core row ID (not core name)."`
	Platform string `query:"platform" required:"false" doc:"Platform whose on-disk binary to re-hash (linux|macos|windows|android). Defaults to linux — the default server host platform."`
}

// RefreshCoreResponse is the admin-UI payload for the refresh mutation.
// Mirrors CoreManifestResponse plus a `changed` boolean so the UI can
// differentiate "already current" from "picked up a new sha" without
// diffing before/after itself.
type RefreshCoreResponse struct {
	Changed   bool       `json:"changed" doc:"True when the on-disk sha256 differed from the previously persisted sha256."`
	OldSha256 string     `json:"oldSha256" doc:"The sha256 stored before this refresh. Empty on the first-ever fingerprint."`
	Sha256    string     `json:"sha256" doc:"Sha256 of the on-disk binary after the refresh."`
	SizeBytes int64      `json:"sizeBytes" doc:"Size of the on-disk binary after the refresh."`
	FetchedAt *time.Time `json:"fetchedAt" doc:"Timestamp persisted on the row after this refresh."`
	SourceURL string     `json:"sourceUrl" doc:"Source URL persisted on the row. Empty for buildbot-default cores."`
}

// RefreshCoreOutput is the huma response envelope for the refresh
// mutation.
type RefreshCoreOutput struct {
	Body RefreshCoreResponse
}

// RegisterCoreRoutes wires the core list + manifest + admin refresh
// endpoints into the huma API. Other core endpoints (download) stay on
// raw gin for now — this migration covers the read paths and the
// refresh mutation only.
func RegisterCoreRoutes(api huma.API, h *CoreHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	requireAdmin := RequireAdmin(api)

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

	huma.Register(api, huma.Operation{
		OperationID: "refreshCore",
		Method:      http.MethodPost,
		Path:        "/api/cores/{id}/refresh",
		Summary:     "Re-fingerprint a core binary (admin)",
		Description: "Admin-only. Re-hashes the on-disk core binary, updates the DB metadata, snapshots the new binary into the history directory, and emits a core_updated system event when the sha256 actually changed. Use after manually swapping a core binary on disk so stored fingerprints don't go stale. Returns 404 when the binary isn't on disk for the requested platform.",
		Tags:        []string{"cores"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit, requireAdmin},
		Security:    []map[string][]string{{"bearer": {}}},
	}, h.HumaRefreshCore)
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
	parsedID, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid core ID")
	}
	var core db.Core
	if err := h.DB.First(&core, uint(parsedID)).Error; err != nil {
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

// HumaRefreshCore is the huma handler for POST /api/cores/{id}/refresh.
// Admin-only. Re-hashes the on-disk binary, updates the DB row, and
// emits an audit event when the sha256 changed.
func (h *CoreHandler) HumaRefreshCore(_ context.Context, in *RefreshCoreInput) (*RefreshCoreOutput, error) {
	parsedID, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid core ID")
	}
	var core db.Core
	if err := h.DB.First(&core, uint(parsedID)).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, huma.Error404NotFound("core not found")
		}
		return nil, huma.Error500InternalServerError("failed to fetch core")
	}
	platform := in.Platform
	if platform == "" {
		platform = "linux"
	}
	result, err := h.RefreshCoreMetadata(&core, platform)
	if err != nil {
		// Binary isn't on disk for this platform — surface as 404 so
		// the admin UI can show a useful message ("upload a binary
		// for this platform first") instead of a 500.
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, huma.Error404NotFound("core binary not available on disk for platform " + platform)
		}
		return nil, huma.Error500InternalServerError("failed to refresh core: " + err.Error())
	}
	return &RefreshCoreOutput{Body: RefreshCoreResponse{
		Changed:   result.Changed,
		OldSha256: result.OldSha256,
		Sha256:    result.NewSha256,
		SizeBytes: result.SizeBytes,
		FetchedAt: result.FetchedAt,
		SourceURL: result.SourceURL,
	}}, nil
}
