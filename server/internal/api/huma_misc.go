package api

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Input / output types: storage ---

// GetStorageUsageInput is the input for GET /api/user/storage.
type GetStorageUsageInput struct{}

// StorageUsageResponse is the wire format for the storage-usage response.
type StorageUsageResponse struct {
	UsedBytes  int64                            `json:"usedBytes"`
	QuotaBytes int64                            `json:"quotaBytes"`
	ByConsole  []SessionStorageConsoleBreakdown `json:"byConsole"`
}

// GetStorageUsageOutput wraps the storage usage response.
type GetStorageUsageOutput struct {
	Body StorageUsageResponse
}

// CompactSavesInput is the input for POST /api/user/saves/compact.
type CompactSavesInput struct{}

// CompactSavesResponse is the wire format for the compact-saves response.
type CompactSavesResponse struct {
	DeletedCount int   `json:"deletedCount"`
	FreedBytes   int64 `json:"freedBytes"`
}

// CompactSavesOutput wraps the compact-saves response.
type CompactSavesOutput struct {
	Body CompactSavesResponse
}

// --- Input / output types: saved searches ---

// CreateSavedSearchInput is the input for POST /api/user/saved-searches.
type CreateSavedSearchInput struct {
	Body SavedSearchRequest
}

// CreateSavedSearchOutput wraps the saved-search response (201 Created).
type CreateSavedSearchOutput struct {
	Body SavedSearchResponse
}

// ListSavedSearchesInput is the input for GET /api/user/saved-searches.
type ListSavedSearchesInput struct{}

// ListSavedSearchesOutput wraps the saved-search list.
type ListSavedSearchesOutput struct {
	Body []SavedSearchResponse
}

// DeleteSavedSearchInput is the input for DELETE /api/user/saved-searches/{id}.
type DeleteSavedSearchInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Saved search ID."`
}

// DeletedStatusResponse is the wire format for delete responses that return a status.
type DeletedStatusResponse struct {
	Status string `json:"status"`
}

// DeleteSavedSearchOutput wraps the delete response.
type DeleteSavedSearchOutput struct {
	Body DeletedStatusResponse
}

// --- Input / output types: metadata matches ---

// MetadataMatchesInput is the input for GET /api/admin/metadata-matches.
type MetadataMatchesInput struct{}

// MetadataMatchesResponse is the wire format for admin metadata-matches.
type MetadataMatchesResponse struct {
	Unscraped  []GameResponse `json:"unscraped"`
	Unverified []GameResponse `json:"unverified"`
	Incomplete []GameResponse `json:"incomplete"`
}

// MetadataMatchesOutput wraps the metadata-matches response.
type MetadataMatchesOutput struct {
	Body MetadataMatchesResponse
}

// --- Input / output types: core compatibility ---

// GetCoreCompatibilityInput is the input for GET /api/admin/core-compatibility.
type GetCoreCompatibilityInput struct{}

// CoreCompatibilityResponse is the wire format for the core-compatibility response.
type CoreCompatibilityResponse struct {
	Consoles []CoreCompatibilityEntry `json:"consoles"`
}

// GetCoreCompatibilityOutput wraps the core-compatibility response.
type GetCoreCompatibilityOutput struct {
	Body CoreCompatibilityResponse
}

// --- Input / output types: rate limit admin ---

// GetUserRateLimitInput is the input for GET /api/admin/users/{id}/rate-limit.
type GetUserRateLimitInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"User ID."`
}

// GetUserRateLimitOutput wraps the rate-limit response.
type GetUserRateLimitOutput struct {
	Body RateLimitResponse
}

// ResetUserRateLimitInput is the input for DELETE /api/admin/users/{id}/rate-limit.
type ResetUserRateLimitInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"User ID."`
}

// ResetUserRateLimitOutput wraps the rate-limit reset response.
type ResetUserRateLimitOutput struct {
	Body MessageResponse
}

