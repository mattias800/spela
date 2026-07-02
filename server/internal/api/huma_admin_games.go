package api

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"path/filepath"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/safehttp"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// --- Metadata ---

// UpdateGameMetadataInput is the input for POST /api/admin/games/{id}/metadata.
type UpdateGameMetadataInput struct {
	ID   string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
	Body UpdateGameMetadataRequest
}

// UpdateGameMetadataOutput wraps the updated game response.
type UpdateGameMetadataOutput struct {
	Body GameResponse
}

// UpdateVerificationTagInput is the input for PUT /api/admin/games/{id}/verification-tag.
type UpdateVerificationTagInput struct {
	ID   string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
	Body UpdateVerificationTagRequest
}

// UpdateVerificationTagResponse is the wire format for the verification tag response.
type UpdateVerificationTagResponse struct {
	VerificationTag string `json:"verificationTag"`
}

// UpdateVerificationTagOutput wraps the response.
type UpdateVerificationTagOutput struct {
	Body UpdateVerificationTagResponse
}

// --- Covers / Heroes ---

// GetGameCoversInput is the input for GET /api/admin/games/{id}/covers.
type GetGameCoversInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
}

// GameCoversResponse is the wire format for the game covers response.
type GameCoversResponse struct {
	Active string        `json:"active"`
	Covers []CoverOption `json:"covers"`
}

// GetGameCoversOutput wraps the response.
type GetGameCoversOutput struct {
	Body GameCoversResponse
}

// SetGameCoverInput is the input for PUT /api/admin/games/{id}/covers.
type SetGameCoverInput struct {
	ID   string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
	Body SetGameCoverRequest
}

// SetGameCoverOutput wraps the updated game response.
type SetGameCoverOutput struct {
	Body GameResponse
}

// GetGameHeroesInput is the input for GET /api/admin/games/{id}/heroes.
type GetGameHeroesInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
}

// GameHeroesResponse is the wire format for the hero options response.
type GameHeroesResponse struct {
	ActiveURL string       `json:"activeUrl"`
	Heroes    []HeroOption `json:"heroes"`
}

// GetGameHeroesOutput wraps the hero options response.
type GetGameHeroesOutput struct {
	Body GameHeroesResponse
}

// SetGameHeroInput is the input for PUT /api/admin/games/{id}/heroes.
type SetGameHeroInput struct {
	ID   string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
	Body SetGameHeroRequest
}

// SetGameHeroOutput wraps the updated game response.
type SetGameHeroOutput struct {
	Body GameResponse
}

// --- IGDB admin ---

// SearchIGDBInput is the input for GET /api/admin/games/{id}/igdb-search.
type SearchIGDBInput struct {
	ID    string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
	Query string `query:"q" doc:"Search query."`
}

// SearchIGDBOutput wraps the IGDB search results.
type SearchIGDBOutput struct {
	Body []IGDBSearchResult
}

// ApplyIGDBMatchInput is the input for POST /api/admin/games/{id}/igdb-match.
type ApplyIGDBMatchInput struct {
	ID   string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
	Body ApplyIGDBMatchRequest
}

// ApplyIGDBMatchOutput wraps the updated game response.
type ApplyIGDBMatchOutput struct {
	Body GameResponse
}

// TestIGDBInput is the input for POST /api/admin/igdb/test.
type TestIGDBInput struct {
	Body TestIGDBRequest
}

// TestIGDBResponse is the wire format for the IGDB test response.
type TestIGDBResponse struct {
	Success bool   `json:"success"`
	Error   string `json:"error"`
}

// TestIGDBOutput wraps the IGDB test response.
type TestIGDBOutput struct {
	Body TestIGDBResponse
}

// GetIGDBStatusInput is the input for GET /api/admin/igdb/status.
type GetIGDBStatusInput struct{}

// IGDBStatusResponse is the wire format for the IGDB status response.
type IGDBStatusResponse struct {
	Configured bool   `json:"configured"`
	Status     string `json:"status"`
	Source     string `json:"source"`
}

// GetIGDBStatusOutput wraps the IGDB status response.
type GetIGDBStatusOutput struct {
	Body IGDBStatusResponse
}

// --- Backfill images ---

// BackfillImagesInput is the input for POST /api/admin/backfill-images.
type BackfillImagesInput struct{}

// BackfillImagesOutput wraps the backfill result.
type BackfillImagesOutput struct {
	Body BackfillImagesResponse
}

// --- Deleted users ---

// ListDeletedUsersInput is the input for GET /api/admin/users/deleted.
type ListDeletedUsersInput struct{}

// ListDeletedUsersOutput wraps the deleted users list.
type ListDeletedUsersOutput struct {
	Body []DeletedUserResponse
}

