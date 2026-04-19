package api

import (
	"strconv"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// SharedSaveHandler handles shared save state endpoints. All HTTP methods
// are served by huma_shared.go / huma_shared_uploads.go; this file keeps
// the struct + response converter still referenced from there.
type SharedSaveHandler struct {
	DB      *gorm.DB
	Storage *storage.Storage
	Hub     *ws.Hub
}

// toResponse converts a db.SharedSaveState to its API response.
func (h *SharedSaveHandler) toResponse(s db.SharedSaveState, _ uint) SharedSaveResponse {
	username := s.User.Username
	avatarURL := s.User.AvatarURL

	if username == "" && s.UserID != 0 {
		var user db.User
		if err := h.DB.First(&user, s.UserID).Error; err == nil {
			username = user.Username
			avatarURL = user.AvatarURL
		}
	}

	return SharedSaveResponse{
		ID:            strconv.FormatUint(uint64(s.ID), 10),
		UserID:        strconv.FormatUint(uint64(s.UserID), 10),
		Username:      username,
		AvatarURL:     avatarURL,
		GameID:        strconv.FormatUint(uint64(s.GameID), 10),
		Name:          s.Name,
		Description:   s.Description,
		FileSize:      s.FileSize,
		ScreenshotURL: s.ScreenshotURL,
		DownloadCount: s.DownloadCount,
		CreatedAt:     s.CreatedAt,
	}
}
