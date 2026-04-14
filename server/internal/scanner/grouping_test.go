package scanner

import (
	"fmt"
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

func TestRegionPriorityWithOrder(t *testing.T) {
	tests := []struct {
		name   string
		region string
		order  []string
		want   int
	}{
		{"Europe first - Europe", "Europe", []string{"europe", "usa", "world"}, 0},
		{"Europe first - USA", "USA", []string{"europe", "usa", "world"}, 1},
		{"Europe first - World", "World", []string{"europe", "usa", "world"}, 2},
		{"Europe first - Japan", "Japan", []string{"europe", "usa", "world"}, 3},
		{"Japan first - Japan", "Japan", []string{"japan", "usa"}, 0},
		{"Japan first - USA", "USA", []string{"japan", "usa"}, 1},
		{"Japan first - Europe", "Europe", []string{"japan", "usa"}, 2},
		{"empty order", "USA", []string{}, 0},
		{"multi-region matches first", "USA, Europe", []string{"europe", "usa"}, 0},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := regionPriorityWithOrder(tt.region, tt.order)
			assert.Equal(t, tt.want, result)
		})
	}
}

func TestBetterVariantWithRegions(t *testing.T) {
	europeFirst := []string{"europe", "usa", "world"}

	tests := []struct {
		name  string
		a     db.Game
		b     db.Game
		aWins bool
	}{
		{
			name:  "Europe wins over USA with Europe-first order",
			a:     db.Game{Region: "Europe", FileName: "Game (Europe).nes"},
			b:     db.Game{Region: "USA", FileName: "Game (USA).nes"},
			aWins: true,
		},
		{
			name:  "USA loses to Europe with Europe-first order",
			a:     db.Game{Region: "USA", FileName: "Game (USA).nes"},
			b:     db.Game{Region: "Europe", FileName: "Game (Europe).nes"},
			aWins: false,
		},
		{
			name:  "non-prerelease still beats prerelease regardless of region order",
			a:     db.Game{IsPreRelease: false, Region: "USA", FileName: "Game (USA).nes"},
			b:     db.Game{IsPreRelease: true, Region: "Europe", FileName: "Game (Europe) (Beta).nes"},
			aWins: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := betterVariantWithRegions(tt.a, tt.b, europeFirst)
			assert.Equal(t, tt.aWins, result)
		})
	}
}

func TestGroupAndElectPrimariesWithRegions(t *testing.T) {
	database := setupTestDB(t)

	var nes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)

	games := []db.Game{
		{ConsoleID: nes.ID, Title: "Game", FileName: "Game (USA).nes", FilePath: "nes/Game (USA).nes", GroupKey: "game", Region: "USA"},
		{ConsoleID: nes.ID, Title: "Game", FileName: "Game (Europe).nes", FilePath: "nes/Game (Europe).nes", GroupKey: "game", Region: "Europe"},
		{ConsoleID: nes.ID, Title: "Game", FileName: "Game (Japan).nes", FilePath: "nes/Game (Japan).nes", GroupKey: "game", Region: "Japan"},
	}
	for i := range games {
		require.NoError(t, database.Create(&games[i]).Error)
	}

	// Elect with Europe-first order
	require.NoError(t, GroupAndElectPrimariesWithRegions(database, []string{"europe", "usa", "world"}))

	// Europe should be primary
	var europeGame db.Game
	require.NoError(t, database.Where("file_path = ?", "nes/Game (Europe).nes").First(&europeGame).Error)
	assert.True(t, europeGame.IsPrimary, "Europe game should be primary with Europe-first order")

	var usaGame db.Game
	require.NoError(t, database.Where("file_path = ?", "nes/Game (USA).nes").First(&usaGame).Error)
	assert.False(t, usaGame.IsPrimary, "USA game should not be primary with Europe-first order")
}

func TestGroupAndElectPrimariesWithRegions_EmptyFallsBack(t *testing.T) {
	database := setupTestDB(t)

	var nes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)

	games := []db.Game{
		{ConsoleID: nes.ID, Title: "Game", FileName: "Game (USA).nes", FilePath: "nes/Game2 (USA).nes", GroupKey: "game2", Region: "USA"},
		{ConsoleID: nes.ID, Title: "Game", FileName: "Game (Europe).nes", FilePath: "nes/Game2 (Europe).nes", GroupKey: "game2", Region: "Europe"},
	}
	for i := range games {
		require.NoError(t, database.Create(&games[i]).Error)
	}

	// Empty region order falls back to default (USA first)
	require.NoError(t, GroupAndElectPrimariesWithRegions(database, nil))

	var usaGame db.Game
	require.NoError(t, database.Where("file_path = ?", "nes/Game2 (USA).nes").First(&usaGame).Error)
	assert.True(t, usaGame.IsPrimary, "USA should be primary with nil region order (default)")
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

