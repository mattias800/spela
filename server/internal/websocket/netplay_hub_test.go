package websocket

import (
	"testing"

	"github.com/gorilla/websocket"
	"github.com/stretchr/testify/assert"
)

func TestNewNetplayHub(t *testing.T) {
	hub := NewNetplayHub(nil)
	assert.NotNil(t, hub)
	assert.NotNil(t, hub.rooms)
}

// Issue #1328: once the global room cap is reached, a connection for a NEW
// session is refused, but joining an existing room is still allowed.
func TestNetplayHub_RoomCapacity(t *testing.T) {
	hub := NewNetplayHub(nil)
	for i := uint(1); i <= maxNetplayRooms; i++ {
		hub.getOrCreateRoom(i)
	}
	assert.False(t, hub.hasCapacityFor(maxNetplayRooms+1), "new room beyond cap must be rejected")
	assert.True(t, hub.hasCapacityFor(1), "joining an existing room must still be allowed")
}

func TestNetplayHub_GetOrCreateRoom(t *testing.T) {
	hub := NewNetplayHub(nil)

	room1 := hub.getOrCreateRoom(1)
	assert.NotNil(t, room1)
	assert.Equal(t, uint(1), room1.SessionID)

	// Same session ID should return the same room
	room1b := hub.getOrCreateRoom(1)
	assert.Equal(t, room1, room1b)

	// Different session ID should create a new room
	room2 := hub.getOrCreateRoom(2)
	assert.NotEqual(t, room1, room2)
	assert.Equal(t, uint(2), room2.SessionID)
}

func TestNetplayHub_RemoveRoom(t *testing.T) {
	hub := NewNetplayHub(nil)

	hub.getOrCreateRoom(1)
	assert.Len(t, hub.rooms, 1)

	hub.removeRoom(1)
	assert.Len(t, hub.rooms, 0)
}

func TestNetplayRoom_AddRemoveClient(t *testing.T) {
	room := &NetplayRoom{
		SessionID: 1,
		clients:   make(map[uint]*NetplayClient),
	}

	client := &NetplayClient{
		UserID:    10,
		SessionID: 1,
		Send:      make(chan netplayEnvelope, 256),
	}

	room.addClient(client)
	assert.Len(t, room.clients, 1)

	remaining := room.removeClient(10)
	assert.Equal(t, 0, remaining)
	assert.Len(t, room.clients, 0)
}

func TestNetplayRoom_BroadcastExcept(t *testing.T) {
	room := &NetplayRoom{
		SessionID: 1,
		clients:   make(map[uint]*NetplayClient),
	}

	c1 := &NetplayClient{UserID: 1, SessionID: 1, Send: make(chan netplayEnvelope, 256)}
	c2 := &NetplayClient{UserID: 2, SessionID: 1, Send: make(chan netplayEnvelope, 256)}
	c3 := &NetplayClient{UserID: 3, SessionID: 1, Send: make(chan netplayEnvelope, 256)}

	room.addClient(c1)
	room.addClient(c2)
	room.addClient(c3)

	msg := []byte(`{"type":"test"}`)
	room.broadcastExcept(msg, 1, websocket.TextMessage)

	// c1 should NOT receive (excluded)
	assert.Len(t, c1.Send, 0)
	// c2 and c3 should receive
	assert.Len(t, c2.Send, 1)
	assert.Len(t, c3.Send, 1)

	// Verify message type is preserved
	env := <-c2.Send
	assert.Equal(t, websocket.TextMessage, env.messageType)
	assert.Equal(t, msg, env.data)
}

func TestNetplayRoom_BroadcastExcept_Binary(t *testing.T) {
	room := &NetplayRoom{
		SessionID: 1,
		clients:   make(map[uint]*NetplayClient),
	}

	c1 := &NetplayClient{UserID: 1, SessionID: 1, Send: make(chan netplayEnvelope, 256)}
	c2 := &NetplayClient{UserID: 2, SessionID: 1, Send: make(chan netplayEnvelope, 256)}

	room.addClient(c1)
	room.addClient(c2)

	binaryData := []byte{0x01, 0x02, 0x03, 0x04}
	room.broadcastExcept(binaryData, 1, websocket.BinaryMessage)

	assert.Len(t, c1.Send, 0)
	assert.Len(t, c2.Send, 1)

	env := <-c2.Send
	assert.Equal(t, websocket.BinaryMessage, env.messageType)
	assert.Equal(t, binaryData, env.data)
}

func TestNetplayRoom_SendTo(t *testing.T) {
	room := &NetplayRoom{
		SessionID: 1,
		clients:   make(map[uint]*NetplayClient),
	}

	c1 := &NetplayClient{UserID: 1, SessionID: 1, Send: make(chan netplayEnvelope, 256)}
	c2 := &NetplayClient{UserID: 2, SessionID: 1, Send: make(chan netplayEnvelope, 256)}

	room.addClient(c1)
	room.addClient(c2)

	msg := []byte(`{"type":"signal"}`)
	room.sendTo(2, msg, websocket.TextMessage)

	assert.Len(t, c1.Send, 0)
	assert.Len(t, c2.Send, 1)

	env := <-c2.Send
	assert.Equal(t, websocket.TextMessage, env.messageType)

	// Send to non-existent user should not panic
	room.sendTo(999, msg, websocket.TextMessage)
}
