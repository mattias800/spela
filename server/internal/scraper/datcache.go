package scraper

import (
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"sync"
)

// DiscBasedSystems lists console abbreviations where CRC-based identification
// is impractical — either disc images are too large, or No-Intro DATs don't
// exist for the platform (arcade ROM sets, DOS, etc.).
var DiscBasedSystems = map[string]bool{
	"PSX":    true,
	"SAT":    true,
	"DC":     true,
	"SCD":    true,
	"PS2":    true,
	"GC":     true,  // disc-based
	"PCFX":   true,  // disc-based
	"NEOGEO": true,  // arcade ROM sets, no No-Intro DAT
	"ARCADE": true,  // MAME ROM sets, no No-Intro DAT
	"DOS":    true,  // no No-Intro DAT
	"PS3":    true,  // disc/pkg-based
	"PS4":    true,  // disc/pkg-based
	"PS5":    true,  // disc/pkg-based
	"X360":   true,  // disc/xex-based
	"XONE":   true,  // disc-based
	"XSX":    true,  // disc-based
	"WII":    true,  // disc-based
	"WIIU":   true,  // disc-based
	"NSW":    true,  // cartridge images, too large for CRC
}

// MaxROMSize defines conservative upper bounds (in bytes) per console abbreviation.
// Used during auto-identification to skip consoles where the file is too large to be a ROM.
// Consoles not in this map (and not in DiscBasedSystems) are skipped during auto-identification.
var MaxROMSize = map[string]int64{
	"NES":  4 * 1024 * 1024,   // 4 MB
	"SNES": 16 * 1024 * 1024,  // 16 MB
	"GB":   8 * 1024 * 1024,   // 8 MB
	"GBC":  8 * 1024 * 1024,   // 8 MB
	"GBA":  64 * 1024 * 1024,  // 64 MB
	"N64":  64 * 1024 * 1024,  // 64 MB
	"GEN":  32 * 1024 * 1024,  // 32 MB
	"SMS":  4 * 1024 * 1024,   // 4 MB
	"GG":   4 * 1024 * 1024,   // 4 MB
	"PCE":  8 * 1024 * 1024,   // 8 MB
	"A26":  1 * 1024 * 1024,   // 1 MB
	"A52":  2 * 1024 * 1024,   // 2 MB
	"A78":  2 * 1024 * 1024,   // 2 MB
	"LYNX": 4 * 1024 * 1024,   // 4 MB
	"JAG":  16 * 1024 * 1024,  // 16 MB
	"NGP":  16 * 1024 * 1024,  // 16 MB
	"WS":   16 * 1024 * 1024,  // 16 MB
	"CV":   1 * 1024 * 1024,   // 1 MB
	"PKMN": 4 * 1024 * 1024,   // 4 MB
	"VB":   16 * 1024 * 1024,  // 16 MB
	"32X":  32 * 1024 * 1024,  // 32 MB
	"NDS":  512 * 1024 * 1024, // 512 MB
	"PSP":  2 * 1024 * 1024 * 1024, // 2 GB
	"C64":  1 * 1024 * 1024,   // 1 MB
}

const datBaseURL = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/no-intro"

const redumpBaseURL = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/redump"

// AbbreviationToRedump maps console abbreviations to Redump DAT file names.
// Includes both new non-playable disc-based consoles and existing disc-based systems.
var AbbreviationToRedump = map[string]string{
	// New non-playable disc-based consoles
	"PS3":  "Sony - PlayStation 3",
	"X360": "Microsoft - Xbox 360",
	"WII":  "Nintendo - Wii",
	// Existing disc-based systems
	"PSX":  "Sony - PlayStation",
	"PS2":  "Sony - PlayStation 2",
	"SAT":  "Sega - Saturn",
	"DC":   "Sega - Dreamcast",
	"GC":   "Nintendo - GameCube",
	"SCD":  "Sega - Mega-CD - Sega CD",
	"PSP":  "Sony - PlayStation Portable",
	"PCFX": "NEC - PC-FX",
}

