package db

import (
	"testing"

	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// #943: ClownMDEmu's Sega CD BIOS-call emulation is incomplete (the boot
// path enters an infinite loop on `UNRECOGNISED BIOS CALL 0x08 / 0x87`),
// so every Sega CD title hangs on the BIOS region screen. The default
// core must stay genesis_plus_gx until upstream catches up.
//
// This test guards against an accidental revert via copy-paste, AI tool,
// or "rebase ate my edit" mishap.
func TestSegaCDDefaultCoreIsGenesisPlusGx(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&Console{}))
	require.NoError(t, SeedConsoles(database))

	var scd Console
	require.NoError(t, database.Where("abbreviation = ?", "SCD").First(&scd).Error)
	require.Equal(t, "genesis_plus_gx", scd.DefaultCore,
		"Sega CD default core regressed — see #943 for why ClownMDEmu can't be the default")
}

// Existing servers that booted on a pre-#943 build and persisted the
// `clownmdemu` value to disk must be migrated by SeedConsoles' backfill
// path on next restart. This test exercises the upgrade path so a future
// refactor of SeedConsoles' backfill loop doesn't silently leave existing
// installs stuck on the broken core.
func TestSegaCDDefaultCoreBackfillsFromClownMDEmu(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&Console{}))

	// Pre-seed the DB to mimic a server that booted on the pre-#943
	// build: Sega CD already exists with the broken default core.
	preExisting := Console{
		Name:         "Sega CD",
		Abbreviation: "SCD",
		Extensions:   ".iso,.bin,.cue,.m3u",
		DefaultCore:  "clownmdemu",
		FolderName:   "segacd",
	}
	require.NoError(t, database.Create(&preExisting).Error)

	require.NoError(t, SeedConsoles(database))

	var scd Console
	require.NoError(t, database.Where("abbreviation = ?", "SCD").First(&scd).Error)
	require.Equal(t, "genesis_plus_gx", scd.DefaultCore,
		"SeedConsoles must backfill DefaultCore from clownmdemu to genesis_plus_gx on existing rows")
}
