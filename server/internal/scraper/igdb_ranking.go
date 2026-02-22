package scraper

import (
	"strings"

	"github.com/spela/server/internal/igdb"
)

// bestIGDBMatch picks the IGDB search result whose name is most similar to the
// query (the cleaned filename). It reuses normalizeName and jaroWinkler from
// namematch.go with the same tiered scoring logic used for LibRetro matching.
func bestIGDBMatch(query string, games []igdb.Game) igdb.Game {
	if len(games) == 1 {
		return games[0]
	}

	normalizedQuery := normalizeName(query)

	bestIdx := 0
	bestScore := -1.0

	for i, g := range games {
		n := normalizeName(g.Name)

		var score float64
		switch {
		case n == normalizedQuery:
			score = 1.0
		case strings.HasPrefix(n, normalizedQuery) || strings.HasPrefix(normalizedQuery, n):
			shorter := len(normalizedQuery)
			longer := len(n)
			if shorter > longer {
				shorter, longer = longer, shorter
			}
			score = 0.85 + 0.10*float64(shorter)/float64(longer)
		case strings.Contains(n, normalizedQuery) || strings.Contains(normalizedQuery, n):
			shorter := len(normalizedQuery)
			longer := len(n)
			if shorter > longer {
				shorter, longer = longer, shorter
			}
			score = 0.85 + 0.10*float64(shorter)/float64(longer)
		default:
			score = jaroWinkler(normalizedQuery, n)
		}

		if score > bestScore {
			bestScore = score
			bestIdx = i
		}
	}

	return games[bestIdx]
}
