package api

import (
	"archive/tar"
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
	query := h.DB.Preload("Console").Preload("Discs").Preload("Screenshots")

	if consoleAbbr := c.Query("consoleId"); consoleAbbr != "" {
		var console db.Console
		if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleAbbr).First(&console).Error; err == nil {
			query = query.Where("console_id = ?", console.ID)
		} else {
			// Unknown console abbreviation — return empty list
			c.JSON(http.StatusOK, []GameResponse{})
			return
		}
	}
	if search := c.Query("search"); search != "" {
		query = query.Where("title LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(search)+"%")
	}
	if genre := c.Query("genre"); genre != "" {
		query = query.Where("genre = ?", genre)
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
	allowedSorts := map[string]bool{"title": true, "created_at": true, "file_size": true, "rating": true}
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

	// Count with the same filters applied
	var total int64
	countQuery := h.DB.Model(&db.Game{})
	if consoleID := c.Query("consoleId"); consoleID != "" {
		countQuery = countQuery.Where("console_id = ?", consoleID)
	}
	if search := c.Query("search"); search != "" {
		countQuery = countQuery.Where("title LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(search)+"%")
	}
	if genre := c.Query("genre"); genre != "" {
		countQuery = countQuery.Where("genre = ?", genre)
	}
	countQuery.Count(&total)

	offset := (page - 1) * perPage
	if err := query.Offset(offset).Limit(perPage).Find(&games).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch games"})
		return
	}

	userID := getUserID(c)
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
	if err := h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").First(&game, id).Error; err != nil {
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

	// Update play history
	userID, _ := c.Get("userId")
	if uid, ok := userID.(uint); ok {
		var ph db.PlayHistory
		result := h.DB.Where("user_id = ? AND game_id = ?", uid, game.ID).First(&ph)
		if result.Error == gorm.ErrRecordNotFound {
			ph = db.PlayHistory{UserID: uid, GameID: game.ID, LastPlayed: time.Now()}
			h.DB.Create(&ph)
		} else {
			ph.LastPlayed = time.Now()
			h.DB.Save(&ph)
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

	h.DB.Preload("Console").Preload("Screenshots").First(&game, game.ID)
	userID := getUserID(c)
	c.JSON(http.StatusOK, ToGameResponse(game, h.DB, userID))
}

// ScanGames triggers a library scan.
func (h *GameHandler) ScanGames(c *gin.Context) {
	h.Hub.Broadcast(ws.Event{Type: "scan_started", Payload: nil})

	result, err := h.Scanner.Scan()
	if err != nil {
		h.Hub.Broadcast(ws.Event{Type: "scan_error", Payload: gin.H{"error": err.Error()}})
		c.JSON(http.StatusInternalServerError, gin.H{"error": "scan failed: " + err.Error()})
		return
	}

	h.Hub.Broadcast(ws.Event{Type: "scan_complete", Payload: result})
	c.JSON(http.StatusOK, result)
}

// ListSaves returns save states for a user and game.
func (h *GameHandler) ListSaves(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")

	var saves []db.SaveState
	if err := h.DB.Where("user_id = ? AND game_id = ?", userID, gameID).
		Order("created_at desc").Find(&saves).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch saves"})
		return
	}

	c.JSON(http.StatusOK, saves)
}

// UploadSave uploads a save state.
func (h *GameHandler) UploadSave(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")
	uid := userID.(uint)

	gid, err := strconv.ParseUint(gameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, header, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "save file required"})
		return
	}
	defer file.Close()

	if err := checkStorageQuota(h.DB, uid, header.Size); err != nil {
		c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "storage quota exceeded"})
		return
	}

	name := c.DefaultPostForm("name", header.Filename)
	screenshotURL := c.PostForm("screenshotUrl")

	// Handle optional screenshot upload
	if screenshotURL == "" {
		if ssFile, ssHeader, ssErr := c.Request.FormFile("screenshot"); ssErr == nil {
			defer ssFile.Close()
			subpath := fmt.Sprintf("save-screenshots/user_%d/game_%d/%s", uid, gid, filepath.Base(ssHeader.Filename))
			if stored, writeErr := h.Storage.WriteImage(subpath, ssFile); writeErr == nil {
				screenshotURL = stored
			}
		}
	}

	size, err := h.Storage.WriteSave(uid, uint(gid), header.Filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
		return
	}

	savePath := h.Storage.SaveStatePath(uid, uint(gid), header.Filename)
	save := db.SaveState{
		UserID:        uid,
		GameID:        uint(gid),
		Name:          name,
		FilePath:      savePath,
		FileSize:      size,
		ScreenshotURL: screenshotURL,
		IsAuto:        false,
	}
	if err := h.DB.Create(&save).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create save record"})
		return
	}

	c.JSON(http.StatusCreated, save)
}

