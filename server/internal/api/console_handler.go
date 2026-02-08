package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// ConsoleHandler handles console-related endpoints.
type ConsoleHandler struct {
	DB *gorm.DB
}

// ListConsoles returns all consoles with game counts.
func (h *ConsoleHandler) ListConsoles(c *gin.Context) {
	var consoles []db.Console
	if err := h.DB.Find(&consoles).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch consoles"})
		return
	}

	// Attach game counts
	for i := range consoles {
		var count int64
		h.DB.Model(&db.Game{}).Where("console_id = ?", consoles[i].ID).Count(&count)
		consoles[i].GameCount = int(count)
	}

	// Convert to API response format
	result := make([]ConsoleResponse, len(consoles))
	for i, con := range consoles {
		result[i] = ToConsoleResponse(con)
	}

	c.JSON(http.StatusOK, result)
}

// ListConsoleGames returns games for a specific console as a flat Game array.
func (h *ConsoleHandler) ListConsoleGames(c *gin.Context) {
	consoleID := c.Param("id")

	var console db.Console
	if err := h.DB.First(&console, consoleID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "console not found"})
		return
	}

	var games []db.Game
	if err := h.DB.Where("console_id = ?", consoleID).
		Preload("Console").
		Order("title asc").
		Find(&games).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch games"})
		return
	}

	userID := getUserID(c)
	c.JSON(http.StatusOK, ToGameResponses(games, h.DB, userID))
}

// getUserID extracts the authenticated user's ID from the context.
func getUserID(c *gin.Context) uint {
	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)
	return uid
}
