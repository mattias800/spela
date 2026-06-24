package api

import (
	"encoding/json"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type photoCredit struct {
	Console string `json:"console"`
	File    string `json:"file"`
	Author  string `json:"author"`
	License string `json:"license"`
	Source  string `json:"source"`
}

// TestBundledConsolePhotosAreReadableAndCredited verifies that every bundled
// console photo (a) actually reads from the embedded FS via the production
// lookup, and (b) has a complete attribution entry in CREDITS.json — author,
// license, and source. This is the license-compliance guard for #1441: we must
// never ship a bundled image without recording who made it and under what
// license (several are CC-BY-SA and legally require attribution).
func TestBundledConsolePhotosAreReadableAndCredited(t *testing.T) {
	require.NotEmpty(t, consolePhotoFiles, "no bundled console photos found")

	raw, err := consolePhotos.ReadFile("static/console-photos/CREDITS.json")
	require.NoError(t, err, "CREDITS.json must be bundled alongside the photos")
	var manifest struct {
		Photos []photoCredit `json:"photos"`
	}
	require.NoError(t, json.Unmarshal(raw, &manifest))
	credits := make(map[string]photoCredit, len(manifest.Photos))
	for _, c := range manifest.Photos {
		credits[c.Console] = c
	}

	for abbr, ext := range consolePhotoFiles {
		abbr, ext := abbr, ext
		t.Run(abbr, func(t *testing.T) {
			data, readErr := consolePhotos.ReadFile("static/console-photos/" + abbr + "." + ext)
			require.NoError(t, readErr)
			assert.NotEmpty(t, data)

			c, ok := credits[abbr]
			require.True(t, ok, "%s.%s is bundled but missing from CREDITS.json", abbr, ext)
			assert.NotEmpty(t, c.Author, "%s has no author credit", abbr)
			assert.NotEmpty(t, c.License, "%s has no license recorded", abbr)
			assert.NotEmpty(t, c.Source, "%s has no source URL", abbr)
		})
	}
}

// TestConsolePhotoCreditsManifestCoversEveryBundledPhoto verifies the manifest
// served by the credits endpoint has a complete, decoded entry for every bundled
// photo. This is what makes the attribution actually reach users (the Credits &
// Licenses screens render it), so it must stay in sync with the bundled files.
func TestConsolePhotoCreditsManifestCoversEveryBundledPhoto(t *testing.T) {
	m := consolePhotoCreditsManifest
	require.NotEmpty(t, m.Note, "credits manifest must carry the attribution note")
	require.Len(t, m.Photos, len(consolePhotoFiles), "every bundled photo needs a credit")

	for _, c := range m.Photos {
		c := c
		t.Run(c.Console, func(t *testing.T) {
			_, ok := consolePhotoFiles[c.Console]
			assert.True(t, ok, "%s credited but not bundled", c.Console)
			assert.NotEmpty(t, c.Author, "%s credit missing author", c.Console)
			assert.NotEmpty(t, c.License, "%s credit missing license", c.Console)
			assert.NotEmpty(t, c.Source, "%s credit missing source", c.Console)
			assert.NotContains(t, c.Author, "&amp;", "%s author should be HTML-unescaped", c.Console)
		})
	}
}

// TestConsolePhotoURLGating verifies the DTO carries the photo endpoint only
// for consoles we actually bundle a photo for, and null otherwise (so the UI
// has an explicit signal to fall back to the logo/watermark).
func TestConsolePhotoURLGating(t *testing.T) {
	bundled := ToConsoleResponse(db.Console{Abbreviation: "NES"})
	require.NotNil(t, bundled.PhotoURL, "NES is bundled, expected a photo URL")
	assert.Equal(t, "/api/consoles/nes/photo", *bundled.PhotoURL)

	// scummvm is intentionally not bundled (it's an engine, not hardware).
	none := ToConsoleResponse(db.Console{Abbreviation: "SCUMMVM"})
	assert.Nil(t, none.PhotoURL, "SCUMMVM has no bundled photo, expected null")

	// Amiga Demos has no photo of its own but inherits the Amiga photo via the
	// parent-platform fallback (#1441).
	demos := ToConsoleResponse(db.Console{Abbreviation: "ADEMO"})
	require.NotNil(t, demos.PhotoURL, "ADEMO should inherit the Amiga photo")
	assert.Equal(t, "/api/consoles/ademo/photo", *demos.PhotoURL)
}

// TestConsolePhotoFallbackServesParent verifies the photo handler resolves a
// child console (Amiga Demos) to its parent platform's bundled photo file.
func TestConsolePhotoFallbackServesParent(t *testing.T) {
	resolved, ok := consolePhotoFor("ademo")
	require.True(t, ok, "ademo should resolve to a parent photo")
	assert.Equal(t, "amiga", resolved, "ademo inherits the Amiga photo")

	// A console with neither its own nor a parent photo resolves to nothing.
	_, ok = consolePhotoFor("scummvm")
	assert.False(t, ok)
}
