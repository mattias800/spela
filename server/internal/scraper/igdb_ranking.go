package scraper

import (
	"fmt"
	"log/slog"
	"strings"

	"github.com/spela/server/internal/igdb"
)

// bestIGDBMatch picks the IGDB search result whose name is most similar to the
// query (the cleaned filename). It reuses normalizeName and jaroWinkler from
// namematch.go with tiered scoring logic.
//
// Scoring tiers (highest to lowest):
//
//	exact  = 1.0           (always wins, no metadata penalty)
//	suffix = 0.91–0.96     (query at end of name: "Disney's Aladdin" for "Aladdin")
//	prefix = 0.90–0.95     (query at start of name: "Aladdin 2000" for "Aladdin")
//	contains = 0.80–0.85   (query somewhere inside name)
//	fuzzy  = Jaro-Winkler  (fallback)
//
// Non-exact matches are further adjusted by metadataQuality, which penalises
// games with missing release dates, companies, cover art, or screenshots.
func bestIGDBMatch(query string, games []igdb.Game) igdb.Game {
	if len(games) == 1 {
		slog.Info("IGDB match: single result", "query", query, "match", games[0].Name, "igdbId", games[0].ID)
		return games[0]
	}

	normalizedQuery := normalizeName(query)

	bestIdx := 0
	bestScore := -1.0

	for i, g := range games {
		n := normalizeName(g.Name)

		var score float64
		var tier string
		switch {
		case n == normalizedQuery:
			score = 1.0
			tier = "exact"
		case strings.HasSuffix(n, normalizedQuery) || strings.HasSuffix(normalizedQuery, n):
			shorter := len(normalizedQuery)
			longer := len(n)
			if shorter > longer {
				shorter, longer = longer, shorter
			}
			score = 0.91 + 0.05*float64(shorter)/float64(longer)
			tier = "suffix"
		case strings.HasPrefix(n, normalizedQuery) || strings.HasPrefix(normalizedQuery, n):
			shorter := len(normalizedQuery)
			longer := len(n)
			if shorter > longer {
				shorter, longer = longer, shorter
			}
			score = 0.90 + 0.05*float64(shorter)/float64(longer)
			tier = "prefix"
		case strings.Contains(n, normalizedQuery) || strings.Contains(normalizedQuery, n):
			shorter := len(normalizedQuery)
			longer := len(n)
			if shorter > longer {
				shorter, longer = longer, shorter
			}
			score = 0.80 + 0.05*float64(shorter)/float64(longer)
			tier = "contains"
		default:
			score = jaroWinkler(normalizedQuery, n)
			tier = "fuzzy"
		}

		// Apply metadata quality as a secondary signal for non-exact matches.
		// Exact matches always score 1.0 regardless of metadata completeness.
		if tier != "exact" {
			mq := metadataQuality(g)
			score *= mq
			slog.Debug("IGDB match candidate", "query", normalizedQuery, "candidate", g.Name, "normalized", n, "tier", tier, "nameScore", fmt.Sprintf("%.4f", score/mq), "metadataQuality", fmt.Sprintf("%.4f", mq), "finalScore", fmt.Sprintf("%.4f", score), "igdbId", g.ID)
		} else {
			slog.Debug("IGDB match candidate", "query", normalizedQuery, "candidate", g.Name, "normalized", n, "tier", tier, "score", fmt.Sprintf("%.4f", score), "igdbId", g.ID)
		}

		if isBetterIGDBMatch(score, g, bestScore, games[bestIdx]) {
			bestScore = score
			bestIdx = i
		}
	}

	slog.Info("IGDB match selected", "query", query, "match", games[bestIdx].Name, "score", fmt.Sprintf("%.4f", bestScore), "igdbId", games[bestIdx].ID)
	return games[bestIdx]
}

// metadataQuality returns a multiplier (0.0–1.0) reflecting how well-documented
// a game is in IGDB. Games with missing metadata are penalised, as sparse entries
// are more likely to be bootlegs, unreleased games, or duplicates.
func metadataQuality(g igdb.Game) float64 {
	q := 1.0
	if g.FirstReleaseDate == 0 {
		q *= 0.92
	}
	if len(g.InvolvedCompanies) == 0 {
		q *= 0.95
	}
	if g.Cover == nil {
		q *= 0.95
	}
	if len(g.Screenshots) == 0 {
		q *= 0.95
	}
	return q
}

// isBetterIGDBMatch returns true if candidate (with candidateScore) is a better
// match than current best (with bestScore). When scores are equal, it applies
// tiebreakers: prefer released games, then earlier release date, then shorter name.
func isBetterIGDBMatch(candidateScore float64, candidate igdb.Game, bestScore float64, best igdb.Game) bool {
	if candidateScore > bestScore {
		return true
	}
	if candidateScore < bestScore {
		return false
	}

	// Tiebreaker 1: prefer released games (non-zero FirstReleaseDate) over unreleased
	candidateReleased := candidate.FirstReleaseDate > 0
	bestReleased := best.FirstReleaseDate > 0
	if candidateReleased && !bestReleased {
		return true
	}
	if !candidateReleased && bestReleased {
		return false
	}

	// Tiebreaker 2: among released games, prefer earlier release (likely the original)
	if candidateReleased && bestReleased && candidate.FirstReleaseDate < best.FirstReleaseDate {
		return true
	}
	if candidateReleased && bestReleased && candidate.FirstReleaseDate > best.FirstReleaseDate {
		return false
	}

	// Tiebreaker 3: prefer shorter name (less likely to be a sequel/subtitle variant)
	if len(candidate.Name) < len(best.Name) {
		return true
	}

	return false
}
