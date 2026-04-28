package api

import (
	"context"
	"log/slog"
	"math"
	"net/http"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Inputs / outputs ---

// GetDevelopersInput is the input for GET /api/explore/developers.
type GetDevelopersInput struct{}

// GetDevelopersOutput wraps the developer list response.
type GetDevelopersOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         DeveloperListResponse
}

// GetDeveloperSpotlightInput is the input for GET /api/explore/developers/spotlight.
type GetDeveloperSpotlightInput struct{}

// GetDeveloperSpotlightOutput wraps the developer spotlight response.
type GetDeveloperSpotlightOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         DeveloperSpotlightResponse
}

// GetDeveloperDetailInput is the input for GET /api/explore/developers/{name}.
type GetDeveloperDetailInput struct {
	Name string `path:"name" doc:"Developer name (URL-decoded)."`
}

// GetDeveloperDetailOutput wraps the developer detail response.
type GetDeveloperDetailOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         DeveloperDetailResponse
}

// GetPublisherDetailInput is the input for GET /api/explore/publishers/{name}.
type GetPublisherDetailInput struct {
	Name string `path:"name" doc:"Publisher name (URL-decoded)."`
}

// GetPublisherDetailOutput wraps the publisher detail response.
type GetPublisherDetailOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         PublisherDetailResponse
}

