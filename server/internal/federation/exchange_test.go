package federation

import (
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewRequestID_UniqueNonEmpty(t *testing.T) {
	a := NewRequestID()
	b := NewRequestID()
	assert.NotEmpty(t, a)
	assert.NotEqual(t, a, b)
}

func TestRecordExchange_WritesLedgerRow(t *testing.T) {
	database := openFedTestDB(t)
	start := time.Now().Add(-50 * time.Millisecond)

	RecordExchange(database, ExchangeRecord{
		RequestID: "req-1", PeerFingerprint: "fp-a", PeerName: "Alice",
		Direction: db.ExchangeOutbound, Operation: "handshake",
		Status: db.ExchangeOK, ItemCount: 3, StartedAt: start, FinishedAt: time.Now(),
	})

	var row db.FederationExchange
	require.NoError(t, database.Where("request_id = ?", "req-1").First(&row).Error)
	assert.Equal(t, db.ExchangeOK, row.Status)
	assert.Equal(t, 3, row.ItemCount)
	assert.GreaterOrEqual(t, row.DurationMs, int64(0))
}

func TestRecordExchange_UpdatesPeerHealthOnSuccess(t *testing.T) {
	database := openFedTestDB(t)
	store := PeerStore{DB: database}
	require.NoError(t, store.Upsert(&db.FederationPeer{
		Fingerprint: "fp-h", PublicKey: "k", BaseURL: "https://h", Status: db.PeerStatusActive,
	}))

	RecordExchange(database, ExchangeRecord{
		RequestID: "r", PeerFingerprint: "fp-h", Direction: db.ExchangeOutbound,
		Operation: "stats_pull", Status: db.ExchangeOK,
	})

	got, err := store.GetByFingerprint("fp-h")
	require.NoError(t, err)
	assert.True(t, got.Reachable)
	require.NotNil(t, got.LastSuccessAt)
	require.NotNil(t, got.LastContactAt)
}

func TestRecordExchange_UpdatesPeerHealthOnError(t *testing.T) {
	database := openFedTestDB(t)
	store := PeerStore{DB: database}
	require.NoError(t, store.Upsert(&db.FederationPeer{
		Fingerprint: "fp-e", PublicKey: "k", BaseURL: "https://e", Status: db.PeerStatusActive, Reachable: true,
	}))

	RecordExchange(database, ExchangeRecord{
		RequestID: "r", PeerFingerprint: "fp-e", Direction: db.ExchangeOutbound,
		Operation: "stats_pull", Status: db.ExchangeError, Error: "connection refused",
	})

	got, err := store.GetByFingerprint("fp-e")
	require.NoError(t, err)
	assert.False(t, got.Reachable)
	assert.Equal(t, "connection refused", got.LastError)
	require.NotNil(t, got.LastErrorAt)
}

func TestPruneExpiredExchanges_RemovesOnlyRowsOutsideRetention(t *testing.T) {
	database := openFedTestDB(t)
	now := time.Date(2026, 7, 4, 12, 0, 0, 0, time.UTC)

	old := db.FederationExchange{
		RequestID: "old", Direction: db.ExchangeOutbound, Operation: "stats_pull",
		Status: db.ExchangeOK, StartedAt: now.Add(-ExchangeRetentionWindow - time.Hour),
	}
	fresh := db.FederationExchange{
		RequestID: "fresh", Direction: db.ExchangeOutbound, Operation: "stats_pull",
		Status: db.ExchangeOK, StartedAt: now.Add(-ExchangeRetentionWindow + time.Hour),
	}
	require.NoError(t, database.Create(&old).Error)
	require.NoError(t, database.Create(&fresh).Error)

	assert.Equal(t, int64(1), PruneExpiredExchanges(database, now))
	assert.Equal(t, int64(0), PruneExpiredExchanges(database, now), "pruning is idempotent")

	var rows []db.FederationExchange
	require.NoError(t, database.Order("request_id").Find(&rows).Error)
	require.Len(t, rows, 1)
	assert.Equal(t, "fresh", rows[0].RequestID)
}
