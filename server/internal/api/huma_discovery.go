package api

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"gorm.io/gorm"
)

// GetSimilarGamesInput is the input for GET /api/games/{id}/similar.
type GetSimilarGamesInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
}

// GetSimilarGamesOutput wraps the similar-games list for the huma response envelope.
type GetSimilarGamesOutput struct {
	Body []SimilarGameResponse
}

// GetDeveloperGamesInput is the input for GET /api/games/{id}/developer-games.
type GetDeveloperGamesInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
}

// GetDeveloperGamesOutput wraps the developer-games list for the huma response envelope.
type GetDeveloperGamesOutput struct {
	Body []DeveloperGameResponse
}

// RegisterDiscoveryRoutes wires the game-discovery (similar + developer games)
// read endpoints into the huma API.
func RegisterDiscoveryRoutes(api huma.API, h *GameDiscoveryHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getSimilarGames",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/similar",
		Summary:     "Get similar games",
		Description: "Returns IGDB-sourced similar games for a local game. Results are cached locally and refreshed from IGDB when older than 7 days.",
		Tags:        []string{"games"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetSimilarGames)

	huma.Register(api, huma.Operation{
		OperationID: "getDeveloperGames",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/developer-games",
		Summary:     "Get other games by the same developer",
		Description: "Returns up to 20 other games by the same developer that are in the local library.",
		Tags:        []string{"games"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetDeveloperGames)
}

// HumaGetSimilarGames is the huma handler for GET /api/games/{id}/similar.
func (h *GameDiscoveryHandler) HumaGetSimilarGames(_ context.Context, in *GetSimilarGamesInput) (*GetSimilarGamesOutput, error) {
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	igdbGameID := parseIGDBGameID(game.ScraperID)
	if igdbGameID == 0 {
		return &GetSimilarGamesOutput{Body: []SimilarGameResponse{}}, nil
	}

	var cached []db.SimilarGame
	h.DB.Where("game_id = ?", game.ID).Find(&cached)

	fresh := len(cached) > 0 && time.Since(cached[0].UpdatedAt) < similarGamesStaleness

	if h.Scraper != nil && h.Scraper.IGDBClient == nil {
		clientID, clientSecret := igdbCredentials(h.DB)
		if clientID != "" && clientSecret != "" {
			h.Scraper.IGDBClient = igdb.NewClient(clientID, clientSecret)
		}
	}

	// Cache refresh is fire-and-forget — never block the user's response
	// on an outbound IGDB HTTP call. The user gets whatever's already
	// cached (possibly nothing on first-ever request for this game);
	// the background goroutine populates the cache so the next visit
	// is fast. Previously this awaited GetSimilarGames synchronously,
	// which added ~500–800ms to every game-detail page load when the
	// cache was older than 7 days (or empty).
	if !fresh && h.Scraper != nil && h.Scraper.IGDBClient != nil && h.Scraper.IGDBClient.IsConfigured() {
		gameID := game.ID
		gameTitle := game.Title
		igdbClient := h.Scraper.IGDBClient
		go func() {
			similarGames, err := igdbClient.GetSimilarGames(igdbGameID)
			if err != nil {
				slog.Warn("failed to fetch similar games from IGDB", "game", gameTitle, "error", err)
				return
			}
			h.upsertSimilarGames(gameID, similarGames)
		}()
	}

	// Batch the cross-reference lookup into a single query instead of
	// one per cached row. The old loop issued
	//   WHERE LOWER(title) = LOWER(?)
	// per similar game (~10 round-trips); the new pass does one
	// `LOWER(title) IN (...)` query and builds a map. Combined with
	// the LOWER(title) functional index added in the migrations,
	// the cross-ref step is now ~one indexed lookup.
	loweredNames := make([]string, 0, len(cached))
	for _, sg := range cached {
		if sg.Name != "" {
			loweredNames = append(loweredNames, strings.ToLower(sg.Name))
		}
	}

	localByLowerTitle := make(map[string]db.Game, len(loweredNames))
	if len(loweredNames) > 0 {
		var localMatches []db.Game
		// Preload Console so the generation filter below can read
		// localGame.Console.Generation without an extra round-trip.
		if err := h.DB.Preload("Console").Where("LOWER(title) IN ?", loweredNames).Find(&localMatches).Error; err == nil {
			for _, g := range localMatches {
				key := strings.ToLower(g.Title)
				if _, exists := localByLowerTitle[key]; !exists {
					localByLowerTitle[key] = g
				}
			}
		}
	}

	// Build an IGDB-platform → console-generation map so we can filter
	// the cached similar games by era. IGDB's similar_games field
	// ignores platform/era and routinely surfaces modern games for
	// retro titles (e.g. Borderlands 3 as "similar to" a 1993 NES
	// Jurassic Park). The filter keeps only games whose platform's
	// console generation is within ± 1 of the source game's console
	// generation.
	srcGen := game.Console.Generation
	platformGen := buildIGDBPlatformGenerationMap(h.DB)

	result := make([]SimilarGameResponse, 0, len(cached))
	for _, sg := range cached {
		localGame, hasLocal := localByLowerTitle[strings.ToLower(sg.Name)]

		// Apply the generation filter when the source game has a
		// known generation (Console.Generation > 0). Consoles seeded
		// without a generation (demos, ungraded retro systems) get
		// no filtering — return everything from the cache.
		if srcGen > 0 {
			candidateGens := make([]int, 0, 4)
			if hasLocal && localGame.Console.Generation > 0 {
				candidateGens = append(candidateGens, localGame.Console.Generation)
			}
			for _, pid := range parseIGDBPlatformsList(sg.Platforms) {
				if gen, ok := platformGen[pid]; ok && gen > 0 {
					candidateGens = append(candidateGens, gen)
				}
			}
			// Drop conservatively when we have no way to verify the
			// generation: no local match and no cached / mapped
			// platform data. Legacy cache rows (pre-Platforms column)
			// land here until the 7-day TTL refreshes them.
			if len(candidateGens) == 0 {
				continue
			}
			inRange := false
			for _, g := range candidateGens {
				if g >= srcGen-1 && g <= srcGen+1 {
					inRange = true
					break
				}
			}
			if !inRange {
				continue
			}
		}

		resp := SimilarGameResponse{
			IGDBGameID:        sg.IGDBGameID,
			Name:              sg.Name,
			IGDBCriticsRating: sg.IGDBCriticsRating,
		}

		if hasLocal {
			localID := fmt.Sprintf("%d", localGame.ID)
			resp.LocalGameId = &localID
			if localGame.CoverURL != "" {
				resp.CoverUrl = resolveImageURL(localGame.CoverURL)
			}
		}
		if resp.CoverUrl == "" && sg.CoverLocalPath != "" {
			resp.CoverUrl = resolveImageURL(sg.CoverLocalPath)
		}

		result = append(result, resp)
	}

	return &GetSimilarGamesOutput{Body: result}, nil
}

// buildIGDBPlatformGenerationMap returns a map from IGDB platform ID to
// the corresponding console generation, derived by joining
// [igdb.AbbreviationToIGDBPlatform] with the seeded Console rows. Used
// by [HumaGetSimilarGames] to filter cached suggestions to platforms
// within ± 1 generation of the source game.
//
// When multiple Spela consoles share an IGDB platform ID (e.g. C64
// and C128 both map to 15), the lowest non-zero generation wins so
// the filter stays permissive.
func buildIGDBPlatformGenerationMap(database *gorm.DB) map[int]int {
	var consoles []db.Console
	if err := database.Find(&consoles).Error; err != nil {
		return nil
	}
	genByAbbrev := make(map[string]int, len(consoles))
	for _, c := range consoles {
		genByAbbrev[c.Abbreviation] = c.Generation
	}
	result := make(map[int]int)
	for abbr, platformIDs := range igdb.AbbreviationToIGDBPlatform {
		gen, ok := genByAbbrev[abbr]
		if !ok || gen == 0 {
			continue
		}
		for _, pid := range platformIDs {
			if existing, exists := result[pid]; !exists || gen < existing {
				result[pid] = gen
			}
		}
	}
	return result
}

// HumaGetDeveloperGames is the huma handler for GET /api/games/{id}/developer-games.
func (h *GameDiscoveryHandler) HumaGetDeveloperGames(_ context.Context, in *GetDeveloperGamesInput) (*GetDeveloperGamesOutput, error) {
	var game db.Game
	if err := h.DB.First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	if game.Developer == "" {
		return &GetDeveloperGamesOutput{Body: []DeveloperGameResponse{}}, nil
	}

	var otherGames []db.Game
	if err := h.DB.Where("developer = ? AND id != ? AND is_primary = true", game.Developer, game.ID).
		Order("title asc").
		Limit(20).
		Find(&otherGames).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch developer games")
	}

	result := make([]DeveloperGameResponse, 0, len(otherGames))
	for _, g := range otherGames {
		coverUrl := resolveImageURL(g.CoverURL)
		result = append(result, DeveloperGameResponse{
			Name:        g.Title,
			CoverUrl:    coverUrl,
			LocalGameId: fmt.Sprintf("%d", g.ID),
		})
	}

	return &GetDeveloperGamesOutput{Body: result}, nil
}
