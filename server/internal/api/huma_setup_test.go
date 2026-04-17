package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestOpenAPISpecGeneration verifies that the huma-generated OpenAPI spec
// is reachable and contains the registered operations. This is the
// foundational guarantee of the huma migration: the spec is always present,
// always derived from code, and always covers everything we register.
func TestOpenAPISpecGeneration(t *testing.T) {
	_, cfg := setupTestEnv(t)
	cfg.Version = "v0.0.1-test"
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/openapi.json", nil)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code, "OpenAPI spec endpoint should return 200")

	var spec map[string]any
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &spec))

	// Spec metadata
	assert.Equal(t, "3.1.0", spec["openapi"], "expected OpenAPI 3.1 spec")
	info, ok := spec["info"].(map[string]any)
	require.True(t, ok, "spec must include info block")
	assert.Equal(t, "Spela API", info["title"])
	assert.Equal(t, "v0.0.1-test", info["version"])

	// The health endpoint should be registered
	paths, ok := spec["paths"].(map[string]any)
	require.True(t, ok, "spec must include paths block")
	healthOp, ok := paths["/api/health"].(map[string]any)
	require.True(t, ok, "/api/health must be present in spec")
	getOp, ok := healthOp["get"].(map[string]any)
	require.True(t, ok, "GET /api/health must be present")
	assert.Equal(t, "getHealth", getOp["operationId"])
}

