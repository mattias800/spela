package api

import (
	"context"
	"log/slog"
	"net/http"
	"strconv"
	"strings"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scraper"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// --- Input / output types ---

// ListThemesInput is the input for GET /api/themes.
type ListThemesInput struct{}

// ListThemesOutput wraps the themes list body.
type ListThemesOutput struct {
	Body []ThemeResponse
}

// ListThemeGamesInput is the input for GET /api/themes/{id}/games.
type ListThemeGamesInput struct {
	ID       string `path:"id" doc:"IGDB theme ID."`
	Page     int    `query:"page" doc:"1-based page number (defaults to 1)."`
	PageSize int    `query:"pageSize" doc:"Page size (defaults to 20, clamped to 1-100)."`
}

// ListThemeGamesOutput wraps the paginated games list.
type ListThemeGamesOutput struct {
	Body PaginatedResponse[GameResponse]
}

// ListKeywordsInput is the input for GET /api/keywords.
type ListKeywordsInput struct {
	Limit int `query:"limit" doc:"Maximum number of keywords (defaults to 50, clamped to 1-200)."`
}

// ListKeywordsOutput wraps the keywords list body.
type ListKeywordsOutput struct {
	Body []KeywordResponse
}

// ListKeywordGamesInput is the input for GET /api/keywords/{id}/games.
type ListKeywordGamesInput struct {
	ID       string `path:"id" doc:"IGDB keyword ID."`
	Page     int    `query:"page" doc:"1-based page number."`
	PageSize int    `query:"pageSize" doc:"Page size."`
}

// ListKeywordGamesOutput wraps the paginated games list.
type ListKeywordGamesOutput struct {
	Body PaginatedResponse[GameResponse]
}

// ListSeriesInput is the input for GET /api/series.
type ListSeriesInput struct{}

// ListSeriesOutput wraps the series list body.
type ListSeriesOutput struct {
	Body []SeriesListResponse
}

// GetSeriesDetailInput is the input for GET /api/series/{id}.
type GetSeriesDetailInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Series ID."`
}

// GetSeriesDetailOutput wraps the series detail body.
type GetSeriesDetailOutput struct {
	Body SeriesDetailResponse
}

// ListFranchisesInput is the input for GET /api/franchises.
type ListFranchisesInput struct{}

// ListFranchisesOutput wraps the franchises list body.
type ListFranchisesOutput struct {
	Body []FranchiseResponse
}

// GetFranchiseDetailInput is the input for GET /api/franchises/{id}.
type GetFranchiseDetailInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Franchise group ID, or IGDB franchise ID as fallback."`
}

// GetFranchiseDetailOutput wraps the franchise detail body.
type GetFranchiseDetailOutput struct {
	Body FranchiseDetailResponse
}

// ListFranchiseGamesInput is the input for GET /api/franchises/{id}/games.
type ListFranchiseGamesInput struct {
	ID       string `path:"id" doc:"IGDB franchise ID."`
	Page     int    `query:"page" doc:"1-based page number."`
	PageSize int    `query:"pageSize" doc:"Page size."`
}

// ListFranchiseGamesOutput wraps the paginated games list.
type ListFranchiseGamesOutput struct {
	Body PaginatedResponse[GameResponse]
}

// GetGameSeriesInput is the input for GET /api/games/{id}/series.
type GetGameSeriesInput struct {
	ID string `path:"id" doc:"Game ID."`
}

// GetGameSeriesOutput wraps the game series list body.
type GetGameSeriesOutput struct {
	Body []GameSeriesResponse
}

// GetGameFranchisesInput is the input for GET /api/games/{id}/franchises.
type GetGameFranchisesInput struct {
	ID string `path:"id" doc:"Game ID."`
}

// GetGameFranchisesOutput wraps the game franchises list body.
type GetGameFranchisesOutput struct {
	Body []GameFranchiseResponse
}

// TriggerEnrichMetadataInput is the input for POST /api/admin/enrich-metadata.
type TriggerEnrichMetadataInput struct {
	Mode string `query:"mode" doc:"Enrichment mode: 'missing' (default) or 'all'."`
}

// EnrichmentStartedResponse is returned on POST /api/admin/enrich-metadata.
type EnrichmentStartedResponse struct {
	Message string `json:"message"`
	Total   int64  `json:"total"`
}

// TriggerEnrichMetadataOutput wraps the started-response body (HTTP 202 Accepted).
type TriggerEnrichMetadataOutput struct {
	Body EnrichmentStartedResponse
}

