package api

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// --- Input / output types ---

// ListGamesInput is the input for GET /api/games with its many filter knobs.
// Two "aliased" query params exist for backward compat: consoleId (→ consoles),
// genre (→ genres), theme (→ themes), keyword (→ keywords), perspective (→ perspectives),
// perPage (→ pageSize), sort (→ sortBy), order (→ sortOrder). The original gin
// handler prefers the multi-select form when both are set — we mirror that.
type ListGamesInput struct {
	Page           int    `query:"page" doc:"1-based page number (defaults to 1)."`
	PageSize       int    `query:"pageSize" doc:"Page size (defaults to 50, clamped to 1-200)."`
	PerPage        int    `query:"perPage" doc:"Legacy alias for pageSize."`
	Consoles       string `query:"consoles" doc:"Comma-separated console abbreviations."`
	ConsoleID      string `query:"consoleId" doc:"Legacy alias for consoles (single console abbreviation)."`
	Search         string `query:"search" doc:"Substring match on title."`
	Letter         string `query:"letter" doc:"Alphabet quick-jump filter ('A'-'Z' or '#' for non-alpha)."`
	Region         string `query:"region" doc:"Region filter (comma-separated for multi-select)."`
	Genres         string `query:"genres" doc:"Comma-separated genres."`
	Genre          string `query:"genre" doc:"Legacy alias for genres."`
	Themes         string `query:"themes" doc:"Comma-separated IGDB theme IDs."`
	Theme          string `query:"theme" doc:"Legacy alias for themes."`
	Keywords       string `query:"keywords" doc:"Comma-separated IGDB keyword IDs."`
	Keyword        string `query:"keyword" doc:"Legacy alias for keywords."`
	Perspectives   string `query:"perspectives" doc:"Comma-separated IGDB player-perspective IDs."`
	Perspective    string `query:"perspective" doc:"Legacy alias for perspectives."`
	Developer      string `query:"developer" doc:"Developer substring filter."`
	Publisher      string `query:"publisher" doc:"Publisher substring filter."`
	YearMin        string `query:"yearMin" doc:"Earliest release year (inclusive)."`
	YearMax        string `query:"yearMax" doc:"Latest release year (inclusive)."`
	RatingMin      string `query:"ratingMin" doc:"Minimum rating (0-100)."`
	RatingMax      string `query:"ratingMax" doc:"Maximum rating (0-100)."`
	PlayStatus     string `query:"playStatus" doc:"Restrict to unplayed | played | favorited | play-later for the current user."`
	Grouped        string `query:"grouped" default:"true" enum:"true,false" doc:"Show only primary variants."`
	HidePreRelease string `query:"hidePreRelease" default:"true" enum:"true,false" doc:"Hide betas/protos/samples."`
	SortBy         string `query:"sortBy" doc:"Sort column (title, created_at, file_size, rating, release_date)."`
	Sort           string `query:"sort" doc:"Legacy alias for sortBy."`
	SortOrder      string `query:"sortOrder" doc:"Sort direction (asc, desc)."`
	Order          string `query:"order" doc:"Legacy alias for sortOrder."`
}

// ListGamesOutput wraps the paginated game list for the huma response envelope.
type ListGamesOutput struct {
	Body PaginatedResponse[GameResponse]
}

// GetGameInput is the input for GET /api/games/{id}.
type GetGameInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
}

// GetGameOutput wraps a single game response.
type GetGameOutput struct {
	Body GameResponse
}

// GetGameStatsOutput wraps the game-stats response body.
type GetGameStatsOutput struct {
	Body GameStatsResponse
}

// GetGameCheatsOutput wraps the cheats list response body.
type GetGameCheatsOutput struct {
	Body []GameCheatResponse
}

// GetRecommendedCoreOutput wraps the recommended-core response.
type GetRecommendedCoreOutput struct {
	Body db.Core
}

// ScrapeIfNeededInput is the input for POST /api/games/{id}/scrape-if-needed.
type ScrapeIfNeededInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
}

