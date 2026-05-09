package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// --- SearchUsers pagination tests ---

func TestSearchUsers_EmptyQuery_ReturnsAllUsers(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router) // creates "apitest" user

	// Create additional users
	createNonOwnerUser(t, router, ownerToken, "alice", "alice@test.com", "SecureTestPass!2024")
	createNonOwnerUser(t, router, ownerToken, "bob", "bob@test.com", "SecureTestPass!2024")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/search", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse[json.RawMessage]
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)

	// Owner is excluded (self), so alice and bob should be returned
	assert.Equal(t, int64(2), resp.Total)
	assert.Equal(t, 1, resp.Page)
	assert.Equal(t, 20, resp.PageSize)

	data, _ := json.Marshal(resp.Data)
	var users []UserSearchResult
	json.Unmarshal(data, &users)
	assert.Len(t, users, 2)

	// Verify disabled users are excluded
	var alice db.User
	database.Where("username = ?", "alice").First(&alice)
	database.Model(&alice).Update("disabled", true)

	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/users/search", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	err = json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, int64(1), resp.Total)
}

func TestSearchUsers_WithQuery_MatchesByPrefix(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	createNonOwnerUser(t, router, ownerToken, "alice", "alice@test.com", "SecureTestPass!2024")
	createNonOwnerUser(t, router, ownerToken, "bob", "bob@test.com", "SecureTestPass!2024")
	createNonOwnerUser(t, router, ownerToken, "alex", "alex@test.com", "SecureTestPass!2024")

	// Search with single character
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/search?q=a", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse[json.RawMessage]
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, int64(2), resp.Total) // alice, alex (apitest excluded as self)

	// Search with two characters
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/users/search?q=al", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	err = json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, int64(2), resp.Total) // alice, alex
}

func TestSearchUsers_Pagination(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	// Create 5 users
	for i := 0; i < 5; i++ {
		createNonOwnerUser(t, router, ownerToken,
			fmt.Sprintf("user%02d", i),
			fmt.Sprintf("user%02d@test.com", i),
			"SecureTestPass!2024",
		)
	}

	// Page 1, pageSize 2
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/search?page=1&pageSize=2", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse[json.RawMessage]
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, int64(5), resp.Total)
	assert.Equal(t, 1, resp.Page)
	assert.Equal(t, 2, resp.PageSize)

	data, _ := json.Marshal(resp.Data)
	var users []UserSearchResult
	json.Unmarshal(data, &users)
	assert.Len(t, users, 2)

	// Page 3 should have 1 result
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/users/search?page=3&pageSize=2", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	err = json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, int64(5), resp.Total)

	data, _ = json.Marshal(resp.Data)
	json.Unmarshal(data, &users)
	assert.Len(t, users, 1)
}

func TestSearchUsers_PageSizeClampedTo50(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/search?pageSize=100", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse[json.RawMessage]
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, 20, resp.PageSize) // clamped to default 20 since >50
}

func TestSearchUsers_ReturnsPaginatedResponse(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/search", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Verify the response has PaginatedResponse structure
	var raw map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &raw)
	require.NoError(t, err)

	assert.Contains(t, raw, "data")
	assert.Contains(t, raw, "total")
	assert.Contains(t, raw, "page")
	assert.Contains(t, raw, "pageSize")
}

func TestSearchUsers_ExcludesSelf(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	// Search for "apitest" (the owner's username)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/search?q=apitest", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse[json.RawMessage]
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, int64(0), resp.Total) // self excluded
}

// --- GetRecentPartners tests ---

func TestRecentPartners_Empty(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/recent-partners", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var results []UserSearchResult
	err := json.Unmarshal(w.Body.Bytes(), &results)
	require.NoError(t, err)
	assert.Empty(t, results)
}

