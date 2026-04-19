package api

import (
	"encoding/json"
	"log/slog"
	"strconv"
	"strings"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// SocialHandler handles activity feed and online status endpoints.
type SocialHandler struct {
	DB  *gorm.DB
	Hub *ws.Hub
}

func toPublicProfileGame(g db.Game, playTime int64) PublicProfileGame {
	coverURL := g.CoverURL
	if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
		coverURL = "/api/images/" + coverURL
	}
	consoleName := ""
	if g.Console.ID != 0 {
		consoleName = g.Console.Name
	}
	return PublicProfileGame{
		ID:          strconv.FormatUint(uint64(g.ID), 10),
		Title:       g.Title,
		CoverURL:    coverURL,
		ConsoleName: consoleName,
		PlayTime:    playTime,
	}
}

// CreateActivityEvent creates a new activity event and broadcasts it via WebSocket.
// metadata should be one of the typed *Metadata structs defined in
// activity_event_metadata.go (e.g. StartedPlayingMetadata, RatedGameMetadata)
// so the call site is compile-checked against the expected shape. Pass nil
// for events that carry no metadata. The wire format is the JSON-encoded
// struct — unchanged from the prior map[string]interface{} representation.
func CreateActivityEvent(database *gorm.DB, hub *ws.Hub, userID uint, eventType string, gameID uint, metadata any) {
	metadataJSON := ""
	// metadataMap is the broadcast-response shape (matches the persisted JSON
	// after round-tripping through the DB). We marshal once for the DB blob,
	// then unmarshal back into a map so the in-memory WebSocket broadcast
	// mirrors what subsequent GETs will return.
	var metadataMap map[string]interface{}
	if metadata != nil {
		b, err := json.Marshal(metadata)
		if err == nil {
			metadataJSON = string(b)
			_ = json.Unmarshal(b, &metadataMap)
		}
	}

	event := db.ActivityEvent{
		UserID:    userID,
		EventType: eventType,
		GameID:    gameID,
		Metadata:  metadataJSON,
	}

	if err := database.Create(&event).Error; err != nil {
		slog.Error("failed to create activity event", "error", err, "eventType", eventType, "userId", userID)
		return
	}

	// Load user and game info for the broadcast
	var user db.User
	database.First(&user, userID)
	var game db.Game
	database.Preload("Console").First(&game, gameID)

	coverURL := game.CoverURL
	if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
		coverURL = "/api/images/" + coverURL
	}

	consoleName := ""
	if game.Console.ID != 0 {
		consoleName = game.Console.Name
	}

	resp := ActivityEventResponse{
		ID:           strconv.FormatUint(uint64(event.ID), 10),
		EventType:    eventType,
		CreatedAt:    event.CreatedAt,
		UserID:       strconv.FormatUint(uint64(userID), 10),
		Username:     user.Username,
		AvatarURL:    user.AvatarURL,
		GameID:       strconv.FormatUint(uint64(gameID), 10),
		GameTitle:    game.Title,
		GameCoverURL: coverURL,
		ConsoleName:  consoleName,
		Metadata:     metadataMap,
	}

	if hub != nil {
		hub.Broadcast(ws.Event{Type: ws.EventActivityNew, Payload: resp})
	}
}
