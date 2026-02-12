package retroachievements

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestLoginWithPassword_Success(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/dorequest.php", r.URL.Path)
		assert.Equal(t, "POST", r.Method)

		err := r.ParseForm()
		require.NoError(t, err)
		assert.Equal(t, "login", r.FormValue("r"))
		assert.Equal(t, "testuser", r.FormValue("u"))
		assert.Equal(t, "testpass", r.FormValue("p"))

		json.NewEncoder(w).Encode(map[string]interface{}{
			"Success": true,
			"Token":   "abc123token",
		})
	}))
	defer server.Close()

	client := &RAClient{BaseURL: server.URL, HTTPClient: server.Client()}
	token, err := client.LoginWithPassword("testuser", "testpass")
	require.NoError(t, err)
	assert.Equal(t, "abc123token", token)
}

func TestLoginWithPassword_InvalidCredentials(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"Success": false,
			"Error":   "Invalid credentials",
		})
	}))
	defer server.Close()

	client := &RAClient{BaseURL: server.URL, HTTPClient: server.Client()}
	_, err := client.LoginWithPassword("bad", "wrong")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "Invalid credentials")
}

func TestLoginWithPassword_EmptyToken(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"Success": true,
			"Token":   "",
		})
	}))
	defer server.Close()

	client := &RAClient{BaseURL: server.URL, HTTPClient: server.Client()}
	_, err := client.LoginWithPassword("user", "pass")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "empty token")
}

func TestLoginWithPassword_ServerError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("internal error"))
	}))
	defer server.Close()

	client := &RAClient{BaseURL: server.URL, HTTPClient: server.Client()}
	_, err := client.LoginWithPassword("user", "pass")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "status 500")
}

func TestGetGameIDFromHash_Success(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/dorequest.php", r.URL.Path)
		assert.Equal(t, "gameid", r.URL.Query().Get("r"))
		assert.Equal(t, "abc123hash", r.URL.Query().Get("m"))

		json.NewEncoder(w).Encode(map[string]interface{}{
			"Success": true,
			"GameID":  float64(12345),
		})
	}))
	defer server.Close()

	client := &RAClient{BaseURL: server.URL, HTTPClient: server.Client()}
	gameID, err := client.GetGameIDFromHash("abc123hash")
	require.NoError(t, err)
	assert.Equal(t, uint(12345), gameID)
}

func TestGetGameIDFromHash_NotFound(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"Success": true,
			"GameID":  float64(0),
		})
	}))
	defer server.Close()

	client := &RAClient{BaseURL: server.URL, HTTPClient: server.Client()}
	_, err := client.GetGameIDFromHash("unknownhash")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "no RA game found")
}

func TestGetGameIDFromHash_Failure(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"Success": false,
			"Error":   "invalid hash",
		})
	}))
	defer server.Close()

	client := &RAClient{BaseURL: server.URL, HTTPClient: server.Client()}
	_, err := client.GetGameIDFromHash("badhash")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "invalid hash")
}

func TestGetGameInfoAndUserProgress_Success(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/API/API_GetGameInfoAndUserProgress.php", r.URL.Path)
		assert.Equal(t, "testuser", r.URL.Query().Get("z"))
		assert.Equal(t, "testtoken", r.URL.Query().Get("y"))
		assert.Equal(t, "100", r.URL.Query().Get("g"))

		json.NewEncoder(w).Encode(map[string]interface{}{
			"ID":    100,
			"Title": "Super Mario Bros.",
			"Achievements": map[string]interface{}{
				"1001": map[string]interface{}{
					"ID":                   1001,
					"Title":                "First Step",
					"Description":          "Complete World 1-1",
					"Points":               10,
					"BadgeName":            "12345",
					"type":                 3,
					"DateEarned":           "2024-01-15 12:30:00",
					"DateEarnedHardcore":   "",
				},
				"1002": map[string]interface{}{
					"ID":                   1002,
					"Title":                "Speed Runner",
					"Description":          "Beat the game in under 10 minutes",
					"Points":               25,
					"BadgeName":            "12346",
					"type":                 3,
					"DateEarned":           "",
					"DateEarnedHardcore":   "2024-02-01 08:00:00",
				},
				"1003": map[string]interface{}{
					"ID":                   1003,
					"Title":                "Unofficial Challenge",
					"Description":          "Do something extra",
					"Points":               5,
					"BadgeName":            "",
					"type":                 5,
					"DateEarned":           "",
					"DateEarnedHardcore":   "",
				},
			},
		})
	}))
	defer server.Close()

	client := &RAClient{BaseURL: server.URL, HTTPClient: server.Client()}
	gameInfo, progress, err := client.GetGameInfoAndUserProgress("testuser", "testtoken", 100)
	require.NoError(t, err)

	assert.Equal(t, uint(100), gameInfo.ID)
	assert.Equal(t, "Super Mario Bros.", gameInfo.Title)
	assert.Equal(t, 3, gameInfo.TotalCount)
	assert.Equal(t, 35, gameInfo.TotalPoints) // 10 + 25 from core achievements only

	// Check progress: should have 2 earned achievements
	assert.Len(t, progress, 2)

	// Verify achievement types
	typeMap := make(map[string]int)
	for _, a := range gameInfo.Achievements {
		typeMap[a.Type]++
	}
	assert.Equal(t, 2, typeMap["core"])
	assert.Equal(t, 1, typeMap["unofficial"])

	// Verify badge URLs
	badgeURLMap := make(map[uint]string)
	for _, a := range gameInfo.Achievements {
		badgeURLMap[a.ID] = a.BadgeURL
	}
	assert.Equal(t, "https://media.retroachievements.org/Badge/12345.png", badgeURLMap[1001])
	assert.Equal(t, "", badgeURLMap[1003]) // no badge name

	// Verify progress details
	progressMap := make(map[uint]UserProgress)
	for _, p := range progress {
		progressMap[p.AchievementID] = p
	}
	assert.False(t, progressMap[1001].IsHardcore)
	assert.Equal(t, "2024-01-15 12:30:00", progressMap[1001].UnlockedAt)
	assert.True(t, progressMap[1002].IsHardcore)
	assert.Equal(t, "2024-02-01 08:00:00", progressMap[1002].UnlockedAt)
}

func TestGetGameInfoAndUserProgress_ServerError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	client := &RAClient{BaseURL: server.URL, HTTPClient: server.Client()}
	_, _, err := client.GetGameInfoAndUserProgress("user", "token", 1)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "status 500")
}

func TestNewRAClient(t *testing.T) {
	client := NewRAClient()
	assert.Equal(t, "https://retroachievements.org", client.BaseURL)
	assert.NotNil(t, client.HTTPClient)
}
