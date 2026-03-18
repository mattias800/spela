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
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
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
		IsPrimary: true,
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

func TestListSeries_ExcludesSoftDeletedGames(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Mario1", 90)
	game2 := createEnrichTestGame(t, env.database, "Mario2", 85)

	series := db.GameSeries{
		IGDBCollectionID: 789,
		Name:             "Super Mario",
	}
	require.NoError(t, env.database.Create(&series).Error)

	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game1.ID, IGDBGameID: 100, Name: "Super Mario Bros.",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game2.ID, IGDBGameID: 200, Name: "Super Mario Bros. 2",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: nil, IGDBGameID: 300, Name: "Super Mario Bros. 3",
	})

	// Soft-delete game2
	env.database.Delete(&game2)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/series", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result []SeriesListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	require.Len(t, result, 1)

	assert.Equal(t, "Super Mario", result[0].Name)
	assert.Equal(t, 3, result[0].TotalGames)
	assert.Equal(t, 1, result[0].LibraryGames) // Only game1, game2 is soft-deleted
}

func TestListThemes_ExcludesSoftDeletedGames(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Zelda", 90)
	game2 := createEnrichTestGame(t, env.database, "Mario", 85)

	env.database.Create(&db.GameTheme{GameID: game1.ID, IGDBThemeID: 1, Name: "Fantasy"})
	env.database.Create(&db.GameTheme{GameID: game2.ID, IGDBThemeID: 1, Name: "Fantasy"})

	// Soft-delete game2
	env.database.Delete(&game2)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/themes", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var themes []ThemeResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &themes))
	require.Len(t, themes, 1)
	assert.Equal(t, 1, themes[0].GameCount) // Only game1
}

func TestListKeywords_ExcludesSoftDeletedGames(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Zelda", 90)
	game2 := createEnrichTestGame(t, env.database, "Mario", 85)

	env.database.Create(&db.GameKeyword{GameID: game1.ID, IGDBKeywordID: 100, Name: "open world"})
	env.database.Create(&db.GameKeyword{GameID: game2.ID, IGDBKeywordID: 100, Name: "open world"})

	// Soft-delete game2
	env.database.Delete(&game2)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/keywords", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var keywords []KeywordResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &keywords))
	require.Len(t, keywords, 1)
	assert.Equal(t, 1, keywords[0].GameCount) // Only game1
}

