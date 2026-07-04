package api

import (
	"context"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// fakePairClient returns a canned bundle from the remote friend. respFingerprint
// overrides the returned fingerprint (empty = the remote's real one) so tests
// can simulate a hostile/misconfigured remote.
type fakePairClient struct {
	remote          federation.Identity
	respFingerprint string
	called          bool
}

func (f *fakePairClient) Pair(baseURL string, _ PairRequestBody) (PairResponseBody, error) {
	f.called = true
	fp := f.remote.Fingerprint()
	if f.respFingerprint != "" {
		fp = f.respFingerprint
	}
	return PairResponseBody{
		Fingerprint: fp,
		PublicKey:   b64(f.remote.PublicKey),
		BaseURL:     baseURL,
		Status:      db.PeerStatusActive,
	}, nil
}

// fakePinger returns a canned ping result.
type fakePinger struct{ res PingResult }

func (f fakePinger) Ping(_, _ string, _ federation.Identity, _ string) PingResult { return f.res }

func TestIssueInvite_StoresNonceAndSignsInvite(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	h := &FederationHandler{DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database}, BaseURL: "https://self"}

	out, err := h.HumaIssueInvite(context.Background(), &IssueInviteInput{})
	require.NoError(t, err)
	assert.NotEmpty(t, out.Body.Invite)

	decoded, err := federation.DecodeInvite(out.Body.Invite)
	require.NoError(t, err)
	assert.Equal(t, selfID.Fingerprint(), decoded.Fingerprint)

	var count int64
	database.Model(&db.FederationInviteNonce{}).Count(&count)
	assert.Equal(t, int64(1), count, "issuing an invite records its nonce")
}

func TestAcceptInvite_PairsAndStoresPeer(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	remoteID, _ := federation.GenerateIdentity()
	fake := &fakePairClient{remote: remoteID}
	h := &FederationHandler{
		DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database},
		BaseURL: "https://self", PairClient: fake,
	}

	inv := remoteID.NewInvite("https://remote", "remote-nonce", time.Unix(4_000_000_000, 0))
	out, err := h.HumaAcceptInvite(context.Background(), &AcceptInviteInput{
		Body: AcceptInviteBody{Invite: federation.EncodeInvite(inv), Name: "Bob"},
	})
	require.NoError(t, err)
	assert.True(t, fake.called, "accept must call the friend back")
	assert.Equal(t, db.PeerStatusActive, out.Body.Status)

	stored, err := h.Peers.GetByFingerprint(remoteID.Fingerprint())
	require.NoError(t, err)
	assert.Equal(t, "Bob", stored.Name)
	assert.Equal(t, db.PeerStatusActive, stored.Status)
}

func TestAcceptInvite_RejectsMismatchedFingerprint(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	remoteID, _ := federation.GenerateIdentity()
	attacker, _ := federation.GenerateIdentity()

	// The remote responds with a fingerprint different from the invite it signed.
	fake := &fakePairClient{remote: remoteID, respFingerprint: attacker.Fingerprint()}
	h := &FederationHandler{
		DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database},
		BaseURL: "https://self", PairClient: fake,
	}
	inv := remoteID.NewInvite("https://remote", "n", time.Unix(4_000_000_000, 0))

	_, err := h.HumaAcceptInvite(context.Background(), &AcceptInviteInput{
		Body: AcceptInviteBody{Invite: federation.EncodeInvite(inv), Name: "Bob"},
	})
	assert.Error(t, err, "must reject a response whose fingerprint differs from the verified invite")

	// Neither the real remote nor the injected attacker identity is stored.
	store := federation.PeerStore{DB: database}
	_, e1 := store.GetByFingerprint(remoteID.Fingerprint())
	_, e2 := store.GetByFingerprint(attacker.Fingerprint())
	assert.Error(t, e1)
	assert.Error(t, e2)
}

func TestRevokePeer_RemovesIt(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	store := federation.PeerStore{DB: database}
	require.NoError(t, store.Upsert(&db.FederationPeer{
		Fingerprint: "fp-x", PublicKey: "k", BaseURL: "https://x", Status: db.PeerStatusActive,
	}))
	h := &FederationHandler{DB: database, Identity: selfID, Peers: store,
		Snapshots:        federation.SnapshotStore{DB: database},
		CatalogSnapshots: federation.CatalogSnapshotStore{DB: database},
		BaseURL:          "https://self"}

	_, err := h.HumaRevokePeer(context.Background(), &RevokePeerInput{Fingerprint: "fp-x"})
	require.NoError(t, err)
	_, err = store.GetByFingerprint("fp-x")
	assert.Error(t, err)
}

