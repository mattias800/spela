package api

import (
	"fmt"
	"regexp"
	"sort"
	"strings"

	"github.com/spela/server/internal/db"
)

// --- Cross-platform title dedupe for the For-You + players-like-you shelves
// (issue #1183 v1).
//
// IGDB models each platform release of a game as a separate entry, so our
// scraped library ends up with Street Fighter II (SNES), Street Fighter II
// (Genesis), Street Fighter II (Arcade), etc. — all distinct `Game` rows.
// Recommendation shelves built off the rating column or the user's play
// history happily list all three of them side-by-side, which wastes carousel
// slots and looks duplicated.
//
// The proper fix (v2 in the issue) is a Title / Work entity backed by IGDB's
// `parent_game` / `version_parent` links so we know which entries are
// versions of the same work. This file implements the v1 quick fix: a
// normalised title key + a deterministic tiebreak picks one game per key.

// titleEditionSuffixes are tail strings stripped from a lower-cased title
// before keying — these are platform/edition shouting-marks that genuinely
// don't change the work. Order matters: longer phrases first so "hd
// remastered" doesn't get trimmed to just "remastered" first leaving "hd".
//
// Conservative list: only suffixes that are widely-used marketing-shouts
// and never the actual title of a distinct game. Notable omissions:
//
//   - "Champion Edition" / "Turbo" / "Super" / "Hyper" → these often
//     distinguish actually-different games (Street Fighter II vs Super
//     Street Fighter II vs Hyper Fighting). Leave them.
//   - "Collection" / "Compilation" → those are genuinely distinct works
//     (e.g. Mega Man Collection contains many games). Leave them.
// titleEditionSuffixes is ordered LONGEST-FIRST. The strip loop is
// greedy and picks the first matching suffix; if a shorter phrase
// preceded a longer one that contains it, the shorter would steal
// the strip and leave residue (e.g. " anniversary edition" would
// strip out of "Game 30th Anniversary Edition", leaving "Game 30th").
var titleEditionSuffixes = []string{
	" 30th anniversary edition",
	" 25th anniversary edition",
	" 20th anniversary edition",
	" 10th anniversary edition",
	" anniversary edition",
	" definitive edition",
	" complete edition",
	" enhanced edition",
	" extended edition",
	" gold edition",
	" platinum edition",
	" director's cut",
	" directors cut",
	" hd remastered",
	" remastered hd",
	" hd remix",
	" remastered",
	" remaster",
	" hd",
}

var (
	titleParenRegex = regexp.MustCompile(`\s*\([^)]*\)|\s*\[[^\]]*\]`)
	titleSpaceRegex = regexp.MustCompile(`\s+`)
	titlePunctRegex = regexp.MustCompile(`[^\p{L}\p{N}\s]`)
)

// normalizeTitleKey returns a canonical lowercase key for grouping games
// that represent the same logical work across platforms.
//
// Pipeline:
//  1. Lower-case.
//  2. Strip every (...) and [...] group — these hold region tags,
//     revision codes, scene tags, etc. that are never part of the work
//     name. Run before suffix-strip so "Title (USA) Remastered" loses
//     the parens and then the trailing suffix.
//  3. Strip a small allow-list of well-known edition / remaster
//     suffixes (see [titleEditionSuffixes]).
//  4. Strip punctuation (keeping letters, digits, and whitespace).
//  5. Collapse all whitespace to single spaces and trim.
//
// Examples:
//
//	"Street Fighter II"                  → "street fighter ii"
//	"Street Fighter II (USA)"            → "street fighter ii"
//	"Street Fighter II [USA]"            → "street fighter ii"
//	"Final Fantasy VII Remastered"       → "final fantasy vii"
//	"Final Fantasy VII: Remake"          → "final fantasy vii remake" (different work, kept)
//	"Super Street Fighter II"            → "super street fighter ii" (different work, kept)
//
// An empty result is possible (e.g. a title that's all punctuation) — the
// caller should treat that as "ungroupable" and key by Game.ID instead.
func normalizeTitleKey(title string) string {
	s := strings.ToLower(title)
	s = titleParenRegex.ReplaceAllString(s, " ")
	// Edition-suffix strip on the post-paren result. Loop because some
	// titles get "Remastered HD Remix" — chained suffixes.
	for {
		trimmed := s
		for _, sfx := range titleEditionSuffixes {
			if strings.HasSuffix(strings.TrimRight(trimmed, " "), sfx) {
				trimmed = strings.TrimSuffix(strings.TrimRight(trimmed, " "), sfx)
			}
		}
		if trimmed == s {
			break
		}
		s = trimmed
	}
	s = titlePunctRegex.ReplaceAllString(s, "")
	s = titleSpaceRegex.ReplaceAllString(s, " ")
	return strings.TrimSpace(s)
}

