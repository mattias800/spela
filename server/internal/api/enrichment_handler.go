package api

import (
	"log/slog"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scraper"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// EnrichmentHandler handles enrichment-related API endpoints.
type EnrichmentHandler struct {
	DB      *gorm.DB
	Scraper *scraper.Scraper
	Hub     *ws.Hub
}

// --- Theme endpoints ---

// ThemeResponse is the API response for a theme with game count.
type ThemeResponse struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	GameCount int    `json:"gameCount"`
}

// ListThemes returns all themes with game counts, sorted by count DESC.
// GET /api/themes
func (h *EnrichmentHandler) ListThemes(c *gin.Context) {
	type themeRow struct {
		IGDBThemeID int
		Name        string
		GameCount   int
	}

	var rows []themeRow
	if err := h.DB.Model(&db.GameTheme{}).
		Joins("JOIN games ON games.id = game_themes.game_id AND games.deleted_at IS NULL").
		Select("igdb_theme_id, game_themes.name, COUNT(DISTINCT game_themes.game_id) as game_count").
		Group("igdb_theme_id, game_themes.name").
		Having("game_count > 0").
		Order("game_count DESC").
		Scan(&rows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch themes"})
		return
	}

	result := make([]ThemeResponse, len(rows))
	for i, r := range rows {
		result[i] = ThemeResponse{
			ID:        strconv.Itoa(r.IGDBThemeID),
			Name:      r.Name,
			GameCount: r.GameCount,
		}
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, result)
}

// ListThemeGames returns paginated games for a theme.
// GET /api/themes/:id/games
func (h *EnrichmentHandler) ListThemeGames(c *gin.Context) {
	themeID, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid theme ID"})
		return
	}

	// Check theme exists
	var count int64
	h.DB.Model(&db.GameTheme{}).Where("igdb_theme_id = ?", themeID).Count(&count)
	if count == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "theme not found"})
		return
	}

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	// Count total matching games
	var total int64
	h.DB.Model(&db.Game{}).
		Joins("JOIN game_themes ON game_themes.game_id = games.id").
		Where("game_themes.igdb_theme_id = ?", themeID).
		Where("games.deleted_at IS NULL").
		Count(&total)

	// Fetch games
	var games []db.Game
	offset := (page - 1) * pageSize
	if err := h.DB.Preload("Console").Preload("Screenshots").
		Joins("JOIN game_themes ON game_themes.game_id = games.id").
		Where("game_themes.igdb_theme_id = ?", themeID).
		Where("games.deleted_at IS NULL").
		Order("games.rating DESC").
		Offset(offset).Limit(pageSize).
		Find(&games).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch theme games"})
		return
	}

	userID := getUserID(c)
	c.JSON(http.StatusOK, PaginatedResponse{
		Data:     ToGameResponses(games, h.DB, userID),
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// --- Keyword endpoints ---

// KeywordResponse is the API response for a keyword with game count.
type KeywordResponse struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	GameCount int    `json:"gameCount"`
}

// ListKeywords returns top keywords by game count.
// GET /api/keywords?limit=50
func (h *EnrichmentHandler) ListKeywords(c *gin.Context) {
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "50"))
	if limit < 1 || limit > 200 {
		limit = 50
	}

	type keywordRow struct {
		IGDBKeywordID int
		Name          string
		GameCount     int
	}

	var rows []keywordRow
	if err := h.DB.Model(&db.GameKeyword{}).
		Joins("JOIN games ON games.id = game_keywords.game_id AND games.deleted_at IS NULL").
		Select("igdb_keyword_id, game_keywords.name, COUNT(DISTINCT game_keywords.game_id) as game_count").
		Group("igdb_keyword_id, game_keywords.name").
		Having("game_count > 0").
		Order("game_count DESC").
		Limit(limit).
		Scan(&rows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch keywords"})
		return
	}

	result := make([]KeywordResponse, len(rows))
	for i, r := range rows {
		result[i] = KeywordResponse{
			ID:        strconv.Itoa(r.IGDBKeywordID),
			Name:      r.Name,
			GameCount: r.GameCount,
		}
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, result)
}

