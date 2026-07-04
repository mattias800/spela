package websocket

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
)

// Event represents a real-time event sent to clients.
//
// RecipientUserIDs filters delivery to a specific subset of connected
// clients (issue #1119). When non-nil and non-empty, the broadcast loop
// only delivers to clients whose UserID is in the list. nil/empty means
// "deliver to everyone" — preserving the historical fan-out semantics
// for genuinely server-wide events (scrape progress, BIOS download
// progress, library scan progress, online-status broadcasts).
//
// The recipient list is intentionally not serialised to clients —
// `json:"-"` keeps it out of the wire payload.
type Event struct {
	Type             string      `json:"type"`
	Payload          interface{} `json:"payload"`
	RecipientUserIDs []uint      `json:"-"`
}

// OnlineUser represents a user currently connected via WebSocket.
type OnlineUser struct {
	UserID      uint
	Username    string
	CurrentGame uint // 0 means not playing
}

// userGameEntry stores the game a user is playing and when the last heartbeat arrived.
type userGameEntry struct {
	GameID        uint
	LastHeartbeat time.Time
}

// Hub manages WebSocket connections and broadcasts events.
type Hub struct {
	clients    map[*Client]bool
	broadcast  chan Event
	register   chan *Client
	unregister chan *Client
	mu         sync.Mutex
	upgrader   websocket.Upgrader
	userGames  map[uint]userGameEntry // userID -> game entry
	nowFunc    func() time.Time       // for testing; defaults to time.Now
	done       chan struct{}
}

// Client represents a single WebSocket connection.
type Client struct {
	Hub    *Hub
	Conn   *websocket.Conn
	Send   chan []byte
	UserID uint
}

// NewHub creates a new WebSocket hub. allowedOrigins controls which
// origins are accepted for WebSocket upgrades. An empty slice rejects all
// cross-origin requests (secure default, consistent with CORS behaviour).
// heartbeatTimeout is how long a user's game status persists without a heartbeat.
// This is 3x the 30-second heartbeat interval, giving room for network jitter.
const heartbeatTimeout = 90 * time.Second

// heartbeatCleanupInterval is how often the hub checks for stale game entries.
const heartbeatCleanupInterval = 30 * time.Second

func NewHub(allowedOrigins []string) *Hub {
	h := &Hub{
		clients:    make(map[*Client]bool),
		broadcast:  make(chan Event, 256),
		register:   make(chan *Client),
		unregister: make(chan *Client),
		userGames:  make(map[uint]userGameEntry),
		nowFunc:    time.Now,
		done:       make(chan struct{}),
	}
	h.upgrader = websocket.Upgrader{
		ReadBufferSize:  1024,
		WriteBufferSize: 1024,
		CheckOrigin:     checkOrigin(allowedOrigins),
	}
	return h
}

// checkOrigin returns an origin checker for WebSocket upgrades. An empty
// allowedOrigins list only allows same-origin requests (secure default).
//
// Issue #1126: a `*` entry in allowedOrigins used to bless any cross-origin
// upgrade, including those carrying ?token=<jwt>. Combined with the
// query-token fallback (#1117), an attacker who got hold of a token
// could open an authenticated WS from any origin. Any request that looks
// credentialed (Origin header present + ?token= query param) now ignores
// the wildcard and falls back to a strict same-origin check; operators
// who genuinely want "open" must enumerate origins explicitly. Plain
// HTTP-style wildcard (no credentials) still works for unauthenticated
// upgrades.
func checkOrigin(allowedOrigins []string) func(*http.Request) bool {
	return func(r *http.Request) bool {
		origin := r.Header.Get("Origin")

		// No Origin header means same-origin request — always allow.
		if origin == "" {
			return true
		}

		isCredentialed := r.URL != nil && r.URL.Query().Get("token") != ""

		// No configured origins: fall back to gorilla/websocket's default
		// behaviour — allow only if the Origin host matches the request Host.
		if len(allowedOrigins) == 0 {
			u, err := url.Parse(origin)
			if err != nil {
				return false
			}
			return strings.EqualFold(u.Host, r.Host)
		}

		for _, allowed := range allowedOrigins {
			if allowed == "*" {
				// Refuse cross-origin upgrades that authenticate via
				// the query token under a wildcard policy. Plain
				// (header-authed or unauthenticated) upgrades still
				// honour the wildcard.
				if isCredentialed {
					u, err := url.Parse(origin)
					if err != nil {
						return false
					}
					if !strings.EqualFold(u.Host, r.Host) {
						return false
					}
				}
				return true
			}
			if allowed == origin {
				return true
			}
		}
		return false
	}
}