func TestUpdatePeerPolicy_SetsShareAndConsume(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	store := federation.PeerStore{DB: database}
	require.NoError(t, store.Upsert(&db.FederationPeer{
		Fingerprint: "fp-pol", PublicKey: "k", BaseURL: "https://x", Status: db.PeerStatusActive,
	}))
	h := &FederationHandler{DB: database, Identity: selfID, Peers: store, BaseURL: "https://self"}

	out, err := h.HumaUpdatePeerPolicy(context.Background(), &UpdatePeerPolicyInput{
		Fingerprint: "fp-pol",
		Body: UpdatePeerPolicyBody{
			SharePolicy:   map[string]bool{"stats": true, "catalog": false},
			ConsumePolicy: map[string]bool{"download": true},
		},
	})
	require.NoError(t, err)
	assert.True(t, federation.CanShare(out.Body.Peer, federation.DataClassStats))
	assert.False(t, federation.CanShare(out.Body.Peer, federation.DataClassCatalog))
	assert.True(t, federation.CanConsume(out.Body.Peer, federation.DataClassDownload))

	// Persisted, not just echoed in the response.
	got, err := store.GetByFingerprint("fp-pol")
	require.NoError(t, err)
	assert.True(t, federation.CanShare(*got, federation.DataClassStats))
	assert.True(t, federation.CanConsume(*got, federation.DataClassDownload))
}

func TestUpdatePeerPolicy_RejectsUnknownClass(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	store := federation.PeerStore{DB: database}
	require.NoError(t, store.Upsert(&db.FederationPeer{
		Fingerprint: "fp-pol", PublicKey: "k", BaseURL: "https://x", Status: db.PeerStatusActive,
	}))
	h := &FederationHandler{DB: database, Identity: selfID, Peers: store, BaseURL: "https://self"}

	_, err := h.HumaUpdatePeerPolicy(context.Background(), &UpdatePeerPolicyInput{
		Fingerprint: "fp-pol",
		Body:        UpdatePeerPolicyBody{SharePolicy: map[string]bool{"bogus": true}},
	})
	assert.Error(t, err, "unknown data class must be rejected")

	// A rejected update leaves the stored policy untouched.
	got, err := store.GetByFingerprint("fp-pol")
	require.NoError(t, err)
	assert.Empty(t, got.SharePolicy)
}

func TestUpdatePeerPolicy_NotFound(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	h := &FederationHandler{DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database}, BaseURL: "https://self"}
	_, err := h.HumaUpdatePeerPolicy(context.Background(), &UpdatePeerPolicyInput{
		Fingerprint: "nope", Body: UpdatePeerPolicyBody{},
	})
	assert.Error(t, err)
}

func TestTestPeer_RunsDiagnosticAndUpdatesHealth(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	remoteID, _ := federation.GenerateIdentity()
	store := activePeer(t, database, remoteID, "Bob", "https://remote")
	h := &FederationHandler{
		DB: database, Identity: selfID, Peers: store, BaseURL: "https://self",
		Pinger: fakePinger{res: PingResult{
			Reachable: true, PeerFingerprint: remoteID.Fingerprint(), FingerprintMatch: true, LatencyMs: 5,
		}},
	}

	out, err := h.HumaTestPeer(context.Background(), &TestPeerInput{Fingerprint: remoteID.Fingerprint()})
	require.NoError(t, err)
	assert.True(t, out.Body.Reachable)
	assert.True(t, out.Body.FingerprintMatch)

	got, err := store.GetByFingerprint(remoteID.Fingerprint())
	require.NoError(t, err)
	assert.True(t, got.Reachable)
	require.NotNil(t, got.LastSuccessAt)
}

