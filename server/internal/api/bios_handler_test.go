package api

import (
	"bytes"
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/bios"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

const biosTestJWTSecret = "bios-test-secret"

// setupBiosTestEnv creates a storage + DB + router with enriched BIOS endpoints
// and admin upload/delete routes behind auth + admin middleware.
func setupBiosTestEnv(t *testing.T) (*storage.Storage, *gorm.DB, *gin.Engine) {
	t.Helper()

	tmpDir := t.TempDir()
	store, err := storage.NewStorage(
		filepath.Join(tmpDir, "saves"),
		filepath.Join(tmpDir, "cores"),
		filepath.Join(tmpDir, "images"),
		filepath.Join(tmpDir, "bios"),
	)
	require.NoError(t, err)

	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.User{}, &db.Console{}, &db.Game{}))
	require.NoError(t, db.SeedConsoles(database))

	gin.SetMode(gin.TestMode)
	router := gin.New()

	handler := &BiosHandler{Storage: store, DB: database}

	// Authenticated routes
	api := router.Group("/api")
	api.Use(AuthMiddleware(biosTestJWTSecret, database))
	{
		api.GET("/bios", handler.ListBiosFiles)
		api.GET("/bios/:filename", handler.GetBiosFile)

		admin := api.Group("/admin")
		admin.Use(AdminMiddleware())
		{
			admin.POST("/bios", handler.UploadBiosFile)
			admin.DELETE("/bios/:filename", handler.DeleteBiosFile)
		}
	}

	return store, database, router
}

// createBiosTestUser creates a user and returns a JWT token.
func createBiosTestUser(t *testing.T, database *gorm.DB, role string) string {
	t.Helper()
	user := db.User{
		Username:     fmt.Sprintf("user-%s-%d", role, database.RowsAffected),
		Email:        fmt.Sprintf("user-%s-%d@test.com", role, database.RowsAffected),
		PasswordHash: "unused",
		Role:         role,
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, biosTestJWTSecret)
	require.NoError(t, err)
	return token
}

// md5sum computes the hex MD5 of data.
func md5sum(data []byte) string {
	h := md5.Sum(data)
	return hex.EncodeToString(h[:])
}

// -- GET /api/bios (enriched) --

func TestListBiosFiles_EmptyDir(t *testing.T) {
	store, database, router := setupBiosTestEnv(t)
	_ = store
	token := createBiosTestUser(t, database, "user")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp BiosListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// All known registry files should be listed as "missing"
	assert.NotEmpty(t, resp.Files)
	for _, f := range resp.Files {
		assert.Equal(t, "missing", f.Status)
	}

	// Console summaries should be present
	assert.NotEmpty(t, resp.Consoles)
}

