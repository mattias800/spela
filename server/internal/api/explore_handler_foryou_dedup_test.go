package api

import (
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
)

func TestNormalizeTitleKey(t *testing.T) {
	tests := []struct {
		in   string
		want string
	}{
		// Identity (no transformations needed)
		{"Street Fighter II", "street fighter ii"},
		// Parenthesised region tag
		{"Street Fighter II (USA)", "street fighter ii"},
		{"Street Fighter II (Europe) (Rev A)", "street fighter ii"},
		// Bracketed region / scene tag
		{"Street Fighter II [USA]", "street fighter ii"},
		{"Street Fighter II [!]", "street fighter ii"},
		// Mixed parens + brackets
		{"Street Fighter II (USA) [!]", "street fighter ii"},
		// Trailing edition / remaster suffixes
		{"Final Fantasy VII Remastered", "final fantasy vii"},
		{"Final Fantasy VII HD Remastered", "final fantasy vii"},
		{"Game Anniversary Edition", "game"},
		{"Game 30th Anniversary Edition", "game"},
		{"Game Director's Cut", "game"},
		// Chained suffixes
		{"Game Remastered HD", "game"},
		// Genuine title variants are NOT collapsed — keep distinct works
		// (the issue explicitly calls these out).
		{"Street Fighter II: Champion Edition", "street fighter ii champion edition"},
		{"Super Street Fighter II", "super street fighter ii"},
		{"Final Fantasy VII: Remake", "final fantasy vii remake"},
		// Punctuation strip + whitespace collapse
		{"Super Mario Bros.", "super mario bros"},
		{"  Sonic   the   Hedgehog  ", "sonic the hedgehog"},
		// Edge: all-punctuation title → empty key (caller fallback)
		{"---", ""},
		// Edge: empty
		{"", ""},
		// Case folding
		{"Pac-Man", "pacman"},
	}
	for _, tt := range tests {
		t.Run(tt.in, func(t *testing.T) {
			assert.Equal(t, tt.want, normalizeTitleKey(tt.in))
		})
	}
}

func TestDedupeGamesByTitle_TiebreakOrder(t *testing.T) {
	// Three platform releases of the same title. Generations:
	//   NES = 3, SNES = 4, GBA = 6
	// Without a most-played hint, the tiebreak picks the lowest
	// generation first, then highest rating.
	games := []db.Game{
		{ID: 1, Title: "Street Fighter II", IGDBCriticsRating: 90, Console: db.Console{Generation: 4}},
		{ID: 2, Title: "Street Fighter II (USA)", IGDBCriticsRating: 85, Console: db.Console{Generation: 3}},
		{ID: 3, Title: "Street Fighter II", IGDBCriticsRating: 95, Console: db.Console{Generation: 6}},
	}
	out := dedupeGamesByTitle(games, nil)
	if assert.Len(t, out, 1) {
		// NES (gen 3) wins over SNES (gen 4) and GBA (gen 6).
		assert.Equal(t, uint(2), out[0].ID,
			"lower generation (NES, gen 3) should win the tiebreak over higher gens")
	}
}

func TestDedupeGamesByTitle_MostPlayedHintWins(t *testing.T) {
	// User has played their SNES version most — even though it's a higher
	// generation than the NES port, the most-played hint takes precedence.
	games := []db.Game{
		{ID: 1, Title: "Street Fighter II", IGDBCriticsRating: 90, Console: db.Console{Generation: 4}},
		{ID: 2, Title: "Street Fighter II (USA)", IGDBCriticsRating: 85, Console: db.Console{Generation: 3}},
	}
	hint := map[string]uint{"street fighter ii": 1}
	out := dedupeGamesByTitle(games, hint)
	if assert.Len(t, out, 1) {
		assert.Equal(t, uint(1), out[0].ID,
			"most-played hint should beat the generation rule")
	}
}

func TestDedupeGamesByTitle_RatingTiebreakWhenSameGeneration(t *testing.T) {
	// SNES + Genesis both gen 4. Higher rating wins.
	games := []db.Game{
		{ID: 1, Title: "Title", IGDBCriticsRating: 80, Console: db.Console{Generation: 4}},
		{ID: 2, Title: "Title", IGDBCriticsRating: 92, Console: db.Console{Generation: 4}},
	}
	out := dedupeGamesByTitle(games, nil)
	if assert.Len(t, out, 1) {
		assert.Equal(t, uint(2), out[0].ID, "higher rating wins within same generation")
	}
}

func TestDedupeGamesByTitle_IDTiebreakLast(t *testing.T) {
	// Same generation, same rating — lowest ID wins (stable fallback).
	games := []db.Game{
		{ID: 7, Title: "Title", IGDBCriticsRating: 80, Console: db.Console{Generation: 4}},
		{ID: 3, Title: "Title", IGDBCriticsRating: 80, Console: db.Console{Generation: 4}},
		{ID: 9, Title: "Title", IGDBCriticsRating: 80, Console: db.Console{Generation: 4}},
	}
	out := dedupeGamesByTitle(games, nil)
	if assert.Len(t, out, 1) {
		assert.Equal(t, uint(3), out[0].ID, "lowest ID wins the final tiebreak")
	}
}

func TestDedupeGamesByTitle_GenerationZeroPushedToEnd(t *testing.T) {
	// Generation 0 means the seed hasn't categorised this console
	// (demos, obscure systems). The rule pushes those to the back so
	// the user's "real" platform always wins.
	games := []db.Game{
		{ID: 1, Title: "Title", IGDBCriticsRating: 95, Console: db.Console{Generation: 0}},
		{ID: 2, Title: "Title", IGDBCriticsRating: 60, Console: db.Console{Generation: 5}},
	}
	out := dedupeGamesByTitle(games, nil)
	if assert.Len(t, out, 1) {
		assert.Equal(t, uint(2), out[0].ID,
			"generation 0 (unknown) should lose to any non-zero generation")
	}
}

func TestDedupeGamesByTitle_DistinctTitlesAllKept(t *testing.T) {
	// Three actually-distinct titles → no dedupe.
	games := []db.Game{
		{ID: 1, Title: "Street Fighter II", Console: db.Console{Generation: 4}},
		{ID: 2, Title: "Super Street Fighter II", Console: db.Console{Generation: 4}},
		{ID: 3, Title: "Street Fighter Alpha", Console: db.Console{Generation: 5}},
	}
	out := dedupeGamesByTitle(games, nil)
	assert.Len(t, out, 3, "different titles should never be collapsed")
}

func TestDedupeGamesByTitle_PreservesFirstOccurrenceOrder(t *testing.T) {
	// Order in the output should follow the FIRST appearance of each
	// title in the input, regardless of which game in the group is
	// picked.
	games := []db.Game{
		{ID: 1, Title: "Alpha", Console: db.Console{Generation: 5}},
		{ID: 2, Title: "Beta", Console: db.Console{Generation: 5}},
		{ID: 3, Title: "Alpha (USA)", Console: db.Console{Generation: 3}}, // dedupes into Alpha
		{ID: 4, Title: "Gamma", Console: db.Console{Generation: 5}},
	}
	out := dedupeGamesByTitle(games, nil)
	if assert.Len(t, out, 3) {
		assert.Equal(t, "Alpha (USA)", out[0].Title, "first slot is the Alpha group's winner (lower-gen NES port wins by tiebreak), positioned at the group's first input index")
		assert.Equal(t, "Beta", out[1].Title)
		assert.Equal(t, "Gamma", out[2].Title)
	}
}
