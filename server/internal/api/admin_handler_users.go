package api

import (
	"fmt"
	"log/slog"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

func (h *AdminHandler) ListUsers(c *gin.Context) {
	var users []db.User
	if err := h.DB.Find(&users).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch users"})
		return
	}

	resp := make([]UserResponse, len(users))
	for i, u := range users {
		resp[i] = ToUserResponse(u)
	}
	c.JSON(http.StatusOK, resp)
}

// CreateUser creates a new user account (admin only).
func (h *AdminHandler) CreateUser(c *gin.Context) {
	var req struct {
		Username string       `json:"username" binding:"required,min=3,max=64"`
		Email    string       `json:"email" binding:"required,email"`
		Password string       `json:"password" binding:"required,min=8,max=72"`
		Role     db.UserRole  `json:"role"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request body"})
		return
	}

	if req.Role == "" {
		req.Role = db.RoleUser
	}
	if req.Role != db.RoleAdmin && req.Role != db.RoleUser {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "role must be 'admin' or 'user'"})
		return
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to hash password"})
		return
	}

	adminID, _ := c.Get("userId")
	var user db.User
	if err := h.DB.Transaction(func(tx *gorm.DB) error {
		var count int64
		tx.Model(&db.User{}).Where("username = ? OR email = ?", req.Username, req.Email).Count(&count)
		if count > 0 {
			c.JSON(http.StatusConflict, ErrorResponse{Error: "username or email already exists"})
			return fmt.Errorf("duplicate")
		}

		user = db.User{
			Username:     req.Username,
			Email:        req.Email,
			PasswordHash: hash,
			Role:         req.Role,
		}
		return tx.Create(&user).Error
	}); err != nil {
		if c.Writer.Written() {
			return
		}
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create user"})
		return
	}

	slog.Info("audit: admin created user", "admin_id", adminID, "username", req.Username, "role", req.Role)
	c.JSON(http.StatusCreated, ToUserResponse(user))
}

// UpdateUser updates a user's role or details (admin only).
func (h *AdminHandler) UpdateUser(c *gin.Context) {
	id := c.Param("id")
	var user db.User
	if err := h.DB.First(&user, id).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "user not found"})
		return
	}

	var req struct {
		Role            db.UserRole `json:"role"`
		Email           string      `json:"email"`
		Password        string      `json:"password"`
		Disabled        *bool       `json:"disabled"`
		PendingApproval *bool       `json:"pendingApproval"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request body"})
		return
	}

	currentUserID, _ := c.Get("userId")

	// Owner protection
	if user.Role == db.RoleOwner && req.Role != "" {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "cannot change the owner's role"})
		return
	}
	if req.Role == db.RoleOwner {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "cannot assign owner role"})
		return
	}
	if currentUserID == user.ID && req.Role != "" && req.Role != user.Role {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "cannot change your own role"})
		return
	}

	if req.Role != "" {
		if req.Role != db.RoleAdmin && req.Role != db.RoleUser {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "role must be 'admin' or 'user'"})
			return
		}
		user.Role = req.Role
	}
	if req.Email != "" {
		user.Email = req.Email
	}
	if req.Password != "" {
		if user.Role == db.RoleOwner {
			c.JSON(http.StatusForbidden, ErrorResponse{Error: "cannot change the owner's password"})
			return
		}
		if len(req.Password) < 8 {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "password must be at least 8 characters"})
			return
		}
		hash, err := auth.HashPassword(req.Password)
		if err != nil {
			c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to hash password"})
			return
		}
		user.PasswordHash = hash
	}
	if req.Disabled != nil {
		if user.Role == db.RoleOwner {
			c.JSON(http.StatusForbidden, ErrorResponse{Error: "cannot disable the owner"})
			return
		}
		user.Disabled = *req.Disabled
	}
	if req.PendingApproval != nil {
		user.PendingApproval = *req.PendingApproval
	}

	// Invalidate all existing tokens if security-sensitive fields changed
	if req.Role != "" || req.Password != "" || req.Disabled != nil || req.PendingApproval != nil {
		user.TokenVersion++
	}

	if err := h.DB.Save(&user).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to update user"})
		return
	}

	slog.Info("audit: admin updated user", "admin_id", currentUserID, "target_user", user.Username,
		"changed_role", req.Role != "", "changed_password", req.Password != "",
		"changed_disabled", req.Disabled != nil, "changed_pending_approval", req.PendingApproval != nil)

	c.JSON(http.StatusOK, ToUserResponse(user))
}

