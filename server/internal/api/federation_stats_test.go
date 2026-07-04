package api

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// fakeStatsClient returns canned friend entries keyed by base URL.
type fakeStatsClient struct {
	byBase      map[string][]federation.StatEntry
	err         error
	lastMaxHops int
}

func (f *fakeStatsClient) FetchStats(baseURL, _ string, _ federation.Identity, _ string, maxHops int) ([]federation.StatEntry, error) {
	f.lastMaxHops = maxHops
	if f.err != nil {
		return nil, f.err
	}
	return f.byBase[baseURL], nil
}

func seedLocalPlay(t *testing.T, database *gorm.DB, scraperID string, playTime int64) {
	t.Helper()
	u := db.User{Username: "localuser", PasswordHash: "h", ProfileVisibility: "public"}
	require.NoError(t, database.Create(&u).Error)
	g := db.Game{Title: "Local Game", ScraperID: scraperID, FilePath: "/lg", ConsoleID: 1}
	require.NoError(t, database.Create(&g).Error)
	require.NoError(t, database.Create(&db.PlayHistory{UserID: u.ID, GameID: g.ID, PlayTime: playTime}).Error)
}

func statsHandler(database *gorm.DB, selfID federation.Identity, client statsClient) *FederationHandler {
	return &FederationHandler{
		DB: database, Identity: selfID,
		Peers:       federation.PeerStore{DB: database},
		Snapshots:   federation.SnapshotStore{DB: database},
		BaseURL:     "https://self",
		StatsClient: client,
	}
}

// --- Export ---------------------------------------------------------------

func callExport(h *FederationHandler, peer *db.FederationPeer) *httptest.ResponseRecorder {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.GET("/x", func(c *gin.Context) {
		c.Set(fedPeerContextKey, peer)
		h.ginExportStats(c)
	})
	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/x", nil))
	return w
}

