package api

import (
	"log/slog"
	"net/http"
	"os"
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
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
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
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
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
		if user.Role == db.RoleOwner {
			c.JSON(http.StatusForbidden, gin.H{"error": "cannot change the owner's password"})
			return
		}
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
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
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

// MetadataMatches returns games needing admin attention: unscraped, unverified,
// and incomplete (scraped only via LibRetro fallback, missing IGDB metadata).
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

	var incomplete []db.Game
	if err := h.DB.Where("scraper_id = 'libretro'").
		Preload("Console").Preload("Discs").
		Find(&incomplete).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch incomplete games"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"unscraped":  ToGameResponses(unscraped, h.DB, uid),
		"unverified": ToGameResponses(unverified, h.DB, uid),
		"incomplete": ToGameResponses(incomplete, h.DB, uid),
	})
}

// TriggerScrape starts a metadata scraping operation (admin only).
// Only one scrape can run at a time; concurrent requests are rejected.
// Pass ?mode=all to re-scrape all games, ?mode=fallback to re-scrape
// games that only have LibRetro metadata. Default scrapes unscraped games only.
// Legacy ?force=true is equivalent to ?mode=all.
func (h *AdminHandler) TriggerScrape(c *gin.Context) {
	h.tryConfigureIGDB()

	mode := c.Query("mode")
	if mode == "" && c.Query("force") == "true" {
		mode = "all"
	}

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
	switch mode {
	case "all":
		h.DB.Model(&db.Game{}).Count(&total)
	case "fallback":
		h.DB.Model(&db.Game{}).Where("scraper_id = 'libretro'").Count(&total)
	default:
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

		count, total, err := h.Scraper.ScrapeAll(mode, func(p scraper.ScrapeProgress) {
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
		slog.Warn("scrape failed", "game", game.Title, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "scrape failed"})
		return
	}

	// Reload with screenshots for the response
	h.DB.Preload("Screenshots").First(&game, game.ID)
	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)
	c.JSON(http.StatusOK, ToGameResponse(game, h.DB, uid))
}

// tryConfigureIGDB loads IGDB credentials and configures the scraper's IGDB client.
// Environment variables take precedence over database settings.
func (h *AdminHandler) tryConfigureIGDB() {
	clientID, clientSecret := igdbCredentials(h.DB)

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

// CoverOption represents a single available cover art source.
type CoverOption struct {
	Source string `json:"source"`
	URL    string `json:"url"`
}

// GetGameCovers returns the available cover art options for a game.
func (h *AdminHandler) GetGameCovers(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	var covers []CoverOption

	if game.LibRetroCoverURL != "" {
		covers = append(covers, CoverOption{Source: "libretro", URL: resolveImageURL(game.LibRetroCoverURL)})
	}

	if game.IGDBCoverURL != "" {
		covers = append(covers, CoverOption{Source: "igdb", URL: resolveImageURL(game.IGDBCoverURL)})
	}

	// Include the current cover as "custom" if it differs from both known sources
	// (e.g. pre-migration games with the old boxart.png naming)
	if game.CoverURL != "" && game.CoverURL != game.LibRetroCoverURL && game.CoverURL != game.IGDBCoverURL {
		covers = append(covers, CoverOption{Source: "custom", URL: resolveImageURL(game.CoverURL)})
	}

	// Determine which source is active
	active := ""
	if game.CoverURL != "" {
		switch game.CoverURL {
		case game.LibRetroCoverURL:
			active = "libretro"
		case game.IGDBCoverURL:
			active = "igdb"
		default:
			active = "custom"
		}
	}

	c.JSON(http.StatusOK, gin.H{
		"active": active,
		"covers": covers,
	})
}

// SetGameCover sets the active cover art for a game from one of the available sources.
func (h *AdminHandler) SetGameCover(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	var req struct {
		Source string `json:"source" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	var newCoverURL string
	switch req.Source {
	case "libretro":
		if game.LibRetroCoverURL == "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "no LibRetro cover available"})
			return
		}
		newCoverURL = game.LibRetroCoverURL
	case "igdb":
		if game.IGDBCoverURL == "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "no IGDB cover available"})
			return
		}
		newCoverURL = game.IGDBCoverURL
	default:
		c.JSON(http.StatusBadRequest, gin.H{"error": "source must be 'libretro' or 'igdb'"})
		return
	}

	if err := h.DB.Model(&game).Updates(map[string]interface{}{
		"cover_url":          newCoverURL,
		"cover_manually_set": true,
	}).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update cover"})
		return
	}

	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)
	if err := h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").First(&game, id).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to reload game"})
		return
	}
	c.JSON(http.StatusOK, ToGameResponse(game, h.DB, uid))
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

// igdbCredentials returns the IGDB client ID and secret.
// Environment variables SPELA_IGDB_CLIENT_ID / SPELA_IGDB_CLIENT_SECRET take
// precedence over database settings.
func igdbCredentials(database *gorm.DB) (clientID, clientSecret string) {
	clientID = os.Getenv("SPELA_IGDB_CLIENT_ID")
	clientSecret = os.Getenv("SPELA_IGDB_CLIENT_SECRET")
	if clientID != "" && clientSecret != "" {
		return clientID, clientSecret
	}

	var settings []db.ServerSetting
	database.Where("key IN ?", []string{
		"igdb_client_id", "igdb_client_secret",
	}).Find(&settings)

	sm := make(map[string]string)
	for _, s := range settings {
		sm[s.Key] = s.Value
	}
	return sm["igdb_client_id"], sm["igdb_client_secret"]
}

// IGDBSource returns "env" if IGDB credentials are set via environment variables,
// "database" if set via admin settings, or "none" if not configured.
func IGDBSource(database *gorm.DB) string {
	if os.Getenv("SPELA_IGDB_CLIENT_ID") != "" && os.Getenv("SPELA_IGDB_CLIENT_SECRET") != "" {
		return "env"
	}

	var count int64
	database.Model(&db.ServerSetting{}).
		Where("key IN ? AND value != ''", []string{"igdb_client_id", "igdb_client_secret"}).
		Count(&count)
	if count == 2 {
		return "database"
	}
	return "none"
}
