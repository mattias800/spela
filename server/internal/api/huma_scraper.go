package api

import (
	"context"
	"log/slog"
	"net/http"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scanner"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// --- Input / output types ---

// TriggerScrapeInput is the input for POST /api/admin/scrape.
type TriggerScrapeInput struct {
	Mode     string `query:"mode" doc:"Scrape mode: 'new' (default), 'all', 'fallback', 'ra'."`
	Force    string `query:"force" doc:"Legacy: 'true' is equivalent to mode=all."`
	Console  string `query:"console" doc:"Optional console abbreviation filter."`
	Source   string `query:"source" doc:"Filter by scrape source (igdb, libretro, steamgriddb)."`
	Status   string `query:"status" doc:"Filter by scrape status (matched, not_found, error, not_attempted)."`
	Conflict string `query:"conflict" doc:"Conflict resolution: 'reject' (default), 'replace', 'merge'."`
}

// ScrapeStartedResponse is the generic scrape-started body.
type ScrapeStartedResponse struct {
	JobID   uint   `json:"jobId"`
	Total   int    `json:"total"`
	Added   int    `json:"added"`
	Message string `json:"message"`
	// TotalItems mirrors the merge-conflict response shape.
	TotalItems int `json:"totalItems"`
}

// TriggerScrapeOutput wraps the scrape-started body.
type TriggerScrapeOutput struct {
	Body ScrapeStartedResponse
}

// CancelScrapeInput is the input for DELETE /api/admin/scrape.
type CancelScrapeInput struct{}

// CancelScrapeOutput wraps the cancel success message.
type CancelScrapeOutput struct {
	Body MessageResponse
}

// ScrapeStatusInput is the input for GET /api/admin/scrape/status.
type ScrapeStatusInput struct{}

// ScrapeStatusResponse is the wire format for scrape status.
type ScrapeStatusResponse struct {
	Active    bool       `json:"active"`
	JobID     uint       `json:"jobId"`
	Current   int        `json:"current"`
	Total     int        `json:"total"`
	Successes int        `json:"successes"`
	Failures  int        `json:"failures"`
	Verified  int        `json:"verified"`
	Mode      string     `json:"mode"`
	StartedAt *time.Time `json:"startedAt"`
}

// ScrapeStatusOutput wraps the scrape status body.
type ScrapeStatusOutput struct {
	Body ScrapeStatusResponse
}

// ScrapeStatusCountsInput is the input for GET /api/admin/scrape/counts.
type ScrapeStatusCountsInput struct{}

// ScrapeStatusCountsResponse is the wire format for scrape counts.
type ScrapeStatusCountsResponse struct {
	Sources []ScraperSourceResultResponse `json:"sources"`
}

// ScrapeStatusCountsOutput wraps the scrape counts body.
type ScrapeStatusCountsOutput struct {
	Body ScrapeStatusCountsResponse
}

// ScrapeGameInput is the input for POST /api/admin/games/{id}/scrape.
type ScrapeGameInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
}

// ScrapeGameResponse is the wire format for scrape-game responses.
type ScrapeGameResponse struct {
	Status string `json:"status"`
	GameID uint   `json:"gameId"`
}

// ScrapeGameOutput wraps the scrape-game response (202 Accepted).
type ScrapeGameOutput struct {
	Body ScrapeGameResponse
}

// RefreshAchievementsInput is the input for POST /api/admin/games/{id}/achievements/refresh.
type RefreshAchievementsInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
}

// RefreshAchievementsResponse is the wire format for refresh responses.
type RefreshAchievementsResponse struct {
	Message string `json:"message"`
	Game    string `json:"game"`
}

// RefreshAchievementsOutput wraps the refresh response.
type RefreshAchievementsOutput struct {
	Body RefreshAchievementsResponse
}

// GetSteamGridDBStatusInput is the input for GET /api/admin/steamgriddb/status.
type GetSteamGridDBStatusInput struct{}

// SteamGridDBStatusResponse is the wire format for the SteamGridDB status response.
type SteamGridDBStatusResponse struct {
	Configured bool   `json:"configured"`
	Source     string `json:"source"`
}

// GetSteamGridDBStatusOutput wraps the status response.
type GetSteamGridDBStatusOutput struct {
	Body SteamGridDBStatusResponse
}

// GetAdminRAStatusInput is the input for GET /api/admin/ra/status.
type GetAdminRAStatusInput struct{}

// AdminRAStatusResponse is the wire format for the admin RA status response.
type AdminRAStatusResponse struct {
	Configured bool   `json:"configured"`
	Source     string `json:"source"`
}

// GetAdminRAStatusOutput wraps the status response.
type GetAdminRAStatusOutput struct {
	Body AdminRAStatusResponse
}

