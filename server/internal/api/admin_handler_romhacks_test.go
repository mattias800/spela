package api

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"hash/crc32"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"testing"

	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// romHackTestEnv holds the test environment for ROM hack handler tests.
type romHackTestEnv struct {
	db         *gorm.DB
	cfg        *Config
	adminToken string
	tmpDir     string
	baseGame   db.Game
}

func setupRomHackTestEnv(t *testing.T) *romHackTestEnv {
	t.Helper()
	database, cfg := setupTestEnv(t)

	// Create admin user
	user := db.User{
		Username:     "romhackadmin",
		Email:        "romhackadmin@test.com",
		PasswordHash: "unused",
		Role:         "owner",
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, string(user.Role), testJWTSecret)
	require.NoError(t, err)

	// Find a console
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)

	// Create the console folder and base ROM file
	consoleDir := filepath.Join(cfg.GameDirs[0], console.FolderName)
	require.NoError(t, os.MkdirAll(consoleDir, 0755))

	romData := []byte("Hello, World! This is a test ROM file for patching.")
	romFilename := "Super Mario Bros (USA).nes"
	romPath := filepath.Join(consoleDir, romFilename)
	require.NoError(t, os.WriteFile(romPath, romData, 0644))

	// Create base game record
	baseGame := db.Game{
		ConsoleID:   console.ID,
		Title:       "Super Mario Bros",
		FileName:    romFilename,
		FilePath:    filepath.Join(console.FolderName, romFilename),
		FileSize:    int64(len(romData)),
		Description: "A classic platformer",
		CoverURL:    "covers/smb.png",
		Developer:   "Nintendo",
		Publisher:   "Nintendo",
		Genre:       "Platformer",
		GroupKey:     "super mario bros",
		IsPrimary:   true,
		Region:      "USA",
	}
	require.NoError(t, database.Create(&baseGame).Error)

	return &romHackTestEnv{
		db:         database,
		cfg:        cfg,
		adminToken: token,
		tmpDir:     cfg.GameDirs[0],
		baseGame:   baseGame,
	}
}

// buildTestIPSPatch creates a valid IPS patch that replaces bytes at the given offset.
func buildTestIPSPatch(offset int, data []byte) []byte {
	var patch []byte
	patch = append(patch, []byte("PATCH")...)
	// 3-byte offset
	patch = append(patch, byte(offset>>16), byte(offset>>8), byte(offset))
	// 2-byte size
	size := make([]byte, 2)
	binary.BigEndian.PutUint16(size, uint16(len(data)))
	patch = append(patch, size...)
	patch = append(patch, data...)
	patch = append(patch, []byte("EOF")...)
	return patch
}

// buildTestBPSPatch creates a minimal valid BPS patch that transforms source into target.
func buildTestBPSPatch(source, target []byte) []byte {
	encodeVLQ := func(value uint64) []byte {
		var result []byte
		for {
			x := byte(value & 0x7F)
			value >>= 7
			if value == 0 {
				result = append(result, x|0x80)
				return result
			}
			result = append(result, x)
			value--
		}
	}
	writeLE32 := func(v uint32) []byte {
		return []byte{byte(v), byte(v >> 8), byte(v >> 16), byte(v >> 24)}
	}

	var patch []byte
	patch = append(patch, []byte("BPS1")...)
	patch = append(patch, encodeVLQ(uint64(len(source)))...)
	patch = append(patch, encodeVLQ(uint64(len(target)))...)
	patch = append(patch, encodeVLQ(0)...) // no metadata

	// TargetRead action for all target data
	actionData := uint64(len(target)-1)<<2 | 1
	patch = append(patch, encodeVLQ(actionData)...)
	patch = append(patch, target...)

	// Footer
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(source))...)
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(target))...)
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(patch))...)

	return patch
}

func createRomHackRequest(t *testing.T, token string, fields map[string]string, patchFilename string, patchData []byte) *http.Request {
	t.Helper()
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)

	for key, value := range fields {
		require.NoError(t, writer.WriteField(key, value))
	}

	part, err := writer.CreateFormFile("patch_file", patchFilename)
	require.NoError(t, err)
	_, err = part.Write(patchData)
	require.NoError(t, err)

	require.NoError(t, writer.Close())

	req := httptest.NewRequest("POST", "/api/admin/rom-hacks", body)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+token)
	return req
}

