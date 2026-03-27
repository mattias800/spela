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
	err = database.AutoMigrate(&db.User{}, &db.Console{}, &db.Game{}, &db.GameDisc{})
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
			result := GameTitle(tt.filename)
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
	result, err := s.Scan(nil)
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
	result, err := s.Scan(nil)
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

	result1, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, result1.NewGames)

	result2, err := s.Scan(nil)
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
	result, err := s.Scan(nil)
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
		"msx1", "msx2",
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
	_, err := s.Scan(nil)
	require.NoError(t, err)

	// Delete the ROM file
	require.NoError(t, os.Remove(romPath))

	result, err := s.Scan(nil)
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
	result, err := s.Scan(nil)
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
	result, err := s.Scan(nil)
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
	result, err := s.Scan(nil)
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
	result, err := s.Scan(nil)
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
	result, err := s.Scan(nil)
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
	result, err := s.Scan(nil)
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
		FilePath:  filepath.Join("psx", "Metal Gear Solid (Disc 1).cue"),
		FileSize:  100,
	}
	require.NoError(t, database.Create(&oldGame1).Error)

	oldGame2 := db.Game{
		ConsoleID: psxConsole.ID,
		Title:     "Metal Gear Solid",
		FileName:  "Metal Gear Solid (Disc 2).cue",
		FilePath:  filepath.Join("psx", "Metal Gear Solid (Disc 2).cue"),
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
	result, err := s.Scan(nil)
	require.NoError(t, err)

	var games []db.Game
	database.Find(&games)
	assert.Len(t, games, 1, "only the multi-disc game should remain after scan")
	assert.Equal(t, 2, games[0].DiscCount, "remaining game should be multi-disc")
	assert.Equal(t, 1, result.NewGames, "should have created 1 new multi-disc game")
	assert.Equal(t, 2, result.RemovedGames, "should have removed 2 old standalone entries")
	assert.Equal(t, 1, result.TotalGames)
}

func TestScan_PSPCHDSetsAchievementsWarning(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	pspDir := filepath.Join(dir, "psp")
	require.NoError(t, os.MkdirAll(pspDir, 0755))

	// Create a CHD file with no CD metadata tags (createdvd mode)
	writeCHDV5(t, pspDir, "God of War.chd", [][4]byte{
		{'R', 'A', 'W', 'H'}, // non-CD tag → createdvd
	})

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, result.NewGames)

	var game db.Game
	require.NoError(t, database.Where("file_path = ?", filepath.Join("psp", "God of War.chd")).First(&game).Error)
	assert.NotEmpty(t, game.AchievementsWarning, "PSP CHD with createdvd should have a warning")
	assert.Contains(t, game.AchievementsWarning, "createdvd")
}

func TestScan_PSPCHDCreateCDNoWarning(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	pspDir := filepath.Join(dir, "psp")
	require.NoError(t, os.MkdirAll(pspDir, 0755))

	// Create a CHD file with CD metadata tags (createcd mode)
	writeCHDV5(t, pspDir, "Lumines.chd", [][4]byte{
		{'C', 'H', 'T', '2'}, // CD track v2 → createcd
	})

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, result.NewGames)

	var game db.Game
	require.NoError(t, database.Where("file_path = ?", filepath.Join("psp", "Lumines.chd")).First(&game).Error)
	assert.Empty(t, game.AchievementsWarning, "PSP CHD with createcd should have no warning")
}

