package api

import (
	"log/slog"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	ws "github.com/spela/server/internal/websocket"
)

func (h *FederationHandler) recordExchange(rec federation.ExchangeRecord) federation.ExchangeResult {
	result := federation.RecordExchange(h.DB, rec)
	if h.Hub == nil {
		return result
	}
	if !result.ExchangePersisted && !result.PeerUpdated {
		return result
	}

	recipients := h.federationAdminRecipientIDs()
	if len(recipients) == 0 {
		return result
	}
	if result.ExchangePersisted {
		h.tryBroadcastFederationEvent(ws.Event{
			Type:             ws.EventFederationExchange,
			Payload:          toFederationExchangePayload(result.Exchange),
			RecipientUserIDs: recipients,
		})
	}
	if result.PeerUpdated {
		h.tryBroadcastFederationEvent(ws.Event{
			Type:             ws.EventFederationPeerStatus,
			Payload:          toFederationPeerStatusPayload(result.Peer),
			RecipientUserIDs: recipients,
		})
	}
	return result
}

func (h *FederationHandler) federationAdminRecipientIDs() []uint {
	var ids []uint
	if err := h.DB.Model(&db.User{}).
		Where("role IN ? AND disabled = ? AND pending_approval = ?", []db.UserRole{db.RoleOwner, db.RoleAdmin}, false, false).
		Pluck("id", &ids).Error; err != nil {
		slog.Warn("federation: failed to resolve live-feed admin recipients", "component", "federation", "error", err)
		return nil
	}
	return ids
}

func (h *FederationHandler) tryBroadcastFederationEvent(event ws.Event) {
	if ok := h.Hub.TryBroadcast(event); !ok {
		slog.Warn("federation: dropped live admin websocket event", "component", "federation", "event", event.Type)
	}
}

func toFederationExchangePayload(exchange db.FederationExchange) ws.FederationExchangePayload {
	return ws.FederationExchangePayload{
		ID:              exchange.ID,
		RequestID:       exchange.RequestID,
		PeerFingerprint: exchange.PeerFingerprint,
		PeerName:        exchange.PeerName,
		Direction:       exchange.Direction,
		Operation:       exchange.Operation,
		DataClass:       exchange.DataClass,
		MaxHops:         exchange.MaxHops,
		Status:          exchange.Status,
		HTTPStatus:      exchange.HTTPStatus,
		ItemCount:       exchange.ItemCount,
		Bytes:           exchange.Bytes,
		DurationMs:      exchange.DurationMs,
		StartedAt:       exchange.StartedAt,
		FinishedAt:      exchange.FinishedAt,
		Error:           exchange.Error,
	}
}

func toFederationPeerStatusPayload(peer db.FederationPeer) ws.FederationPeerStatusPayload {
	return ws.FederationPeerStatusPayload{
		ID:            peer.ID,
		Fingerprint:   peer.Fingerprint,
		Name:          peer.Name,
		Status:        peer.Status,
		Reachable:     peer.Reachable,
		LastContactAt: peer.LastContactAt,
		LastSuccessAt: peer.LastSuccessAt,
		LastError:     peer.LastError,
		LastErrorAt:   peer.LastErrorAt,
		UpdatedAt:     peer.UpdatedAt,
	}
}
