package api

import (
	"strings"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestConsoleAssetURLsAreContentVersioned verifies the icon/logo/logo.png URLs
// in the DTO carry a `?v=` content hash so a changed asset busts client caches
// (browser + the player app's Coil disk cache) instead of staying stale for the
// week-long Cache-Control max-age. This is the fix for the DOS IBM-logo staleness
// (#1441) — users must not have to clear caches by hand.
func TestConsoleAssetURLsAreContentVersioned(t *testing.T) {
	resp := ToConsoleResponse(db.Console{Abbreviation: "NES"})

	for name, url := range map[string]string{
		"icon":     resp.IconURL,
		"logo":     resp.LogoURL,
		"logo.png": resp.LogoPngURL,
	} {
		assert.Contains(t, url, "?v=", "%s URL must carry a content-hash cache-buster: %q", name, url)
	}
}

// TestAssetHashTracksContent verifies the hash is content-derived (different
// files → different hashes) and stable for the same file, so changing one
// asset only re-fetches that asset.
func TestAssetHashTracksContent(t *testing.T) {
	nesLogo := consoleLogoURL("nes")
	snesLogo := consoleLogoURL("snes")
	require.Contains(t, nesLogo, "?v=")
	require.Contains(t, snesLogo, "?v=")

	nesHash := nesLogo[strings.Index(nesLogo, "?v=")+3:]
	snesHash := snesLogo[strings.Index(snesLogo, "?v=")+3:]
	assert.NotEqual(t, nesHash, snesHash, "distinct logos must hash differently")
	assert.Equal(t, nesHash, consoleLogoURL("nes")[strings.Index(consoleLogoURL("nes"), "?v=")+3:],
		"hash must be stable for the same file")
}
