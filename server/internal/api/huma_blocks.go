package api

import (
	"context"
	"net/http"
	"strconv"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Block list endpoints (issue #1121) ---
//
// Blocks are one-way ("user A blocked user B") but searches/profiles/
// invites filter symmetrically: if either side has a block on the other,
// they don't appear in each other's results. This protects the harassed
// party from continuing exposure even if they neglected to block back.

// ListBlocksInput is the input for GET /api/user/blocks.
type ListBlocksInput struct{}

// BlockedUserResponse is the wire format of a single block list entry.
type BlockedUserResponse struct {
	UserID    string `json:"userId"`
	Username  string `json:"username"`
	AvatarURL string `json:"avatarUrl"`
}

// ListBlocksResponse wraps the caller's block list.
type ListBlocksResponse struct {
	Blocked []BlockedUserResponse `json:"blocked"`
}

// ListBlocksOutput wraps the block list body.
type ListBlocksOutput struct {
	Body ListBlocksResponse
}

// CreateBlockInput is the input for POST /api/user/blocks/{userId}.
type CreateBlockInput struct {
	UserID string `path:"userId" pattern:"^[0-9]+$" maxLength:"20" doc:"User ID to block."`
}

// CreateBlockOutput wraps the created-block confirmation.
type CreateBlockOutput struct {
	Body MessageResponse
}

// DeleteBlockInput is the input for DELETE /api/user/blocks/{userId}.
type DeleteBlockInput struct {
	UserID string `path:"userId" pattern:"^[0-9]+$" maxLength:"20" doc:"User ID to unblock."`
}

// DeleteBlockOutput wraps the unblock confirmation.
type DeleteBlockOutput struct {
	Body MessageResponse
}

// RegisterBlockRoutes wires the block-list endpoints into the huma API.
func RegisterBlockRoutes(api huma.API, h *SocialHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "listBlocks",
		Method:      http.MethodGet,
		Path:        "/api/user/blocks",
		Summary:     "List the caller's blocked users",
		Description: "Returns the users the authenticated caller has blocked.",
		Tags:        []string{"social"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListBlocks)

	huma.Register(api, huma.Operation{
		OperationID:   "createBlock",
		Method:        http.MethodPost,
		Path:          "/api/user/blocks/{userId}",
		Summary:       "Block another user",
		Description:   "Adds the specified user to the caller's block list. The relationship is enforced symmetrically — neither party will see the other in search/profile/invite endpoints.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"social"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaCreateBlock)

	huma.Register(api, huma.Operation{
		OperationID: "deleteBlock",
		Method:      http.MethodDelete,
		Path:        "/api/user/blocks/{userId}",
		Summary:     "Unblock another user",
		Description: "Removes the specified user from the caller's block list.",
		Tags:        []string{"social"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDeleteBlock)
}

// HumaListBlocks is the huma handler for GET /api/user/blocks.
func (h *SocialHandler) HumaListBlocks(ctx context.Context, _ *ListBlocksInput) (*ListBlocksOutput, error) {
	uid := UserIDFromContext(ctx)
	var rows []db.Block
	if err := h.DB.Where("user_id = ?", uid).Preload("BlockedUser").Find(&rows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch block list")
	}
	out := make([]BlockedUserResponse, 0, len(rows))
	for _, b := range rows {
		if b.BlockedUser.ID == 0 {
			continue
		}
		out = append(out, BlockedUserResponse{
			UserID:    strconv.FormatUint(uint64(b.BlockedUser.ID), 10),
			Username:  b.BlockedUser.Username,
			AvatarURL: b.BlockedUser.AvatarURL,
		})
	}
	return &ListBlocksOutput{Body: ListBlocksResponse{Blocked: out}}, nil
}

// HumaCreateBlock is the huma handler for POST /api/user/blocks/{userId}.
func (h *SocialHandler) HumaCreateBlock(ctx context.Context, in *CreateBlockInput) (*CreateBlockOutput, error) {
	uid := UserIDFromContext(ctx)
	parsed, err := strconv.ParseUint(in.UserID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid user ID")
	}
	target := uint(parsed)
	if target == uid {
		return nil, huma.Error400BadRequest("cannot block yourself")
	}
	var existing db.User
	if err := h.DB.Select("id").First(&existing, target).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}
	block := db.Block{UserID: uid, BlockedUserID: target}
	// Idempotent: unique index on (user_id, blocked_user_id) makes a
	// duplicate insert fail, which we treat as already-blocked success.
	if err := h.DB.Create(&block).Error; err != nil {
		return &CreateBlockOutput{Body: MessageResponse{Message: "already blocked"}}, nil
	}
	return &CreateBlockOutput{Body: MessageResponse{Message: "user blocked"}}, nil
}

// HumaDeleteBlock is the huma handler for DELETE /api/user/blocks/{userId}.
func (h *SocialHandler) HumaDeleteBlock(ctx context.Context, in *DeleteBlockInput) (*DeleteBlockOutput, error) {
	uid := UserIDFromContext(ctx)
	parsed, err := strconv.ParseUint(in.UserID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid user ID")
	}
	target := uint(parsed)
	result := h.DB.Where("user_id = ? AND blocked_user_id = ?", uid, target).Delete(&db.Block{})
	if result.RowsAffected == 0 {
		return nil, huma.Error404NotFound("block not found")
	}
	return &DeleteBlockOutput{Body: MessageResponse{Message: "user unblocked"}}, nil
}
