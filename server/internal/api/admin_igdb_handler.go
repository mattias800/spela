package api

import (
	"log/slog"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/igdb"
	"gorm.io/gorm"
)

// IGDBHandler handles IGDB-related admin endpoints.
type IGDBHandler struct {
	DB *gorm.DB
}

// TestIGDB validates IGDB credentials by attempting a Twitch OAuth token exchange.
func (h *IGDBHandler) TestIGDB(c *gin.Context) {
	var req struct {
		ClientID     string `json:"clientId"`
		ClientSecret string `json:"clientSecret"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "clientId and clientSecret are required"})
		return
	}
	if req.ClientID == "" || req.ClientSecret == "" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "clientId and clientSecret are required"})
		return
	}

	client := igdb.NewClient("", "")
	defer client.Close()
	if err := client.TestCredentials(req.ClientID, req.ClientSecret); err != nil {
		slog.Warn("IGDB credential test failed", "error", err)
		c.JSON(http.StatusOK, gin.H{"success": false, "error": "credential validation failed"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"success": true})
}

// GetIGDBStatus returns the current IGDB configuration status.
// Checks environment variables first, then database settings.
// Does NOT make live API calls to Twitch — use "Test Connection" for that.
func (h *IGDBHandler) GetIGDBStatus(c *gin.Context) {
	source := IGDBSource(h.DB)

	if source == "none" {
		c.JSON(http.StatusOK, gin.H{
			"configured": false,
			"status":     "not_configured",
			"source":     "none",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"configured": true,
		"status":     "connected",
		"source":     source,
	})
}
