package scanner

import (
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestBetterVariant(t *testing.T) {
	tests := []struct {
		name   string
		a      db.Game
		b      db.Game
		aWins  bool
	}{
		{
			name:  "non-prerelease beats prerelease",
			a:     db.Game{IsPreRelease: false, FileName: "Game (USA).nes"},
			b:     db.Game{IsPreRelease: true, FileName: "Game (USA) (Beta).nes"},
			aWins: true,
		},
		{
			name:  "prerelease loses to non-prerelease",
			a:     db.Game{IsPreRelease: true, FileName: "Game (USA) (Beta).nes"},
			b:     db.Game{IsPreRelease: false, FileName: "Game (USA).nes"},
			aWins: false,
		},
		{
			name:  "USA beats Europe",
			a:     db.Game{Region: "USA", FileName: "Game (USA).nes"},
			b:     db.Game{Region: "Europe", FileName: "Game (Europe).nes"},
			aWins: true,
		},
		{
			name:  "USA beats World",
			a:     db.Game{Region: "USA", FileName: "Game (USA).nes"},
			b:     db.Game{Region: "World", FileName: "Game (World).nes"},
			aWins: true,
		},
		{
			name:  "World beats Europe",
			a:     db.Game{Region: "World", FileName: "Game (World).nes"},
			b:     db.Game{Region: "Europe", FileName: "Game (Europe).nes"},
			aWins: true,
		},
		{
			name:  "Europe beats Japan",
			a:     db.Game{Region: "Europe", FileName: "Game (Europe).nes"},
			b:     db.Game{Region: "Japan", FileName: "Game (Japan).nes"},
			aWins: true,
		},
		{
			name:  "later revision wins",
			a:     db.Game{Region: "USA", Revision: "Rev B", FileName: "Game (USA) (Rev B).nes"},
			b:     db.Game{Region: "USA", Revision: "Rev A", FileName: "Game (USA) (Rev A).nes"},
			aWins: true,
		},
		{
			name:  "revision beats no revision",
			a:     db.Game{Region: "USA", Revision: "Rev A", FileName: "Game (USA) (Rev A).nes"},
			b:     db.Game{Region: "USA", Revision: "", FileName: "Game (USA).nes"},
			aWins: true,
		},
		{
			name:  "version comparison",
			a:     db.Game{Region: "USA", Revision: "v1.1", FileName: "Game (USA) (v1.1).nes"},
			b:     db.Game{Region: "USA", Revision: "v1.0", FileName: "Game (USA) (v1.0).nes"},
			aWins: true,
		},
		{
			name:  "has metadata wins",
			a:     db.Game{Region: "USA", Description: "A cool game", FileName: "Game (USA).nes"},
			b:     db.Game{Region: "USA", FileName: "Game (USA).nes"},
			aWins: true,
		},
		{
			name:  "has cover wins",
			a:     db.Game{Region: "USA", CoverURL: "http://example.com/cover.png", FileName: "Game (USA).nes"},
			b:     db.Game{Region: "USA", FileName: "Game (USA).nes"},
			aWins: true,
		},
		{
			name:  "verified beats unverified",
			a:     db.Game{Region: "USA", VerificationStatus: "verified", FileName: "Game (USA).nes"},
			b:     db.Game{Region: "USA", VerificationStatus: "", FileName: "Game (USA).nes"},
			aWins: true,
		},
		{
			name:  "shorter filename wins as tiebreaker",
			a:     db.Game{Region: "USA", FileName: "Game (USA).nes"},
			b:     db.Game{Region: "USA", FileName: "Game (USA) [!].nes"},
			aWins: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := betterVariant(tt.a, tt.b)
			assert.Equal(t, tt.aWins, result)
		})
	}
}

func TestRevisionOrder(t *testing.T) {
	tests := []struct {
		name     string
		revision string
		want     int
	}{
		{"empty", "", 0},
		{"Rev A", "Rev A", 1},
		{"Rev B", "Rev B", 2},
		{"Rev 1", "Rev 1", 1},
		{"Rev 2", "Rev 2", 2},
		{"v1.0", "v1.0", 100},
		{"v1.1", "v1.1", 101},
		{"v2.0", "v2.0", 200},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := revisionOrder(tt.revision)
			assert.Equal(t, tt.want, result)
		})
	}
}

func TestRegionPriority(t *testing.T) {
	tests := []struct {
		name   string
		region string
		want   int
	}{
		{"USA", "USA", 0},
		{"USA, Europe", "USA, Europe", 0},
		{"World", "World", 1},
		{"Europe", "Europe", 2},
		{"Japan", "Japan", 3},
		{"empty", "", 3},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := regionPriority(tt.region)
			assert.Equal(t, tt.want, result)
		})
	}
}

