package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"

	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func createAdminUser(t *testing.T, database *gorm.DB) (db.User, string) {
	t.Helper()
	user := db.User{
		Username:     "admin",
		Email:        "admin@test.com",
		PasswordHash: "unused",
		Role:         "owner",
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, testJWTSecret)
	require.NoError(t, err)
	return user, token
}

func createTestGame(t *testing.T, database *gorm.DB) db.Game {
	t.Helper()
	var console db.Console
	database.Where("abbreviation = ?", "NES").First(&console)
	if console.ID == 0 {
		console = db.Console{Name: "NES", Abbreviation: "NES", Extensions: "nes"}
		require.NoError(t, database.Create(&console).Error)
	}

	game := db.Game{
		ConsoleID:        console.ID,
		Title:            "Test Game",
		FileName:         "Test Game.nes",
		LibRetroCoverURL: "NES/1/boxart-libretro.png",
		IGDBCoverURL:     "NES/1/boxart-igdb.jpg",
		CoverURL:         "NES/1/boxart-libretro.png",
	}
	require.NoError(t, database.Create(&game).Error)
	return game
}

func TestGetGameCovers_ReturnsBothSources(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)
	game := createTestGame(t, database)

	req := httptest.NewRequest("GET", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/covers", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp struct {
		Active string `json:"active"`
		Covers []struct {
			Source string `json:"source"`
			URL    string `json:"url"`
		} `json:"covers"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "libretro", resp.Active)
	assert.Len(t, resp.Covers, 2)
	assert.Equal(t, "libretro", resp.Covers[0].Source)
	assert.Equal(t, "igdb", resp.Covers[1].Source)
	assert.Contains(t, resp.Covers[0].URL, "/api/images/")
	assert.Contains(t, resp.Covers[1].URL, "/api/images/")
}

func TestGetGameCovers_OnlyIGDB(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	var console db.Console
	database.Where("abbreviation = ?", "NES").First(&console)
	game := db.Game{
		ConsoleID:    console.ID,
		Title:        "IGDB Only Game",
		FileName:     "igdb-only.nes",
		IGDBCoverURL: "NES/2/boxart-igdb.jpg",
		CoverURL:     "NES/2/boxart-igdb.jpg",
	}
	require.NoError(t, database.Create(&game).Error)

	req := httptest.NewRequest("GET", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/covers", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp struct {
		Active string `json:"active"`
		Covers []struct {
			Source string `json:"source"`
		} `json:"covers"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "igdb", resp.Active)
	assert.Len(t, resp.Covers, 1)
	assert.Equal(t, "igdb", resp.Covers[0].Source)
}

func TestSetGameCover_SwitchToIGDB(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)
	game := createTestGame(t, database)

	body, _ := json.Marshal(map[string]string{"source": "igdb"})
	req := httptest.NewRequest("PUT", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/covers", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	// Verify the cover was updated in DB
	var updated db.Game
	database.First(&updated, game.ID)
	assert.Equal(t, game.IGDBCoverURL, updated.CoverURL)
}

func TestSetGameCover_SwitchToLibRetro(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)
	game := createTestGame(t, database)

	// First switch to IGDB
	body, _ := json.Marshal(map[string]string{"source": "igdb"})
	req := httptest.NewRequest("PUT", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/covers", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Then switch back to LibRetro
	body, _ = json.Marshal(map[string]string{"source": "libretro"})
	req = httptest.NewRequest("PUT", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/covers", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w = httptest.NewRecorder()
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var updated db.Game
	database.First(&updated, game.ID)
	assert.Equal(t, game.LibRetroCoverURL, updated.CoverURL)
}

func TestSetGameCover_MissingSource_Returns400(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	var console db.Console
	database.Where("abbreviation = ?", "NES").First(&console)
	game := db.Game{
		ConsoleID:    console.ID,
		Title:        "Only LibRetro",
		FileName:     "libretro-only.nes",
		LibRetroCoverURL: "NES/3/boxart-libretro.png",
		CoverURL:     "NES/3/boxart-libretro.png",
	}
	require.NoError(t, database.Create(&game).Error)

	// Try to switch to IGDB which doesn't exist
	body, _ := json.Marshal(map[string]string{"source": "igdb"})
	req := httptest.NewRequest("PUT", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/covers", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestSetGameCover_InvalidSource_Returns400(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)
	game := createTestGame(t, database)

	body, _ := json.Marshal(map[string]string{"source": "invalid"})
	req := httptest.NewRequest("PUT", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/covers", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestGetGameCovers_NotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	database := cfg.DB
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	req := httptest.NewRequest("GET", "/api/admin/games/99999/covers", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetGameCovers_NonAdmin_Returns403(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)

	user := db.User{
		Username:     "regular",
		Email:        "regular@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, testJWTSecret)
	require.NoError(t, err)

	game := createTestGame(t, database)

	req := httptest.NewRequest("GET", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/covers", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestGetGameCovers_NoCovers(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	var console db.Console
	database.Where("abbreviation = ?", "NES").First(&console)
	if console.ID == 0 {
		console = db.Console{Name: "NES", Abbreviation: "NES", Extensions: "nes"}
		require.NoError(t, database.Create(&console).Error)
	}

	game := db.Game{
		ConsoleID: console.ID,
		Title:     "No Cover Game",
		FileName:  "no-cover.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	req := httptest.NewRequest("GET", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/covers", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp struct {
		Active string `json:"active"`
		Covers []struct {
			Source string `json:"source"`
		} `json:"covers"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Empty(t, resp.Active)
	assert.Empty(t, resp.Covers)
}

func TestGetGameCovers_CustomPreMigrationCover(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	var console db.Console
	database.Where("abbreviation = ?", "NES").First(&console)
	if console.ID == 0 {
		console = db.Console{Name: "NES", Abbreviation: "NES", Extensions: "nes"}
		require.NoError(t, database.Create(&console).Error)
	}

	// Pre-migration game: CoverURL set but no LibRetro/IGDB fields
	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Old Game",
		FileName:  "old-game.nes",
		CoverURL:  "NES/5/boxart.png",
	}
	require.NoError(t, database.Create(&game).Error)

	req := httptest.NewRequest("GET", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/covers", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp struct {
		Active string `json:"active"`
		Covers []struct {
			Source string `json:"source"`
			URL    string `json:"url"`
		} `json:"covers"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "custom", resp.Active)
	assert.Len(t, resp.Covers, 1)
	assert.Equal(t, "custom", resp.Covers[0].Source)
	assert.Contains(t, resp.Covers[0].URL, "/api/images/")
}

func TestSetGameCover_RegionalRejectsUnknownLibRetroName(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)
	game := createTestGame(t, database)

	// Attempt to set a libretro-regional cover with an arbitrary name
	// that is not a known regional variant — should be rejected (SSRF prevention)
	body, _ := json.Marshal(map[string]interface{}{
		"source":      "libretro-regional",
		"libretroName": "../../etc/passwd",
	})
	req := httptest.NewRequest("PUT", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/covers", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)

	var resp map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Contains(t, resp["error"], "not a known regional variant")
}

func TestSetGameCover_SetsCoverManuallySet(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)
	game := createTestGame(t, database)

	// Verify CoverManuallySet starts as false
	var before db.Game
	database.First(&before, game.ID)
	assert.False(t, before.CoverManuallySet)

	body, _ := json.Marshal(map[string]string{"source": "igdb"})
	req := httptest.NewRequest("PUT", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/covers", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var after db.Game
	database.First(&after, game.ID)
	assert.True(t, after.CoverManuallySet)
	assert.Equal(t, game.IGDBCoverURL, after.CoverURL)
}
