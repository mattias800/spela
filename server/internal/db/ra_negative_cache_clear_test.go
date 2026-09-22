package db

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestClearRANegativeCache_WholeLibrary(t *testing.T) {
	database := setupBackfillDB(t)

	console := Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)

	cached := Game{ConsoleID: console.ID, Title: "No Match", FileName: "a.nes", FilePath: "roms/a.nes",
		RAHashChecked: true, RAGameID: 0}
	resolved := Game{ConsoleID: console.ID, Title: "Matched", FileName: "b.nes", FilePath: "roms/b.nes",
		RAHashChecked: true, RAGameID: 42}
	untouched := Game{ConsoleID: console.ID, Title: "Never Checked", FileName: "c.nes", FilePath: "roms/c.nes"}
	require.NoError(t, database.Create(&cached).Error)
	require.NoError(t, database.Create(&resolved).Error)
	require.NoError(t, database.Create(&untouched).Error)

	n, err := ClearRANegativeCache(database, 0)
	require.NoError(t, err)
	assert.Equal(t, int64(1), n, "only the negative-cached game should be re-opened")

	var got Game
	require.NoError(t, database.First(&got, cached.ID).Error)
	assert.False(t, got.RAHashChecked)

	var gotResolved Game
	require.NoError(t, database.First(&gotResolved, resolved.ID).Error)
	assert.True(t, gotResolved.RAHashChecked, "a resolved game is not negative-cached and must be left alone")
	assert.Equal(t, uint(42), gotResolved.RAGameID)
}

func TestClearRANegativeCache_ScopedToConsole(t *testing.T) {
	database := setupBackfillDB(t)

	nes := Console{Name: "NES", Abbreviation: "nes", Playable: true}
	snes := Console{Name: "SNES", Abbreviation: "snes", Playable: true}
	require.NoError(t, database.Create(&nes).Error)
	require.NoError(t, database.Create(&snes).Error)

	nesGame := Game{ConsoleID: nes.ID, Title: "NES", FileName: "a.nes", FilePath: "roms/a.nes",
		RAHashChecked: true, RAGameID: 0}
	snesGame := Game{ConsoleID: snes.ID, Title: "SNES", FileName: "b.sfc", FilePath: "roms/b.sfc",
		RAHashChecked: true, RAGameID: 0}
	require.NoError(t, database.Create(&nesGame).Error)
	require.NoError(t, database.Create(&snesGame).Error)

	n, err := ClearRANegativeCache(database, nes.ID)
	require.NoError(t, err)
	assert.Equal(t, int64(1), n)

	var gotNES Game
	require.NoError(t, database.First(&gotNES, nesGame.ID).Error)
	assert.False(t, gotNES.RAHashChecked)

	var gotSNES Game
	require.NoError(t, database.First(&gotSNES, snesGame.ID).Error)
	assert.True(t, gotSNES.RAHashChecked, "a console-scoped re-check must not touch other consoles")
}

func TestClearRANegativeCache_NothingToClear(t *testing.T) {
	database := setupBackfillDB(t)
	n, err := ClearRANegativeCache(database, 0)
	require.NoError(t, err)
	assert.Equal(t, int64(0), n)
}
