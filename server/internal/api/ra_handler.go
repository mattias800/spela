package api

import (
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"gorm.io/gorm"
)

// RAHandler handles RetroAchievements endpoints.
type RAHandler struct {
	DB       *gorm.DB
	RAClient *retroachievements.RAClient
	GameDir  string
}

// LinkAccount links a user's RetroAchievements account by exchanging credentials for a token.
func (h *RAHandler) LinkAccount(c *gin.Context) {
	uid := getUserID(c)

	var req struct {
		Username string `json:"username" binding:"required"`
		Password string `json:"password" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "username and password are required"})
		return
	}

	token, err := h.RAClient.LoginWithPassword(req.Username, req.Password)
	if err != nil {
		slog.Warn("RA login failed", "user_id", uid, "error", err)
		c.JSON(http.StatusUnauthorized, gin.H{"error": "RetroAchievements login failed: invalid credentials"})
		return
	}

	// Upsert credential
	var cred db.RetroAchievementCredential
	result := h.DB.Where("user_id = ?", uid).First(&cred)
	if result.Error == nil {
		cred.RAUsername = req.Username
		cred.RAToken = token
		if err := h.DB.Save(&cred).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update RA credentials"})
			return
		}
	} else {
		cred = db.RetroAchievementCredential{
			UserID:     uid,
			RAUsername:  req.Username,
			RAToken:    token,
		}
		if err := h.DB.Create(&cred).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to store RA credentials"})
			return
		}
	}

	c.JSON(http.StatusOK, gin.H{"linked": true, "username": req.Username})
}

// UnlinkAccount removes a user's RetroAchievements credentials.
func (h *RAHandler) UnlinkAccount(c *gin.Context) {
	uid := getUserID(c)

	result := h.DB.Where("user_id = ?", uid).Delete(&db.RetroAchievementCredential{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "no RA account linked"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"linked": false})
}

// GetStatus returns the user's RA link status.
func (h *RAHandler) GetStatus(c *gin.Context) {
	uid := getUserID(c)

	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		c.JSON(http.StatusOK, gin.H{"linked": false, "username": "", "hardcoreEnabled": false})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"linked":          true,
		"username":        cred.RAUsername,
		"hardcoreEnabled": cred.HardcoreEnabled,
	})
}

// UpdateSettings updates RA-specific settings for the user.
func (h *RAHandler) UpdateSettings(c *gin.Context) {
	uid := getUserID(c)

	var req struct {
		HardcoreEnabled *bool `json:"hardcoreEnabled"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "no RA account linked"})
		return
	}

	if req.HardcoreEnabled != nil {
		cred.HardcoreEnabled = *req.HardcoreEnabled
	}

	if err := h.DB.Save(&cred).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update RA settings"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"linked":          true,
		"username":        cred.RAUsername,
		"hardcoreEnabled": cred.HardcoreEnabled,
	})
}

// GetToken returns the user's RA credentials for the player app.
func (h *RAHandler) GetToken(c *gin.Context) {
	uid := getUserID(c)

	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "no RA account linked"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"username": cred.RAUsername,
		"token":    cred.RAToken,
	})
}

