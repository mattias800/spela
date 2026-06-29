package db

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// #1513: a console's on-disk folder name is a static, registry-owned fact. It
// is derived in Console.AfterFind from the code registry (ConsoleFolderName)
// and no longer stored in / backfilled to a DB column. Filesystem-coupled, so
// the raw-SQL read path in MigrateToRelativePaths is converted to read the
// registry directly (guarded by the existing dedup_test MigrateToRelativePaths
// tests).

func TestConsoleFolderName_FromRegistry(t *testing.T) {
	assert.Equal(t, "nes", ConsoleFolderName("NES"))
	assert.Equal(t, "nes", ConsoleFolderName("nes"), "lookup is case-insensitive")
	assert.Equal(t, "mastersystem", ConsoleFolderName("SMS"), "folder name differs from abbreviation")
	assert.Equal(t, "", ConsoleFolderName("NOT_A_CONSOLE"), "unknown console derives empty")
}

func TestConsole_FolderNameDerivedAndColumnDropped(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&Console{}, &HardwareMaker{}, &MediaType{}, &MediaTypeCategory{}))
	require.NoError(t, SeedConsoles(database))

	// The folder_name column is no longer part of the managed schema.
	var scratch string
	colErr := database.Raw("SELECT folder_name FROM consoles LIMIT 1").Scan(&scratch).Error
	assert.Error(t, colErr, "folder_name column should no longer exist (registry-derived now)")

	// FolderName is still populated on load, derived from the registry.
	var sms Console
	require.NoError(t, database.Where("abbreviation = ?", "SMS").First(&sms).Error)
	assert.Equal(t, "mastersystem", sms.FolderName, "FolderName must be derived from the registry in AfterFind")
}