// ListKeywordGames returns paginated games for a keyword.
// GET /api/keywords/:id/games
func (h *EnrichmentHandler) ListKeywordGames(c *gin.Context) {
	keywordID, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid keyword ID"})
		return
	}

	var count int64
	h.DB.Model(&db.GameKeyword{}).Where("igdb_keyword_id = ?", keywordID).Count(&count)
	if count == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "keyword not found"})
		return
	}

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.Game{}).
		Joins("JOIN game_keywords ON game_keywords.game_id = games.id").
		Where("game_keywords.igdb_keyword_id = ?", keywordID).
		Where("games.deleted_at IS NULL").
		Count(&total)

	var games []db.Game
	offset := (page - 1) * pageSize
	if err := h.DB.Preload("Console").Preload("Screenshots").
		Joins("JOIN game_keywords ON game_keywords.game_id = games.id").
		Where("game_keywords.igdb_keyword_id = ?", keywordID).
		Where("games.deleted_at IS NULL").
		Order("games.rating DESC").
		Offset(offset).Limit(pageSize).
		Find(&games).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch keyword games"})
		return
	}

	userID := getUserID(c)
	c.JSON(http.StatusOK, PaginatedResponse{
		Data:     ToGameResponses(games, h.DB, userID),
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// --- Series endpoints ---

// SeriesListResponse is the API response for a series in the list view.
type SeriesListResponse struct {
	ID               string `json:"id"`
	IGDBCollectionID int    `json:"igdbCollectionId"`
	Name             string `json:"name"`
	TotalGames       int    `json:"totalGames"`
	LibraryGames     int    `json:"libraryGames"`
}

// ListSeries returns all series with at least one local game, sorted by library count.
// GET /api/series
func (h *EnrichmentHandler) ListSeries(c *gin.Context) {
	type seriesRow struct {
		ID               uint
		IGDBCollectionID int
		Name             string
		TotalGames       int
		LibraryGames     int
	}

	var rows []seriesRow
	if err := h.DB.
		Table("game_series").
		Select(`game_series.id, game_series.igdb_collection_id, game_series.name,
			COUNT(game_series_entries.id) as total_games,
			COUNT(CASE WHEN games.id IS NOT NULL AND games.deleted_at IS NULL THEN 1 END) as library_games`).
		Joins("JOIN game_series_entries ON game_series_entries.series_id = game_series.id").
		Joins("LEFT JOIN games ON games.id = game_series_entries.game_id").
		Group("game_series.id").
		Having("library_games > 0").
		Order("library_games DESC").
		Scan(&rows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch series"})
		return
	}

	result := make([]SeriesListResponse, len(rows))
	for i, r := range rows {
		result[i] = SeriesListResponse{
			ID:               strconv.FormatUint(uint64(r.ID), 10),
			IGDBCollectionID: r.IGDBCollectionID,
			Name:             r.Name,
			TotalGames:       r.TotalGames,
			LibraryGames:     r.LibraryGames,
		}
	}

	c.JSON(http.StatusOK, result)
}

// SeriesDetailResponse is the API response for a series detail view.
type SeriesDetailResponse struct {
	ID               string                `json:"id"`
	IGDBCollectionID int                   `json:"igdbCollectionId"`
	Name             string                `json:"name"`
	HeroURL          string                `json:"heroUrl,omitempty"`
	LibraryGames     int                   `json:"libraryGames"`
	TotalGames       int                   `json:"totalGames"`
	Consoles         []SeriesConsoleInfo   `json:"consoles"`
	Games            []SeriesGameResponse  `json:"games"`
}

// SeriesConsoleInfo represents a console with game count in a series.
type SeriesConsoleInfo struct {
	Abbreviation string `json:"abbreviation"`
	Name         string `json:"name"`
	Color        string `json:"color"`
	GameCount    int    `json:"gameCount"`
}

// SeriesGameResponse is the API response for a game within a series.
type SeriesGameResponse struct {
	IGDBGameID        int     `json:"igdbGameId"`
	Name              string  `json:"name"`
	InLibrary         bool    `json:"inLibrary"`
	LocalGameID       *string `json:"localGameId"`
	CoverURL          *string `json:"coverUrl"`
	ReleaseDate       string  `json:"releaseDate,omitempty"`
	Rating            float64 `json:"rating"`
	ConsoleAbbreviation string `json:"consoleAbbreviation,omitempty"`
	ConsoleName       string  `json:"consoleName,omitempty"`
	ConsoleColor      string  `json:"consoleColor,omitempty"`
}

// GetSeriesDetail returns a series with all its games (local and non-local).
// GET /api/series/:id
func (h *EnrichmentHandler) GetSeriesDetail(c *gin.Context) {
	id := c.Param("id")
	var series db.GameSeries
	if err := h.DB.Preload("Entries").First(&series, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "series not found"})
		return
	}

	// Collect local game IDs for batch loading
	var localGameIDs []uint
	for _, entry := range series.Entries {
		if entry.GameID != nil {
			localGameIDs = append(localGameIDs, *entry.GameID)
		}
	}

	// Batch-load local games with console data
	gameMap := make(map[uint]db.Game)
	if len(localGameIDs) > 0 {
		var localGames []db.Game
		h.DB.Preload("Console").Where("id IN ?", localGameIDs).Find(&localGames)
		for _, g := range localGames {
			gameMap[g.ID] = g
		}
	}

	// Batch-load hero artwork for local games
	artworkMap := make(map[uint]db.GameArtwork)
	if len(localGameIDs) > 0 {
		var artworks []db.GameArtwork
		h.DB.Where("game_id IN ? AND hero_url != ''", localGameIDs).Find(&artworks)
		for _, a := range artworks {
			artworkMap[a.GameID] = a
		}
	}

	// Build per-game responses and track consoles
	consoleCountMap := make(map[uint]int)       // consoleID -> count
	consoleInfoMap := make(map[uint]db.Console)  // consoleID -> Console
	libraryGames := 0

	// Track the best game for hero art (highest rated with hero art)
	var bestHeroURL string
	var bestHeroRating float64 = -1

	games := make([]SeriesGameResponse, len(series.Entries))
	for i, entry := range series.Entries {
		gameResp := SeriesGameResponse{
			IGDBGameID: entry.IGDBGameID,
			Name:       entry.Name,
			InLibrary:  entry.GameID != nil,
		}
		if entry.GameID != nil {
			localID := strconv.FormatUint(uint64(*entry.GameID), 10)
			gameResp.LocalGameID = &localID

			if g, ok := gameMap[*entry.GameID]; ok {
				// Check soft-delete
				if g.DeletedAt.Valid {
					gameResp.InLibrary = false
					gameResp.LocalGameID = nil
				} else {
					libraryGames++

					if g.CoverURL != "" {
						coverURL := resolveImageURL(g.CoverURL)
						gameResp.CoverURL = &coverURL
					}
					gameResp.ReleaseDate = g.ReleaseDate
					gameResp.Rating = g.Rating

					if g.Console.ID != 0 {
						abbr := strings.ToLower(g.Console.Abbreviation)
						gameResp.ConsoleAbbreviation = abbr
						gameResp.ConsoleName = g.Console.Name
						gameResp.ConsoleColor = g.Console.ColorTheme
						consoleCountMap[g.Console.ID]++
						consoleInfoMap[g.Console.ID] = g.Console
					}

					// Track best hero art
					if artwork, ok := artworkMap[*entry.GameID]; ok {
						if g.Rating > bestHeroRating {
							bestHeroRating = g.Rating
							bestHeroURL = artwork.HeroURL
						}
					}
				}
			} else {
				// Game not found (e.g. soft-deleted and filtered by GORM)
				gameResp.InLibrary = false
				gameResp.LocalGameID = nil
			}
		}
		games[i] = gameResp
	}

	// Build console info list
	consoles := make([]SeriesConsoleInfo, 0, len(consoleCountMap))
	for consoleID, count := range consoleCountMap {
		con := consoleInfoMap[consoleID]
		consoles = append(consoles, SeriesConsoleInfo{
			Abbreviation: strings.ToLower(con.Abbreviation),
			Name:         con.Name,
			Color:        con.ColorTheme,
			GameCount:    count,
		})
	}

	c.JSON(http.StatusOK, SeriesDetailResponse{
		ID:               strconv.FormatUint(uint64(series.ID), 10),
		IGDBCollectionID: series.IGDBCollectionID,
		Name:             series.Name,
		HeroURL:          bestHeroURL,
		LibraryGames:     libraryGames,
		TotalGames:       len(series.Entries),
		Consoles:         consoles,
		Games:            games,
	})
}

