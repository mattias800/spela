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

// makeSystemEvent is a test helper that mirrors the recorder's behavior of
// denormalizing the lowercase username so unit tests exercising the filter
// path go through the same index.
func makeSystemEvent(e db.SystemEvent) db.SystemEvent {
	e.UsernameLower = strings.ToLower(e.Username)
	if e.CategoryID == 0 {
		e.CategoryID = 1 // security category (seeded as ID 1 in setupTestEnv)
	}
	return e
}

// seedSystemEvents inserts a known set of system events for filtering tests.
func seedSystemEvents(t *testing.T, database *gorm.DB) {
	t.Helper()
	now := time.Now()
	rows := []db.SystemEvent{
		makeSystemEvent(db.SystemEvent{EventType: db.SystemEventLoginFailed, Reason: "bad_password", Username: "alice", IP: "10.0.0.1", CreatedAt: now.Add(-30 * time.Minute)}),
		makeSystemEvent(db.SystemEvent{EventType: db.SystemEventLoginFailed, Reason: "bad_password", Username: "alice", IP: "10.0.0.1", CreatedAt: now.Add(-25 * time.Minute)}),
		makeSystemEvent(db.SystemEvent{EventType: db.SystemEventAccountLocked, Username: "alice", IP: "10.0.0.1", CreatedAt: now.Add(-20 * time.Minute)}),
		makeSystemEvent(db.SystemEvent{EventType: db.SystemEventLoginSuccess, Username: "bob", IP: "10.0.0.2", CreatedAt: now.Add(-2 * time.Hour)}),
		makeSystemEvent(db.SystemEvent{EventType: db.SystemEventRevokedTokenUsed, Username: "carol", IP: "192.168.1.5", CreatedAt: now.Add(-10 * 24 * time.Hour)}),
	}
	for i := range rows {
		require.NoError(t, database.Create(&rows[i]).Error)
	}
}

