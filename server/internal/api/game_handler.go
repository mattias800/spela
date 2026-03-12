package api

import (
	"archive/tar"
	"archive/zip"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// GameHandler handles game-related endpoints.
type GameHandler struct {
	DB       *gorm.DB
	Scanner  *scanner.Scanner
	Storage  *storage.Storage
	Hub      *ws.Hub
	GameDirs []string
	Scraper  *scraper.Scraper
}

// ListGames returns all games with optional filtering.
func (h *GameHandler) ListGames(c *gin.Context) {
	var games []db.Game
	query := h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").Preload("ReleaseDates")
	countQuery := h.DB.Model(&db.Game{})

	userID := getUserID(c)

	// --- Console filter (multi-select with backward compat) ---
	consoles := c.Query("consoles")
	if consoles == "" {
		consoles = c.Query("consoleId")
	}
	if consoles != "" {
		abbrs := strings.Split(consoles, ",")
		var consoleIDs []uint
		for _, abbr := range abbrs {
			abbr = strings.TrimSpace(abbr)
			if abbr == "" {
				continue
			}
			var console db.Console
			if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", abbr).First(&console).Error; err == nil {
				consoleIDs = append(consoleIDs, console.ID)
			}
		}
		if len(consoleIDs) == 0 {
			// All abbreviations unknown — return empty list
			c.JSON(http.StatusOK, PaginatedResponse{
				Data:     []GameResponse{},
				Total:    0,
				Page:     1,
				PageSize: 50,
			})
			return
		}
		query = query.Where("console_id IN ?", consoleIDs)
		countQuery = countQuery.Where("console_id IN ?", consoleIDs)
	}

	// --- Text search ---
	if search := c.Query("search"); search != "" {
		query = query.Where("title LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(search)+"%")
		countQuery = countQuery.Where("title LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(search)+"%")
	}

	// --- Genre filter (multi-select with backward compat) ---
	genres := c.Query("genres")
	if genres == "" {
		genres = c.Query("genre")
	}
	if genres != "" {
		genreList := strings.Split(genres, ",")
		var trimmed []string
		for _, g := range genreList {
			g = strings.TrimSpace(g)
			if g != "" {
				trimmed = append(trimmed, g)
			}
		}
		if len(trimmed) > 0 {
			query = query.Where("genre IN ?", trimmed)
			countQuery = countQuery.Where("genre IN ?", trimmed)
		}
	}

	// Track whether any join-based filter is active (needs DISTINCT to avoid duplicates)
	needsDistinct := false

	// --- Theme filter (multi-select with backward compat) ---
	themes := c.Query("themes")
	if themes == "" {
		themes = c.Query("theme")
	}
	if themes != "" {
		themeIDs := strings.Split(themes, ",")
		query = query.Joins("JOIN game_themes ON game_themes.game_id = games.id").
			Where("game_themes.igdb_theme_id IN ?", themeIDs)
		countQuery = countQuery.Joins("JOIN game_themes ON game_themes.game_id = games.id").
			Where("game_themes.igdb_theme_id IN ?", themeIDs)
		needsDistinct = true
	}

	// --- Keyword filter (multi-select with backward compat) ---
	keywords := c.Query("keywords")
	if keywords == "" {
		keywords = c.Query("keyword")
	}
	if keywords != "" {
		keywordIDs := strings.Split(keywords, ",")
		query = query.Joins("JOIN game_keywords ON game_keywords.game_id = games.id").
			Where("game_keywords.igdb_keyword_id IN ?", keywordIDs)
		countQuery = countQuery.Joins("JOIN game_keywords ON game_keywords.game_id = games.id").
			Where("game_keywords.igdb_keyword_id IN ?", keywordIDs)
		needsDistinct = true
	}

	// --- Perspective filter (multi-select with backward compat) ---
	perspectives := c.Query("perspectives")
	if perspectives == "" {
		perspectives = c.Query("perspective")
	}
	if perspectives != "" {
		perspectiveIDs := strings.Split(perspectives, ",")
		query = query.Joins("JOIN game_player_perspectives ON game_player_perspectives.game_id = games.id").
			Where("game_player_perspectives.igdb_perspective_id IN ?", perspectiveIDs)
		countQuery = countQuery.Joins("JOIN game_player_perspectives ON game_player_perspectives.game_id = games.id").
			Where("game_player_perspectives.igdb_perspective_id IN ?", perspectiveIDs)
		needsDistinct = true
	}

	// --- Developer filter (substring match) ---
	if developer := c.Query("developer"); developer != "" {
		query = query.Where("games.developer LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(developer)+"%")
		countQuery = countQuery.Where("games.developer LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(developer)+"%")
	}

	// --- Publisher filter (substring match) ---
	if publisher := c.Query("publisher"); publisher != "" {
		query = query.Where("games.publisher LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(publisher)+"%")
		countQuery = countQuery.Where("games.publisher LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(publisher)+"%")
	}

	// --- Year range filters ---
	if yearMin := c.Query("yearMin"); yearMin != "" {
		if y, err := strconv.Atoi(yearMin); err == nil && y >= 1970 && y <= 2100 {
			query = query.Where("release_date >= ?", fmt.Sprintf("%d", y))
			countQuery = countQuery.Where("release_date >= ?", fmt.Sprintf("%d", y))
		}
	}
	if yearMax := c.Query("yearMax"); yearMax != "" {
		if y, err := strconv.Atoi(yearMax); err == nil && y >= 1970 && y <= 2100 {
			query = query.Where("release_date < ?", fmt.Sprintf("%d", y+1))
			countQuery = countQuery.Where("release_date < ?", fmt.Sprintf("%d", y+1))
		}
	}

	// --- Rating range filters ---
	if ratingMin := c.Query("ratingMin"); ratingMin != "" {
		if r, err := strconv.ParseFloat(ratingMin, 64); err == nil && r >= 0 && r <= 100 {
			query = query.Where("games.rating >= ?", r)
			countQuery = countQuery.Where("games.rating >= ?", r)
		}
	}
	if ratingMax := c.Query("ratingMax"); ratingMax != "" {
		if r, err := strconv.ParseFloat(ratingMax, 64); err == nil && r >= 0 && r <= 100 {
			query = query.Where("games.rating <= ?", r)
			countQuery = countQuery.Where("games.rating <= ?", r)
		}
	}

	// --- Play status filter (requires user context) ---
	if playStatus := c.Query("playStatus"); playStatus != "" && userID > 0 {
		switch playStatus {
		case "unplayed":
			query = query.Joins("LEFT JOIN play_histories ON play_histories.game_id = games.id AND play_histories.user_id = ? AND play_histories.deleted_at IS NULL", userID).
				Where("play_histories.id IS NULL")
			countQuery = countQuery.Joins("LEFT JOIN play_histories ON play_histories.game_id = games.id AND play_histories.user_id = ? AND play_histories.deleted_at IS NULL", userID).
				Where("play_histories.id IS NULL")
		case "played":
			query = query.Joins("JOIN play_histories ON play_histories.game_id = games.id AND play_histories.user_id = ? AND play_histories.deleted_at IS NULL", userID)
			countQuery = countQuery.Joins("JOIN play_histories ON play_histories.game_id = games.id AND play_histories.user_id = ? AND play_histories.deleted_at IS NULL", userID)
			needsDistinct = true
		case "favorited":
			query = query.Joins("JOIN favorites ON favorites.game_id = games.id AND favorites.user_id = ? AND favorites.deleted_at IS NULL", userID)
			countQuery = countQuery.Joins("JOIN favorites ON favorites.game_id = games.id AND favorites.user_id = ? AND favorites.deleted_at IS NULL", userID)
		case "play-later":
			query = query.Joins("JOIN play_later_items ON play_later_items.game_id = games.id AND play_later_items.user_id = ? AND play_later_items.deleted_at IS NULL", userID)
			countQuery = countQuery.Joins("JOIN play_later_items ON play_later_items.game_id = games.id AND play_later_items.user_id = ? AND play_later_items.deleted_at IS NULL", userID)
		}
	}

	// Apply DISTINCT when join-based filters are active to prevent duplicate rows
	if needsDistinct {
		query = query.Distinct("games.*")
		countQuery = countQuery.Distinct("games.id")
	}

	// Sorting - whitelist both column and direction to prevent SQL injection
	sort := c.DefaultQuery("sort", "title")
	if s := c.Query("sortBy"); s != "" {
		sort = s
	}
	order := c.DefaultQuery("order", "asc")
	if o := c.Query("sortOrder"); o != "" {
		order = o
	}
	allowedSorts := map[string]bool{"title": true, "created_at": true, "file_size": true, "rating": true, "release_date": true}
	if !allowedSorts[sort] {
		sort = "title"
	}
	if order != "asc" && order != "desc" {
		order = "asc"
	}
	query = query.Order(sort + " " + order)

	// Pagination - accept both perPage and pageSize
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("perPage", "50"))
	if ps := c.Query("pageSize"); ps != "" {
		perPage, _ = strconv.Atoi(ps)
	}
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 200 {
		perPage = 50
	}

	var total int64
	countQuery.Count(&total)

	offset := (page - 1) * perPage
	if err := query.Offset(offset).Limit(perPage).Find(&games).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch games"})
		return
	}

	c.JSON(http.StatusOK, PaginatedResponse{
		Data:     ToGameResponses(games, h.DB, userID),
		Total:    total,
		Page:     page,
		PageSize: perPage,
	})
}

// GetGame returns details for a single game.
func (h *GameHandler) GetGame(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").Preload("ReleaseDates").Preload("Videos").Preload("LanguageSupports").Preload("AgeRatings").First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	userID := getUserID(c)
	resp := ToGameResponse(game, h.DB, userID)
	resp.BiosStatus = GetConsoleStatus(h.Storage.BiosDir, game.Console.Abbreviation)
	c.JSON(http.StatusOK, resp)
}

// DownloadGame serves the ROM file for a game.
func (h *GameHandler) DownloadGame(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	// Resolve relative path to absolute for filesystem access
	absPath, err := storage.ResolveGamePath(game.FilePath, h.GameDirs)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game file not found"})
		return
	}

	// Security: validate the file path is within allowed directories
	if !storage.ValidateROMPath(absPath, h.GameDirs) {
		c.JSON(http.StatusForbidden, gin.H{"error": "file access denied"})
		return
	}

	// For .cue/.gdi files, serve a tar/zip bundle with companion track files.
	// This handles both new games (with disc records) and old DB entries
	// (without disc records) that were created before the scanner change.
	lower := strings.ToLower(game.FileName)
	if strings.HasSuffix(lower, ".cue") || strings.HasSuffix(lower, ".gdi") {
		companions, _, err := scanner.DiscCompanionFiles(absPath)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to read disc files"})
			return
		}

		format := c.Query("format")
		if format == "zip" {
			c.Header("Content-Type", "application/zip")
			c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", game.FileName+".zip"))
			c.Status(http.StatusOK)
			if err := serveZip(c.Writer, companions); err != nil {
				slog.Warn("error streaming zip for .cue game", "game", game.Title, "error", err)
			}
			return
		}

		if len(companions) > 1 {
			c.Header("Content-Type", "application/x-tar")
			c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", game.FileName+".tar"))
			c.Status(http.StatusOK)
			if err := serveTar(c.Writer, companions); err != nil {
				slog.Warn("error streaming tar for .cue game", "game", game.Title, "error", err)
			}
			return
		}
	}

	c.Header("Content-Disposition", fmt.Sprintf("inline; filename=%q", game.FileName))
	c.File(absPath)
}

// UpdateMetadata manually updates game metadata.
func (h *GameHandler) UpdateMetadata(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	var req struct {
		Title         string  `json:"title"`
		Description   string  `json:"description"`
		CoverURL      string  `json:"coverUrl"`
		ScreenshotURL string  `json:"screenshotUrl"`
		Developer     string  `json:"developer"`
		Publisher     string  `json:"publisher"`
		ReleaseDate   string  `json:"releaseDate"`
		Genre         string  `json:"genre"`
		Players       int     `json:"players"`
		Rating        float64 `json:"rating"`
		CoreOverride  string  `json:"coreOverride"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	if req.Title != "" {
		game.Title = req.Title
	}
	if req.Description != "" {
		game.Description = req.Description
	}
	if req.CoverURL != "" {
		game.CoverURL = req.CoverURL
	}
	if req.ScreenshotURL != "" {
		game.ScreenshotURL = req.ScreenshotURL
	}
	if req.Developer != "" {
		game.Developer = req.Developer
	}
	if req.Publisher != "" {
		game.Publisher = req.Publisher
	}
	if req.ReleaseDate != "" {
		game.ReleaseDate = req.ReleaseDate
	}
	if req.Genre != "" {
		game.Genre = req.Genre
	}
	if req.Players > 0 {
		game.Players = req.Players
	}
	if req.Rating > 0 {
		game.Rating = req.Rating
	}
	if req.CoreOverride != "" {
		game.CoreOverride = req.CoreOverride
	}

	if err := h.DB.Save(&game).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update game"})
		return
	}

	h.DB.Preload("Console").Preload("Screenshots").Preload("ReleaseDates").First(&game, game.ID)
	userID := getUserID(c)
	c.JSON(http.StatusOK, ToGameResponse(game, h.DB, userID))
}

// scanGameSummary is a lightweight game entry returned in scan results.
type scanGameSummary struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	ConsoleName string `json:"consoleName"`
}

// scanResultResponse extends the scan result with new game details and auto-scrape status.
type scanResultResponse struct {
	NewGames     int               `json:"newGames"`
	UpdatedGames int               `json:"updatedGames"`
	RemovedGames int               `json:"removedGames"`
	TotalGames   int               `json:"totalGames"`
	NewGamesList []scanGameSummary `json:"newGamesList,omitempty"`
	AutoScraping bool              `json:"autoScraping"`
}

// ScanGames triggers a library scan in the background.
// Returns 202 immediately; progress is reported via WebSocket events.
// Pass ?console=<abbreviation> to scan only games for a specific console.
func (h *GameHandler) ScanGames(c *gin.Context) {
	consoleAbbrev := c.Query("console")

	// Validate console abbreviation if provided
	if consoleAbbrev != "" {
		var con db.Console
		if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleAbbrev).First(&con).Error; err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "unknown console"})
			return
		}
		consoleAbbrev = con.Abbreviation // normalize casing
	}

	if !h.Scanner.TryStartScan() {
		c.JSON(http.StatusConflict, gin.H{"error": "a scan is already in progress"})
		return
	}

	h.Hub.Broadcast(ws.Event{Type: "scan_started", Payload: nil})
	c.JSON(http.StatusAccepted, gin.H{"message": "scan started in background"})

	go func() {
		defer h.Scanner.FinishScan()

		var scanArgs []string
		if consoleAbbrev != "" {
			scanArgs = append(scanArgs, consoleAbbrev)
		}
		result, err := h.Scanner.Scan(func(p scanner.ScanProgress) {
			h.Scanner.SetScanProgress(&p)
			h.Hub.Broadcast(ws.Event{Type: "scan_progress", Payload: p})
		}, scanArgs...)
		if err != nil {
			slog.Error("game library scan failed", "error", err)
			h.Hub.Broadcast(ws.Event{Type: "scan_error", Payload: gin.H{"error": "library scan failed"}})
			return
		}

		// Build response with new game summaries
		resp := scanResultResponse{
			NewGames:     result.NewGames,
			UpdatedGames: result.UpdatedGames,
			RemovedGames: result.RemovedGames,
			TotalGames:   result.TotalGames,
		}

		if len(result.NewGameIDs) > 0 {
			var newGames []db.Game
			h.DB.Preload("Console").Where("id IN ?", result.NewGameIDs).Find(&newGames)
			for _, g := range newGames {
				resp.NewGamesList = append(resp.NewGamesList, scanGameSummary{
					ID:          strconv.FormatUint(uint64(g.ID), 10),
					Title:       g.Title,
					ConsoleName: g.Console.Name,
				})
			}
		}

		h.Hub.Broadcast(ws.Event{Type: "scan_complete", Payload: resp})

		// Configure SteamGridDB if API key is set (best-effort artwork during auto-scrape)
		if apiKey := steamGridDBAPIKey(h.DB); apiKey != "" {
			h.Scraper.ConfigureSteamGridDB(apiKey)
		}

		// Auto-scrape new games if IGDB is configured
		if result.NewGames > 0 && h.Scraper.IsIGDBConfigured() {
			if h.Scraper.TryStartScrape() {
				h.Hub.Broadcast(ws.Event{Type: "scrape_started", Payload: nil})
				count, total, scrapeErr := h.Scraper.ScrapeAll("new", 0, func(p scraper.ScrapeProgress) {
					h.Scraper.SetScrapeProgress(&p)
					h.Hub.Broadcast(ws.Event{Type: "scrape_progress", Payload: p})
				})
				h.Scraper.FinishScrape()
				if scrapeErr != nil {
					slog.Error("auto-scrape after scan failed", "error", scrapeErr)
					h.Hub.Broadcast(ws.Event{Type: "scrape_error", Payload: gin.H{"error": "auto-scrape failed"}})
					return
				}
				slog.Info("auto-scrape after scan complete", "scraped", count, "total", total)
				h.Hub.Broadcast(ws.Event{Type: "scrape_complete", Payload: gin.H{"scraped": count, "total": total}})
			}
		}
	}()
}

// ScanStatus returns the current scan operation status.
func (h *GameHandler) ScanStatus(c *gin.Context) {
	active, progress := h.Scanner.GetScanStatus()

	if !active || progress == nil {
		c.JSON(http.StatusOK, gin.H{"active": active})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"active":  true,
		"phase":   progress.Phase,
		"current": progress.Current,
		"total":   progress.Total,
		"message": progress.Message,
	})
}

// ScrapeIfNeeded triggers an async scrape for a game that has never been scraped.
// Returns 202 if scraping was started, 200 if the game was already scraped.
func (h *GameHandler) ScrapeIfNeeded(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	if game.ScrapeAttempts > 0 {
		c.JSON(http.StatusOK, gin.H{"status": "already_scraped"})
		return
	}

	// Configure SteamGridDB if API key is set (best-effort artwork)
	if apiKey := steamGridDBAPIKey(h.DB); apiKey != "" {
		h.Scraper.ConfigureSteamGridDB(apiKey)
	}

	go func() {
		var g db.Game
		if err := h.DB.Preload("Console").First(&g, id).Error; err != nil {
			slog.Warn("auto-scrape: failed to load game", "id", id, "error", err)
			return
		}
		if err := h.Scraper.ScrapeGame(&g); err != nil {
			slog.Warn("auto-scrape failed", "game", g.Title, "error", err)
			return
		}
		// Reload with Screenshots so the WebSocket broadcast includes them.
		if err := h.DB.Preload("Console").Preload("Screenshots").First(&g, id).Error; err != nil {
			slog.Warn("auto-scrape: failed to reload game after scrape", "id", id, "error", err)
			return
		}
		h.Hub.Broadcast(ws.Event{
			Type:    "game_scraped",
			Payload: ToGameResponse(g, h.DB, 0),
		})
	}()

	c.JSON(http.StatusAccepted, gin.H{"status": "scraping"})
}

// UpdatePlayTime increments the play time for a game.
// POST /api/games/:id/play-time with {"seconds": number}.
func (h *GameHandler) UpdatePlayTime(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")
	uid, ok := userID.(uint)
	if !ok {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "unauthorized"})
		return
	}

	gid, err := strconv.ParseUint(gameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	// Verify game exists
	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	// maxPlayTimeSeconds caps total play time at ~100 years to prevent overflow.
	const maxPlayTimeSeconds int64 = 100 * 365 * 24 * 3600

	var req struct {
		Seconds int64 `json:"seconds" binding:"min=0,max=86400"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: seconds must be between 0 and 86400"})
		return
	}

	var ph db.PlayHistory
	result := h.DB.Where("user_id = ? AND game_id = ?", uid, gid).First(&ph)
	if result.Error == gorm.ErrRecordNotFound {
		ph = db.PlayHistory{
			UserID:     uid,
			GameID:     uint(gid),
			LastPlayed: time.Now(),
			PlayTime:   req.Seconds,
		}
		if err := h.DB.Create(&ph).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create play history"})
			return
		}
	} else {
		newTotal := ph.PlayTime + req.Seconds
		if newTotal > maxPlayTimeSeconds {
			newTotal = maxPlayTimeSeconds
		}
		ph.PlayTime = newTotal
		ph.LastPlayed = time.Now()
		if err := h.DB.Save(&ph).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update play time"})
			return
		}
	}

	// Record daily play activity for heatmap
	RecordDailyPlayActivity(h.DB, uid, req.Seconds)

	// Mark user as currently playing this game
	if h.Hub != nil {
		h.Hub.SetUserGame(uid, uint(gid))
	}

	// Emit activity event for started playing
	CreateActivityEvent(h.DB, h.Hub, uid, "started_playing", uint(gid), map[string]interface{}{
		"seconds": req.Seconds,
	})

	c.JSON(http.StatusOK, gin.H{
		"playTime":   ph.PlayTime,
		"lastPlayed": ph.LastPlayed,
	})
}

