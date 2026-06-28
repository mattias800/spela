package db

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// consolesWithoutCatalogMetadata are the registry entries that intentionally
// carry no code/maker/media yet — newer platforms with no IGDB entry. Keeping
// this list explicit means adding a console without metadata (or a console
// silently *losing* its metadata) fails the completeness check below until the
// gap is a conscious choice. This is exactly the drift that previously let
// ZXS/CPC/X68K/TIC-80 ship with empty metadata unnoticed (#1443).
var consolesWithoutCatalogMetadata = map[string]bool{
	"ZXS":   true,
	"CPC":   true,
	"X68K":  true,
	"TIC80": true,
}

// TestConsoleRegistryMetadataCompleteness guards the console registry against
// the metadata drift that motivated #1443: every console (bar the documented
// no-metadata set) must declare a code, maker and media type, and those
// maker/media codes must resolve to real seeded rows (catching typos like
// MakerCode: "nintndo"). Abbreviations and codes must also be unique, since
// they back unique DB columns / the AfterFind lookup.
func TestConsoleRegistryMetadataCompleteness(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&HardwareMaker{}, &MediaType{}, &MediaTypeCategory{}))
	require.NoError(t, SeedMediaTypeCategories(database))
	require.NoError(t, SeedMediaTypes(database))
	require.NoError(t, SeedHardwareMakers(database))

	makerCodes := map[string]bool{}
	var makers []HardwareMaker
	require.NoError(t, database.Find(&makers).Error)
	for _, m := range makers {
		makerCodes[m.Code] = true
	}
	mediaCodes := map[string]bool{}
	var media []MediaType
	require.NoError(t, database.Find(&media).Error)
	for _, m := range media {
		mediaCodes[m.Code] = true
	}

	seenAbbr := map[string]bool{}
	seenCode := map[string]bool{}
	for _, s := range ConsoleRegistry() {
		require.NotEmpty(t, s.Abbreviation, "registry entry with empty abbreviation: %+v", s)
		assert.Falsef(t, seenAbbr[s.Abbreviation], "duplicate abbreviation %q in registry", s.Abbreviation)
		seenAbbr[s.Abbreviation] = true
		if s.Code != "" {
			assert.Falsef(t, seenCode[s.Code], "duplicate code %q in registry", s.Code)
			seenCode[s.Code] = true
		}

		if consolesWithoutCatalogMetadata[s.Abbreviation] {
			// Keep the allowlist honest: once a console gains a code it must
			// drop out of this set so the completeness check starts guarding it.
			assert.Emptyf(t, s.Code,
				"%s now has metadata — remove it from consolesWithoutCatalogMetadata", s.Abbreviation)
			continue
		}

		assert.NotEmptyf(t, s.Code, "console %s is missing a code", s.Abbreviation)
		assert.NotEmptyf(t, s.MakerCode, "console %s is missing a maker code", s.Abbreviation)
		assert.NotEmptyf(t, s.MediaCode, "console %s is missing a media code", s.Abbreviation)
		assert.Truef(t, makerCodes[s.MakerCode],
			"console %s references unknown maker code %q", s.Abbreviation, s.MakerCode)
		assert.Truef(t, mediaCodes[s.MediaCode],
			"console %s references unknown media code %q", s.Abbreviation, s.MediaCode)
	}
}
