package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestGetGameStats_GameNotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/99999/stats", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetGameStats_NoPlayHistory(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create a game with no play history
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "No History Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/stats", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, float64(0), resp["totalPlayers"])
	assert.Equal(t, float64(0), resp["totalPlayTime"])
	assert.Equal(t, float64(0), resp["averagePlayTime"])
	topPlayers := resp["topPlayers"].([]interface{})
	assert.Len(t, topPlayers, 0)
}

func TestGetGameStats_WithPlayHistory(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Stats Game", FileName: "stats.nes", FilePath: "/tmp/stats.nes", FileSize: 100}
	database.Create(&game)

	// Create two users with play history
	user1 := db.User{Username: "alice", Email: "alice@example.com", PasswordHash: "hash", Role: "user"}
	user2 := db.User{Username: "bob", Email: "bob@example.com", PasswordHash: "hash", Role: "user"}
	database.Create(&user1)
	database.Create(&user2)

	database.Create(&db.PlayHistory{UserID: user1.ID, GameID: game.ID, PlayTime: 7200, LastPlayed: time.Now()})
	database.Create(&db.PlayHistory{UserID: user2.ID, GameID: game.ID, PlayTime: 3600, LastPlayed: time.Now()})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/stats", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, float64(2), resp["totalPlayers"])
	assert.Equal(t, float64(10800), resp["totalPlayTime"])
	assert.Equal(t, float64(5400), resp["averagePlayTime"])

	topPlayers := resp["topPlayers"].([]interface{})
	assert.Len(t, topPlayers, 2)

	// First player should have the most play time
	first := topPlayers[0].(map[string]interface{})
	assert.Equal(t, "alice", first["username"])
	assert.Equal(t, float64(7200), first["playTime"])

	// userId should be a string
	_, idIsString := first["userId"].(string)
	assert.True(t, idIsString, "userId should be a string")
}

func TestMostPlayedGames_Empty(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/stats/most-played", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	games := resp["games"].([]interface{})
	assert.Len(t, games, 0)
}

func TestMostPlayedGames_WithData(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)

	game1 := db.Game{ConsoleID: console.ID, Title: "Popular Game", FileName: "pop.nes", FilePath: "/tmp/pop.nes", FileSize: 100}
	game2 := db.Game{ConsoleID: console.ID, Title: "Less Popular", FileName: "less.nes", FilePath: "/tmp/less.nes", FileSize: 100}
	database.Create(&game1)
	database.Create(&game2)

	user1 := db.User{Username: "player1", Email: "p1@example.com", PasswordHash: "hash", Role: "user"}
	user2 := db.User{Username: "player2", Email: "p2@example.com", PasswordHash: "hash", Role: "user"}
	database.Create(&user1)
	database.Create(&user2)

	database.Create(&db.PlayHistory{UserID: user1.ID, GameID: game1.ID, PlayTime: 5000, LastPlayed: time.Now()})
	database.Create(&db.PlayHistory{UserID: user2.ID, GameID: game1.ID, PlayTime: 3000, LastPlayed: time.Now()})
	database.Create(&db.PlayHistory{UserID: user1.ID, GameID: game2.ID, PlayTime: 1000, LastPlayed: time.Now()})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/stats/most-played", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	games := resp["games"].([]interface{})
	assert.Len(t, games, 2)

	// First game should be the most played (8000 total)
	first := games[0].(map[string]interface{})
	assert.Equal(t, float64(8000), first["totalPlayTime"])
	assert.Equal(t, float64(2), first["totalPlayers"])

	// It should include a full game object
	gameObj := first["game"].(map[string]interface{})
	assert.Equal(t, "Popular Game", gameObj["title"])
	_, hasID := gameObj["id"].(string)
	assert.True(t, hasID, "game id should be a string")
}

func TestMostActivePlayers_Empty(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/stats/most-active-players", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	players := resp["players"].([]interface{})
	assert.Len(t, players, 0)
}

