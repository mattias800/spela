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

// GetExploreFeaturedInput is the input for GET /api/explore/featured.
type GetExploreFeaturedInput struct{}

// GetExploreFeaturedOutput wraps the featured games list for the huma response envelope.
type GetExploreFeaturedOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         []FeaturedGameResponse
}

// GetExploreRowsInput is the input for GET /api/explore/rows.
type GetExploreRowsInput struct{}

// GetExploreRowsOutput wraps the curated shelf rows response.
type GetExploreRowsOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         ExploreRowsResponse
}

// GetExploreFeaturedSeriesInput is the input for GET /api/explore/series/featured.
type GetExploreFeaturedSeriesInput struct{}

// GetExploreFeaturedSeriesOutput wraps the featured series response.
type GetExploreFeaturedSeriesOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         []FeaturedSeriesResponse
}

// GetExploreMoodsInput is the input for GET /api/explore/moods.
type GetExploreMoodsInput struct{}

// GetExploreMoodsOutput wraps the moods response.
type GetExploreMoodsOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         []MoodResponse
}

// GetMoodGamesInput is the input for GET /api/explore/mood/{mood}.
type GetMoodGamesInput struct {
	Mood string `path:"mood" pattern:"^[a-z][a-z0-9_-]{0,31}$" maxLength:"32" doc:"Mood identifier, e.g. 'chill', 'challenge'."`
}

// GetMoodGamesOutput wraps the games list for a mood.
type GetMoodGamesOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         []GameResponse
}

// GetSurpriseGameInput is the input for GET /api/explore/surprise.
type GetSurpriseGameInput struct {
	Console   string `query:"console" doc:"Console abbreviation filter."`
	Genre     string `query:"genre" doc:"Genre filter."`
	MinRating string `query:"minRating" doc:"Minimum IGDB rating (0-100); defaults to 70."`
}

// GetSurpriseGameOutput wraps a single surprise game response.
type GetSurpriseGameOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         GameResponse
}

// --- Registration ---

