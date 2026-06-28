package api

import (
	"context"
	"log/slog"
	"math"
	"net/http"
	"strconv"
	"strings"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Inputs / outputs ---

// GetScreenshotGalleryInput is the input for GET /api/explore/screenshots.
type GetScreenshotGalleryInput struct {
	Page    int    `query:"page" doc:"1-based page number (defaults to 1)."`
	Limit   int    `query:"limit" doc:"Page size (defaults to 40, max 100)."`
	Console string `query:"console" doc:"Console abbreviation filter."`
	Genre   string `query:"genre" doc:"Genre filter."`
}

// GetScreenshotGalleryOutput wraps the paginated screenshot list.
type GetScreenshotGalleryOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         ScreenshotGalleryResponse
}

// GetArtworkGalleryInput is the input for GET /api/explore/artwork.
type GetArtworkGalleryInput struct {
	Page  int `query:"page" doc:"1-based page number (defaults to 1)."`
	Limit int `query:"limit" doc:"Page size (defaults to 40, max 100)."`
}

// GetArtworkGalleryOutput wraps the paginated artwork list.
type GetArtworkGalleryOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         ArtworkGalleryResponse
}

// GetCoverGalleryInput is the input for GET /api/explore/covers.
type GetCoverGalleryInput struct {
	Page    int    `query:"page" doc:"1-based page number (defaults to 1)."`
	Limit   int    `query:"limit" doc:"Page size (defaults to 60, max 200)."`
	Console string `query:"console" doc:"Console abbreviation filter."`
}

// GetCoverGalleryOutput wraps the paginated cover list.
type GetCoverGalleryOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         CoverGalleryResponse
}

