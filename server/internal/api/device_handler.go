package api

import (
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// DeviceHandler handles device registration and preference endpoints.
type DeviceHandler struct {
	DB *gorm.DB
}

// deviceResponse is the JSON shape returned for a device.
type deviceResponse struct {
	db.Device
	ConsoleShaders map[string]string `json:"consoleShaders"`
}

// RegisterDevice handles POST /api/user/devices.
// Upserts a device by (user_id, device_uuid) and returns it with shader preferences.
func (h *DeviceHandler) RegisterDevice(c *gin.Context) {
	uid := getUserID(c)

	var req struct {
		DeviceUUID string `json:"deviceUuid" binding:"required"`
		Name       string `json:"name" binding:"required"`
		Platform   string `json:"platform" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	var device db.Device
	result := h.DB.Where("user_id = ? AND device_uuid = ?", uid, req.DeviceUUID).First(&device)
	if result.Error == nil {
		// Existing device — update
		device.Name = req.Name
		device.Platform = req.Platform
		device.LastSeenAt = time.Now()
		if err := h.DB.Save(&device).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update device"})
			return
		}
	} else {
		// Enforce per-user device limit to prevent abuse
		var count int64
		h.DB.Model(&db.Device{}).Where("user_id = ?", uid).Count(&count)
		if count >= 50 {
			c.JSON(http.StatusBadRequest, gin.H{"error": "device limit reached (max 50)"})
			return
		}
		// New device — create
		device = db.Device{
			UserID:     uid,
			DeviceUUID: req.DeviceUUID,
			Name:       req.Name,
			Platform:   req.Platform,
			LastSeenAt: time.Now(),
		}
		if err := h.DB.Create(&device).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to register device"})
			return
		}
	}

	c.JSON(http.StatusOK, deviceResponse{
		Device:         device,
		ConsoleShaders: h.buildDeviceShaderMap(device.ID),
	})
}

// GetDevices handles GET /api/user/devices.
func (h *DeviceHandler) GetDevices(c *gin.Context) {
	uid := getUserID(c)

	var devices []db.Device
	if err := h.DB.Where("user_id = ?", uid).Order("last_seen_at desc").Find(&devices).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch devices"})
		return
	}

	resp := make([]deviceResponse, 0, len(devices))
	for _, d := range devices {
		resp = append(resp, deviceResponse{
			Device:         d,
			ConsoleShaders: h.buildDeviceShaderMap(d.ID),
		})
	}

	c.JSON(http.StatusOK, resp)
}

// UpdateDevice handles PUT /api/user/devices/:id.
func (h *DeviceHandler) UpdateDevice(c *gin.Context) {
	uid := getUserID(c)
	id := c.Param("id")

	var device db.Device
	if err := h.DB.First(&device, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "device not found"})
		return
	}
	if device.UserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
		return
	}

	var req struct {
		Name string `json:"name" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	device.Name = req.Name
	if err := h.DB.Save(&device).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update device"})
		return
	}

	c.JSON(http.StatusOK, device)
}

// DeleteDevice handles DELETE /api/user/devices/:id.
func (h *DeviceHandler) DeleteDevice(c *gin.Context) {
	uid := getUserID(c)
	id := c.Param("id")

	var device db.Device
	if err := h.DB.First(&device, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "device not found"})
		return
	}
	if device.UserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
		return
	}

	// Delete shader preferences first, then the device
	h.DB.Where("device_id = ?", device.ID).Delete(&db.DeviceShaderPreference{})
	if err := h.DB.Delete(&device).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to delete device"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "device deleted"})
}

// GetDevicePreferences handles GET /api/user/devices/:id/preferences.
func (h *DeviceHandler) GetDevicePreferences(c *gin.Context) {
	uid := getUserID(c)
	id := c.Param("id")

	var device db.Device
	if err := h.DB.First(&device, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "device not found"})
		return
	}
	if device.UserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"consoleShaders": h.buildDeviceShaderMap(device.ID)})
}

// UpdateDevicePreferences handles PUT /api/user/devices/:id/preferences.
func (h *DeviceHandler) UpdateDevicePreferences(c *gin.Context) {
	uid := getUserID(c)
	id := c.Param("id")

	var device db.Device
	if err := h.DB.First(&device, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "device not found"})
		return
	}
	if device.UserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
		return
	}

	var req struct {
		ConsoleShaders map[string]string `json:"consoleShaders"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	for consoleAbbr, shader := range req.ConsoleShaders {
		var console db.Console
		if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleAbbr).First(&console).Error; err != nil {
			continue
		}
		if shader == "" || shader == "none" {
			// Delete the override to revert to user/global default
			h.DB.Where("device_id = ? AND console_id = ?", device.ID, console.ID).
				Delete(&db.DeviceShaderPreference{})
		} else {
			// Upsert: update if exists, create if not
			var existing db.DeviceShaderPreference
			result := h.DB.Where("device_id = ? AND console_id = ?", device.ID, console.ID).First(&existing)
			if result.Error == nil {
				h.DB.Model(&existing).Update("shader", shader)
			} else {
				h.DB.Create(&db.DeviceShaderPreference{
					DeviceID:  device.ID,
					ConsoleID: console.ID,
					Shader:    shader,
				})
			}
		}
	}

	c.JSON(http.StatusOK, gin.H{"consoleShaders": h.buildDeviceShaderMap(device.ID)})
}

// AdminGetUserDevices handles GET /api/admin/users/:id/devices.
func (h *DeviceHandler) AdminGetUserDevices(c *gin.Context) {
	userID := c.Param("id")

	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}

	var devices []db.Device
	if err := h.DB.Where("user_id = ?", user.ID).Order("last_seen_at desc").Find(&devices).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch devices"})
		return
	}

	resp := make([]deviceResponse, 0, len(devices))
	for _, d := range devices {
		resp = append(resp, deviceResponse{
			Device:         d,
			ConsoleShaders: h.buildDeviceShaderMap(d.ID),
		})
	}

	c.JSON(http.StatusOK, resp)
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
