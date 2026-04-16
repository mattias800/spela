package api

import (
	"log/slog"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/cheats"
	"github.com/spela/server/internal/db"
)

// TriggerCheatImport runs a cheat database import synchronously and returns the result.
func (h *AdminHandler) TriggerCheatImport(c *gin.Context) {
	result, err := cheats.ImportAllCheats(h.DB, cheats.DefaultBaseURL)
	if err != nil {
		slog.Error("cheat import failed", "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "cheat import failed"})
		return
	}
	slog.Info("cheat import completed", "cheatsImported", result.CheatsImported, "gamesImported", result.GamesImported)
	c.JSON(http.StatusOK, result)
}

// GetCheatStats returns statistics about imported cheat codes.
func (h *AdminHandler) GetCheatStats(c *gin.Context) {
	var totalCheats, totalGames, verifiedGames, gamesWithCheats int64
	h.DB.Model(&db.CheatCode{}).Count(&totalCheats)
	h.DB.Model(&db.Game{}).Count(&totalGames)
	h.DB.Model(&db.Game{}).Where("verification_status = ?", "verified").Count(&verifiedGames)
	h.DB.Model(&db.CheatCode{}).Distinct("game_id").Count(&gamesWithCheats)

	c.JSON(http.StatusOK, gin.H{
		"totalCheats":    totalCheats,
		"gamesWithCheats": gamesWithCheats,
		"totalGames":     totalGames,
		"verifiedGames":  verifiedGames,
	})
}
