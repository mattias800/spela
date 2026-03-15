package scraper

import (
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"path/filepath"
	"strings"
)

var libRetroThumbnailBase = "https://thumbnails.libretro.com"

// gameNameFromFileName strips the file extension to derive the game name.
func gameNameFromFileName(fileName string) string {
	ext := filepath.Ext(fileName)
	return strings.TrimSuffix(fileName, ext)
}

// tryDownloadImage attempts to download a single image by exact name from LibRetro Thumbnails.
// Returns the relative storage path on success, or empty string on failure.
func (s *Scraper) tryDownloadImage(system, name, imageType, subpath string) string {
	imageURL := fmt.Sprintf("%s/%s/%s/%s.png",
		libRetroThumbnailBase,
		url.PathEscape(system),
		imageType,
		url.PathEscape(name),
	)

	resp, err := s.HTTPClient.Get(imageURL)
	if err != nil {
		slog.Debug("failed to fetch LibRetro image", "url", imageURL, "error", err)
		return ""
	}

	if resp.StatusCode != http.StatusOK {
		resp.Body.Close()
		slog.Debug("LibRetro image not found", "url", imageURL, "status", resp.StatusCode)
		return ""
	}

	savedPath, err := s.Storage.WriteImage(subpath, resp.Body)
	resp.Body.Close()
	if err != nil {
		slog.Warn("failed to save LibRetro image", "subpath", subpath, "error", err)
		return ""
	}

	slog.Debug("downloaded LibRetro image", "name", name, "type", imageType)
	return savedPath
}

// downloadLibRetroImage downloads an image from LibRetro Thumbnails and saves it locally.
// When the game name contains a non-preferred region (e.g. Japan, Europe), it skips
// the exact name match and goes straight to fuzzy matching, which prefers USA/World
// entries via hasPreferredRegion tie-breaking. For games with preferred regions only
// (USA, World) or no region tags, it tries the exact name first as a fast path.
// Returns the relative storage path on success, or empty string if the image was not found.
func (s *Scraper) downloadLibRetroImage(system, gameName, imageType, subpath string) string {
	// Fast path: try exact name match when the name already has a preferred
	// region or no region at all. Skip this for non-preferred regions so the
	// fuzzy matcher can find a USA/World alternative.
	if !hasNonPreferredRegion(gameName) {
		if path := s.tryDownloadImage(system, gameName, imageType, subpath); path != "" {
			return path
		}
	}

	// Fuzzy matching: prefers USA/World entries via hasPreferredRegion tie-breaking.
	entries, err := s.cache.getOrLoad(system, s.HTTPClient)
	if err != nil {
		slog.Warn("failed to load LibRetro name listing", "system", system, "error", err)
		return ""
	}

	normalized := normalizeName(gameName)
	match, score, found := findBestMatch(normalized, entries, 0.88)
	if !found {
		slog.Debug("no fuzzy match found", "game", gameName, "normalized", normalized)
		return ""
	}

	slog.Info("fuzzy matched", "original", gameName, "matched", match.Raw, "score", score)
	return s.tryDownloadImage(system, match.Raw, imageType, subpath)
}

// FindRegionalVariants returns all regional box art variants available on LibRetro
// for the given game. It searches the LibRetro name cache for entries that match
// the game name (by normalized fuzzy matching) and have different region tags.
// The currentLibRetroName is excluded from the results if provided.
func (s *Scraper) FindRegionalVariants(consoleAbbr, gameName string) []RegionalVariant {
	system, ok := AbbreviationToLibRetro[consoleAbbr]
	if !ok {
		return nil
	}

	entries, err := s.cache.getOrLoad(system, s.HTTPClient)
	if err != nil {
		slog.Warn("failed to load LibRetro listing for regional variants", "system", system, "error", err)
		return nil
	}

	normalized := normalizeName(gameName)

	// Find which entry was used as the default cover (the best match from scraping)
	// so we can exclude it from the regional variants list.
	bestMatch, _, bestFound := findBestMatch(normalized, entries, 0.88)

	matches := findAllMatches(normalized, entries, 0.88)

	var variants []RegionalVariant
	for _, m := range matches {
		// Skip low-quality entries
		if m.Priority > 2 {
			continue
		}
		// Skip the entry that was used as the default cover
		if bestFound && m.Raw == bestMatch.Raw {
			continue
		}
		region := ExtractRegion(m.Raw)
		if region == "" {
			region = "Unknown"
		}
		variants = append(variants, RegionalVariant{
			LibRetroName: m.Raw,
			Region:       region,
		})
	}

	return variants
}

