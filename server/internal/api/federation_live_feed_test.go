package api

import (
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

type fakeFederationHub struct {
	events []ws.Event
}

func (f *fakeFederationHub) PlayingNow() []ws.PlayingSession { return nil }

func (f *fakeFederationHub) TryBroadcast(event ws.Event) bool {
	f.events = append(f.events, event)
	return true
}

func TestRecordExchange_BroadcastsAdminOnlyLiveEvents(t *testing.T) {
	database := openAPIFedTestDB(t)
	owner := createFederationLiveFeedUser(t, database, "owner", db.RoleOwner, false, false)
	admin := createFederationLiveFeedUser(t, database, "admin", db.RoleAdmin, false, false)
	regular := createFederationLiveFeedUser(t, database, "user", db.RoleUser, false, false)
	disabledAdmin := createFederationLiveFeedUser(t, database, "disabled", db.RoleAdmin, true, false)
	pendingAdmin := createFederationLiveFeedUser(t, database, "pending", db.RoleAdmin, false, true)

	store := federation.PeerStore{DB: database}
	require.NoError(t, store.Upsert(&db.FederationPeer{
		Fingerprint: "fp-live", PublicKey: "k", Name: "Friend", BaseURL: "https://friend",
		Status: db.PeerStatusActive,
	}))
	hub := &fakeFederationHub{}
	h := &FederationHandler{DB: database, Hub: hub}

	result := h.recordExchange(federation.ExchangeRecord{
		RequestID: "req-live", PeerFingerprint: "fp-live", PeerName: "Friend",
		Direction: db.ExchangeOutbound, Operation: "stats_pull",
		DataClass: string(federation.DataClassStats), Status: db.ExchangeOK, ItemCount: 3,
	})

	require.True(t, result.ExchangePersisted)
	require.True(t, result.PeerUpdated)
	require.Len(t, hub.events, 2)

	exchangeEvent := hub.events[0]
	assert.Equal(t, ws.EventFederationExchange, exchangeEvent.Type)
	assert.ElementsMatch(t, []uint{owner.ID, admin.ID}, exchangeEvent.RecipientUserIDs)
	assert.NotContains(t, exchangeEvent.RecipientUserIDs, regular.ID)
	assert.NotContains(t, exchangeEvent.RecipientUserIDs, disabledAdmin.ID)
	assert.NotContains(t, exchangeEvent.RecipientUserIDs, pendingAdmin.ID)
	exchangePayload, ok := exchangeEvent.Payload.(ws.FederationExchangePayload)
	require.True(t, ok)
	assert.Equal(t, "req-live", exchangePayload.RequestID)
	assert.Equal(t, "fp-live", exchangePayload.PeerFingerprint)
	assert.Equal(t, "stats_pull", exchangePayload.Operation)
	assert.Equal(t, db.ExchangeOK, exchangePayload.Status)
	assert.Equal(t, 3, exchangePayload.ItemCount)

	peerEvent := hub.events[1]
	assert.Equal(t, ws.EventFederationPeerStatus, peerEvent.Type)
	assert.ElementsMatch(t, []uint{owner.ID, admin.ID}, peerEvent.RecipientUserIDs)
	peerPayload, ok := peerEvent.Payload.(ws.FederationPeerStatusPayload)
	require.True(t, ok)
	assert.Equal(t, "fp-live", peerPayload.Fingerprint)
	assert.Equal(t, "Friend", peerPayload.Name)
	assert.Equal(t, db.PeerStatusActive, peerPayload.Status)
	assert.True(t, peerPayload.Reachable)
	require.NotNil(t, peerPayload.LastContactAt)
	require.NotNil(t, peerPayload.LastSuccessAt)
}

func TestRecordExchange_LiveFeedSkipsMissingHub(t *testing.T) {
	database := openAPIFedTestDB(t)
	h := &FederationHandler{DB: database}

	result := h.recordExchange(federation.ExchangeRecord{
		RequestID: "req-no-hub", PeerFingerprint: "fp-missing",
		Direction: db.ExchangeOutbound, Operation: "stats_pull", Status: db.ExchangeOK,
	})

	assert.True(t, result.ExchangePersisted)
	assert.False(t, result.PeerUpdated)
}

func TestRecordExchange_LiveFeedEmitsOnlyExchangeWithoutPeerHealth(t *testing.T) {
	database := openAPIFedTestDB(t)
	owner := createFederationLiveFeedUser(t, database, "owner", db.RoleOwner, false, false)
	hub := &fakeFederationHub{}
	h := &FederationHandler{DB: database, Hub: hub}

	result := h.recordExchange(federation.ExchangeRecord{
		RequestID: "req-no-peer", PeerFingerprint: "fp-missing",
		Direction: db.ExchangeOutbound, Operation: "stats_pull", Status: db.ExchangeOK,
	})

	assert.True(t, result.ExchangePersisted)
	assert.False(t, result.PeerUpdated)
	require.Len(t, hub.events, 1)
	assert.Equal(t, ws.EventFederationExchange, hub.events[0].Type)
	assert.Equal(t, []uint{owner.ID}, hub.events[0].RecipientUserIDs)
}

func createFederationLiveFeedUser(t *testing.T, database *gorm.DB, username string, role db.UserRole, disabled, pending bool) db.User {
	t.Helper()
	user := db.User{
		Username:        username,
		PasswordHash:    "hash",
		Role:            role,
		Disabled:        disabled,
		PendingApproval: pending,
	}
	require.NoError(t, database.Create(&user).Error)
	return user
}
