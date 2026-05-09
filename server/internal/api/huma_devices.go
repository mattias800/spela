package api

import (
	"context"
	"net/http"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Input / output types ---

// RegisterDeviceInput is the input for POST /api/user/devices.
type RegisterDeviceInput struct {
	Body RegisterDeviceRequest
}

// RegisterDeviceOutput wraps the device response.
type RegisterDeviceOutput struct {
	Body DeviceResponse
}

// ListDevicesInput is the input for GET /api/user/devices.
type ListDevicesInput struct{}

// ListDevicesOutput wraps the list of devices.
type ListDevicesOutput struct {
	Body []DeviceResponse
}

// UpdateDeviceInput is the input for PUT /api/user/devices/{id}.
type UpdateDeviceInput struct {
	ID   string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Device ID."`
	Body UpdateDeviceRequest
}

// UpdateDeviceOutput wraps the device model in the response (matches gin wire format).
type UpdateDeviceOutput struct {
	Body db.Device
}

// DeleteDeviceInput is the input for DELETE /api/user/devices/{id}.
type DeleteDeviceInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Device ID."`
}

// DeleteDeviceOutput wraps the delete success message.
type DeleteDeviceOutput struct {
	Body MessageResponse
}

// GetDevicePreferencesInput is the input for GET /api/user/devices/{id}/preferences.
type GetDevicePreferencesInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Device ID."`
}

// DevicePreferencesResponse is the wire format for device preferences.
type DevicePreferencesResponse struct {
	ConsoleShaders map[string]string `json:"consoleShaders"`
}

// GetDevicePreferencesOutput wraps the device preferences response.
type GetDevicePreferencesOutput struct {
	Body DevicePreferencesResponse
}

// UpdateDevicePreferencesInput is the input for PUT /api/user/devices/{id}/preferences.
type UpdateDevicePreferencesInput struct {
	ID   string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Device ID."`
	Body UpdateDevicePreferencesRequest
}

// UpdateDevicePreferencesOutput wraps the device preferences response.
type UpdateDevicePreferencesOutput struct {
	Body DevicePreferencesResponse
}

