package api

import (
	"context"
	"log/slog"
	"net/http"
	"sync"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// Cached bcrypt hashes for the seeded admin/player passwords. Computed
// once on first reset and reused — bcrypt at cost=12 is ~250ms per call,
// so hashing the same two passwords on every reset adds ~500ms to a
// handler that should be sub-50ms. The hashes are deterministic only in
// the sense that the inputs ("admin123", "player123") are deterministic;
// each bcrypt run produces a fresh salt, so we just keep the first
// successful hash and stop re-hashing. Test-mode-only — production
// authentication still calls auth.HashPassword on every set-password
// flow with a fresh salt.
var (
	resetHashOnce  sync.Once
	resetAdminHash string
	resetPlayerHash string
	resetHashErr    error
)

func cachedSeedHashes() (admin, player string, err error) {
	resetHashOnce.Do(func() {
		resetAdminHash, resetHashErr = auth.HashPassword("admin123")
		if resetHashErr != nil {
			return
		}
		resetPlayerHash, resetHashErr = auth.HashPassword("player123")
	})
	return resetAdminHash, resetPlayerHash, resetHashErr
}

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
//
// Kicks off a background goroutine to pre-warm cachedSeedHashes() — bcrypt at
// cost=12 takes 2-5s on docker-on-macOS, so the first /api/test/reset call
// would otherwise pay that latency in the test path. Doing it at registration
// time means by the time the first test runs, the hashes are cached and
// every reset is sub-50ms.
func RegisterTestRoute(api huma.API, h *TestHandler) {
	go func() {
		_, _, err := cachedSeedHashes()
		if err != nil {
			slog.Warn("test-reset seed-hash warmup failed", "error", err)
		}
	}()

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

	adminHash, playerHash, hashErr := cachedSeedHashes()
	if hashErr != nil {
		return nil, huma.Error500InternalServerError("seed hash failed")
	}

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

		// 13. Reset admin user to default state.
		// adminHash / playerHash come from the cachedSeedHashes() call
		// outside the transaction — bcrypt cost=12 is ~250ms per hash,
		// running both on every reset added ~500ms to what is otherwise
		// a sub-50ms operation on tmpfs.
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

		// 14. Reset player user to default state.
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
