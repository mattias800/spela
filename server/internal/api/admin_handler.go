package api

import (
	"net/http"
	"sync"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// AdminHandler handles admin-only endpoints.
type AdminHandler struct {
	DB      *gorm.DB
	Scraper *scraper.Scraper
	Hub     *ws.Hub
	Storage *storage.Storage

	scrapeMu       sync.Mutex
	scraping       bool
	scrapeProgress *scraper.ScrapeProgress
}

// ListUsers returns all users (admin only).
func (h *AdminHandler) ListUsers(c *gin.Context) {
	var users []db.User
	if err := h.DB.Find(&users).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch users"})
		return
	}

	resp := make([]UserResponse, len(users))
	for i, u := range users {
		resp[i] = ToUserResponse(u)
	}
	c.JSON(http.StatusOK, resp)
}

// CreateUser creates a new user account (admin only).
func (h *AdminHandler) CreateUser(c *gin.Context) {
	var req struct {
		Username string `json:"username" binding:"required,min=3,max=64"`
		Email    string `json:"email" binding:"required,email"`
		Password string `json:"password" binding:"required,min=8"`
		Role     string `json:"role"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	if req.Role == "" {
		req.Role = "user"
	}
	if req.Role != "admin" && req.Role != "user" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "role must be 'admin' or 'user'"})
		return
	}

	// Check for duplicates
	var count int64
	h.DB.Model(&db.User{}).Where("username = ? OR email = ?", req.Username, req.Email).Count(&count)
	if count > 0 {
		c.JSON(http.StatusConflict, gin.H{"error": "username or email already exists"})
		return
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to hash password"})
		return
	}

	user := db.User{
		Username:     req.Username,
		Email:        req.Email,
		PasswordHash: hash,
		Role:         req.Role,
	}
	if err := h.DB.Create(&user).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create user"})
		return
	}

	c.JSON(http.StatusCreated, ToUserResponse(user))
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
		Role     string `json:"role"`
		Email    string `json:"email"`
		Password string `json:"password"`
		Disabled *bool  `json:"disabled"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	currentUserID, _ := c.Get("userId")

	// Owner protection
	if user.Role == db.RoleOwner && req.Role != "" {
		c.JSON(http.StatusForbidden, gin.H{"error": "cannot change the owner's role"})
		return
	}
	if req.Role == db.RoleOwner {
		c.JSON(http.StatusBadRequest, gin.H{"error": "cannot assign owner role"})
		return
	}
	if currentUserID == user.ID && req.Role != "" && req.Role != user.Role {
		c.JSON(http.StatusBadRequest, gin.H{"error": "cannot change your own role"})
		return
	}

	if req.Role != "" {
		if req.Role != db.RoleAdmin && req.Role != db.RoleUser {
			c.JSON(http.StatusBadRequest, gin.H{"error": "role must be 'admin' or 'user'"})
			return
		}
		user.Role = req.Role
	}
	if req.Email != "" {
		user.Email = req.Email
	}
	if req.Password != "" {
		if len(req.Password) < 8 {
			c.JSON(http.StatusBadRequest, gin.H{"error": "password must be at least 8 characters"})
			return
		}
		hash, err := auth.HashPassword(req.Password)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to hash password"})
			return
		}
		user.PasswordHash = hash
	}
	if req.Disabled != nil {
		if user.Role == db.RoleOwner {
			c.JSON(http.StatusForbidden, gin.H{"error": "cannot disable the owner"})
			return
		}
		user.Disabled = *req.Disabled
	}

	if err := h.DB.Save(&user).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update user"})
		return
	}

	c.JSON(http.StatusOK, ToUserResponse(user))
}

// secretSettingKeys are settings that should be masked in GET responses.
var secretSettingKeys = map[string]bool{
	"igdb_client_secret": true,
}

