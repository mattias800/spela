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

func TestGetCompanyByID_Success(t *testing.T) {
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
		assert.Equal(t, "/v4/companies", r.URL.Path)
		assert.Equal(t, "myid", r.Header.Get("Client-ID"))
		assert.Equal(t, "Bearer test-token", r.Header.Get("Authorization"))

		json.NewEncoder(w).Encode([]map[string]interface{}{
			{
				"id":          421,
				"name":        "Nintendo",
				"description": "Nintendo Co., Ltd. is a Japanese video game company.",
				"logo":        map[string]interface{}{"image_id": "cl_nintendo"},
				"country":     392,
				"start_date":  -2208988800, // 1889
				"websites": []map[string]interface{}{
					{"url": "https://www.nintendo.com", "category": 1},
					{"url": "https://en.wikipedia.org/wiki/Nintendo", "category": 3},
					{"url": "https://twitter.com/NintendoAmerica", "category": 5},
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

	detail, err := c.GetCompanyByID(421)
	require.NoError(t, err)
	require.NotNil(t, detail)

	assert.Equal(t, 421, detail.ID)
	assert.Equal(t, "Nintendo", detail.Name)
	assert.Equal(t, "Nintendo Co., Ltd. is a Japanese video game company.", detail.Description)
	assert.Equal(t, "cl_nintendo", detail.LogoImageID)
	assert.Equal(t, 392, detail.Country)
	assert.Equal(t, int64(-2208988800), detail.StartDate)
	assert.Equal(t, "https://www.nintendo.com", detail.WebsiteURL)
	assert.Equal(t, "https://en.wikipedia.org/wiki/Nintendo", detail.WikipediaURL)
}

func TestGetCompanyByID_NotFound(t *testing.T) {
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

	detail, err := c.GetCompanyByID(99999)
	require.NoError(t, err)
	assert.Nil(t, detail)
}

func TestGetCompanyByID_APIError(t *testing.T) {
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

	_, err := c.GetCompanyByID(421)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "500")
}

func TestGetCompanyByID_NoLogo(t *testing.T) {
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
				"id":      100,
				"name":    "Small Studio",
				"country": 840,
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

	detail, err := c.GetCompanyByID(100)
	require.NoError(t, err)
	require.NotNil(t, detail)

	assert.Equal(t, "Small Studio", detail.Name)
	assert.Empty(t, detail.LogoImageID)
	assert.Equal(t, 840, detail.Country)
	assert.Empty(t, detail.WebsiteURL)
}

func TestSearchCompanyByName_Success(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/v4/companies", r.URL.Path)

		json.NewEncoder(w).Encode([]map[string]interface{}{
			{
				"id":          999,
				"name":        "capcom co",
				"description": "Not the right one",
			},
			{
				"id":          12,
				"name":        "Capcom",
				"description": "A Japanese game developer and publisher.",
				"logo":        map[string]interface{}{"image_id": "cl_capcom"},
				"country":     392,
				"start_date":  488332800,
				"websites": []map[string]interface{}{
					{"url": "https://www.capcom.com", "category": 1},
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

	detail, err := c.SearchCompanyByName("Capcom")
	require.NoError(t, err)
	require.NotNil(t, detail)

	// Should pick the exact name match "Capcom" instead of "capcom co"
	assert.Equal(t, 12, detail.ID)
	assert.Equal(t, "Capcom", detail.Name)
	assert.Equal(t, "A Japanese game developer and publisher.", detail.Description)
	assert.Equal(t, "cl_capcom", detail.LogoImageID)
	assert.Equal(t, 392, detail.Country)
	assert.Equal(t, "https://www.capcom.com", detail.WebsiteURL)
}

func TestSearchCompanyByName_NotFound(t *testing.T) {
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

	detail, err := c.SearchCompanyByName("Nonexistent Studio XYZ")
	require.NoError(t, err)
	assert.Nil(t, detail)
}

func TestCompanyLogoURL(t *testing.T) {
	tests := []struct {
		imageID  string
		expected string
	}{
		{"cl_nintendo", "https://images.igdb.com/igdb/image/upload/t_logo_med/cl_nintendo.png"},
		{"cl_capcom", "https://images.igdb.com/igdb/image/upload/t_logo_med/cl_capcom.png"},
		{"", ""},
	}
	for _, tt := range tests {
		t.Run(tt.imageID, func(t *testing.T) {
			assert.Equal(t, tt.expected, CompanyLogoURL(tt.imageID))
		})
	}
}
