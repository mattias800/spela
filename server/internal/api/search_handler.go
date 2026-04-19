package api

import (
	"log/slog"
	"math"
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
	ID               string  `json:"id"`
	Title            string  `json:"title"`
	CoverURL         string  `json:"coverUrl"`
	ConsoleName      string  `json:"consoleName"`
	ConsoleID        string  `json:"consoleId"`
	Developer        string  `json:"developer"`
	Genre            string  `json:"genre"`
	CoverAspectRatio float64 `json:"coverAspectRatio"`
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

// searchGames searches for games matching the query, excluding soft-deleted entries.
func (h *SearchHandler) searchGames(likePattern string, limit int) SearchCategoryResult[SearchGameResult] {
	type gameRow struct {
		ID          uint
		Title       string
		CoverURL    string
		ConsoleName string
		ConsoleAbbr string
		Developer   string
		Genre       string
		CoverAspect string
	}

	likeClause := "games.title LIKE ?" + likeEscape + " AND games.deleted_at IS NULL"

	var total int64
	h.DB.Model(&db.Game{}).
		Joins("JOIN consoles ON consoles.id = games.console_id").
		Where(likeClause, likePattern).
		Count(&total)

	var rows []gameRow
	if err := h.DB.
		Table("games").
		Select("games.id, games.title, games.cover_url, consoles.name as console_name, consoles.abbreviation as console_abbr, games.developer, games.genre, consoles.cover_aspect").
		Joins("JOIN consoles ON consoles.id = games.console_id").
		Where(likeClause, likePattern).
		Order("games.title ASC").
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("search: failed to search games", "error", err)
		return SearchCategoryResult[SearchGameResult]{Results: []SearchGameResult{}, Total: 0}
	}

	results := make([]SearchGameResult, len(rows))
	for i, r := range rows {
		coverURL := r.CoverURL
		if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
			coverURL = "/api/images/" + coverURL
		}
		results[i] = SearchGameResult{
			ID:               strconv.FormatUint(uint64(r.ID), 10),
			Title:            r.Title,
			CoverURL:         coverURL,
			ConsoleName:      r.ConsoleName,
			ConsoleID:        strings.ToLower(r.ConsoleAbbr),
			Developer:        r.Developer,
			Genre:            r.Genre,
			CoverAspectRatio: parseAspectRatio(r.CoverAspect),
		}
	}

	return SearchCategoryResult[SearchGameResult]{Results: results, Total: int(total)}
}

// searchConsoles searches for consoles matching the query, only returning those with at least 1 game.
func (h *SearchHandler) searchConsoles(likePattern string, limit int) SearchCategoryResult[SearchConsoleResult] {
	type consoleRow struct {
		ID           uint
		Name         string
		Abbreviation string
		ColorTheme   string
		GameCount    int
	}

	likeClause := "consoles.name LIKE ?" + likeEscape

	// Build the base query for consoles with games
	baseQuery := func() *gorm.DB {
		return h.DB.
			Table("consoles").
			Joins("JOIN games ON games.console_id = consoles.id AND games.deleted_at IS NULL").
			Where(likeClause, likePattern).
			Group("consoles.id").
			Having("COUNT(games.id) > 0")
	}

	// Count total matching consoles
	var total int64
	row := baseQuery().Select("COUNT(*) as total").Row()
	if err := row.Scan(&total); err != nil {
		// Fallback: count via subquery approach
		total = 0
	}

	var rows []consoleRow
	if err := h.DB.
		Table("consoles").
		Select("consoles.id, consoles.name, consoles.abbreviation, consoles.color_theme, COUNT(games.id) as game_count").
		Joins("JOIN games ON games.console_id = consoles.id AND games.deleted_at IS NULL").
		Where(likeClause, likePattern).
		Group("consoles.id").
		Having("game_count > 0").
		Order("consoles.name ASC").
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("search: failed to search consoles", "error", err)
		return SearchCategoryResult[SearchConsoleResult]{Results: []SearchConsoleResult{}, Total: 0}
	}

	// If the Row() scan above failed, fall back to result length (imprecise but functional)
	if total == 0 && len(rows) > 0 {
		total = int64(len(rows))
	}

	results := make([]SearchConsoleResult, len(rows))
	for i, r := range rows {
		abbr := strings.ToLower(r.Abbreviation)
		results[i] = SearchConsoleResult{
			ID:         abbr,
			Name:       r.Name,
			IconURL:    "/api/consoles/" + abbr + "/icon",
			GameCount:  r.GameCount,
			ColorTheme: r.ColorTheme,
		}
	}

	return SearchCategoryResult[SearchConsoleResult]{Results: results, Total: int(total)}
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
