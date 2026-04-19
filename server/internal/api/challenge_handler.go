package api

import (
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// Challenge statuses.
const (
	ChallengeStatusActive  = "active"
	ChallengeStatusClosed  = "closed"
	ChallengeStatusExpired = "expired"
)

// Attempt statuses.
const (
	AttemptStatusInProgress = "in_progress"
	AttemptStatusCompleted  = "completed"
	AttemptStatusAbandoned  = "abandoned"
)

// Challenge types.
const (
	ChallengeTypeCompletion = "completion"
	ChallengeTypeSpeedrun   = "speedrun"
	ChallengeTypeSurvival   = "survival"
)

// Difficulty levels.
const (
	DifficultyEasy   = "easy"
	DifficultyMedium = "medium"
	DifficultyHard   = "hard"
)

// maxChallengeSaveSize is the maximum file size for challenge save states (10 MB).
const maxChallengeSaveSize = 10 << 20

// maxChallengeNameLength is the maximum length for challenge names (matching DB column).
const maxChallengeNameLength = 255

// rateLimiterCleanupThreshold triggers inline cleanup when the map exceeds this size.
const rateLimiterCleanupThreshold = 1000

// ChallengeHandler handles game challenge endpoints.
type ChallengeHandler struct {
	DB      *gorm.DB
	Storage *storage.Storage
	Hub     *ws.Hub

	// AttemptRateLimitSeconds is the minimum interval between attempt starts
	// per user per challenge. Defaults to 30 if zero.
	AttemptRateLimitSeconds int

	// Rate limiter: tracks last attempt start per (userID, challengeID).
	attemptMu        sync.Mutex
	attemptLastStart map[string]time.Time
}

// NewChallengeHandler creates a ChallengeHandler with initialized internals.
func NewChallengeHandler(database *gorm.DB, store *storage.Storage, hub *ws.Hub) *ChallengeHandler {
	return &ChallengeHandler{
		DB:                     database,
		Storage:                store,
		Hub:                    hub,
		AttemptRateLimitSeconds: 30,
		attemptLastStart:       make(map[string]time.Time),
	}
}

// validChallengeTypes is the set of allowed challenge types.
var validChallengeTypes = map[string]bool{
	"completion": true,
	"speedrun":   true,
	"survival":   true,
}

// validDifficulties is the set of allowed difficulty levels.
var validDifficulties = map[string]bool{
	"easy":   true,
	"medium": true,
	"hard":   true,
}

// CreateChallenge creates a new game challenge with an uploaded save state.
func (h *ChallengeHandler) CreateChallenge(c *gin.Context) {
	uid := getUserID(c)

	// Parse multipart form with size limit
	if err := c.Request.ParseMultipartForm(maxChallengeSaveSize + 1<<20); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "request too large or invalid multipart form"})
		return
	}

	name := strings.TrimSpace(c.PostForm("name"))
	if name == "" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "name is required"})
		return
	}
	if len(name) > maxChallengeNameLength {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: fmt.Sprintf("name must be %d characters or fewer", maxChallengeNameLength)})
		return
	}

	gameIDStr := c.PostForm("gameId")
	gid, err := strconv.ParseUint(gameIDStr, 10, 64)
	if err != nil || gid == 0 {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "valid gameId is required"})
		return
	}

	// Verify game exists
	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	if err := requirePlayableConsole(h.DB, uint(gid)); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: errNonPlayableConsole.Error()})
		return
	}

	challengeType := c.DefaultPostForm("type", "completion")
	if !validChallengeTypes[challengeType] {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "type must be completion, speedrun, or survival"})
		return
	}

	difficulty := c.DefaultPostForm("difficulty", "medium")
	if !validDifficulties[difficulty] {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "difficulty must be easy, medium, or hard"})
		return
	}

	description := c.PostForm("description")
	if len(description) > 2048 {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "description must be 2048 characters or fewer"})
		return
	}
	coreName := c.PostForm("coreName")

	var expiresAt *time.Time
	if expStr := c.PostForm("expiresAt"); expStr != "" {
		t, err := time.Parse(time.RFC3339, expStr)
		if err != nil {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "expiresAt must be RFC3339 format"})
			return
		}
		if t.Before(time.Now()) {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "expiresAt must be in the future"})
			return
		}
		expiresAt = &t
	}

	// Save file (required)
	saveFile, saveHeader, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "save file is required"})
		return
	}
	defer saveFile.Close()

	if saveHeader.Size > maxChallengeSaveSize {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: fmt.Sprintf("save file too large (max %d MB)", maxChallengeSaveSize>>20)})
		return
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create challenge"})
		return
	}

	// Write save file
	savePath, saveSize, err := h.Storage.WriteChallengeSave(challenge.ID, saveFile)
	if err != nil {
		h.DB.Unscoped().Delete(&challenge)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to save challenge file"})
		return
	}

	challenge.SaveFilePath = savePath
	challenge.SaveFileSize = saveSize

	// Screenshot (optional)
	if screenshotFile, _, err := c.Request.FormFile("screenshot"); err == nil {
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to update challenge record"})
		return
	}

	// Reload with associations
	h.DB.Preload("Creator").Preload("Game").Preload("Game.Console").First(&challenge, challenge.ID)

	CreateActivityEvent(h.DB, h.Hub, uid, "challenge_created", challenge.GameID, ChallengeCreatedMetadata{
		ChallengeID:   challenge.ID,
		ChallengeName: challenge.Name,
	})

	c.JSON(http.StatusCreated, h.toChallengeResponse(challenge))
}

