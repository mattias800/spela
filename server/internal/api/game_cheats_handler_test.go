package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestGetGameCheats_Empty(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var nes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)
	game := db.Game{ConsoleID: nes.ID, Title: "Test Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/cheats", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp []map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp)
}

func TestGetGameCheats_WithCheats(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var nes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)
	game := db.Game{ConsoleID: nes.ID, Title: "Test Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	database.Create(&db.CheatCode{GameID: game.ID, CheatIndex: 0, Description: "Infinite Lives", Code: "AABB-1122"})
	database.Create(&db.CheatCode{GameID: game.ID, CheatIndex: 1, Description: "Max Health", Code: "CCDD-3344"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/cheats", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp []map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp, 2)
	assert.Equal(t, "Infinite Lives", resp[0]["description"])
	assert.Equal(t, "AABB-1122", resp[0]["code"])
	assert.Equal(t, float64(0), resp[0]["index"])
	assert.Equal(t, "Max Health", resp[1]["description"])
	assert.Equal(t, "CCDD-3344", resp[1]["code"])
	assert.Equal(t, float64(1), resp[1]["index"])
}

func TestGetGameCheats_NotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/99999/cheats", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}
