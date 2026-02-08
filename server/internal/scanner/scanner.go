package scanner

import (
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"strings"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// Scanner detects ROMs in configured directories and maps them to consoles.
type Scanner struct {
	DB       *gorm.DB
	GameDirs []string
}

// NewScanner creates a new game scanner.
func NewScanner(database *gorm.DB, gameDirs []string) *Scanner {
	return &Scanner{
		DB:       database,
		GameDirs: gameDirs,
	}
}

// consoleExtMap maps file extensions to console abbreviations.
var consoleExtMap = map[string]string{
	".nes": "NES",
	".fds": "NES",
	".sfc": "SNES",
	".smc": "SNES",
	".gb":  "GB",
	".gbc": "GBC",
	".gba": "GBA",
	".n64": "N64",
	".z64": "N64",
	".v64": "N64",
	".nds": "NDS",
	".sms": "SMS",
	".md":  "GEN",
	".gen": "GEN",
	".pce": "PCE",
	".a26": "A26",
	".cso": "PSP",
	".pbp": "PSX",
	".cue": "PSX", // .cue files indicate PSX disc images
}

// directoryConsoleMap maps directory names to console abbreviations.
var directoryConsoleMap = map[string]string{
	"nes":     "NES",
	"snes":    "SNES",
	"gb":      "GB",
	"gbc":     "GBC",
	"gba":     "GBA",
	"n64":     "N64",
	"nds":     "NDS",
	"sms":     "SMS",
	"genesis": "GEN",
	"gen":     "GEN",
	"md":      "GEN",
	"megadrive": "GEN",
	"saturn":  "SAT",
	"sat":     "SAT",
	"psx":     "PSX",
	"ps1":     "PSX",
	"playstation": "PSX",
	"psp":     "PSP",
	"neogeo":  "NEOGEO",
	"arcade":  "ARCADE",
	"mame":    "ARCADE",
	"pce":     "PCE",
	"tg16":    "PCE",
	"atari2600": "A26",
	"a26":     "A26",
}

// ScanResult holds the results of a scan operation.
type ScanResult struct {
	NewGames     int `json:"newGames"`
	UpdatedGames int `json:"updatedGames"`
	RemovedGames int `json:"removedGames"`
	TotalGames   int `json:"totalGames"`
}

// Scan walks all configured directories and detects ROMs.
func (s *Scanner) Scan() (*ScanResult, error) {
	result := &ScanResult{}

	// Load all consoles into a map by abbreviation
	var consoles []db.Console
	if err := s.DB.Find(&consoles).Error; err != nil {
		return nil, fmt.Errorf("loading consoles: %w", err)
	}
	consoleMap := make(map[string]*db.Console)
	for i := range consoles {
		consoleMap[consoles[i].Abbreviation] = &consoles[i]
	}

	// Track found file paths to detect removed games
	foundPaths := make(map[string]bool)

	for _, dir := range s.GameDirs {
		if err := s.scanDirectory(dir, consoleMap, foundPaths, result); err != nil {
			slog.Warn("error scanning directory", "dir", dir, "error", err)
		}
	}

	// Remove games whose files no longer exist
	var allGames []db.Game
	if err := s.DB.Find(&allGames).Error; err != nil {
		return nil, fmt.Errorf("loading existing games: %w", err)
	}
	for _, g := range allGames {
		if !foundPaths[g.FilePath] {
			if _, err := os.Stat(g.FilePath); os.IsNotExist(err) {
				slog.Info("removing missing game", "title", g.Title, "path", g.FilePath)
				s.DB.Delete(&g)
				result.RemovedGames++
			}
		}
	}

	var count int64
	s.DB.Model(&db.Game{}).Count(&count)
	result.TotalGames = int(count)

	slog.Info("scan complete",
		"new", result.NewGames,
		"updated", result.UpdatedGames,
		"removed", result.RemovedGames,
		"total", result.TotalGames,
	)

	return result, nil
}

func (s *Scanner) scanDirectory(dir string, consoleMap map[string]*db.Console, foundPaths map[string]bool, result *ScanResult) error {
	return filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil // skip errors
		}
		if info.IsDir() {
			return nil
		}

		ext := strings.ToLower(filepath.Ext(path))
		if ext == "" {
			return nil
		}

		// Determine console from extension or parent directory
		abbrev := s.identifyConsole(path, ext)
		if abbrev == "" {
			return nil
		}

		console, exists := consoleMap[abbrev]
		if !exists {
			return nil
		}

		foundPaths[path] = true

		// Check if game already exists
		var existing db.Game
		if err := s.DB.Where("file_path = ?", path).First(&existing).Error; err == nil {
			// Game exists, check if file size changed
			if existing.FileSize != info.Size() {
				existing.FileSize = info.Size()
				s.DB.Save(&existing)
				result.UpdatedGames++
			}
			return nil
		}

		// Create new game entry
		title := gameTitle(info.Name())
		game := db.Game{
			ConsoleID: console.ID,
			Title:     title,
			FileName:  info.Name(),
			FilePath:  path,
			FileSize:  info.Size(),
		}
		if err := s.DB.Create(&game).Error; err != nil {
			slog.Warn("failed to create game entry", "path", path, "error", err)
			return nil
		}
		result.NewGames++
		slog.Info("found game", "title", title, "console", console.Abbreviation)

		return nil
	})
}

// identifyConsole determines the console for a file by its extension and parent directory.
func (s *Scanner) identifyConsole(path, ext string) string {
	// First try parent directory name
	parentDir := strings.ToLower(filepath.Base(filepath.Dir(path)))
	if abbrev, ok := directoryConsoleMap[parentDir]; ok {
		return abbrev
	}

	// Then try file extension
	if abbrev, ok := consoleExtMap[ext]; ok {
		return abbrev
	}

	return ""
}

// gameTitle extracts a clean game title from a filename.
func gameTitle(filename string) string {
	// Remove extension
	name := strings.TrimSuffix(filename, filepath.Ext(filename))
	// Remove common tags in parentheses/brackets
	for _, pair := range [][2]string{{"(", ")"}, {"[", "]"}} {
		for {
			start := strings.Index(name, pair[0])
			if start == -1 {
				break
			}
			end := strings.Index(name[start:], pair[1])
			if end == -1 {
				break
			}
			name = name[:start] + name[start+end+1:]
		}
	}
	return strings.TrimSpace(name)
}
