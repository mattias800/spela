package api

import (
	"context"
	"encoding/base64"
	"log/slog"
	"net/http"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"gorm.io/gorm"
)

// --- Issue invite ----------------------------------------------------------

type IssueInviteInput struct{}
type IssueInviteOutput struct {
	Body struct {
		Invite string `json:"invite"`
	}
}

// HumaIssueInvite records a one-time nonce and returns a signed, encoded invite
// for the operator to hand to a friend out-of-band.
func (h *FederationHandler) HumaIssueInvite(_ context.Context, _ *IssueInviteInput) (*IssueInviteOutput, error) {
	nonce := federation.NewRequestID() + federation.NewRequestID()
	expires := time.Now().Add(30 * time.Minute)
	if err := h.DB.Create(&db.FederationInviteNonce{Nonce: nonce, ExpiresAt: expires}).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to record invite nonce")
	}
	inv := h.Identity.NewInvite(h.BaseURL, nonce, expires)
	out := &IssueInviteOutput{}
	out.Body.Invite = federation.EncodeInvite(inv)
	return out, nil
}

// --- Accept invite ---------------------------------------------------------

type AcceptInviteBody struct {
	Invite string `json:"invite"`
	Name   string `json:"name" maxLength:"128"`
}
type AcceptInviteInput struct {
	Body AcceptInviteBody
}
type AcceptInviteOutput struct {
	Body PairResponseBody
}

// HumaAcceptInvite verifies a friend's invite, calls the friend back with our
// signed bundle, and stores the friend as an active peer.
func (h *FederationHandler) HumaAcceptInvite(_ context.Context, in *AcceptInviteInput) (*AcceptInviteOutput, error) {
	started := time.Now()
	reqID := federation.NewRequestID()

	inv, err := federation.DecodeInvite(in.Body.Invite)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid invite")
	}
	ok, err := federation.VerifyInvite(inv, time.Now())
	if err != nil || !ok {
		return nil, huma.Error400BadRequest("invite failed verification")
	}

	pubB64 := base64.StdEncoding.EncodeToString(h.Identity.PublicKey)
	fp := h.Identity.Fingerprint()
	sig := h.Identity.Sign(pairBundleBytes(fp, pubB64, h.BaseURL, inv.Nonce))
	bundle := PairRequestBody{
		Fingerprint: fp, PublicKey: pubB64, BaseURL: h.BaseURL,
		Nonce: inv.Nonce, Signature: base64.StdEncoding.EncodeToString(sig),
	}

	client := h.PairClient
	if client == nil {
		client = httpPairClient{}
	}
	resp, err := client.Pair(inv.BaseURL, bundle)
	if err != nil {
		federation.RecordExchange(h.DB, federation.ExchangeRecord{
			RequestID: reqID, PeerFingerprint: inv.Fingerprint, PeerName: in.Body.Name,
			Direction: db.ExchangeOutbound, Operation: "pair", Status: db.ExchangeError,
			StartedAt: started, Error: err.Error(),
		})
		return nil, huma.Error502BadGateway("pairing callback failed: " + err.Error())
	}

	// Trust the invite's verified identity, NOT the response body. The invite
	// was signature-verified (VerifyInvite binds fingerprint == SHA-256(pubkey)
	// and checks the signature), so inv.Fingerprint/PublicKey/BaseURL are
	// trustworthy. The response is only a success confirmation; a hostile or
	// misconfigured remote must not be able to inject a different peer's
	// identity. Mismatch => abort.
	if resp.Fingerprint != inv.Fingerprint {
		federation.RecordExchange(h.DB, federation.ExchangeRecord{
			RequestID: reqID, PeerFingerprint: inv.Fingerprint, PeerName: in.Body.Name,
			Direction: db.ExchangeOutbound, Operation: "pair", Status: db.ExchangeRejected,
			StartedAt: started, Error: "remote returned a mismatched fingerprint",
		})
		return nil, huma.Error502BadGateway("remote returned a mismatched fingerprint")
	}

	if err := h.Peers.Upsert(&db.FederationPeer{
		Fingerprint: inv.Fingerprint, PublicKey: inv.PublicKey,
		Name: in.Body.Name, BaseURL: inv.BaseURL, Status: db.PeerStatusActive,
	}); err != nil {
		return nil, huma.Error500InternalServerError("failed to store peer")
	}
	federation.RecordExchange(h.DB, federation.ExchangeRecord{
		RequestID: reqID, PeerFingerprint: inv.Fingerprint, PeerName: in.Body.Name,
		Direction: db.ExchangeOutbound, Operation: "pair", Status: db.ExchangeOK, StartedAt: started,
	})
	return &AcceptInviteOutput{Body: PairResponseBody{
		Fingerprint: inv.Fingerprint, PublicKey: inv.PublicKey,
		BaseURL: inv.BaseURL, Status: db.PeerStatusActive,
	}}, nil
}