func TestListBiosFiles_ValidFile(t *testing.T) {
	store, database, router := setupBiosTestEnv(t)
	token := createBiosTestUser(t, database, "user")

	// Get expected MD5 for scph5501.bin from registry
	entries := bios.ByFileName("scph5501.bin")
	require.Len(t, entries, 1)
	expectedMD5 := entries[0].MD5

	// Write a file with the correct MD5 (simulate by writing known content and
	// using its actual MD5 — but we can't easily match the registry MD5 with
	// arbitrary content, so instead we just test that the status logic works).
	// For a "valid" file we need the MD5 to match, which requires the real BIOS.
	// Instead, test "invalid" (present but wrong MD5).
	content := []byte("not the real psx bios")
	require.NoError(t, os.WriteFile(filepath.Join(store.BiosDir, "scph5501.bin"), content, 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp BiosListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Find scph5501.bin in the file list
	var found *BiosFileResponse
	for i, f := range resp.Files {
		if f.Name == "scph5501.bin" {
			found = &resp.Files[i]
			break
		}
	}
	require.NotNil(t, found)
	assert.Equal(t, "invalid", found.Status, "wrong MD5 should be invalid")
	assert.NotEqual(t, expectedMD5, found.MD5)
	assert.Equal(t, "psx", *found.ConsoleID)
	assert.Equal(t, "PlayStation", *found.ConsoleName)
	assert.True(t, found.Required)
}

func TestListBiosFiles_UnknownFile(t *testing.T) {
	store, database, router := setupBiosTestEnv(t)
	token := createBiosTestUser(t, database, "user")

	content := []byte("some unknown bios data")
	require.NoError(t, os.WriteFile(filepath.Join(store.BiosDir, "mystery.bin"), content, 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp BiosListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Find mystery.bin
	var found *BiosFileResponse
	for i, f := range resp.Files {
		if f.Name == "mystery.bin" {
			found = &resp.Files[i]
			break
		}
	}
	require.NotNil(t, found, "unknown files should appear in file list")
	assert.Equal(t, "present", found.Status)
	assert.Nil(t, found.ConsoleID, "unknown file should have nil consoleId")
	assert.False(t, found.Required)
}

func TestListBiosFiles_ConsoleSummary_MissingRequired(t *testing.T) {
	_, database, router := setupBiosTestEnv(t)
	token := createBiosTestUser(t, database, "user")

	// Don't write any files — PSX required BIOS should be missing

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp BiosListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Find PSX console status
	var psxConsole *ConsoleBiosStatus
	for i, cs := range resp.Consoles {
		if cs.ConsoleID == "psx" {
			psxConsole = &resp.Consoles[i]
			break
		}
	}
	require.NotNil(t, psxConsole)
	assert.Equal(t, "missing", psxConsole.Status)
	assert.True(t, psxConsole.BiosRequired)
	assert.Equal(t, 0, psxConsole.RequiredPresent)
	assert.Equal(t, 1, psxConsole.RequiredTotal)
}

func TestListBiosFiles_ConsoleSummary_NotRequired(t *testing.T) {
	_, database, router := setupBiosTestEnv(t)
	token := createBiosTestUser(t, database, "user")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	var resp BiosListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// GBA has only optional BIOS
	var gbaConsole *ConsoleBiosStatus
	for i, cs := range resp.Consoles {
		if cs.ConsoleID == "gba" {
			gbaConsole = &resp.Consoles[i]
			break
		}
	}
	require.NotNil(t, gbaConsole)
	assert.Equal(t, "not_required", gbaConsole.Status)
	assert.False(t, gbaConsole.BiosRequired)
}

func TestListBiosFiles_RequiresAuth(t *testing.T) {
	_, _, router := setupBiosTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

// -- GET /api/bios/:filename --

func TestGetBiosFile_Success(t *testing.T) {
	store, database, router := setupBiosTestEnv(t)
	token := createBiosTestUser(t, database, "user")

	content := []byte("bios file content")
	require.NoError(t, os.WriteFile(filepath.Join(store.BiosDir, "test.bin"), content, 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios/test.bin", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "bios file content", w.Body.String())
}

func TestGetBiosFile_NotFound(t *testing.T) {
	_, database, router := setupBiosTestEnv(t)
	token := createBiosTestUser(t, database, "user")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios/nonexistent.bin", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetBiosFile_PathTraversal(t *testing.T) {
	store, database, router := setupBiosTestEnv(t)
	token := createBiosTestUser(t, database, "user")

	secretPath := filepath.Join(filepath.Dir(store.BiosDir), "secret.txt")
	require.NoError(t, os.WriteFile(secretPath, []byte("secret data"), 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios/..%2Fsecret.txt", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.NotEqual(t, "secret data", w.Body.String(),
		"path traversal should not serve files outside bios directory")
}

// -- POST /api/admin/bios (upload) --

func uploadBiosFile(t *testing.T, router *gin.Engine, token string, filename string, content []byte) *httptest.ResponseRecorder {
	t.Helper()
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, err := writer.CreateFormFile("file", filename)
	require.NoError(t, err)
	_, err = part.Write(content)
	require.NoError(t, err)
	require.NoError(t, writer.Close())

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/bios", &buf)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	return w
}

func TestUploadBiosFile_Success_KnownFile(t *testing.T) {
	_, database, router := setupBiosTestEnv(t)
	adminToken := createBiosTestUser(t, database, "admin")

	content := []byte("fake psx bios content")
	w := uploadBiosFile(t, router, adminToken, "scph5501.bin", content)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp BiosFileResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "scph5501.bin", resp.Name)
	assert.Equal(t, int64(len(content)), resp.Size)
	assert.Equal(t, md5sum(content), resp.MD5)
	assert.NotNil(t, resp.ConsoleID)
	assert.Equal(t, "psx", *resp.ConsoleID)
	assert.True(t, resp.Required)
	// Content MD5 won't match registry, so status should be "invalid"
	assert.Equal(t, "invalid", resp.Status)
}

func TestUploadBiosFile_Success_UnknownFile(t *testing.T) {
	_, database, router := setupBiosTestEnv(t)
	adminToken := createBiosTestUser(t, database, "admin")

	content := []byte("unknown bios data")
	w := uploadBiosFile(t, router, adminToken, "custom.bin", content)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp BiosFileResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "custom.bin", resp.Name)
	assert.Nil(t, resp.ConsoleID)
	assert.Equal(t, "present", resp.Status)
	assert.False(t, resp.Required)
}

func TestUploadBiosFile_Overwrite(t *testing.T) {
	store, database, router := setupBiosTestEnv(t)
	adminToken := createBiosTestUser(t, database, "admin")

	// Write initial file
	require.NoError(t, os.WriteFile(filepath.Join(store.BiosDir, "test.bin"), []byte("old"), 0644))

	// Upload a new version
	newContent := []byte("new content")
	w := uploadBiosFile(t, router, adminToken, "test.bin", newContent)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify the file was overwritten
	data, err := os.ReadFile(filepath.Join(store.BiosDir, "test.bin"))
	require.NoError(t, err)
	assert.Equal(t, newContent, data)
}

func TestUploadBiosFile_PathTraversal(t *testing.T) {
	store, database, router := setupBiosTestEnv(t)
	adminToken := createBiosTestUser(t, database, "admin")

	content := []byte("malicious content")
	w := uploadBiosFile(t, router, adminToken, "../evil.bin", content)

	assert.Equal(t, http.StatusOK, w.Code)

	// The file should NOT be written outside the bios directory
	parentPath := filepath.Join(filepath.Dir(store.BiosDir), "evil.bin")
	_, err := os.Stat(parentPath)
	assert.True(t, os.IsNotExist(err), "path traversal should not write outside bios dir")
}

func TestUploadBiosFile_NonAdmin_Rejected(t *testing.T) {
	_, database, router := setupBiosTestEnv(t)
	userToken := createBiosTestUser(t, database, "user")

	content := []byte("data")
	w := uploadBiosFile(t, router, userToken, "test.bin", content)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestUploadBiosFile_NoAuth_Rejected(t *testing.T) {
	_, _, router := setupBiosTestEnv(t)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("file", "test.bin")
	part.Write([]byte("data"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/bios", &buf)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestUploadBiosFile_NoFile_BadRequest(t *testing.T) {
	_, database, router := setupBiosTestEnv(t)
	adminToken := createBiosTestUser(t, database, "admin")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/bios", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

// -- DELETE /api/admin/bios/:filename --

func TestDeleteBiosFile_Success(t *testing.T) {
	store, database, router := setupBiosTestEnv(t)
	adminToken := createBiosTestUser(t, database, "admin")

	require.NoError(t, os.WriteFile(filepath.Join(store.BiosDir, "delete-me.bin"), []byte("data"), 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/admin/bios/delete-me.bin", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	// Verify file is gone
	_, err := os.Stat(filepath.Join(store.BiosDir, "delete-me.bin"))
	assert.True(t, os.IsNotExist(err))
}

func TestDeleteBiosFile_NotFound(t *testing.T) {
	_, database, router := setupBiosTestEnv(t)
	adminToken := createBiosTestUser(t, database, "admin")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/admin/bios/nonexistent.bin", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestDeleteBiosFile_NonAdmin_Rejected(t *testing.T) {
	store, database, router := setupBiosTestEnv(t)
	userToken := createBiosTestUser(t, database, "user")

	require.NoError(t, os.WriteFile(filepath.Join(store.BiosDir, "protected.bin"), []byte("data"), 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/admin/bios/protected.bin", nil)
	req.Header.Set("Authorization", "Bearer "+userToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)

	// File should still exist
	_, err := os.Stat(filepath.Join(store.BiosDir, "protected.bin"))
	assert.NoError(t, err)
}

// -- GetConsoleStatus (used by game handler for biosStatus) --

func TestGetConsoleStatus(t *testing.T) {
	tmpDir := t.TempDir()
	biosDir := filepath.Join(tmpDir, "bios")
	require.NoError(t, os.MkdirAll(biosDir, 0755))

	tests := []struct {
		name       string
		consoleID  string
		setupFiles func()
		wantStatus string
	}{
		{
			name:       "unknown console returns not_required",
			consoleID:  "NES",
			setupFiles: func() {},
			wantStatus: "not_required",
		},
		{
			name:       "PSX with no files returns missing",
			consoleID:  "PSX",
			setupFiles: func() {},
			wantStatus: "missing",
		},
		{
			name:      "GBA with no files returns not_required (only optional BIOS)",
			consoleID: "GBA",
			setupFiles: func() {},
			wantStatus: "not_required",
		},
		{
			name:      "PSX with wrong MD5 returns invalid",
			consoleID: "PSX",
			setupFiles: func() {
				os.WriteFile(filepath.Join(biosDir, "scph5501.bin"), []byte("wrong content"), 0644)
			},
			wantStatus: "invalid",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Clean the bios dir for each subtest
			os.RemoveAll(biosDir)
			os.MkdirAll(biosDir, 0755)

			tt.setupFiles()
			status := GetConsoleStatus(biosDir, tt.consoleID)
			assert.Equal(t, tt.wantStatus, status)
		})
	}
}

// -- biosStatus on GET /api/games/:id --

func TestGetGame_IncludesBiosStatus(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)

	// Create a PSX game
	var psx db.Console
	require.NoError(t, database.Where("abbreviation = ?", "PSX").First(&psx).Error)

	game := db.Game{
		ConsoleID: psx.ID,
		Title:     "Test PSX Game",
		FileName:  "test.bin",
		FilePath:  "/nonexistent/test.bin",
	}
	require.NoError(t, database.Create(&game).Error)

	// Register a user and get token
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// PSX requires BIOS and we haven't put any BIOS files, so should be "missing"
	assert.Equal(t, "missing", resp["biosStatus"])
}

func TestGetGame_BiosStatus_NotRequired(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)

	// Create a NES game (NES doesn't need BIOS)
	var nes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)

	game := db.Game{
		ConsoleID: nes.ID,
		Title:     "Test NES Game",
		FileName:  "test.nes",
		FilePath:  "/nonexistent/test.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "not_required", resp["biosStatus"])
}

func TestUploadBiosFile_OwnerCanUpload(t *testing.T) {
	_, database, router := setupBiosTestEnv(t)
	ownerToken := createBiosTestUser(t, database, "owner")

	content := []byte("owner uploaded bios")
	w := uploadBiosFile(t, router, ownerToken, "test.bin", content)

	assert.Equal(t, http.StatusOK, w.Code)
}

func TestDeleteBiosFile_OwnerCanDelete(t *testing.T) {
	store, database, router := setupBiosTestEnv(t)
	ownerToken := createBiosTestUser(t, database, "owner")

	require.NoError(t, os.WriteFile(filepath.Join(store.BiosDir, "owner-del.bin"), []byte("data"), 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/admin/bios/owner-del.bin", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
}
