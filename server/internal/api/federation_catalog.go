package api

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
)

// catalogClient fetches a peer's source-stamped catalog over a signed request.
type catalogClient interface {
	FetchCatalog(baseURL, requestID string, id federation.Identity, peerFingerprint string, maxHops int) ([]federation.CatalogEntry, error)
}

type httpCatalogClient struct{}

func (httpCatalogClient) FetchCatalog(baseURL, requestID string, id federation.Identity, peerFingerprint string, maxHops int) ([]federation.CatalogEntry, error) {
	const path = "/api/federation/catalog"
	url := fmt.Sprintf("%s%s?maxHops=%d", baseURL, path, maxHops)
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	for k, v := range signedFederationHeaders(id, http.MethodGet, path, requestID, nil, peerFingerprint) {
		req.Header.Set(k, v)
	}
	resp, err := fedHTTPClient().Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("remote returned %d", resp.StatusCode)
	}
	var body struct {
		Entries []federation.CatalogEntry `json:"entries"`
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, maxStatsResponseBytes)).Decode(&body); err != nil {
		return nil, err
	}
	return body.Entries, nil
}

// sanitizeCatalogBatch drops entries claiming our own origin (loop) or exceeding
// the hop budget, and caps the count per peer.
func sanitizeCatalogBatch(entries []federation.CatalogEntry, selfFingerprint string, maxAcceptedHops int) []federation.CatalogEntry {
	out := make([]federation.CatalogEntry, 0, len(entries))
	for _, e := range entries {
		if e.OriginFingerprint == "" || e.OriginFingerprint == selfFingerprint {
			continue
		}
		if e.Hops < 0 || e.Hops > maxAcceptedHops || e.Key == "" || len(e.Key) > 255 {
			continue
		}
		// Bound untrusted string fields — SQLite ignores the VARCHAR size hints,
		// so a hostile peer could otherwise store arbitrarily long Title/Console.
		if len(e.Title) > 255 {
			e.Title = e.Title[:255]
		}
		if len(e.Console) > 32 {
			e.Console = e.Console[:32]
		}
		out = append(out, e)
		if len(out) >= maxStatEntriesPerPeer {
			break
		}
	}
	return out
}

// ginExportCatalog serves this server's catalog (hop 0) plus its cached friend
// catalogs within the requested hop budget. Behind VerifyFederationRequest +
// SharePolicy(catalog).
func (h *FederationHandler) ginExportCatalog(c *gin.Context) {
	reqID := c.GetHeader(headerFedRequestID)
	if reqID == "" {
		reqID = federation.NewRequestID()
	}
	peer, ok := federationPeerFromGin(c)
	if !ok {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "no verified peer"})
		return
	}
	if !federation.CanShare(*peer, federation.DataClassCatalog) {
		c.JSON(http.StatusForbidden, gin.H{"error": "catalog not shared with this peer"})
		return
	}

	maxHops := federation.MaxFederationHops - 1
	if q := c.Query("maxHops"); q != "" {
		if n, err := strconv.Atoi(q); err == nil && n >= 0 && n <= maxHops {
			maxHops = n
		}
	}

	local, err := federation.BuildLocalCatalog(h.DB, h.Identity.Fingerprint())
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build catalog"})
		return
	}
	cached, err := h.CatalogSnapshots.EntriesWithinHops(maxHops)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to read catalog snapshots"})
		return
	}
	entries := append(local, cached...)

	federation.RecordExchange(h.DB, federation.ExchangeRecord{
		RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
		Direction: db.ExchangeInbound, Operation: "catalog_export", MaxHops: maxHops,
		DataClass: string(federation.DataClassCatalog), Status: db.ExchangeOK, ItemCount: len(entries),
	})
	c.JSON(http.StatusOK, gin.H{"entries": entries})
}