func TestScan_PSPISONoWarning(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	pspDir := filepath.Join(dir, "psp")
	require.NoError(t, os.MkdirAll(pspDir, 0755))

	require.NoError(t, os.WriteFile(filepath.Join(pspDir, "Crisis Core.iso"), []byte("fake iso"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, result.NewGames)

	var game db.Game
	require.NoError(t, database.Where("file_path = ?", filepath.Join("psp", "Crisis Core.iso")).First(&game).Error)
	assert.Empty(t, game.AchievementsWarning, "PSP ISO should have no warning")
}

func TestScan_PSPCSOSetsWarning(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	pspDir := filepath.Join(dir, "psp")
	require.NoError(t, os.MkdirAll(pspDir, 0755))

	require.NoError(t, os.WriteFile(filepath.Join(pspDir, "Patapon.cso"), []byte("fake cso"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, result.NewGames)

	var game db.Game
	require.NoError(t, database.Where("file_path = ?", filepath.Join("psp", "Patapon.cso")).First(&game).Error)
	assert.NotEmpty(t, game.AchievementsWarning, "PSP CSO should have a warning")
	assert.Contains(t, game.AchievementsWarning, "CSO")
}

func TestScan_NonPSPCHDNoWarning(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	// Place a createdvd CHD in the PSP directory but verify non-PSP consoles
	// don't get warnings. We test this by manually creating a game in a
	// non-PSP console and checking the warning function directly,
	// since CHD in non-PSP dirs may not be scanned depending on extension support.
	pspDir := filepath.Join(dir, "psp")
	require.NoError(t, os.MkdirAll(pspDir, 0755))

	// A createcd CHD in PSP dir — should NOT get a warning
	chdPath := writeCHDV5(t, pspDir, "GoodGame.chd", [][4]byte{
		{'C', 'H', 'T', 'R'}, // CD metadata tag → createcd
	})

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, result.NewGames)

	var game db.Game
	require.NoError(t, database.Where("file_path = ?", filepath.Join("psp", "GoodGame.chd")).First(&game).Error)
	assert.Empty(t, game.AchievementsWarning, "PSP CHD with createcd mode should have no warning")

	// Also verify the unit-level function: non-PSP consoles never get warnings
	assert.Empty(t, PSPCHDAchievementsWarning(chdPath, "PSX"), "PSX console should never get PSP CHD warning")
	assert.Empty(t, PSPCHDAchievementsWarning(chdPath, "SAT"), "SAT console should never get PSP CHD warning")
}

// When a multi-disc game exists in the DB but has no GameDisc records
// (e.g., created before multi-disc support), a rescan should backfill them.
func TestScan_RescanCreatesDiscRecordsForExistingGame(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	// Create disc files on disk
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc1.bin"), []byte("bin1data"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc1.cue"), []byte("FILE \"disc1.bin\" BINARY\n"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc2.bin"), []byte("bin2data"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "disc2.cue"), []byte("FILE \"disc2.bin\" BINARY\n"), 0644))

	// Create .m3u file
	m3uContent := "disc1.cue\ndisc2.cue\n"
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "Metal Gear Solid.m3u"), []byte(m3uContent), 0644))

	// Manually insert the game WITHOUT disc records — simulates a game
	// created before multi-disc support or with an interrupted scan
	var psxConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "PSX").First(&psxConsole).Error)

	existingGame := db.Game{
		ConsoleID: psxConsole.ID,
		Title:     "Metal Gear Solid",
		FileName:  "Metal Gear Solid.m3u",
		FilePath:  filepath.Join("psx", "Metal Gear Solid.m3u"),
		FileSize:  0,
		DiscCount: 0,
	}
	require.NoError(t, database.Create(&existingGame).Error)

	// Verify: game exists, no disc records
	var discCount int64
	database.Model(&db.GameDisc{}).Where("game_id = ?", existingGame.ID).Count(&discCount)
	require.Equal(t, int64(0), discCount, "should have no disc records before scan")

	// Scan — should detect the existing game and backfill disc records
	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)

	assert.Equal(t, 0, result.NewGames, "should not create a new game")
	assert.Equal(t, 1, result.TotalGames)

	// Verify disc records were created
	var discs []db.GameDisc
	database.Where("game_id = ?", existingGame.ID).Order("disc_number").Find(&discs)
	assert.Len(t, discs, 2, "should have 2 disc records after scan")
	assert.Equal(t, 1, discs[0].DiscNumber)
	assert.Equal(t, "disc1.cue", discs[0].FileName)
	assert.Equal(t, 2, discs[1].DiscNumber)
	assert.Equal(t, "disc2.cue", discs[1].FileName)

	// Verify disc count and file size were updated
	var updatedGame db.Game
	database.First(&updatedGame, existingGame.ID)
	assert.Equal(t, 2, updatedGame.DiscCount, "disc count should be updated")
	assert.Greater(t, updatedGame.FileSize, int64(0), "file size should be updated")

	// Rescan should be idempotent — no duplicate disc records
	_, err = s.Scan(nil)
	require.NoError(t, err)

	var discsAfterRescan []db.GameDisc
	database.Where("game_id = ?", existingGame.ID).Find(&discsAfterRescan)
	assert.Len(t, discsAfterRescan, 2, "rescan should not create duplicate disc records")
}

// Scanning must be deterministic: the end state depends only on what files
// are on disk, not on previous DB state. This test verifies the full cycle:
// scan → remove files → scan (removes) → restore files → scan (recreates).
func TestScan_Deterministic_RemoveAndRestore(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	nesDir := filepath.Join(dir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0755))
	romPath := filepath.Join(nesDir, "Mario.nes")
	require.NoError(t, os.WriteFile(romPath, []byte("rom data"), 0644))

	s := NewScanner(database, []string{dir})

	// Scan 1: game appears
	r1, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, r1.NewGames)
	assert.Equal(t, 1, r1.TotalGames)

	var game1 db.Game
	require.NoError(t, database.Where("file_path = ?", filepath.Join("nes", "Mario.nes")).First(&game1).Error)

	// Remove the file
	require.NoError(t, os.Remove(romPath))

	// Scan 2: game is removed
	r2, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, r2.RemovedGames)
	assert.Equal(t, 0, r2.TotalGames)

	// Game should be fully gone (hard-deleted), not just soft-deleted
	var ghostCount int64
	database.Unscoped().Model(&db.Game{}).Where("file_path = ?", filepath.Join("nes", "Mario.nes")).Count(&ghostCount)
	assert.Equal(t, int64(0), ghostCount, "game should be hard-deleted, not soft-deleted")

	// Restore the file
	require.NoError(t, os.WriteFile(romPath, []byte("rom data"), 0644))

	// Scan 3: game reappears as new
	r3, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, r3.NewGames)
	assert.Equal(t, 1, r3.TotalGames)

	var game3 db.Game
	require.NoError(t, database.Where("file_path = ?", filepath.Join("nes", "Mario.nes")).First(&game3).Error)
	assert.Equal(t, "Mario", game3.Title)
}

