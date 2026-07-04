package api

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
)

// maxStatsResponseBytes caps an inbound friend stat payload (defense against a
// hostile/misconfigured peer returning an unbounded body).
const maxStatsResponseBytes = 4 << 20 // 4 MiB

// maxStatEntriesPerPeer caps how many entries we accept from one friend per
// refresh, bounding a friend's influence (a single friend can't flood the mesh).
const maxStatEntriesPerPeer = 5000

// statsClient fetches a peer's source-stamped rollup over a signed request,
// asking for entries up to maxHops hops from that peer.
type statsClient interface {
	FetchStats(baseURL, requestID string, id federation.Identity, peerFingerprint string, maxHops int) ([]federation.StatEntry, error)
}

type httpStatsClient struct{}

func (httpStatsClient) FetchStats(baseURL, requestID string, id federation.Identity, peerFingerprint string, maxHops int) ([]federation.StatEntry, error) {
	const path = "/api/federation/stats"
	// maxHops rides in the query (not signed — it only bounds how much data comes
	// back). The signature covers the path, which is identical on both ends.
	url := fmt.Sprintf("%s%s?maxHops=%d", baseURL, path, maxHops)
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	for k, v := range signedFederationHeaders(id, http.MethodGet, path, requestID, nil, peerFingerprint) {
		req.Header.Set(k, v)
	}
	resp, err := doFederationRequest(fedHTTPClient(), req, peerFingerprint)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("remote returned %d", resp.StatusCode)
	}
	var body struct {
		Entries []federation.StatEntry `json:"entries"`
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, maxStatsResponseBytes)).Decode(&body); err != nil {
		return nil, err
	}
	return body.Entries, nil
}

// sanitizeFriendBatch cleans a friend's transitive rollup before we ingest it:
// drop entries that claim OUR origin (a loop — our local data is authoritative),
// exceed the hop budget, carry negative values, or are missing an origin. Caps
// the count per peer. (Phase 2 trusts a direct friend to have honestly
// aggregated its own friends — a friend can still invent unknown origins; that
// residual risk is bounded by this cap, dedupe-by-origin, and the per-friend
// consume toggle. Cryptographic per-origin provenance is a later hardening.)
func sanitizeFriendBatch(entries []federation.StatEntry, selfFingerprint string, maxAcceptedHops int) []federation.StatEntry {
	out := make([]federation.StatEntry, 0, len(entries))
	for _, e := range entries {
		if e.OriginFingerprint == "" || e.OriginFingerprint == selfFingerprint {
			continue
		}
		if e.Hops < 0 || e.Hops > maxAcceptedHops {
			continue
		}
		if e.PlayTimeSeconds < 0 || e.Players < 0 {
			continue
		}
		out = append(out, e)
		if len(out) >= maxStatEntriesPerPeer {
			break
		}
	}
	return out
}

// ginExportStats serves this server's rollup (hop 0) PLUS its cached friend
// rollups up to the requested hop distance, so a friend can re-aggregate
// transitively. Behind VerifyFederationRequest + SharePolicy(stats).
func (h *FederationHandler) ginExportStats(c *gin.Context) {
	reqID := c.GetHeader(headerFedRequestID)
	if reqID == "" {
		reqID = federation.NewRequestID()
	}
	peer, ok := federationPeerFromGin(c)
	if !ok {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "no verified peer"})
		return
	}
	if !federation.CanShare(*peer, federation.DataClassStats) {
		c.JSON(http.StatusForbidden, gin.H{"error": "stats not shared with this peer"})
		return
	}

	maxHops := federation.MaxFederationHops - 1
	if q := c.Query("maxHops"); q != "" {
		if n, err := strconv.Atoi(q); err == nil && n >= 0 && n <= maxHops {
			maxHops = n
		}
	}

	local, err := federation.BuildLocalRollup(h.DB, h.Identity.Fingerprint())
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build rollup"})
		return
	}
	// Re-serve cached friend data within the hop budget (maxHops counts the
	// distance from THIS server; the requester adds 1 on ingest).
	cached, err := h.Snapshots.EntriesWithinHops(maxHops)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to read snapshots"})
		return
	}
	entries := append(local, cached...)

	h.recordExchange(federation.ExchangeRecord{
		RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
		Direction: db.ExchangeInbound, Operation: "stats_export", MaxHops: maxHops,
		DataClass: string(federation.DataClassStats), Status: db.ExchangeOK, ItemCount: len(entries),
	})
	c.JSON(http.StatusOK, gin.H{"entries": entries})
}

