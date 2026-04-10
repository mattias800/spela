package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	neturl "net/url"
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

func TestListSecurityEvents_DefaultWindowReturnsRecentRows(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSecurityEvents(t, database)

	// The seed rows are all within the 30d default window. This verifies the
	// default view renders as expected without the caller specifying `since`.
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

func TestListSecurityEvents_DefaultSinceExcludesOldRows(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	// One row inside the default 30d window, one row well outside it.
	fresh := makeSecurityEvent(db.SecurityEvent{
		EventType: db.SecurityEventLoginFailed,
		Username:  "alice",
		IP:        "10.0.0.1",
		CreatedAt: time.Now().Add(-1 * time.Hour),
	})
	ancient := makeSecurityEvent(db.SecurityEvent{
		EventType: db.SecurityEventLoginFailed,
		Username:  "bob",
		IP:        "10.0.0.2",
		CreatedAt: time.Now().Add(-45 * 24 * time.Hour),
	})
	require.NoError(t, database.Create(&fresh).Error)
	require.NoError(t, database.Create(&ancient).Error)

	// Default query (no `since` param) must exclude the ancient row.
	req := httptest.NewRequest("GET", "/api/admin/security-events", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SecurityEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(1), resp.Total)
	require.Len(t, resp.Data, 1)
	assert.Equal(t, "alice", resp.Data[0].Username)
}

func TestListSecurityEvents_SinceAllOptsIntoUnboundedWindow(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	// An ancient row that would normally be excluded by the default window.
	ancient := makeSecurityEvent(db.SecurityEvent{
		EventType: db.SecurityEventLoginFailed,
		Username:  "ghost",
		IP:        "10.0.0.99",
		CreatedAt: time.Now().Add(-200 * 24 * time.Hour),
	})
	require.NoError(t, database.Create(&ancient).Error)

	// since=all must include it.
	req := httptest.NewRequest("GET", "/api/admin/security-events?since=all", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SecurityEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(1), resp.Total)
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
	// Must include every constant declared in models.go, not just a
	// handpicked subset — the slice is the source of truth.
	assert.ElementsMatch(t, db.AllSecurityEventTypes, resp.Types)
}

func TestListSecurityEvents_UsernameFilterTooLongReturns400(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	long := strings.Repeat("a", maxUsernameFilterLength+1)
	req := httptest.NewRequest("GET", "/api/admin/security-events?username="+long, nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "username filter too long")
}

func TestListSecurityEvents_InvalidIPFilterReturns400(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	req := httptest.NewRequest("GET", "/api/admin/security-events?ip=10.o.0.1", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "invalid ip filter")
}

func TestListSecurityEvents_LikeWildcardsEscaped(t *testing.T) {
	// Each case seeds a row whose username contains a LIKE metacharacter
	// (or the escape char itself) plus a decoy that would erroneously match
	// if the escape were skipped. The filter query should return only the
	// literal-matching row.
	cases := []struct {
		name    string
		literal string
		decoy   string
		query   string // raw query value; caller URL-encodes
	}{
		{
			name:    "percent_literal",
			literal: "user%admin",
			decoy:   "abc",
			query:   "%", // `%` matches any string in unescaped LIKE
		},
		{
			name:    "underscore_literal",
			literal: "user_admin",
			decoy:   "user1admin", // would match `user_admin` under unescaped LIKE
			query:   "_",
		},
		{
			name:    "backslash_literal",
			literal: `us\er`,
			decoy:   "user",
			query:   `\`,
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			database, cfg := setupTestEnv(t)
			router, cleanup := NewRouter(*cfg)
			defer cleanup()
			_, adminToken := createAdminUser(t, database)

			literal := makeSecurityEvent(db.SecurityEvent{
				EventType: db.SecurityEventLoginFailed,
				Username:  tc.literal,
				IP:        "10.0.0.1",
				CreatedAt: time.Now().Add(-1 * time.Hour),
			})
			decoy := makeSecurityEvent(db.SecurityEvent{
				EventType: db.SecurityEventLoginFailed,
				Username:  tc.decoy,
				IP:        "10.0.0.2",
				CreatedAt: time.Now().Add(-1 * time.Hour),
			})
			require.NoError(t, database.Create(&literal).Error)
			require.NoError(t, database.Create(&decoy).Error)

			url := "/api/admin/security-events?username=" + netURLQueryEscape(tc.query)
			req := httptest.NewRequest("GET", url, nil)
			req.Header.Set("Authorization", "Bearer "+adminToken)
			w := httptest.NewRecorder()
			router.ServeHTTP(w, req)

			require.Equal(t, http.StatusOK, w.Code)
			var resp SecurityEventsListResponse
			require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
			require.Equal(t, int64(1), resp.Total, "decoy row %q should not match literal search %q", tc.decoy, tc.query)
			assert.Equal(t, tc.literal, resp.Data[0].Username)
		})
	}
}

// netURLQueryEscape wraps url.QueryEscape locally so the test file doesn't
// need a separate import line for one call.
func netURLQueryEscape(s string) string {
	return neturl.QueryEscape(s)
}

func TestGetSecurityEvent_MalformedMetadataFallsBackToRaw(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	// A legacy/edited row with a metadata blob that isn't valid JSON. The
	// handler must not drop the data silently — it should surface it via
	// MetadataRaw so investigators still see something.
	row := db.SecurityEvent{
		EventType: db.SecurityEventLoginFailed,
		Username:  "alice",
		IP:        "10.0.0.1",
		Metadata:  "not-json-{broken",
	}
	require.NoError(t, database.Create(&row).Error)

	req := httptest.NewRequest("GET", "/api/admin/security-events/"+strconv.FormatUint(uint64(row.ID), 10), nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SecurityEventResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Nil(t, resp.Metadata)
	assert.Equal(t, "not-json-{broken", resp.MetadataRaw)
}

func TestParseSinceParam(t *testing.T) {
	cases := []struct {
		name         string
		input        string
		wantExplicit bool
		wantZero     bool
		// approx is compared against time.Since(result) for preset cases.
		approx time.Duration
	}{
		{name: "empty", input: "", wantExplicit: false, wantZero: true},
		{name: "garbage", input: "not-a-time", wantExplicit: false, wantZero: true},
		{name: "all", input: "all", wantExplicit: true, wantZero: true},
		{name: "ALL upper", input: "ALL", wantExplicit: true, wantZero: true},
		{name: "1h", input: "1h", wantExplicit: true, wantZero: false, approx: time.Hour},
		{name: "24h", input: "24h", wantExplicit: true, wantZero: false, approx: 24 * time.Hour},
		{name: "1d alias", input: "1d", wantExplicit: true, wantZero: false, approx: 24 * time.Hour},
		{name: "7d", input: "7d", wantExplicit: true, wantZero: false, approx: 7 * 24 * time.Hour},
		{name: "30d", input: "30d", wantExplicit: true, wantZero: false, approx: 30 * 24 * time.Hour},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got, explicit := parseSinceParam(tc.input)
			assert.Equal(t, tc.wantExplicit, explicit)
			if tc.wantZero {
				assert.True(t, got.IsZero(), "expected zero time, got %v", got)
				return
			}
			diff := time.Since(got)
			assert.InDelta(t, tc.approx.Seconds(), diff.Seconds(), 1.0)
		})
	}

	t.Run("RFC3339 timestamp", func(t *testing.T) {
		iso := "2026-03-15T10:00:00Z"
		got, explicit := parseSinceParam(iso)
		assert.True(t, explicit)
		assert.False(t, got.IsZero())
		expected, _ := time.Parse(time.RFC3339, iso)
		assert.True(t, got.Equal(expected))
	})

	t.Run("case-insensitive presets with whitespace", func(t *testing.T) {
		got, explicit := parseSinceParam("  24h  ")
		assert.True(t, explicit)
		assert.False(t, got.IsZero())
	})
}

func TestValidateIPFilter(t *testing.T) {
	cases := []struct {
		name  string
		input string
		ok    bool
	}{
		{name: "empty", input: "", ok: true},
		{name: "ipv4_full", input: "10.0.0.1", ok: true},
		{name: "ipv4_prefix_single_octet", input: "10.", ok: true},
		{name: "ipv4_prefix_three_octets", input: "10.0.0.", ok: true},
		{name: "ipv6_prefix", input: "2001:db8::", ok: true},
		{name: "ipv6_loopback", input: "::1", ok: true},
		{name: "ipv6_link_local", input: "FF02::1", ok: true},
		{name: "letter_o_rejected", input: "10.o.0.1", ok: false},
		{name: "nonsense_text", input: "nonsense", ok: false},
		{name: "sql_injection_attempt", input: "10.0.0.1 DROP", ok: false},
		{name: "too_long", input: strings.Repeat("1", 46), ok: false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			assert.Equal(t, tc.ok, validateIPFilter(tc.input))
		})
	}
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