func TestMostActivePlayers_WithData(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)

	game1 := db.Game{ConsoleID: console.ID, Title: "Game A", FileName: "a.nes", FilePath: "/tmp/a.nes", FileSize: 100}
	game2 := db.Game{ConsoleID: console.ID, Title: "Game B", FileName: "b.nes", FilePath: "/tmp/b.nes", FileSize: 100}
	database.Create(&game1)
	database.Create(&game2)

	user1 := db.User{Username: "active_alice", Email: "aa@example.com", PasswordHash: "hash", Role: "user", AvatarURL: "https://avatar.test/alice"}
	user2 := db.User{Username: "active_bob", Email: "ab@example.com", PasswordHash: "hash", Role: "user"}
	database.Create(&user1)
	database.Create(&user2)

	now := time.Now()
	database.Create(&db.PlayHistory{UserID: user1.ID, GameID: game1.ID, PlayTime: 5000, LastPlayed: now})
	database.Create(&db.PlayHistory{UserID: user1.ID, GameID: game2.ID, PlayTime: 3000, LastPlayed: now})
	database.Create(&db.PlayHistory{UserID: user2.ID, GameID: game1.ID, PlayTime: 2000, LastPlayed: now})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/stats/most-active-players", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	players := resp["players"].([]interface{})
	// We have 3 users: apitest (from registerAndGetToken), user1, user2
	// Only user1 and user2 have play history
	assert.Len(t, players, 2)

	// First player should be the most active (8000 total)
	first := players[0].(map[string]interface{})
	assert.Equal(t, "active_alice", first["username"])
	assert.Equal(t, float64(8000), first["totalPlayTime"])
	assert.Equal(t, float64(2), first["gamesPlayed"])
	assert.Equal(t, "https://avatar.test/alice", first["avatarUrl"])

	// userId should be a string
	_, idIsString := first["userId"].(string)
	assert.True(t, idIsString, "userId should be a string")

	// Second player
	second := players[1].(map[string]interface{})
	assert.Equal(t, "active_bob", second["username"])
	assert.Equal(t, float64(2000), second["totalPlayTime"])
	assert.Equal(t, float64(1), second["gamesPlayed"])
}

func TestGetUserStats_NoHistory(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/stats", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, float64(0), resp["totalPlayTime"])
	assert.Equal(t, float64(0), resp["gamesPlayed"])
	assert.Equal(t, float64(0), resp["currentStreak"])
	assert.Equal(t, float64(0), resp["longestStreak"])
	assert.Nil(t, resp["mostPlayedGame"])
	assert.Equal(t, float64(0), resp["mostPlayedGameTime"])
	assert.Nil(t, resp["lastPlayedAt"])
}

func TestGetUserStats_WithHistory(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)

	game1 := db.Game{ConsoleID: console.ID, Title: "Fav Game", FileName: "fav.nes", FilePath: "/tmp/fav.nes", FileSize: 100}
	game2 := db.Game{ConsoleID: console.ID, Title: "Other Game", FileName: "other.nes", FilePath: "/tmp/other.nes", FileSize: 100}
	database.Create(&game1)
	database.Create(&game2)

	// Get the test user's ID
	var user db.User
	database.Where("username = ?", "apitest").First(&user)

	now := time.Now()
	database.Create(&db.PlayHistory{UserID: user.ID, GameID: game1.ID, PlayTime: 7200, LastPlayed: now})
	database.Create(&db.PlayHistory{UserID: user.ID, GameID: game2.ID, PlayTime: 3600, LastPlayed: now.Add(-24 * time.Hour)})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/stats", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, float64(10800), resp["totalPlayTime"])
	assert.Equal(t, float64(2), resp["gamesPlayed"])
	assert.Equal(t, float64(7200), resp["mostPlayedGameTime"])
	assert.NotNil(t, resp["lastPlayedAt"])

	// Most played game should be a full GameResponse
	mostPlayed := resp["mostPlayedGame"].(map[string]interface{})
	assert.Equal(t, "Fav Game", mostPlayed["title"])
}

func TestRecordDailyPlayActivity_Upsert(t *testing.T) {
	database, _ := setupTestEnv(t)

	// Create a test user
	user := db.User{Username: "heatmap_user", Email: "heatmap@example.com", PasswordHash: "hash", Role: "user"}
	database.Create(&user)

	// First call creates a new record
	RecordDailyPlayActivity(database, user.ID, 60)

	today := time.Now().UTC().Truncate(24 * time.Hour)
	var record db.DailyPlayActivity
	err := database.Where("user_id = ? AND date = ?", user.ID, today).First(&record).Error
	require.NoError(t, err)
	assert.Equal(t, int64(60), record.PlayTime)

	// Second call increments the existing record
	RecordDailyPlayActivity(database, user.ID, 120)

	database.Where("user_id = ? AND date = ?", user.ID, today).First(&record)
	assert.Equal(t, int64(180), record.PlayTime)
}

func TestHeatmap_Empty(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/play-heatmap", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var entries []heatmapEntry
	err := json.Unmarshal(w.Body.Bytes(), &entries)
	require.NoError(t, err)
	assert.Len(t, entries, 0)
}

func TestHeatmap_WithData(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var user db.User
	database.Where("username = ?", "apitest").First(&user)

	today := time.Now().UTC().Truncate(24 * time.Hour)
	yesterday := today.AddDate(0, 0, -1)

	database.Create(&db.DailyPlayActivity{UserID: user.ID, Date: today, PlayTime: 3600})
	database.Create(&db.DailyPlayActivity{UserID: user.ID, Date: yesterday, PlayTime: 1800})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/play-heatmap", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var entries []heatmapEntry
	err := json.Unmarshal(w.Body.Bytes(), &entries)
	require.NoError(t, err)
	assert.Len(t, entries, 2)

	// Should be sorted ascending by date
	assert.Equal(t, yesterday.Format("2006-01-02"), entries[0].Date)
	assert.Equal(t, int64(1800), entries[0].PlayTime)
	assert.Equal(t, today.Format("2006-01-02"), entries[1].Date)
	assert.Equal(t, int64(3600), entries[1].PlayTime)
}