// GetGameAchievements returns achievements for a game, using cache when available.
func (h *RAHandler) GetGameAchievements(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")

	// Look up the game
	var game db.Game
	if err := h.DB.First(&game, gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	// Get user's RA credentials
	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "no RA account linked"})
		return
	}

	// Compute MD5 hash of the ROM file
	romPath := filepath.Join(h.GameDir, game.FilePath)
	hash, err := computeMD5(romPath)
	if err != nil {
		slog.Error("failed to compute ROM hash", "path", romPath, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute ROM hash"})
		return
	}

	// Look up RA game ID from hash
	raGameID, err := h.RAClient.GetGameIDFromHash(hash)
	if err != nil {
		slog.Warn("RA game ID lookup failed", "hash", hash, "error", err)
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found on RetroAchievements"})
		return
	}

	// Check cache (valid for 24 hours)
	var cache db.GameAchievementCache
	cacheHit := h.DB.Where("ra_game_id = ?", raGameID).First(&cache).Error == nil
	if cacheHit && time.Since(cache.CachedAt) < 24*time.Hour {
		var achievements []retroachievements.Achievement
		if err := json.Unmarshal([]byte(cache.AchievementJSON), &achievements); err == nil {
			c.JSON(http.StatusOK, gin.H{
				"raGameId":     raGameID,
				"title":        cache.Title,
				"achievements": achievements,
				"totalCount":   cache.TotalCount,
				"totalPoints":  cache.TotalPoints,
			})
			return
		}
	}

	// Fetch from RA API
	gameInfo, _, err := h.RAClient.GetGameInfoAndUserProgress(cred.RAUsername, cred.RAToken, raGameID)
	if err != nil {
		slog.Error("failed to fetch RA game info", "ra_game_id", raGameID, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch achievements from RetroAchievements"})
		return
	}

	// Cache the result
	achJSON, _ := json.Marshal(gameInfo.Achievements)
	if cacheHit {
		cache.Title = gameInfo.Title
		cache.AchievementJSON = string(achJSON)
		cache.TotalCount = gameInfo.TotalCount
		cache.TotalPoints = gameInfo.TotalPoints
		cache.CachedAt = time.Now()
		cache.GameID = game.ID
		h.DB.Save(&cache)
	} else {
		h.DB.Create(&db.GameAchievementCache{
			RAGameID:        raGameID,
			GameID:          game.ID,
			Title:           gameInfo.Title,
			AchievementJSON: string(achJSON),
			TotalCount:      gameInfo.TotalCount,
			TotalPoints:     gameInfo.TotalPoints,
			CachedAt:        time.Now(),
		})
	}

	c.JSON(http.StatusOK, gin.H{
		"raGameId":     raGameID,
		"title":        gameInfo.Title,
		"achievements": gameInfo.Achievements,
		"totalCount":   gameInfo.TotalCount,
		"totalPoints":  gameInfo.TotalPoints,
	})
}

// GetAchievementProgress returns the user's achievement progress for a game.
func (h *RAHandler) GetAchievementProgress(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")

	// Look up the game
	var game db.Game
	if err := h.DB.First(&game, gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	// Get user's RA credentials
	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "no RA account linked"})
		return
	}

	// Compute MD5 hash of the ROM file
	romPath := filepath.Join(h.GameDir, game.FilePath)
	hash, err := computeMD5(romPath)
	if err != nil {
		slog.Error("failed to compute ROM hash", "path", romPath, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute ROM hash"})
		return
	}

	// Look up RA game ID from hash
	raGameID, err := h.RAClient.GetGameIDFromHash(hash)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found on RetroAchievements"})
		return
	}

	// Fetch fresh progress from RA API
	_, userProgress, err := h.RAClient.GetGameInfoAndUserProgress(cred.RAUsername, cred.RAToken, raGameID)
	if err != nil {
		slog.Error("failed to fetch RA progress", "ra_game_id", raGameID, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch progress from RetroAchievements"})
		return
	}

	// Store/update progress in DB
	for _, p := range userProgress {
		unlockedAt, _ := time.Parse("2006-01-02 15:04:05", p.UnlockedAt)
		var existing db.UserAchievementProgress
		result := h.DB.Where("user_id = ? AND achievement_ra_id = ?", uid, p.AchievementID).First(&existing)
		if result.Error == nil {
			existing.UnlockedAt = unlockedAt
			existing.IsHardcore = p.IsHardcore
			h.DB.Save(&existing)
		} else {
			h.DB.Create(&db.UserAchievementProgress{
				UserID:          uid,
				AchievementRAID: p.AchievementID,
				RAGameID:        raGameID,
				UnlockedAt:      unlockedAt,
				IsHardcore:      p.IsHardcore,
			})
		}
	}

	c.JSON(http.StatusOK, gin.H{
		"raGameId": raGameID,
		"progress": userProgress,
	})
}

// computeMD5 calculates the MD5 hash of a file.
func computeMD5(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", fmt.Errorf("opening file for hash: %w", err)
	}
	defer f.Close()

	h := md5.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", fmt.Errorf("computing file hash: %w", err)
	}

	return hex.EncodeToString(h.Sum(nil)), nil
}
