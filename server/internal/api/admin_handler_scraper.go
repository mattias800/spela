package api

import (
	"log/slog"
	"net/http"
	"os"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/scraper"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// TriggerScrape starts a metadata scraping operation (admin only).
// Only one scrape can run at a time; concurrent requests are rejected.
// Pass ?mode=all to re-scrape all games, ?mode=fallback to re-scrape
// games that only have LibRetro metadata. Default scrapes unscraped games only.
// Pass ?console=<abbreviation> to scrape only games for a specific console.
// Legacy ?force=true is equivalent to ?mode=all.
func (h *AdminHandler) TriggerScrape(c *gin.Context) {
	h.tryConfigureIGDB()
	h.tryConfigureSteamGridDB()

	mode := c.Query("mode")
	if mode == "" && c.Query("force") == "true" {
		mode = "all"
	}

	// Resolve optional console filter
	var consoleID uint
	if abbr := c.Query("console"); abbr != "" {
		var console db.Console
		if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", abbr).First(&console).Error; err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "unknown console"})
			return
		}
		consoleID = console.ID
	}

	ctx, ok := h.Scraper.TryStartScrape()
	if !ok {
		c.JSON(http.StatusConflict, gin.H{"error": "A scrape operation is already in progress"})
		return
	}

	// Count matching games before launching the goroutine so we can
	// return the total in the HTTP response for immediate user feedback.
	var total int64
	q := h.DB.Model(&db.Game{})
	if consoleID > 0 {
		q = q.Where("console_id = ?", consoleID)
	}
	switch mode {
	case "all":
		q.Count(&total)
	case "fallback":
		q.Where("scraper_id = 'libretro'").Count(&total)
	default:
		q.Where("scraper_id = '' OR scraper_id IS NULL").Count(&total)
	}

	h.Hub.Broadcast(ws.Event{Type: "scrape_started", Payload: nil})

	go func() {
		defer h.Scraper.FinishScrape()

		count, total, err := h.Scraper.ScrapeAll(ctx, mode, consoleID, func(p scraper.ScrapeProgress) {
			h.Scraper.SetScrapeProgress(&p)
			h.Hub.Broadcast(ws.Event{Type: "scrape_progress", Payload: p})
		})
		if err != nil {
			if ctx.Err() != nil {
				slog.Info("metadata scrape cancelled", "scraped", count, "total", total)
				h.Hub.Broadcast(ws.Event{Type: "scrape_cancelled", Payload: gin.H{"scraped": count, "total": total}})
				return
			}
			slog.Error("metadata scrape failed", "error", err)
			h.Hub.Broadcast(ws.Event{Type: "scrape_error", Payload: gin.H{"error": "metadata scrape failed"}})
			return
		}
		h.Hub.Broadcast(ws.Event{Type: "scrape_complete", Payload: gin.H{"scraped": count, "total": total}})

		// Refresh top-rated cache for all consoles with IGDB platform mappings.
		if h.Scraper.IGDBClient != nil && h.Scraper.IGDBClient.IsConfigured() {
			h.refreshTopRatedForAllConsoles()
		}
	}()

	adminID, _ := c.Get("userId")
	slog.Info("audit: admin triggered scrape", "admin_id", adminID, "mode", mode, "console_id", consoleID)
	c.JSON(http.StatusAccepted, gin.H{"message": "scrape started in background", "total": total})
}

// CancelScrape stops the running scrape operation (admin only).
func (h *AdminHandler) CancelScrape(c *gin.Context) {
	if !h.Scraper.CancelScrape() {
		c.JSON(http.StatusConflict, gin.H{"error": "no scrape operation is running"})
		return
	}

	// Broadcast immediately so the frontend stops spinning.
	// The goroutine will also broadcast when it detects the cancel, but that
	// can be delayed by seconds while the current game finishes scraping.
	// Receiving the event twice is harmless (frontend just sets phase="complete").
	h.Hub.Broadcast(ws.Event{Type: "scrape_cancelled", Payload: gin.H{}})

	adminID, _ := c.Get("userId")
	slog.Info("audit: admin cancelled scrape", "admin_id", adminID)
	c.JSON(http.StatusOK, gin.H{"message": "scrape cancellation requested"})
}

// ScrapeStatus returns the current scrape operation status.
func (h *AdminHandler) ScrapeStatus(c *gin.Context) {
	active, progress := h.Scraper.GetScrapeStatus()

	if !active || progress == nil {
		c.JSON(http.StatusOK, gin.H{"active": false})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"active":      true,
		"current":     progress.Current,
		"total":       progress.Total,
		"gameId":      progress.GameID,
		"gameName":    progress.GameName,
		"consoleName": progress.ConsoleName,
		"consoleAbbr": progress.ConsoleAbbr,
		"successes":   progress.Successes,
		"failures":    progress.Failures,
		"verified":    progress.Verified,
	})
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
	h.tryConfigureSteamGridDB()

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