// StatusMessageResponse is the {status: ...} wire format used by the scrape /
// stop-playing endpoints. Wraps a free-form status enum the client treats as
// opaque.
type StatusMessageResponse struct {
	Status string `json:"status"`
}

// ScrapeIfNeededOutput uses huma's dynamic-status pattern: the Status field
// (special, not a JSON field) overrides the HTTP status code per call — 200
// when already scraped, 202 otherwise — matching the raw gin handler.
type ScrapeIfNeededOutput struct {
	Status int
	Body   StatusMessageResponse
}

// UpdateGamePlayTimeInput is the input for POST /api/games/{id}/play-time.
type UpdateGamePlayTimeInput struct {
	ID   string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
	Body UpdateGamePlayTimeRequest
}

// UpdateGamePlayTimeResponse is the wire format for the play-time update.
type UpdateGamePlayTimeResponse struct {
	PlayTime   int64     `json:"playTime"`
	LastPlayed time.Time `json:"lastPlayed"`
}

// UpdateGamePlayTimeOutput wraps the play-time response.
type UpdateGamePlayTimeOutput struct {
	Body UpdateGamePlayTimeResponse
}

// StopPlayingInput is the input for DELETE /api/games/{id}/play-time.
type StopPlayingInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID (path placeholder; not used server-side)."`
}

// StopPlayingOutput wraps the stop-playing status.
type StopPlayingOutput struct {
	Body StatusMessageResponse
}