// HardDeleteUserInput is the input for DELETE /api/admin/users/{id}/permanent.
type HardDeleteUserInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"User ID."`
}

// HardDeleteUserOutput wraps the hard-delete success message.
type HardDeleteUserOutput struct {
	Body MessageResponse
}

// --- Scan games ---

// ScanGamesInput is the input for POST /api/admin/games/scan.
type ScanGamesInput struct {
	Console string `query:"console" doc:"Optional console abbreviation to scan only that console's games."`
}

// ScanStartedResponse is the wire format of the 202 scan start response.
type ScanStartedResponse struct {
	Message string `json:"message"`
}

// ScanGamesOutput wraps the scan-start response (202 Accepted).
type ScanGamesOutput struct {
	Body ScanStartedResponse
}

// ScanStatusInput is the input for GET /api/admin/games/scan/status.
type ScanStatusInput struct{}

// ScanStatusResponse is the wire format for the scan status response.
type ScanStatusResponse struct {
	Active  bool   `json:"active"`
	Phase   string `json:"phase"`
	Current int    `json:"current"`
	Total   int    `json:"total"`
	Message string `json:"message"`
}

// ScanStatusOutput wraps the scan status response.
type ScanStatusOutput struct {
	Body ScanStatusResponse
}

// RegisterAdminGameRoutes wires the admin game-metadata / IGDB / covers /
// heroes / backfill / deleted-users / scan endpoints into the huma API.
func RegisterAdminGameRoutes(api huma.API, gameHandler *GameHandler, adminHandler *AdminHandler, igdbHandler *IGDBHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	requireAdmin := RequireAdmin(api)
	adminMW := huma.Middlewares{requireAuth, rateLimit, requireAdmin}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "updateGameMetadata",
		Method:      http.MethodPost,
		Path:        "/api/admin/games/{id}/metadata",
		Summary:     "Update game metadata",
		Description: "Admin-only. Manually updates metadata fields on a game. Only provided fields are applied.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, gameHandler.HumaUpdateMetadata)

	huma.Register(api, huma.Operation{
		OperationID: "updateVerificationTag",
		Method:      http.MethodPut,
		Path:        "/api/admin/games/{id}/verification-tag",
		Summary:     "Set verification tag",
		Description: "Admin-only. Sets a custom verification tag on a game for bookkeeping.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, gameHandler.HumaUpdateVerificationTag)

	huma.Register(api, huma.Operation{
		OperationID: "getGameCovers",
		Method:      http.MethodGet,
		Path:        "/api/admin/games/{id}/covers",
		Summary:     "List available cover art options for a game",
		Description: "Admin-only. Returns all known cover sources (LibRetro, IGDB, regional LibRetro variants) and which source is currently active.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, adminHandler.HumaGetGameCovers)

	huma.Register(api, huma.Operation{
		OperationID: "setGameCover",
		Method:      http.MethodPut,
		Path:        "/api/admin/games/{id}/covers",
		Summary:     "Switch the active cover art for a game",
		Description: "Admin-only. Selects one of the available cover sources and sets it as the active cover on the game record.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, adminHandler.HumaSetGameCover)

	huma.Register(api, huma.Operation{
		OperationID: "getGameHeroes",
		Method:      http.MethodGet,
		Path:        "/api/admin/games/{id}/heroes",
		Summary:     "List available hero art options from SteamGridDB",
		Description: "Admin-only. Returns hero art options fetched from SteamGridDB for the game.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, adminHandler.HumaGetGameHeroes)

	huma.Register(api, huma.Operation{
		OperationID: "setGameHero",
		Method:      http.MethodPut,
		Path:        "/api/admin/games/{id}/heroes",
		Summary:     "Set the active hero art for a game",
		Description: "Admin-only. Downloads the supplied SteamGridDB image locally and stores it as the game's hero art.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, adminHandler.HumaSetGameHero)

	huma.Register(api, huma.Operation{
		OperationID: "searchIGDB",
		Method:      http.MethodGet,
		Path:        "/api/admin/games/{id}/igdb-search",
		Summary:     "Search IGDB for candidates for a game",
		Description: "Admin-only. Searches IGDB scoped to the game's platform for possible matches.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, adminHandler.HumaSearchIGDB)

	huma.Register(api, huma.Operation{
		OperationID: "applyIGDBMatch",
		Method:      http.MethodPost,
		Path:        "/api/admin/games/{id}/igdb-match",
		Summary:     "Re-scrape a game with a chosen IGDB match",
		Description: "Admin-only. Re-runs metadata scraping using the supplied IGDB game ID.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, adminHandler.HumaApplyIGDBMatch)

	huma.Register(api, huma.Operation{
		OperationID: "testIGDB",
		Method:      http.MethodPost,
		Path:        "/api/admin/igdb/test",
		Summary:     "Test IGDB credentials",
		Description: "Admin-only. Validates a clientId/clientSecret pair by attempting a Twitch token exchange.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, igdbHandler.HumaTestIGDB)

	huma.Register(api, huma.Operation{
		OperationID: "getIGDBStatus",
		Method:      http.MethodGet,
		Path:        "/api/admin/igdb/status",
		Summary:     "Get current IGDB configuration status",
		Description: "Admin-only. Reports whether IGDB is configured via env vars, DB settings, or not at all.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, igdbHandler.HumaGetIGDBStatus)

	huma.Register(api, huma.Operation{
		OperationID: "backfillImages",
		Method:      http.MethodPost,
		Path:        "/api/admin/backfill-images",
		Summary:     "Download external CDN images locally",
		Description: "Admin-only. One-shot migration that downloads external IGDB/SteamGridDB image URLs and swaps them to local paths.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, adminHandler.HumaBackfillImages)

	huma.Register(api, huma.Operation{
		OperationID: "listDeletedUsers",
		Method:      http.MethodGet,
		Path:        "/api/admin/users/deleted",
		Summary:     "List soft-deleted users",
		Description: "Admin-only. Returns soft-deleted user records eligible for permanent deletion.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, adminHandler.HumaListDeletedUsers)

	huma.Register(api, huma.Operation{
		OperationID: "hardDeleteUser",
		Method:      http.MethodDelete,
		Path:        "/api/admin/users/{id}/permanent",
		Summary:     "Permanently delete a soft-deleted user",
		Description: "Admin-only. Removes the user and all associated data from the database. Requires the user to already be soft-deleted.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, adminHandler.HumaHardDeleteUser)

	huma.Register(api, huma.Operation{
		OperationID:   "scanGames",
		Method:        http.MethodPost,
		Path:          "/api/admin/games/scan",
		Summary:       "Trigger a library scan",
		Description:   "Admin-only. Starts a library scan in the background. Returns 202 immediately; progress is reported via WebSocket events.",
		DefaultStatus: http.StatusAccepted,
		Tags:          []string{"admin"},
		Middlewares:   adminMW,
		Security:      sec,
	}, gameHandler.HumaScanGames)

	huma.Register(api, huma.Operation{
		OperationID: "getScanStatus",
		Method:      http.MethodGet,
		Path:        "/api/admin/games/scan/status",
		Summary:     "Get current scan status",
		Description: "Admin-only. Returns whether a scan is currently active and the latest phase / progress.",
		Tags:        []string{"admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, gameHandler.HumaScanStatus)
}

// --- Handlers ---

// HumaUpdateMetadata is the huma handler for POST /api/admin/games/{id}/metadata.
func (h *GameHandler) HumaUpdateMetadata(ctx context.Context, in *UpdateGameMetadataInput) (*UpdateGameMetadataOutput, error) {
	var game db.Game
	if err := h.DB.First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	req := in.Body
	if req.Title != nil {
		game.Title = *req.Title
	}
	if req.Description != nil {
		game.Description = *req.Description
	}
	if req.CoverURL != nil {
		game.CoverURL = *req.CoverURL
	}
	if req.ScreenshotURL != nil {
		game.ScreenshotURL = *req.ScreenshotURL
	}
	if req.Developer != nil {
		game.Developer = *req.Developer
	}
	if req.Publisher != nil {
		game.Publisher = *req.Publisher
	}
	if req.ReleaseDate != nil {
		game.ReleaseDate = *req.ReleaseDate
	}
	if req.Genre != nil {
		game.Genre = *req.Genre
	}
	if req.Players != nil {
		game.Players = *req.Players
	}
	if req.IGDBCriticsRating != nil {
		game.IGDBCriticsRating = *req.IGDBCriticsRating
	}
	if req.CoreOverride != nil {
		game.CoreOverride = *req.CoreOverride
	}
	if req.PartyInfo != nil {
		game.PartyInfo = *req.PartyInfo
	}

	if err := h.DB.Save(&game).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update game")
	}

	h.DB.Preload("Console").Preload("Screenshots").Preload("ReleaseDates").First(&game, game.ID)
	uid := UserIDFromContext(ctx)
	return &UpdateGameMetadataOutput{Body: ToGameResponse(game, h.DB, uid)}, nil
}

// HumaUpdateVerificationTag is the huma handler for PUT /api/admin/games/{id}/verification-tag.
func (h *GameHandler) HumaUpdateVerificationTag(_ context.Context, in *UpdateVerificationTagInput) (*UpdateVerificationTagOutput, error) {
	var game db.Game
	if err := h.DB.First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	game.VerificationTag = in.Body.Tag
	if err := h.DB.Save(&game).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update verification tag")
	}

	return &UpdateVerificationTagOutput{Body: UpdateVerificationTagResponse{VerificationTag: game.VerificationTag}}, nil
}

// HumaGetGameCovers is the huma handler for GET /api/admin/games/{id}/covers.
func (h *AdminHandler) HumaGetGameCovers(_ context.Context, in *GetGameCoversInput) (*GetGameCoversOutput, error) {
	var game db.Game
	if err := h.DB.First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	covers := make([]CoverOption, 0)
	gameName := strings.TrimSuffix(game.FileName, filepath.Ext(game.FileName))

	if game.LibRetroCoverURL != "" {
		region := scraper.ExtractRegion(gameName)
		label := "LibRetro"
		if region != "" {
			label = fmt.Sprintf("LibRetro (%s)", region)
		}
		covers = append(covers, CoverOption{Source: "libretro", URL: resolveImageURL(game.LibRetroCoverURL), Label: label})
	}

	if game.IGDBCoverURL != "" {
		covers = append(covers, CoverOption{Source: "igdb", URL: resolveImageURL(game.IGDBCoverURL), Label: "IGDB"})
	}

	if game.CoverURL != "" && game.CoverURL != game.LibRetroCoverURL && game.CoverURL != game.IGDBCoverURL {
		covers = append(covers, CoverOption{Source: "custom", URL: resolveImageURL(game.CoverURL), Label: "Custom"})
	}

	if h.Scraper != nil {
		var console db.Console
		if err := h.DB.First(&console, game.ConsoleID).Error; err == nil {
			variants := h.Scraper.FindRegionalVariants(console.Abbreviation, gameName)
			for _, v := range variants {
				thumbURL := scraper.LibRetroThumbnailURL(console.Abbreviation, v.LibRetroName)
				if thumbURL == "" {
					continue
				}
				covers = append(covers, CoverOption{
					Source:       "libretro-regional",
					URL:          thumbURL,
					Label:        fmt.Sprintf("LibRetro (%s)", v.Region),
					LibRetroName: v.LibRetroName,
				})
			}
		}
	}

	active := ""
	if game.CoverURL != "" {
		switch game.CoverURL {
		case game.LibRetroCoverURL:
			active = "libretro"
		case game.IGDBCoverURL:
			active = "igdb"
		default:
			active = "custom"
		}
	}

	return &GetGameCoversOutput{Body: GameCoversResponse{Active: active, Covers: covers}}, nil
}

// HumaSetGameCover is the huma handler for PUT /api/admin/games/{id}/covers.
func (h *AdminHandler) HumaSetGameCover(ctx context.Context, in *SetGameCoverInput) (*SetGameCoverOutput, error) {
	var game db.Game
	if err := h.DB.First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	req := in.Body
	if req.Source == "" {
		return nil, huma.Error400BadRequest("invalid request body")
	}

	var newCoverURL string
	switch req.Source {
	case "libretro":
		if game.LibRetroCoverURL == "" {
			return nil, huma.Error400BadRequest("no LibRetro cover available")
		}
		newCoverURL = game.LibRetroCoverURL
	case "igdb":
		if game.IGDBCoverURL == "" {
			return nil, huma.Error400BadRequest("no IGDB cover available")
		}
		newCoverURL = game.IGDBCoverURL
	case "libretro-regional":
		if req.LibRetroName == "" {
			return nil, huma.Error400BadRequest("libretroName is required for libretro-regional source")
		}
		if h.Scraper == nil {
			return nil, huma.Error500InternalServerError("scraper not available")
		}
		var console db.Console
		if err := h.DB.First(&console, game.ConsoleID).Error; err != nil {
			return nil, huma.Error500InternalServerError("failed to load console")
		}
		gameName := strings.TrimSuffix(game.FileName, filepath.Ext(game.FileName))
		variants := h.Scraper.FindRegionalVariants(console.Abbreviation, gameName)
		validName := false
		for _, v := range variants {
			if v.LibRetroName == req.LibRetroName {
				validName = true
				break
			}
		}
		if !validName {
			return nil, huma.Error400BadRequest("libretroName is not a known regional variant for this game")
		}
		gameIDStr := fmt.Sprintf("%d", game.ID)
		subpath := fmt.Sprintf("%s/%s/boxart-libretro.png", console.Abbreviation, gameIDStr)
		path := h.Scraper.DownloadRegionalCover(console.Abbreviation, req.LibRetroName, subpath)
		if path == "" {
			return nil, huma.Error400BadRequest("failed to download regional cover")
		}
		game.LibRetroCoverURL = path
		newCoverURL = path
	default:
		return nil, huma.Error400BadRequest("source must be 'libretro', 'igdb', or 'libretro-regional'")
	}

	updates := map[string]interface{}{
		"cover_url":          newCoverURL,
		"cover_manually_set": true,
	}
	if req.Source == "libretro-regional" {
		updates["lib_retro_cover_url"] = newCoverURL
	}

	if err := h.DB.Model(&game).Updates(updates).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update cover")
	}

	uid := UserIDFromContext(ctx)
	if err := h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").First(&game, in.ID).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to reload game")
	}
	return &SetGameCoverOutput{Body: ToGameResponse(game, h.DB, uid)}, nil
}

// HumaGetGameHeroes is the huma handler for GET /api/admin/games/{id}/heroes.
func (h *AdminHandler) HumaGetGameHeroes(_ context.Context, in *GetGameHeroesInput) (*GetGameHeroesOutput, error) {
	var game db.Game
	if err := h.DB.First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	var artwork db.GameArtwork
	if err := h.DB.Where("game_id = ?", game.ID).First(&artwork).Error; err != nil {
		return &GetGameHeroesOutput{Body: GameHeroesResponse{ActiveURL: "", Heroes: []HeroOption{}}}, nil
	}

	if artwork.SteamGridDBID == 0 {
		return &GetGameHeroesOutput{Body: GameHeroesResponse{ActiveURL: "", Heroes: []HeroOption{}}}, nil
	}

	if h.Scraper == nil || h.Scraper.SteamGridDBClient == nil {
		return &GetGameHeroesOutput{Body: GameHeroesResponse{
			ActiveURL: resolveImageURL(artwork.HeroURL),
			Heroes:    []HeroOption{},
		}}, nil
	}

	heroes, err := h.Scraper.SteamGridDBClient.GetHeroes(artwork.SteamGridDBID)
	if err != nil {
		slog.Warn("failed to fetch hero options from SteamGridDB", "game", game.Title, "error", err)
		return &GetGameHeroesOutput{Body: GameHeroesResponse{
			ActiveURL: resolveImageURL(artwork.HeroURL),
			Heroes:    []HeroOption{},
		}}, nil
	}

	options := make([]HeroOption, len(heroes))
	for i, hero := range heroes {
		options[i] = HeroOption{
			URL:   hero.URL,
			Thumb: hero.Thumb,
			ID:    hero.ID,
		}
	}

	return &GetGameHeroesOutput{Body: GameHeroesResponse{
		ActiveURL: resolveImageURL(artwork.HeroURL),
		Heroes:    options,
	}}, nil
}

// HumaSetGameHero is the huma handler for PUT /api/admin/games/{id}/heroes.
func (h *AdminHandler) HumaSetGameHero(ctx context.Context, in *SetGameHeroInput) (*SetGameHeroOutput, error) {
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	req := in.Body
	if req.URL == "" {
		return nil, huma.Error400BadRequest("invalid request body")
	}
	// Issue #1120: gate the URL against private/internal IPs and
	// non-http(s) schemes BEFORE handing it to the scraper, otherwise
	// a rogue admin could point us at the AWS metadata endpoint or
	// internal services. Defense in depth: NewClient also re-checks
	// every redirect hop.
	if err := safehttp.CheckURL(req.URL); err != nil {
		return nil, huma.Error400BadRequest("invalid hero URL: " + err.Error())
	}

	if h.Scraper == nil {
		return nil, huma.Error500InternalServerError("scraper not available")
	}

	gameIDStr := fmt.Sprintf("%d", game.ID)
	subpath := fmt.Sprintf("%s/%s/artwork-hero.jpg", game.Console.Abbreviation, gameIDStr)
	localPath := h.Scraper.DownloadExternalImage(req.URL, subpath)
	if localPath == "" {
		return nil, huma.Error400BadRequest("failed to download hero image")
	}

	var artwork db.GameArtwork
	if err := h.DB.Where("game_id = ?", game.ID).First(&artwork).Error; err != nil {
		artwork = db.GameArtwork{GameID: game.ID}
	}
	artwork.HeroURL = localPath
	artwork.HeroManuallySet = true

	if artwork.ID == 0 {
		if err := h.DB.Create(&artwork).Error; err != nil {
			return nil, huma.Error500InternalServerError("failed to save hero art")
		}
	} else {
		if err := h.DB.Save(&artwork).Error; err != nil {
			return nil, huma.Error500InternalServerError("failed to save hero art")
		}
	}

	adminID := UserIDFromContext(ctx)
	slog.Info("audit: admin changed hero art", "admin_id", adminID, "game", game.Title, "hero_url", req.URL)

	if err := h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").First(&game, in.ID).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to reload game")
	}
	return &SetGameHeroOutput{Body: ToGameResponse(game, h.DB, adminID)}, nil
}

// HumaSearchIGDB is the huma handler for GET /api/admin/games/{id}/igdb-search.
func (h *AdminHandler) HumaSearchIGDB(_ context.Context, in *SearchIGDBInput) (*SearchIGDBOutput, error) {
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	if in.Query == "" {
		return nil, huma.Error400BadRequest("query parameter 'q' is required")
	}

	h.tryConfigureIGDB()
	if h.Scraper.IGDBClient == nil || !h.Scraper.IGDBClient.IsConfigured() {
		return nil, huma.Error503ServiceUnavailable("IGDB is not configured")
	}

	platformIDs, ok := igdb.AbbreviationToIGDBPlatform[game.Console.Abbreviation]
	if !ok {
		return nil, huma.Error400BadRequest("no IGDB platform mapping for this console")
	}

	games, err := h.Scraper.IGDBClient.SearchGame(in.Query, platformIDs)
	if err != nil {
		slog.Warn("IGDB search failed", "query", in.Query, "error", err)
		return nil, huma.Error502BadGateway("IGDB search failed")
	}

	results := make([]IGDBSearchResult, 0, len(games))
	for _, g := range games {
		result := IGDBSearchResult{
			IGDBID:  g.ID,
			Name:    g.Name,
			Summary: g.Summary,
		}
		if g.Cover != nil && g.Cover.ImageID != "" {
			result.CoverURL = igdb.ImageURL(g.Cover.ImageID, "cover_big")
		}
		if g.FirstReleaseDate > 0 {
			result.ReleaseYear = time.Unix(g.FirstReleaseDate, 0).Year()
		}
		for _, ic := range g.InvolvedCompanies {
			if ic.Developer {
				result.Developer = ic.Company.Name
				break
			}
		}
		results = append(results, result)
	}

	return &SearchIGDBOutput{Body: results}, nil
}

// HumaApplyIGDBMatch is the huma handler for POST /api/admin/games/{id}/igdb-match.
func (h *AdminHandler) HumaApplyIGDBMatch(ctx context.Context, in *ApplyIGDBMatchInput) (*ApplyIGDBMatchOutput, error) {
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	if in.Body.IGDBID <= 0 {
		return nil, huma.Error400BadRequest("igdbId is required and must be a positive integer")
	}

	h.tryConfigureIGDB()
	h.tryConfigureSteamGridDB()

	if err := h.Scraper.ScrapeGameWithIGDBMatch(&game, in.Body.IGDBID); err != nil {
		slog.Warn("IGDB match failed", "game", game.Title, "igdbId", in.Body.IGDBID, "error", err)
		return nil, huma.Error500InternalServerError("failed to apply IGDB match")
	}

	h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").First(&game, game.ID)
	uid := UserIDFromContext(ctx)
	return &ApplyIGDBMatchOutput{Body: ToGameResponse(game, h.DB, uid)}, nil
}

// HumaTestIGDB is the huma handler for POST /api/admin/igdb/test.
func (h *IGDBHandler) HumaTestIGDB(_ context.Context, in *TestIGDBInput) (*TestIGDBOutput, error) {
	req := in.Body
	if req.ClientID == "" || req.ClientSecret == "" {
		return nil, huma.Error400BadRequest("clientId and clientSecret are required")
	}

	client := igdb.NewClient("", "")
	defer client.Close()
	if err := client.TestCredentials(req.ClientID, req.ClientSecret); err != nil {
		slog.Warn("IGDB credential test failed", "error", err)
		return &TestIGDBOutput{Body: TestIGDBResponse{Success: false, Error: "credential validation failed"}}, nil
	}

	return &TestIGDBOutput{Body: TestIGDBResponse{Success: true}}, nil
}

// HumaGetIGDBStatus is the huma handler for GET /api/admin/igdb/status.
func (h *IGDBHandler) HumaGetIGDBStatus(_ context.Context, _ *GetIGDBStatusInput) (*GetIGDBStatusOutput, error) {
	source := IGDBSource(h.DB)

	if source == "none" {
		return &GetIGDBStatusOutput{Body: IGDBStatusResponse{
			Configured: false,
			Status:     "not_configured",
			Source:     "none",
		}}, nil
	}

	return &GetIGDBStatusOutput{Body: IGDBStatusResponse{
		Configured: true,
		Status:     "connected",
		Source:     source,
	}}, nil
}

// HumaBackfillImages is the huma handler for POST /api/admin/backfill-images.
func (h *AdminHandler) HumaBackfillImages(_ context.Context, _ *BackfillImagesInput) (*BackfillImagesOutput, error) {
	if h.Scraper == nil {
		return nil, huma.Error503ServiceUnavailable("scraper not configured")
	}

	result := BackfillImagesResponse{}

	var artworks []db.GameArtwork
	h.DB.Where("hero_url LIKE 'http%' OR grid_url LIKE 'http%' OR logo_url LIKE 'http%' OR icon_url LIKE 'http%'").Find(&artworks)

	artworkGameIDs := make([]uint, 0, len(artworks))
	for _, a := range artworks {
		artworkGameIDs = append(artworkGameIDs, a.GameID)
	}
	gameMap := batchLoadGamesWithConsole(h.DB, artworkGameIDs)

	for _, a := range artworks {
		game, ok := gameMap[a.GameID]
		if !ok {
			result.Errors++
			continue
		}
		consoleAbbr := strings.ToLower(game.Console.Abbreviation)
		gameIDStr := fmt.Sprintf("%d", a.GameID)
		updates := map[string]interface{}{}

		if strings.HasPrefix(a.HeroURL, "http") {
			if path := h.Scraper.DownloadExternalImage(a.HeroURL, fmt.Sprintf("%s/%s/artwork-hero.jpg", consoleAbbr, gameIDStr)); path != "" {
				updates["hero_url"] = path
				result.ArtworkDownloaded++
			}
		}
		if strings.HasPrefix(a.GridURL, "http") {
			if path := h.Scraper.DownloadExternalImage(a.GridURL, fmt.Sprintf("%s/%s/artwork-grid.jpg", consoleAbbr, gameIDStr)); path != "" {
				updates["grid_url"] = path
				result.ArtworkDownloaded++
			}
		}
		if strings.HasPrefix(a.LogoURL, "http") {
			if path := h.Scraper.DownloadExternalImage(a.LogoURL, fmt.Sprintf("%s/%s/artwork-logo.png", consoleAbbr, gameIDStr)); path != "" {
				updates["logo_url"] = path
				result.ArtworkDownloaded++
			}
		}
		if strings.HasPrefix(a.IconURL, "http") {
			if path := h.Scraper.DownloadExternalImage(a.IconURL, fmt.Sprintf("%s/%s/artwork-icon.png", consoleAbbr, gameIDStr)); path != "" {
				updates["icon_url"] = path
				result.ArtworkDownloaded++
			}
		}
		if len(updates) > 0 {
			h.DB.Model(&a).Updates(updates)
		}
	}

	var topRated []db.TopRatedGame
	h.DB.Where("cover_image_id != '' AND (cover_local_path = '' OR cover_local_path IS NULL)").Find(&topRated)
	for _, tr := range topRated {
		coverURL := igdb.ImageURL(tr.CoverImageID, "cover_big")
		subpath := fmt.Sprintf("top-rated/%d/cover.jpg", tr.IGDBGameID)
		if path := h.Scraper.DownloadExternalImage(coverURL, subpath); path != "" {
			h.DB.Model(&tr).Update("cover_local_path", path)
			result.TopRatedDownloaded++
		}
	}

	var similar []db.SimilarGame
	h.DB.Where("cover_image_id != '' AND (cover_local_path = '' OR cover_local_path IS NULL)").Find(&similar)
	for _, sg := range similar {
		coverURL := igdb.ImageURL(sg.CoverImageID, "cover_big")
		subpath := fmt.Sprintf("similar/%d/%d/cover.jpg", sg.GameID, sg.IGDBGameID)
		if path := h.Scraper.DownloadExternalImage(coverURL, subpath); path != "" {
			h.DB.Model(&sg).Update("cover_local_path", path)
			result.SimilarDownloaded++
		}
	}

	var artworkImages []db.GameArtworkImage
	h.DB.Where("igdb_image_id != '' AND (local_path = '' OR local_path IS NULL)").Find(&artworkImages)

	aiGameIDs := make([]uint, 0, len(artworkImages))
	for _, ai := range artworkImages {
		aiGameIDs = append(aiGameIDs, ai.GameID)
	}
	aiGameMap := batchLoadGamesWithConsole(h.DB, aiGameIDs)

	for _, ai := range artworkImages {
		game, ok := aiGameMap[ai.GameID]
		if !ok {
			result.Errors++
			continue
		}
		consoleAbbr := strings.ToLower(game.Console.Abbreviation)
		artworkURL := igdb.ImageURL(ai.IGDBImageID, "screenshot_big")
		subpath := fmt.Sprintf("%s/%d/artwork_%s.jpg", consoleAbbr, game.ID, ai.IGDBImageID)
		if path := h.Scraper.DownloadExternalImage(artworkURL, subpath); path != "" {
			h.DB.Model(&ai).Update("local_path", path)
			result.GalleryDownloaded++
		}
	}

	var companies []db.Company
	h.DB.Where("logo_url LIKE 'http%'").Find(&companies)
	for _, co := range companies {
		subpath := fmt.Sprintf("companies/%d/logo.png", co.IGDBCompanyID)
		if path := h.Scraper.DownloadExternalImage(co.LogoURL, subpath); path != "" {
			h.DB.Model(&co).Update("logo_url", path)
			result.CompanyDownloaded++
		}
	}

	slog.Info("backfill-images complete",
		"artwork", result.ArtworkDownloaded,
		"topRated", result.TopRatedDownloaded,
		"similar", result.SimilarDownloaded,
		"gallery", result.GalleryDownloaded,
		"company", result.CompanyDownloaded,
		"errors", result.Errors,
	)

	return &BackfillImagesOutput{Body: result}, nil
}

// HumaListDeletedUsers is the huma handler for GET /api/admin/users/deleted.
func (h *AdminHandler) HumaListDeletedUsers(_ context.Context, _ *ListDeletedUsersInput) (*ListDeletedUsersOutput, error) {
	var users []db.User
	if err := h.DB.Unscoped().Where("deleted_at IS NOT NULL").Find(&users).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch deleted users")
	}

	resp := make([]DeletedUserResponse, len(users))
	for i, u := range users {
		resp[i] = ToDeletedUserResponse(u)
	}
	return &ListDeletedUsersOutput{Body: resp}, nil
}

// HumaHardDeleteUser is the huma handler for DELETE /api/admin/users/{id}/permanent.
func (h *AdminHandler) HumaHardDeleteUser(ctx context.Context, in *HardDeleteUserInput) (*HardDeleteUserOutput, error) {
	var user db.User
	if err := h.DB.Unscoped().First(&user, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}

	if !user.DeletedAt.Valid {
		return nil, huma.Error400BadRequest("user is not soft-deleted; use the regular delete endpoint first")
	}

	if user.Role == db.RoleOwner {
		return nil, huma.Error403Forbidden("cannot permanently delete the owner")
	}

	// Issue #1122: only owner can hard-delete another admin row.
	currentUserID := UserIDFromContext(ctx)
	var caller db.User
	if err := h.DB.Select("id", "role").First(&caller, currentUserID).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to load caller")
	}
	if user.Role == db.RoleAdmin && caller.Role != db.RoleOwner {
		return nil, huma.Error403Forbidden("only the owner can permanently delete other admins")
	}

	err := h.DB.Transaction(func(tx *gorm.DB) error {
		uid := user.ID

		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.ConsoleShaderPreference{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.ConsoleRenderScalePreference{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.ConsoleSaveStatePolicy{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.GameSaveStatePolicy{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.ConsoleKeyMappingPreference{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.GameKeyMappingPreference{})

		var devices []db.Device
		tx.Unscoped().Where("user_id = ?", uid).Find(&devices)
		for _, d := range devices {
			tx.Unscoped().Where("device_id = ?", d.ID).Delete(&db.DeviceShaderPreference{})
		}
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.Device{})

		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.Favorite{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.PlayHistory{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.GameRating{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.PlayLaterItem{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.ActivityEvent{})

		var collections []db.GameCollection
		tx.Unscoped().Where("user_id = ?", uid).Find(&collections)
		for _, col := range collections {
			tx.Unscoped().Where("collection_id = ?", col.ID).Delete(&db.CollectionItem{})
		}
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.GameCollection{})

		var sharedSaves []db.SharedSaveState
		tx.Unscoped().Where("user_id = ?", uid).Find(&sharedSaves)
		for _, ss := range sharedSaves {
			if h.Storage != nil {
				h.Storage.DeleteSave(ss.FilePath)
			}
		}
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.SharedSaveState{})

		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.RetroAchievementCredential{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.UserAchievementProgress{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.UserAchievementShowcase{})

		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.ChallengeAttempt{})

		// Tables that were leaving orphans on hard-delete (#971):
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.DailyPlayActivity{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.SavedSearch{})

		// Sessions hosted by the deleted user — clean up the SharedSession
		// rows the user owns and any save data attached to them. The
		// SharedSessionMember/Invite cleanup below covers the deleted
		// user's membership in OTHER hosts' sessions.
		var hostedSessions []db.SharedSession
		tx.Unscoped().Where("user_id = ?", uid).Find(&hostedSessions)
		for _, s := range hostedSessions {
			var saves []db.SharedSessionSave
			tx.Unscoped().Where("shared_session_id = ?", s.ID).Find(&saves)
			for _, save := range saves {
				if h.Storage != nil && save.FilePath != "" {
					_ = h.Storage.DeleteSave(save.FilePath)
				}
			}
			tx.Unscoped().Where("shared_session_id = ?", s.ID).Delete(&db.SharedSessionSave{})
			tx.Unscoped().Where("shared_session_id = ?", s.ID).Delete(&db.SharedSessionMember{})
			tx.Unscoped().Where("shared_session_id = ?", s.ID).Delete(&db.SharedSessionInvite{})
		}
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.SharedSession{})

		// SessionSaveState rows owned by the user (auto-saves, slot saves
		// from sessions). The on-disk files are removed after the DB rows
		// to avoid removing a path that's still referenced.
		var sessionSaves []db.SessionSaveState
		tx.Unscoped().Where("user_id = ?", uid).Find(&sessionSaves)
		for _, ss := range sessionSaves {
			if h.Storage != nil && ss.FilePath != "" {
				_ = h.Storage.DeleteSave(ss.FilePath)
			}
		}
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.SessionSaveState{})

		// Membership / invites in OTHER users' shared sessions.
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.SharedSessionMember{})
		tx.Unscoped().Where("inviter_id = ? OR invitee_id = ?", uid, uid).Delete(&db.SharedSessionInvite{})

		tx.Unscoped().Where("host_user_id = ? OR client_user_id = ?", uid, uid).Delete(&db.NetplaySession{})

		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.RefreshToken{})

		return tx.Unscoped().Delete(&user).Error
	})
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to permanently delete user")
	}

	slog.Info("audit: admin permanently deleted user", "admin_id", currentUserID, "target_user", user.Username)
	return &HardDeleteUserOutput{Body: MessageResponse{Message: "user permanently deleted"}}, nil
}

// HumaScanGames is the huma handler for POST /api/admin/games/scan.
func (h *GameHandler) HumaScanGames(_ context.Context, in *ScanGamesInput) (*ScanGamesOutput, error) {
	consoleAbbrev := in.Console

	if consoleAbbrev != "" {
		var con db.Console
		if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleAbbrev).First(&con).Error; err != nil {
			return nil, huma.Error400BadRequest("unknown console")
		}
		consoleAbbrev = con.Abbreviation
	}

	if !h.Scanner.TryStartScan() {
		return nil, huma.Error409Conflict("A scan is already in progress")
	}

	h.Hub.Broadcast(ws.Event{Type: ws.EventScanStarted, Payload: nil})

	go func() {
		defer h.Scanner.FinishScan()

		var scanArgs []string
		if consoleAbbrev != "" {
			scanArgs = append(scanArgs, consoleAbbrev)
		}
		result, err := h.Scanner.Scan(func(p scanner.ScanProgress) {
			h.Scanner.SetScanProgress(&p)
			h.Hub.Broadcast(ws.Event{Type: ws.EventScanProgress, Payload: p})
		}, scanArgs...)
		if err != nil {
			slog.Error("game library scan failed", "error", err)
			h.Hub.Broadcast(ws.Event{Type: ws.EventScanError, Payload: ErrorResponse{Error: "library scan failed"}})
			return
		}

		resp := GameScanResultResponse{
			NewGames:     result.NewGames,
			UpdatedGames: result.UpdatedGames,
			RemovedGames: result.RemovedGames,
			TotalGames:   result.TotalGames,
		}

		if len(result.NewGameIDs) > 0 {
			var newGames []db.Game
			h.DB.Preload("Console").Where("id IN ?", result.NewGameIDs).Find(&newGames)
			for _, g := range newGames {
				resp.NewGamesList = append(resp.NewGamesList, GameScanSummary{
					ID:          fmt.Sprintf("%d", g.ID),
					Title:       g.Title,
					ConsoleName: g.Console.Name,
				})
			}
		}

		h.Hub.Broadcast(ws.Event{Type: ws.EventScanComplete, Payload: resp})

		if apiKey := steamGridDBAPIKey(h.DB); apiKey != "" {
			h.Scraper.ConfigureSteamGridDB(apiKey)
		}

		if result.NewGames > 0 && h.Scraper.IsIGDBConfigured() {
			var gameIDs []uint
			h.DB.Model(&db.Game{}).
				Where("scraper_id = '' OR scraper_id IS NULL").
				Pluck("id", &gameIDs)

			if len(gameIDs) > 0 {
				activeJob, _ := h.Scraper.Queue.GetActiveJob()
				if activeJob != nil {
					added, mergeErr := h.Scraper.Queue.MergeGames(activeJob.ID, gameIDs)
					if mergeErr != nil {
						slog.Error("failed to merge new games into active scrape job", "error", mergeErr)
					} else if added > 0 {
						slog.Info("merged new games into active scrape job", "added", added, "jobId", activeJob.ID)
					}
				} else {
					job, jobErr := h.Scraper.Queue.CreateJob("new", "", "", "", len(gameIDs))
					if jobErr != nil {
						slog.Error("failed to create scan auto-scrape job", "error", jobErr)
					} else {
						if enqErr := h.Scraper.Queue.EnqueueGames(job.ID, gameIDs, 0); enqErr != nil {
							slog.Error("failed to enqueue scan auto-scrape games", "error", enqErr)
						} else {
							slog.Info("scan auto-scrape job created", "jobId", job.ID, "total", len(gameIDs))
							h.Hub.Broadcast(ws.Event{Type: ws.EventScrapeStarted, Payload: ws.ScrapeStartedPayload{
								JobID: job.ID,
								Total: len(gameIDs),
								Mode:  "new",
							}})
						}
					}
				}
			}
		}
	}()

	return &ScanGamesOutput{Body: ScanStartedResponse{Message: "scan started in background"}}, nil
}

// HumaScanStatus is the huma handler for GET /api/admin/games/scan/status.
func (h *GameHandler) HumaScanStatus(_ context.Context, _ *ScanStatusInput) (*ScanStatusOutput, error) {
	active, progress := h.Scanner.GetScanStatus()

	if !active || progress == nil {
		return &ScanStatusOutput{Body: ScanStatusResponse{Active: active}}, nil
	}

	return &ScanStatusOutput{Body: ScanStatusResponse{
		Active:  true,
		Phase:   progress.Phase,
		Current: progress.Current,
		Total:   progress.Total,
		Message: progress.Message,
	}}, nil
}
