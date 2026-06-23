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

// fakeAchievementsClient returns canned leaderboard rows keyed by base URL.
type fakeAchievementsClient struct {
	byBase map[string][]federation.AchievementEntry
	err    error
}

func (f *fakeAchievementsClient) FetchAchievements(baseURL, _ string, _ federation.Identity, _ string) ([]federation.AchievementEntry, error) {
	if f.err != nil {
		return nil, f.err
	}
	return f.byBase[baseURL], nil
}

// achievementsPolicyPeer upserts an active peer with an achievements policy.
func achievementsPolicyPeer(t *testing.T, database *gorm.DB, id federation.Identity, name, baseURL string, share, consume bool) {
	t.Helper()
	sp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassAchievement: share})
	cp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassAchievement: consume})
	require.NoError(t, federation.PeerStore{DB: database}.Upsert(&db.FederationPeer{
		Fingerprint: id.Fingerprint(), PublicKey: b64(id.PublicKey), Name: name, BaseURL: baseURL,
		Status: db.PeerStatusActive, SharePolicy: sp, ConsumePolicy: cp,
	}))
}

func achievementsHandler(database *gorm.DB, selfID federation.Identity, client achievementsClient) *FederationHandler {
	return &FederationHandler{
		DB: database, Identity: selfID,
		Peers:              federation.PeerStore{DB: database},
		BaseURL:            "https://self",
		AchievementsClient: client,
	}
}

// seedAchievements creates a public+active user with `count` unlocked achievements.
func seedAchievements(t *testing.T, database *gorm.DB, username string, count int) {
	t.Helper()
	u := db.User{Username: username, Email: username + "@x.test", PasswordHash: "h", ProfileVisibility: "public"}
	require.NoError(t, database.Create(&u).Error)
	for i := 0; i < count; i++ {
		require.NoError(t, database.Create(&db.UserAchievementProgress{
			UserID: u.ID, AchievementRAID: uint(i + 1), RAGameID: 1,
		}).Error)
	}
}

// --- Export ---------------------------------------------------------------

func callExportAchievements(h *FederationHandler, peer *db.FederationPeer) *httptest.ResponseRecorder {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.GET("/x", func(c *gin.Context) {
		c.Set(fedPeerContextKey, peer)
		h.ginExportAchievements(c)
	})
	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/x", nil))
	return w
}