// --- List peers (with health) ----------------------------------------------

type ListPeersInput struct{}
type ListPeersOutput struct {
	Body struct {
		Peers []db.FederationPeer `json:"peers"`
	}
}

func (h *FederationHandler) HumaListPeers(_ context.Context, _ *ListPeersInput) (*ListPeersOutput, error) {
	peers, err := h.Peers.List()
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to list peers")
	}
	out := &ListPeersOutput{}
	out.Body.Peers = peers
	return out, nil
}

// --- Revoke peer -----------------------------------------------------------

type RevokePeerInput struct {
	Fingerprint string `path:"fingerprint" pattern:"^[a-z2-7]{52}$" maxLength:"64" doc:"Peer fingerprint (base32)."`
}
type RevokePeerOutput struct {
	Body struct {
		Revoked bool `json:"revoked"`
	}
}

func (h *FederationHandler) HumaRevokePeer(_ context.Context, in *RevokePeerInput) (*RevokePeerOutput, error) {
	if err := h.Peers.Remove(in.Fingerprint); err != nil {
		return nil, huma.Error500InternalServerError("failed to revoke peer")
	}
	// Drop the revoked friend's cached rollup so its contribution vanishes
	// immediately (clean blast radius).
	if err := h.Snapshots.RemovePeerSnapshot(in.Fingerprint); err != nil {
		slog.Warn("federation: failed to drop revoked peer snapshot", "component", "federation",
			"peer", federation.ShortFingerprint(in.Fingerprint), "error", err)
	}
	if err := h.CatalogSnapshots.RemovePeerSnapshot(in.Fingerprint); err != nil {
		slog.Warn("federation: failed to drop revoked peer catalog snapshot", "component", "federation",
			"peer", federation.ShortFingerprint(in.Fingerprint), "error", err)
	}
	slog.Info("federation: revoked peer", "component", "federation",
		"peer", federation.ShortFingerprint(in.Fingerprint))
	out := &RevokePeerOutput{}
	out.Body.Revoked = true
	return out, nil
}

// --- Update peer policy ----------------------------------------------------

type UpdatePeerPolicyBody struct {
	SharePolicy   map[string]bool `json:"sharePolicy" doc:"Data classes we expose to this peer (class -> allowed)."`
	ConsumePolicy map[string]bool `json:"consumePolicy" doc:"Data classes we accept from this peer (class -> allowed)."`
}
type UpdatePeerPolicyInput struct {
	Fingerprint string `path:"fingerprint" pattern:"^[a-z2-7]{52}$" maxLength:"64" doc:"Peer fingerprint (base32)."`
	Body        UpdatePeerPolicyBody
}
type UpdatePeerPolicyOutput struct {
	Body struct {
		Peer db.FederationPeer `json:"peer"`
	}
}

// HumaUpdatePeerPolicy sets the per-friend share/consume policy — which data
// classes we expose to and accept from this peer. Unknown data classes are
// rejected so a typo or hostile payload can't persist a meaningless key.
func (h *FederationHandler) HumaUpdatePeerPolicy(_ context.Context, in *UpdatePeerPolicyInput) (*UpdatePeerPolicyOutput, error) {
	peer, err := h.Peers.GetByFingerprint(in.Fingerprint)
	if err != nil {
		return nil, huma.Error404NotFound("peer not found")
	}
	share, err := federation.ValidatePolicyMap(in.Body.SharePolicy)
	if err != nil {
		return nil, huma.Error400BadRequest(err.Error())
	}
	consume, err := federation.ValidatePolicyMap(in.Body.ConsumePolicy)
	if err != nil {
		return nil, huma.Error400BadRequest(err.Error())
	}
	shareJSON, err := federation.MarshalPolicy(share)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to encode share policy")
	}
	consumeJSON, err := federation.MarshalPolicy(consume)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to encode consume policy")
	}
	if err := h.Peers.SetPolicies(in.Fingerprint, shareJSON, consumeJSON); err != nil {
		return nil, huma.Error500InternalServerError("failed to update policy")
	}
	// Re-read so the response carries the freshly-bumped UpdatedAt, not the
	// pre-update timestamp from the existence check above.
	peer, err = h.Peers.GetByFingerprint(in.Fingerprint)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to read updated peer")
	}
	slog.Info("federation: updated peer policy", "component", "federation",
		"peer", federation.ShortFingerprint(in.Fingerprint))
	out := &UpdatePeerPolicyOutput{}
	out.Body.Peer = *peer
	return out, nil
}

