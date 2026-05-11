package api

import (
	"context"
	"log/slog"
	"net/http"
	"strconv"
	"strings"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Inputs / outputs ---

// GetConsoleShowcaseInput is the input for GET /api/explore/consoles/{id}/showcase.
type GetConsoleShowcaseInput struct {
	ID string `path:"id" pattern:"^[a-zA-Z0-9_-]+$" maxLength:"32" doc:"Console abbreviation (e.g. 'snes')."`
}

// GetConsoleShowcaseOutput wraps the console showcase response.
type GetConsoleShowcaseOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         ConsoleShowcaseResponse
}

// GetConsoleHighlightsInput is the input for GET /api/explore/console-highlights.
type GetConsoleHighlightsInput struct{}

// GetConsoleHighlightsOutput wraps the console highlights response.
type GetConsoleHighlightsOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         ConsoleHighlightsResponse
}

// RegisterExploreConsoleRoutes wires the console showcase + highlights endpoints.
func RegisterExploreConsoleRoutes(api huma.API, h *ExploreHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getConsoleShowcase",
		Method:      http.MethodGet,
		Path:        "/api/explore/consoles/{id}/showcase",
		Summary:     "Get console showcase",
		Description: "Returns aggregated showcase data for a specific console: essentials, hidden gems, genre breakdown, top developers, recently played, recently added.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetConsoleShowcase)

	huma.Register(api, huma.Operation{
		OperationID: "getConsoleHighlights",
		Method:      http.MethodGet,
		Path:        "/api/explore/console-highlights",
		Summary:     "Get console highlights",
		Description: "Returns a compact list of consoles with game counts and top game for the explore page quick-jump row.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetConsoleHighlights)
}

// --- Handlers ---

// HumaGetConsoleShowcase is the huma handler for GET /api/explore/consoles/{id}/showcase.
func (h *ExploreHandler) HumaGetConsoleShowcase(ctx context.Context, in *GetConsoleShowcaseInput) (*GetConsoleShowcaseOutput, error) {
	userID := UserIDFromContext(ctx)
	abbr := strings.ToLower(in.ID)

	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = ?", abbr).First(&console).Error; err != nil {
		return nil, huma.Error404NotFound("console not found")
	}

	var gameCount int64
	h.DB.Model(&db.Game{}).Where("console_id = ? AND is_primary = true AND deleted_at IS NULL", console.ID).Count(&gameCount)
	console.GameCount = int(gameCount)

	var essentials []db.Game
	if err := h.DB.Preload("Console").
		Where("console_id = ? AND is_primary = true AND "+effectiveRating+" > 0 AND deleted_at IS NULL", console.ID).
		Order(effectiveRating + " DESC").
		Limit(10).
		Find(&essentials).Error; err != nil {
		slog.Error("failed to fetch console essentials", "console", abbr, "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch console data")
	}

	essentialIDs := make([]uint, len(essentials))
	for i, g := range essentials {
		essentialIDs[i] = g.ID
	}
	hiddenGems := h.buildConsoleHiddenGems(console.ID, essentialIDs)
	launchGames := h.buildLaunchGames(console.ID, console.Abbreviation)
	topDevelopers := h.buildConsoleTopDevelopers(console.ID, console.Name)

	var recentlyPlayed []db.Game
	if userID > 0 {
		var playHistories []db.PlayHistory
		if err := h.DB.
			Joins("JOIN games ON games.id = play_histories.game_id").
			Where("play_histories.user_id = ? AND games.console_id = ? AND games.is_primary = true AND games.deleted_at IS NULL", userID, console.ID).
			Order("play_histories.last_played DESC").
			Limit(10).
			Find(&playHistories).Error; err != nil {
			slog.Error("failed to fetch recently played", "console", abbr, "error", err)
		} else {
			gameIDs := make([]uint, 0, len(playHistories))
			for _, ph := range playHistories {
				gameIDs = append(gameIDs, ph.GameID)
			}
			if len(gameIDs) > 0 {
				if err := h.DB.Preload("Console").
					Where("id IN ? AND is_primary = true AND deleted_at IS NULL", gameIDs).
					Find(&recentlyPlayed).Error; err != nil {
					slog.Error("failed to fetch recently played games", "console", abbr, "error", err)
				}
				gameMap := make(map[uint]db.Game, len(recentlyPlayed))
				for _, g := range recentlyPlayed {
					gameMap[g.ID] = g
				}
				ordered := make([]db.Game, 0, len(gameIDs))
				for _, id := range gameIDs {
					if g, ok := gameMap[id]; ok {
						ordered = append(ordered, g)
					}
				}
				recentlyPlayed = ordered
			}
		}
	}

	var recentlyAdded []db.Game
	if err := h.DB.Preload("Console").
		Where("console_id = ? AND is_primary = true AND deleted_at IS NULL", console.ID).
		Order("created_at DESC").
		Limit(10).
		Find(&recentlyAdded).Error; err != nil {
		slog.Error("failed to fetch recently added games", "console", abbr, "error", err)
	}

	allGames := make([]db.Game, 0, len(essentials)+len(hiddenGems)+len(launchGames)+len(recentlyPlayed)+len(recentlyAdded))
	allGames = append(allGames, essentials...)
	allGames = append(allGames, hiddenGems...)
	allGames = append(allGames, launchGames...)
	allGames = append(allGames, recentlyPlayed...)
	allGames = append(allGames, recentlyAdded...)
	allGameIDs := make([]uint, len(allGames))
	for i, g := range allGames {
		allGameIDs[i] = g.ID
	}
	userData := loadUserGameData(h.DB, userID, allGameIDs)

	toResponses := func(games []db.Game) []GameResponse {
		result := make([]GameResponse, len(games))
		for i, g := range games {
			result[i] = toGameResponseWithData(g, &userData)
		}
		return result
	}

	return &GetConsoleShowcaseOutput{
		CacheControl: "private, max-age=300",
		Body: ConsoleShowcaseResponse{
			Console:        ToConsoleResponse(console),
			Essentials:     toResponses(essentials),
			HiddenGems:     toResponses(hiddenGems),
			LaunchGames:    toResponses(launchGames),
			TopDevelopers:  topDevelopers,
			RecentlyPlayed: toResponses(recentlyPlayed),
			RecentlyAdded:  toResponses(recentlyAdded),
		},
	}, nil
}

// HumaGetConsoleHighlights is the huma handler for GET /api/explore/console-highlights.
func (h *ExploreHandler) HumaGetConsoleHighlights(ctx context.Context, _ *GetConsoleHighlightsInput) (*GetConsoleHighlightsOutput, error) {
	userID := UserIDFromContext(ctx)

	var consoles []db.Console
	if err := h.DB.Order("name ASC").Find(&consoles).Error; err != nil {
		slog.Error("failed to fetch consoles for highlights", "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch consoles")
	}

	type consoleCount struct {
		ConsoleID uint
		GameCount int
	}
	var counts []consoleCount
	if err := h.DB.
		Table("games").
		Select("console_id, COUNT(*) as game_count").
		Where("deleted_at IS NULL AND is_primary = true").
		Group("console_id").
		Scan(&counts).Error; err != nil {
		slog.Error("failed to count games per console", "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch console data")
	}

	countMap := make(map[uint]int, len(counts))
	for _, cc := range counts {
		countMap[cc.ConsoleID] = cc.GameCount
	}

	type topGameRow struct {
		ConsoleID uint
		GameID    uint
	}
	var topGameRows []topGameRow
	if err := h.DB.Raw(`
		SELECT console_id, game_id FROM (
			SELECT games.console_id, games.id as game_id,
				`+effectiveRatingPrefixed+` as eff_rating,
				ROW_NUMBER() OVER (PARTITION BY games.console_id ORDER BY `+effectiveRatingPrefixed+` DESC) as rn
			FROM games
			JOIN game_artworks ON game_artworks.game_id = games.id AND game_artworks.hero_url != ''
			WHERE games.deleted_at IS NULL AND games.is_primary = true AND `+effectiveRatingPrefixed+` > 0
		) ranked WHERE rn = 1
	`).Scan(&topGameRows).Error; err != nil {
		slog.Error("failed to fetch top games for console highlights", "error", err)
	}

	topGameByConsole := make(map[uint]uint, len(topGameRows))
	for _, row := range topGameRows {
		topGameByConsole[row.ConsoleID] = row.GameID
	}

	topGameIDs := make([]uint, 0, len(topGameByConsole))
	for _, gid := range topGameByConsole {
		topGameIDs = append(topGameIDs, gid)
	}

	var topGames []db.Game
	if len(topGameIDs) > 0 {
		if err := h.DB.Preload("Console").Where("id IN ?", topGameIDs).Find(&topGames).Error; err != nil {
			slog.Error("failed to load top games for console highlights", "error", err)
		}
	}

	topGameResponses := ToGameResponses(topGames, h.DB, userID)
	topGameResponseMap := make(map[string]*GameResponse, len(topGameResponses))
	for i := range topGameResponses {
		topGameResponseMap[topGameResponses[i].ID] = &topGameResponses[i]
	}

	highlights := make([]ConsoleHighlight, 0, len(consoles))
	for _, con := range consoles {
		gc := countMap[con.ID]
		if gc == 0 {
			continue
		}

		abbr := strings.ToLower(con.Abbreviation)
		highlight := ConsoleHighlight{
			ID:              abbr,
			Name:            con.Name,
			ColorTheme:      con.ColorTheme,
			IconURL:         "/api/consoles/" + abbr + "/icon",
			LogoURL:         "/api/consoles/" + abbr + "/logo",
			LogoAspectRatio: con.LogoAspectRatio,
			GameCount:       gc,
		}

		if topGameID, ok := topGameByConsole[con.ID]; ok {
			idStr := strconv.FormatUint(uint64(topGameID), 10)
			if gr, ok := topGameResponseMap[idStr]; ok {
				highlight.TopGame = gr
			}
		}

		highlights = append(highlights, highlight)
	}

	return &GetConsoleHighlightsOutput{
		CacheControl: "private, max-age=300",
		Body:         ConsoleHighlightsResponse{Consoles: highlights},
	}, nil
}
