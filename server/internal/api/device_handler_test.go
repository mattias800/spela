package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestRegisterDevice_NewDevice(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]string{
		"deviceUuid": "uuid-1234",
		"name":       "My Pixel",
		"platform":   "android",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/devices", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, "uuid-1234", resp["deviceUuid"])
	assert.Equal(t, "My Pixel", resp["name"])
	assert.Equal(t, "android", resp["platform"])
	assert.NotNil(t, resp["consoleShaders"])
}

func TestRegisterDevice_UpsertExisting(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]string{
		"deviceUuid": "uuid-1234",
		"name":       "My Pixel",
		"platform":   "android",
	})

	// First registration
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/devices", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Upsert with new name
	body, _ = json.Marshal(map[string]string{
		"deviceUuid": "uuid-1234",
		"name":       "Pixel Renamed",
		"platform":   "android",
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/user/devices", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "Pixel Renamed", resp["name"])

	// Should still be only one device
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/devices", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var devices []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &devices)
	assert.Len(t, devices, 1)
}

func TestRegisterDevice_MissingFields(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	tests := []struct {
		name string
		body map[string]string
	}{
		{"missing deviceUuid", map[string]string{"name": "Phone", "platform": "android"}},
		{"missing name", map[string]string{"deviceUuid": "uuid-1", "platform": "android"}},
		{"missing platform", map[string]string{"deviceUuid": "uuid-1", "name": "Phone"}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body, _ := json.Marshal(tt.body)
			w := httptest.NewRecorder()
			req := httptest.NewRequest("POST", "/api/user/devices", bytes.NewReader(body))
			req.Header.Set("Authorization", "Bearer "+token)
			req.Header.Set("Content-Type", "application/json")
			router.ServeHTTP(w, req)
			assert.Equal(t, http.StatusBadRequest, w.Code)
		})
	}
}

func TestGetDevices_Empty(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/devices", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var devices []map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &devices)
	require.NoError(t, err)
	assert.Len(t, devices, 0)
}

func TestGetDevices_MultipleDevicesWithShaders(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Register two devices
	for _, d := range []map[string]string{
		{"deviceUuid": "uuid-a", "name": "Phone", "platform": "android"},
		{"deviceUuid": "uuid-b", "name": "Desktop", "platform": "linux"},
	} {
		body, _ := json.Marshal(d)
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/user/devices", bytes.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+token)
		req.Header.Set("Content-Type", "application/json")
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusOK, w.Code)
	}

	// Set shader on first device
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/devices", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var devices []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &devices)
	assert.Len(t, devices, 2)
	// Each should have consoleShaders map
	for _, d := range devices {
		_, ok := d["consoleShaders"].(map[string]interface{})
		assert.True(t, ok, "each device should have consoleShaders")
	}
}