func TestMergeGroupsByIGDBID(t *testing.T) {
	database := setupTestDB(t)

	var scd db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SCD").First(&scd).Error)

	// Two games with different titles/GroupKeys but same IGDB ID
	game1 := db.Game{
		ConsoleID: scd.ID,
		Title:     "Sonic CD",
		FileName:  "Sonic CD (USA).cue",
		FilePath:  "segacd/Sonic CD (USA).cue",
		GroupKey:  "sonic cd",
		Region:    "USA",
		ScraperID: "igdb:2071",
	}
	game2 := db.Game{
		ConsoleID: scd.ID,
		Title:     "Sonic the Hedgehog CD",
		FileName:  "Sonic the Hedgehog CD (Japan).cue",
		FilePath:  "segacd/Sonic the Hedgehog CD (Japan).cue",
		GroupKey:  "sonic hedgehog cd",
		Region:    "Japan",
		ScraperID: "igdb:2071",
	}
	require.NoError(t, database.Create(&game1).Error)
	require.NoError(t, database.Create(&game2).Error)

	// Before merge: both should be in different groups
	require.NotEqual(t, game1.GroupKey, game2.GroupKey)

	// Run election first — each gets elected primary in its own group
	require.NoError(t, GroupAndElectPrimaries(database))

	var pre1, pre2 db.Game
	database.First(&pre1, game1.ID)
	database.First(&pre2, game2.ID)
	assert.True(t, pre1.IsPrimary, "game1 should be primary before merge")
	assert.True(t, pre2.IsPrimary, "game2 should be primary before merge")

	// Run IGDB merge
	merged, err := MergeGroupsByIGDBID(database)
	require.NoError(t, err)
	assert.Equal(t, 1, merged, "should have merged 1 game into a different group")

	// After merge: both should share the same GroupKey
	var post1, post2 db.Game
	database.First(&post1, game1.ID)
	database.First(&post2, game2.ID)
	assert.Equal(t, post1.GroupKey, post2.GroupKey, "both games should share the same GroupKey after merge")

	// Only one should be primary (USA preferred)
	primaryCount := 0
	if post1.IsPrimary {
		primaryCount++
	}
	if post2.IsPrimary {
		primaryCount++
	}
	assert.Equal(t, 1, primaryCount, "exactly one game should be primary after merge")

	// USA version should be primary (region preference)
	assert.True(t, post1.IsPrimary, "USA version should be primary")
	assert.False(t, post2.IsPrimary, "Japan version should not be primary")
}

func TestMergeGroupsByIGDBID_SkipsUnrelatedTitles(t *testing.T) {
	database := setupTestDB(t)

	var dos db.Console
	require.NoError(t, database.Where("abbreviation = ?", "DOS").First(&dos).Error)

	// Two games with completely different titles but same IGDB ID (false match)
	game1 := db.Game{
		ConsoleID: dos.ID,
		Title:     "Alley Cat",
		FileName:  "Alley Cat.zip",
		FilePath:  "dos/Alley Cat.zip",
		GroupKey:  "alley cat",
		ScraperID: "igdb:999",
	}
	game2 := db.Game{
		ConsoleID: dos.ID,
		Title:     "Space Invaders",
		FileName:  "Space Invaders.zip",
		FilePath:  "dos/Space Invaders.zip",
		GroupKey:  "space invaders",
		ScraperID: "igdb:999",
	}
	require.NoError(t, database.Create(&game1).Error)
	require.NoError(t, database.Create(&game2).Error)
	require.NoError(t, GroupAndElectPrimaries(database))

	merged, err := MergeGroupsByIGDBID(database)
	require.NoError(t, err)
	assert.Equal(t, 0, merged, "should NOT merge games with unrelated titles")

	// Both should still be primary in their own groups
	var post1, post2 db.Game
	database.First(&post1, game1.ID)
	database.First(&post2, game2.ID)
	assert.True(t, post1.IsPrimary)
	assert.True(t, post2.IsPrimary)
	assert.NotEqual(t, post1.GroupKey, post2.GroupKey, "group keys should remain different")
}

