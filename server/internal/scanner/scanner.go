package scanner

import (
	"bufio"
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"regexp"
	"sort"
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

// romExtensions is the set of file extensions recognized as ROM/disc files.
// Files with other extensions (e.g. .txt, .jpg, .nfo) are never scanned,
// even when placed inside a console-named directory.
var romExtensions = map[string]bool{
	".nes": true, ".fds": true,
	".sfc": true, ".smc": true,
	".gb": true, ".gbc": true, ".gba": true,
	".n64": true, ".z64": true, ".v64": true,
	".nds": true,
	".sms": true, ".gg": true,
	".md": true, ".gen": true, ".bin": true,
	".pce": true,
	".a26": true,
	".cso": true, ".iso": true,
	".pbp": true, ".cue": true,
	".zip": true, ".7z": true,
	".chd": true,
	".m3u": true,
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
	"sms":          "SMS",
	"mastersystem": "SMS",
	"genesis":      "GEN",
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
	"dreamcast": "DC",
	"dc":        "DC",
	"segacd":    "SCD",
	"scd":       "SCD",
	"ps2":       "PS2",
	"pcfx":      "PCFX",
}

// discPattern matches disc/disk/cd markers in filenames, e.g. "(Disc 1)", "[Disk 2]", "(CD 3)".
var discPattern = regexp.MustCompile(`(?i)[\(\[]\s*(?:disc|disk|cd)\s*(\d+)\s*[\)\]]`)

// CreateConsoleFolders creates ES-DE standard console subdirectories in each game directory.
// It loads console definitions from the DB and creates a subfolder per console using FolderName.
// The operation is idempotent — existing directories and files are not affected.
func CreateConsoleFolders(database *gorm.DB, gameDirs []string) error {
	var consoles []db.Console
	if err := database.Find(&consoles).Error; err != nil {
		return fmt.Errorf("loading consoles: %w", err)
	}

	for _, dir := range gameDirs {
		for _, c := range consoles {
			if c.FolderName == "" {
				continue
			}
			path := filepath.Join(dir, c.FolderName)
			if err := os.MkdirAll(path, 0755); err != nil {
				return fmt.Errorf("creating folder %s: %w", path, err)
			}
			slog.Info("ensured console folder", "path", path)
		}
	}

	return nil
}

// ScanResult holds the results of a scan operation.
type ScanResult struct {
	NewGames     int `json:"newGames"`
	UpdatedGames int `json:"updatedGames"`
	RemovedGames int `json:"removedGames"`
	TotalGames   int `json:"totalGames"`
}

// parseM3U reads an .m3u file and returns resolved file paths.
// Blank lines and lines starting with # are skipped.
func parseM3U(m3uPath string) ([]string, error) {
	f, err := os.Open(m3uPath)
	if err != nil {
		return nil, fmt.Errorf("opening .m3u file: %w", err)
	}
	defer f.Close()

	dir := filepath.Dir(m3uPath)
	var paths []string
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		resolved := line
		if !filepath.IsAbs(resolved) {
			resolved = filepath.Join(dir, resolved)
		}
		paths = append(paths, filepath.Clean(resolved))
	}
	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("reading .m3u file: %w", err)
	}
	return paths, nil
}

