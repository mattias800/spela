package db

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// #1513: console file extensions are static, registry-owned facts. They are
// derived in Console.AfterFind from the code registry (ConsoleExtensions) and
// are no longer stored in — or backfilled to — a DB column. This mirrors the
// #1443 treatment of Name / ColorTheme / CoverAspect / Generation.

func TestConsoleExtensions_FromRegistry(t *testing.T) {
	assert.Equal(t, ".nes,.fds", ConsoleExtensions("NES"))
	assert.Equal(t, ".nes,.fds", ConsoleExtensions("nes"), "lookup is case-insensitive")
	assert.Equal(t, ".sfc,.smc", ConsoleExtensions("SNES"))
	assert.Equal(t, "", ConsoleExtensions("NOT_A_CONSOLE"), "unknown console derives empty")
}

func TestConsole_ExtensionsDerivedAndColumnDropped(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&Console{}, &HardwareMaker{}, &MediaType{}, &MediaTypeCategory{}))
	require.NoError(t, SeedConsoles(database))

	// The extensions column is no longer part of the managed schema — the
	// registry is the source of truth, so a fresh AutoMigrate never creates it.
	var scratch string
	colErr := database.Raw("SELECT extensions FROM consoles LIMIT 1").Scan(&scratch).Error
	assert.Error(t, colErr, "extensions column should no longer exist (registry-derived now)")

	// Extensions is still populated on load, derived from the registry.
	var nes Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)
	assert.Equal(t, ".nes,.fds", nes.Extensions, "Extensions must be derived from the registry in AfterFind")
}
