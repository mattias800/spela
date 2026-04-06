package api

import (
	"archive/zip"
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
	"github.com/spela/server/internal/scraper"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// replaceROMTestEnv holds the test environment for replace ROM tests.
type replaceROMTestEnv struct {
	db         *gorm.DB
	cfg        *Config
	router     http.Handler
	adminToken string
	gameDir    string
}

func setupReplaceROMTestEnv(t *testing.T) *replaceROMTestEnv {
	t.Helper()
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)

	// Create admin user
	user := db.User{
		Username:     "replaceadmin",
		Email:        "replaceadmin@test.com",
		PasswordHash: "unused",
		Role:         "owner",
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, testJWTSecret)
	require.NoError(t, err)

	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	return &replaceROMTestEnv{
		db:         database,
		cfg:        cfg,
		router:     router,
		adminToken: token,
		gameDir:    cfg.GameDirs[0],
	}
}

// createTestGameWithROM creates a game record and its ROM file on disk.
func createTestGameWithROM(t *testing.T, env *replaceROMTestEnv, consoleAbbrev, title, fileName string, romContent []byte) db.Game {
	t.Helper()

	var console db.Console
	require.NoError(t, env.db.Where("abbreviation = ?", consoleAbbrev).First(&console).Error)

	// Create the ROM file on disk
	consoleDir := filepath.Join(env.gameDir, console.FolderName)
	require.NoError(t, os.MkdirAll(consoleDir, 0755))
	romPath := filepath.Join(consoleDir, fileName)
	require.NoError(t, os.WriteFile(romPath, romContent, 0644))

	// Compute CRC for the original file
	crc, err := scraper.ComputeFileCRC32(romPath)
	require.NoError(t, err)

	game := db.Game{
		ConsoleID:          console.ID,
		Title:              title,
		FileName:           fileName,
		FilePath:           filepath.Join(console.FolderName, fileName),
		FileSize:           int64(len(romContent)),
		CRC32:              crc,
		VerificationStatus: "unverified",
	}
	require.NoError(t, env.db.Create(&game).Error)
	return game
}

// replaceROM sends a PUT request to replace a game's ROM file.
func replaceROM(t *testing.T, env *replaceROMTestEnv, gameID uint, fileName string, fileContent []byte) *httptest.ResponseRecorder {
	t.Helper()

	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)
	part, err := writer.CreateFormFile("file", fileName)
	require.NoError(t, err)
	_, err = part.Write(fileContent)
	require.NoError(t, err)
	require.NoError(t, writer.Close())

	w := httptest.NewRecorder()
	url := fmt.Sprintf("/api/admin/games/%d/replace-rom", gameID)
	req := httptest.NewRequest("PUT", url, body)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	env.router.ServeHTTP(w, req)
	return w
}

func TestReplaceROM_Success(t *testing.T) {
	env := setupReplaceROMTestEnv(t)

	// Create a game with an initial ROM
	originalContent := []byte("original rom data")
	game := createTestGameWithROM(t, env, "NES", "Test Game", "test.nes", originalContent)
	originalCRC := game.CRC32

	// Replace with new ROM content
	newContent := []byte("new rom data that is different")
	w := replaceROM(t, env, game.ID, "replacement.nes", newContent)

	assert.Equal(t, http.StatusOK, w.Code, w.Body.String())

	var resp ReplaceROMResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Verify response structure
	assert.NotEmpty(t, resp.ReplacementResult.CRC32)
	assert.NotEqual(t, originalCRC, resp.ReplacementResult.CRC32)
	assert.Equal(t, "unverified", resp.ReplacementResult.PreviousStatus)
	assert.Equal(t, originalCRC, resp.ReplacementResult.PreviousCRC32)

	// Verify game record was updated
	var updatedGame db.Game
	require.NoError(t, env.db.First(&updatedGame, game.ID).Error)
	assert.Equal(t, resp.ReplacementResult.CRC32, updatedGame.CRC32)
	assert.Equal(t, int64(len(newContent)), updatedGame.FileSize)
}

