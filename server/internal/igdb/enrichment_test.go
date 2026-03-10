package igdb

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestGetGameEnrichment_Success(t *testing.T) {
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

		json.NewEncoder(w).Encode([]map[string]interface{}{
			{
				"themes": []map[string]interface{}{
					{"id": 1, "name": "Fantasy"},
					{"id": 17, "name": "Sci-Fi"},
				},
				"keywords": []map[string]interface{}{
					{"id": 100, "name": "time travel"},
				},
				"player_perspectives": []map[string]interface{}{
					{"id": 3, "name": "Side view"},
				},
				"franchises": []int{123, 456},
				"collection": 789,
				"artworks": []map[string]interface{}{
					{"image_id": "ar1234", "width": 1920, "height": 1080},
					{"image_id": "ar5678", "width": 3840, "height": 2160},
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

	enrichment, err := c.GetGameEnrichment(1234)
	require.NoError(t, err)
	require.NotNil(t, enrichment)

	assert.Len(t, enrichment.Themes, 2)
	assert.Equal(t, 1, enrichment.Themes[0].ID)
	assert.Equal(t, "Fantasy", enrichment.Themes[0].Name)
	assert.Equal(t, 17, enrichment.Themes[1].ID)
	assert.Equal(t, "Sci-Fi", enrichment.Themes[1].Name)

	assert.Len(t, enrichment.Keywords, 1)
	assert.Equal(t, 100, enrichment.Keywords[0].ID)
	assert.Equal(t, "time travel", enrichment.Keywords[0].Name)

	assert.Len(t, enrichment.PlayerPerspectives, 1)
	assert.Equal(t, 3, enrichment.PlayerPerspectives[0].ID)
	assert.Equal(t, "Side view", enrichment.PlayerPerspectives[0].Name)

	assert.Equal(t, []int{123, 456}, enrichment.Franchises)

	require.NotNil(t, enrichment.CollectionID)
	assert.Equal(t, 789, *enrichment.CollectionID)

	assert.Len(t, enrichment.Artworks, 2)
	assert.Equal(t, "ar1234", enrichment.Artworks[0].ImageID)
	assert.Equal(t, 1920, enrichment.Artworks[0].Width)
	assert.Equal(t, 1080, enrichment.Artworks[0].Height)
}

func TestGetGameEnrichment_NotFound(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]map[string]interface{}{})
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

	enrichment, err := c.GetGameEnrichment(99999)
	require.NoError(t, err)
	assert.Nil(t, enrichment)
}

func TestGetGameEnrichment_APIError(t *testing.T) {
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

	_, err := c.GetGameEnrichment(1234)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "500")
}

func TestGetGameEnrichment_NoCollection(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]map[string]interface{}{
			{
				"themes": []map[string]interface{}{
					{"id": 1, "name": "Fantasy"},
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

	enrichment, err := c.GetGameEnrichment(1234)
	require.NoError(t, err)
	require.NotNil(t, enrichment)

	assert.Len(t, enrichment.Themes, 1)
	assert.Nil(t, enrichment.CollectionID)
	assert.Empty(t, enrichment.Franchises)
	assert.Empty(t, enrichment.Artworks)
}

func TestGetCollection_Success(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/v4/collections", r.URL.Path)
		json.NewEncoder(w).Encode([]CollectionData{
			{ID: 789, Name: "Super Mario", GameIDs: []int{100, 200, 300}},
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

	collection, err := c.GetCollection(789)
	require.NoError(t, err)
	require.NotNil(t, collection)

	assert.Equal(t, 789, collection.ID)
	assert.Equal(t, "Super Mario", collection.Name)
	assert.Equal(t, []int{100, 200, 300}, collection.GameIDs)
}

func TestGetCollection_NotFound(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]CollectionData{})
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

	collection, err := c.GetCollection(99999)
	require.NoError(t, err)
	assert.Nil(t, collection)
}

func TestGetFranchise_Success(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/v4/franchises", r.URL.Path)
		json.NewEncoder(w).Encode([]FranchiseData{
			{ID: 123, Name: "The Legend of Zelda", GameIDs: []int{10, 20, 30, 40}},
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

	franchise, err := c.GetFranchise(123)
	require.NoError(t, err)
	require.NotNil(t, franchise)

	assert.Equal(t, 123, franchise.ID)
	assert.Equal(t, "The Legend of Zelda", franchise.Name)
	assert.Equal(t, []int{10, 20, 30, 40}, franchise.GameIDs)
}

func TestGetFranchise_NotFound(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]FranchiseData{})
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

	franchise, err := c.GetFranchise(99999)
	require.NoError(t, err)
	assert.Nil(t, franchise)
}

func TestGetCollectionGames_Success(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]map[string]interface{}{
			{"id": 100, "name": "Super Mario Bros.", "cover": map[string]interface{}{"image_id": "co1111"}},
			{"id": 200, "name": "Super Mario Bros. 2", "cover": map[string]interface{}{"image_id": "co2222"}},
			{"id": 300, "name": "Super Mario Bros. 3"},
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

	games, err := c.GetCollectionGames([]int{100, 200, 300})
	require.NoError(t, err)
	require.Len(t, games, 3)

	assert.Equal(t, 100, games[0].ID)
	assert.Equal(t, "Super Mario Bros.", games[0].Name)
	assert.Equal(t, "co1111", games[0].CoverImageID)

	assert.Equal(t, 200, games[1].ID)
	assert.Equal(t, "co2222", games[1].CoverImageID)

	// Game without cover
	assert.Equal(t, 300, games[2].ID)
	assert.Equal(t, "", games[2].CoverImageID)
}

func TestGetCollectionGames_EmptyInput(t *testing.T) {
	c := &Client{
		ClientID:     "myid",
		ClientSecret: "mysecret",
		HTTPClient:   &http.Client{Timeout: 5 * time.Second},
		rateLimiter:  time.Tick(time.Millisecond),
	}

	games, err := c.GetCollectionGames(nil)
	require.NoError(t, err)
	assert.Nil(t, games)

	games, err = c.GetCollectionGames([]int{})
	require.NoError(t, err)
	assert.Nil(t, games)
}