// --- Franchise endpoints ---

// FranchiseResponse is the API response for a franchise with game count.
type FranchiseResponse struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	GameCount int    `json:"gameCount"`
}

// ListFranchises returns all franchises with at least one game in the library.
// GET /api/franchises
func (h *EnrichmentHandler) ListFranchises(c *gin.Context) {
	type franchiseRow struct {
		IGDBFranchiseID int
		FranchiseName   string
		GameCount       int
	}

	var rows []franchiseRow
	if err := h.DB.Model(&db.GameFranchise{}).
		Joins("JOIN games ON games.id = game_franchises.game_id AND games.deleted_at IS NULL").
		Select("igdb_franchise_id, franchise_name, COUNT(DISTINCT game_franchises.game_id) as game_count").
		Group("igdb_franchise_id, franchise_name").
		Having("game_count > 0").
		Order("game_count DESC").
		Scan(&rows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch franchises"})
		return
	}

	result := make([]FranchiseResponse, len(rows))
	for i, r := range rows {
		result[i] = FranchiseResponse{
			ID:        strconv.Itoa(r.IGDBFranchiseID),
			Name:      r.FranchiseName,
			GameCount: r.GameCount,
		}
	}

	c.JSON(http.StatusOK, result)
}

// ListFranchiseGames returns paginated games in a franchise.
// GET /api/franchises/:id/games
func (h *EnrichmentHandler) ListFranchiseGames(c *gin.Context) {
	franchiseID, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid franchise ID"})
		return
	}

	var count int64
	h.DB.Model(&db.GameFranchise{}).Where("igdb_franchise_id = ?", franchiseID).Count(&count)
	if count == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "franchise not found"})
		return
	}

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	// Count total matching games
	var total int64
	h.DB.Model(&db.Game{}).
		Joins("JOIN game_franchises ON game_franchises.game_id = games.id").
		Where("game_franchises.igdb_franchise_id = ?", franchiseID).
		Where("games.deleted_at IS NULL").
		Count(&total)

	// Fetch games
	var games []db.Game
	offset := (page - 1) * pageSize
	if err := h.DB.Preload("Console").Preload("Screenshots").
		Joins("JOIN game_franchises ON game_franchises.game_id = games.id").
		Where("game_franchises.igdb_franchise_id = ?", franchiseID).
		Where("games.deleted_at IS NULL").
		Order("games.release_date ASC, games.title ASC").
		Offset(offset).Limit(pageSize).
		Find(&games).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch franchise games"})
		return
	}

	userID := getUserID(c)
	c.JSON(http.StatusOK, PaginatedResponse{
		Data:     ToGameResponses(games, h.DB, userID),
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// FranchiseDetailResponse is the API response for a franchise detail view.
type FranchiseDetailResponse struct {
	ID              string               `json:"id"`
	IGDBFranchiseID int                  `json:"igdbFranchiseId"`
	Name            string               `json:"name"`
	HeroURL         string               `json:"heroUrl,omitempty"`
	LibraryGames    int                  `json:"libraryGames"`
	TotalGames      int                  `json:"totalGames"`
	Consoles        []SeriesConsoleInfo  `json:"consoles"`
	Games           []SeriesGameResponse `json:"games"`
}

// GetFranchiseDetail returns a franchise with all its games (local and non-local).
// GET /api/franchises/:id
func (h *EnrichmentHandler) GetFranchiseDetail(c *gin.Context) {
	id := c.Param("id")
	var franchise db.GameFranchiseGroup

	// Try lookup by DB primary key first, then by IGDB franchise ID as fallback.
	// This handles the transitional state where GetGameFranchises may emit an IGDB ID
	// before the GameFranchiseGroup has been populated.
	if err := h.DB.Preload("Entries").First(&franchise, id).Error; err != nil {
		if err2 := h.DB.Preload("Entries").Where("igdb_franchise_id = ?", id).First(&franchise).Error; err2 != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "franchise not found"})
			return
		}
	}

	// Collect local game IDs for batch loading
	var localGameIDs []uint
	for _, entry := range franchise.Entries {
		if entry.GameID != nil {
			localGameIDs = append(localGameIDs, *entry.GameID)
		}
	}

	// Batch-load local games with console data
	gameMap := make(map[uint]db.Game)
	if len(localGameIDs) > 0 {
		var localGames []db.Game
		h.DB.Preload("Console").Where("id IN ?", localGameIDs).Find(&localGames)
		for _, g := range localGames {
			gameMap[g.ID] = g
		}
	}

	// Batch-load hero artwork for local games
	artworkMap := make(map[uint]db.GameArtwork)
	if len(localGameIDs) > 0 {
		var artworks []db.GameArtwork
		h.DB.Where("game_id IN ? AND hero_url != ''", localGameIDs).Find(&artworks)
		for _, a := range artworks {
			artworkMap[a.GameID] = a
		}
	}

	// Build per-game responses and track consoles
	consoleCountMap := make(map[uint]int)
	consoleInfoMap := make(map[uint]db.Console)
	libraryGames := 0

	var bestHeroURL string
	var bestHeroRating float64 = -1

	games := make([]SeriesGameResponse, len(franchise.Entries))
	for i, entry := range franchise.Entries {
		gameResp := SeriesGameResponse{
			IGDBGameID: entry.IGDBGameID,
			Name:       entry.Name,
			InLibrary:  entry.GameID != nil,
		}
		if entry.GameID != nil {
			localID := strconv.FormatUint(uint64(*entry.GameID), 10)
			gameResp.LocalGameID = &localID

			if g, ok := gameMap[*entry.GameID]; ok {
				if g.DeletedAt.Valid {
					gameResp.InLibrary = false
					gameResp.LocalGameID = nil
				} else {
					libraryGames++

					if g.CoverURL != "" {
						coverURL := resolveImageURL(g.CoverURL)
						gameResp.CoverURL = &coverURL
					}
					gameResp.ReleaseDate = g.ReleaseDate
					gameResp.Rating = g.Rating

					if g.Console.ID != 0 {
						abbr := strings.ToLower(g.Console.Abbreviation)
						gameResp.ConsoleAbbreviation = abbr
						gameResp.ConsoleName = g.Console.Name
						gameResp.ConsoleColor = g.Console.ColorTheme
						consoleCountMap[g.Console.ID]++
						consoleInfoMap[g.Console.ID] = g.Console
					}

					if artwork, ok := artworkMap[*entry.GameID]; ok {
						if g.Rating > bestHeroRating {
							bestHeroRating = g.Rating
							bestHeroURL = artwork.HeroURL
						}
					}
				}
			} else {
				// Game not found (e.g. soft-deleted and filtered by GORM)
				gameResp.InLibrary = false
				gameResp.LocalGameID = nil
			}
		}
		games[i] = gameResp
	}

	// Build console info list
	consoles := make([]SeriesConsoleInfo, 0, len(consoleCountMap))
	for consoleID, count := range consoleCountMap {
		con := consoleInfoMap[consoleID]
		consoles = append(consoles, SeriesConsoleInfo{
			Abbreviation: strings.ToLower(con.Abbreviation),
			Name:         con.Name,
			Color:        con.ColorTheme,
			GameCount:    count,
		})
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, FranchiseDetailResponse{
		ID:              strconv.FormatUint(uint64(franchise.ID), 10),
		IGDBFranchiseID: franchise.IGDBFranchiseID,
		Name:            franchise.Name,
		HeroURL:         bestHeroURL,
		LibraryGames:    libraryGames,
		TotalGames:      len(franchise.Entries),
		Consoles:        consoles,
		Games:           games,
	})
}