// Same determinism test for multi-disc games.
func TestScan_Deterministic_MultiDisc_RemoveAndRestore(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	disc1Bin := filepath.Join(psxDir, "disc1.bin")
	disc1Cue := filepath.Join(psxDir, "disc1.cue")
	disc2Bin := filepath.Join(psxDir, "disc2.bin")
	disc2Cue := filepath.Join(psxDir, "disc2.cue")
	m3uPath := filepath.Join(psxDir, "FF7.m3u")

	writeDiscFiles := func() {
		require.NoError(t, os.WriteFile(disc1Bin, []byte("bin1"), 0644))
		require.NoError(t, os.WriteFile(disc1Cue, []byte("FILE \"disc1.bin\" BINARY\n"), 0644))
		require.NoError(t, os.WriteFile(disc2Bin, []byte("bin2data"), 0644))
		require.NoError(t, os.WriteFile(disc2Cue, []byte("FILE \"disc2.bin\" BINARY\n"), 0644))
		require.NoError(t, os.WriteFile(m3uPath, []byte("disc1.cue\ndisc2.cue\n"), 0644))
	}

	removeDiscFiles := func() {
		os.Remove(disc1Bin)
		os.Remove(disc1Cue)
		os.Remove(disc2Bin)
		os.Remove(disc2Cue)
		os.Remove(m3uPath)
	}

	writeDiscFiles()
	s := NewScanner(database, []string{dir})

	// Scan 1: game + discs created
	r1, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, r1.NewGames)

	var game1 db.Game
	require.NoError(t, database.First(&game1).Error)
	var discs1 []db.GameDisc
	database.Where("game_id = ?", game1.ID).Find(&discs1)
	assert.Len(t, discs1, 2)

	// Remove all files
	removeDiscFiles()

	// Scan 2: game removed
	r2, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, r2.RemovedGames)
	assert.Equal(t, 0, r2.TotalGames)

	// Both game and disc records should be hard-deleted
	var ghostGames, ghostDiscs int64
	database.Unscoped().Model(&db.Game{}).Count(&ghostGames)
	database.Unscoped().Model(&db.GameDisc{}).Count(&ghostDiscs)
	assert.Equal(t, int64(0), ghostGames, "game should be hard-deleted")
	assert.Equal(t, int64(0), ghostDiscs, "disc records should be hard-deleted")

	// Restore files
	writeDiscFiles()

	// Scan 3: game + discs recreated
	r3, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, r3.NewGames)
	assert.Equal(t, 1, r3.TotalGames)

	var game3 db.Game
	require.NoError(t, database.First(&game3).Error)
	assert.Equal(t, 2, game3.DiscCount)

	var discs3 []db.GameDisc
	database.Where("game_id = ?", game3.ID).Order("disc_number").Find(&discs3)
	assert.Len(t, discs3, 2, "disc records should be recreated")
	assert.Equal(t, 1, discs3[0].DiscNumber)
	assert.Equal(t, 2, discs3[1].DiscNumber)
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

	result1, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, result1.NewGames)
	assert.Equal(t, 1, result1.TotalGames)

	result2, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 0, result2.NewGames, "rescan should not create duplicates")
	assert.Equal(t, 1, result2.TotalGames)

	var games []db.Game
	database.Find(&games)
	assert.Len(t, games, 1, "only 1 game should exist after two scans")
}

// Standalone .cue+.bin files (no .m3u, no disc pattern) should create a single
// game entry with DiscCount=1 and a GameDisc record. The companion .bin file
// must NOT create a separate game entry.
func TestScan_StandaloneCueBin(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	// Create a standalone .cue + .bin pair (single disc, no disc pattern)
	binPath := filepath.Join(psxDir, "Crash Bandicoot.bin")
	require.NoError(t, os.WriteFile(binPath, []byte("binary game data"), 0644))

	cuePath := filepath.Join(psxDir, "Crash Bandicoot.cue")
	cueContent := "FILE \"Crash Bandicoot.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n"
	require.NoError(t, os.WriteFile(cuePath, []byte(cueContent), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)

	// Should create exactly 1 game (the .cue), NOT 2 (one for .cue, one for .bin)
	assert.Equal(t, 1, result.NewGames, "should create exactly 1 game for standalone .cue+.bin")
	assert.Equal(t, 1, result.TotalGames)

	var games []db.Game
	database.Find(&games)
	require.Len(t, games, 1)
	assert.Equal(t, "Crash Bandicoot", games[0].Title)
	assert.Equal(t, 1, games[0].DiscCount)
	assert.Greater(t, games[0].FileSize, int64(0), "file size should include .cue + .bin")

	// Verify a GameDisc record was created
	var discs []db.GameDisc
	database.Where("game_id = ?", games[0].ID).Find(&discs)
	require.Len(t, discs, 1, "should have 1 disc record")
	assert.Equal(t, 1, discs[0].DiscNumber)
	assert.Equal(t, "Crash Bandicoot.cue", discs[0].FileName)

	// Rescan should be idempotent
	result2, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 0, result2.NewGames, "rescan should not create duplicates")
	assert.Equal(t, 1, result2.TotalGames)
}