// RegisterGameRoutes wires the game read + auth-required mutation endpoints
// into the huma API. File download endpoints (game download, disc download)
// stay on raw gin because huma does not map gin's c.File() pattern cleanly.
func RegisterGameRoutes(api huma.API, h *GameHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "listGames",
		Method:      http.MethodGet,
		Path:        "/api/games",
		Summary:     "List games",
		Description: "Returns a paginated list of games across all consoles. Supports console, genre, theme, keyword, perspective, developer, publisher, region, search, letter, year-range, rating-range, play-status, grouped, and hidePreRelease filters.",
		Tags:        []string{"games"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListGames)

	huma.Register(api, huma.Operation{
		OperationID: "getGame",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}",
		Summary:     "Get game by ID",
		Description: "Returns the full enriched game record including variants, ROM-hack references, BIOS status and user-scoped data.",
		Tags:        []string{"games"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetGame)

	huma.Register(api, huma.Operation{
		OperationID: "getGameStats",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/stats",
		Summary:     "Get aggregate community play stats for a game",
		Description: "Returns total player count, total/average play time and up to 10 top players by play time for the specified game.",
		Tags:        []string{"games"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetGameStats)

	huma.Register(api, huma.Operation{
		OperationID: "getGameCheats",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/cheats",
		Summary:     "Get cheat codes for a game",
		Description: "Returns all cheat codes associated with the specified game, ordered by cheat index.",
		Tags:        []string{"games"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetGameCheats)

	huma.Register(api, huma.Operation{
		OperationID: "getRecommendedCore",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/core",
		Summary:     "Get recommended libretro core for a game",
		Description: "Returns the recommended libretro core for the game as a full Core record. Responds 404 when the game is unknown or when the recommended core is not seeded on the server.",
		Tags:        []string{"games"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetRecommendedCore)

	huma.Register(api, huma.Operation{
		OperationID:   "scrapeGameIfNeeded",
		Method:        http.MethodPost,
		Path:          "/api/games/{id}/scrape-if-needed",
		Summary:       "Enqueue a game for scraping if not already scraped",
		Description:   "Returns {status:'already_scraped'} if the game has metadata, {status:'already_queued'} if a scrape is pending, or 202 {status:'queued'} if a new scrape job was enqueued.",
		DefaultStatus: http.StatusAccepted,
		Tags:          []string{"games"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaScrapeIfNeeded)

	huma.Register(api, huma.Operation{
		OperationID: "updateGamePlayTime",
		Method:      http.MethodPost,
		Path:        "/api/games/{id}/play-time",
		Summary:     "Increment the authenticated user's play time for a game",
		Description: "Adds the supplied number of seconds to the caller's play history for the specified game and marks them as currently playing it. Records daily activity for the play heatmap.",
		Tags:        []string{"games"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdatePlayTime)

	huma.Register(api, huma.Operation{
		OperationID: "stopPlayingGame",
		Method:      http.MethodDelete,
		Path:        "/api/games/{id}/play-time",
		Summary:     "Clear the authenticated user's currently-playing status",
		Description: "Clears the user's currently-playing game in the WebSocket hub. The path-level game ID is accepted for symmetry with POST but is not inspected.",
		Tags:        []string{"games"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaStopPlaying)
}

// HumaListGames is the huma handler for GET /api/games.
func (h *GameHandler) HumaListGames(ctx context.Context, in *ListGamesInput) (*ListGamesOutput, error) {
	userID := UserIDFromContext(ctx)

	query := h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").Preload("ReleaseDates")
	countQuery := h.DB.Model(&db.Game{})

	// --- Console filter ---
	consoles := in.Consoles
	if consoles == "" {
		consoles = in.ConsoleID
	}
	if consoles != "" {
		abbrs := strings.Split(consoles, ",")
		// Resolve all abbreviations in one query rather than one per item.
		// Previously a request like ?consoles=snes,nes,gba issued one
		// First() round-trip per entry — a multi-console filter on a
		// large library could fire 5+ queries before the main games
		// query even started.
		lowerAbbrs := make([]string, 0, len(abbrs))
		for _, abbr := range abbrs {
			trimmed := strings.TrimSpace(abbr)
			if trimmed != "" {
				lowerAbbrs = append(lowerAbbrs, strings.ToLower(trimmed))
			}
		}
		var consoleIDs []uint
		if len(lowerAbbrs) > 0 {
			var resolved []db.Console
			h.DB.Where("LOWER(abbreviation) IN ?", lowerAbbrs).Find(&resolved)
			for _, c := range resolved {
				consoleIDs = append(consoleIDs, c.ID)
			}
		}
		if len(consoleIDs) == 0 {
			return &ListGamesOutput{
				Body: PaginatedResponse[GameResponse]{
					Data:     []GameResponse{},
					Total:    0,
					Page:     1,
					PageSize: 50,
				},
			}, nil
		}
		query = query.Where("console_id IN ?", consoleIDs)
		countQuery = countQuery.Where("console_id IN ?", consoleIDs)
	}

	// --- Variant grouping ---
	if in.Grouped == "true" {
		query = query.Where("games.is_primary = ?", true)
		countQuery = countQuery.Where("is_primary = ?", true)
	}

	// --- Hide pre-release ---
	if in.HidePreRelease == "true" {
		query = query.Where("games.is_pre_release = ?", false)
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
			regionWhere := h.DB
			for i, r := range trimmed {
				cond := "LOWER(games.region) = LOWER(?) OR games.region LIKE ? ESCAPE '\\'"
				args := []interface{}{r, "%" + escapeLikePattern(r) + "%"}
				if i == 0 {
					regionWhere = regionWhere.Where(cond, args...)
				} else {
					regionWhere = regionWhere.Or(cond, args...)
				}
			}
			query = query.Where(regionWhere)
			countRegionWhere := h.DB
			for i, r := range trimmed {
				cond := "LOWER(region) = LOWER(?) OR region LIKE ? ESCAPE '\\'"
				args := []interface{}{r, "%" + escapeLikePattern(r) + "%"}
				if i == 0 {
					countRegionWhere = countRegionWhere.Where(cond, args...)
				} else {
					countRegionWhere = countRegionWhere.Or(cond, args...)
				}
			}
			countQuery = countQuery.Where(countRegionWhere)
		}
	}

	// --- Letter filter ---
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

	// --- Search ---
	if in.Search != "" {
		query = query.Where("title LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(in.Search)+"%")
		countQuery = countQuery.Where("title LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(in.Search)+"%")
	}

	// --- Genre filter ---
	genres := in.Genres
	if genres == "" {
		genres = in.Genre
	}
	if genres != "" {
		genreList := strings.Split(genres, ",")
		var trimmed []string
		for _, g := range genreList {
			g = strings.TrimSpace(g)
			if g != "" {
				trimmed = append(trimmed, g)
			}
		}
		if len(trimmed) > 0 {
			query = query.Where("genre IN ?", trimmed)
			countQuery = countQuery.Where("genre IN ?", trimmed)
		}
	}

	needsDistinct := false

	// --- Theme filter ---
	themes := in.Themes
	if themes == "" {
		themes = in.Theme
	}
	if themes != "" {
		themeIDs := strings.Split(themes, ",")
		query = query.Joins("JOIN game_themes ON game_themes.game_id = games.id").
			Where("game_themes.igdb_theme_id IN ?", themeIDs)
		countQuery = countQuery.Joins("JOIN game_themes ON game_themes.game_id = games.id").
			Where("game_themes.igdb_theme_id IN ?", themeIDs)
		needsDistinct = true
	}

	// --- Keyword filter ---
	keywords := in.Keywords
	if keywords == "" {
		keywords = in.Keyword
	}
	if keywords != "" {
		keywordIDs := strings.Split(keywords, ",")
		query = query.Joins("JOIN game_keywords ON game_keywords.game_id = games.id").
			Where("game_keywords.igdb_keyword_id IN ?", keywordIDs)
		countQuery = countQuery.Joins("JOIN game_keywords ON game_keywords.game_id = games.id").
			Where("game_keywords.igdb_keyword_id IN ?", keywordIDs)
		needsDistinct = true
	}

	// --- Perspective filter ---
	perspectives := in.Perspectives
	if perspectives == "" {
		perspectives = in.Perspective
	}
	if perspectives != "" {
		perspectiveIDs := strings.Split(perspectives, ",")
		query = query.Joins("JOIN game_player_perspectives ON game_player_perspectives.game_id = games.id").
			Where("game_player_perspectives.igdb_perspective_id IN ?", perspectiveIDs)
		countQuery = countQuery.Joins("JOIN game_player_perspectives ON game_player_perspectives.game_id = games.id").
			Where("game_player_perspectives.igdb_perspective_id IN ?", perspectiveIDs)
		needsDistinct = true
	}

	// --- Developer filter ---
	if in.Developer != "" {
		query = query.Where("games.developer LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(in.Developer)+"%")
		countQuery = countQuery.Where("games.developer LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(in.Developer)+"%")
	}

	// --- Publisher filter ---
	if in.Publisher != "" {
		query = query.Where("games.publisher LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(in.Publisher)+"%")
		countQuery = countQuery.Where("games.publisher LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(in.Publisher)+"%")
	}

	// --- Year range filters ---
	if in.YearMin != "" {
		if y, err := strconv.Atoi(in.YearMin); err == nil && y >= 1970 && y <= 2100 {
			query = query.Where("release_date >= ?", fmt.Sprintf("%d", y))
			countQuery = countQuery.Where("release_date >= ?", fmt.Sprintf("%d", y))
		}
	}
	if in.YearMax != "" {
		if y, err := strconv.Atoi(in.YearMax); err == nil && y >= 1970 && y <= 2100 {
			query = query.Where("release_date < ?", fmt.Sprintf("%d", y+1))
			countQuery = countQuery.Where("release_date < ?", fmt.Sprintf("%d", y+1))
		}
	}

	// --- Rating range filters ---
	if in.RatingMin != "" {
		if r, err := strconv.ParseFloat(in.RatingMin, 64); err == nil && r >= 0 && r <= 100 {
			query = query.Where("games.rating >= ?", r)
			countQuery = countQuery.Where("games.rating >= ?", r)
		}
	}
	if in.RatingMax != "" {
		if r, err := strconv.ParseFloat(in.RatingMax, 64); err == nil && r >= 0 && r <= 100 {
			query = query.Where("games.rating <= ?", r)
			countQuery = countQuery.Where("games.rating <= ?", r)
		}
	}

	// --- Play status filter ---
	if in.PlayStatus != "" && userID > 0 {
		switch in.PlayStatus {
		case "unplayed":
			query = query.Joins("LEFT JOIN play_histories ON play_histories.game_id = games.id AND play_histories.user_id = ? AND play_histories.deleted_at IS NULL", userID).
				Where("play_histories.id IS NULL")
			countQuery = countQuery.Joins("LEFT JOIN play_histories ON play_histories.game_id = games.id AND play_histories.user_id = ? AND play_histories.deleted_at IS NULL", userID).
				Where("play_histories.id IS NULL")
		case "played":
			query = query.Joins("JOIN play_histories ON play_histories.game_id = games.id AND play_histories.user_id = ? AND play_histories.deleted_at IS NULL", userID)
			countQuery = countQuery.Joins("JOIN play_histories ON play_histories.game_id = games.id AND play_histories.user_id = ? AND play_histories.deleted_at IS NULL", userID)
			needsDistinct = true
		case "favorited":
			query = query.Joins("JOIN favorites ON favorites.game_id = games.id AND favorites.user_id = ? AND favorites.deleted_at IS NULL", userID)
			countQuery = countQuery.Joins("JOIN favorites ON favorites.game_id = games.id AND favorites.user_id = ? AND favorites.deleted_at IS NULL", userID)
		case "play-later":
			query = query.Joins("JOIN play_later_items ON play_later_items.game_id = games.id AND play_later_items.user_id = ? AND play_later_items.deleted_at IS NULL", userID)
			countQuery = countQuery.Joins("JOIN play_later_items ON play_later_items.game_id = games.id AND play_later_items.user_id = ? AND play_later_items.deleted_at IS NULL", userID)
		}
	}

	if needsDistinct {
		query = query.Distinct("games.*")
		countQuery = countQuery.Distinct("games.id")
	}

	// Sorting
	sortCol := in.SortBy
	if sortCol == "" {
		sortCol = in.Sort
	}
	if sortCol == "" {
		sortCol = "title"
	}
	allowedSorts := map[string]bool{"title": true, "created_at": true, "file_size": true, "rating": true, "release_date": true}
	if !allowedSorts[sortCol] {
		sortCol = "title"
	}
	sortOrder := in.SortOrder
	if sortOrder == "" {
		sortOrder = in.Order
	}
	if sortOrder != "asc" && sortOrder != "desc" {
		sortOrder = "asc"
	}
	query = query.Order(sortCol + " " + sortOrder)

	// Pagination
	page := in.Page
	if page < 1 {
		page = 1
	}
	perPage := in.PageSize
	if perPage == 0 {
		perPage = in.PerPage
	}
	if perPage < 1 || perPage > 200 {
		perPage = 50
	}

	var total int64
	countQuery.Count(&total)

	var games []db.Game
	offset := (page - 1) * perPage
	if err := query.Offset(offset).Limit(perPage).Find(&games).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch games")
	}

	return &ListGamesOutput{
		Body: PaginatedResponse[GameResponse]{
			Data:     ToGameResponses(games, h.DB, userID),
			Total:    total,
			Page:     page,
			PageSize: perPage,
		},
	}, nil
}

// HumaGetGame is the huma handler for GET /api/games/{id}.
func (h *GameHandler) HumaGetGame(ctx context.Context, in *GetGameInput) (*GetGameOutput, error) {
	var game db.Game
	if err := h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").Preload("ReleaseDates").Preload("Videos").Preload("LanguageSupports").Preload("AgeRatings").First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	userID := UserIDFromContext(ctx)
	resp := ToGameResponse(game, h.DB, userID)
	resp.BiosStatus = GetConsoleStatus(h.Storage.BiosDir, game.Console.Abbreviation)

	if game.GroupKey != "" {
		var variants []db.Game
		h.DB.Where("console_id = ? AND group_key = ? AND id != ?",
			game.ConsoleID, game.GroupKey, game.ID).
			Order("file_name ASC").
			Find(&variants)
		if len(variants) > 0 {
			resp.Variants = make([]VariantResponse, len(variants))
			for i, v := range variants {
				resp.Variants[i] = VariantResponse{
					ID:                 strconv.FormatUint(uint64(v.ID), 10),
					Title:              v.Title,
					FileName:           v.FileName,
					Region:             v.Region,
					Revision:           v.Revision,
					Tags:               v.Tags,
					IsPreRelease:       v.IsPreRelease,
					FileSize:           v.FileSize,
					VerificationStatus: v.VerificationStatus,
				}
			}
		}
		resp.VariantCount = len(variants) + 1
	}

	if game.ParentGameID != nil {
		var parent db.Game
		if err := h.DB.Select("id", "title", "cover_url").First(&parent, *game.ParentGameID).Error; err == nil {
			resp.ParentGame = &ParentGameResponse{
				ID:       strconv.FormatUint(uint64(parent.ID), 10),
				Title:    parent.Title,
				CoverURL: resolveImageURL(parent.CoverURL),
			}
		}
	}

	var romHacks []db.Game
	h.DB.Select("id", "title", "cover_url").
		Where("parent_game_id = ?", game.ID).
		Order("title ASC").
		Find(&romHacks)
	if len(romHacks) > 0 {
		resp.RomHacks = make([]RomHackGameResponse, len(romHacks))
		for i, rh := range romHacks {
			resp.RomHacks[i] = RomHackGameResponse{
				ID:       strconv.FormatUint(uint64(rh.ID), 10),
				Title:    rh.Title,
				CoverURL: resolveImageURL(rh.CoverURL),
			}
		}
	}

	return &GetGameOutput{Body: resp}, nil
}

// HumaGetGameStats is the huma handler for GET /api/games/{id}/stats.
func (h *GameHandler) HumaGetGameStats(_ context.Context, in *GetGameInput) (*GetGameStatsOutput, error) {
	var game db.Game
	if err := h.DB.First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	var stats struct {
		TotalPlayers  int64
		TotalPlayTime int64
	}
	if err := h.DB.Model(&db.PlayHistory{}).
		Where("game_id = ?", game.ID).
		Select("COUNT(DISTINCT user_id) as total_players, COALESCE(SUM(play_time), 0) as total_play_time").
		Scan(&stats).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch game stats")
	}

	var averagePlayTime int64
	if stats.TotalPlayers > 0 {
		averagePlayTime = stats.TotalPlayTime / stats.TotalPlayers
	}

	type topPlayerRow struct {
		UserID    uint
		Username  string
		AvatarURL string
		PlayTime  int64
	}
	var rows []topPlayerRow
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("play_histories.user_id, users.username, users.avatar_url, play_histories.play_time").
		Joins("JOIN users ON users.id = play_histories.user_id").
		Where("play_histories.game_id = ?", game.ID).
		Order("play_histories.play_time DESC").
		Limit(10).
		Scan(&rows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch top players")
	}

	topPlayers := make([]GameStatsTopPlayer, 0, len(rows))
	for _, r := range rows {
		topPlayers = append(topPlayers, GameStatsTopPlayer{
			UserID:    strconv.FormatUint(uint64(r.UserID), 10),
			Username:  r.Username,
			AvatarURL: r.AvatarURL,
			PlayTime:  r.PlayTime,
		})
	}

	return &GetGameStatsOutput{Body: GameStatsResponse{
		TotalPlayers:    stats.TotalPlayers,
		TotalPlayTime:   stats.TotalPlayTime,
		AveragePlayTime: averagePlayTime,
		TopPlayers:      topPlayers,
	}}, nil
}

// HumaGetGameCheats is the huma handler for GET /api/games/{id}/cheats.
func (h *GameHandler) HumaGetGameCheats(_ context.Context, in *GetGameInput) (*GetGameCheatsOutput, error) {
	var game db.Game
	if err := h.DB.First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}
	var cheats []db.CheatCode
	if err := h.DB.Where("game_id = ?", game.ID).Order("cheat_index ASC").Find(&cheats).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch cheats")
	}
	resp := make([]GameCheatResponse, len(cheats))
	for i, ch := range cheats {
		resp[i] = GameCheatResponse{
			ID:          ch.ID,
			Index:       ch.CheatIndex,
			Description: ch.Description,
			Code:        ch.Code,
		}
	}
	return &GetGameCheatsOutput{Body: resp}, nil
}

// HumaGetRecommendedCore is the huma handler for GET /api/games/{id}/core.
// Returns the full db.Core struct as JSON. A missing Core row indicates a seed
// data gap on the server (every console's DefaultCore should be in SeedCores);
// we return 404 rather than a half-populated fallback so the gap is visible.
func (h *GameHandler) HumaGetRecommendedCore(_ context.Context, in *GetGameInput) (*GetRecommendedCoreOutput, error) {
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	coreName := game.CoreOverride
	if coreName == "" {
		coreName = game.Console.DefaultCore
	}

	var core db.Core
	if err := h.DB.Where("name = ?", coreName).First(&core).Error; err != nil {
		return nil, huma.Error404NotFound("no Core record for '" + coreName + "'; server is missing seed data")
	}

	return &GetRecommendedCoreOutput{Body: core}, nil
}

// HumaScrapeIfNeeded is the huma handler for POST /api/games/{id}/scrape-if-needed.
// The raw gin handler returns 200 when already scraped and 202 otherwise; we
// replicate that using huma's dynamic-status pattern (the Status int field
// overrides the default status per call).
func (h *GameHandler) HumaScrapeIfNeeded(_ context.Context, in *ScrapeIfNeededInput) (*ScrapeIfNeededOutput, error) {
	var game db.Game
	if err := h.DB.First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	if game.ScrapeAttempts > 0 {
		return &ScrapeIfNeededOutput{
			Status: http.StatusOK,
			Body:   StatusMessageResponse{Status: "already_scraped"},
		}, nil
	}

	if apiKey := steamGridDBAPIKey(h.DB); apiKey != "" {
		h.Scraper.ConfigureSteamGridDB(apiKey)
	}

	if queued, _ := h.Scraper.Queue.IsGameQueued(game.ID); queued {
		return &ScrapeIfNeededOutput{
			Status: http.StatusAccepted,
			Body:   StatusMessageResponse{Status: "already_queued"},
		}, nil
	}

	activeJob, _ := h.Scraper.Queue.GetActiveJob()
	var jobID *uint
	if activeJob != nil {
		jobID = &activeJob.ID
		h.DB.Model(&db.ScrapeJob{}).Where("id = ?", activeJob.ID).
			Update("total_items", gorm.Expr("total_items + 1"))
	}

	if err := h.Scraper.Queue.EnqueueGame(game.ID, jobID, 100); err != nil {
		slog.Warn("auto-scrape: failed to enqueue", "game", game.Title, "error", err)
		return nil, huma.Error500InternalServerError("failed to enqueue")
	}

	return &ScrapeIfNeededOutput{
		Status: http.StatusAccepted,
		Body:   StatusMessageResponse{Status: "queued"},
	}, nil
}

// HumaUpdatePlayTime is the huma handler for POST /api/games/{id}/play-time.
func (h *GameHandler) HumaUpdatePlayTime(ctx context.Context, in *UpdateGamePlayTimeInput) (*UpdateGamePlayTimeOutput, error) {
	uid := UserIDFromContext(ctx)
	if uid == 0 {
		return nil, huma.Error401Unauthorized("unauthorized")
	}

	gid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	const maxPlayTimeSeconds int64 = 100 * 365 * 24 * 3600

	seconds := in.Body.Seconds
	if seconds < 0 || seconds > 86400 {
		return nil, huma.Error400BadRequest("invalid request: seconds must be between 0 and 86400")
	}
	playedAt := time.Now()
	if in.Body.PlayedAt != nil {
		playedAt = *in.Body.PlayedAt
	}
	clientReportID := ""
	if in.Body.ClientReportID != nil {
		clientReportID = *in.Body.ClientReportID
	}
	updatePresence := true
	if in.Body.UpdatePresence != nil {
		updatePresence = *in.Body.UpdatePresence
	}

	var ph db.PlayHistory
	applied := false
	if err := h.DB.Transaction(func(tx *gorm.DB) error {
		if clientReportID != "" {
			receipt := db.PlayTimeReportReceipt{
				UserID:         uid,
				ClientReportID: clientReportID,
				GameID:         uint(gid),
				PlayedAt:       playedAt,
				Seconds:        seconds,
			}
			result := tx.Clauses(clause.OnConflict{DoNothing: true}).Create(&receipt)
			if result.Error != nil {
				return huma.Error500InternalServerError("failed to record play-time report receipt")
			}
			if result.RowsAffected == 0 {
				if err := tx.Where("user_id = ? AND game_id = ?", uid, gid).First(&ph).Error; err != nil {
					return huma.Error500InternalServerError("failed to load play history")
				}
				return nil
			}
		}

		result := tx.Where("user_id = ? AND game_id = ?", uid, gid).First(&ph)
		// Whether this report begins a new play session. A brand-new PlayHistory
		// row is always a new session; an existing one only when the previous
		// report was long enough ago (gap measured before LastPlayed is bumped).
		newSession := false
		if result.Error == gorm.ErrRecordNotFound {
			newSession = true
			ph = db.PlayHistory{
				UserID:     uid,
				GameID:     uint(gid),
				LastPlayed: playedAt,
				PlayTime:   seconds,
			}
			if err := tx.Create(&ph).Error; err != nil {
				return huma.Error500InternalServerError("failed to create play history")
			}
		} else if result.Error != nil {
			return huma.Error500InternalServerError("failed to load play history")
		} else {
			newSession = playedAt.Sub(ph.LastPlayed) > db.StartedPlayingSessionGap
			newTotal := ph.PlayTime + seconds
			if newTotal > maxPlayTimeSeconds {
				newTotal = maxPlayTimeSeconds
			}
			ph.PlayTime = newTotal
			if playedAt.After(ph.LastPlayed) {
				ph.LastPlayed = playedAt
			}
			if err := tx.Save(&ph).Error; err != nil {
				return huma.Error500InternalServerError("failed to update play time")
			}
		}

		RecordDailyPlayActivityAt(tx, uid, seconds, playedAt)

		// Only emit a feed event when a session actually starts — not on every
		// heartbeat, which would flood the activity feed.
		if newSession {
			CreateActivityEvent(tx, h.Hub, uid, "started_playing", uint(gid), StartedPlayingMetadata{
				Seconds: seconds,
			})
		}

		applied = true
		return nil
	}); err != nil {
		return nil, err
	}

	if applied && updatePresence && h.Hub != nil {
		h.Hub.SetUserGame(uid, uint(gid))
	}

	return &UpdateGamePlayTimeOutput{
		Body: UpdateGamePlayTimeResponse{
			PlayTime:   ph.PlayTime,
			LastPlayed: ph.LastPlayed,
		},
	}, nil
}

// HumaStopPlaying is the huma handler for DELETE /api/games/{id}/play-time.
func (h *GameHandler) HumaStopPlaying(ctx context.Context, _ *StopPlayingInput) (*StopPlayingOutput, error) {
	uid := UserIDFromContext(ctx)
	if uid == 0 {
		return nil, huma.Error401Unauthorized("unauthorized")
	}
	if h.Hub != nil {
		h.Hub.SetUserGame(uid, 0)
	}
	return &StopPlayingOutput{Body: StatusMessageResponse{Status: "cleared"}}, nil
}
