package api

import (
	"strconv"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// CollectionHandler handles game collection endpoints.
type CollectionHandler struct {
	DB  *gorm.DB
	Hub *ws.Hub
}

// toCollectionResponse converts a db.GameCollection to its API response.
func (h *CollectionHandler) toCollectionResponse(col db.GameCollection, _ uint) CollectionResponse {
	username := col.User.Username
	avatarURL := col.User.AvatarURL

	if username == "" && col.UserID != 0 {
		var user db.User
		if err := h.DB.First(&user, col.UserID).Error; err == nil {
			username = user.Username
			avatarURL = user.AvatarURL
		}
	}

	// Derive cover URL from the first game in the collection
	coverURL := ""
	if len(col.Items) > 0 {
		var firstGame db.Game
		if err := h.DB.First(&firstGame, col.Items[0].GameID).Error; err == nil {
			coverURL = firstGame.CoverURL
			if coverURL != "" && len(coverURL) > 0 && coverURL[0] != 'h' {
				coverURL = "/api/images/" + coverURL
			}
		}
	}

	return CollectionResponse{
		ID:          strconv.FormatUint(uint64(col.ID), 10),
		UserID:      strconv.FormatUint(uint64(col.UserID), 10),
		Username:    username,
		AvatarURL:   avatarURL,
		Name:        col.Name,
		Description: col.Description,
		IsPublic:    col.IsPublic,
		CoverURL:    coverURL,
		GameCount:   len(col.Items),
		CreatedAt:   col.CreatedAt,
		UpdatedAt:   col.UpdatedAt,
	}
}
