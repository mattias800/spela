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
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// fakePresenceClient returns canned presence entries keyed by base URL.
type fakePresenceClient struct {
	byBase map[string][]federation.PresenceEntry
	err    error
}

func (f *fakePresenceClient) FetchPresence(baseURL, _ string, _ federation.Identity, _ string) ([]federation.PresenceEntry, error) {
	if f.err != nil {
		return nil, f.err
	}
	return f.byBase[baseURL], nil
}

// presencePolicyPeer upserts an active peer with a presence share/consume policy.
func presencePolicyPeer(t *testing.T, database *gorm.DB, id federation.Identity, name, baseURL string, share, consume bool) {
	t.Helper()
	sp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassPresence: share})
	cp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassPresence: consume})
	require.NoError(t, federation.PeerStore{DB: database}.Upsert(&db.FederationPeer{
		Fingerprint: id.Fingerprint(), PublicKey: b64(id.PublicKey), Name: name, BaseURL: baseURL,
		Status: db.PeerStatusActive, SharePolicy: sp, ConsumePolicy: cp,
	}))
}

func presenceHandler(database *gorm.DB, selfID federation.Identity, hub *ws.Hub, client presenceClient) *FederationHandler {
	return &FederationHandler{
		DB: database, Identity: selfID,
		Peers:          federation.PeerStore{DB: database},
		BaseURL:        "https://self",
		Hub:            hub,
		PresenceClient: client,
	}
}

// seedPresenceSession creates a public user + an IGDB-identified game and marks
// the user as currently playing it in the hub. Returns the game's cross-key.
func seedPresenceSession(t *testing.T, database *gorm.DB, hub *ws.Hub, username, scraperID string) string {
	t.Helper()
	u := db.User{Username: username, Email: username + "@x.test", PasswordHash: "h", ProfileVisibility: "public"}
	require.NoError(t, database.Create(&u).Error)
	g := db.Game{Title: "Game " + scraperID, ScraperID: scraperID, FilePath: "/g-" + scraperID, ConsoleID: 1}
	require.NoError(t, database.Create(&g).Error)
	hub.SetUserGame(u.ID, g.ID)
	return scraperID
}

// --- Sanitize -------------------------------------------------------------

func TestSanitizePresenceBatch_DropsLoopsSelfAndNonZeroHops(t *testing.T) {
	in := []federation.PresenceEntry{
		{OriginFingerprint: "self", Hops: 0, Username: "loop"},    // our own origin looping back
		{OriginFingerprint: "", Hops: 0, Username: "noorigin"},    // missing origin
		{OriginFingerprint: "X", Hops: 1, Username: "transitive"}, // friend relaying (not allowed)
		{OriginFingerprint: "Y", Hops: 0, Username: "good"},       // accepted
	}
	out := sanitizePresenceBatch(in, "self")
	require.Len(t, out, 1)
	assert.Equal(t, "good", out[0].Username)
}

// --- Export ---------------------------------------------------------------

func callExportPresence(h *FederationHandler, peer *db.FederationPeer) *httptest.ResponseRecorder {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.GET("/x", func(c *gin.Context) {
		c.Set(fedPeerContextKey, peer)
		h.ginExportPresence(c)
	})
	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/x", nil))
	return w
}