// --- Per-game series/franchise endpoints ---

// GameSeriesResponse is the API response for a series that a game belongs to.
type GameSeriesResponse struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	TotalGames   int    `json:"totalGames"`
	LibraryGames int    `json:"libraryGames"`
}

// GetGameSeries returns the series that a game belongs to.
// GET /api/games/:id/series
func (h *EnrichmentHandler) GetGameSeries(c *gin.Context) {
	gameID, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	// Check game exists
	var game db.Game
	if err := h.DB.First(&game, gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	// Find series entries for this game, then load the full series with counts
	var entries []db.GameSeriesEntry
	if err := h.DB.Where("game_id = ?", gameID).Find(&entries).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch series"})
		return
	}

	if len(entries) == 0 {
		c.JSON(http.StatusOK, []GameSeriesResponse{})
		return
	}

	// Collect unique series IDs
	seriesIDs := make([]uint, 0, len(entries))
	seen := make(map[uint]bool)
	for _, e := range entries {
		if !seen[e.SeriesID] {
			seriesIDs = append(seriesIDs, e.SeriesID)
			seen[e.SeriesID] = true
		}
	}

	// Query series with counts
	type seriesRow struct {
		ID           uint
		Name         string
		TotalGames   int
		LibraryGames int
	}

	var rows []seriesRow
	if err := h.DB.
		Table("game_series").
		Select(`game_series.id, game_series.name,
			COUNT(game_series_entries.id) as total_games,
			COUNT(CASE WHEN games.id IS NOT NULL AND games.deleted_at IS NULL THEN 1 END) as library_games`).
		Joins("JOIN game_series_entries ON game_series_entries.series_id = game_series.id").
		Joins("LEFT JOIN games ON games.id = game_series_entries.game_id").
		Where("game_series.id IN ?", seriesIDs).
		Group("game_series.id").
		Scan(&rows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch series details"})
		return
	}

	result := make([]GameSeriesResponse, len(rows))
	for i, r := range rows {
		result[i] = GameSeriesResponse{
			ID:           strconv.FormatUint(uint64(r.ID), 10),
			Name:         r.Name,
			TotalGames:   r.TotalGames,
			LibraryGames: r.LibraryGames,
		}
	}

	c.JSON(http.StatusOK, result)
}

