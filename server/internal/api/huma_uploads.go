package api

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Input / output types ---

// CheckUploadsWritableInput is the input for GET /api/admin/uploads/writable.
type CheckUploadsWritableInput struct{}

// CheckUploadsWritableResponse is the wire format for the writable check.
type CheckUploadsWritableResponse struct {
	Writable bool   `json:"writable"`
	Reason   string `json:"reason"`
}

// CheckUploadsWritableOutput wraps the writable response.
type CheckUploadsWritableOutput struct {
	Body CheckUploadsWritableResponse
}

// ListUploadsInput is the input for GET /api/admin/uploads.
type ListUploadsInput struct{}

// ListUploadsOutput wraps the staged uploads list.
type ListUploadsOutput struct {
	Body []StagedUploadResponse
}

// SetUploadConsoleInput is the input for POST /api/admin/uploads/{id}/console.
type SetUploadConsoleInput struct {
	ID   string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Staged upload ID."`
	Body SetUploadConsoleRequest
}

// SetUploadConsoleOutput wraps the updated upload response.
type SetUploadConsoleOutput struct {
	Body StagedUploadResponse
}

// ScrapeUploadInput is the input for POST /api/admin/uploads/{id}/scrape.
type ScrapeUploadInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Staged upload ID."`
}

// ScrapeUploadOutput wraps the scraped upload response.
type ScrapeUploadOutput struct {
	Body StagedUploadResponse
}

// ScrapeAllUploadsInput is the input for POST /api/admin/uploads/scrape.
type ScrapeAllUploadsInput struct{}

// ScrapeAllUploadsOutput wraps the scraped uploads list.
type ScrapeAllUploadsOutput struct {
	Body []StagedUploadResponse
}

// AcceptUploadInput is the input for POST /api/admin/uploads/{id}/accept.
type AcceptUploadInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Staged upload ID."`
}

// AcceptUploadOutput wraps the accepted game response.
type AcceptUploadOutput struct {
	Body GameResponse
}

// RejectUploadInput is the input for POST /api/admin/uploads/{id}/reject.
type RejectUploadInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Staged upload ID."`
}

// RejectUploadOutput wraps the reject success message.
type RejectUploadOutput struct {
	Body MessageResponse
}

// AcceptAllUploadsInput is the input for POST /api/admin/uploads/accept-all.
type AcceptAllUploadsInput struct{}

// AcceptAllUploadsOutput wraps the accept-all response. A raw map is used so
// the huma response body matches the historical `{"accepted": N}` shape
// exactly — wrapping in a named struct causes huma to inject a `$schema`
// field, which changes the wire format.
type AcceptAllUploadsOutput struct {
	Body map[string]int
}

// RejectAllUploadsInput is the input for POST /api/admin/uploads/reject-all.
type RejectAllUploadsInput struct{}

// RejectAllUploadsOutput wraps the reject-all response (raw map to avoid the
// `$schema` field huma adds to struct responses).
type RejectAllUploadsOutput struct {
	Body map[string]int
}

// ClearStagingInput is the input for DELETE /api/admin/uploads.
type ClearStagingInput struct{}

// ClearStagingOutput wraps the clear-staging response (raw map to avoid the
// `$schema` field huma adds to struct responses).
type ClearStagingOutput struct {
	Body map[string]int
}