func TestListFranchises_ExcludesSoftDeletedGames(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Zelda1", 90)
	game2 := createEnrichTestGame(t, env.database, "Zelda2", 85)

	env.database.Create(&db.GameFranchise{GameID: game1.ID, IGDBFranchiseID: 100, FranchiseName: "The Legend of Zelda"})
	env.database.Create(&db.GameFranchise{GameID: game2.ID, IGDBFranchiseID: 100, FranchiseName: "The Legend of Zelda"})

	// Soft-delete game2
	env.database.Delete(&game2)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/franchises", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var franchises []FranchiseResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &franchises))
	require.Len(t, franchises, 1)
	assert.Equal(t, 1, franchises[0].GameCount) // Only game1
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

// --- Extended Series Detail tests ---

func TestGetSeriesDetail_ExtendedFields(t *testing.T) {
	env := setupEnrichTestEnv(t)

	// Create a game on NES console
	game1 := createEnrichTestGame(t, env.database, "Zelda1", 90)
	// Update with more fields
	env.database.Model(&game1).Updates(map[string]interface{}{
		"release_date": "1986-02-21",
	})

	// Create second game on SNES console
	var snesConsole db.Console
	require.NoError(t, env.database.Where("abbreviation = ?", "SNES").First(&snesConsole).Error)
	game2 := db.Game{
		ConsoleID:   snesConsole.ID,
		Title:       "Zelda2",
		FileName:    "Zelda2.sfc",
		FilePath:    "SNES/Zelda2.sfc",
		Rating:      95,
		ReleaseDate: "1991-11-21",
		ScraperID:   "igdb:2000",
	}
	require.NoError(t, env.database.Create(&game2).Error)

	// Create hero artwork for game2 (highest rated)
	env.database.Create(&db.GameArtwork{
		GameID:  game2.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/zelda2.png",
	})

	series := db.GameSeries{
		IGDBCollectionID: 100,
		Name:             "The Legend of Zelda",
	}
	require.NoError(t, env.database.Create(&series).Error)

	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game1.ID, IGDBGameID: 100, Name: "Zelda 1",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game2.ID, IGDBGameID: 200, Name: "Zelda 2",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: nil, IGDBGameID: 300, Name: "Zelda 3",
	})

	url := fmt.Sprintf("/api/series/%d", series.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var detail SeriesDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &detail))

	// Top-level extended fields
	assert.Equal(t, "The Legend of Zelda", detail.Name)
	assert.Equal(t, 2, detail.LibraryGames)
	assert.Equal(t, 3, detail.TotalGames)
	assert.Equal(t, "https://cdn.steamgriddb.com/hero/zelda2.png", detail.HeroURL)

	// Consoles list
	require.Len(t, detail.Consoles, 2)
	consoleMap := make(map[string]SeriesConsoleInfo)
	for _, c := range detail.Consoles {
		consoleMap[c.Abbreviation] = c
	}
	assert.Equal(t, 1, consoleMap["nes"].GameCount)
	assert.Equal(t, 1, consoleMap["snes"].GameCount)

	// Per-game fields
	require.Len(t, detail.Games, 3)

	// Find game1 (Zelda 1) in the response
	var zelda1, zelda2 *SeriesGameResponse
	for i := range detail.Games {
		if detail.Games[i].IGDBGameID == 100 {
			zelda1 = &detail.Games[i]
		}
		if detail.Games[i].IGDBGameID == 200 {
			zelda2 = &detail.Games[i]
		}
	}
	require.NotNil(t, zelda1)
	require.NotNil(t, zelda2)

	assert.True(t, zelda1.InLibrary)
	assert.Equal(t, "1986-02-21", zelda1.ReleaseDate)
	assert.Equal(t, 90.0, zelda1.Rating)
	assert.Equal(t, "nes", zelda1.ConsoleAbbreviation)
	assert.NotEmpty(t, zelda1.ConsoleName)

	assert.True(t, zelda2.InLibrary)
	assert.Equal(t, "1991-11-21", zelda2.ReleaseDate)
	assert.Equal(t, 95.0, zelda2.Rating)
	assert.Equal(t, "snes", zelda2.ConsoleAbbreviation)

	// Non-local game should have no per-game fields
	var zelda3 *SeriesGameResponse
	for i := range detail.Games {
		if detail.Games[i].IGDBGameID == 300 {
			zelda3 = &detail.Games[i]
		}
	}
	require.NotNil(t, zelda3)
	assert.False(t, zelda3.InLibrary)
	assert.Empty(t, zelda3.ReleaseDate)
	assert.Equal(t, 0.0, zelda3.Rating)
	assert.Empty(t, zelda3.ConsoleAbbreviation)
}

func TestGetSeriesDetail_HeroFromBestRatedGame(t *testing.T) {
	env := setupEnrichTestEnv(t)

	// Create two games: lower rated has hero, higher rated does not
	game1 := createEnrichTestGame(t, env.database, "LowRated", 50)
	game2 := createEnrichTestGame(t, env.database, "HighRated", 99)

	env.database.Create(&db.GameArtwork{
		GameID:  game1.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/low.png",
	})
	env.database.Create(&db.GameArtwork{
		GameID:  game2.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/high.png",
	})

	series := db.GameSeries{IGDBCollectionID: 200, Name: "Test Series"}
	require.NoError(t, env.database.Create(&series).Error)

	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game1.ID, IGDBGameID: 100, Name: "Low",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game2.ID, IGDBGameID: 200, Name: "High",
	})

	url := fmt.Sprintf("/api/series/%d", series.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var detail SeriesDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &detail))

	// Hero should come from the highest-rated game
	assert.Equal(t, "https://cdn.steamgriddb.com/hero/high.png", detail.HeroURL)
}

func TestGetSeriesDetail_NoHeroArt(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game := createEnrichTestGame(t, env.database, "NoArt", 80)
	series := db.GameSeries{IGDBCollectionID: 300, Name: "No Art Series"}
	require.NoError(t, env.database.Create(&series).Error)

	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game.ID, IGDBGameID: 100, Name: "NoArt",
	})

	url := fmt.Sprintf("/api/series/%d", series.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var detail SeriesDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &detail))

	assert.Empty(t, detail.HeroURL)
}

// --- GetGameSeries tests ---

