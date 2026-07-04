package db_test

import (
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// IMPORTANT: These tests must NOT call t.Parallel(). The system event
// recorder uses a process-scoped dedup singleton (globalSystemEventDedup)
// so parallel tests would race on its state and the dedup assertions below
// would become nondeterministic. Each test calls
// db.ResetSystemEventDedupForTest() in its setup to get a clean slate.

// newRecorderTestDB creates an in-memory SQLite database with the
// SystemEventCategory and SystemEvent tables migrated plus seeded categories.
func newRecorderTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.SystemEventCategory{}, &db.SystemEvent{}))
	database.Create(&db.SystemEventCategory{Code: db.CategorySecurity, Name: "Security"})
	database.Create(&db.SystemEventCategory{Code: db.CategoryOperational, Name: "Operational"})
	database.Create(&db.SystemEventCategory{Code: db.CategoryFederation, Name: "Federation"})
	return database
}

func TestRecordSecurityEvent_PersistsRow(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSystemEventDedupForTest()
	db.ResetCategoryIDCacheForTest()

	uid := uint(7)
	db.RecordSecurityEvent(database, db.SystemEventInput{
		EventType: db.SystemEventLoginFailed,
		Reason:    "bad_password",
		Username:  "Alice",
		UserID:    &uid,
		IP:        "10.0.0.1",
		Metadata:  map[string]any{"failedCount": 3},
	})

	var rows []db.SystemEvent
	require.NoError(t, database.Find(&rows).Error)
	require.Len(t, rows, 1)

	row := rows[0]
	assert.Equal(t, db.SystemEventLoginFailed, row.EventType)
	assert.Equal(t, "bad_password", row.Reason)
	assert.Equal(t, "Alice", row.Username)
	// UsernameLower must be auto-populated by the recorder so the
	// case-insensitive LIKE filter can use the index.
	assert.Equal(t, "alice", row.UsernameLower)
	assert.Equal(t, "10.0.0.1", row.IP)
	require.NotNil(t, row.UserID)
	assert.Equal(t, uint(7), *row.UserID)
	// Metadata should round-trip as JSON.
	assert.Contains(t, row.Metadata, `"failedCount"`)
	// CategoryID should be resolved to the security category.
	assert.NotZero(t, row.CategoryID)
}

func TestRecordSecurityEvent_DedupSuppressesMiddlewareFloods(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSystemEventDedupForTest()
	db.ResetCategoryIDCacheForTest()

	// A dedup-eligible event (revoked_token_used) hit 5 times in a row from
	// the same IP should only produce a single DB row.
	uid := uint(42)
	for i := 0; i < 5; i++ {
		db.RecordSecurityEvent(database, db.SystemEventInput{
			EventType: db.SystemEventRevokedTokenUsed,
			Username:  "attacker",
			UserID:    &uid,
			IP:        "203.0.113.7",
		})
	}
	var count int64
	database.Model(&db.SystemEvent{}).
		Where("event_type = ?", db.SystemEventRevokedTokenUsed).
		Count(&count)
	assert.Equal(t, int64(1), count)
}

func TestRecordSecurityEvent_LoginEventsAreNotDeduped(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSystemEventDedupForTest()
	db.ResetCategoryIDCacheForTest()

	// login_failed must NOT be deduped — each attempt is independently
	// meaningful for admins, even when the same user hits the same endpoint
	// from the same IP in quick succession.
	for i := 0; i < 3; i++ {
		db.RecordSecurityEvent(database, db.SystemEventInput{
			EventType: db.SystemEventLoginFailed,
			Reason:    "bad_password",
			Username:  "alice",
			IP:        "10.0.0.1",
		})
	}
	var count int64
	database.Model(&db.SystemEvent{}).
		Where("event_type = ?", db.SystemEventLoginFailed).
		Count(&count)
	assert.Equal(t, int64(3), count)
}

func TestRecordSecurityEvent_DedupDistinguishesNilAndZeroUserID(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSystemEventDedupForTest()
	db.ResetCategoryIDCacheForTest()

	zero := uint(0)
	db.RecordSecurityEvent(database, db.SystemEventInput{
		EventType: db.SystemEventRevokedTokenUsed,
		IP:        "10.0.0.1",
		UserID:    &zero,
	})
	db.RecordSecurityEvent(database, db.SystemEventInput{
		EventType: db.SystemEventRevokedTokenUsed,
		IP:        "10.0.0.1",
		UserID:    nil,
	})

	var count int64
	database.Model(&db.SystemEvent{}).Count(&count)
	assert.Equal(t, int64(2), count)
}

