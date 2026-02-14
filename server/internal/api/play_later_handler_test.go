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
)

func TestAddToPlayLater(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Play Later Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/play-later/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, "added to play later", resp["message"])
}

func TestAddToPlayLater_GameNotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/play-later/99999", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestAddToPlayLater_Duplicate(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Dupe PL Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Add first time
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/play-later/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// Add again - should be idempotent (return OK, not create duplicate)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/user/play-later/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "already in play later", resp["message"])

	// Verify only one item exists in queue (no duplicate)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/play-later", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	var games []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &games)
	assert.Equal(t, 1, len(games))
}

func TestAddToPlayLater_CreatesActivityEvent(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "PL Activity Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Add to play later
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/play-later/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// Check activity feed
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/social/activity", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var feedResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &feedResp)
	data := feedResp["data"].([]interface{})

	found := false
	for _, item := range data {
		event := item.(map[string]interface{})
		if event["eventType"] == "queued_play_later" {
			found = true
			assert.Equal(t, "PL Activity Game", event["gameTitle"])
			break
		}
	}
	assert.True(t, found, "should have a queued_play_later activity event")
}

func TestRemoveFromPlayLater(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Remove PL Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Add first
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/play-later/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Remove
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/user/play-later/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "removed from play later", resp["message"])

	// Verify queue is empty
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/play-later", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var games []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &games)
	assert.Len(t, games, 0)
}

func TestRemoveFromPlayLater_NotInQueue(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Not In Queue Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/user/play-later/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "game not in play later queue", resp["error"])
}

func TestListPlayLater_Empty(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/play-later", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var games []map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &games)
	require.NoError(t, err)
	assert.Len(t, games, 0)
}

func TestListPlayLater_OrderedByPosition(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game1 := db.Game{ConsoleID: console.ID, Title: "First PL Game", FileName: "first.nes", FilePath: "/tmp/first.nes", FileSize: 100}
	game2 := db.Game{ConsoleID: console.ID, Title: "Second PL Game", FileName: "second.nes", FilePath: "/tmp/second.nes", FileSize: 100}
	game3 := db.Game{ConsoleID: console.ID, Title: "Third PL Game", FileName: "third.nes", FilePath: "/tmp/third.nes", FileSize: 100}
	database.Create(&game1)
	database.Create(&game2)
	database.Create(&game3)

	// Add games in order
	for _, g := range []db.Game{game1, game2, game3} {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", fmt.Sprintf("/api/user/play-later/%d", g.ID), nil)
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusCreated, w.Code)
	}

	// List - should be in order
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/play-later", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var games []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &games)
	assert.Len(t, games, 3)
	assert.Equal(t, "First PL Game", games[0]["title"])
	assert.Equal(t, "Second PL Game", games[1]["title"])
	assert.Equal(t, "Third PL Game", games[2]["title"])
}

func TestListPlayLater_ReturnsEnrichedGameResponse(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Enriched PL Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Add to play later
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/play-later/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Also favorite the game
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/user/favorites/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// List play later - should have enriched data
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/play-later", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var games []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &games)
	require.Len(t, games, 1)
	assert.Equal(t, "Enriched PL Game", games[0]["title"])
	assert.Equal(t, true, games[0]["isInPlayLater"])
	assert.Equal(t, true, games[0]["isFavorite"])
	assert.NotNil(t, games[0]["consoleName"])
}