func TestCreateRomHack_VariantMode(t *testing.T) {
	env := setupRomHackTestEnv(t)
	router, cleanup := NewRouter(*env.cfg)
	defer cleanup()

	// Create an IPS patch
	patch := buildTestIPSPatch(0, []byte("Patched"))

	req := createRomHackRequest(t, env.adminToken, map[string]string{
		"base_game_id": strconv.FormatUint(uint64(env.baseGame.ID), 10),
		"mode":         "variant",
		"label":        "English Translation",
	}, "translation.ips", patch)

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusCreated, w.Code, "body: %s", w.Body.String())

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.NotEmpty(t, resp["id"])
	assert.Equal(t, "Super Mario Bros [English Translation]", resp["title"])

	// Verify the game record
	gameID, _ := strconv.ParseUint(resp["id"].(string), 10, 64)
	var game db.Game
	require.NoError(t, env.db.First(&game, gameID).Error)
	assert.Equal(t, env.baseGame.ConsoleID, game.ConsoleID)
	assert.Equal(t, env.baseGame.GroupKey, game.GroupKey)
	assert.Contains(t, game.Tags, "hack")
	assert.Equal(t, "Super Mario Bros [English Translation]", game.Title)
	assert.Equal(t, env.baseGame.Description, game.Description)
	assert.Equal(t, env.baseGame.CoverURL, game.CoverURL)
	assert.Equal(t, env.baseGame.Developer, game.Developer)
	assert.Nil(t, game.ParentGameID) // variants don't have ParentGameID

	// Verify the patched ROM file exists on disk
	romPath := filepath.Join(env.tmpDir, game.FilePath)
	_, err := os.Stat(romPath)
	assert.NoError(t, err)

	// Verify patched content
	content, err := os.ReadFile(romPath)
	require.NoError(t, err)
	assert.Equal(t, byte('P'), content[0])
	assert.Equal(t, byte('a'), content[1])
}

func TestCreateRomHack_StandaloneMode(t *testing.T) {
	env := setupRomHackTestEnv(t)
	router, cleanup := NewRouter(*env.cfg)
	defer cleanup()

	// Create an IPS patch
	patch := buildTestIPSPatch(0, []byte("Patched"))

	req := createRomHackRequest(t, env.adminToken, map[string]string{
		"base_game_id": strconv.FormatUint(uint64(env.baseGame.ID), 10),
		"mode":         "standalone",
		"title":        "My Cool ROM Hack",
	}, "hack.ips", patch)

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusCreated, w.Code, "body: %s", w.Body.String())

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.NotEmpty(t, resp["id"])
	assert.Equal(t, "My Cool ROM Hack", resp["title"])

	// Verify the game record
	gameID, _ := strconv.ParseUint(resp["id"].(string), 10, 64)
	var game db.Game
	require.NoError(t, env.db.First(&game, gameID).Error)
	assert.Equal(t, env.baseGame.ConsoleID, game.ConsoleID)
	assert.NotEqual(t, env.baseGame.GroupKey, game.GroupKey) // standalone gets its own group key
	assert.Equal(t, "My Cool ROM Hack", game.Title)
	assert.NotNil(t, game.ParentGameID)
	assert.Equal(t, env.baseGame.ID, *game.ParentGameID)
	assert.True(t, game.IsPrimary)

	// Verify the patched ROM file exists
	romPath := filepath.Join(env.tmpDir, game.FilePath)
	_, err := os.Stat(romPath)
	assert.NoError(t, err)
}

