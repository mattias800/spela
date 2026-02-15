package scanner

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func setupTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	err = database.AutoMigrate(&db.User{}, &db.Console{}, &db.Game{}, &db.SaveState{})
	require.NoError(t, err)
	err = db.SeedConsoles(database)
	require.NoError(t, err)
	return database
}

func TestGameTitle(t *testing.T) {
	tests := []struct {
		filename string
		want     string
	}{
		{"Super Mario Bros.nes", "Super Mario Bros"},
		{"Zelda (USA).nes", "Zelda"},
		{"Pokemon [J].gba", "Pokemon"},
		{"Chrono Trigger (USA) [!].sfc", "Chrono Trigger"},
		{"Final Fantasy VI.smc", "Final Fantasy VI"},
	}

	for _, tt := range tests {
		t.Run(tt.filename, func(t *testing.T) {
			result := gameTitle(tt.filename)
			assert.Equal(t, tt.want, result)
		})
	}
}

func TestIdentifyConsole(t *testing.T) {
	s := &Scanner{}
	tests := []struct {
		name string
		path string
		ext  string
		want string
	}{
		{"NES by extension", "/games/some/file.nes", ".nes", "NES"},
		{"SNES by extension", "/games/some/file.sfc", ".sfc", "SNES"},
		{"GBA by extension", "/games/some/file.gba", ".gba", "GBA"},
		{"NES by directory", "/games/nes/file.bin", ".bin", "NES"},
		{"SNES by directory", "/games/snes/file.bin", ".bin", "SNES"},
		{"PSX by directory", "/games/psx/disc.bin", ".bin", "PSX"},
		{"Unknown", "/games/unknown/file.xyz", ".xyz", ""},
		{"txt in NES dir ignored", "/games/nes/readme.txt", ".txt", ""},
		{"jpg in snes dir ignored", "/games/snes/cover.jpg", ".jpg", ""},
		{"nfo in gba dir ignored", "/games/gba/game.nfo", ".nfo", ""},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := s.identifyConsole(tt.path, tt.ext)
			assert.Equal(t, tt.want, result)
		})
	}
}

func TestScan_EmptyDirectory(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	s := NewScanner(database, []string{dir})
	result, err := s.Scan()
	require.NoError(t, err)
	assert.Equal(t, 0, result.NewGames)
	assert.Equal(t, 0, result.TotalGames)
}