// GetSettings returns server settings (admin only).
// Secret values are masked with "********" placeholders.
func (h *AdminHandler) GetSettings(c *gin.Context) {
	var settings []db.ServerSetting
	if err := h.DB.Find(&settings).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch settings"})
		return
	}

	settingsMap := make(map[string]string)
	for _, s := range settings {
		if secretSettingKeys[s.Key] && s.Value != "" {
			settingsMap[s.Key] = secretMaskPlaceholder
		} else {
			settingsMap[s.Key] = s.Value
		}
	}

	c.JSON(http.StatusOK, settingsMap)
}

// secretMaskPlaceholder is the masked value returned for secret settings in GET responses.
const secretMaskPlaceholder = "********"

// UpdateSettings updates server settings (admin only).
// Secret keys whose value equals the mask placeholder are skipped to prevent
// overwriting the real secret with the masked value.
func (h *AdminHandler) UpdateSettings(c *gin.Context) {
	var req map[string]string
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	for key, value := range req {
		// Skip secret keys when value is the mask placeholder — the frontend
		// loaded "********" from GET and is sending it back unchanged.
		if secretSettingKeys[key] && value == secretMaskPlaceholder {
			continue
		}
		setting := db.ServerSetting{Key: key, Value: value}
		h.DB.Where("key = ?", key).Assign(setting).FirstOrCreate(&setting)
	}

	c.JSON(http.StatusOK, gin.H{"message": "settings updated"})
}

// MetadataMatches returns games needing admin attention: unscraped and unverified.
func (h *AdminHandler) MetadataMatches(c *gin.Context) {
	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)

	var unscraped []db.Game
	if err := h.DB.Where("scraper_id = '' OR scraper_id IS NULL").
		Preload("Console").Preload("Discs").
		Find(&unscraped).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch unscraped games"})
		return
	}

	var unverified []db.Game
	if err := h.DB.Where("verification_status = ? AND (verification_tag = '' OR verification_tag IS NULL)", "unverified").
		Preload("Console").Preload("Discs").
		Find(&unverified).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch unverified games"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"unscraped":  ToGameResponses(unscraped, h.DB, uid),
		"unverified": ToGameResponses(unverified, h.DB, uid),
	})
}

// TriggerScrape starts a metadata scraping operation (admin only).
// Only one scrape can run at a time; concurrent requests are rejected.
// Pass ?force=true to re-scrape all games, not just unscraped ones.
func (h *AdminHandler) TriggerScrape(c *gin.Context) {
	h.tryConfigureIGDB()

	force := c.Query("force") == "true"

	h.scrapeMu.Lock()
	if h.scraping {
		h.scrapeMu.Unlock()
		c.JSON(http.StatusConflict, gin.H{"error": "a scrape operation is already in progress"})
		return
	}
	h.scraping = true
	h.scrapeMu.Unlock()

	// Count matching games before launching the goroutine so we can
	// return the total in the HTTP response for immediate user feedback.
	var total int64
	if force {
		h.DB.Model(&db.Game{}).Count(&total)
	} else {
		h.DB.Model(&db.Game{}).Where("scraper_id = '' OR scraper_id IS NULL").Count(&total)
	}

	h.Hub.Broadcast(ws.Event{Type: "scrape_started", Payload: nil})

	go func() {
		defer func() {
			h.scrapeMu.Lock()
			h.scraping = false
			h.scrapeProgress = nil
			h.scrapeMu.Unlock()
		}()

		count, total, err := h.Scraper.ScrapeAll(force, func(p scraper.ScrapeProgress) {
			h.scrapeMu.Lock()
			h.scrapeProgress = &p
			h.scrapeMu.Unlock()

			h.Hub.Broadcast(ws.Event{Type: "scrape_progress", Payload: p})
		})
		if err != nil {
			h.Hub.Broadcast(ws.Event{Type: "scrape_error", Payload: gin.H{"error": err.Error()}})
			return
		}
		h.Hub.Broadcast(ws.Event{Type: "scrape_complete", Payload: gin.H{"scraped": count, "total": total}})
	}()

	c.JSON(http.StatusAccepted, gin.H{"message": "scrape started in background", "total": total})
}

