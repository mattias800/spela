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

// maxPresenceResponseBytes caps an inbound friend presence payload (defense
// against a hostile/misconfigured peer returning an unbounded body).
const maxPresenceResponseBytes = 1 << 20 // 1 MiB

// maxPresenceEntriesPerPeer caps how many presence rows we accept from one
// connected server per read, bounding a single peer's influence on the view.
const maxPresenceEntriesPerPeer = 2000

// presenceClient fetches a connected server's live presence over a signed
// request. Unlike stats, presence is pulled live at read time (no snapshot
// cache) because it is ephemeral.
type presenceClient interface {
	FetchPresence(baseURL, requestID string, id federation.Identity, peerFingerprint string) ([]federation.PresenceEntry, error)
}

type httpPresenceClient struct{}

func (httpPresenceClient) FetchPresence(baseURL, requestID string, id federation.Identity, peerFingerprint string) ([]federation.PresenceEntry, error) {
	const path = "/api/federation/presence"
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
		Entries []federation.PresenceEntry `json:"entries"`
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, maxPresenceResponseBytes)).Decode(&body); err != nil {
		return nil, err
	}
	return body.Entries, nil
}

// buildLocalPresence snapshots the hub's active sessions and turns them into
// source-stamped hop-0 presence. Returns an empty slice when no hub is wired
// (e.g. in handlers constructed without one).
func (h *FederationHandler) buildLocalPresence() ([]federation.PresenceEntry, error) {
	if h.Hub == nil {
		return []federation.PresenceEntry{}, nil
	}
	raw := h.Hub.PlayingNow()
	sessions := make([]federation.PlayingSession, len(raw))
	for i, s := range raw {
		sessions[i] = federation.PlayingSession{UserID: s.UserID, GameID: s.GameID}
	}
	return federation.BuildLocalPresence(h.DB, h.Identity.Fingerprint(), sessions)
}

// sanitizePresenceBatch cleans a connected server's presence before we ingest
// it: drop entries claiming OUR origin (a loop), missing an origin, or claiming
// any hop other than 0. A direct server exports only its own (hop-0) presence —
// it does not relay transitive presence — so anything else is malformed or
// hostile. Caps the count per peer.
func sanitizePresenceBatch(entries []federation.PresenceEntry, selfFingerprint string) []federation.PresenceEntry {
	out := make([]federation.PresenceEntry, 0, len(entries))
	for _, e := range entries {
		if e.OriginFingerprint == "" || e.OriginFingerprint == selfFingerprint {
			continue
		}
		if e.Hops != 0 {
			continue
		}
		out = append(out, e)
		if len(out) >= maxPresenceEntriesPerPeer {
			break
		}
	}
	return out
}

// ginExportPresence serves this server's own live presence (hop 0) to a verified
// connected server. Behind VerifyFederationRequest + SharePolicy(presence).
// Presence is never re-served transitively — only local sessions are exposed.
func (h *FederationHandler) ginExportPresence(c *gin.Context) {
	reqID := c.GetHeader(headerFedRequestID)
	if reqID == "" {
		reqID = federation.NewRequestID()
	}
	peer, ok := federationPeerFromGin(c)
	if !ok {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "no verified peer"})
		return
	}
	if !federation.CanShare(*peer, federation.DataClassPresence) {
		c.JSON(http.StatusForbidden, gin.H{"error": "presence not shared with this peer"})
		return
	}

	local, err := h.buildLocalPresence()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build presence"})
		return
	}

	federation.RecordExchange(h.DB, federation.ExchangeRecord{
		RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
		Direction: db.ExchangeInbound, Operation: "presence_export",
		DataClass: string(federation.DataClassPresence), Status: db.ExchangeOK, ItemCount: len(local),
	})
	c.JSON(http.StatusOK, gin.H{"entries": local})
}

// --- Aggregated read (user-facing) -----------------------------------------

type AggregatedPresenceInput struct{}
type AggregatedPresenceOutput struct {
	Body struct {
		Presence []federation.PresenceEntry `json:"presence"`
	}
}

// HumaAggregatedPresence returns who is playing what right now across the mesh:
// this server's own sessions (hop 0) plus a LIVE pull from each
// presence-consumable active connected server (hop 1). There is no snapshot
// cache — presence is too short-lived to cache — so the pulls happen on every
// read. Entries are deduped by (origin, user) and the admin-only origin
// fingerprint is stripped from the user-facing response (a friendly ServerName
// is provided instead).
func (h *FederationHandler) HumaAggregatedPresence(_ context.Context, _ *AggregatedPresenceInput) (*AggregatedPresenceOutput, error) {
	all, err := h.buildLocalPresence()
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to build local presence")
	}

	peers, err := h.Peers.List()
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to list peers")
	}
	client := h.PresenceClient
	if client == nil {
		client = httpPresenceClient{}
	}
	self := h.Identity.Fingerprint()

	// Pull each presence-consumable connected server concurrently: the endpoint
	// is polled live by the UI, so one slow or unreachable peer must not stall
	// the whole view. Only the network fetches run in parallel — the ledger
	// writes and the merge happen serially afterwards, in peer order, so the
	// result is deterministic and DB writes don't race.
	var consumable []db.FederationPeer
	for _, peer := range peers {
		if peer.Status == db.PeerStatusActive && federation.CanConsume(peer, federation.DataClassPresence) {
			consumable = append(consumable, peer)
		}
	}
	type pullResult struct {
		reqID   string
		started time.Time
		entries []federation.PresenceEntry
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
			entries, ferr := client.FetchPresence(peer.BaseURL, reqID, h.Identity, peer.Fingerprint)
			results[i] = pullResult{reqID: reqID, started: started, entries: entries, err: ferr}
		}(i)
	}
	wg.Wait()

	for i, peer := range consumable {
		r := results[i]
		if r.err != nil {
			federation.RecordExchange(h.DB, federation.ExchangeRecord{
				RequestID: r.reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
				Direction: db.ExchangeOutbound, Operation: "presence_pull",
				DataClass: string(federation.DataClassPresence), Status: db.ExchangeError,
				StartedAt: r.started, Error: r.err.Error(),
			})
			continue
		}
		cleaned := sanitizePresenceBatch(r.entries, self)
		for j := range cleaned {
			cleaned[j].Hops++ // distance from us = distance from the server + 1
			cleaned[j].ServerName = peer.Name
		}
		all = append(all, cleaned...)
		federation.RecordExchange(h.DB, federation.ExchangeRecord{
			RequestID: r.reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
			Direction: db.ExchangeOutbound, Operation: "presence_pull",
			DataClass: string(federation.DataClassPresence), Status: db.ExchangeOK,
			StartedAt: r.started, ItemCount: len(cleaned),
		})
	}

	deduped := federation.DedupePresenceEntries(all)
	// Strip the origin fingerprint: it identifies peers, and the peer roster is
	// admin-only elsewhere. The friendly ServerName remains for display.
	for i := range deduped {
		deduped[i].OriginFingerprint = ""
	}

	out := &AggregatedPresenceOutput{}
	out.Body.Presence = deduped
	return out, nil
}
