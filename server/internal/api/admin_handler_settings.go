package api

import (
	"log/slog"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

// secretSettingKeys are settings that should be masked in GET responses.
var secretSettingKeys = map[string]bool{
	"igdb_client_secret":   true,
	"steamgriddb_api_key":  true,
	"ra_api_key":           true,
}

// GetSettings returns server settings (admin only).
// Secret values are masked with "********" placeholders.
func (h *AdminHandler) GetSettings(c *gin.Context) {
	var settings []db.ServerSetting
	if err := h.DB.Find(&settings).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch settings"})
		return
	}

	settingsMap := make(map[string]string)
	for _, s := range settings {
		if secretSettingKeys[s.Key] && s.Value != "" {
			settingsMap[s.Key] = secretMaskPlaceholder
		} else {
			settingsMap[s.Key] = s.Value
		}
	}

	c.JSON(http.StatusOK, settingsMap)
}

// secretMaskPlaceholder is the masked value returned for secret settings in GET responses.
const secretMaskPlaceholder = "********"

// allowedSettingKeys is the allowlist of setting keys that may be written via the admin API.
var allowedSettingKeys = map[string]bool{
	"registration_enabled":    true,
	"igdb_client_id":          true,
	"igdb_client_secret":      true,
	"bios_auto_download":      true,
	"steamgriddb_api_key":     true,
	"default_region":          true,
	"hide_pre_release_default": true,
	"ra_api_key":              true,
}

// UpdateSettings updates server settings (admin only).
// Secret keys whose value equals the mask placeholder are skipped to prevent
// overwriting the real secret with the masked value.
// Only keys in allowedSettingKeys are accepted.
func (h *AdminHandler) UpdateSettings(c *gin.Context) {
	var req map[string]string
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request body"})
		return
	}

	adminID, _ := c.Get("userId")
	var changedKeys []string
	for key, value := range req {
		if !allowedSettingKeys[key] {
			continue
		}
		// Skip secret keys when value is the mask placeholder — the frontend
		// loaded "********" from GET and is sending it back unchanged.
		if secretSettingKeys[key] && value == secretMaskPlaceholder {
			continue
		}
		setting := db.ServerSetting{Key: key, Value: value}
		h.DB.Where("key = ?", key).Assign(setting).FirstOrCreate(&setting)
		changedKeys = append(changedKeys, key)
	}

	slog.Info("audit: admin updated settings", "admin_id", adminID, "changed_keys", changedKeys)
	c.JSON(http.StatusOK, gin.H{"message": "settings updated"})
}

// MetadataMatches returns games needing admin attention: unscraped, unverified,
// and incomplete (scraped only via LibRetro fallback, missing IGDB metadata).
func (h *AdminHandler) MetadataMatches(c *gin.Context) {
	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)

	var unscraped []db.Game
	if err := h.DB.Where("scraper_id = '' OR scraper_id IS NULL").
		Preload("Console").Preload("Discs").
		Find(&unscraped).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch unscraped games"})
		return
	}

	var unverified []db.Game
	if err := h.DB.Where("verification_status = ? AND (verification_tag = '' OR verification_tag IS NULL)", "unverified").
		Preload("Console").Preload("Discs").
		Find(&unverified).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch unverified games"})
		return
	}

	var incomplete []db.Game
	if err := h.DB.Where("scraper_id = 'libretro'").
		Preload("Console").Preload("Discs").
		Find(&incomplete).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch incomplete games"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"unscraped":  ToGameResponses(unscraped, h.DB, uid),
		"unverified": ToGameResponses(unverified, h.DB, uid),
		"incomplete": ToGameResponses(incomplete, h.DB, uid),
	})
}
