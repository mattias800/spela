package api

import (
	"log/slog"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

// --- Phase 14: Wild Features — Wizard, Badges, Completionist Map ---

// WizardStep represents a single step in the decision wizard.
type WizardStep struct {
	Step    int              `json:"step"`
	Title   string           `json:"title"`
	Type    string           `json:"type"` // "mood", "era", "vibe"
	Options []WizardOption   `json:"options"`
}

// WizardOption is a selectable option in a wizard step.
type WizardOption struct {
	ID          string `json:"id"`
	Label       string `json:"label"`
	Description string `json:"description,omitempty"`
	ImageURL    string `json:"imageUrl,omitempty"`
}

// WizardResponse is the API response for the wizard endpoint.
type WizardResponse struct {
	Steps []WizardStep `json:"steps"`
}

// WizardResultsResponse is the response for wizard recommendations.
type WizardResultsResponse struct {
	Games []GameResponse `json:"games"`
	Title string         `json:"title"`
}

// GetWizardSteps returns the decision wizard configuration (the steps and options).
// GET /api/explore/wizard
func (h *ExploreHandler) GetWizardSteps(c *gin.Context) {
	steps := []WizardStep{
		{
			Step:  1,
			Title: "What are you in the mood for?",
			Type:  "mood",
			Options: []WizardOption{
				{ID: "action", Label: "Action & Excitement", Description: "Fast-paced thrills"},
				{ID: "chill", Label: "Chill & Relaxing", Description: "Laid-back vibes"},
				{ID: "story", Label: "Deep Story", Description: "Rich narrative experiences"},
				{ID: "challenge", Label: "A Real Challenge", Description: "Test your skills"},
				{ID: "fun", Label: "Pure Fun", Description: "Simple pick-up-and-play"},
			},
		},
		{
			Step:  2,
			Title: "Pick an era",
			Type:  "era",
			Options: []WizardOption{
				{ID: "80s", Label: "The 80s", Description: "Birth of console gaming"},
				{ID: "early90s", Label: "Early 90s", Description: "16-bit golden age"},
				{ID: "late90s", Label: "Late 90s", Description: "3D revolution begins"},
				{ID: "2000s", Label: "The 2000s", Description: "Handheld renaissance"},
				{ID: "any", Label: "Any Era", Description: "Surprise me"},
			},
		},
		{
			Step:  3,
			Title: "Refine your vibe",
			Type:  "vibe",
			Options: []WizardOption{
				{ID: "solo", Label: "Solo Adventure", Description: "Just me and the game"},
				{ID: "multiplayer", Label: "Multiplayer", Description: "Games with friends"},
				{ID: "short", Label: "Quick Session", Description: "Short and sweet"},
				{ID: "long", Label: "Deep Dive", Description: "Hours of content"},
				{ID: "any", Label: "Anything Goes", Description: "No preference"},
			},
		},
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, WizardResponse{Steps: steps})
}

// GetWizardResults returns 5 game recommendations based on wizard choices.
// GET /api/explore/wizard/results?mood=action&era=90s&vibe=solo
func (h *ExploreHandler) GetWizardResults(c *gin.Context) {
	userID := getUserID(c)
	mood := c.Query("mood")
	era := c.Query("era")
	vibe := c.Query("vibe")

	query := h.DB.Preload("Console").Where("cover_url != '' AND is_primary = true")

	// Mood -> genre/theme mapping
	switch mood {
	case "action":
		query = query.Where("genre IN ?", []string{"Action", "Shooter", "Fighting", "Beat 'em up"})
	case "chill":
		query = query.Where("genre IN ?", []string{"Puzzle", "Simulation", "Sports"})
	case "story":
		query = query.Where("genre IN ?", []string{"RPG", "Adventure"})
	case "challenge":
		query = query.Where("genre IN ?", []string{"Action", "Platformer", "Shooter"}).
			Where("rating >= 75")
	case "fun":
		query = query.Where("genre IN ?", []string{"Platformer", "Arcade", "Racing", "Puzzle"})
	}

	// Era -> year range mapping
	switch era {
	case "80s":
		query = query.Where("release_date >= '1980' AND release_date < '1990'")
	case "early90s":
		query = query.Where("release_date >= '1990' AND release_date < '1995'")
	case "late90s":
		query = query.Where("release_date >= '1995' AND release_date < '2000'")
	case "2000s":
		query = query.Where("release_date >= '2000' AND release_date < '2010'")
	// "any" = no filter
	}

	// Vibe -> refinement
	switch vibe {
	case "solo":
		query = query.Where("(players IS NULL OR players = 0 OR players = 1)")
	case "multiplayer":
		query = query.Where("players > 1")
	case "short":
		// Prefer games with shorter average sessions
		query = query.Where("genre NOT IN ?", []string{"RPG"})
	case "long":
		query = query.Where("genre IN ?", []string{"RPG", "Adventure", "Strategy"})
	// "any" = no filter
	}

	var games []db.Game
	if err := query.Where("rating > 0").
		Order("rating DESC, RANDOM()").
		Limit(5).
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch wizard results", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch wizard results"})
		return
	}

	// If not enough results, relax and try without rating filter
	if len(games) < 3 {
		relaxed := h.DB.Preload("Console").Where("cover_url != '' AND is_primary = true")
		switch mood {
		case "action":
			relaxed = relaxed.Where("genre IN ?", []string{"Action", "Shooter", "Fighting", "Beat 'em up", "Platformer"})
		case "story":
			relaxed = relaxed.Where("genre IN ?", []string{"RPG", "Adventure"})
		default:
			// No genre filter for relaxed search
		}
		relaxed.Order("RANDOM()").Limit(5).Find(&games)
	}

	title := "Your Perfect Picks"
	if mood != "" {
		titles := map[string]string{
			"action":    "Action-Packed Picks",
			"chill":     "Chill & Relaxing",
			"story":     "Story-Driven Adventures",
			"challenge": "Challenge Accepted",
			"fun":       "Pure Fun Picks",
		}
		if t, ok := titles[mood]; ok {
			title = t
		}
	}

	c.Header("Cache-Control", "no-store")
	c.JSON(http.StatusOK, WizardResultsResponse{
		Games: ToGameResponses(games, h.DB, userID),
		Title: title,
	})
}