func (h *ChallengeHandler) lazyExpire(challenge *db.Challenge, now time.Time) {
	if challenge.Status == ChallengeStatusActive && challenge.ExpiresAt != nil && challenge.ExpiresAt.Before(now) {
		challenge.Status = ChallengeStatusExpired
		if err := h.DB.Model(challenge).Update("status", ChallengeStatusExpired).Error; err != nil {
			slog.Error("failed to lazy-expire challenge", "challengeId", challenge.ID, "error", err)
		}
	}
}

// toChallengeResponse converts a db.Challenge to its API response.
func (h *ChallengeHandler) toChallengeResponse(ch db.Challenge) ChallengeResponse {
	creatorUsername := ch.Creator.Username
	creatorAvatar := ch.Creator.AvatarURL
	if creatorUsername == "" && ch.CreatorID != 0 {
		var user db.User
		if err := h.DB.First(&user, ch.CreatorID).Error; err == nil {
			creatorUsername = user.Username
			creatorAvatar = user.AvatarURL
		}
	}

	gameTitle := ch.Game.Title
	gameCoverURL := ch.Game.CoverURL
	consoleName := ""
	if ch.Game.ID != 0 {
		if gameCoverURL != "" && !strings.HasPrefix(gameCoverURL, "http") {
			gameCoverURL = "/api/images/" + gameCoverURL
		}
		if ch.Game.Console.ID != 0 {
			consoleName = ch.Game.Console.Name
		}
	}

	screenshotURL := ""
	if ch.ScreenshotPath != "" {
		screenshotURL = fmt.Sprintf("/api/challenges/%d/screenshot", ch.ID)
	}

	return ChallengeResponse{
		ID:              strconv.FormatUint(uint64(ch.ID), 10),
		CreatorID:       strconv.FormatUint(uint64(ch.CreatorID), 10),
		CreatorUsername: creatorUsername,
		CreatorAvatar:   creatorAvatar,
		GameID:          strconv.FormatUint(uint64(ch.GameID), 10),
		GameTitle:       gameTitle,
		GameCoverURL:    gameCoverURL,
		ConsoleName:     consoleName,
		Name:            ch.Name,
		Description:     ch.Description,
		Type:            ch.Type,
		Difficulty:      ch.Difficulty,
		Status:          ch.Status,
		SaveFileSize:    ch.SaveFileSize,
		ScreenshotURL:   screenshotURL,
		CoreName:        ch.CoreName,
		AttemptCount:    ch.AttemptCount,
		CompletionCount: ch.CompletionCount,
		ExpiresAt:       ch.ExpiresAt,
		CreatedAt:       ch.CreatedAt,
		UpdatedAt:       ch.UpdatedAt,
	}
}

// toAttemptResponse converts a db.ChallengeAttempt to its API response.
func (h *ChallengeHandler) toAttemptResponse(a db.ChallengeAttempt) ChallengeAttemptResponse {
	username := a.User.Username
	avatarURL := a.User.AvatarURL
	if username == "" && a.UserID != 0 {
		var user db.User
		if err := h.DB.First(&user, a.UserID).Error; err == nil {
			username = user.Username
			avatarURL = user.AvatarURL
		}
	}

	return ChallengeAttemptResponse{
		ID:          strconv.FormatUint(uint64(a.ID), 10),
		ChallengeID: strconv.FormatUint(uint64(a.ChallengeID), 10),
		UserID:      strconv.FormatUint(uint64(a.UserID), 10),
		Username:    username,
		AvatarURL:   avatarURL,
		Status:      a.Status,
		StartedAt:   a.StartedAt,
		CompletedAt: a.CompletedAt,
		DurationMs:  a.DurationMs,
		IsBest:      a.IsBest,
	}
}
