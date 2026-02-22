package igdb

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewClient(t *testing.T) {
	c := NewClient("myid", "mysecret")
	assert.Equal(t, "myid", c.ClientID)
	assert.Equal(t, "mysecret", c.ClientSecret)
	assert.True(t, c.IsConfigured())
}

func TestIsConfigured(t *testing.T) {
	tests := []struct {
		name     string
		id       string
		secret   string
		expected bool
	}{
		{"both set", "id", "secret", true},
		{"empty id", "", "secret", false},
		{"empty secret", "id", "", false},
		{"both empty", "", "", false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c := &Client{ClientID: tt.id, ClientSecret: tt.secret}
			assert.Equal(t, tt.expected, c.IsConfigured())
		})
	}
}

func TestOAuthTokenExchange_Success(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "POST", r.Method)
		assert.Equal(t, "myid", r.FormValue("client_id"))
		assert.Equal(t, "mysecret", r.FormValue("client_secret"))
		assert.Equal(t, "client_credentials", r.FormValue("grant_type"))

		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token-123",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer server.Close()

	origURL := twitchTokenURL
	twitchTokenURL = server.URL
	defer func() { twitchTokenURL = origURL }()

	c := &Client{
		ClientID:     "myid",
		ClientSecret: "mysecret",
		HTTPClient:   server.Client(),
		rateLimiter:  time.Tick(time.Millisecond),
	}

	err := c.authenticate()
	require.NoError(t, err)
	assert.Equal(t, "test-token-123", c.token.AccessToken)
	assert.True(t, c.token.ExpiresAt.After(time.Now()))
}

func TestOAuthTokenExchange_Failure(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte(`{"status":401,"message":"invalid client"}`))
	}))
	defer server.Close()

	origURL := twitchTokenURL
	twitchTokenURL = server.URL
	defer func() { twitchTokenURL = origURL }()

	c := &Client{
		ClientID:     "bad",
		ClientSecret: "bad",
		HTTPClient:   server.Client(),
		rateLimiter:  time.Tick(time.Millisecond),
	}

	err := c.authenticate()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "401")
}

func TestOAuthTokenExchange_NetworkError(t *testing.T) {
	origURL := twitchTokenURL
	twitchTokenURL = "http://127.0.0.1:1/token"
	defer func() { twitchTokenURL = origURL }()

	c := &Client{
		ClientID:     "id",
		ClientSecret: "secret",
		HTTPClient:   &http.Client{Timeout: 100 * time.Millisecond},
		rateLimiter:  time.Tick(time.Millisecond),
	}

	err := c.authenticate()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "requesting Twitch OAuth token")
}

func TestTokenAutoRefresh(t *testing.T) {
	var callCount int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&callCount, 1)
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "refreshed-token",
			"expires_in":   7200,
			"token_type":   "bearer",
		})
	}))
	defer server.Close()

	origURL := twitchTokenURL
	twitchTokenURL = server.URL
	defer func() { twitchTokenURL = origURL }()

	c := &Client{
		ClientID:     "id",
		ClientSecret: "secret",
		HTTPClient:   server.Client(),
		rateLimiter:  time.Tick(time.Millisecond),
	}

	// Set an expired token
	c.token = &oauthToken{
		AccessToken: "old-token",
		ExpiresAt:   time.Now().Add(-time.Hour),
	}

	err := c.authenticate()
	require.NoError(t, err)
	assert.Equal(t, "refreshed-token", c.token.AccessToken)
	assert.Equal(t, int32(1), atomic.LoadInt32(&callCount))
}

func TestTokenNotRefreshedWhenValid(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("should not request new token when existing token is valid")
	}))
	defer server.Close()

	origURL := twitchTokenURL
	twitchTokenURL = server.URL
	defer func() { twitchTokenURL = origURL }()

	c := &Client{
		ClientID:     "id",
		ClientSecret: "secret",
		HTTPClient:   server.Client(),
		rateLimiter:  time.Tick(time.Millisecond),
	}

	// Set a valid token (expires in 2 hours, > 1 hour buffer)
	c.token = &oauthToken{
		AccessToken: "valid-token",
		ExpiresAt:   time.Now().Add(2 * time.Hour),
	}

	err := c.authenticate()
	require.NoError(t, err)
	assert.Equal(t, "valid-token", c.token.AccessToken)
}

