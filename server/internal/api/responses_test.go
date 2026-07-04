package api

import (
	"strconv"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// TestToGameResponseSurfacesConsoleSaveStatePolicy verifies that the
// per-game payload carries the parent console's tier so the player
// can render the first-launch prompt at game-launch time without a
// second round-trip. See #804 phase 4b.
func TestToGameResponseSurfacesConsoleSaveStatePolicy(t *testing.T) {
	cases := []struct {
		name       string
		console    db.Console
		wantPolicy string
	}{
		{
			name: "large GameCube",
			console: db.Console{
				ID:              1,
				Name:            "GameCube",
				Abbreviation:    "GC",
				CoverAspect:     "1:1",
				SaveStatePolicy: db.SaveStatePolicyLarge,
			},
			wantPolicy: "large",
		},
		{
			name: "small NES",
			console: db.Console{
				ID:              2,
				Name:            "NES",
				Abbreviation:    "NES",
				CoverAspect:     "5:7",
				SaveStatePolicy: db.SaveStatePolicySmall,
			},
			wantPolicy: "small",
		},
		{
			name: "console with no policy falls back to small",
			console: db.Console{
				ID:           3,
				Name:         "Custom",
				Abbreviation: "CUST",
				CoverAspect:  "1:1",
			},
			wantPolicy: "small",
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			resp := toGameResponseWithData(
				db.Game{ID: 99, Console: tc.console, ConsoleID: tc.console.ID, Title: "Test"},
				nil,
			)
			assert.Equal(t, tc.wantPolicy, resp.ConsoleSaveStatePolicy)
		})
	}
}

func TestToGameResponseAlwaysIncludesSelfPlatform(t *testing.T) {
	console := db.Console{
		ID:              1,
		Name:            "Super Nintendo",
		Abbreviation:    "SNES",
		CoverAspect:     "5:7",
		SaveStatePolicy: db.SaveStatePolicySmall,
	}
	resp := toGameResponseWithData(
		db.Game{ID: 99, Console: console, ConsoleID: console.ID, Title: "Test"},
		nil,
	)

	require.Len(t, resp.Platforms, 1)
	assert.Equal(t, "99", resp.Platforms[0].GameID)
	assert.Equal(t, "snes", resp.Platforms[0].ConsoleID)
	assert.Equal(t, "Super Nintendo", resp.Platforms[0].ConsoleName)
	assert.True(t, resp.Platforms[0].IsPreferred)
}

func TestToGameResponseLoadsTitleRootPlatforms(t *testing.T) {
	database, _ := setupTestEnv(t)
	snes := mustFindConsole(t, database, "SNES")
	gba := mustFindConsole(t, database, "GBA")
	nes := mustFindConsole(t, database, "NES")

	rootID := uint(1234)
	selected := db.Game{ConsoleID: snes.ID, Title: "Final Fantasy VI", FileName: "ff6.sfc", FilePath: "/roms/snes/ff6.sfc", TitleRootIGDBID: &rootID}
	sibling := db.Game{ConsoleID: gba.ID, Title: "Final Fantasy VI Advance", FileName: "ff6.gba", FilePath: "/roms/gba/ff6.gba", TitleRootIGDBID: &rootID}
	otherRoot := uint(9999)
	unrelated := db.Game{ConsoleID: nes.ID, Title: "Final Fantasy", FileName: "ff.nes", FilePath: "/roms/nes/ff.nes", TitleRootIGDBID: &otherRoot}
	require.NoError(t, database.Create(&selected).Error)
	require.NoError(t, database.Create(&sibling).Error)
	require.NoError(t, database.Create(&unrelated).Error)

	var loaded db.Game
	require.NoError(t, database.Preload("Console").First(&loaded, selected.ID).Error)

	resp := ToGameResponse(loaded, database, 0)

	require.Len(t, resp.Platforms, 2)
	assert.Equal(t, strconvID(selected.ID), resp.Platforms[0].GameID)
	assert.True(t, resp.Platforms[0].IsPreferred)
	assert.Equal(t, []string{"snes", "gba"}, platformConsoleIDs(resp.Platforms))
	assert.Equal(t, []string{strconvID(selected.ID), strconvID(sibling.ID)}, platformGameIDs(resp.Platforms))
}

