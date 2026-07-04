package db

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func openFederationModelTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(
		&FederationPeer{}, &FederationInviteNonce{}, &FederationExchange{},
	))
	return database
}

func TestFederationPeer_FingerprintUnique(t *testing.T) {
	database := openFederationModelTestDB(t)
	require.NoError(t, database.Create(&FederationPeer{
		Fingerprint: "abc", PublicKey: "k", BaseURL: "https://a", Status: PeerStatusActive,
	}).Error)

	err := database.Create(&FederationPeer{
		Fingerprint: "abc", PublicKey: "k2", BaseURL: "https://b", Status: PeerStatusActive,
	}).Error
	assert.Error(t, err, "duplicate fingerprint must violate the unique index")
}

func TestFederationInviteNonce_NonceUnique(t *testing.T) {
	database := openFederationModelTestDB(t)
	require.NoError(t, database.Create(&FederationInviteNonce{Nonce: "n1"}).Error)
	assert.Error(t, database.Create(&FederationInviteNonce{Nonce: "n1"}).Error)
}

func TestFederationExchange_Persists(t *testing.T) {
	database := openFederationModelTestDB(t)
	require.NoError(t, database.Create(&FederationExchange{
		RequestID: "r1", PeerFingerprint: "abc", Direction: ExchangeOutbound,
		Operation: "handshake", Status: ExchangeOK,
	}).Error)

	var got FederationExchange
	require.NoError(t, database.Where("request_id = ?", "r1").First(&got).Error)
	assert.Equal(t, ExchangeOutbound, got.Direction)
	assert.Equal(t, ExchangeOK, got.Status)
}

func TestFederationExchange_StartedAtIndexed(t *testing.T) {
	database := openFederationModelTestDB(t)

	var count int64
	require.NoError(t, database.Raw(`
		SELECT count(1)
		FROM sqlite_master
		WHERE type = 'index'
			AND tbl_name = 'federation_exchanges'
			AND sql LIKE '%started_at%'
	`).Scan(&count).Error)
	assert.Equal(t, int64(1), count)
}