// StopPlaying clears the user's current game status.
// DELETE /api/games/:id/play-time
func (h *GameHandler) StopPlaying(c *gin.Context) {
	userID, _ := c.Get("userId")
	uid, ok := userID.(uint)
	if !ok {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "unauthorized"})
		return
	}

	if h.Hub != nil {
		h.Hub.SetUserGame(uid, 0)
	}

	c.JSON(http.StatusOK, gin.H{"status": "cleared"})
}

// DownloadDisc serves files for a specific disc in a multi-disc game.
// For single-file disc formats (.iso, .chd), serves the file directly.
// For multi-file disc formats (.cue+.bin), streams an uncompressed tar.
func (h *GameHandler) DownloadDisc(c *gin.Context) {
	gameID := c.Param("id")
	discNumberStr := c.Param("discNumber")
	discNumber, err := strconv.Atoi(discNumberStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid disc number"})
		return
	}

	var disc db.GameDisc
	if err := h.DB.Where("game_id = ? AND disc_number = ?", gameID, discNumber).First(&disc).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "disc not found"})
		return
	}

	// Resolve relative path to absolute for filesystem access
	absDiscPath, err := storage.ResolveGamePath(disc.FilePath, h.GameDirs)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "disc file not found"})
		return
	}

	// Security: validate the disc file path is within allowed directories
	if !storage.ValidateROMPath(absDiscPath, h.GameDirs) {
		c.JSON(http.StatusForbidden, gin.H{"error": "file access denied"})
		return
	}

	// Get companion files for this disc
	companions, _, err := scanner.DiscCompanionFiles(absDiscPath)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to read disc files"})
		return
	}

	// EmulatorJS (browser emulation) supports zip but not tar, so the web
	// frontend requests format=zip. This must be checked before the single-file
	// early return so that even single-file discs (.iso, .chd) get zipped.
	format := c.Query("format")
	if format == "zip" {
		c.Header("Content-Type", "application/zip")
		c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", disc.FileName+".zip"))
		c.Status(http.StatusOK)

		if err := serveZip(c.Writer, companions); err != nil {
			slog.Warn("error streaming zip for disc", "disc", discNumber, "error", err)
		}
		return
	}

	if len(companions) == 1 {
		// Single file (.iso, .chd, etc.) — serve directly
		c.Header("Content-Disposition", fmt.Sprintf("inline; filename=%q", disc.FileName))
		c.File(companions[0])
		return
	}

	// Default: tar stream (used by native player app)
	c.Header("Content-Type", "application/x-tar")
	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", disc.FileName+".tar"))
	c.Status(http.StatusOK)

	if err := serveTar(c.Writer, companions); err != nil {
		slog.Warn("error streaming tar for disc", "disc", discNumber, "error", err)
	}
}

