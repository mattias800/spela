package api

import (
	"log/slog"
	"math"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/igdb"
)

// --- Phase 9: Visual Browsing — Gallery & Art Modes ---

// ScreenshotItem represents a single screenshot in the gallery response.
type ScreenshotItem struct {
	URL              string `json:"url"`
	GameID           string `json:"gameId"`
	GameTitle        string `json:"gameTitle"`
	ConsoleName      string `json:"consoleName"`
	ConsoleAbbr      string `json:"consoleAbbreviation"`
	ConsoleColor     string `json:"consoleColor"`
}

// ScreenshotGalleryResponse is the paginated response for the screenshot gallery.
type ScreenshotGalleryResponse struct {
	Screenshots []ScreenshotItem `json:"screenshots"`
	Page        int              `json:"page"`
	TotalPages  int              `json:"totalPages"`
	TotalCount  int              `json:"totalCount"`
}

// ArtworkItem represents a single IGDB artwork image in the gallery response.
type ArtworkItem struct {
	URL          string `json:"url"`
	Width        int    `json:"width"`
	Height       int    `json:"height"`
	GameID       string `json:"gameId"`
	GameTitle    string `json:"gameTitle"`
	ConsoleName  string `json:"consoleName"`
	ConsoleAbbr  string `json:"consoleAbbreviation"`
	ConsoleColor string `json:"consoleColor"`
}

// ArtworkGalleryResponse is the paginated response for the IGDB artwork gallery.
type ArtworkGalleryResponse struct {
	Artworks   []ArtworkItem `json:"artworks"`
	Page       int           `json:"page"`
	TotalPages int           `json:"totalPages"`
	TotalCount int           `json:"totalCount"`
}

// CoverItem represents a single cover in the cover gallery response.
type CoverItem struct {
	CoverURL         string  `json:"coverUrl"`
	GameID           string  `json:"gameId"`
	GameTitle        string  `json:"gameTitle"`
	ConsoleName      string  `json:"consoleName"`
	ConsoleAbbr      string  `json:"consoleAbbreviation"`
	ConsoleColor     string  `json:"consoleColor"`
	Rating           float64 `json:"rating"`
	CoverAspectRatio float64 `json:"coverAspectRatio"`
}

// CoverGalleryResponse is the paginated response for the cover gallery.
type CoverGalleryResponse struct {
	Covers     []CoverItem `json:"covers"`
	Page       int         `json:"page"`
	TotalPages int         `json:"totalPages"`
	TotalCount int         `json:"totalCount"`
}

// parsePagination parses page/limit query params with defaults and max bounds.
// Returns page (1-based), limit, and offset for SQL queries.
func parsePagination(c *gin.Context, defaultLimit, maxLimit int) (page, limit, offset int) {
	page = 1
	if p, err := strconv.Atoi(c.Query("page")); err == nil && p > 0 {
		page = p
	}
	limit = defaultLimit
	if l, err := strconv.Atoi(c.Query("limit")); err == nil && l > 0 {
		limit = l
	}
	if limit > maxLimit {
		limit = maxLimit
	}
	offset = (page - 1) * limit
	return
}

// totalPages computes the number of pages given a total count and page size.
func totalPages(totalCount, limit int) int {
	if limit <= 0 {
		return 0
	}
	return int(math.Ceil(float64(totalCount) / float64(limit)))
}

// GetScreenshotGallery returns a paginated stream of screenshots with minimal game metadata.
func (h *ExploreHandler) GetScreenshotGallery(c *gin.Context) {
	page, limit, offset := parsePagination(c, 40, 100)

	consoleFilter := strings.TrimSpace(c.Query("console"))
	genreFilter := strings.TrimSpace(c.Query("genre"))

	// Build the base query joining screenshots with games and consoles.
	baseQuery := h.DB.Table("game_screenshots").
		Joins("JOIN games ON games.id = game_screenshots.game_id AND games.deleted_at IS NULL").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Where("game_screenshots.deleted_at IS NULL")

	if consoleFilter != "" {
		baseQuery = baseQuery.Where("LOWER(consoles.abbreviation) = LOWER(?)", consoleFilter)
	}
	if genreFilter != "" {
		baseQuery = baseQuery.Where("LOWER(games.genre) = LOWER(?)", genreFilter)
	}

	// Count total matching screenshots.
	var count int64
	if err := baseQuery.Count(&count).Error; err != nil {
		slog.Error("failed to count screenshots for gallery", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load screenshot gallery"})
		return
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
	// Reuse baseQuery with select/order/pagination for the data fetch.
	if err := baseQuery.
		Select("game_screenshots.url, games.id as game_id, games.title as game_title, consoles.name as console_name, LOWER(consoles.abbreviation) as console_abbr, consoles.color_theme as console_color").
		Order("(game_screenshots.id * 2654435761) % 2147483647").
		Offset(offset).
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to load screenshot gallery", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load screenshot gallery"})
		return
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

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, ScreenshotGalleryResponse{
		Screenshots: items,
		Page:        page,
		TotalPages:  totalPages(int(count), limit),
		TotalCount:  int(count),
	})
}