// DATCache manages downloading, caching, and parsing No-Intro and Redump DAT files.
type DATCache struct {
	dir           string
	client        *http.Client
	mu            sync.Mutex
	indices       map[string]*DATIndex // consoleAbbrev → parsed No-Intro index
	redumpIndices map[string]*DATIndex // consoleAbbrev → parsed Redump index
}

// NewDATCache creates a new DAT cache that stores files in dir.
func NewDATCache(dir string, client *http.Client) *DATCache {
	return &DATCache{
		dir:           dir,
		client:        client,
		indices:       make(map[string]*DATIndex),
		redumpIndices: make(map[string]*DATIndex),
	}
}

// Dir returns the directory where DAT files are stored.
func (c *DATCache) Dir() string {
	return c.dir
}

// GetIndex returns the parsed DAT index for the given console abbreviation.
// It loads and parses the bundled DAT file from disk if not already in memory.
// Returns nil, nil for disc-based systems, unmapped systems, or if the file is missing.
func (c *DATCache) GetIndex(consoleAbbrev string) (*DATIndex, error) {
	if DiscBasedSystems[consoleAbbrev] {
		return nil, nil
	}

	systemName, ok := AbbreviationToLibRetro[consoleAbbrev]
	if !ok {
		return nil, nil
	}

	c.mu.Lock()
	defer c.mu.Unlock()

	// Return from memory cache if available
	if idx, ok := c.indices[consoleAbbrev]; ok {
		return idx, nil
	}

	// Load from disk (bundled DAT files)
	datPath := filepath.Join(c.dir, systemName+".dat")
	if _, err := os.Stat(datPath); err == nil {
		idx, err := c.parseFile(datPath)
		if err == nil {
			c.indices[consoleAbbrev] = idx
			return idx, nil
		}
		slog.Warn("failed to parse DAT file", "path", datPath, "error", err)
	}

	// File not on disk — return nil (no download attempt)
	return nil, nil
}

// GetIndexForNameLookup returns a DAT index for name-based lookups (not CRC).
// Unlike GetIndex, this does NOT skip disc-based systems — it's used for
// resolving MAME short names to full titles where we need the DAT's
// name→description mapping but don't care about CRC verification.
func (c *DATCache) GetIndexForNameLookup(consoleAbbrev string) (*DATIndex, error) {
	systemName, ok := AbbreviationToLibRetro[consoleAbbrev]
	if !ok {
		return nil, nil
	}

	c.mu.Lock()
	defer c.mu.Unlock()

	if idx, ok := c.indices[consoleAbbrev]; ok {
		return idx, nil
	}

	datPath := filepath.Join(c.dir, systemName+".dat")
	if _, err := os.Stat(datPath); err == nil {
		idx, err := c.parseFile(datPath)
		if err == nil {
			c.indices[consoleAbbrev] = idx
			return idx, nil
		}
		slog.Warn("failed to parse DAT file for name lookup", "path", datPath, "error", err)
	}

	return nil, nil
}

// nameOnlyDATSystems lists disc-based/arcade systems where we still want the
// DAT file for name→description lookups (e.g. MAME short name resolution),
// even though CRC verification is skipped.
var nameOnlyDATSystems = map[string]bool{
	"ARCADE": true,
	"NEOGEO": true,
}

// RefreshAll downloads/updates DAT files for all mapped non-disc-based systems.
func (c *DATCache) RefreshAll() {
	if err := os.MkdirAll(c.dir, 0o755); err != nil {
		slog.Warn("failed to create DAT dir for refresh", "dir", c.dir, "error", err)
		return
	}

	var ok, failures int
	for consoleAbbrev, systemName := range AbbreviationToLibRetro {
		if DiscBasedSystems[consoleAbbrev] && !nameOnlyDATSystems[consoleAbbrev] {
			continue
		}

		datPath := filepath.Join(c.dir, systemName+".dat")
		idx, err := c.downloadAndCache(consoleAbbrev, systemName, datPath)
		if err != nil {
			slog.Warn("failed to refresh DAT file", "system", systemName, "error", err)
			failures++
			continue
		}

		c.mu.Lock()
		c.indices[consoleAbbrev] = idx
		c.mu.Unlock()

		ok++
	}

	slog.Info("DAT refresh complete", "refreshed", ok, "failures", failures)
}