// TestOpenAPISpec_HasNewOperations verifies that every freshly-migrated huma
// operation shows up in the generated OpenAPI spec with the expected
// operationId. This is the behavioural guarantee that replaces per-endpoint
// "is the route registered?" tests — if the spec has it, the route exists.
func TestOpenAPISpec_HasNewOperations(t *testing.T) {
	_, cfg := setupTestEnv(t)
	cfg.Version = "v0.0.1-test"
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/openapi.json", nil)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var spec map[string]any
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &spec))

	paths, ok := spec["paths"].(map[string]any)
	require.True(t, ok, "spec must include paths block")

	cases := []struct {
		path   string
		method string
		opID   string
	}{
		{"/api/makers", "get", "listMakers"},
		{"/api/makers/{code}", "get", "getMaker"},
		{"/api/cores", "get", "listCores"},
		{"/api/stats/most-played", "get", "getMostPlayed"},
		{"/api/stats/most-active-players", "get", "getMostActivePlayers"},
		// Consoles batch
		{"/api/consoles", "get", "listConsoles"},
		{"/api/consoles/{id}/games", "get", "listConsoleGames"},
		{"/api/consoles/{id}/top-rated", "get", "getConsoleTopRated"},
		{"/api/top-rated", "get", "getTopRatedGlobal"},
		{"/api/top-lists/top-rated", "get", "getTopListAvailable"},
		{"/api/top-lists/top-rated-critics", "get", "getTopListCritics"},
		{"/api/top-lists/longest", "get", "getTopListLongest"},
		{"/api/consoles/{id}/top-lists/top-rated", "get", "getConsoleTopListAvailable"},
		{"/api/consoles/{id}/top-lists/top-rated-critics", "get", "getConsoleTopListCritics"},
		{"/api/consoles/{id}/top-lists/longest", "get", "getConsoleTopListLongest"},
		// User batch
		{"/api/user/profile", "get", "getUserProfile"},
		{"/api/user/preferences", "get", "getUserPreferences"},
		// Game discovery batch
		{"/api/games/{id}/artwork", "get", "getGameArtwork"},
		{"/api/games/{id}/similar", "get", "getSimilarGames"},
		{"/api/games/{id}/developer-games", "get", "getDeveloperGames"},
		// User mutations
		{"/api/user/profile", "put", "updateUserProfile"},
		{"/api/user/preferences", "put", "updateUserPreferences"},
		{"/api/user/password", "put", "changeUserPassword"},
		// Games batch
		{"/api/games", "get", "listGames"},
		{"/api/games/{id}", "get", "getGame"},
		{"/api/games/{id}/stats", "get", "getGameStats"},
		{"/api/games/{id}/cheats", "get", "getGameCheats"},
		{"/api/games/{id}/core", "get", "getRecommendedCore"},
		{"/api/games/{id}/scrape-if-needed", "post", "scrapeGameIfNeeded"},
		{"/api/games/{id}/play-time", "post", "updateGamePlayTime"},
		{"/api/games/{id}/play-time", "delete", "stopPlayingGame"},
		// Ratings batch
		{"/api/games/{id}/ratings", "post", "createOrUpdateGameRating"},
		{"/api/games/{id}/ratings", "get", "listGameRatings"},
		{"/api/games/{id}/ratings/summary", "get", "getGameRatingSummary"},
		{"/api/games/{id}/ratings/mine", "get", "getMyGameRating"},
		{"/api/games/{id}/ratings", "delete", "deleteMyGameRating"},
		// Favorites + play later
		{"/api/user/favorites", "get", "listFavorites"},
		{"/api/user/favorites/{gameId}", "post", "addFavorite"},
		{"/api/user/favorites/{gameId}", "delete", "removeFavorite"},
		{"/api/user/play-later", "get", "listPlayLater"},
		{"/api/user/play-later/{gameId}", "post", "addToPlayLater"},
		{"/api/user/play-later/{gameId}", "delete", "removeFromPlayLater"},
		// Search + social
		{"/api/search", "get", "globalSearch"},
		{"/api/users/search", "get", "searchUsers"},
		{"/api/social/online", "get", "getOnlineUsers"},
		// Sessions batch
		{"/api/games/{id}/sessions", "get", "listGameSessions"},
		{"/api/games/{id}/sessions", "post", "createSession"},
		{"/api/sessions/{id}", "get", "getSession"},
		{"/api/sessions/{id}", "put", "updateSession"},
		{"/api/sessions/{id}", "delete", "deleteSession"},
		{"/api/sessions/{id}/duplicate", "post", "duplicateSession"},
		{"/api/sessions/{id}/saves", "get", "listSessionSaves"},
		{"/api/sessions/{id}/saves/slots", "get", "listSlotSaves"},
		{"/api/sessions/{id}/saves", "delete", "bulkDeleteSessionSaves"},
		{"/api/sessions/{id}/saves/{saveId}", "delete", "deleteSessionSave"},
		{"/api/sessions/{id}/saves/{saveId}", "put", "updateSessionSave"},
		{"/api/sessions/{id}/play-time", "post", "updateSessionPlayTime"},
		{"/api/sessions/{id}/play-time", "delete", "stopPlayingSession"},
		{"/api/sessions/{id}/cheats", "get", "getSessionCheats"},
		{"/api/sessions/{id}/cheats", "put", "updateSessionCheats"},
		// Shared saves + shared sessions
		{"/api/games/{id}/shared-saves", "get", "listSharedSaves"},
		{"/api/games/{id}/shared-saves/{saveId}", "delete", "deleteSharedSave"},
		{"/api/shared-sessions", "post", "createSharedSession"},
		{"/api/shared-sessions/{id}", "get", "getSharedSession"},
		{"/api/shared-sessions/{id}", "put", "updateSharedSession"},
		{"/api/shared-sessions/{id}", "delete", "deleteSharedSession"},
		// Netplay
		{"/api/netplay/sessions", "post", "createNetplaySession"},
		{"/api/netplay/sessions", "get", "listNetplaySessions"},
		{"/api/netplay/sessions/join", "post", "joinNetplayByInviteCode"},
		{"/api/netplay/sessions/{id}", "get", "getNetplaySession"},
		{"/api/netplay/sessions/{id}", "delete", "deleteNetplaySession"},
		{"/api/netplay/sessions/{id}/leave", "post", "leaveNetplaySession"},
		{"/api/netplay/sessions/{id}/settings", "put", "updateNetplaySettings"},
		{"/api/netplay/sessions/{id}/invites", "post", "netplayInviteUser"},
		{"/api/netplay/invites/{inviteId}/accept", "post", "acceptNetplayInvite"},
		{"/api/netplay/invites/{inviteId}/decline", "post", "declineNetplayInvite"},
		// Challenges
		{"/api/challenges", "get", "listChallenges"},
		{"/api/challenges/{id}", "get", "getChallenge"},
		{"/api/challenges/{id}", "put", "updateChallenge"},
		{"/api/challenges/{id}", "delete", "deleteChallenge"},
		// Admin
		{"/api/admin/settings", "get", "getAdminSettings"},
		{"/api/admin/settings", "put", "updateAdminSettings"},
		{"/api/admin/stats", "get", "getAdminStats"},
		{"/api/admin/users", "get", "listAdminUsers"},
		{"/api/admin/users", "post", "adminCreateUser"},
		{"/api/admin/users/{id}", "put", "adminUpdateUser"},
		{"/api/admin/users/{id}", "delete", "adminDeleteUser"},
		// System events
		{"/api/admin/system-events", "get", "listSystemEvents"},
		{"/api/admin/system-events/types", "get", "getSystemEventTypes"},
		{"/api/admin/system-events/categories", "get", "getSystemEventCategories"},
		{"/api/admin/system-events/{id}", "get", "getSystemEvent"},
		{"/api/admin/system-events/{id}/dismiss", "put", "dismissSystemEvent"},
		// Enrichment
		{"/api/themes", "get", "listThemes"},
		{"/api/themes/{id}/games", "get", "listThemeGames"},
		{"/api/keywords", "get", "listKeywords"},
		{"/api/keywords/{id}/games", "get", "listKeywordGames"},
		{"/api/series", "get", "listSeries"},
		{"/api/series/{id}", "get", "getSeriesDetail"},
		{"/api/franchises", "get", "listFranchises"},
		{"/api/franchises/{id}", "get", "getFranchiseDetail"},
		{"/api/franchises/{id}/games", "get", "listFranchiseGames"},
		{"/api/games/{id}/series", "get", "getGameSeries"},
		{"/api/games/{id}/franchises", "get", "getGameFranchises"},
		{"/api/admin/enrich-metadata", "post", "triggerEnrichMetadata"},
		{"/api/admin/enrich-metadata/status", "get", "getEnrichMetadataStatus"},
		// RetroAchievements
		{"/api/user/ra/link", "post", "linkRAAccount"},
		{"/api/user/ra/link", "delete", "unlinkRAAccount"},
		{"/api/user/ra/status", "get", "getRAAccountStatus"},
		{"/api/user/ra/settings", "put", "updateRASettings"},
		{"/api/user/ra/token", "get", "getRAToken"},
		{"/api/games/{id}/achievements/progress", "get", "getAchievementProgress"},
		{"/api/games/{id}/achievements/timeline", "get", "getAchievementTimeline"},
		{"/api/games/{id}/achievements/leaderboard", "get", "getAchievementLeaderboard"},
		{"/api/user/achievements/recent", "get", "getRecentAchievements"},
		{"/api/user/achievements/unlocked", "get", "getUnlockedAchievements"},
		// BIOS
		{"/api/bios", "get", "listBiosFiles"},
		{"/api/admin/bios/{filename}", "delete", "deleteBiosFile"},
		{"/api/admin/bios/download", "post", "triggerBiosDownload"},
		// Achievement Showcase
		{"/api/user/achievements/showcase", "get", "getAchievementShowcase"},
		{"/api/user/achievements/showcase", "put", "updateAchievementShowcase"},
		{"/api/users/{id}/achievements/showcase", "get", "getPublicAchievementShowcase"},
		// Devices
		{"/api/user/devices", "post", "registerDevice"},
		{"/api/user/devices", "get", "listDevices"},
		{"/api/user/devices/{id}", "put", "updateDevice"},
		{"/api/user/devices/{id}", "delete", "deleteDevice"},
		{"/api/user/devices/{id}/preferences", "get", "getDevicePreferences"},
		{"/api/user/devices/{id}/preferences", "put", "updateDevicePreferences"},
		{"/api/admin/users/{id}/devices", "get", "adminGetUserDevices"},
		// Scraper admin
		{"/api/admin/scrape", "post", "triggerScrape"},
		{"/api/admin/scrape", "delete", "cancelScrape"},
		{"/api/admin/scrape/status", "get", "getScrapeStatus"},
		{"/api/admin/scrape/counts", "get", "getScrapeStatusCounts"},
		{"/api/admin/games/{id}/scrape", "post", "scrapeGame"},
		{"/api/admin/games/{id}/achievements/refresh", "post", "refreshAchievements"},
		{"/api/admin/steamgriddb/status", "get", "getSteamGridDBStatus"},
		{"/api/admin/ra/status", "get", "getAdminRAStatus"},
		// Cheat admin
		{"/api/admin/cheats/import", "post", "triggerCheatImport"},
		{"/api/admin/cheats/stats", "get", "getCheatStats"},
		// User extra
		{"/api/user/stats", "get", "getUserStats"},
		{"/api/user/play-stats", "get", "getPlayStats"},
		{"/api/user/recent", "get", "getRecentGames"},
		{"/api/user/play-heatmap", "get", "getPlayHeatmap"},
		{"/api/users/{id}/play-heatmap", "get", "getPublicPlayHeatmap"},
		{"/api/emulator/error", "post", "reportEmulatorError"},
		{"/api/user/games/{gameId}/keymapping", "get", "getGameKeyMapping"},
		{"/api/user/games/{gameId}/keymapping", "put", "updateGameKeyMapping"},
		{"/api/user/games/{gameId}/keymapping", "delete", "deleteGameKeyMapping"},
		// Misc
		{"/api/user/storage", "get", "getStorageUsage"},
		{"/api/user/saves/compact", "post", "compactSaves"},
		{"/api/user/saved-searches", "post", "createSavedSearch"},
		{"/api/user/saved-searches", "get", "listSavedSearches"},
		{"/api/user/saved-searches/{id}", "delete", "deleteSavedSearch"},
		{"/api/admin/metadata-matches", "get", "getMetadataMatches"},
		{"/api/admin/core-compatibility", "get", "getCoreCompatibility"},
		{"/api/admin/users/{id}/rate-limit", "get", "getUserRateLimit"},
		{"/api/admin/users/{id}/rate-limit", "delete", "resetUserRateLimit"},
	}

	for _, c := range cases {
		t.Run(c.opID, func(t *testing.T) {
			pathItem, ok := paths[c.path].(map[string]any)
			require.True(t, ok, "%s must be present in spec", c.path)
			op, ok := pathItem[c.method].(map[string]any)
			require.True(t, ok, "%s %s must be present in spec", c.method, c.path)
			assert.Equal(t, c.opID, op["operationId"])
			// Every migrated read-only endpoint is bearer-auth protected.
			sec, ok := op["security"].([]any)
			require.True(t, ok, "%s should have security requirement", c.opID)
			assert.NotEmpty(t, sec)
		})
	}
}

// TestHumaError_WireFormatMatchesErrorResponse verifies that huma's typed error
// helpers (huma.Error404NotFound, etc.) produce the same JSON wire format as
// the existing ErrorResponse shape used by raw gin handlers — `{"error": ..., "message": ...}`.
// This is the critical backwards-compatibility guarantee: consumers do not
// need to change their error-parsing code as endpoints migrate.
func TestHumaError_WireFormatMatchesErrorResponse(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Hit a migrated endpoint with a path that forces a 404 through huma.
	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/makers/does-not-exist", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusNotFound, w.Code)

	var body map[string]any
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &body))
	assert.Equal(t, "maker not found", body["error"], "error field should carry the message (ErrorResponse shape)")
	// huma's default RFC 7807 fields must NOT appear — that would be a wire change.
	_, hasTitle := body["title"]
	_, hasDetail := body["detail"]
	_, hasStatus := body["status"]
	assert.False(t, hasTitle, "should not include RFC 7807 title")
	assert.False(t, hasDetail, "should not include RFC 7807 detail")
	assert.False(t, hasStatus, "should not include RFC 7807 status")
}
