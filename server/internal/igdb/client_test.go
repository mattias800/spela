package igdb

import (
	"encoding/json"
	"io"
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
		body, err := io.ReadAll(r.Body)
		require.NoError(t, err)
		query := string(body)
		assert.Contains(t, query, "parent_game")
		assert.Contains(t, query, "version_parent")
		assert.Contains(t, query, "category")

		category := IGDBCategoryPort
		parentID := 4321
		json.NewEncoder(w).Encode([]Game{
			{
				ID:           1234,
				Name:         "Super Mario Bros.",
				Summary:      "A classic platformer",
				Cover:        &Image{ID: 1, ImageID: "co1234"},
				ParentGameID: &parentID,
				Category:     &category,
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

	games, err := c.SearchGame("Super Mario Bros", []int{18})
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
	require.NotNil(t, game.ParentGameID)
	assert.Equal(t, 4321, *game.ParentGameID)
	require.NotNil(t, game.Category)
	assert.Equal(t, IGDBCategoryPort, *game.Category)
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

	_, err := c.SearchGame("test", []int{18})
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

	games, err := c.SearchGame("nonexistent-game-xyz", []int{18})
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
		{"Puzzle Bobble ~ Bust-A-Move (Japan) (En,Ja).cue", "Puzzle Bobble"},
		{"Bakumatsu Roman - Gekka no Kenshi ~ The Last Blade (Japan).cue", "Bakumatsu Roman - Gekka no Kenshi"},
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
		_, err := c.SearchGame("test", []int{18})
		require.NoError(t, err)
	}
	elapsed := time.Since(start)

	assert.Equal(t, int32(3), atomic.LoadInt32(&requestCount))
	// 3 requests with 100ms intervals should take at least 200ms
	assert.GreaterOrEqual(t, elapsed.Milliseconds(), int64(200))
}

func TestGetSimilarGames_Success(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	requestCount := 0
	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requestCount++
		if requestCount == 1 {
			// First request: return similar_games IDs
			json.NewEncoder(w).Encode([]map[string]interface{}{
				{"similar_games": []int{5678, 9012}},
			})
		} else {
			// Second request: return game details
			json.NewEncoder(w).Encode([]SimilarGame{
				{
					ID:          5678,
					Name:        "Super Mario Bros. 2",
					Cover:       &Image{ID: 1, ImageID: "co9999"},
					TotalRating: 80.5,
				},
				{
					ID:          9012,
					Name:        "Kirby's Adventure",
					Cover:       &Image{ID: 2, ImageID: "co8888"},
					TotalRating: 82.0,
				},
			})
		}
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

	games, err := c.GetSimilarGames(1234)
	require.NoError(t, err)
	require.Len(t, games, 2)

	assert.Equal(t, 5678, games[0].ID)
	assert.Equal(t, "Super Mario Bros. 2", games[0].Name)
	assert.NotNil(t, games[0].Cover)
	assert.Equal(t, "co9999", games[0].Cover.ImageID)
	assert.InDelta(t, 80.5, games[0].TotalRating, 0.01)

	assert.Equal(t, 9012, games[1].ID)
	assert.Equal(t, "Kirby's Adventure", games[1].Name)
}

func TestGetSimilarGames_NoSimilarGames(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Return a game with no similar_games field
		json.NewEncoder(w).Encode([]map[string]interface{}{
			{},
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

	games, err := c.GetSimilarGames(1234)
	require.NoError(t, err)
	assert.Nil(t, games)
}

func TestGetSimilarGames_APIError(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("internal error"))
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

	_, err := c.GetSimilarGames(1234)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "500")
}

func TestGetGameByID_Success(t *testing.T) {
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
		body, err := io.ReadAll(r.Body)
		require.NoError(t, err)
		assert.Contains(t, string(body), "version_parent")

		category := IGDBCategoryRemaster
		versionParentID := 99
		json.NewEncoder(w).Encode([]Game{
			{
				ID:              1234,
				Name:            "Disney's Aladdin",
				Summary:         "A platformer based on the Disney film",
				Cover:           &Image{ID: 1, ImageID: "co5555"},
				VersionParentID: &versionParentID,
				Category:        &category,
				Screenshots: []Image{
					{ID: 2, ImageID: "sc6666"},
				},
				Genres: []Genre{
					{ID: 8, Name: "Platform"},
				},
				InvolvedCompanies: []InvolvedCompany{
					{Company: Company{ID: 1, Name: "Capcom"}, Developer: true, Publisher: false},
					{Company: Company{ID: 2, Name: "Nintendo"}, Developer: false, Publisher: true},
				},
				FirstReleaseDate: 753926400,
				AggregatedRating: 78.0,
				GameModes: []GameMode{
					{ID: 1, Name: "Single player"},
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

	game, err := c.GetGameByID(1234)
	require.NoError(t, err)
	require.NotNil(t, game)

	assert.Equal(t, 1234, game.ID)
	assert.Equal(t, "Disney's Aladdin", game.Name)
	assert.Equal(t, "A platformer based on the Disney film", game.Summary)
	assert.NotNil(t, game.Cover)
	assert.Equal(t, "co5555", game.Cover.ImageID)
	assert.Len(t, game.Screenshots, 1)
	assert.Equal(t, "sc6666", game.Screenshots[0].ImageID)
	assert.Len(t, game.Genres, 1)
	assert.Equal(t, "Platform", game.Genres[0].Name)
	assert.Len(t, game.InvolvedCompanies, 2)
	assert.True(t, game.InvolvedCompanies[0].Developer)
	assert.Equal(t, "Capcom", game.InvolvedCompanies[0].Company.Name)
	assert.Equal(t, int64(753926400), game.FirstReleaseDate)
	assert.InDelta(t, 78.0, game.AggregatedRating, 0.01)
	assert.Len(t, game.GameModes, 1)
	require.NotNil(t, game.VersionParentID)
	assert.Equal(t, 99, *game.VersionParentID)
	require.NotNil(t, game.Category)
	assert.Equal(t, IGDBCategoryRemaster, *game.Category)
}

func TestSearchGameExactIncludesTitleRelationshipFields(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	var capturedQuery string
	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, err := io.ReadAll(r.Body)
		require.NoError(t, err)
		capturedQuery = string(body)
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

	_, err := c.SearchGameExact("Resident Evil 2", []int{4})
	require.NoError(t, err)
	assert.Contains(t, capturedQuery, "parent_game")
	assert.Contains(t, capturedQuery, "version_parent")
	assert.Contains(t, capturedQuery, "category")
}

func TestGetGameByID_NotFound(t *testing.T) {
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

	game, err := c.GetGameByID(99999)
	require.NoError(t, err)
	assert.Nil(t, game)
}

func TestGetGameByID_APIError(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("internal error"))
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

	_, err := c.GetGameByID(1234)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "500")
}

func TestGetTimeToBeat_Success(t *testing.T) {
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
		assert.Equal(t, "/v4/game_time_to_beats", r.URL.Path)
		assert.Equal(t, "myid", r.Header.Get("Client-ID"))
		assert.Equal(t, "Bearer test-token", r.Header.Get("Authorization"))

		json.NewEncoder(w).Encode([]TimeToBeat{
			{Hastily: 31200, Normally: 111888, Completely: 187200},
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

	ttb, err := c.GetTimeToBeat(1029)
	require.NoError(t, err)
	require.NotNil(t, ttb)
	assert.Equal(t, 31200, ttb.Hastily)
	assert.Equal(t, 111888, ttb.Normally)
	assert.Equal(t, 187200, ttb.Completely)
}

func TestGetTimeToBeat_NoData(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]TimeToBeat{})
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

	ttb, err := c.GetTimeToBeat(99999)
	require.NoError(t, err)
	assert.Nil(t, ttb)
}

func TestGetTimeToBeat_APIError(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("internal error"))
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

	_, err := c.GetTimeToBeat(1234)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "500")
}

func TestPlatformMapping(t *testing.T) {
	// Verify key platform mappings exist
	assert.Equal(t, []int{18}, AbbreviationToIGDBPlatform["NES"])
	// SNES maps to both the international SNES id (19) AND the Japanese
	// Super Famicom id (58). Japan-only releases like Alcahest (IGDB id
	// 3651) are filed under Super Famicom on IGDB and would not be found
	// by a single-id search filter. See scraper_igdb_test.go /
	// TestScrapeGame_SNESJapanOnlyGame_SearchesSuperFamicomPlatform for
	// the full regression test.
	assert.Equal(t, []int{19, 58}, AbbreviationToIGDBPlatform["SNES"])
	assert.Equal(t, []int{7}, AbbreviationToIGDBPlatform["PSX"])
	assert.Equal(t, []int{29}, AbbreviationToIGDBPlatform["GEN"])
	assert.Equal(t, []int{4}, AbbreviationToIGDBPlatform["N64"])

	// Unknown platform returns zero value
	_, exists := AbbreviationToIGDBPlatform["UNKNOWN"]
	assert.False(t, exists)
}

func TestIGDBPlatformsFor(t *testing.T) {
	tests := []struct {
		name          string
		consoleAbbrev string
		hint          string
		want          []int
	}{
		{
			name:          "non-scummvm console falls through to direct map lookup",
			consoleAbbrev: "SNES",
			hint:          "Super Mario World",
			want:          []int{19, 58},
		},
		{
			name:          "non-scummvm console with no map entry returns empty",
			consoleAbbrev: "UNKNOWN",
			hint:          "anything",
			want:          nil,
		},
		{
			name:          "scummvm DOS hint resolves to DOS only",
			consoleAbbrev: "SCUMMVM",
			hint:          "The Secret of Monkey Island (CD DOS VGA)",
			want:          []int{13},
		},
		{
			name:          "scummvm VGA hint resolves to DOS",
			consoleAbbrev: "SCUMMVM",
			hint:          "Loom (VGA)",
			want:          []int{13},
		},
		{
			name:          "scummvm Amiga hint resolves to Amiga",
			consoleAbbrev: "SCUMMVM",
			hint:          "Indiana Jones and the Last Crusade (Amiga)",
			want:          []int{16},
		},
		{
			name:          "scummvm Macintosh hint resolves to Mac",
			consoleAbbrev: "SCUMMVM",
			hint:          "The Secret of Monkey Island (Macintosh)",
			want:          []int{14},
		},
		{
			name:          "scummvm Atari ST hint resolves to Atari ST",
			consoleAbbrev: "SCUMMVM",
			hint:          "Zak McKracken (Atari ST)",
			want:          []int{63},
		},
		{
			name:          "scummvm Apple II hint resolves to Apple II",
			consoleAbbrev: "SCUMMVM",
			hint:          "Maniac Mansion (Apple IIgs)",
			want:          []int{75},
		},
		{
			name:          "scummvm with no hint searches multi-platform",
			consoleAbbrev: "SCUMMVM",
			hint:          "Day of the Tentacle",
			want:          []int{13, 16, 14, 63},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := IGDBPlatformsFor(tt.consoleAbbrev, tt.hint)
			assert.Equal(t, tt.want, got)
		})
	}
}

func TestLookupScummvmGameTitle(t *testing.T) {
	tests := []struct {
		name   string
		gameid string
		want   string
	}{
		{
			name:   "gob1 resolves to Gobliiins",
			gameid: "gob1",
			want:   "Gobliiins",
		},
		{
			name:   "case-insensitive (uppercase)",
			gameid: "GOB1",
			want:   "Gobliiins",
		},
		{
			name:   "leading/trailing whitespace tolerated",
			gameid: "  indy3 ",
			want:   "Indiana Jones and the Last Crusade: The Graphic Adventure",
		},
		{
			name:   "ite resolves to Inherit the Earth",
			gameid: "ite",
			want:   "Inherit the Earth: Quest for the Orb",
		},
		{
			name:   "monkey resolves to Secret of Monkey Island",
			gameid: "monkey",
			want:   "The Secret of Monkey Island",
		},
		{
			name:   "monkey1 alias resolves to the same",
			gameid: "monkey1",
			want:   "The Secret of Monkey Island",
		},
		{
			name:   "eob1 alias resolves to Eye of the Beholder",
			gameid: "eob1",
			want:   "Eye of the Beholder",
		},
		{
			name:   "freddi1 alias resolves to Freddi Fish 1",
			gameid: "freddi1",
			want:   "Freddi Fish and the Case of the Missing Kelp Seeds",
		},
		{
			name:   "myst1 alias resolves to Myst",
			gameid: "myst1",
			want:   "Myst",
		},
		{
			name:   "unknown gameid returns empty",
			gameid: "unknown_engine_xyz",
			want:   "",
		},
		{
			name:   "descriptive title with parentheses isn't matched",
			gameid: "The Secret of Monkey Island (CD DOS VGA)",
			want:   "",
		},
		{
			name:   "title with spaces isn't matched",
			gameid: "Day of the Tentacle",
			want:   "",
		},
		{
			name:   "empty string returns empty",
			gameid: "",
			want:   "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := LookupScummvmGameTitle(tt.gameid)
			assert.Equal(t, tt.want, got)
		})
	}
}
