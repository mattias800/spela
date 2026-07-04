package api

import (
	"fmt"
	"log/slog"
	"math"
	"regexp"
	"sort"
	"strconv"
	"strings"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// SearchHandler handles the global search endpoint.
type SearchHandler struct {
	DB *gorm.DB
}

// --- Search response types ---

// SearchResponse is the top-level response for the global search endpoint.
type SearchResponse struct {
	Games       SearchCategoryResult[SearchGameResult]       `json:"games"`
	Consoles    SearchCategoryResult[SearchConsoleResult]    `json:"consoles"`
	Developers  SearchCategoryResult[SearchCompanyResult]    `json:"developers"`
	Publishers  SearchCategoryResult[SearchCompanyResult]    `json:"publishers"`
	Collections SearchCategoryResult[SearchCollectionResult] `json:"collections"`
	Series      SearchCategoryResult[SearchSeriesResult]     `json:"series"`
	Franchises  SearchCategoryResult[SearchFranchiseResult]  `json:"franchises"`
}

// SearchCategoryResult wraps a slice of results with a total count for a single entity type.
type SearchCategoryResult[T any] struct {
	Results []T `json:"results"`
	Total   int `json:"total"`
}

// SearchGameResult is a game entry in search results.
type SearchGameResult struct {
	ID               string                 `json:"id"`
	Title            string                 `json:"title"`
	CoverURL         string                 `json:"coverUrl"`
	ConsoleName      string                 `json:"consoleName"`
	ConsoleID        string                 `json:"consoleId"`
	Platforms        []GamePlatformResponse `json:"platforms"`
	Developer        string                 `json:"developer"`
	Genre            string                 `json:"genre"`
	CoverAspectRatio float64                `json:"coverAspectRatio"`
}

// SearchConsoleResult is a console entry in search results.
type SearchConsoleResult struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	IconURL    string `json:"iconUrl"`
	GameCount  int    `json:"gameCount"`
	ColorTheme string `json:"colorTheme"`
}

// SearchCompanyResult is a developer or publisher entry in search results.
type SearchCompanyResult struct {
	Name      string  `json:"name"`
	GameCount int     `json:"gameCount"`
	AvgRating float64 `json:"avgRating"`
}

// SearchCollectionResult is a collection entry in search results.
type SearchCollectionResult struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	GameCount int    `json:"gameCount"`
	CoverURL  string `json:"coverUrl"`
	Username  string `json:"username"`
}

// SearchSeriesResult is a series entry in search results.
type SearchSeriesResult struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	TotalGames   int    `json:"totalGames"`
	LibraryGames int    `json:"libraryGames"`
}

// SearchFranchiseResult is a franchise entry in search results.
type SearchFranchiseResult struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	TotalGames   int    `json:"totalGames"`
	LibraryGames int    `json:"libraryGames"`
}

// likeEscape is the ESCAPE clause fragment used in all LIKE queries.
const likeEscape = " ESCAPE '\\'"

const (
	searchGameMaterializationMultiplier = 20
	searchGameMaterializationMin        = 100
	searchGameMaterializationMax        = 1000
)

// consoleAbbrSafe restricts what may be inlined into the CASE expression
// used by [SearchHandler.searchGames] when applying a platform-code
// priority. Real console abbreviations are A-Z 0-9 only (NES, SNES, GBA,
// A52, GG, etc.); this regex rejects anything else as a guard against
// future bugs that might let raw user text slip into priorityAbbr.
var consoleAbbrSafe = regexp.MustCompile(`^[A-Z0-9]{1,16}$`)