// dedupeGamesByTitle returns one game per normalised title (see
// [normalizeTitleKey]). The relative order of the first occurrence of each
// title is preserved.
//
// For each group, the winner is picked by:
//  1. The user's most-played platform for this title, if [mostPlayedByTitle]
//     has an entry for the key whose game ID appears in the group.
//  2. Lower [Console.Generation] — the original-platform release tends to
//     be what users recognise as the canonical version. Generation 0 is
//     treated as "unknown" and pushed to the end.
//  3. Higher [Game.IGDBCriticsRating] — prefer the better-received version
//     when two consoles are in the same generation.
//  4. Lower [Game.ID] — stable fallback so identical inputs always pick
//     the same winner across requests.
//
// [Console] must be preloaded on every Game for tiebreak (2) to work.
func dedupeGamesByTitle(games []db.Game, mostPlayedByTitle map[string]uint) []db.Game {
	type group struct {
		key   string
		games []db.Game
	}

	groupByKey := make(map[string]*group, len(games))
	orderedKeys := make([]string, 0, len(games))

	for _, g := range games {
		key := normalizeTitleKey(g.Title)
		if key == "" {
			// Untitled / all-punctuation → key by ID so it never collides
			// with another row.
			key = fmt.Sprintf("__id_%d", g.ID)
		}
		if existing, ok := groupByKey[key]; ok {
			existing.games = append(existing.games, g)
		} else {
			groupByKey[key] = &group{key: key, games: []db.Game{g}}
			orderedKeys = append(orderedKeys, key)
		}
	}

	result := make([]db.Game, 0, len(orderedKeys))
	for _, key := range orderedKeys {
		grp := groupByKey[key]
		if len(grp.games) == 1 {
			result = append(result, grp.games[0])
			continue
		}
		result = append(result, pickWinnerForTitle(key, grp.games, mostPlayedByTitle))
	}
	return result
}

// pickWinnerForTitle applies the tiebreak rules documented on
// [dedupeGamesByTitle] to choose one game from a group sharing the same
// normalised title key.
func pickWinnerForTitle(key string, games []db.Game, mostPlayedByTitle map[string]uint) db.Game {
	if mostPlayedID, ok := mostPlayedByTitle[key]; ok {
		for _, g := range games {
			if g.ID == mostPlayedID {
				return g
			}
		}
	}

	sorted := make([]db.Game, len(games))
	copy(sorted, games)
	sort.SliceStable(sorted, func(i, j int) bool {
		gi, gj := sorted[i].Console.Generation, sorted[j].Console.Generation
		if gi != gj {
			// Generation 0 (unknown) → push to the end.
			if gi == 0 {
				return false
			}
			if gj == 0 {
				return true
			}
			return gi < gj
		}
		ri, rj := sorted[i].IGDBCriticsRating, sorted[j].IGDBCriticsRating
		if ri != rj {
			return ri > rj
		}
		return sorted[i].ID < sorted[j].ID
	})
	return sorted[0]
}

// fetchMostPlayedTitleMap returns a map from normalised title key to the
// user's most-played game ID for that title. Used as a tiebreak hint by
// [dedupeGamesByTitle] so the "user's most-played platform wins" rule
// can resolve a dedupe group when the user has played the work on more
// than one platform.
//
// Cheap single query: joins play_histories with games, ordered by
// play_time DESC, and keeps the first hit per normalised key. The map
// is request-scoped (no caching) so it always reflects current play
// history.
func (h *ExploreHandler) fetchMostPlayedTitleMap(userID uint) map[string]uint {
	if userID == 0 {
		return nil
	}
	type row struct {
		GameID   uint
		Title    string
		PlayTime int64
	}
	var rows []row
	if err := h.DB.Table("play_histories").
		Select("play_histories.game_id, games.title, play_histories.play_time").
		Joins("JOIN games ON games.id = play_histories.game_id AND games.deleted_at IS NULL").
		Where("play_histories.user_id = ? AND play_histories.play_time > 0", userID).
		Order("play_histories.play_time DESC").
		Scan(&rows).Error; err != nil {
		return nil
	}
	result := make(map[string]uint, len(rows))
	for _, r := range rows {
		key := normalizeTitleKey(r.Title)
		if key == "" {
			continue
		}
		if _, exists := result[key]; !exists {
			result[key] = r.GameID
		}
	}
	return result
}
