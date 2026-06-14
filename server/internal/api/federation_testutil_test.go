package api

import (
	"encoding/base64"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// openAPIFedTestDB returns an in-memory DB migrated with the tables the
// federation API handlers and middleware touch.
func openAPIFedTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(
		&db.ServerSetting{},
		&db.FederationPeer{},
		&db.FederationInviteNonce{},
		&db.FederationExchange{},
	))
	return database
}

// b64 is a test helper for base64-std encoding a byte slice.
func b64(b []byte) string { return base64.StdEncoding.EncodeToString(b) }

// activePeer upserts an active peer for the given identity and returns its store.
func activePeer(t *testing.T, database *gorm.DB, id federation.Identity, name, baseURL string) federation.PeerStore {
	t.Helper()
	store := federation.PeerStore{DB: database}
	require.NoError(t, store.Upsert(&db.FederationPeer{
		Fingerprint: id.Fingerprint(),
		PublicKey:   b64(id.PublicKey),
		Name:        name,
		BaseURL:     baseURL,
		Status:      db.PeerStatusActive,
	}))
	return store
}