func TestGetGameSeries_Empty(t *testing.T) {
	env := setupEnrichTestEnv(t)
	game := createEnrichTestGame(t, env.database, "Lonely Game", 80)

	url := fmt.Sprintf("/api/games/%d/series", game.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result []GameSeriesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	assert.Empty(t, result)
}

func TestGetGameSeries_WithData(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Mario1", 90)
	game2 := createEnrichTestGame(t, env.database, "Mario2", 85)

	series := db.GameSeries{IGDBCollectionID: 789, Name: "Super Mario"}
	require.NoError(t, env.database.Create(&series).Error)

	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game1.ID, IGDBGameID: 100, Name: "Mario 1",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game2.ID, IGDBGameID: 200, Name: "Mario 2",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: nil, IGDBGameID: 300, Name: "Mario 3",
	})

	url := fmt.Sprintf("/api/games/%d/series", game1.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result []GameSeriesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	require.Len(t, result, 1)
	assert.Equal(t, "Super Mario", result[0].Name)
	assert.Equal(t, 3, result[0].TotalGames)
	assert.Equal(t, 2, result[0].LibraryGames)
}

func TestGetGameSeries_GameNotFound(t *testing.T) {
	env := setupEnrichTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/99999/series", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetGameSeries_InvalidID(t *testing.T) {
	env := setupEnrichTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/abc/series", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestGetGameSeries_MultipleSeries(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game := createEnrichTestGame(t, env.database, "CrossoverGame", 90)

	series1 := db.GameSeries{IGDBCollectionID: 100, Name: "Series A"}
	series2 := db.GameSeries{IGDBCollectionID: 200, Name: "Series B"}
	require.NoError(t, env.database.Create(&series1).Error)
	require.NoError(t, env.database.Create(&series2).Error)

	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series1.ID, GameID: &game.ID, IGDBGameID: 100, Name: "Game",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series2.ID, GameID: &game.ID, IGDBGameID: 100, Name: "Game",
	})

	url := fmt.Sprintf("/api/games/%d/series", game.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result []GameSeriesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	assert.Len(t, result, 2)
}

// --- GetGameFranchises tests ---