// When a standalone .cue+.bin game existed as a .bin-only entry (from a prior scan),
// the new scan should remove the orphaned .bin entry and create the .cue game.
func TestScan_StandaloneCueBin_CleansUpOldBinEntry(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	binPath := filepath.Join(psxDir, "Crash Bandicoot.bin")
	require.NoError(t, os.WriteFile(binPath, []byte("binary game data"), 0644))

	cuePath := filepath.Join(psxDir, "Crash Bandicoot.cue")
	cueContent := "FILE \"Crash Bandicoot.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n"
	require.NoError(t, os.WriteFile(cuePath, []byte(cueContent), 0644))

	// Simulate an old scanner that created a standalone .bin entry
	var psxConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "PSX").First(&psxConsole).Error)

	oldBinGame := db.Game{
		ConsoleID: psxConsole.ID,
		Title:     "Crash Bandicoot",
		FileName:  "Crash Bandicoot.bin",
		FilePath:  filepath.Join("psx", "Crash Bandicoot.bin"),
		FileSize:  100,
	}
	require.NoError(t, database.Create(&oldBinGame).Error)

	// Verify old entry exists
	var countBefore int64
	database.Model(&db.Game{}).Count(&countBefore)
	require.Equal(t, int64(1), countBefore)

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)

	// The old .bin entry should be removed and replaced by the .cue entry
	var games []db.Game
	database.Find(&games)
	require.Len(t, games, 1, "should have exactly 1 game after cleanup")
	assert.Equal(t, "Crash Bandicoot.cue", games[0].FileName, "remaining game should be the .cue")
	assert.Equal(t, 1, games[0].DiscCount)
	assert.Equal(t, 1, result.NewGames)
	assert.Equal(t, 1, result.RemovedGames, "old .bin entry should be removed")
}

// When both a .cue and .bin entry exist from a prior scan, the .bin entry should
// be removed even though the .cue entry already exists (no new game created).
func TestScan_StandaloneCueBin_CleansUpBinWhenCueExists(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	binPath := filepath.Join(psxDir, "SSX.bin")
	require.NoError(t, os.WriteFile(binPath, []byte("binary game data"), 0644))

	cuePath := filepath.Join(psxDir, "SSX.cue")
	cueContent := "FILE \"SSX.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n"
	require.NoError(t, os.WriteFile(cuePath, []byte(cueContent), 0644))

	// Simulate old scanner: created BOTH a .cue entry and a .bin entry
	var psxConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "PSX").First(&psxConsole).Error)

	oldCueGame := db.Game{
		ConsoleID: psxConsole.ID,
		Title:     "SSX",
		FileName:  "SSX.cue",
		FilePath:  filepath.Join("psx", "SSX.cue"),
		FileSize:  50,
	}
	require.NoError(t, database.Create(&oldCueGame).Error)

	oldBinGame := db.Game{
		ConsoleID: psxConsole.ID,
		Title:     "SSX",
		FileName:  "SSX.bin",
		FilePath:  filepath.Join("psx", "SSX.bin"),
		FileSize:  100,
	}
	require.NoError(t, database.Create(&oldBinGame).Error)

	var countBefore int64
	database.Model(&db.Game{}).Count(&countBefore)
	require.Equal(t, int64(2), countBefore)

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)

	// Only the .cue game should remain; the .bin entry should be cleaned up
	var games []db.Game
	database.Find(&games)
	require.Len(t, games, 1, "should have exactly 1 game after cleanup")
	assert.Equal(t, "SSX.cue", games[0].FileName, "remaining game should be the .cue")
	assert.Equal(t, 0, result.NewGames, "no new game created — .cue already existed")
	assert.Equal(t, 1, result.RemovedGames, "old .bin entry should be removed")
}

// Standalone .cue game that already exists in DB (from prior scan) should get
// disc records backfilled.
func TestScan_StandaloneCueBin_BackfillDiscRecord(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	binPath := filepath.Join(psxDir, "Crash.bin")
	require.NoError(t, os.WriteFile(binPath, []byte("binary data"), 0644))

	cuePath := filepath.Join(psxDir, "Crash.cue")
	cueContent := "FILE \"Crash.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n"
	require.NoError(t, os.WriteFile(cuePath, []byte(cueContent), 0644))

	// Manually insert game WITHOUT disc record — simulates old scan
	var psxConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "PSX").First(&psxConsole).Error)

	existingGame := db.Game{
		ConsoleID: psxConsole.ID,
		Title:     "Crash",
		FileName:  "Crash.cue",
		FilePath:  filepath.Join("psx", "Crash.cue"),
		FileSize:  0,
		DiscCount: 0,
	}
	require.NoError(t, database.Create(&existingGame).Error)

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)

	assert.Equal(t, 0, result.NewGames, "should not create a new game")
	assert.Equal(t, 1, result.TotalGames)

	// Verify disc record was backfilled
	var discs []db.GameDisc
	database.Where("game_id = ?", existingGame.ID).Find(&discs)
	require.Len(t, discs, 1, "should have backfilled 1 disc record")
	assert.Equal(t, 1, discs[0].DiscNumber)
	assert.Equal(t, "Crash.cue", discs[0].FileName)

	// Verify disc count and file size were updated
	var updatedGame db.Game
	database.First(&updatedGame, existingGame.ID)
	assert.Equal(t, 1, updatedGame.DiscCount)
	assert.Greater(t, updatedGame.FileSize, int64(0))
}

