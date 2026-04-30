package db

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// TestSeedConsolesAssignsSaveStatePolicy guards against forgetting the
// new tier on a console row. SaveStatePolicy is what the player UI keys
// off for slot count / opt-out / retention, and an empty value would
// silently fall back to the "small, named saves" UX on a 90 MB
// GameCube state — the exact thing #804 is rethinking.
func TestSeedConsolesAssignsSaveStatePolicy(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&Console{}))
	require.NoError(t, SeedConsoles(database))

	var consoles []Console
	require.NoError(t, database.Find(&consoles).Error)

	allowed := map[SaveStatePolicy]bool{
		SaveStatePolicySmall:  true,
		SaveStatePolicyMedium: true,
		SaveStatePolicyLarge:  true,
	}
	for _, c := range consoles {
		assert.NotEmpty(t, string(c.SaveStatePolicy),
			"console %q has no SaveStatePolicy", c.Abbreviation)
		assert.Truef(t, allowed[c.SaveStatePolicy],
			"console %q has unknown SaveStatePolicy %q", c.Abbreviation, c.SaveStatePolicy)
	}
}

// TestSeedConsolesPolicyAnchors pins the tier for the consoles called
// out by name in #804. If a future change demotes GameCube to "small"
// or promotes NES to "large" we want a CI failure, not a silent
// quota-blowing UX shift.
func TestSeedConsolesPolicyAnchors(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&Console{}))
	require.NoError(t, SeedConsoles(database))

	cases := []struct {
		abbr string
		want SaveStatePolicy
	}{
		{"NES", SaveStatePolicySmall},
		{"SNES", SaveStatePolicySmall},
		{"GBA", SaveStatePolicySmall},
		{"NEOGEO", SaveStatePolicySmall},
		{"PSX", SaveStatePolicyMedium},
		{"N64", SaveStatePolicyMedium},
		{"NDS", SaveStatePolicyMedium},
		{"SAT", SaveStatePolicyMedium},
		{"DC", SaveStatePolicyMedium},
		{"GC", SaveStatePolicyLarge},
		{"PS2", SaveStatePolicyLarge},
		{"WII", SaveStatePolicyLarge},
		// 3DS is large because azahar states routinely exceed the 30 MB
		// medium ceiling on full-speed titles.
		{"3DS", SaveStatePolicyLarge},
	}
	for _, tc := range cases {
		t.Run(tc.abbr, func(t *testing.T) {
			var c Console
			require.NoError(t, database.Where("abbreviation = ?", tc.abbr).First(&c).Error)
			assert.Equal(t, tc.want, c.SaveStatePolicy)
		})
	}
}

// TestSeedConsolesPreservesAdminOverridePolicy locks in the contract
// that the backfill ONLY fires for empty-string rows. Once a non-empty
// value lives in the column — set by a direct SQL edit today, by a
// future admin UI later — SeedConsoles must leave it alone, otherwise
// the seed loop would silently undo the admin's choice on every boot.
// See #804 phase 3 review feedback on PR #816.
func TestSeedConsolesPreservesAdminOverridePolicy(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&Console{}))

	// Pre-seed a GameCube row whose admin has previously demoted it
	// from the default "large" tier down to "medium". The seed value
	// disagrees, but that disagreement is exactly what we must
	// tolerate.
	require.NoError(t, database.Create(&Console{
		Name:             "Nintendo GameCube",
		Abbreviation:     "GC",
		Extensions:       ".iso",
		DefaultCore:      "dolphin",
		FolderName:       "gc",
		Generation:       6,
		SaveStateSupport: true,
		SaveStatePolicy:  SaveStatePolicyMedium,
		Playable:         true,
	}).Error)

	require.NoError(t, SeedConsoles(database))

	var c Console
	require.NoError(t, database.Where("abbreviation = ?", "GC").First(&c).Error)
	assert.Equal(t, SaveStatePolicyMedium, c.SaveStatePolicy,
		"admin override must survive SeedConsoles")
}

// TestSeedConsolesBackfillsExistingRowPolicy simulates a pre-#804
// install: a Console row already exists with an empty SaveStatePolicy.
// SeedConsoles must backfill it on the next boot.
func TestSeedConsolesBackfillsExistingRowPolicy(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&Console{}))

	// Pre-seed a GameCube row with no policy — the shape of a row
	// migrated up from the previous schema.
	require.NoError(t, database.Create(&Console{
		Name:             "Nintendo GameCube",
		Abbreviation:     "GC",
		Extensions:       ".iso",
		DefaultCore:      "dolphin",
		FolderName:       "gc",
		Generation:       6,
		SaveStateSupport: true,
		// SaveStatePolicy intentionally left empty.
		Playable: true,
	}).Error)

	require.NoError(t, SeedConsoles(database))

	var c Console
	require.NoError(t, database.Where("abbreviation = ?", "GC").First(&c).Error)
	assert.Equal(t, SaveStatePolicyLarge, c.SaveStatePolicy)
}