func TestReplaceROM_CRC32Updated(t *testing.T) {
	env := setupReplaceROMTestEnv(t)

	originalContent := []byte("original nes rom")
	game := createTestGameWithROM(t, env, "NES", "CRC Test", "crc_test.nes", originalContent)

	// Compute expected CRC of new content
	newContent := []byte("completely different rom content for crc check")
	tmpFile, err := os.CreateTemp("", "crc-test-*.nes")
	require.NoError(t, err)
	_, err = tmpFile.Write(newContent)
	require.NoError(t, err)
	tmpFile.Close()
	defer os.Remove(tmpFile.Name())
	expectedCRC, err := scraper.ComputeFileCRC32(tmpFile.Name())
	require.NoError(t, err)

	w := replaceROM(t, env, game.ID, "new_crc.nes", newContent)
	assert.Equal(t, http.StatusOK, w.Code, w.Body.String())

	var resp ReplaceROMResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// CRC in response should match expected
	assert.Equal(t, expectedCRC, resp.ReplacementResult.CRC32)

	// DB record should have the new CRC
	var updatedGame db.Game
	require.NoError(t, env.db.First(&updatedGame, game.ID).Error)
	assert.Equal(t, expectedCRC, updatedGame.CRC32)
}

func TestReplaceROM_FileSizeUpdated(t *testing.T) {
	env := setupReplaceROMTestEnv(t)

	originalContent := []byte("short")
	game := createTestGameWithROM(t, env, "SNES", "Size Test", "size_test.sfc", originalContent)

	newContent := []byte("this is a much longer piece of content that represents the new ROM file")
	w := replaceROM(t, env, game.ID, "bigger.sfc", newContent)
	assert.Equal(t, http.StatusOK, w.Code, w.Body.String())

	var updatedGame db.Game
	require.NoError(t, env.db.First(&updatedGame, game.ID).Error)
	assert.Equal(t, int64(len(newContent)), updatedGame.FileSize)
}

func TestReplaceROM_GameNotFound(t *testing.T) {
	env := setupReplaceROMTestEnv(t)

	w := replaceROM(t, env, 99999, "test.nes", []byte("rom data"))
	assert.Equal(t, http.StatusNotFound, w.Code)

	var resp map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "game not found", resp["error"])
}

func TestReplaceROM_InvalidExtension(t *testing.T) {
	env := setupReplaceROMTestEnv(t)

	originalContent := []byte("original rom")
	game := createTestGameWithROM(t, env, "NES", "Ext Test", "ext_test.nes", originalContent)

	w := replaceROM(t, env, game.ID, "readme.txt", []byte("not a rom"))
	assert.Equal(t, http.StatusBadRequest, w.Code)

	var resp map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Contains(t, resp["error"], "unrecognized ROM file extension")
}

func TestReplaceROM_ZipWithROM(t *testing.T) {
	env := setupReplaceROMTestEnv(t)

	originalContent := []byte("original nes rom data")
	game := createTestGameWithROM(t, env, "NES", "Zip Test", "zip_test.nes", originalContent)

	// Create a zip file containing a NES ROM
	romContent := []byte("zipped rom content for NES")
	zipBuf := &bytes.Buffer{}
	zw := zip.NewWriter(zipBuf)
	zfWriter, err := zw.Create("inner_game.nes")
	require.NoError(t, err)
	_, err = zfWriter.Write(romContent)
	require.NoError(t, err)
	require.NoError(t, zw.Close())

	w := replaceROM(t, env, game.ID, "game_bundle.zip", zipBuf.Bytes())
	assert.Equal(t, http.StatusOK, w.Code, w.Body.String())

	var resp ReplaceROMResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// CRC should be of the extracted ROM content, not the zip
	assert.NotEmpty(t, resp.ReplacementResult.CRC32)

	// File size should match the extracted ROM, not the zip
	var updatedGame db.Game
	require.NoError(t, env.db.First(&updatedGame, game.ID).Error)
	assert.Equal(t, int64(len(romContent)), updatedGame.FileSize)
}

