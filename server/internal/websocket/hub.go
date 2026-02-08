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

// Hub manages WebSocket connections and broadcasts events.
type Hub struct {
	clients    map[*Client]bool
	broadcast  chan Event
	register   chan *Client
	unregister chan *Client
	mu         sync.RWMutex
}

// Client represents a single WebSocket connection.
type Client struct {
	Hub    *Hub
	Conn   *websocket.Conn
	Send   chan []byte
	UserID uint
}

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		return true // Allow all origins in development; restrict in production via CORS
	},
}

// NewHub creates a new WebSocket hub.
func NewHub() *Hub {
	return &Hub{
		clients:    make(map[*Client]bool),
		broadcast:  make(chan Event, 256),
		register:   make(chan *Client),
		unregister: make(chan *Client),
	}
}

// Run starts the hub event loop. Call this in a goroutine.
func (h *Hub) Run() {
	for {
		select {
		case client := <-h.register:
			h.mu.Lock()
			h.clients[client] = true
			h.mu.Unlock()
			slog.Info("websocket client connected", "userId", client.UserID)

		case client := <-h.unregister:
			h.mu.Lock()
			if _, ok := h.clients[client]; ok {
				delete(h.clients, client)
				close(client.Send)
			}
			h.mu.Unlock()
			slog.Info("websocket client disconnected", "userId", client.UserID)

		case event := <-h.broadcast:
			data, err := json.Marshal(event)
			if err != nil {
				slog.Error("failed to marshal websocket event", "error", err)
				continue
			}
			h.mu.RLock()
			for client := range h.clients {
				select {
				case client.Send <- data:
				default:
					close(client.Send)
					delete(h.clients, client)
				}
			}
			h.mu.RUnlock()
		}
	}
}

// Broadcast sends an event to all connected clients.
func (h *Hub) Broadcast(event Event) {
	h.broadcast <- event
}

// HandleWebSocket upgrades an HTTP request to a WebSocket connection.
func (h *Hub) HandleWebSocket(c *gin.Context) {
	conn, err := upgrader.Upgrade(c.Writer, c.Request, nil)
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

func (c *Client) readPump() {
	defer func() {
		c.Hub.unregister <- c
		c.Conn.Close()
	}()
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
