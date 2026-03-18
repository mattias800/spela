package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestGetDeveloperDetail_WithCompanyInfo(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create a game with a developer
	game := createExploreGame(t, env.database, "NES", "Super Mario Bros", 90.0)
	env.database.Model(&game).Update("developer", "Nintendo")

	// Create a Company record in the DB
	company := db.Company{
		IGDBCompanyID: 421,
		Name:          "Nintendo",
		Description:   "Nintendo Co., Ltd. is a Japanese video game company.",
		LogoURL:       "https://images.igdb.com/igdb/image/upload/t_logo_med/cl_nintendo.png",
		Country:       392,
		FoundedYear:   1889,
		WebsiteURL:    "https://www.nintendo.com",
		WikipediaURL:  "https://en.wikipedia.org/wiki/Nintendo",
	}
	require.NoError(t, env.database.Create(&company).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/Nintendo", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "Nintendo", resp.Name)
	assert.Equal(t, 1, resp.GameCount)

	// Verify company info is included
	require.NotNil(t, resp.CompanyInfo)
	assert.Equal(t, "https://images.igdb.com/igdb/image/upload/t_logo_med/cl_nintendo.png", resp.CompanyInfo.LogoURL)
	assert.Equal(t, "Nintendo Co., Ltd. is a Japanese video game company.", resp.CompanyInfo.Description)
	assert.Equal(t, 1889, resp.CompanyInfo.FoundedYear)
	assert.Equal(t, "Japan", resp.CompanyInfo.Country)
	assert.Equal(t, "https://www.nintendo.com", resp.CompanyInfo.WebsiteURL)
	assert.Equal(t, "https://en.wikipedia.org/wiki/Nintendo", resp.CompanyInfo.WikipediaURL)
}

func TestGetDeveloperDetail_WithoutCompanyInfo(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create a game with a developer but no Company record
	game := createExploreGame(t, env.database, "NES", "Some Game", 85.0)
	env.database.Model(&game).Update("developer", "UnknownStudio")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/UnknownStudio", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "UnknownStudio", resp.Name)
	// CompanyInfo should be nil when no Company record exists
	assert.Nil(t, resp.CompanyInfo)
}

func TestGetPublisherDetail_WithCompanyInfo(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create a game with a publisher
	game := createExploreGame(t, env.database, "SNES", "Mega Man X", 92.0)
	env.database.Model(&game).Update("publisher", "Capcom")

	// Create a Company record
	company := db.Company{
		IGDBCompanyID: 12,
		Name:          "Capcom",
		Description:   "A Japanese game developer and publisher.",
		LogoURL:       "https://images.igdb.com/igdb/image/upload/t_logo_med/cl_capcom.png",
		Country:       392,
		FoundedYear:   1983,
		WebsiteURL:    "https://www.capcom.com",
	}
	require.NoError(t, env.database.Create(&company).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/publishers/Capcom", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PublisherDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "Capcom", resp.Name)

	require.NotNil(t, resp.CompanyInfo)
	assert.Equal(t, "A Japanese game developer and publisher.", resp.CompanyInfo.Description)
	assert.Equal(t, 1983, resp.CompanyInfo.FoundedYear)
	assert.Equal(t, "Japan", resp.CompanyInfo.Country)
}

func TestGetDeveloperDetail_CaseInsensitiveCompanyLookup(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create a game with lowercase developer name
	game := createExploreGame(t, env.database, "NES", "Test Game", 80.0)
	env.database.Model(&game).Update("developer", "nintendo")

	// Company record with proper casing
	company := db.Company{
		IGDBCompanyID: 421,
		Name:          "Nintendo",
		Description:   "The house of Mario.",
		FoundedYear:   1889,
		Country:       392,
	}
	require.NoError(t, env.database.Create(&company).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/nintendo", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Company lookup should work despite case mismatch
	require.NotNil(t, resp.CompanyInfo)
	assert.Equal(t, "The house of Mario.", resp.CompanyInfo.Description)
	assert.Equal(t, "Japan", resp.CompanyInfo.Country)
}

func TestCompanyToInfo_EmptyCompany(t *testing.T) {
	// A company with no useful data should return nil
	company := &db.Company{
		IGDBCompanyID: 1,
		Name:          "Empty Co",
	}
	info := companyToInfo(company)
	assert.Nil(t, info)
}

func TestCompanyToInfo_NilCompany(t *testing.T) {
	info := companyToInfo(nil)
	assert.Nil(t, info)
}

func TestCompanyToInfo_PartialData(t *testing.T) {
	company := &db.Company{
		IGDBCompanyID: 1,
		Name:          "Partial Co",
		Description:   "Some description",
		Country:       840,
	}
	info := companyToInfo(company)
	require.NotNil(t, info)
	assert.Equal(t, "Some description", info.Description)
	assert.Equal(t, "United States", info.Country)
	assert.Empty(t, info.LogoURL)
	assert.Empty(t, info.WebsiteURL)
	assert.Equal(t, 0, info.FoundedYear)
}

func TestCompanyToInfo_UnknownCountryCode(t *testing.T) {
	company := &db.Company{
		IGDBCompanyID: 1,
		Name:          "Mystery Co",
		Description:   "From an unknown land",
		Country:       999, // unknown code
	}
	info := companyToInfo(company)
	require.NotNil(t, info)
	assert.Equal(t, "From an unknown land", info.Description)
	assert.Empty(t, info.Country) // unknown code returns ""
}

// setupExploreCompanyTestEnv creates a test env with a scraper that has no IGDB client
// (which is the typical test scenario).
func setupExploreCompanyTestEnv(t *testing.T) *exploreTestEnv {
	t.Helper()
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)
	return &exploreTestEnv{
		database: database,
		router:   router,
		token:    token,
	}
}
