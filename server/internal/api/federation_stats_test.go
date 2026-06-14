package api

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// fakeStatsClient returns canned friend entries keyed by base URL.
type fakeStatsClient struct {
	byBase map[string][]federation.StatEntry
	err    error
}

func (f fakeStatsClient) FetchStats(baseURL, _ string, _ federation.Identity, _ string) ([]federation.StatEntry, error) {
	if f.err != nil {
		return nil, f.err
	}
	return f.byBase[baseURL], nil
}

func seedLocalPlay(t *testing.T, database *gorm.DB, scraperID string, playTime int64) {
	t.Helper()
	u := db.User{Username: "localuser", Email: "l@x.test", PasswordHash: "h", ProfileVisibility: "public"}
	require.NoError(t, database.Create(&u).Error)
	g := db.Game{Title: "Local Game", ScraperID: scraperID, FilePath: "/lg", ConsoleID: 1}
	require.NoError(t, database.Create(&g).Error)
	require.NoError(t, database.Create(&db.PlayHistory{UserID: u.ID, GameID: g.ID, PlayTime: playTime}).Error)
}

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

func TestExportStats_SharesStampedRollupWhenPolicyAllows(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	seedLocalPlay(t, database, "igdb:1", 100)
	h := &FederationHandler{DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database}, BaseURL: "https://self"}

	sp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassStats: true})
	peer := &db.FederationPeer{Fingerprint: "fp-friend", Name: "Friend", SharePolicy: sp, Status: db.PeerStatusActive}

	w := callExport(h, peer)
	require.Equal(t, http.StatusOK, w.Code)

	var body struct {
		Entries []federation.StatEntry `json:"entries"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &body))
	require.NotEmpty(t, body.Entries)
	for _, e := range body.Entries {
		assert.Equal(t, selfID.Fingerprint(), e.OriginFingerprint)
		assert.Equal(t, 0, e.Hops)
	}
}

func TestExportStats_ForbiddenWhenNotShared(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	seedLocalPlay(t, database, "igdb:1", 100)
	h := &FederationHandler{DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database}, BaseURL: "https://self"}

	// SharePolicy does not include stats.
	peer := &db.FederationPeer{Fingerprint: "fp-friend", Name: "Friend", SharePolicy: "", Status: db.PeerStatusActive}

	w := callExport(h, peer)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestAggregatedStats_MergesLocalAndFriend(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	seedLocalPlay(t, database, "igdb:1", 100) // local: game igdb:1 = 100

	// An active friend we consume stats from.
	policyPeer(t, database, friendID, "Friend", "https://friend", true, true)

	// Friend reports the same game igdb:1 with 250 (hop 0 on their side).
	fake := fakeStatsClient{byBase: map[string][]federation.StatEntry{
		"https://friend": {{
			OriginFingerprint: friendID.Fingerprint(), Hops: 0,
			Metric: federation.MetricGamePlay, Key: "igdb:1", Label: "Local Game", PlayTimeSeconds: 250, Players: 5,
		}},
	}}
	h := &FederationHandler{DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database}, BaseURL: "https://self", StatsClient: fake}

	out, err := h.HumaAggregatedStats(context.Background(), &AggregatedStatsInput{Metric: "game_play"})
	require.NoError(t, err)
	require.Len(t, out.Body.Stats, 1)
	assert.Equal(t, "igdb:1", out.Body.Stats[0].Key)
	assert.Equal(t, int64(350), out.Body.Stats[0].TotalPlayTime, "local 100 + friend 250")
	require.Len(t, out.Body.Stats[0].Sources, 2)

	// A successful pull was recorded in the ledger.
	var pulls int64
	database.Model(&db.FederationExchange{}).Where("operation = ? AND status = ?", "stats_pull", db.ExchangeOK).Count(&pulls)
	assert.Equal(t, int64(1), pulls)
}

func TestAggregatedStats_SkipsFriendOnErrorButReturnsLocal(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	seedLocalPlay(t, database, "igdb:1", 100)
	policyPeer(t, database, friendID, "Friend", "https://friend", true, true)

	h := &FederationHandler{DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database}, BaseURL: "https://self",
		StatsClient: fakeStatsClient{err: errors.New("connection refused")}}

	out, err := h.HumaAggregatedStats(context.Background(), &AggregatedStatsInput{Metric: "game_play"})
	require.NoError(t, err, "an unreachable friend must not fail the whole read")
	require.Len(t, out.Body.Stats, 1)
	assert.Equal(t, int64(100), out.Body.Stats[0].TotalPlayTime, "local-only when friend is down")

	var errs int64
	database.Model(&db.FederationExchange{}).Where("operation = ? AND status = ?", "stats_pull", db.ExchangeError).Count(&errs)
	assert.Equal(t, int64(1), errs)
}

func TestAggregatedStats_DoesNotPullWhenConsumeDisabled(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	seedLocalPlay(t, database, "igdb:1", 100)
	// share=true but consume=false → we must NOT pull stats from this peer.
	policyPeer(t, database, friendID, "Friend", "https://friend", true, false)

	called := false
	h := &FederationHandler{DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database}, BaseURL: "https://self",
		StatsClient: fakeStatsClientFunc(func() { called = true })}

	out, err := h.HumaAggregatedStats(context.Background(), &AggregatedStatsInput{Metric: "game_play"})
	require.NoError(t, err)
	assert.False(t, called, "must not fetch from a peer we don't consume stats from")
	require.Len(t, out.Body.Stats, 1)
	assert.Equal(t, int64(100), out.Body.Stats[0].TotalPlayTime)
}

// fakeStatsClientFunc records whether FetchStats was invoked.
type fakeStatsClientFunc func()

func (f fakeStatsClientFunc) FetchStats(_, _ string, _ federation.Identity, _ string) ([]federation.StatEntry, error) {
	f()
	return nil, nil
}
