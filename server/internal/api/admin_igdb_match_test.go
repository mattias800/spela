package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

const igdbMatchTestSecret = "igdb-match-test-secret"

func setupIGDBMatchTestEnv(t *testing.T, tokenServerURL, igdbServerURL string) (*gorm.DB, *gin.Engine, *scraper.Scraper) {
	t.Helper()

	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(
		&db.User{}, &db.Console{}, &db.Game{}, &db.GameScreenshot{},
		&db.GameDisc{}, &db.Favorite{}, &db.PlayHistory{}, &db.ServerSetting{},
		&db.GameRating{}, &db.PlayLaterItem{}, &db.GameReleaseDate{},
		&db.GameVideo{}, &db.GameLanguageSupport{}, &db.GameAgeRating{},
	))

	if tokenServerURL != "" {
		origURL := igdb.TwitchTokenURLForTest()
		igdb.SetTwitchTokenURLForTest(tokenServerURL)
		t.Cleanup(func() { igdb.SetTwitchTokenURLForTest(origURL) })
	}
	if igdbServerURL != "" {
		origBase := igdb.IGDBAPIBaseForTest()
		igdb.SetIGDBAPIBaseForTest(igdbServerURL + "/v4")
		t.Cleanup(func() { igdb.SetIGDBAPIBaseForTest(origBase) })
	}

	tmpDir := t.TempDir()
	store, err := storage.NewStorage(tmpDir+"/saves", tmpDir+"/cores", tmpDir+"/images", tmpDir+"/bios")
	require.NoError(t, err)

	hub := ws.NewHub(nil)
	go hub.Run()

	s := scraper.NewScraper(database, store, t.TempDir(), []string{tmpDir})
	if tokenServerURL != "" && igdbServerURL != "" {
		igdbClient := igdb.NewClient("test-id", "test-secret")
		igdbClient.HTTPClient = &http.Client{Timeout: 5 * time.Second}
		s.IGDBClient = igdbClient
	}

	adminHandler := &AdminHandler{DB: database, Scraper: s, Hub: hub, Storage: store}

	gin.SetMode(gin.TestMode)
	router := gin.New()
	api := router.Group("/api")
	api.Use(AuthMiddleware(igdbMatchTestSecret, database))
	{
		admin := api.Group("/admin")
		admin.Use(AdminMiddleware())
		{
			admin.GET("/games/:id/igdb-search", adminHandler.SearchIGDB)
			admin.POST("/games/:id/igdb-match", adminHandler.ApplyIGDBMatch)
		}
	}

	return database, router, s
}

func createIGDBMatchTestUser(t *testing.T, database *gorm.DB) string {
	t.Helper()
	user := db.User{
		Username:     "match-admin",
		Email:        "match-admin@test.com",
		PasswordHash: "unused",
		Role:         "admin",
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, igdbMatchTestSecret)
	require.NoError(t, err)
	return token
}

func TestSearchIGDB_Success(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]igdb.Game{
			{
				ID:      999,
				Name:    "Disney's Aladdin",
				Summary: "A platformer based on the Disney film",
				Cover:   &igdb.Image{ID: 1, ImageID: "co5555"},
				InvolvedCompanies: []igdb.InvolvedCompany{
					{Company: igdb.Company{ID: 1, Name: "Capcom"}, Developer: true},
				},
				FirstReleaseDate: 753926400,
			},
			{
				ID:   888,
				Name: "Aladdin 2000",
			},
		})
	}))
	defer igdbServer.Close()

	database, router, _ := setupIGDBMatchTestEnv(t, tokenServer.URL, igdbServer.URL)
	token := createIGDBMatchTestUser(t, database)

	console := db.Console{Abbreviation: "SNES", Name: "Super Nintendo"}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{ConsoleID: console.ID, Title: "Aladdin", FileName: "Aladdin.sfc"}
	require.NoError(t, database.Create(&game).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/igdb-search?q=Aladdin", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var results []IGDBSearchResult
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &results))
	require.Len(t, results, 2)

	assert.Equal(t, 999, results[0].IGDBID)
	assert.Equal(t, "Disney's Aladdin", results[0].Name)
	assert.NotEmpty(t, results[0].CoverURL)
	assert.Equal(t, 1993, results[0].ReleaseYear)
	assert.Equal(t, "Capcom", results[0].Developer)
	assert.Equal(t, "A platformer based on the Disney film", results[0].Summary)

	assert.Equal(t, 888, results[1].IGDBID)
	assert.Equal(t, "Aladdin 2000", results[1].Name)
	assert.Empty(t, results[1].CoverURL)
	assert.Equal(t, 0, results[1].ReleaseYear)
}