func TestHeatmap_PublicEndpoint(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create another user with activity
	otherUser := db.User{Username: "other_heatmap", Email: "other@example.com", PasswordHash: "hash", Role: "user"}
	database.Create(&otherUser)

	today := time.Now().UTC().Truncate(24 * time.Hour)
	database.Create(&db.DailyPlayActivity{UserID: otherUser.ID, Date: today, PlayTime: 7200})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/users/%d/play-heatmap", otherUser.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var entries []heatmapEntry
	err := json.Unmarshal(w.Body.Bytes(), &entries)
	require.NoError(t, err)
	assert.Len(t, entries, 1)
	assert.Equal(t, int64(7200), entries[0].PlayTime)
}

func TestHeatmap_UserIsolation(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var user db.User
	database.Where("username = ?", "apitest").First(&user)

	// Create another user with activity
	otherUser := db.User{Username: "isolated_user", Email: "isolated@example.com", PasswordHash: "hash", Role: "user"}
	database.Create(&otherUser)

	today := time.Now().UTC().Truncate(24 * time.Hour)
	database.Create(&db.DailyPlayActivity{UserID: user.ID, Date: today, PlayTime: 100})
	database.Create(&db.DailyPlayActivity{UserID: otherUser.ID, Date: today, PlayTime: 9999})

	// Current user's heatmap should only show their own data
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/play-heatmap", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var entries []heatmapEntry
	err := json.Unmarshal(w.Body.Bytes(), &entries)
	require.NoError(t, err)
	assert.Len(t, entries, 1)
	assert.Equal(t, int64(100), entries[0].PlayTime)
}

func TestHeatmap_PublicNotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/99999/play-heatmap", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestCalculateStreaks(t *testing.T) {
	tests := []struct {
		name            string
		dates           []string // yyyy-mm-dd
		today           string
		wantCurrent     int
		wantLongest     int
	}{
		{
			name:        "no dates",
			dates:       nil,
			today:       "2026-02-13",
			wantCurrent: 0,
			wantLongest: 0,
		},
		{
			name:        "single day is today",
			dates:       []string{"2026-02-13"},
			today:       "2026-02-13",
			wantCurrent: 1,
			wantLongest: 1,
		},
		{
			name:        "single day is yesterday",
			dates:       []string{"2026-02-12"},
			today:       "2026-02-13",
			wantCurrent: 1,
			wantLongest: 1,
		},
		{
			name:        "single day is two days ago",
			dates:       []string{"2026-02-11"},
			today:       "2026-02-13",
			wantCurrent: 0,
			wantLongest: 1,
		},
		{
			name:        "three consecutive days ending today",
			dates:       []string{"2026-02-11", "2026-02-12", "2026-02-13"},
			today:       "2026-02-13",
			wantCurrent: 3,
			wantLongest: 3,
		},
		{
			name:        "three consecutive days ending yesterday",
			dates:       []string{"2026-02-10", "2026-02-11", "2026-02-12"},
			today:       "2026-02-13",
			wantCurrent: 3,
			wantLongest: 3,
		},
		{
			name:        "gap in the middle",
			dates:       []string{"2026-02-09", "2026-02-10", "2026-02-12", "2026-02-13"},
			today:       "2026-02-13",
			wantCurrent: 2,
			wantLongest: 2,
		},
		{
			name:        "longer past streak than current",
			dates:       []string{"2026-02-01", "2026-02-02", "2026-02-03", "2026-02-04", "2026-02-12", "2026-02-13"},
			today:       "2026-02-13",
			wantCurrent: 2,
			wantLongest: 4,
		},
		{
			name:        "current streak is the longest",
			dates:       []string{"2026-02-01", "2026-02-02", "2026-02-10", "2026-02-11", "2026-02-12", "2026-02-13"},
			today:       "2026-02-13",
			wantCurrent: 4,
			wantLongest: 4,
		},
		{
			name:        "duplicate dates are handled",
			dates:       []string{"2026-02-12", "2026-02-12", "2026-02-13", "2026-02-13"},
			today:       "2026-02-13",
			wantCurrent: 2,
			wantLongest: 2,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var dates []time.Time
			for _, d := range tt.dates {
				parsed, err := time.Parse("2006-01-02", d)
				require.NoError(t, err)
				dates = append(dates, parsed)
			}
			today, err := time.Parse("2006-01-02", tt.today)
			require.NoError(t, err)

			current, longest := calculateStreaks(dates, today)
			assert.Equal(t, tt.wantCurrent, current, "currentStreak")
			assert.Equal(t, tt.wantLongest, longest, "longestStreak")
		})
	}
}
