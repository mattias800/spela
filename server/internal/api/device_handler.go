package api

import (
	"strings"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// DeviceHandler handles device registration and preference endpoints.
type DeviceHandler struct {
	DB *gorm.DB
}

// buildDeviceShaderMap queries all DeviceShaderPreference rows for a device
// and returns a map keyed by console abbreviation (lowercase).
func (h *DeviceHandler) buildDeviceShaderMap(deviceID uint) map[string]string {
	var prefs []db.DeviceShaderPreference
	h.DB.Where("device_id = ?", deviceID).Find(&prefs)

	// Batch-load console abbreviations
	consoleIDs := make([]uint, 0, len(prefs))
	for _, p := range prefs {
		consoleIDs = append(consoleIDs, p.ConsoleID)
	}
	abbrMap := resolveConsoleAbbrs(h.DB, consoleIDs)

	m := make(map[string]string, len(prefs))
	for _, p := range prefs {
		if abbr, ok := abbrMap[p.ConsoleID]; ok {
			m[abbr] = p.Shader
		}
	}
	return m
}

// resolveConsoleAbbrs batch-loads console abbreviations for a set of console IDs.
func resolveConsoleAbbrs(database *gorm.DB, consoleIDs []uint) map[uint]string {
	if len(consoleIDs) == 0 {
		return nil
	}
	var consoles []db.Console
	database.Where("id IN ?", consoleIDs).Select("id, abbreviation").Find(&consoles)
	m := make(map[uint]string, len(consoles))
	for _, c := range consoles {
		m[c.ID] = strings.ToLower(c.Abbreviation)
	}
	return m
}