// GameFranchiseResponse is the API response for a franchise that a game belongs to.
type GameFranchiseResponse struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	TotalGames   int    `json:"totalGames"`
	LibraryGames int    `json:"libraryGames"`
}

// GetGameFranchises returns the franchises that a game belongs to.
// GET /api/games/:id/franchises
func (h *EnrichmentHandler) GetGameFranchises(c *gin.Context) {
	gameID, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	// Check game exists
	var game db.Game
	if err := h.DB.First(&game, gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	// Find franchise associations for this game
	var franchises []db.GameFranchise
	if err := h.DB.Where("game_id = ?", gameID).Find(&franchises).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch franchises"})
		return
	}

	if len(franchises) == 0 {
		c.JSON(http.StatusOK, []GameFranchiseResponse{})
		return
	}

	// Try to use the new GameFranchiseGroup tables for accurate counts
	result := make([]GameFranchiseResponse, 0, len(franchises))
	seen := make(map[int]bool)
	for _, f := range franchises {
		if seen[f.IGDBFranchiseID] {
			continue
		}
		seen[f.IGDBFranchiseID] = true

		resp := GameFranchiseResponse{
			Name: f.FranchiseName,
		}

		// Look for the franchise group
		var group db.GameFranchiseGroup
		if err := h.DB.Where("igdb_franchise_id = ?", f.IGDBFranchiseID).First(&group).Error; err == nil {
			resp.ID = strconv.FormatUint(uint64(group.ID), 10)
			// Count entries
			var totalCount int64
			h.DB.Model(&db.GameFranchiseEntry{}).Where("franchise_group_id = ?", group.ID).Count(&totalCount)
			resp.TotalGames = int(totalCount)
			var libraryCount int64
			h.DB.Model(&db.GameFranchiseEntry{}).
				Joins("JOIN games ON games.id = game_franchise_entries.game_id AND games.deleted_at IS NULL").
				Where("game_franchise_entries.franchise_group_id = ?", group.ID).
				Count(&libraryCount)
			resp.LibraryGames = int(libraryCount)
		} else {
			// Fallback: use IGDB franchise ID and count local games only
			resp.ID = strconv.Itoa(f.IGDBFranchiseID)
			var count int64
			h.DB.Model(&db.GameFranchise{}).
				Joins("JOIN games ON games.id = game_franchises.game_id AND games.deleted_at IS NULL").
				Where("game_franchises.igdb_franchise_id = ?", f.IGDBFranchiseID).
				Count(&count)
			resp.TotalGames = int(count)
			resp.LibraryGames = int(count)
		}

		result = append(result, resp)
	}

	c.JSON(http.StatusOK, result)
}