func TestMergeGroupsByIGDBID_AvoidsCorruptedGroupKey(t *testing.T) {
	database := setupTestDB(t)

	var gc db.Console
	require.NoError(t, database.Where("abbreviation = ?", "GC").First(&gc).Error)

	// Simulate a corrupted catch-all group: "bad group" has games with many different IGDB IDs
	for i := 0; i < 5; i++ {
		database.Create(&db.Game{
			ConsoleID: gc.ID,
			Title:     fmt.Sprintf("Unrelated Game %d", i),
			FileName:  fmt.Sprintf("Unrelated Game %d.rvz", i),
			FilePath:  fmt.Sprintf("gc/Unrelated Game %d.rvz", i),
			GroupKey:  "bad group",
			ScraperID: fmt.Sprintf("igdb:%d", 5000+i),
		})
	}

	// One of the Smash Bros games is in the corrupted group
	smashJP := db.Game{
		ConsoleID: gc.ID,
		Title:     "Super Smash Bros. Melee",
		FileName:  "Dairantou Smash Brothers DX (Japan).rvz",
		FilePath:  "gc/Dairantou Smash Brothers DX (Japan).rvz",
		GroupKey:  "bad group",
		ScraperID: "igdb:1627",
	}
	// The other is in its own clean group
	smashUSA := db.Game{
		ConsoleID: gc.ID,
		Title:     "Super Smash Bros. Melee",
		FileName:  "Super Smash Bros. Melee (USA).rvz",
		FilePath:  "gc/Super Smash Bros. Melee (USA).rvz",
		GroupKey:  "super smash bros melee",
		ScraperID: "igdb:1627",
	}
	require.NoError(t, database.Create(&smashJP).Error)
	require.NoError(t, database.Create(&smashUSA).Error)
	require.NoError(t, GroupAndElectPrimaries(database))

	merged, err := MergeGroupsByIGDBID(database)
	require.NoError(t, err)
	assert.Equal(t, 1, merged)

	// The canonical key should be "super smash bros melee" (the pure group),
	// NOT "bad group" (which contains games with other IGDB IDs).
	var postJP, postUSA db.Game
	database.First(&postJP, smashJP.ID)
	database.First(&postUSA, smashUSA.ID)
	assert.Equal(t, "super smash bros melee", postJP.GroupKey, "should merge into the pure group")
	assert.Equal(t, "super smash bros melee", postUSA.GroupKey)
}

func TestTitlesRelated(t *testing.T) {
	tests := []struct {
		name   string
		titles []string
		want   bool
	}{
		{"same title", []string{"Sonic CD", "Sonic CD"}, true},
		{"regional variants", []string{"Sonic CD", "Sonic the Hedgehog CD"}, true},
		{"completely different", []string{"Alley Cat", "Space Invaders"}, false},
		{"one shared word", []string{"Super Mario Bros", "Super Smash Bros"}, true},
		{"no shared significant word", []string{"A Game", "B Game"}, true}, // "game" is shared
		{"single game", []string{"Test"}, true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			games := make([]db.Game, len(tt.titles))
			for i, title := range tt.titles {
				games[i] = db.Game{Title: title}
			}
			assert.Equal(t, tt.want, titlesRelated(games))
		})
	}
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