// DiscCompanionFiles returns all file paths and total size for a disc entry.
// For .cue files, it parses FILE directives to find companion .bin files.
// For .iso/.chd/.pbp, it returns just the file itself.
func DiscCompanionFiles(discEntryPath string) ([]string, int64, error) {
	ext := strings.ToLower(filepath.Ext(discEntryPath))
	if ext != ".cue" {
		// Single file disc format
		info, err := os.Stat(discEntryPath)
		if err != nil {
			return nil, 0, fmt.Errorf("stat disc file: %w", err)
		}
		return []string{discEntryPath}, info.Size(), nil
	}

	// Parse .cue file for FILE directives
	f, err := os.Open(discEntryPath)
	if err != nil {
		return nil, 0, fmt.Errorf("opening .cue file: %w", err)
	}
	defer f.Close()

	dir := filepath.Dir(discEntryPath)
	files := []string{discEntryPath}
	var totalSize int64

	info, err := os.Stat(discEntryPath)
	if err != nil {
		return nil, 0, fmt.Errorf("stat .cue file: %w", err)
	}
	totalSize += info.Size()

	cueFilePattern := regexp.MustCompile(`(?i)^\s*FILE\s+"([^"]+)"`)
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		matches := cueFilePattern.FindStringSubmatch(sc.Text())
		if matches == nil {
			continue
		}
		binName := matches[1]
		binPath := binName
		if !filepath.IsAbs(binPath) {
			binPath = filepath.Join(dir, binPath)
		}
		binPath = filepath.Clean(binPath)
		files = append(files, binPath)
		if binInfo, err := os.Stat(binPath); err == nil {
			totalSize += binInfo.Size()
		}
	}

	return files, totalSize, nil
}

// generateM3U writes a .m3u file listing the given disc files and returns the .m3u path.
func generateM3U(dir, baseName string, discFiles []string) (string, error) {
	m3uPath := filepath.Join(dir, baseName+".m3u")
	var lines []string
	for _, f := range discFiles {
		// Use relative paths if files are in the same directory
		if filepath.Dir(f) == dir {
			lines = append(lines, filepath.Base(f))
		} else {
			lines = append(lines, f)
		}
	}
	content := strings.Join(lines, "\n") + "\n"
	if err := os.WriteFile(m3uPath, []byte(content), 0644); err != nil {
		return "", fmt.Errorf("writing .m3u file: %w", err)
	}
	return m3uPath, nil
}

// stripDiscMarker removes the disc marker from a filename to get the base title.
func stripDiscMarker(filename string) string {
	return strings.TrimSpace(discPattern.ReplaceAllString(filename, ""))
}

// discGroupKey returns a key for grouping disc files: (parentDir, baseTitle).
type discGroupKey struct {
	Dir   string
	Title string
}