// AdminGetUserDevicesInput is the input for GET /api/admin/users/{id}/devices.
type AdminGetUserDevicesInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"User ID."`
}

// AdminGetUserDevicesOutput wraps the list of devices for a user (admin view).
type AdminGetUserDevicesOutput struct {
	Body []DeviceResponse
}

// RegisterDeviceRoutes wires device management endpoints into the huma API.
func RegisterDeviceRoutes(api huma.API, h *DeviceHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	requireAdmin := RequireAdmin(api)
	mw := huma.Middlewares{requireAuth, rateLimit}
	adminMW := huma.Middlewares{requireAuth, rateLimit, requireAdmin}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "registerDevice",
		Method:      http.MethodPost,
		Path:        "/api/user/devices",
		Summary:     "Register a device",
		Description: "Upserts a device by (user, deviceUuid). Returns the device with its per-console shader preferences.",
		Tags:        []string{"devices"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaRegisterDevice)

	huma.Register(api, huma.Operation{
		OperationID: "listDevices",
		Method:      http.MethodGet,
		Path:        "/api/user/devices",
		Summary:     "List the caller's devices",
		Description: "Returns the caller's registered devices with per-console shader preferences.",
		Tags:        []string{"devices"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetDevices)

	huma.Register(api, huma.Operation{
		OperationID: "updateDevice",
		Method:      http.MethodPut,
		Path:        "/api/user/devices/{id}",
		Summary:     "Update a device",
		Description: "Updates the device name for a device owned by the caller.",
		Tags:        []string{"devices"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateDevice)

	huma.Register(api, huma.Operation{
		OperationID: "deleteDevice",
		Method:      http.MethodDelete,
		Path:        "/api/user/devices/{id}",
		Summary:     "Delete a device",
		Description: "Removes a device and its per-console shader preferences.",
		Tags:        []string{"devices"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDeleteDevice)

	huma.Register(api, huma.Operation{
		OperationID: "getDevicePreferences",
		Method:      http.MethodGet,
		Path:        "/api/user/devices/{id}/preferences",
		Summary:     "Get device preferences",
		Description: "Returns per-console shader overrides for the device.",
		Tags:        []string{"devices"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetDevicePreferences)

	huma.Register(api, huma.Operation{
		OperationID: "updateDevicePreferences",
		Method:      http.MethodPut,
		Path:        "/api/user/devices/{id}/preferences",
		Summary:     "Update device preferences",
		Description: "Upserts per-console shader preferences for the device; empty/'none' removes the override.",
		Tags:        []string{"devices"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateDevicePreferences)

	huma.Register(api, huma.Operation{
		OperationID: "adminGetUserDevices",
		Method:      http.MethodGet,
		Path:        "/api/admin/users/{id}/devices",
		Summary:     "Admin: list a user's devices",
		Description: "Admin-only. Returns all devices registered to the specified user with per-console shader preferences.",
		Tags:        []string{"admin", "devices"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaAdminGetUserDevices)
}

// --- Handlers ---

// HumaRegisterDevice is the huma handler for POST /api/user/devices.
func (h *DeviceHandler) HumaRegisterDevice(ctx context.Context, in *RegisterDeviceInput) (*RegisterDeviceOutput, error) {
	uid := UserIDFromContext(ctx)
	req := in.Body

	if req.DeviceUUID == "" || req.Name == "" || req.Platform == "" {
		return nil, huma.Error400BadRequest("invalid request body")
	}

	var device db.Device
	result := h.DB.Where("user_id = ? AND device_uuid = ?", uid, req.DeviceUUID).First(&device)
	if result.Error == nil {
		device.Name = req.Name
		device.Platform = req.Platform
		device.LastSeenAt = time.Now()
		if err := h.DB.Save(&device).Error; err != nil {
			return nil, huma.Error500InternalServerError("failed to update device")
		}
	} else {
		var count int64
		h.DB.Model(&db.Device{}).Where("user_id = ?", uid).Count(&count)
		if count >= 50 {
			return nil, huma.Error400BadRequest("device limit reached (max 50)")
		}
		device = db.Device{
			UserID:     uid,
			DeviceUUID: req.DeviceUUID,
			Name:       req.Name,
			Platform:   req.Platform,
			LastSeenAt: time.Now(),
		}
		if err := h.DB.Create(&device).Error; err != nil {
			return nil, huma.Error500InternalServerError("failed to register device")
		}
	}

	return &RegisterDeviceOutput{Body: DeviceResponse{
		Device:         device,
		ConsoleShaders: h.buildDeviceShaderMap(device.ID),
	}}, nil
}

// HumaGetDevices is the huma handler for GET /api/user/devices.
func (h *DeviceHandler) HumaGetDevices(ctx context.Context, _ *ListDevicesInput) (*ListDevicesOutput, error) {
	uid := UserIDFromContext(ctx)

	var devices []db.Device
	if err := h.DB.Where("user_id = ?", uid).Order("last_seen_at desc").Find(&devices).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch devices")
	}

	resp := make([]DeviceResponse, 0, len(devices))
	for _, d := range devices {
		resp = append(resp, DeviceResponse{
			Device:         d,
			ConsoleShaders: h.buildDeviceShaderMap(d.ID),
		})
	}
	return &ListDevicesOutput{Body: resp}, nil
}

// HumaUpdateDevice is the huma handler for PUT /api/user/devices/{id}.
func (h *DeviceHandler) HumaUpdateDevice(ctx context.Context, in *UpdateDeviceInput) (*UpdateDeviceOutput, error) {
	uid := UserIDFromContext(ctx)

	if in.Body.Name == "" {
		return nil, huma.Error400BadRequest("invalid request body")
	}

	var device db.Device
	if err := h.DB.First(&device, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("device not found")
	}
	if device.UserID != uid {
		return nil, huma.Error403Forbidden("access denied")
	}

	device.Name = in.Body.Name
	if err := h.DB.Save(&device).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update device")
	}
	return &UpdateDeviceOutput{Body: device}, nil
}

// HumaDeleteDevice is the huma handler for DELETE /api/user/devices/{id}.
func (h *DeviceHandler) HumaDeleteDevice(ctx context.Context, in *DeleteDeviceInput) (*DeleteDeviceOutput, error) {
	uid := UserIDFromContext(ctx)

	var device db.Device
	if err := h.DB.First(&device, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("device not found")
	}
	if device.UserID != uid {
		return nil, huma.Error403Forbidden("access denied")
	}

	h.DB.Where("device_id = ?", device.ID).Delete(&db.DeviceShaderPreference{})
	if err := h.DB.Delete(&device).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to delete device")
	}
	return &DeleteDeviceOutput{Body: MessageResponse{Message: "device deleted"}}, nil
}

// HumaGetDevicePreferences is the huma handler for GET /api/user/devices/{id}/preferences.
func (h *DeviceHandler) HumaGetDevicePreferences(ctx context.Context, in *GetDevicePreferencesInput) (*GetDevicePreferencesOutput, error) {
	uid := UserIDFromContext(ctx)

	var device db.Device
	if err := h.DB.First(&device, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("device not found")
	}
	if device.UserID != uid {
		return nil, huma.Error403Forbidden("access denied")
	}

	return &GetDevicePreferencesOutput{Body: DevicePreferencesResponse{
		ConsoleShaders: h.buildDeviceShaderMap(device.ID),
	}}, nil
}

// HumaUpdateDevicePreferences is the huma handler for PUT /api/user/devices/{id}/preferences.
func (h *DeviceHandler) HumaUpdateDevicePreferences(ctx context.Context, in *UpdateDevicePreferencesInput) (*UpdateDevicePreferencesOutput, error) {
	uid := UserIDFromContext(ctx)

	var device db.Device
	if err := h.DB.First(&device, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("device not found")
	}
	if device.UserID != uid {
		return nil, huma.Error403Forbidden("access denied")
	}

	for consoleAbbr, shader := range in.Body.ConsoleShaders {
		var console db.Console
		if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleAbbr).First(&console).Error; err != nil {
			continue
		}
		if shader == "" || shader == "none" {
			h.DB.Where("device_id = ? AND console_id = ?", device.ID, console.ID).
				Delete(&db.DeviceShaderPreference{})
		} else {
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

	return &UpdateDevicePreferencesOutput{Body: DevicePreferencesResponse{
		ConsoleShaders: h.buildDeviceShaderMap(device.ID),
	}}, nil
}

// HumaAdminGetUserDevices is the huma handler for GET /api/admin/users/{id}/devices.
func (h *DeviceHandler) HumaAdminGetUserDevices(_ context.Context, in *AdminGetUserDevicesInput) (*AdminGetUserDevicesOutput, error) {
	var user db.User
	if err := h.DB.First(&user, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}

	var devices []db.Device
	if err := h.DB.Where("user_id = ?", user.ID).Order("last_seen_at desc").Find(&devices).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch devices")
	}

	resp := make([]DeviceResponse, 0, len(devices))
	for _, d := range devices {
		resp = append(resp, DeviceResponse{
			Device:         d,
			ConsoleShaders: h.buildDeviceShaderMap(d.ID),
		})
	}
	return &AdminGetUserDevicesOutput{Body: resp}, nil
}
