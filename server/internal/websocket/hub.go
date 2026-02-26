package websocket

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"sync"

	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
)

// Event represents a real-time event sent to clients.
type Event struct {
	Type    string      `json:"type"`
	Payload interface{} `json:"payload"`
}

// OnlineUser represents a user currently connected via WebSocket.
type OnlineUser struct {
	UserID      uint
	Username    string
	CurrentGame uint // 0 means not playing
}

// Hub manages WebSocket connections and broadcasts events.
type Hub struct {
	clients    map[*Client]bool
	broadcast  chan Event
	register   chan *Client
	unregister chan *Client
	mu         sync.Mutex
	upgrader   websocket.Upgrader
	userGames  map[uint]uint // userID -> gameID (0 = not playing)
}

// Client represents a single WebSocket connection.
type Client struct {
	Hub    *Hub
	Conn   *websocket.Conn
	Send   chan []byte
	UserID uint
}

// NewHub creates a new WebSocket hub. allowedOrigins controls which
// origins are accepted for WebSocket upgrades. An empty slice allows all.
func NewHub(allowedOrigins []string) *Hub {
	h := &Hub{
		clients:    make(map[*Client]bool),
		broadcast:  make(chan Event, 256),
		register:   make(chan *Client),
		unregister: make(chan *Client),
		userGames:  make(map[uint]uint),
	}
	h.upgrader = websocket.Upgrader{
		ReadBufferSize:  1024,
		WriteBufferSize: 1024,
		CheckOrigin: func(r *http.Request) bool {
			if len(allowedOrigins) == 0 {
				return true
			}
			origin := r.Header.Get("Origin")
			for _, allowed := range allowedOrigins {
				if allowed == "*" || allowed == origin {
					return true
				}
			}
			return false
		},
	}
	return h
}

// Run starts the hub event loop. Call this in a goroutine.
func (h *Hub) Run() {
	for {
		select {
		case client := <-h.register:
			h.mu.Lock()
			h.clients[client] = true
			userID := client.UserID
			h.mu.Unlock()
			slog.Info("websocket client connected", "userId", userID)
			h.Broadcast(Event{Type: "online_status", Payload: map[string]interface{}{"userId": userID, "status": "online"}})

		case client := <-h.unregister:
			var broadcastOffline bool
			var userID uint
			h.mu.Lock()
			if _, ok := h.clients[client]; ok {
				delete(h.clients, client)
				close(client.Send)
				userID = client.UserID
				// Clean up user game tracking if no other connections for this user
				if !h.hasOtherConnection(userID, client) {
					delete(h.userGames, userID)
					broadcastOffline = true
				}
			}
			h.mu.Unlock()
			slog.Info("websocket client disconnected", "userId", userID)
			if broadcastOffline {
				h.Broadcast(Event{Type: "online_status", Payload: map[string]interface{}{"userId": userID, "status": "offline"}})
			}

		case event := <-h.broadcast:
			data, err := json.Marshal(event)
			if err != nil {
				slog.Error("failed to marshal websocket event", "error", err)
				continue
			}
			h.mu.Lock()
			for client := range h.clients {
				select {
				case client.Send <- data:
				default:
					close(client.Send)
					delete(h.clients, client)
				}
			}
			h.mu.Unlock()
		}
	}
}

// Broadcast sends an event to all connected clients.
func (h *Hub) Broadcast(event Event) {
	h.broadcast <- event
}

// hasOtherConnection checks if the user has another active WebSocket connection
// besides the one being removed. Must be called with h.mu held.
func (h *Hub) hasOtherConnection(userID uint, exclude *Client) bool {
	for client := range h.clients {
		if client != exclude && client.UserID == userID {
			return true
		}
	}
	return false
}

// GetOnlineUserIDs returns a list of unique user IDs currently connected.
func (h *Hub) GetOnlineUserIDs() []uint {
	h.mu.Lock()
	defer h.mu.Unlock()

	seen := make(map[uint]bool)
	var ids []uint
	for client := range h.clients {
		if client.UserID != 0 && !seen[client.UserID] {
			seen[client.UserID] = true
			ids = append(ids, client.UserID)
		}
	}
	return ids
}

// SetUserGame marks a user as currently playing a game. Pass 0 to clear.
func (h *Hub) SetUserGame(userID, gameID uint) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if gameID == 0 {
		delete(h.userGames, userID)
	} else {
		h.userGames[userID] = gameID
	}
}

// GetUserGame returns the game ID a user is currently playing (0 if not playing).
func (h *Hub) GetUserGame(userID uint) uint {
	h.mu.Lock()
	defer h.mu.Unlock()
	return h.userGames[userID]
}

// HandleWebSocket upgrades an HTTP request to a WebSocket connection.
func (h *Hub) HandleWebSocket(c *gin.Context) {
	conn, err := h.upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		slog.Error("websocket upgrade failed", "error", err)
		return
	}

	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)

	client := &Client{
		Hub:    h,
		Conn:   conn,
		Send:   make(chan []byte, 256),
		UserID: uid,
	}

	h.register <- client

	go client.writePump()
	go client.readPump()
}

// maxHubMessageSize is the maximum allowed WebSocket frame size for the
// event hub. Since the hub discards all incoming messages, this limit exists
// purely to prevent a malicious client from consuming excessive memory.
const maxHubMessageSize = 4096

func (c *Client) readPump() {
	defer func() {
		c.Hub.unregister <- c
		c.Conn.Close()
	}()
	c.Conn.SetReadLimit(maxHubMessageSize)
	for {
		_, _, err := c.Conn.ReadMessage()
		if err != nil {
			break
		}
		// We don't process incoming messages currently; just keep the connection alive
	}
}

func (c *Client) writePump() {
	defer c.Conn.Close()
	for msg := range c.Send {
		if err := c.Conn.WriteMessage(websocket.TextMessage, msg); err != nil {
			break
		}
	}
}
