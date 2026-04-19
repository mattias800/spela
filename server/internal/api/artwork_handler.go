package api

import (
	"gorm.io/gorm"
)

// ArtworkHandler handles game artwork endpoints. The HTTP method is served
// by huma — see HumaGetGameArtwork in huma_artwork.go. The struct is kept
// because huma_artwork.go receives a *ArtworkHandler.
type ArtworkHandler struct {
	DB *gorm.DB
}