// extractConsoleHint pulls a single console-abbreviation token out of a
// multi-token search query so callers can prioritise (not filter)
// matching games. Lets users type "jurassic park nes" or "nes jurassic
// park" and have NES results float to the top.
//
// Rules:
//   - One-token queries are returned unchanged. ("nes" alone keeps the
//     existing behaviour: searches every category for "nes" and finds
//     the NES console.)
//   - For multi-token queries, the first token whose lower-cased form
//     exactly matches a Console.Abbreviation (case-insensitive) is
//     stripped from the query; that abbreviation becomes the hint.
//   - If stripping leaves fewer than two characters of query, the hint
//     is dropped and the original query is returned — we don't want
//     "DS Dark Souls" → search for nothing if the user was reaching
//     for the regular search.
//   - If no token matches, the original query is returned with empty hint.
//
// Returns the (potentially-stripped) cleaned query and the canonical
// upper-case abbreviation of the matched console, or empty string if
// none.
func (h *SearchHandler) extractConsoleHint(query string) (cleanedQuery string, priorityAbbr string) {
	tokens := strings.Fields(query)
	if len(tokens) < 2 {
		return query, ""
	}

	var consoleAbbrs []string
	if err := h.DB.Model(&db.Console{}).
		Where("deleted_at IS NULL").
		Pluck("abbreviation", &consoleAbbrs).Error; err != nil {
		return query, ""
	}
	abbrSet := make(map[string]string, len(consoleAbbrs))
	for _, a := range consoleAbbrs {
		abbrSet[strings.ToLower(a)] = a
	}

	matched := ""
	remaining := make([]string, 0, len(tokens))
	for _, t := range tokens {
		if matched == "" {
			if canonical, ok := abbrSet[strings.ToLower(t)]; ok {
				matched = canonical
				continue
			}
		}
		remaining = append(remaining, t)
	}

	if matched == "" {
		return query, ""
	}

	cleaned := strings.Join(remaining, " ")
	if len(cleaned) < 2 {
		// User typed something like "nes" with stray whitespace, or
		// "nes a". Leaving them with a single-character query is
		// useless. Fall back to the original.
		return query, ""
	}
	return cleaned, matched
}

// titleLikeClauses tokenizes a query on whitespace and returns an AND-ed
// set of "<column> LIKE ?" fragments — one per token — alongside the
// matching %...%-wrapped, escaped arguments. Requiring every token to
// appear (rather than matching the whole query as one substring) lets a
// multi-word query match titles where the words are non-adjacent, e.g.
// "symphony night" → "Castlevania: Symphony of the Night". A single
// token reduces to one fragment, preserving the original behavior.
func titleLikeClauses(column, query string) (clause string, args []any) {
	tokens := strings.Fields(query)
	if len(tokens) == 0 {
		tokens = []string{query}
	}
	fragments := make([]string, len(tokens))
	args = make([]any, len(tokens))
	for i, t := range tokens {
		fragments[i] = column + " LIKE ?" + likeEscape
		args[i] = "%" + escapeLikePattern(t) + "%"
	}
	return strings.Join(fragments, " AND "), args
}

// searchGames searches for games matching the query, excluding soft-deleted entries.
//
// The query is tokenized on whitespace and every token must appear in
// the title (see [titleLikeClauses]), so multi-word queries match titles
// whose words are non-adjacent.
//
// When priorityAbbr is non-empty, games on that console float to the top
// of the result list without filtering out other consoles' results.
func (h *SearchHandler) searchGames(gameQuery string, limit int, priorityAbbr string, userID uint) SearchCategoryResult[SearchGameResult] {
	titleClause, titleArgs := titleLikeClauses("games.title", gameQuery)
	likeClause := "(" + titleClause + ") AND games.deleted_at IS NULL"

	var rawTotal int64
	if err := h.DB.Model(&db.Game{}).
		Joins("JOIN consoles ON consoles.id = games.console_id").
		Where(likeClause, titleArgs...).
		Count(&rawTotal).Error; err != nil {
		slog.Error("search: failed to count games", "error", err)
	}

	var games []db.Game
	q := h.DB.
		Model(&db.Game{}).
		Select("games.*").
		Preload("Console").
		Joins("JOIN consoles ON consoles.id = games.console_id").
		Where(likeClause, titleArgs...)

	// Priority-console hint: matching console first, then alphabetical
	// within each bucket. Implemented via a CASE expression on the
	// abbreviation column so SQLite can short-circuit the comparison
	// per row without an extra subquery.
	//
	// priorityAbbr is sourced from extractConsoleHint, which only
	// returns values it found in the consoles table — never user
	// input — so inlining it into the SQL string is safe. Belt-and-
	// braces: a regex sanity check rejects anything that isn't the
	// alphanumeric / underscore form abbreviations actually use, so a
	// future bug that lets user text leak in can't become injection.
	// (Parameter binding via gorm.Expr / gorm.Order doesn't survive
	// — GORM's Order() falls through to fmt.Sprintf and drops the
	// Vars.)
	if priorityAbbr != "" && consoleAbbrSafe.MatchString(priorityAbbr) {
		q = q.Order(fmt.Sprintf(
			"CASE WHEN UPPER(consoles.abbreviation) = '%s' THEN 0 ELSE 1 END",
			strings.ToUpper(priorityAbbr),
		))
	}

	if err := q.
		Order("games.title ASC").
		Order("games.id ASC").
		Limit(searchGameMaterializationLimit(limit)).
		Find(&games).Error; err != nil {
		slog.Error("search: failed to search games", "error", err)
		return SearchCategoryResult[SearchGameResult]{Results: []SearchGameResult{}, Total: 0}
	}

	preferredByTitle := fetchPreferredTitlePlatformMap(h.DB, userID, titleKeysForGames(games))
	folded := foldSearchGamesByTitle(games, priorityAbbr, preferredByTitle)
	total := len(folded)
	if rawTotal > int64(len(games)) {
		total = int(rawTotal)
	}
	if limit > 0 && len(folded) > limit {
		folded = folded[:limit]
	}

	platformUserID := userID
	if priorityAbbr != "" {
		platformUserID = 0
	}
	platformsByGameID := loadGamePlatforms(h.DB, platformUserID, games)
	gameByID := make(map[uint]db.Game, len(games))
	for _, game := range games {
		gameByID[game.ID] = game
	}
	preferredGames := map[uint]db.Game{}
	if priorityAbbr == "" {
		preferredGames = h.loadPreferredSearchResultGames(folded, platformsByGameID, gameByID)
	}

	results := make([]SearchGameResult, len(folded))
	for i, game := range folded {
		resultGame := game
		if preferredGame, ok := preferredGames[game.ID]; ok {
			resultGame = preferredGame
		}
		results[i] = toSearchGameResult(resultGame, platformsByGameID[game.ID])
	}

	return SearchCategoryResult[SearchGameResult]{Results: results, Total: total}
}

