package api

import (
	"context"
	"log/slog"
	"net/http"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Inputs / outputs --------------------------------------------------------

// TestResetInput is the input for POST /api/test/reset. The endpoint takes no
// body or path params but huma requires an input struct, so this stays empty.
type TestResetInput struct{}

// TestResetResponse is the wire format returned by /api/test/reset.
// Matches the gin handler's `{"status": "reset"}` response exactly.
type TestResetResponse struct {
	Status string `json:"status" doc:"Always 'reset' when the reset succeeded."`
}

// TestResetOutput wraps the reset response.
type TestResetOutput struct {
	Body TestResetResponse
}

// --- Registration ------------------------------------------------------------

// RegisterTestRoute wires the test-only POST /api/test/reset endpoint into
// the huma API. Must only be invoked when cfg.TestMode is true — this endpoint
// wipes user-generated data and MUST NOT be exposed in production.
//
// The route is intentionally unauthenticated (matches the gin handler) so E2E
// tests can call it without first logging in.
func RegisterTestRoute(api huma.API, h *TestHandler) {
	huma.Register(api, huma.Operation{
		OperationID: "testReset",
		Method:      http.MethodPost,
		Path:        "/api/test/reset",
		Summary:     "Reset the database to seed state (E2E test only)",
		Description: "Deletes all user-generated data and resets admin/player users to their seed state. Only registered when the server is started with SPELA_TEST_MODE=true. MUST NOT be enabled in production.",
		Tags:        []string{"test"},
	}, h.HumaReset)
}

// --- Handler -----------------------------------------------------------------

// HumaReset is the huma implementation of POST /api/test/reset.
// Restores the database to its seed state for E2E test isolation by deleting
// all user-generated data while preserving consoles, cores, and scanned games.
func (h *TestHandler) HumaReset(_ context.Context, _ *TestResetInput) (*TestResetOutput, error) {
	slog.Info("test reset: resetting database to seed state")

	err := h.DB.Transaction(func(tx *gorm.DB) error {
		// Order matters: delete children before parents to respect foreign keys.

		// 1. Session-related (children first)
		tx.Unscoped().Where("1 = 1").Delete(&db.SessionCheatSetting{})
		tx.Unscoped().Where("1 = 1").Delete(&db.SessionSaveData{})
		tx.Unscoped().Where("1 = 1").Delete(&db.SessionSaveState{})
		tx.Unscoped().Where("1 = 1").Delete(&db.GameSession{})

		// 2. Shared sessions (children first)
		tx.Unscoped().Where("1 = 1").Delete(&db.SharedSessionSave{})
		tx.Unscoped().Where("1 = 1").Delete(&db.SharedSessionInvite{})
		tx.Unscoped().Where("1 = 1").Delete(&db.SharedSessionMember{})
		tx.Unscoped().Where("1 = 1").Delete(&db.SharedSession{})

		// 3. Netplay (children first)
		tx.Unscoped().Where("1 = 1").Delete(&db.NetplayInvite{})
		tx.Unscoped().Where("1 = 1").Delete(&db.NetplaySession{})

		// 4. Challenges (children first)
		tx.Unscoped().Where("1 = 1").Delete(&db.ChallengeAttempt{})
		tx.Unscoped().Where("1 = 1").Delete(&db.Challenge{})

		// 5. Collections (children first)
		tx.Unscoped().Where("1 = 1").Delete(&db.CollectionItem{})
		tx.Unscoped().Where("1 = 1").Delete(&db.GameCollection{})

		// 6. User-game associations
		tx.Unscoped().Where("1 = 1").Delete(&db.Favorite{})
		tx.Unscoped().Where("1 = 1").Delete(&db.PlayHistory{})
		tx.Unscoped().Where("1 = 1").Delete(&db.DailyPlayActivity{})
		tx.Unscoped().Where("1 = 1").Delete(&db.PlayLaterItem{})
		tx.Unscoped().Where("1 = 1").Delete(&db.GameRating{})
		tx.Unscoped().Where("1 = 1").Delete(&db.SharedSaveState{})
		tx.Unscoped().Where("1 = 1").Delete(&db.ActivityEvent{})
		tx.Unscoped().Where("1 = 1").Delete(&db.GameKeyMappingPreference{})

		// 7. User preferences
		tx.Unscoped().Where("1 = 1").Delete(&db.ConsoleShaderPreference{})
		tx.Unscoped().Where("1 = 1").Delete(&db.ConsoleKeyMappingPreference{})
		tx.Unscoped().Where("1 = 1").Delete(&db.DeviceShaderPreference{})
		tx.Unscoped().Where("1 = 1").Delete(&db.Device{})

		// 8. RetroAchievements
		tx.Unscoped().Where("1 = 1").Delete(&db.RetroAchievementCredential{})
		tx.Unscoped().Where("1 = 1").Delete(&db.UserAchievementProgress{})
		tx.Unscoped().Where("1 = 1").Delete(&db.GameAchievementCache{})

		// 9. Auth tokens and login attempts
		tx.Unscoped().Where("1 = 1").Delete(&db.RefreshToken{})
		tx.Unscoped().Where("1 = 1").Delete(&db.TokenBlacklist{})
		tx.Unscoped().Where("1 = 1").Delete(&db.LoginAttempt{})
		tx.Unscoped().Where("1 = 1").Delete(&db.SystemEvent{})

		// 10. Staged uploads
		tx.Unscoped().Where("1 = 1").Delete(&db.StagedUpload{})

		// 11. Server settings (reset to defaults)
		tx.Unscoped().Where("1 = 1").Delete(&db.ServerSetting{})

		// 12. Delete extra users (keep admin and player)
		tx.Unscoped().Where("username NOT IN ?", []string{"admin", "player"}).Delete(&db.User{})

		// 13. Reset admin user to default state
		adminHash, err := auth.HashPassword("admin123")
		if err != nil {
			return err
		}
		tx.Model(&db.User{}).Where("username = ?", "admin").Updates(map[string]interface{}{
			"password_hash":              adminHash,
			"role":                       "owner",
			"disabled":                   false,
			"pending_approval":           false,
			"show_perf_overlay":          false,
			"auto_save_enabled":          true,
			"auto_load_save_enabled":     true,
			"selected_shader":            "none",
			"selected_theme":             "default-dark",
			"default_second_screen_page": "art",
			"selected_key_mapping":       "arrows-left",
			"custom_key_mapping":         "",
			"avatar_url":                 "",
			"token_version":              0,
			"deleted_at":                 nil,
		})

		// 14. Reset player user to default state
		playerHash, err := auth.HashPassword("player123")
		if err != nil {
			return err
		}
		tx.Model(&db.User{}).Where("username = ?", "player").Updates(map[string]interface{}{
			"password_hash":              playerHash,
			"role":                       "user",
			"disabled":                   false,
			"pending_approval":           false,
			"show_perf_overlay":          false,
			"auto_save_enabled":          true,
			"auto_load_save_enabled":     true,
			"selected_shader":            "none",
			"selected_theme":             "default-dark",
			"default_second_screen_page": "art",
			"selected_key_mapping":       "arrows-left",
			"custom_key_mapping":         "",
			"avatar_url":                 "",
			"token_version":              0,
			"deleted_at":                 nil,
		})

		return nil
	})

	if err != nil {
		slog.Error("test reset failed", "error", err)
		return nil, huma.Error500InternalServerError("reset failed")
	}

	slog.Info("test reset: database reset complete")
	return &TestResetOutput{Body: TestResetResponse{Status: "reset"}}, nil
}
