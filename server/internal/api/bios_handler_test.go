package api

import (
	"archive/zip"
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
	"gorm.io/gorm"
)

// setupBiosTestEnv builds a router from the production NewRouter so the BIOS
// endpoints — including the multipart admin upload — are exercised through the
// huma operations rather than a custom in-test gin registration.
func setupBiosTestEnv(t *testing.T) (*storage.Storage, *gorm.DB, *gin.Engine) {
	t.Helper()
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	t.Cleanup(cleanup)
	return cfg.Storage, database, router
}

// createBiosTestUser creates a user and returns a JWT token signed with the
// test JWT secret used by setupTestEnv.
func createBiosTestUser(t *testing.T, database *gorm.DB, role db.UserRole) string {
	t.Helper()
	user := db.User{
		Username:     fmt.Sprintf("user-%s-%d", role, database.RowsAffected),
		PasswordHash: "unused",
		Role:         role,
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, string(user.Role), testJWTSecret)
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

// #911 regression — PSP entry is a Bundle with SubDir="PPSSPP" and
// StripPrefix; the sentinel lands at <biosDir>/PPSSPP/ppge_atlas.zim
// after the buildbot archive is extracted. Earlier shapes that put
// the path inside FileName ("PPSSPP/ppge_atlas.zim" with empty
// SubDir) tripped the listing endpoint into reporting "missing" even
// when the file was on disk — the flat-dir lookup missed it and the
// Stat fallback was gated on SubDir!="". Lock the present-status
// behaviour for the canonical PSP entry shape.
func TestListBiosFiles_BundleEntry_PresentUnderSubDir(t *testing.T) {
	store, database, router := setupBiosTestEnv(t)
	token := createBiosTestUser(t, database, "user")

	pspDir := filepath.Join(store.BiosDir, "PPSSPP")
	require.NoError(t, os.MkdirAll(pspDir, 0755))
	// Sentinel must be > 1KB so the size heuristic doesn't flag it.
	require.NoError(t, os.WriteFile(filepath.Join(pspDir, "ppge_atlas.zim"), bytes.Repeat([]byte{0xAB}, 2048), 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp BiosListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	var psp *BiosFileResponse
	for i, f := range resp.Files {
		if f.Name == "ppge_atlas.zim" && f.ConsoleID != nil && *f.ConsoleID == "psp" {
			psp = &resp.Files[i]
			break
		}
	}
	require.NotNil(t, psp, "PSP bundle entry should appear in /api/bios with FileName=ppge_atlas.zim")
	assert.NotEqual(t, "missing", psp.Status, "sentinel under SubDir must not be reported as missing")
	assert.Equal(t, "PPSSPP", psp.SubDir)

	var pspConsole *ConsoleBiosStatus
	for i, cs := range resp.Consoles {
		if cs.ConsoleID == "psp" {
			pspConsole = &resp.Consoles[i]
			break
		}
	}
	require.NotNil(t, pspConsole)
	assert.Equal(t, 1, pspConsole.RequiredPresent, "PSP required count should report 1/1 with sentinel on disk")
	assert.Equal(t, 1, pspConsole.RequiredTotal)
}

// #911 — clients downloading a Bundle entry fetch a zip of the
// SubDir tree rather than a single file. Build the SubDir on disk,
// hit the archive endpoint, assert the response is a valid zip
// containing the files we placed.
func TestDownloadBiosArchive_StreamsZipOfSubDir(t *testing.T) {
	store, database, router := setupBiosTestEnv(t)
	token := createBiosTestUser(t, database, "user")

	pspDir := filepath.Join(store.BiosDir, "PPSSPP")
	require.NoError(t, os.MkdirAll(filepath.Join(pspDir, "flash0", "font"), 0755))
	require.NoError(t, os.WriteFile(filepath.Join(pspDir, "ppge_atlas.zim"), bytes.Repeat([]byte{0xAB}, 2048), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(pspDir, "flash0", "font", "ltn0.pgf"), []byte("font-data"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(pspDir, "Roboto_Condensed-Regular.ttf"), bytes.Repeat([]byte{0xCD}, 1024), 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios/archive/ppge_atlas.zim", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code, "body=%s", w.Body.String())
	assert.Equal(t, "application/zip", w.Header().Get("Content-Type"))

	zr, err := zip.NewReader(bytes.NewReader(w.Body.Bytes()), int64(w.Body.Len()))
	require.NoError(t, err)

	got := make(map[string]int)
	for _, f := range zr.File {
		got[f.Name] = int(f.UncompressedSize64)
	}
	assert.Equal(t, 2048, got["ppge_atlas.zim"])
	assert.Equal(t, len("font-data"), got["flash0/font/ltn0.pgf"])
	assert.Equal(t, 1024, got["Roboto_Condensed-Regular.ttf"])
}

func TestDownloadBiosArchive_404WhenNotBundle(t *testing.T) {
	_, database, router := setupBiosTestEnv(t)
	token := createBiosTestUser(t, database, "user")

	w := httptest.NewRecorder()
	// scph5501 is a single-file PSX BIOS, not a bundle.
	req := httptest.NewRequest("GET", "/api/bios/archive/scph5501.bin", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestDownloadBiosArchive_404WhenSubDirMissing(t *testing.T) {
	_, database, router := setupBiosTestEnv(t)
	token := createBiosTestUser(t, database, "user")

	// PSP entry is a bundle but the SubDir hasn't been populated on this
	// fresh test env — the endpoint should 404, not 500 or empty zip.
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios/archive/ppge_atlas.zim", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
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

// TestUploadBiosFile_KnownFile_MismatchedMD5 verifies issue #1124(A):
// uploading bytes that match a registry filename but mismatch the
// expected MD5 returns 400 (so the upload is REJECTED) rather than the
// pre-fix 200 with status="invalid" that left the bad bytes on disk.
func TestUploadBiosFile_KnownFile_MismatchedMD5(t *testing.T) {
	_, database, router := setupBiosTestEnv(t)
	adminToken := createBiosTestUser(t, database, "admin")

	content := []byte("fake psx bios content")
	w := uploadBiosFile(t, router, adminToken, "scph5501.bin", content)

	assert.Equal(t, http.StatusBadRequest, w.Code, "body=%s", w.Body.String())
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

	// 422 (Unprocessable Entity) from huma's multipart-body validation when no
	// Content-Type/body is sent — previously 400 from the gin handler's manual
	// FormFile check. Semantically equivalent (both indicate an invalid
	// request); huma surfaces the more specific RFC status code.
	assert.Equal(t, http.StatusUnprocessableEntity, w.Code)
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
			name:       "GBA with no files returns not_required (only optional BIOS)",
			consoleID:  "GBA",
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
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

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
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

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
