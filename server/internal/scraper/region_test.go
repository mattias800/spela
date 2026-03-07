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

func TestHasNonPreferredRegion(t *testing.T) {
	tests := []struct {
		name string
		input string
		want bool
	}{
		{"USA only", "Castlevania (USA)", false},
		{"World only", "Tetris (World)", false},
		{"no region", "Homebrew", false},
		{"Japan only", "Ice Climber (Japan)", true},
		{"Europe only", "Sonic (Europe)", true},
		{"Japan and USA", "Ice Climber (Japan, USA)", true},
		{"USA and Europe", "Mega Man 2 (USA, Europe)", true},
		{"Japan only with extension", "Ice Climber (Japan).nes", true},
		{"USA only with extension", "Castlevania (USA).nes", false},
		{"Korea", "Game (Korea)", true},
		{"Brazil", "Game (Brazil)", true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := hasNonPreferredRegion(tt.input)
			assert.Equal(t, tt.want, got)
		})
	}
}