// --- Test connection (active diagnostic) -----------------------------------

type TestPeerInput struct {
	Fingerprint string `path:"fingerprint" pattern:"^[a-z2-7]{52}$" maxLength:"64" doc:"Peer fingerprint (base32)."`
}
type TestPeerOutput struct {
	Body PingResult
}

// HumaTestPeer performs a signed ping round-trip to a peer and returns a
// per-step diagnostic (reachable, latency, clock skew, fingerprint match). The
// outcome is folded into the peer's health via the exchange ledger.
func (h *FederationHandler) HumaTestPeer(_ context.Context, in *TestPeerInput) (*TestPeerOutput, error) {
	peer, err := h.Peers.GetByFingerprint(in.Fingerprint)
	if err != nil {
		return nil, huma.Error404NotFound("peer not found")
	}
	reqID := federation.NewRequestID()
	started := time.Now()

	pinger := h.Pinger
	if pinger == nil {
		pinger = httpPeerPinger{}
	}
	res := pinger.Ping(peer.BaseURL, reqID, h.Identity, peer.Fingerprint)

	status := db.ExchangeOK
	if !res.Reachable {
		status = db.ExchangeError
	}
	federation.RecordExchange(h.DB, federation.ExchangeRecord{
		RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
		Direction: db.ExchangeOutbound, Operation: "ping", Status: status,
		StartedAt: started, Error: res.Error,
	})
	return &TestPeerOutput{Body: res}, nil
}

// --- List exchange ledger --------------------------------------------------

type ListExchangesInput struct {
	Peer          string `query:"peer"`
	Direction     string `query:"direction"`
	Operation     string `query:"operation"`
	Status        string `query:"status"`
	StartedAfter  string `query:"startedAfter"`
	StartedBefore string `query:"startedBefore"`
	Limit         int    `query:"limit"`
}
type ListExchangesOutput struct {
	Body struct {
		Exchanges []db.FederationExchange `json:"exchanges"`
	}
}

func (h *FederationHandler) HumaListExchanges(_ context.Context, in *ListExchangesInput) (*ListExchangesOutput, error) {
	q := h.DB.Model(&db.FederationExchange{}).Order("created_at DESC")
	if in.Peer != "" {
		q = q.Where("peer_fingerprint = ?", in.Peer)
	}
	if in.Direction != "" {
		q = q.Where("direction = ?", in.Direction)
	}
	if in.Operation != "" {
		q = q.Where("operation = ?", in.Operation)
	}
	if in.Status != "" {
		q = q.Where("status = ?", in.Status)
	}
	if in.StartedAfter != "" {
		startedAfter, err := time.Parse(time.RFC3339, in.StartedAfter)
		if err != nil {
			return nil, huma.Error400BadRequest("invalid startedAfter")
		}
		q = q.Where("started_at >= ?", startedAfter)
	}
	if in.StartedBefore != "" {
		startedBefore, err := time.Parse(time.RFC3339, in.StartedBefore)
		if err != nil {
			return nil, huma.Error400BadRequest("invalid startedBefore")
		}
		q = q.Where("started_at <= ?", startedBefore)
	}
	limit := in.Limit
	if limit <= 0 || limit > 500 {
		limit = 100
	}
	var rows []db.FederationExchange
	if err := q.Limit(limit).Find(&rows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to list exchanges")
	}
	out := &ListExchangesOutput{}
	out.Body.Exchanges = rows
	return out, nil
}

// --- Ping (inbound, gin route behind VerifyFederationRequest) ---------------