func TestReplaceROM_ZipNoROMs(t *testing.T) {
	env := setupReplaceROMTestEnv(t)

	originalContent := []byte("original rom")
	game := createTestGameWithROM(t, env, "NES", "Empty Zip", "empty_zip_test.nes", originalContent)

	// Create a zip file with no ROM files
	zipBuf := &bytes.Buffer{}
	zw := zip.NewWriter(zipBuf)
	zfWriter, err := zw.Create("readme.txt")
	require.NoError(t, err)
	_, err = zfWriter.Write([]byte("just a readme"))
	require.NoError(t, err)
	require.NoError(t, zw.Close())

	w := replaceROM(t, env, game.ID, "no_roms.zip", zipBuf.Bytes())
	assert.Equal(t, http.StatusBadRequest, w.Code)

	var resp map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Contains(t, resp["error"], "no recognized ROM file")
}

func TestReplaceROM_PreservesMetadata(t *testing.T) {
	env := setupReplaceROMTestEnv(t)

	originalContent := []byte("original rom")
	game := createTestGameWithROM(t, env, "NES", "Metadata Preserve", "meta_test.nes", originalContent)

	// Add metadata to the game
	game.Description = "A great game"
	game.Developer = "Test Dev"
	game.Publisher = "Test Pub"
	game.Genre = "Action"
	game.IGDBCriticsRating = 85.5
	game.CoverURL = "https://example.com/cover.jpg"
	require.NoError(t, env.db.Save(&game).Error)

	// Replace ROM
	newContent := []byte("replacement rom data")
	w := replaceROM(t, env, game.ID, "new.nes", newContent)
	assert.Equal(t, http.StatusOK, w.Code, w.Body.String())

	// Verify metadata is preserved
	var updatedGame db.Game
	require.NoError(t, env.db.First(&updatedGame, game.ID).Error)
	assert.Equal(t, "A great game", updatedGame.Description)
	assert.Equal(t, "Test Dev", updatedGame.Developer)
	assert.Equal(t, "Test Pub", updatedGame.Publisher)
	assert.Equal(t, "Action", updatedGame.Genre)
	assert.Equal(t, 85.5, updatedGame.IGDBCriticsRating)
	assert.Equal(t, "https://example.com/cover.jpg", updatedGame.CoverURL)

	// But CRC and file size should be updated
	assert.Equal(t, int64(len(newContent)), updatedGame.FileSize)
	assert.NotEqual(t, game.CRC32, updatedGame.CRC32)
}

func TestReplaceROM_UnverifiedStaysUnverified(t *testing.T) {
	env := setupReplaceROMTestEnv(t)

	// No DAT files loaded, so verification will not happen
	originalContent := []byte("original rom")
	game := createTestGameWithROM(t, env, "NES", "Unverified Test", "unverified_test.nes", originalContent)

	newContent := []byte("new unverified rom")
	w := replaceROM(t, env, game.ID, "new.nes", newContent)
	assert.Equal(t, http.StatusOK, w.Code, w.Body.String())

	var resp ReplaceROMResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Without DAT files, ROM should remain unverified
	assert.False(t, resp.ReplacementResult.Verified)
	assert.Equal(t, "unverified", resp.ReplacementResult.PreviousStatus)

	var updatedGame db.Game
	require.NoError(t, env.db.First(&updatedGame, game.ID).Error)
	assert.Equal(t, "unverified", updatedGame.VerificationStatus)
}

func TestReplaceROM_RequiresAdmin(t *testing.T) {
	env := setupReplaceROMTestEnv(t)

	originalContent := []byte("original rom")
	game := createTestGameWithROM(t, env, "NES", "Auth Test", "auth_test.nes", originalContent)

	// Create a non-admin user
	user := db.User{
		Username:     "regularuser",
		Email:        "regular@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, env.db.Create(&user).Error)
	userToken, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, testJWTSecret)
	require.NoError(t, err)

	// Try to replace ROM as non-admin
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)
	part, err := writer.CreateFormFile("file", "new.nes")
	require.NoError(t, err)
	_, err = part.Write([]byte("new rom data"))
	require.NoError(t, err)
	require.NoError(t, writer.Close())

	w := httptest.NewRecorder()
	url := fmt.Sprintf("/api/admin/games/%d/replace-rom", game.ID)
	req := httptest.NewRequest("PUT", url, body)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+userToken)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}