func TestRecordSecurityEvent_DedupDistinguishesDifferentIPs(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSystemEventDedupForTest()
	db.ResetCategoryIDCacheForTest()

	uid := uint(42)
	db.RecordSecurityEvent(database, db.SystemEventInput{
		EventType: db.SystemEventRevokedTokenUsed,
		UserID:    &uid,
		IP:        "10.0.0.1",
	})
	db.RecordSecurityEvent(database, db.SystemEventInput{
		EventType: db.SystemEventRevokedTokenUsed,
		UserID:    &uid,
		IP:        "10.0.0.2",
	})

	var count int64
	database.Model(&db.SystemEvent{}).
		Where("event_type = ?", db.SystemEventRevokedTokenUsed).
		Count(&count)
	assert.Equal(t, int64(2), count)
}

func TestRecordSecurityEvent_DBFailureIsSwallowed(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSystemEventDedupForTest()
	db.ResetCategoryIDCacheForTest()
	require.NoError(t, database.Migrator().DropTable(&db.SystemEvent{}))

	// This should not panic or throw.
	db.RecordSecurityEvent(database, db.SystemEventInput{
		EventType: db.SystemEventLoginFailed,
		Username:  "ghost",
	})
}

func TestRecordOperationalEvent_PersistsRow(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSystemEventDedupForTest()
	db.ResetCategoryIDCacheForTest()

	db.RecordOperationalEvent(database, db.SystemEventInput{
		EventType: db.SystemEventRACircuitBreakerTripped,
		Metadata: map[string]any{
			"consecutiveFailures": 5,
			"lastError":           "status 403",
		},
	})

	var rows []db.SystemEvent
	require.NoError(t, database.Find(&rows).Error)
	require.Len(t, rows, 1)

	row := rows[0]
	assert.Equal(t, db.SystemEventRACircuitBreakerTripped, row.EventType)
	assert.NotZero(t, row.CategoryID)
	assert.Contains(t, row.Metadata, `"consecutiveFailures"`)
}

func TestRecordOperationalEvent_DedupWorks(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSystemEventDedupForTest()
	db.ResetCategoryIDCacheForTest()

	// Operational events should be deduped
	for i := 0; i < 5; i++ {
		db.RecordOperationalEvent(database, db.SystemEventInput{
			EventType: db.SystemEventRACircuitBreakerTripped,
		})
	}
	var count int64
	database.Model(&db.SystemEvent{}).Count(&count)
	assert.Equal(t, int64(1), count)
}

func TestRecordOperationalEvent_FederationEventsUseFederationCategory(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSystemEventDedupForTest()
	db.ResetCategoryIDCacheForTest()

	db.RecordOperationalEvent(database, db.SystemEventInput{
		EventType: db.SystemEventFederationPeerPaired,
		Metadata: db.FederationEventMetadata{
			PeerFingerprint: "peer-fp",
			PeerName:        "Bob",
			RequestID:       "req-1",
			Direction:       "outbound",
			Operation:       "pair",
		},
	})

	var row db.SystemEvent
	require.NoError(t, database.Preload("Category").First(&row).Error)
	assert.Equal(t, db.SystemEventFederationPeerPaired, row.EventType)
	assert.Equal(t, db.CategoryFederation, row.Category.Code)
	assert.Contains(t, row.Metadata, `"peerFingerprint":"peer-fp"`)
	assert.Contains(t, row.Metadata, `"peerName":"Bob"`)
	assert.Contains(t, row.Metadata, `"requestId":"req-1"`)
}

func TestRecordOperationalEvent_FederationFailureEventsDedup(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSystemEventDedupForTest()
	db.ResetCategoryIDCacheForTest()

	for i := 0; i < 5; i++ {
		db.RecordOperationalEvent(database, db.SystemEventInput{
			EventType: db.SystemEventFederationAuthRejected,
			Reason:    "signature verification failed",
			IP:        "203.0.113.10",
			Path:      "/api/federation/stats",
			Metadata: db.FederationEventMetadata{
				PeerFingerprint: "peer-fp",
				RequestID:       "req-1",
			},
		})
	}

	var count int64
	database.Model(&db.SystemEvent{}).
		Where("event_type = ?", db.SystemEventFederationAuthRejected).
		Count(&count)
	assert.Equal(t, int64(1), count)
}
