package api

import (
	"gorm.io/gorm"
)

// IGDBHandler handles IGDB-related admin endpoints.
type IGDBHandler struct {
	DB *gorm.DB
}
