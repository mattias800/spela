package federation

import (
	"strings"
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

func TestSafeCoverURL(t *testing.T) {
	ok := "https://images.igdb.com/igdb/image/upload/t_cover_big/abc.jpg"
	assert.Equal(t, ok, SafeCoverURL(ok), "public IGDB CDN URL is kept")
	assert.Empty(t, SafeCoverURL("/api/images/local.jpg"), "local path dropped (no host leak)")
	assert.Empty(t, SafeCoverURL("http://images.igdb.com/x.jpg"), "non-https dropped")
	assert.Empty(t, SafeCoverURL("https://evil.example.com/x.jpg"), "non-IGDB host dropped")
	assert.Empty(t, SafeCoverURL(""), "empty stays empty")
	assert.Empty(t, SafeCoverURL(igdbCoverPrefix+strings.Repeat("a", 600)), "overlong dropped")
}

func TestBuildLocalCatalog_EmitsOnlyIgdbCovers(t *testing.T) {
	database := openCatalogTestDB(t)
	console := db.Console{Name: "Super Nintendo", Abbreviation: "SNES"}
	require.NoError(t, database.Create(&console).Error)
	const igdbCover = "https://images.igdb.com/igdb/image/upload/t_cover_big/abc.jpg"
	require.NoError(t, database.Create(&db.Game{
		Title: "Cover Game", ScraperID: "igdb:7", FilePath: "/a", ConsoleID: console.ID,
		IGDBCoverURL: igdbCover,
	}).Error)
	require.NoError(t, database.Create(&db.Game{
		Title: "Local Cover Game", ScraperID: "igdb:8", FilePath: "/b", ConsoleID: console.ID,
		IGDBCoverURL: "/api/images/local.jpg", // local path — must not leak across the mesh
	}).Error)

	entries, err := BuildLocalCatalog(database, "selfFP")
	require.NoError(t, err)
	byKey := map[string]CatalogEntry{}
	for _, e := range entries {
		byKey[e.Key] = e
	}
	assert.Equal(t, igdbCover, byKey["igdb:7"].Cover)
	assert.Empty(t, byKey["igdb:8"].Cover, "a local image path must not be emitted as a federated cover")

	// Cover survives the snapshot round-trip and carries into the aggregate.
	store := CatalogSnapshotStore{DB: database}
	require.NoError(t, store.ReplacePeerSnapshot("B", []CatalogEntry{
		{OriginFingerprint: "B", Hops: 1, Key: "igdb:9", Title: "Remote", Console: "NES", Cover: igdbCover},
	}, time.Unix(1, 0)))
	back, err := store.EntriesWithinHops(-1)
	require.NoError(t, err)
	require.Len(t, back, 1)
	assert.Equal(t, igdbCover, back[0].Cover)
	agg := AggregateCatalog(back, "selfFP")
	require.Len(t, agg, 1)
	assert.Equal(t, igdbCover, agg[0].Cover)
}
