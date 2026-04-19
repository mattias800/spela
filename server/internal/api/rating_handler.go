package api

import (
	"strconv"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// RatingHandler handles game rating endpoints. All HTTP methods are served
// by huma_ratings.go now; this file keeps the struct + response converter
// still referenced from there.
type RatingHandler struct {
	DB  *gorm.DB
	Hub *ws.Hub
}

// toRatingResponse converts a db.GameRating to its API response.
func (h *RatingHandler) toRatingResponse(r db.GameRating, _ uint) GameRatingResponse {
	username := r.User.Username
	avatarURL := r.User.AvatarURL

	// If User wasn't preloaded, load it
	if username == "" && r.UserID != 0 {
		var user db.User
		if err := h.DB.First(&user, r.UserID).Error; err == nil {
			username = user.Username
			avatarURL = user.AvatarURL
		}
	}

	return GameRatingResponse{
		ID:        strconv.FormatUint(uint64(r.ID), 10),
		UserID:    strconv.FormatUint(uint64(r.UserID), 10),
		Username:  username,
		AvatarURL: avatarURL,
		GameID:    strconv.FormatUint(uint64(r.GameID), 10),
		Rating:    r.Rating,
		Review:    r.Review,
		CreatedAt: r.CreatedAt,
		UpdatedAt: r.UpdatedAt,
	}
}
