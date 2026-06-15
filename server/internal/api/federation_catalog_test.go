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

type fakeCatalogClient struct {
	byBase map[string][]federation.CatalogEntry
	err    error
}

func (f *fakeCatalogClient) FetchCatalog(baseURL, _ string, _ federation.Identity, _ string, _ int) ([]federation.CatalogEntry, error) {
	if f.err != nil {
		return nil, f.err
	}
	return f.byBase[baseURL], nil
}

func catalogHandler(database *gorm.DB, selfID federation.Identity, client catalogClient) *FederationHandler {
	return &FederationHandler{
		DB: database, Identity: selfID,
		Peers:            federation.PeerStore{DB: database},
		Snapshots:        federation.SnapshotStore{DB: database},
		CatalogSnapshots: federation.CatalogSnapshotStore{DB: database},
		BaseURL:          "https://self",
		CatalogClient:    client,
	}
}

func seedConsoleAndGame(t *testing.T, database *gorm.DB, scraperID, title string) {
	t.Helper()
	var console db.Console
	if err := database.Where("abbreviation = ?", "SNES").First(&console).Error; err != nil {
		console = db.Console{Name: "Super Nintendo", Abbreviation: "SNES"}
		require.NoError(t, database.Create(&console).Error)
	}
	require.NoError(t, database.Create(&db.Game{Title: title, ScraperID: scraperID, FilePath: "/" + scraperID, ConsoleID: console.ID}).Error)
}

func catalogPolicyPeer(t *testing.T, database *gorm.DB, id federation.Identity, name, baseURL string, share, consume bool) {
	t.Helper()
	sp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassCatalog: share})
	cp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassCatalog: consume})
	require.NoError(t, federation.PeerStore{DB: database}.Upsert(&db.FederationPeer{
		Fingerprint: id.Fingerprint(), PublicKey: b64(id.PublicKey), Name: name, BaseURL: baseURL,
		Status: db.PeerStatusActive, SharePolicy: sp, ConsumePolicy: cp,
	}))
}

func callExportCatalog(h *FederationHandler, peer *db.FederationPeer) *httptest.ResponseRecorder {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.GET("/x", func(c *gin.Context) {
		c.Set(fedPeerContextKey, peer)
		h.ginExportCatalog(c)
	})
	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/x", nil))
	return w
}

