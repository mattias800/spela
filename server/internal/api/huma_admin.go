package api

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Admin settings ---

// GetAdminSettingsInput is the input for GET /api/admin/settings.
type GetAdminSettingsInput struct{}

// AdminSettingsResponse is the wire format: a flat map of key → string value.
// Secret values are masked with "********" on GET.
type AdminSettingsResponse map[string]string

// GetAdminSettingsOutput wraps the settings map.
type GetAdminSettingsOutput struct {
	Body AdminSettingsResponse
}

// UpdateAdminSettingsInput is the input for PUT /api/admin/settings.
type UpdateAdminSettingsInput struct {
	Body map[string]string
}

// UpdateAdminSettingsOutput wraps the update-settings success message.
type UpdateAdminSettingsOutput struct {
	Body MessageResponse
}

// --- Admin stats ---

// GetAdminStatsInput is the input for GET /api/admin/stats.
type GetAdminStatsInput struct{}

// AdminStatsResponse is the wire format for admin dashboard counters.
type AdminStatsResponse struct {
	Users    int64 `json:"users"`
	Games    int64 `json:"games"`
	Consoles int64 `json:"consoles"`
	Saves    int64 `json:"saves"`
}

// GetAdminStatsOutput wraps the admin-stats body.
type GetAdminStatsOutput struct {
	Body AdminStatsResponse
}

// --- Admin user management ---

// ListAdminUsersInput is the input for GET /api/admin/users. No pagination
// params — the raw gin handler returns the full list.
type ListAdminUsersInput struct{}

// ListAdminUsersOutput wraps the flat user list.
type ListAdminUsersOutput struct {
	Body []UserResponse
}

// AdminCreateUserInput is the input for POST /api/admin/users.
type AdminCreateUserInput struct {
	Body AdminCreateUserRequest
}

// AdminCreateUserOutput wraps the created user response (201 Created).
type AdminCreateUserOutput struct {
	Body UserResponse
}

// AdminUpdateUserInput is the input for PUT /api/admin/users/{id}.
type AdminUpdateUserInput struct {
	ID   string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"User ID."`
	Body AdminUpdateUserRequest
}

// AdminUpdateUserOutput wraps the updated user response.
type AdminUpdateUserOutput struct {
	Body UserResponse
}

// AdminDeleteUserInput is the input for DELETE /api/admin/users/{id}.
type AdminDeleteUserInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"User ID."`
}

// AdminDeleteUserOutput wraps the delete success message.
type AdminDeleteUserOutput struct {
	Body MessageResponse
}

