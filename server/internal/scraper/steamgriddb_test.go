package scraper

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestSteamGridDBClient_SearchGame(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "Bearer test-api-key", r.Header.Get("Authorization"))
		assert.Equal(t, "/api/v2/search/autocomplete/Super Mario World", r.URL.Path)

		resp := steamGridDBResponse[[]steamGridDBSearchResult]{
			Success: true,
			Data: []steamGridDBSearchResult{
				{ID: 5432, Name: "Super Mario World"},
				{ID: 9999, Name: "Super Mario World 2"},
			},
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	}))
	defer server.Close()

	client := &SteamGridDBClient{
		APIKey:     "test-api-key",
		BaseURL:    server.URL + "/api/v2",
		HTTPClient: server.Client(),
	}

	gameID, err := client.SearchGame("Super Mario World", "")
	require.NoError(t, err)
	assert.Equal(t, 5432, gameID)
}

func TestSteamGridDBClient_SearchGame_NoResults(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		resp := steamGridDBResponse[[]steamGridDBSearchResult]{
			Success: true,
			Data:    []steamGridDBSearchResult{},
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	}))
	defer server.Close()

	client := &SteamGridDBClient{
		APIKey:     "test-api-key",
		BaseURL:    server.URL + "/api/v2",
		HTTPClient: server.Client(),
	}

	_, err := client.SearchGame("NonexistentGame12345", "")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "no results found")
}

func TestSteamGridDBClient_SearchGame_Unauthorized(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte(`{"success":false}`))
	}))
	defer server.Close()

	client := &SteamGridDBClient{
		APIKey:     "bad-key",
		BaseURL:    server.URL + "/api/v2",
		HTTPClient: server.Client(),
	}

	_, err := client.SearchGame("Test", "")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "unauthorized")
}

func TestSteamGridDBClient_GetHeroes(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/api/v2/heroes/game/5432", r.URL.Path)

		resp := steamGridDBResponse[[]SteamGridDBImage]{
			Success: true,
			Data: []SteamGridDBImage{
				{ID: 1, URL: "https://cdn.steamgriddb.com/hero/1.png", Score: 5, Width: 1920, Height: 620},
				{ID: 2, URL: "https://cdn.steamgriddb.com/hero/2.png", Score: 10, Width: 1920, Height: 620},
				{ID: 3, URL: "https://cdn.steamgriddb.com/hero/3.png", Score: 3, Width: 1920, Height: 620},
			},
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	}))
	defer server.Close()

	client := &SteamGridDBClient{
		APIKey:     "test-api-key",
		BaseURL:    server.URL + "/api/v2",
		HTTPClient: server.Client(),
	}

	heroes, err := client.GetHeroes(5432)
	require.NoError(t, err)
	require.Len(t, heroes, 3)

	// Verify sorted by score descending
	assert.Equal(t, 10, heroes[0].Score)
	assert.Equal(t, "https://cdn.steamgriddb.com/hero/2.png", heroes[0].URL)
	assert.Equal(t, 5, heroes[1].Score)
	assert.Equal(t, 3, heroes[2].Score)
}

func TestSteamGridDBClient_GetGrids(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/api/v2/grids/game/100", r.URL.Path)

		resp := steamGridDBResponse[[]SteamGridDBImage]{
			Success: true,
			Data: []SteamGridDBImage{
				{ID: 10, URL: "https://cdn.steamgriddb.com/grid/10.png", Score: 8},
			},
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	}))
	defer server.Close()

	client := &SteamGridDBClient{
		APIKey:     "test-api-key",
		BaseURL:    server.URL + "/api/v2",
		HTTPClient: server.Client(),
	}

	grids, err := client.GetGrids(100)
	require.NoError(t, err)
	require.Len(t, grids, 1)
	assert.Equal(t, "https://cdn.steamgriddb.com/grid/10.png", grids[0].URL)
}

func TestSteamGridDBClient_GetLogos(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/api/v2/logos/game/200", r.URL.Path)

		resp := steamGridDBResponse[[]SteamGridDBImage]{
			Success: true,
			Data: []SteamGridDBImage{
				{ID: 20, URL: "https://cdn.steamgriddb.com/logo/20.png", Score: 12},
			},
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	}))
	defer server.Close()

	client := &SteamGridDBClient{
		APIKey:     "test-api-key",
		BaseURL:    server.URL + "/api/v2",
		HTTPClient: server.Client(),
	}

	logos, err := client.GetLogos(200)
	require.NoError(t, err)
	require.Len(t, logos, 1)
	assert.Equal(t, "https://cdn.steamgriddb.com/logo/20.png", logos[0].URL)
}

func TestSteamGridDBClient_GetIcons(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/api/v2/icons/game/300", r.URL.Path)

		resp := steamGridDBResponse[[]SteamGridDBImage]{
			Success: true,
			Data: []SteamGridDBImage{
				{ID: 30, URL: "https://cdn.steamgriddb.com/icon/30.png", Score: 7},
			},
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	}))
	defer server.Close()

	client := &SteamGridDBClient{
		APIKey:     "test-api-key",
		BaseURL:    server.URL + "/api/v2",
		HTTPClient: server.Client(),
	}

	icons, err := client.GetIcons(300)
	require.NoError(t, err)
	require.Len(t, icons, 1)
	assert.Equal(t, "https://cdn.steamgriddb.com/icon/30.png", icons[0].URL)
}