func TestReorderPlayLater(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game1 := db.Game{ConsoleID: console.ID, Title: "Reorder A", FileName: "a.nes", FilePath: "/tmp/a.nes", FileSize: 100}
	game2 := db.Game{ConsoleID: console.ID, Title: "Reorder B", FileName: "b.nes", FilePath: "/tmp/b.nes", FileSize: 100}
	game3 := db.Game{ConsoleID: console.ID, Title: "Reorder C", FileName: "c.nes", FilePath: "/tmp/c.nes", FileSize: 100}
	database.Create(&game1)
	database.Create(&game2)
	database.Create(&game3)

	// Add games in A, B, C order
	for _, g := range []db.Game{game1, game2, game3} {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", fmt.Sprintf("/api/user/play-later/%d", g.ID), nil)
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusCreated, w.Code)
	}

	// Reorder to C, A, B
	body, _ := json.Marshal(map[string]interface{}{
		"gameIds": []string{
			fmt.Sprintf("%d", game3.ID),
			fmt.Sprintf("%d", game1.ID),
			fmt.Sprintf("%d", game2.ID),
		},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/play-later/reorder", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "queue reordered", resp["message"])

	// Verify order
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/play-later", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var games []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &games)
	assert.Len(t, games, 3)
	assert.Equal(t, "Reorder C", games[0]["title"])
	assert.Equal(t, "Reorder A", games[1]["title"])
	assert.Equal(t, "Reorder B", games[2]["title"])
}

func TestReorderPlayLater_EmptyArray(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{
		"gameIds": []string{},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/play-later/reorder", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "gameIds required", resp["error"])
}

func TestReorderPlayLater_InvalidGameIDs(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Reorder Invalid", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Add game to queue
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/play-later/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Try to reorder with a game ID that's not in the queue
	body, _ := json.Marshal(map[string]interface{}{
		"gameIds": []string{gameID, "99999"},
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/play-later/reorder", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "invalid game IDs", resp["error"])
}

func TestGameResponse_IsInPlayLater_Populated(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "PL Flag Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Check game detail before adding - isInPlayLater should be false
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, false, resp["isInPlayLater"])

	// Add to play later
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/user/play-later/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Check game detail after adding - isInPlayLater should be true
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, true, resp["isInPlayLater"])

	// Remove from play later
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/user/play-later/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Check again - should be false
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, false, resp["isInPlayLater"])
}

func TestGameResponse_IsInPlayLater_InGamesList(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game1 := db.Game{ConsoleID: console.ID, Title: "PL List Game 1", FileName: "test1.nes", FilePath: "/tmp/test1.nes", FileSize: 100}
	game2 := db.Game{ConsoleID: console.ID, Title: "PL List Game 2", FileName: "test2.nes", FilePath: "/tmp/test2.nes", FileSize: 100}
	database.Create(&game1)
	database.Create(&game2)

	// Add only game1 to play later
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", fmt.Sprintf("/api/user/play-later/%d", game1.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// List all games
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	data := resp["data"].([]interface{})
	assert.Len(t, data, 2)

	// Build map by title for easier assertion
	gamesByTitle := make(map[string]map[string]interface{})
	for _, g := range data {
		game := g.(map[string]interface{})
		gamesByTitle[game["title"].(string)] = game
	}

	assert.Equal(t, true, gamesByTitle["PL List Game 1"]["isInPlayLater"])
	assert.Equal(t, false, gamesByTitle["PL List Game 2"]["isInPlayLater"])
}

func TestPlayLater_PerUserIsolation(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)

	// Register second user
	body, _ := json.Marshal(map[string]string{
		"username": "otheruser",
		"email":    "other@example.com",
		"password": "password123",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var regResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &regResp)
	token2 := regResp["accessToken"].(string)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Isolation Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// User 1 adds game to play later
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/user/play-later/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token1)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// User 2's queue should be empty
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/play-later", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var games []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &games)
	assert.Len(t, games, 0)

	// User 1's queue should have the game
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/play-later", nil)
	req.Header.Set("Authorization", "Bearer "+token1)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &games)
	assert.Len(t, games, 1)
	assert.Equal(t, "Isolation Game", games[0]["title"])
}

func TestReorderPlayLater_MissingBody(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/play-later/reorder", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestReorderPlayLater_PartialList(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game1 := db.Game{ConsoleID: console.ID, Title: "Partial A", FileName: "pa.nes", FilePath: "/tmp/pa.nes", FileSize: 100}
	game2 := db.Game{ConsoleID: console.ID, Title: "Partial B", FileName: "pb.nes", FilePath: "/tmp/pb.nes", FileSize: 100}
	database.Create(&game1)
	database.Create(&game2)

	// Add both games to queue
	for _, g := range []db.Game{game1, game2} {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", fmt.Sprintf("/api/user/play-later/%d", g.ID), nil)
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusCreated, w.Code)
	}

	// Try to reorder with only one of the two games - should fail
	body, _ := json.Marshal(map[string]interface{}{
		"gameIds": []string{fmt.Sprintf("%d", game1.ID)},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/play-later/reorder", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "gameIds must include all games in queue", resp["error"])
}
