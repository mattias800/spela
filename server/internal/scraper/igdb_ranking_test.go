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
			name:  "Zelda suffix match beats prefix match",
			query: "Zelda",
			games: []igdb.Game{
				{ID: 1, Name: "The Legend of Zelda"},
				{ID: 2, Name: "Zelda II: The Adventure of Link"},
			},
			wantName: "The Legend of Zelda",
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

func TestBestIGDBMatch_Aladdin_PicksRealGame(t *testing.T) {
	// Regression test: IGDB text search for "Aladdin" on SNES returns both
	// "Aladdin 2000" (unlicensed bootleg, prefix match) and "Disney's Aladdin"
	// (real Capcom game, suffix match). The suffix tier + metadata quality
	// penalties must prefer the real game.
	got := bestIGDBMatch("Aladdin", []igdb.Game{
		{
			ID:               247332,
			Name:             "Aladdin 2000",
			FirstReleaseDate: 0, // no release date
			InvolvedCompanies: []igdb.InvolvedCompany{
				{Company: igdb.Company{ID: 1, Name: "Unknown"}},
			},
			Screenshots: []igdb.Image{{ID: 1, ImageID: "s1"}},
			Genres:      []igdb.Genre{{ID: 1, Name: "Platform"}},
			// no cover
		},
		{
			ID:               2473,
			Name:             "Disney's Aladdin",
			FirstReleaseDate: 754272000, // 1993-11-21
			InvolvedCompanies: []igdb.InvolvedCompany{
				{Company: igdb.Company{ID: 1, Name: "Capcom"}, Developer: true},
				{Company: igdb.Company{ID: 2, Name: "Capcom"}, Publisher: true},
				{Company: igdb.Company{ID: 3, Name: "The Walt Disney Company"}},
			},
			Cover:       &igdb.Image{ID: 1, ImageID: "c1"},
			Screenshots: []igdb.Image{{ID: 1, ImageID: "s1"}, {ID: 2, ImageID: "s2"}, {ID: 3, ImageID: "s3"}},
			Genres:      []igdb.Genre{{ID: 1, Name: "Platform"}, {ID: 2, Name: "Adventure"}, {ID: 3, Name: "Arcade"}},
		},
	})
	assert.Equal(t, "Disney's Aladdin", got.Name)
	assert.Equal(t, 2473, got.ID)
}

func TestBestIGDBMatch_SuffixBeatsPrefixForQualifiedNames(t *testing.T) {
	// Games with possessive qualifiers (e.g. "Tom Clancy's Splinter Cell")
	// should rank higher via suffix match than prefix matches.
	tests := []struct {
		name     string
		query    string
		games    []igdb.Game
		wantName string
	}{
		{
			name:  "Tom Clancys Splinter Cell suffix over Splinter Cell Blacklist prefix",
			query: "Splinter Cell",
			games: []igdb.Game{
				{ID: 1, Name: "Splinter Cell: Blacklist", FirstReleaseDate: 100, Cover: &igdb.Image{ID: 1, ImageID: "c1"}},
				{ID: 2, Name: "Tom Clancy's Splinter Cell", FirstReleaseDate: 100, Cover: &igdb.Image{ID: 1, ImageID: "c2"}},
			},
			wantName: "Tom Clancy's Splinter Cell",
		},
		{
			name:  "Disneys Aladdin suffix over Aladdin 2000 prefix",
			query: "Aladdin",
			games: []igdb.Game{
				{ID: 1, Name: "Aladdin 2000", FirstReleaseDate: 100, Cover: &igdb.Image{ID: 1, ImageID: "c1"}},
				{ID: 2, Name: "Disney's Aladdin", FirstReleaseDate: 100, Cover: &igdb.Image{ID: 1, ImageID: "c2"}},
			},
			wantName: "Disney's Aladdin",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := bestIGDBMatch(tt.query, tt.games)
			assert.Equal(t, tt.wantName, got.Name)
		})
	}
}

func TestMetadataQuality(t *testing.T) {
	tests := []struct {
		name string
		game igdb.Game
		want float64
	}{
		{
			name: "full metadata scores 1.0",
			game: igdb.Game{
				FirstReleaseDate:  100,
				InvolvedCompanies: []igdb.InvolvedCompany{{Company: igdb.Company{ID: 1}}},
				Cover:             &igdb.Image{ID: 1, ImageID: "c1"},
				Screenshots:       []igdb.Image{{ID: 1, ImageID: "s1"}},
			},
			want: 1.0,
		},
		{
			name: "missing release date",
			game: igdb.Game{
				FirstReleaseDate:  0,
				InvolvedCompanies: []igdb.InvolvedCompany{{Company: igdb.Company{ID: 1}}},
				Cover:             &igdb.Image{ID: 1, ImageID: "c1"},
				Screenshots:       []igdb.Image{{ID: 1, ImageID: "s1"}},
			},
			want: 0.92,
		},
		{
			name: "missing everything",
			game: igdb.Game{},
			want: 0.92 * 0.95 * 0.95 * 0.95,
		},
		{
			name: "only missing cover",
			game: igdb.Game{
				FirstReleaseDate:  100,
				InvolvedCompanies: []igdb.InvolvedCompany{{Company: igdb.Company{ID: 1}}},
				Screenshots:       []igdb.Image{{ID: 1, ImageID: "s1"}},
			},
			want: 0.95,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := metadataQuality(tt.game)
			assert.InDelta(t, tt.want, got, 0.0001)
		})
	}
}

func TestBestIGDBMatch_MetadataPenaltyBreaksTie(t *testing.T) {
	// Two prefix matches with same length ratio — metadata quality decides.
	// "Game A" has no metadata, "Game B" is well-documented.
	got := bestIGDBMatch("Battle", []igdb.Game{
		{ID: 1, Name: "Battle Zone"}, // prefix match, no metadata → penalised
		{
			ID:                2,
			Name:              "Battle Toads",
			FirstReleaseDate:  100,
			InvolvedCompanies: []igdb.InvolvedCompany{{Company: igdb.Company{ID: 1}}},
			Cover:             &igdb.Image{ID: 1, ImageID: "c1"},
			Screenshots:       []igdb.Image{{ID: 1, ImageID: "s1"}},
		}, // prefix match, full metadata → no penalty
	})
	assert.Equal(t, "Battle Toads", got.Name)
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