// RefreshAchievements invalidates the achievement cache for a single game,
// forcing the next request to re-fetch from RetroAchievements.
func (h *AdminHandler) RefreshAchievements(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	// Delete achievement cache entries for this game (by game_id or ra_game_id)
	deleted := int64(0)
	if game.RAGameID > 0 {
		result := h.DB.Where("ra_game_id = ?", game.RAGameID).Delete(&db.GameAchievementCache{})
		deleted = result.RowsAffected
	}
	// Also try by game_id in case ra_game_id wasn't set
	result := h.DB.Where("game_id = ?", game.ID).Delete(&db.GameAchievementCache{})
	deleted += result.RowsAffected

	slog.Info("refreshed achievement cache", "game", game.Title, "deleted", deleted)
	c.JSON(http.StatusOK, gin.H{"message": "Achievement cache cleared", "game": game.Title})
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

// tryConfigureSteamGridDB loads the SteamGridDB API key from environment or database
// settings and configures the scraper's SteamGridDB client.
func (h *AdminHandler) tryConfigureSteamGridDB() {
	apiKey := steamGridDBAPIKey(h.DB)
	h.Scraper.ConfigureSteamGridDB(apiKey)
}

// steamGridDBAPIKey returns the SteamGridDB API key from the environment
// variable SPELA_STEAMGRIDDB_API_KEY, falling back to the database setting.
func steamGridDBAPIKey(database *gorm.DB) string {
	if key := os.Getenv("SPELA_STEAMGRIDDB_API_KEY"); key != "" {
		return key
	}
	var setting db.ServerSetting
	if err := database.Where("key = ?", "steamgriddb_api_key").First(&setting).Error; err != nil {
		return ""
	}
	return setting.Value
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

func SteamGridDBSource(database *gorm.DB) string {
	if os.Getenv("SPELA_STEAMGRIDDB_API_KEY") != "" {
		return "env"
	}

	var count int64
	database.Model(&db.ServerSetting{}).
		Where("key = ? AND value != ''", "steamgriddb_api_key").
		Count(&count)
	if count == 1 {
		return "database"
	}
	return "none"
}

// GetSteamGridDBStatus returns the current SteamGridDB configuration status.
func (h *AdminHandler) GetSteamGridDBStatus(c *gin.Context) {
	source := SteamGridDBSource(h.DB)

	if source == "none" {
		c.JSON(http.StatusOK, gin.H{
			"configured": false,
			"source":     "none",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"configured": true,
		"source":     source,
	})
}

// RASource returns "env" if RA API key is set via environment variables,
// "database" if set via admin settings, or "none" if not configured.
func RASource(database *gorm.DB) string {
	if os.Getenv("SPELA_RA_API_KEY") != "" {
		return "env"
	}
	var count int64
	database.Model(&db.ServerSetting{}).
		Where("key = ? AND value != ''", "ra_api_key").
		Count(&count)
	if count == 1 {
		return "database"
	}
	return "none"
}

// GetRAStatus returns the current RetroAchievements API configuration status.
func (h *AdminHandler) GetRAStatus(c *gin.Context) {
	source := RASource(h.DB)
	c.JSON(http.StatusOK, gin.H{
		"configured": source != "none",
		"source":     source,
	})
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

// refreshTopRatedForAllConsoles fetches IGDB top-rated games for every console
// that has an IGDB platform mapping and upserts them into the cache.
// Called after a scrape completes so the top lists are always fresh.
func (h *AdminHandler) refreshTopRatedForAllConsoles() {
	slog.Info("refreshing top-rated games for all consoles")

	var consoles []db.Console
	h.DB.Find(&consoles)

	consoleHandler := &ConsoleHandler{DB: h.DB, Scraper: h.Scraper}
	refreshed := 0

	for _, console := range consoles {
		abbr := console.Abbreviation
		platformID, ok := igdb.AbbreviationToIGDBPlatform[abbr]
		if !ok {
			continue
		}

		topGames, err := h.Scraper.IGDBClient.GetTopGames(platformID, 100)
		if err != nil {
			slog.Warn("failed to fetch top-rated games", "console", abbr, "error", err)
			continue
		}

		ranked := bayesianRank(topGames, 25)
		consoleHandler.upsertTopRatedGames(console.ID, ranked)
		refreshed++
	}

	slog.Info("top-rated refresh complete", "consoles", refreshed)
}
