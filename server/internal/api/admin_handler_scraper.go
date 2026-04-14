package api

import (
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/scanner"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// TriggerScrape creates a scrape job and enqueues matching games (admin only).
// Pass ?mode=all to re-scrape all games, ?mode=fallback to re-scrape
// games that only have LibRetro metadata. Default scrapes unscraped games only.
// Pass ?console=<abbreviation> to scrape only games for a specific console.
// Pass ?conflict=replace to cancel the current scrape and start a new one,
// or ?conflict=merge to add games to the current scrape.
// Legacy ?force=true is equivalent to ?mode=all.
func (h *AdminHandler) TriggerScrape(c *gin.Context) {
	h.tryConfigureIGDB()
	h.tryConfigureSteamGridDB()

	mode := c.DefaultQuery("mode", "new")
	if c.Query("force") == "true" {
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

	source := c.Query("source")
	status := c.Query("status")
	conflict := c.DefaultQuery("conflict", "reject")

	// Check for active job
	activeJob, err := h.Scraper.Queue.GetActiveJob()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "checking active job"})
		return
	}

	if activeJob != nil {
		switch conflict {
		case "replace":
			if err := h.Scraper.Queue.CancelJob(activeJob.ID); err != nil {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "cancelling active job"})
				return
			}
			h.Hub.Broadcast(ws.Event{Type: "scrape_cancelled", Payload: gin.H{}})
		case "merge":
			gameIDs, err := h.collectGameIDs(mode, consoleID, source, status)
			if err != nil {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "collecting games"})
				return
			}
			added, err := h.Scraper.Queue.MergeGames(activeJob.ID, gameIDs)
			if err != nil {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "merging games"})
				return
			}
			c.JSON(http.StatusOK, gin.H{
				"jobId":      activeJob.ID,
				"added":      added,
				"totalItems": activeJob.TotalItems + added,
			})
			return
		default: // reject
			c.JSON(http.StatusConflict, gin.H{
				"error":      "scrape already in progress",
				"jobId":      activeJob.ID,
				"totalItems": activeJob.TotalItems,
				"completed":  activeJob.CompletedItems,
				"failed":     activeJob.FailedItems,
			})
			return
		}
	}

	// Collect game IDs matching the query
	gameIDs, err := h.collectGameIDs(mode, consoleID, source, status)
	if err != nil {
		slog.Error("failed to collect game IDs for scrape", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "collecting games"})
		return
	}

	if len(gameIDs) == 0 {
		c.JSON(http.StatusOK, gin.H{"total": 0, "message": "no games to scrape"})
		return
	}

	consoleFilter := c.Query("console")

	// Create job and enqueue
	job, err := h.Scraper.Queue.CreateJob(mode, source, status, consoleFilter, len(gameIDs))
	if err != nil {
		slog.Error("failed to create scrape job", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "creating job"})
		return
	}

	if err := h.Scraper.Queue.EnqueueGames(job.ID, gameIDs, 0); err != nil {
		slog.Error("failed to enqueue games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "enqueuing games"})
		return
	}

	h.Hub.Broadcast(ws.Event{Type: "scrape_started", Payload: gin.H{
		"jobId": job.ID,
		"total": len(gameIDs),
		"mode":  mode,
	}})

	adminID, _ := c.Get("userId")
	slog.Info("audit: admin triggered scrape", "admin_id", adminID, "mode", mode, "console_id", consoleID, "total", len(gameIDs))
	c.JSON(http.StatusOK, gin.H{"jobId": job.ID, "total": len(gameIDs)})
}

// collectGameIDs builds the game query based on filters and returns matching IDs.
func (h *AdminHandler) collectGameIDs(mode string, consoleID uint, source, status string) ([]uint, error) {
	q := h.DB.Model(&db.Game{})
	if consoleID > 0 {
		q = q.Where("console_id = ?", consoleID)
	}

	if source != "" && status != "" {
		cooldownCutoff := time.Now().AddDate(0, 0, -7)
		if status == "not_attempted" {
			q = q.Where("id NOT IN (SELECT game_id FROM game_scrape_results WHERE source = ?)", source)
		} else {
			subQ := "id IN (SELECT game_id FROM game_scrape_results WHERE source = ? AND status = ?"
			args := []interface{}{source, status}
			if status == "not_found" || status == "error" {
				subQ += " AND (last_attempt_at IS NULL OR last_attempt_at < ?)"
				args = append(args, cooldownCutoff)
			}
			subQ += ")"
			q = q.Where(subQ, args...)
		}
	} else {
		switch mode {
		case "all":
			// no filter
		case "fallback":
			q = q.Where("scraper_id = 'libretro'")
		default:
			q = q.Where("scraper_id = '' OR scraper_id IS NULL")
		}
	}

	var gameIDs []uint
	if err := q.Pluck("id", &gameIDs).Error; err != nil {
		return nil, fmt.Errorf("collecting game IDs: %w", err)
	}
	return gameIDs, nil
}

// CancelScrape stops the running scrape operation (admin only).
func (h *AdminHandler) CancelScrape(c *gin.Context) {
	job, err := h.Scraper.Queue.GetActiveJob()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "checking active job"})
		return
	}
	if job == nil {
		c.JSON(http.StatusConflict, gin.H{"error": "no scrape operation is running"})
		return
	}

	if err := h.Scraper.Queue.CancelJob(job.ID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "cancelling job"})
		return
	}

	h.Hub.Broadcast(ws.Event{Type: "scrape_cancelled", Payload: gin.H{
		"jobId": job.ID,
	}})

	// Rebuild variant groups after cancellation
	go func() {
		if err := scanner.GroupAndElectPrimaries(h.DB); err != nil {
			slog.Warn("regrouping after cancel failed", "error", err)
		}
		if merged, err := scanner.MergeGroupsByIGDBID(h.DB); err != nil {
			slog.Warn("IGDB group merge after cancel failed", "error", err)
		} else if merged > 0 {
			slog.Info("IGDB group merge after cancel complete", "merged", merged)
		}
	}()

	adminID, _ := c.Get("userId")
	slog.Info("audit: admin cancelled scrape", "admin_id", adminID, "jobId", job.ID)
	c.JSON(http.StatusOK, gin.H{"message": "scrape cancellation requested"})
}