// Run starts the hub event loop. Call this in a goroutine.
// Call Close() to stop the loop.
func (h *Hub) Run() {
	go h.cleanupStaleGames()

	for {
		select {
		case <-h.done:
			return
		case client := <-h.register:
			h.mu.Lock()
			h.clients[client] = true
			userID := client.UserID
			h.mu.Unlock()
			slog.Info("websocket client connected", "userId", userID)
			h.Broadcast(Event{Type: EventOnlineStatus, Payload: OnlineStatusPayload{UserID: userID, Status: "online"}})

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
				h.Broadcast(Event{Type: EventOnlineStatus, Payload: OnlineStatusPayload{UserID: userID, Status: "offline"}})
			}

		case event := <-h.broadcast:
			data, err := json.Marshal(event)
			if err != nil {
				slog.Error("failed to marshal websocket event", "error", err)
				continue
			}
			// Build a recipient set if the event is targeted; otherwise
			// fan out to every connected client (issue #1119).
			var recipientSet map[uint]struct{}
			if len(event.RecipientUserIDs) > 0 {
				recipientSet = make(map[uint]struct{}, len(event.RecipientUserIDs))
				for _, uid := range event.RecipientUserIDs {
					recipientSet[uid] = struct{}{}
				}
			}
			h.mu.Lock()
			for client := range h.clients {
				if recipientSet != nil {
					if _, ok := recipientSet[client.UserID]; !ok {
						continue
					}
				}
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

// TryBroadcast sends an event without blocking. It returns false when the hub
// queue is full or closed enough that callers should drop best-effort live
// observability events instead of delaying the request path.
func (h *Hub) TryBroadcast(event Event) bool {
	select {
	case <-h.done:
		return false
	default:
	}
	select {
	case h.broadcast <- event:
		return true
	default:
		return false
	}
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
		h.userGames[userID] = userGameEntry{
			GameID:        gameID,
			LastHeartbeat: h.nowFunc(),
		}
	}
}

// GetUserGame returns the game ID a user is currently playing (0 if not playing).
func (h *Hub) GetUserGame(userID uint) uint {
	h.mu.Lock()
	defer h.mu.Unlock()
	entry, ok := h.userGames[userID]
	if !ok {
		return 0
	}
	return entry.GameID
}

// PlayingSession is one user's currently-active game session.
type PlayingSession struct {
	UserID uint
	GameID uint
}

// PlayingNow returns a snapshot of the users currently playing a game, skipping
// entries with no game and those whose heartbeat has gone stale (the same
// timeout the cleanup goroutine uses, applied live so a caller never sees a
// session the cleanup tick hasn't swept yet). Powers cross-mesh presence (#1349).
func (h *Hub) PlayingNow() []PlayingSession {
	h.mu.Lock()
	defer h.mu.Unlock()
	now := h.nowFunc()
	out := make([]PlayingSession, 0, len(h.userGames))
	for userID, entry := range h.userGames {
		if entry.GameID == 0 {
			continue
		}
		if now.Sub(entry.LastHeartbeat) > heartbeatTimeout {
			continue
		}
		out = append(out, PlayingSession{UserID: userID, GameID: entry.GameID})
	}
	return out
}

// Close stops the hub event loop and cleanup goroutine.
func (h *Hub) Close() {
	select {
	case <-h.done:
	default:
		close(h.done)
	}
}

// cleanupStaleGames periodically removes game entries whose heartbeat has expired.
func (h *Hub) cleanupStaleGames() {
	ticker := time.NewTicker(heartbeatCleanupInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
			h.expireStaleGames()
		case <-h.done:
			return
		}
	}
}

// expireStaleGames removes game entries that haven't received a heartbeat within
// the timeout window. Separated from cleanupStaleGames for testability.
func (h *Hub) expireStaleGames() {
	now := h.nowFunc()
	h.mu.Lock()
	defer h.mu.Unlock()
	for userID, entry := range h.userGames {
		if now.Sub(entry.LastHeartbeat) > heartbeatTimeout {
			slog.Info("clearing stale game status", "userId", userID, "gameId", entry.GameID)
			delete(h.userGames, userID)
		}
	}
}

// maxConnectionsPerUser is the maximum number of concurrent WebSocket connections
// allowed per user. This prevents a single compromised account from exhausting
// server resources by opening many connections.
const maxConnectionsPerUser = 10

// HandleWebSocket upgrades an HTTP request to a WebSocket connection.
func (h *Hub) HandleWebSocket(c *gin.Context) {
	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)

	// Enforce per-user connection limit
	h.mu.Lock()
	count := 0
	for client := range h.clients {
		if client.UserID == uid {
			count++
		}
	}
	h.mu.Unlock()
	if count >= maxConnectionsPerUser {
		c.JSON(429, gin.H{"error": "too many WebSocket connections"})
		return
	}

	conn, err := h.upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		slog.Error("websocket upgrade failed", "error", err)
		return
	}

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

const (
	// maxHubMessageSize is the maximum allowed WebSocket frame size for the
	// event hub. Since the hub discards all incoming messages, this limit exists
	// purely to prevent a malicious client from consuming excessive memory.
	maxHubMessageSize = 4096

	// pongWait is how long the server waits for a pong response before
	// considering the connection dead.
	pongWait = 60 * time.Second

	// pingInterval is how often the server sends pings. Must be less than pongWait.
	pingInterval = 30 * time.Second
)

func (c *Client) readPump() {
	defer func() {
		c.Hub.unregister <- c
		c.Conn.Close()
	}()
	c.Conn.SetReadLimit(maxHubMessageSize)
	c.Conn.SetReadDeadline(time.Now().Add(pongWait))
	c.Conn.SetPongHandler(func(string) error {
		c.Conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})
	for {
		_, _, err := c.Conn.ReadMessage()
		if err != nil {
			break
		}
		// We don't process incoming messages currently; just keep the connection alive
	}
}

func (c *Client) writePump() {
	ticker := time.NewTicker(pingInterval)
	defer func() {
		ticker.Stop()
		c.Conn.Close()
	}()
	for {
		select {
		case msg, ok := <-c.Send:
			if !ok {
				c.Conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			if err := c.Conn.WriteMessage(websocket.TextMessage, msg); err != nil {
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