func (h *SearchHandler) loadPreferredSearchResultGames(folded []db.Game, platformsByGameID map[uint][]GamePlatformResponse, gameByID map[uint]db.Game) map[uint]db.Game {
	result := make(map[uint]db.Game)
	missingIDs := make(map[uint]struct{})

	for _, game := range folded {
		preferredID, ok := preferredPlatformGameID(platformsByGameID[game.ID])
		if !ok || preferredID == game.ID {
			continue
		}
		if preferredGame, ok := gameByID[preferredID]; ok {
			result[game.ID] = preferredGame
			continue
		}
		missingIDs[preferredID] = struct{}{}
	}
	if len(missingIDs) == 0 {
		return result
	}

	ids := make([]uint, 0, len(missingIDs))
	for id := range missingIDs {
		ids = append(ids, id)
	}
	sort.Slice(ids, func(i, j int) bool { return ids[i] < ids[j] })

	var loaded []db.Game
	h.DB.Preload("Console").Where("id IN ?", ids).Find(&loaded)
	for _, game := range loaded {
		gameByID[game.ID] = game
	}

	for _, game := range folded {
		preferredID, ok := preferredPlatformGameID(platformsByGameID[game.ID])
		if !ok || preferredID == game.ID {
			continue
		}
		if preferredGame, ok := gameByID[preferredID]; ok {
			result[game.ID] = preferredGame
		}
	}
	return result
}

func preferredPlatformGameID(platforms []GamePlatformResponse) (uint, bool) {
	for _, platform := range platforms {
		if !platform.IsPreferred {
			continue
		}
		id, err := strconv.ParseUint(platform.GameID, 10, 64)
		if err != nil {
			return 0, false
		}
		return uint(id), true
	}
	return 0, false
}

func searchGameMaterializationLimit(limit int) int {
	if limit < 1 {
		limit = 5
	}
	n := limit * searchGameMaterializationMultiplier
	if n < searchGameMaterializationMin {
		return searchGameMaterializationMin
	}
	if n > searchGameMaterializationMax {
		return searchGameMaterializationMax
	}
	return n
}

func foldSearchGamesByTitle(games []db.Game, priorityAbbr string, preferredByTitle map[string]uint) []db.Game {
	type group struct {
		key   string
		games []db.Game
	}

	groups := make(map[string]*group, len(games))
	orderedKeys := make([]string, 0, len(games))
	for _, game := range games {
		key := titleDedupeKey(game)
		if existing, ok := groups[key]; ok {
			existing.games = append(existing.games, game)
			continue
		}
		groups[key] = &group{key: key, games: []db.Game{game}}
		orderedKeys = append(orderedKeys, key)
	}

	folded := make([]db.Game, 0, len(orderedKeys))
	for _, key := range orderedKeys {
		grp := groups[key]
		folded = append(folded, pickSearchResultForTitle(grp.key, grp.games, priorityAbbr, preferredByTitle))
	}
	return folded
}

