package api

import (
	"strings"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

// TestEveryConsoleHasLogoAndIcon asserts that every console seeded by
// db.SeedConsoles has both a logo SVG (served by GET /api/consoles/{id}/logo)
// and a console icon PNG (served by GET /api/consoles/{id}/icon).
//
// This catches the silent-404 failure mode where a new platform lands in
// the DB seed but the embedded asset files were forgotten. The UI surfaces
// the platform but every browse view shows broken images for it.
//
// Lookup paths mirror the production handlers:
//   - Logo:  static/console-logos/<lowercase abbreviation>.svg
//            (HumaGetConsoleLogo in huma_downloads.go)
//   - Icon:  static/console-icons/<lowercase abbreviation>.png
//            (HumaGetConsoleIcon in huma_downloads.go)
//
// Consoles that fall back to a parent platform's asset via
// consoleLogoFallbacks (e.g. ADEMO → AMIGA, DDEMO → DOS) are exempt from
// the direct-asset assertion; the fallback row is checked instead.
//
// Future child consoles get a sensible default with one map entry in
// consoleLogoFallbacks (see HumaGetConsoleLogo's docstring).
func TestEveryConsoleHasLogoAndIcon(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.Console{}))
	require.NoError(t, db.SeedConsoles(database))

	var consoles []db.Console
	require.NoError(t, database.Find(&consoles).Error)
	require.NotEmpty(t, consoles, "SeedConsoles produced no rows")

	for _, c := range consoles {
		c := c
		t.Run(c.Abbreviation, func(t *testing.T) {
			abbrev := strings.ToLower(c.Abbreviation)
			lookupAbbrev := abbrev
			if fallback, ok := consoleLogoFallbacks[abbrev]; ok {
				lookupAbbrev = fallback
			}

			// Logo SVG (served as image/svg+xml; the handler also has a
			// PNG fallback path but we only assert the canonical SVG —
			// the PNG variant is generated from the SVG by scripts/
			// generate-logo-pngs.sh).
			logoPath := "static/console-logos/" + lookupAbbrev + ".svg"
			_, logoErr := consoleLogos.ReadFile(logoPath)
			assert.NoError(t, logoErr,
				"console %s (looked up as %q.svg) has no logo SVG in the "+
					"embedded asset directory — add %s or, for child "+
					"consoles, add an entry to consoleLogoFallbacks",
				c.Abbreviation, lookupAbbrev, logoPath)

			// Console icon PNG (served as image/png).
			iconPath := "static/console-icons/" + lookupAbbrev + ".png"
			_, iconErr := consoleIcons.ReadFile(iconPath)
			assert.NoError(t, iconErr,
				"console %s (looked up as %q.png) has no console-icon PNG "+
					"in the embedded asset directory — add %s",
				c.Abbreviation, lookupAbbrev, iconPath)
		})
	}
}