func TestGetGameFranchises_Empty(t *testing.T) {
	env := setupEnrichTestEnv(t)
	game := createEnrichTestGame(t, env.database, "NoFranchise", 80)

	url := fmt.Sprintf("/api/games/%d/franchises", game.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result []GameFranchiseResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	assert.Empty(t, result)
}

func TestGetGameFranchises_WithData(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Zelda1", 90)
	game2 := createEnrichTestGame(t, env.database, "Zelda2", 85)

	env.database.Create(&db.GameFranchise{GameID: game1.ID, IGDBFranchiseID: 100, FranchiseName: "The Legend of Zelda"})
	env.database.Create(&db.GameFranchise{GameID: game2.ID, IGDBFranchiseID: 100, FranchiseName: "The Legend of Zelda"})

	url := fmt.Sprintf("/api/games/%d/franchises", game1.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result []GameFranchiseResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	require.Len(t, result, 1)
	assert.Equal(t, "100", result[0].ID)
	assert.Equal(t, "The Legend of Zelda", result[0].Name)
	assert.Equal(t, 2, result[0].TotalGames)
	assert.Equal(t, 2, result[0].LibraryGames)
}

func TestGetGameFranchises_GameNotFound(t *testing.T) {
	env := setupEnrichTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/99999/franchises", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetGameFranchises_InvalidID(t *testing.T) {
	env := setupEnrichTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/abc/franchises", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestGetGameFranchises_MultipleFranchises(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game := createEnrichTestGame(t, env.database, "CrossFranchise", 90)

	env.database.Create(&db.GameFranchise{GameID: game.ID, IGDBFranchiseID: 100, FranchiseName: "Franchise A"})
	env.database.Create(&db.GameFranchise{GameID: game.ID, IGDBFranchiseID: 200, FranchiseName: "Franchise B"})

	url := fmt.Sprintf("/api/games/%d/franchises", game.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result []GameFranchiseResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	assert.Len(t, result, 2)
}

func TestGetGameFranchises_ExcludesSoftDeletedGames(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "Alive", 90)
	game2 := createEnrichTestGame(t, env.database, "Deleted", 85)

	env.database.Create(&db.GameFranchise{GameID: game1.ID, IGDBFranchiseID: 100, FranchiseName: "TestFranchise"})
	env.database.Create(&db.GameFranchise{GameID: game2.ID, IGDBFranchiseID: 100, FranchiseName: "TestFranchise"})

	// Soft-delete game2
	env.database.Delete(&game2)

	url := fmt.Sprintf("/api/games/%d/franchises", game1.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result []GameFranchiseResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	require.Len(t, result, 1)
	assert.Equal(t, 1, result[0].TotalGames)   // Only the alive game
	assert.Equal(t, 1, result[0].LibraryGames) // Only the alive game
}

// --- GetFranchiseDetail tests ---

func TestGetFranchiseDetail_Success(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game := createEnrichTestGame(t, env.database, "Mario1", 90)

	franchise := db.GameFranchiseGroup{
		IGDBFranchiseID: 500,
		Name:            "Mario",
	}
	require.NoError(t, env.database.Create(&franchise).Error)

	env.database.Create(&db.GameFranchiseEntry{
		FranchiseGroupID: franchise.ID, GameID: &game.ID, IGDBGameID: 100, Name: "Super Mario Bros.",
	})
	env.database.Create(&db.GameFranchiseEntry{
		FranchiseGroupID: franchise.ID, GameID: nil, IGDBGameID: 200, Name: "Mario Kart",
	})

	url := fmt.Sprintf("/api/franchises/%d", franchise.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var detail FranchiseDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &detail))
	assert.Equal(t, "Mario", detail.Name)
	assert.Equal(t, 500, detail.IGDBFranchiseID)
	assert.Equal(t, 1, detail.LibraryGames)
	assert.Equal(t, 2, detail.TotalGames)
	require.Len(t, detail.Games, 2)

	// The local game
	var local, nonLocal *SeriesGameResponse
	for i := range detail.Games {
		if detail.Games[i].IGDBGameID == 100 {
			local = &detail.Games[i]
		}
		if detail.Games[i].IGDBGameID == 200 {
			nonLocal = &detail.Games[i]
		}
	}
	require.NotNil(t, local)
	assert.True(t, local.InLibrary)
	assert.NotNil(t, local.LocalGameID)
	assert.Equal(t, "Super Mario Bros.", local.Name)

	require.NotNil(t, nonLocal)
	assert.False(t, nonLocal.InLibrary)
	assert.Nil(t, nonLocal.LocalGameID)
	assert.Equal(t, "Mario Kart", nonLocal.Name)
}

func TestGetFranchiseDetail_NotFound(t *testing.T) {
	env := setupEnrichTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/franchises/99999", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetFranchiseDetail_ByIGDBID(t *testing.T) {
	env := setupEnrichTestEnv(t)

	franchise := db.GameFranchiseGroup{
		IGDBFranchiseID: 777,
		Name:            "Zelda Franchise",
	}
	require.NoError(t, env.database.Create(&franchise).Error)
	env.database.Create(&db.GameFranchiseEntry{
		FranchiseGroupID: franchise.ID, GameID: nil, IGDBGameID: 300, Name: "Zelda 1",
	})

	// Use IGDB franchise ID (777) as path param — tests the fallback lookup
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/franchises/777", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var detail FranchiseDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &detail))
	assert.Equal(t, "Zelda Franchise", detail.Name)
	assert.Equal(t, 777, detail.IGDBFranchiseID)
}

func TestGetFranchiseDetail_ExtendedFields(t *testing.T) {
	env := setupEnrichTestEnv(t)

	// Create a game on NES
	game1 := createEnrichTestGame(t, env.database, "Zelda1", 90)
	env.database.Model(&game1).Updates(map[string]interface{}{
		"release_date": "1986-02-21",
	})

	// Create a game on SNES
	var snesConsole db.Console
	require.NoError(t, env.database.Where("abbreviation = ?", "SNES").First(&snesConsole).Error)
	game2 := db.Game{
		ConsoleID:   snesConsole.ID,
		Title:       "Zelda2",
		FileName:    "Zelda2.sfc",
		FilePath:    "SNES/Zelda2.sfc",
		Rating:      95,
		ReleaseDate: "1991-11-21",
		ScraperID:   "igdb:2001",
	}
	require.NoError(t, env.database.Create(&game2).Error)

	// Hero artwork for game2 (highest rated)
	env.database.Create(&db.GameArtwork{
		GameID:  game2.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/zelda_franchise.png",
	})

	franchise := db.GameFranchiseGroup{
		IGDBFranchiseID: 600,
		Name:            "The Legend of Zelda",
	}
	require.NoError(t, env.database.Create(&franchise).Error)

	env.database.Create(&db.GameFranchiseEntry{
		FranchiseGroupID: franchise.ID, GameID: &game1.ID, IGDBGameID: 100, Name: "Zelda 1",
	})
	env.database.Create(&db.GameFranchiseEntry{
		FranchiseGroupID: franchise.ID, GameID: &game2.ID, IGDBGameID: 200, Name: "Zelda 2",
	})
	env.database.Create(&db.GameFranchiseEntry{
		FranchiseGroupID: franchise.ID, GameID: nil, IGDBGameID: 300, Name: "Zelda 3",
	})

	url := fmt.Sprintf("/api/franchises/%d", franchise.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var detail FranchiseDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &detail))

	assert.Equal(t, "The Legend of Zelda", detail.Name)
	assert.Equal(t, 2, detail.LibraryGames)
	assert.Equal(t, 3, detail.TotalGames)
	assert.Equal(t, "https://cdn.steamgriddb.com/hero/zelda_franchise.png", detail.HeroURL)

	// Consoles
	require.Len(t, detail.Consoles, 2)
	consoleMap := make(map[string]SeriesConsoleInfo)
	for _, c := range detail.Consoles {
		consoleMap[c.Abbreviation] = c
	}
	assert.Equal(t, 1, consoleMap["nes"].GameCount)
	assert.Equal(t, 1, consoleMap["snes"].GameCount)

	// Per-game fields
	require.Len(t, detail.Games, 3)
	var zelda1, zelda2 *SeriesGameResponse
	for i := range detail.Games {
		if detail.Games[i].IGDBGameID == 100 {
			zelda1 = &detail.Games[i]
		}
		if detail.Games[i].IGDBGameID == 200 {
			zelda2 = &detail.Games[i]
		}
	}
	require.NotNil(t, zelda1)
	assert.True(t, zelda1.InLibrary)
	assert.Equal(t, "1986-02-21", zelda1.ReleaseDate)
	assert.Equal(t, 90.0, zelda1.Rating)
	assert.Equal(t, "nes", zelda1.ConsoleAbbreviation)

	require.NotNil(t, zelda2)
	assert.True(t, zelda2.InLibrary)
	assert.Equal(t, "1991-11-21", zelda2.ReleaseDate)
	assert.Equal(t, 95.0, zelda2.Rating)
	assert.Equal(t, "snes", zelda2.ConsoleAbbreviation)
}

func TestGetFranchiseDetail_HeroFromBestRatedGame(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "LowFranchise", 50)
	game2 := createEnrichTestGame(t, env.database, "HighFranchise", 99)

	env.database.Create(&db.GameArtwork{GameID: game1.ID, HeroURL: "https://cdn.steamgriddb.com/hero/low_f.png"})
	env.database.Create(&db.GameArtwork{GameID: game2.ID, HeroURL: "https://cdn.steamgriddb.com/hero/high_f.png"})

	franchise := db.GameFranchiseGroup{IGDBFranchiseID: 700, Name: "Test Franchise"}
	require.NoError(t, env.database.Create(&franchise).Error)

	env.database.Create(&db.GameFranchiseEntry{
		FranchiseGroupID: franchise.ID, GameID: &game1.ID, IGDBGameID: 100, Name: "Low",
	})
	env.database.Create(&db.GameFranchiseEntry{
		FranchiseGroupID: franchise.ID, GameID: &game2.ID, IGDBGameID: 200, Name: "High",
	})

	url := fmt.Sprintf("/api/franchises/%d", franchise.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var detail FranchiseDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &detail))
	assert.Equal(t, "https://cdn.steamgriddb.com/hero/high_f.png", detail.HeroURL)
}