// RegisterMiscRoutes wires storage, saved-search, metadata-match,
// core-compatibility and admin rate-limit endpoints into the huma API.
func RegisterMiscRoutes(api huma.API, sessionHandler *SessionHandler, savedSearchHandler *SavedSearchHandler, adminHandler *AdminHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	requireAdmin := RequireAdmin(api)
	mw := huma.Middlewares{requireAuth, rateLimit}
	adminMW := huma.Middlewares{requireAuth, rateLimit, requireAdmin}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getStorageUsage",
		Method:      http.MethodGet,
		Path:        "/api/user/storage",
		Summary:     "Get storage usage",
		Description: "Returns the caller's total save storage, per-console breakdown and quota.",
		Tags:        []string{"user"},
		Middlewares: mw,
		Security:    sec,
	}, sessionHandler.HumaGetStorageUsage)

	huma.Register(api, huma.Operation{
		OperationID: "compactSaves",
		Method:      http.MethodPost,
		Path:        "/api/user/saves/compact",
		Summary:     "Compact save states",
		Description: "Keeps only the most recent auto-save and manual save per session for the caller. Aggressive cleanup; use when quota is tight.",
		Tags:        []string{"user"},
		Middlewares: mw,
		Security:    sec,
	}, sessionHandler.HumaCompactSaves)

	huma.Register(api, huma.Operation{
		OperationID:   "createSavedSearch",
		Method:        http.MethodPost,
		Path:          "/api/user/saved-searches",
		Summary:       "Create a saved search",
		Description:   "Persists a named search filter for the caller. Up to 50 saved searches per user.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"user"},
		Middlewares:   mw,
		Security:      sec,
	}, savedSearchHandler.HumaCreateSavedSearch)

	huma.Register(api, huma.Operation{
		OperationID: "listSavedSearches",
		Method:      http.MethodGet,
		Path:        "/api/user/saved-searches",
		Summary:     "List saved searches",
		Description: "Returns all saved searches for the caller, newest first.",
		Tags:        []string{"user"},
		Middlewares: mw,
		Security:    sec,
	}, savedSearchHandler.HumaListSavedSearches)

	huma.Register(api, huma.Operation{
		OperationID: "deleteSavedSearch",
		Method:      http.MethodDelete,
		Path:        "/api/user/saved-searches/{id}",
		Summary:     "Delete a saved search",
		Description: "Soft-deletes a saved search owned by the caller.",
		Tags:        []string{"user"},
		Middlewares: mw,
		Security:    sec,
	}, savedSearchHandler.HumaDeleteSavedSearch)

	huma.Register(api, huma.Operation{
		OperationID: "getMetadataMatches",
		Method:      http.MethodGet,
		Path:        "/api/admin/metadata-matches",
		Summary:     "Get games needing metadata attention",
		Description: "Admin-only. Returns unscraped, unverified, and incomplete games (scraped only via LibRetro fallback).",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, adminHandler.HumaMetadataMatches)

	huma.Register(api, huma.Operation{
		OperationID: "getCoreCompatibility",
		Method:      http.MethodGet,
		Path:        "/api/admin/core-compatibility",
		Summary:     "Get core compatibility table",
		Description: "Admin-only. Returns the compatibility matrix between native-player cores and EmulatorJS cores per console.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, adminHandler.HumaGetCoreCompatibility)

	huma.Register(api, huma.Operation{
		OperationID: "getUserRateLimit",
		Method:      http.MethodGet,
		Path:        "/api/admin/users/{id}/rate-limit",
		Summary:     "Get a user's login rate-limit status",
		Description: "Admin-only. Returns failed login counters and lockout status for a user.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, adminHandler.HumaGetUserRateLimit)

	huma.Register(api, huma.Operation{
		OperationID: "resetUserRateLimit",
		Method:      http.MethodDelete,
		Path:        "/api/admin/users/{id}/rate-limit",
		Summary:     "Reset a user's login rate-limit",
		Description: "Admin-only. Clears failed-login counters for a user.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, adminHandler.HumaResetUserRateLimit)
}

// --- Handlers: SessionHandler storage/compact ---

// HumaGetStorageUsage is the huma handler for GET /api/user/storage.
func (h *SessionHandler) HumaGetStorageUsage(ctx context.Context, _ *GetStorageUsageInput) (*GetStorageUsageOutput, error) {
	uid := UserIDFromContext(ctx)

	var usedBytes int64
	if err := h.DB.Model(&db.SessionSaveState{}).Where("user_id = ?", uid).
		Select("COALESCE(SUM(file_size), 0)").Scan(&usedBytes).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to query storage usage")
	}

	var rows []SessionStorageConsoleBreakdown
	h.DB.Model(&db.SessionSaveState{}).
		Joins("JOIN game_sessions ON game_sessions.id = session_save_states.session_id AND game_sessions.deleted_at IS NULL").
		Joins("JOIN games ON games.id = game_sessions.game_id AND games.deleted_at IS NULL").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Where("session_save_states.user_id = ? AND session_save_states.deleted_at IS NULL", uid).
		Select("consoles.abbreviation AS console_id, COALESCE(SUM(session_save_states.file_size), 0) AS bytes, COUNT(*) AS save_count").
		Group("consoles.id").
		Order("bytes DESC").
		Scan(&rows)

	if rows == nil {
		rows = []SessionStorageConsoleBreakdown{}
	}
	for i := range rows {
		rows[i].ConsoleName = db.ConsoleName(rows[i].ConsoleID) // registry-derived (#1443)
	}

	return &GetStorageUsageOutput{Body: StorageUsageResponse{
		UsedBytes:  usedBytes,
		QuotaBytes: maxSaveStorageBytes(),
		ByConsole:  rows,
	}}, nil
}

// HumaCompactSaves is the huma handler for POST /api/user/saves/compact.
func (h *SessionHandler) HumaCompactSaves(ctx context.Context, _ *CompactSavesInput) (*CompactSavesOutput, error) {
	uid := UserIDFromContext(ctx)

	var sessions []db.GameSession
	if err := h.DB.Where("owner_id = ?", uid).Find(&sessions).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch sessions")
	}

	var totalDeleted int
	var totalFreed int64

	for _, session := range sessions {
		var keepIDs []uint

		var latestAuto db.SessionSaveState
		if err := h.DB.Where("session_id = ? AND is_auto = ?", session.ID, true).
			Order("created_at DESC").First(&latestAuto).Error; err == nil {
			keepIDs = append(keepIDs, latestAuto.ID)
		}

		var latestManual db.SessionSaveState
		if err := h.DB.Where("session_id = ? AND is_auto = ? AND slot IS NULL", session.ID, false).
			Order("created_at DESC").First(&latestManual).Error; err == nil {
			keepIDs = append(keepIDs, latestManual.ID)
		}

		query := h.DB.Where("session_id = ?", session.ID)
		if len(keepIDs) > 0 {
			query = query.Where("id NOT IN ?", keepIDs)
		}

		var toDelete []db.SessionSaveState
		if err := query.Find(&toDelete).Error; err != nil {
			continue
		}

		for _, save := range toDelete {
			totalFreed += save.FileSize
			h.Storage.DeleteSave(save.FilePath)
			if save.ScreenshotURL != "" {
				h.Storage.DeleteSave(h.Storage.ImagePath(save.ScreenshotURL))
			}
		}

		if len(toDelete) > 0 {
			deleteQuery := h.DB.Where("session_id = ?", session.ID)
			if len(keepIDs) > 0 {
				deleteQuery = deleteQuery.Where("id NOT IN ?", keepIDs)
			}
			deleteQuery.Delete(&db.SessionSaveState{})
		}

		totalDeleted += len(toDelete)
	}

	return &CompactSavesOutput{Body: CompactSavesResponse{
		DeletedCount: totalDeleted,
		FreedBytes:   totalFreed,
	}}, nil
}

// --- Handlers: SavedSearchHandler ---

// HumaCreateSavedSearch is the huma handler for POST /api/user/saved-searches.
func (h *SavedSearchHandler) HumaCreateSavedSearch(ctx context.Context, in *CreateSavedSearchInput) (*CreateSavedSearchOutput, error) {
	uid := UserIDFromContext(ctx)
	if uid == 0 {
		return nil, huma.Error401Unauthorized("unauthorized")
	}

	req := in.Body
	if req.Name == "" || len(req.Filters) == 0 {
		return nil, huma.Error400BadRequest("name and filters are required")
	}
	if len(req.Name) > 255 {
		return nil, huma.Error400BadRequest("name must be 255 characters or fewer")
	}
	if !json.Valid(req.Filters) {
		return nil, huma.Error400BadRequest("filters must be valid JSON")
	}

	var count int64
	h.DB.Model(&db.SavedSearch{}).Where("user_id = ?", uid).Count(&count)
	if count >= maxSavedSearchesPerUser {
		return nil, huma.Error409Conflict("maximum of 50 saved searches reached")
	}

	savedSearch := db.SavedSearch{
		UserID:  uid,
		Name:    req.Name,
		Filters: string(req.Filters),
	}
	if err := h.DB.Create(&savedSearch).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to create saved search")
	}

	return &CreateSavedSearchOutput{Body: SavedSearchResponse{
		ID:        strconv.FormatUint(uint64(savedSearch.ID), 10),
		Name:      savedSearch.Name,
		Filters:   json.RawMessage(savedSearch.Filters),
		CreatedAt: savedSearch.CreatedAt,
	}}, nil
}

// HumaListSavedSearches is the huma handler for GET /api/user/saved-searches.
func (h *SavedSearchHandler) HumaListSavedSearches(ctx context.Context, _ *ListSavedSearchesInput) (*ListSavedSearchesOutput, error) {
	uid := UserIDFromContext(ctx)
	if uid == 0 {
		return nil, huma.Error401Unauthorized("unauthorized")
	}

	var searches []db.SavedSearch
	if err := h.DB.Where("user_id = ?", uid).Order("created_at DESC, id DESC").Find(&searches).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch saved searches")
	}

	resp := make([]SavedSearchResponse, 0, len(searches))
	for _, s := range searches {
		resp = append(resp, SavedSearchResponse{
			ID:        strconv.FormatUint(uint64(s.ID), 10),
			Name:      s.Name,
			Filters:   json.RawMessage(s.Filters),
			CreatedAt: s.CreatedAt,
		})
	}
	return &ListSavedSearchesOutput{Body: resp}, nil
}

// HumaDeleteSavedSearch is the huma handler for DELETE /api/user/saved-searches/{id}.
func (h *SavedSearchHandler) HumaDeleteSavedSearch(ctx context.Context, in *DeleteSavedSearchInput) (*DeleteSavedSearchOutput, error) {
	uid := UserIDFromContext(ctx)
	if uid == 0 {
		return nil, huma.Error401Unauthorized("unauthorized")
	}

	id, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid ID")
	}

	var savedSearch db.SavedSearch
	if err := h.DB.First(&savedSearch, id).Error; err != nil {
		return nil, huma.Error404NotFound("saved search not found")
	}

	if savedSearch.UserID != uid {
		return nil, huma.Error403Forbidden("access denied")
	}

	if err := h.DB.Delete(&savedSearch).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to delete saved search")
	}
	return &DeleteSavedSearchOutput{Body: DeletedStatusResponse{Status: "deleted"}}, nil
}

