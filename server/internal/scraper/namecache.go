package scraper

import (
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"
)

// nameEntry represents a single game name from the LibRetro thumbnail listing.
type nameEntry struct {
	Raw        string // original name from listing, without .png
	Normalized string // pre-computed via normalizeName
	Priority   int    // from computePriority
}

// nameCache caches parsed LibRetro thumbnail directory listings per system.
type nameCache struct {
	mu      sync.RWMutex
	entries map[string][]nameEntry // keyed by LibRetro system name
}

// reHref extracts href attributes from HTML anchor tags.
var reHref = regexp.MustCompile(`href="([^"]+)"`)

// parseDirectoryListing extracts game names from a LibRetro thumbnail directory HTML listing.
// It URL-decodes the href values and strips the .png suffix.
func parseDirectoryListing(body string) []string {
	matches := reHref.FindAllStringSubmatch(body, -1)
	var names []string
	for _, m := range matches {
		href := m[1]
		if href == "../" || !strings.HasSuffix(href, ".png") {
			continue
		}
		decoded, err := url.PathUnescape(href)
		if err != nil {
			continue
		}
		name := strings.TrimSuffix(decoded, ".png")
		names = append(names, name)
	}
	return names
}

// getOrLoad returns the cached entries for the given LibRetro system name,
// fetching and parsing the directory listing if not yet cached.
func (c *nameCache) getOrLoad(system string, httpClient *http.Client) ([]nameEntry, error) {
	// Fast path: read lock
	c.mu.RLock()
	if entries, ok := c.entries[system]; ok {
		c.mu.RUnlock()
		return entries, nil
	}
	c.mu.RUnlock()

	// Slow path: write lock, double-check
	c.mu.Lock()
	defer c.mu.Unlock()

	if entries, ok := c.entries[system]; ok {
		return entries, nil
	}

	listingURL := fmt.Sprintf("%s/%s/Named_Boxarts/",
		libRetroThumbnailBase,
		url.PathEscape(system),
	)

	slog.Info("fetching LibRetro name listing", "system", system, "url", listingURL)

	resp, err := httpClient.Get(listingURL)
	if err != nil {
		return nil, fmt.Errorf("fetching name listing for %s: %w", system, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("name listing for %s returned status %d", system, resp.StatusCode)
	}

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("reading name listing for %s: %w", system, err)
	}

	rawNames := parseDirectoryListing(string(bodyBytes))
	entries := make([]nameEntry, 0, len(rawNames))
	for _, raw := range rawNames {
		entries = append(entries, nameEntry{
			Raw:        raw,
			Normalized: normalizeName(raw),
			Priority:   computePriority(raw),
		})
	}

	slog.Info("cached LibRetro name listing", "system", system, "entries", len(entries))
	c.entries[system] = entries
	return entries, nil
}
