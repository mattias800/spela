package api

import (
	"context"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// catalogPolicyPeer (shared with federation_catalog_test.go) upserts an active
// peer with a catalog share/consume policy.

// TestTwoServers_CatalogDiscoveryWithCoverOverHTTP wires two real federation
// servers together over httptest and exercises the whole cross-server discovery
// path — signed request, signature verification, per-friend SharePolicy gate,
// catalog transfer, sanitization, and aggregation — proving it works without
// deploying two servers. Server B hosts a game with an IGDB cover; server A
// pulls B's catalog and surfaces the game (remote-only) with its cover.
func TestTwoServers_CatalogDiscoveryWithCoverOverHTTP(t *testing.T) {
	gin.SetMode(gin.TestMode)

	// --- Server B (origin): a library with one IGDB-covered game, export routes.
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

	consoleB := db.Console{Name: "Super Nintendo", Abbreviation: "SNES"}
	require.NoError(t, dbB.Create(&consoleB).Error)
	require.NoError(t, dbB.Create(&db.Game{
		Title: "The Legend of Zelda", ScraperID: "igdb:1022", FilePath: "/z",
		ConsoleID: consoleB.ID,
	}).Error)

	// --- Server A (consumer). CatalogClient nil => real signed HTTP to B.
	// A resolves covers locally from the cross-key via its own IGDB client
	// (faked here), not from anything B sent — covers are not federated.
	const cover = "https://images.igdb.com/igdb/image/upload/t_cover_big/zelda.jpg"
	dbA := openAPIFedTestDB(t)
	idA, _ := federation.GenerateIdentity()
	hA := &FederationHandler{
		DB: dbA, Identity: idA, Peers: federation.PeerStore{DB: dbA},
		Snapshots: federation.SnapshotStore{DB: dbA}, CatalogSnapshots: federation.CatalogSnapshotStore{DB: dbA},
		CoverResolver: fakeCoverResolver{byKey: map[string]string{"igdb:1022": cover}},
	}

	// Mutual pairing with catalog policy: A consumes from B; B shares with A.
	catalogPolicyPeer(t, dbA, idB, "Server B", srvB.URL, false, true)
	catalogPolicyPeer(t, dbB, idA, "Server A", "", true, false)

	// A pulls B's catalog over the wire (signed GET, verified by B's middleware).
	refreshed, failed := hA.RefreshFederationCatalog()
	require.Equal(t, 0, failed, "no failed pulls")
	require.Equal(t, 1, refreshed, "A pulled B's catalog")

	// A's discovery view surfaces B's game, remote-only, with A's own cover.
	out, err := hA.HumaAvailableGames(context.Background(), &AvailableGamesInput{RemoteOnly: true})
	require.NoError(t, err)
	require.Len(t, out.Body.Games, 1)
	g := out.Body.Games[0]
	assert.Equal(t, "The Legend of Zelda", g.Title)
	assert.Equal(t, "igdb:1022", g.Key)
	assert.Equal(t, "SNES", g.Console)
	assert.False(t, g.Local, "A does not have this game locally")
	assert.Equal(t, 1, g.OriginCount)
	assert.Equal(t, cover, g.Cover, "A resolved the cover locally from the cross-key")

	// And the q-filter (the search slice) finds it by title.
	hit, err := hA.HumaAvailableGames(context.Background(), &AvailableGamesInput{RemoteOnly: true, Q: "zelda"})
	require.NoError(t, err)
	require.Len(t, hit.Body.Games, 1)
	assert.Equal(t, "igdb:1022", hit.Body.Games[0].Key)
}

// TestTwoServers_CatalogBlockedWithoutSharePolicy verifies the per-friend gate:
// if B has not shared its catalog with A, A's pull is rejected and discovers
// nothing — share consent is required, not just a connection.
func TestTwoServers_CatalogBlockedWithoutSharePolicy(t *testing.T) {
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

	consoleB := db.Console{Name: "Super Nintendo", Abbreviation: "SNES"}
	require.NoError(t, dbB.Create(&consoleB).Error)
	require.NoError(t, dbB.Create(&db.Game{
		Title: "Secret Game", ScraperID: "igdb:1", FilePath: "/s", ConsoleID: consoleB.ID,
	}).Error)

	dbA := openAPIFedTestDB(t)
	idA, _ := federation.GenerateIdentity()
	hA := &FederationHandler{
		DB: dbA, Identity: idA, Peers: federation.PeerStore{DB: dbA},
		Snapshots: federation.SnapshotStore{DB: dbA}, CatalogSnapshots: federation.CatalogSnapshotStore{DB: dbA},
	}

	// A wants to consume; but B does NOT share catalog with A (share=false).
	catalogPolicyPeer(t, dbA, idB, "Server B", srvB.URL, false, true)
	catalogPolicyPeer(t, dbB, idA, "Server A", "", false, false)

	refreshed, failed := hA.RefreshFederationCatalog()
	assert.Equal(t, 1, failed, "B refuses (403), so the pull fails")
	assert.Equal(t, 0, refreshed)

	out, err := hA.HumaAvailableGames(context.Background(), &AvailableGamesInput{RemoteOnly: true})
	require.NoError(t, err)
	assert.Empty(t, out.Body.Games, "A discovers nothing without B's share consent")
}
