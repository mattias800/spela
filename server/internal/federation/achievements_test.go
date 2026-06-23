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

func openAchievementsTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.User{}, &db.UserAchievementProgress{}))
	return database
}

func unlock(t *testing.T, database *gorm.DB, userID uint, raID uint) {
	t.Helper()
	require.NoError(t, database.Create(&db.UserAchievementProgress{
		UserID: userID, AchievementRAID: raID, RAGameID: 1,
	}).Error)
}

func TestDedupeAchievementEntries_KeepsFirstPerOriginUser(t *testing.T) {
	in := []AchievementEntry{
		{OriginFingerprint: "A", Username: "bob", Count: 10},
		{OriginFingerprint: "A", Username: "bob", Count: 99}, // dup via another path
		{OriginFingerprint: "B", Username: "bob", Count: 3},  // different origin = distinct
	}
	out := DedupeAchievementEntries(in)
	require.Len(t, out, 2)
	assert.Equal(t, int64(10), out[0].Count, "first occurrence wins for (A, bob)")
	assert.Equal(t, "B", out[1].OriginFingerprint)
	assert.Equal(t, out, DedupeAchievementEntries(out), "idempotent")
}

func TestSortAchievementEntries_ByCountDescThenName(t *testing.T) {
	out := SortAchievementEntries([]AchievementEntry{
		{Username: "carol", Count: 5},
		{Username: "alice", Count: 12},
		{Username: "bob", Count: 12},
	})
	assert.Equal(t, "alice", out[0].Username) // 12, tie broken by name
	assert.Equal(t, "bob", out[1].Username)   // 12
	assert.Equal(t, "carol", out[2].Username) // 5
}

func TestBuildLocalAchievements_CountsPerPublicActiveUser(t *testing.T) {
	database := openAchievementsTestDB(t)

	pub := db.User{Username: "publicguy", Email: "a@x.test", PasswordHash: "h", ProfileVisibility: "public"}
	priv := db.User{Username: "privateguy", Email: "b@x.test", PasswordHash: "h", ProfileVisibility: "private"}
	disabled := db.User{Username: "banned", Email: "c@x.test", PasswordHash: "h", ProfileVisibility: "public", Disabled: true}
	require.NoError(t, database.Create(&pub).Error)
	require.NoError(t, database.Create(&priv).Error)
	require.NoError(t, database.Create(&disabled).Error)

	unlock(t, database, pub.ID, 1)
	unlock(t, database, pub.ID, 2)
	unlock(t, database, pub.ID, 3)
	unlock(t, database, priv.ID, 1)     // private — excluded
	unlock(t, database, disabled.ID, 1) // disabled — excluded

	entries, err := BuildLocalAchievements(database, "selfFP")
	require.NoError(t, err)
	require.Len(t, entries, 1, "only the public, active user federates")
	assert.Equal(t, "selfFP", entries[0].OriginFingerprint)
	assert.Equal(t, 0, entries[0].Hops)
	assert.Equal(t, "publicguy", entries[0].Username)
	assert.Equal(t, int64(3), entries[0].Count)
}