func (h *AdminHandler) DeleteUser(c *gin.Context) {
	id := c.Param("id")
	var user db.User
	if err := h.DB.First(&user, id).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "user not found"})
		return
	}

	if user.Role == db.RoleOwner {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "cannot delete the owner"})
		return
	}

	currentUserID, _ := c.Get("userId")
	if currentUserID == user.ID {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "cannot delete yourself"})
		return
	}

	err := h.DB.Transaction(func(tx *gorm.DB) error {
		uid := user.ID

		// Per-user preferences
		tx.Where("user_id = ?", uid).Delete(&db.ConsoleShaderPreference{})
		tx.Where("user_id = ?", uid).Delete(&db.ConsoleKeyMappingPreference{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.GameKeyMappingPreference{})

		// Devices + device shader preferences
		var devices []db.Device
		tx.Where("user_id = ?", uid).Find(&devices)
		for _, d := range devices {
			tx.Where("device_id = ?", d.ID).Delete(&db.DeviceShaderPreference{})
		}
		tx.Where("user_id = ?", uid).Delete(&db.Device{})

		// Social / community data
		tx.Where("user_id = ?", uid).Delete(&db.Favorite{})
		tx.Where("user_id = ?", uid).Delete(&db.PlayHistory{})
		tx.Where("user_id = ?", uid).Delete(&db.GameRating{})
		tx.Where("user_id = ?", uid).Delete(&db.PlayLaterItem{})
		tx.Where("user_id = ?", uid).Delete(&db.ActivityEvent{})

		// Collections (+ items for owned collections)
		var collections []db.GameCollection
		tx.Where("user_id = ?", uid).Find(&collections)
		for _, col := range collections {
			tx.Where("collection_id = ?", col.ID).Delete(&db.CollectionItem{})
		}
		tx.Where("user_id = ?", uid).Delete(&db.GameCollection{})

		// Shared save states (+ delete files)
		var sharedSaves []db.SharedSaveState
		tx.Where("user_id = ?", uid).Find(&sharedSaves)
		for _, ss := range sharedSaves {
			if h.Storage != nil {
				h.Storage.DeleteSave(ss.FilePath)
			}
		}
		tx.Where("user_id = ?", uid).Delete(&db.SharedSaveState{})

		// RetroAchievements
		tx.Where("user_id = ?", uid).Delete(&db.RetroAchievementCredential{})

		// Challenges
		tx.Where("user_id = ?", uid).Delete(&db.ChallengeAttempt{})

		// Shared Sessions
		tx.Where("user_id = ?", uid).Delete(&db.SharedSessionMember{})
		tx.Where("inviter_id = ? OR invitee_id = ?", uid, uid).Delete(&db.SharedSessionInvite{})

		// Netplay
		tx.Where("host_user_id = ? OR client_user_id = ?", uid, uid).Delete(&db.NetplaySession{})

		// Auth / security
		tx.Where("user_id = ?", uid).Delete(&db.RefreshToken{})

		// Finally delete the user
		tx.Delete(&user)
		return nil
	})
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to delete user"})
		return
	}

	slog.Info("audit: admin deleted user", "admin_id", currentUserID, "target_user", user.Username)
	c.JSON(http.StatusOK, gin.H{"message": "user deleted"})
}

// ListDeletedUsers returns soft-deleted users (admin only).
func (h *AdminHandler) ListDeletedUsers(c *gin.Context) {
	var users []db.User
	if err := h.DB.Unscoped().Where("deleted_at IS NOT NULL").Find(&users).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch deleted users"})
		return
	}

	resp := make([]DeletedUserResponse, len(users))
	for i, u := range users {
		resp[i] = ToDeletedUserResponse(u)
	}
	c.JSON(http.StatusOK, resp)
}

// HardDeleteUser permanently removes a soft-deleted user and all their data (admin only).
func (h *AdminHandler) HardDeleteUser(c *gin.Context) {
	id := c.Param("id")
	var user db.User
	if err := h.DB.Unscoped().First(&user, id).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "user not found"})
		return
	}

	if !user.DeletedAt.Valid {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "user is not soft-deleted; use the regular delete endpoint first"})
		return
	}

	if user.Role == db.RoleOwner {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "cannot permanently delete the owner"})
		return
	}

	err := h.DB.Transaction(func(tx *gorm.DB) error {
		uid := user.ID

		// Per-user preferences
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.ConsoleShaderPreference{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.ConsoleKeyMappingPreference{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.GameKeyMappingPreference{})

		// Devices + device shader preferences
		var devices []db.Device
		tx.Unscoped().Where("user_id = ?", uid).Find(&devices)
		for _, d := range devices {
			tx.Unscoped().Where("device_id = ?", d.ID).Delete(&db.DeviceShaderPreference{})
		}
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.Device{})

		// Social / community data
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.Favorite{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.PlayHistory{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.GameRating{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.PlayLaterItem{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.ActivityEvent{})

		// Collections (+ items for owned collections)
		var collections []db.GameCollection
		tx.Unscoped().Where("user_id = ?", uid).Find(&collections)
		for _, col := range collections {
			tx.Unscoped().Where("collection_id = ?", col.ID).Delete(&db.CollectionItem{})
		}
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.GameCollection{})

		// Shared save states (+ delete files)
		var sharedSaves []db.SharedSaveState
		tx.Unscoped().Where("user_id = ?", uid).Find(&sharedSaves)
		for _, ss := range sharedSaves {
			if h.Storage != nil {
				h.Storage.DeleteSave(ss.FilePath)
			}
		}
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.SharedSaveState{})

		// RetroAchievements
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.RetroAchievementCredential{})
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.UserAchievementProgress{})

		// Challenges
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.ChallengeAttempt{})

		// Shared Sessions
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.SharedSessionMember{})
		tx.Unscoped().Where("inviter_id = ? OR invitee_id = ?", uid, uid).Delete(&db.SharedSessionInvite{})

		// Netplay
		tx.Unscoped().Where("host_user_id = ? OR client_user_id = ?", uid, uid).Delete(&db.NetplaySession{})

		// Auth / security
		tx.Unscoped().Where("user_id = ?", uid).Delete(&db.RefreshToken{})

		// Permanently delete the user
		return tx.Unscoped().Delete(&user).Error
	})
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to permanently delete user"})
		return
	}

	currentUserID, _ := c.Get("userId")
	slog.Info("audit: admin permanently deleted user", "admin_id", currentUserID, "target_user", user.Username)
	c.JSON(http.StatusOK, gin.H{"message": "user permanently deleted"})
}
