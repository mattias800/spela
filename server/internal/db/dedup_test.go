package db

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func setupTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	err = database.AutoMigrate(
		&User{}, &Console{}, &Game{}, &GameDisc{},
		&Favorite{}, &PlayHistory{},
		&GameRating{}, &GameScreenshot{}, &ActivityEvent{},
		&SharedSaveState{}, &GameAchievementCache{},
		&GameCollection{}, &CollectionItem{}, &PlayLaterItem{},
		&SharedSession{}, &NetplaySession{}, &Challenge{},
		&GameKeyMappingPreference{},
		&GameTheme{}, &GameKeyword{}, &GamePlayerPerspective{},
		&GameFranchise{}, &GameArtworkImage{}, &GameReleaseDate{},
		&GameVideo{}, &GameLanguageSupport{}, &GameAgeRating{},
		&GameSeries{}, &GameSeriesEntry{},
		&GameFranchiseGroup{}, &GameFranchiseEntry{},
	)
	require.NoError(t, err)

	// Drop the unique index on file_path so we can create duplicates in tests
	// (simulates the real-world condition where duplicates exist from old migrations).
	database.Exec("DROP INDEX IF EXISTS idx_games_file_path")
	database.Exec("DROP INDEX IF EXISTS uni_games_file_path")

	return database
}

func seedNESConsole(t *testing.T, database *gorm.DB) Console {
	t.Helper()
	c := Console{Name: "NES", Abbreviation: "NES", Extensions: ".nes", FolderName: "nes"}
	require.NoError(t, database.Create(&c).Error)
	return c
}

func TestExtractRelativeByConsoleFolders(t *testing.T) {
	folders := map[string]bool{"nes": true, "snes": true, "gba": true}

	tests := []struct {
		name string
		path string
		want string
	}{
		{"matches nes folder", "/old/docker/path/nes/game.nes", "nes/game.nes"},
		{"matches snes folder", "/app/games/snes/zelda.sfc", "snes/zelda.sfc"},
		{"nested subfolders", "/mnt/data/games/gba/subdir/game.gba", "gba/subdir/game.gba"},
		{"no match returns original", "/some/random/path/game.nes", "/some/random/path/game.nes"},
		{"folder name is last segment (no file after)", "/path/nes", "/path/nes"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := extractRelativeByConsoleFolders(tt.path, folders)
			assert.Equal(t, tt.want, got)
		})
	}
}

