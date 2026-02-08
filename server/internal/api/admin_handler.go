package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scraper"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// AdminHandler handles admin-only endpoints.
type AdminHandler struct {
	DB      *gorm.DB
	Scraper *scraper.Scraper
	Hub     *ws.Hub
}

// ListUsers returns all users (admin only).
func (h *AdminHandler) ListUsers(c *gin.Context) {
	var users []db.User
	if err := h.DB.Find(&users).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch users"})
		return
	}
	c.JSON(http.StatusOK, users)
}

// UpdateUser updates a user's role or details (admin only).
func (h *AdminHandler) UpdateUser(c *gin.Context) {
	id := c.Param("id")
	var user db.User
	if err := h.DB.First(&user, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}

	var req struct {
		Role  string `json:"role"`
		Email string `json:"email"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	if req.Role != "" {
		if req.Role != "admin" && req.Role != "user" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "role must be 'admin' or 'user'"})
			return
		}
		user.Role = req.Role
	}
	if req.Email != "" {
		user.Email = req.Email
	}

	if err := h.DB.Save(&user).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update user"})
		return
	}

	c.JSON(http.StatusOK, user)
}

// GetSettings returns server settings (admin only).
func (h *AdminHandler) GetSettings(c *gin.Context) {
	var settings []db.ServerSetting
	if err := h.DB.Find(&settings).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch settings"})
		return
	}

	settingsMap := make(map[string]string)
	for _, s := range settings {
		settingsMap[s.Key] = s.Value
	}

	c.JSON(http.StatusOK, settingsMap)
}

// UpdateSettings updates server settings (admin only).
func (h *AdminHandler) UpdateSettings(c *gin.Context) {
	var req map[string]string
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	for key, value := range req {
		setting := db.ServerSetting{Key: key, Value: value}
		h.DB.Where("key = ?", key).Assign(setting).FirstOrCreate(&setting)
	}

	c.JSON(http.StatusOK, gin.H{"message": "settings updated"})
}

// TriggerScrape starts a metadata scraping operation (admin only).
func (h *AdminHandler) TriggerScrape(c *gin.Context) {
	if !h.Scraper.IsConfigured() {
		c.JSON(http.StatusBadRequest, gin.H{"error": "scraper not configured; set ScreenScraper credentials in settings"})
		return
	}

	h.Hub.Broadcast(ws.Event{Type: "scrape_started", Payload: nil})

	go func() {
		count, err := h.Scraper.ScrapeAll()
		if err != nil {
			h.Hub.Broadcast(ws.Event{Type: "scrape_error", Payload: gin.H{"error": err.Error()}})
			return
		}
		h.Hub.Broadcast(ws.Event{Type: "scrape_complete", Payload: gin.H{"scraped": count}})
	}()

	c.JSON(http.StatusAccepted, gin.H{"message": "scrape started in background"})
}