// Scan walks all configured directories and detects ROMs.
// Uses a two-pass algorithm: first discovers multi-disc games, then scans remaining files.
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

	// Track paths claimed by multi-disc games (pass 1)
	claimedPaths := make(map[string]bool)

	// Pass 1: Multi-disc discovery
	for _, dir := range s.GameDirs {
		if err := s.scanMultiDisc(dir, consoleMap, foundPaths, claimedPaths, result); err != nil {
			slog.Warn("error in multi-disc scan", "dir", dir, "error", err)
		}
	}

	// Pass 2: Normal single-disc scan, skipping claimed paths
	for _, dir := range s.GameDirs {
		if err := s.scanDirectory(dir, consoleMap, foundPaths, claimedPaths, result); err != nil {
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
				// Delete associated discs first
				s.DB.Where("game_id = ?", g.ID).Delete(&db.GameDisc{})
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

// scanMultiDisc performs pass 1: discovers multi-disc games via .m3u files and disc patterns.
func (s *Scanner) scanMultiDisc(dir string, consoleMap map[string]*db.Console, foundPaths, claimedPaths map[string]bool, result *ScanResult) error {
	// Collect .m3u files and disc-pattern ROM files
	var m3uFiles []string
	discGroups := make(map[discGroupKey][]string) // grouped by (dir, baseTitle)

	err := filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil
		}
		if info.IsDir() {
			if strings.EqualFold(info.Name(), "bios") {
				return filepath.SkipDir
			}
			return nil
		}

		ext := strings.ToLower(filepath.Ext(path))
		if ext == ".m3u" {
			m3uFiles = append(m3uFiles, path)
			return nil
		}

		// Check for disc pattern in filename
		if discPattern.MatchString(info.Name()) && romExtensions[ext] && ext != ".m3u" {
			parentDir := filepath.Dir(path)
			nameNoExt := strings.TrimSuffix(info.Name(), filepath.Ext(info.Name()))
			baseTitle := stripDiscMarker(nameNoExt)
			key := discGroupKey{Dir: parentDir, Title: baseTitle}
			discGroups[key] = append(discGroups[key], path)
		}

		return nil
	})
	if err != nil {
		return err
	}

	// Process .m3u files first
	for _, m3uPath := range m3uFiles {
		discFiles, err := parseM3U(m3uPath)
		if err != nil {
			slog.Warn("failed to parse .m3u", "path", m3uPath, "error", err)
			continue
		}
		if len(discFiles) < 2 {
			continue // Not a multi-disc .m3u
		}

		// Claim the .m3u and all referenced files
		claimedPaths[m3uPath] = true
		var allClaimed []string
		allClaimed = append(allClaimed, m3uPath)
		for _, df := range discFiles {
			claimedPaths[df] = true
			allClaimed = append(allClaimed, df)
			// Also claim companion files (.bin files referenced by .cue)
			companions, _, _ := DiscCompanionFiles(df)
			for _, c := range companions {
				claimedPaths[c] = true
				allClaimed = append(allClaimed, c)
			}
		}

		// Mark all claimed paths as found
		for _, p := range allClaimed {
			foundPaths[p] = true
		}

		// Determine console from parent directory
		abbrev := s.identifyConsoleForDir(filepath.Dir(m3uPath))
		if abbrev == "" {
			// Try identifying from first disc file's extension
			if len(discFiles) > 0 {
				ext := strings.ToLower(filepath.Ext(discFiles[0]))
				abbrev = s.identifyConsole(discFiles[0], ext)
			}
		}
		if abbrev == "" {
			slog.Warn("could not identify console for multi-disc game", "m3u", m3uPath)
			continue
		}

		console, exists := consoleMap[abbrev]
		if !exists {
			continue
		}

		s.createMultiDiscGame(m3uPath, discFiles, console, foundPaths, result)
	}

	// Process disc-pattern groups (only if not already claimed by .m3u)
	for key, files := range discGroups {
		if len(files) < 2 {
			continue // Single disc, not a group
		}

		// Check if any files are already claimed
		allClaimed := true
		for _, f := range files {
			if !claimedPaths[f] {
				allClaimed = false
				break
			}
		}
		if allClaimed {
			continue
		}

		// Sort files by disc number
		sort.Slice(files, func(i, j int) bool {
			return files[i] < files[j]
		})

		// Determine console
		ext := strings.ToLower(filepath.Ext(files[0]))
		abbrev := s.identifyConsole(files[0], ext)
		if abbrev == "" {
			continue
		}
		console, exists := consoleMap[abbrev]
		if !exists {
			continue
		}

		// Auto-generate .m3u file
		m3uPath, err := generateM3U(key.Dir, key.Title, files)
		if err != nil {
			slog.Warn("failed to generate .m3u", "title", key.Title, "error", err)
			continue
		}

		// Claim all files
		claimedPaths[m3uPath] = true
		foundPaths[m3uPath] = true
		for _, f := range files {
			claimedPaths[f] = true
			foundPaths[f] = true
			companions, _, _ := DiscCompanionFiles(f)
			for _, c := range companions {
				claimedPaths[c] = true
				foundPaths[c] = true
			}
		}

		s.createMultiDiscGame(m3uPath, files, console, foundPaths, result)
	}

	return nil
}

// identifyConsoleForDir determines the console from a directory name.
func (s *Scanner) identifyConsoleForDir(dir string) string {
	dirName := strings.ToLower(filepath.Base(dir))
	if abbrev, ok := directoryConsoleMap[dirName]; ok {
		return abbrev
	}
	return ""
}