func TestToGameResponsesGroupsNormalizedFallbackPlatformsWithinBatch(t *testing.T) {
	database, _ := setupTestEnv(t)
	snes := mustFindConsole(t, database, "SNES")
	gba := mustFindConsole(t, database, "GBA")
	nes := mustFindConsole(t, database, "NES")

	selected := db.Game{ConsoleID: snes.ID, Title: "Street Fighter II (USA)", FileName: "sf2.sfc", FilePath: "/roms/snes/sf2.sfc"}
	sibling := db.Game{ConsoleID: gba.ID, Title: "Street Fighter II [Europe]", FileName: "sf2.gba", FilePath: "/roms/gba/sf2.gba"}
	unrelated := db.Game{ConsoleID: nes.ID, Title: "Street Fighter III", FileName: "sf3.nes", FilePath: "/roms/nes/sf3.nes"}
	require.NoError(t, database.Create(&selected).Error)
	require.NoError(t, database.Create(&sibling).Error)
	require.NoError(t, database.Create(&unrelated).Error)

	var selectedLoaded db.Game
	require.NoError(t, database.Preload("Console").First(&selectedLoaded, selected.ID).Error)
	var siblingLoaded db.Game
	require.NoError(t, database.Preload("Console").First(&siblingLoaded, sibling.ID).Error)

	soloResp := ToGameResponse(selectedLoaded, database, 0)
	require.Len(t, soloResp.Platforms, 1)

	responses := ToGameResponses([]db.Game{selectedLoaded, siblingLoaded}, database, 0)
	require.Len(t, responses, 2)

	require.Len(t, responses[0].Platforms, 2)
	assert.Equal(t, []string{"snes", "gba"}, platformConsoleIDs(responses[0].Platforms))
	assert.Equal(t, []string{strconvID(selected.ID), strconvID(sibling.ID)}, platformGameIDs(responses[0].Platforms))
	assert.True(t, responses[0].Platforms[0].IsPreferred)

	require.Len(t, responses[1].Platforms, 2)
	assert.Equal(t, []string{"gba", "snes"}, platformConsoleIDs(responses[1].Platforms))
	assert.Equal(t, []string{strconvID(sibling.ID), strconvID(selected.ID)}, platformGameIDs(responses[1].Platforms))
	assert.True(t, responses[1].Platforms[0].IsPreferred)
}

func TestToGameResponseWithDataUsesPreloadedPlatforms(t *testing.T) {
	database, _ := setupTestEnv(t)
	snes := mustFindConsole(t, database, "SNES")
	gba := mustFindConsole(t, database, "GBA")

	rootID := uint(4321)
	selected := db.Game{ConsoleID: snes.ID, Title: "Secret of Mana", FileName: "som.sfc", FilePath: "/roms/snes/som.sfc", TitleRootIGDBID: &rootID}
	sibling := db.Game{ConsoleID: gba.ID, Title: "Secret of Mana Advance", FileName: "som.gba", FilePath: "/roms/gba/som.gba", TitleRootIGDBID: &rootID}
	require.NoError(t, database.Create(&selected).Error)
	require.NoError(t, database.Create(&sibling).Error)

	var selectedLoaded db.Game
	require.NoError(t, database.Preload("Console").First(&selectedLoaded, selected.ID).Error)
	var siblingLoaded db.Game
	require.NoError(t, database.Preload("Console").First(&siblingLoaded, sibling.ID).Error)

	data := loadGameResponseData(database, 0, []db.Game{selectedLoaded, siblingLoaded})
	resp := toGameResponseWithData(selectedLoaded, &data)

	require.Len(t, resp.Platforms, 2)
	assert.Equal(t, []string{"snes", "gba"}, platformConsoleIDs(resp.Platforms))
}

// TestToGameResponseFallsBackForOrphanedGame covers the rare case
// where a game has no joined console (Console.ID == 0). The default
// must still be a known tier value so the player switch is total.
func TestToGameResponseFallsBackForOrphanedGame(t *testing.T) {
	resp := toGameResponseWithData(
		db.Game{ID: 99, Title: "Orphan"},
		nil,
	)
	assert.Equal(t, "small", resp.ConsoleSaveStatePolicy)
	require.Len(t, resp.Platforms, 1)
	assert.True(t, resp.Platforms[0].IsPreferred)
}

// TestToConsoleResponseSurfacesSaveStatePolicy pins the wire shape for
// #804 phase 3: ToConsoleResponse must always emit a non-empty
// "saveStatePolicy" so the player UI can switch unconditionally.
//
// Two paths matter:
//
//	(a) row has a tier from the seed → echoed verbatim.
//	(b) row has empty SaveStatePolicy (manually-inserted custom row,
//	    or a future schema slip) → fallback to "small" rather than
//	    leaking an empty string to clients that expect a closed enum.
func TestToConsoleResponseSurfacesSaveStatePolicy(t *testing.T) {
	cases := []struct {
		name string
		in   db.SaveStatePolicy
		want string
	}{
		{"large GameCube", db.SaveStatePolicyLarge, "large"},
		{"medium PSX", db.SaveStatePolicyMedium, "medium"},
		{"small NES", db.SaveStatePolicySmall, "small"},
		{"empty falls back", "", "small"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			resp := ToConsoleResponse(db.Console{
				Abbreviation:    "TEST",
				Name:            "Test Console",
				Extensions:      "",
				CoverAspect:     "1:1",
				SaveStatePolicy: tc.in,
			})
			assert.Equal(t, tc.want, resp.SaveStatePolicy)
		})
	}
}

func mustFindConsole(t *testing.T, database *gorm.DB, abbreviation string) db.Console {
	t.Helper()
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", abbreviation).First(&console).Error)
	return console
}

func platformConsoleIDs(platforms []GamePlatformResponse) []string {
	ids := make([]string, len(platforms))
	for i, platform := range platforms {
		ids[i] = platform.ConsoleID
	}
	return ids
}

func platformGameIDs(platforms []GamePlatformResponse) []string {
	ids := make([]string, len(platforms))
	for i, platform := range platforms {
		ids[i] = platform.GameID
	}
	return ids
}

func strconvID(id uint) string {
	return strconv.FormatUint(uint64(id), 10)
}
