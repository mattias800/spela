package api

import (
	"log/slog"
	"math"
	"regexp"
	"strings"

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

// ConsoleShowcaseResponse is the API response for a console showcase page.
type ConsoleShowcaseResponse struct {
	Console        ConsoleResponse    `json:"console"`
	Essentials     []GameResponse     `json:"essentials"`
	HiddenGems     []GameResponse     `json:"hiddenGems"`
	LaunchGames    []GameResponse     `json:"launchGames"`
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

// reLaunchPunct is the punctuation class used by normalizeLaunchTitle. Kept
// narrow: we strip characters that commonly differ between ROM filenames and
// canonical titles (dots, commas, apostrophes, colons, hyphens, etc.) while
// leaving alphanumerics and spaces intact.
var reLaunchPunct = regexp.MustCompile(`[.,'":;!?\-_/\\+\(\)\[\]]+`)

// reLaunchSpaces collapses runs of whitespace to a single space.
var reLaunchSpaces = regexp.MustCompile(`\s+`)

// normalizeLaunchTitle lowercases and strips punctuation/articles so that
// seed titles like "Donkey Kong Jr. Math" and DB titles like "Donkey Kong Jr
// Math" compare equal. The normalization is deliberately simpler than the
// scraper's namematch.normalizeName — launch-game seed data is curated, so
// we don't need fuzzy matching, only forgiveness for routine punctuation
// and leading-article variance.
func normalizeLaunchTitle(title string) string {
	s := strings.ToLower(strings.TrimSpace(title))
	// Normalize ampersand to "and" before stripping punctuation so "Rock & Roll" → "rock and roll".
	s = strings.ReplaceAll(s, "&", " and ")
	s = reLaunchPunct.ReplaceAllString(s, " ")
	s = reLaunchSpaces.ReplaceAllString(s, " ")
	s = strings.TrimSpace(s)
	// Drop a leading "the " to match "The Legend of Zelda" ↔ "Legend of Zelda".
	s = strings.TrimPrefix(s, "the ")
	s = strings.TrimPrefix(s, "a ")
	s = strings.TrimPrefix(s, "an ")
	return s
}

// buildLaunchGames returns games on the given console whose title matches a
// curated launch-title entry for the console. Order follows the seed list
// (Wikipedia's canonical launch-day ordering) so early titles surface first.
// Returns nil if no curated list exists for the console.
func (h *ExploreHandler) buildLaunchGames(consoleID uint, abbr string) []db.Game {
	titles := launchGamesFor(strings.ToLower(abbr))
	if len(titles) == 0 {
		return nil
	}

	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("console_id = ? AND is_primary = true AND deleted_at IS NULL", consoleID).
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch games for launch matching", "console", abbr, "error", err)
		return nil
	}

	// Index DB games by normalized title for O(n+m) matching.
	byTitle := make(map[string]db.Game, len(games))
	for _, g := range games {
		key := normalizeLaunchTitle(g.Title)
		if _, exists := byTitle[key]; !exists {
			byTitle[key] = g
		}
	}

	matched := make([]db.Game, 0, len(titles))
	for _, seed := range titles {
		key := normalizeLaunchTitle(seed)
		if g, ok := byTitle[key]; ok {
			matched = append(matched, g)
		}
	}
	return matched
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
