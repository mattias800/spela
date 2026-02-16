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
	err = database.AutoMigrate(&db.User{}, &db.Console{}, &db.Game{}, &db.GameDisc{}, &db.SaveState{})
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

func TestScanSkipsBiosDirectory(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	// Create a valid NES ROM in nes/ directory
	nesDir := filepath.Join(dir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(nesDir, "Mario.nes"), []byte("rom"), 0644))

	// Create a BIOS subdirectory with a .bin file that could match a console
	biosDir := filepath.Join(dir, "bios")
	require.NoError(t, os.MkdirAll(biosDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(biosDir, "scph5501.bin"), []byte("bios data"), 0644))

	// Also test case-insensitivity: create a "BIOS" directory
	biosUpper := filepath.Join(dir, "BIOS")
	// On case-insensitive filesystems this may be the same dir, so skip if it already exists
	if _, err := os.Stat(biosUpper); os.IsNotExist(err) {
		require.NoError(t, os.MkdirAll(biosUpper, 0755))
		require.NoError(t, os.WriteFile(filepath.Join(biosUpper, "gba_bios.bin"), []byte("gba bios"), 0644))
	}

	s := NewScanner(database, []string{dir})
	result, err := s.Scan()
	require.NoError(t, err)

	// Only the NES ROM should be detected, not BIOS files
	assert.Equal(t, 1, result.NewGames)
	assert.Equal(t, 1, result.TotalGames)
}

func TestScanRejectsWrongExtensionInConsoleDir(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	// NES console only supports .nes and .fds extensions
	nesDir := filepath.Join(dir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0755))

	// Valid NES ROM
	require.NoError(t, os.WriteFile(filepath.Join(nesDir, "Mario.nes"), []byte("rom"), 0644))
	// Valid FDS ROM
	require.NoError(t, os.WriteFile(filepath.Join(nesDir, "DiskSystem.fds"), []byte("fds rom"), 0644))
	// Invalid: .bin is not a valid NES extension
	require.NoError(t, os.WriteFile(filepath.Join(nesDir, "wrong.bin"), []byte("not nes"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan()
	require.NoError(t, err)

	// Only .nes and .fds should be detected, not .bin
	assert.Equal(t, 2, result.NewGames)
	assert.Equal(t, 2, result.TotalGames)
}

func TestConsoleHasExtension(t *testing.T) {
	tests := []struct {
		name       string
		extensions string
		ext        string
		want       bool
	}{
		{"exact match", ".nes,.fds", ".nes", true},
		{"second extension", ".nes,.fds", ".fds", true},
		{"no match", ".nes,.fds", ".bin", false},
		{"single extension", ".gb", ".gb", true},
		{"single no match", ".gb", ".gbc", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			console := &db.Console{Extensions: tt.extensions}
			result := consoleHasExtension(console, tt.ext)
			assert.Equal(t, tt.want, result)
		})
	}
}

func TestParseM3U(t *testing.T) {
	dir := t.TempDir()
	m3uPath := filepath.Join(dir, "game.m3u")
	content := "disc1.cue\n\ndisc2.cue\n# comment\n"
	require.NoError(t, os.WriteFile(m3uPath, []byte(content), 0644))

	paths, err := parseM3U(m3uPath)
	require.NoError(t, err)
	assert.Len(t, paths, 2)
	assert.Equal(t, filepath.Join(dir, "disc1.cue"), paths[0])
	assert.Equal(t, filepath.Join(dir, "disc2.cue"), paths[1])
}

func TestDiscPattern(t *testing.T) {
	tests := []struct {
		name    string
		input   string
		matches bool
	}{
		{"Disc in parens", "Final Fantasy VII (Disc 1).cue", true},
		{"Disk in parens", "Game (Disk 2).cue", true},
		{"CD in brackets", "Game [CD 3].iso", true},
		{"disc lowercase", "Game (disc 1).cue", true},
		{"no disc marker", "Game.cue", false},
		{"USA region tag", "Game (USA).cue", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := discPattern.MatchString(tt.input)
			assert.Equal(t, tt.matches, result)
		})
	}
}

