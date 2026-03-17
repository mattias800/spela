package scanner

import (
	"fmt"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func setupBackfillTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.Game{}, &db.Console{}))
	return database
}

func TestBackfillGameMetadata_SetsGroupKey(t *testing.T) {
	database := setupBackfillTestDB(t)

	console := db.Console{Name: "NES", Abbreviation: "nes"}
	database.Create(&console)

	games := []db.Game{
		{Title: "Super Mario Bros.", FileName: "Super Mario Bros. (USA).nes", FilePath: "/roms/nes/Super Mario Bros. (USA).nes", ConsoleID: console.ID},
		{Title: "Zelda", FileName: "Legend of Zelda, The (USA) (Rev A).nes", FilePath: "/roms/nes/Legend of Zelda, The (USA) (Rev A).nes", ConsoleID: console.ID},
	}
	for i := range games {
		require.NoError(t, database.Create(&games[i]).Error)
	}

	var count int64
	database.Model(&db.Game{}).Where("group_key = '' OR group_key IS NULL").Count(&count)
	assert.Equal(t, int64(2), count)

	var progressCalls int
	err := BackfillGameMetadataWithProgress(database, func(processed, total int64) {
		progressCalls++
		assert.LessOrEqual(t, processed, total, "processed should never exceed total")
	})
	require.NoError(t, err)

	database.Model(&db.Game{}).Where("group_key = '' OR group_key IS NULL").Count(&count)
	assert.Equal(t, int64(0), count, "all games should have GroupKey after backfill")
}

func TestBackfillGameMetadata_DoesNotInfiniteLoop(t *testing.T) {
	database := setupBackfillTestDB(t)

	console := db.Console{Name: "DOS", Abbreviation: "dos"}
	database.Create(&console)

	// A filename that produces empty GroupKey from parsing (bare name, no metadata)
	game := db.Game{Title: "game", FileName: "game.exe", FilePath: "/roms/dos/game.exe", ConsoleID: console.ID}
	require.NoError(t, database.Create(&game).Error)

	var progressCalls int
	err := BackfillGameMetadataWithProgress(database, func(processed, total int64) {
		progressCalls++
	})
	require.NoError(t, err)

	assert.Equal(t, 1, progressCalls, "should process the batch only once, not loop")

	var updated db.Game
	database.First(&updated, game.ID)
	assert.NotEmpty(t, updated.GroupKey, "GroupKey should not be empty after backfill")
}

func TestBackfillGameMetadata_ProgressNeverExceedsTotal(t *testing.T) {
	database := setupBackfillTestDB(t)

	console := db.Console{Name: "SNES", Abbreviation: "snes"}
	database.Create(&console)

	for i := 0; i < 5; i++ {
		name := fmt.Sprintf("Game %c", 'A'+i)
		require.NoError(t, database.Create(&db.Game{
			Title:     name,
			FileName:  name + " (USA).sfc",
			FilePath:  fmt.Sprintf("/roms/snes/%s (USA).sfc", name),
			ConsoleID: console.ID,
		}).Error)
	}

	err := BackfillGameMetadataWithProgress(database, func(processed, total int64) {
		assert.LessOrEqual(t, processed, total,
			"processed (%d) should not exceed total (%d)", processed, total)
	})
	require.NoError(t, err)
}

func TestBackfillGameMetadata_SkipsAlreadyBackfilled(t *testing.T) {
	database := setupBackfillTestDB(t)

	console := db.Console{Name: "GBA", Abbreviation: "gba"}
	database.Create(&console)

	require.NoError(t, database.Create(&db.Game{
		Title: "Existing", FileName: "Existing (USA).gba",
		FilePath: "/roms/gba/Existing (USA).gba",
		ConsoleID: console.ID, GroupKey: "existing",
	}).Error)
	require.NoError(t, database.Create(&db.Game{
		Title: "New Game", FileName: "New Game (Japan).gba",
		FilePath:  "/roms/gba/New Game (Japan).gba",
		ConsoleID: console.ID,
	}).Error)

	var lastTotal int64
	err := BackfillGameMetadataWithProgress(database, func(processed, total int64) {
		lastTotal = total
	})
	require.NoError(t, err)

	assert.Equal(t, int64(1), lastTotal, "should only count games needing backfill")
}