func TestExportCatalog_ServesLocalWhenShared(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	seedConsoleAndGame(t, database, "igdb:1", "Game One")
	h := catalogHandler(database, selfID, nil)

	sp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassCatalog: true})
	peer := &db.FederationPeer{Fingerprint: "fp", Name: "Friend", SharePolicy: sp, Status: db.PeerStatusActive}

	w := callExportCatalog(h, peer)
	require.Equal(t, http.StatusOK, w.Code)
	var body struct {
		Entries []federation.CatalogEntry `json:"entries"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &body))
	require.Len(t, body.Entries, 1)
	assert.Equal(t, "igdb:1", body.Entries[0].Key)
	assert.Equal(t, selfID.Fingerprint(), body.Entries[0].OriginFingerprint)
	assert.Equal(t, "SNES", body.Entries[0].Console)
}

func TestExportCatalog_ForbiddenWhenNotShared(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	seedConsoleAndGame(t, database, "igdb:1", "Game One")
	h := catalogHandler(database, selfID, nil)

	peer := &db.FederationPeer{Fingerprint: "fp", Name: "Friend", SharePolicy: "", Status: db.PeerStatusActive}
	w := callExportCatalog(h, peer)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestRefreshCatalog_PullsConsumableFriend(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	catalogPolicyPeer(t, database, friendID, "Friend", "https://friend", true, true)

	fake := &fakeCatalogClient{byBase: map[string][]federation.CatalogEntry{
		"https://friend": {{OriginFingerprint: friendID.Fingerprint(), Hops: 0, Key: "igdb:9", Title: "Remote", Console: "NES"}},
	}}
	h := catalogHandler(database, selfID, fake)

	refreshed, failed := h.RefreshFederationCatalog()
	assert.Equal(t, 1, refreshed)
	assert.Equal(t, 0, failed)

	cached, err := h.CatalogSnapshots.EntriesWithinHops(-1)
	require.NoError(t, err)
	require.Len(t, cached, 1)
	assert.Equal(t, 1, cached[0].Hops, "friend's hop-0 entry stored at hop 1")
	assert.Equal(t, "igdb:9", cached[0].Key)
}

func TestRefreshCatalog_SanitizesLoopAndOverBudget(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	catalogPolicyPeer(t, database, friendID, "Friend", "https://friend", true, true)

	fake := &fakeCatalogClient{byBase: map[string][]federation.CatalogEntry{
		"https://friend": {
			{OriginFingerprint: selfID.Fingerprint(), Hops: 0, Key: "loop", Title: "Loop"},
			{OriginFingerprint: "X", Hops: federation.MaxFederationHops, Key: "toofar", Title: "Far"},
			{OriginFingerprint: "Y", Hops: 1, Key: "ok", Title: "OK"},
		},
	}}
	h := catalogHandler(database, selfID, fake)

	_, _ = h.RefreshFederationCatalog()
	cached, err := h.CatalogSnapshots.EntriesWithinHops(-1)
	require.NoError(t, err)
	require.Len(t, cached, 1, "loop + over-budget dropped")
	assert.Equal(t, "Y", cached[0].OriginFingerprint)
}

func TestRefreshCatalog_TruncatesOversizedStrings(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	catalogPolicyPeer(t, database, friendID, "Friend", "https://friend", true, true)

	longTitle := make([]byte, 1000)
	for i := range longTitle {
		longTitle[i] = 'A'
	}
	fake := &fakeCatalogClient{byBase: map[string][]federation.CatalogEntry{
		"https://friend": {{OriginFingerprint: friendID.Fingerprint(), Hops: 0, Key: "igdb:9", Title: string(longTitle), Console: "NES"}},
	}}
	h := catalogHandler(database, selfID, fake)

	_, _ = h.RefreshFederationCatalog()
	cached, err := h.CatalogSnapshots.EntriesWithinHops(-1)
	require.NoError(t, err)
	require.Len(t, cached, 1)
	assert.LessOrEqual(t, len(cached[0].Title), 255, "oversized title bounded before storage")
}

func TestRefreshCatalog_RecordsErrorOnFailure(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	friendID, _ := federation.GenerateIdentity()
	catalogPolicyPeer(t, database, friendID, "Friend", "https://friend", true, true)
	h := catalogHandler(database, selfID, &fakeCatalogClient{err: errors.New("refused")})

	_, failed := h.RefreshFederationCatalog()
	assert.Equal(t, 1, failed)
	var errs int64
	database.Model(&db.FederationExchange{}).Where("operation = ? AND status = ?", "catalog_pull", db.ExchangeError).Count(&errs)
	assert.Equal(t, int64(1), errs)
}

func TestAvailableGames_AggregatesAndFiltersRemoteOnly(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	seedConsoleAndGame(t, database, "igdb:local", "Local Game") // local has igdb:local
	h := catalogHandler(database, selfID, nil)

	// A friend offers a game we DON'T have, plus the same one we do.
	require.NoError(t, h.CatalogSnapshots.ReplacePeerSnapshot("B", []federation.CatalogEntry{
		{OriginFingerprint: "B", Hops: 1, Key: "igdb:remote", Title: "Remote Game", Console: "NES"},
		{OriginFingerprint: "B", Hops: 1, Key: "igdb:local", Title: "Local Game", Console: "SNES"},
	}, time.Unix(1, 0)))

	all, err := h.HumaAvailableGames(context.Background(), &AvailableGamesInput{})
	require.NoError(t, err)
	assert.Len(t, all.Body.Games, 2)

	remoteOnly, err := h.HumaAvailableGames(context.Background(), &AvailableGamesInput{RemoteOnly: true})
	require.NoError(t, err)
	require.Len(t, remoteOnly.Body.Games, 1, "only the game we don't have locally")
	assert.Equal(t, "igdb:remote", remoteOnly.Body.Games[0].Key)
	assert.False(t, remoteOnly.Body.Games[0].Local)
}

func TestAvailableGames_FiltersByQuery(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	h := catalogHandler(database, selfID, nil)

	require.NoError(t, h.CatalogSnapshots.ReplacePeerSnapshot("B", []federation.CatalogEntry{
		{OriginFingerprint: "B", Hops: 1, Key: "igdb:1", Title: "Super Mario Bros", Console: "NES"},
		{OriginFingerprint: "B", Hops: 1, Key: "igdb:2", Title: "Sonic the Hedgehog", Console: "MD"},
	}, time.Unix(1, 0)))

	// Case-insensitive substring match on title.
	res, err := h.HumaAvailableGames(context.Background(), &AvailableGamesInput{Q: "mario"})
	require.NoError(t, err)
	require.Len(t, res.Body.Games, 1)
	assert.Equal(t, "Super Mario Bros", res.Body.Games[0].Title)

	// Blank query returns everything (no filter).
	none, err := h.HumaAvailableGames(context.Background(), &AvailableGamesInput{Q: "   "})
	require.NoError(t, err)
	assert.Len(t, none.Body.Games, 2)
}