func TestDiscCompanionFiles_Gdi(t *testing.T) {
	dir := t.TempDir()

	// Create track files
	track1 := filepath.Join(dir, "track01.bin")
	require.NoError(t, os.WriteFile(track1, []byte("track 1 data here"), 0644))
	track2 := filepath.Join(dir, "track02.raw")
	require.NoError(t, os.WriteFile(track2, []byte("track 2 raw data here!"), 0644))
	track3 := filepath.Join(dir, "track03.bin")
	require.NoError(t, os.WriteFile(track3, []byte("track 3 data"), 0644))

	// Create .gdi file
	gdiPath := filepath.Join(dir, "game.gdi")
	gdiContent := "3\n1 0 4 2352 track01.bin 0\n2 450 0 2352 track02.raw 0\n3 45000 4 2352 track03.bin 0\n"
	require.NoError(t, os.WriteFile(gdiPath, []byte(gdiContent), 0644))

	files, totalSize, err := DiscCompanionFiles(gdiPath)
	require.NoError(t, err)
	assert.Len(t, files, 4, "should return .gdi + 3 track files")
	assert.Contains(t, files, gdiPath)
	assert.Contains(t, files, track1)
	assert.Contains(t, files, track2)
	assert.Contains(t, files, track3)

	gdiInfo, _ := os.Stat(gdiPath)
	t1Info, _ := os.Stat(track1)
	t2Info, _ := os.Stat(track2)
	t3Info, _ := os.Stat(track3)
	expectedSize := gdiInfo.Size() + t1Info.Size() + t2Info.Size() + t3Info.Size()
	assert.Equal(t, expectedSize, totalSize)
}

// Standalone .gdi with track files in a dreamcast/ dir should create a single
// game entry with DiscCount=1, a GameDisc record, and claim all track files.
func TestScan_StandaloneGdiBin(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	dcDir := filepath.Join(dir, "dreamcast")
	require.NoError(t, os.MkdirAll(dcDir, 0755))

	// Create track files
	require.NoError(t, os.WriteFile(filepath.Join(dcDir, "track01.bin"), []byte("track 1 data"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(dcDir, "track02.raw"), []byte("track 2 data"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(dcDir, "track03.bin"), []byte("track 3 data"), 0644))

	// Create .gdi file
	gdiContent := "3\n1 0 4 2352 track01.bin 0\n2 450 0 2352 track02.raw 0\n3 45000 4 2352 track03.bin 0\n"
	require.NoError(t, os.WriteFile(filepath.Join(dcDir, "Sonic Adventure.gdi"), []byte(gdiContent), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)

	// Should create exactly 1 game — the .gdi — not separate entries for track files
	assert.Equal(t, 1, result.NewGames, "should create exactly 1 game for standalone .gdi")
	assert.Equal(t, 1, result.TotalGames)

	var games []db.Game
	database.Find(&games)
	require.Len(t, games, 1)
	assert.Equal(t, "Sonic Adventure", games[0].Title)
	assert.Equal(t, "Sonic Adventure.gdi", games[0].FileName)
	assert.Equal(t, 1, games[0].DiscCount)
	assert.Greater(t, games[0].FileSize, int64(0), "file size should include .gdi + tracks")

	// Verify correct console
	var dcConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "DC").First(&dcConsole).Error)
	assert.Equal(t, dcConsole.ID, games[0].ConsoleID)

	// Verify a GameDisc record was created
	var discs []db.GameDisc
	database.Where("game_id = ?", games[0].ID).Find(&discs)
	require.Len(t, discs, 1, "should have 1 disc record")
	assert.Equal(t, 1, discs[0].DiscNumber)
	assert.Equal(t, "Sonic Adventure.gdi", discs[0].FileName)

	// Rescan should be idempotent
	result2, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 0, result2.NewGames, "rescan should not create duplicates")
	assert.Equal(t, 1, result2.TotalGames)
}

func TestNewConsoleExtensionMappings(t *testing.T) {
	s := &Scanner{}
	tests := []struct {
		name string
		path string
		ext  string
		want string
	}{
		{"X360 by .xex extension", "/games/some/game.xex", ".xex", "X360"},
		{"X360 by .god extension", "/games/some/game.god", ".god", "X360"},
		{"WII by .wbfs extension", "/games/some/game.wbfs", ".wbfs", "WII"},
		{"WIIU by .rpx extension", "/games/some/game.rpx", ".rpx", "WIIU"},
		{"WIIU by .wud extension", "/games/some/game.wud", ".wud", "WIIU"},
		{"WIIU by .wux extension", "/games/some/game.wux", ".wux", "WIIU"},
		{"NSW by .nsp extension", "/games/some/game.nsp", ".nsp", "NSW"},
		{"NSW by .xci extension", "/games/some/game.xci", ".xci", "NSW"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := s.identifyConsole(tt.path, tt.ext)
			assert.Equal(t, tt.want, result)
		})
	}
}

func TestNewRomExtensionsRegistered(t *testing.T) {
	newExts := []string{".pkg", ".xex", ".god", ".wbfs", ".rpx", ".wud", ".wux", ".nsp", ".xci", ".xvd"}
	for _, ext := range newExts {
		assert.True(t, RomExtensions[ext], "RomExtensions should include %s", ext)
	}
}