func TestExportPresence_ForbiddenWhenNotShared(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	hub := ws.NewHub(nil)
	seedPresenceSession(t, database, hub, "alice", "igdb:1")
	h := presenceHandler(database, selfID, hub, nil)

	peer := &db.FederationPeer{Fingerprint: "fp-friend", Name: "Friend", SharePolicy: "", Status: db.PeerStatusActive}
	w := callExportPresence(h, peer)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestExportPresence_ServesLocalPresence(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	hub := ws.NewHub(nil)
	seedPresenceSession(t, database, hub, "alice", "igdb:1")
	h := presenceHandler(database, selfID, hub, nil)

	sp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassPresence: true})
	peer := &db.FederationPeer{Fingerprint: "fp-friend", Name: "Friend", SharePolicy: sp, Status: db.PeerStatusActive}

	w := callExportPresence(h, peer)
	require.Equal(t, http.StatusOK, w.Code)

	var body struct {
		Entries []federation.PresenceEntry `json:"entries"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &body))
	require.Len(t, body.Entries, 1)
	assert.Equal(t, selfID.Fingerprint(), body.Entries[0].OriginFingerprint)
	assert.Equal(t, 0, body.Entries[0].Hops)
	assert.Equal(t, "alice", body.Entries[0].Username)
	assert.Equal(t, "igdb:1", body.Entries[0].GameKey)
}

// --- Aggregated read ------------------------------------------------------

func TestAggregatedPresence_MergesLocalAndLiveFriend(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	presencePolicyPeer(t, database, friendID, "Friend B", "https://friend", true, true)

	hub := ws.NewHub(nil)
	seedPresenceSession(t, database, hub, "localalice", "igdb:1")

	fake := &fakePresenceClient{byBase: map[string][]federation.PresenceEntry{
		"https://friend": {{
			OriginFingerprint: friendID.Fingerprint(), Hops: 0,
			Username: "remotebob", GameKey: "igdb:99", GameTitle: "Remote Game",
		}},
	}}
	h := presenceHandler(database, selfID, hub, fake)

	out, err := h.HumaAggregatedPresence(context.Background(), &AggregatedPresenceInput{})
	require.NoError(t, err)
	require.Len(t, out.Body.Presence, 2)

	byUser := map[string]federation.PresenceEntry{}
	for _, e := range out.Body.Presence {
		byUser[e.Username] = e
		assert.Empty(t, e.OriginFingerprint, "origin fingerprint stripped from the user-facing response")
	}

	local, ok := byUser["localalice"]
	require.True(t, ok)
	assert.Equal(t, 0, local.Hops)
	assert.Empty(t, local.ServerName, "local presence has no server label")

	remote, ok := byUser["remotebob"]
	require.True(t, ok)
	assert.Equal(t, 1, remote.Hops, "friend's hop-0 datum surfaces at hop 1")
	assert.Equal(t, "Friend B", remote.ServerName)
	assert.Equal(t, "igdb:99", remote.GameKey)
}

func TestAggregatedPresence_SkipsNonConsumablePeer(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	presencePolicyPeer(t, database, friendID, "Friend B", "https://friend", true, false) // consume=false

	hub := ws.NewHub(nil)
	seedPresenceSession(t, database, hub, "localalice", "igdb:1")

	fake := &fakePresenceClient{byBase: map[string][]federation.PresenceEntry{
		"https://friend": {{OriginFingerprint: friendID.Fingerprint(), Hops: 0, Username: "remotebob", GameKey: "igdb:99"}},
	}}
	h := presenceHandler(database, selfID, hub, fake)

	out, err := h.HumaAggregatedPresence(context.Background(), &AggregatedPresenceInput{})
	require.NoError(t, err)
	require.Len(t, out.Body.Presence, 1, "non-consumable peer's presence is not pulled")
	assert.Equal(t, "localalice", out.Body.Presence[0].Username)
}

func TestAggregatedPresence_RecordsErrorOnPullFailure(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	presencePolicyPeer(t, database, friendID, "Friend B", "https://friend", true, true)

	h := presenceHandler(database, selfID, ws.NewHub(nil), &fakePresenceClient{err: errors.New("connection refused")})

	out, err := h.HumaAggregatedPresence(context.Background(), &AggregatedPresenceInput{})
	require.NoError(t, err)
	assert.Empty(t, out.Body.Presence, "a failed pull contributes nothing but does not fail the read")

	var errs int64
	database.Model(&db.FederationExchange{}).Where("operation = ? AND status = ?", "presence_pull", db.ExchangeError).Count(&errs)
	assert.Equal(t, int64(1), errs)
}

// --- Two-server (real signed HTTP) ----------------------------------------

// TestTwoServers_PresenceOverHTTP wires two real federation servers over
// httptest and exercises the live cross-mesh presence path end-to-end: server B
// has a user playing a game; server A pulls B's presence live (signed GET,
// verified by B's middleware, SharePolicy(presence)-gated) and surfaces it.
func TestTwoServers_PresenceOverHTTP(t *testing.T) {
	gin.SetMode(gin.TestMode)

	// --- Server B (origin): a user playing a game, presence export routes.
	dbB := openAPIFedTestDB(t)
	idB, _ := federation.GenerateIdentity()
	hubB := ws.NewHub(nil)
	hB := &FederationHandler{
		DB: dbB, Identity: idB, Peers: federation.PeerStore{DB: dbB},
		Snapshots: federation.SnapshotStore{DB: dbB}, CatalogSnapshots: federation.CatalogSnapshotStore{DB: dbB},
		Hub: hubB,
	}
	rB := gin.New()
	RegisterFederationGinRoutes(rB, hB, NewRateLimiter(1000, time.Minute))
	srvB := httptest.NewServer(rB)
	defer srvB.Close()
	hB.BaseURL = srvB.URL
	seedPresenceSession(t, dbB, hubB, "remotebob", "igdb:1022")

	// --- Server A (consumer). PresenceClient nil => real signed HTTP to B.
	dbA := openAPIFedTestDB(t)
	idA, _ := federation.GenerateIdentity()
	hA := &FederationHandler{
		DB: dbA, Identity: idA, Peers: federation.PeerStore{DB: dbA},
		Snapshots: federation.SnapshotStore{DB: dbA}, CatalogSnapshots: federation.CatalogSnapshotStore{DB: dbA},
	}

	// Mutual policy: A consumes presence from B; B shares presence with A.
	presencePolicyPeer(t, dbA, idB, "Server B", srvB.URL, false, true)
	presencePolicyPeer(t, dbB, idA, "Server A", "", true, false)

	out, err := hA.HumaAggregatedPresence(context.Background(), &AggregatedPresenceInput{})
	require.NoError(t, err)
	require.Len(t, out.Body.Presence, 1)
	g := out.Body.Presence[0]
	assert.Equal(t, "remotebob", g.Username)
	assert.Equal(t, "igdb:1022", g.GameKey)
	assert.Equal(t, 1, g.Hops)
	assert.Equal(t, "Server B", g.ServerName)
	assert.Empty(t, g.OriginFingerprint, "origin fingerprint stripped from the user-facing response")
}

// TestTwoServers_PresenceBlockedWithoutSharePolicy verifies the per-peer gate:
// if B does not share presence with A, A's live pull is rejected and surfaces
// nothing.
func TestTwoServers_PresenceBlockedWithoutSharePolicy(t *testing.T) {
	gin.SetMode(gin.TestMode)

	dbB := openAPIFedTestDB(t)
	idB, _ := federation.GenerateIdentity()
	hubB := ws.NewHub(nil)
	hB := &FederationHandler{
		DB: dbB, Identity: idB, Peers: federation.PeerStore{DB: dbB},
		Snapshots: federation.SnapshotStore{DB: dbB}, CatalogSnapshots: federation.CatalogSnapshotStore{DB: dbB},
		Hub: hubB,
	}
	rB := gin.New()
	RegisterFederationGinRoutes(rB, hB, NewRateLimiter(1000, time.Minute))
	srvB := httptest.NewServer(rB)
	defer srvB.Close()
	hB.BaseURL = srvB.URL
	seedPresenceSession(t, dbB, hubB, "remotebob", "igdb:1022")

	dbA := openAPIFedTestDB(t)
	idA, _ := federation.GenerateIdentity()
	hA := &FederationHandler{
		DB: dbA, Identity: idA, Peers: federation.PeerStore{DB: dbA},
		Snapshots: federation.SnapshotStore{DB: dbA}, CatalogSnapshots: federation.CatalogSnapshotStore{DB: dbA},
	}

	// A wants to consume; but B does NOT share presence with A (share=false).
	presencePolicyPeer(t, dbA, idB, "Server B", srvB.URL, false, true)
	presencePolicyPeer(t, dbB, idA, "Server A", "", false, false)

	out, err := hA.HumaAggregatedPresence(context.Background(), &AggregatedPresenceInput{})
	require.NoError(t, err)
	assert.Empty(t, out.Body.Presence, "A surfaces nothing without B's share consent")
}
