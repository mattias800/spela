package scraper

import (
	"fmt"
	"log/slog"
	"strings"

	"github.com/spela/server/internal/igdb"
)

// bestIGDBMatch picks the IGDB search result whose name is most similar to the
// query (the cleaned filename). It reuses normalizeName and jaroWinkler from
// namematch.go with the same tiered scoring logic used for LibRetro matching.
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
		case strings.HasPrefix(n, normalizedQuery) || strings.HasPrefix(normalizedQuery, n):
			shorter := len(normalizedQuery)
			longer := len(n)
			if shorter > longer {
				shorter, longer = longer, shorter
			}
			score = 0.85 + 0.10*float64(shorter)/float64(longer)
			tier = "prefix"
		case strings.Contains(n, normalizedQuery) || strings.Contains(normalizedQuery, n):
			shorter := len(normalizedQuery)
			longer := len(n)
			if shorter > longer {
				shorter, longer = longer, shorter
			}
			score = 0.85 + 0.10*float64(shorter)/float64(longer)
			tier = "contains"
		default:
			score = jaroWinkler(normalizedQuery, n)
			tier = "fuzzy"
		}

		slog.Debug("IGDB match candidate", "query", normalizedQuery, "candidate", g.Name, "normalized", n, "tier", tier, "score", fmt.Sprintf("%.4f", score), "igdbId", g.ID)

		if score > bestScore {
			bestScore = score
			bestIdx = i
		}
	}

	slog.Info("IGDB match selected", "query", query, "match", games[bestIdx].Name, "score", fmt.Sprintf("%.4f", bestScore), "igdbId", games[bestIdx].ID)
	return games[bestIdx]
}
