package api

import (
	"gorm.io/gorm"
)

// MakerHandler handles hardware maker-related endpoints. All HTTP methods
// are served by huma — see HumaListMakers / HumaGetMaker in huma_maker.go.
// This struct is kept because huma_maker.go receives a *MakerHandler and
// router.go instantiates one.
type MakerHandler struct {
	DB *gorm.DB
}
