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
	"sync"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// ScanProgress holds the current state of an in-progress scan.
type ScanProgress struct {
	Phase   string `json:"phase"`   // "discovering" or "checking_removed"
	Current int    `json:"current"` // files/games processed so far
	Total   int    `json:"total"`   // total expected (0 if unknown)
	Message string `json:"message"` // human-readable status
}

// Scanner detects ROMs in configured directories and maps them to consoles.
type Scanner struct {
	DB       *gorm.DB
	GameDirs []string

	mu       sync.Mutex
	scanning bool
	progress *ScanProgress
}

// NewScanner creates a new game scanner.
func NewScanner(database *gorm.DB, gameDirs []string) *Scanner {
	return &Scanner{
		DB:       database,
		GameDirs: gameDirs,
	}
}

// TryStartScan attempts to acquire the scan lock.
// Returns true if acquired (caller must call FinishScan when done).
func (s *Scanner) TryStartScan() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.scanning {
		return false
	}
	s.scanning = true
	s.progress = nil
	return true
}

// FinishScan releases the scan lock and clears progress.
func (s *Scanner) FinishScan() {
	s.mu.Lock()
	s.scanning = false
	s.progress = nil
	s.mu.Unlock()
}

// SetScanProgress updates the current scan progress.
func (s *Scanner) SetScanProgress(p *ScanProgress) {
	s.mu.Lock()
	s.progress = p
	s.mu.Unlock()
}

// GetScanStatus returns whether a scan is active and the current progress.
func (s *Scanner) GetScanStatus() (bool, *ScanProgress) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.progress == nil {
		return s.scanning, nil
	}
	p := *s.progress
	return s.scanning, &p
}

// ConsoleExtMap maps file extensions to console abbreviations.
var ConsoleExtMap = map[string]string{
	".nes":  "NES",
	".fds":  "NES",
	".sfc":  "SNES",
	".smc":  "SNES",
	".gb":   "GB",
	".gbc":  "GBC",
	".gba":  "GBA",
	".n64":  "N64",
	".z64":  "N64",
	".v64":  "N64",
	".nds":  "NDS",
	".sms":  "SMS",
	".md":   "GEN",
	".gen":  "GEN",
	".pce":  "PCE",
	".a26":  "A26",
	".cso":  "PSP",
	".pbp":  "PSX",
	".cue":  "PSX", // .cue files indicate PSX disc images
	".gdi":  "DC",
	".cdi":  "DC",
	".3ds":  "3DS",
	".cci":  "3DS",
	".cia":  "3DS",
	".gcm":  "GC",
	".gcz":  "GC",
	".rvz":  "GC",
	".ciso": "GC",
	".xex":  "X360",
	".god":  "X360",
	".wbfs": "WII",
	".rpx":  "WIIU",
	".wud":  "WIIU",
	".wux":  "WIIU",
	".nsp":  "NSW",
	".xci":  "NSW",
	".mx1":  "MSX1",
	".mx2":  "MSX2",
	".gg":   "GG",
	".vb":   "VB",
	".vboy": "VB",
	".lnx":  "LYNX",
	".lyx":  "LYNX",
	".ngp":  "NGP",
	".ngc":  "NGP",
	".ws":   "WS",
	".wsc":  "WS",
	".col":  "CV",
	".min":  "PKMN",
	".j64":  "JAG",
	".jag":  "JAG",
	".32x":  "32X",
	".a52":  "A52",
	".a78":  "A78",
}

// RomExtensions is the set of file extensions recognized as ROM/disc files.
// Files with other extensions (e.g. .txt, .jpg, .nfo) are never scanned,
// even when placed inside a console-named directory.
var RomExtensions = map[string]bool{
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
	".pbp": true, ".cue": true, ".gdi": true, ".cdi": true,
	".zip": true, ".7z": true,
	".chd": true,
	".m3u": true,
	".3ds": true, ".cci": true, ".cia": true,
	".gcm": true, ".gcz": true, ".rvz": true, ".ciso": true,
	".pkg": true, ".xex": true, ".god": true, ".wbfs": true,
	".rpx": true, ".wud": true, ".wux": true,
	".nsp": true, ".xci": true, ".xvd": true,
	".mx1": true, ".mx2": true, ".cas": true,
	".rom": true, ".dsk": true,
	".vb": true, ".vboy": true,
	".lnx": true, ".lyx": true,
	".ngp": true, ".ngc": true,
	".ws": true, ".wsc": true,
	".col": true,
	".d64": true, ".d71": true, ".d81": true, ".t64": true, ".prg": true, ".crt": true,
	".conf": true, ".dosz": true,
	".adf": true, ".hdf": true, ".lha": true, ".ipf": true, ".dms": true,
	".min": true,
	".j64": true, ".jag": true,
	".32x": true,
}

