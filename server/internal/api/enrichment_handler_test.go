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
	"gorm.io/gorm"
)

// enrichTestEnv holds shared test fixtures for enrichment handler tests.
type enrichTestEnv struct {
	database *gorm.DB
	router   http.Handler
	token    string
}

func setupEnrichTestEnv(t *testing.T) *enrichTestEnv {
	t.Helper()
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)
	return &enrichTestEnv{
		database: database,
		router:   router,
		token:    token,
	}
}

// createEnrichTestGame creates a game in the NES console with the given title and rating.
func createEnrichTestGame(t *testing.T, database *gorm.DB, title string, rating float64) db.Game {
	t.Helper()
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     title,
		FileName:  title + ".nes",
		FilePath:  "NES/" + title + ".nes",
		Rating:    rating,
		ScraperID: fmt.Sprintf("igdb:%d", 1000+int(rating)),
	}
	require.NoError(t, database.Create(&game).Error)
	return game
}

// --- Theme endpoint tests ---

func TestListThemes_Empty(t *testing.T) {
	env := setupEnrichTestEnv(t)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/themes", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var themes []ThemeResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &themes))
	assert.Empty(t, themes)
}

func TestListThemes_WithData(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Zelda", 90)
	game2 := createEnrichTestGame(t, env.database, "Mario", 85)

	// Create themes
	env.database.Create(&db.GameTheme{GameID: game1.ID, IGDBThemeID: 1, Name: "Fantasy"})
	env.database.Create(&db.GameTheme{GameID: game2.ID, IGDBThemeID: 1, Name: "Fantasy"})
	env.database.Create(&db.GameTheme{GameID: game1.ID, IGDBThemeID: 17, Name: "Sci-Fi"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/themes", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var themes []ThemeResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &themes))
	require.Len(t, themes, 2)

	// Sorted by game count DESC
	assert.Equal(t, "1", themes[0].ID)
	assert.Equal(t, "Fantasy", themes[0].Name)
	assert.Equal(t, 2, themes[0].GameCount)

	assert.Equal(t, "17", themes[1].ID)
	assert.Equal(t, "Sci-Fi", themes[1].Name)
	assert.Equal(t, 1, themes[1].GameCount)
}

func TestListThemeGames_Success(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Zelda", 90)
	game2 := createEnrichTestGame(t, env.database, "Mario", 85)
	_ = createEnrichTestGame(t, env.database, "Metroid", 80) // no theme

	env.database.Create(&db.GameTheme{GameID: game1.ID, IGDBThemeID: 1, Name: "Fantasy"})
	env.database.Create(&db.GameTheme{GameID: game2.ID, IGDBThemeID: 1, Name: "Fantasy"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/themes/1/games", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(2), resp.Total)
	assert.Equal(t, 1, resp.Page)
}

func TestListThemeGames_NotFound(t *testing.T) {
	env := setupEnrichTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/themes/99999/games", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestListThemeGames_InvalidID(t *testing.T) {
	env := setupEnrichTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/themes/abc/games", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

// --- Keyword endpoint tests ---

func TestListKeywords_Empty(t *testing.T) {
	env := setupEnrichTestEnv(t)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/keywords", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var keywords []KeywordResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &keywords))
	assert.Empty(t, keywords)
}

func TestListKeywords_WithData(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Zelda", 90)
	game2 := createEnrichTestGame(t, env.database, "Mario", 85)

	env.database.Create(&db.GameKeyword{GameID: game1.ID, IGDBKeywordID: 100, Name: "open world"})
	env.database.Create(&db.GameKeyword{GameID: game2.ID, IGDBKeywordID: 100, Name: "open world"})
	env.database.Create(&db.GameKeyword{GameID: game1.ID, IGDBKeywordID: 200, Name: "puzzle"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/keywords", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var keywords []KeywordResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &keywords))
	require.Len(t, keywords, 2)
	assert.Equal(t, "100", keywords[0].ID)
	assert.Equal(t, "open world", keywords[0].Name)
	assert.Equal(t, 2, keywords[0].GameCount)
}

func TestListKeywords_WithLimit(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game := createEnrichTestGame(t, env.database, "Zelda", 90)
	env.database.Create(&db.GameKeyword{GameID: game.ID, IGDBKeywordID: 100, Name: "keyword1"})
	env.database.Create(&db.GameKeyword{GameID: game.ID, IGDBKeywordID: 200, Name: "keyword2"})
	env.database.Create(&db.GameKeyword{GameID: game.ID, IGDBKeywordID: 300, Name: "keyword3"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/keywords?limit=2", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var keywords []KeywordResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &keywords))
	assert.Len(t, keywords, 2)
}

func TestListKeywordGames_Success(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game := createEnrichTestGame(t, env.database, "Zelda", 90)
	env.database.Create(&db.GameKeyword{GameID: game.ID, IGDBKeywordID: 100, Name: "open world"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/keywords/100/games", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(1), resp.Total)
}

func TestListKeywordGames_NotFound(t *testing.T) {
	env := setupEnrichTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/keywords/99999/games", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

// --- Franchise endpoint tests ---

func TestListFranchises_Empty(t *testing.T) {
	env := setupEnrichTestEnv(t)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/franchises", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var franchises []FranchiseResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &franchises))
	assert.Empty(t, franchises)
}

func TestListFranchises_WithData(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Zelda1", 90)
	game2 := createEnrichTestGame(t, env.database, "Zelda2", 85)
	game3 := createEnrichTestGame(t, env.database, "Mario1", 80)

	env.database.Create(&db.GameFranchise{GameID: game1.ID, IGDBFranchiseID: 100, FranchiseName: "The Legend of Zelda"})
	env.database.Create(&db.GameFranchise{GameID: game2.ID, IGDBFranchiseID: 100, FranchiseName: "The Legend of Zelda"})
	env.database.Create(&db.GameFranchise{GameID: game3.ID, IGDBFranchiseID: 200, FranchiseName: "Mario"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/franchises", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var franchises []FranchiseResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &franchises))
	require.Len(t, franchises, 2)

	// Sorted by game count DESC
	assert.Equal(t, "100", franchises[0].ID)
	assert.Equal(t, "The Legend of Zelda", franchises[0].Name)
	assert.Equal(t, 2, franchises[0].GameCount)

	assert.Equal(t, "200", franchises[1].ID)
	assert.Equal(t, "Mario", franchises[1].Name)
	assert.Equal(t, 1, franchises[1].GameCount)
}

func TestListFranchiseGames_Success(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Zelda1", 90)
	game2 := createEnrichTestGame(t, env.database, "Zelda2", 85)

	env.database.Create(&db.GameFranchise{GameID: game1.ID, IGDBFranchiseID: 100, FranchiseName: "Zelda"})
	env.database.Create(&db.GameFranchise{GameID: game2.ID, IGDBFranchiseID: 100, FranchiseName: "Zelda"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/franchises/100/games", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(2), resp.Total)
	assert.Equal(t, 1, resp.Page)
}

func TestListFranchiseGames_NotFound(t *testing.T) {
	env := setupEnrichTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/franchises/99999/games", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

// --- Series endpoint tests ---

func TestListSeries_Empty(t *testing.T) {
	env := setupEnrichTestEnv(t)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/series", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var series []SeriesListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &series))
	assert.Empty(t, series)
}

func TestListSeries_WithData(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Mario1", 90)
	game2 := createEnrichTestGame(t, env.database, "Mario2", 85)

	series := db.GameSeries{
		IGDBCollectionID: 789,
		Name:             "Super Mario",
	}
	require.NoError(t, env.database.Create(&series).Error)

	// Add entries (two local, one non-local)
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game1.ID, IGDBGameID: 100, Name: "Super Mario Bros.",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game2.ID, IGDBGameID: 200, Name: "Super Mario Bros. 2",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: nil, IGDBGameID: 300, Name: "Super Mario Bros. 3",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/series", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result []SeriesListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	require.Len(t, result, 1)

	assert.Equal(t, "Super Mario", result[0].Name)
	assert.Equal(t, 789, result[0].IGDBCollectionID)
	assert.Equal(t, 3, result[0].TotalGames)
	assert.Equal(t, 2, result[0].LibraryGames)
}

func TestGetSeriesDetail_Success(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game := createEnrichTestGame(t, env.database, "Mario1", 90)

	series := db.GameSeries{
		IGDBCollectionID: 789,
		Name:             "Super Mario",
	}
	require.NoError(t, env.database.Create(&series).Error)

	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game.ID, IGDBGameID: 100, Name: "Super Mario Bros.",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: nil, IGDBGameID: 200, Name: "Super Mario Bros. 2",
	})

	url := fmt.Sprintf("/api/series/%d", series.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var detail SeriesDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &detail))
	assert.Equal(t, "Super Mario", detail.Name)
	assert.Equal(t, 789, detail.IGDBCollectionID)
	require.Len(t, detail.Games, 2)

	// The local game
	assert.Equal(t, 100, detail.Games[0].IGDBGameID)
	assert.Equal(t, "Super Mario Bros.", detail.Games[0].Name)
	assert.True(t, detail.Games[0].InLibrary)
	assert.NotNil(t, detail.Games[0].LocalGameID)

	// The non-local game
	assert.Equal(t, 200, detail.Games[1].IGDBGameID)
	assert.Equal(t, "Super Mario Bros. 2", detail.Games[1].Name)
	assert.False(t, detail.Games[1].InLibrary)
	assert.Nil(t, detail.Games[1].LocalGameID)
}

func TestGetSeriesDetail_NotFound(t *testing.T) {
	env := setupEnrichTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/series/99999", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

// --- Game filter enrichment tests ---

func TestListGames_FilterByTheme(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Zelda", 90)
	_ = createEnrichTestGame(t, env.database, "Tetris", 80)

	env.database.Create(&db.GameTheme{GameID: game1.ID, IGDBThemeID: 1, Name: "Fantasy"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games?theme=1", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(1), resp.Total)
}

func TestListGames_FilterByKeyword(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Zelda", 90)
	_ = createEnrichTestGame(t, env.database, "Tetris", 80)

	env.database.Create(&db.GameKeyword{GameID: game1.ID, IGDBKeywordID: 100, Name: "open world"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games?keyword=100", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(1), resp.Total)
}

func TestListGames_FilterByPerspective(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Zelda", 90)
	_ = createEnrichTestGame(t, env.database, "Tetris", 80)

	env.database.Create(&db.GamePlayerPerspective{GameID: game1.ID, IGDBPerspectiveID: 3, Name: "Side view"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games?perspective=3", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(1), resp.Total)
}

// --- Admin enrichment endpoint tests ---

func TestEnrichMetadataStatus_NoActiveEnrichment(t *testing.T) {
	env := setupEnrichTestEnv(t)

	// The first registered user is the owner, so the token is admin/owner
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/enrich-metadata/status", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, false, resp["active"])
}

func TestTriggerEnrichMetadata_NoIGDBConfig(t *testing.T) {
	env := setupEnrichTestEnv(t)

	// Scraper has no IGDB client configured by default in tests
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/enrich-metadata", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

// --- Authentication tests ---

func TestEnrichmentEndpoints_RequireAuth(t *testing.T) {
	env := setupEnrichTestEnv(t)

	endpoints := []string{
		"/api/themes",
		"/api/keywords",
		"/api/series",
		"/api/franchises",
	}

	for _, endpoint := range endpoints {
		t.Run(endpoint, func(t *testing.T) {
			w := httptest.NewRecorder()
			req := httptest.NewRequest("GET", endpoint, nil)
			env.router.ServeHTTP(w, req)
			assert.Equal(t, http.StatusUnauthorized, w.Code)
		})
	}
}