// --- Handlers: AdminHandler misc ---

// HumaMetadataMatches is the huma handler for GET /api/admin/metadata-matches.
func (h *AdminHandler) HumaMetadataMatches(ctx context.Context, _ *MetadataMatchesInput) (*MetadataMatchesOutput, error) {
	uid := UserIDFromContext(ctx)

	var unscraped []db.Game
	if err := h.DB.Where("scraper_id = '' OR scraper_id IS NULL").
		Preload("Console").Preload("Discs").
		Find(&unscraped).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch unscraped games")
	}

	var unverified []db.Game
	if err := h.DB.Where("verification_status = ? AND (verification_tag = '' OR verification_tag IS NULL)", "unverified").
		Preload("Console").Preload("Discs").
		Find(&unverified).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch unverified games")
	}

	var incomplete []db.Game
	if err := h.DB.Where("scraper_id = 'libretro'").
		Preload("Console").Preload("Discs").
		Find(&incomplete).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch incomplete games")
	}

	return &MetadataMatchesOutput{Body: MetadataMatchesResponse{
		Unscraped:  ToGameResponses(unscraped, h.DB, uid),
		Unverified: ToGameResponses(unverified, h.DB, uid),
		Incomplete: ToGameResponses(incomplete, h.DB, uid),
	}}, nil
}