// directoryConsoleMap maps directory names to console abbreviations.
var directoryConsoleMap = map[string]string{
	"nes":          "NES",
	"snes":         "SNES",
	"gb":           "GB",
	"gbc":          "GBC",
	"gba":          "GBA",
	"n64":          "N64",
	"nds":          "NDS",
	"sms":          "SMS",
	"mastersystem": "SMS",
	"genesis":      "GEN",
	"gen":          "GEN",
	"md":           "GEN",
	"megadrive":    "GEN",
	"saturn":       "SAT",
	"sat":          "SAT",
	"psx":          "PSX",
	"ps1":          "PSX",
	"playstation":  "PSX",
	"psp":          "PSP",
	"neogeo":       "NEOGEO",
	"arcade":       "ARCADE",
	"mame":         "ARCADE",
	"pce":          "PCE",
	"tg16":         "PCE",
	"atari2600":    "A26",
	"a26":          "A26",
	"dreamcast":    "DC",
	"dc":           "DC",
	"segacd":       "SCD",
	"scd":          "SCD",
	"ps2":          "PS2",
	"pcfx":         "PCFX",
	"n3ds":         "3DS",
	"3ds":          "3DS",
	"gc":           "GC",
	"gamecube":     "GC",
	"ps3":          "PS3",
	"ps4":          "PS4",
	"ps5":          "PS5",
	"xbox360":      "X360",
	"xboxone":      "XONE",
	"xboxseries":   "XSX",
	"wii":          "WII",
	"wiiu":         "WIIU",
	"switch":       "NSW",
	"msx1":         "MSX1",
	"msx":          "MSX1",
	"msx2":         "MSX2",
	"sega32x":      "32X",
	"32x":          "32X",
	"gamegear":     "GG",
	"gg":           "GG",
	"virtualboy":   "VB",
	"vb":           "VB",
	"atari5200":    "A52",
	"a52":          "A52",
	"atari7800":    "A78",
	"a78":          "A78",
	"atarilynx":    "LYNX",
	"lynx":         "LYNX",
	"atarijaguar":  "JAG",
	"jag":          "JAG",
	"ngp":          "NGP",
	"wonderswan":   "WS",
	"ws":           "WS",
	"colecovision": "CV",
	"pokemonmini":  "PKMN",
	"c64":          "C64",
	"dos":          "DOS",
	"amiga":        "AMIGA",
	"3do":          "3DO",
	"c128":         "C128",
	"pet":          "PET",
	"plus4":        "PLUS4",
	"vic20":        "VIC20",
	"cdi":          "CDI",
	"tg16cd":       "PCECD",
	"tgcd":         "PCECD",
	"pcecd":        "PCECD",
	"neogeocd":     "NEOCD",
	"neocd":        "NEOCD",
}

// discPattern matches disc/disk/cd markers in filenames, e.g. "(Disc 1)", "[Disk 2]", "(CD 3)".
var discPattern = regexp.MustCompile(`(?i)[\(\[]\s*(?:disc|disk|cd)\s*(\d+)\s*[\)\]]`)

// trackPattern matches CD audio track markers in filenames, e.g. "(Track 06)", "(Track 1)".
// These are companion files referenced by .cue sheets and should not be scanned as standalone games.
var trackPattern = regexp.MustCompile(`(?i)\(Track\s*\d+\)`)

