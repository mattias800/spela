package api

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

// GetStats returns admin dashboard statistics.
func (h *AdminHandler) GetStats(c *gin.Context) {
	var users, games, consoles, saves int64
	h.DB.Model(&db.User{}).Count(&users)
	h.DB.Model(&db.Game{}).Count(&games)
	h.DB.Model(&db.Console{}).Where("id IN (SELECT DISTINCT console_id FROM games)").Count(&consoles)
	h.DB.Model(&db.SessionSaveState{}).Count(&saves)

	c.JSON(http.StatusOK, gin.H{
		"users":    users,
		"games":    games,
		"consoles": consoles,
		"saves":    saves,
	})
}

// CoreCompatibilityEntry is the API response for a single console's core compatibility.
type CoreCompatibilityEntry struct {
	ConsoleID   string `json:"consoleId"`
	ConsoleName string `json:"consoleName"`
	NativeCore  string `json:"nativeCore"`
	WebCore     string `json:"webCore"`
	Matched     bool   `json:"matched"`
}

// GetCoreCompatibility returns a list of consoles showing core compatibility
// between the native player (DefaultCore) and the web browser (EmulatorJSCore).
// GET /api/admin/core-compatibility
func (h *AdminHandler) GetCoreCompatibility(c *gin.Context) {
	var consoles []db.Console
	if err := h.DB.Order("name ASC").Find(&consoles).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch consoles"})
		return
	}

	result := make([]CoreCompatibilityEntry, 0, len(consoles))
	for _, console := range consoles {
		abbr := strings.ToLower(console.Abbreviation)
		matched := console.DefaultCore == console.EmulatorJSCore
		result = append(result, CoreCompatibilityEntry{
			ConsoleID:   abbr,
			ConsoleName: console.Name,
			NativeCore:  console.DefaultCore,
			WebCore:     console.EmulatorJSCore,
			Matched:     matched,
		})
	}

	c.JSON(http.StatusOK, gin.H{"consoles": result})
}
