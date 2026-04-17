package api

import (
	"context"
	"log/slog"
	"net/http"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/cheats"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Input / output types ---

// TriggerCheatImportInput is the input for POST /api/admin/cheats/import.
type TriggerCheatImportInput struct{}

// TriggerCheatImportOutput wraps the cheat import result (direct pass-through
// from the cheats.ImportResult).
type TriggerCheatImportOutput struct {
	Body cheats.ImportResult
}

// GetCheatStatsInput is the input for GET /api/admin/cheats/stats.
type GetCheatStatsInput struct{}

// CheatStatsResponse is the wire format for the cheat stats response.
type CheatStatsResponse struct {
	TotalCheats     int64 `json:"totalCheats"`
	GamesWithCheats int64 `json:"gamesWithCheats"`
	TotalGames      int64 `json:"totalGames"`
	VerifiedGames   int64 `json:"verifiedGames"`
}

// GetCheatStatsOutput wraps the cheat stats response.
type GetCheatStatsOutput struct {
	Body CheatStatsResponse
}

// RegisterCheatAdminRoutes wires the admin cheat endpoints into the huma API.
func RegisterCheatAdminRoutes(api huma.API, h *AdminHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	requireAdmin := RequireAdmin(api)
	mw := huma.Middlewares{requireAuth, rateLimit, requireAdmin}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "triggerCheatImport",
		Method:      http.MethodPost,
		Path:        "/api/admin/cheats/import",
		Summary:     "Import cheat database",
		Description: "Admin-only. Runs a synchronous cheat-database import and returns the result.",
		Tags:        []string{"admin", "cheats"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaTriggerCheatImport)

	huma.Register(api, huma.Operation{
		OperationID: "getCheatStats",
		Method:      http.MethodGet,
		Path:        "/api/admin/cheats/stats",
		Summary:     "Get cheat statistics",
		Description: "Admin-only. Returns counts of total cheats, games with cheats, total games, and verified games.",
		Tags:        []string{"admin", "cheats"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetCheatStats)
}

// --- Handlers ---

// HumaTriggerCheatImport is the huma handler for POST /api/admin/cheats/import.
func (h *AdminHandler) HumaTriggerCheatImport(_ context.Context, _ *TriggerCheatImportInput) (*TriggerCheatImportOutput, error) {
	result, err := cheats.ImportAllCheats(h.DB, cheats.DefaultBaseURL)
	if err != nil {
		slog.Error("cheat import failed", "error", err)
		return nil, huma.Error500InternalServerError("cheat import failed")
	}
	slog.Info("cheat import completed", "cheatsImported", result.CheatsImported, "gamesImported", result.GamesImported)
	return &TriggerCheatImportOutput{Body: *result}, nil
}

// HumaGetCheatStats is the huma handler for GET /api/admin/cheats/stats.
func (h *AdminHandler) HumaGetCheatStats(_ context.Context, _ *GetCheatStatsInput) (*GetCheatStatsOutput, error) {
	var totalCheats, totalGames, verifiedGames, gamesWithCheats int64
	h.DB.Model(&db.CheatCode{}).Count(&totalCheats)
	h.DB.Model(&db.Game{}).Count(&totalGames)
	h.DB.Model(&db.Game{}).Where("verification_status = ?", "verified").Count(&verifiedGames)
	h.DB.Model(&db.CheatCode{}).Distinct("game_id").Count(&gamesWithCheats)

	return &GetCheatStatsOutput{Body: CheatStatsResponse{
		TotalCheats:     totalCheats,
		GamesWithCheats: gamesWithCheats,
		TotalGames:      totalGames,
		VerifiedGames:   verifiedGames,
	}}, nil
}