func TestSearchGame_Success(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "POST", r.Method)
		assert.Equal(t, "/v4/games", r.URL.Path)
		assert.Equal(t, "myid", r.Header.Get("Client-ID"))
		assert.Equal(t, "Bearer test-token", r.Header.Get("Authorization"))

		json.NewEncoder(w).Encode([]Game{
			{
				ID:      1234,
				Name:    "Super Mario Bros.",
				Summary: "A classic platformer",
				Cover:   &Image{ID: 1, ImageID: "co1234"},
				Screenshots: []Image{
					{ID: 2, ImageID: "sc5678"},
				},
				Genres: []Genre{
					{ID: 8, Name: "Platform"},
				},
				InvolvedCompanies: []InvolvedCompany{
					{Company: Company{ID: 1, Name: "Nintendo"}, Developer: true, Publisher: true},
				},
				FirstReleaseDate: 496800000,
				AggregatedRating: 85.5,
				GameModes: []GameMode{
					{ID: 1, Name: "Single player"},
					{ID: 2, Name: "Multiplayer"},
				},
			},
		})
	}))
	defer igdbServer.Close()

	origTokenURL := twitchTokenURL
	origAPIBase := igdbAPIBase
	twitchTokenURL = tokenServer.URL
	igdbAPIBase = igdbServer.URL + "/v4"
	defer func() {
		twitchTokenURL = origTokenURL
		igdbAPIBase = origAPIBase
	}()

	c := &Client{
		ClientID:     "myid",
		ClientSecret: "mysecret",
		HTTPClient:   &http.Client{Timeout: 5 * time.Second},
		rateLimiter:  time.Tick(time.Millisecond),
	}

	games, err := c.SearchGame("Super Mario Bros", 18)
	require.NoError(t, err)
	require.Len(t, games, 1)

	game := games[0]
	assert.Equal(t, 1234, game.ID)
	assert.Equal(t, "Super Mario Bros.", game.Name)
	assert.Equal(t, "A classic platformer", game.Summary)
	assert.NotNil(t, game.Cover)
	assert.Equal(t, "co1234", game.Cover.ImageID)
	assert.Len(t, game.Screenshots, 1)
	assert.Equal(t, "sc5678", game.Screenshots[0].ImageID)
	assert.Len(t, game.Genres, 1)
	assert.Equal(t, "Platform", game.Genres[0].Name)
	assert.Len(t, game.InvolvedCompanies, 1)
	assert.True(t, game.InvolvedCompanies[0].Developer)
	assert.True(t, game.InvolvedCompanies[0].Publisher)
	assert.Equal(t, "Nintendo", game.InvolvedCompanies[0].Company.Name)
	assert.Equal(t, int64(496800000), game.FirstReleaseDate)
	assert.InDelta(t, 85.5, game.AggregatedRating, 0.01)
	assert.Len(t, game.GameModes, 2)
}

func TestSearchGame_APIError(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusTooManyRequests)
		w.Write([]byte("rate limited"))
	}))
	defer igdbServer.Close()

	origTokenURL := twitchTokenURL
	origAPIBase := igdbAPIBase
	twitchTokenURL = tokenServer.URL
	igdbAPIBase = igdbServer.URL + "/v4"
	defer func() {
		twitchTokenURL = origTokenURL
		igdbAPIBase = origAPIBase
	}()

	c := &Client{
		ClientID:     "myid",
		ClientSecret: "mysecret",
		HTTPClient:   &http.Client{Timeout: 5 * time.Second},
		rateLimiter:  time.Tick(time.Millisecond),
	}

	_, err := c.SearchGame("test", 18)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "429")
}

func TestSearchGame_NoResults(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]Game{})
	}))
	defer igdbServer.Close()

	origTokenURL := twitchTokenURL
	origAPIBase := igdbAPIBase
	twitchTokenURL = tokenServer.URL
	igdbAPIBase = igdbServer.URL + "/v4"
	defer func() {
		twitchTokenURL = origTokenURL
		igdbAPIBase = origAPIBase
	}()

	c := &Client{
		ClientID:     "myid",
		ClientSecret: "mysecret",
		HTTPClient:   &http.Client{Timeout: 5 * time.Second},
		rateLimiter:  time.Tick(time.Millisecond),
	}

	games, err := c.SearchGame("nonexistent-game-xyz", 18)
	require.NoError(t, err)
	assert.Empty(t, games)
}

func TestTestCredentials_Success(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "testid", r.FormValue("client_id"))
		assert.Equal(t, "testsecret", r.FormValue("client_secret"))
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer server.Close()

	origURL := twitchTokenURL
	twitchTokenURL = server.URL
	defer func() { twitchTokenURL = origURL }()

	c := &Client{HTTPClient: server.Client()}
	err := c.TestCredentials("testid", "testsecret")
	assert.NoError(t, err)
}

