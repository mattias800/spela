package api

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Inputs / outputs --------------------------------------------------------

// CreateChallengeBody is the multipart body for POST /api/challenges. All
// fields are marked optional at the schema level so the handler can emit the
// existing validation error messages ("name is required", "valid gameId is
// required", etc.) instead of huma's generic 422 response.
type CreateChallengeBody struct {
	GameID      string        `form:"gameId" required:"false" doc:"Numeric ID of the game the challenge is for."`
	Name        string        `form:"name" required:"false" doc:"Display name for the challenge. Max 255 characters."`
	Description string        `form:"description" required:"false" doc:"Optional longer description. Max 2048 characters."`
	Type        string        `form:"type" required:"false" doc:"Challenge type: 'completion', 'speedrun', or 'survival'. Defaults to 'completion'."`
	Difficulty  string        `form:"difficulty" required:"false" doc:"Difficulty: 'easy', 'medium', or 'hard'. Defaults to 'medium'."`
	ExpiresAt   string        `form:"expiresAt" required:"false" doc:"Optional RFC3339 expiry timestamp; must be in the future."`
	CoreName    string        `form:"coreName" required:"false" doc:"libretro core identifier that produced the starting save."`
	Save        huma.FormFile `form:"save" required:"false" contentType:"application/octet-stream" doc:"Starting save state file. Required. Max 10 MB."`
	Screenshot  huma.FormFile `form:"screenshot" required:"false" contentType:"application/octet-stream" doc:"Optional screenshot displayed on the challenge."`
}

// CreateChallengeInput wraps the multipart body.
type CreateChallengeInput struct {
	RawBody huma.MultipartFormFiles[CreateChallengeBody]
}

// CreateChallengeOutput wraps the created-challenge response body with a
// 201 Created status (matches the gin handler).
type CreateChallengeOutput struct {
	Body ChallengeResponse
}

// --- Registration ------------------------------------------------------------

// RegisterChallengeCreateRoute wires the POST /api/challenges multipart upload
// into the huma API. Kept in a separate file from the other challenge routes
// because it uses the multipart pattern (MultipartFormFiles) rather than JSON
// bodies, matching the RegisterAdminMultipartRoutes grouping style.
func RegisterChallengeCreateRoute(
	api huma.API,
	h *ChallengeHandler,
	jwtSecret string,
	database *gorm.DB,
	userLimiter *RateLimiter,
) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID:   "createChallenge",
		Method:        http.MethodPost,
		Path:          "/api/challenges",
		Summary:       "Create a new challenge",
		Description:   "Creates a challenge for a game with a required starting save state and an optional screenshot. Max save size is 10 MB.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"challenges"},
		Middlewares:   mw,
		Security:      sec,
		MaxBodyBytes:  maxChallengeSaveSize + (1 << 20),
	}, h.HumaCreateChallenge)
}

// --- Handler -----------------------------------------------------------------

// HumaCreateChallenge is the huma implementation of POST /api/challenges.
// Mirrors the gin handler 1:1: field validation, game lookup, playable-console
// check, save file write (with size cap), optional screenshot, activity event,
// and final ChallengeResponse.
func (h *ChallengeHandler) HumaCreateChallenge(ctx context.Context, in *CreateChallengeInput) (*CreateChallengeOutput, error) {
	uid := UserIDFromContext(ctx)

	body := in.RawBody.Data()

	name := strings.TrimSpace(body.Name)
	if name == "" {
		return nil, huma.Error400BadRequest("name is required")
	}
	if len(name) > maxChallengeNameLength {
		return nil, huma.Error400BadRequest(fmt.Sprintf("name must be %d characters or fewer", maxChallengeNameLength))
	}

	gid, err := strconv.ParseUint(body.GameID, 10, 64)
	if err != nil || gid == 0 {
		return nil, huma.Error400BadRequest("valid gameId is required")
	}

	// Verify game exists
	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	if err := requirePlayableConsole(h.DB, uint(gid)); err != nil {
		return nil, huma.Error400BadRequest(errNonPlayableConsole.Error())
	}

	challengeType := body.Type
	if challengeType == "" {
		challengeType = "completion"
	}
	if !validChallengeTypes[challengeType] {
		return nil, huma.Error400BadRequest("type must be completion, speedrun, or survival")
	}

	difficulty := body.Difficulty
	if difficulty == "" {
		difficulty = "medium"
	}
	if !validDifficulties[difficulty] {
		return nil, huma.Error400BadRequest("difficulty must be easy, medium, or hard")
	}

	description := body.Description
	if len(description) > 2048 {
		return nil, huma.Error400BadRequest("description must be 2048 characters or fewer")
	}
	coreName := body.CoreName

	var expiresAt *time.Time
	if expStr := body.ExpiresAt; expStr != "" {
		t, err := time.Parse(time.RFC3339, expStr)
		if err != nil {
			return nil, huma.Error400BadRequest("expiresAt must be RFC3339 format")
		}
		if t.Before(time.Now()) {
			return nil, huma.Error400BadRequest("expiresAt must be in the future")
		}
		expiresAt = &t
	}

	// Save file (required)
	saveFile := body.Save
	if !saveFile.IsSet {
		return nil, huma.Error400BadRequest("save file is required")
	}
	defer saveFile.Close()

	if saveFile.Size > maxChallengeSaveSize {
		return nil, huma.Error400BadRequest(fmt.Sprintf("save file too large (max %d MB)", maxChallengeSaveSize>>20))
	}

	// Create DB record first to get ID
	challenge := db.Challenge{
		CreatorID:   uid,
		GameID:      uint(gid),
		Name:        name,
		Description: description,
		Type:        challengeType,
		Difficulty:  difficulty,
		Status:      ChallengeStatusActive,
		CoreName:    coreName,
		ExpiresAt:   expiresAt,
	}
	if err := h.DB.Create(&challenge).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to create challenge")
	}

	// Write save file
	savePath, saveSize, err := h.Storage.WriteChallengeSave(challenge.ID, saveFile)
	if err != nil {
		h.DB.Unscoped().Delete(&challenge)
		return nil, huma.Error500InternalServerError("failed to save challenge file")
	}

	challenge.SaveFilePath = savePath
	challenge.SaveFileSize = saveSize

	// Screenshot (optional)
	if body.Screenshot.IsSet {
		screenshotFile := body.Screenshot
		defer screenshotFile.Close()
		if ssPath, err := h.Storage.WriteChallengeScreenshot(challenge.ID, screenshotFile); err == nil {
			challenge.ScreenshotPath = ssPath
		} else {
			slog.Error("failed to write challenge screenshot", "challengeId", challenge.ID, "error", err)
		}
	}

	if err := h.DB.Save(&challenge).Error; err != nil {
		h.Storage.DeleteChallengeSave(challenge.ID)
		h.DB.Unscoped().Delete(&challenge)
		return nil, huma.Error500InternalServerError("failed to update challenge record")
	}

	// Reload with associations
	h.DB.Preload("Creator").Preload("Game").Preload("Game.Console").First(&challenge, challenge.ID)

	CreateActivityEvent(h.DB, h.Hub, uid, "challenge_created", challenge.GameID, ChallengeCreatedMetadata{
		ChallengeID:   challenge.ID,
		ChallengeName: challenge.Name,
	})

	return &CreateChallengeOutput{Body: h.toChallengeResponse(challenge)}, nil
}
