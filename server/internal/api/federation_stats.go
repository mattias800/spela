package api

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
)

// statsClient fetches a peer's source-stamped stat rollup over a signed request.
type statsClient interface {
	FetchStats(baseURL, requestID string, id federation.Identity, peerFingerprint string) ([]federation.StatEntry, error)
}

type httpStatsClient struct{}

func (httpStatsClient) FetchStats(baseURL, requestID string, id federation.Identity, peerFingerprint string) ([]federation.StatEntry, error) {
	const path = "/api/federation/stats"
	req, err := http.NewRequest(http.MethodGet, baseURL+path, nil)
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
		Entries []federation.StatEntry `json:"entries"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return nil, err
	}
	return body.Entries, nil
}

// ginExportStats serves this server's source-stamped rollup (hop 0) to an
// authenticated peer we share stats with. Behind VerifyFederationRequest.
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
	entries, err := federation.BuildLocalRollup(h.DB, h.Identity.Fingerprint())
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build rollup"})
		return
	}
	federation.RecordExchange(h.DB, federation.ExchangeRecord{
		RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
		Direction: db.ExchangeInbound, Operation: "stats_export",
		DataClass: string(federation.DataClassStats), Status: db.ExchangeOK, ItemCount: len(entries),
	})
	c.JSON(http.StatusOK, gin.H{"entries": entries})
}

// --- Aggregated read (user-facing) -----------------------------------------

type AggregatedStatsInput struct {
	Metric string `query:"metric" enum:"game_play,player_play"`
	Limit  int    `query:"limit"`
}
type AggregatedStatsOutput struct {
	Body struct {
		Stats []federation.AggregatedStat `json:"stats"`
	}
}

// HumaAggregatedStats merges this server's rollup (hop 0) with each stats-
// consumable active friend's rollup (hop 1) and returns the aggregated
// leaderboard with per-source breakdown. Phase 1: direct friends only. A friend
// that fails to respond is recorded and skipped (graceful degradation).
func (h *FederationHandler) HumaAggregatedStats(_ context.Context, in *AggregatedStatsInput) (*AggregatedStatsOutput, error) {
	all, err := federation.BuildLocalRollup(h.DB, h.Identity.Fingerprint())
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to build local rollup")
	}

	peers, err := h.Peers.List()
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to list peers")
	}
	client := h.StatsClient
	if client == nil {
		client = httpStatsClient{}
	}
	for _, peer := range peers {
		if peer.Status != db.PeerStatusActive || !federation.CanConsume(peer, federation.DataClassStats) {
			continue
		}
		reqID := federation.NewRequestID()
		started := time.Now()
		entries, ferr := client.FetchStats(peer.BaseURL, reqID, h.Identity, peer.Fingerprint)
		if ferr != nil {
			federation.RecordExchange(h.DB, federation.ExchangeRecord{
				RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
				Direction: db.ExchangeOutbound, Operation: "stats_pull",
				DataClass: string(federation.DataClassStats), Status: db.ExchangeError,
				StartedAt: started, Error: ferr.Error(),
			})
			continue
		}
		// Ingest: a friend's hop-0 datum is hop 1 from us.
		for i := range entries {
			entries[i].Hops++
		}
		all = append(all, entries...)
		federation.RecordExchange(h.DB, federation.ExchangeRecord{
			RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
			Direction: db.ExchangeOutbound, Operation: "stats_pull",
			DataClass: string(federation.DataClassStats), Status: db.ExchangeOK,
			StartedAt: started, ItemCount: len(entries),
		})
	}

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

	out := &AggregatedStatsOutput{}
	out.Body.Stats = aggregated
	return out, nil
}