func TestNewDirectoryConsoleMappings(t *testing.T) {
	s := &Scanner{}
	tests := []struct {
		name string
		path string
		ext  string
		want string
	}{
		{"PS3 by directory", "/games/ps3/game.bin", ".bin", "PS3"},
		{"PS4 by directory", "/games/ps4/game.pkg", ".pkg", "PS4"},
		{"PS5 by directory", "/games/ps5/game.pkg", ".pkg", "PS5"},
		{"X360 by directory", "/games/xbox360/game.iso", ".iso", "X360"},
		{"XONE by directory", "/games/xboxone/game.xvd", ".xvd", "XONE"},
		{"XSX by directory", "/games/xboxseries/game.xvd", ".xvd", "XSX"},
		{"WII by directory", "/games/wii/game.iso", ".iso", "WII"},
		{"WIIU by directory", "/games/wiiu/game.rpx", ".rpx", "WIIU"},
		{"NSW by directory", "/games/switch/game.nsp", ".nsp", "NSW"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := s.identifyConsole(tt.path, tt.ext)
			assert.Equal(t, tt.want, result)
		})
	}
}

func TestScan_NonPlayableConsoles(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	// Create files in non-playable console directories
	ps3Dir := filepath.Join(dir, "ps3")
	require.NoError(t, os.MkdirAll(ps3Dir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(ps3Dir, "The Last of Us.iso"), []byte("fake iso"), 0644))

	switchDir := filepath.Join(dir, "switch")
	require.NoError(t, os.MkdirAll(switchDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(switchDir, "Zelda BOTW.nsp"), []byte("fake nsp"), 0644))

	wiiDir := filepath.Join(dir, "wii")
	require.NoError(t, os.MkdirAll(wiiDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(wiiDir, "Mario Galaxy.wbfs"), []byte("fake wbfs"), 0644))

	x360Dir := filepath.Join(dir, "xbox360")
	require.NoError(t, os.MkdirAll(x360Dir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(x360Dir, "Halo 3.xex"), []byte("fake xex"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 4, result.NewGames)
	assert.Equal(t, 4, result.TotalGames)

	// Verify each game is assigned to the correct console
	verifyConsole := func(abbrev string, expectedCount int) {
		var console db.Console
		require.NoError(t, database.Where("abbreviation = ?", abbrev).First(&console).Error, "console %s should exist", abbrev)
		var count int64
		database.Model(&db.Game{}).Where("console_id = ?", console.ID).Count(&count)
		assert.Equal(t, int64(expectedCount), count, "expected %d game(s) for %s", expectedCount, abbrev)
	}

	verifyConsole("PS3", 1)
	verifyConsole("NSW", 1)
	verifyConsole("WII", 1)
	verifyConsole("X360", 1)
}

func TestIdentifyConsole_MSXExtensions(t *testing.T) {
	s := &Scanner{}
	tests := []struct {
		name string
		path string
		ext  string
		want string
	}{
		{"MSX1 by .mx1 extension", "/games/some/game.mx1", ".mx1", "MSX1"},
		{"MSX2 by .mx2 extension", "/games/some/game.mx2", ".mx2", "MSX2"},
		{"MSX1 by directory", "/games/msx1/game.rom", ".rom", "MSX1"},
		{"MSX1 by msx directory", "/games/msx/game.rom", ".rom", "MSX1"},
		{"MSX2 by directory", "/games/msx2/game.rom", ".rom", "MSX2"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := s.identifyConsole(tt.path, tt.ext)
			assert.Equal(t, tt.want, result)
		})
	}
}

func TestIdentifyConsole_NewDirectoryMappings(t *testing.T) {
	s := &Scanner{}
	tests := []struct {
		name string
		path string
		ext  string
		want string
	}{
		{"32x by sega32x dir", "/games/sega32x/game.bin", ".bin", "32X"},
		{"32x by 32x dir", "/games/32x/game.bin", ".bin", "32X"},
		{"Game Gear by gamegear dir", "/games/gamegear/game.gg", ".gg", "GG"},
		{"Game Gear by gg dir", "/games/gg/game.gg", ".gg", "GG"},
		{"Virtual Boy by virtualboy dir", "/games/virtualboy/game.vb", ".vb", "VB"},
		{"Virtual Boy by vb dir", "/games/vb/game.vb", ".vb", "VB"},
		{"Lynx by atarilynx dir", "/games/atarilynx/game.lnx", ".lnx", "LYNX"},
		{"Lynx by lynx dir", "/games/lynx/game.lnx", ".lnx", "LYNX"},
		{"Lynx lyx by atarilynx dir", "/games/atarilynx/game.lyx", ".lyx", "LYNX"},
		{"NGP by ngp dir", "/games/ngp/game.ngp", ".ngp", "NGP"},
		{"WonderSwan by wonderswan dir", "/games/wonderswan/game.ws", ".ws", "WS"},
		{"WonderSwan by ws dir", "/games/ws/game.ws", ".ws", "WS"},
		{"ColecoVision by colecovision dir", "/games/colecovision/game.col", ".col", "CV"},
		{"C64 by c64 dir", "/games/c64/game.d64", ".d64", "C64"},
		{"DOS by dos dir", "/games/dos/game.zip", ".zip", "DOS"},
		{"Amiga by amiga dir", "/games/amiga/game.adf", ".adf", "AMIGA"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := s.identifyConsole(tt.path, tt.ext)
			assert.Equal(t, tt.want, result)
		})
	}
}

