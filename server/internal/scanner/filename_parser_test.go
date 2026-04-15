package scanner

import (
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestParseFilenameMetadata(t *testing.T) {
	tests := []struct {
		name         string
		filename     string
		wantRegion   string
		wantRevision string
		wantTags     string
		wantPreRel   bool
		wantGroupKey string
	}{
		{
			name:         "standard no-intro USA",
			filename:     "Super Mario World (USA).sfc",
			wantRegion:   "USA",
			wantRevision: "",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "super mario world",
		},
		{
			name:         "multi-region",
			filename:     "Cool Game (USA, Europe).gba",
			wantRegion:   "USA, Europe",
			wantRevision: "",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "cool game",
		},
		{
			name:         "revision number",
			filename:     "Sonic (USA) (Rev 1).sfc",
			wantRegion:   "USA",
			wantRevision: "Rev 1",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "sonic",
		},
		{
			name:         "revision letter",
			filename:     "Game (Japan) (Rev A).sfc",
			wantRegion:   "Japan",
			wantRevision: "Rev A",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "game",
		},
		{
			name:         "version tag",
			filename:     "Game (Japan) (v1.1).sfc",
			wantRegion:   "Japan",
			wantRevision: "v1.1",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "game",
		},
		{
			name:         "beta pre-release",
			filename:     "Mega Man (USA) (Beta).sfc",
			wantRegion:   "USA",
			wantRevision: "",
			wantTags:     "beta",
			wantPreRel:   true,
			wantGroupKey: "mega man",
		},
		{
			name:         "proto and unlicensed",
			filename:     "Game (Japan) (Proto) (Unl).nes",
			wantRegion:   "Japan",
			wantRevision: "",
			wantTags:     "proto,unl",
			wantPreRel:   true,
			wantGroupKey: "game",
		},
		{
			name:         "no tags homebrew",
			filename:     "MyHomebrew.nes",
			wantRegion:   "",
			wantRevision: "",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "myhomebrew",
		},
		{
			name:         "unlicensed only not pre-release",
			filename:     "Game Genie (USA) (Unl).nes",
			wantRegion:   "USA",
			wantRevision: "",
			wantTags:     "unl",
			wantPreRel:   false,
			wantGroupKey: "game genie",
		},
		{
			name:         "leading article The stripped",
			filename:     "The Legend of Zelda (USA).nes",
			wantRegion:   "USA",
			wantRevision: "",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "legend of zelda",
		},
		{
			name:         "trailing article stripped",
			filename:     "Legend of Zelda, The (USA).nes",
			wantRegion:   "USA",
			wantRevision: "",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "legend of zelda",
		},
		{
			name:         "demo is pre-release",
			filename:     "Cool Demo (Europe) (Demo).gba",
			wantRegion:   "Europe",
			wantRevision: "",
			wantTags:     "demo",
			wantPreRel:   true,
			wantGroupKey: "cool demo",
		},
		{
			name:         "sample is pre-release",
			filename:     "Test Game (USA) (Sample).sfc",
			wantRegion:   "USA",
			wantRevision: "",
			wantTags:     "sample",
			wantPreRel:   true,
			wantGroupKey: "test game",
		},
		{
			name:         "world region",
			filename:     "Tetris (World).gb",
			wantRegion:   "World",
			wantRevision: "",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "tetris",
		},
		{
			name:         "hack tag",
			filename:     "Super Mario World (USA) (Hack).sfc",
			wantRegion:   "USA",
			wantRevision: "",
			wantTags:     "hack",
			wantPreRel:   false,
			wantGroupKey: "super mario world",
		},
		{
			name:         "revision and region combined",
			filename:     "Donkey Kong Country (USA) (Rev B).sfc",
			wantRegion:   "USA",
			wantRevision: "Rev B",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "donkey kong country",
		},
		{
			name:         "version with extra digits",
			filename:     "Pokemon (Japan) (v2.0).gbc",
			wantRegion:   "Japan",
			wantRevision: "v2.0",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "pokemon",
		},
		{
			name:         "pirate tag",
			filename:     "Super Game (USA) (Pirate).nes",
			wantRegion:   "USA",
			wantRevision: "",
			wantTags:     "pirate",
			wantPreRel:   false,
			wantGroupKey: "super game",
		},
		{
			name:         "article A stripped",
			filename:     "A Boy and His Blob (USA).nes",
			wantRegion:   "USA",
			wantRevision: "",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "boy and his blob",
		},
		{
			name:         "cue file",
			filename:     "Final Fantasy VII (USA) (Disc 1).cue",
			wantRegion:   "USA",
			wantRevision: "",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "final fantasy vii",
		},
		{
			name:         "m3u file",
			filename:     "Chrono Cross (USA).m3u",
			wantRegion:   "USA",
			wantRevision: "",
			wantTags:     "",
			wantPreRel:   false,
			wantGroupKey: "chrono cross",
		},
		{
			name:         "demo tag marks pre-release",
			filename:     "Mega Man Battle Network 5 (USA) (Demo).gba",
			wantRegion:   "USA",
			wantRevision: "",
			wantTags:     "demo",
			wantPreRel:   true,
			wantGroupKey: "mega man battle network 5",
		},
		{
			name:         "taikenban (Japanese demo) marks pre-release",
			filename:     "Dairantou Smash Brothers DX (Japan) (Taikenban).rvz",
			wantRegion:   "Japan",
			wantRevision: "",
			wantTags:     "demo",
			wantPreRel:   true,
			wantGroupKey: "dairantou smash brothers dx",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			meta := ParseFilenameMetadata(tt.filename)
			assert.Equal(t, tt.wantRegion, meta.Region, "Region")
			assert.Equal(t, tt.wantRevision, meta.Revision, "Revision")
			assert.Equal(t, tt.wantPreRel, meta.IsPreRelease, "IsPreRelease")
			assert.Equal(t, tt.wantGroupKey, meta.GroupKey, "GroupKey")

			// Tags may be in different order, so check containment
			if tt.wantTags == "" {
				assert.Equal(t, "", meta.Tags, "Tags should be empty")
			} else {
				wantParts := splitTags(tt.wantTags)
				gotParts := splitTags(meta.Tags)
				assert.ElementsMatch(t, wantParts, gotParts, "Tags")
			}
		})
	}
}

