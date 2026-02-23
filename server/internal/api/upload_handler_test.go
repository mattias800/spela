package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// uploadTestEnv holds the test environment for upload handler tests.
type uploadTestEnv struct {
	db         *gorm.DB
	cfg        *Config
	router     *httptest.Server
	adminToken string
	tmpDir     string
}

func setupUploadTestEnv(t *testing.T) *uploadTestEnv {
	t.Helper()
	database, cfg := setupTestEnv(t)

	// Create admin user
	user := db.User{
		Username:     "uploadadmin",
		Email:        "uploadadmin@test.com",
		PasswordHash: "unused",
		Role:         "owner",
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, testJWTSecret)
	require.NoError(t, err)

	return &uploadTestEnv{
		db:         database,
		cfg:        cfg,
		adminToken: token,
		tmpDir:     cfg.GameDirs[0],
	}
}

func uploadFiles(t *testing.T, router http.Handler, token string, files map[string][]byte) *httptest.ResponseRecorder {
	t.Helper()
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)
	for name, content := range files {
		part, err := writer.CreateFormFile("files", name)
		require.NoError(t, err)
		_, err = part.Write(content)
		require.NoError(t, err)
	}
	require.NoError(t, writer.Close())

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads", body)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	return w
}

func TestUploadROMs_ValidExtension(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	files := map[string][]byte{
		"Super Mario Bros.nes": []byte("fake rom data"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	assert.Equal(t, http.StatusOK, w.Code)

	var results []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &results))
	require.Len(t, results, 1)
	assert.Equal(t, "Super Mario Bros.nes", results[0].FileName)
	assert.Equal(t, "pending_scrape", results[0].Status)
	assert.NotNil(t, results[0].ConsoleID)
	assert.Equal(t, "nes", *results[0].ConsoleID)

	// Verify file exists in staging
	stagingDir := filepath.Join(env.tmpDir, "staging")
	_, err := os.Stat(filepath.Join(stagingDir, "Super Mario Bros.nes"))
	assert.NoError(t, err)
}

func TestUploadROMs_InvalidExtension(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	files := map[string][]byte{
		"readme.txt": []byte("not a rom"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	assert.Equal(t, http.StatusOK, w.Code)

	var results []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &results))
	require.Len(t, results, 1)
	assert.Equal(t, "rejected", results[0].Status)

	// Verify no staged uploads in DB
	var count int64
	env.db.Model(&db.StagedUpload{}).Count(&count)
	assert.Equal(t, int64(0), count)
}

func TestUploadROMs_MultipleFiles(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	files := map[string][]byte{
		"Mario.nes":    []byte("nes rom"),
		"Zelda.sfc":    []byte("snes rom"),
		"readme.txt":   []byte("not a rom"),
		"Pokemon.gba":  []byte("gba rom"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	assert.Equal(t, http.StatusOK, w.Code)

	var results []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &results))
	assert.Len(t, results, 4)

	// Count accepted vs rejected
	accepted := 0
	rejected := 0
	for _, r := range results {
		if r.Status == "rejected" {
			rejected++
		} else {
			accepted++
		}
	}
	assert.Equal(t, 3, accepted)
	assert.Equal(t, 1, rejected)
}

func TestUploadROMs_AmbiguousExtension(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	files := map[string][]byte{
		"game.bin": []byte("could be genesis or psx"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	assert.Equal(t, http.StatusOK, w.Code)

	var results []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &results))
	require.Len(t, results, 1)
	assert.Equal(t, "pending_console", results[0].Status)
	assert.Nil(t, results[0].ConsoleID)
	assert.NotEmpty(t, results[0].PossibleConsoles)
}

