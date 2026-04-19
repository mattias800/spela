package api

import (
	"fmt"
	"log/slog"
	"strconv"
	"strings"
	"sync"
	"time"

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
