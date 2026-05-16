package db

import (
	"testing"

	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// #1187: live deployments seeded before the buildbot switchover have a
// stale CustomDownloadURL pinned to a github.com/azahar-emu release that
// 404s on Android arm64-v8a and ships an Apple Silicon Vulkan crash.
// MigrateAzaharToBuildbot force-clears that override so the player falls
// back to the libretro buildbot nightly.

func openAzaharTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&Core{}))
	return database
}

func TestMigrateAzaharToBuildbotClearsLegacyURL(t *testing.T) {
	database := openAzaharTestDB(t)

	require.NoError(t, database.Create(&Core{
		Name:              "azahar",
		DisplayName:       "Azahar",
		Platforms:         "windows,linux,macos,android",
		Version:           "2125.0.1",
		CustomDownloadURL: "https://github.com/azahar-emu/azahar/releases/download/2125.0.1/azahar-libretro-{platform}-2125.0.1.zip",
	}).Error)

	require.NoError(t, MigrateAzaharToBuildbot(database))

	var got Core
	require.NoError(t, database.Where("name = ?", "azahar").First(&got).Error)
	require.Equal(t, "", got.CustomDownloadURL, "stale GitHub URL must be cleared so player uses buildbot")
	require.Equal(t, "", got.Version, "stale pinned version must be cleared")
}

func TestMigrateAzaharToBuildbotIdempotent(t *testing.T) {
	database := openAzaharTestDB(t)

	require.NoError(t, database.Create(&Core{
		Name:              "azahar",
		DisplayName:       "Azahar",
		Platforms:         "windows,linux,macos,android",
		Version:           "2125.0-alpha4",
		CustomDownloadURL: "https://github.com/azahar-emu/azahar/releases/download/2125.0-alpha4/azahar-libretro-2125.0-alpha4-{platform}.zip",
	}).Error)

	require.NoError(t, MigrateAzaharToBuildbot(database))
	require.NoError(t, MigrateAzaharToBuildbot(database))

	var got Core
	require.NoError(t, database.Where("name = ?", "azahar").First(&got).Error)
	require.Equal(t, "", got.CustomDownloadURL)
	require.Equal(t, "", got.Version)
}

func TestMigrateAzaharToBuildbotNoRow(t *testing.T) {
	database := openAzaharTestDB(t)

	// Fresh DB with no azahar row — migration must succeed quietly so
	// startup doesn't fail before SeedCores has had a chance to run.
	require.NoError(t, MigrateAzaharToBuildbot(database))
}

func TestMigrateAzaharToBuildbotLeavesCleanRowAlone(t *testing.T) {
	database := openAzaharTestDB(t)

	require.NoError(t, database.Create(&Core{
		Name:        "azahar",
		DisplayName: "Azahar",
		Platforms:   "windows,linux,macos,android",
		Sha256:      "deadbeef",
		SizeBytes:   42,
	}).Error)

	require.NoError(t, MigrateAzaharToBuildbot(database))

	var got Core
	require.NoError(t, database.Where("name = ?", "azahar").First(&got).Error)
	require.Equal(t, "", got.CustomDownloadURL)
	require.Equal(t, "", got.Version)
	// Binary metadata is left intact — the server's cached binary may
	// still be valid (downloaded from a previous buildbot session) and
	// staleness/freshness is the player's call, not the migration's.
	require.Equal(t, "deadbeef", got.Sha256)
	require.Equal(t, int64(42), got.SizeBytes)
}
