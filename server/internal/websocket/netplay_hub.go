package websocket

import (
	"encoding/json"
	"log/slog"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
	"github.com/spela/server/internal/db"
	"golang.org/x/time/rate"
	"gorm.io/gorm"
)

const (
	// maxNetplayMessageSize is the maximum allowed WebSocket frame size (64 KB).
	maxNetplayMessageSize = 64 * 1024
	// netplayMsgRateLimit is the maximum netplay messages per second per client.
	netplayMsgRateLimit = 60
	// netplayMsgBurst is the burst allowance for the per-client rate limiter.
	netplayMsgBurst = 120
)

// NetplayMessage represents a JSON message in a netplay session.
type NetplayMessage struct {
	Type     string          `json:"type"`     // signal_sdp, signal_ice, chat, state_update
	TargetID *uint           `json:"targetId"` // target user ID for signaling (nil = broadcast)
	Data     json.RawMessage `json:"data"`     // message-type-specific payload
}

// NetplayOutMessage is a message sent to a netplay client, including the sender ID.
type NetplayOutMessage struct {
	Type     string          `json:"type"`
	SenderID uint            `json:"senderId"`
	Data     json.RawMessage `json:"data"`
}

// netplayEnvelope wraps a message with its websocket message type.
type netplayEnvelope struct {
	messageType int
	data        []byte
}

// NetplayClient wraps a WebSocket connection in a netplay room.
type NetplayClient struct {
	Conn      *websocket.Conn
	UserID    uint
	SessionID uint
	Send      chan netplayEnvelope
}

// NetplayRoom holds all clients for a single netplay session.
type NetplayRoom struct {
	SessionID uint
	clients   map[uint]*NetplayClient // userID -> client
	mu        sync.Mutex
}

// NetplayHub manages netplay rooms, one per active session.
type NetplayHub struct {
	rooms    map[uint]*NetplayRoom // sessionID -> room
	mu       sync.Mutex
	upgrader websocket.Upgrader
}

// NewNetplayHub creates a new NetplayHub.
func NewNetplayHub(allowedOrigins []string) *NetplayHub {
	return &NetplayHub{
		rooms: make(map[uint]*NetplayRoom),
		upgrader: websocket.Upgrader{
			ReadBufferSize:  4096,
			WriteBufferSize: 4096,
			CheckOrigin: checkOrigin(allowedOrigins),
		},
	}
}

// HandleNetplayWebSocket upgrades the connection and joins the client to a room.
func (h *NetplayHub) HandleNetplayWebSocket(c *gin.Context, sessionID, userID uint) {
	conn, err := h.upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		slog.Error("netplay websocket upgrade failed", "error", err, "sessionId", sessionID, "userId", userID)
		return
	}

	client := &NetplayClient{
		Conn:      conn,
		UserID:    userID,
		SessionID: sessionID,
		Send:      make(chan netplayEnvelope, 256),
	}

	room := h.getOrCreateRoom(sessionID)
	room.addClient(client)

	slog.Info("netplay client connected", "sessionId", sessionID, "userId", userID)

	// Notify other clients in the room
	joinMsg, _ := json.Marshal(NetplayOutMessage{
		Type:     "peer_joined",
		SenderID: userID,
		Data:     nil,
	})
	room.broadcastExcept(joinMsg, userID, websocket.TextMessage)

	go client.netplayWritePump()
	go client.netplayReadPump(h, room)
}

// getOrCreateRoom returns the room for a session, creating it if needed.
func (h *NetplayHub) getOrCreateRoom(sessionID uint) *NetplayRoom {
	h.mu.Lock()
	defer h.mu.Unlock()

	room, ok := h.rooms[sessionID]
	if !ok {
		room = &NetplayRoom{
			SessionID: sessionID,
			clients:   make(map[uint]*NetplayClient),
		}
		h.rooms[sessionID] = room
		slog.Info("netplay room created", "sessionId", sessionID)
	}
	return room
}

// removeRoom deletes a room when it becomes empty.
func (h *NetplayHub) removeRoom(sessionID uint) {
	h.mu.Lock()
	defer h.mu.Unlock()
	delete(h.rooms, sessionID)
	slog.Info("netplay room removed", "sessionId", sessionID)
}

// StartCleanup runs a background goroutine that periodically marks stale waiting
// sessions as ended. A waiting session is stale if it was created more than 15
// minutes ago. The goroutine runs every 5 minutes.
func (h *NetplayHub) StartCleanup(database *gorm.DB) {
	go func() {
		ticker := time.NewTicker(5 * time.Minute)
		defer ticker.Stop()
		for range ticker.C {
			threshold := time.Now().Add(-15 * time.Minute)
			result := database.Model(&db.NetplaySession{}).
				Where("status = ? AND created_at < ?", "waiting", threshold).
				Updates(map[string]interface{}{
					"status":     "ended",
					"end_reason": "timeout",
					"ended_at":   time.Now(),
				})
			if result.RowsAffected > 0 {
				slog.Info("cleaned up stale netplay sessions", "count", result.RowsAffected)
			}
		}
	}()
}

