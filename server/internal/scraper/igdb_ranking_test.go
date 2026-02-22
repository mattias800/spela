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
			name:  "Zelda matches Legend of Zelda over sequel",
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
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := bestIGDBMatch(tt.query, tt.games)
			assert.Equal(t, tt.wantName, got.Name)
		})
	}
}