func pickSearchResultForTitle(key string, games []db.Game, priorityAbbr string, preferredByTitle map[string]uint) db.Game {
	if priorityAbbr != "" {
		for _, game := range games {
			if strings.EqualFold(game.Console.Abbreviation, priorityAbbr) {
				return game
			}
		}
	}
	return pickWinnerForTitle(key, games, titleWinnerHints{preferredByTitle: preferredByTitle})
}

func toSearchGameResult(game db.Game, platforms []GamePlatformResponse) SearchGameResult {
	if len(platforms) == 0 {
		platforms = []GamePlatformResponse{toGamePlatformResponse(game, true)}
	}

	consoleAbbr := game.Console.Abbreviation
	coverURL := game.CoverURL
	if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
		coverURL = "/api/images/" + coverURL
	}

	return SearchGameResult{
		ID:               strconv.FormatUint(uint64(game.ID), 10),
		Title:            game.Title,
		CoverURL:         coverURL,
		ConsoleName:      db.ConsoleName(consoleAbbr), // registry-derived (#1443)
		ConsoleID:        strings.ToLower(consoleAbbr),
		Platforms:        platforms,
		Developer:        game.Developer,
		Genre:            game.Genre,
		CoverAspectRatio: parseAspectRatio(db.ConsoleCoverAspect(consoleAbbr)), // registry-derived (#1443)
	}
}

// searchConsoles searches consoles by display name, returning only those
// with at least one game. Console names live in the code registry now
// (#1443), so the SQL pulls consoles-with-games and the name match + sort
// happen in Go over the ~75-entry catalog.
func (h *SearchHandler) searchConsoles(query string, limit int) SearchCategoryResult[SearchConsoleResult] {
	type consoleRow struct {
		ID           uint
		Abbreviation string
		GameCount    int
	}

	var rows []consoleRow
	if err := h.DB.
		Table("consoles").
		Select("consoles.id, consoles.abbreviation, COUNT(games.id) as game_count").
		Joins("JOIN games ON games.console_id = consoles.id AND games.deleted_at IS NULL").
		Group("consoles.id").
		Having("game_count > 0").
		Scan(&rows).Error; err != nil {
		slog.Error("search: failed to search consoles", "error", err)
		return SearchCategoryResult[SearchConsoleResult]{Results: []SearchConsoleResult{}, Total: 0}
	}

	needle := strings.ToLower(query)
	type match struct {
		row  consoleRow
		name string
	}
	var matches []match
	for _, r := range rows {
		name := db.ConsoleName(r.Abbreviation)
		if strings.Contains(strings.ToLower(name), needle) {
			matches = append(matches, match{row: r, name: name})
		}
	}
	sort.SliceStable(matches, func(i, j int) bool { return matches[i].name < matches[j].name })

	total := len(matches)
	if limit > 0 && len(matches) > limit {
		matches = matches[:limit]
	}

	results := make([]SearchConsoleResult, len(matches))
	for i, m := range matches {
		abbr := strings.ToLower(m.row.Abbreviation)
		results[i] = SearchConsoleResult{
			ID:         abbr,
			Name:       m.name,
			IconURL:    consoleIconURL(abbr),
			GameCount:  m.row.GameCount,
			ColorTheme: db.ConsoleColorTheme(m.row.Abbreviation), // registry-derived (#1443)
		}
	}

	return SearchCategoryResult[SearchConsoleResult]{Results: results, Total: total}
}

// searchDevelopers searches for developers matching the query.
func (h *SearchHandler) searchDevelopers(likePattern string, limit int) SearchCategoryResult[SearchCompanyResult] {
	type devRow struct {
		Developer string
		GameCount int
		AvgRating float64
	}

	likeClause := "developer LIKE ?" + likeEscape + " AND developer != '' AND deleted_at IS NULL"

	var total int64
	h.DB.Model(&db.Game{}).
		Where(likeClause, likePattern).
		Select("COUNT(DISTINCT developer)").
		Scan(&total)

	var rows []devRow
	if err := h.DB.
		Table("games").
		Select("developer, COUNT(*) as game_count, AVG(CASE WHEN "+effectiveRating+" > 0 THEN "+effectiveRating+" ELSE NULL END) as avg_rating").
		Where(likeClause, likePattern).
		Group("developer").
		Order("game_count DESC").
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("search: failed to search developers", "error", err)
		return SearchCategoryResult[SearchCompanyResult]{Results: []SearchCompanyResult{}, Total: 0}
	}

	results := make([]SearchCompanyResult, len(rows))
	for i, r := range rows {
		avgRating := 0.0
		if r.AvgRating > 0 {
			avgRating = math.Round(r.AvgRating*10) / 10
		}
		results[i] = SearchCompanyResult{
			Name:      r.Developer,
			GameCount: r.GameCount,
			AvgRating: avgRating,
		}
	}

	return SearchCategoryResult[SearchCompanyResult]{Results: results, Total: int(total)}
}

