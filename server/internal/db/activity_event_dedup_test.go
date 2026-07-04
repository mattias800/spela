package db

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestDedupeStartedPlayingEvents verifies the one-time cleanup that collapses
// the historical "started_playing" flood (one event per 30s heartbeat) down to
// one event per play session, while leaving other event types and other games
// untouched — and that it is idempotent (re-running changes nothing).
func TestDedupeStartedPlayingEvents(t *testing.T) {
	database := setupTestDB(t)

	user := User{Username: "player", PasswordHash: "x", Role: RoleUser}
	require.NoError(t, database.Create(&user).Error)
	console := seedNESConsole(t, database)
	gameA := Game{ConsoleID: console.ID, Title: "Game A", FileName: "a.nes", FilePath: "/tmp/a.nes", FileSize: 1}
	gameB := Game{ConsoleID: console.ID, Title: "Game B", FileName: "b.nes", FilePath: "/tmp/b.nes", FileSize: 1}
	require.NoError(t, database.Create(&gameA).Error)
	require.NoError(t, database.Create(&gameB).Error)

	mk := func(eventType string, gameID *uint, at time.Time) {
		require.NoError(t, database.Create(&ActivityEvent{
			UserID:    user.ID,
			EventType: eventType,
			GameID:    gameID,
			CreatedAt: at,
		}).Error)
	}

	base := time.Date(2026, 1, 1, 12, 0, 0, 0, time.UTC)

	// Game A, session 1: 6 dense heartbeats 30s apart (the flood).
	for i := 0; i < 6; i++ {
		mk("started_playing", &gameA.ID, base.Add(time.Duration(i)*30*time.Second))
	}
	// Game A, session 2: a new session two hours later, 3 more heartbeats.
	s2 := base.Add(2 * time.Hour)
	for i := 0; i < 3; i++ {
		mk("started_playing", &gameA.ID, s2.Add(time.Duration(i)*30*time.Second))
	}

	// A different game's session — must be collapsed independently, not merged.
	mk("started_playing", &gameB.ID, base.Add(10*time.Second))
	mk("started_playing", &gameB.ID, base.Add(40*time.Second))

	// A non-started_playing event — must never be touched.
	mk("favorited_game", &gameA.ID, base.Add(45*time.Second))

	countSP := func(gameID uint) int64 {
		var c int64
		database.Model(&ActivityEvent{}).
			Where("event_type = ? AND game_id = ?", "started_playing", gameID).Count(&c)
		return c
	}
	countType := func(eventType string) int64 {
		var c int64
		database.Model(&ActivityEvent{}).Where("event_type = ?", eventType).Count(&c)
		return c
	}

	require.NoError(t, dedupeStartedPlayingEvents(database))

	// Game A collapses to one event per session (2), Game B to one (1).
	assert.Equal(t, int64(2), countSP(gameA.ID), "game A: one started_playing per session")
	assert.Equal(t, int64(1), countSP(gameB.ID), "game B: one started_playing for its single session")
	assert.Equal(t, int64(1), countType("favorited_game"), "non-play events untouched")

	// The survivors are the earliest event of each session.
	var survivors []ActivityEvent
	require.NoError(t, database.Where("event_type = ? AND game_id = ?", "started_playing", gameA.ID).
		Order("created_at").Find(&survivors).Error)
	require.Len(t, survivors, 2)
	assert.True(t, survivors[0].CreatedAt.Equal(base), "session 1 keeps its first event")
	assert.True(t, survivors[1].CreatedAt.Equal(s2), "session 2 keeps its first event")

	// Idempotent: a second pass deletes nothing more.
	require.NoError(t, dedupeStartedPlayingEvents(database))
	assert.Equal(t, int64(2), countSP(gameA.ID), "idempotent for game A")
	assert.Equal(t, int64(1), countSP(gameB.ID), "idempotent for game B")
}
