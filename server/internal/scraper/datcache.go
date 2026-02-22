package scraper

import (
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
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
// It lazily downloads and parses the DAT file if not already cached.
// Returns nil, nil for disc-based systems or systems without a LibRetro mapping.
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

	// Try loading from disk cache
	datPath := filepath.Join(c.dir, systemName+".dat")
	if _, err := os.Stat(datPath); err == nil {
		idx, err := c.parseFile(datPath)
		if err == nil {
			c.indices[consoleAbbrev] = idx
			return idx, nil
		}
		slog.Warn("failed to parse cached DAT file, re-downloading", "path", datPath, "error", err)
	}

	// Download from GitHub
	idx, err := c.downloadAndCache(consoleAbbrev, systemName, datPath)
	if err != nil {
		return nil, err
	}

	c.indices[consoleAbbrev] = idx
	return idx, nil
}

// RefreshAll re-downloads all DAT files that are already cached on disk.
func (c *DATCache) RefreshAll() {
	entries, err := os.ReadDir(c.dir)
	if err != nil {
		slog.Debug("no DAT cache dir to refresh", "dir", c.dir, "error", err)
		return
	}

	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".dat") {
			continue
		}

		systemName := strings.TrimSuffix(entry.Name(), ".dat")

		// Find the console abbreviation for this system name
		var consoleAbbrev string
		for abbrev, name := range AbbreviationToLibRetro {
			if name == systemName {
				consoleAbbrev = abbrev
				break
			}
		}
		if consoleAbbrev == "" {
			continue
		}

		datPath := filepath.Join(c.dir, entry.Name())
		idx, err := c.downloadAndCache(consoleAbbrev, systemName, datPath)
		if err != nil {
			slog.Warn("failed to refresh DAT file", "system", systemName, "error", err)
			continue
		}

		c.mu.Lock()
		c.indices[consoleAbbrev] = idx
		c.mu.Unlock()

		slog.Info("refreshed DAT file", "system", systemName)
	}
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