// --- Admin backfill endpoint ---

// TriggerEnrichMetadata starts a background IGDB metadata enrichment.
// POST /api/admin/enrich-metadata?mode=missing|all
func (h *EnrichmentHandler) TriggerEnrichMetadata(c *gin.Context) {
	if h.Scraper.IGDBClient == nil || !h.Scraper.IGDBClient.IsConfigured() {
		c.JSON(http.StatusBadRequest, gin.H{"error": "IGDB credentials not configured"})
		return
	}

	mode := c.DefaultQuery("mode", "missing")

	if !h.Scraper.TryStartEnrich() {
		c.JSON(http.StatusConflict, gin.H{"error": "an enrichment or scrape operation is already in progress"})
		return
	}

	// Count matching games
	var total int64
	switch mode {
	case "all":
		h.DB.Model(&db.Game{}).Where("scraper_id LIKE 'igdb:%'").Count(&total)
	default:
		h.DB.Model(&db.Game{}).Where("scraper_id LIKE 'igdb:%'").
			Where("id NOT IN (SELECT DISTINCT game_id FROM game_themes)").
			Count(&total)
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "enrich_started", Payload: gin.H{"total": total}})
	}

	go func() {
		defer h.Scraper.FinishEnrich()

		count, enrichTotal, err := h.Scraper.EnrichAll(mode, func(p scraper.EnrichProgress) {
			h.Scraper.SetEnrichProgress(&p)
			if h.Hub != nil {
				h.Hub.Broadcast(ws.Event{Type: "enrich_progress", Payload: p})
			}
		})
		if err != nil {
			slog.Error("metadata enrichment failed", "error", err)
			if h.Hub != nil {
				h.Hub.Broadcast(ws.Event{Type: "enrich_error", Payload: gin.H{"error": "enrichment failed"}})
			}
			return
		}
		slog.Info("metadata enrichment complete", "enriched", count, "total", enrichTotal)
		if h.Hub != nil {
			h.Hub.Broadcast(ws.Event{Type: "enrich_complete", Payload: gin.H{"enriched": count, "total": enrichTotal}})
		}
	}()

	adminID, _ := c.Get("userId")
	slog.Info("audit: admin triggered enrichment", "admin_id", adminID, "mode", mode)
	c.JSON(http.StatusAccepted, gin.H{"message": "enrichment started in background", "total": total})
}

// EnrichMetadataStatus returns the current enrichment status.
// GET /api/admin/enrich-metadata/status
func (h *EnrichmentHandler) EnrichMetadataStatus(c *gin.Context) {
	active, progress := h.Scraper.GetEnrichStatus()

	if !active || progress == nil {
		c.JSON(http.StatusOK, gin.H{"active": false})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"active":    true,
		"current":   progress.Current,
		"total":     progress.Total,
		"gameName":  progress.GameName,
		"successes": progress.Successes,
		"failures":  progress.Failures,
	})
}
