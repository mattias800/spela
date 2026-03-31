package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// MakerHandler handles hardware maker-related endpoints.
type MakerHandler struct {
	DB *gorm.DB
}

// ListMakers returns all hardware makers that have at least one console with games.
func (h *MakerHandler) ListMakers(c *gin.Context) {
	var makers []db.HardwareMaker
	if err := h.DB.Order("name ASC").Find(&makers).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch makers"})
		return
	}

	result := make([]MakerDetailResponse, 0, len(makers))
	for _, maker := range makers {
		// Count consoles that have at least one primary, non-pre-release game
		var consoleCount int64
		h.DB.Model(&db.Console{}).
			Where("hardware_maker_id = ? AND id IN (?)",
				maker.ID,
				h.DB.Model(&db.Game{}).
					Select("DISTINCT console_id").
					Where("is_primary = ? AND is_pre_release = ?", true, false),
			).Count(&consoleCount)

		if consoleCount > 0 {
			result = append(result, MakerDetailResponse{
				Code:         maker.Code,
				Name:         maker.Name,
				ConsoleCount: int(consoleCount),
			})
		}
	}

	c.JSON(http.StatusOK, result)
}

// GetMaker returns a single hardware maker by code with its consoles.
func (h *MakerHandler) GetMaker(c *gin.Context) {
	code := c.Param("code")

	var maker db.HardwareMaker
	if err := h.DB.Where("code = ?", code).First(&maker).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "maker not found"})
		return
	}

	// Load all consoles for this maker with preloaded relationships
	var consoles []db.Console
	h.DB.Where("hardware_maker_id = ?", maker.ID).
		Preload("HardwareMaker").
		Preload("MediaType").
		Preload("MediaType.Category").
		Order("generation ASC, name ASC").
		Find(&consoles)

	// Attach game counts (only primary, non-pre-release games)
	for i := range consoles {
		var count int64
		h.DB.Model(&db.Game{}).Where("console_id = ? AND is_primary = ? AND is_pre_release = ?", consoles[i].ID, true, false).Count(&count)
		consoles[i].GameCount = int(count)
	}

	// Only include consoles that have games
	consoleResponses := make([]ConsoleResponse, 0, len(consoles))
	for _, con := range consoles {
		if con.GameCount > 0 {
			consoleResponses = append(consoleResponses, ToConsoleResponse(con))
		}
	}

	resp := MakerDetailResponse{
		Code:         maker.Code,
		Name:         maker.Name,
		ConsoleCount: len(consoleResponses),
		Consoles:     consoleResponses,
	}

	c.JSON(http.StatusOK, resp)
}