// GetArtworkGallery returns a paginated stream of IGDB promotional artwork.
func (h *ExploreHandler) GetArtworkGallery(c *gin.Context) {
	page, limit, offset := parsePagination(c, 40, 100)

	// Count total artwork images.
	var count int64
	if err := h.DB.Table("game_artwork_images").
		Joins("JOIN games ON games.id = game_artwork_images.game_id AND games.deleted_at IS NULL").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Count(&count).Error; err != nil {
		slog.Error("failed to count artwork for gallery", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load artwork gallery"})
		return
	}

	type artworkRow struct {
		IGDBImageID  string
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
		Select("game_artwork_images.igdb_image_id, game_artwork_images.width, game_artwork_images.height, games.id as game_id, games.title as game_title, games.rating, consoles.name as console_name, LOWER(consoles.abbreviation) as console_abbr, consoles.color_theme as console_color").
		Joins("JOIN games ON games.id = game_artwork_images.game_id AND games.deleted_at IS NULL").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Order("games.rating DESC").
		Offset(offset).
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to load artwork gallery", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load artwork gallery"})
		return
	}

	items := make([]ArtworkItem, 0, len(rows))
	for _, r := range rows {
		items = append(items, ArtworkItem{
			URL:          igdb.ImageURL(r.IGDBImageID, "screenshot_big"),
			Width:        r.Width,
			Height:       r.Height,
			GameID:       strconv.FormatUint(uint64(r.GameID), 10),
			GameTitle:    r.GameTitle,
			ConsoleName:  r.ConsoleName,
			ConsoleAbbr:  r.ConsoleAbbr,
			ConsoleColor: r.ConsoleColor,
		})
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, ArtworkGalleryResponse{
		Artworks:   items,
		Page:       page,
		TotalPages: totalPages(int(count), limit),
		TotalCount: int(count),
	})
}

// GetCoverGallery returns a paginated dense cover art feed with minimal metadata.
func (h *ExploreHandler) GetCoverGallery(c *gin.Context) {
	page, limit, offset := parsePagination(c, 60, 200)

	consoleFilter := strings.TrimSpace(c.Query("console"))

	baseQuery := h.DB.Table("games").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Where("games.deleted_at IS NULL").
		Where("games.cover_url != ''")

	if consoleFilter != "" {
		baseQuery = baseQuery.Where("LOWER(consoles.abbreviation) = LOWER(?)", consoleFilter)
	}

	// Count total covers.
	var count int64
	if err := baseQuery.Count(&count).Error; err != nil {
		slog.Error("failed to count covers for gallery", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load cover gallery"})
		return
	}

	type coverRow struct {
		CoverURL     string
		GameID       uint
		GameTitle    string
		ConsoleName  string
		ConsoleAbbr  string
		ConsoleColor string
		Rating       float64
		CoverAspect  string
	}

	var rows []coverRow
	if err := baseQuery.
		Select("games.cover_url, games.id as game_id, games.title as game_title, games.rating, consoles.name as console_name, LOWER(consoles.abbreviation) as console_abbr, consoles.color_theme as console_color, consoles.cover_aspect").
		Order("games.rating DESC").
		Offset(offset).
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to load cover gallery", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load cover gallery"})
		return
	}

	items := make([]CoverItem, 0, len(rows))
	for _, r := range rows {
		items = append(items, CoverItem{
			CoverURL:         resolveImageURL(r.CoverURL),
			GameID:           strconv.FormatUint(uint64(r.GameID), 10),
			GameTitle:        r.GameTitle,
			ConsoleName:      r.ConsoleName,
			ConsoleAbbr:      r.ConsoleAbbr,
			ConsoleColor:     r.ConsoleColor,
			Rating:           r.Rating,
			CoverAspectRatio: parseAspectRatio(r.CoverAspect),
		})
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, CoverGalleryResponse{
		Covers:     items,
		Page:       page,
		TotalPages: totalPages(int(count), limit),
		TotalCount: int(count),
	})
}
