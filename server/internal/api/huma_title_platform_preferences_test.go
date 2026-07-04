package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func TestSetTitlePlatformPreferenceUpsertsPerUser(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	ownerToken := registerAndGetToken(t, router)
	otherToken := createNonOwnerUser(t, router, ownerToken, "platformprefuser", "SecureTestPass!2024")

	snes := mustFindConsole(t, database, "SNES")
	gba := mustFindConsole(t, database, "GBA")
	rootID := uint(1630)
	snesGame := db.Game{ConsoleID: snes.ID, Title: "Preferred Platform", FileName: "pref.sfc", FilePath: "/tmp/pref.sfc", FileSize: 100, TitleRootIGDBID: &rootID}
	gbaGame := db.Game{ConsoleID: gba.ID, Title: "Preferred Platform Advance", FileName: "pref.gba", FilePath: "/tmp/pref.gba", FileSize: 100, TitleRootIGDBID: &rootID}
	require.NoError(t, database.Create(&snesGame).Error)
	require.NoError(t, database.Create(&gbaGame).Error)

	resp := setTitlePlatformPreference(t, router, ownerToken, gbaGame.ID)
	assert.Equal(t, "igdb:1630", resp.TitleKey)
	assert.Equal(t, strconvID(gbaGame.ID), resp.PreferredGameID)

	resp = setTitlePlatformPreference(t, router, ownerToken, snesGame.ID)
	assert.Equal(t, strconvID(snesGame.ID), resp.PreferredGameID)

	ownerID := mustFindUserID(t, database, "apitest")
	otherID := mustFindUserID(t, database, "platformprefuser")

	var ownerPref db.UserTitlePlatformPreference
	require.NoError(t, database.Where("user_id = ? AND title_key = ?", ownerID, "igdb:1630").First(&ownerPref).Error)
	assert.Equal(t, snesGame.ID, ownerPref.PreferredGameID)

	setTitlePlatformPreference(t, router, otherToken, gbaGame.ID)

	var count int64
	database.Model(&db.UserTitlePlatformPreference{}).Where("title_key = ?", "igdb:1630").Count(&count)
	assert.Equal(t, int64(2), count)

	var otherPref db.UserTitlePlatformPreference
	require.NoError(t, database.Where("user_id = ? AND title_key = ?", otherID, "igdb:1630").First(&otherPref).Error)
	assert.Equal(t, gbaGame.ID, otherPref.PreferredGameID)
}

func TestSetTitlePlatformPreferenceRejectsUnknownGame(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPut, "/api/user/title-platform-preferences/999999", bytes.NewReader(nil))
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestToGameResponseMarksSavedPreferredPlatformWhenViewingOtherPlatform(t *testing.T) {
	database, _ := setupTestEnv(t)
	user := db.User{Username: "platformpref", PasswordHash: "hash"}
	require.NoError(t, database.Create(&user).Error)

	snes := mustFindConsole(t, database, "SNES")
	gba := mustFindConsole(t, database, "GBA")
	rootID := uint(1631)
	selected := db.Game{ConsoleID: snes.ID, Title: "Detail Platform", FileName: "detail.sfc", FilePath: "/tmp/detail.sfc", FileSize: 100, TitleRootIGDBID: &rootID}
	preferred := db.Game{ConsoleID: gba.ID, Title: "Detail Platform Advance", FileName: "detail.gba", FilePath: "/tmp/detail.gba", FileSize: 100, TitleRootIGDBID: &rootID}
	require.NoError(t, database.Create(&selected).Error)
	require.NoError(t, database.Create(&preferred).Error)
	require.NoError(t, database.Create(&db.UserTitlePlatformPreference{
		UserID:          user.ID,
		TitleKey:        "igdb:1631",
		PreferredGameID: preferred.ID,
	}).Error)

	var loaded db.Game
	require.NoError(t, database.Preload("Console").First(&loaded, selected.ID).Error)

	resp := ToGameResponse(loaded, database, user.ID)

	assert.Equal(t, strconvID(selected.ID), resp.ID)
	require.Len(t, resp.Platforms, 2)
	assert.Equal(t, []string{"gba", "snes"}, platformConsoleIDs(resp.Platforms))
	assert.Equal(t, []string{strconvID(preferred.ID), strconvID(selected.ID)}, platformGameIDs(resp.Platforms))
	assert.True(t, resp.Platforms[0].IsPreferred)
	assert.False(t, resp.Platforms[1].IsPreferred)
}