// ScrapeStatus returns the current scrape operation status.
func (h *AdminHandler) ScrapeStatus(c *gin.Context) {
	job, err := h.Scraper.Queue.GetActiveJob()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "checking active job"})
		return
	}

	if job == nil {
		c.JSON(http.StatusOK, gin.H{"active": false})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"active":    true,
		"jobId":     job.ID,
		"current":   job.CompletedItems + job.FailedItems,
		"total":     job.TotalItems,
		"successes": job.CompletedItems,
		"failures":  job.FailedItems,
		"verified":  job.VerifiedItems,
		"mode":      job.Mode,
		"startedAt": job.StartedAt,
	})
}

// ScrapeStatusCounts returns aggregate scrape result counts per source.
// For each source (igdb, libretro, steamgriddb) it reports:
//   - matched: games successfully scraped
//   - notFound: games where the source returned no result
//   - notFoundEligible: notFound games eligible for retry (last attempt > 7 days ago or never)
//   - error: games where the scrape errored
//   - errorEligible: error games eligible for retry
//   - notAttempted: games with no result row for this source at all
func (h *AdminHandler) ScrapeStatusCounts(c *gin.Context) {
	type sourceRow struct {
		Source string
		Status string
		Count  int64
	}

	var rows []sourceRow
	if err := h.DB.Model(&db.GameScrapeResult{}).
		Select("source, status, COUNT(*) as count").
		Group("source, status").
		Scan(&rows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to query scrape results"})
		return
	}

	var totalGames int64
	if err := h.DB.Model(&db.Game{}).Count(&totalGames).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to count games"})
		return
	}

	cooldownCutoff := time.Now().AddDate(0, 0, -7)

	type eligibleRow struct {
		Source string
		Status string
		Count  int64
	}
	var eligibleRows []eligibleRow
	if err := h.DB.Model(&db.GameScrapeResult{}).
		Select("source, status, COUNT(*) as count").
		Where("status IN ? AND (last_attempt_at IS NULL OR last_attempt_at < ?)", []string{"not_found", "error"}, cooldownCutoff).
		Group("source, status").
		Scan(&eligibleRows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to query eligible counts"})
		return
	}

	// Build lookup maps
	counts := make(map[string]map[string]int64)
	for _, r := range rows {
		if counts[r.Source] == nil {
			counts[r.Source] = make(map[string]int64)
		}
		counts[r.Source][r.Status] = r.Count
	}

	eligible := make(map[string]map[string]int64)
	for _, r := range eligibleRows {
		if eligible[r.Source] == nil {
			eligible[r.Source] = make(map[string]int64)
		}
		eligible[r.Source][r.Status] = r.Count
	}

	type sourceResult struct {
		Source           string `json:"source"`
		Matched          int64  `json:"matched"`
		NotFound         int64  `json:"notFound"`
		NotFoundEligible int64  `json:"notFoundEligible"`
		Error            int64  `json:"error"`
		ErrorEligible    int64  `json:"errorEligible"`
		NotAttempted     int64  `json:"notAttempted"`
	}

	sources := []string{"igdb", "libretro", "steamgriddb"}
	results := make([]sourceResult, 0, len(sources))

	for _, src := range sources {
		sc := counts[src]
		el := eligible[src]

		var attempted int64
		h.DB.Raw("SELECT COUNT(DISTINCT game_id) FROM game_scrape_results WHERE source = ?", src).Scan(&attempted)

		results = append(results, sourceResult{
			Source:           src,
			Matched:          sc["matched"],
			NotFound:         sc["not_found"],
			NotFoundEligible: el["not_found"],
			Error:            sc["error"],
			ErrorEligible:    el["error"],
			NotAttempted:     totalGames - attempted,
		})
	}

	c.JSON(http.StatusOK, gin.H{"sources": results})
}

// ScrapeGame scrapes metadata for a single game (admin only).
func (h *AdminHandler) ScrapeGame(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	h.tryConfigureIGDB()
	h.tryConfigureSteamGridDB()

	// Skip if already queued
	if queued, _ := h.Scraper.Queue.IsGameQueued(game.ID); queued {
		c.JSON(http.StatusAccepted, gin.H{"status": "already_queued", "gameId": game.ID})
		return
	}

	// Attach to active job if one exists
	activeJob, _ := h.Scraper.Queue.GetActiveJob()
	var jobID *uint
	if activeJob != nil {
		jobID = &activeJob.ID
		h.DB.Model(&db.ScrapeJob{}).Where("id = ?", activeJob.ID).
			Update("total_items", gorm.Expr("total_items + 1"))
	}

	if err := h.Scraper.Queue.EnqueueGame(game.ID, jobID, 100); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to enqueue game"})
		return
	}

	c.JSON(http.StatusAccepted, gin.H{
		"status": "queued",
		"gameId": game.ID,
	})
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
		platformIDs, ok := igdb.AbbreviationToIGDBPlatform[abbr]
		if !ok {
			continue
		}

		topGames, err := h.Scraper.IGDBClient.GetTopGames(platformIDs, 100)
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