// ginPing answers a peer's signed connection test with our fingerprint and
// clock, and records the inbound exchange.
func (h *FederationHandler) ginPing(c *gin.Context) {
	reqID := c.GetHeader(headerFedRequestID)
	if reqID == "" {
		reqID = federation.NewRequestID()
	}
	fp, name := "", ""
	if peer, ok := federationPeerFromGin(c); ok {
		fp, name = peer.Fingerprint, peer.Name
	}
	federation.RecordExchange(h.DB, federation.ExchangeRecord{
		RequestID: reqID, PeerFingerprint: fp, PeerName: name,
		Direction: db.ExchangeInbound, Operation: "ping", Status: db.ExchangeOK,
	})
	c.JSON(http.StatusOK, gin.H{
		"fingerprint": h.Identity.Fingerprint(),
		"unixTime":    time.Now().Unix(),
	})
}

// --- Registration ----------------------------------------------------------

// RegisterFederationRoutes wires the huma federation endpoints. The pair
// endpoint is public (bootstrap, invite-nonce-gated); admin endpoints require
// admin auth.
func RegisterFederationRoutes(api huma.API, h *FederationHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	requireAdmin := RequireAdmin(api)
	adminMW := huma.Middlewares{requireAuth, rateLimit, requireAdmin}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "federationPair",
		Method:      http.MethodPost,
		Path:        "/api/federation/pair",
		Summary:     "Pairing callback (server-to-server bootstrap)",
		Tags:        []string{"federation"},
	}, h.HumaPair)

	huma.Register(api, huma.Operation{
		OperationID: "federationIssueInvite",
		Method:      http.MethodPost,
		Path:        "/api/admin/federation/invite",
		Summary:     "Issue a federation pairing invite",
		Tags:        []string{"federation", "admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaIssueInvite)

	huma.Register(api, huma.Operation{
		OperationID: "federationAcceptInvite",
		Method:      http.MethodPost,
		Path:        "/api/admin/federation/peers/accept",
		Summary:     "Accept a friend's invite and pair",
		Tags:        []string{"federation", "admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaAcceptInvite)

	huma.Register(api, huma.Operation{
		OperationID: "federationListPeers",
		Method:      http.MethodGet,
		Path:        "/api/admin/federation/peers",
		Summary:     "List federation peers (with health)",
		Tags:        []string{"federation", "admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaListPeers)

	huma.Register(api, huma.Operation{
		OperationID: "federationTestPeer",
		Method:      http.MethodPost,
		Path:        "/api/admin/federation/peers/{fingerprint}/test",
		Summary:     "Test connection to a peer",
		Tags:        []string{"federation", "admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaTestPeer)

	huma.Register(api, huma.Operation{
		OperationID: "federationRevokePeer",
		Method:      http.MethodDelete,
		Path:        "/api/admin/federation/peers/{fingerprint}",
		Summary:     "Revoke a federation peer",
		Tags:        []string{"federation", "admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaRevokePeer)

	huma.Register(api, huma.Operation{
		OperationID: "federationUpdatePeerPolicy",
		Method:      http.MethodPut,
		Path:        "/api/admin/federation/peers/{fingerprint}/policy",
		Summary:     "Update a peer's share/consume policy",
		Tags:        []string{"federation", "admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaUpdatePeerPolicy)

	huma.Register(api, huma.Operation{
		OperationID: "federationListExchanges",
		Method:      http.MethodGet,
		Path:        "/api/admin/federation/exchanges",
		Summary:     "List the federation exchange ledger (observability)",
		Tags:        []string{"federation", "admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaListExchanges)

	// User-facing: the federated (mesh) leaderboard. Auth + rate limit, not admin.
	huma.Register(api, huma.Operation{
		OperationID: "federationAggregatedStats",
		Method:      http.MethodGet,
		Path:        "/api/federation/stats/aggregated",
		Summary:     "Federated (mesh) aggregate stats, hop-bounded",
		Tags:        []string{"federation", "stats"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    sec,
	}, h.HumaAggregatedStats)

	// User-facing: who's playing what right now across the mesh (live, hop-bounded).
	huma.Register(api, huma.Operation{
		OperationID: "federationAggregatedPresence",
		Method:      http.MethodGet,
		Path:        "/api/federation/presence/aggregated",
		Summary:     "Who's playing now across the connected-server mesh (live)",
		Tags:        []string{"federation", "presence"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    sec,
	}, h.HumaAggregatedPresence)

	// User-facing: top achievers across the mesh (live, hop-bounded).
	huma.Register(api, huma.Operation{
		OperationID: "federationAggregatedAchievements",
		Method:      http.MethodGet,
		Path:        "/api/federation/achievements/aggregated",
		Summary:     "Top achievers across the connected-server mesh (live)",
		Tags:        []string{"federation", "achievements"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    sec,
	}, h.HumaAggregatedAchievements)

	// Admin: force a refresh of cached friend rollups (also runs periodically).
	huma.Register(api, huma.Operation{
		OperationID: "federationRefreshStats",
		Method:      http.MethodPost,
		Path:        "/api/admin/federation/stats/refresh",
		Summary:     "Refresh cached friend stat rollups now",
		Tags:        []string{"federation", "admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaRefreshStats)

	// User-facing: mesh game catalog (which games are available across friends).
	huma.Register(api, huma.Operation{
		OperationID: "federationAvailableGames",
		Method:      http.MethodGet,
		Path:        "/api/federation/catalog/available",
		Summary:     "Games available across the friend mesh, hop-bounded",
		Tags:        []string{"federation", "catalog"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    sec,
	}, h.HumaAvailableGames)

	// User-facing: per-console game counts for the connected-servers browse
	// overview (no covers resolved — counts only).
	huma.Register(api, huma.Operation{
		OperationID: "federationCatalogConsoles",
		Method:      http.MethodGet,
		Path:        "/api/federation/catalog/consoles",
		Summary:     "Per-console game counts across the connected-server mesh",
		Tags:        []string{"federation", "catalog"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    sec,
	}, h.HumaCatalogConsoles)

	// User-facing (capability-gated in the handler): import a connected-server
	// game into the local library, and list import jobs + their progress (#1350).
	huma.Register(api, huma.Operation{
		OperationID: "federationStartImport",
		Method:      http.MethodPost,
		Path:        "/api/federation/import",
		Summary:     "Import a connected-server game into the local library",
		Tags:        []string{"federation", "catalog"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    sec,
	}, h.HumaStartImport)

	huma.Register(api, huma.Operation{
		OperationID: "federationListImports",
		Method:      http.MethodGet,
		Path:        "/api/federation/imports",
		Summary:     "List game imports with status and progress",
		Tags:        []string{"federation", "catalog"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    sec,
	}, h.HumaListImports)

	// Admin: force a refresh of cached friend catalogs (also runs periodically).
	huma.Register(api, huma.Operation{
		OperationID: "federationRefreshCatalog",
		Method:      http.MethodPost,
		Path:        "/api/admin/federation/catalog/refresh",
		Summary:     "Refresh cached friend game catalogs now",
		Tags:        []string{"federation", "admin"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaRefreshCatalog)
}

// RegisterFederationGinRoutes wires raw-gin federation routes: the signed ping
// used by the connection-test diagnostic, plus the signed stat/catalog exports.
func RegisterFederationGinRoutes(r *gin.Engine, h *FederationHandler, downloadLimiter *RateLimiter) {
	requireFedPeer := VerifyFederationRequest(h.DB, h.Identity.Fingerprint())
	r.GET("/api/federation/ping", requireFedPeer, h.ginPing)
	r.GET("/api/federation/stats", requireFedPeer, h.ginExportStats)
	r.GET("/api/federation/presence", requireFedPeer, h.ginExportPresence)
	r.GET("/api/federation/achievements", requireFedPeer, h.ginExportAchievements)
	r.GET("/api/federation/catalog", requireFedPeer, h.ginExportCatalog)
	// Binary ROM transfer routes are bandwidth-sensitive — rate-limit them like
	// the other download endpoints (#1348 Phase 3b-1).
	// Serve a local ROM to a friend (peer-to-peer, SharePolicy(download)-gated).
	r.GET("/api/federation/download", downloadLimiter.RateLimit(), requireFedPeer, h.ginServeDownload)
	// User-facing: download a game a direct friend offers.
	r.GET("/api/federation/games/download", downloadLimiter.RateLimit(), AuthMiddleware(h.JWTSecret, h.DB), h.ginUserDownload)
}
