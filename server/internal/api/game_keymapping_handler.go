package api

import (
	"gorm.io/gorm"
)

// GameKeyMappingHandler handles per-game key mapping preference endpoints.
type GameKeyMappingHandler struct {
	DB *gorm.DB
}
