package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/storage"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func setupBiosTestEnv(t *testing.T) (*storage.Storage, *gin.Engine) {
	t.Helper()

	tmpDir := t.TempDir()
	store, err := storage.NewStorage(
		filepath.Join(tmpDir, "saves"),
		filepath.Join(tmpDir, "cores"),
		filepath.Join(tmpDir, "images"),
		filepath.Join(tmpDir, "bios"),
	)
	require.NoError(t, err)

	gin.SetMode(gin.TestMode)
	router := gin.New()

	handler := &BiosHandler{Storage: store}
	router.GET("/api/bios", handler.ListBiosFiles)
	router.GET("/api/bios/:filename", handler.GetBiosFile)

	return store, router
}

func TestListBiosFiles_Empty(t *testing.T) {
	_, router := setupBiosTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var files []BiosFileInfo
	err := json.Unmarshal(w.Body.Bytes(), &files)
	require.NoError(t, err)
	assert.Empty(t, files)
}

func TestListBiosFiles_ReturnsFiles(t *testing.T) {
	store, router := setupBiosTestEnv(t)

	// Create some BIOS files
	require.NoError(t, os.WriteFile(filepath.Join(store.BiosDir, "scph5501.bin"), []byte("psx bios data here"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(store.BiosDir, "gba_bios.bin"), []byte("gba"), 0644))

	// Create a subdirectory (should be ignored)
	require.NoError(t, os.MkdirAll(filepath.Join(store.BiosDir, "subdir"), 0755))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var files []BiosFileInfo
	err := json.Unmarshal(w.Body.Bytes(), &files)
	require.NoError(t, err)
	assert.Len(t, files, 2)

	// Build a map for easier assertions (order may vary)
	fileMap := make(map[string]int64)
	for _, f := range files {
		fileMap[f.Name] = f.Size
	}
	assert.Equal(t, int64(18), fileMap["scph5501.bin"])
	assert.Equal(t, int64(3), fileMap["gba_bios.bin"])
}

func TestGetBiosFile_Success(t *testing.T) {
	store, router := setupBiosTestEnv(t)

	content := []byte("bios file content")
	require.NoError(t, os.WriteFile(filepath.Join(store.BiosDir, "test.bin"), content, 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios/test.bin", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "bios file content", w.Body.String())
}

func TestGetBiosFile_NotFound(t *testing.T) {
	_, router := setupBiosTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios/nonexistent.bin", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, "bios file not found", resp["error"])
}

func TestGetBiosFile_PathTraversal(t *testing.T) {
	store, router := setupBiosTestEnv(t)

	// Create a file outside the BIOS directory
	secretPath := filepath.Join(filepath.Dir(store.BiosDir), "secret.txt")
	require.NoError(t, os.WriteFile(secretPath, []byte("secret data"), 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios/..%2Fsecret.txt", nil)
	router.ServeHTTP(w, req)

	// Should either return 404 or serve a file from within bios dir only
	// The sanitizeFilename strips the path traversal, so the sanitized name
	// won't match any file in the bios directory
	assert.NotEqual(t, "secret data", w.Body.String(),
		"path traversal should not serve files outside bios directory")
}
