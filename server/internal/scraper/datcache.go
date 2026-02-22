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

// discBasedSystems lists console abbreviations where CRC-based identification
// is impractical (disc images are too large, and No-Intro DATs don't cover them).
var discBasedSystems = map[string]bool{
	"PSX": true,
	"SAT": true,
	"DC":  true,
	"SCD": true,
	"PS2": true,
}

const datBaseURL = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/no-intro"

// DATCache manages downloading, caching, and parsing No-Intro DAT files.
type DATCache struct {
	dir     string
	client  *http.Client
	mu      sync.Mutex
	indices map[string]*DATIndex // consoleAbbrev → parsed index
}

// NewDATCache creates a new DAT cache that stores files in dir.
func NewDATCache(dir string, client *http.Client) *DATCache {
	return &DATCache{
		dir:     dir,
		client:  client,
		indices: make(map[string]*DATIndex),
	}
}

// GetIndex returns the parsed DAT index for the given console abbreviation.
// It loads and parses the bundled DAT file from disk if not already in memory.
// Returns nil, nil for disc-based systems, unmapped systems, or if the file is missing.
func (c *DATCache) GetIndex(consoleAbbrev string) (*DATIndex, error) {
	if discBasedSystems[consoleAbbrev] {
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

// RefreshAll downloads/updates DAT files for all mapped non-disc-based systems.
func (c *DATCache) RefreshAll() {
	if err := os.MkdirAll(c.dir, 0o755); err != nil {
		slog.Warn("failed to create DAT dir for refresh", "dir", c.dir, "error", err)
		return
	}

	var ok, failures int
	for consoleAbbrev, systemName := range AbbreviationToLibRetro {
		if discBasedSystems[consoleAbbrev] {
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

// downloadAndCache downloads a DAT file, saves it to disk, then parses it.
func (c *DATCache) downloadAndCache(consoleAbbrev, systemName, datPath string) (*DATIndex, error) {
	datURL := fmt.Sprintf("%s/%s.dat", datBaseURL, url.PathEscape(systemName))

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

func (c *DATCache) parseFile(path string) (*DATIndex, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	return ParseDAT(f)
}