// searchPublishers searches for publishers matching the query.
func (h *SearchHandler) searchPublishers(likePattern string, limit int) SearchCategoryResult[SearchCompanyResult] {
	type pubRow struct {
		Publisher string
		GameCount int
		AvgRating float64
	}

	likeClause := "publisher LIKE ?" + likeEscape + " AND publisher != '' AND deleted_at IS NULL"

	var total int64
	h.DB.Model(&db.Game{}).
		Where(likeClause, likePattern).
		Select("COUNT(DISTINCT publisher)").
		Scan(&total)

	var rows []pubRow
	if err := h.DB.
		Table("games").
		Select("publisher, COUNT(*) as game_count, AVG(CASE WHEN "+effectiveRating+" > 0 THEN "+effectiveRating+" ELSE NULL END) as avg_rating").
		Where(likeClause, likePattern).
		Group("publisher").
		Order("game_count DESC").
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("search: failed to search publishers", "error", err)
		return SearchCategoryResult[SearchCompanyResult]{Results: []SearchCompanyResult{}, Total: 0}
	}

	results := make([]SearchCompanyResult, len(rows))
	for i, r := range rows {
		avgRating := 0.0
		if r.AvgRating > 0 {
			avgRating = math.Round(r.AvgRating*10) / 10
		}
		results[i] = SearchCompanyResult{
			Name:      r.Publisher,
			GameCount: r.GameCount,
			AvgRating: avgRating,
		}
	}

	return SearchCategoryResult[SearchCompanyResult]{Results: results, Total: int(total)}
}

// searchCollections searches for collections matching the query.
// Returns public collections + the authenticated user's own collections.
func (h *SearchHandler) searchCollections(likePattern string, limit int, userID uint) SearchCategoryResult[SearchCollectionResult] {
	type colRow struct {
		ID        uint
		Name      string
		UserID    uint
		Username  string
		GameCount int
	}

	likeClause := "game_collections.name LIKE ?" + likeEscape + " AND game_collections.deleted_at IS NULL"

	// Build the visibility-scoped query
	scopedQuery := func(tx *gorm.DB) *gorm.DB {
		q := tx.Table("game_collections").
			Joins("JOIN users ON users.id = game_collections.user_id").
			Where(likeClause, likePattern)
		if userID > 0 {
			q = q.Where("(game_collections.is_public = 1 OR game_collections.user_id = ?)", userID)
		} else {
			q = q.Where("game_collections.is_public = 1")
		}
		return q
	}

	var total int64
	scopedQuery(h.DB).Count(&total)

	var rows []colRow
	if err := scopedQuery(h.DB).
		Select(`game_collections.id, game_collections.name, game_collections.user_id,
			users.username,
			(SELECT COUNT(*) FROM collection_items WHERE collection_items.collection_id = game_collections.id) as game_count`).
		Order("game_collections.name ASC").
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("search: failed to search collections", "error", err)
		return SearchCategoryResult[SearchCollectionResult]{Results: []SearchCollectionResult{}, Total: 0}
	}

	results := make([]SearchCollectionResult, len(rows))
	for i, r := range rows {
		// Get cover URL from first game in collection
		coverURL := ""
		var firstItem db.CollectionItem
		if err := h.DB.Where("collection_id = ?", r.ID).Order("position ASC").First(&firstItem).Error; err == nil {
			var game db.Game
			if err := h.DB.Select("cover_url").First(&game, firstItem.GameID).Error; err == nil {
				coverURL = game.CoverURL
				if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
					coverURL = "/api/images/" + coverURL
				}
			}
		}

		results[i] = SearchCollectionResult{
			ID:        strconv.FormatUint(uint64(r.ID), 10),
			Name:      r.Name,
			GameCount: r.GameCount,
			CoverURL:  coverURL,
			Username:  r.Username,
		}
	}

	return SearchCategoryResult[SearchCollectionResult]{Results: results, Total: int(total)}
}

