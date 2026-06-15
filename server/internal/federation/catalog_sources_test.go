package federation

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestSourcePeersForKey_AnyHop(t *testing.T) {
	store := CatalogSnapshotStore{DB: openCatalogTestDB(t)}
	// B relays a friend-of-friend's game (hop 2); D has it directly (hop 1).
	require.NoError(t, store.ReplacePeerSnapshot("B", []CatalogEntry{
		{OriginFingerprint: "C", Hops: 2, Key: "deep", Title: "Deep"},
	}, time.Unix(1, 0)))
	require.NoError(t, store.ReplacePeerSnapshot("D", []CatalogEntry{
		{OriginFingerprint: "D", Hops: 1, Key: "deep", Title: "Deep"},
	}, time.Unix(1, 0)))

	sources, err := store.SourcePeersForKey("deep")
	require.NoError(t, err)
	// Both direct friends are askable, regardless of how deep the origin is.
	assert.ElementsMatch(t, []string{"B", "D"}, sources)

	none, err := store.SourcePeersForKey("missing")
	require.NoError(t, err)
	assert.Empty(t, none)
}