// DownloadRegionalCover downloads a specific LibRetro box art by its raw name
// and saves it to the given subpath. Returns the stored path or empty string on failure.
func (s *Scraper) DownloadRegionalCover(consoleAbbr, libRetroName, subpath string) string {
	system, ok := AbbreviationToLibRetro[consoleAbbr]
	if !ok {
		return ""
	}
	return s.tryDownloadImage(system, libRetroName, "Named_Boxarts", subpath)
}

// DownloadLibRetroBoxart downloads a LibRetro Named_Boxarts cover for a game title.
// Uses fuzzy matching to find the best match. Returns the local storage path or "".
func (s *Scraper) DownloadLibRetroBoxart(system, gameTitle, subpath string) string {
	return s.downloadLibRetroImage(system, gameTitle, "Named_Boxarts", subpath)
}

// LibRetroThumbnailURL constructs the direct URL to a LibRetro thumbnail.
func LibRetroThumbnailURL(consoleAbbr, libRetroName string) string {
	system, ok := AbbreviationToLibRetro[consoleAbbr]
	if !ok {
		return ""
	}
	return fmt.Sprintf("%s/%s/Named_Boxarts/%s.png",
		libRetroThumbnailBase,
		url.PathEscape(system),
		url.PathEscape(libRetroName),
	)
}

// ResolveFullTitle resolves a short ROM name (e.g. MAME code "mslug") to
// its full game title (e.g. "Metal Slug - Super Vehicle-001").
//
// Resolution strategy (in order):
// 1. DAT file lookup — if a MAME/FBNeo DAT is loaded, the `description`
//    field provides an authoritative name→title mapping for every ROM.
// 2. LibRetro fuzzy match — falls back to matching against the LibRetro
//    thumbnail directory listing (works when short name normalizes close
//    to the full title, e.g. "akkaarrh" → "Akka Arrh").
func (s *Scraper) ResolveFullTitle(consoleAbbr, shortName string) string {
	// Strategy 1: DAT file name→description lookup (authoritative, covers all ROMs)
	if idx, err := s.DATCache.GetIndexForNameLookup(consoleAbbr); err == nil && idx != nil {
		if entry, ok := idx.LookupName(shortName); ok && entry.Description != "" {
			// Strip region/version tags from description
			title := entry.Description
			if i := strings.Index(title, "("); i > 0 {
				title = strings.TrimSpace(title[:i])
			}
			slog.Info("resolved ROM name via DAT file",
				"short", shortName, "full", title, "description", entry.Description)
			return title
		}
	}

	// Strategy 2: LibRetro fuzzy match (fallback)
	system, ok := AbbreviationToLibRetro[consoleAbbr]
	if !ok {
		return ""
	}

	entries, err := s.cache.getOrLoad(system, s.HTTPClient)
	if err != nil || len(entries) == 0 {
		return ""
	}

	normalized := normalizeName(shortName)
	match, score, found := findBestMatch(normalized, entries, 0.85)
	if !found {
		return ""
	}

	title := match.Raw
	if i := strings.Index(title, "("); i > 0 {
		title = strings.TrimSpace(title[:i])
	}

	slog.Info("resolved ROM name via LibRetro fuzzy match",
		"short", shortName, "full", title, "raw", match.Raw, "score", score)
	return title
}
