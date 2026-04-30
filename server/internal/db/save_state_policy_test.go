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
	}
	for _, tc := range cases {
		t.Run(tc.abbr, func(t *testing.T) {
			var c Console
			require.NoError(t, database.Where("abbreviation = ?", tc.abbr).First(&c).Error)
			assert.Equal(t, tc.want, c.SaveStatePolicy)
		})
	}
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