func TestUploadROMs_UnambiguousConsoleDetection(t *testing.T) {
	tests := []struct {
		filename  string
		consoleID string
	}{
		{"game.nes", "nes"},
		{"game.sfc", "snes"},
		{"game.smc", "snes"},
		{"game.gb", "gb"},
		{"game.gbc", "gbc"},
		{"game.gba", "gba"},
		{"game.n64", "n64"},
		{"game.z64", "n64"},
		{"game.nds", "nds"},
		{"game.sms", "sms"},
		{"game.md", "gen"},
		{"game.gen", "gen"},
		{"game.pce", "pce"},
		{"game.a26", "a26"},
	}

	for _, tt := range tests {
		t.Run(tt.filename, func(t *testing.T) {
			env := setupUploadTestEnv(t)
			router := NewRouter(*env.cfg)

			files := map[string][]byte{
				tt.filename: []byte("rom data"),
			}
			w := uploadFiles(t, router, env.adminToken, files)
			assert.Equal(t, http.StatusOK, w.Code)

			var results []StagedUploadResponse
			require.NoError(t, json.Unmarshal(w.Body.Bytes(), &results))
			require.Len(t, results, 1)
			assert.Equal(t, "pending_scrape", results[0].Status)
			require.NotNil(t, results[0].ConsoleID)
			assert.Equal(t, tt.consoleID, *results[0].ConsoleID)
		})
	}
}

func TestSetConsole_AmbiguousUpload(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	// Upload ambiguous file
	files := map[string][]byte{
		"game.bin": []byte("bin data"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	require.Equal(t, http.StatusOK, w.Code)

	var uploads []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &uploads))
	require.Len(t, uploads, 1)
	uploadID := uploads[0].ID

	// Set console to Genesis
	body, _ := json.Marshal(map[string]string{"consoleId": "gen"})
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads/"+uploadID+"/console", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	assert.Equal(t, "pending_scrape", result.Status)
	require.NotNil(t, result.ConsoleID)
	assert.Equal(t, "gen", *result.ConsoleID)
}

func TestListUploads(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	// Upload some files
	files := map[string][]byte{
		"Mario.nes": []byte("nes rom"),
		"Zelda.sfc": []byte("snes rom"),
	}
	uploadFiles(t, router, env.adminToken, files)

	// List uploads
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/uploads", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var results []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &results))
	assert.Len(t, results, 2)
}

func TestScrapeUpload(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	files := map[string][]byte{
		"Mario.nes": []byte("nes rom"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	require.Equal(t, http.StatusOK, w.Code)

	var uploads []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &uploads))
	uploadID := uploads[0].ID

	// Trigger scrape
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads/"+uploadID+"/scrape", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	// Should be ready (or duplicate) after scrape, not pending_scrape
	assert.NotEqual(t, "pending_scrape", result.Status)
	assert.Equal(t, "Mario", result.Title)
}

func TestScrapeUpload_RequiresConsole(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	// Upload ambiguous file
	files := map[string][]byte{
		"game.bin": []byte("bin data"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	require.Equal(t, http.StatusOK, w.Code)

	var uploads []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &uploads))
	uploadID := uploads[0].ID

	// Try to scrape without setting console
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads/"+uploadID+"/scrape", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestAcceptUpload(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	files := map[string][]byte{
		"TestGame.nes": []byte("nes rom data here"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	require.Equal(t, http.StatusOK, w.Code)

	var uploads []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &uploads))
	uploadID := uploads[0].ID

	// Accept the upload
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads/"+uploadID+"/accept", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	// Verify game was created in DB
	var game db.Game
	err := env.db.Where("file_name = ?", "TestGame.nes").First(&game).Error
	require.NoError(t, err)
	assert.Equal(t, "TestGame", game.Title)

	// Verify file was moved to library
	var nesConsole db.Console
	env.db.Where("abbreviation = ?", "NES").First(&nesConsole)
	expectedAbsPath := filepath.Join(env.tmpDir, nesConsole.FolderName, "TestGame.nes")
	_, err = os.Stat(expectedAbsPath)
	assert.NoError(t, err, "ROM file should exist in library directory")

	// Verify game stores relative path
	assert.Equal(t, filepath.Join(nesConsole.FolderName, "TestGame.nes"), game.FilePath)

	// Verify staging record was deleted
	var count int64
	env.db.Model(&db.StagedUpload{}).Count(&count)
	assert.Equal(t, int64(0), count)
}