func TestScan_DetectsROMs(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	// Create ROM files in console-named subdirectories
	nesDir := filepath.Join(dir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(nesDir, "Super Mario Bros.nes"), []byte("fake rom"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(nesDir, "Zelda.nes"), []byte("fake rom 2"), 0644))

	snesDir := filepath.Join(dir, "snes")
	require.NoError(t, os.MkdirAll(snesDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(snesDir, "Chrono Trigger.sfc"), []byte("fake snes rom"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan()
	require.NoError(t, err)
	assert.Equal(t, 3, result.NewGames)
	assert.Equal(t, 3, result.TotalGames)

	// Verify games are in the database
	var games []db.Game
	database.Find(&games)
	assert.Len(t, games, 3)

	// Verify console assignment
	var nesGames []db.Game
	var nesConsole db.Console
	database.Where("abbreviation = ?", "NES").First(&nesConsole)
	database.Where("console_id = ?", nesConsole.ID).Find(&nesGames)
	assert.Len(t, nesGames, 2)
}

func TestScan_RescanDoesNotDuplicate(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	nesDir := filepath.Join(dir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(nesDir, "Mario.nes"), []byte("rom"), 0644))

	s := NewScanner(database, []string{dir})

	result1, err := s.Scan()
	require.NoError(t, err)
	assert.Equal(t, 1, result1.NewGames)

	result2, err := s.Scan()
	require.NoError(t, err)
	assert.Equal(t, 0, result2.NewGames)
	assert.Equal(t, 1, result2.TotalGames)
}

// .bin files in a non-console-named directory are ambiguous (could be Genesis,
// PSX, Saturn, etc.) so they are correctly skipped — the user must place them
// in a console-named directory for identification.
func TestIdentifyConsole_BinAmbiguity(t *testing.T) {
	s := &Scanner{}

	result := s.identifyConsole("/games/final-fantasy-vii/disc1.bin", ".bin")
	assert.Equal(t, "", result, ".bin in unknown dir is ambiguous and skipped")
}

// .cue files map to PSX. Companion .bin files in a non-console directory are
// ambiguous and skipped, which avoids the old bug of misidentifying them as Genesis.
func TestIdentifyConsole_CueBinMismatch(t *testing.T) {
	s := &Scanner{}

	cueResult := s.identifyConsole("/games/ff7/disc1.cue", ".cue")
	assert.Equal(t, "PSX", cueResult)

	binResult := s.identifyConsole("/games/ff7/disc1.bin", ".bin")
	assert.Equal(t, "", binResult, ".bin companion in unknown dir is skipped (ambiguous)")
}

// BUG: .iso files are not in consoleExtMap, so games in generic directories
// are silently ignored during scan.
func TestIdentifyConsole_IsoNotDetected(t *testing.T) {
	s := &Scanner{}
	result := s.identifyConsole("/games/roms/game.iso", ".iso")
	assert.Equal(t, "", result, ".iso not in consoleExtMap - games are silently skipped")
}

// BUG: .zip files (Neo Geo / Arcade) are not in consoleExtMap either.
func TestIdentifyConsole_ZipNotDetected(t *testing.T) {
	s := &Scanner{}
	result := s.identifyConsole("/games/roms/game.zip", ".zip")
	assert.Equal(t, "", result, ".zip not in consoleExtMap - Neo Geo/Arcade need directory hint")
}

func TestScan_IgnoresNonROMFiles(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	nesDir := filepath.Join(dir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(nesDir, "Mario.nes"), []byte("rom"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(nesDir, "readme.txt"), []byte("not a rom"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(nesDir, "cover.jpg"), []byte("image"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(nesDir, "game.nfo"), []byte("info"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan()
	require.NoError(t, err)
	assert.Equal(t, 1, result.NewGames)
	assert.Equal(t, 1, result.TotalGames)
}

func TestCreateConsoleFolders_CreatesExpectedDirs(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	err := CreateConsoleFolders(database, []string{dir})
	require.NoError(t, err)

	expectedFolders := []string{
		"nes", "snes", "gb", "gbc", "gba", "n64", "nds",
		"mastersystem", "genesis", "saturn", "psx", "psp",
		"neogeo", "arcade", "tg16", "atari2600",
	}
	for _, folder := range expectedFolders {
		info, err := os.Stat(filepath.Join(dir, folder))
		require.NoError(t, err, "folder %s should exist", folder)
		assert.True(t, info.IsDir(), "folder %s should be a directory", folder)
	}
}

func TestCreateConsoleFolders_MultipleGameDirs(t *testing.T) {
	database := setupTestDB(t)
	dir1 := t.TempDir()
	dir2 := t.TempDir()

	err := CreateConsoleFolders(database, []string{dir1, dir2})
	require.NoError(t, err)

	for _, dir := range []string{dir1, dir2} {
		info, err := os.Stat(filepath.Join(dir, "nes"))
		require.NoError(t, err, "nes folder should exist in %s", dir)
		assert.True(t, info.IsDir())

		info, err = os.Stat(filepath.Join(dir, "snes"))
		require.NoError(t, err, "snes folder should exist in %s", dir)
		assert.True(t, info.IsDir())
	}
}

func TestCreateConsoleFolders_IdempotentWithExistingFiles(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	// Pre-create a console folder with a file inside
	nesDir := filepath.Join(dir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0755))
	romPath := filepath.Join(nesDir, "Mario.nes")
	require.NoError(t, os.WriteFile(romPath, []byte("fake rom"), 0644))

	// Run CreateConsoleFolders — should not destroy existing content
	err := CreateConsoleFolders(database, []string{dir})
	require.NoError(t, err)

	// Verify existing file is untouched
	data, err := os.ReadFile(romPath)
	require.NoError(t, err)
	assert.Equal(t, "fake rom", string(data))

	// Verify other folders were also created
	info, err := os.Stat(filepath.Join(dir, "snes"))
	require.NoError(t, err)
	assert.True(t, info.IsDir())
}

func TestScan_RemovesMissingGames(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	nesDir := filepath.Join(dir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0755))
	romPath := filepath.Join(nesDir, "Temp.nes")
	require.NoError(t, os.WriteFile(romPath, []byte("rom"), 0644))

	s := NewScanner(database, []string{dir})
	_, err := s.Scan()
	require.NoError(t, err)

	// Delete the ROM file
	require.NoError(t, os.Remove(romPath))

	result, err := s.Scan()
	require.NoError(t, err)
	assert.Equal(t, 1, result.RemovedGames)
	assert.Equal(t, 0, result.TotalGames)
}
