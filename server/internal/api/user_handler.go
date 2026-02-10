package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// UserHandler handles user profile and preference endpoints.
type UserHandler struct {
	DB *gorm.DB
}

// GetProfile returns the current user's profile.
func (h *UserHandler) GetProfile(c *gin.Context) {
	userID, _ := c.Get("userId")
	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}
	c.JSON(http.StatusOK, user)
}

// UpdateProfile updates the current user's profile.
func (h *UserHandler) UpdateProfile(c *gin.Context) {
	userID, _ := c.Get("userId")
	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}

	var req struct {
		Email     string `json:"email"`
		AvatarURL string `json:"avatarUrl"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	if req.Email != "" {
		user.Email = req.Email
	}
	if req.AvatarURL != "" {
		user.AvatarURL = req.AvatarURL
	}

	if err := h.DB.Save(&user).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update profile"})
		return
	}

	c.JSON(http.StatusOK, user)
}

// preferencesResponse is the JSON shape for the preferences endpoints.
type preferencesResponse struct {
	ShowPerformanceOverlay bool `json:"showPerformanceOverlay"`
	AutoSaveEnabled        bool `json:"autoSaveEnabled"`
	AutoLoadSaveEnabled    bool `json:"autoLoadSaveEnabled"`
}

// GetPreferences returns the current user's emulation preferences.
func (h *UserHandler) GetPreferences(c *gin.Context) {
	userID, _ := c.Get("userId")
	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}
	c.JSON(http.StatusOK, preferencesResponse{
		ShowPerformanceOverlay: user.ShowPerfOverlay,
		AutoSaveEnabled:        user.AutoSaveEnabled,
		AutoLoadSaveEnabled:    user.AutoLoadSaveEnabled,
	})
}

// UpdatePreferences partially updates the current user's emulation preferences.
func (h *UserHandler) UpdatePreferences(c *gin.Context) {
	userID, _ := c.Get("userId")
	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}

	var req struct {
		ShowPerformanceOverlay *bool `json:"showPerformanceOverlay"`
		AutoSaveEnabled        *bool `json:"autoSaveEnabled"`
		AutoLoadSaveEnabled    *bool `json:"autoLoadSaveEnabled"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	if req.ShowPerformanceOverlay != nil {
		user.ShowPerfOverlay = *req.ShowPerformanceOverlay
	}
	if req.AutoSaveEnabled != nil {
		user.AutoSaveEnabled = *req.AutoSaveEnabled
	}
	if req.AutoLoadSaveEnabled != nil {
		user.AutoLoadSaveEnabled = *req.AutoLoadSaveEnabled
	}

	if err := h.DB.Save(&user).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update preferences"})
		return
	}

	c.JSON(http.StatusOK, preferencesResponse{
		ShowPerformanceOverlay: user.ShowPerfOverlay,
		AutoSaveEnabled:        user.AutoSaveEnabled,
		AutoLoadSaveEnabled:    user.AutoLoadSaveEnabled,
	})
}

// GetRecentGames returns the user's recently played games as a flat Game array.
func (h *UserHandler) GetRecentGames(c *gin.Context) {
	uid := getUserID(c)

	var history []db.PlayHistory
	if err := h.DB.Where("user_id = ?", uid).
		Preload("Game").Preload("Game.Console").
		Order("last_played desc").
		Limit(20).
		Find(&history).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch recent games"})
		return
	}

	// Collect game IDs for batch enrichment
	gameIDs := make([]uint, 0, len(history))
	for _, ph := range history {
		if ph.Game.ID != 0 {
			gameIDs = append(gameIDs, ph.Game.ID)
		}
	}
	data := loadUserGameData(h.DB, uid, gameIDs)

	// Flatten: return Game[] with play history data merged in
	games := make([]GameResponse, 0, len(history))
	for _, ph := range history {
		if ph.Game.ID == 0 {
			continue
		}
		resp := toGameResponseWithData(ph.Game, &data)
		resp.LastPlayedAt = &ph.LastPlayed
		resp.TotalPlayTime = ph.PlayTime
		games = append(games, resp)
	}

	c.JSON(http.StatusOK, games)
}

// GetFavorites returns the user's favorite games as a flat Game array.
func (h *UserHandler) GetFavorites(c *gin.Context) {
	uid := getUserID(c)

	var favorites []db.Favorite
	if err := h.DB.Where("user_id = ?", uid).
		Preload("Game").Preload("Game.Console").
		Find(&favorites).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch favorites"})
		return
	}

	// Collect game IDs for batch enrichment
	gameIDs := make([]uint, 0, len(favorites))
	for _, fav := range favorites {
		if fav.Game.ID != 0 {
			gameIDs = append(gameIDs, fav.Game.ID)
		}
	}
	data := loadUserGameData(h.DB, uid, gameIDs)

	// Flatten: return Game[] with isFavorite=true
	games := make([]GameResponse, 0, len(favorites))
	for _, fav := range favorites {
		if fav.Game.ID == 0 {
			continue
		}
		resp := toGameResponseWithData(fav.Game, &data)
		resp.IsFavorite = true
		games = append(games, resp)
	}

	c.JSON(http.StatusOK, games)
}

// AddFavorite adds a game to the user's favorites.
func (h *UserHandler) AddFavorite(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("gameId")

	// Verify game exists
	var game db.Game
	if err := h.DB.First(&game, gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	fav := db.Favorite{
		UserID: uid,
		GameID: game.ID,
	}

	if err := h.DB.Create(&fav).Error; err != nil {
		c.JSON(http.StatusConflict, gin.H{"error": "already favorited"})
		return
	}

	c.JSON(http.StatusCreated, gin.H{"message": "favorite added"})
}

// RemoveFavorite removes a game from the user's favorites.
func (h *UserHandler) RemoveFavorite(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("gameId")

	result := h.DB.Where("user_id = ? AND game_id = ?", uid, gameID).Delete(&db.Favorite{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "favorite not found"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "favorite removed"})
}
