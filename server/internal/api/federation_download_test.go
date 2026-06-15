package api

import (
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

type fakeDownloadClient struct {
	body   string
	status int
	err    error
}

func (f *fakeDownloadClient) FetchDownload(_, _ string, _ federation.Identity, _, _ string, _ int) (*http.Response, error) {
	if f.err != nil {
		return nil, f.err
	}
	st := f.status
	if st == 0 {
		st = http.StatusOK
	}
	// Simulate a HOSTILE peer trying to get HTML rendered on our origin.
	return &http.Response{
		StatusCode: st,
		Body:       io.NopCloser(strings.NewReader(f.body)),
		Header:     http.Header{"Content-Type": {"text/html"}, "Content-Disposition": {"inline"}},
	}, nil
}

func downloadHandler(database *gorm.DB, selfID federation.Identity, gameDirs []string, client downloadClient) *FederationHandler {
	return &FederationHandler{
		DB: database, Identity: selfID,
		Peers:            federation.PeerStore{DB: database},
		CatalogSnapshots: federation.CatalogSnapshotStore{DB: database},
		GameDirs:         gameDirs,
		BaseURL:          "https://self",
		DownloadClient:   client,
	}
}

// seedLocalROM writes a fake ROM file into a temp game dir and registers a game.
func seedLocalROM(t *testing.T, database *gorm.DB, scraperID, contents string) string {
	t.Helper()
	dir := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(dir, "rom.bin"), []byte(contents), 0o644))
	var console db.Console
	if err := database.Where("abbreviation = ?", "SNES").First(&console).Error; err != nil {
		console = db.Console{Name: "Super Nintendo", Abbreviation: "SNES"}
		require.NoError(t, database.Create(&console).Error)
	}
	require.NoError(t, database.Create(&db.Game{Title: "Local", ScraperID: scraperID, FilePath: "rom.bin", ConsoleID: console.ID}).Error)
	return dir
}

func callServeDownload(h *FederationHandler, peer *db.FederationPeer, key string) *httptest.ResponseRecorder {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.GET("/x", func(c *gin.Context) {
		c.Set(fedPeerContextKey, peer)
		h.ginServeDownload(c)
	})
	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/x?key="+key, nil))
	return w
}

func TestServeDownload_StreamsLocalWhenShared(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	dir := seedLocalROM(t, database, "igdb:1", "ROM-BYTES-HERE")
	h := downloadHandler(database, selfID, []string{dir}, nil)

	sp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassDownload: true})
	peer := &db.FederationPeer{Fingerprint: "fp", Name: "Friend", SharePolicy: sp, Status: db.PeerStatusActive}

	w := callServeDownload(h, peer, "igdb:1")
	require.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "ROM-BYTES-HERE", w.Body.String())
}

func TestServeDownload_ForbiddenWhenNotShared(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	dir := seedLocalROM(t, database, "igdb:1", "X")
	h := downloadHandler(database, selfID, []string{dir}, nil)

	peer := &db.FederationPeer{Fingerprint: "fp", Name: "Friend", SharePolicy: "", Status: db.PeerStatusActive}
	w := callServeDownload(h, peer, "igdb:1")
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestServeDownload_NotFoundWhenNotLocal(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	h := downloadHandler(database, selfID, []string{t.TempDir()}, nil)

	sp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassDownload: true})
	peer := &db.FederationPeer{Fingerprint: "fp", Name: "Friend", SharePolicy: sp, Status: db.PeerStatusActive}
	w := callServeDownload(h, peer, "igdb:nope")
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func callUserDownload(h *FederationHandler, key string) *httptest.ResponseRecorder {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.GET("/x", h.ginUserDownload)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/x?key="+key, nil))
	return w
}