func TestSteamGridDBClient_GetBestArtwork(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		w.Header().Set("Content-Type", "application/json")

		switch {
		case r.URL.Path == "/api/v2/search/autocomplete/Zelda":
			resp := steamGridDBResponse[[]steamGridDBSearchResult]{
				Success: true,
				Data:    []steamGridDBSearchResult{{ID: 42, Name: "The Legend of Zelda"}},
			}
			json.NewEncoder(w).Encode(resp)

		case r.URL.Path == "/api/v2/heroes/game/42":
			resp := steamGridDBResponse[[]SteamGridDBImage]{
				Success: true,
				Data:    []SteamGridDBImage{{ID: 1, URL: "https://cdn.steamgriddb.com/hero/zelda.png", Score: 15}},
			}
			json.NewEncoder(w).Encode(resp)

		case r.URL.Path == "/api/v2/grids/game/42":
			resp := steamGridDBResponse[[]SteamGridDBImage]{
				Success: true,
				Data:    []SteamGridDBImage{{ID: 2, URL: "https://cdn.steamgriddb.com/grid/zelda.png", Score: 10}},
			}
			json.NewEncoder(w).Encode(resp)

		case r.URL.Path == "/api/v2/logos/game/42":
			resp := steamGridDBResponse[[]SteamGridDBImage]{
				Success: true,
				Data:    []SteamGridDBImage{{ID: 3, URL: "https://cdn.steamgriddb.com/logo/zelda.png", Score: 8}},
			}
			json.NewEncoder(w).Encode(resp)

		case r.URL.Path == "/api/v2/icons/game/42":
			resp := steamGridDBResponse[[]SteamGridDBImage]{
				Success: true,
				Data:    []SteamGridDBImage{{ID: 4, URL: "https://cdn.steamgriddb.com/icon/zelda.png", Score: 5}},
			}
			json.NewEncoder(w).Encode(resp)

		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer server.Close()

	client := &SteamGridDBClient{
		APIKey:     "test-api-key",
		BaseURL:    server.URL + "/api/v2",
		HTTPClient: server.Client(),
	}

	artwork, err := client.GetBestArtwork("Zelda", "")
	require.NoError(t, err)
	require.NotNil(t, artwork)

	assert.Equal(t, 42, artwork.SteamGridDBID)
	assert.Equal(t, "https://cdn.steamgriddb.com/hero/zelda.png", artwork.HeroURL)
	assert.Equal(t, "https://cdn.steamgriddb.com/grid/zelda.png", artwork.GridURL)
	assert.Equal(t, "https://cdn.steamgriddb.com/logo/zelda.png", artwork.LogoURL)
	assert.Equal(t, "https://cdn.steamgriddb.com/icon/zelda.png", artwork.IconURL)

	// 1 search + 4 image type requests
	assert.Equal(t, 5, callCount)
}

func TestSteamGridDBClient_GetBestArtwork_PartialFailure(t *testing.T) {
	// Test that partial failures (e.g. no logos) don't fail the whole request
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")

		switch {
		case r.URL.Path == "/api/v2/search/autocomplete/Tetris":
			resp := steamGridDBResponse[[]steamGridDBSearchResult]{
				Success: true,
				Data:    []steamGridDBSearchResult{{ID: 99, Name: "Tetris"}},
			}
			json.NewEncoder(w).Encode(resp)

		case r.URL.Path == "/api/v2/heroes/game/99":
			resp := steamGridDBResponse[[]SteamGridDBImage]{
				Success: true,
				Data:    []SteamGridDBImage{{ID: 1, URL: "https://cdn.steamgriddb.com/hero/tetris.png", Score: 5}},
			}
			json.NewEncoder(w).Encode(resp)

		case r.URL.Path == "/api/v2/grids/game/99":
			// No grids available
			resp := steamGridDBResponse[[]SteamGridDBImage]{
				Success: true,
				Data:    []SteamGridDBImage{},
			}
			json.NewEncoder(w).Encode(resp)

		case r.URL.Path == "/api/v2/logos/game/99":
			// Server error
			w.WriteHeader(http.StatusNotFound)

		case r.URL.Path == "/api/v2/icons/game/99":
			// Server error
			w.WriteHeader(http.StatusInternalServerError)

		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer server.Close()

	client := &SteamGridDBClient{
		APIKey:     "test-api-key",
		BaseURL:    server.URL + "/api/v2",
		HTTPClient: server.Client(),
	}

	artwork, err := client.GetBestArtwork("Tetris", "")
	require.NoError(t, err)
	require.NotNil(t, artwork)

	assert.Equal(t, 99, artwork.SteamGridDBID)
	assert.Equal(t, "https://cdn.steamgriddb.com/hero/tetris.png", artwork.HeroURL)
	assert.Empty(t, artwork.GridURL)  // empty array
	assert.Empty(t, artwork.LogoURL)  // 404 error
	assert.Empty(t, artwork.IconURL)  // 500 error
}

func TestSteamGridDBClient_RateLimited(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusTooManyRequests)
	}))
	defer server.Close()

	client := &SteamGridDBClient{
		APIKey:     "test-api-key",
		BaseURL:    server.URL + "/api/v2",
		HTTPClient: server.Client(),
	}

	_, err := client.SearchGame("Test", "")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "rate limited")
}

func TestBestImageURL(t *testing.T) {
	t.Run("empty slice returns empty string", func(t *testing.T) {
		assert.Empty(t, bestImageURL(nil))
		assert.Empty(t, bestImageURL([]SteamGridDBImage{}))
	})

	t.Run("returns first image URL", func(t *testing.T) {
		images := []SteamGridDBImage{
			{URL: "https://example.com/best.png", Score: 10},
			{URL: "https://example.com/worst.png", Score: 1},
		}
		assert.Equal(t, "https://example.com/best.png", bestImageURL(images))
	})
}
