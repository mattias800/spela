package db

import (
	"testing"

	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// TestSeedCoresCoversEveryConsoleDefaultCore guards against the drift that
// motivated issue #555: every console's DefaultCore must correspond to a row
// seeded by SeedCores. If this test fails after adding a new console, add the
// matching core to SeedCores (or remove the DefaultCore if the platform is
// not yet playable).
func TestSeedCoresCoversEveryConsoleDefaultCore(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&Console{}, &Core{}))
	require.NoError(t, SeedConsoles(database))
	require.NoError(t, SeedCores(database))

	var consoles []Console
	require.NoError(t, database.Find(&consoles).Error)

	var missing []string
	for _, c := range consoles {
		if c.DefaultCore == "" {
			continue
		}
		var core Core
		err := database.Where("name = ?", c.DefaultCore).First(&core).Error
		if err != nil {
			missing = append(missing, c.Abbreviation+":"+c.DefaultCore)
		}
	}

	if len(missing) > 0 {
		t.Fatalf("consoles reference DefaultCore values not present in SeedCores: %v", missing)
	}
}