// searchSeries searches for game series matching the query.
func (h *SearchHandler) searchSeries(likePattern string, limit int) SearchCategoryResult[SearchSeriesResult] {
	type seriesRow struct {
		ID           uint
		Name         string
		TotalGames   int
		LibraryGames int
	}

	likeClause := "game_series.name LIKE ?" + likeEscape

	// Build the base query
	baseQuery := func(tx *gorm.DB) *gorm.DB {
		return tx.
			Table("game_series").
			Joins("JOIN game_series_entries ON game_series_entries.series_id = game_series.id").
			Joins("LEFT JOIN games ON games.id = game_series_entries.game_id").
			Where(likeClause, likePattern).
			Group("game_series.id").
			Having("COUNT(CASE WHEN games.id IS NOT NULL AND games.deleted_at IS NULL THEN 1 END) > 0")
	}

	// Count total
	var countRows []seriesRow
	baseQuery(h.DB).Select("game_series.id").Scan(&countRows)
	total := int64(len(countRows))

	var rows []seriesRow
	if err := h.DB.
		Table("game_series").
		Select(`game_series.id, game_series.name,
			COUNT(game_series_entries.id) as total_games,
			COUNT(CASE WHEN games.id IS NOT NULL AND games.deleted_at IS NULL THEN 1 END) as library_games`).
		Joins("JOIN game_series_entries ON game_series_entries.series_id = game_series.id").
		Joins("LEFT JOIN games ON games.id = game_series_entries.game_id").
		Where(likeClause, likePattern).
		Group("game_series.id").
		Having("library_games > 0").
		Order("library_games DESC").
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("search: failed to search series", "error", err)
		return SearchCategoryResult[SearchSeriesResult]{Results: []SearchSeriesResult{}, Total: 0}
	}

	results := make([]SearchSeriesResult, len(rows))
	for i, r := range rows {
		results[i] = SearchSeriesResult{
			ID:           strconv.FormatUint(uint64(r.ID), 10),
			Name:         r.Name,
			TotalGames:   r.TotalGames,
			LibraryGames: r.LibraryGames,
		}
	}

	return SearchCategoryResult[SearchSeriesResult]{Results: results, Total: int(total)}
}

// searchFranchises searches for game franchises matching the query.
func (h *SearchHandler) searchFranchises(likePattern string, limit int) SearchCategoryResult[SearchFranchiseResult] {
	type franchiseRow struct {
		ID           uint
		Name         string
		TotalGames   int
		LibraryGames int
	}

	likeClause := "game_franchise_groups.name LIKE ?" + likeEscape

	// Count total
	var countRows []franchiseRow
	h.DB.
		Table("game_franchise_groups").
		Select("game_franchise_groups.id").
		Joins("JOIN game_franchise_entries ON game_franchise_entries.franchise_group_id = game_franchise_groups.id").
		Joins("LEFT JOIN games ON games.id = game_franchise_entries.game_id").
		Where(likeClause, likePattern).
		Group("game_franchise_groups.id").
		Having("COUNT(CASE WHEN games.id IS NOT NULL AND games.deleted_at IS NULL THEN 1 END) > 0").
		Scan(&countRows)
	total := int64(len(countRows))

	var rows []franchiseRow
	if err := h.DB.
		Table("game_franchise_groups").
		Select(`game_franchise_groups.id, game_franchise_groups.name,
			COUNT(game_franchise_entries.id) as total_games,
			COUNT(CASE WHEN games.id IS NOT NULL AND games.deleted_at IS NULL THEN 1 END) as library_games`).
		Joins("JOIN game_franchise_entries ON game_franchise_entries.franchise_group_id = game_franchise_groups.id").
		Joins("LEFT JOIN games ON games.id = game_franchise_entries.game_id").
		Where(likeClause, likePattern).
		Group("game_franchise_groups.id").
		Having("library_games > 0").
		Order("library_games DESC").
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("search: failed to search franchises", "error", err)
		return SearchCategoryResult[SearchFranchiseResult]{Results: []SearchFranchiseResult{}, Total: 0}
	}

	results := make([]SearchFranchiseResult, len(rows))
	for i, r := range rows {
		results[i] = SearchFranchiseResult{
			ID:           strconv.FormatUint(uint64(r.ID), 10),
			Name:         r.Name,
			TotalGames:   r.TotalGames,
			LibraryGames: r.LibraryGames,
		}
	}

	return SearchCategoryResult[SearchFranchiseResult]{Results: results, Total: int(total)}
}