func TestSearchIGDB_MissingQuery(t *testing.T) {
	database, router, _ := setupIGDBMatchTestEnv(t, "", "")
	token := createIGDBMatchTestUser(t, database)

	console := db.Console{Abbreviation: "SNES", Name: "Super Nintendo"}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{ConsoleID: console.ID, Title: "Test", FileName: "Test.sfc"}
	require.NoError(t, database.Create(&game).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/igdb-search", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestSearchIGDB_GameNotFound(t *testing.T) {
	database, router, _ := setupIGDBMatchTestEnv(t, "", "")
	token := createIGDBMatchTestUser(t, database)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/games/99999/igdb-search?q=test", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestSearchIGDB_IGDBNotConfigured(t *testing.T) {
	database, router, _ := setupIGDBMatchTestEnv(t, "", "")
	token := createIGDBMatchTestUser(t, database)

	console := db.Console{Abbreviation: "SNES", Name: "Super Nintendo"}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{ConsoleID: console.ID, Title: "Test", FileName: "Test.sfc"}
	require.NoError(t, database.Create(&game).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/igdb-search?q=test", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusServiceUnavailable, w.Code)
}

func TestApplyIGDBMatch_Success(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]igdb.Game{
			{
				ID:      999,
				Name:    "Disney's Aladdin",
				Summary: "The correct Aladdin game",
				Genres:  []igdb.Genre{{ID: 8, Name: "Platform"}},
				InvolvedCompanies: []igdb.InvolvedCompany{
					{Company: igdb.Company{ID: 1, Name: "Capcom"}, Developer: true},
				},
				FirstReleaseDate: 753926400,
				AggregatedRating: 82.0,
				GameModes: []igdb.GameMode{
					{ID: 1, Name: "Single player"},
				},
			},
		})
	}))
	defer igdbServer.Close()

	database, router, _ := setupIGDBMatchTestEnv(t, tokenServer.URL, igdbServer.URL)
	token := createIGDBMatchTestUser(t, database)

	console := db.Console{Abbreviation: "SNES", Name: "Super Nintendo"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID:      console.ID,
		Title:          "Aladdin 2000",
		FileName:       "Aladdin.sfc",
		ScraperID:      "igdb:555",
		ScrapeAttempts: 1,
	}
	require.NoError(t, database.Create(&game).Error)

	body, _ := json.Marshal(map[string]int{"igdbId": 999})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/igdb-match", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "Disney's Aladdin", resp["title"])
	assert.Equal(t, "The correct Aladdin game", resp["description"])
	assert.Equal(t, "igdb:999", resp["scraperId"])
}

func TestApplyIGDBMatch_GameNotFound(t *testing.T) {
	database, router, _ := setupIGDBMatchTestEnv(t, "", "")
	token := createIGDBMatchTestUser(t, database)

	body, _ := json.Marshal(map[string]int{"igdbId": 999})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/games/99999/igdb-match", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestApplyIGDBMatch_MissingIGDBID(t *testing.T) {
	database, router, _ := setupIGDBMatchTestEnv(t, "", "")
	token := createIGDBMatchTestUser(t, database)

	console := db.Console{Abbreviation: "SNES", Name: "Super Nintendo"}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{ConsoleID: console.ID, Title: "Test", FileName: "Test.sfc"}
	require.NoError(t, database.Create(&game).Error)

	body, _ := json.Marshal(map[string]string{})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/igdb-match", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestApplyIGDBMatch_IGDBNotFound(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Return empty results — game not found on IGDB
		json.NewEncoder(w).Encode([]igdb.Game{})
	}))
	defer igdbServer.Close()

	database, router, _ := setupIGDBMatchTestEnv(t, tokenServer.URL, igdbServer.URL)
	token := createIGDBMatchTestUser(t, database)

	console := db.Console{Abbreviation: "SNES", Name: "Super Nintendo"}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{ConsoleID: console.ID, Title: "Test", FileName: "Test.sfc"}
	require.NoError(t, database.Create(&game).Error)

	body, _ := json.Marshal(map[string]int{"igdbId": 99999})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/games/"+strconv.Itoa(int(game.ID))+"/igdb-match", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusInternalServerError, w.Code)
}
