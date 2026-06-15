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

func (f *fakeDownloadClient) FetchDownload(_, _ string, _ federation.Identity, _, _ string) (*http.Response, error) {
	if f.err != nil {
		return nil, f.err
	}
	st := f.status
	if st == 0 {
		st = http.StatusOK
	}
	return &http.Response{
		StatusCode: st,
		Body:       io.NopCloser(strings.NewReader(f.body)),
		Header:     http.Header{},
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

func (f downloadClientFunc) FetchDownload(_, _ string, _ federation.Identity, _, _ string) (*http.Response, error) {
	f()
	return &http.Response{StatusCode: http.StatusOK, Body: io.NopCloser(strings.NewReader("")), Header: http.Header{}}, nil
}