func TestUserDownload_ProxiesFromDirectFriend(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()

	// Friend is active and we consume downloads from them.
	dp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassDownload: true})
	require.NoError(t, federation.PeerStore{DB: database}.Upsert(&db.FederationPeer{
		Fingerprint: friendID.Fingerprint(), PublicKey: b64(friendID.PublicKey), Name: "Friend",
		BaseURL: "https://friend", Status: db.PeerStatusActive, ConsumePolicy: dp,
	}))
	// Catalog says the friend (hop 1) has the game.
	cs := federation.CatalogSnapshotStore{DB: database}
	require.NoError(t, cs.ReplacePeerSnapshot(friendID.Fingerprint(), []federation.CatalogEntry{
		{OriginFingerprint: friendID.Fingerprint(), Hops: 1, Key: "igdb:7", Title: "Friend Game", Console: "NES"},
	}, time.Unix(1, 0)))

	h := downloadHandler(database, selfID, nil, &fakeDownloadClient{body: "FRIEND-ROM"})

	w := callUserDownload(h, "igdb:7")
	require.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "FRIEND-ROM", w.Body.String())
	// SECURITY: the peer's malicious text/html content typing must NOT be echoed.
	assert.Equal(t, "application/octet-stream", w.Header().Get("Content-Type"))
	assert.Contains(t, w.Header().Get("Content-Disposition"), "attachment")
	assert.Equal(t, "nosniff", w.Header().Get("X-Content-Type-Options"))
}

func TestUserDownload_NotFoundWhenNoConsumableSource(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()

	// Friend has the game but we do NOT consume downloads from them.
	require.NoError(t, federation.PeerStore{DB: database}.Upsert(&db.FederationPeer{
		Fingerprint: friendID.Fingerprint(), PublicKey: b64(friendID.PublicKey), Name: "Friend",
		BaseURL: "https://friend", Status: db.PeerStatusActive, ConsumePolicy: "",
	}))
	cs := federation.CatalogSnapshotStore{DB: database}
	require.NoError(t, cs.ReplacePeerSnapshot(friendID.Fingerprint(), []federation.CatalogEntry{
		{OriginFingerprint: friendID.Fingerprint(), Hops: 1, Key: "igdb:7", Title: "Friend Game", Console: "NES"},
	}, time.Unix(1, 0)))

	called := false
	h := downloadHandler(database, selfID, nil, downloadClientFunc(func() { called = true }))

	w := callUserDownload(h, "igdb:7")
	assert.Equal(t, http.StatusNotFound, w.Code)
	assert.False(t, called, "must not fetch from a peer we don't consume downloads from")
}

type downloadClientFunc func()

func (f downloadClientFunc) FetchDownload(_, _ string, _ federation.Identity, _, _ string, _ int) (*http.Response, error) {
	f()
	return &http.Response{StatusCode: http.StatusOK, Body: io.NopCloser(strings.NewReader("")), Header: http.Header{}}, nil
}

// --- Multi-hop relay (#1348 Phase 3b-2) ------------------------------------

func setRelayEnabled(t *testing.T, database *gorm.DB, on bool) {
	t.Helper()
	v := "false"
	if on {
		v = "true"
	}
	require.NoError(t, database.Save(&db.ServerSetting{Key: "federation_relay_enabled", Value: v}).Error)
}

// relaySetup wires: a requester peer (shares nothing back, just asks us) and a
// source friend that offers the key (we consume downloads from them). Returns
// the requester peer to pass into the serve handler.
func relaySetup(t *testing.T, database *gorm.DB, key string) (*db.FederationPeer, federation.Identity) {
	t.Helper()
	requesterID, _ := federation.GenerateIdentity()
	sourceID, _ := federation.GenerateIdentity()

	// We must share downloads with the requester (so CanShare passes).
	sp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassDownload: true})
	requester := &db.FederationPeer{Fingerprint: requesterID.Fingerprint(), Name: "Requester", SharePolicy: sp, Status: db.PeerStatusActive}

	// The source friend offers the key and we consume downloads from them.
	dp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassDownload: true})
	require.NoError(t, federation.PeerStore{DB: database}.Upsert(&db.FederationPeer{
		Fingerprint: sourceID.Fingerprint(), PublicKey: b64(sourceID.PublicKey), Name: "Source",
		BaseURL: "https://source", Status: db.PeerStatusActive, ConsumePolicy: dp,
	}))
	require.NoError(t, federation.CatalogSnapshotStore{DB: database}.ReplacePeerSnapshot(sourceID.Fingerprint(), []federation.CatalogEntry{
		{OriginFingerprint: sourceID.Fingerprint(), Hops: 1, Key: key, Title: "Relayed", Console: "NES"},
	}, time.Unix(1, 0)))
	return requester, sourceID
}