func TestRecentPartners_NetplayPartners(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	// Create users
	createNonOwnerUser(t, router, ownerToken, "alice", "alice@test.com", "SecureTestPass!2024")
	createNonOwnerUser(t, router, ownerToken, "bob", "bob@test.com", "SecureTestPass!2024")

	var owner, alice, bob db.User
	database.Where("username = ?", "apitest").First(&owner)
	database.Where("username = ?", "alice").First(&alice)
	database.Where("username = ?", "bob").First(&bob)

	// Create a game for the netplay session
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Test Game", FileName: "test.nes", FilePath: "/tmp/test_rp.nes", FileSize: 100}
	database.Create(&game)

	// Create netplay sessions: owner hosted with alice, bob was client with owner
	aliceID := alice.ID
	database.Create(&db.NetplaySession{
		HostUserID:   owner.ID,
		ClientUserID: &aliceID,
		GameID:       game.ID,
		Status:       "ended",
		InviteCode:   "ABC123",
		CoreName:     "nestopia",
	})

	ownerID := owner.ID
	database.Create(&db.NetplaySession{
		HostUserID:   bob.ID,
		ClientUserID: &ownerID,
		GameID:       game.ID,
		Status:       "ended",
		InviteCode:   "DEF456",
		CoreName:     "nestopia",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/recent-partners", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var results []UserSearchResult
	err := json.Unmarshal(w.Body.Bytes(), &results)
	require.NoError(t, err)
	assert.Len(t, results, 2)

	// Verify both alice and bob are in results
	usernames := make([]string, len(results))
	for i, r := range results {
		usernames[i] = r.Username
	}
	assert.Contains(t, usernames, "alice")
	assert.Contains(t, usernames, "bob")
}

func TestRecentPartners_SharedSessionPartners(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	createNonOwnerUser(t, router, ownerToken, "carol", "carol@test.com", "SecureTestPass!2024")

	var owner, carol db.User
	database.Where("username = ?", "apitest").First(&owner)
	database.Where("username = ?", "carol").First(&carol)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Shared Game", FileName: "shared.nes", FilePath: "/tmp/shared_rp.nes", FileSize: 100}
	database.Create(&game)

	// Create a shared session with both users as members
	sharedSession := db.SharedSession{
		OwnerID: owner.ID,
		GameID:  game.ID,
		Name:    "Test Shared Session",
		Status:  "active",
	}
	database.Create(&sharedSession)

	database.Create(&db.SharedSessionMember{
		SharedSessionID: sharedSession.ID,
		UserID:          owner.ID,
		Role:            "owner",
		JoinedAt:        time.Now(),
	})
	database.Create(&db.SharedSessionMember{
		SharedSessionID: sharedSession.ID,
		UserID:          carol.ID,
		Role:            "member",
		JoinedAt:        time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/recent-partners", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var results []UserSearchResult
	err := json.Unmarshal(w.Body.Bytes(), &results)
	require.NoError(t, err)
	assert.Len(t, results, 1)
	assert.Equal(t, "carol", results[0].Username)
}

func TestRecentPartners_ExcludesDisabledUsers(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	createNonOwnerUser(t, router, ownerToken, "disabled_user", "disabled@test.com", "SecureTestPass!2024")

	var owner, disabled db.User
	database.Where("username = ?", "apitest").First(&owner)
	database.Where("username = ?", "disabled_user").First(&disabled)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Test Game", FileName: "test2.nes", FilePath: "/tmp/test2_rp.nes", FileSize: 100}
	database.Create(&game)

	disabledID := disabled.ID
	database.Create(&db.NetplaySession{
		HostUserID:   owner.ID,
		ClientUserID: &disabledID,
		GameID:       game.ID,
		Status:       "ended",
		InviteCode:   "DIS123",
		CoreName:     "nestopia",
	})

	// Disable the user
	database.Model(&disabled).Update("disabled", true)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/recent-partners", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var results []UserSearchResult
	err := json.Unmarshal(w.Body.Bytes(), &results)
	require.NoError(t, err)
	assert.Empty(t, results)
}

func TestRecentPartners_ExcludesOldSessions(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	createNonOwnerUser(t, router, ownerToken, "old_partner", "old@test.com", "SecureTestPass!2024")

	var owner, oldPartner db.User
	database.Where("username = ?", "apitest").First(&owner)
	database.Where("username = ?", "old_partner").First(&oldPartner)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Old Game", FileName: "old.nes", FilePath: "/tmp/old_rp.nes", FileSize: 100}
	database.Create(&game)

	oldPartnerID := oldPartner.ID
	session := db.NetplaySession{
		HostUserID:   owner.ID,
		ClientUserID: &oldPartnerID,
		GameID:       game.ID,
		Status:       "ended",
		InviteCode:   "OLD123",
		CoreName:     "nestopia",
	}
	database.Create(&session)

	// Backdate the session to 7 months ago
	sevenMonthsAgo := time.Now().AddDate(0, -7, 0)
	database.Model(&session).Update("created_at", sevenMonthsAgo)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/recent-partners", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var results []UserSearchResult
	err := json.Unmarshal(w.Body.Bytes(), &results)
	require.NoError(t, err)
	assert.Empty(t, results)
}

func TestRecentPartners_SortedByMostRecent(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	createNonOwnerUser(t, router, ownerToken, "first", "first@test.com", "SecureTestPass!2024")
	createNonOwnerUser(t, router, ownerToken, "second", "second@test.com", "SecureTestPass!2024")

	var owner, first, second db.User
	database.Where("username = ?", "apitest").First(&owner)
	database.Where("username = ?", "first").First(&first)
	database.Where("username = ?", "second").First(&second)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Sort Game", FileName: "sort.nes", FilePath: "/tmp/sort_rp.nes", FileSize: 100}
	database.Create(&game)

	// Create older session with "first"
	firstID := first.ID
	s1 := db.NetplaySession{
		HostUserID:   owner.ID,
		ClientUserID: &firstID,
		GameID:       game.ID,
		Status:       "ended",
		InviteCode:   "SRT001",
		CoreName:     "nestopia",
	}
	database.Create(&s1)
	twoMonthsAgo := time.Now().AddDate(0, -2, 0)
	database.Model(&s1).Update("created_at", twoMonthsAgo)

	// Create newer session with "second"
	secondID := second.ID
	s2 := db.NetplaySession{
		HostUserID:   owner.ID,
		ClientUserID: &secondID,
		GameID:       game.ID,
		Status:       "ended",
		InviteCode:   "SRT002",
		CoreName:     "nestopia",
	}
	database.Create(&s2)
	oneMonthAgo := time.Now().AddDate(0, -1, 0)
	database.Model(&s2).Update("created_at", oneMonthAgo)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/recent-partners", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var results []UserSearchResult
	err := json.Unmarshal(w.Body.Bytes(), &results)
	require.NoError(t, err)
	require.Len(t, results, 2)

	// "second" should be first (more recent session)
	assert.Equal(t, "second", results[0].Username)
	assert.Equal(t, "first", results[1].Username)
}

func TestRecentPartners_NetplayWithoutClient_Excluded(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	var owner db.User
	database.Where("username = ?", "apitest").First(&owner)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Solo Game", FileName: "solo.nes", FilePath: "/tmp/solo_rp.nes", FileSize: 100}
	database.Create(&game)

	// Create a netplay session with no client (waiting state)
	database.Create(&db.NetplaySession{
		HostUserID:   owner.ID,
		ClientUserID: nil,
		GameID:       game.ID,
		Status:       "waiting",
		InviteCode:   "SOLO01",
		CoreName:     "nestopia",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/recent-partners", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var results []UserSearchResult
	err := json.Unmarshal(w.Body.Bytes(), &results)
	require.NoError(t, err)
	assert.Empty(t, results)
}

func TestRecentPartners_MaxTenResults(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	var owner db.User
	database.Where("username = ?", "apitest").First(&owner)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Max Game", FileName: "max.nes", FilePath: "/tmp/max_rp.nes", FileSize: 100}
	database.Create(&game)

	// Create 12 partners via netplay
	for i := 0; i < 12; i++ {
		username := fmt.Sprintf("partner%02d", i)
		createNonOwnerUser(t, router, ownerToken, username, fmt.Sprintf("%s@test.com", username), "SecureTestPass!2024")

		var partner db.User
		database.Where("username = ?", username).First(&partner)

		partnerID := partner.ID
		database.Create(&db.NetplaySession{
			HostUserID:   owner.ID,
			ClientUserID: &partnerID,
			GameID:       game.ID,
			Status:       "ended",
			InviteCode:   fmt.Sprintf("MAX%03d", i),
			CoreName:     "nestopia",
		})
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/recent-partners", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var results []UserSearchResult
	err := json.Unmarshal(w.Body.Bytes(), &results)
	require.NoError(t, err)
	assert.Len(t, results, 10) // capped at 10
}

func TestRecentPartners_CombinesNetplayAndSharedSessions(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	createNonOwnerUser(t, router, ownerToken, "netplay_friend", "nf@test.com", "SecureTestPass!2024")
	createNonOwnerUser(t, router, ownerToken, "shared_friend", "sf@test.com", "SecureTestPass!2024")

	var owner, netplayFriend, sharedFriend db.User
	database.Where("username = ?", "apitest").First(&owner)
	database.Where("username = ?", "netplay_friend").First(&netplayFriend)
	database.Where("username = ?", "shared_friend").First(&sharedFriend)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Combo Game", FileName: "combo.nes", FilePath: "/tmp/combo_rp.nes", FileSize: 100}
	database.Create(&game)

	// Netplay partner
	nfID := netplayFriend.ID
	database.Create(&db.NetplaySession{
		HostUserID:   owner.ID,
		ClientUserID: &nfID,
		GameID:       game.ID,
		Status:       "ended",
		InviteCode:   "CMB001",
		CoreName:     "nestopia",
	})

	// Shared session partner
	sharedSession := db.SharedSession{
		OwnerID: owner.ID,
		GameID:  game.ID,
		Name:    "Combo Shared",
		Status:  "active",
	}
	database.Create(&sharedSession)
	database.Create(&db.SharedSessionMember{
		SharedSessionID: sharedSession.ID,
		UserID:          owner.ID,
		Role:            "owner",
		JoinedAt:        time.Now(),
	})
	database.Create(&db.SharedSessionMember{
		SharedSessionID: sharedSession.ID,
		UserID:          sharedFriend.ID,
		Role:            "member",
		JoinedAt:        time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/recent-partners", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var results []UserSearchResult
	err := json.Unmarshal(w.Body.Bytes(), &results)
	require.NoError(t, err)
	assert.Len(t, results, 2)

	usernames := make([]string, len(results))
	for i, r := range results {
		usernames[i] = r.Username
	}
	assert.Contains(t, usernames, "netplay_friend")
	assert.Contains(t, usernames, "shared_friend")
}