func TestSearchUsesSavedPreferredPlatformFromTitleRootSibling(t *testing.T) {
	database, router, token := setupSearchEnv(t)

	snes := mustFindConsole(t, database, "SNES")
	gba := mustFindConsole(t, database, "GBA")
	rootID := uint(1632)
	snesGame := db.Game{ConsoleID: snes.ID, Title: "Final Fantasy VI", FileName: "ff6.sfc", FilePath: "/tmp/ff6.sfc", FileSize: 100, TitleRootIGDBID: &rootID}
	gbaGame := db.Game{ConsoleID: gba.ID, Title: "Final Fantasy III Advance", FileName: "ff3.gba", FilePath: "/tmp/ff3.gba", FileSize: 100, TitleRootIGDBID: &rootID}
	require.NoError(t, database.Create(&snesGame).Error)
	require.NoError(t, database.Create(&gbaGame).Error)

	setTitlePlatformPreference(t, router, token, gbaGame.ID)

	code, resp := searchGet(t, router, token, "/api/search?q=Final+Fantasy+VI")
	require.Equal(t, http.StatusOK, code)
	require.Len(t, resp.Games.Results, 1)

	result := resp.Games.Results[0]
	assert.Equal(t, strconvID(gbaGame.ID), result.ID)
	assert.Equal(t, "gba", result.ConsoleID)
	require.Len(t, result.Platforms, 2)
	assert.Equal(t, []string{"gba", "snes"}, platformConsoleIDs(result.Platforms))
	assert.Equal(t, []string{strconvID(gbaGame.ID), strconvID(snesGame.ID)}, platformGameIDs(result.Platforms))
	assert.True(t, result.Platforms[0].IsPreferred)
}

func TestDedupeGamesByTitleForUserWithMostPlayedPrefersSavedPlatform(t *testing.T) {
	database, _ := setupTestEnv(t)
	user := db.User{Username: "dedupepref", PasswordHash: "hash"}
	require.NoError(t, database.Create(&user).Error)

	snes := mustFindConsole(t, database, "SNES")
	gba := mustFindConsole(t, database, "GBA")
	rootID := uint(1633)
	snesGame := db.Game{ConsoleID: snes.ID, Title: "For You Platform", FileName: "foryou.sfc", FilePath: "/tmp/foryou.sfc", FileSize: 100, TitleRootIGDBID: &rootID}
	gbaGame := db.Game{ConsoleID: gba.ID, Title: "For You Platform Advance", FileName: "foryou.gba", FilePath: "/tmp/foryou.gba", FileSize: 100, TitleRootIGDBID: &rootID}
	require.NoError(t, database.Create(&snesGame).Error)
	require.NoError(t, database.Create(&gbaGame).Error)
	snesGame.Console = snes
	gbaGame.Console = gba
	require.NoError(t, database.Create(&db.UserTitlePlatformPreference{
		UserID:          user.ID,
		TitleKey:        "igdb:1633",
		PreferredGameID: gbaGame.ID,
	}).Error)

	out := dedupeGamesByTitleForUserWithMostPlayed(
		[]db.Game{snesGame, gbaGame},
		database,
		user.ID,
		map[string]uint{"igdb:1633": snesGame.ID},
	)

	require.Len(t, out, 1)
	assert.Equal(t, gbaGame.ID, out[0].ID)
}

func setTitlePlatformPreference(t *testing.T, router http.Handler, token string, gameID uint) TitlePlatformPreferenceResponse {
	t.Helper()
	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPut, fmt.Sprintf("/api/user/title-platform-preferences/%d", gameID), bytes.NewReader(nil))
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code, w.Body.String())

	var resp TitlePlatformPreferenceResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	return resp
}

func mustFindUserID(t *testing.T, database *gorm.DB, username string) uint {
	t.Helper()
	var user db.User
	require.NoError(t, database.Where("username = ?", username).First(&user).Error)
	return user.ID
}