func TestAcceptUpload_RequiresConsole(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	// Upload ambiguous file
	files := map[string][]byte{
		"game.bin": []byte("bin data"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	require.Equal(t, http.StatusOK, w.Code)

	var uploads []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &uploads))
	uploadID := uploads[0].ID

	// Try to accept without setting console
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads/"+uploadID+"/accept", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestRejectUpload(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	files := map[string][]byte{
		"BadGame.nes": []byte("rom data"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	require.Equal(t, http.StatusOK, w.Code)

	var uploads []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &uploads))
	uploadID := uploads[0].ID

	// Verify file exists in staging before rejection
	stagingDir := filepath.Join(env.tmpDir, "staging")
	_, err := os.Stat(filepath.Join(stagingDir, "BadGame.nes"))
	require.NoError(t, err)

	// Reject
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads/"+uploadID+"/reject", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	// Verify file was deleted
	_, err = os.Stat(filepath.Join(stagingDir, "BadGame.nes"))
	assert.True(t, os.IsNotExist(err), "ROM file should be deleted from staging")

	// Verify DB record was deleted
	var count int64
	env.db.Model(&db.StagedUpload{}).Count(&count)
	assert.Equal(t, int64(0), count)
}

func TestDuplicateDetection(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	// Create an existing game in the library
	var nesConsole db.Console
	env.db.Where("abbreviation = ?", "NES").First(&nesConsole)
	existingGame := db.Game{
		ConsoleID: nesConsole.ID,
		Title:     "Mario",
		FileName:  "Mario.nes",
		FilePath:  "/some/path/Mario.nes",
		FileSize:  100,
	}
	require.NoError(t, env.db.Create(&existingGame).Error)

	// Upload a file with the same name
	files := map[string][]byte{
		"Mario.nes": []byte("nes rom data"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	require.Equal(t, http.StatusOK, w.Code)

	var uploads []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &uploads))
	uploadID := uploads[0].ID

	// Trigger scrape which does duplicate detection
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads/"+uploadID+"/scrape", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	assert.Equal(t, "duplicate", result.Status)
	assert.NotNil(t, result.DuplicateOfGameID)
}

