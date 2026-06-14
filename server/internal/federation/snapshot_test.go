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

func openSnapshotTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.FederationStatSnapshot{}))
	return database
}

func TestSnapshotStore_ReplaceIsIdempotentPerPeer(t *testing.T) {
	store := SnapshotStore{DB: openSnapshotTestDB(t)}
	now := time.Unix(1000, 0)

	entries := []StatEntry{
		{OriginFingerprint: "C", Hops: 1, Metric: MetricGamePlay, Key: "g1", PlayTimeSeconds: 50},
		{OriginFingerprint: "D", Hops: 2, Metric: MetricGamePlay, Key: "g2", PlayTimeSeconds: 70},
	}
	require.NoError(t, store.ReplacePeerSnapshot("B", entries, now))
	require.NoError(t, store.ReplacePeerSnapshot("B", entries, now)) // replace, not append

	got, err := store.EntriesWithinHops(-1)
	require.NoError(t, err)
	assert.Len(t, got, 2, "replace must not duplicate rows")
}

func TestSnapshotStore_FiltersByHops(t *testing.T) {
	store := SnapshotStore{DB: openSnapshotTestDB(t)}
	require.NoError(t, store.ReplacePeerSnapshot("B", []StatEntry{
		{OriginFingerprint: "B", Hops: 1, Metric: MetricGamePlay, Key: "g1"},
		{OriginFingerprint: "C", Hops: 2, Metric: MetricGamePlay, Key: "g2"},
		{OriginFingerprint: "D", Hops: 3, Metric: MetricGamePlay, Key: "g3"},
	}, time.Unix(1, 0)))

	within2, err := store.EntriesWithinHops(2)
	require.NoError(t, err)
	assert.Len(t, within2, 2, "hop <= 2 excludes the hop-3 entry")

	all, err := store.EntriesWithinHops(-1)
	require.NoError(t, err)
	assert.Len(t, all, 3)
}

func TestSnapshotStore_RemovePeer(t *testing.T) {
	store := SnapshotStore{DB: openSnapshotTestDB(t)}
	require.NoError(t, store.ReplacePeerSnapshot("B", []StatEntry{{OriginFingerprint: "B", Hops: 1, Key: "g1", Metric: MetricGamePlay}}, time.Unix(1, 0)))
	require.NoError(t, store.ReplacePeerSnapshot("D", []StatEntry{{OriginFingerprint: "D", Hops: 1, Key: "g2", Metric: MetricGamePlay}}, time.Unix(1, 0)))

	require.NoError(t, store.RemovePeerSnapshot("B"))
	got, err := store.EntriesWithinHops(-1)
	require.NoError(t, err)
	require.Len(t, got, 1)
	assert.Equal(t, "D", got[0].OriginFingerprint)
}