// RegisterScraperAdminRoutes wires admin scraper endpoints into the huma API.
func RegisterScraperAdminRoutes(api huma.API, h *AdminHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	requireAdmin := RequireAdmin(api)
	mw := huma.Middlewares{requireAuth, rateLimit, requireAdmin}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "triggerScrape",
		Method:      http.MethodPost,
		Path:        "/api/admin/scrape",
		Summary:     "Trigger a scrape job",
		Description: "Admin-only. Enqueues matching games for scraping. Supports conflict resolution (reject/replace/merge) if another scrape is active.",
		Tags:        []string{"admin", "scraper"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaTriggerScrape)

	huma.Register(api, huma.Operation{
		OperationID: "cancelScrape",
		Method:      http.MethodDelete,
		Path:        "/api/admin/scrape",
		Summary:     "Cancel the active scrape",
		Description: "Admin-only. Cancels the currently running scrape job. Kicks off a post-cancel variant-regroup pass.",
		Tags:        []string{"admin", "scraper"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaCancelScrape)

	huma.Register(api, huma.Operation{
		OperationID: "getScrapeStatus",
		Method:      http.MethodGet,
		Path:        "/api/admin/scrape/status",
		Summary:     "Get scrape status",
		Description: "Admin-only. Returns the current scrape job state (active, counts, mode).",
		Tags:        []string{"admin", "scraper"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaScrapeStatus)

	huma.Register(api, huma.Operation{
		OperationID: "getScrapeStatusCounts",
		Method:      http.MethodGet,
		Path:        "/api/admin/scrape/counts",
		Summary:     "Get scrape result counts",
		Description: "Admin-only. Returns aggregate scrape result counts per source (matched, not found, error, not attempted).",
		Tags:        []string{"admin", "scraper"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaScrapeStatusCounts)

	huma.Register(api, huma.Operation{
		OperationID:   "scrapeGame",
		Method:        http.MethodPost,
		Path:          "/api/admin/games/{id}/scrape",
		Summary:       "Scrape a single game",
		Description:   "Admin-only. Enqueues a single game for scraping, attaching to the active job if any.",
		DefaultStatus: http.StatusAccepted,
		Tags:          []string{"admin", "scraper"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaScrapeGame)

	huma.Register(api, huma.Operation{
		OperationID: "refreshAchievements",
		Method:      http.MethodPost,
		Path:        "/api/admin/games/{id}/achievements/refresh",
		Summary:     "Refresh achievement cache",
		Description: "Admin-only. Deletes the achievement cache entries for a game so the next request re-fetches from RetroAchievements.",
		Tags:        []string{"admin", "retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaRefreshAchievements)

	huma.Register(api, huma.Operation{
		OperationID: "getSteamGridDBStatus",
		Method:      http.MethodGet,
		Path:        "/api/admin/steamgriddb/status",
		Summary:     "Get SteamGridDB status",
		Description: "Admin-only. Returns whether SteamGridDB is configured and the credential source (env, database, none).",
		Tags:        []string{"admin", "scraper"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetSteamGridDBStatus)

	huma.Register(api, huma.Operation{
		OperationID: "getAdminRAStatus",
		Method:      http.MethodGet,
		Path:        "/api/admin/ra/status",
		Summary:     "Get RetroAchievements API status",
		Description: "Admin-only. Returns whether the server-level RA API key is configured and the credential source (env, database, none).",
		Tags:        []string{"admin", "retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetAdminRAStatus)
}

// --- Handlers ---

// HumaTriggerScrape is the huma handler for POST /api/admin/scrape.
func (h *AdminHandler) HumaTriggerScrape(ctx context.Context, in *TriggerScrapeInput) (*TriggerScrapeOutput, error) {
	h.tryConfigureIGDB()
	h.tryConfigureSteamGridDB()

	mode := in.Mode
	if mode == "" {
		mode = "new"
	}
	if in.Force == "true" {
		mode = "all"
	}

	var consoleID uint
	if in.Console != "" {
		var console db.Console
		if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", in.Console).First(&console).Error; err != nil {
			return nil, huma.Error400BadRequest("unknown console")
		}
		consoleID = console.ID
	}

	source := in.Source
	status := in.Status
	conflict := in.Conflict
	if conflict == "" {
		conflict = "reject"
	}

	activeJob, err := h.Scraper.Queue.GetActiveScrapeJob()
	if err != nil {
		return nil, huma.Error500InternalServerError("checking active job")
	}

	if activeJob != nil {
		switch conflict {
		case "replace":
			if err := h.Scraper.Queue.CancelJob(activeJob.ID); err != nil {
				return nil, huma.Error500InternalServerError("cancelling active job")
			}
			h.Hub.Broadcast(ws.Event{Type: ws.EventScrapeCancelled, Payload: ws.ScrapeCancelledPayload{}})
		case "merge":
			gameIDs, err := h.collectGameIDs(mode, consoleID, source, status)
			if err != nil {
				return nil, huma.Error500InternalServerError("collecting games")
			}
			added, err := h.Scraper.Queue.MergeGames(activeJob.ID, gameIDs)
			if err != nil {
				return nil, huma.Error500InternalServerError("merging games")
			}
			return &TriggerScrapeOutput{Body: ScrapeStartedResponse{
				JobID:      activeJob.ID,
				Added:      added,
				TotalItems: activeJob.TotalItems + added,
			}}, nil
		default: // reject
			return nil, huma.Error409Conflict("scrape already in progress")
		}
	}

	gameIDs, err := h.collectGameIDs(mode, consoleID, source, status)
	if err != nil {
		slog.Error("failed to collect game IDs for scrape", "error", err)
		return nil, huma.Error500InternalServerError("collecting games")
	}

	if len(gameIDs) == 0 {
		return &TriggerScrapeOutput{Body: ScrapeStartedResponse{
			Total:   0,
			Message: "no games to scrape",
		}}, nil
	}

	consoleFilter := in.Console

	job, err := h.Scraper.Queue.CreateJob(mode, source, status, consoleFilter, len(gameIDs))
	if err != nil {
		slog.Error("failed to create scrape job", "error", err)
		return nil, huma.Error500InternalServerError("creating job")
	}

	if mode == "ra" {
		if err := h.Scraper.Queue.EnqueueGamesWithType(job.ID, gameIDs, 0, "ra_fetch"); err != nil {
			slog.Error("failed to enqueue RA fetch games", "error", err)
			return nil, huma.Error500InternalServerError("enqueuing games")
		}
	} else {
		if err := h.Scraper.Queue.EnqueueGames(job.ID, gameIDs, 0); err != nil {
			slog.Error("failed to enqueue games", "error", err)
			return nil, huma.Error500InternalServerError("enqueuing games")
		}
	}

	h.Hub.Broadcast(ws.Event{Type: ws.EventScrapeStarted, Payload: ws.ScrapeStartedPayload{
		JobID: job.ID,
		Total: len(gameIDs),
		Mode:  mode,
	}})

	adminID := UserIDFromContext(ctx)
	slog.Info("audit: admin triggered scrape", "admin_id", adminID, "mode", mode, "console_id", consoleID, "total", len(gameIDs))
	return &TriggerScrapeOutput{Body: ScrapeStartedResponse{
		JobID: job.ID,
		Total: len(gameIDs),
	}}, nil
}

// HumaCancelScrape is the huma handler for DELETE /api/admin/scrape.
func (h *AdminHandler) HumaCancelScrape(ctx context.Context, _ *CancelScrapeInput) (*CancelScrapeOutput, error) {
	job, err := h.Scraper.Queue.GetActiveScrapeJob()
	if err != nil {
		return nil, huma.Error500InternalServerError("checking active job")
	}
	if job == nil {
		return nil, huma.Error409Conflict("No scrape operation is running")
	}

	if err := h.Scraper.Queue.CancelJob(job.ID); err != nil {
		return nil, huma.Error500InternalServerError("cancelling job")
	}

	h.Hub.Broadcast(ws.Event{Type: ws.EventScrapeCancelled, Payload: ws.ScrapeCancelledPayload{
		JobID: job.ID,
	}})

	go func() {
		if err := scanner.RecomputeGroupKeys(h.DB, nil); err != nil {
			slog.Warn("group key recompute after cancel failed", "error", err)
		}
		if err := scanner.GroupAndElectPrimaries(h.DB); err != nil {
			slog.Warn("regrouping after cancel failed", "error", err)
		}
		if merged, err := scanner.MergeGroupsByIGDBID(h.DB); err != nil {
			slog.Warn("IGDB group merge after cancel failed", "error", err)
		} else if merged > 0 {
			slog.Info("IGDB group merge after cancel complete", "merged", merged)
		}
	}()

	adminID := UserIDFromContext(ctx)
	slog.Info("audit: admin cancelled scrape", "admin_id", adminID, "jobId", job.ID)
	return &CancelScrapeOutput{Body: MessageResponse{Message: "scrape cancellation requested"}}, nil
}

// HumaScrapeStatus is the huma handler for GET /api/admin/scrape/status.
func (h *AdminHandler) HumaScrapeStatus(_ context.Context, _ *ScrapeStatusInput) (*ScrapeStatusOutput, error) {
	job, err := h.Scraper.Queue.GetActiveScrapeJob()
	if err != nil {
		return nil, huma.Error500InternalServerError("checking active job")
	}
	if job == nil {
		return &ScrapeStatusOutput{Body: ScrapeStatusResponse{Active: false}}, nil
	}
	return &ScrapeStatusOutput{Body: ScrapeStatusResponse{
		Active:    true,
		JobID:     job.ID,
		Current:   job.CompletedItems + job.FailedItems,
		Total:     job.TotalItems,
		Successes: job.CompletedItems,
		Failures:  job.FailedItems,
		Verified:  job.VerifiedItems,
		Mode:      job.Mode,
		StartedAt: job.StartedAt,
	}}, nil
}

// HumaScrapeStatusCounts is the huma handler for GET /api/admin/scrape/counts.
func (h *AdminHandler) HumaScrapeStatusCounts(_ context.Context, _ *ScrapeStatusCountsInput) (*ScrapeStatusCountsOutput, error) {
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
		return nil, huma.Error500InternalServerError("failed to query scrape results")
	}

	var totalGames int64
	if err := h.DB.Model(&db.Game{}).Count(&totalGames).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to count games")
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
		return nil, huma.Error500InternalServerError("failed to query eligible counts")
	}

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

	sources := []string{"igdb", "libretro", "steamgriddb"}
	results := make([]ScraperSourceResultResponse, 0, len(sources))

	for _, src := range sources {
		sc := counts[src]
		el := eligible[src]

		var attempted int64
		h.DB.Raw("SELECT COUNT(DISTINCT game_id) FROM game_scrape_results WHERE source = ?", src).Scan(&attempted)

		results = append(results, ScraperSourceResultResponse{
			Source:           src,
			Matched:          sc["matched"],
			NotFound:         sc["not_found"],
			NotFoundEligible: el["not_found"],
			Error:            sc["error"],
			ErrorEligible:    el["error"],
			NotAttempted:     totalGames - attempted,
		})
	}

	return &ScrapeStatusCountsOutput{Body: ScrapeStatusCountsResponse{Sources: results}}, nil
}

// HumaScrapeGame is the huma handler for POST /api/admin/games/{id}/scrape.
func (h *AdminHandler) HumaScrapeGame(_ context.Context, in *ScrapeGameInput) (*ScrapeGameOutput, error) {
	var game db.Game
	if err := h.DB.First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	h.tryConfigureIGDB()
	h.tryConfigureSteamGridDB()

	if queued, _ := h.Scraper.Queue.IsGameQueued(game.ID); queued {
		return &ScrapeGameOutput{Body: ScrapeGameResponse{Status: "already_queued", GameID: game.ID}}, nil
	}

	activeJob, _ := h.Scraper.Queue.GetActiveScrapeJob()
	var jobID *uint
	if activeJob != nil {
		jobID = &activeJob.ID
		h.DB.Model(&db.ScrapeJob{}).Where("id = ?", activeJob.ID).
			Update("total_items", gorm.Expr("total_items + 1"))
	}

	if err := h.Scraper.Queue.EnqueueGame(game.ID, jobID, 100); err != nil {
		return nil, huma.Error500InternalServerError("failed to enqueue game")
	}

	return &ScrapeGameOutput{Body: ScrapeGameResponse{Status: "queued", GameID: game.ID}}, nil
}

// HumaRefreshAchievements is the huma handler for POST /api/admin/games/{id}/achievements/refresh.
func (h *AdminHandler) HumaRefreshAchievements(_ context.Context, in *RefreshAchievementsInput) (*RefreshAchievementsOutput, error) {
	var game db.Game
	if err := h.DB.First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	deleted := int64(0)
	if game.RAGameID > 0 {
		result := h.DB.Where("ra_game_id = ?", game.RAGameID).Delete(&db.GameAchievementCache{})
		deleted = result.RowsAffected
	}
	result := h.DB.Where("game_id = ?", game.ID).Delete(&db.GameAchievementCache{})
	deleted += result.RowsAffected

	slog.Info("refreshed achievement cache", "game", game.Title, "deleted", deleted)
	return &RefreshAchievementsOutput{Body: RefreshAchievementsResponse{
		Message: "Achievement cache cleared",
		Game:    game.Title,
	}}, nil
}

// HumaGetSteamGridDBStatus is the huma handler for GET /api/admin/steamgriddb/status.
func (h *AdminHandler) HumaGetSteamGridDBStatus(_ context.Context, _ *GetSteamGridDBStatusInput) (*GetSteamGridDBStatusOutput, error) {
	source := SteamGridDBSource(h.DB)
	return &GetSteamGridDBStatusOutput{Body: SteamGridDBStatusResponse{
		Configured: source != "none",
		Source:     source,
	}}, nil
}

// HumaGetAdminRAStatus is the huma handler for GET /api/admin/ra/status.
func (h *AdminHandler) HumaGetAdminRAStatus(_ context.Context, _ *GetAdminRAStatusInput) (*GetAdminRAStatusOutput, error) {
	source := RASource(h.DB)
	return &GetAdminRAStatusOutput{Body: AdminRAStatusResponse{
		Configured: source != "none",
		Source:     source,
	}}, nil
}
