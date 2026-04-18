package api

import (
	"log/slog"
	"math"

	"github.com/spela/server/internal/db"
)

// effectiveRating is a SQL expression that picks the best available IGDB rating.
// IGDB stores three rating types:
//   - rating (aggregated_rating): critics' aggregated score — sparse for retro games
//   - total_rating: combined critic + user score — much more populated
//   - igdb_user_rating: user-only score — fallback when neither of the above exists
//
// The DB mirrors IGDB 1:1; this expression does the fallback at read time.
const effectiveRating = "COALESCE(NULLIF(rating, 0), NULLIF(total_rating, 0), NULLIF(igdb_user_rating, 0), 0)"

// effectiveRatingPrefixed is the same but with "games." table prefix for JOINed queries.
const effectiveRatingPrefixed = "COALESCE(NULLIF(games.rating, 0), NULLIF(games.total_rating, 0), NULLIF(games.igdb_user_rating, 0), 0)"

// --- Phase 8: Console Showcase Pages ---
//
// The gin handlers GetConsoleShowcase and GetConsoleHighlights have been
// migrated to huma — see huma_explore_console.go. Only the wire-format types
// and the shared helper functions remain here because the huma handlers still
// depend on them.

// GenreCount holds a genre name and the number of games in that genre.
type GenreCount struct {
	Name      string `json:"name"`
	GameCount int    `json:"gameCount"`
}

// ConsoleShowcaseResponse is the API response for a console showcase page.
type ConsoleShowcaseResponse struct {
	Console        ConsoleResponse    `json:"console"`
	Essentials     []GameResponse     `json:"essentials"`
	HiddenGems     []GameResponse     `json:"hiddenGems"`
	GenreBreakdown []GenreCount       `json:"genreBreakdown"`
	TopDevelopers  []DeveloperSummary `json:"topDevelopers"`
	RecentlyPlayed []GameResponse     `json:"recentlyPlayed"`
	RecentlyAdded  []GameResponse     `json:"recentlyAdded"`
}

// ConsoleHighlight is a compact summary of a console for the explore page quick-jump row.
//
// TopGame is optional: a console may have games without any one of them being
// chosen as a "top" pick (e.g. no game has hero art yet). The pointer plus
// `omitempty` tag make huma emit the field as an optional, nullable reference
// in the OpenAPI spec rather than marking it required (which would crash the
// Kotlin client whenever the server returned null).
type ConsoleHighlight struct {
	ID         string        `json:"id"`
	Name       string        `json:"name"`
	ColorTheme string        `json:"colorTheme"`
	IconURL    string        `json:"iconUrl"`
	LogoURL    string        `json:"logoUrl"`
	GameCount  int           `json:"gameCount"`
	TopGame    *GameResponse `json:"topGame,omitempty"`
}

// ConsoleHighlightsResponse is the API response for the console highlights endpoint.
type ConsoleHighlightsResponse struct {
	Consoles []ConsoleHighlight `json:"consoles"`
}

// buildConsoleHiddenGems returns games with high rating but low play time for a console.
func (h *ExploreHandler) buildConsoleHiddenGems(consoleID uint, excludeIDs []uint) []db.Game {
	// Calculate play-time threshold (25th percentile) for this console
	type thresholdRow struct {
		TotalPlayTime int64
	}
	var playTimes []thresholdRow
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("COALESCE(SUM(play_histories.play_time), 0) as total_play_time").
		Joins("JOIN games ON games.id = play_histories.game_id").
		Where("games.console_id = ? AND games.is_primary = true AND games.deleted_at IS NULL", consoleID).
		Group("play_histories.game_id").
		Having("total_play_time > 0").
		Order("total_play_time ASC").
		Scan(&playTimes).Error; err != nil {
		slog.Error("failed to calculate play time threshold for console hidden gems", "error", err)
		return nil
	}

	var threshold int64
	if len(playTimes) > 0 {
		idx := len(playTimes) / 4
		threshold = playTimes[idx].TotalPlayTime
		if threshold == 0 {
			threshold = 1
		}
	}

	var games []db.Game
	query := h.DB.Preload("Console").
		Joins("LEFT JOIN (SELECT game_id, COALESCE(SUM(play_time), 0) as total_play_time FROM play_histories GROUP BY game_id) ph ON ph.game_id = games.id").
		Where("games.console_id = ?", consoleID).
		Where(effectiveRatingPrefixed + " >= 70").
		Where("games.deleted_at IS NULL").
		Where("games.is_primary = true")

	if len(excludeIDs) > 0 {
		query = query.Where("games.id NOT IN ?", excludeIDs)
	}

	if threshold > 0 {
		query = query.Where("ph.total_play_time IS NULL OR ph.total_play_time <= ?", threshold)
	}

	if err := query.
		Order(effectiveRatingPrefixed + " DESC").
		Limit(10).
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch console hidden gems", "error", err)
		return nil
	}

	return games
}

// buildGenreBreakdown returns genre counts for games on a specific console.
func (h *ExploreHandler) buildGenreBreakdown(consoleID uint) []GenreCount {
	type genreRow struct {
		Genre     string
		GameCount int
	}
	var rows []genreRow
	if err := h.DB.
		Table("games").
		Select("genre, COUNT(*) as game_count").
		Where("console_id = ? AND is_primary = true AND deleted_at IS NULL AND genre != ''", consoleID).
		Group("genre").
		Order("game_count DESC").
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch genre breakdown", "error", err)
		return []GenreCount{}
	}

	result := make([]GenreCount, len(rows))
	for i, r := range rows {
		result[i] = GenreCount{
			Name:      r.Genre,
			GameCount: r.GameCount,
		}
	}
	return result
}

// buildConsoleTopDevelopers returns the top 5 developers by game count for a console.
func (h *ExploreHandler) buildConsoleTopDevelopers(consoleID uint, consoleName string) []DeveloperSummary {
	type devRow struct {
		Developer string
		GameCount int
		AvgRating float64
	}

	var rows []devRow
	if err := h.DB.
		Table("games").
		Select("developer, COUNT(*) as game_count, AVG(CASE WHEN "+effectiveRating+" > 0 THEN "+effectiveRating+" ELSE NULL END) as avg_rating").
		Where("console_id = ? AND is_primary = true AND deleted_at IS NULL AND developer != ''", consoleID).
		Group("developer").
		Order("game_count DESC").
		Limit(5).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch top developers for console", "error", err)
		return []DeveloperSummary{}
	}

	developers := make([]DeveloperSummary, len(rows))
	for i, r := range rows {
		avgRating := 0.0
		if r.AvgRating > 0 {
			avgRating = math.Round(r.AvgRating*10) / 10
		}
		developers[i] = DeveloperSummary{
			Name:      r.Developer,
			GameCount: r.GameCount,
			AvgRating: avgRating,
			Consoles:  []string{consoleName},
		}
	}
	return developers
}