func TestServeDownload_RelayForwardsWhenEnabled(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	setRelayEnabled(t, database, true)
	requester, _ := relaySetup(t, database, "igdb:far")

	// We don't have igdb:far locally (no GameDirs/game), so we must relay it.
	h := downloadHandler(database, selfID, []string{t.TempDir()}, &fakeDownloadClient{body: "RELAYED-ROM"})

	w := callServeDownload(h, requester, "igdb:far")
	require.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "RELAYED-ROM", w.Body.String())
	assert.Equal(t, "application/octet-stream", w.Header().Get("Content-Type"))

	var relays int64
	database.Model(&db.FederationExchange{}).Where("operation = ? AND status = ?", "download_relay", db.ExchangeOK).Count(&relays)
	assert.Equal(t, int64(1), relays)
}

func TestServeDownload_NoForwardWhenRelayDisabled(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	// relay NOT enabled (default)
	requester, _ := relaySetup(t, database, "igdb:far")
	called := false
	h := downloadHandler(database, selfID, []string{t.TempDir()}, downloadClientFunc(func() { called = true }))

	w := callServeDownload(h, requester, "igdb:far")
	assert.Equal(t, http.StatusNotFound, w.Code)
	assert.False(t, called, "relay disabled → never forwards")
}

func TestServeDownload_RelayLoopGuard(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	setRelayEnabled(t, database, true)

	// The ONLY catalog source for the key is the requester itself → must not
	// forward back to them (loop guard) → 404.
	requesterID, _ := federation.GenerateIdentity()
	sp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassDownload: true})
	dp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassDownload: true})
	require.NoError(t, federation.PeerStore{DB: database}.Upsert(&db.FederationPeer{
		Fingerprint: requesterID.Fingerprint(), PublicKey: b64(requesterID.PublicKey), Name: "Requester",
		BaseURL: "https://req", Status: db.PeerStatusActive, SharePolicy: sp, ConsumePolicy: dp,
	}))
	require.NoError(t, federation.CatalogSnapshotStore{DB: database}.ReplacePeerSnapshot(requesterID.Fingerprint(), []federation.CatalogEntry{
		{OriginFingerprint: requesterID.Fingerprint(), Hops: 1, Key: "igdb:loop", Title: "Loop", Console: "NES"},
	}, time.Unix(1, 0)))
	requester, _ := federation.PeerStore{DB: database}.GetByFingerprint(requesterID.Fingerprint())

	called := false
	h := downloadHandler(database, selfID, []string{t.TempDir()}, downloadClientFunc(func() { called = true }))

	w := callServeDownload(h, requester, "igdb:loop")
	assert.Equal(t, http.StatusNotFound, w.Code)
	assert.False(t, called, "must not forward back to the requester")
}

func TestServeDownload_NoForwardWhenBudgetExhausted(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	setRelayEnabled(t, database, true)
	requester, _ := relaySetup(t, database, "igdb:far")
	called := false
	h := downloadHandler(database, selfID, []string{t.TempDir()}, downloadClientFunc(func() { called = true }))

	// hops=0 → no budget to forward.
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.GET("/x", func(c *gin.Context) { c.Set(fedPeerContextKey, requester); h.ginServeDownload(c) })
	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/x?key=igdb:far&hops=0", nil))

	assert.Equal(t, http.StatusNotFound, w.Code)
	assert.False(t, called, "hops=0 → no forwarding")
}