func TestGroupAndElectPrimaries(t *testing.T) {
	database := setupTestDB(t)

	// Get NES console
	var nes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)

	// Create games in the same group
	games := []db.Game{
		{ConsoleID: nes.ID, Title: "Super Mario Bros", FileName: "Super Mario Bros (USA).nes", FilePath: "nes/Super Mario Bros (USA).nes", GroupKey: "super mario bros", Region: "USA"},
		{ConsoleID: nes.ID, Title: "Super Mario Bros", FileName: "Super Mario Bros (Europe).nes", FilePath: "nes/Super Mario Bros (Europe).nes", GroupKey: "super mario bros", Region: "Europe"},
		{ConsoleID: nes.ID, Title: "Super Mario Bros", FileName: "Super Mario Bros (Japan).nes", FilePath: "nes/Super Mario Bros (Japan).nes", GroupKey: "super mario bros", Region: "Japan"},
		{ConsoleID: nes.ID, Title: "Super Mario Bros", FileName: "Super Mario Bros (USA) (Beta).nes", FilePath: "nes/Super Mario Bros (USA) (Beta).nes", GroupKey: "super mario bros", Region: "USA", IsPreRelease: true, Tags: "beta"},
	}
	for i := range games {
		require.NoError(t, database.Create(&games[i]).Error)
	}

	// Run election
	require.NoError(t, GroupAndElectPrimaries(database))

	// Verify: USA non-prerelease should be primary
	var usaGame db.Game
	require.NoError(t, database.Where("file_path = ?", "nes/Super Mario Bros (USA).nes").First(&usaGame).Error)
	assert.True(t, usaGame.IsPrimary, "USA game should be primary")
	assert.Nil(t, usaGame.PrimaryGameID, "primary game should not point to another game")

	// Verify: Europe should not be primary and should point to USA
	var europeGame db.Game
	require.NoError(t, database.Where("file_path = ?", "nes/Super Mario Bros (Europe).nes").First(&europeGame).Error)
	assert.False(t, europeGame.IsPrimary, "Europe game should not be primary")
	assert.NotNil(t, europeGame.PrimaryGameID, "non-primary should have PrimaryGameID")
	assert.Equal(t, usaGame.ID, *europeGame.PrimaryGameID)

	// Verify: Beta should not be primary
	var betaGame db.Game
	require.NoError(t, database.Where("file_path = ?", "nes/Super Mario Bros (USA) (Beta).nes").First(&betaGame).Error)
	assert.False(t, betaGame.IsPrimary, "beta game should not be primary")
}

func TestGroupAndElectPrimaries_RevisionPreference(t *testing.T) {
	database := setupTestDB(t)

	var snes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&snes).Error)

	games := []db.Game{
		{ConsoleID: snes.ID, Title: "Donkey Kong Country", FileName: "Donkey Kong Country (USA).sfc", FilePath: "snes/Donkey Kong Country (USA).sfc", GroupKey: "donkey kong country", Region: "USA", Revision: ""},
		{ConsoleID: snes.ID, Title: "Donkey Kong Country", FileName: "Donkey Kong Country (USA) (Rev A).sfc", FilePath: "snes/Donkey Kong Country (USA) (Rev A).sfc", GroupKey: "donkey kong country", Region: "USA", Revision: "Rev A"},
		{ConsoleID: snes.ID, Title: "Donkey Kong Country", FileName: "Donkey Kong Country (USA) (Rev B).sfc", FilePath: "snes/Donkey Kong Country (USA) (Rev B).sfc", GroupKey: "donkey kong country", Region: "USA", Revision: "Rev B"},
	}
	for i := range games {
		require.NoError(t, database.Create(&games[i]).Error)
	}

	require.NoError(t, GroupAndElectPrimaries(database))

	// Rev B should win (latest revision)
	var revB db.Game
	require.NoError(t, database.Where("file_path = ?", "snes/Donkey Kong Country (USA) (Rev B).sfc").First(&revB).Error)
	assert.True(t, revB.IsPrimary, "Rev B should be primary")
}

func TestGroupAndElectPrimaries_SingleGameGroup(t *testing.T) {
	database := setupTestDB(t)

	var nes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)

	game := db.Game{
		ConsoleID: nes.ID,
		Title:     "Unique Game",
		FileName:  "Unique Game (USA).nes",
		FilePath:  "nes/Unique Game (USA).nes",
		GroupKey:  "unique game",
		Region:    "USA",
	}
	require.NoError(t, database.Create(&game).Error)

	require.NoError(t, GroupAndElectPrimaries(database))

	var result db.Game
	require.NoError(t, database.First(&result, game.ID).Error)
	assert.True(t, result.IsPrimary, "single game in group should be primary")
}

func TestReElectPrimaryForGroup(t *testing.T) {
	database := setupTestDB(t)

	var nes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)

	games := []db.Game{
		{ConsoleID: nes.ID, Title: "Game", FileName: "Game (USA).nes", FilePath: "nes/Game (USA).nes", GroupKey: "game", Region: "USA", IsPrimary: true},
		{ConsoleID: nes.ID, Title: "Game", FileName: "Game (Europe).nes", FilePath: "nes/Game (Europe).nes", GroupKey: "game", Region: "Europe", IsPrimary: false, PrimaryGameID: nil},
	}
	for i := range games {
		require.NoError(t, database.Create(&games[i]).Error)
	}
	// Set PrimaryGameID on Europe to point to USA
	database.Model(&games[1]).Update("primary_game_id", games[0].ID)

	// Simulate removing the primary (USA) game
	database.Unscoped().Delete(&games[0])

	// Re-elect
	require.NoError(t, ReElectPrimaryForGroup(database, nes.ID, "game"))

	// Europe should now be primary
	var europeGame db.Game
	require.NoError(t, database.Where("file_path = ?", "nes/Game (Europe).nes").First(&europeGame).Error)
	assert.True(t, europeGame.IsPrimary, "Europe game should be elected as new primary")
}