func TestAcceptAllUploads(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	// Upload multiple files
	files := map[string][]byte{
		"Game1.nes": []byte("nes rom 1"),
		"Game2.sfc": []byte("snes rom 2"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	require.Equal(t, http.StatusOK, w.Code)

	// Scrape all first so they're in "ready" status
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads/scrape", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Accept all
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/admin/uploads/accept-all", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]int
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 2, resp["accepted"])

	// Verify games were created
	var gameCount int64
	env.db.Model(&db.Game{}).Count(&gameCount)
	assert.Equal(t, int64(2), gameCount)

	// Verify staging is empty
	var stagingCount int64
	env.db.Model(&db.StagedUpload{}).Count(&stagingCount)
	assert.Equal(t, int64(0), stagingCount)
}

func TestRejectAllUploads(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	files := map[string][]byte{
		"Game1.nes": []byte("nes rom 1"),
		"Game2.sfc": []byte("snes rom 2"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	require.Equal(t, http.StatusOK, w.Code)

	// Reject all
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads/reject-all", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]int
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 2, resp["rejected"])

	// Verify staging is empty
	var count int64
	env.db.Model(&db.StagedUpload{}).Count(&count)
	assert.Equal(t, int64(0), count)
}

func TestClearStaging(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	files := map[string][]byte{
		"Game1.nes": []byte("nes rom 1"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	require.Equal(t, http.StatusOK, w.Code)

	// Clear staging
	w = httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/admin/uploads", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]int
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 1, resp["cleared"])

	// Verify staging is empty
	var count int64
	env.db.Model(&db.StagedUpload{}).Count(&count)
	assert.Equal(t, int64(0), count)
}

func TestUploadROMs_NonAdmin_Forbidden(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	// Create regular user
	regularUser := db.User{
		Username:     "regularuser",
		Email:        "regular@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, env.db.Create(&regularUser).Error)
	userToken, err := auth.GenerateAccessToken(regularUser.ID, regularUser.Username, regularUser.Role, testJWTSecret)
	require.NoError(t, err)

	files := map[string][]byte{
		"Mario.nes": []byte("nes rom"),
	}
	w := uploadFiles(t, router, userToken, files)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestUploadROMs_PathTraversal(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	files := map[string][]byte{
		"../../../evil.nes": []byte("evil content"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	assert.Equal(t, http.StatusOK, w.Code)

	var results []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &results))
	require.Len(t, results, 1)

	// Should be sanitized to just "evil.nes"
	assert.Equal(t, "evil.nes", results[0].FileName)
	assert.Equal(t, "pending_scrape", results[0].Status)
}

func TestScrapeAllUploads(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	files := map[string][]byte{
		"Game1.nes": []byte("nes rom 1"),
		"Game2.gba": []byte("gba rom 2"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	require.Equal(t, http.StatusOK, w.Code)

	// Scrape all
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads/scrape", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var results []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &results))
	assert.Len(t, results, 2)

	for _, r := range results {
		assert.Equal(t, "ready", r.Status)
		assert.NotEmpty(t, r.Title)
	}
}

func TestAcceptUpload_CreatesGameInCorrectDirectory(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	// Upload an SNES ROM
	files := map[string][]byte{
		"Chrono Trigger.sfc": []byte("snes rom data"),
	}
	w := uploadFiles(t, router, env.adminToken, files)
	require.Equal(t, http.StatusOK, w.Code)

	var uploads []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &uploads))
	uploadID := uploads[0].ID

	// Accept
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads/"+uploadID+"/accept", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Verify file is in the SNES console directory
	var snesConsole db.Console
	env.db.Where("abbreviation = ?", "SNES").First(&snesConsole)
	expectedDir := filepath.Join(env.tmpDir, snesConsole.FolderName)
	expectedAbsPath := filepath.Join(expectedDir, "Chrono Trigger.sfc")
	_, err := os.Stat(expectedAbsPath)
	assert.NoError(t, err, "ROM should be in the SNES folder")

	// Verify game record stores relative path
	var game db.Game
	require.NoError(t, env.db.Where("file_name = ?", "Chrono Trigger.sfc").First(&game).Error)
	assert.Equal(t, snesConsole.ID, game.ConsoleID)
	assert.Equal(t, filepath.Join(snesConsole.FolderName, "Chrono Trigger.sfc"), game.FilePath)
}

func TestUploadROMs_DuplicateFilename(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	// Upload same file twice
	files1 := map[string][]byte{"Mario.nes": []byte("rom 1")}
	w := uploadFiles(t, router, env.adminToken, files1)
	require.Equal(t, http.StatusOK, w.Code)

	files2 := map[string][]byte{"Mario.nes": []byte("rom 2")}
	w = uploadFiles(t, router, env.adminToken, files2)
	require.Equal(t, http.StatusOK, w.Code)

	var results []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &results))
	require.Len(t, results, 1)
	// Should have been given a unique name
	assert.NotEqual(t, "Mario.nes", results[0].FileName, "duplicate should get unique filename")
	assert.Contains(t, results[0].FileName, "Mario")

	// Both should be in DB
	var count int64
	env.db.Model(&db.StagedUpload{}).Count(&count)
	assert.Equal(t, int64(2), count)
}

func TestUploadEndpoint_NotFound(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	// Try to accept non-existent upload
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads/99999/accept", nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestSetConsole_UnknownConsole(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	// Upload ambiguous file
	files := map[string][]byte{"game.bin": []byte("data")}
	w := uploadFiles(t, router, env.adminToken, files)
	require.Equal(t, http.StatusOK, w.Code)

	var uploads []StagedUploadResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &uploads))
	uploadID := uploads[0].ID

	// Set console to non-existent
	body, _ := json.Marshal(map[string]string{"consoleId": "FAKE"})
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", fmt.Sprintf("/api/admin/uploads/%s/console", uploadID), bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestUploadROMs_NoFiles(t *testing.T) {
	env := setupUploadTestEnv(t)
	router := NewRouter(*env.cfg)

	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)
	require.NoError(t, writer.Close())

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/uploads", body)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}
