package api

import (
	"context"
	"log/slog"
	"math"
	"net/http"
	"strings"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Input / output types ---

// GetTasteProfileInput is the input for GET /api/user/taste-profile.
type GetTasteProfileInput struct{}

// GetTasteProfileOutput wraps the taste profile response.
type GetTasteProfileOutput struct {
	Body TasteProfileResponse
}

// GetExplorerBadgesInput is the input for GET /api/user/explorer-badges.
type GetExplorerBadgesInput struct{}

// GetExplorerBadgesOutput wraps the explorer badges response.
type GetExplorerBadgesOutput struct {
	Body ExplorerBadgesResponse
}

// GetCompletionistMapInput is the input for GET /api/user/completionist-map.
type GetCompletionistMapInput struct{}

// GetCompletionistMapOutput wraps the completionist map response.
type GetCompletionistMapOutput struct {
	Body CompletionistMapResponse
}

// RegisterProfileRoutes wires the per-user "profile" endpoints (taste profile,
// explorer badges, completionist map) into the huma API.
func RegisterProfileRoutes(api huma.API, h *ExploreHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getTasteProfile",
		Method:      http.MethodGet,
		Path:        "/api/user/taste-profile",
		Summary:     "Get the caller's taste profile",
		Description: "Returns the caller's genre / theme / top-console breakdown derived from play history.",
		Tags:        []string{"profiles"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetTasteProfile)

	huma.Register(api, huma.Operation{
		OperationID: "getExplorerBadges",
		Method:      http.MethodGet,
		Path:        "/api/user/explorer-badges",
		Summary:     "Get the caller's explorer badges",
		Description: "Returns breadth-of-exploration achievements (consoles, genres, decades, games played, hours).",
		Tags:        []string{"profiles"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetExplorerBadges)

	huma.Register(api, huma.Operation{
		OperationID: "getCompletionistMap",
		Method:      http.MethodGet,
		Path:        "/api/user/completionist-map",
		Summary:     "Get the caller's per-console completion stats",
		Description: "Returns a per-console breakdown of games played vs total + an overall percentage.",
		Tags:        []string{"profiles"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetCompletionistMap)
}

// --- Handlers ---

// HumaGetTasteProfile is the huma handler for GET /api/user/taste-profile.
func (h *ExploreHandler) HumaGetTasteProfile(ctx context.Context, _ *GetTasteProfileInput) (*GetTasteProfileOutput, error) {
	userID := UserIDFromContext(ctx)
	if userID == 0 {
		return nil, huma.Error401Unauthorized("authentication required")
	}

	var totalPlayTime int64
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("COALESCE(SUM(play_time), 0)").
		Where("user_id = ? AND play_time > 0", userID).
		Scan(&totalPlayTime).Error; err != nil {
		slog.Error("failed to calculate total play time", "error", err)
		return nil, huma.Error500InternalServerError("failed to build taste profile")
	}

	type genreRow struct {
		Genre     string
		PlayTime  int64
		GameCount int
	}
	var genreRows []genreRow
	if err := h.DB.
		Table("play_histories").
		Select("games.genre, SUM(play_histories.play_time) as play_time, COUNT(DISTINCT play_histories.game_id) as game_count").
		Joins("JOIN games ON games.id = play_histories.game_id AND games.deleted_at IS NULL").
		Where("play_histories.user_id = ? AND play_histories.play_time > 0 AND games.genre != '' AND play_histories.deleted_at IS NULL", userID).
		Group("games.genre").
		Order("play_time DESC").
		Scan(&genreRows).Error; err != nil {
		slog.Error("failed to get genre breakdown", "error", err)
		return nil, huma.Error500InternalServerError("failed to build taste profile")
	}

	genres := make([]TasteProfileGenre, len(genreRows))
	for i, r := range genreRows {
		pct := float64(0)
		if totalPlayTime > 0 {
			pct = math.Round(float64(r.PlayTime) / float64(totalPlayTime) * 100)
		}
		genres[i] = TasteProfileGenre{
			Name:       r.Genre,
			Percentage: pct,
			PlayTime:   r.PlayTime,
			GameCount:  r.GameCount,
		}
	}

	type themeRow struct {
		Name      string
		PlayTime  int64
		GameCount int
	}
	var themeRows []themeRow
	if err := h.DB.
		Table("play_histories").
		Select("game_themes.name, SUM(play_histories.play_time) as play_time, COUNT(DISTINCT play_histories.game_id) as game_count").
		Joins("JOIN game_themes ON game_themes.game_id = play_histories.game_id").
		Where("play_histories.user_id = ? AND play_histories.play_time > 0 AND play_histories.deleted_at IS NULL", userID).
		Group("game_themes.name").
		Order("play_time DESC").
		Scan(&themeRows).Error; err != nil {
		slog.Error("failed to get theme breakdown", "error", err)
		return nil, huma.Error500InternalServerError("failed to build taste profile")
	}

	themes := make([]TasteProfileTheme, len(themeRows))
	for i, r := range themeRows {
		pct := float64(0)
		if totalPlayTime > 0 {
			pct = math.Round(float64(r.PlayTime) / float64(totalPlayTime) * 100)
		}
		themes[i] = TasteProfileTheme{
			Name:       r.Name,
			Percentage: pct,
			PlayTime:   r.PlayTime,
			GameCount:  r.GameCount,
		}
	}

	type consoleRow struct {
		Name         string
		Abbreviation string
		PlayTime     int64
		GameCount    int
	}
	var consoleRows []consoleRow
	if err := h.DB.
		Table("play_histories").
		Select("consoles.name, consoles.abbreviation, SUM(play_histories.play_time) as play_time, COUNT(DISTINCT play_histories.game_id) as game_count").
		Joins("JOIN games ON games.id = play_histories.game_id AND games.deleted_at IS NULL").
		Joins("JOIN consoles ON consoles.id = games.console_id").
		Where("play_histories.user_id = ? AND play_histories.play_time > 0 AND play_histories.deleted_at IS NULL", userID).
		Group("consoles.id").
		Order("play_time DESC").
		Scan(&consoleRows).Error; err != nil {
		slog.Error("failed to get console breakdown", "error", err)
		return nil, huma.Error500InternalServerError("failed to build taste profile")
	}

	topConsoles := make([]TasteProfileConsole, len(consoleRows))
	for i, r := range consoleRows {
		topConsoles[i] = TasteProfileConsole{
			Name:         r.Name,
			Abbreviation: strings.ToLower(r.Abbreviation),
			PlayTime:     r.PlayTime,
			GameCount:    r.GameCount,
		}
	}

	return &GetTasteProfileOutput{Body: TasteProfileResponse{
		TotalPlayTime: totalPlayTime,
		Genres:        genres,
		Themes:        themes,
		TopConsoles:   topConsoles,
	}}, nil
}

// HumaGetExplorerBadges is the huma handler for GET /api/user/explorer-badges.
func (h *ExploreHandler) HumaGetExplorerBadges(ctx context.Context, _ *GetExplorerBadgesInput) (*GetExplorerBadgesOutput, error) {
	userID := UserIDFromContext(ctx)
	if userID == 0 {
		return nil, huma.Error401Unauthorized("authentication required")
	}

	var consolesPlayed int64
	if err := h.DB.Raw(`
		SELECT COUNT(DISTINCT g.console_id)
		FROM play_histories ph
		JOIN games g ON g.id = ph.game_id
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL AND g.deleted_at IS NULL
	`, userID).Scan(&consolesPlayed).Error; err != nil {
		slog.Error("failed to query consoles played", "error", err)
		return nil, huma.Error500InternalServerError("failed to compute badges")
	}

	var totalConsoles int64
	if err := h.DB.Raw(`SELECT COUNT(DISTINCT console_id) FROM games WHERE deleted_at IS NULL`).Scan(&totalConsoles).Error; err != nil {
		slog.Error("failed to query total consoles", "error", err)
		return nil, huma.Error500InternalServerError("failed to compute badges")
	}

	var genresPlayed int64
	if err := h.DB.Raw(`
		SELECT COUNT(DISTINCT g.genre)
		FROM play_histories ph
		JOIN games g ON g.id = ph.game_id
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL AND g.deleted_at IS NULL AND g.genre != ''
	`, userID).Scan(&genresPlayed).Error; err != nil {
		slog.Error("failed to query genres played", "error", err)
		return nil, huma.Error500InternalServerError("failed to compute badges")
	}

	var decadesPlayed int64
	if err := h.DB.Raw(`
		SELECT COUNT(DISTINCT CAST(SUBSTR(g.release_date, 1, 3) AS TEXT))
		FROM play_histories ph
		JOIN games g ON g.id = ph.game_id
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL AND g.deleted_at IS NULL
		AND g.release_date != '' AND LENGTH(g.release_date) >= 4
	`, userID).Scan(&decadesPlayed).Error; err != nil {
		slog.Error("failed to query decades played", "error", err)
		return nil, huma.Error500InternalServerError("failed to compute badges")
	}

	var gamesPlayed int64
	if err := h.DB.Raw(`
		SELECT COUNT(DISTINCT ph.game_id)
		FROM play_histories ph
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL
	`, userID).Scan(&gamesPlayed).Error; err != nil {
		slog.Error("failed to query games played", "error", err)
		return nil, huma.Error500InternalServerError("failed to compute badges")
	}

	var totalPlayTime int64
	if err := h.DB.Raw(`
		SELECT COALESCE(SUM(ph.play_time), 0)
		FROM play_histories ph
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL
	`, userID).Scan(&totalPlayTime).Error; err != nil {
		slog.Error("failed to query total play time", "error", err)
		return nil, huma.Error500InternalServerError("failed to compute badges")
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

	return &GetExplorerBadgesOutput{Body: ExplorerBadgesResponse{Badges: badges}}, nil
}

// HumaGetCompletionistMap is the huma handler for GET /api/user/completionist-map.
func (h *ExploreHandler) HumaGetCompletionistMap(ctx context.Context, _ *GetCompletionistMapInput) (*GetCompletionistMapOutput, error) {
	userID := UserIDFromContext(ctx)
	if userID == 0 {
		return nil, huma.Error401Unauthorized("authentication required")
	}

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
		return nil, huma.Error500InternalServerError("failed to compute completionist map")
	}

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
		return nil, huma.Error500InternalServerError("failed to compute completionist map")
	}

	playedMap := make(map[uint]int)
	for _, pr := range playedRows {
		playedMap[pr.ConsoleID] = pr.PlayedGames
	}

	consoles := make([]CompletionistConsole, 0, len(consoleRows))
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

	return &GetCompletionistMapOutput{Body: CompletionistMapResponse{
		Consoles:    consoles,
		TotalGames:  totalGames,
		TotalPlayed: totalPlayed,
		OverallPct:  overallPct,
	}}, nil
}
