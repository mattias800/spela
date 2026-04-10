package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// makeSecurityEvent is a test helper that mirrors the recorder's behavior of
// denormalizing the lowercase username so unit tests exercising the filter
// path go through the same index.
func makeSecurityEvent(e db.SecurityEvent) db.SecurityEvent {
	e.UsernameLower = strings.ToLower(e.Username)
	return e
}

// seedSecurityEvents inserts a known set of security events for filtering tests.
// CreatedAt is set explicitly so the test can assert the "since" filter behavior
// without depending on row insertion order.
func seedSecurityEvents(t *testing.T, database *gorm.DB) {
	t.Helper()
	now := time.Now()
	rows := []db.SecurityEvent{
		makeSecurityEvent(db.SecurityEvent{EventType: db.SecurityEventLoginFailed, Reason: "bad_password", Username: "alice", IP: "10.0.0.1", CreatedAt: now.Add(-30 * time.Minute)}),
		makeSecurityEvent(db.SecurityEvent{EventType: db.SecurityEventLoginFailed, Reason: "bad_password", Username: "alice", IP: "10.0.0.1", CreatedAt: now.Add(-25 * time.Minute)}),
		makeSecurityEvent(db.SecurityEvent{EventType: db.SecurityEventAccountLocked, Username: "alice", IP: "10.0.0.1", CreatedAt: now.Add(-20 * time.Minute)}),
		makeSecurityEvent(db.SecurityEvent{EventType: db.SecurityEventLoginSuccess, Username: "bob", IP: "10.0.0.2", CreatedAt: now.Add(-2 * time.Hour)}),
		makeSecurityEvent(db.SecurityEvent{EventType: db.SecurityEventRevokedTokenUsed, Username: "carol", IP: "192.168.1.5", CreatedAt: now.Add(-10 * 24 * time.Hour)}),
	}
	for i := range rows {
		require.NoError(t, database.Create(&rows[i]).Error)
	}
}