// downloadAndCache downloads a No-Intro DAT file, saves it to disk, then parses it.
func (c *DATCache) downloadAndCache(consoleAbbrev, systemName, datPath string) (*DATIndex, error) {
	return c.downloadAndCacheFromURL(consoleAbbrev, systemName, datPath, datBaseURL)
}

// downloadAndCacheFromURL downloads a DAT file from the given base URL, saves it to disk, then parses it.
func (c *DATCache) downloadAndCacheFromURL(consoleAbbrev, systemName, datPath, baseURL string) (*DATIndex, error) {
	datURL := fmt.Sprintf("%s/%s.dat", baseURL, url.PathEscape(systemName))

	resp, err := c.client.Get(datURL)
	if err != nil {
		return nil, fmt.Errorf("downloading DAT for %s: %w", consoleAbbrev, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("downloading DAT for %s: HTTP %d", consoleAbbrev, resp.StatusCode)
	}

	// Save to disk first
	if err := os.MkdirAll(filepath.Dir(datPath), 0o755); err != nil {
		slog.Warn("failed to create DAT cache dir", "error", err)
	}

	f, err := os.Create(datPath)
	if err != nil {
		return nil, fmt.Errorf("creating DAT cache file: %w", err)
	}

	if _, err := io.Copy(f, resp.Body); err != nil {
		f.Close()
		return nil, fmt.Errorf("writing DAT cache file: %w", err)
	}
	f.Close()

	// Parse from disk
	return c.parseFile(datPath)
}

// GetRedumpIndex returns the parsed Redump DAT index for the given console abbreviation.
// It loads and parses the bundled Redump DAT file from disk if not already in memory.
// Returns nil, nil for unmapped systems or if the file is missing.
func (c *DATCache) GetRedumpIndex(consoleAbbrev string) (*DATIndex, error) {
	systemName, ok := AbbreviationToRedump[consoleAbbrev]
	if !ok {
		return nil, nil
	}

	c.mu.Lock()
	defer c.mu.Unlock()

	// Return from memory cache if available
	if idx, ok := c.redumpIndices[consoleAbbrev]; ok {
		return idx, nil
	}

	// Load from disk (bundled Redump DAT files stored in redump/ subdirectory)
	datPath := filepath.Join(c.dir, "redump", systemName+".dat")
	if _, err := os.Stat(datPath); err == nil {
		idx, err := c.parseFile(datPath)
		if err == nil {
			c.redumpIndices[consoleAbbrev] = idx
			return idx, nil
		}
		slog.Warn("failed to parse Redump DAT file", "path", datPath, "error", err)
	}

	// File not on disk — return nil (no download attempt)
	return nil, nil
}

// RefreshRedump downloads/updates Redump DAT files for all mapped systems.
func (c *DATCache) RefreshRedump() {
	redumpDir := filepath.Join(c.dir, "redump")
	if err := os.MkdirAll(redumpDir, 0o755); err != nil {
		slog.Warn("failed to create Redump DAT dir for refresh", "dir", redumpDir, "error", err)
		return
	}

	var ok, failures int
	for consoleAbbrev, systemName := range AbbreviationToRedump {
		datPath := filepath.Join(redumpDir, systemName+".dat")
		idx, err := c.downloadAndCacheFromURL(consoleAbbrev, systemName, datPath, redumpBaseURL)
		if err != nil {
			slog.Warn("failed to refresh Redump DAT file", "system", systemName, "error", err)
			failures++
			continue
		}

		c.mu.Lock()
		c.redumpIndices[consoleAbbrev] = idx
		c.mu.Unlock()

		ok++
	}

	slog.Info("Redump DAT refresh complete", "refreshed", ok, "failures", failures)
}

func (c *DATCache) parseFile(path string) (*DATIndex, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	return ParseDAT(f)
}