func TestCreateRomHack_BPSPatch(t *testing.T) {
	env := setupRomHackTestEnv(t)
	router, cleanup := NewRouter(*env.cfg)
	defer cleanup()

	// Read the actual ROM data to build a BPS patch
	romPath := filepath.Join(env.tmpDir, env.baseGame.FilePath)
	romData, err := os.ReadFile(romPath)
	require.NoError(t, err)

	// Create target data
	target := make([]byte, len(romData))
	copy(target, romData)
	copy(target, []byte("BPS Patched!"))

	patch := buildTestBPSPatch(romData, target)

	req := createRomHackRequest(t, env.adminToken, map[string]string{
		"base_game_id": strconv.FormatUint(uint64(env.baseGame.ID), 10),
		"mode":         "standalone",
		"title":        "BPS Hack",
	}, "hack.bps", patch)

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusCreated, w.Code, "body: %s", w.Body.String())

	// Verify patched content
	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	gameID, _ := strconv.ParseUint(resp["id"].(string), 10, 64)
	var game db.Game
	require.NoError(t, env.db.First(&game, gameID).Error)

	patchedRomPath := filepath.Join(env.tmpDir, game.FilePath)
	patchedContent, err := os.ReadFile(patchedRomPath)
	require.NoError(t, err)
	assert.Equal(t, target, patchedContent)
}

func TestCreateRomHack_MissingBaseGameID(t *testing.T) {
	env := setupRomHackTestEnv(t)
	router, cleanup := NewRouter(*env.cfg)
	defer cleanup()

	patch := buildTestIPSPatch(0, []byte("X"))

	req := createRomHackRequest(t, env.adminToken, map[string]string{
		"mode":  "variant",
		"label": "Test",
	}, "patch.ips", patch)

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "base_game_id")
}

func TestCreateRomHack_InvalidMode(t *testing.T) {
	env := setupRomHackTestEnv(t)
	router, cleanup := NewRouter(*env.cfg)
	defer cleanup()

	patch := buildTestIPSPatch(0, []byte("X"))

	req := createRomHackRequest(t, env.adminToken, map[string]string{
		"base_game_id": strconv.FormatUint(uint64(env.baseGame.ID), 10),
		"mode":         "invalid",
	}, "patch.ips", patch)

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "mode must be")
}

func TestCreateRomHack_MissingLabel(t *testing.T) {
	env := setupRomHackTestEnv(t)
	router, cleanup := NewRouter(*env.cfg)
	defer cleanup()

	patch := buildTestIPSPatch(0, []byte("X"))

	req := createRomHackRequest(t, env.adminToken, map[string]string{
		"base_game_id": strconv.FormatUint(uint64(env.baseGame.ID), 10),
		"mode":         "variant",
	}, "patch.ips", patch)

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "label is required")
}

func TestCreateRomHack_MissingTitle(t *testing.T) {
	env := setupRomHackTestEnv(t)
	router, cleanup := NewRouter(*env.cfg)
	defer cleanup()

	patch := buildTestIPSPatch(0, []byte("X"))

	req := createRomHackRequest(t, env.adminToken, map[string]string{
		"base_game_id": strconv.FormatUint(uint64(env.baseGame.ID), 10),
		"mode":         "standalone",
	}, "patch.ips", patch)

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "title is required")
}

func TestCreateRomHack_UnsupportedFormat(t *testing.T) {
	env := setupRomHackTestEnv(t)
	router, cleanup := NewRouter(*env.cfg)
	defer cleanup()

	req := createRomHackRequest(t, env.adminToken, map[string]string{
		"base_game_id": strconv.FormatUint(uint64(env.baseGame.ID), 10),
		"mode":         "standalone",
		"title":        "Test",
	}, "patch.xyz", []byte("data"))

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "unsupported patch format")
}