// RefreshFederationCatalog pulls each catalog-consumable active friend's catalog,
// sanitizes + increments hops, and replaces that friend's cached snapshot.
// Serialized against itself; runs on the periodic ticker + admin trigger.
func (h *FederationHandler) RefreshFederationCatalog() (int, int) {
	if !h.catalogRefreshMu.TryLock() {
		slog.Info("federation: catalog refresh already in progress, skipping", "component", "federation")
		return 0, 0
	}
	defer h.catalogRefreshMu.Unlock()

	peers, err := h.Peers.List()
	if err != nil {
		return 0, 0
	}
	client := h.CatalogClient
	if client == nil {
		client = httpCatalogClient{}
	}
	refreshed, failed := 0, 0
	for _, peer := range peers {
		if peer.Status != db.PeerStatusActive || !federation.CanConsume(peer, federation.DataClassCatalog) {
			continue
		}
		reqID := federation.NewRequestID()
		started := time.Now()
		raw, ferr := client.FetchCatalog(peer.BaseURL, reqID, h.Identity, peer.Fingerprint, federation.MaxFederationHops-1)
		if ferr != nil {
			federation.RecordExchange(h.DB, federation.ExchangeRecord{
				RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
				Direction: db.ExchangeOutbound, Operation: "catalog_pull",
				DataClass: string(federation.DataClassCatalog), Status: db.ExchangeError,
				StartedAt: started, Error: ferr.Error(),
			})
			failed++
			continue
		}
		cleaned := sanitizeCatalogBatch(raw, h.Identity.Fingerprint(), federation.MaxFederationHops-1)
		for i := range cleaned {
			cleaned[i].Hops++
		}
		if err := h.CatalogSnapshots.ReplacePeerSnapshot(peer.Fingerprint, cleaned, time.Now()); err != nil {
			federation.RecordExchange(h.DB, federation.ExchangeRecord{
				RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
				Direction: db.ExchangeOutbound, Operation: "catalog_pull",
				DataClass: string(federation.DataClassCatalog), Status: db.ExchangeError,
				StartedAt: started, Error: "store: " + err.Error(),
			})
			failed++
			continue
		}
		federation.RecordExchange(h.DB, federation.ExchangeRecord{
			RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
			Direction: db.ExchangeOutbound, Operation: "catalog_pull",
			DataClass: string(federation.DataClassCatalog), Status: db.ExchangeOK,
			StartedAt: started, ItemCount: len(cleaned),
		})
		refreshed++
	}
	return refreshed, failed
}

// --- Available games (user-facing discovery) -------------------------------

type AvailableGamesInput struct {
	MaxHops    int    `query:"maxHops"`    // viewer reach; <=0 = full reachable mesh
	RemoteOnly bool   `query:"remoteOnly"` // only games not available locally
	Q          string `query:"q"`          // case-insensitive title filter; empty = all
}
type AvailableGamesOutput struct {
	Body struct {
		Games []federation.CatalogAvailability `json:"games"`
	}
}

// HumaAvailableGames returns the mesh game catalog (local + cached friends),
// each row showing how many servers offer it and whether it's local.
func (h *FederationHandler) HumaAvailableGames(_ context.Context, in *AvailableGamesInput) (*AvailableGamesOutput, error) {
	local, err := federation.BuildLocalCatalog(h.DB, h.Identity.Fingerprint())
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to build local catalog")
	}
	snapHops := -1
	if in.MaxHops >= 1 {
		snapHops = in.MaxHops
	}
	cached, err := h.CatalogSnapshots.EntriesWithinHops(snapHops)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to read catalog snapshots")
	}
	all := append(local, cached...)

	games := federation.AggregateCatalog(all, h.Identity.Fingerprint())
	if in.RemoteOnly {
		filtered := make([]federation.CatalogAvailability, 0, len(games))
		for _, g := range games {
			if !g.Local {
				filtered = append(filtered, g)
			}
		}
		games = filtered
	}
	if q := strings.TrimSpace(strings.ToLower(in.Q)); q != "" {
		filtered := make([]federation.CatalogAvailability, 0, len(games))
		for _, g := range games {
			if strings.Contains(strings.ToLower(g.Title), q) {
				filtered = append(filtered, g)
			}
		}
		games = filtered
	}

	// Fill in cover art locally from each game's cross-key — no covers are
	// carried across the mesh. The resolver caches results (so repeats are free)
	// and only "igdb:" keys ever reach IGDB, which self-rate-limits; the one
	// caller today is the q-filtered search, so result sets are small. A future
	// unfiltered browse should paginate rather than resolve a whole catalog.
	if h.CoverResolver != nil {
		for i := range games {
			games[i].Cover = h.CoverResolver.CoverURL(games[i].Key)
		}
	}

	out := &AvailableGamesOutput{}
	out.Body.Games = games
	return out, nil
}

// --- Catalog refresh (admin trigger) ---------------------------------------

type RefreshCatalogInput struct{}
type RefreshCatalogOutput struct {
	Body struct {
		Refreshed int `json:"refreshed"`
		Failed    int `json:"failed"`
	}
}

func (h *FederationHandler) HumaRefreshCatalog(_ context.Context, _ *RefreshCatalogInput) (*RefreshCatalogOutput, error) {
	r, f := h.RefreshFederationCatalog()
	out := &RefreshCatalogOutput{}
	out.Body.Refreshed = r
	out.Body.Failed = f
	return out, nil
}