func TestMigrateToRelativePaths_ConsoleFolderFallback(t *testing.T) {
	database := setupTestDB(t)
	console := seedNESConsole(t, database)

	// Create a game with an absolute path that won't match any gameDir prefix
	game := Game{
		ConsoleID: console.ID,
		Title:     "Test Game",
		FileName:  "game.nes",
		FilePath:  "/old/docker/path/nes/game.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	// gameDirs has a different prefix than the stored path
	err := MigrateToRelativePaths(database, []string{"/current/games"})
	require.NoError(t, err)

	var updated Game
	require.NoError(t, database.First(&updated, game.ID).Error)
	assert.Equal(t, "nes/game.nes", updated.FilePath)
}

func TestMigrateToRelativePaths_DotDotPrefix(t *testing.T) {
	database := setupTestDB(t)
	console := seedNESConsole(t, database)

	// Simulate a game with ../testdata/roms/ prefix (stale relative path)
	game := Game{
		ConsoleID: console.ID,
		Title:     "Test Game",
		FileName:  "game.nes",
		FilePath:  "../testdata/roms/nes/game.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	err := MigrateToRelativePaths(database, []string{"./games"})
	require.NoError(t, err)

	var updated Game
	require.NoError(t, database.First(&updated, game.ID).Error)
	assert.Equal(t, "nes/game.nes", updated.FilePath)
}

func TestMigrateToRelativePaths_DotDotDuplicate(t *testing.T) {
	database := setupTestDB(t)
	console := seedNESConsole(t, database)

	// Good game with canonical path
	game1 := Game{ConsoleID: console.ID, Title: "Good", FileName: "game.nes", FilePath: "nes/game.nes", ScraperID: "igdb:1"}
	require.NoError(t, database.Create(&game1).Error)
	// Stale duplicate with ../testdata prefix
	game2 := Game{ConsoleID: console.ID, Title: "Stale", FileName: "game.nes", FilePath: "../testdata/roms/nes/game.nes"}
	require.NoError(t, database.Create(&game2).Error)

	err := MigrateToRelativePaths(database, []string{"./games"})
	require.NoError(t, err)

	// Only the good game should remain
	var games []Game
	database.Find(&games)
	require.Len(t, games, 1)
	assert.Equal(t, game1.ID, games[0].ID)
}

func TestDeduplicateGames_KeepsBestMetadata(t *testing.T) {
	database := setupTestDB(t)
	console := seedNESConsole(t, database)

	// Game 1: no metadata (lower ID)
	game1 := Game{
		ConsoleID: console.ID,
		Title:     "Game No Meta",
		FileName:  "game.nes",
		FilePath:  "nes/game.nes",
	}
	require.NoError(t, database.Create(&game1).Error)

	// Game 2: has scraper metadata (higher ID, but better)
	game2 := Game{
		ConsoleID: console.ID,
		Title:     "Game With Meta",
		FileName:  "game.nes",
		FilePath:  "nes/game.nes",
		ScraperID: "igdb:12345",
		CoverURL:  "https://example.com/cover.jpg",
		Developer: "Nintendo",
	}
	require.NoError(t, database.Create(&game2).Error)

	err := DeduplicateGames(database)
	require.NoError(t, err)

	// Only one game should remain
	var games []Game
	database.Find(&games)
	require.Len(t, games, 1)

	// The keeper should be game2 (better metadata)
	assert.Equal(t, game2.ID, games[0].ID)
	assert.Equal(t, "igdb:12345", games[0].ScraperID)
}

func TestDeduplicateGames_MergesFavorites(t *testing.T) {
	database := setupTestDB(t)
	console := seedNESConsole(t, database)
	user := User{Username: "testuser", Email: "test@example.com", PasswordHash: "hash"}
	require.NoError(t, database.Create(&user).Error)

	game1 := Game{ConsoleID: console.ID, Title: "G1", FileName: "g.nes", FilePath: "nes/g.nes", ScraperID: "igdb:1"}
	game2 := Game{ConsoleID: console.ID, Title: "G2", FileName: "g.nes", FilePath: "nes/g.nes"}
	require.NoError(t, database.Create(&game1).Error)
	require.NoError(t, database.Create(&game2).Error)

	// Favorite on the duplicate
	fav := Favorite{UserID: user.ID, GameID: game2.ID}
	require.NoError(t, database.Create(&fav).Error)

	err := DeduplicateGames(database)
	require.NoError(t, err)

	// Favorite should now point to the keeper (game1)
	var favs []Favorite
	database.Find(&favs)
	require.Len(t, favs, 1)
	assert.Equal(t, game1.ID, favs[0].GameID)
}

func TestDeduplicateGames_MergesPlayHistory(t *testing.T) {
	database := setupTestDB(t)
	console := seedNESConsole(t, database)
	user := User{Username: "testuser", Email: "test@example.com", PasswordHash: "hash"}
	require.NoError(t, database.Create(&user).Error)

	game1 := Game{ConsoleID: console.ID, Title: "G1", FileName: "g.nes", FilePath: "nes/g.nes", ScraperID: "igdb:1"}
	game2 := Game{ConsoleID: console.ID, Title: "G2", FileName: "g.nes", FilePath: "nes/g.nes"}
	require.NoError(t, database.Create(&game1).Error)
	require.NoError(t, database.Create(&game2).Error)

	now := time.Now()
	earlier := now.Add(-1 * time.Hour)

	// Keeper has less play time and earlier timestamp
	ph1 := PlayHistory{UserID: user.ID, GameID: game1.ID, PlayTime: 100, LastPlayed: earlier}
	require.NoError(t, database.Create(&ph1).Error)

	// Duplicate has more play time and later timestamp
	ph2 := PlayHistory{UserID: user.ID, GameID: game2.ID, PlayTime: 500, LastPlayed: now}
	require.NoError(t, database.Create(&ph2).Error)

	err := DeduplicateGames(database)
	require.NoError(t, err)

	// Should have merged: one entry with best of both
	var histories []PlayHistory
	database.Find(&histories)
	require.Len(t, histories, 1)
	assert.Equal(t, game1.ID, histories[0].GameID)
	assert.Equal(t, int64(500), histories[0].PlayTime)
	assert.WithinDuration(t, now, histories[0].LastPlayed, time.Second)
}

func TestDeduplicateGames_NoDuplicates(t *testing.T) {
	database := setupTestDB(t)
	console := seedNESConsole(t, database)

	game1 := Game{ConsoleID: console.ID, Title: "G1", FileName: "a.nes", FilePath: "nes/a.nes"}
	game2 := Game{ConsoleID: console.ID, Title: "G2", FileName: "b.nes", FilePath: "nes/b.nes"}
	require.NoError(t, database.Create(&game1).Error)
	require.NoError(t, database.Create(&game2).Error)

	err := DeduplicateGames(database)
	require.NoError(t, err)

	var games []Game
	database.Find(&games)
	assert.Len(t, games, 2)
}

func TestDeduplicateGames_MergesMetadataFromDuplicate(t *testing.T) {
	database := setupTestDB(t)
	console := seedNESConsole(t, database)

	// Keeper has scraper ID but no cover
	game1 := Game{ConsoleID: console.ID, Title: "G1", FileName: "g.nes", FilePath: "nes/g.nes", ScraperID: "igdb:1"}
	require.NoError(t, database.Create(&game1).Error)

	// Duplicate has cover and developer
	game2 := Game{ConsoleID: console.ID, Title: "G2", FileName: "g.nes", FilePath: "nes/g.nes",
		CoverURL: "https://example.com/cover.jpg", Developer: "Nintendo"}
	require.NoError(t, database.Create(&game2).Error)

	err := DeduplicateGames(database)
	require.NoError(t, err)

	var keeper Game
	require.NoError(t, database.First(&keeper, game1.ID).Error)
	assert.Equal(t, "igdb:1", keeper.ScraperID)
	assert.Equal(t, "https://example.com/cover.jpg", keeper.CoverURL)
	assert.Equal(t, "Nintendo", keeper.Developer)
}

func TestDeduplicateGames_DuplicateFavoriteSkipped(t *testing.T) {
	database := setupTestDB(t)
	console := seedNESConsole(t, database)
	user := User{Username: "testuser", Email: "test@example.com", PasswordHash: "hash"}
	require.NoError(t, database.Create(&user).Error)

	game1 := Game{ConsoleID: console.ID, Title: "G1", FileName: "g.nes", FilePath: "nes/g.nes", ScraperID: "igdb:1"}
	game2 := Game{ConsoleID: console.ID, Title: "G2", FileName: "g.nes", FilePath: "nes/g.nes"}
	require.NoError(t, database.Create(&game1).Error)
	require.NoError(t, database.Create(&game2).Error)

	// Both games have favorites from the same user
	fav1 := Favorite{UserID: user.ID, GameID: game1.ID}
	fav2 := Favorite{UserID: user.ID, GameID: game2.ID}
	require.NoError(t, database.Create(&fav1).Error)
	require.NoError(t, database.Create(&fav2).Error)

	err := DeduplicateGames(database)
	require.NoError(t, err)

	// Should only have one favorite pointing to keeper
	var favs []Favorite
	database.Find(&favs)
	require.Len(t, favs, 1)
	assert.Equal(t, game1.ID, favs[0].GameID)
}
