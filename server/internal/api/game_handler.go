package api

import (
	"fmt"
	"net/http"
	"path/filepath"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scanner"
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
}

// ListGames returns all games with optional filtering.
func (h *GameHandler) ListGames(c *gin.Context) {
	var games []db.Game
	query := h.DB.Preload("Console")

	if consoleID := c.Query("consoleId"); consoleID != "" {
		query = query.Where("console_id = ?", consoleID)
	}
	if search := c.Query("search"); search != "" {
		query = query.Where("title LIKE ?", "%"+search+"%")
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
		countQuery = countQuery.Where("title LIKE ?", "%"+search+"%")
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
	if err := h.DB.Preload("Console").First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	userID := getUserID(c)
	c.JSON(http.StatusOK, ToGameResponse(game, h.DB, userID))
}

// DownloadGame serves the ROM file for a game.
func (h *GameHandler) DownloadGame(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	// Security: validate the file path is within allowed directories
	if !storage.ValidateROMPath(game.FilePath, h.GameDirs) {
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

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", game.FileName))
	c.File(game.FilePath)
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
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
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

	h.DB.Preload("Console").First(&game, game.ID)
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

	file, header, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "save file required"})
		return
	}
	defer file.Close()

	name := c.DefaultPostForm("name", header.Filename)

	size, err := h.Storage.WriteSave(uid, uint(gid), header.Filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
		return
	}

	savePath := h.Storage.SaveStatePath(uid, uint(gid), header.Filename)
	save := db.SaveState{
		UserID:   uid,
		GameID:   uint(gid),
		Name:     name,
		FilePath: savePath,
		FileSize: size,
		IsAuto:   false,
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

	h.DB.Delete(&save)
	c.JSON(http.StatusOK, gin.H{"message": "save deleted"})
}

// UploadAutoSave uploads an auto-save for a game.
func (h *GameHandler) UploadAutoSave(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")
	uid := userID.(uint)

	gid, err := strconv.ParseUint(gameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	file, _, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "save file required"})
		return
	}
	defer file.Close()

	filename := "autosave.sav"
	size, err := h.Storage.WriteSave(uid, uint(gid), filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
		return
	}

	savePath := h.Storage.SaveStatePath(uid, uint(gid), filename)

	// Upsert: update existing auto-save or create new
	var save db.SaveState
	result := h.DB.Where("user_id = ? AND game_id = ? AND is_auto = ?", uid, gid, true).First(&save)
	if result.Error == gorm.ErrRecordNotFound {
		save = db.SaveState{
			UserID:   uid,
			GameID:   uint(gid),
			Name:     "Auto Save",
			FilePath: savePath,
			FileSize: size,
			IsAuto:   true,
		}
		h.DB.Create(&save)
	} else {
		save.FilePath = savePath
		save.FileSize = size
		h.DB.Save(&save)
	}

	c.JSON(http.StatusOK, save)
}

// GetAutoSave returns the latest auto-save for a game.
func (h *GameHandler) GetAutoSave(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")

	var save db.SaveState
	if err := h.DB.Where("user_id = ? AND game_id = ? AND is_auto = ?", userID, gameID, true).
		First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "no auto-save found"})
		return
	}

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filepath.Base(save.FilePath)))
	c.File(save.FilePath)
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