// DownloadSave serves a save state file.
func (h *GameHandler) DownloadSave(c *gin.Context) {
	saveID := c.Param("saveId")
	userID, _ := c.Get("userId")

	var save db.SaveState
	if err := h.DB.Where("id = ? AND user_id = ?", saveID, userID).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "save not found"})
		return
	}

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filepath.Base(save.FilePath)))
	c.File(save.FilePath)
}

// DeleteSave removes a save state.
func (h *GameHandler) DeleteSave(c *gin.Context) {
	saveID := c.Param("saveId")
	userID, _ := c.Get("userId")

	var save db.SaveState
	if err := h.DB.Where("id = ? AND user_id = ?", saveID, userID).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "save not found"})
		return
	}

	if err := h.Storage.DeleteSave(save.FilePath); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to delete save file"})
		return
	}

	// Clean up screenshot file if present
	if save.ScreenshotURL != "" {
		screenshotPath := h.Storage.ImagePath(save.ScreenshotURL)
		h.Storage.DeleteSave(screenshotPath)
	}

	h.DB.Delete(&save)
	c.JSON(http.StatusOK, gin.H{"message": "save deleted"})
}

// UploadAutoSave uploads an auto-save for a game.
// Creates a new auto-save each time (keeping up to 5 per user per game).
func (h *GameHandler) UploadAutoSave(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")
	uid := userID.(uint)

	gid, err := strconv.ParseUint(gameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, autoHeader, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "save file required"})
		return
	}
	defer file.Close()

	if err := checkStorageQuota(h.DB, uid, autoHeader.Size); err != nil {
		c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "storage quota exceeded"})
		return
	}

	filename := fmt.Sprintf("autosave_%d.sav", time.Now().UnixNano())
	screenshotURL := c.PostForm("screenshotUrl")

	// Handle optional screenshot upload
	if screenshotURL == "" {
		if ssFile, ssHeader, ssErr := c.Request.FormFile("screenshot"); ssErr == nil {
			defer ssFile.Close()
			subpath := fmt.Sprintf("save-screenshots/user_%d/game_%d/%s", uid, gid, filepath.Base(ssHeader.Filename))
			if stored, writeErr := h.Storage.WriteImage(subpath, ssFile); writeErr == nil {
				screenshotURL = stored
			}
		}
	}

	size, err := h.Storage.WriteSave(uid, uint(gid), filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
		return
	}

	savePath := h.Storage.SaveStatePath(uid, uint(gid), filename)

	// Always create a new auto-save
	save := db.SaveState{
		UserID:        uid,
		GameID:        uint(gid),
		Name:          "Auto Save",
		FilePath:      savePath,
		FileSize:      size,
		ScreenshotURL: screenshotURL,
		IsAuto:        true,
	}
	h.DB.Create(&save)

	// Clean up old auto-saves: keep only the last 5
	const maxAutoSaves = 5
	var oldSaves []db.SaveState
	h.DB.Where("user_id = ? AND game_id = ? AND is_auto = ?", uid, gid, true).
		Order("created_at DESC").
		Offset(maxAutoSaves).
		Find(&oldSaves)

	for _, old := range oldSaves {
		h.Storage.DeleteSave(old.FilePath)
		h.DB.Delete(&old)
	}

	c.JSON(http.StatusOK, save)
}

// GetAutoSave returns the most recent auto-save for a game.
func (h *GameHandler) GetAutoSave(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")

	var save db.SaveState
	if err := h.DB.Where("user_id = ? AND game_id = ? AND is_auto = ?", userID, gameID, true).
		Order("created_at DESC").
		First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "no auto-save found"})
		return
	}

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filepath.Base(save.FilePath)))
	c.File(save.FilePath)
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

	var req struct {
		Seconds int64 `json:"seconds" binding:"required,min=1"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: seconds must be a positive integer"})
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
		ph.PlayTime += req.Seconds
		ph.LastPlayed = time.Now()
		if err := h.DB.Save(&ph).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update play time"})
			return
		}
	}

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

	if len(companions) == 1 {
		// Single file (.iso, .chd, etc.) — serve directly
		c.Header("Content-Disposition", fmt.Sprintf("inline; filename=%q", disc.FileName))
		c.File(companions[0])
		return
	}

	// Multiple files (.cue + .bin) — serve as tar stream
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

// escapeLikePattern escapes SQL LIKE wildcard characters in user input.
func escapeLikePattern(s string) string {
	s = strings.ReplaceAll(s, "\\", "\\\\")
	s = strings.ReplaceAll(s, "%", "\\%")
	s = strings.ReplaceAll(s, "_", "\\_")
	return s
}