func TestListSecurityEvents_RequiresAdmin(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	req := httptest.NewRequest("GET", "/api/admin/security-events", nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestListSecurityEvents_ReturnsAllByDefault(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSecurityEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/security-events", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SecurityEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(5), resp.Total)
	assert.Equal(t, 1, resp.Page)
	assert.Equal(t, 50, resp.PageSize)
	// Newest first.
	for i := 1; i < len(resp.Data); i++ {
		assert.False(t, resp.Data[i].CreatedAt.After(resp.Data[i-1].CreatedAt))
	}
}

func TestListSecurityEvents_FilterByEventType(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSecurityEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/security-events?eventType=login_failed", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SecurityEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(2), resp.Total)
	for _, e := range resp.Data {
		assert.Equal(t, db.SecurityEventLoginFailed, e.EventType)
	}
}

func TestListSecurityEvents_FilterByMultipleEventTypes(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSecurityEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/security-events?eventType=login_failed&eventType=account_locked", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SecurityEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(3), resp.Total)
}

func TestListSecurityEvents_FilterByUsername(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSecurityEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/security-events?username=alice", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SecurityEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(3), resp.Total)
	for _, e := range resp.Data {
		assert.Equal(t, "alice", e.Username)
	}
}

func TestListSecurityEvents_FilterByUsernameSubstringCaseInsensitive(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSecurityEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/security-events?username=AL", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SecurityEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(3), resp.Total)
}

func TestListSecurityEvents_FilterByIPPrefix(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSecurityEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/security-events?ip=10.0.0.", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SecurityEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(4), resp.Total)
}

func TestListSecurityEvents_FilterBySincePreset(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSecurityEvents(t, database)

	// Last hour: only the alice rows. carol (10d ago) and bob (2h ago) excluded.
	req := httptest.NewRequest("GET", "/api/admin/security-events?since=1h", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SecurityEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(3), resp.Total)
}

func TestListSecurityEvents_Pagination(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSecurityEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/security-events?pageSize=2&page=1", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SecurityEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Len(t, resp.Data, 2)
	assert.Equal(t, 2, resp.PageSize)
	assert.GreaterOrEqual(t, resp.Total, int64(5))
}

func TestGetSecurityEvent_ReturnsRow(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	row := db.SecurityEvent{
		EventType: db.SecurityEventLoginFailed,
		Reason:    "bad_password",
		Username:  "dave",
		IP:        "1.2.3.4",
		Path:      "/api/auth/login",
		Metadata:  `{"failedCount":3}`,
	}
	require.NoError(t, database.Create(&row).Error)

	req := httptest.NewRequest("GET", "/api/admin/security-events/"+strconv.FormatUint(uint64(row.ID), 10), nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SecurityEventResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, row.ID, resp.ID)
	assert.Equal(t, "bad_password", resp.Reason)
	assert.Equal(t, "dave", resp.Username)
	require.NotNil(t, resp.Metadata)
	assert.EqualValues(t, 3, resp.Metadata["failedCount"])
}

func TestGetSecurityEvent_NotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, cfg.DB)

	req := httptest.NewRequest("GET", "/api/admin/security-events/99999", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetSecurityEventTypes(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	req := httptest.NewRequest("GET", "/api/admin/security-events/types", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SecurityEventTypesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Contains(t, resp.Types, db.SecurityEventLoginFailed)
	assert.Contains(t, resp.Types, db.SecurityEventLoginSuccess)
	assert.Contains(t, resp.Types, db.SecurityEventAccountLocked)
}

func TestParseSinceParam(t *testing.T) {
	cases := []struct {
		name    string
		input   string
		isZero  bool
		// approxDelta is compared against time.Since(result) for preset cases.
		approx time.Duration
	}{
		{name: "empty", input: "", isZero: true},
		{name: "all", input: "all", isZero: true},
		{name: "ALL upper", input: "ALL", isZero: true},
		{name: "garbage", input: "not-a-time", isZero: true},
		{name: "1h", input: "1h", isZero: false, approx: time.Hour},
		{name: "24h", input: "24h", isZero: false, approx: 24 * time.Hour},
		{name: "1d alias", input: "1d", isZero: false, approx: 24 * time.Hour},
		{name: "7d", input: "7d", isZero: false, approx: 7 * 24 * time.Hour},
		{name: "30d", input: "30d", isZero: false, approx: 30 * 24 * time.Hour},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := parseSinceParam(tc.input)
			if tc.isZero {
				assert.True(t, got.IsZero(), "expected zero time, got %v", got)
				return
			}
			diff := time.Since(got)
			// Allow 1s slack for test jitter.
			assert.InDelta(t, tc.approx.Seconds(), diff.Seconds(), 1.0)
		})
	}

	t.Run("RFC3339 timestamp", func(t *testing.T) {
		iso := "2026-03-15T10:00:00Z"
		got := parseSinceParam(iso)
		assert.False(t, got.IsZero())
		expected, _ := time.Parse(time.RFC3339, iso)
		assert.True(t, got.Equal(expected))
	})

	t.Run("case-insensitive presets with whitespace", func(t *testing.T) {
		got := parseSinceParam("  24h  ")
		assert.False(t, got.IsZero())
	})
}

func TestPruneExpiredSecurityEvents(t *testing.T) {
	database, _ := setupTestEnv(t)

	// Fresh row (keep) + old row (delete).
	fresh := db.SecurityEvent{
		EventType: db.SecurityEventLoginFailed,
		Username:  "alice",
		CreatedAt: time.Now().Add(-1 * time.Hour),
	}
	old := db.SecurityEvent{
		EventType: db.SecurityEventLoginFailed,
		Username:  "bob",
		CreatedAt: time.Now().Add(-100 * 24 * time.Hour),
	}
	require.NoError(t, database.Create(&fresh).Error)
	require.NoError(t, database.Create(&old).Error)

	pruned := pruneExpiredSecurityEvents(database)
	assert.Equal(t, int64(1), pruned)

	var remaining []db.SecurityEvent
	require.NoError(t, database.Find(&remaining).Error)
	require.Len(t, remaining, 1)
	assert.Equal(t, "alice", remaining[0].Username)
}

func TestSecurityEventDedup_SuppressesRepeats(t *testing.T) {
	database, _ := setupTestEnv(t)

	// Reset global dedup state so tests don't interfere with each other.
	globalSecurityEventDedup.mu.Lock()
	globalSecurityEventDedup.lastSeen = make(map[dedupKey]time.Time)
	globalSecurityEventDedup.mu.Unlock()

	// A dedup-eligible event (revoked_token_used) hit 5 times in a row from
	// the same IP should only produce a single DB row.
	uid := uint(42)
	for i := 0; i < 5; i++ {
		recordSecurityEvent(database, securityEventInput{
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

func TestSecurityEventDedup_LoginEventsNotDeduped(t *testing.T) {
	database, _ := setupTestEnv(t)

	// Reset global dedup state.
	globalSecurityEventDedup.mu.Lock()
	globalSecurityEventDedup.lastSeen = make(map[dedupKey]time.Time)
	globalSecurityEventDedup.mu.Unlock()

	// login_failed should NOT be deduped — each attempt is independently
	// meaningful for admins.
	for i := 0; i < 3; i++ {
		recordSecurityEvent(database, securityEventInput{
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

// Failed-login flow integration test: hitting /api/auth/login with bad creds
// must persist a security event the admin can then query.
func TestLoginFailureRecordsSecurityEvent(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	// Create a non-admin user to fail-login against.
	user := db.User{
		Username:     "victim",
		Email:        "victim@test.com",
		PasswordHash: "$2a$10$AAAAAAAAAAAAAAAAAAAAAOYL5eXPeq3xFV2DdRbHeBwM8tCKZWfQO", // dummy
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)

	body := `{"username":"victim","password":"wrongpass"}`
	req := httptest.NewRequest("POST", "/api/auth/login", strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)

	// Now query the security log as admin and verify the event is present.
	req = httptest.NewRequest("GET", "/api/admin/security-events?username=victim", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w = httptest.NewRecorder()
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var resp SecurityEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Equal(t, int64(1), resp.Total)
	assert.Equal(t, db.SecurityEventLoginFailed, resp.Data[0].EventType)
	assert.Equal(t, "bad_password", resp.Data[0].Reason)
}