// removeOldDiscGames deletes standalone Game records whose FilePath matches a claimed disc file,
// so they don't coexist with the new multi-disc entry.
func (s *Scanner) removeOldDiscGames(claimedFiles []string, newM3UPath string, result *ScanResult) {
	for _, f := range claimedFiles {
		var oldGame db.Game
		if err := s.DB.Where("file_path = ?", f).First(&oldGame).Error; err == nil {
			slog.Info("removing old single-disc entry superseded by multi-disc game",
				"title", oldGame.Title, "path", f)
			s.DB.Unscoped().Where("game_id = ?", oldGame.ID).Delete(&db.GameDisc{})
			s.DB.Unscoped().Delete(&oldGame)
			result.RemovedGames++
		}
	}
}

// createMultiDiscGame creates or updates a multi-disc game entry in the database.
func (s *Scanner) createMultiDiscGame(m3uPath string, discFiles []string, console *db.Console, foundPaths map[string]bool, result *ScanResult) {
	// Check if game already exists
	var existing db.Game
	if err := s.DB.Where("file_path = ?", m3uPath).First(&existing).Error; err == nil {
		// Game exists, update if needed
		foundPaths[m3uPath] = true
		return
	}

	// Calculate total size across all discs
	var totalSize int64
	var discs []db.GameDisc
	for i, df := range discFiles {
		_, discSize, err := DiscCompanionFiles(df)
		if err != nil {
			slog.Warn("failed to get disc companion files", "path", df, "error", err)
			continue
		}
		totalSize += discSize
		discs = append(discs, db.GameDisc{
			DiscNumber: i + 1,
			FilePath:   df,
			FileName:   filepath.Base(df),
			FileSize:   discSize,
		})
	}

	// Extract title from the .m3u filename
	m3uName := filepath.Base(m3uPath)
	title := gameTitle(m3uName)

	game := db.Game{
		ConsoleID: console.ID,
		Title:     title,
		FileName:  m3uName,
		FilePath:  m3uPath,
		FileSize:  totalSize,
		DiscCount: len(discFiles),
	}
	if err := s.DB.Create(&game).Error; err != nil {
		slog.Warn("failed to create multi-disc game", "path", m3uPath, "error", err)
		return
	}

	// Create disc entries
	for i := range discs {
		discs[i].GameID = game.ID
		if err := s.DB.Create(&discs[i]).Error; err != nil {
			slog.Warn("failed to create disc entry", "game", title, "disc", discs[i].DiscNumber, "error", err)
		}
	}

	result.NewGames++
	slog.Info("found multi-disc game", "title", title, "console", console.Abbreviation, "discs", len(discFiles))

	// Clean up old standalone entries for discs now claimed by this multi-disc game
	s.removeOldDiscGames(discFiles, m3uPath, result)
}

func (s *Scanner) scanDirectory(dir string, consoleMap map[string]*db.Console, foundPaths, claimedPaths map[string]bool, result *ScanResult) error {
	return filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil // skip errors
		}
		if info.IsDir() {
			if strings.EqualFold(info.Name(), "bios") {
				return filepath.SkipDir
			}
			return nil
		}

		// Skip paths already claimed by multi-disc games
		if claimedPaths[path] {
			return nil
		}

		ext := strings.ToLower(filepath.Ext(path))
		if ext == "" {
			return nil
		}

		// Skip .m3u files in pass 2 (handled in pass 1)
		if ext == ".m3u" {
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

		// Validate the file extension is supported by this console
		if !consoleHasExtension(console, ext) {
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
// Only files with known ROM extensions are considered; other files (.txt, .jpg, etc.) are skipped.
func (s *Scanner) identifyConsole(path, ext string) string {
	if !romExtensions[ext] {
		return ""
	}

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

// consoleHasExtension checks if the console's Extensions field includes the given extension.
func consoleHasExtension(console *db.Console, ext string) bool {
	for _, e := range strings.Split(console.Extensions, ",") {
		if strings.TrimSpace(e) == ext {
			return true
		}
	}
	return false
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