func TestListSystemEvents_RequiresAdmin(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	req := httptest.NewRequest("GET", "/api/admin/system-events", nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestListSystemEvents_DefaultWindowReturnsRecentRows(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSystemEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/system-events", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(5), resp.Total)
	assert.Equal(t, 1, resp.Page)
	assert.Equal(t, 50, resp.PageSize)
	for i := 1; i < len(resp.Data); i++ {
		assert.False(t, resp.Data[i].CreatedAt.After(resp.Data[i-1].CreatedAt))
	}
}

func TestListSystemEvents_DefaultSinceExcludesOldRows(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	fresh := makeSystemEvent(db.SystemEvent{
		EventType: db.SystemEventLoginFailed,
		Username:  "alice",
		IP:        "10.0.0.1",
		CreatedAt: time.Now().Add(-1 * time.Hour),
	})
	ancient := makeSystemEvent(db.SystemEvent{
		EventType: db.SystemEventLoginFailed,
		Username:  "bob",
		IP:        "10.0.0.2",
		CreatedAt: time.Now().Add(-45 * 24 * time.Hour),
	})
	require.NoError(t, database.Create(&fresh).Error)
	require.NoError(t, database.Create(&ancient).Error)

	req := httptest.NewRequest("GET", "/api/admin/system-events", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(1), resp.Total)
	require.Len(t, resp.Data, 1)
	assert.Equal(t, "alice", resp.Data[0].Username)
}

func TestListSystemEvents_SinceAllOptsIntoUnboundedWindow(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	ancient := makeSystemEvent(db.SystemEvent{
		EventType: db.SystemEventLoginFailed,
		Username:  "ghost",
		IP:        "10.0.0.99",
		CreatedAt: time.Now().Add(-200 * 24 * time.Hour),
	})
	require.NoError(t, database.Create(&ancient).Error)

	req := httptest.NewRequest("GET", "/api/admin/system-events?since=all", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(1), resp.Total)
}

func TestListSystemEvents_FilterByEventType(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSystemEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/system-events?eventType=login_failed", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(2), resp.Total)
	for _, e := range resp.Data {
		assert.Equal(t, db.SystemEventLoginFailed, e.EventType)
	}
}

func TestListSystemEvents_FilterByMultipleEventTypes(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSystemEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/system-events?eventType=login_failed&eventType=account_locked", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(3), resp.Total)
}

func TestListSystemEvents_FilterByUsername(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSystemEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/system-events?username=alice", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(3), resp.Total)
	for _, e := range resp.Data {
		assert.Equal(t, "alice", e.Username)
	}
}

func TestListSystemEvents_FilterByUsernameSubstringCaseInsensitive(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSystemEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/system-events?username=AL", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(3), resp.Total)
}

func TestListSystemEvents_FilterByIPPrefix(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSystemEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/system-events?ip=10.0.0.", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(4), resp.Total)
}

func TestListSystemEvents_FilterBySincePreset(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSystemEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/system-events?since=1h", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(3), resp.Total)
}

func TestListSystemEvents_Pagination(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	seedSystemEvents(t, database)

	req := httptest.NewRequest("GET", "/api/admin/system-events?pageSize=2&page=1", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Len(t, resp.Data, 2)
	assert.Equal(t, 2, resp.PageSize)
	assert.GreaterOrEqual(t, resp.Total, int64(5))
}

func TestGetSystemEvent_ReturnsRow(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	row := makeSystemEvent(db.SystemEvent{
		EventType: db.SystemEventLoginFailed,
		Reason:    "bad_password",
		Username:  "dave",
		IP:        "1.2.3.4",
		Path:      "/api/auth/login",
		Metadata:  `{"failedCount":3}`,
	})
	require.NoError(t, database.Create(&row).Error)

	req := httptest.NewRequest("GET", "/api/admin/system-events/"+strconv.FormatUint(uint64(row.ID), 10), nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, row.ID, resp.ID)
	assert.Equal(t, "bad_password", resp.Reason)
	assert.Equal(t, "dave", resp.Username)
	assert.Equal(t, "security", resp.CategoryCode)
	require.NotNil(t, resp.Metadata)
	assert.EqualValues(t, 3, resp.Metadata["failedCount"])
}

func TestGetSystemEvent_NotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, cfg.DB)

	req := httptest.NewRequest("GET", "/api/admin/system-events/99999", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetSystemEventTypes(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	req := httptest.NewRequest("GET", "/api/admin/system-events/types", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventTypesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Len(t, resp.Types, len(db.AllSystemEventTypes))
	for _, ti := range resp.Types {
		assert.NotEmpty(t, ti.Category)
	}
}

func TestGetSystemEventCategories(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	req := httptest.NewRequest("GET", "/api/admin/system-events/categories", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var cats []struct {
		Code string `json:"code"`
		Name string `json:"name"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &cats))
	assert.Len(t, cats, 2)
	codes := []string{cats[0].Code, cats[1].Code}
	assert.Contains(t, codes, "security")
	assert.Contains(t, codes, "operational")
}

func TestListSystemEvents_FilterByCategory(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	var secCat, opCat db.SystemEventCategory
	database.Where("code = ?", db.CategorySecurity).First(&secCat)
	database.Where("code = ?", db.CategoryOperational).First(&opCat)

	secEvent := makeSystemEvent(db.SystemEvent{
		CategoryID: secCat.ID,
		EventType:  db.SystemEventLoginFailed,
		Username:   "alice",
		IP:         "10.0.0.1",
		CreatedAt:  time.Now().Add(-1 * time.Hour),
	})
	opEvent := makeSystemEvent(db.SystemEvent{
		CategoryID: opCat.ID,
		EventType:  db.SystemEventRACircuitBreakerTripped,
		CreatedAt:  time.Now().Add(-1 * time.Hour),
	})
	require.NoError(t, database.Create(&secEvent).Error)
	require.NoError(t, database.Create(&opEvent).Error)

	req := httptest.NewRequest("GET", "/api/admin/system-events?category=operational", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(1), resp.Total)
	assert.Equal(t, db.SystemEventRACircuitBreakerTripped, resp.Data[0].EventType)
}

func TestDismissSystemEvent(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	row := makeSystemEvent(db.SystemEvent{
		EventType: db.SystemEventLoginFailed,
		Username:  "alice",
		CreatedAt: time.Now(),
	})
	require.NoError(t, database.Create(&row).Error)

	// Dismiss it
	req := httptest.NewRequest("PUT", "/api/admin/system-events/"+strconv.FormatUint(uint64(row.ID), 10)+"/dismiss", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Default list should exclude it
	req = httptest.NewRequest("GET", "/api/admin/system-events", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w = httptest.NewRecorder()
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(0), resp.Total)

	// dismissed=true should include it
	req = httptest.NewRequest("GET", "/api/admin/system-events?dismissed=true", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w = httptest.NewRecorder()
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(1), resp.Total)
	assert.NotNil(t, resp.Data[0].DismissedAt)
}

func TestListSystemEvents_UsernameFilterTooLongReturns400(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	long := strings.Repeat("a", maxUsernameFilterLength+1)
	req := httptest.NewRequest("GET", "/api/admin/system-events?username="+long, nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "username filter too long")
}

func TestListSystemEvents_InvalidIPFilterReturns400(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	req := httptest.NewRequest("GET", "/api/admin/system-events?ip=10.o.0.1", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "invalid ip filter")
}

func TestListSystemEvents_LikeWildcardsEscaped(t *testing.T) {
	cases := []struct {
		name    string
		literal string
		decoy   string
		query   string
	}{
		{
			name:    "percent_literal",
			literal: "user%admin",
			decoy:   "abc",
			query:   "%",
		},
		{
			name:    "underscore_literal",
			literal: "user_admin",
			decoy:   "user1admin",
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

			literal := makeSystemEvent(db.SystemEvent{
				EventType: db.SystemEventLoginFailed,
				Username:  tc.literal,
				IP:        "10.0.0.1",
				CreatedAt: time.Now().Add(-1 * time.Hour),
			})
			decoy := makeSystemEvent(db.SystemEvent{
				EventType: db.SystemEventLoginFailed,
				Username:  tc.decoy,
				IP:        "10.0.0.2",
				CreatedAt: time.Now().Add(-1 * time.Hour),
			})
			require.NoError(t, database.Create(&literal).Error)
			require.NoError(t, database.Create(&decoy).Error)

			url := "/api/admin/system-events?username=" + netURLQueryEscape(tc.query)
			req := httptest.NewRequest("GET", url, nil)
			req.Header.Set("Authorization", "Bearer "+adminToken)
			w := httptest.NewRecorder()
			router.ServeHTTP(w, req)

			require.Equal(t, http.StatusOK, w.Code)
			var resp SystemEventsListResponse
			require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
			require.Equal(t, int64(1), resp.Total, "decoy row %q should not match literal search %q", tc.decoy, tc.query)
			assert.Equal(t, tc.literal, resp.Data[0].Username)
		})
	}
}

func netURLQueryEscape(s string) string {
	return neturl.QueryEscape(s)
}

func TestGetSystemEvent_MalformedMetadataFallsBackToRaw(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	row := makeSystemEvent(db.SystemEvent{
		EventType: db.SystemEventLoginFailed,
		Username:  "alice",
		IP:        "10.0.0.1",
		Metadata:  "not-json-{broken",
	})
	require.NoError(t, database.Create(&row).Error)

	req := httptest.NewRequest("GET", "/api/admin/system-events/"+strconv.FormatUint(uint64(row.ID), 10), nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventResponse
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
		approx       time.Duration
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

func TestPruneExpiredSystemEvents(t *testing.T) {
	database, _ := setupTestEnv(t)

	fresh := makeSystemEvent(db.SystemEvent{
		EventType: db.SystemEventLoginFailed,
		Username:  "alice",
		CreatedAt: time.Now().Add(-1 * time.Hour),
	})
	old := makeSystemEvent(db.SystemEvent{
		EventType: db.SystemEventLoginFailed,
		Username:  "bob",
		CreatedAt: time.Now().Add(-100 * 24 * time.Hour),
	})
	require.NoError(t, database.Create(&fresh).Error)
	require.NoError(t, database.Create(&old).Error)

	pruned := pruneExpiredSystemEvents(database)
	assert.Equal(t, int64(1), pruned)

	var remaining []db.SystemEvent
	require.NoError(t, database.Find(&remaining).Error)
	require.Len(t, remaining, 1)
	assert.Equal(t, "alice", remaining[0].Username)
}

func TestLoginFailureRecordsSystemEvent(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)
	db.ResetSystemEventDedupForTest()
	db.ResetCategoryIDCacheForTest()

	user := db.User{
		Username:     "victim",
		Email:        "victim@test.com",
		PasswordHash: "$2a$10$AAAAAAAAAAAAAAAAAAAAAOYL5eXPeq3xFV2DdRbHeBwM8tCKZWfQO",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)

	body := `{"username":"victim","password":"wrongpass"}`
	req := httptest.NewRequest("POST", "/api/auth/login", strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)

	req = httptest.NewRequest("GET", "/api/admin/system-events?username=victim", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w = httptest.NewRecorder()
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Equal(t, int64(1), resp.Total)
	assert.Equal(t, db.SystemEventLoginFailed, resp.Data[0].EventType)
	assert.Equal(t, "bad_password", resp.Data[0].Reason)
}