func TestExportAchievements_ForbiddenWhenNotShared(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	seedAchievements(t, database, "alice", 3)
	h := achievementsHandler(database, selfID, nil)

	peer := &db.FederationPeer{Fingerprint: "fp-friend", Name: "Friend", SharePolicy: "", Status: db.PeerStatusActive}
	w := callExportAchievements(h, peer)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestExportAchievements_ServesLocalLeaderboard(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	seedAchievements(t, database, "alice", 3)
	h := achievementsHandler(database, selfID, nil)

	sp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassAchievement: true})
	peer := &db.FederationPeer{Fingerprint: "fp-friend", Name: "Friend", SharePolicy: sp, Status: db.PeerStatusActive}

	w := callExportAchievements(h, peer)
	require.Equal(t, http.StatusOK, w.Code)
	var body struct {
		Entries []federation.AchievementEntry `json:"entries"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &body))
	require.Len(t, body.Entries, 1)
	assert.Equal(t, selfID.Fingerprint(), body.Entries[0].OriginFingerprint)
	assert.Equal(t, 0, body.Entries[0].Hops)
	assert.Equal(t, "alice", body.Entries[0].Username)
	assert.Equal(t, int64(3), body.Entries[0].Count)
}

// --- Aggregated read ------------------------------------------------------

func TestAggregatedAchievements_MergesLocalAndPeerSortedByCount(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	achievementsPolicyPeer(t, database, friendID, "Server B", "https://b", true, true)

	seedAchievements(t, database, "localalice", 5)
	fake := &fakeAchievementsClient{byBase: map[string][]federation.AchievementEntry{
		"https://b": {{OriginFingerprint: friendID.Fingerprint(), Hops: 0, Username: "remotebob", Count: 20}},
	}}
	h := achievementsHandler(database, selfID, fake)

	out, err := h.HumaAggregatedAchievements(context.Background(), &AggregatedAchievementsInput{})
	require.NoError(t, err)
	require.Len(t, out.Body.Achievements, 2)
	// Sorted by count desc: remotebob (20) before localalice (5).
	assert.Equal(t, "remotebob", out.Body.Achievements[0].Username)
	assert.Equal(t, 1, out.Body.Achievements[0].Hops)
	assert.Equal(t, "Server B", out.Body.Achievements[0].ServerName)
	assert.Equal(t, "localalice", out.Body.Achievements[1].Username)
	for _, e := range out.Body.Achievements {
		assert.Empty(t, e.OriginFingerprint, "origin fingerprint stripped from the user-facing response")
	}
}

func TestAggregatedAchievements_SkipsNonConsumablePeer(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	achievementsPolicyPeer(t, database, friendID, "Server B", "https://b", true, false) // consume=false

	seedAchievements(t, database, "localalice", 5)
	fake := &fakeAchievementsClient{byBase: map[string][]federation.AchievementEntry{
		"https://b": {{OriginFingerprint: friendID.Fingerprint(), Hops: 0, Username: "remotebob", Count: 20}},
	}}
	h := achievementsHandler(database, selfID, fake)

	out, err := h.HumaAggregatedAchievements(context.Background(), &AggregatedAchievementsInput{})
	require.NoError(t, err)
	require.Len(t, out.Body.Achievements, 1)
	assert.Equal(t, "localalice", out.Body.Achievements[0].Username)
}

func TestAggregatedAchievements_RecordsErrorOnPullFailure(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	achievementsPolicyPeer(t, database, friendID, "Server B", "https://b", true, true)
	h := achievementsHandler(database, selfID, &fakeAchievementsClient{err: errors.New("connection refused")})

	out, err := h.HumaAggregatedAchievements(context.Background(), &AggregatedAchievementsInput{})
	require.NoError(t, err)
	assert.Empty(t, out.Body.Achievements, "a failed pull contributes nothing but does not fail the read")

	var errs int64
	database.Model(&db.FederationExchange{}).Where("operation = ? AND status = ?", "achievements_pull", db.ExchangeError).Count(&errs)
	assert.Equal(t, int64(1), errs)
}

func TestAggregatedAchievements_RespectsLimit(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	seedAchievements(t, database, "a", 3)
	seedAchievements(t, database, "b", 2)
	seedAchievements(t, database, "c", 1)
	h := achievementsHandler(database, selfID, nil)

	out, err := h.HumaAggregatedAchievements(context.Background(), &AggregatedAchievementsInput{Limit: 2})
	require.NoError(t, err)
	require.Len(t, out.Body.Achievements, 2)
	assert.Equal(t, "a", out.Body.Achievements[0].Username) // top by count
	assert.Equal(t, "b", out.Body.Achievements[1].Username)
}

// --- Two-server (real signed HTTP) ----------------------------------------

func TestTwoServers_AchievementsOverHTTP(t *testing.T) {
	gin.SetMode(gin.TestMode)

	// Server B (origin): a user with achievements + export routes.
	dbB := openAPIFedTestDB(t)
	idB, _ := federation.GenerateIdentity()
	hB := &FederationHandler{
		DB: dbB, Identity: idB, Peers: federation.PeerStore{DB: dbB},
		Snapshots: federation.SnapshotStore{DB: dbB}, CatalogSnapshots: federation.CatalogSnapshotStore{DB: dbB},
	}
	rB := gin.New()
	RegisterFederationGinRoutes(rB, hB, NewRateLimiter(1000, time.Minute))
	srvB := httptest.NewServer(rB)
	defer srvB.Close()
	hB.BaseURL = srvB.URL
	seedAchievements(t, dbB, "remotebob", 7)

	// Server A (consumer). AchievementsClient nil => real signed HTTP to B.
	dbA := openAPIFedTestDB(t)
	idA, _ := federation.GenerateIdentity()
	hA := &FederationHandler{
		DB: dbA, Identity: idA, Peers: federation.PeerStore{DB: dbA},
		Snapshots: federation.SnapshotStore{DB: dbA}, CatalogSnapshots: federation.CatalogSnapshotStore{DB: dbA},
	}

	// Mutual policy: A consumes from B; B shares with A.
	achievementsPolicyPeer(t, dbA, idB, "Server B", srvB.URL, false, true)
	achievementsPolicyPeer(t, dbB, idA, "Server A", "", true, false)

	out, err := hA.HumaAggregatedAchievements(context.Background(), &AggregatedAchievementsInput{})
	require.NoError(t, err)
	require.Len(t, out.Body.Achievements, 1)
	g := out.Body.Achievements[0]
	assert.Equal(t, "remotebob", g.Username)
	assert.Equal(t, int64(7), g.Count)
	assert.Equal(t, 1, g.Hops)
	assert.Equal(t, "Server B", g.ServerName)
	assert.Empty(t, g.OriginFingerprint)
}

func TestTwoServers_AchievementsBlockedWithoutSharePolicy(t *testing.T) {
	gin.SetMode(gin.TestMode)

	dbB := openAPIFedTestDB(t)
	idB, _ := federation.GenerateIdentity()
	hB := &FederationHandler{
		DB: dbB, Identity: idB, Peers: federation.PeerStore{DB: dbB},
		Snapshots: federation.SnapshotStore{DB: dbB}, CatalogSnapshots: federation.CatalogSnapshotStore{DB: dbB},
	}
	rB := gin.New()
	RegisterFederationGinRoutes(rB, hB, NewRateLimiter(1000, time.Minute))
	srvB := httptest.NewServer(rB)
	defer srvB.Close()
	hB.BaseURL = srvB.URL
	seedAchievements(t, dbB, "remotebob", 7)

	dbA := openAPIFedTestDB(t)
	idA, _ := federation.GenerateIdentity()
	hA := &FederationHandler{
		DB: dbA, Identity: idA, Peers: federation.PeerStore{DB: dbA},
		Snapshots: federation.SnapshotStore{DB: dbA}, CatalogSnapshots: federation.CatalogSnapshotStore{DB: dbA},
	}

	// A wants to consume; B does NOT share achievements with A.
	achievementsPolicyPeer(t, dbA, idB, "Server B", srvB.URL, false, true)
	achievementsPolicyPeer(t, dbB, idA, "Server A", "", false, false)

	out, err := hA.HumaAggregatedAchievements(context.Background(), &AggregatedAchievementsInput{})
	require.NoError(t, err)
	assert.Empty(t, out.Body.Achievements, "A sees nothing without B's share consent")
}