// ExplorerBadge represents a discovery/exploration badge.
type ExplorerBadge struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
	Icon        string `json:"icon"`
	Earned      bool   `json:"earned"`
	Progress    int    `json:"progress"`
	Target      int    `json:"target"`
}

// ExplorerBadgesResponse is the API response for explorer badges.
type ExplorerBadgesResponse struct {
	Badges []ExplorerBadge `json:"badges"`
}

// GetExplorerBadges returns the user's exploration breadth badges.
// GET /api/user/explorer-badges
func (h *ExploreHandler) GetExplorerBadges(c *gin.Context) {
	userID := getUserID(c)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "authentication required"})
		return
	}

	// Count distinct consoles played
	var consolesPlayed int64
	if err := h.DB.Raw(`
		SELECT COUNT(DISTINCT g.console_id)
		FROM play_histories ph
		JOIN games g ON g.id = ph.game_id
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL AND g.deleted_at IS NULL
	`, userID).Scan(&consolesPlayed).Error; err != nil {
		slog.Error("failed to query consoles played", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute badges"})
		return
	}

	// Count total consoles with games
	var totalConsoles int64
	if err := h.DB.Raw(`SELECT COUNT(DISTINCT console_id) FROM games WHERE deleted_at IS NULL`).Scan(&totalConsoles).Error; err != nil {
		slog.Error("failed to query total consoles", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute badges"})
		return
	}

	// Count distinct genres played
	var genresPlayed int64
	if err := h.DB.Raw(`
		SELECT COUNT(DISTINCT g.genre)
		FROM play_histories ph
		JOIN games g ON g.id = ph.game_id
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL AND g.deleted_at IS NULL AND g.genre != ''
	`, userID).Scan(&genresPlayed).Error; err != nil {
		slog.Error("failed to query genres played", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute badges"})
		return
	}

	// Count distinct decades played
	var decadesPlayed int64
	if err := h.DB.Raw(`
		SELECT COUNT(DISTINCT CAST(SUBSTR(g.release_date, 1, 3) AS TEXT))
		FROM play_histories ph
		JOIN games g ON g.id = ph.game_id
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL AND g.deleted_at IS NULL
		AND g.release_date != '' AND LENGTH(g.release_date) >= 4
	`, userID).Scan(&decadesPlayed).Error; err != nil {
		slog.Error("failed to query decades played", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute badges"})
		return
	}

	// Count total games played
	var gamesPlayed int64
	if err := h.DB.Raw(`
		SELECT COUNT(DISTINCT ph.game_id)
		FROM play_histories ph
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL
	`, userID).Scan(&gamesPlayed).Error; err != nil {
		slog.Error("failed to query games played", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute badges"})
		return
	}

	// Count total play time (seconds)
	var totalPlayTime int64
	if err := h.DB.Raw(`
		SELECT COALESCE(SUM(ph.play_time), 0)
		FROM play_histories ph
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL
	`, userID).Scan(&totalPlayTime).Error; err != nil {
		slog.Error("failed to query total play time", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute badges"})
		return
	}
	totalHours := totalPlayTime / 3600

	badges := []ExplorerBadge{
		{
			ID:          "console-explorer",
			Name:        "Console Explorer",
			Description: "Play games on every console",
			Icon:        "gamepad",
			Earned:      consolesPlayed >= totalConsoles && totalConsoles > 0,
			Progress:    int(consolesPlayed),
			Target:      int(totalConsoles),
		},
		{
			ID:          "genre-master",
			Name:        "Genre Master",
			Description: "Play games from 10 different genres",
			Icon:        "layers",
			Earned:      genresPlayed >= 10,
			Progress:    int(genresPlayed),
			Target:      10,
		},
		{
			ID:          "time-traveler",
			Name:        "Time Traveler",
			Description: "Play games from 5 different decades",
			Icon:        "clock",
			Earned:      decadesPlayed >= 5,
			Progress:    int(decadesPlayed),
			Target:      5,
		},
		{
			ID:          "centurion",
			Name:        "Centurion",
			Description: "Play 100 different games",
			Icon:        "trophy",
			Earned:      gamesPlayed >= 100,
			Progress:    int(gamesPlayed),
			Target:      100,
		},
		{
			ID:          "dedicated-gamer",
			Name:        "Dedicated Gamer",
			Description: "Accumulate 50 hours of play time",
			Icon:        "timer",
			Earned:      totalHours >= 50,
			Progress:    int(totalHours),
			Target:      50,
		},
		{
			ID:          "marathon-runner",
			Name:        "Marathon Runner",
			Description: "Accumulate 200 hours of play time",
			Icon:        "flame",
			Earned:      totalHours >= 200,
			Progress:    int(totalHours),
			Target:      200,
		},
	}

	c.Header("Cache-Control", "private, max-age=120")
	c.JSON(http.StatusOK, ExplorerBadgesResponse{Badges: badges})
}

// CompletionistConsole represents per-console completion stats.
type CompletionistConsole struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	TotalGames  int    `json:"totalGames"`
	PlayedGames int    `json:"playedGames"`
	Percentage  int    `json:"percentage"`
}

// CompletionistMapResponse is the API response for the completionist map.
type CompletionistMapResponse struct {
	Consoles     []CompletionistConsole `json:"consoles"`
	TotalGames   int                    `json:"totalGames"`
	TotalPlayed  int                    `json:"totalPlayed"`
	OverallPct   int                    `json:"overallPct"`
}

// GetCompletionistMap returns per-console completion percentages.
// GET /api/user/completionist-map
func (h *ExploreHandler) GetCompletionistMap(c *gin.Context) {
	userID := getUserID(c)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "authentication required"})
		return
	}

	// Get per-console game counts
	type consoleRow struct {
		ConsoleID    uint
		ConsoleName  string
		Abbreviation string
		TotalGames   int
	}
	var consoleRows []consoleRow
	if err := h.DB.Raw(`
		SELECT c.id AS console_id, c.name AS console_name, c.abbreviation, COUNT(g.id) AS total_games
		FROM consoles c
		JOIN games g ON g.console_id = c.id AND g.deleted_at IS NULL AND g.is_primary = true
		WHERE c.deleted_at IS NULL
		GROUP BY c.id
		HAVING COUNT(g.id) > 0
		ORDER BY c.name
	`).Scan(&consoleRows).Error; err != nil {
		slog.Error("failed to query console counts", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute completionist map"})
		return
	}

	// Get per-console played counts for this user
	type playedRow struct {
		ConsoleID   uint
		PlayedGames int
	}
	var playedRows []playedRow
	if err := h.DB.Raw(`
		SELECT g.console_id, COUNT(DISTINCT g.id) AS played_games
		FROM play_histories ph
		JOIN games g ON g.id = ph.game_id AND g.deleted_at IS NULL
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL
		GROUP BY g.console_id
	`, userID).Scan(&playedRows).Error; err != nil {
		slog.Error("failed to query played counts", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute completionist map"})
		return
	}

	playedMap := make(map[uint]int)
	for _, pr := range playedRows {
		playedMap[pr.ConsoleID] = pr.PlayedGames
	}

	var consoles []CompletionistConsole
	totalGames := 0
	totalPlayed := 0

	for _, cr := range consoleRows {
		played := playedMap[cr.ConsoleID]
		pct := 0
		if cr.TotalGames > 0 {
			pct = played * 100 / cr.TotalGames
		}
		consoles = append(consoles, CompletionistConsole{
			ID:          cr.Abbreviation,
			Name:        cr.ConsoleName,
			TotalGames:  cr.TotalGames,
			PlayedGames: played,
			Percentage:  pct,
		})
		totalGames += cr.TotalGames
		totalPlayed += played
	}

	overallPct := 0
	if totalGames > 0 {
		overallPct = totalPlayed * 100 / totalGames
	}

	c.Header("Cache-Control", "private, max-age=120")
	c.JSON(http.StatusOK, CompletionistMapResponse{
		Consoles:    consoles,
		TotalGames:  totalGames,
		TotalPlayed: totalPlayed,
		OverallPct:  overallPct,
	})
}