// --- Room methods ---

func (r *NetplayRoom) addClient(client *NetplayClient) {
	r.mu.Lock()
	defer r.mu.Unlock()

	// Close existing connection for the same user (reconnection scenario)
	if existing, ok := r.clients[client.UserID]; ok {
		close(existing.Send)
		existing.Conn.Close()
	}

	r.clients[client.UserID] = client
}

func (r *NetplayRoom) removeClient(userID uint) int {
	r.mu.Lock()
	defer r.mu.Unlock()

	delete(r.clients, userID)
	return len(r.clients)
}

// broadcastExcept sends data to all clients except the one with excludeUID.
func (r *NetplayRoom) broadcastExcept(data []byte, excludeUID uint, msgType int) {
	r.mu.Lock()
	defer r.mu.Unlock()

	env := netplayEnvelope{messageType: msgType, data: data}
	for uid, client := range r.clients {
		if uid == excludeUID {
			continue
		}
		select {
		case client.Send <- env:
		default:
			// Client buffer full, drop message
			slog.Warn("netplay client send buffer full, dropping message", "userId", uid, "sessionId", r.SessionID)
		}
	}
}

// sendTo sends data to a specific user in the room.
func (r *NetplayRoom) sendTo(targetUID uint, data []byte, msgType int) {
	r.mu.Lock()
	defer r.mu.Unlock()

	if client, ok := r.clients[targetUID]; ok {
		select {
		case client.Send <- netplayEnvelope{messageType: msgType, data: data}:
		default:
			slog.Warn("netplay client send buffer full, dropping message", "userId", targetUID, "sessionId", r.SessionID)
		}
	}
}

// --- Client message pumps ---

const (
	netplayPongWait    = 60 * time.Second
	netplayPingInterval = 30 * time.Second
)

func (c *NetplayClient) netplayReadPump(hub *NetplayHub, room *NetplayRoom) {
	defer func() {
		remaining := room.removeClient(c.UserID)
		if remaining == 0 {
			hub.removeRoom(c.SessionID)
		} else {
			// Notify others that this peer left
			leaveMsg, _ := json.Marshal(NetplayOutMessage{
				Type:     "peer_left",
				SenderID: c.UserID,
				Data:     nil,
			})
			room.broadcastExcept(leaveMsg, c.UserID, websocket.TextMessage)
		}
		c.Conn.Close()
		slog.Info("netplay client disconnected", "sessionId", c.SessionID, "userId", c.UserID)
	}()

	c.Conn.SetReadLimit(maxNetplayMessageSize)
	c.Conn.SetReadDeadline(time.Now().Add(netplayPongWait))
	c.Conn.SetPongHandler(func(string) error {
		c.Conn.SetReadDeadline(time.Now().Add(netplayPongWait))
		return nil
	})
	limiter := rate.NewLimiter(rate.Limit(netplayMsgRateLimit), netplayMsgBurst)

	for {
		messageType, data, err := c.Conn.ReadMessage()
		if err != nil {
			break
		}

		// Drop messages that exceed the per-client rate limit
		if !limiter.Allow() {
			continue
		}

		if messageType == websocket.BinaryMessage {
			// Binary frames: forward as binary to all other clients in the room
			room.broadcastExcept(data, c.UserID, websocket.BinaryMessage)
			continue
		}

		// JSON text messages
		var msg NetplayMessage
		if err := json.Unmarshal(data, &msg); err != nil {
			slog.Warn("netplay: invalid JSON message", "userId", c.UserID, "error", err)
			continue
		}

		outMsg, _ := json.Marshal(NetplayOutMessage{
			Type:     msg.Type,
			SenderID: c.UserID,
			Data:     msg.Data,
		})

		switch msg.Type {
		case "signal_sdp", "signal_ice":
			// Signaling messages: forward to specific target
			if msg.TargetID != nil {
				room.sendTo(*msg.TargetID, outMsg, websocket.TextMessage)
			}
		default:
			// All other messages: broadcast to everyone else
			room.broadcastExcept(outMsg, c.UserID, websocket.TextMessage)
		}
	}
}

func (c *NetplayClient) netplayWritePump() {
	ticker := time.NewTicker(netplayPingInterval)
	defer func() {
		ticker.Stop()
		c.Conn.Close()
	}()
	for {
		select {
		case env, ok := <-c.Send:
			if !ok {
				c.Conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			if err := c.Conn.WriteMessage(env.messageType, env.data); err != nil {
				return
			}
		case <-ticker.C:
			c.Conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if err := c.Conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}
