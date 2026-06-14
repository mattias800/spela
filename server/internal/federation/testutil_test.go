package federation

import (
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// openFedTestDB returns an in-memory SQLite DB migrated with the federation
// tables plus ServerSetting (used for identity persistence).
func openFedTestDB(t *testing.T) *gorm.DB {
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

// testAESKey is a fixed 32-byte AES key for deterministic encryption tests.
var testAESKey = []byte("0123456789abcdef0123456789abcdef")
