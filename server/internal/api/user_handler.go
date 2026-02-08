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

// GetRecentGames returns the user's recently played games.
func (h *UserHandler) GetRecentGames(c *gin.Context) {
	userID, _ := c.Get("userId")

	var history []db.PlayHistory
	if err := h.DB.Where("user_id = ?", userID).
		Preload("Game").Preload("Game.Console").
		Order("last_played desc").
		Limit(20).
		Find(&history).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch recent games"})
		return
	}

	c.JSON(http.StatusOK, history)
}

// GetFavorites returns the user's favorite games.
func (h *UserHandler) GetFavorites(c *gin.Context) {
	userID, _ := c.Get("userId")

	var favorites []db.Favorite
	if err := h.DB.Where("user_id = ?", userID).
		Preload("Game").Preload("Game.Console").
		Find(&favorites).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch favorites"})
		return
	}

	c.JSON(http.StatusOK, favorites)
}

// AddFavorite adds a game to the user's favorites.
func (h *UserHandler) AddFavorite(c *gin.Context) {
	userID, _ := c.Get("userId")
	uid := userID.(uint)
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

	c.JSON(http.StatusCreated, fav)
}

// RemoveFavorite removes a game from the user's favorites.
func (h *UserHandler) RemoveFavorite(c *gin.Context) {
	userID, _ := c.Get("userId")
	gameID := c.Param("gameId")

	result := h.DB.Where("user_id = ? AND game_id = ?", userID, gameID).Delete(&db.Favorite{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "favorite not found"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "favorite removed"})
}