// EnrichMetadataStatusInput is the input for GET /api/admin/enrich-metadata/status.
type EnrichMetadataStatusInput struct{}

// EnrichmentStatusResponse is the wire format for enrichment status.
type EnrichmentStatusResponse struct {
	Active    bool   `json:"active"`
	Current   int    `json:"current"`
	Total     int    `json:"total"`
	GameName  string `json:"gameName"`
	Successes int    `json:"successes"`
	Failures  int    `json:"failures"`
}

// EnrichMetadataStatusOutput wraps the status response.
type EnrichMetadataStatusOutput struct {
	Body EnrichmentStatusResponse
}

// RegisterEnrichmentRoutes wires enrichment endpoints into the huma API.
func RegisterEnrichmentRoutes(api huma.API, h *EnrichmentHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	requireAdmin := RequireAdmin(api)
	mw := huma.Middlewares{requireAuth, rateLimit}
	adminMW := huma.Middlewares{requireAuth, rateLimit, requireAdmin}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "listThemes",
		Method:      http.MethodGet,
		Path:        "/api/themes",
		Summary:     "List IGDB themes",
		Description: "Returns all themes with at least one game in the library, sorted by game count descending.",
		Tags:        []string{"enrichment"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListThemes)

	huma.Register(api, huma.Operation{
		OperationID: "listThemeGames",
		Method:      http.MethodGet,
		Path:        "/api/themes/{id}/games",
		Summary:     "List games for a theme",
		Description: "Returns paginated games tagged with the specified IGDB theme, sorted by rating descending.",
		Tags:        []string{"enrichment"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListThemeGames)

	huma.Register(api, huma.Operation{
		OperationID: "listKeywords",
		Method:      http.MethodGet,
		Path:        "/api/keywords",
		Summary:     "List top keywords",
		Description: "Returns the top N keywords by game count (default 50, max 200).",
		Tags:        []string{"enrichment"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListKeywords)

	huma.Register(api, huma.Operation{
		OperationID: "listKeywordGames",
		Method:      http.MethodGet,
		Path:        "/api/keywords/{id}/games",
		Summary:     "List games for a keyword",
		Description: "Returns paginated games tagged with the specified IGDB keyword.",
		Tags:        []string{"enrichment"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListKeywordGames)

	huma.Register(api, huma.Operation{
		OperationID: "listSeries",
		Method:      http.MethodGet,
		Path:        "/api/series",
		Summary:     "List game series",
		Description: "Returns series with at least one local game, sorted by library game count.",
		Tags:        []string{"enrichment"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListSeries)

	huma.Register(api, huma.Operation{
		OperationID: "getSeriesDetail",
		Method:      http.MethodGet,
		Path:        "/api/series/{id}",
		Summary:     "Get series detail",
		Description: "Returns a series with all its games (local and non-local).",
		Tags:        []string{"enrichment"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetSeriesDetail)

	huma.Register(api, huma.Operation{
		OperationID: "listFranchises",
		Method:      http.MethodGet,
		Path:        "/api/franchises",
		Summary:     "List franchises",
		Description: "Returns all franchises with at least one game in the library.",
		Tags:        []string{"enrichment"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListFranchises)

	huma.Register(api, huma.Operation{
		OperationID: "getFranchiseDetail",
		Method:      http.MethodGet,
		Path:        "/api/franchises/{id}",
		Summary:     "Get franchise detail",
		Description: "Returns a franchise with all its games (local and non-local). Falls back to lookup by IGDB franchise ID.",
		Tags:        []string{"enrichment"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetFranchiseDetail)

	huma.Register(api, huma.Operation{
		OperationID: "listFranchiseGames",
		Method:      http.MethodGet,
		Path:        "/api/franchises/{id}/games",
		Summary:     "List games for a franchise",
		Description: "Returns paginated games in a franchise (by IGDB franchise ID).",
		Tags:        []string{"enrichment"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListFranchiseGames)

	huma.Register(api, huma.Operation{
		OperationID: "getGameSeries",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/series",
		Summary:     "Get series a game belongs to",
		Description: "Returns the series (with library and total game counts) that the specified game belongs to.",
		Tags:        []string{"enrichment"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetGameSeries)

	huma.Register(api, huma.Operation{
		OperationID: "getGameFranchises",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/franchises",
		Summary:     "Get franchises a game belongs to",
		Description: "Returns the franchises that the specified game belongs to with total/library game counts.",
		Tags:        []string{"enrichment"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetGameFranchises)

	huma.Register(api, huma.Operation{
		OperationID:   "triggerEnrichMetadata",
		Method:        http.MethodPost,
		Path:          "/api/admin/enrich-metadata",
		Summary:       "Trigger IGDB metadata enrichment",
		Description:   "Admin-only. Starts a background IGDB enrichment pass. Mode 'missing' (default) enriches games without themes; 'all' enriches every IGDB-scraped game.",
		DefaultStatus: http.StatusAccepted,
		Tags:          []string{"admin", "enrichment"},
		Middlewares:   adminMW,
		Security:      sec,
	}, h.HumaTriggerEnrichMetadata)

	huma.Register(api, huma.Operation{
		OperationID: "getEnrichMetadataStatus",
		Method:      http.MethodGet,
		Path:        "/api/admin/enrich-metadata/status",
		Summary:     "Get enrichment status",
		Description: "Admin-only. Returns whether an enrichment pass is active and its current progress.",
		Tags:        []string{"admin", "enrichment"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaEnrichMetadataStatus)
}

// --- Handlers ---

// HumaListThemes is the huma handler for GET /api/themes.
func (h *EnrichmentHandler) HumaListThemes(_ context.Context, _ *ListThemesInput) (*ListThemesOutput, error) {
	type themeRow struct {
		IGDBThemeID int
		Name        string
		GameCount   int
	}

	var rows []themeRow
	if err := h.DB.Model(&db.GameTheme{}).
		Joins("JOIN games ON games.id = game_themes.game_id AND games.deleted_at IS NULL AND games.is_primary = true").
		Select("igdb_theme_id, game_themes.name, COUNT(DISTINCT game_themes.game_id) as game_count").
		Group("igdb_theme_id, game_themes.name").
		Having("game_count > 0").
		Order("game_count DESC").
		Scan(&rows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch themes")
	}

	result := make([]ThemeResponse, len(rows))
	for i, r := range rows {
		result[i] = ThemeResponse{
			ID:        strconv.Itoa(r.IGDBThemeID),
			Name:      r.Name,
			GameCount: r.GameCount,
		}
	}
	return &ListThemesOutput{Body: result}, nil
}

// HumaListThemeGames is the huma handler for GET /api/themes/{id}/games.
func (h *EnrichmentHandler) HumaListThemeGames(ctx context.Context, in *ListThemeGamesInput) (*ListThemeGamesOutput, error) {
	themeID, err := strconv.Atoi(in.ID)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid theme ID")
	}

	var count int64
	h.DB.Model(&db.GameTheme{}).Where("igdb_theme_id = ?", themeID).Count(&count)
	if count == 0 {
		return nil, huma.Error404NotFound("theme not found")
	}

	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.Game{}).
		Joins("JOIN game_themes ON game_themes.game_id = games.id").
		Where("game_themes.igdb_theme_id = ?", themeID).
		Where("games.deleted_at IS NULL").
		Where("games.is_primary = true").
		Count(&total)

	var games []db.Game
	offset := (page - 1) * pageSize
	if err := h.DB.Preload("Console").Preload("Screenshots").
		Joins("JOIN game_themes ON game_themes.game_id = games.id").
		Where("game_themes.igdb_theme_id = ?", themeID).
		Where("games.deleted_at IS NULL").
		Where("games.is_primary = true").
		Order("games.rating DESC").
		Offset(offset).Limit(pageSize).
		Find(&games).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch theme games")
	}

	userID := UserIDFromContext(ctx)
	return &ListThemeGamesOutput{Body: PaginatedResponse[GameResponse]{
		Data:     ToGameResponses(games, h.DB, userID),
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}}, nil
}

// HumaListKeywords is the huma handler for GET /api/keywords.
func (h *EnrichmentHandler) HumaListKeywords(_ context.Context, in *ListKeywordsInput) (*ListKeywordsOutput, error) {
	limit := in.Limit
	if limit < 1 || limit > 200 {
		limit = 50
	}

	type keywordRow struct {
		IGDBKeywordID int
		Name          string
		GameCount     int
	}

	var rows []keywordRow
	if err := h.DB.Model(&db.GameKeyword{}).
		Joins("JOIN games ON games.id = game_keywords.game_id AND games.deleted_at IS NULL AND games.is_primary = true").
		Select("igdb_keyword_id, game_keywords.name, COUNT(DISTINCT game_keywords.game_id) as game_count").
		Group("igdb_keyword_id, game_keywords.name").
		Having("game_count > 0").
		Order("game_count DESC").
		Limit(limit).
		Scan(&rows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch keywords")
	}

	result := make([]KeywordResponse, len(rows))
	for i, r := range rows {
		result[i] = KeywordResponse{
			ID:        strconv.Itoa(r.IGDBKeywordID),
			Name:      r.Name,
			GameCount: r.GameCount,
		}
	}
	return &ListKeywordsOutput{Body: result}, nil
}

// HumaListKeywordGames is the huma handler for GET /api/keywords/{id}/games.
func (h *EnrichmentHandler) HumaListKeywordGames(ctx context.Context, in *ListKeywordGamesInput) (*ListKeywordGamesOutput, error) {
	keywordID, err := strconv.Atoi(in.ID)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid keyword ID")
	}

	var count int64
	h.DB.Model(&db.GameKeyword{}).Where("igdb_keyword_id = ?", keywordID).Count(&count)
	if count == 0 {
		return nil, huma.Error404NotFound("keyword not found")
	}

	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.Game{}).
		Joins("JOIN game_keywords ON game_keywords.game_id = games.id").
		Where("game_keywords.igdb_keyword_id = ?", keywordID).
		Where("games.deleted_at IS NULL").
		Where("games.is_primary = true").
		Count(&total)

	var games []db.Game
	offset := (page - 1) * pageSize
	if err := h.DB.Preload("Console").Preload("Screenshots").
		Joins("JOIN game_keywords ON game_keywords.game_id = games.id").
		Where("game_keywords.igdb_keyword_id = ?", keywordID).
		Where("games.deleted_at IS NULL").
		Where("games.is_primary = true").
		Order("games.rating DESC").
		Offset(offset).Limit(pageSize).
		Find(&games).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch keyword games")
	}

	userID := UserIDFromContext(ctx)
	return &ListKeywordGamesOutput{Body: PaginatedResponse[GameResponse]{
		Data:     ToGameResponses(games, h.DB, userID),
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}}, nil
}

// HumaListSeries is the huma handler for GET /api/series.
func (h *EnrichmentHandler) HumaListSeries(_ context.Context, _ *ListSeriesInput) (*ListSeriesOutput, error) {
	type seriesRow struct {
		ID               uint
		IGDBCollectionID int
		Name             string
		TotalGames       int
		LibraryGames     int
	}

	var rows []seriesRow
	if err := h.DB.
		Table("game_series").
		Select(`game_series.id, game_series.igdb_collection_id, game_series.name,
			COUNT(game_series_entries.id) as total_games,
			COUNT(CASE WHEN games.id IS NOT NULL AND games.deleted_at IS NULL THEN 1 END) as library_games`).
		Joins("JOIN game_series_entries ON game_series_entries.series_id = game_series.id").
		Joins("LEFT JOIN games ON games.id = game_series_entries.game_id").
		Group("game_series.id").
		Having("library_games > 0").
		Order("library_games DESC").
		Scan(&rows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch series")
	}

	result := make([]SeriesListResponse, len(rows))
	for i, r := range rows {
		result[i] = SeriesListResponse{
			ID:               strconv.FormatUint(uint64(r.ID), 10),
			IGDBCollectionID: r.IGDBCollectionID,
			Name:             r.Name,
			TotalGames:       r.TotalGames,
			LibraryGames:     r.LibraryGames,
		}
	}
	return &ListSeriesOutput{Body: result}, nil
}

// HumaGetSeriesDetail is the huma handler for GET /api/series/{id}.
func (h *EnrichmentHandler) HumaGetSeriesDetail(_ context.Context, in *GetSeriesDetailInput) (*GetSeriesDetailOutput, error) {
	parsedID, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid series ID")
	}
	var series db.GameSeries
	if err := h.DB.Preload("Entries").First(&series, uint(parsedID)).Error; err != nil {
		return nil, huma.Error404NotFound("series not found")
	}

	var localGameIDs []uint
	for _, entry := range series.Entries {
		if entry.GameID != nil {
			localGameIDs = append(localGameIDs, *entry.GameID)
		}
	}

	gameMap := make(map[uint]db.Game)
	if len(localGameIDs) > 0 {
		var localGames []db.Game
		h.DB.Preload("Console").Where("id IN ?", localGameIDs).Find(&localGames)
		for _, g := range localGames {
			gameMap[g.ID] = g
		}
	}

	artworkMap := make(map[uint]db.GameArtwork)
	if len(localGameIDs) > 0 {
		var artworks []db.GameArtwork
		h.DB.Where("game_id IN ? AND hero_url != ''", localGameIDs).Find(&artworks)
		for _, a := range artworks {
			artworkMap[a.GameID] = a
		}
	}

	consoleCountMap := make(map[uint]int)
	consoleInfoMap := make(map[uint]db.Console)
	libraryGames := 0

	var bestHeroURL string
	var bestLogoURL string
	var bestHeroRating float64 = -1

	games := make([]SeriesGameResponse, len(series.Entries))
	for i, entry := range series.Entries {
		gameResp := SeriesGameResponse{
			IGDBGameID: entry.IGDBGameID,
			Name:       entry.Name,
			InLibrary:  entry.GameID != nil,
		}
		if entry.GameID != nil {
			localID := strconv.FormatUint(uint64(*entry.GameID), 10)
			gameResp.LocalGameID = &localID

			if g, ok := gameMap[*entry.GameID]; ok {
				if g.DeletedAt.Valid {
					gameResp.InLibrary = false
					gameResp.LocalGameID = nil
				} else {
					libraryGames++
					if g.CoverURL != "" {
						coverURL := resolveImageURL(g.CoverURL)
						gameResp.CoverURL = &coverURL
					}
					gameResp.ReleaseDate = g.ReleaseDate
					gameResp.IGDBCriticsRating = g.IGDBCriticsRating
					if g.Console.ID != 0 {
						abbr := strings.ToLower(g.Console.Abbreviation)
						gameResp.ConsoleAbbreviation = abbr
						gameResp.ConsoleName = g.Console.Name
						gameResp.ConsoleColor = g.Console.ColorTheme
						consoleCountMap[g.Console.ID]++
						consoleInfoMap[g.Console.ID] = g.Console
					}
					if artwork, ok := artworkMap[*entry.GameID]; ok {
						if g.IGDBCriticsRating > bestHeroRating {
							bestHeroRating = g.IGDBCriticsRating
							bestHeroURL = resolveImageURL(artwork.HeroURL)
						}
						if bestLogoURL == "" && artwork.LogoURL != "" {
							bestLogoURL = resolveImageURL(artwork.LogoURL)
						}
					}
				}
			} else {
				gameResp.InLibrary = false
				gameResp.LocalGameID = nil
			}
		}
		games[i] = gameResp
	}

	consoles := make([]SeriesConsoleInfo, 0, len(consoleCountMap))
	for consoleID, cnt := range consoleCountMap {
		con := consoleInfoMap[consoleID]
		consoles = append(consoles, SeriesConsoleInfo{
			Abbreviation: strings.ToLower(con.Abbreviation),
			Name:         con.Name,
			Color:        con.ColorTheme,
			GameCount:    cnt,
		})
	}

	return &GetSeriesDetailOutput{Body: SeriesDetailResponse{
		ID:               strconv.FormatUint(uint64(series.ID), 10),
		IGDBCollectionID: series.IGDBCollectionID,
		Name:             series.Name,
		HeroURL:          bestHeroURL,
		LogoURL:          bestLogoURL,
		LibraryGames:     libraryGames,
		TotalGames:       len(series.Entries),
		Consoles:         consoles,
		Games:            games,
	}}, nil
}

// HumaListFranchises is the huma handler for GET /api/franchises.
func (h *EnrichmentHandler) HumaListFranchises(_ context.Context, _ *ListFranchisesInput) (*ListFranchisesOutput, error) {
	type franchiseRow struct {
		IGDBFranchiseID int
		FranchiseName   string
		GameCount       int
	}

	var rows []franchiseRow
	if err := h.DB.Model(&db.GameFranchise{}).
		Joins("JOIN games ON games.id = game_franchises.game_id AND games.deleted_at IS NULL AND games.is_primary = true").
		Select("igdb_franchise_id, franchise_name, COUNT(DISTINCT game_franchises.game_id) as game_count").
		Group("igdb_franchise_id, franchise_name").
		Having("game_count > 0").
		Order("game_count DESC").
		Scan(&rows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch franchises")
	}

	result := make([]FranchiseResponse, len(rows))
	for i, r := range rows {
		result[i] = FranchiseResponse{
			ID:        strconv.Itoa(r.IGDBFranchiseID),
			Name:      r.FranchiseName,
			GameCount: r.GameCount,
		}
	}
	return &ListFranchisesOutput{Body: result}, nil
}

// HumaGetFranchiseDetail is the huma handler for GET /api/franchises/{id}.
func (h *EnrichmentHandler) HumaGetFranchiseDetail(_ context.Context, in *GetFranchiseDetailInput) (*GetFranchiseDetailOutput, error) {
	parsedID, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid franchise ID")
	}
	id := uint(parsedID)
	var franchise db.GameFranchiseGroup
	if err := h.DB.Preload("Entries").First(&franchise, id).Error; err != nil {
		if err2 := h.DB.Preload("Entries").Where("igdb_franchise_id = ?", id).First(&franchise).Error; err2 != nil {
			return nil, huma.Error404NotFound("franchise not found")
		}
	}

	var localGameIDs []uint
	for _, entry := range franchise.Entries {
		if entry.GameID != nil {
			localGameIDs = append(localGameIDs, *entry.GameID)
		}
	}

	gameMap := make(map[uint]db.Game)
	if len(localGameIDs) > 0 {
		var localGames []db.Game
		h.DB.Preload("Console").Where("id IN ?", localGameIDs).Find(&localGames)
		for _, g := range localGames {
			gameMap[g.ID] = g
		}
	}

	artworkMap := make(map[uint]db.GameArtwork)
	if len(localGameIDs) > 0 {
		var artworks []db.GameArtwork
		h.DB.Where("game_id IN ? AND hero_url != ''", localGameIDs).Find(&artworks)
		for _, a := range artworks {
			artworkMap[a.GameID] = a
		}
	}

	consoleCountMap := make(map[uint]int)
	consoleInfoMap := make(map[uint]db.Console)
	libraryGames := 0

	var bestHeroURL string
	var bestLogoURL string
	var bestHeroRating float64 = -1

	games := make([]SeriesGameResponse, len(franchise.Entries))
	for i, entry := range franchise.Entries {
		gameResp := SeriesGameResponse{
			IGDBGameID: entry.IGDBGameID,
			Name:       entry.Name,
			InLibrary:  entry.GameID != nil,
		}
		if entry.GameID != nil {
			localID := strconv.FormatUint(uint64(*entry.GameID), 10)
			gameResp.LocalGameID = &localID

			if g, ok := gameMap[*entry.GameID]; ok {
				if g.DeletedAt.Valid {
					gameResp.InLibrary = false
					gameResp.LocalGameID = nil
				} else {
					libraryGames++
					if g.CoverURL != "" {
						coverURL := resolveImageURL(g.CoverURL)
						gameResp.CoverURL = &coverURL
					}
					gameResp.ReleaseDate = g.ReleaseDate
					gameResp.IGDBCriticsRating = g.IGDBCriticsRating
					if g.Console.ID != 0 {
						abbr := strings.ToLower(g.Console.Abbreviation)
						gameResp.ConsoleAbbreviation = abbr
						gameResp.ConsoleName = g.Console.Name
						gameResp.ConsoleColor = g.Console.ColorTheme
						consoleCountMap[g.Console.ID]++
						consoleInfoMap[g.Console.ID] = g.Console
					}
					if artwork, ok := artworkMap[*entry.GameID]; ok {
						if g.IGDBCriticsRating > bestHeroRating {
							bestHeroRating = g.IGDBCriticsRating
							bestHeroURL = resolveImageURL(artwork.HeroURL)
						}
						if bestLogoURL == "" && artwork.LogoURL != "" {
							bestLogoURL = resolveImageURL(artwork.LogoURL)
						}
					}
				}
			} else {
				gameResp.InLibrary = false
				gameResp.LocalGameID = nil
			}
		}
		games[i] = gameResp
	}

	consoles := make([]SeriesConsoleInfo, 0, len(consoleCountMap))
	for consoleID, cnt := range consoleCountMap {
		con := consoleInfoMap[consoleID]
		consoles = append(consoles, SeriesConsoleInfo{
			Abbreviation: strings.ToLower(con.Abbreviation),
			Name:         con.Name,
			Color:        con.ColorTheme,
			GameCount:    cnt,
		})
	}

	return &GetFranchiseDetailOutput{Body: FranchiseDetailResponse{
		ID:              strconv.FormatUint(uint64(franchise.ID), 10),
		IGDBFranchiseID: franchise.IGDBFranchiseID,
		Name:            franchise.Name,
		HeroURL:         bestHeroURL,
		LogoURL:         bestLogoURL,
		LibraryGames:    libraryGames,
		TotalGames:      len(franchise.Entries),
		Consoles:        consoles,
		Games:           games,
	}}, nil
}

// HumaListFranchiseGames is the huma handler for GET /api/franchises/{id}/games.
func (h *EnrichmentHandler) HumaListFranchiseGames(ctx context.Context, in *ListFranchiseGamesInput) (*ListFranchiseGamesOutput, error) {
	franchiseID, err := strconv.Atoi(in.ID)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid franchise ID")
	}

	var count int64
	h.DB.Model(&db.GameFranchise{}).Where("igdb_franchise_id = ?", franchiseID).Count(&count)
	if count == 0 {
		return nil, huma.Error404NotFound("franchise not found")
	}

	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.Game{}).
		Joins("JOIN game_franchises ON game_franchises.game_id = games.id").
		Where("game_franchises.igdb_franchise_id = ?", franchiseID).
		Where("games.deleted_at IS NULL").
		Where("games.is_primary = true").
		Count(&total)

	var games []db.Game
	offset := (page - 1) * pageSize
	if err := h.DB.Preload("Console").Preload("Screenshots").
		Joins("JOIN game_franchises ON game_franchises.game_id = games.id").
		Where("game_franchises.igdb_franchise_id = ?", franchiseID).
		Where("games.deleted_at IS NULL").
		Where("games.is_primary = true").
		Order("games.release_date ASC, games.title ASC").
		Offset(offset).Limit(pageSize).
		Find(&games).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch franchise games")
	}

	userID := UserIDFromContext(ctx)
	return &ListFranchiseGamesOutput{Body: PaginatedResponse[GameResponse]{
		Data:     ToGameResponses(games, h.DB, userID),
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}}, nil
}

// HumaGetGameSeries is the huma handler for GET /api/games/{id}/series.
func (h *EnrichmentHandler) HumaGetGameSeries(_ context.Context, in *GetGameSeriesInput) (*GetGameSeriesOutput, error) {
	gameID, err := strconv.Atoi(in.ID)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var game db.Game
	if err := h.DB.First(&game, gameID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	var entries []db.GameSeriesEntry
	if err := h.DB.Where("game_id = ?", gameID).Find(&entries).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch series")
	}

	if len(entries) == 0 {
		return &GetGameSeriesOutput{Body: []GameSeriesResponse{}}, nil
	}

	seriesIDs := make([]uint, 0, len(entries))
	seen := make(map[uint]bool)
	for _, e := range entries {
		if !seen[e.SeriesID] {
			seriesIDs = append(seriesIDs, e.SeriesID)
			seen[e.SeriesID] = true
		}
	}

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
		Where("game_series.id IN ?", seriesIDs).
		Group("game_series.id").
		Scan(&rows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch series details")
	}

	result := make([]GameSeriesResponse, len(rows))
	for i, r := range rows {
		result[i] = GameSeriesResponse{
			ID:           strconv.FormatUint(uint64(r.ID), 10),
			Name:         r.Name,
			TotalGames:   r.TotalGames,
			LibraryGames: r.LibraryGames,
		}
	}
	return &GetGameSeriesOutput{Body: result}, nil
}

// HumaGetGameFranchises is the huma handler for GET /api/games/{id}/franchises.
func (h *EnrichmentHandler) HumaGetGameFranchises(_ context.Context, in *GetGameFranchisesInput) (*GetGameFranchisesOutput, error) {
	gameID, err := strconv.Atoi(in.ID)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var game db.Game
	if err := h.DB.First(&game, gameID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	var franchises []db.GameFranchise
	if err := h.DB.Where("game_id = ?", gameID).Find(&franchises).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch franchises")
	}

	if len(franchises) == 0 {
		return &GetGameFranchisesOutput{Body: []GameFranchiseResponse{}}, nil
	}

	result := make([]GameFranchiseResponse, 0, len(franchises))
	seen := make(map[int]bool)
	for _, f := range franchises {
		if seen[f.IGDBFranchiseID] {
			continue
		}
		seen[f.IGDBFranchiseID] = true

		resp := GameFranchiseResponse{
			Name: f.FranchiseName,
		}

		var group db.GameFranchiseGroup
		if err := h.DB.Where("igdb_franchise_id = ?", f.IGDBFranchiseID).First(&group).Error; err == nil {
			resp.ID = strconv.FormatUint(uint64(group.ID), 10)
			var totalCount int64
			h.DB.Model(&db.GameFranchiseEntry{}).Where("franchise_group_id = ?", group.ID).Count(&totalCount)
			resp.TotalGames = int(totalCount)
			var libraryCount int64
			h.DB.Model(&db.GameFranchiseEntry{}).
				Joins("JOIN games ON games.id = game_franchise_entries.game_id AND games.deleted_at IS NULL").
				Where("game_franchise_entries.franchise_group_id = ?", group.ID).
				Count(&libraryCount)
			resp.LibraryGames = int(libraryCount)
		} else {
			resp.ID = strconv.Itoa(f.IGDBFranchiseID)
			var cnt int64
			h.DB.Model(&db.GameFranchise{}).
				Joins("JOIN games ON games.id = game_franchises.game_id AND games.deleted_at IS NULL").
				Where("game_franchises.igdb_franchise_id = ?", f.IGDBFranchiseID).
				Count(&cnt)
			resp.TotalGames = int(cnt)
			resp.LibraryGames = int(cnt)
		}

		result = append(result, resp)
	}
	return &GetGameFranchisesOutput{Body: result}, nil
}

// HumaTriggerEnrichMetadata is the huma handler for POST /api/admin/enrich-metadata.
func (h *EnrichmentHandler) HumaTriggerEnrichMetadata(ctx context.Context, in *TriggerEnrichMetadataInput) (*TriggerEnrichMetadataOutput, error) {
	if h.Scraper.IGDBClient == nil || !h.Scraper.IGDBClient.IsConfigured() {
		return nil, huma.Error400BadRequest("IGDB credentials not configured")
	}

	mode := in.Mode
	if mode == "" {
		mode = "missing"
	}

	if !h.Scraper.TryStartEnrich() {
		return nil, huma.Error409Conflict("An enrichment or scrape operation is already in progress")
	}

	var total int64
	switch mode {
	case "all":
		h.DB.Model(&db.Game{}).Where("scraper_id LIKE 'igdb:%'").Count(&total)
	default:
		h.DB.Model(&db.Game{}).Where("scraper_id LIKE 'igdb:%'").
			Where("id NOT IN (SELECT DISTINCT game_id FROM game_themes)").
			Count(&total)
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventEnrichStarted, Payload: ws.EnrichStartedPayload{Total: total}})
	}

	go func() {
		defer h.Scraper.FinishEnrich()

		count, enrichTotal, err := h.Scraper.EnrichAll(mode, func(p scraper.EnrichProgress) {
			h.Scraper.SetEnrichProgress(&p)
			if h.Hub != nil {
				h.Hub.Broadcast(ws.Event{Type: ws.EventEnrichProgress, Payload: p})
			}
		})
		if err != nil {
			slog.Error("metadata enrichment failed", "error", err)
			if h.Hub != nil {
				h.Hub.Broadcast(ws.Event{Type: ws.EventEnrichError, Payload: ErrorResponse{Error: "enrichment failed"}})
			}
			return
		}
		slog.Info("metadata enrichment complete", "enriched", count, "total", enrichTotal)
		if h.Hub != nil {
			h.Hub.Broadcast(ws.Event{Type: ws.EventEnrichComplete, Payload: ws.EnrichCompletePayload{Enriched: count, Total: enrichTotal}})
		}
	}()

	adminID := UserIDFromContext(ctx)
	slog.Info("audit: admin triggered enrichment", "admin_id", adminID, "mode", mode)
	return &TriggerEnrichMetadataOutput{Body: EnrichmentStartedResponse{
		Message: "enrichment started in background",
		Total:   total,
	}}, nil
}

// HumaEnrichMetadataStatus is the huma handler for GET /api/admin/enrich-metadata/status.
func (h *EnrichmentHandler) HumaEnrichMetadataStatus(_ context.Context, _ *EnrichMetadataStatusInput) (*EnrichMetadataStatusOutput, error) {
	active, progress := h.Scraper.GetEnrichStatus()
	if !active || progress == nil {
		return &EnrichMetadataStatusOutput{Body: EnrichmentStatusResponse{Active: false}}, nil
	}
	return &EnrichMetadataStatusOutput{Body: EnrichmentStatusResponse{
		Active:    true,
		Current:   progress.Current,
		Total:     progress.Total,
		GameName:  progress.GameName,
		Successes: progress.Successes,
		Failures:  progress.Failures,
	}}, nil
}