func TestCreateRomHack_BaseGameNotFound(t *testing.T) {
	env := setupRomHackTestEnv(t)
	router, cleanup := NewRouter(*env.cfg)
	defer cleanup()

	patch := buildTestIPSPatch(0, []byte("X"))

	req := createRomHackRequest(t, env.adminToken, map[string]string{
		"base_game_id": "99999",
		"mode":         "standalone",
		"title":        "Test",
	}, "patch.ips", patch)

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestCreateRomHack_NonAdminDenied(t *testing.T) {
	env := setupRomHackTestEnv(t)
	router, cleanup := NewRouter(*env.cfg)
	defer cleanup()

	// Create a regular user
	user := db.User{
		Username:     "regularuser",
		Email:        "regular@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, env.db.Create(&user).Error)
	userToken, err := auth.GenerateAccessToken(user.ID, user.Username, string(user.Role), testJWTSecret)
	require.NoError(t, err)

	patch := buildTestIPSPatch(0, []byte("X"))

	req := createRomHackRequest(t, userToken, map[string]string{
		"base_game_id": strconv.FormatUint(uint64(env.baseGame.ID), 10),
		"mode":         "standalone",
		"title":        "Test",
	}, "patch.ips", patch)

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestGetGame_ParentGameAndRomHacks(t *testing.T) {
	env := setupRomHackTestEnv(t)
	router, cleanup := NewRouter(*env.cfg)
	defer cleanup()

	// Create a standalone ROM hack that references the base game
	parentID := env.baseGame.ID

	var console db.Console
	require.NoError(t, env.db.Where("abbreviation = ?", "NES").First(&console).Error)

	hackGame := db.Game{
		ConsoleID:    console.ID,
		Title:        "Hack Game",
		FileName:     "hack.nes",
		FilePath:     filepath.Join(console.FolderName, "hack.nes"),
		FileSize:     100,
		GroupKey:      "hack game",
		ParentGameID: &parentID,
		IsPrimary:    true,
		CoverURL:     "covers/hack.png",
	}
	require.NoError(t, env.db.Create(&hackGame).Error)

	// Write a dummy ROM for the hack
	hackRomDir := filepath.Join(env.tmpDir, console.FolderName)
	require.NoError(t, os.WriteFile(filepath.Join(hackRomDir, "hack.nes"), []byte("hack rom"), 0644))

	// Get the base game detail — should include romHacks
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.FormatUint(uint64(env.baseGame.ID), 10), nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var baseResp GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &baseResp))
	assert.Nil(t, baseResp.ParentGame) // base game has no parent
	require.Len(t, baseResp.RomHacks, 1)
	assert.Equal(t, strconv.FormatUint(uint64(hackGame.ID), 10), baseResp.RomHacks[0].ID)
	assert.Equal(t, "Hack Game", baseResp.RomHacks[0].Title)

	// Get the hack game detail — should include parentGame
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+strconv.FormatUint(uint64(hackGame.ID), 10), nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var hackResp GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &hackResp))
	require.NotNil(t, hackResp.ParentGame)
	assert.Equal(t, strconv.FormatUint(uint64(env.baseGame.ID), 10), hackResp.ParentGame.ID)
	assert.Equal(t, "Super Mario Bros", hackResp.ParentGame.Title)
	assert.Empty(t, hackResp.RomHacks) // hack has no child hacks
}

func TestGetGame_DeletedParentGraceful(t *testing.T) {
	env := setupRomHackTestEnv(t)
	router, cleanup := NewRouter(*env.cfg)
	defer cleanup()

	// Create a standalone hack with a non-existent parent ID
	nonExistentID := uint(99999)
	var console db.Console
	require.NoError(t, env.db.Where("abbreviation = ?", "NES").First(&console).Error)

	hackGame := db.Game{
		ConsoleID:    console.ID,
		Title:        "Orphan Hack",
		FileName:     "orphan.nes",
		FilePath:     filepath.Join(console.FolderName, "orphan.nes"),
		FileSize:     100,
		GroupKey:      "orphan hack",
		ParentGameID: &nonExistentID,
		IsPrimary:    true,
	}
	require.NoError(t, env.db.Create(&hackGame).Error)

	// Write a dummy ROM
	hackRomDir := filepath.Join(env.tmpDir, console.FolderName)
	require.NoError(t, os.WriteFile(filepath.Join(hackRomDir, "orphan.nes"), []byte("orphan"), 0644))

	// Get the hack game detail — parentGame should be nil (graceful)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.FormatUint(uint64(hackGame.ID), 10), nil)
	req.Header.Set("Authorization", "Bearer "+env.adminToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Nil(t, resp.ParentGame) // parent not found, should be nil gracefully
}