// TestFullRegroupingIsIdempotent verifies that running GroupAndElectPrimaries
// followed by MergeGroupsByIGDBID produces the correct result regardless of
// starting state. This is the core "stateless" guarantee.
func TestFullRegroupingIsIdempotent(t *testing.T) {
	database := setupTestDB(t)

	var gc db.Console
	require.NoError(t, database.Where("abbreviation = ?", "GC").First(&gc).Error)

	// Set up: 3 Smash Bros games with different filenames, same IGDB ID,
	// and one starts in a corrupted catch-all group with unrelated games.
	smashUSA := db.Game{
		ConsoleID: gc.ID, Title: "Super Smash Bros. Melee",
		FileName: "Super Smash Bros. Melee (USA) (En,Ja).rvz",
		FilePath: "gc/Super Smash Bros. Melee (USA) (En,Ja).rvz",
		GroupKey: "corrupted group", ScraperID: "igdb:1627",
	}
	smashJP := db.Game{
		ConsoleID: gc.ID, Title: "Super Smash Bros. Melee",
		FileName: "Dairantou Smash Brothers DX (Japan) (En,Ja).rvz",
		FilePath: "gc/Dairantou Smash Brothers DX (Japan) (En,Ja).rvz",
		GroupKey: "corrupted group", ScraperID: "igdb:1627",
	}
	smashDemo := db.Game{
		ConsoleID: gc.ID, Title: "Super Smash Bros. Melee",
		FileName: "Dairantou Smash Brothers DX (Japan) (Taikenban).rvz",
		FilePath: "gc/Dairantou Smash Brothers DX (Japan) (Taikenban).rvz",
		GroupKey: "dairantou smash brothers dx", ScraperID: "igdb:1627",
	}
	// Unrelated games in the corrupted group
	unrelated1 := db.Game{
		ConsoleID: gc.ID, Title: "Ace Golf",
		FileName: "Ace Golf (Europe).rvz",
		FilePath: "gc/Ace Golf (Europe).rvz",
		GroupKey: "corrupted group", ScraperID: "igdb:9999",
	}
	unrelated2 := db.Game{
		ConsoleID: gc.ID, Title: "Beach Spikers",
		FileName: "Beach Spikers (USA).rvz",
		FilePath: "gc/Beach Spikers (USA).rvz",
		GroupKey: "corrupted group", ScraperID: "igdb:8888",
	}
	for _, g := range []*db.Game{&smashUSA, &smashJP, &smashDemo, &unrelated1, &unrelated2} {
		require.NoError(t, database.Create(g).Error)
	}

	// Run the full pipeline: recompute keys, regroup, then merge by IGDB ID
	require.NoError(t, RecomputeGroupKeys(database))
	require.NoError(t, GroupAndElectPrimaries(database))
	merged, err := MergeGroupsByIGDBID(database)
	require.NoError(t, err)

	// Reload all games
	var postUSA, postJP, postDemo, postAce, postBeach db.Game
	database.First(&postUSA, smashUSA.ID)
	database.First(&postJP, smashJP.ID)
	database.First(&postDemo, smashDemo.ID)
	database.First(&postAce, unrelated1.ID)
	database.First(&postBeach, unrelated2.ID)

	// All 3 Smash Bros games should be in the same group
	assert.Equal(t, postUSA.GroupKey, postJP.GroupKey, "Smash USA and JP should be grouped")
	assert.Equal(t, postUSA.GroupKey, postDemo.GroupKey, "Smash USA and Demo should be grouped")
	assert.True(t, merged > 0, "should have merged at least one game")

	// Unrelated games should NOT be in the Smash group
	assert.NotEqual(t, postUSA.GroupKey, postAce.GroupKey, "Ace Golf should not be in Smash group")
	assert.NotEqual(t, postUSA.GroupKey, postBeach.GroupKey, "Beach Spikers should not be in Smash group")

	// Ace Golf and Beach Spikers should be in their own groups (ungrouped from each other too)
	assert.NotEqual(t, postAce.GroupKey, postBeach.GroupKey, "unrelated games should be in separate groups")

	// Exactly one Smash game should be primary
	smashPrimaries := 0
	for _, g := range []db.Game{postUSA, postJP, postDemo} {
		if g.IsPrimary {
			smashPrimaries++
		}
	}
	assert.Equal(t, 1, smashPrimaries, "exactly one Smash game should be primary")

	// Run the whole thing again — result should be identical (idempotent)
	require.NoError(t, RecomputeGroupKeys(database))
	require.NoError(t, GroupAndElectPrimaries(database))
	_, err = MergeGroupsByIGDBID(database)
	require.NoError(t, err)

	var postUSA2, postJP2, postDemo2, postAce2, postBeach2 db.Game
	database.First(&postUSA2, smashUSA.ID)
	database.First(&postJP2, smashJP.ID)
	database.First(&postDemo2, smashDemo.ID)
	database.First(&postAce2, unrelated1.ID)
	database.First(&postBeach2, unrelated2.ID)

	assert.Equal(t, postUSA.GroupKey, postUSA2.GroupKey, "idempotent: USA group unchanged")
	assert.Equal(t, postJP.GroupKey, postJP2.GroupKey, "idempotent: JP group unchanged")
	assert.Equal(t, postDemo.GroupKey, postDemo2.GroupKey, "idempotent: Demo group unchanged")
	assert.Equal(t, postAce.GroupKey, postAce2.GroupKey, "idempotent: Ace group unchanged")
	assert.Equal(t, postBeach.GroupKey, postBeach2.GroupKey, "idempotent: Beach group unchanged")
}