// HumaGetCoreCompatibility is the huma handler for GET /api/admin/core-compatibility.
func (h *AdminHandler) HumaGetCoreCompatibility(_ context.Context, _ *GetCoreCompatibilityInput) (*GetCoreCompatibilityOutput, error) {
	var consoles []db.Console
	if err := h.DB.Find(&consoles).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch consoles")
	}
	// Name is registry-derived (#1443); sort in Go after AfterFind populates it.
	sort.SliceStable(consoles, func(i, j int) bool { return consoles[i].Name < consoles[j].Name })

	result := make([]CoreCompatibilityEntry, 0, len(consoles))
	for _, console := range consoles {
		abbr := strings.ToLower(console.Abbreviation)
		matched := coresCompatible(console.DefaultCore, console.EmulatorJSCore)
		result = append(result, CoreCompatibilityEntry{
			ConsoleID:   abbr,
			ConsoleName: console.Name,
			NativeCore:  console.DefaultCore,
			WebCore:     console.EmulatorJSCore,
			Matched:     matched,
		})
	}
	return &GetCoreCompatibilityOutput{Body: CoreCompatibilityResponse{Consoles: result}}, nil
}

// HumaGetUserRateLimit is the huma handler for GET /api/admin/users/{id}/rate-limit.
func (h *AdminHandler) HumaGetUserRateLimit(_ context.Context, in *GetUserRateLimitInput) (*GetUserRateLimitOutput, error) {
	var user db.User
	if err := h.DB.First(&user, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}

	hashed := hashUsername(user.Username)
	var attempt db.LoginAttempt
	if err := h.DB.Where("username = ?", hashed).First(&attempt).Error; err != nil {
		return &GetUserRateLimitOutput{Body: RateLimitResponse{
			FailedCount: 0,
			LockedUntil: nil,
			IsLockedOut: false,
		}}, nil
	}

	if time.Since(attempt.UpdatedAt) > loginAttemptMaxAge {
		return &GetUserRateLimitOutput{Body: RateLimitResponse{
			FailedCount: 0,
			LockedUntil: nil,
			IsLockedOut: false,
		}}, nil
	}

	isLocked := attempt.FailedCount >= maxLoginAttempts && time.Now().Before(attempt.LockedUntil)
	var lockedUntil *time.Time
	if !attempt.LockedUntil.IsZero() {
		lockedUntil = &attempt.LockedUntil
	}

	return &GetUserRateLimitOutput{Body: RateLimitResponse{
		FailedCount: attempt.FailedCount,
		LockedUntil: lockedUntil,
		IsLockedOut: isLocked,
	}}, nil
}

// HumaResetUserRateLimit is the huma handler for DELETE /api/admin/users/{id}/rate-limit.
func (h *AdminHandler) HumaResetUserRateLimit(ctx context.Context, in *ResetUserRateLimitInput) (*ResetUserRateLimitOutput, error) {
	var user db.User
	if err := h.DB.First(&user, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}
	hashed := hashUsername(user.Username)
	h.DB.Where("username = ?", hashed).Delete(&db.LoginAttempt{})
	adminID := UserIDFromContext(ctx)
	slog.Info("audit: admin reset rate limit", "admin_id", adminID, "target_user", user.Username)
	return &ResetUserRateLimitOutput{Body: MessageResponse{Message: "rate limit reset"}}, nil
}
