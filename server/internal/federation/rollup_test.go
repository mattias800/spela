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

func openRollupTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.User{}, &db.Game{}, &db.PlayHistory{}))
	return database
}

func TestBuildLocalRollup_StampsGamesAndGatesPrivatePlayers(t *testing.T) {
	database := openRollupTestDB(t)

	pub := db.User{Username: "publicguy", PasswordHash: "h", ProfileVisibility: "public"}
	priv := db.User{Username: "privateguy", PasswordHash: "h", ProfileVisibility: "private"}
	require.NoError(t, database.Create(&pub).Error)
	require.NoError(t, database.Create(&priv).Error)

	g1 := db.Game{Title: "Game One", ScraperID: "igdb:111", FilePath: "/g1", ConsoleID: 1}
	require.NoError(t, database.Create(&g1).Error)

	// Both users played g1.
	require.NoError(t, database.Create(&db.PlayHistory{UserID: pub.ID, GameID: g1.ID, PlayTime: 100}).Error)
	require.NoError(t, database.Create(&db.PlayHistory{UserID: priv.ID, GameID: g1.ID, PlayTime: 200}).Error)

	entries, err := BuildLocalRollup(database, "selfFP")
	require.NoError(t, err)

	var games, players []StatEntry
	for _, e := range entries {
		switch e.Metric {
		case MetricGamePlay:
			games = append(games, e)
		case MetricPlayerPlay:
			players = append(players, e)
		}
	}

	require.Len(t, games, 1)
	assert.Equal(t, "selfFP", games[0].OriginFingerprint)
	assert.Equal(t, 0, games[0].Hops)
	assert.Equal(t, "igdb:111", games[0].Key)
	assert.Equal(t, int64(100), games[0].PlayTimeSeconds, "only the public user's play time counts toward the game total")
	assert.Equal(t, int64(1), games[0].Players, "private user excluded from the game player count too")

	require.Len(t, players, 1, "private user must be excluded from player_play")
	assert.Equal(t, "publicguy", players[0].Key)
	assert.Equal(t, int64(100), players[0].PlayTimeSeconds)
}

func TestBuildLocalRollup_ExcludesDisabledAndPendingUsers(t *testing.T) {
	database := openRollupTestDB(t)
	disabled := db.User{Username: "banned", PasswordHash: "h", ProfileVisibility: "public", Disabled: true}
	pending := db.User{Username: "newbie", PasswordHash: "h", ProfileVisibility: "public", PendingApproval: true}
	require.NoError(t, database.Create(&disabled).Error)
	require.NoError(t, database.Create(&pending).Error)

	g := db.Game{Title: "G", ScraperID: "igdb:9", FilePath: "/g", ConsoleID: 1}
	require.NoError(t, database.Create(&g).Error)
	require.NoError(t, database.Create(&db.PlayHistory{UserID: disabled.ID, GameID: g.ID, PlayTime: 50}).Error)
	require.NoError(t, database.Create(&db.PlayHistory{UserID: pending.ID, GameID: g.ID, PlayTime: 50}).Error)

	entries, err := BuildLocalRollup(database, "selfFP")
	require.NoError(t, err)
	assert.Empty(t, entries, "disabled and pending-approval users must not federate (either metric)")
}

func TestGameStatKey_PrefersScraperThenCRC_SkipsUnidentifiable(t *testing.T) {
	k, ok := gameStatKey(db.Game{ScraperID: "igdb:5", CRC32: "abc", Title: "T"})
	assert.True(t, ok)
	assert.Equal(t, "igdb:5", k)

	k, ok = gameStatKey(db.Game{CRC32: "abc", Title: "T"})
	assert.True(t, ok)
	assert.Equal(t, "crc:abc", k)

	_, ok = gameStatKey(db.Game{Title: "Zelda"})
	assert.False(t, ok, "no scraper id / CRC32 => not federated (no title fallback)")
}