// ScrapeStatus returns the current scrape operation status.
func (h *AdminHandler) ScrapeStatus(c *gin.Context) {
	h.scrapeMu.Lock()
	active := h.scraping
	var progress *scraper.ScrapeProgress
	if h.scrapeProgress != nil {
		p := *h.scrapeProgress
		progress = &p
	}
	h.scrapeMu.Unlock()

	if !active || progress == nil {
		c.JSON(http.StatusOK, gin.H{"active": false})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"active":    true,
		"current":   progress.Current,
		"total":     progress.Total,
		"gameName":  progress.GameName,
		"successes": progress.Successes,
		"failures":  progress.Failures,
	})
}

// DeleteUser permanently deletes a user and all their data (admin only).
func (h *AdminHandler) DeleteUser(c *gin.Context) {
	id := c.Param("id")
	var user db.User
	if err := h.DB.First(&user, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}

	if user.Role == db.RoleOwner {
		c.JSON(http.StatusForbidden, gin.H{"error": "cannot delete the owner"})
		return
	}

	currentUserID, _ := c.Get("userId")
	if currentUserID == user.ID {
		c.JSON(http.StatusBadRequest, gin.H{"error": "cannot delete yourself"})
		return
	}

	err := h.DB.Transaction(func(tx *gorm.DB) error {
		tx.Where("user_id = ?", user.ID).Delete(&db.Favorite{})
		tx.Where("user_id = ?", user.ID).Delete(&db.PlayHistory{})

		var saves []db.SaveState
		tx.Where("user_id = ?", user.ID).Find(&saves)
		for _, save := range saves {
			if h.Storage != nil {
				h.Storage.DeleteSave(save.FilePath)
			}
		}
		tx.Where("user_id = ?", user.ID).Delete(&db.SaveState{})

		tx.Where("user_id = ?", user.ID).Delete(&db.RefreshToken{})
		tx.Delete(&user)
		return nil
	})
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to delete user"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "user deleted"})
}

// ScrapeGame scrapes metadata for a single game (admin only).
func (h *AdminHandler) ScrapeGame(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	h.tryConfigureIGDB()

	if err := h.Scraper.ScrapeGame(&game); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "scrape failed: " + err.Error()})
		return
	}

	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)
	c.JSON(http.StatusOK, ToGameResponse(game, h.DB, uid))
}

// tryConfigureIGDB loads IGDB credentials from DB settings and configures the scraper's IGDB client.
// Always reloads from DB so that updated credentials are picked up without a server restart.
func (h *AdminHandler) tryConfigureIGDB() {
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
		return
	}

	// Skip re-creation if credentials haven't changed
	if h.Scraper.IGDBClient != nil &&
		h.Scraper.IGDBClient.ClientID == clientID &&
		h.Scraper.IGDBClient.ClientSecret == clientSecret {
		return
	}

	// Close old client to release its rate limiter ticker
	if h.Scraper.IGDBClient != nil {
		h.Scraper.IGDBClient.Close()
	}
	h.Scraper.IGDBClient = igdb.NewClient(clientID, clientSecret)
}

// GetStats returns admin dashboard statistics.
func (h *AdminHandler) GetStats(c *gin.Context) {
	var users, games, consoles, saves int64
	h.DB.Model(&db.User{}).Count(&users)
	h.DB.Model(&db.Game{}).Count(&games)
	h.DB.Model(&db.Console{}).Where("id IN (SELECT DISTINCT console_id FROM games)").Count(&consoles)
	h.DB.Model(&db.SaveState{}).Count(&saves)

	c.JSON(http.StatusOK, gin.H{
		"users":    users,
		"games":    games,
		"consoles": consoles,
		"saves":    saves,
	})
}