func TestUpdateDevice_Rename(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Register device
	body, _ := json.Marshal(map[string]string{
		"deviceUuid": "uuid-1", "name": "Old Name", "platform": "macos",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/devices", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var registered map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &registered)
	deviceID := fmt.Sprintf("%.0f", registered["id"].(float64))

	// Update name
	body, _ = json.Marshal(map[string]string{"name": "New Name"})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/devices/"+deviceID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var updated map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &updated)
	assert.Equal(t, "New Name", updated["name"])
}

func TestUpdateDevice_Unauthorized(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Create a device belonging to a different user
	database.Create(&db.User{Username: "other", Email: "other@x.com", PasswordHash: "x", Role: "user"})
	var otherUser db.User
	database.Where("username = ?", "other").First(&otherUser)
	database.Create(&db.Device{UserID: otherUser.ID, DeviceUUID: "other-uuid", Name: "Other Phone", Platform: "android"})
	var otherDevice db.Device
	database.Where("device_uuid = ?", "other-uuid").First(&otherDevice)

	body, _ := json.Marshal(map[string]string{"name": "Hacked"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", fmt.Sprintf("/api/user/devices/%d", otherDevice.ID), bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestUpdateDevice_NotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]string{"name": "Nope"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/devices/999", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestDeleteDevice_Success(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Register device
	body, _ := json.Marshal(map[string]string{
		"deviceUuid": "uuid-del", "name": "Delete Me", "platform": "android",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/devices", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var registered map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &registered)
	deviceID := fmt.Sprintf("%.0f", registered["id"].(float64))
	deviceIDUint := uint(registered["id"].(float64))

	// Add a shader preference to the device directly
	database.Create(&db.DeviceShaderPreference{DeviceID: deviceIDUint, ConsoleID: 1, Shader: "crt-simple"})

	// Delete
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/user/devices/"+deviceID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify device is gone
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/devices", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var devices []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &devices)
	assert.Len(t, devices, 0)

	// Verify shader preferences are also deleted
	var count int64
	database.Model(&db.DeviceShaderPreference{}).Where("device_id = ?", deviceIDUint).Count(&count)
	assert.Equal(t, int64(0), count)
}

func TestDeleteDevice_Unauthorized(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Create device owned by different user
	database.Create(&db.User{Username: "other2", Email: "other2@x.com", PasswordHash: "x", Role: "user"})
	var otherUser db.User
	database.Where("username = ?", "other2").First(&otherUser)
	database.Create(&db.Device{UserID: otherUser.ID, DeviceUUID: "other-uuid-2", Name: "Other", Platform: "linux"})
	var otherDevice db.Device
	database.Where("device_uuid = ?", "other-uuid-2").First(&otherDevice)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", fmt.Sprintf("/api/user/devices/%d", otherDevice.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestDeleteDevice_NotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/user/devices/999", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetDevicePreferences_Empty(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Register device
	body, _ := json.Marshal(map[string]string{
		"deviceUuid": "uuid-prefs", "name": "Prefs Device", "platform": "android",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/devices", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var registered map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &registered)
	deviceID := fmt.Sprintf("%.0f", registered["id"].(float64))

	// Get preferences
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/devices/"+deviceID+"/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	consoleShaders := resp["consoleShaders"].(map[string]interface{})
	assert.Empty(t, consoleShaders)
}

func TestGetDevicePreferences_Unauthorized(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	database.Create(&db.User{Username: "other3", Email: "other3@x.com", PasswordHash: "x", Role: "user"})
	var otherUser db.User
	database.Where("username = ?", "other3").First(&otherUser)
	database.Create(&db.Device{UserID: otherUser.ID, DeviceUUID: "other-uuid-3", Name: "Other", Platform: "linux"})
	var otherDevice db.Device
	database.Where("device_uuid = ?", "other-uuid-3").First(&otherDevice)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/user/devices/%d/preferences", otherDevice.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestUpdateDevicePreferences_AddUpdateDelete(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Register device
	body, _ := json.Marshal(map[string]string{
		"deviceUuid": "uuid-shader", "name": "Shader Device", "platform": "macos",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/devices", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var registered map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &registered)
	deviceID := fmt.Sprintf("%.0f", registered["id"].(float64))

	// Add shaders
	body, _ = json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"nes": "crt-royale", "snes": "lcd-grid"},
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/devices/"+deviceID+"/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	shaders := resp["consoleShaders"].(map[string]interface{})
	assert.Equal(t, "crt-royale", shaders["nes"])
	assert.Equal(t, "lcd-grid", shaders["snes"])

	// Update one shader
	body, _ = json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"nes": "crt-simple"},
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/devices/"+deviceID+"/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &resp)
	shaders = resp["consoleShaders"].(map[string]interface{})
	assert.Equal(t, "crt-simple", shaders["nes"])
	assert.Equal(t, "lcd-grid", shaders["snes"])

	// Delete one shader
	body, _ = json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"snes": "none"},
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/devices/"+deviceID+"/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &resp)
	shaders = resp["consoleShaders"].(map[string]interface{})
	assert.Equal(t, "crt-simple", shaders["nes"])
	_, exists := shaders["snes"]
	assert.False(t, exists, "shader for console 2 should be removed")
}

func TestUpdateDevicePreferences_Unauthorized(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	database.Create(&db.User{Username: "other4", Email: "other4@x.com", PasswordHash: "x", Role: "user"})
	var otherUser db.User
	database.Where("username = ?", "other4").First(&otherUser)
	database.Create(&db.Device{UserID: otherUser.ID, DeviceUUID: "other-uuid-4", Name: "Other", Platform: "linux"})
	var otherDevice db.Device
	database.Where("device_uuid = ?", "other-uuid-4").First(&otherDevice)

	body, _ := json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"nes": "crt-royale"},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", fmt.Sprintf("/api/user/devices/%d/preferences", otherDevice.ID), bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestAdminGetUserDevices_Success(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router) // owner/admin

	// Get the admin user's ID
	var adminUser db.User
	database.Where("username = ?", "apitest").First(&adminUser)

	// Register a device for the admin user
	body, _ := json.Marshal(map[string]string{
		"deviceUuid": "admin-uuid", "name": "Admin Phone", "platform": "android",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/devices", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Admin lists their own devices via admin endpoint
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", fmt.Sprintf("/api/admin/users/%d/devices", adminUser.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var devices []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &devices)
	assert.Len(t, devices, 1)
	assert.Equal(t, "Admin Phone", devices[0]["name"])
}

func TestAdminGetUserDevices_NonAdmin(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)

	// Register first user (becomes owner)
	registerAndGetToken(t, router)

	// Register second user (regular user)
	body, _ := json.Marshal(map[string]string{
		"username": "regular",
		"email":    "regular@example.com",
		"password": "password123",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var regResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &regResp)
	userToken := regResp["accessToken"].(string)

	var adminUser db.User
	database.Where("username = ?", "apitest").First(&adminUser)

	// Regular user tries admin endpoint
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", fmt.Sprintf("/api/admin/users/%d/devices", adminUser.ID), nil)
	req.Header.Set("Authorization", "Bearer "+userToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}
