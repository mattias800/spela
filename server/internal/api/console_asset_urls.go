package api

import (
	"crypto/sha256"
	"embed"
	"encoding/hex"
	"sync"
)

// Console asset URLs (icon / logo / logo.png / photo) carry a short content
// hash as a `?v=` query param so that when a bundled asset's bytes change, its
// URL changes and every client re-fetches it. The asset endpoints serve a long
// Cache-Control max-age (a week) with no ETag, so without a content-addressed
// URL a changed asset — e.g. the DOS platform's MS-DOS → IBM logo swap (#1441)
// — would stay stale in browser caches and the player app's Coil disk cache for
// up to a week, and users would have to clear caches by hand. The hash makes
// each URL immutable-per-content: change the file, change the URL, cache busts.
//
// Hashes are memoized; the bytes are embedded so they never change at runtime.
var assetHashCache sync.Map // embedded path -> short hash (or "" when absent)

func assetHash(fsys embed.FS, path string) string {
	if v, ok := assetHashCache.Load(path); ok {
		return v.(string)
	}
	h := ""
	if data, err := fsys.ReadFile(path); err == nil {
		sum := sha256.Sum256(data)
		h = hex.EncodeToString(sum[:])[:10]
	}
	assetHashCache.Store(path, h)
	return h
}

func withVersion(url, hash string) string {
	if hash == "" {
		return url
	}
	return url + "?v=" + hash
}

// consoleIconURL is the icon endpoint for [abbr] (lowercase) with a content
// hash for cache-busting.
func consoleIconURL(abbr string) string {
	return withVersion(
		"/api/consoles/"+abbr+"/icon",
		assetHash(consoleIcons, "static/console-icons/"+abbr+".png"),
	)
}

// consoleLogoURL is the SVG logo endpoint for [abbr] with a content hash. The
// hash reflects the actually-served file, resolving the same parent-platform
// fallback as HumaGetConsoleLogo so demo-scene consoles hash their parent's logo.
func consoleLogoURL(abbr string) string {
	path := "static/console-logos/" + abbr + ".svg"
	if _, err := consoleLogos.ReadFile(path); err != nil {
		if parent, ok := consoleLogoFallbacks[abbr]; ok {
			path = "static/console-logos/" + parent + ".svg"
		}
	}
	return withVersion("/api/consoles/"+abbr+"/logo", assetHash(consoleLogos, path))
}

// consoleLogoPngURL is the PNG logo endpoint for [abbr] with a content hash,
// mirroring consoleLogoURL's fallback resolution.
func consoleLogoPngURL(abbr string) string {
	path := "static/console-logos-png/" + abbr + ".png"
	if _, err := consoleLogosPng.ReadFile(path); err != nil {
		if parent, ok := consoleLogoFallbacks[abbr]; ok {
			path = "static/console-logos-png/" + parent + ".png"
		}
	}
	return withVersion("/api/consoles/"+abbr+"/logo.png", assetHash(consoleLogosPng, path))
}
