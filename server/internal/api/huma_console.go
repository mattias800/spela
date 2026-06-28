package api

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"sort"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"gorm.io/gorm"
)

// ListConsolesInput is the input for GET /api/consoles.
type ListConsolesInput struct{}

// ListConsolesOutput wraps the consoles list for the huma response envelope.
type ListConsolesOutput struct {
	Body []ConsoleResponse
}

// ListConsoleGamesInput is the input for GET /api/consoles/{id}/games.
type ListConsoleGamesInput struct {
	ID             string `path:"id" pattern:"^[a-zA-Z0-9_-]+$" maxLength:"32" doc:"Console abbreviation (e.g. 'snes') or code."`
	Page           int    `query:"page" default:"1" minimum:"1" doc:"1-based page number."`
	PageSize       int    `query:"pageSize" default:"50" minimum:"1" maximum:"200" doc:"Page size."`
	SortBy         string `query:"sortBy" default:"title" enum:"title,created_at,file_size,rating" doc:"Sort column."`
	SortOrder      string `query:"sortOrder" default:"asc" enum:"asc,desc" doc:"Sort direction."`
	Search         string `query:"search" doc:"Substring match on title."`
	Letter         string `query:"letter" doc:"Alphabet quick-jump filter ('A'-'Z' or '#' for non-alpha)."`
	Region         string `query:"region" doc:"Region filter (comma-separated for multi-select)."`
	Grouped        string `query:"grouped" default:"true" enum:"true,false" doc:"Show only primary variants."`
	HidePreRelease string `query:"hidePreRelease" default:"true" enum:"true,false" doc:"Hide betas/protos/samples."`
}

// ListConsoleGamesOutput is the paginated game list response for GET /api/consoles/{id}/games.
type ListConsoleGamesOutput struct {
	Body PaginatedResponse[GameResponse]
}

// GetTopRatedInput is the input for GET /api/consoles/{id}/top-rated.
type GetTopRatedInput struct {
	ID string `path:"id" pattern:"^[a-zA-Z0-9_-]+$" maxLength:"32" doc:"Console abbreviation (e.g. 'snes') or code."`
}

// TopRatedListOutput wraps the top-rated game list for the huma response envelope.
type TopRatedListOutput struct {
	Body []TopRatedGameResponse
}

// GetTopRatedGlobalInput is the input for GET /api/top-rated.
type GetTopRatedGlobalInput struct{}

// TopListInput is the input for /api/consoles/{id}/top-lists/* endpoints that
// are scoped to a single console.
type TopListInput struct {
	ID string `path:"id" pattern:"^[a-zA-Z0-9_-]+$" maxLength:"32" doc:"Console abbreviation (e.g. 'snes') or code."`
}

// TopListGlobalInput is the input for the global /api/top-lists/* endpoints.
type TopListGlobalInput struct{}

// TopListOutput wraps the generic top-list (rated/critics) response body.
type TopListOutput struct {
	Body []TopListGameResponse
}

// TopListLongestOutput wraps the longest-games top-list response body.
type TopListLongestOutput struct {
	Body []LongestGameResponse
}

