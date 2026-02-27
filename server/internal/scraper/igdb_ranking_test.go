package scraper

import (
	"testing"

	"github.com/spela/server/internal/igdb"
	"github.com/stretchr/testify/assert"
)

func TestBestIGDBMatch_Ranking(t *testing.T) {
	tests := []struct {
		name     string
		query    string
		games    []igdb.Game
		wantName string
	}{
		{
			name:  "Castlevania exact over sequel",
			query: "Castlevania",
			games: []igdb.Game{
				{ID: 1, Name: "Castlevania II: Simon's Quest"},
				{ID: 2, Name: "Castlevania"},
			},
			wantName: "Castlevania",
		},
		{
			name:  "Super Mario Bros exact over sequels",
			query: "Super Mario Bros",
			games: []igdb.Game{
				{ID: 1, Name: "Super Mario Bros."},
				{ID: 2, Name: "Super Mario Bros. 2"},
				{ID: 3, Name: "Super Mario Bros. 3"},
			},
			wantName: "Super Mario Bros.",
		},
		{
			name:  "Zelda prefix matches Zelda II over Legend of Zelda contains",
			query: "Zelda",
			games: []igdb.Game{
				{ID: 1, Name: "The Legend of Zelda"},
				{ID: 2, Name: "Zelda II: The Adventure of Link"},
			},
			wantName: "Zelda II: The Adventure of Link",
		},
		{
			name:  "single result returns it",
			query: "Metroid",
			games: []igdb.Game{
				{ID: 1, Name: "Metroid"},
			},
			wantName: "Metroid",
		},
		{
			name:  "exact match wins among fuzzy matches",
			query: "Contra",
			games: []igdb.Game{
				{ID: 1, Name: "Contra III: The Alien Wars"},
				{ID: 2, Name: "Super Contra"},
				{ID: 3, Name: "Contra"},
			},
			wantName: "Contra",
		},
		{
			name:  "Super Mario 64 over unreleased sequel",
			query: "Super Mario 64",
			games: []igdb.Game{
				{ID: 1, Name: "Super Mario 64 2", FirstReleaseDate: 0},
				{ID: 2, Name: "Super Mario 64", FirstReleaseDate: 835488000},
			},
			wantName: "Super Mario 64",
		},
		{
			name:  "released game preferred over unreleased with equal score",
			query: "Sonic Adventure",
			games: []igdb.Game{
				{ID: 1, Name: "Sonic Adventure", FirstReleaseDate: 0},
				{ID: 2, Name: "Sonic Adventure", FirstReleaseDate: 914544000},
			},
			wantName: "Sonic Adventure",
		},
		{
			name:  "prefix match beats contains match",
			query: "Mario",
			games: []igdb.Game{
				{ID: 1, Name: "Super Mario Bros."},
				{ID: 2, Name: "Mario Bros."},
			},
			wantName: "Mario Bros.",
		},
		{
			name:  "earlier release wins among equal-scoring released games",
			query: "Doom",
			games: []igdb.Game{
				{ID: 1, Name: "Doom", FirstReleaseDate: 1576540800}, // 2019 reboot
				{ID: 2, Name: "Doom", FirstReleaseDate: 755222400},  // 1993 original
			},
			wantName: "Doom",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := bestIGDBMatch(tt.query, tt.games)
			assert.Equal(t, tt.wantName, got.Name)
		})
	}
}

func TestBestIGDBMatch_SuperMario64_PicksOriginal(t *testing.T) {
	// Regression test: IGDB returns "Super Mario 64 2" (unreleased) before
	// "Super Mario 64" (released 1996). The matcher must prefer the released original.
	got := bestIGDBMatch("Super Mario 64", []igdb.Game{
		{ID: 999, Name: "Super Mario 64 2", FirstReleaseDate: 0},
		{ID: 100, Name: "Super Mario 64", FirstReleaseDate: 835488000},
	})
	assert.Equal(t, "Super Mario 64", got.Name)
	assert.Equal(t, 100, got.ID)
}

func TestBestIGDBMatch_ReleasedOverUnreleased(t *testing.T) {
	// When two games have the same name, prefer the one that was actually released
	got := bestIGDBMatch("Sonic Adventure", []igdb.Game{
		{ID: 1, Name: "Sonic Adventure", FirstReleaseDate: 0},
		{ID: 2, Name: "Sonic Adventure", FirstReleaseDate: 914544000},
	})
	assert.Equal(t, 2, got.ID)
}

func TestBestIGDBMatch_EarlierReleasePreferred(t *testing.T) {
	// When two games have the same name and are both released, prefer earlier release
	got := bestIGDBMatch("Doom", []igdb.Game{
		{ID: 1, Name: "Doom", FirstReleaseDate: 1576540800}, // 2019
		{ID: 2, Name: "Doom", FirstReleaseDate: 755222400},  // 1993
	})
	assert.Equal(t, 2, got.ID)
}

func TestIsBetterIGDBMatch(t *testing.T) {
	tests := []struct {
		name           string
		candidateScore float64
		candidate      igdb.Game
		bestScore      float64
		best           igdb.Game
		want           bool
	}{
		{
			name:           "higher score wins",
			candidateScore: 0.95,
			candidate:      igdb.Game{Name: "A"},
			bestScore:      0.90,
			best:           igdb.Game{Name: "B"},
			want:           true,
		},
		{
			name:           "lower score loses",
			candidateScore: 0.85,
			candidate:      igdb.Game{Name: "A"},
			bestScore:      0.90,
			best:           igdb.Game{Name: "B"},
			want:           false,
		},
		{
			name:           "released beats unreleased at equal score",
			candidateScore: 0.95,
			candidate:      igdb.Game{Name: "A", FirstReleaseDate: 100},
			bestScore:      0.95,
			best:           igdb.Game{Name: "B", FirstReleaseDate: 0},
			want:           true,
		},
		{
			name:           "unreleased loses to released at equal score",
			candidateScore: 0.95,
			candidate:      igdb.Game{Name: "A", FirstReleaseDate: 0},
			bestScore:      0.95,
			best:           igdb.Game{Name: "B", FirstReleaseDate: 100},
			want:           false,
		},
		{
			name:           "earlier release beats later at equal score",
			candidateScore: 0.95,
			candidate:      igdb.Game{Name: "A", FirstReleaseDate: 100},
			bestScore:      0.95,
			best:           igdb.Game{Name: "B", FirstReleaseDate: 200},
			want:           true,
		},
		{
			name:           "shorter name wins as final tiebreaker",
			candidateScore: 0.95,
			candidate:      igdb.Game{Name: "AB", FirstReleaseDate: 100},
			bestScore:      0.95,
			best:           igdb.Game{Name: "ABC", FirstReleaseDate: 100},
			want:           true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := isBetterIGDBMatch(tt.candidateScore, tt.candidate, tt.bestScore, tt.best)
			assert.Equal(t, tt.want, got)
		})
	}
}
