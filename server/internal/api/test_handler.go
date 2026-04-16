package api

import (
	"log/slog"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// TestHandler provides endpoints for E2E test state management.
// Only registered when SPELA_TEST_MODE=true.
type TestHandler struct {
	DB *gorm.DB
}

// Reset restores the database to its seed state for E2E test isolation.
// Deletes all user-generated data while preserving consoles, cores, and scanned games.
// POST /api/test/reset
func (h *TestHandler) Reset(c *gin.Context) {
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "reset failed"})
		return
	}

	slog.Info("test reset: database reset complete")
	c.JSON(http.StatusOK, gin.H{"status": "reset"})
}
