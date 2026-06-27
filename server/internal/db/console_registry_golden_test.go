package db

import (
	"encoding/json"
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// consoleGolden is the canonical, storage-independent snapshot of every
// field a fully-seeded console row carries (resolving the maker/media
// foreign keys back to their stable codes). It is the contract that the
// console code registry refactor (#1443) must preserve byte-for-byte.
type consoleGolden struct {
	Abbreviation     string  `json:"abbreviation"`
	Name             string  `json:"name"`
	Code             *string `json:"code"`
	Extensions       string  `json:"extensions"`
	DefaultCore      string  `json:"defaultCore"`
	EmulatorJSCore   string  `json:"emulatorJsCore"`
	FolderName       string  `json:"folderName"`
	CoverAspect      string  `json:"coverAspect"`
	ColorTheme       string  `json:"colorTheme"`
	Generation       int     `json:"generation"`
	SaveStateSupport bool    `json:"saveStateSupport"`
	SaveStatePolicy  string  `json:"saveStatePolicy"`
	Playable         bool    `json:"playable"`
	MakerCode        *string `json:"makerCode"`
	MediaCode        *string `json:"mediaCode"`
	ReleaseYear      *int    `json:"releaseYear"`
	UnitsSold        *int64  `json:"unitsSold"`
	Summary          *string `json:"summary"`
	Tag              *string `json:"tag"`
}

// seedAllConsoleData runs the full console seeding pipeline (consoles +
// makers/media + metadata) in the same order as cmd/server, then returns
// the canonical snapshot ordered by abbreviation.
func seedAllConsoleData(t *testing.T) []consoleGolden {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(
		&Console{}, &HardwareMaker{}, &MediaType{}, &MediaTypeCategory{},
	))

	require.NoError(t, SeedConsoles(database))
	require.NoError(t, SeedMediaTypeCategories(database))
	require.NoError(t, SeedMediaTypes(database))
	require.NoError(t, SeedHardwareMakers(database))
	require.NoError(t, SeedConsoleMetadata(database))

	var consoles []Console
	require.NoError(t, database.
		Preload("HardwareMaker").
		Preload("MediaType").
		Order("abbreviation").
		Find(&consoles).Error)

	snapshot := make([]consoleGolden, 0, len(consoles))
	for _, c := range consoles {
		g := consoleGolden{
			Abbreviation:     c.Abbreviation,
			Name:             c.Name,
			Code:             c.Code,
			Extensions:       c.Extensions,
			DefaultCore:      c.DefaultCore,
			EmulatorJSCore:   c.EmulatorJSCore,
			FolderName:       c.FolderName,
			CoverAspect:      c.CoverAspect,
			ColorTheme:       c.ColorTheme,
			Generation:       c.Generation,
			SaveStateSupport: c.SaveStateSupport,
			SaveStatePolicy:  string(c.SaveStatePolicy),
			Playable:         c.Playable,
			ReleaseYear:      c.ReleaseYear,
			UnitsSold:        c.UnitsSold,
			Summary:          c.Summary,
			Tag:              c.Tag,
		}
		if c.HardwareMaker != nil {
			code := c.HardwareMaker.Code
			g.MakerCode = &code
		}
		if c.MediaType != nil {
			code := c.MediaType.Code
			g.MediaCode = &code
		}
		snapshot = append(snapshot, g)
	}
	return snapshot
}

// TestConsoleSeedGolden freezes the exact seeded console catalog. The
// console-registry refactor (#1443) merges the two seed literals
// (SeedConsoles + SeedConsoleMetadata) into a single code registry; this
// test guarantees the merge reproduces the catalog field-for-field, so a
// transcription slip across 132 consoles fails CI instead of silently
// shipping wrong metadata.
//
// Regenerate after an intentional catalog change with:
//
//	UPDATE_CONSOLE_GOLDEN=1 go test ./internal/db -run TestConsoleSeedGolden
func TestConsoleSeedGolden(t *testing.T) {
	snapshot := seedAllConsoleData(t)
	got, err := json.MarshalIndent(snapshot, "", "  ")
	require.NoError(t, err)
	got = append(got, '\n')

	// Tracked alongside the package source: the repo's .gitignore excludes
	// every testdata/ dir (reserved for large, uncommitted ROM fixtures),
	// so a golden under testdata/ would never reach CI.
	goldenPath := "console_seed_golden.json"

	if os.Getenv("UPDATE_CONSOLE_GOLDEN") == "1" {
		require.NoError(t, os.WriteFile(goldenPath, got, 0o644))
		t.Logf("wrote golden file %s (%d consoles)", goldenPath, len(snapshot))
		return
	}

	want, err := os.ReadFile(goldenPath)
	require.NoErrorf(t, err, "missing golden file; regenerate with UPDATE_CONSOLE_GOLDEN=1")
	assert.Equal(t, string(want), string(got),
		"seeded console catalog drifted from golden; if intentional, regenerate with UPDATE_CONSOLE_GOLDEN=1")
}
