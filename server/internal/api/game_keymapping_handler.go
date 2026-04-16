package api

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// GameKeyMappingHandler handles per-game key mapping preference endpoints.
type GameKeyMappingHandler struct {
	DB *gorm.DB
}

// gameKeyMappingResponse is the JSON shape returned by the key mapping endpoints.
type gameKeyMappingResponse struct {
	GameID        uint              `json:"gameId"`
	CustomMapping map[string]string `json:"customMapping"`
}

// GetGameKeyMapping returns the user's key mapping for a specific game.
func (h *GameKeyMappingHandler) GetGameKeyMapping(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("gameId")

	var pref db.GameKeyMappingPreference
	if err := h.DB.Where("user_id = ? AND game_id = ?", uid, gameID).First(&pref).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			c.JSON(http.StatusNotFound, ErrorResponse{Error: "no key mapping found for this game"})
			return
		}
		slog.Error("fetching key mapping", "error", err, "userId", uid, "gameId", gameID)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch key mapping"})
		return
	}

	c.JSON(http.StatusOK, gameKeyMappingResponse{
		GameID:        pref.GameID,
		CustomMapping: parseJSONMap(pref.CustomMapping),
	})
}

// UpdateGameKeyMapping creates or updates the user's key mapping for a specific game.
func (h *GameKeyMappingHandler) UpdateGameKeyMapping(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("gameId")

	var req UpdateGameKeyMappingRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request body"})
		return
	}

	if req.CustomMapping == nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "customMapping is required"})
		return
	}

	// Verify game exists
	var game db.Game
	if err := h.DB.First(&game, gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	customJSON, err := json.Marshal(req.CustomMapping)
	if err != nil {
		slog.Error("marshalling custom mapping", "error", err, "userId", uid)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to process key mapping"})
		return
	}

	// Upsert: use Unscoped to find soft-deleted rows and avoid unique constraint conflict
	var existing db.GameKeyMappingPreference
	result := h.DB.Unscoped().Where("user_id = ? AND game_id = ?", uid, game.ID).First(&existing)
	if result.Error == nil {
		existing.CustomMapping = string(customJSON)
		existing.DeletedAt = gorm.DeletedAt{}
		if err := h.DB.Unscoped().Save(&existing).Error; err != nil {
			slog.Error("updating key mapping", "error", err, "userId", uid, "gameId", game.ID)
			c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to update key mapping"})
			return
		}
	} else {
		pref := db.GameKeyMappingPreference{
			UserID:        uid,
			GameID:        game.ID,
			CustomMapping: string(customJSON),
		}
		if err := h.DB.Create(&pref).Error; err != nil {
			slog.Error("creating key mapping", "error", err, "userId", uid, "gameId", game.ID)
			c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create key mapping"})
			return
		}
	}

	c.JSON(http.StatusOK, gameKeyMappingResponse{
		GameID:        game.ID,
		CustomMapping: req.CustomMapping,
	})
}

// DeleteGameKeyMapping deletes the user's key mapping for a specific game.
func (h *GameKeyMappingHandler) DeleteGameKeyMapping(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("gameId")

	result := h.DB.Unscoped().Where("user_id = ? AND game_id = ?", uid, gameID).Delete(&db.GameKeyMappingPreference{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "no key mapping found for this game"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "key mapping deleted"})
}