func TestIdentifyConsole_AmigaDemosFolderMapping(t *testing.T) {
	s := &Scanner{}
	tests := []struct {
		name string
		path string
		ext  string
		want string
	}{
		{"amiga-demos folder", "/games/amiga-demos/second_reality.adf", ".adf", "ADEMO"},
		{"amigademos folder", "/games/amigademos/state_of_the_art.lha", ".lha", "ADEMO"},
		{"amiga folder stays AMIGA", "/games/amiga/game.adf", ".adf", "AMIGA"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := s.identifyConsole(tt.path, tt.ext)
			assert.Equal(t, tt.want, result)
		})
	}
}

func TestCreateConsoleFolders_AmigaDemosFolderCreated(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	err := CreateConsoleFolders(database, []string{dir})
	require.NoError(t, err)

	// Verify amiga-demos folder was created
	info, err := os.Stat(filepath.Join(dir, "amiga-demos"))
	require.NoError(t, err, "amiga-demos folder should exist")
	assert.True(t, info.IsDir())

	// Verify README.txt mentions Amiga Demos
	data, err := os.ReadFile(filepath.Join(dir, "amiga-demos", "README.txt"))
	require.NoError(t, err)
	assert.Contains(t, string(data), "Amiga Demos")
}

func TestCreateConsoleFolders_WritesREADME(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	err := CreateConsoleFolders(database, []string{dir})
	require.NoError(t, err)

	// Check that README.txt exists in the NES folder
	readmePath := filepath.Join(dir, "nes", "README.txt")
	data, err := os.ReadFile(readmePath)
	require.NoError(t, err, "README.txt should exist in nes folder")
	content := string(data)

	assert.Contains(t, content, "Nintendo Entertainment System")
	assert.Contains(t, content, ".nes")
	assert.Contains(t, content, "Place your ROM files in this folder")
}

func TestCreateConsoleFolders_READMEIncludesNotes(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	err := CreateConsoleFolders(database, []string{dir})
	require.NoError(t, err)

	// Neo Geo should have a note about Neo Geo Pocket
	data, err := os.ReadFile(filepath.Join(dir, "neogeo", "README.txt"))
	require.NoError(t, err)
	content := string(data)
	assert.Contains(t, content, "Neo Geo Pocket")
	assert.Contains(t, content, "ngp")

	// WonderSwan should mention WonderSwan Color
	data, err = os.ReadFile(filepath.Join(dir, "wonderswan", "README.txt"))
	require.NoError(t, err)
	content = string(data)
	assert.Contains(t, content, "WonderSwan Color")

	// TG16 should clarify it's not PC-FX
	data, err = os.ReadFile(filepath.Join(dir, "tg16", "README.txt"))
	require.NoError(t, err)
	content = string(data)
	assert.Contains(t, content, "NOT the same system as PC-FX")
}

func TestCreateConsoleFolders_PreservesExistingROMs(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	// Pre-create a folder with a ROM and a custom README
	nesDir := filepath.Join(dir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0755))
	romPath := filepath.Join(nesDir, "Mario.nes")
	require.NoError(t, os.WriteFile(romPath, []byte("fake rom"), 0644))

	err := CreateConsoleFolders(database, []string{dir})
	require.NoError(t, err)

	// ROM should be untouched
	data, err := os.ReadFile(romPath)
	require.NoError(t, err)
	assert.Equal(t, "fake rom", string(data))

	// README.txt should have been written
	readmeData, err := os.ReadFile(filepath.Join(nesDir, "README.txt"))
	require.NoError(t, err)
	assert.Contains(t, string(readmeData), "Nintendo Entertainment System")
}

func TestConsoleReadmeContent(t *testing.T) {
	tests := []struct {
		name    string
		console db.Console
		want    []string
	}{
		{
			name:    "basic console",
			console: db.Console{Name: "Nintendo Entertainment System", Abbreviation: "NES", Extensions: ".nes,.fds"},
			want:    []string{"Nintendo Entertainment System (NES)", ".nes,.fds", "Place your ROM files"},
		},
		{
			name:    "console with notes",
			console: db.Console{Name: "Neo Geo", Abbreviation: "NEOGEO", Extensions: ".zip"},
			want:    []string{"Neo Geo (NEOGEO)", "Neo Geo Pocket", "ngp"},
		},
		{
			name:    "MSX1 with notes",
			console: db.Console{Name: "MSX", Abbreviation: "MSX1", Extensions: ".rom,.mx1,.dsk,.cas"},
			want:    []string{"MSX (MSX1)", "msx2", ".mx1"},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			content := ConsoleReadmeContent(tt.console)
			for _, w := range tt.want {
				assert.Contains(t, content, w)
			}
		})
	}
}

