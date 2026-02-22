package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
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
		c.JSON(http.StatusBadRequest, gin.H{"error": "clientId and clientSecret are required"})
		return
	}
	if req.ClientID == "" || req.ClientSecret == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "clientId and clientSecret are required"})
		return
	}

	client := igdb.NewClient("", "")
	defer client.Close()
	if err := client.TestCredentials(req.ClientID, req.ClientSecret); err != nil {
		c.JSON(http.StatusOK, gin.H{"success": false, "error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"success": true})
}

// GetIGDBStatus returns the current IGDB configuration status.
// Only checks whether credentials exist in the database — does NOT make live
// API calls to Twitch. Use the "Test Connection" button for live validation.
func (h *IGDBHandler) GetIGDBStatus(c *gin.Context) {
	var settings []db.ServerSetting
	h.DB.Where("key IN ?", []string{
		"igdb_client_id", "igdb_client_secret",
	}).Find(&settings)

	sm := make(map[string]string)
	for _, s := range settings {
		sm[s.Key] = s.Value
	}

	clientID := sm["igdb_client_id"]
	clientSecret := sm["igdb_client_secret"]

	if clientID == "" || clientSecret == "" {
		c.JSON(http.StatusOK, gin.H{
			"configured": false,
			"status":     "not_configured",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"configured": true,
		"status":     "connected",
	})
}
