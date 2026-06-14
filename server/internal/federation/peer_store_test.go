package federation

import (
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestPeerStore_UpsertGetList(t *testing.T) {
	store := PeerStore{DB: openFedTestDB(t)}

	require.NoError(t, store.Upsert(&db.FederationPeer{
		Fingerprint: "fp1", PublicKey: "k1", Name: "Alice", BaseURL: "https://a", Status: db.PeerStatusActive,
	}))

	got, err := store.GetByFingerprint("fp1")
	require.NoError(t, err)
	assert.Equal(t, "Alice", got.Name)

	// Upsert with the same fingerprint updates rather than duplicates.
	require.NoError(t, store.Upsert(&db.FederationPeer{
		Fingerprint: "fp1", PublicKey: "k1", Name: "Alice Renamed", BaseURL: "https://a", Status: db.PeerStatusActive,
	}))
	list, err := store.List()
	require.NoError(t, err)
	require.Len(t, list, 1)
	assert.Equal(t, "Alice Renamed", list[0].Name)
}

func TestPeerStore_RemoveRevokes(t *testing.T) {
	store := PeerStore{DB: openFedTestDB(t)}
	require.NoError(t, store.Upsert(&db.FederationPeer{
		Fingerprint: "fp2", PublicKey: "k", BaseURL: "https://b", Status: db.PeerStatusActive,
	}))
	require.NoError(t, store.Remove("fp2"))

	_, err := store.GetByFingerprint("fp2")
	assert.Error(t, err, "removed peer must no longer be found")
}

func TestPeerStore_GetByFingerprint_NotFound(t *testing.T) {
	store := PeerStore{DB: openFedTestDB(t)}
	_, err := store.GetByFingerprint("missing")
	assert.Error(t, err)
}

func TestPeerStore_SetStatus(t *testing.T) {
	store := PeerStore{DB: openFedTestDB(t)}
	require.NoError(t, store.Upsert(&db.FederationPeer{
		Fingerprint: "fp3", PublicKey: "k", BaseURL: "https://c", Status: db.PeerStatusPending,
	}))
	require.NoError(t, store.SetStatus("fp3", db.PeerStatusActive))
	got, err := store.GetByFingerprint("fp3")
	require.NoError(t, err)
	assert.Equal(t, db.PeerStatusActive, got.Status)
}
