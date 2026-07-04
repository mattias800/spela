package api

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
)

// maxAchievementsResponseBytes caps an inbound peer achievements payload.
const maxAchievementsResponseBytes = 1 << 20 // 1 MiB

// maxAchievementEntriesPerPeer caps how many leaderboard rows we accept from one
// connected server per read, bounding a single peer's influence on the view.
const maxAchievementEntriesPerPeer = 5000

// achievementsClient fetches a connected server's achievement leaderboard over a
// signed request. Like presence, this is pulled live at read time (no snapshot
// cache); transitive reach is a later concern.
type achievementsClient interface {
	FetchAchievements(baseURL, requestID string, id federation.Identity, peerFingerprint string) ([]federation.AchievementEntry, error)
}

type httpAchievementsClient struct{}

func (httpAchievementsClient) FetchAchievements(baseURL, requestID string, id federation.Identity, peerFingerprint string) ([]federation.AchievementEntry, error) {
	const path = "/api/federation/achievements"
	req, err := http.NewRequest(http.MethodGet, baseURL+path, nil)
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
		Entries []federation.AchievementEntry `json:"entries"`
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, maxAchievementsResponseBytes)).Decode(&body); err != nil {
		return nil, err
	}
	return body.Entries, nil
}

// sanitizeAchievementsBatch cleans a connected server's leaderboard before we
// ingest it: drop entries claiming OUR origin (a loop), missing an origin, any
// hop other than 0 (a direct server exports only its own hop-0 rows — no
// transitive relay), or negative counts. Caps the count per peer.
func sanitizeAchievementsBatch(entries []federation.AchievementEntry, selfFingerprint string) []federation.AchievementEntry {
	out := make([]federation.AchievementEntry, 0, len(entries))
	for _, e := range entries {
		if e.OriginFingerprint == "" || e.OriginFingerprint == selfFingerprint {
			continue
		}
		if e.Hops != 0 || e.Count < 0 {
			continue
		}
		out = append(out, e)
		if len(out) >= maxAchievementEntriesPerPeer {
			break
		}
	}
	return out
}

// ginExportAchievements serves this server's own achievement leaderboard (hop 0)
// to a verified connected server. Behind VerifyFederationRequest +
// SharePolicy(achievements). Never re-served transitively — local rows only.
func (h *FederationHandler) ginExportAchievements(c *gin.Context) {
	reqID := c.GetHeader(headerFedRequestID)
	if reqID == "" {
		reqID = federation.NewRequestID()
	}
	peer, ok := federationPeerFromGin(c)
	if !ok {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "no verified peer"})
		return
	}
	if !federation.CanShare(*peer, federation.DataClassAchievement) {
		c.JSON(http.StatusForbidden, gin.H{"error": "achievements not shared with this peer"})
		return
	}

	local, err := federation.BuildLocalAchievements(h.DB, h.Identity.Fingerprint())
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build achievements"})
		return
	}

	federation.RecordExchange(h.DB, federation.ExchangeRecord{
		RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
		Direction: db.ExchangeInbound, Operation: "achievements_export",
		DataClass: string(federation.DataClassAchievement), Status: db.ExchangeOK, ItemCount: len(local),
	})
	c.JSON(http.StatusOK, gin.H{"entries": local})
}

// --- Aggregated read (user-facing) -----------------------------------------

type AggregatedAchievementsInput struct {
	Limit int `query:"limit"`
}
type AggregatedAchievementsOutput struct {
	Body struct {
		Achievements []federation.AchievementEntry `json:"achievements"`
	}
}

// HumaAggregatedAchievements returns the "top achievers" leaderboard across the
// mesh: this server's per-player unlock counts (hop 0) plus a concurrent LIVE
// pull from each achievements-consumable active connected server (hop 1). Deduped
// by (origin, user), sorted by count descending, admin-only origin fingerprints
// stripped (a friendly ServerName is provided instead).
func (h *FederationHandler) HumaAggregatedAchievements(_ context.Context, in *AggregatedAchievementsInput) (*AggregatedAchievementsOutput, error) {
	all, err := federation.BuildLocalAchievements(h.DB, h.Identity.Fingerprint())
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to build local achievements")
	}

	peers, err := h.Peers.List()
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to list peers")
	}
	client := h.AchievementsClient
	if client == nil {
		client = httpAchievementsClient{}
	}
	self := h.Identity.Fingerprint()

	// Pull each achievements-consumable connected server concurrently; one slow
	// or unreachable peer must not stall the view. Only the network fetches run
	// in parallel — ledger writes + merge run serially afterwards, in peer order,
	// so the result is deterministic and DB writes don't race.
	var consumable []db.FederationPeer
	for _, peer := range peers {
		if peer.Status == db.PeerStatusActive && federation.CanConsume(peer, federation.DataClassAchievement) {
			consumable = append(consumable, peer)
		}
	}
	type pullResult struct {
		reqID   string
		started time.Time
		entries []federation.AchievementEntry
		err     error
	}
	results := make([]pullResult, len(consumable))
	var wg sync.WaitGroup
	for i := range consumable {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			peer := consumable[i]
			reqID := federation.NewRequestID()
			started := time.Now()
			entries, ferr := client.FetchAchievements(peer.BaseURL, reqID, h.Identity, peer.Fingerprint)
			results[i] = pullResult{reqID: reqID, started: started, entries: entries, err: ferr}
		}(i)
	}
	wg.Wait()

	for i, peer := range consumable {
		r := results[i]
		if r.err != nil {
			federation.RecordExchange(h.DB, federation.ExchangeRecord{
				RequestID: r.reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
				Direction: db.ExchangeOutbound, Operation: "achievements_pull",
				DataClass: string(federation.DataClassAchievement), Status: db.ExchangeError,
				StartedAt: r.started, Error: r.err.Error(),
			})
			continue
		}
		cleaned := sanitizeAchievementsBatch(r.entries, self)
		for j := range cleaned {
			cleaned[j].Hops++ // distance from us = distance from the server + 1
			cleaned[j].ServerName = peer.Name
		}
		all = append(all, cleaned...)
		federation.RecordExchange(h.DB, federation.ExchangeRecord{
			RequestID: r.reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
			Direction: db.ExchangeOutbound, Operation: "achievements_pull",
			DataClass: string(federation.DataClassAchievement), Status: db.ExchangeOK,
			StartedAt: r.started, ItemCount: len(cleaned),
		})
	}

	leaderboard := federation.SortAchievementEntries(federation.DedupeAchievementEntries(all))
	// Strip the origin fingerprint (admin-only peer roster); ServerName remains.
	for i := range leaderboard {
		leaderboard[i].OriginFingerprint = ""
	}
	if in.Limit > 0 && len(leaderboard) > in.Limit {
		leaderboard = leaderboard[:in.Limit]
	}

	out := &AggregatedAchievementsOutput{}
	out.Body.Achievements = leaderboard
	return out, nil
}
