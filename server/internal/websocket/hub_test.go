package websocket

import (
	"net/http"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func makeRequest(origin, host string) *http.Request {
	r := &http.Request{
		Header: make(http.Header),
		Host:   host,
	}
	if origin != "" {
		r.Header.Set("Origin", origin)
	}
	return r
}

func TestCheckOrigin_NoOriginHeader(t *testing.T) {
	// Same-origin request (no Origin header) is always allowed.
	check := checkOrigin(nil)
	assert.True(t, check(makeRequest("", "example.com")))

	check2 := checkOrigin([]string{})
	assert.True(t, check2(makeRequest("", "example.com")))
}

func TestCheckOrigin_NoConfiguredOrigins_MatchingHost(t *testing.T) {
	// No configured origins + Origin host == request Host → allowed.
	// This is the production case: NPM proxies the request and the
	// Origin header carries the same host as the Host header.
	check := checkOrigin(nil)
	assert.True(t, check(makeRequest("https://spela.example.com", "spela.example.com")))
}

func TestCheckOrigin_NoConfiguredOrigins_MismatchedHost(t *testing.T) {
	// No configured origins + Origin host != request Host → rejected.
	check := checkOrigin(nil)
	assert.False(t, check(makeRequest("https://evil.com", "spela.example.com")))
}

func TestCheckOrigin_ExplicitList_Allowed(t *testing.T) {
	check := checkOrigin([]string{"https://spela.example.com"})
	assert.True(t, check(makeRequest("https://spela.example.com", "server:8080")))
}

func TestCheckOrigin_ExplicitList_Rejected(t *testing.T) {
	check := checkOrigin([]string{"https://spela.example.com"})
	assert.False(t, check(makeRequest("https://other.com", "server:8080")))
}

func TestCheckOrigin_Wildcard(t *testing.T) {
	check := checkOrigin([]string{"*"})
	assert.True(t, check(makeRequest("https://anything.com", "server:8080")))
}

func TestCheckOrigin_InvalidOriginURL(t *testing.T) {
	// Malformed Origin URL should be rejected gracefully.
	check := checkOrigin(nil)
	assert.False(t, check(makeRequest("://bad url", "server:8080")))
}

func TestSetUserGame_RecordsHeartbeat(t *testing.T) {
	hub := NewHub(nil)
	now := time.Now()
	hub.nowFunc = func() time.Time { return now }

	hub.SetUserGame(1, 42)

	assert.Equal(t, uint(42), hub.GetUserGame(1))

	hub.mu.Lock()
	entry := hub.userGames[1]
	hub.mu.Unlock()
	assert.Equal(t, now, entry.LastHeartbeat)
}

func TestSetUserGame_ClearWithZero(t *testing.T) {
	hub := NewHub(nil)

	hub.SetUserGame(1, 42)
	assert.Equal(t, uint(42), hub.GetUserGame(1))

	hub.SetUserGame(1, 0)
	assert.Equal(t, uint(0), hub.GetUserGame(1))
}

func TestExpireStaleGames_RemovesExpiredEntries(t *testing.T) {
	hub := NewHub(nil)
	now := time.Now()
	hub.nowFunc = func() time.Time { return now }

	// Set a game with a heartbeat 2 minutes ago (stale, > 90s)
	hub.mu.Lock()
	hub.userGames[1] = userGameEntry{
		GameID:        42,
		LastHeartbeat: now.Add(-2 * time.Minute),
	}
	hub.mu.Unlock()

	hub.expireStaleGames()

	assert.Equal(t, uint(0), hub.GetUserGame(1), "stale game entry should be removed")
}

func TestExpireStaleGames_KeepsFreshEntries(t *testing.T) {
	hub := NewHub(nil)
	now := time.Now()
	hub.nowFunc = func() time.Time { return now }

	// Set a game with a heartbeat 30s ago (fresh, < 90s)
	hub.mu.Lock()
	hub.userGames[1] = userGameEntry{
		GameID:        42,
		LastHeartbeat: now.Add(-30 * time.Second),
	}
	hub.mu.Unlock()

	hub.expireStaleGames()

	assert.Equal(t, uint(42), hub.GetUserGame(1), "fresh game entry should not be removed")
}

func TestExpireStaleGames_MixedEntries(t *testing.T) {
	hub := NewHub(nil)
	now := time.Now()
	hub.nowFunc = func() time.Time { return now }

	hub.mu.Lock()
	// User 1: stale heartbeat (2 minutes ago)
	hub.userGames[1] = userGameEntry{
		GameID:        10,
		LastHeartbeat: now.Add(-2 * time.Minute),
	}
	// User 2: fresh heartbeat (10 seconds ago)
	hub.userGames[2] = userGameEntry{
		GameID:        20,
		LastHeartbeat: now.Add(-10 * time.Second),
	}
	// User 3: exactly at the boundary (91 seconds ago, should expire)
	hub.userGames[3] = userGameEntry{
		GameID:        30,
		LastHeartbeat: now.Add(-91 * time.Second),
	}
	hub.mu.Unlock()

	hub.expireStaleGames()

	assert.Equal(t, uint(0), hub.GetUserGame(1), "user 1 stale entry should be removed")
	assert.Equal(t, uint(20), hub.GetUserGame(2), "user 2 fresh entry should remain")
	assert.Equal(t, uint(0), hub.GetUserGame(3), "user 3 boundary entry should be removed")
}

func TestExpireStaleGames_HeartbeatRefreshPreventsExpiry(t *testing.T) {
	hub := NewHub(nil)
	now := time.Now()
	hub.nowFunc = func() time.Time { return now }

	// Set initial game 2 minutes ago
	hub.mu.Lock()
	hub.userGames[1] = userGameEntry{
		GameID:        42,
		LastHeartbeat: now.Add(-2 * time.Minute),
	}
	hub.mu.Unlock()

	// Simulate a heartbeat refresh via SetUserGame
	hub.SetUserGame(1, 42)

	hub.expireStaleGames()

	assert.Equal(t, uint(42), hub.GetUserGame(1), "refreshed heartbeat should not be expired")
}