// consoleNotes contains helpful guidance for console folders where the
// purpose or correct usage may be ambiguous. These are included in the
// README.txt files generated inside each console folder.
var consoleNotes = map[string]string{
	"NEOGEO": "This is for Neo Geo AES/MVS arcade ROMs (not Neo Geo Pocket).\nNeo Geo Pocket ROMs go in the 'ngp' folder.",
	"NGP":    "This is for both Neo Geo Pocket AND Neo Geo Pocket Color ROMs.\nFile extensions: .ngp (Neo Geo Pocket), .ngc (Neo Geo Pocket Color).",
	"WS":     "This is for both WonderSwan AND WonderSwan Color ROMs.\nFile extensions: .ws (WonderSwan), .wsc (WonderSwan Color).",
	"PCE":    "This is for TurboGrafx-16 / PC Engine ROMs.\nThis is NOT the same system as PC-FX (which has its own 'pcfx' folder).",
	"PCFX":   "This is for NEC PC-FX disc images.\nThis is NOT the same system as TurboGrafx-16/PC Engine (which uses the 'tg16' folder).",
	"A78":    "Prefer .a78 files over .bin when both are available.\nThe .a78 format includes a header that helps the emulator identify the game correctly.",
	"A26":    "Prefer .a26 files over .bin when both are available.",
	"A52":    "Prefer .a52 files over .bin when both are available.",
	"GEN":    "Prefer .md or .gen files over .bin when both are available.\nThe .bin format is ambiguous and could be mistaken for other systems.",
	"MSX1":   "This is for MSX (first generation) ROMs.\nMSX2 ROMs go in the 'msx2' folder.\nUse .mx1 for MSX1-specific ROMs, or .rom/.dsk for generic MSX media.",
	"MSX2":   "This is for MSX2 (and MSX2+) ROMs.\nOriginal MSX ROMs go in the 'msx1' folder.\nUse .mx2 for MSX2-specific ROMs, or .rom/.dsk for generic MSX media.",
	"GB":     "This is for original Game Boy ROMs only (.gb).\nGame Boy Color ROMs go in the 'gbc' folder.",
	"GBC":    "This is for Game Boy Color ROMs (.gbc).\nOriginal Game Boy ROMs go in the 'gb' folder.",
}

// ConsoleReadmeContent generates the content for a README.txt file
// placed inside a console folder to help users understand what ROMs belong there.
func ConsoleReadmeContent(c db.Console) string {
	var b strings.Builder
	b.WriteString(c.Name)
	headerLen := len(c.Name)
	if c.Abbreviation != "" {
		b.WriteString(" (")
		b.WriteString(c.Abbreviation)
		b.WriteString(")")
		headerLen += len(c.Abbreviation) + 3 // " (" + ")"
	}
	b.WriteString("\n")
	b.WriteString(strings.Repeat("=", headerLen))
	b.WriteString("\n\n")

	if c.Extensions != "" {
		b.WriteString("Supported file extensions: ")
		b.WriteString(c.Extensions)
		b.WriteString("\n\n")
	}

	if note, ok := consoleNotes[c.Abbreviation]; ok {
		b.WriteString("Notes:\n")
		for _, line := range strings.Split(note, "\n") {
			b.WriteString("  ")
			b.WriteString(line)
			b.WriteString("\n")
		}
		b.WriteString("\n")
	}

	b.WriteString("Place your ROM files in this folder. They will be detected\n")
	b.WriteString("automatically when you run a library scan.\n")

	return b.String()
}

// CreateConsoleFolders creates ES-DE standard console subdirectories in each game directory.
// It loads console definitions from the DB and creates a subfolder per console using FolderName.
// Each folder gets a README.txt explaining what ROMs belong there.
// The operation is idempotent — existing directories are not affected, but README.txt
// files are updated on every run to keep them current.
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

			readmePath := filepath.Join(path, "README.txt")
			content := ConsoleReadmeContent(c)
			if err := os.WriteFile(readmePath, []byte(content), 0644); err != nil {
				slog.Warn("failed to write README.txt", "path", readmePath, "error", err)
			}

			slog.Info("ensured console folder", "path", path)
		}
	}

	return nil
}

