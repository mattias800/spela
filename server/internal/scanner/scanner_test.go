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

// BUG: .bin files in a non-console-named directory default to Genesis (GEN)
// via consoleExtMap. But .bin is valid for PSX and Saturn too.
func TestIdentifyConsole_BinAmbiguity(t *testing.T) {
	s := &Scanner{}

	// A PSX .bin file in a generic directory is misidentified as Genesis
	result := s.identifyConsole("/games/final-fantasy-vii/disc1.bin", ".bin")
	// Documenting the bug: this returns "GEN" (Genesis) instead of "PSX"
	assert.Equal(t, "GEN", result, ".bin in unknown dir maps to GEN - known ambiguity")
}

// BUG: .cue files map to PSX but companion .bin files map to GEN.
// A PSX game with disc1.cue and disc1.bin creates two separate game entries.
func TestIdentifyConsole_CueBinMismatch(t *testing.T) {
	s := &Scanner{}

	cueResult := s.identifyConsole("/games/ff7/disc1.cue", ".cue")
	assert.Equal(t, "PSX", cueResult)

	binResult := s.identifyConsole("/games/ff7/disc1.bin", ".bin")
	// BUG: companion .bin maps to Genesis, not PSX
	assert.Equal(t, "GEN", binResult, ".bin companion to .cue maps to GEN, not PSX")
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
