package db

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func setupBackfillDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&Console{}, &Game{}, &ServerSetting{}))
	return database
}

func TestMigrateRAHashChecked_ReopensNegativeCachedGames(t *testing.T) {
	database := setupBackfillDB(t)

	console := Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)

	// Poisoned by the old ROM-not-found write, or a genuine no-match — we
	// cannot tell them apart retroactively, so both re-open exactly once.
	poisoned := Game{ConsoleID: console.ID, Title: "Poisoned", FileName: "a.nes", FilePath: "roms/a.nes",
		RAHashChecked: true, RAGameID: 0}
	// A resolved game must be left completely alone.
	matched := Game{ConsoleID: console.ID, Title: "Matched", FileName: "b.nes", FilePath: "roms/b.nes",
		RAHashChecked: true, RAGameID: 42}
	require.NoError(t, database.Create(&poisoned).Error)
	require.NoError(t, database.Create(&matched).Error)

	require.NoError(t, MigrateRAHashChecked(database))

	var gotPoisoned Game
	require.NoError(t, database.First(&gotPoisoned, poisoned.ID).Error)
	assert.False(t, gotPoisoned.RAHashChecked, "a negative-cached game must be re-opened for one more lookup")

	var gotMatched Game
	require.NoError(t, database.First(&gotMatched, matched.ID).Error)
	assert.True(t, gotMatched.RAHashChecked, "a resolved game must not be touched")
	assert.Equal(t, uint(42), gotMatched.RAGameID)
}

// Running every boot would clear the negative cache every boot — i.e. exactly
// the #1674 hammering this whole change exists to stop. It must run once.
func TestMigrateRAHashChecked_IsOneTime(t *testing.T) {
	database := setupBackfillDB(t)

	console := Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := Game{ConsoleID: console.ID, Title: "Unmatched", FileName: "a.nes", FilePath: "roms/a.nes",
		RAHashChecked: true, RAGameID: 0}
	require.NoError(t, database.Create(&game).Error)

	require.NoError(t, MigrateRAHashChecked(database))

	// The scrape then runs and re-confirms there is no RA match.
	require.NoError(t, database.Model(&Game{}).Where("id = ?", game.ID).
		Updates(map[string]interface{}{"ra_hash_checked": true}).Error)

	// Second boot: the migration must be a no-op, leaving the flag set.
	require.NoError(t, MigrateRAHashChecked(database))

	var got Game
	require.NoError(t, database.First(&got, game.ID).Error)
	assert.True(t, got.RAHashChecked, "re-running must not re-open an already-confirmed no-match")
}

func TestMigrateRAHashChecked_EmptyDB(t *testing.T) {
	database := setupBackfillDB(t)
	require.NoError(t, MigrateRAHashChecked(database))
	require.NoError(t, MigrateRAHashChecked(database))
}