func TestGetFranchiseDetail_SoftDeletedGame(t *testing.T) {
	env := setupEnrichTestEnv(t)

	game1 := createEnrichTestGame(t, env.database, "AliveF", 90)
	game2 := createEnrichTestGame(t, env.database, "DeletedF", 85)

	franchise := db.GameFranchiseGroup{IGDBFranchiseID: 800, Name: "Soft Delete Franchise"}
	require.NoError(t, env.database.Create(&franchise).Error)

	env.database.Create(&db.GameFranchiseEntry{
		FranchiseGroupID: franchise.ID, GameID: &game1.ID, IGDBGameID: 100, Name: "Alive",
	})
	env.database.Create(&db.GameFranchiseEntry{
		FranchiseGroupID: franchise.ID, GameID: &game2.ID, IGDBGameID: 200, Name: "Deleted",
	})

	// Soft-delete game2
	env.database.Delete(&game2)

	url := fmt.Sprintf("/api/franchises/%d", franchise.ID)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var detail FranchiseDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &detail))

	assert.Equal(t, 1, detail.LibraryGames)
	assert.Equal(t, 2, detail.TotalGames)

	// The soft-deleted game should show as not in library
	var deleted *SeriesGameResponse
	for i := range detail.Games {
		if detail.Games[i].IGDBGameID == 200 {
			deleted = &detail.Games[i]
		}
	}
	require.NotNil(t, deleted)
	assert.False(t, deleted.InLibrary)
	assert.Nil(t, deleted.LocalGameID)
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