func TestTestPeer_RecordsErrorWhenUnreachable(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	remoteID, _ := federation.GenerateIdentity()
	store := activePeer(t, database, remoteID, "Bob", "https://remote")
	h := &FederationHandler{
		DB: database, Identity: selfID, Peers: store, BaseURL: "https://self",
		Pinger: fakePinger{res: PingResult{Reachable: false, Error: "connection refused"}},
	}

	out, err := h.HumaTestPeer(context.Background(), &TestPeerInput{Fingerprint: remoteID.Fingerprint()})
	require.NoError(t, err)
	assert.False(t, out.Body.Reachable)

	got, err := store.GetByFingerprint(remoteID.Fingerprint())
	require.NoError(t, err)
	assert.False(t, got.Reachable)
	assert.Equal(t, "connection refused", got.LastError)
}

func TestTestPeer_NotFound(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	h := &FederationHandler{DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database}, BaseURL: "https://self"}
	_, err := h.HumaTestPeer(context.Background(), &TestPeerInput{Fingerprint: "nope"})
	assert.Error(t, err)
}

func TestListExchanges_ReturnsAndFiltersLedger(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	h := &FederationHandler{DB: database, Identity: selfID, Peers: federation.PeerStore{DB: database}, BaseURL: "https://self"}
	base := time.Date(2026, 7, 4, 12, 0, 0, 0, time.UTC)

	federation.RecordExchange(database, federation.ExchangeRecord{
		RequestID: "a", PeerFingerprint: "p1", Direction: db.ExchangeOutbound, Operation: "ping", Status: db.ExchangeOK,
		StartedAt: base.Add(-2 * time.Hour), FinishedAt: base.Add(-2*time.Hour + time.Second),
	})
	federation.RecordExchange(database, federation.ExchangeRecord{
		RequestID: "b", PeerFingerprint: "p2", Direction: db.ExchangeInbound, Operation: "pair", Status: db.ExchangeRejected,
		StartedAt: base.Add(-30 * time.Minute), FinishedAt: base.Add(-30*time.Minute + time.Second),
	})
	federation.RecordExchange(database, federation.ExchangeRecord{
		RequestID: "c", PeerFingerprint: "p3", Direction: db.ExchangeOutbound, Operation: "stats_pull", Status: db.ExchangeOK,
		StartedAt: base.Add(30 * time.Minute), FinishedAt: base.Add(30*time.Minute + time.Second),
	})

	all, err := h.HumaListExchanges(context.Background(), &ListExchangesInput{})
	require.NoError(t, err)
	assert.Len(t, all.Body.Exchanges, 3)

	rejected, err := h.HumaListExchanges(context.Background(), &ListExchangesInput{Status: db.ExchangeRejected})
	require.NoError(t, err)
	require.Len(t, rejected.Body.Exchanges, 1)
	assert.Equal(t, "p2", rejected.Body.Exchanges[0].PeerFingerprint)

	statsPull, err := h.HumaListExchanges(context.Background(), &ListExchangesInput{Operation: "stats_pull"})
	require.NoError(t, err)
	require.Len(t, statsPull.Body.Exchanges, 1)
	assert.Equal(t, "p3", statsPull.Body.Exchanges[0].PeerFingerprint)

	recent, err := h.HumaListExchanges(context.Background(), &ListExchangesInput{
		StartedAfter: base.Add(-time.Hour).Format(time.RFC3339),
	})
	require.NoError(t, err)
	require.Len(t, recent.Body.Exchanges, 2)
	assert.ElementsMatch(t, []string{"p2", "p3"}, []string{
		recent.Body.Exchanges[0].PeerFingerprint,
		recent.Body.Exchanges[1].PeerFingerprint,
	})

	before, err := h.HumaListExchanges(context.Background(), &ListExchangesInput{
		StartedBefore: base.Format(time.RFC3339),
	})
	require.NoError(t, err)
	require.Len(t, before.Body.Exchanges, 2)
	assert.ElementsMatch(t, []string{"p1", "p2"}, []string{
		before.Body.Exchanges[0].PeerFingerprint,
		before.Body.Exchanges[1].PeerFingerprint,
	})

	combined, err := h.HumaListExchanges(context.Background(), &ListExchangesInput{
		Direction:    db.ExchangeOutbound,
		Operation:    "stats_pull",
		StartedAfter: base.Format(time.RFC3339),
	})
	require.NoError(t, err)
	require.Len(t, combined.Body.Exchanges, 1)
	assert.Equal(t, "p3", combined.Body.Exchanges[0].PeerFingerprint)

	_, err = h.HumaListExchanges(context.Background(), &ListExchangesInput{StartedAfter: "not-a-time"})
	assert.Error(t, err)
}