// RegisterExploreFeaturedRoutes wires the featured / rows / series / moods /
// surprise explore endpoints into the huma API.
func RegisterExploreFeaturedRoutes(api huma.API, h *ExploreHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getExploreFeatured",
		Method:      http.MethodGet,
		Path:        "/api/explore/featured",
		Summary:     "Get featured games for the explore hero carousel",
		Description: "Returns up to 10 featured games selected via weighted random sampling, stable per-day.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetExploreFeatured)

	huma.Register(api, huma.Operation{
		OperationID: "getExploreRows",
		Method:      http.MethodGet,
		Path:        "/api/explore/rows",
		Summary:     "Get curated explore-page shelf rows",
		Description: "Returns curated shelves (top-rated, recently-added, hidden gems, most-played). Empty rows are omitted.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetExploreRows)

	huma.Register(api, huma.Operation{
		OperationID: "getExploreFeaturedSeries",
		Method:      http.MethodGet,
		Path:        "/api/explore/series/featured",
		Summary:     "Get featured game series",
		Description: "Returns up to 20 top series with at least 2 library games, sorted by library game count DESC.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetExploreFeaturedSeries)

	huma.Register(api, huma.Operation{
		OperationID: "getExploreMoods",
		Method:      http.MethodGet,
		Path:        "/api/explore/moods",
		Summary:     "List explore mood options",
		Description: "Returns the static list of moods available in the mood picker.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetExploreMoods)

	huma.Register(api, huma.Operation{
		OperationID: "getMoodGames",
		Method:      http.MethodGet,
		Path:        "/api/explore/mood/{mood}",
		Summary:     "Get games matching a specific mood",
		Description: "Returns up to 20 games matching the chosen mood (chill, challenge, nostalgia, something-new, quick, together).",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetMoodGames)

	huma.Register(api, huma.Operation{
		OperationID: "getSurpriseGame",
		Method:      http.MethodGet,
		Path:        "/api/explore/surprise",
		Summary:     "Get a random surprise game",
		Description: "Returns a single random game matching optional console/genre/min-rating filters.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetSurpriseGame)
}

// --- Handlers ---

// HumaGetExploreFeatured is the huma handler for GET /api/explore/featured.
func (h *ExploreHandler) HumaGetExploreFeatured(ctx context.Context, _ *GetExploreFeaturedInput) (*GetExploreFeaturedOutput, error) {
	userID := UserIDFromContext(ctx)

	type featuredRow struct {
		GameID    uint
		Title     string
		HeroURL   string
		LogoURL   string
		Rating    float64
		Genre     string
		ConsoleID uint
	}

	var allRows []featuredRow
	err := h.DB.
		Table("games").
		Select("games.id AS game_id, games.title, game_artworks.hero_url, game_artworks.logo_url, "+effectiveRatingPrefixed+" AS rating, games.genre, games.console_id").
		Joins("JOIN game_artworks ON game_artworks.game_id = games.id").
		Where("games.deleted_at IS NULL").
		Where("games.is_primary = true").
		Where("game_artworks.hero_url != '' AND game_artworks.logo_url != ''").
		Scan(&allRows).Error
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch featured games")
	}

	rows := weightedSample(allRows, 10, func(r featuredRow) float64 {
		if r.Rating > 0 {
			return r.Rating
		}
		return 10
	})

	if len(rows) == 0 {
		return &GetExploreFeaturedOutput{
			CacheControl: "private, max-age=300",
			Body:         []FeaturedGameResponse{},
		}, nil
	}

	consoleIDs := make([]uint, 0, len(rows))
	for _, r := range rows {
		consoleIDs = append(consoleIDs, r.ConsoleID)
	}
	var consoles []db.Console
	if err := h.DB.Where("id IN ?", consoleIDs).Find(&consoles).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch console data")
	}
	consoleMap := make(map[uint]db.Console, len(consoles))
	for _, con := range consoles {
		consoleMap[con.ID] = con
	}

	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}
	favorites := make(map[uint]bool)
	playLater := make(map[uint]bool)
	if userID > 0 {
		var favs []db.Favorite
		if err := h.DB.Where("user_id = ? AND game_id IN ?", userID, gameIDs).Find(&favs).Error; err != nil {
			return nil, huma.Error500InternalServerError("failed to fetch favorites")
		}
		for _, f := range favs {
			favorites[f.GameID] = true
		}
		var plItems []db.PlayLaterItem
		if err := h.DB.Where("user_id = ? AND game_id IN ?", userID, gameIDs).Find(&plItems).Error; err != nil {
			return nil, huma.Error500InternalServerError("failed to fetch play later items")
		}
		for _, item := range plItems {
			playLater[item.GameID] = true
		}
	}

	result := make([]FeaturedGameResponse, len(rows))
	for i, r := range rows {
		con := consoleMap[r.ConsoleID]
		abbr := strings.ToLower(con.Abbreviation)
		result[i] = FeaturedGameResponse{
			GameID:              strconv.FormatUint(uint64(r.GameID), 10),
			Title:               r.Title,
			HeroURL:             resolveImageURL(r.HeroURL),
			LogoURL:             resolveImageURL(r.LogoURL),
			ConsoleID:           abbr,
			ConsoleName:         con.Name,
			ConsoleAbbreviation: abbr,
			ConsoleColor:        con.ColorTheme,
			IGDBCriticsRating:   r.Rating,
			Genre:               r.Genre,
			IsFavorite:          favorites[r.GameID],
			IsPlayLater:         playLater[r.GameID],
		}
	}

	return &GetExploreFeaturedOutput{
		CacheControl: "private, max-age=300",
		Body:         result,
	}, nil
}

// HumaGetExploreRows is the huma handler for GET /api/explore/rows.
func (h *ExploreHandler) HumaGetExploreRows(ctx context.Context, _ *GetExploreRowsInput) (*GetExploreRowsOutput, error) {
	userID := UserIDFromContext(ctx)

	rows := []ExploreRowResponse{}

	if row, err := h.buildTopRatedRow(userID); err != nil {
		return nil, huma.Error500InternalServerError("failed to build top-rated row")
	} else if row != nil {
		rows = append(rows, *row)
	}

	if row, err := h.buildRecentlyAddedRow(userID); err != nil {
		return nil, huma.Error500InternalServerError("failed to build recently-added row")
	} else if row != nil {
		rows = append(rows, *row)
	}

	if row, err := h.buildHiddenGemsRow(userID); err != nil {
		return nil, huma.Error500InternalServerError("failed to build hidden-gems row")
	} else if row != nil {
		rows = append(rows, *row)
	}

	if row, err := h.buildMostPlayedRow(userID); err != nil {
		return nil, huma.Error500InternalServerError("failed to build most-played row")
	} else if row != nil {
		rows = append(rows, *row)
	}

	return &GetExploreRowsOutput{
		CacheControl: "private, max-age=300",
		Body:         ExploreRowsResponse{Rows: rows},
	}, nil
}

// HumaGetExploreFeaturedSeries is the huma handler for GET /api/explore/series/featured.
func (h *ExploreHandler) HumaGetExploreFeaturedSeries(_ context.Context, _ *GetExploreFeaturedSeriesInput) (*GetExploreFeaturedSeriesOutput, error) {
	type seriesRow struct {
		ID           uint
		Name         string
		TotalGames   int
		LibraryGames int
	}

	var rows []seriesRow
	if err := h.DB.
		Table("game_series").
		Select(`game_series.id, game_series.name,
			COUNT(game_series_entries.id) as total_games,
			COUNT(CASE WHEN games.id IS NOT NULL AND games.deleted_at IS NULL THEN 1 END) as library_games`).
		Joins("JOIN game_series_entries ON game_series_entries.series_id = game_series.id").
		Joins("LEFT JOIN games ON games.id = game_series_entries.game_id").
		Group("game_series.id").
		Having("library_games >= 2").
		Order("library_games DESC").
		Limit(20).
		Scan(&rows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch featured series")
	}

	if len(rows) == 0 {
		return &GetExploreFeaturedSeriesOutput{
			CacheControl: "private, max-age=300",
			Body:         []FeaturedSeriesResponse{},
		}, nil
	}

	seriesIDs := make([]uint, len(rows))
	for i, r := range rows {
		seriesIDs[i] = r.ID
	}

	type entryRow struct {
		SeriesID  uint
		GameID    uint
		ConsoleID uint
		Rating    float64
	}
	var entryRows []entryRow
	if err := h.DB.Table("game_series_entries").
		Select("game_series_entries.series_id, games.id as game_id, games.console_id, games.rating").
		Joins("JOIN games ON games.id = game_series_entries.game_id AND games.deleted_at IS NULL").
		Where("game_series_entries.series_id IN ?", seriesIDs).
		Scan(&entryRows).Error; err != nil {
		slog.Error("failed to batch-load series entries", "error", err)
	}

	type seriesGameInfo struct {
		gameID    uint
		consoleID uint
		rating    float64
	}
	seriesGames := make(map[uint][]seriesGameInfo)
	for _, e := range entryRows {
		seriesGames[e.SeriesID] = append(seriesGames[e.SeriesID], seriesGameInfo{
			gameID:    e.GameID,
			consoleID: e.ConsoleID,
			rating:    e.Rating,
		})
	}

	allGameIDs := make([]uint, 0)
	for _, games := range seriesGames {
		for _, g := range games {
			allGameIDs = append(allGameIDs, g.gameID)
		}
	}

	artworkMap := make(map[uint]string)
	if len(allGameIDs) > 0 {
		var artworks []db.GameArtwork
		h.DB.Where("game_id IN ? AND hero_url != ''", allGameIDs).Find(&artworks)
		for _, a := range artworks {
			artworkMap[a.GameID] = resolveImageURL(a.HeroURL)
		}
	}

	result := make([]FeaturedSeriesResponse, len(rows))
	for i, r := range rows {
		consolesSet := make(map[uint]bool)
		var bestHeroURL string
		var bestRating float64 = -1
		for _, g := range seriesGames[r.ID] {
			consolesSet[g.consoleID] = true
			if heroURL, ok := artworkMap[g.gameID]; ok {
				if g.rating > bestRating {
					bestRating = g.rating
					bestHeroURL = heroURL
				}
			}
		}

		result[i] = FeaturedSeriesResponse{
			ID:           strconv.FormatUint(uint64(r.ID), 10),
			Name:         r.Name,
			LibraryGames: r.LibraryGames,
			TotalGames:   r.TotalGames,
			ConsoleCount: len(consolesSet),
			HeroURL:      bestHeroURL,
		}
	}

	return &GetExploreFeaturedSeriesOutput{
		CacheControl: "private, max-age=300",
		Body:         result,
	}, nil
}

// HumaGetExploreMoods is the huma handler for GET /api/explore/moods.
func (h *ExploreHandler) HumaGetExploreMoods(_ context.Context, _ *GetExploreMoodsInput) (*GetExploreMoodsOutput, error) {
	return &GetExploreMoodsOutput{
		CacheControl: "private, max-age=300",
		Body:         moodDefinitions,
	}, nil
}

// HumaGetMoodGames is the huma handler for GET /api/explore/mood/{mood}.
func (h *ExploreHandler) HumaGetMoodGames(ctx context.Context, in *GetMoodGamesInput) (*GetMoodGamesOutput, error) {
	userID := UserIDFromContext(ctx)

	var games []db.Game
	var err error

	switch in.Mood {
	case "chill":
		games, err = h.getMoodChillGames()
	case "challenge":
		games, err = h.getMoodChallengeGames()
	case "nostalgia":
		games, err = h.getMoodNostalgiaGames(userID)
	case "something-new":
		games, err = h.getMoodSomethingNewGames(userID)
	case "quick":
		games, err = h.getMoodQuickGames()
	case "together":
		games, err = h.getMoodTogetherGames()
	default:
		return nil, huma.Error400BadRequest("invalid mood")
	}

	if err != nil {
		slog.Error("failed to fetch mood games", "mood", in.Mood, "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch mood games")
	}

	return &GetMoodGamesOutput{
		CacheControl: "private, max-age=120",
		Body:         ToGameResponses(games, h.DB, userID),
	}, nil
}

// HumaGetSurpriseGame is the huma handler for GET /api/explore/surprise.
func (h *ExploreHandler) HumaGetSurpriseGame(ctx context.Context, in *GetSurpriseGameInput) (*GetSurpriseGameOutput, error) {
	userID := UserIDFromContext(ctx)

	query := h.DB.Preload("Console").Where("cover_url != '' AND is_primary = true")

	if in.Console != "" {
		var console db.Console
		if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", in.Console).First(&console).Error; err == nil {
			query = query.Where("console_id = ?", console.ID)
		}
	}

	if in.Genre != "" {
		query = query.Where("genre = ?", in.Genre)
	}

	minRating := 70.0
	if in.MinRating != "" {
		if r, err := strconv.ParseFloat(in.MinRating, 64); err == nil && r >= 0 && r <= 100 {
			minRating = r
		}
	}
	if minRating > 0 {
		query = query.Where("rating >= ?", minRating)
	}

	var game db.Game
	if err := query.Order("RANDOM()").Limit(1).First(&game).Error; err != nil {
		if err.Error() == "record not found" {
			return nil, huma.Error404NotFound("no eligible games found")
		}
		slog.Error("failed to fetch surprise game", "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch surprise game")
	}

	return &GetSurpriseGameOutput{
		CacheControl: "no-store",
		Body:         ToGameResponse(game, h.DB, userID),
	}, nil
}