// RegisterConsoleRoutes wires the console read endpoints into the huma API.
// Covers the simple list, the paginated games list, the top-rated cache, and
// all three top-list variants (both global and per-console paths).
func RegisterConsoleRoutes(api huma.API, h *ConsoleHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "listConsoles",
		Method:      http.MethodGet,
		Path:        "/api/consoles",
		Summary:     "List consoles",
		Description: "Returns all consoles that have at least one primary, non-pre-release game, sorted by generation and name.",
		Tags:        []string{"consoles"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListConsoles)

	huma.Register(api, huma.Operation{
		OperationID: "listConsoleGames",
		Method:      http.MethodGet,
		Path:        "/api/consoles/{id}/games",
		Summary:     "List games for a console",
		Description: "Returns a paginated list of games for the specified console. Supports search, letter, region, sort, grouped (primary-variants-only) and hidePreRelease filters.",
		Tags:        []string{"consoles"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListConsoleGames)

	huma.Register(api, huma.Operation{
		OperationID: "getConsoleTopRated",
		Method:      http.MethodGet,
		Path:        "/api/consoles/{id}/top-rated",
		Summary:     "Top-rated games for a console",
		Description: "Returns the cached top-rated IGDB games for the specified console. Refreshed from IGDB when cached data is older than 7 days.",
		Tags:        []string{"consoles"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetTopRated)

	huma.Register(api, huma.Operation{
		OperationID: "getTopRatedGlobal",
		Method:      http.MethodGet,
		Path:        "/api/top-rated",
		Summary:     "Top-rated games (global)",
		Description: "Returns the top 20 top-rated IGDB games across all consoles, deduplicated by IGDB game ID, preferring entries whose console has a local library match.",
		Tags:        []string{"consoles"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetTopRatedGlobal)

	// --- Top lists (global) ---

	huma.Register(api, huma.Operation{
		OperationID: "getTopListAvailable",
		Method:      http.MethodGet,
		Path:        "/api/top-lists/top-rated",
		Summary:     "Top-rated available games (global)",
		Description: "Returns top-rated IGDB games that have a matching local game on the server, ranked by total rating descending.",
		Tags:        []string{"top-lists"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetTopListAvailableGlobal)

	huma.Register(api, huma.Operation{
		OperationID: "getTopListCritics",
		Method:      http.MethodGet,
		Path:        "/api/top-lists/top-rated-critics",
		Summary:     "Top-rated by critics (global)",
		Description: "Returns IGDB critic top-rated games that have a matching local game on the server, ranked by critic rating descending.",
		Tags:        []string{"top-lists"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetTopListCriticsGlobal)

	huma.Register(api, huma.Operation{
		OperationID: "getTopListLongest",
		Method:      http.MethodGet,
		Path:        "/api/top-lists/longest",
		Summary:     "Longest games (global)",
		Description: "Returns the games with the highest time-to-beat values, ranked descending.",
		Tags:        []string{"top-lists"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetTopListLongestGlobal)

	// --- Top lists (per-console) ---

	huma.Register(api, huma.Operation{
		OperationID: "getConsoleTopListAvailable",
		Method:      http.MethodGet,
		Path:        "/api/consoles/{id}/top-lists/top-rated",
		Summary:     "Top-rated available games (per console)",
		Description: "Per-console variant of the top-rated available list, scoped to the supplied console abbreviation.",
		Tags:        []string{"top-lists"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetTopListAvailable)

	huma.Register(api, huma.Operation{
		OperationID: "getConsoleTopListCritics",
		Method:      http.MethodGet,
		Path:        "/api/consoles/{id}/top-lists/top-rated-critics",
		Summary:     "Top-rated by critics (per console)",
		Description: "Per-console variant of the critic top-rated list, scoped to the supplied console abbreviation.",
		Tags:        []string{"top-lists"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetTopListCritics)

	huma.Register(api, huma.Operation{
		OperationID: "getConsoleTopListLongest",
		Method:      http.MethodGet,
		Path:        "/api/consoles/{id}/top-lists/longest",
		Summary:     "Longest games (per console)",
		Description: "Per-console variant of the longest-games list, scoped to the supplied console abbreviation.",
		Tags:        []string{"top-lists"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetTopListLongest)
}

// HumaListConsoles is the huma handler for GET /api/consoles.
func (h *ConsoleHandler) HumaListConsoles(_ context.Context, _ *ListConsolesInput) (*ListConsolesOutput, error) {
	var consoles []db.Console
	if err := h.DB.Preload("HardwareMaker").Preload("MediaType").Preload("MediaType.Category").
		Find(&consoles).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch consoles")
	}
	// Name and Generation are registry-derived (#1443), not sortable DB
	// columns, so order by (generation, name) in Go after AfterFind populates them.
	sort.SliceStable(consoles, func(i, j int) bool {
		if consoles[i].Generation != consoles[j].Generation {
			return consoles[i].Generation < consoles[j].Generation
		}
		return consoles[i].Name < consoles[j].Name
	})

	// Attach game counts (only primary, non-pre-release games — matches what users see).
	// One GROUP BY query instead of N COUNT queries — on a 40-console install this turns
	// 41 round-trips into 2.
	type countRow struct {
		ConsoleID uint
		Cnt       int64
	}
	var rows []countRow
	h.DB.Model(&db.Game{}).
		Select("console_id, COUNT(*) AS cnt").
		Where("is_primary = ? AND is_pre_release = ?", true, false).
		Group("console_id").
		Scan(&rows)
	counts := make(map[uint]int64, len(rows))
	for _, r := range rows {
		counts[r.ConsoleID] = r.Cnt
	}
	for i := range consoles {
		consoles[i].GameCount = int(counts[consoles[i].ID])
	}

	// Only return consoles that have at least one game
	result := make([]ConsoleResponse, 0, len(consoles))
	for _, con := range consoles {
		if con.GameCount > 0 {
			result = append(result, ToConsoleResponse(con))
		}
	}

	return &ListConsolesOutput{Body: result}, nil
}

// HumaListConsoleGames is the huma handler for GET /api/consoles/{id}/games.
func (h *ConsoleHandler) HumaListConsoleGames(ctx context.Context, in *ListConsoleGamesInput) (*ListConsoleGamesOutput, error) {
	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?) OR code = ?", in.ID, in.ID).First(&console).Error; err != nil {
		return nil, huma.Error404NotFound("console not found")
	}

	query := h.DB.Where("console_id = ?", console.ID).Preload("Console")
	countQuery := h.DB.Model(&db.Game{}).Where("console_id = ?", console.ID)

	// --- Variant grouping (default: show only primaries) ---
	if in.Grouped == "true" {
		query = query.Where("is_primary = ?", true)
		countQuery = countQuery.Where("is_primary = ?", true)
	}

	// --- Hide pre-release (default: hide betas/protos/samples) ---
	if in.HidePreRelease == "true" {
		query = query.Where("is_pre_release = ?", false)
		countQuery = countQuery.Where("is_pre_release = ?", false)
	}

	// --- Region filter ---
	if in.Region != "" {
		regions := strings.Split(in.Region, ",")
		var trimmed []string
		for _, r := range regions {
			r = strings.TrimSpace(r)
			if r != "" {
				trimmed = append(trimmed, r)
			}
		}
		if len(trimmed) > 0 {
			regionWhere := h.DB.Session(&gorm.Session{NewDB: true})
			countRegionWhere := h.DB.Session(&gorm.Session{NewDB: true})
			for i, r := range trimmed {
				cond := "LOWER(region) = LOWER(?) OR region LIKE ? ESCAPE '\\'"
				args := []interface{}{r, "%" + escapeLikePattern(r) + "%"}
				if i == 0 {
					regionWhere = regionWhere.Where(cond, args...)
					countRegionWhere = countRegionWhere.Where(cond, args...)
				} else {
					regionWhere = regionWhere.Or(cond, args...)
					countRegionWhere = countRegionWhere.Or(cond, args...)
				}
			}
			query = query.Where(regionWhere)
			countQuery = countQuery.Where(countRegionWhere)
		}
	}

	// --- Letter filter (alphabet quick-jump) ---
	if in.Letter != "" {
		letter := in.Letter
		if letter == "#" {
			query = query.Where("title GLOB '[0-9]*'")
			countQuery = countQuery.Where("title GLOB '[0-9]*'")
		} else if len(letter) == 1 && ((letter[0] >= 'A' && letter[0] <= 'Z') || (letter[0] >= 'a' && letter[0] <= 'z')) {
			upper := strings.ToUpper(letter)
			lower := strings.ToLower(letter)
			query = query.Where("title LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\'", lower+"%", upper+"%")
			countQuery = countQuery.Where("title LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\'", lower+"%", upper+"%")
		}
	}

	// Search
	if in.Search != "" {
		query = query.Where("title LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(in.Search)+"%")
		countQuery = countQuery.Where("title LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(in.Search)+"%")
	}

	// Sorting — huma has already validated the enum, but keep the defensive
	// fallback so behaviour matches the raw gin handler exactly.
	sortCol := in.SortBy
	allowedSorts := map[string]bool{"title": true, "created_at": true, "file_size": true, "rating": true}
	if !allowedSorts[sortCol] {
		sortCol = "title"
	}
	sortOrder := in.SortOrder
	if sortOrder != "asc" && sortOrder != "desc" {
		sortOrder = "asc"
	}
	query = query.Order(sortCol + " " + sortOrder)

	// Pagination
	page := in.Page
	perPage := in.PageSize
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 200 {
		perPage = 50
	}

	var total int64
	countQuery.Count(&total)

	offset := (page - 1) * perPage
	var games []db.Game
	if err := query.Offset(offset).Limit(perPage).Find(&games).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch games")
	}

	userID := UserIDFromContext(ctx)
	return &ListConsoleGamesOutput{
		Body: PaginatedResponse[GameResponse]{
			Data:     ToGameResponses(games, h.DB, userID),
			Total:    total,
			Page:     page,
			PageSize: perPage,
		},
	}, nil
}

// HumaGetTopRated is the huma handler for GET /api/consoles/{id}/top-rated.
func (h *ConsoleHandler) HumaGetTopRated(_ context.Context, in *GetTopRatedInput) (*TopRatedListOutput, error) {
	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?) OR code = ?", in.ID, in.ID).First(&console).Error; err != nil {
		return nil, huma.Error404NotFound("console not found")
	}

	abbr := strings.ToUpper(console.Abbreviation)
	igdbPlatformIDs, ok := igdb.AbbreviationToIGDBPlatform[abbr]
	if !ok {
		return &TopRatedListOutput{Body: []TopRatedGameResponse{}}, nil
	}

	var cached []db.TopRatedGame
	h.DB.Where("console_id = ?", console.ID).Order("rank asc").Find(&cached)

	fresh := len(cached) > 0 && time.Since(cached[0].UpdatedAt) < topRatedStaleness

	if h.Scraper != nil && h.Scraper.IGDBClient == nil {
		clientID, clientSecret := igdbCredentials(h.DB)
		if clientID != "" && clientSecret != "" {
			h.Scraper.IGDBClient = igdb.NewClient(clientID, clientSecret)
		}
	}

	// Cache refresh is fire-and-forget — never block the user's
	// response on the outbound IGDB HTTP call. See
	// HumaGetSimilarGames for the same pattern; same justification.
	if !fresh && h.Scraper != nil && h.Scraper.IGDBClient != nil && h.Scraper.IGDBClient.IsConfigured() {
		consoleID := console.ID
		igdbClient := h.Scraper.IGDBClient
		go func() {
			topGames, err := igdbClient.GetTopGames(igdbPlatformIDs, 100)
			if err != nil {
				slog.Warn("failed to fetch top-rated games from IGDB", "console", abbr, "error", err)
				return
			}
			ranked := bayesianRank(topGames, 25)
			h.upsertTopRatedGames(consoleID, ranked)
		}()
	}

	return &TopRatedListOutput{Body: h.buildTopRatedResponses(cached, false)}, nil
}

// HumaGetTopRatedGlobal is the huma handler for GET /api/top-rated.
func (h *ConsoleHandler) HumaGetTopRatedGlobal(_ context.Context, _ *GetTopRatedGlobalInput) (*TopRatedListOutput, error) {
	var libraryConsoleIDs []uint
	h.DB.Model(&db.Game{}).Where("deleted_at IS NULL").Distinct("console_id").Pluck("console_id", &libraryConsoleIDs)

	if len(libraryConsoleIDs) == 0 {
		return &TopRatedListOutput{Body: []TopRatedGameResponse{}}, nil
	}

	var all []db.TopRatedGame
	h.DB.Where("console_id IN ?", libraryConsoleIDs).Order("total_rating desc").Find(&all)

	// Determine which IGDB game IDs have a matching local game (and on
	// which console). The naive loop here used to issue one COUNT(*)
	// per IGDB row — N+1, with N up to ~150 candidates, and the inner
	// query did `LOWER(title)` against an unindexed column. With the
	// LOWER(title) functional index in place a single batched query
	// suffices: one trip for the scraper_id matches, one for the title
	// matches; everything else lives in maps.
	localConsoles := make(map[int]uint)
	if len(all) > 0 {
		scraperIDs := make([]string, 0, len(all))
		loweredTitles := make([]string, 0, len(all))
		scraperIDByTopRated := make(map[string][]int, len(all)) // scraperID -> []IGDBGameID
		titleByTopRated := make(map[string][]int, len(all))     // lowered name -> []IGDBGameID
		consoleByTopRated := make(map[int]uint, len(all))       // IGDBGameID -> ConsoleID
		for _, tr := range all {
			scraperID := fmt.Sprintf("igdb:%d", tr.IGDBGameID)
			loweredName := strings.ToLower(tr.Name)
			scraperIDs = append(scraperIDs, scraperID)
			loweredTitles = append(loweredTitles, loweredName)
			scraperIDByTopRated[scraperID] = append(scraperIDByTopRated[scraperID], tr.IGDBGameID)
			titleByTopRated[loweredName] = append(titleByTopRated[loweredName], tr.IGDBGameID)
			consoleByTopRated[tr.IGDBGameID] = tr.ConsoleID
		}

		type matchedGame struct {
			ScraperID string
			Title     string
			ConsoleID uint
		}
		var matches []matchedGame
		h.DB.Model(&db.Game{}).
			Select("scraper_id, title, console_id").
			Where("(scraper_id IN ? OR LOWER(title) IN ?) AND console_id IN ?",
				scraperIDs, loweredTitles, libraryConsoleIDs).
			Scan(&matches)

		for _, m := range matches {
			for _, igdbID := range scraperIDByTopRated[m.ScraperID] {
				if consoleByTopRated[igdbID] == m.ConsoleID {
					localConsoles[igdbID] = m.ConsoleID
				}
			}
			for _, igdbID := range titleByTopRated[strings.ToLower(m.Title)] {
				if consoleByTopRated[igdbID] == m.ConsoleID {
					localConsoles[igdbID] = m.ConsoleID
				}
			}
		}
	}

	best := make(map[int]db.TopRatedGame)
	order := make([]int, 0)
	for _, tr := range all {
		existing, seen := best[tr.IGDBGameID]
		if !seen {
			best[tr.IGDBGameID] = tr
			order = append(order, tr.IGDBGameID)
		} else if prefConsole, ok := localConsoles[tr.IGDBGameID]; ok && tr.ConsoleID == prefConsole && existing.ConsoleID != prefConsole {
			best[tr.IGDBGameID] = tr
		}
	}

	deduped := make([]db.TopRatedGame, 0, 20)
	for _, igdbID := range order {
		deduped = append(deduped, best[igdbID])
		if len(deduped) >= 20 {
			break
		}
	}

	return &TopRatedListOutput{Body: h.buildTopRatedResponses(deduped, true)}, nil
}

// HumaGetTopListAvailableGlobal is the huma handler for GET /api/top-lists/top-rated.
func (h *ConsoleHandler) HumaGetTopListAvailableGlobal(_ context.Context, _ *TopListGlobalInput) (*TopListOutput, error) {
	return h.humaTopListByRating("top_rated_games.total_rating", "top_rated_games.total_rating > 0", "")
}

// HumaGetTopListCriticsGlobal is the huma handler for GET /api/top-lists/top-rated-critics.
func (h *ConsoleHandler) HumaGetTopListCriticsGlobal(_ context.Context, _ *TopListGlobalInput) (*TopListOutput, error) {
	return h.humaTopListByRating("top_rated_games.critic_rating", "top_rated_games.critic_rating > 0", "")
}

// HumaGetTopListLongestGlobal is the huma handler for GET /api/top-lists/longest.
func (h *ConsoleHandler) HumaGetTopListLongestGlobal(_ context.Context, _ *TopListGlobalInput) (*TopListLongestOutput, error) {
	return h.humaTopListLongest("")
}

// HumaGetTopListAvailable is the huma handler for GET /api/consoles/{id}/top-lists/top-rated.
func (h *ConsoleHandler) HumaGetTopListAvailable(_ context.Context, in *TopListInput) (*TopListOutput, error) {
	return h.humaTopListByRating("top_rated_games.total_rating", "top_rated_games.total_rating > 0", in.ID)
}

// HumaGetTopListCritics is the huma handler for GET /api/consoles/{id}/top-lists/top-rated-critics.
func (h *ConsoleHandler) HumaGetTopListCritics(_ context.Context, in *TopListInput) (*TopListOutput, error) {
	return h.humaTopListByRating("top_rated_games.critic_rating", "top_rated_games.critic_rating > 0", in.ID)
}

// HumaGetTopListLongest is the huma handler for GET /api/consoles/{id}/top-lists/longest.
func (h *ConsoleHandler) HumaGetTopListLongest(_ context.Context, in *TopListInput) (*TopListLongestOutput, error) {
	return h.humaTopListLongest(in.ID)
}

// humaTopListByRating runs the shared top-rated / top-critics query, optionally
// scoped to a single console when consoleParam is non-empty.
func (h *ConsoleHandler) humaTopListByRating(ratingColumn, ratingFilter, consoleParam string) (*TopListOutput, error) {
	type row struct {
		GameID      uint
		Name        string
		CoverURL    string
		ConsoleAbbr string
		Rating      float64
	}

	q := h.DB.
		Table("top_rated_games").
		Select(`MIN(games.id) AS game_id,
				top_rated_games.name AS name,
				MIN(games.cover_url) AS cover_url,
				consoles.abbreviation AS console_abbr,
				` + ratingColumn + ` AS rating`).
		Joins("JOIN games ON (games.scraper_id = ('igdb:' || CAST(top_rated_games.igdb_game_id AS TEXT)) OR LOWER(games.title) = LOWER(top_rated_games.name)) AND games.console_id = top_rated_games.console_id AND games.deleted_at IS NULL AND games.is_primary = true").
		Joins("JOIN consoles ON consoles.id = top_rated_games.console_id AND consoles.deleted_at IS NULL").
		Where("top_rated_games.deleted_at IS NULL AND " + ratingFilter).
		Where("consoles.abbreviation NOT IN ?", demoConsoleAbbreviations)

	if consoleParam != "" {
		q = q.Where("LOWER(consoles.abbreviation) = LOWER(?)", consoleParam)
	}

	var rows []row
	err := q.
		Group("top_rated_games.id, top_rated_games.name, consoles.abbreviation, " + ratingColumn).
		Order(ratingColumn + " DESC").
		Limit(50).
		Find(&rows).Error
	if err != nil {
		slog.Error("failed to fetch top list", "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch top list")
	}

	result := make([]TopListGameResponse, len(rows))
	for i, r := range rows {
		result[i] = TopListGameResponse{
			Rank:              i + 1,
			GameId:            fmt.Sprintf("%d", r.GameID),
			Name:              r.Name,
			CoverUrl:          resolveImageURL(r.CoverURL),
			ConsoleName:       db.ConsoleName(r.ConsoleAbbr), // registry-derived (#1443)
			ConsoleId:         strings.ToLower(r.ConsoleAbbr),
			IGDBCriticsRating: r.Rating,
		}
	}

	return &TopListOutput{Body: result}, nil
}

// humaTopListLongest runs the shared longest-games query, optionally scoped
// to a single console when consoleParam is non-empty.
func (h *ConsoleHandler) humaTopListLongest(consoleParam string) (*TopListLongestOutput, error) {
	type row struct {
		GameID               uint
		Title                string
		CoverURL             string
		ConsoleAbbr          string
		TimeToBeatNormally   int
		TimeToBeatHastily    int
		TimeToBeatCompletely int
	}

	q := h.DB.
		Table("games").
		Select(`games.id AS game_id,
				games.title,
				games.cover_url,
				consoles.abbreviation AS console_abbr,
				games.time_to_beat_normally,
				games.time_to_beat_hastily,
				games.time_to_beat_completely`).
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Where("games.time_to_beat_normally > 0 AND games.time_to_beat_normally <= ? AND games.deleted_at IS NULL AND games.is_primary = true", maxTimeToBeatSeconds).
		Where("consoles.abbreviation NOT IN ?", demoConsoleAbbreviations)

	if consoleParam != "" {
		q = q.Where("LOWER(consoles.abbreviation) = LOWER(?)", consoleParam)
	}

	var rows []row
	err := q.
		Order("games.time_to_beat_normally DESC").
		Limit(50).
		Find(&rows).Error
	if err != nil {
		slog.Error("failed to fetch longest games list", "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch longest games list")
	}

	result := make([]LongestGameResponse, len(rows))
	for i, r := range rows {
		result[i] = LongestGameResponse{
			Rank:                 i + 1,
			GameId:               fmt.Sprintf("%d", r.GameID),
			Name:                 r.Title,
			CoverUrl:             resolveImageURL(r.CoverURL),
			ConsoleName:          db.ConsoleName(r.ConsoleAbbr), // registry-derived (#1443)
			ConsoleId:            strings.ToLower(r.ConsoleAbbr),
			TimeToBeatNormally:   r.TimeToBeatNormally,
			TimeToBeatHastily:    r.TimeToBeatHastily,
			TimeToBeatCompletely: r.TimeToBeatCompletely,
		}
	}

	return &TopListLongestOutput{Body: result}, nil
}