// RegisterUploadAdminRoutes wires the JSON-only admin upload staging endpoints
// into the huma API. The multipart POST /api/admin/uploads (HumaUploadROMs)
// is registered separately by RegisterAdminMultipartRoutes.
func RegisterUploadAdminRoutes(api huma.API, h *UploadHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	requireAdmin := RequireAdmin(api)
	adminMW := huma.Middlewares{requireAuth, rateLimit, requireAdmin}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "checkUploadsWritable",
		Method:      http.MethodGet,
		Path:        "/api/admin/uploads/writable",
		Summary:     "Check whether the game library directory is writable",
		Description: "Admin-only. Verifies that the staging/game library directory is writable.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaCheckWritable)

	huma.Register(api, huma.Operation{
		OperationID: "listUploads",
		Method:      http.MethodGet,
		Path:        "/api/admin/uploads",
		Summary:     "List staged uploads",
		Description: "Admin-only. Returns all currently staged ROM uploads, newest first.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaListUploads)

	huma.Register(api, huma.Operation{
		OperationID: "setUploadConsole",
		Method:      http.MethodPost,
		Path:        "/api/admin/uploads/{id}/console",
		Summary:     "Set the console for a staged upload",
		Description: "Admin-only. Resolves console ambiguity on a staged upload by explicitly choosing the console abbreviation.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaSetConsole)

	huma.Register(api, huma.Operation{
		OperationID: "scrapeUpload",
		Method:      http.MethodPost,
		Path:        "/api/admin/uploads/{id}/scrape",
		Summary:     "Scrape metadata for a staged upload",
		Description: "Admin-only. Runs metadata scraping + duplicate detection on a staged upload. Requires the console to be set first.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaScrapeUpload)

	huma.Register(api, huma.Operation{
		OperationID: "scrapeAllUploads",
		Method:      http.MethodPost,
		Path:        "/api/admin/uploads/scrape",
		Summary:     "Scrape all pending staged uploads",
		Description: "Admin-only. Runs metadata scraping + duplicate detection on every upload currently in pending_scrape state.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaScrapeAllUploads)

	huma.Register(api, huma.Operation{
		OperationID: "acceptUpload",
		Method:      http.MethodPost,
		Path:        "/api/admin/uploads/{id}/accept",
		Summary:     "Accept a staged upload",
		Description: "Admin-only. Moves the staged ROM into the library and creates a Game record.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaAcceptUpload)

	huma.Register(api, huma.Operation{
		OperationID: "rejectUpload",
		Method:      http.MethodPost,
		Path:        "/api/admin/uploads/{id}/reject",
		Summary:     "Reject a staged upload",
		Description: "Admin-only. Deletes the staged ROM from disk and drops the staging record.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaRejectUpload)

	huma.Register(api, huma.Operation{
		OperationID: "acceptAllUploads",
		Method:      http.MethodPost,
		Path:        "/api/admin/uploads/accept-all",
		Summary:     "Accept all non-duplicate staged uploads",
		Description: "Admin-only. Accepts every upload currently in ready or pending_scrape state (with a console set).",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaAcceptAllUploads)

	huma.Register(api, huma.Operation{
		OperationID: "rejectAllUploads",
		Method:      http.MethodPost,
		Path:        "/api/admin/uploads/reject-all",
		Summary:     "Reject all staged uploads",
		Description: "Admin-only. Deletes every staged ROM from disk and drops the staging records.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaRejectAllUploads)

	huma.Register(api, huma.Operation{
		OperationID: "clearStaging",
		Method:      http.MethodDelete,
		Path:        "/api/admin/uploads",
		Summary:     "Clear the staging directory",
		Description: "Admin-only. Deletes every staged ROM from disk and drops all staging records. Alias for reject-all.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaClearStaging)
}

// --- Handlers ---

// HumaCheckWritable is the huma handler for GET /api/admin/uploads/writable.
func (h *UploadHandler) HumaCheckWritable(_ context.Context, _ *CheckUploadsWritableInput) (*CheckUploadsWritableOutput, error) {
	gameDir := h.GameDirs[0]
	tmpPath := filepath.Join(gameDir, ".spela-write-test")
	f, err := os.Create(tmpPath)
	if err != nil {
		return &CheckUploadsWritableOutput{Body: CheckUploadsWritableResponse{
			Writable: false,
			Reason:   "Game library directory is read-only",
		}}, nil
	}
	f.Close()
	defer os.Remove(tmpPath)
	return &CheckUploadsWritableOutput{Body: CheckUploadsWritableResponse{Writable: true}}, nil
}

// HumaListUploads is the huma handler for GET /api/admin/uploads.
func (h *UploadHandler) HumaListUploads(_ context.Context, _ *ListUploadsInput) (*ListUploadsOutput, error) {
	var uploads []db.StagedUpload
	if err := h.DB.Order("created_at desc").Find(&uploads).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch uploads")
	}

	results := make([]StagedUploadResponse, len(uploads))
	for i, u := range uploads {
		results[i] = toStagedUploadResponse(u, h.DB)
	}
	return &ListUploadsOutput{Body: results}, nil
}

// HumaSetConsole is the huma handler for POST /api/admin/uploads/{id}/console.
func (h *UploadHandler) HumaSetConsole(_ context.Context, in *SetUploadConsoleInput) (*SetUploadConsoleOutput, error) {
	var staged db.StagedUpload
	if err := h.DB.First(&staged, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("upload not found")
	}

	if in.Body.ConsoleID == "" {
		return nil, huma.Error400BadRequest("consoleId required")
	}

	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", in.Body.ConsoleID).First(&console).Error; err != nil {
		return nil, huma.Error400BadRequest("unknown console")
	}

	staged.ConsoleID = &console.ID
	staged.Status = "pending_scrape"
	if err := h.DB.Save(&staged).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update upload")
	}

	return &SetUploadConsoleOutput{Body: toStagedUploadResponse(staged, h.DB)}, nil
}

// HumaScrapeUpload is the huma handler for POST /api/admin/uploads/{id}/scrape.
func (h *UploadHandler) HumaScrapeUpload(_ context.Context, in *ScrapeUploadInput) (*ScrapeUploadOutput, error) {
	var staged db.StagedUpload
	if err := h.DB.First(&staged, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("upload not found")
	}

	if staged.ConsoleID == nil {
		return nil, huma.Error400BadRequest("console must be set before scraping")
	}

	h.scrapeStaged(&staged)

	return &ScrapeUploadOutput{Body: toStagedUploadResponse(staged, h.DB)}, nil
}

// HumaScrapeAllUploads is the huma handler for POST /api/admin/uploads/scrape.
func (h *UploadHandler) HumaScrapeAllUploads(_ context.Context, _ *ScrapeAllUploadsInput) (*ScrapeAllUploadsOutput, error) {
	var uploads []db.StagedUpload
	if err := h.DB.Where("status = ?", "pending_scrape").Find(&uploads).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch uploads")
	}

	for i := range uploads {
		h.scrapeStaged(&uploads[i])
	}

	results := make([]StagedUploadResponse, len(uploads))
	for i, u := range uploads {
		results[i] = toStagedUploadResponse(u, h.DB)
	}
	return &ScrapeAllUploadsOutput{Body: results}, nil
}

// HumaAcceptUpload is the huma handler for POST /api/admin/uploads/{id}/accept.
func (h *UploadHandler) HumaAcceptUpload(ctx context.Context, in *AcceptUploadInput) (*AcceptUploadOutput, error) {
	var staged db.StagedUpload
	if err := h.DB.First(&staged, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("upload not found")
	}

	if staged.ConsoleID == nil {
		return nil, huma.Error400BadRequest("console must be set before accepting")
	}

	game, err := h.acceptStaged(&staged)
	if err != nil {
		slog.Warn("failed to accept upload", "id", in.ID, "error", err)
		return nil, huma.Error500InternalServerError("failed to accept upload")
	}

	uid := UserIDFromContext(ctx)
	h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").First(game, game.ID)
	return &AcceptUploadOutput{Body: ToGameResponse(*game, h.DB, uid)}, nil
}

// HumaRejectUpload is the huma handler for POST /api/admin/uploads/{id}/reject.
func (h *UploadHandler) HumaRejectUpload(_ context.Context, in *RejectUploadInput) (*RejectUploadOutput, error) {
	var staged db.StagedUpload
	if err := h.DB.First(&staged, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("upload not found")
	}

	os.Remove(staged.FilePath)
	h.DB.Delete(&staged)

	return &RejectUploadOutput{Body: MessageResponse{Message: "upload rejected"}}, nil
}

// HumaAcceptAllUploads is the huma handler for POST /api/admin/uploads/accept-all.
func (h *UploadHandler) HumaAcceptAllUploads(_ context.Context, _ *AcceptAllUploadsInput) (*AcceptAllUploadsOutput, error) {
	var uploads []db.StagedUpload
	if err := h.DB.Where("status IN ? AND console_id IS NOT NULL", []string{"ready", "pending_scrape"}).Find(&uploads).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch uploads")
	}

	var accepted int
	for i := range uploads {
		if _, err := h.acceptStaged(&uploads[i]); err != nil {
			slog.Warn("failed to accept staged upload", "id", uploads[i].ID, "error", err)
			continue
		}
		accepted++
	}

	return &AcceptAllUploadsOutput{Body: map[string]int{"accepted": accepted}}, nil
}

// HumaRejectAllUploads is the huma handler for POST /api/admin/uploads/reject-all.
func (h *UploadHandler) HumaRejectAllUploads(_ context.Context, _ *RejectAllUploadsInput) (*RejectAllUploadsOutput, error) {
	var uploads []db.StagedUpload
	if err := h.DB.Find(&uploads).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch uploads")
	}

	for _, u := range uploads {
		os.Remove(u.FilePath)
	}
	h.DB.Where("1 = 1").Delete(&db.StagedUpload{})

	return &RejectAllUploadsOutput{Body: map[string]int{"rejected": len(uploads)}}, nil
}

// HumaClearStaging is the huma handler for DELETE /api/admin/uploads.
func (h *UploadHandler) HumaClearStaging(_ context.Context, _ *ClearStagingInput) (*ClearStagingOutput, error) {
	var uploads []db.StagedUpload
	if err := h.DB.Find(&uploads).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch uploads")
	}

	for _, u := range uploads {
		os.Remove(u.FilePath)
	}
	h.DB.Where("1 = 1").Delete(&db.StagedUpload{})

	return &ClearStagingOutput{Body: map[string]int{"cleared": len(uploads)}}, nil
}
