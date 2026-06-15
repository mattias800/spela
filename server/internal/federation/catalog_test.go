package federation

import (
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func openCatalogTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.Console{}, &db.Game{}, &db.FederationCatalogSnapshot{}))
	return database
}

func TestBuildLocalCatalog_StampsAndSkipsUnidentifiable(t *testing.T) {
	database := openCatalogTestDB(t)
	console := db.Console{Name: "Super Nintendo", Abbreviation: "SNES"}
	require.NoError(t, database.Create(&console).Error)

	// Identifiable (scraper id), identifiable (crc), and unidentifiable (neither).
	require.NoError(t, database.Create(&db.Game{Title: "Game A", ScraperID: "igdb:1", FilePath: "/a", ConsoleID: console.ID}).Error)
	require.NoError(t, database.Create(&db.Game{Title: "Game B", CRC32: "deadbeef", FilePath: "/b", ConsoleID: console.ID}).Error)
	require.NoError(t, database.Create(&db.Game{Title: "Homebrew", FilePath: "/c", ConsoleID: console.ID}).Error)

	entries, err := BuildLocalCatalog(database, "selfFP")
	require.NoError(t, err)
	require.Len(t, entries, 2, "unidentifiable game is not federated")

	keys := map[string]CatalogEntry{}
	for _, e := range entries {
		keys[e.Key] = e
		assert.Equal(t, "selfFP", e.OriginFingerprint)
		assert.Equal(t, 0, e.Hops)
		assert.Equal(t, "SNES", e.Console)
	}
	assert.Contains(t, keys, "igdb:1")
	assert.Contains(t, keys, "crc:deadbeef")
}

func TestCatalogSnapshotStore_ReplaceFilterRemove(t *testing.T) {
	store := CatalogSnapshotStore{DB: openCatalogTestDB(t)}
	require.NoError(t, store.ReplacePeerSnapshot("B", []CatalogEntry{
		{OriginFingerprint: "B", Hops: 1, Key: "k1", Title: "T1", Console: "NES"},
		{OriginFingerprint: "C", Hops: 3, Key: "k2", Title: "T2", Console: "NES"},
	}, time.Unix(1, 0)))
	// Replace is idempotent (no duplication).
	require.NoError(t, store.ReplacePeerSnapshot("B", []CatalogEntry{
		{OriginFingerprint: "B", Hops: 1, Key: "k1", Title: "T1", Console: "NES"},
		{OriginFingerprint: "C", Hops: 3, Key: "k2", Title: "T2", Console: "NES"},
	}, time.Unix(1, 0)))

	within1, err := store.EntriesWithinHops(1)
	require.NoError(t, err)
	assert.Len(t, within1, 1, "hop filter excludes the hop-3 entry")

	all, err := store.EntriesWithinHops(-1)
	require.NoError(t, err)
	assert.Len(t, all, 2)

	require.NoError(t, store.RemovePeerSnapshot("B"))
	all, err = store.EntriesWithinHops(-1)
	require.NoError(t, err)
	assert.Empty(t, all)
}

func TestAggregateCatalog_CountsOriginsAndFlagsLocal(t *testing.T) {
	entries := []CatalogEntry{
		{OriginFingerprint: "self", Hops: 0, Key: "g1", Title: "Game 1", Console: "SNES"},
		{OriginFingerprint: "B", Hops: 1, Key: "g1", Title: "Game 1", Console: "SNES"},
		{OriginFingerprint: "B", Hops: 1, Key: "g1", Title: "Game 1", Console: "SNES"}, // dup (origin,key) → ignored
		{OriginFingerprint: "C", Hops: 2, Key: "g2", Title: "Game 2", Console: "NES"},
	}
	out := AggregateCatalog(entries, "self")
	require.Len(t, out, 2)

	byKey := map[string]CatalogAvailability{}
	for _, a := range out {
		byKey[a.Key] = a
	}
	assert.Equal(t, 2, byKey["g1"].OriginCount, "self + B, dup ignored")
	assert.True(t, byKey["g1"].Local)
	assert.Equal(t, 1, byKey["g2"].OriginCount)
	assert.False(t, byKey["g2"].Local, "g2 only on a remote server")
}
