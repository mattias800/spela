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

// IMPORTANT: These tests must NOT call t.Parallel(). The security event
// recorder uses a process-scoped dedup singleton (globalSecurityEventDedup)
// so parallel tests would race on its state and the dedup assertions below
// would become nondeterministic. Each test calls
// db.ResetSecurityEventDedupForTest() in its setup to get a clean slate.

// newRecorderTestDB creates an in-memory SQLite database with only the
// SecurityEvent table migrated, which is all these tests need. Using a
// narrow setup (instead of the api package's full setupTestEnv) keeps this
// test file independent of the api package and reinforces that the
// recorder is genuinely domain-layer code.
//
// If SecurityEvent ever gains a foreign key or association to another
// table, add the referenced table to this migration list.
func newRecorderTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.SecurityEvent{}))
	return database
}

func TestRecordSecurityEvent_PersistsRow(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSecurityEventDedupForTest()

	uid := uint(7)
	db.RecordSecurityEvent(database, db.SecurityEventInput{
		EventType: db.SecurityEventLoginFailed,
		Reason:    "bad_password",
		Username:  "Alice",
		UserID:    &uid,
		IP:        "10.0.0.1",
		Metadata:  map[string]any{"failedCount": 3},
	})

	var rows []db.SecurityEvent
	require.NoError(t, database.Find(&rows).Error)
	require.Len(t, rows, 1)

	row := rows[0]
	assert.Equal(t, db.SecurityEventLoginFailed, row.EventType)
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
}

func TestRecordSecurityEvent_DedupSuppressesMiddlewareFloods(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSecurityEventDedupForTest()

	// A dedup-eligible event (revoked_token_used) hit 5 times in a row from
	// the same IP should only produce a single DB row.
	uid := uint(42)
	for i := 0; i < 5; i++ {
		db.RecordSecurityEvent(database, db.SecurityEventInput{
			EventType: db.SecurityEventRevokedTokenUsed,
			Username:  "attacker",
			UserID:    &uid,
			IP:        "203.0.113.7",
		})
	}
	var count int64
	database.Model(&db.SecurityEvent{}).
		Where("event_type = ?", db.SecurityEventRevokedTokenUsed).
		Count(&count)
	assert.Equal(t, int64(1), count)
}

func TestRecordSecurityEvent_LoginEventsAreNotDeduped(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSecurityEventDedupForTest()

	// login_failed must NOT be deduped — each attempt is independently
	// meaningful for admins, even when the same user hits the same endpoint
	// from the same IP in quick succession.
	for i := 0; i < 3; i++ {
		db.RecordSecurityEvent(database, db.SecurityEventInput{
			EventType: db.SecurityEventLoginFailed,
			Reason:    "bad_password",
			Username:  "alice",
			IP:        "10.0.0.1",
		})
	}
	var count int64
	database.Model(&db.SecurityEvent{}).
		Where("event_type = ?", db.SecurityEventLoginFailed).
		Count(&count)
	assert.Equal(t, int64(3), count)
}

func TestRecordSecurityEvent_DedupDistinguishesNilAndZeroUserID(t *testing.T) {
	// Regression test for a subtle dedup bug. The old key folded UserID=nil
	// into 0, so an unauthenticated flood (nil UserID) would collapse with
	// an authenticated user whose real ID happened to be 0. Now the key
	// tracks a `hasUserID` bit so nil and 0 are distinct buckets.
	database := newRecorderTestDB(t)
	db.ResetSecurityEventDedupForTest()

	zero := uint(0)
	db.RecordSecurityEvent(database, db.SecurityEventInput{
		EventType: db.SecurityEventRevokedTokenUsed,
		IP:        "10.0.0.1",
		UserID:    &zero,
	})
	db.RecordSecurityEvent(database, db.SecurityEventInput{
		EventType: db.SecurityEventRevokedTokenUsed,
		IP:        "10.0.0.1",
		UserID:    nil,
	})

	var count int64
	database.Model(&db.SecurityEvent{}).Count(&count)
	assert.Equal(t, int64(2), count)
}

func TestRecordSecurityEvent_DedupDistinguishesDifferentIPs(t *testing.T) {
	database := newRecorderTestDB(t)
	db.ResetSecurityEventDedupForTest()

	// Same event type and user, different source IPs — both should persist
	// because the dedup key includes the IP.
	uid := uint(42)
	db.RecordSecurityEvent(database, db.SecurityEventInput{
		EventType: db.SecurityEventRevokedTokenUsed,
		UserID:    &uid,
		IP:        "10.0.0.1",
	})
	db.RecordSecurityEvent(database, db.SecurityEventInput{
		EventType: db.SecurityEventRevokedTokenUsed,
		UserID:    &uid,
		IP:        "10.0.0.2",
	})

	var count int64
	database.Model(&db.SecurityEvent{}).
		Where("event_type = ?", db.SecurityEventRevokedTokenUsed).
		Count(&count)
	assert.Equal(t, int64(2), count)
}

func TestRecordSecurityEvent_DBFailureIsSwallowed(t *testing.T) {
	// Best-effort recorder: if the DB write fails, the caller must not
	// receive an error back (returning an error would block auth on a
	// transient audit-log failure, which would be a self-inflicted DoS).
	// Simulate the failure by dropping the table before the call.
	database := newRecorderTestDB(t)
	db.ResetSecurityEventDedupForTest()
	require.NoError(t, database.Migrator().DropTable(&db.SecurityEvent{}))

	// This should not panic or throw.
	db.RecordSecurityEvent(database, db.SecurityEventInput{
		EventType: db.SecurityEventLoginFailed,
		Username:  "ghost",
	})
}