// RegisterExploreGalleryRoutes wires the screenshot / artwork / cover gallery endpoints.
func RegisterExploreGalleryRoutes(api huma.API, h *ExploreHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getScreenshotGallery",
		Method:      http.MethodGet,
		Path:        "/api/explore/screenshots",
		Summary:     "Get paginated screenshot gallery",
		Description: "Returns a paginated stream of screenshots with minimal game metadata. Supports console/genre filters.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetScreenshotGallery)

	huma.Register(api, huma.Operation{
		OperationID: "getArtworkGallery",
		Method:      http.MethodGet,
		Path:        "/api/explore/artwork",
		Summary:     "Get paginated IGDB artwork gallery",
		Description: "Returns a paginated stream of IGDB promotional artwork with game metadata.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetArtworkGallery)

	huma.Register(api, huma.Operation{
		OperationID: "getCoverGallery",
		Method:      http.MethodGet,
		Path:        "/api/explore/covers",
		Summary:     "Get paginated cover gallery",
		Description: "Returns a paginated dense cover art feed with minimal metadata. Supports console filter.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetCoverGallery)
}

// clampPagination returns (page, limit, offset) after applying defaults and max
// limits. The zero-value behaviour matches the gin version's parsePagination
// helper: page defaults to 1 if <= 0, limit defaults to defaultLimit if <= 0
// and is capped at maxLimit.
func clampPagination(page, limit, defaultLimit, maxLimit int) (int, int, int) {
	if page <= 0 {
		page = 1
	}
	if limit <= 0 {
		limit = defaultLimit
	}
	if limit > maxLimit {
		limit = maxLimit
	}
	return page, limit, (page - 1) * limit
}

// humaTotalPages mirrors the gin helper totalPages().
func humaTotalPages(totalCount, limit int) int {
	if limit <= 0 {
		return 0
	}
	return int(math.Ceil(float64(totalCount) / float64(limit)))
}

// --- Handlers ---

// HumaGetScreenshotGallery is the huma handler for GET /api/explore/screenshots.
func (h *ExploreHandler) HumaGetScreenshotGallery(_ context.Context, in *GetScreenshotGalleryInput) (*GetScreenshotGalleryOutput, error) {
	page, limit, offset := clampPagination(in.Page, in.Limit, 40, 100)

	consoleFilter := strings.TrimSpace(in.Console)
	genreFilter := strings.TrimSpace(in.Genre)

	baseQuery := h.DB.Table("game_screenshots").
		Joins("JOIN games ON games.id = game_screenshots.game_id AND games.deleted_at IS NULL AND games.is_primary = true").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Where("game_screenshots.deleted_at IS NULL")

	if consoleFilter != "" {
		baseQuery = baseQuery.Where("LOWER(consoles.abbreviation) = LOWER(?)", consoleFilter)
	}
	if genreFilter != "" {
		baseQuery = baseQuery.Where("LOWER(games.genre) = LOWER(?)", genreFilter)
	}

	var count int64
	if err := baseQuery.Count(&count).Error; err != nil {
		slog.Error("failed to count screenshots for gallery", "error", err)
		return nil, huma.Error500InternalServerError("failed to load screenshot gallery")
	}

	type screenshotRow struct {
		URL          string
		GameID       uint
		GameTitle    string
		ConsoleName  string
		ConsoleAbbr  string
		ConsoleColor string
	}

	var rows []screenshotRow
	if err := baseQuery.
		Select("game_screenshots.url, games.id as game_id, games.title as game_title, consoles.name as console_name, LOWER(consoles.abbreviation) as console_abbr, consoles.color_theme as console_color").
		Order("(game_screenshots.id * 2654435761) % 2147483647").
		Offset(offset).
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to load screenshot gallery", "error", err)
		return nil, huma.Error500InternalServerError("failed to load screenshot gallery")
	}

	items := make([]ScreenshotItem, 0, len(rows))
	for _, r := range rows {
		items = append(items, ScreenshotItem{
			URL:          resolveImageURL(r.URL),
			GameID:       strconv.FormatUint(uint64(r.GameID), 10),
			GameTitle:    r.GameTitle,
			ConsoleName:  r.ConsoleName,
			ConsoleAbbr:  r.ConsoleAbbr,
			ConsoleColor: r.ConsoleColor,
		})
	}

	return &GetScreenshotGalleryOutput{
		CacheControl: "private, max-age=300",
		Body: ScreenshotGalleryResponse{
			Screenshots: items,
			Page:        page,
			TotalPages:  humaTotalPages(int(count), limit),
			TotalCount:  int(count),
		},
	}, nil
}

// HumaGetArtworkGallery is the huma handler for GET /api/explore/artwork.
func (h *ExploreHandler) HumaGetArtworkGallery(_ context.Context, in *GetArtworkGalleryInput) (*GetArtworkGalleryOutput, error) {
	page, limit, offset := clampPagination(in.Page, in.Limit, 40, 100)

	var count int64
	if err := h.DB.Table("game_artwork_images").
		Joins("JOIN games ON games.id = game_artwork_images.game_id AND games.deleted_at IS NULL AND games.is_primary = true").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Count(&count).Error; err != nil {
		slog.Error("failed to count artwork for gallery", "error", err)
		return nil, huma.Error500InternalServerError("failed to load artwork gallery")
	}

	type artworkRow struct {
		IGDBImageID  string
		LocalPath    string
		Width        int
		Height       int
		GameID       uint
		GameTitle    string
		ConsoleName  string
		ConsoleAbbr  string
		ConsoleColor string
		Rating       float64
	}

	var rows []artworkRow
	if err := h.DB.Table("game_artwork_images").
		Select("game_artwork_images.igdb_image_id, game_artwork_images.local_path, game_artwork_images.width, game_artwork_images.height, games.id as game_id, games.title as game_title, games.rating, consoles.name as console_name, LOWER(consoles.abbreviation) as console_abbr, consoles.color_theme as console_color").
		Joins("JOIN games ON games.id = game_artwork_images.game_id AND games.deleted_at IS NULL AND games.is_primary = true").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Order("games.rating DESC").
		Offset(offset).
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to load artwork gallery", "error", err)
		return nil, huma.Error500InternalServerError("failed to load artwork gallery")
	}

	items := make([]ArtworkItem, 0, len(rows))
	for _, r := range rows {
		if r.LocalPath == "" {
			continue
		}
		items = append(items, ArtworkItem{
			URL:          resolveImageURL(r.LocalPath),
			Width:        r.Width,
			Height:       r.Height,
			GameID:       strconv.FormatUint(uint64(r.GameID), 10),
			GameTitle:    r.GameTitle,
			ConsoleName:  r.ConsoleName,
			ConsoleAbbr:  r.ConsoleAbbr,
			ConsoleColor: r.ConsoleColor,
		})
	}

	return &GetArtworkGalleryOutput{
		CacheControl: "private, max-age=300",
		Body: ArtworkGalleryResponse{
			Artworks:   items,
			Page:       page,
			TotalPages: humaTotalPages(int(count), limit),
			TotalCount: int(count),
		},
	}, nil
}

// HumaGetCoverGallery is the huma handler for GET /api/explore/covers.
func (h *ExploreHandler) HumaGetCoverGallery(_ context.Context, in *GetCoverGalleryInput) (*GetCoverGalleryOutput, error) {
	page, limit, offset := clampPagination(in.Page, in.Limit, 60, 200)

	consoleFilter := strings.TrimSpace(in.Console)

	baseQuery := h.DB.Table("games").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Where("games.deleted_at IS NULL").
		Where("games.is_primary = true").
		Where("games.cover_url != ''")

	if consoleFilter != "" {
		baseQuery = baseQuery.Where("LOWER(consoles.abbreviation) = LOWER(?)", consoleFilter)
	}

	var count int64
	if err := baseQuery.Count(&count).Error; err != nil {
		slog.Error("failed to count covers for gallery", "error", err)
		return nil, huma.Error500InternalServerError("failed to load cover gallery")
	}

	type coverRow struct {
		CoverURL     string
		GameID       uint
		GameTitle    string
		ConsoleName  string
		ConsoleAbbr  string
		ConsoleColor string
		Rating       float64
	}

	var rows []coverRow
	if err := baseQuery.
		Select("games.cover_url, games.id as game_id, games.title as game_title, games.rating, consoles.name as console_name, LOWER(consoles.abbreviation) as console_abbr, consoles.color_theme as console_color").
		Order("games.rating DESC").
		Offset(offset).
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to load cover gallery", "error", err)
		return nil, huma.Error500InternalServerError("failed to load cover gallery")
	}

	items := make([]CoverItem, 0, len(rows))
	for _, r := range rows {
		items = append(items, CoverItem{
			CoverURL:          resolveImageURL(r.CoverURL),
			GameID:            strconv.FormatUint(uint64(r.GameID), 10),
			GameTitle:         r.GameTitle,
			ConsoleName:       r.ConsoleName,
			ConsoleAbbr:       r.ConsoleAbbr,
			ConsoleColor:      r.ConsoleColor,
			IGDBCriticsRating: r.Rating,
			// cover_aspect is registry-derived (#1443), not a DB column.
			CoverAspectRatio: parseAspectRatio(db.ConsoleCoverAspect(r.ConsoleAbbr)),
		})
	}

	return &GetCoverGalleryOutput{
		CacheControl: "private, max-age=300",
		Body: CoverGalleryResponse{
			Covers:     items,
			Page:       page,
			TotalPages: humaTotalPages(int(count), limit),
			TotalCount: int(count),
		},
	}, nil
}
