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

	pub := db.User{Username: "publicguy", Email: "a@x.test", PasswordHash: "h", ProfileVisibility: "public"}
	priv := db.User{Username: "privateguy", Email: "b@x.test", PasswordHash: "h", ProfileVisibility: "private"}
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
	assert.Equal(t, int64(300), games[0].PlayTimeSeconds, "both users' play time summed")
	assert.Equal(t, int64(2), games[0].Players)

	require.Len(t, players, 1, "private user must be excluded from player_play")
	assert.Equal(t, "publicguy", players[0].Key)
	assert.Equal(t, int64(100), players[0].PlayTimeSeconds)
}

func TestGameStatKey_PrefersScraperThenCRCThenTitle(t *testing.T) {
	assert.Equal(t, "igdb:5", gameStatKey(db.Game{ScraperID: "igdb:5", CRC32: "abc", Title: "T"}))
	assert.Equal(t, "crc:abc", gameStatKey(db.Game{CRC32: "abc", Title: "T"}))
	assert.Equal(t, "title:zelda", gameStatKey(db.Game{Title: "Zelda"}))
}