// RefreshFederationStats pulls each stats-consumable active friend's rollup
// (bounded by the hop budget), sanitizes + increments hops, and replaces that
// friend's cached snapshot. This is the periodic sync that powers transitive
// re-serving. Returns (refreshed, failed) peer counts.
func (h *FederationHandler) RefreshFederationStats() (int, int) {
	// Serialize refreshes: the periodic ticker and an admin trigger must not run
	// concurrently (WAL allows overlapping write txns, which would race the
	// per-peer delete-then-insert). If one is already running, skip.
	if !h.refreshMu.TryLock() {
		slog.Info("federation: stats refresh already in progress, skipping", "component", "federation")
		return 0, 0
	}
	defer h.refreshMu.Unlock()

	peers, err := h.Peers.List()
	if err != nil {
		return 0, 0
	}
	client := h.StatsClient
	if client == nil {
		client = httpStatsClient{}
	}
	refreshed, failed := 0, 0
	for _, peer := range peers {
		if peer.Status != db.PeerStatusActive || !federation.CanConsume(peer, federation.DataClassStats) {
			continue
		}
		reqID := federation.NewRequestID()
		started := time.Now()
		raw, ferr := client.FetchStats(peer.BaseURL, reqID, h.Identity, peer.Fingerprint, federation.MaxFederationHops-1)
		if ferr != nil {
			h.recordExchange(federation.ExchangeRecord{
				RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
				Direction: db.ExchangeOutbound, Operation: "stats_pull",
				DataClass: string(federation.DataClassStats), Status: db.ExchangeError,
				StartedAt: started, Error: ferr.Error(),
			})
			failed++
			continue
		}
		cleaned := sanitizeFriendBatch(raw, h.Identity.Fingerprint(), federation.MaxFederationHops-1)
		for i := range cleaned {
			cleaned[i].Hops++ // distance from us = distance from the friend + 1
		}
		if err := h.Snapshots.ReplacePeerSnapshot(peer.Fingerprint, cleaned, time.Now()); err != nil {
			h.recordExchange(federation.ExchangeRecord{
				RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
				Direction: db.ExchangeOutbound, Operation: "stats_pull",
				DataClass: string(federation.DataClassStats), Status: db.ExchangeError,
				StartedAt: started, Error: "store: " + err.Error(),
			})
			failed++
			continue
		}
		h.recordExchange(federation.ExchangeRecord{
			RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
			Direction: db.ExchangeOutbound, Operation: "stats_pull",
			DataClass: string(federation.DataClassStats), Status: db.ExchangeOK,
			StartedAt: started, ItemCount: len(cleaned),
		})
		refreshed++
	}
	return refreshed, failed
}

// --- Refresh (admin trigger) -----------------------------------------------

type RefreshStatsInput struct{}
type RefreshStatsOutput struct {
	Body struct {
		Refreshed int `json:"refreshed"`
		Failed    int `json:"failed"`
	}
}

func (h *FederationHandler) HumaRefreshStats(_ context.Context, _ *RefreshStatsInput) (*RefreshStatsOutput, error) {
	r, f := h.RefreshFederationStats()
	out := &RefreshStatsOutput{}
	out.Body.Refreshed = r
	out.Body.Failed = f
	return out, nil
}

// --- Aggregated read (user-facing) -----------------------------------------

type AggregatedStatsInput struct {
	Metric  string `query:"metric" enum:"game_play,player_play"`
	Limit   int    `query:"limit"`
	MaxHops int    `query:"maxHops"` // viewer reach: >=1 limits to that distance; <=0 = full reachable mesh
}
type AggregatedStatsOutput struct {
	Body struct {
		Stats []federation.AggregatedStat `json:"stats"`
	}
}

// HumaAggregatedStats merges this server's rollup (hop 0) with its cached friend
// snapshots (hop >= 1) and returns the aggregated mesh leaderboard. Reads from
// the snapshot store (populated by RefreshFederationStats) — no live pulls — so
// reach is unbounded in depth yet cheap. MaxHops bounds the viewer's reach.
func (h *FederationHandler) HumaAggregatedStats(_ context.Context, in *AggregatedStatsInput) (*AggregatedStatsOutput, error) {
	all, err := federation.BuildLocalRollup(h.DB, h.Identity.Fingerprint())
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to build local rollup")
	}

	snapHops := -1 // full reachable mesh
	if in.MaxHops >= 1 {
		snapHops = in.MaxHops
	}
	cached, err := h.Snapshots.EntriesWithinHops(snapHops)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to read snapshots")
	}
	all = append(all, cached...)

	aggregated := federation.AggregateStatEntries(all)
	if in.Metric != "" {
		filtered := make([]federation.AggregatedStat, 0, len(aggregated))
		for _, a := range aggregated {
			if string(a.Metric) == in.Metric {
				filtered = append(filtered, a)
			}
		}
		aggregated = filtered
	}
	if in.Limit > 0 && len(aggregated) > in.Limit {
		aggregated = aggregated[:in.Limit]
	}

	// Strip the per-source breakdown: it carries peer fingerprints, and the peer
	// roster is admin-only elsewhere. Regular users get totals + labels only.
	for i := range aggregated {
		aggregated[i].Sources = nil
	}

	out := &AggregatedStatsOutput{}
	out.Body.Stats = aggregated
	return out, nil
}