// RegisterAdminRoutes wires the admin settings / stats / user-management
// endpoints into the huma API. All operations require RequireAdmin on top of
// RequireAuth.
func RegisterAdminRoutes(api huma.API, h *AdminHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	requireAdmin := RequireAdmin(api)
	mw := huma.Middlewares{requireAuth, rateLimit, requireAdmin}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getAdminSettings",
		Method:      http.MethodGet,
		Path:        "/api/admin/settings",
		Summary:     "Get server settings",
		Description: "Admin-only. Returns server settings as a flat key/value map; secret keys (client secrets, API keys) are masked with ********.",
		Tags:        []string{"admin"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetAdminSettings)

	huma.Register(api, huma.Operation{
		OperationID: "updateAdminSettings",
		Method:      http.MethodPut,
		Path:        "/api/admin/settings",
		Summary:     "Update server settings",
		Description: "Admin-only. Updates server settings. Only allowlisted keys are accepted. Secret keys whose value is the mask placeholder are skipped.",
		Tags:        []string{"admin"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateAdminSettings)

	huma.Register(api, huma.Operation{
		OperationID: "getAdminStats",
		Method:      http.MethodGet,
		Path:        "/api/admin/stats",
		Summary:     "Get admin dashboard stats",
		Description: "Admin-only. Returns total counts of users, games, consoles with games, and session saves.",
		Tags:        []string{"admin"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetAdminStats)

	huma.Register(api, huma.Operation{
		OperationID: "listAdminUsers",
		Method:      http.MethodGet,
		Path:        "/api/admin/users",
		Summary:     "List all users",
		Description: "Admin-only. Returns the full list of (non-soft-deleted) user records.",
		Tags:        []string{"admin"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListAdminUsers)

	huma.Register(api, huma.Operation{
		OperationID:   "adminCreateUser",
		Method:        http.MethodPost,
		Path:          "/api/admin/users",
		Summary:       "Create a user",
		Description:   "Admin-only. Creates a new user account with a hashed password. Role defaults to 'user' if not specified; 'owner' is rejected.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"admin"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaAdminCreateUser)

	huma.Register(api, huma.Operation{
		OperationID: "adminUpdateUser",
		Method:      http.MethodPut,
		Path:        "/api/admin/users/{id}",
		Summary:     "Update a user",
		Description: "Admin-only. Updates role, password, disabled or pendingApproval fields. Owner role is protected from changes.",
		Tags:        []string{"admin"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaAdminUpdateUser)

	huma.Register(api, huma.Operation{
		OperationID: "adminDeleteUser",
		Method:      http.MethodDelete,
		Path:        "/api/admin/users/{id}",
		Summary:     "Delete a user",
		Description: "Admin-only. Soft-deletes a user and cascades to per-user preferences, devices, social data, collections, shared saves, RA credentials, challenges, shared sessions, netplay sessions and refresh tokens.",
		Tags:        []string{"admin"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaAdminDeleteUser)
}

// --- Handlers ---

// HumaGetAdminSettings is the huma handler for GET /api/admin/settings.
func (h *AdminHandler) HumaGetAdminSettings(_ context.Context, _ *GetAdminSettingsInput) (*GetAdminSettingsOutput, error) {
	var settings []db.ServerSetting
	if err := h.DB.Find(&settings).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch settings")
	}
	settingsMap := make(AdminSettingsResponse, len(settings))
	for _, s := range settings {
		if secretSettingKeys[s.Key] && s.Value != "" {
			settingsMap[s.Key] = secretMaskPlaceholder
		} else {
			settingsMap[s.Key] = s.Value
		}
	}
	return &GetAdminSettingsOutput{Body: settingsMap}, nil
}

// HumaUpdateAdminSettings is the huma handler for PUT /api/admin/settings.
func (h *AdminHandler) HumaUpdateAdminSettings(ctx context.Context, in *UpdateAdminSettingsInput) (*UpdateAdminSettingsOutput, error) {
	adminID := UserIDFromContext(ctx)
	var changedKeys []string
	for key, value := range in.Body {
		if !allowedSettingKeys[key] {
			continue
		}
		if secretSettingKeys[key] && value == secretMaskPlaceholder {
			continue
		}
		// Encrypt secret values at rest (#1318); non-secret settings are
		// stored as-is.
		stored := value
		if secretSettingKeys[key] {
			enc, err := encryptSecretSetting(value)
			if err != nil {
				return nil, huma.Error500InternalServerError("failed to encrypt setting")
			}
			stored = enc
		}
		setting := db.ServerSetting{Key: key, Value: stored}
		h.DB.Where("key = ?", key).Assign(setting).FirstOrCreate(&setting)
		changedKeys = append(changedKeys, key)
	}

	slog.Info("audit: admin updated settings", "admin_id", adminID, "changed_keys", changedKeys)
	return &UpdateAdminSettingsOutput{Body: MessageResponse{Message: "settings updated"}}, nil
}

// HumaGetAdminStats is the huma handler for GET /api/admin/stats.
func (h *AdminHandler) HumaGetAdminStats(_ context.Context, _ *GetAdminStatsInput) (*GetAdminStatsOutput, error) {
	var users, games, consoles, saves int64
	h.DB.Model(&db.User{}).Count(&users)
	h.DB.Model(&db.Game{}).Count(&games)
	h.DB.Model(&db.Console{}).Where("id IN (SELECT DISTINCT console_id FROM games)").Count(&consoles)
	h.DB.Model(&db.SessionSaveState{}).Count(&saves)

	return &GetAdminStatsOutput{Body: AdminStatsResponse{
		Users:    users,
		Games:    games,
		Consoles: consoles,
		Saves:    saves,
	}}, nil
}

// HumaListAdminUsers is the huma handler for GET /api/admin/users.
func (h *AdminHandler) HumaListAdminUsers(_ context.Context, _ *ListAdminUsersInput) (*ListAdminUsersOutput, error) {
	var users []db.User
	if err := h.DB.Find(&users).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch users")
	}
	resp := make([]UserResponse, len(users))
	for i, u := range users {
		resp[i] = ToUserResponse(u)
	}
	return &ListAdminUsersOutput{Body: resp}, nil
}

// HumaAdminCreateUser is the huma handler for POST /api/admin/users.
func (h *AdminHandler) HumaAdminCreateUser(ctx context.Context, in *AdminCreateUserInput) (*AdminCreateUserOutput, error) {
	req := in.Body
	if req.Role == "" {
		req.Role = db.RoleUser
	}
	if req.Role != db.RoleAdmin && req.Role != db.RoleUser {
		return nil, huma.Error400BadRequest("role must be 'admin' or 'user'")
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to hash password")
	}

	adminID := UserIDFromContext(ctx)
	var user db.User
	var conflict bool
	if err := h.DB.Transaction(func(tx *gorm.DB) error {
		var count int64
		tx.Model(&db.User{}).Where("username = ?", req.Username).Count(&count)
		if count > 0 {
			conflict = true
			return fmt.Errorf("duplicate")
		}

		user = db.User{
			Username:     req.Username,
			PasswordHash: hash,
			Role:         req.Role,
		}
		return tx.Create(&user).Error
	}); err != nil {
		if conflict {
			return nil, huma.Error409Conflict("username already exists")
		}
		return nil, huma.Error500InternalServerError("failed to create user")
	}

	slog.Info("audit: admin created user", "admin_id", adminID, "username", req.Username, "role", req.Role)
	return &AdminCreateUserOutput{Body: ToUserResponse(user)}, nil
}

// HumaAdminUpdateUser is the huma handler for PUT /api/admin/users/{id}.
func (h *AdminHandler) HumaAdminUpdateUser(ctx context.Context, in *AdminUpdateUserInput) (*AdminUpdateUserOutput, error) {
	var user db.User
	if err := h.DB.First(&user, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}

	req := in.Body
	currentUserID := UserIDFromContext(ctx)

	// Issue #1122: only owner can mutate other admins. Otherwise a
	// single compromised admin account can demote/disable/lock out
	// every other admin and become the sole non-owner administrator.
	// The owner is exempt from this rule (they have ultimate control)
	// and the existing checks below cover owner-as-target.
	var caller db.User
	if err := h.DB.Select("id", "role").First(&caller, currentUserID).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to load caller")
	}
	if user.Role == db.RoleAdmin && caller.Role != db.RoleOwner && caller.ID != user.ID {
		return nil, huma.Error403Forbidden("only the owner can modify other admins")
	}

	if user.Role == db.RoleOwner && req.Role != "" {
		return nil, huma.Error403Forbidden("cannot change the owner's role")
	}
	if req.Role == db.RoleOwner {
		return nil, huma.Error400BadRequest("cannot assign owner role")
	}
	if currentUserID == user.ID && req.Role != "" && req.Role != user.Role {
		return nil, huma.Error400BadRequest("cannot change your own role")
	}

	if req.Role != "" {
		if req.Role != db.RoleAdmin && req.Role != db.RoleUser {
			return nil, huma.Error400BadRequest("role must be 'admin' or 'user'")
		}
		user.Role = req.Role
	}
	if req.Password != "" {
		if user.Role == db.RoleOwner {
			return nil, huma.Error403Forbidden("cannot change the owner's password")
		}
		// Issue #1123: only owner may change another admin's password.
		if user.Role == db.RoleAdmin && caller.Role != db.RoleOwner && caller.ID != user.ID {
			return nil, huma.Error403Forbidden("only the owner can change another admin's password")
		}
		if len(req.Password) < 8 {
			return nil, huma.Error400BadRequest("password must be at least 8 characters")
		}
		hash, err := auth.HashPassword(req.Password)
		if err != nil {
			return nil, huma.Error500InternalServerError("failed to hash password")
		}
		user.PasswordHash = hash
	}
	if req.Disabled != nil {
		if user.Role == db.RoleOwner {
			return nil, huma.Error403Forbidden("cannot disable the owner")
		}
		user.Disabled = *req.Disabled
	}
	if req.PendingApproval != nil {
		user.PendingApproval = *req.PendingApproval
	}
	if req.CanImportGames != nil {
		user.CanImportGames = *req.CanImportGames
	}

	if req.Role != "" || req.Password != "" || req.Disabled != nil || req.PendingApproval != nil {
		user.TokenVersion++
	}

	if err := h.DB.Save(&user).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update user")
	}

	slog.Info("audit: admin updated user", "admin_id", currentUserID, "target_user", user.Username,
		"changed_role", req.Role != "", "changed_password", req.Password != "",
		"changed_disabled", req.Disabled != nil, "changed_pending_approval", req.PendingApproval != nil,
		"changed_can_import_games", req.CanImportGames != nil)

	return &AdminUpdateUserOutput{Body: ToUserResponse(user)}, nil
}

// HumaAdminDeleteUser is the huma handler for DELETE /api/admin/users/{id}.
func (h *AdminHandler) HumaAdminDeleteUser(ctx context.Context, in *AdminDeleteUserInput) (*AdminDeleteUserOutput, error) {
	var user db.User
	if err := h.DB.First(&user, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}

	if user.Role == db.RoleOwner {
		return nil, huma.Error403Forbidden("cannot delete the owner")
	}

	currentUserID := UserIDFromContext(ctx)
	if currentUserID == user.ID {
		return nil, huma.Error400BadRequest("cannot delete yourself")
	}

	// Issue #1122: only owner can soft-delete another admin.
	var caller db.User
	if err := h.DB.Select("id", "role").First(&caller, currentUserID).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to load caller")
	}
	if user.Role == db.RoleAdmin && caller.Role != db.RoleOwner {
		return nil, huma.Error403Forbidden("only the owner can delete other admins")
	}

	err := h.DB.Transaction(func(tx *gorm.DB) error {
		uid := user.ID

		tx.Where("user_id = ?", uid).Delete(&db.ConsoleShaderPreference{})
		tx.Where("user_id = ?", uid).Delete(&db.ConsoleRenderScalePreference{})
		tx.Where("user_id = ?", uid).Delete(&db.ConsoleKeyMappingPreference{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.GameKeyMappingPreference{})

		var devices []db.Device
		tx.Where("user_id = ?", uid).Find(&devices)
		for _, d := range devices {
			tx.Where("device_id = ?", d.ID).Delete(&db.DeviceShaderPreference{})
		}
		tx.Where("user_id = ?", uid).Delete(&db.Device{})

		tx.Where("user_id = ?", uid).Delete(&db.Favorite{})
		tx.Where("user_id = ?", uid).Delete(&db.PlayHistory{})
		tx.Where("user_id = ?", uid).Delete(&db.GameRating{})
		tx.Where("user_id = ?", uid).Delete(&db.PlayLaterItem{})
		tx.Where("user_id = ?", uid).Delete(&db.ActivityEvent{})

		var collections []db.GameCollection
		tx.Where("user_id = ?", uid).Find(&collections)
		for _, col := range collections {
			tx.Where("collection_id = ?", col.ID).Delete(&db.CollectionItem{})
		}
		tx.Where("user_id = ?", uid).Delete(&db.GameCollection{})

		var sharedSaves []db.SharedSaveState
		tx.Where("user_id = ?", uid).Find(&sharedSaves)
		for _, ss := range sharedSaves {
			if h.Storage != nil {
				h.Storage.DeleteSave(ss.FilePath)
			}
		}
		tx.Where("user_id = ?", uid).Delete(&db.SharedSaveState{})

		tx.Where("user_id = ?", uid).Delete(&db.RetroAchievementCredential{})
		tx.Where("user_id = ?", uid).Delete(&db.ChallengeAttempt{})
		tx.Where("user_id = ?", uid).Delete(&db.SharedSessionMember{})
		tx.Where("inviter_id = ? OR invitee_id = ?", uid, uid).Delete(&db.SharedSessionInvite{})
		tx.Where("host_user_id = ? OR client_user_id = ?", uid, uid).Delete(&db.NetplaySession{})
		tx.Where("user_id = ?", uid).Delete(&db.RefreshToken{})

		tx.Delete(&user)
		return nil
	})
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to delete user")
	}

	slog.Info("audit: admin deleted user", "admin_id", currentUserID, "target_user", user.Username)
	return &AdminDeleteUserOutput{Body: MessageResponse{Message: "user deleted"}}, nil
}