func TestTestCredentials_Failure(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte(`{"status":401,"message":"invalid client"}`))
	}))
	defer server.Close()

	origURL := twitchTokenURL
	twitchTokenURL = server.URL
	defer func() { twitchTokenURL = origURL }()

	c := &Client{HTTPClient: server.Client()}
	err := c.TestCredentials("bad", "bad")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "authentication failed")
}

func TestTestCredentials_NetworkError(t *testing.T) {
	origURL := twitchTokenURL
	twitchTokenURL = "http://127.0.0.1:1/token"
	defer func() { twitchTokenURL = origURL }()

	c := &Client{HTTPClient: &http.Client{Timeout: 100 * time.Millisecond}}
	err := c.TestCredentials("id", "secret")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "connecting to Twitch")
}

func TestImageURL(t *testing.T) {
	tests := []struct {
		imageID  string
		size     string
		expected string
	}{
		{"co1234", "cover_big", "https://images.igdb.com/igdb/image/upload/t_cover_big/co1234.jpg"},
		{"sc5678", "screenshot_huge", "https://images.igdb.com/igdb/image/upload/t_screenshot_huge/sc5678.jpg"},
		{"co0001", "thumb", "https://images.igdb.com/igdb/image/upload/t_thumb/co0001.jpg"},
	}
	for _, tt := range tests {
		t.Run(tt.imageID+"_"+tt.size, func(t *testing.T) {
			assert.Equal(t, tt.expected, ImageURL(tt.imageID, tt.size))
		})
	}
}

func TestCleanGameName(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"Super Mario Bros. (USA) (Rev A).nes", "Super Mario Bros."},
		{"Zelda (Europe) (En,Fr,De).sfc", "Zelda"},
		{"Castlevania [!].nes", "Castlevania"},
		{"Sonic (USA) (Disc 1).bin", "Sonic"},
		{"Game.gba", "Game"},
		{"Simple", "Simple"},
		{"Brackets [a] (b) [c].rom", "Brackets"},
		{"Final Fantasy III (Japan) (Rev 1).smc", "Final Fantasy III"},
	}
	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			assert.Equal(t, tt.expected, CleanGameName(tt.input))
		})
	}
}

func TestEscapeQuery(t *testing.T) {
	assert.Equal(t, `test\"game`, escapeQuery(`test"game`))
	assert.Equal(t, "no quotes", escapeQuery("no quotes"))
	// SEC-1: semicolons must be stripped to prevent query injection
	assert.Equal(t, "game limit 999", escapeQuery("game; limit 999"))
	assert.Equal(t, "clean name", escapeQuery("clean; name"))
}

func TestRateLimiting(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	var requestCount int32
	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&requestCount, 1)
		json.NewEncoder(w).Encode([]Game{})
	}))
	defer igdbServer.Close()

	origTokenURL := twitchTokenURL
	origAPIBase := igdbAPIBase
	twitchTokenURL = tokenServer.URL
	igdbAPIBase = igdbServer.URL + "/v4"
	defer func() {
		twitchTokenURL = origTokenURL
		igdbAPIBase = origAPIBase
	}()

	// Use a 100ms rate limiter (10 req/s) — fast enough for testing
	c := &Client{
		ClientID:     "id",
		ClientSecret: "secret",
		HTTPClient:   &http.Client{Timeout: 5 * time.Second},
		rateLimiter:  time.Tick(100 * time.Millisecond),
	}

	start := time.Now()
	for i := 0; i < 3; i++ {
		_, err := c.SearchGame("test", 18)
		require.NoError(t, err)
	}
	elapsed := time.Since(start)

	assert.Equal(t, int32(3), atomic.LoadInt32(&requestCount))
	// 3 requests with 100ms intervals should take at least 200ms
	assert.GreaterOrEqual(t, elapsed.Milliseconds(), int64(200))
}

func TestPlatformMapping(t *testing.T) {
	// Verify key platform mappings exist
	assert.Equal(t, 18, AbbreviationToIGDBPlatform["NES"])
	assert.Equal(t, 19, AbbreviationToIGDBPlatform["SNES"])
	assert.Equal(t, 7, AbbreviationToIGDBPlatform["PSX"])
	assert.Equal(t, 29, AbbreviationToIGDBPlatform["GEN"])
	assert.Equal(t, 4, AbbreviationToIGDBPlatform["N64"])

	// Unknown platform returns zero value
	_, exists := AbbreviationToIGDBPlatform["UNKNOWN"]
	assert.False(t, exists)
}
