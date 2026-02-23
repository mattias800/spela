package scraper

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestExtractRegion(t *testing.T) {
	tests := []struct {
		name     string
		filename string
		want     string
	}{
		{"USA", "Castlevania (USA).nes", "USA"},
		{"Japan", "Super Mario World (Japan).sfc", "Japan"},
		{"Europe", "Sonic the Hedgehog (Europe).md", "Europe"},
		{"World", "Tetris (World).gb", "World"},
		{"multi-region", "Mega Man 2 (USA, Europe).nes", "USA, Europe"},
		{"Korea", "Some Game (Korea).sfc", "Korea"},
		{"no region", "homebrew.nes", ""},
		{"non-region parens", "Game (Rev 1).nes", ""},
		{"region after other parens", "Game (Rev 1) (USA).nes", "USA"},
		{"Brazil", "Game (Brazil).sfc", "Brazil"},
		{"France", "Game (France).sfc", "France"},
		{"Germany", "Game (Germany).sfc", "Germany"},
		{"Spain", "Game (Spain).sfc", "Spain"},
		{"Italy", "Game (Italy).sfc", "Italy"},
		{"Australia", "Game (Australia).sfc", "Australia"},
		{"Asia", "Game (Asia).sfc", "Asia"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := ExtractRegion(tt.filename)
			assert.Equal(t, tt.want, got)
		})
	}
}
