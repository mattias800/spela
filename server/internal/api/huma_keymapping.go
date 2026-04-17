package api

import (
	"context"
	"encoding/json"
	"log/slog"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Handlers: GameKeyMappingHandler ---

// HumaGetGameKeyMapping is the huma handler for GET /api/user/games/{gameId}/keymapping.
func (h *GameKeyMappingHandler) HumaGetGameKeyMapping(ctx context.Context, in *GetGameKeyMappingInput) (*GetGameKeyMappingOutput, error) {
	uid := UserIDFromContext(ctx)
	gameID := in.GameID

	var pref db.GameKeyMappingPreference
	if err := h.DB.Where("user_id = ? AND game_id = ?", uid, gameID).First(&pref).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, huma.Error404NotFound("no key mapping found for this game")
		}
		slog.Error("fetching key mapping", "error", err, "userId", uid, "gameId", gameID)
		return nil, huma.Error500InternalServerError("failed to fetch key mapping")
	}

	return &GetGameKeyMappingOutput{Body: GameKeyMappingResponse{
		GameID:        pref.GameID,
		CustomMapping: parseJSONMap(pref.CustomMapping),
	}}, nil
}

// HumaUpdateGameKeyMapping is the huma handler for PUT /api/user/games/{gameId}/keymapping.
func (h *GameKeyMappingHandler) HumaUpdateGameKeyMapping(ctx context.Context, in *UpdateGameKeyMappingInput) (*UpdateGameKeyMappingOutput, error) {
	uid := UserIDFromContext(ctx)
	gameID := in.GameID

	if in.Body.CustomMapping == nil {
		return nil, huma.Error400BadRequest("customMapping is required")
	}

	var game db.Game
	if err := h.DB.First(&game, gameID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	customJSON, err := json.Marshal(in.Body.CustomMapping)
	if err != nil {
		slog.Error("marshalling custom mapping", "error", err, "userId", uid)
		return nil, huma.Error500InternalServerError("failed to process key mapping")
	}

	var existing db.GameKeyMappingPreference
	result := h.DB.Unscoped().Where("user_id = ? AND game_id = ?", uid, game.ID).First(&existing)
	if result.Error == nil {
		existing.CustomMapping = string(customJSON)
		existing.DeletedAt = gorm.DeletedAt{}
		if err := h.DB.Unscoped().Save(&existing).Error; err != nil {
			slog.Error("updating key mapping", "error", err, "userId", uid, "gameId", game.ID)
			return nil, huma.Error500InternalServerError("failed to update key mapping")
		}
	} else {
		pref := db.GameKeyMappingPreference{
			UserID:        uid,
			GameID:        game.ID,
			CustomMapping: string(customJSON),
		}
		if err := h.DB.Create(&pref).Error; err != nil {
			slog.Error("creating key mapping", "error", err, "userId", uid, "gameId", game.ID)
			return nil, huma.Error500InternalServerError("failed to create key mapping")
		}
	}

	return &UpdateGameKeyMappingOutput{Body: GameKeyMappingResponse{
		GameID:        game.ID,
		CustomMapping: in.Body.CustomMapping,
	}}, nil
}

// HumaDeleteGameKeyMapping is the huma handler for DELETE /api/user/games/{gameId}/keymapping.
func (h *GameKeyMappingHandler) HumaDeleteGameKeyMapping(ctx context.Context, in *DeleteGameKeyMappingInput) (*DeleteGameKeyMappingOutput, error) {
	uid := UserIDFromContext(ctx)
	gameID := in.GameID

	result := h.DB.Unscoped().Where("user_id = ? AND game_id = ?", uid, gameID).Delete(&db.GameKeyMappingPreference{})
	if result.RowsAffected == 0 {
		return nil, huma.Error404NotFound("no key mapping found for this game")
	}
	return &DeleteGameKeyMappingOutput{Body: MessageResponse{Message: "key mapping deleted"}}, nil
}
