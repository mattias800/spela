package federation

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestDirectSourcesForKey_OnlyHop1(t *testing.T) {
	store := CatalogSnapshotStore{DB: openCatalogTestDB(t)}
	require.NoError(t, store.ReplacePeerSnapshot("B", []CatalogEntry{
		{OriginFingerprint: "B", Hops: 1, Key: "g1", Title: "T"},        // direct friend has it
		{OriginFingerprint: "C", Hops: 2, Key: "g1", Title: "T"},        // friend-of-friend (not direct)
		{OriginFingerprint: "B", Hops: 1, Key: "other", Title: "Other"}, // different game
	}, time.Unix(1, 0)))

	sources, err := store.DirectSourcesForKey("g1")
	require.NoError(t, err)
	require.Len(t, sources, 1, "only the hop-1 source qualifies for direct download")
	assert.Equal(t, "B", sources[0])

	none, err := store.DirectSourcesForKey("missing")
	require.NoError(t, err)
	assert.Empty(t, none)
}