func TestExportStats_ServesLocalPlusCachedWithinHops(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	seedLocalPlay(t, database, "igdb:1", 100)
	h := statsHandler(database, selfID, nil)

	// Cache a friend-of-friend entry (hop 2) so the export should re-serve it.
	require.NoError(t, h.Snapshots.ReplacePeerSnapshot("B", []federation.StatEntry{
		{OriginFingerprint: "C", Hops: 2, Metric: federation.MetricGamePlay, Key: "igdb:9", Label: "Far Game", PlayTimeSeconds: 40},
	}, time.Unix(1, 0)))

	sp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassStats: true})
	peer := &db.FederationPeer{Fingerprint: "fp-friend", Name: "Friend", SharePolicy: sp, Status: db.PeerStatusActive}

	w := callExport(h, peer)
	require.Equal(t, http.StatusOK, w.Code)

	var body struct {
		Entries []federation.StatEntry `json:"entries"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &body))

	var sawLocal, sawCached bool
	for _, e := range body.Entries {
		if e.OriginFingerprint == selfID.Fingerprint() && e.Hops == 0 {
			sawLocal = true
		}
		if e.OriginFingerprint == "C" && e.Hops == 2 {
			sawCached = true
		}
	}
	assert.True(t, sawLocal, "export includes local hop-0 data")
	assert.True(t, sawCached, "export re-serves cached transitive data within the hop budget")
}

func TestExportStats_ForbiddenWhenNotShared(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	seedLocalPlay(t, database, "igdb:1", 100)
	h := statsHandler(database, selfID, nil)

	peer := &db.FederationPeer{Fingerprint: "fp-friend", Name: "Friend", SharePolicy: "", Status: db.PeerStatusActive}
	w := callExport(h, peer)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

// --- Refresh (pull into snapshot store) -----------------------------------

func TestRefresh_PullsConsumableFriendIntoSnapshot(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	policyPeer(t, database, friendID, "Friend", "https://friend", true, true)

	fake := &fakeStatsClient{byBase: map[string][]federation.StatEntry{
		"https://friend": {{
			OriginFingerprint: friendID.Fingerprint(), Hops: 0,
			Metric: federation.MetricGamePlay, Key: "igdb:1", PlayTimeSeconds: 250, Players: 5,
		}},
	}}
	h := statsHandler(database, selfID, fake)

	refreshed, failed := h.RefreshFederationStats()
	assert.Equal(t, 1, refreshed)
	assert.Equal(t, 0, failed)
	assert.Equal(t, federation.MaxFederationHops-1, fake.lastMaxHops, "asks for the hop budget minus one")

	cached, err := h.Snapshots.EntriesWithinHops(-1)
	require.NoError(t, err)
	require.Len(t, cached, 1)
	assert.Equal(t, 1, cached[0].Hops, "friend's hop-0 datum is stored at hop 1")
	assert.Equal(t, friendID.Fingerprint(), cached[0].OriginFingerprint)
}

func TestRefresh_SkipsNonConsumablePeer(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	policyPeer(t, database, friendID, "Friend", "https://friend", true, false) // consume=false

	fake := &fakeStatsClient{byBase: map[string][]federation.StatEntry{
		"https://friend": {{OriginFingerprint: friendID.Fingerprint(), Hops: 0, Metric: federation.MetricGamePlay, Key: "igdb:1", PlayTimeSeconds: 1}},
	}}
	h := statsHandler(database, selfID, fake)

	refreshed, _ := h.RefreshFederationStats()
	assert.Equal(t, 0, refreshed)
	cached, _ := h.Snapshots.EntriesWithinHops(-1)
	assert.Empty(t, cached)
}

func TestRefresh_SanitizesLoopAndOverBudgetEntries(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	policyPeer(t, database, friendID, "Friend", "https://friend", true, true)

	fake := &fakeStatsClient{byBase: map[string][]federation.StatEntry{
		"https://friend": {
			{OriginFingerprint: selfID.Fingerprint(), Hops: 0, Metric: federation.MetricGamePlay, Key: "loop", PlayTimeSeconds: 100},             // our own data looping back
			{OriginFingerprint: "X", Hops: federation.MaxFederationHops, Metric: federation.MetricGamePlay, Key: "toofar", PlayTimeSeconds: 100}, // over budget
			{OriginFingerprint: "Y", Hops: 1, Metric: federation.MetricGamePlay, Key: "ok", PlayTimeSeconds: 100},                                // good
		},
	}}
	h := statsHandler(database, selfID, fake)

	_, _ = h.RefreshFederationStats()
	cached, err := h.Snapshots.EntriesWithinHops(-1)
	require.NoError(t, err)
	require.Len(t, cached, 1, "loop + over-budget entries dropped")
	assert.Equal(t, "Y", cached[0].OriginFingerprint)
	assert.Equal(t, 2, cached[0].Hops)
}

func TestRefresh_RecordsErrorOnFetchFailure(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	policyPeer(t, database, friendID, "Friend", "https://friend", true, true)
	h := statsHandler(database, selfID, &fakeStatsClient{err: errors.New("connection refused")})

	refreshed, failed := h.RefreshFederationStats()
	assert.Equal(t, 0, refreshed)
	assert.Equal(t, 1, failed)

	var errs int64
	database.Model(&db.FederationExchange{}).Where("operation = ? AND status = ?", "stats_pull", db.ExchangeError).Count(&errs)
	assert.Equal(t, int64(1), errs)
}

// --- Aggregated read (from snapshot store) --------------------------------

func TestAggregatedStats_MergesLocalAndSnapshots(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	seedLocalPlay(t, database, "igdb:1", 100) // local: igdb:1 = 100
	h := statsHandler(database, selfID, nil)

	// A previously-refreshed friend snapshot for the same game (hop 1).
	require.NoError(t, h.Snapshots.ReplacePeerSnapshot("B", []federation.StatEntry{
		{OriginFingerprint: "B", Hops: 1, Metric: federation.MetricGamePlay, Key: "igdb:1", Label: "Local Game", PlayTimeSeconds: 250, Players: 5},
	}, time.Unix(1, 0)))

	out, err := h.HumaAggregatedStats(context.Background(), &AggregatedStatsInput{Metric: "game_play"})
	require.NoError(t, err)
	require.Len(t, out.Body.Stats, 1)
	assert.Equal(t, int64(350), out.Body.Stats[0].TotalPlayTime, "local 100 + snapshot 250")
	assert.Empty(t, out.Body.Stats[0].Sources, "per-source breakdown stripped from the user-facing response")
}

func TestAggregatedStats_RespectsViewerMaxHops(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	seedLocalPlay(t, database, "igdb:local", 10)
	h := statsHandler(database, selfID, nil)

	require.NoError(t, h.Snapshots.ReplacePeerSnapshot("B", []federation.StatEntry{
		{OriginFingerprint: "B", Hops: 1, Metric: federation.MetricGamePlay, Key: "near", Label: "Near", PlayTimeSeconds: 1},
		{OriginFingerprint: "D", Hops: 3, Metric: federation.MetricGamePlay, Key: "far", Label: "Far", PlayTimeSeconds: 1},
	}, time.Unix(1, 0)))

	out, err := h.HumaAggregatedStats(context.Background(), &AggregatedStatsInput{Metric: "game_play", MaxHops: 1})
	require.NoError(t, err)
	keys := map[string]bool{}
	for _, s := range out.Body.Stats {
		keys[s.Key] = true
	}
	assert.True(t, keys["igdb:local"], "local always included")
	assert.True(t, keys["near"], "hop-1 included at maxHops=1")
	assert.False(t, keys["far"], "hop-3 excluded at maxHops=1")
}