func TestDiscCompanionFiles_CueBin(t *testing.T) {
	dir := t.TempDir()
	binPath := filepath.Join(dir, "game.bin")
	require.NoError(t, os.WriteFile(binPath, []byte("binary data here"), 0644))

	cuePath := filepath.Join(dir, "game.cue")
	cueContent := "FILE \"game.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n"
	require.NoError(t, os.WriteFile(cuePath, []byte(cueContent), 0644))

	files, totalSize, err := DiscCompanionFiles(cuePath)
	require.NoError(t, err)
	assert.Len(t, files, 2)
	assert.Contains(t, files, cuePath)
	assert.Contains(t, files, binPath)

	cueInfo, _ := os.Stat(cuePath)
	binInfo, _ := os.Stat(binPath)
	assert.Equal(t, cueInfo.Size()+binInfo.Size(), totalSize)
}

func TestDiscCompanionFiles_SingleFile(t *testing.T) {
	dir := t.TempDir()
	isoPath := filepath.Join(dir, "game.iso")
	require.NoError(t, os.WriteFile(isoPath, []byte("iso data"), 0644))

	files, totalSize, err := DiscCompanionFiles(isoPath)
	require.NoError(t, err)
	assert.Len(t, files, 1)
	assert.Equal(t, isoPath, files[0])

	info, _ := os.Stat(isoPath)
	assert.Equal(t, info.Size(), totalSize)
}

func TestScan_M3UMultiDisc(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	// Create disc files
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc1.bin"), []byte("bin1"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc1.cue"), []byte("FILE \"disc1.bin\" BINARY\n"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc2.bin"), []byte("bin2data"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc2.cue"), []byte("FILE \"disc2.bin\" BINARY\n"), 0644))

	// Create .m3u file
	m3uContent := "disc1.cue\ndisc2.cue\n"
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "Final Fantasy VII.m3u"), []byte(m3uContent), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan()
	require.NoError(t, err)

	assert.Equal(t, 1, result.NewGames, "should create exactly 1 game")
	assert.Equal(t, 1, result.TotalGames)

	var game db.Game
	require.NoError(t, database.First(&game).Error)
	assert.Equal(t, "Final Fantasy VII", game.Title)
	assert.Equal(t, 2, game.DiscCount)

	var discs []db.GameDisc
	database.Where("game_id = ?", game.ID).Order("disc_number").Find(&discs)
	assert.Len(t, discs, 2)
	assert.Equal(t, 1, discs[0].DiscNumber)
	assert.Equal(t, 2, discs[1].DiscNumber)
}

func TestScan_PatternMultiDisc(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	// Create disc 1 — companion .bin files use plain names (no disc marker)
	// to avoid being independently matched by the disc pattern regex.
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "game_d1.bin"), []byte("bin1"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "Game (Disc 1).cue"), []byte("FILE \"game_d1.bin\" BINARY\n"), 0644))

	// Create disc 2
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "game_d2.bin"), []byte("bin2"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "Game (Disc 2).cue"), []byte("FILE \"game_d2.bin\" BINARY\n"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan()
	require.NoError(t, err)

	assert.Equal(t, 1, result.NewGames, "should create exactly 1 game from disc pattern")
	assert.Equal(t, 1, result.TotalGames)

	// Verify an .m3u file was auto-generated
	m3uPath := filepath.Join(psxDir, "Game.m3u")
	_, err = os.Stat(m3uPath)
	assert.NoError(t, err, "auto-generated .m3u file should exist")

	var game db.Game
	require.NoError(t, database.First(&game).Error)
	assert.Equal(t, 2, game.DiscCount)
}

func TestScan_SingleDiscNotGrouped(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	// Use a single .iso file to avoid the .cue/.bin double-detection issue
	// (both .cue and .bin are valid PSX extensions, so they each create a game)
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "Crash Bandicoot.iso"), []byte("iso data"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan()
	require.NoError(t, err)

	var games []db.Game
	database.Find(&games)
	require.Len(t, games, 1)
	assert.Equal(t, 0, games[0].DiscCount, "single disc game should have DiscCount 0")

	var discs []db.GameDisc
	database.Where("game_id = ?", games[0].ID).Find(&discs)
	assert.Len(t, discs, 0, "single disc game should have no GameDisc records")

	assert.Equal(t, 1, result.TotalGames)
}

