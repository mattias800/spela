package api

import (
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// PlayLaterHandler handles Play Later queue endpoints.
type PlayLaterHandler struct {
	DB  *gorm.DB
	Hub *ws.Hub
}