// serveTar streams files as an uncompressed tar archive.
func serveTar(w io.Writer, filePaths []string) error {
	tw := tar.NewWriter(w)
	defer tw.Close()

	for _, path := range filePaths {
		info, err := os.Stat(path)
		if err != nil {
			return fmt.Errorf("stat file %s: %w", path, err)
		}

		header := &tar.Header{
			Name: filepath.Base(path),
			Size: info.Size(),
			Mode: 0644,
		}
		if err := tw.WriteHeader(header); err != nil {
			return fmt.Errorf("writing tar header for %s: %w", path, err)
		}

		f, err := os.Open(path)
		if err != nil {
			return fmt.Errorf("opening file %s: %w", path, err)
		}
		if _, err := io.Copy(tw, f); err != nil {
			f.Close()
			return fmt.Errorf("writing file %s to tar: %w", path, err)
		}
		f.Close()
	}

	return nil
}

// serveZip streams files as a zip archive. Used by EmulatorJS which supports
// zip extraction but not tar.
func serveZip(w io.Writer, filePaths []string) error {
	zw := zip.NewWriter(w)
	defer zw.Close()

	for _, path := range filePaths {
		info, err := os.Stat(path)
		if err != nil {
			return fmt.Errorf("stat file %s: %w", path, err)
		}

		header, err := zip.FileInfoHeader(info)
		if err != nil {
			return fmt.Errorf("creating zip header for %s: %w", path, err)
		}
		header.Name = filepath.Base(path)
		header.Method = zip.Store // no compression — ROM data doesn't compress well

		writer, err := zw.CreateHeader(header)
		if err != nil {
			return fmt.Errorf("writing zip header for %s: %w", path, err)
		}

		f, err := os.Open(path)
		if err != nil {
			return fmt.Errorf("opening file %s: %w", path, err)
		}
		if _, err := io.Copy(writer, f); err != nil {
			f.Close()
			return fmt.Errorf("writing file %s to zip: %w", path, err)
		}
		f.Close()
	}

	return nil
}