func TestScan_M3UClaimsFiles(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	// Create disc files
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc1.bin"), []byte("bin1"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc1.cue"), []byte("FILE \"disc1.bin\" BINARY\n"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc2.bin"), []byte("bin2"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc2.cue"), []byte("FILE \"disc2.bin\" BINARY\n"), 0644))

	// Create .m3u referencing the discs
	m3uContent := "disc1.cue\ndisc2.cue\n"
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "MyGame.m3u"), []byte(m3uContent), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan()
	require.NoError(t, err)

	// The .cue files should be claimed by the .m3u, not creating separate games
	assert.Equal(t, 1, result.TotalGames, "m3u should claim disc files, preventing separate game entries")
}

func TestScan_RescanUpgradesOldEntries(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	// Create disc files on disk
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc1.bin"), []byte("bin1"), 0644))
	cue1Path := filepath.Join(psxDir, "Metal Gear Solid (Disc 1).cue")
	require.NoError(t, os.WriteFile(cue1Path, []byte("FILE \"disc1.bin\" BINARY\n"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc2.bin"), []byte("bin2"), 0644))
	cue2Path := filepath.Join(psxDir, "Metal Gear Solid (Disc 2).cue")
	require.NoError(t, os.WriteFile(cue2Path, []byte("FILE \"disc2.bin\" BINARY\n"), 0644))

	// Simulate pre-multi-disc scanner: manually insert standalone Game records
	// for each disc file, as the old scanner would have done
	var psxConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "PSX").First(&psxConsole).Error)

	oldGame1 := db.Game{
		ConsoleID: psxConsole.ID,
		Title:     "Metal Gear Solid",
		FileName:  "Metal Gear Solid (Disc 1).cue",
		FilePath:  cue1Path,
		FileSize:  100,
	}
	require.NoError(t, database.Create(&oldGame1).Error)

	oldGame2 := db.Game{
		ConsoleID: psxConsole.ID,
		Title:     "Metal Gear Solid",
		FileName:  "Metal Gear Solid (Disc 2).cue",
		FilePath:  cue2Path,
		FileSize:  100,
	}
	require.NoError(t, database.Create(&oldGame2).Error)

	// Verify 2 old standalone games exist
	var count int64
	database.Model(&db.Game{}).Count(&count)
	require.Equal(t, int64(2), count, "should have 2 old standalone games before scan")

	// Now scan — disc pattern detects the group, creates multi-disc game,
	// and should remove the old standalone entries
	s := NewScanner(database, []string{dir})
	result, err := s.Scan()
	require.NoError(t, err)

	var games []db.Game
	database.Find(&games)
	assert.Len(t, games, 1, "only the multi-disc game should remain after scan")
	assert.Equal(t, 2, games[0].DiscCount, "remaining game should be multi-disc")
	assert.Equal(t, 1, result.NewGames, "should have created 1 new multi-disc game")
	assert.Equal(t, 2, result.RemovedGames, "should have removed 2 old standalone entries")
	assert.Equal(t, 1, result.TotalGames)
}

func TestScan_RescanIdempotent_MultiDisc(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc1.bin"), []byte("bin1"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc1.cue"), []byte("FILE \"disc1.bin\" BINARY\n"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc2.bin"), []byte("bin2"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc2.cue"), []byte("FILE \"disc2.bin\" BINARY\n"), 0644))

	m3uContent := "disc1.cue\ndisc2.cue\n"
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "MultiDiscGame.m3u"), []byte(m3uContent), 0644))

	s := NewScanner(database, []string{dir})

	result1, err := s.Scan()
	require.NoError(t, err)
	assert.Equal(t, 1, result1.NewGames)
	assert.Equal(t, 1, result1.TotalGames)

	result2, err := s.Scan()
	require.NoError(t, err)
	assert.Equal(t, 0, result2.NewGames, "rescan should not create duplicates")
	assert.Equal(t, 1, result2.TotalGames)

	var games []db.Game
	database.Find(&games)
	assert.Len(t, games, 1, "only 1 game should exist after two scans")
}
