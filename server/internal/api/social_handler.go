package api

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// SocialHandler handles activity feed and online status endpoints.
type SocialHandler struct {
	DB  *gorm.DB
	Hub *ws.Hub
}

// GetOnlineUsers returns users currently connected via WebSocket.
func (h *SocialHandler) GetOnlineUsers(c *gin.Context) {
	onlineIDs := h.Hub.GetOnlineUserIDs()

	if len(onlineIDs) == 0 {
		c.JSON(http.StatusOK, []OnlineUserResponse{})
		return
	}

	var users []db.User
	if err := h.DB.Where("id IN ?", onlineIDs).Find(&users).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch online users"})
		return
	}

	result := make([]OnlineUserResponse, 0, len(users))
	for _, u := range users {
		resp := OnlineUserResponse{
			ID:        strconv.FormatUint(uint64(u.ID), 10),
			Username:  u.Username,
			AvatarURL: u.AvatarURL,
		}
		if gameID := h.Hub.GetUserGame(u.ID); gameID != 0 {
			gidStr := strconv.FormatUint(uint64(gameID), 10)
			resp.CurrentGame = &gidStr
		}
		result = append(result, resp)
	}

	c.JSON(http.StatusOK, result)
}

// GetActivityFeed returns a paginated activity feed (most recent first).
func (h *SocialHandler) GetActivityFeed(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.ActivityEvent{}).Count(&total)

	var events []db.ActivityEvent
	if err := h.DB.Preload("User").Preload("Game").Preload("Game.Console").
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&events).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch activity feed"})
		return
	}

	result := make([]ActivityEventResponse, 0, len(events))
	for _, e := range events {
		coverURL := e.Game.CoverURL
		if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
			coverURL = "/api/images/" + coverURL
		}

		consoleName := ""
		if e.Game.Console.ID != 0 {
			consoleName = e.Game.Console.Name
		}

		result = append(result, ActivityEventResponse{
			ID:           strconv.FormatUint(uint64(e.ID), 10),
			EventType:    e.EventType,
			CreatedAt:    e.CreatedAt,
			UserID:       strconv.FormatUint(uint64(e.UserID), 10),
			Username:     e.User.Username,
			AvatarURL:    e.User.AvatarURL,
			GameID:       strconv.FormatUint(uint64(e.GameID), 10),
			GameTitle:    e.Game.Title,
			GameCoverURL: coverURL,
			ConsoleName:  consoleName,
			Metadata:     e.Metadata,
		})
	}

	c.JSON(http.StatusOK, PaginatedResponse{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// CreateActivityEvent creates a new activity event and broadcasts it via WebSocket.
func CreateActivityEvent(database *gorm.DB, hub *ws.Hub, userID uint, eventType string, gameID uint, metadata map[string]interface{}) {
	metadataJSON := ""
	if metadata != nil {
		b, err := json.Marshal(metadata)
		if err == nil {
			metadataJSON = string(b)
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
		Metadata:     metadataJSON,
	}

	if hub != nil {
		hub.Broadcast(ws.Event{Type: "activity_new", Payload: resp})
	}
}