// UpdateVerificationTag sets a custom verification tag on a game (admin only).
func (h *GameHandler) UpdateVerificationTag(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	var req struct {
		Tag string `json:"tag"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request"})
		return
	}

	game.VerificationTag = req.Tag
	if err := h.DB.Save(&game).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update verification tag"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"verificationTag": game.VerificationTag})
}

// GetRecommendedCore returns the recommended libretro core for a game.
func (h *GameHandler) GetRecommendedCore(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	coreName := game.CoreOverride
	if coreName == "" {
		coreName = game.Console.DefaultCore
	}

	var core db.Core
	if err := h.DB.Where("name = ?", coreName).First(&core).Error; err != nil {
		// Return just the name if core isn't in DB
		c.JSON(http.StatusOK, gin.H{"coreName": coreName})
		return
	}

	c.JSON(http.StatusOK, core)
}

// gameStatsTopPlayer is a player entry in the game stats response.
type gameStatsTopPlayer struct {
	UserID   string `json:"userId"`
	Username string `json:"username"`
	AvatarURL string `json:"avatarUrl"`
	PlayTime int64  `json:"playTime"`
}

// gameStatsResponse is the response for GET /api/games/:id/stats.
type gameStatsResponse struct {
	TotalPlayers   int64                `json:"totalPlayers"`
	TotalPlayTime  int64                `json:"totalPlayTime"`
	AveragePlayTime int64              `json:"averagePlayTime"`
	TopPlayers     []gameStatsTopPlayer `json:"topPlayers"`
}

// GetGameStats returns aggregate community play statistics for a game.
func (h *GameHandler) GetGameStats(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	// Aggregate stats
	var stats struct {
		TotalPlayers  int64
		TotalPlayTime int64
	}
	if err := h.DB.Model(&db.PlayHistory{}).
		Where("game_id = ?", game.ID).
		Select("COUNT(DISTINCT user_id) as total_players, COALESCE(SUM(play_time), 0) as total_play_time").
		Scan(&stats).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch game stats"})
		return
	}

	var averagePlayTime int64
	if stats.TotalPlayers > 0 {
		averagePlayTime = stats.TotalPlayTime / stats.TotalPlayers
	}

	// Top players
	type topPlayerRow struct {
		UserID   uint
		Username string
		AvatarURL string
		PlayTime int64
	}
	var rows []topPlayerRow
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("play_histories.user_id, users.username, users.avatar_url, play_histories.play_time").
		Joins("JOIN users ON users.id = play_histories.user_id").
		Where("play_histories.game_id = ?", game.ID).
		Order("play_histories.play_time DESC").
		Limit(10).
		Scan(&rows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch top players"})
		return
	}

	topPlayers := make([]gameStatsTopPlayer, 0, len(rows))
	for _, r := range rows {
		topPlayers = append(topPlayers, gameStatsTopPlayer{
			UserID:   strconv.FormatUint(uint64(r.UserID), 10),
			Username: r.Username,
			AvatarURL: r.AvatarURL,
			PlayTime: r.PlayTime,
		})
	}

	c.JSON(http.StatusOK, gameStatsResponse{
		TotalPlayers:    stats.TotalPlayers,
		TotalPlayTime:   stats.TotalPlayTime,
		AveragePlayTime: averagePlayTime,
		TopPlayers:      topPlayers,
	})
}

// GetGameCheats returns cheat codes for a game.
func (h *GameHandler) GetGameCheats(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}
	var cheats []db.CheatCode
	if err := h.DB.Where("game_id = ?", game.ID).Order("cheat_index ASC").Find(&cheats).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch cheats"})
		return
	}
	type CheatResponse struct {
		ID          uint   `json:"id"`
		Index       int    `json:"index"`
		Description string `json:"description"`
		Code        string `json:"code"`
	}
	resp := make([]CheatResponse, len(cheats))
	for i, ch := range cheats {
		resp[i] = CheatResponse{
			ID:          ch.ID,
			Index:       ch.CheatIndex,
			Description: ch.Description,
			Code:        ch.Code,
		}
	}
	c.JSON(http.StatusOK, resp)
}

// escapeLikePattern escapes SQL LIKE wildcard characters in user input.
func escapeLikePattern(s string) string {
	s = strings.ReplaceAll(s, "\\", "\\\\")
	s = strings.ReplaceAll(s, "%", "\\%")
	s = strings.ReplaceAll(s, "_", "\\_")
	return s
}