func TestNormalizeGroupKey(t *testing.T) {
	tests := []struct {
		name     string
		filename string
		want     string
	}{
		{"simple", "Super Mario World.sfc", "super mario world"},
		{"with tags", "Super Mario World (USA) (Rev 1).sfc", "super mario world"},
		{"brackets", "Game [!].nes", "game"},
		{"ampersand", "Chip & Dale.nes", "chip and dale"},
		{"accented", "Pokémon Blue.gbc", "pokemon blue"},
		{"leading the", "The Legend of Zelda.nes", "legend of zelda"},
		{"trailing the", "Legend of Zelda, The.nes", "legend of zelda"},
		{"leading a", "A Boy and His Blob.nes", "boy and his blob"},
		{"leading an", "An American Tail.nes", "american tail"},
		{"punctuation", "Ys III - Wanderers from Ys.sfc", "ys iii wanderers from ys"},
		{"apostrophe", "Kirby's Dream Land.gb", "kirbys dream land"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := normalizeGroupKey(tt.filename)
			assert.Equal(t, tt.want, result)
		})
	}
}

// splitTags splits a comma-separated tag string into a slice.
func splitTags(tags string) []string {
	if tags == "" {
		return nil
	}
	parts := make([]string, 0)
	for _, p := range strings.Split(tags, ",") {
		p = strings.TrimSpace(p)
		if p != "" {
			parts = append(parts, p)
		}
	}
	return parts
}