func TestScan_DetectsMSXGames(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	msx1Dir := filepath.Join(dir, "msx1")
	require.NoError(t, os.MkdirAll(msx1Dir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(msx1Dir, "Penguin Adventure.mx1"), []byte("rom"), 0644))

	msx2Dir := filepath.Join(dir, "msx2")
	require.NoError(t, os.MkdirAll(msx2Dir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(msx2Dir, "Space Manbow.mx2"), []byte("rom"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 2, result.NewGames)

	var msx1Console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "MSX1").First(&msx1Console).Error)
	var msx1Count int64
	database.Model(&db.Game{}).Where("console_id = ?", msx1Console.ID).Count(&msx1Count)
	assert.Equal(t, int64(1), msx1Count)

	var msx2Console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "MSX2").First(&msx2Console).Error)
	var msx2Count int64
	database.Model(&db.Game{}).Where("console_id = ?", msx2Console.ID).Count(&msx2Count)
	assert.Equal(t, int64(1), msx2Count)
}

func TestScan_SkipsTrackBinFiles(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	// Create orphaned CD audio track files (no .cue file present)
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "Blam! Machinehead (Japan) (Track 01).bin"), []byte("data"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "Blam! Machinehead (Japan) (Track 02).bin"), []byte("audio"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "Blam! Machinehead (Japan) (Track 06).bin"), []byte("audio"), 0644))

	// Also create a legitimate single-file .bin game (no Track pattern)
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "Crash Bandicoot (USA).bin"), []byte("game"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)

	// Only the legitimate game should be created, track files should be skipped
	assert.Equal(t, 1, result.NewGames, "only the non-track .bin should be a game")
	assert.Equal(t, 1, result.TotalGames)

	var games []db.Game
	database.Find(&games)
	require.Len(t, games, 1)
	assert.Equal(t, "Crash Bandicoot", games[0].Title)
}

func TestScan_CleansUpExistingTrackFileGames(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	psxDir := filepath.Join(dir, "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	// Create track files on disk
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "Game (Track 01).bin"), []byte("data"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "Game (Track 02).bin"), []byte("audio"), 0644))

	// Simulate existing Game entries for track files (created by a prior scanner version)
	var psxConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "PSX").First(&psxConsole).Error)

	for i := 1; i <= 2; i++ {
		game := db.Game{
			ConsoleID: psxConsole.ID,
			Title:     "Game",
			FileName:  "Game (Track 0" + string(rune('0'+i)) + ").bin",
			FilePath:  "psx/Game (Track 0" + string(rune('0'+i)) + ").bin",
			FileSize:  100,
		}
		require.NoError(t, database.Create(&game).Error)
	}

	var countBefore int64
	database.Model(&db.Game{}).Count(&countBefore)
	require.Equal(t, int64(2), countBefore)

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)

	// Both track file entries should be cleaned up
	assert.Equal(t, 2, result.RemovedGames, "track file entries should be removed")
	assert.Equal(t, 0, result.TotalGames)

	var games []db.Game
	database.Unscoped().Find(&games)
	assert.Len(t, games, 0, "track file games should be hard-deleted")
}

func TestTrackPattern(t *testing.T) {
	tests := []struct {
		filename string
		isTrack  bool
	}{
		{"Game (Track 01).bin", true},
		{"Game (Track 1).bin", true},
		{"Blam! Machinehead (Japan) (Track 06).bin", true},
		{"Game (Track 99).bin", true},
		{"Game (track 3).bin", true},
		{"Crash Bandicoot (USA).bin", false},
		{"Super Mario 64 (USA).z64", false},
		{"Game (Disc 1).bin", false},
		{"Trackmania.bin", false},
	}

	for _, tt := range tests {
		t.Run(tt.filename, func(t *testing.T) {
			assert.Equal(t, tt.isTrack, trackPattern.MatchString(tt.filename))
		})
	}
}

func TestScan_DetectsScummVMGames(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	scummDir := filepath.Join(dir, "scummvm", "monkey1")
	require.NoError(t, os.MkdirAll(scummDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(scummDir, "monkey1.scummvm"), []byte("monkey\n"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(scummDir, "MONKEY.000"), []byte("data"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(scummDir, "MONKEY.001"), []byte("data"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, result.NewGames)

	var game db.Game
	err = database.Preload("Console").First(&game).Error
	require.NoError(t, err)
	assert.Equal(t, "SCUMMVM", game.Console.Abbreviation)
	assert.Equal(t, "monkey1.scummvm", game.FileName)
	assert.Contains(t, game.FilePath, "scummvm")
	assert.Contains(t, game.FilePath, "monkey1")
}

func TestScan_DetectsNestedScummVMGames(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	scummDir := filepath.Join(dir, "scummvm", "Beneath a Steel Sky", "sky")
	require.NoError(t, os.MkdirAll(scummDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(scummDir, "sky.scummvm"), []byte("sky\n"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(scummDir, "SKY.DNR"), []byte("data"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, result.NewGames)

	var game db.Game
	err = database.Preload("Console").First(&game).Error
	require.NoError(t, err)
	assert.Equal(t, "SCUMMVM", game.Console.Abbreviation)
	assert.Equal(t, "sky.scummvm", game.FileName)
}

func TestScan_MultipleScummVMGames(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	game1Dir := filepath.Join(dir, "scummvm", "monkey1")
	require.NoError(t, os.MkdirAll(game1Dir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(game1Dir, "monkey1.scummvm"), []byte("monkey\n"), 0644))

	game2Dir := filepath.Join(dir, "scummvm", "tentacle")
	require.NoError(t, os.MkdirAll(game2Dir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(game2Dir, "tentacle.scummvm"), []byte("tentacle\n"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 2, result.NewGames)
}