// ScanResult holds the results of a scan operation.
type ScanResult struct {
	NewGames     int    `json:"newGames"`
	UpdatedGames int    `json:"updatedGames"`
	RemovedGames int    `json:"removedGames"`
	TotalGames   int    `json:"totalGames"`
	NewGameIDs   []uint `json:"newGameIds,omitempty"`
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
	if ext != ".cue" && ext != ".gdi" {
		// Single file disc format
		info, err := os.Stat(discEntryPath)
		if err != nil {
			return nil, 0, fmt.Errorf("stat disc file: %w", err)
		}
		return []string{discEntryPath}, info.Size(), nil
	}

	dir := filepath.Dir(discEntryPath)
	files := []string{discEntryPath}
	var totalSize int64

	info, err := os.Stat(discEntryPath)
	if err != nil {
		return nil, 0, fmt.Errorf("stat %s file: %w", ext, err)
	}
	totalSize += info.Size()

	f, err := os.Open(discEntryPath)
	if err != nil {
		return nil, 0, fmt.Errorf("opening %s file: %w", ext, err)
	}
	defer f.Close()

	if ext == ".gdi" {
		// GDI format: first line is track count, subsequent lines are
		// TRACK LBA TYPE SECTOR_SIZE FILENAME OFFSET
		sc := bufio.NewScanner(f)
		lineNum := 0
		for sc.Scan() {
			lineNum++
			line := strings.TrimSpace(sc.Text())
			if line == "" {
				continue
			}
			if lineNum == 1 {
				// First non-blank line is the track count — skip it
				continue
			}
			// Parse track line: fields are space-separated, filename is field 4 (0-indexed)
			fields := strings.Fields(line)
			if len(fields) < 5 {
				continue
			}
			trackFile := strings.Trim(fields[4], "\"")
			trackPath := trackFile
			if !filepath.IsAbs(trackPath) {
				trackPath = filepath.Join(dir, trackPath)
			}
			trackPath = filepath.Clean(trackPath)
			files = append(files, trackPath)
			if trackInfo, err := os.Stat(trackPath); err == nil {
				totalSize += trackInfo.Size()
			}
		}
	} else {
		// Parse .cue file for FILE directives
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

// ProgressFunc is a callback for reporting scan progress.
type ProgressFunc func(ScanProgress)

// Scan walks all configured directories and detects ROMs.
// Uses a two-pass algorithm: first discovers multi-disc games, then scans remaining files.
// The optional onProgress callback is called at key points to report progress.
// If consoleFilter is non-empty, only games matching that console abbreviation are processed
// and the removal pass is scoped to that console.
func (s *Scanner) Scan(onProgress ProgressFunc, consoleFilter ...string) (*ScanResult, error) {
	result := &ScanResult{}
	filterAbbrev := ""
	if len(consoleFilter) > 0 {
		filterAbbrev = consoleFilter[0]
	}

	report := func(p ScanProgress) {
		if onProgress != nil {
			onProgress(p)
		}
	}

	report(ScanProgress{Phase: "discovering", Message: "Loading console definitions..."})

	// Load all consoles into a map by abbreviation
	var consoles []db.Console
	if err := s.DB.Find(&consoles).Error; err != nil {
		return nil, fmt.Errorf("loading consoles: %w", err)
	}
	consoleMap := make(map[string]*db.Console)
	for i := range consoles {
		consoleMap[consoles[i].Abbreviation] = &consoles[i]
	}

	// When filtering by console, restrict the map so only that console is processed
	if filterAbbrev != "" {
		if con, ok := consoleMap[filterAbbrev]; ok {
			consoleMap = map[string]*db.Console{filterAbbrev: con}
		} else {
			return nil, fmt.Errorf("unknown console: %s", filterAbbrev)
		}
	}

	// Track found file paths to detect removed games
	foundPaths := make(map[string]bool)

	// Track paths claimed by multi-disc games (pass 1)
	claimedPaths := make(map[string]bool)

	// Pass 1: Multi-disc discovery
	report(ScanProgress{Phase: "discovering", Message: "Scanning for multi-disc games..."})
	multiDiscFilesWalked := 0
	for _, dir := range s.GameDirs {
		if err := s.scanMultiDisc(dir, consoleMap, foundPaths, claimedPaths, result, func() {
			multiDiscFilesWalked++
			if multiDiscFilesWalked%200 == 0 {
				report(ScanProgress{
					Phase:   "discovering",
					Current: multiDiscFilesWalked,
					Message: fmt.Sprintf("Scanning for multi-disc games... (%d files checked)", multiDiscFilesWalked),
				})
			}
		}); err != nil {
			slog.Warn("error in multi-disc scan", "dir", dir, "error", err)
		}
	}

	report(ScanProgress{
		Phase:   "discovering",
		Current: result.NewGames,
		Message: fmt.Sprintf("Scanning game directories... (%d new so far)", result.NewGames),
	})

	// Pass 2: Normal single-disc scan, skipping claimed paths
	filesProcessed := 0
	for _, dir := range s.GameDirs {
		if err := s.scanDirectory(dir, consoleMap, foundPaths, claimedPaths, result, func() {
			filesProcessed++
			if filesProcessed%50 == 0 {
				report(ScanProgress{
					Phase:   "discovering",
					Current: result.NewGames,
					Message: fmt.Sprintf("Scanning game directories... (%d new so far, %d files checked)", result.NewGames, filesProcessed),
				})
			}
		}); err != nil {
			slog.Warn("error scanning directory", "dir", dir, "error", err)
		}
	}

	// Clean up existing Game entries for CD audio track files (e.g. "Game (Track 06).bin")
	// that were erroneously created as standalone games before the track-file filter was added.
	removed := s.removeTrackFileGames()
	result.RemovedGames += removed

	report(ScanProgress{
		Phase:   "checking_removed",
		Current: result.NewGames,
		Message: "Checking for removed games...",
	})

	// Remove games whose files no longer exist.
	// Use Unscoped to include soft-deleted games — if a game was previously
	// soft-deleted and the file is still gone, hard-delete it to free the
	// unique index slot. If the file came back, it was already restored above.
	var allGames []db.Game
	gamesQuery := s.DB.Unscoped()
	if filterAbbrev != "" {
		if con, ok := consoleMap[filterAbbrev]; ok {
			gamesQuery = gamesQuery.Where("console_id = ?", con.ID)
		}
	}
	if err := gamesQuery.Find(&allGames).Error; err != nil {
		return nil, fmt.Errorf("loading existing games: %w", err)
	}
	for i, g := range allGames {
		if !foundPaths[g.FilePath] {
			// Resolve relative path to absolute for filesystem check
			absPath, resolveErr := storage.ResolveGamePath(g.FilePath, s.GameDirs)
			if resolveErr != nil || func() bool { _, err := os.Stat(absPath); return os.IsNotExist(err) }() {
				if !g.DeletedAt.Valid {
					slog.Info("removing missing game", "title", g.Title, "path", g.FilePath)
					result.RemovedGames++
				}
				// If this game was the primary variant for its group,
				// re-elect a new primary after deletion.
				wasPrimary := g.IsPrimary
				removedConsoleID := g.ConsoleID
				removedGroupKey := g.GroupKey
				// Hard-delete game and associated discs so the unique index
				// is freed and future scans can recreate them cleanly.
				s.DB.Unscoped().Where("game_id = ?", g.ID).Delete(&db.GameDisc{})
				s.DB.Unscoped().Delete(&g)
				if wasPrimary && removedGroupKey != "" {
					if err := ReElectPrimaryForGroup(s.DB, removedConsoleID, removedGroupKey); err != nil {
						slog.Warn("failed to re-elect primary after game removal", "groupKey", removedGroupKey, "error", err)
					}
				}
			}
		}
		if i%100 == 0 {
			report(ScanProgress{
				Phase:   "checking_removed",
				Current: i,
				Total:   len(allGames),
				Message: fmt.Sprintf("Checking for removed games... (%d/%d)", i, len(allGames)),
			})
		}
	}

	var count int64
	s.DB.Model(&db.Game{}).Count(&count)
	result.TotalGames = int(count)

	// Backfill metadata for games created before large-library-support
	report(ScanProgress{Phase: "backfill", Message: "Backfilling game metadata..."})
	if err := BackfillGameMetadataWithProgress(s.DB, func(processed, total int64) {
		report(ScanProgress{
			Phase:   "backfill",
			Current: int(processed),
			Total:   int(total),
			Message: fmt.Sprintf("Backfilling game metadata... (%d/%d)", processed, total),
		})
	}); err != nil {
		slog.Warn("failed to backfill game metadata during scan", "error", err)
	}

	// Group variants and elect primary representatives
	report(ScanProgress{Phase: "grouping", Message: "Grouping variants and electing primaries..."})
	if err := GroupAndElectPrimaries(s.DB); err != nil {
		slog.Warn("failed to group and elect primaries", "error", err)
	}

	slog.Info("scan complete",
		"new", result.NewGames,
		"updated", result.UpdatedGames,
		"removed", result.RemovedGames,
		"total", result.TotalGames,
	)

	return result, nil
}

// scanMultiDisc performs pass 1: discovers multi-disc games via .m3u files, disc patterns,
// and standalone .cue files (which need companion .bin files bundled).
// The optional onFile callback is called for each file visited during the directory walk.
func (s *Scanner) scanMultiDisc(dir string, consoleMap map[string]*db.Console, foundPaths, claimedPaths map[string]bool, result *ScanResult, onFile func()) error {
	// Collect .m3u files, disc-pattern ROM files, and standalone .cue/.gdi files
	var m3uFiles []string
	var cueFiles []string
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

		if onFile != nil {
			onFile()
		}

		ext := strings.ToLower(filepath.Ext(path))
		if ext == ".m3u" {
			m3uFiles = append(m3uFiles, path)
			return nil
		}

		if ext == ".cue" || ext == ".gdi" {
			cueFiles = append(cueFiles, path)
		}

		// Check for disc pattern in filename
		if discPattern.MatchString(info.Name()) && RomExtensions[ext] && ext != ".m3u" {
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

		// Mark the .m3u relative path as found (disc files don't have their own Game records)
		foundPaths[storage.RelativeGamePath(m3uPath, s.GameDirs)] = true

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

		// Claim all files (absolute paths for pass-2 exclusion)
		claimedPaths[m3uPath] = true
		for _, f := range files {
			claimedPaths[f] = true
			companions, _, _ := DiscCompanionFiles(f)
			for _, c := range companions {
				claimedPaths[c] = true
			}
		}
		// Mark the .m3u relative path as found
		foundPaths[storage.RelativeGamePath(m3uPath, s.GameDirs)] = true

		s.createMultiDiscGame(m3uPath, files, console, foundPaths, result)
	}

	// Process standalone .cue/.gdi files not already claimed by .m3u or disc patterns.
	// A standalone .cue+.bin or .gdi+tracks needs a Game with DiscCount=1 and a GameDisc
	// record so the download handler serves a tar bundle (not just the entry file).
	for _, cuePath := range cueFiles {
		if claimedPaths[cuePath] {
			continue
		}

		// Get companion files (track files referenced in the .cue/.gdi)
		companions, totalSize, err := DiscCompanionFiles(cuePath)
		if err != nil {
			slog.Warn("failed to get companion files for disc entry", "path", cuePath, "error", err)
			continue
		}

		// Claim the entry file and all companion files to prevent pass 2 from
		// creating separate game entries for track files
		for _, c := range companions {
			claimedPaths[c] = true
		}

		// Determine console
		ext := strings.ToLower(filepath.Ext(cuePath))
		abbrev := s.identifyConsole(cuePath, ext)
		if abbrev == "" {
			continue
		}
		console, exists := consoleMap[abbrev]
		if !exists {
			continue
		}
		if !consoleHasExtension(console, ext) {
			continue
		}

		relPath := storage.RelativeGamePath(cuePath, s.GameDirs)
		foundPaths[relPath] = true

		// Check if game already exists
		var existing db.Game
		if err := s.DB.Unscoped().Where("file_path = ?", relPath).First(&existing).Error; err == nil {
			// Restore if soft-deleted
			if existing.DeletedAt.Valid {
				slog.Info("restoring previously removed .cue game",
					"title", existing.Title, "gameId", existing.ID)
				s.DB.Unscoped().Model(&existing).Update("deleted_at", nil)
			}
			// Fix console if directory indicates a different one (e.g. .cue in ps2/
			// was previously identified as PSX via extension mapping)
			if existing.ConsoleID != console.ID {
				slog.Info("updating console for .cue game",
					"title", existing.Title, "from", existing.ConsoleID, "to", console.ID)
				s.DB.Model(&existing).Update("console_id", console.ID)
			}
			// Backfill disc record if missing
			var discCount int64
			s.DB.Model(&db.GameDisc{}).Where("game_id = ?", existing.ID).Count(&discCount)
			if discCount == 0 {
				slog.Info("creating disc record for standalone .cue game",
					"title", existing.Title, "gameId", existing.ID)
				disc := db.GameDisc{
					GameID:     existing.ID,
					DiscNumber: 1,
					FilePath:   relPath,
					FileName:   filepath.Base(cuePath),
					FileSize:   totalSize,
				}
				s.DB.Create(&disc)
				if existing.DiscCount == 0 || existing.FileSize == 0 {
					s.DB.Model(&existing).Updates(map[string]interface{}{
						"disc_count": 1,
						"file_size":  totalSize,
					})
				}
			}
		} else {
			// Create new game with disc record
			cueBase := filepath.Base(cuePath)
			title := GameTitle(cueBase)
			meta := ParseFilenameMetadata(cueBase)
			game := db.Game{
				ConsoleID:    console.ID,
				Title:        title,
				FileName:     cueBase,
				FilePath:     relPath,
				FileSize:     totalSize,
				DiscCount:    1,
				Region:       meta.Region,
				Revision:     meta.Revision,
				Tags:         meta.Tags,
				IsPreRelease: meta.IsPreRelease,
				GroupKey:     meta.GroupKey,
			}
			if err := s.DB.Create(&game).Error; err != nil {
				slog.Warn("failed to create .cue game", "path", cuePath, "error", err)
				continue
			}

			disc := db.GameDisc{
				GameID:     game.ID,
				DiscNumber: 1,
				FilePath:   relPath,
				FileName:   filepath.Base(cuePath),
				FileSize:   totalSize,
			}
			if err := s.DB.Create(&disc).Error; err != nil {
				slog.Warn("failed to create disc entry for .cue game", "path", cuePath, "error", err)
			}

			result.NewGames++
			result.NewGameIDs = append(result.NewGameIDs, game.ID)
			slog.Info("found standalone .cue game", "title", title, "console", console.Abbreviation)
		}

		// Always clean up old standalone entries for companion files (e.g. orphaned
		// .bin game entries), regardless of whether the .cue game already existed.
		var binCompanions []string
		for _, c := range companions {
			if c != cuePath {
				binCompanions = append(binCompanions, c)
			}
		}
		s.removeOldDiscGames(binCompanions, cuePath, result)
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

// removeTrackFileGames deletes Game entries for CD audio track files (e.g. "Game (Track 06).bin")
// that were previously created as standalone games. These files are companions to .cue sheets
// and should never be standalone game entries.
func (s *Scanner) removeTrackFileGames() int {
	var games []db.Game
	if err := s.DB.Where("file_name LIKE '%.bin'").Find(&games).Error; err != nil {
		slog.Warn("failed to query .bin games for track file cleanup", "error", err)
		return 0
	}

	removed := 0
	for _, g := range games {
		if trackPattern.MatchString(g.FileName) {
			slog.Info("removing CD audio track file game entry",
				"title", g.Title, "fileName", g.FileName, "gameId", g.ID)
			s.DB.Unscoped().Where("game_id = ?", g.ID).Delete(&db.GameDisc{})
			s.DB.Unscoped().Delete(&g)
			removed++
		}
	}
	if removed > 0 {
		slog.Info("cleaned up CD audio track file entries", "removed", removed)
	}
	return removed
}

// removeOldDiscGames deletes standalone Game records whose FilePath matches a claimed disc file,
// so they don't coexist with the new multi-disc entry.
func (s *Scanner) removeOldDiscGames(claimedFiles []string, newM3UPath string, result *ScanResult) {
	for _, f := range claimedFiles {
		relPath := storage.RelativeGamePath(f, s.GameDirs)
		var oldGame db.Game
		if err := s.DB.Where("file_path = ?", relPath).First(&oldGame).Error; err == nil {
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
	// Compute relative paths for DB storage
	m3uRelPath := storage.RelativeGamePath(m3uPath, s.GameDirs)

	// Check if game already exists (including soft-deleted — the unique index
	// on file_path covers soft-deleted rows, so we must see them to avoid
	// creation failures and to restore games whose files reappear).
	var existing db.Game
	if err := s.DB.Unscoped().Where("file_path = ?", m3uRelPath).First(&existing).Error; err == nil {
		// Restore if soft-deleted (file is back on disk)
		if existing.DeletedAt.Valid {
			slog.Info("restoring previously removed multi-disc game",
				"title", existing.Title, "gameId", existing.ID)
			s.DB.Unscoped().Model(&existing).Update("deleted_at", nil)
		}
		foundPaths[m3uRelPath] = true

		// Ensure disc records exist — they may be missing if the game was
		// created before multi-disc support or if disc records were deleted.
		var discCount int64
		s.DB.Unscoped().Model(&db.GameDisc{}).Where("game_id = ?", existing.ID).Count(&discCount)
		if discCount == 0 && len(discFiles) > 0 {
			slog.Info("creating missing disc records for existing multi-disc game",
				"title", existing.Title, "gameId", existing.ID, "discs", len(discFiles))
			var totalSize int64
			for i, df := range discFiles {
				_, discSize, err := DiscCompanionFiles(df)
				if err != nil {
					slog.Warn("failed to get disc companion files", "path", df, "error", err)
					continue
				}
				totalSize += discSize
				disc := db.GameDisc{
					GameID:     existing.ID,
					DiscNumber: i + 1,
					FilePath:   storage.RelativeGamePath(df, s.GameDirs),
					FileName:   filepath.Base(df),
					FileSize:   discSize,
				}
				if err := s.DB.Create(&disc).Error; err != nil {
					slog.Warn("failed to create disc entry", "game", existing.Title, "disc", disc.DiscNumber, "error", err)
				}
			}
			if existing.DiscCount == 0 || existing.FileSize == 0 {
				s.DB.Model(&existing).Updates(map[string]interface{}{
					"disc_count": len(discFiles),
					"file_size":  totalSize,
				})
			}
		} else if discCount > 0 {
			// Restore any soft-deleted disc records
			s.DB.Unscoped().Model(&db.GameDisc{}).
				Where("game_id = ? AND deleted_at IS NOT NULL", existing.ID).
				Update("deleted_at", nil)
		}
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
			FilePath:   storage.RelativeGamePath(df, s.GameDirs),
			FileName:   filepath.Base(df),
			FileSize:   discSize,
		})
	}

	// Extract title from the .m3u filename
	m3uName := filepath.Base(m3uPath)
	title := GameTitle(m3uName)
	meta := ParseFilenameMetadata(m3uName)

	game := db.Game{
		ConsoleID:    console.ID,
		Title:        title,
		FileName:     m3uName,
		FilePath:     m3uRelPath,
		FileSize:     totalSize,
		DiscCount:    len(discFiles),
		Region:       meta.Region,
		Revision:     meta.Revision,
		Tags:         meta.Tags,
		IsPreRelease: meta.IsPreRelease,
		GroupKey:     meta.GroupKey,
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

	// Check for achievements warnings (e.g. PSP CHD created with createdvd)
	if len(discFiles) > 0 {
		if warning := PSPCHDAchievementsWarning(discFiles[0], console.Abbreviation); warning != "" {
			game.AchievementsWarning = warning
			s.DB.Save(&game)
		}
	}

	result.NewGames++
	result.NewGameIDs = append(result.NewGameIDs, game.ID)
	slog.Info("found multi-disc game", "title", title, "console", console.Abbreviation, "discs", len(discFiles))

	// Clean up old standalone entries for discs now claimed by this multi-disc game
	s.removeOldDiscGames(discFiles, m3uPath, result)
}

func (s *Scanner) scanDirectory(dir string, consoleMap map[string]*db.Console, foundPaths, claimedPaths map[string]bool, result *ScanResult, onFile func()) error {
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

		if onFile != nil {
			onFile()
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

		// Skip .bin files that are CD audio tracks (e.g. "Game (Track 06).bin").
		// These are companion files referenced by .cue sheets. When the .cue file
		// exists, Pass 1 claims them; this catches orphaned track files where the
		// .cue file is missing or was already handled.
		if ext == ".bin" && trackPattern.MatchString(info.Name()) {
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

		// Compute relative path from game dir root for DB storage
		relPath := storage.RelativeGamePath(path, s.GameDirs)
		foundPaths[relPath] = true

		// Check if game already exists (including soft-deleted — unique index
		// on file_path covers soft-deleted rows).
		var existing db.Game
		if err := s.DB.Unscoped().Where("file_path = ?", relPath).First(&existing).Error; err == nil {
			// Restore if soft-deleted (file is back on disk)
			if existing.DeletedAt.Valid {
				slog.Info("restoring previously removed game",
					"title", existing.Title, "path", relPath)
				s.DB.Unscoped().Model(&existing).Update("deleted_at", nil)
			}
			// Check if file size changed
			if existing.FileSize != info.Size() {
				existing.FileSize = info.Size()
				s.DB.Save(&existing)
				result.UpdatedGames++
			}
			return nil
		}

		// Create new game entry
		title := GameTitle(info.Name())
		meta := ParseFilenameMetadata(info.Name())
		game := db.Game{
			ConsoleID:    console.ID,
			Title:        title,
			FileName:     info.Name(),
			FilePath:     relPath,
			FileSize:     info.Size(),
			Region:       meta.Region,
			Revision:     meta.Revision,
			Tags:         meta.Tags,
			IsPreRelease: meta.IsPreRelease,
			GroupKey:     meta.GroupKey,
		}
		if err := s.DB.Create(&game).Error; err != nil {
			slog.Warn("failed to create game entry", "path", relPath, "error", err)
			return nil
		}

		// Check for achievements warnings (e.g. PSP CHD created with createdvd)
		if warning := PSPCHDAchievementsWarning(path, console.Abbreviation); warning != "" {
			game.AchievementsWarning = warning
			s.DB.Save(&game)
		}

		result.NewGames++
		result.NewGameIDs = append(result.NewGameIDs, game.ID)
		slog.Info("found game", "title", title, "console", console.Abbreviation)

		return nil
	})
}

// identifyConsole determines the console for a file by its extension and parent directory.
// Only files with known ROM extensions are considered; other files (.txt, .jpg, etc.) are skipped.
func (s *Scanner) identifyConsole(path, ext string) string {
	if !RomExtensions[ext] {
		return ""
	}

	// First try parent directory name
	parentDir := strings.ToLower(filepath.Base(filepath.Dir(path)))
	if abbrev, ok := directoryConsoleMap[parentDir]; ok {
		return abbrev
	}

	// Try grandparent directory (handles layouts like ps2/GameName/game.bin)
	grandparentDir := strings.ToLower(filepath.Base(filepath.Dir(filepath.Dir(path))))
	if abbrev, ok := directoryConsoleMap[grandparentDir]; ok {
		return abbrev
	}

	// Then try file extension
	if abbrev, ok := ConsoleExtMap[ext]; ok {
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

// GameTitle extracts a clean game title from a filename.
func GameTitle(filename string) string {
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
