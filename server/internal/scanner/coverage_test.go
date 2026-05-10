package scanner

import (
	"strings"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

// TestEverySeededConsoleIsScannable asserts that every console seeded by
// db.SeedConsoles is fully wired into the scanner — its FolderName is in
// directoryConsoleMap, and every extension in its Extensions field is in
// RomExtensions.
//
// This catches the class of bug where a new platform lands in the DB seed
// but the scanner still silently ignores ROMs for it. Surfaced live in
// #1157 (initial platform add) — the new platforms' .tic / .tzx / .z80 /
// .sna / .cdt / .cpr / .d88 / .88d / .hdm / .img extensions and their
// zxspectrum/ / amstradcpc/ / x68000/ / tic80/ folders were not in the
// scanner's maps, so even properly-formed ROMs landing in those folders
// would have been silently skipped by the library scan.
//
// Keep this test cheap and direct — it's a one-shot invariant.
func TestEverySeededConsoleIsScannable(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.Console{}))
	require.NoError(t, db.SeedConsoles(database))

	var consoles []db.Console
	require.NoError(t, database.Find(&consoles).Error)
	require.NotEmpty(t, consoles, "SeedConsoles produced no rows")

	// Reverse map: abbreviation → at least one directory name in directoryConsoleMap.
	dirCoversAbbrev := make(map[string]bool, len(consoles))
	for _, abbrev := range directoryConsoleMap {
		dirCoversAbbrev[abbrev] = true
	}

	for _, c := range consoles {
		// Non-playable rows (Wii, PS3, PS4, PS5, Switch, etc. before they
		// have libretro cores) are metadata-only — they don't need
		// scanner coverage. The scanner gracefully skips them when
		// console.DefaultCore == "".
		if !c.Playable {
			continue
		}

		t.Run(c.Abbreviation, func(t *testing.T) {
			assert.True(t, dirCoversAbbrev[c.Abbreviation],
				"console %s (folder %q) is playable but no entry in "+
					"directoryConsoleMap maps to it — scanner won't recognise "+
					"its folder; add e.g. %q: %q to scanner.directoryConsoleMap",
				c.Abbreviation, c.FolderName, c.FolderName, c.Abbreviation)

			for _, ext := range strings.Split(c.Extensions, ",") {
				ext = strings.TrimSpace(ext)
				if ext == "" {
					continue
				}
				assert.True(t, RomExtensions[ext],
					"console %s declares extension %q in its DB seed but "+
						"scanner.RomExtensions doesn't include it — files with "+
						"this extension will be silently ignored by the library scan",
					c.Abbreviation, ext)
			}
		})
	}
}
