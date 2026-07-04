package federation

import (
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func openPresenceTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.User{}, &db.Game{}))
	return database
}

func TestDedupePresenceEntries_KeepsFirstPerOriginUser(t *testing.T) {
	in := []PresenceEntry{
		{OriginFingerprint: "A", Username: "bob", GameKey: "igdb:1", GameTitle: "Real"},
		{OriginFingerprint: "A", Username: "bob", GameKey: "igdb:2", GameTitle: "Dup via other path"},
		{OriginFingerprint: "B", Username: "bob", GameKey: "igdb:3", GameTitle: "Different origin"},
	}
	out := DedupePresenceEntries(in)
	require.Len(t, out, 2)
	assert.Equal(t, "igdb:1", out[0].GameKey, "first occurrence wins for (A, bob)")
	assert.Equal(t, "B", out[1].OriginFingerprint, "same username on a different origin is distinct")

	// Idempotent.
	assert.Equal(t, out, DedupePresenceEntries(out))
}

func TestBuildLocalPresence_GatesPrivateAndUnidentifiableGames(t *testing.T) {
	database := openPresenceTestDB(t)

	pub := db.User{Username: "publicguy", PasswordHash: "h", ProfileVisibility: "public"}
	priv := db.User{Username: "privateguy", PasswordHash: "h", ProfileVisibility: "private"}
	disabled := db.User{Username: "banned", PasswordHash: "h", ProfileVisibility: "public", Disabled: true}
	require.NoError(t, database.Create(&pub).Error)
	require.NoError(t, database.Create(&priv).Error)
	require.NoError(t, database.Create(&disabled).Error)

	identified := db.Game{Title: "Zelda", ScraperID: "igdb:1022", FilePath: "/z", ConsoleID: 1}
	homebrew := db.Game{Title: "Homebrew", FilePath: "/h", ConsoleID: 1} // no scraper id / CRC
	require.NoError(t, database.Create(&identified).Error)
	require.NoError(t, database.Create(&homebrew).Error)

	sessions := []PlayingSession{
		{UserID: pub.ID, GameID: identified.ID},      // included
		{UserID: priv.ID, GameID: identified.ID},     // dropped: private
		{UserID: disabled.ID, GameID: identified.ID}, // dropped: disabled
		{UserID: pub.ID, GameID: homebrew.ID},        // dropped: no cross-key
	}

	entries, err := BuildLocalPresence(database, "selfFP", sessions)
	require.NoError(t, err)
	require.Len(t, entries, 1)
	assert.Equal(t, "selfFP", entries[0].OriginFingerprint)
	assert.Equal(t, 0, entries[0].Hops)
	assert.Equal(t, "publicguy", entries[0].Username)
	assert.Equal(t, "igdb:1022", entries[0].GameKey)
	assert.Equal(t, "Zelda", entries[0].GameTitle)
}

func TestBuildLocalPresence_EmptyForNoSessions(t *testing.T) {
	database := openPresenceTestDB(t)
	entries, err := BuildLocalPresence(database, "selfFP", nil)
	require.NoError(t, err)
	assert.Empty(t, entries)
}