// RegisterExploreDeveloperRoutes wires the developer / publisher detail endpoints.
func RegisterExploreDeveloperRoutes(api huma.API, h *ExploreHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getDevelopers",
		Method:      http.MethodGet,
		Path:        "/api/explore/developers",
		Summary:     "List top developers",
		Description: "Returns the top 50 developers by game count with average rating and console coverage.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetDevelopers)

	// NOTE: /developers/spotlight must be registered BEFORE /developers/{name} so huma
	// doesn't treat "spotlight" as a wildcard capture.
	huma.Register(api, huma.Operation{
		OperationID: "getDeveloperSpotlight",
		Method:      http.MethodGet,
		Path:        "/api/explore/developers/spotlight",
		Summary:     "Get the weekly featured developer",
		Description: "Returns a rotating spotlight of a top developer with games that have hero art. Rotates weekly.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetDeveloperSpotlight)

	huma.Register(api, huma.Operation{
		OperationID: "getDeveloperDetail",
		Method:      http.MethodGet,
		Path:        "/api/explore/developers/{name}",
		Summary:     "Get developer detail",
		Description: "Returns all games by a specific developer along with statistics (rating distribution, genre/platform breakdown, user stats, timeline, related developers).",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetDeveloperDetail)

	huma.Register(api, huma.Operation{
		OperationID: "getPublisherDetail",
		Method:      http.MethodGet,
		Path:        "/api/explore/publishers/{name}",
		Summary:     "Get publisher detail",
		Description: "Returns all games by a specific publisher along with statistics (rating distribution, genre/platform breakdown, user stats, timeline, related publishers).",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetPublisherDetail)
}

// --- Handlers ---

// HumaGetDevelopers is the huma handler for GET /api/explore/developers.
func (h *ExploreHandler) HumaGetDevelopers(_ context.Context, _ *GetDevelopersInput) (*GetDevelopersOutput, error) {
	type devRow struct {
		Developer string
		GameCount int
		AvgRating float64
	}

	var rows []devRow
	if err := h.DB.
		Table("games").
		Select("developer, COUNT(*) as game_count, AVG(CASE WHEN rating > 0 THEN rating ELSE NULL END) as avg_rating").
		Where("games.deleted_at IS NULL AND games.is_primary = true AND developer != ''").
		Group("developer").
		Order("game_count DESC").
		Limit(50).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch developers", "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch developers")
	}

	if len(rows) == 0 {
		return &GetDevelopersOutput{
			CacheControl: "private, max-age=300",
			Body:         DeveloperListResponse{Developers: []DeveloperSummary{}},
		}, nil
	}

	devNames := make([]string, len(rows))
	for i, r := range rows {
		devNames[i] = r.Developer
	}

	type devConsoleRow struct {
		Developer    string
		Abbreviation string
	}
	var consoleRows []devConsoleRow
	if err := h.DB.
		Table("games").
		Select("DISTINCT games.developer, consoles.abbreviation").
		Joins("JOIN consoles ON consoles.id = games.console_id").
		Where("games.deleted_at IS NULL AND games.is_primary = true AND games.developer IN ?", devNames).
		Scan(&consoleRows).Error; err != nil {
		slog.Error("failed to fetch developer consoles", "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch developers")
	}

	devConsoles := make(map[string][]string)
	for _, cr := range consoleRows {
		abbr := strings.ToLower(cr.Abbreviation)
		devConsoles[cr.Developer] = append(devConsoles[cr.Developer], abbr)
	}

	developers := make([]DeveloperSummary, len(rows))
	for i, r := range rows {
		avgRating := 0.0
		if r.AvgRating > 0 {
			avgRating = math.Round(r.AvgRating*10) / 10
		}
		consoles := devConsoles[r.Developer]
		if consoles == nil {
			consoles = []string{}
		}
		developers[i] = DeveloperSummary{
			Name:      r.Developer,
			GameCount: r.GameCount,
			AvgRating: avgRating,
			Consoles:  consoles,
		}
	}

	return &GetDevelopersOutput{
		CacheControl: "private, max-age=300",
		Body:         DeveloperListResponse{Developers: developers},
	}, nil
}

// HumaGetDeveloperDetail is the huma handler for GET /api/explore/developers/{name}.
func (h *ExploreHandler) HumaGetDeveloperDetail(ctx context.Context, in *GetDeveloperDetailInput) (*GetDeveloperDetailOutput, error) {
	userID := UserIDFromContext(ctx)
	name := in.Name

	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("LOWER(games.developer) = LOWER(?) AND games.is_primary = true AND games.deleted_at IS NULL", name).
		Order("games.rating DESC, games.title ASC").
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch developer games", "developer", name, "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch developer games")
	}

	avgRating, consoles := calcRatingAndConsoles(games)

	canonicalName := name
	if len(games) > 0 && games[0].Developer != "" {
		canonicalName = games[0].Developer
	}

	gameResponses := ToGameResponses(games, h.DB, userID)
	heroURL := h.findHeroURL(games)
	topGames := buildTopGames(gameResponses, 8)
	platformBreakdown := buildPlatformBreakdown(games)
	publishers := buildNameCountBreakdown(games, func(g db.Game) string { return g.Publisher })

	var userStats *EntityUserStats
	if userID > 0 {
		userStats = h.buildEntityUserStats(userID, games, gameResponses)
	}

	companyInfo := h.lookupCompanyInfo(canonicalName)
	activeYears := buildActiveYears(games)
	ratingDist := buildRatingDistribution(games)
	primaryGenre := buildPrimaryGenre(games)
	timeline := buildTimeline(games)
	relatedDevelopers := buildRelatedDevelopers(h.DB, canonicalName, publishers)

	return &GetDeveloperDetailOutput{
		CacheControl: "private, max-age=300",
		Body: DeveloperDetailResponse{
			Name:               canonicalName,
			GameCount:          len(games),
			AvgRating:          avgRating,
			Consoles:           consoles,
			Games:              gameResponses,
			HeroURL:            heroURL,
			TopGames:           topGames,
			PlatformBreakdown:  platformBreakdown,
			UserStats:          userStats,
			Publishers:         publishers,
			CompanyInfo:        companyInfo,
			ActiveYears:        activeYears,
			RatingDistribution: ratingDist,
			PrimaryGenre:       primaryGenre,
			Timeline:           timeline,
			RelatedDevelopers:  relatedDevelopers,
		},
	}, nil
}

// HumaGetPublisherDetail is the huma handler for GET /api/explore/publishers/{name}.
func (h *ExploreHandler) HumaGetPublisherDetail(ctx context.Context, in *GetPublisherDetailInput) (*GetPublisherDetailOutput, error) {
	userID := UserIDFromContext(ctx)
	name := in.Name

	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("LOWER(games.publisher) = LOWER(?) AND games.is_primary = true AND games.deleted_at IS NULL", name).
		Order("games.rating DESC, games.title ASC").
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch publisher games", "publisher", name, "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch publisher games")
	}

	avgRating, consoles := calcRatingAndConsoles(games)

	canonicalName := name
	if len(games) > 0 && games[0].Publisher != "" {
		canonicalName = games[0].Publisher
	}

	gameResponses := ToGameResponses(games, h.DB, userID)
	heroURL := h.findHeroURL(games)
	topGames := buildTopGames(gameResponses, 8)
	platformBreakdown := buildPlatformBreakdown(games)
	developers := buildNameCountBreakdown(games, func(g db.Game) string { return g.Developer })

	var userStats *EntityUserStats
	if userID > 0 {
		userStats = h.buildEntityUserStats(userID, games, gameResponses)
	}

	companyInfo := h.lookupCompanyInfo(canonicalName)
	activeYears := buildActiveYears(games)
	ratingDist := buildRatingDistribution(games)
	primaryGenre := buildPrimaryGenre(games)
	timeline := buildTimeline(games)
	relatedPublishers := buildRelatedPublishers(h.DB, canonicalName, developers)

	return &GetPublisherDetailOutput{
		CacheControl: "private, max-age=300",
		Body: PublisherDetailResponse{
			Name:               canonicalName,
			GameCount:          len(games),
			AvgRating:          avgRating,
			Consoles:           consoles,
			Games:              gameResponses,
			HeroURL:            heroURL,
			TopGames:           topGames,
			PlatformBreakdown:  platformBreakdown,
			UserStats:          userStats,
			Developers:         developers,
			CompanyInfo:        companyInfo,
			ActiveYears:        activeYears,
			RatingDistribution: ratingDist,
			PrimaryGenre:       primaryGenre,
			Timeline:           timeline,
			RelatedPublishers:  relatedPublishers,
		},
	}, nil
}

// HumaGetDeveloperSpotlight is the huma handler for GET /api/explore/developers/spotlight.
func (h *ExploreHandler) HumaGetDeveloperSpotlight(ctx context.Context, _ *GetDeveloperSpotlightInput) (*GetDeveloperSpotlightOutput, error) {
	userID := UserIDFromContext(ctx)

	type devRow struct {
		Developer string
		GameCount int
	}
	var rows []devRow
	if err := h.DB.
		Table("games").
		Select("games.developer, COUNT(DISTINCT games.id) as game_count").
		Joins("JOIN game_artworks ON game_artworks.game_id = games.id AND game_artworks.hero_url != ''").
		Where("games.deleted_at IS NULL AND games.is_primary = true AND games.developer != ''").
		Group("games.developer").
		Order("game_count DESC").
		Limit(5).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch developer spotlight candidates", "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch developer spotlight")
	}

	if len(rows) == 0 {
		// Empty spotlight is a normal state on a fresh / minimally-seeded
		// server, not an error. Return 200 with an empty Name so the
		// client can hide the section without surfacing a HumaError to
		// the user.
		return &GetDeveloperSpotlightOutput{
			CacheControl: "private, max-age=60",
			Body:         DeveloperSpotlightResponse{},
		}, nil
	}

	_, weekNumber := time.Now().ISOWeek()
	selectedDev := rows[weekNumber%len(rows)]

	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("LOWER(games.developer) = LOWER(?) AND games.is_primary = true AND games.deleted_at IS NULL", selectedDev.Developer).
		Order("games.rating DESC, games.title ASC").
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch spotlight developer games", "developer", selectedDev.Developer, "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch developer spotlight")
	}

	avgRating, consoles := calcRatingAndConsoles(games)

	topGames := games
	if len(topGames) > 8 {
		topGames = topGames[:8]
	}

	heroURL := ""
	if len(games) > 0 {
		gameIDs := make([]uint, len(games))
		for i, g := range games {
			gameIDs[i] = g.ID
		}
		var artwork db.GameArtwork
		if err := h.DB.
			Joins("JOIN games ON games.id = game_artworks.game_id").
			Where("game_artworks.game_id IN ? AND game_artworks.hero_url != ''", gameIDs).
			Order("games.rating DESC").
			First(&artwork).Error; err == nil {
			heroURL = resolveImageURL(artwork.HeroURL)
		}
	}

	var totalGameCount int64
	h.DB.Model(&db.Game{}).
		Where("LOWER(developer) = LOWER(?) AND is_primary = true AND deleted_at IS NULL", selectedDev.Developer).
		Count(&totalGameCount)

	return &GetDeveloperSpotlightOutput{
		CacheControl: "private, max-age=300",
		Body: DeveloperSpotlightResponse{
			Name:      selectedDev.Developer,
			GameCount: int(totalGameCount),
			AvgRating: avgRating,
			Consoles:  consoles,
			TopGames:  ToGameResponses(topGames, h.DB, userID),
			HeroURL:   heroURL,
		},
	}, nil
}
