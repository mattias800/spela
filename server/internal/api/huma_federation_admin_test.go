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

// fakePairClient returns a canned bundle from the remote friend.
type fakePairClient struct {
	remote federation.Identity
	called bool
}

func (f *fakePairClient) Pair(baseURL string, _ PairRequestBody) (PairResponseBody, error) {
	f.called = true
	return PairResponseBody{
		Fingerprint: f.remote.Fingerprint(),
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

func TestRevokePeer_RemovesIt(t *testing.T) {
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()
	store := federation.PeerStore{DB: database}
	require.NoError(t, store.Upsert(&db.FederationPeer{
		Fingerprint: "fp-x", PublicKey: "k", BaseURL: "https://x", Status: db.PeerStatusActive,
	}))
	h := &FederationHandler{DB: database, Identity: selfID, Peers: store, BaseURL: "https://self"}

	_, err := h.HumaRevokePeer(context.Background(), &RevokePeerInput{Fingerprint: "fp-x"})
	require.NoError(t, err)
	_, err = store.GetByFingerprint("fp-x")
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

	federation.RecordExchange(database, federation.ExchangeRecord{
		RequestID: "a", PeerFingerprint: "p1", Direction: db.ExchangeOutbound, Operation: "ping", Status: db.ExchangeOK,
	})
	federation.RecordExchange(database, federation.ExchangeRecord{
		RequestID: "b", PeerFingerprint: "p2", Direction: db.ExchangeInbound, Operation: "pair", Status: db.ExchangeRejected,
	})

	all, err := h.HumaListExchanges(context.Background(), &ListExchangesInput{})
	require.NoError(t, err)
	assert.Len(t, all.Body.Exchanges, 2)

	rejected, err := h.HumaListExchanges(context.Background(), &ListExchangesInput{Status: db.ExchangeRejected})
	require.NoError(t, err)
	require.Len(t, rejected.Body.Exchanges, 1)
	assert.Equal(t, "p2", rejected.Body.Exchanges[0].PeerFingerprint)
}
