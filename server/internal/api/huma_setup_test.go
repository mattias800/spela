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
		// Collections
		{"/api/collections", "post", "createCollection"},
		{"/api/collections", "get", "listMyCollections"},
		{"/api/collections/public", "get", "listPublicCollections"},
		{"/api/collections/{id}", "get", "getCollection"},
		{"/api/collections/{id}", "put", "updateCollection"},
		{"/api/collections/{id}", "delete", "deleteCollection"},
		{"/api/collections/{id}/games", "post", "addGameToCollection"},
		{"/api/collections/{id}/games/{gameId}", "delete", "removeGameFromCollection"},
		// Play Later reorder
		{"/api/user/play-later/reorder", "put", "reorderPlayLater"},
		// Challenges (extended)
		{"/api/challenges/{id}/attempts/start", "post", "startChallengeAttempt"},
		{"/api/challenges/{id}/attempts/{aid}/complete", "post", "completeChallengeAttempt"},
		{"/api/challenges/{id}/attempts/{aid}/abandon", "post", "abandonChallengeAttempt"},
		{"/api/challenges/{id}/attempts/mine", "get", "listMyChallengeAttempts"},
		{"/api/challenges/{id}/leaderboard", "get", "getChallengeLeaderboard"},
		{"/api/games/{id}/challenges", "get", "listGameChallenges"},
		{"/api/user/challenges", "get", "listMyChallenges"},
		// Shared sessions (extended)
		{"/api/games/{id}/shared-sessions", "get", "listGameSharedSessions"},
		{"/api/shared-sessions", "get", "listMySharedSessions"},
		{"/api/shared-sessions/{id}/invites", "post", "inviteToSharedSession"},
		{"/api/shared-sessions/{id}/leave", "post", "leaveSharedSession"},
		{"/api/shared-sessions/{id}/members/{userId}", "delete", "removeSharedSessionMember"},
		{"/api/shared-sessions/{id}/take-turn", "post", "sharedSessionTakeTurn"},
		{"/api/shared-sessions/{id}/release-turn", "post", "sharedSessionReleaseTurn"},
		{"/api/shared-sessions/{id}/heartbeat", "post", "sharedSessionHeartbeat"},
		{"/api/shared-sessions/{id}/saves", "get", "listSharedSessionSaves"},
		{"/api/shared-sessions/{id}/saves/{saveId}", "delete", "deleteSharedSessionSave"},
		{"/api/shared-sessions/{id}/saves/{saveId}/rename", "put", "renameSharedSessionSave"},
		{"/api/user/shared-session-invites", "get", "listSharedSessionInvites"},
		{"/api/user/shared-session-invites/count", "get", "getPendingSharedSessionInviteCount"},
		{"/api/user/shared-session-invites/{id}/accept", "post", "acceptSharedSessionInvite"},
		{"/api/user/shared-session-invites/{id}/decline", "post", "declineSharedSessionInvite"},
		// Netplay invites
		{"/api/netplay/sessions/{id}/invites", "get", "listNetplaySessionInvites"},
		{"/api/netplay/invites", "get", "listMyNetplayInvites"},
		{"/api/netplay/invites/count", "get", "getPendingNetplayInviteCount"},
		// Admin game metadata
		{"/api/admin/games/{id}/metadata", "post", "updateGameMetadata"},
		{"/api/admin/games/{id}/verification-tag", "put", "updateVerificationTag"},
		{"/api/admin/games/{id}/covers", "get", "getGameCovers"},
		{"/api/admin/games/{id}/covers", "put", "setGameCover"},
		{"/api/admin/games/{id}/heroes", "get", "getGameHeroes"},
		{"/api/admin/games/{id}/heroes", "put", "setGameHero"},
		{"/api/admin/games/{id}/igdb-search", "get", "searchIGDB"},
		{"/api/admin/games/{id}/igdb-match", "post", "applyIGDBMatch"},
		{"/api/admin/igdb/test", "post", "testIGDB"},
		{"/api/admin/igdb/status", "get", "getIGDBStatus"},
		{"/api/admin/backfill-images", "post", "backfillImages"},
		{"/api/admin/users/deleted", "get", "listDeletedUsers"},
		{"/api/admin/users/{id}/permanent", "delete", "hardDeleteUser"},
		{"/api/admin/games/scan", "post", "scanGames"},
		{"/api/admin/games/scan/status", "get", "getScanStatus"},
		// Upload admin
		{"/api/admin/uploads/writable", "get", "checkUploadsWritable"},
		{"/api/admin/uploads", "get", "listUploads"},
		{"/api/admin/uploads/{id}/console", "post", "setUploadConsole"},
		{"/api/admin/uploads/{id}/scrape", "post", "scrapeUpload"},
		{"/api/admin/uploads/scrape", "post", "scrapeAllUploads"},
		{"/api/admin/uploads/{id}/accept", "post", "acceptUpload"},
		{"/api/admin/uploads/{id}/reject", "post", "rejectUpload"},
		{"/api/admin/uploads/accept-all", "post", "acceptAllUploads"},
		{"/api/admin/uploads/reject-all", "post", "rejectAllUploads"},
		{"/api/admin/uploads", "delete", "clearStaging"},
		// Social extra
		{"/api/social/activity", "get", "getActivityFeed"},
		{"/api/users/recent-partners", "get", "getRecentPartners"},
		{"/api/users/{id}/profile", "get", "getPublicProfile"},
		// Profiles
		{"/api/user/taste-profile", "get", "getTasteProfile"},
		{"/api/user/explorer-badges", "get", "getExplorerBadges"},
		{"/api/user/completionist-map", "get", "getCompletionistMap"},
		// Auth — logout (post-multipart batch)
		{"/api/auth/logout", "post", "authLogout"},
		// Admin multipart uploads
		{"/api/admin/bios", "post", "adminUploadBios"},
		{"/api/admin/games/{id}/replace-rom", "put", "adminReplaceROM"},
		{"/api/admin/rom-hacks", "post", "adminCreateRomHack"},
		{"/api/admin/uploads", "post", "adminUploadROMs"},
		// Session save uploads
		{"/api/sessions/{id}/saves", "post", "uploadSessionSave"},
		{"/api/sessions/{id}/saves/auto", "post", "uploadAutoSave"},
		{"/api/sessions/{id}/saves/slot/{slot}", "put", "upsertSlotSave"},
		{"/api/sessions/{id}/sram", "post", "uploadSRAM"},
		// Shared save + shared session uploads
		{"/api/games/{id}/shared-saves", "post", "shareSave"},
		{"/api/shared-sessions/{id}/saves", "post", "uploadSharedSessionSave"},
		{"/api/shared-sessions/{id}/saves/auto", "post", "uploadSharedSessionAutoSave"},
		// Binary downloads (StreamResponse)
		{"/api/cores/{id}/download", "get", "downloadCore"},
		{"/api/bios/{filename}", "get", "downloadBios"},
		{"/api/sessions/{id}/saves/{saveId}", "get", "downloadSessionSave"},
		{"/api/sessions/{id}/saves/auto", "get", "downloadSessionAutoSave"},
		{"/api/sessions/{id}/saves/slot/{slot}", "get", "downloadSessionSlotSave"},
		{"/api/sessions/{id}/sram", "get", "downloadSessionSRAM"},
		{"/api/games/{id}/shared-saves/{saveId}/download", "get", "downloadSharedSave"},
		{"/api/shared-sessions/{id}/saves/{saveId}", "get", "downloadSharedSessionSave"},
		{"/api/shared-sessions/{id}/saves/auto", "get", "downloadSharedSessionAutoSave"},
		// Public assets
		{"/api/consoles/{id}/icon", "get", "getConsoleIcon"},
		{"/api/consoles/{id}/logo", "get", "getConsoleLogo"},
		{"/api/consoles/{id}/logo.png", "get", "getConsoleLogoPng"},
		{"/api/consoles/{id}/preview-screenshot", "get", "getConsolePreviewScreenshot"},
		{"/api/branding/logo", "get", "getBrandingLogo"},
		// ROM + challenge downloads
		{"/api/games/{id}/download", "get", "downloadGame"},
		{"/api/games/{id}/download/{filename}", "get", "downloadGameWithFilename"},
		{"/api/games/{id}/discs/{discNumber}/download", "get", "downloadGameDisc"},
		{"/api/challenges/{id}/save/download", "get", "downloadChallengeSave"},
		{"/api/challenges/{id}/screenshot", "get", "getChallengeScreenshot"},
	}

	// Operations that are intentionally public (no auth) — typically images
	// loaded by <img> tags or pre-render assets. Every other migrated
	// operation should carry a bearer-auth security requirement.
	publicOps := map[string]bool{
		"getConsoleIcon":              true,
		"getConsoleLogo":              true,
		"getConsoleLogoPng":           true,
		"getConsolePreviewScreenshot": true,
		"getBrandingLogo":             true,
	}

	for _, c := range cases {
		t.Run(c.opID, func(t *testing.T) {
			pathItem, ok := paths[c.path].(map[string]any)
			require.True(t, ok, "%s must be present in spec", c.path)
			op, ok := pathItem[c.method].(map[string]any)
			require.True(t, ok, "%s %s must be present in spec", c.method, c.path)
			assert.Equal(t, c.opID, op["operationId"])
			if publicOps[c.opID] {
				// Public endpoints should NOT have a security requirement.
				if _, hasSec := op["security"]; hasSec {
					t.Errorf("%s should be public but has a security requirement", c.opID)
				}
				return
			}
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
