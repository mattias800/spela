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
		c.JSON(http.StatusBadRequest, gin.H{"error": "request too large or invalid multipart form"})
		return
	}

	name := strings.TrimSpace(c.PostForm("name"))
	if name == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "name is required"})
		return
	}
	if len(name) > maxChallengeNameLength {
		c.JSON(http.StatusBadRequest, gin.H{"error": fmt.Sprintf("name must be %d characters or fewer", maxChallengeNameLength)})
		return
	}

	gameIDStr := c.PostForm("gameId")
	gid, err := strconv.ParseUint(gameIDStr, 10, 64)
	if err != nil || gid == 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "valid gameId is required"})
		return
	}

	// Verify game exists
	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	challengeType := c.DefaultPostForm("type", "completion")
	if !validChallengeTypes[challengeType] {
		c.JSON(http.StatusBadRequest, gin.H{"error": "type must be completion, speedrun, or survival"})
		return
	}

	difficulty := c.DefaultPostForm("difficulty", "medium")
	if !validDifficulties[difficulty] {
		c.JSON(http.StatusBadRequest, gin.H{"error": "difficulty must be easy, medium, or hard"})
		return
	}

	description := c.PostForm("description")
	if len(description) > 2048 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "description must be 2048 characters or fewer"})
		return
	}
	coreName := c.PostForm("coreName")

	var expiresAt *time.Time
	if expStr := c.PostForm("expiresAt"); expStr != "" {
		t, err := time.Parse(time.RFC3339, expStr)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "expiresAt must be RFC3339 format"})
			return
		}
		if t.Before(time.Now()) {
			c.JSON(http.StatusBadRequest, gin.H{"error": "expiresAt must be in the future"})
			return
		}
		expiresAt = &t
	}

	// Save file (required)
	saveFile, saveHeader, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "save file is required"})
		return
	}
	defer saveFile.Close()

	if saveHeader.Size > maxChallengeSaveSize {
		c.JSON(http.StatusBadRequest, gin.H{"error": fmt.Sprintf("save file too large (max %d MB)", maxChallengeSaveSize>>20)})
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create challenge"})
		return
	}

	// Write save file
	savePath, saveSize, err := h.Storage.WriteChallengeSave(challenge.ID, saveFile)
	if err != nil {
		h.DB.Unscoped().Delete(&challenge)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save challenge file"})
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update challenge record"})
		return
	}

	// Reload with associations
	h.DB.Preload("Creator").Preload("Game").Preload("Game.Console").First(&challenge, challenge.ID)

	CreateActivityEvent(h.DB, h.Hub, uid, "challenge_created", challenge.GameID, map[string]interface{}{
		"challengeId":   challenge.ID,
		"challengeName": challenge.Name,
	})

	c.JSON(http.StatusCreated, h.toChallengeResponse(challenge))
}

// ListChallenges returns a paginated list of challenges with optional filters.
func (h *ChallengeHandler) ListChallenges(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	query := h.DB.Model(&db.Challenge{})

	// Filters
	if gameID := c.Query("gameId"); gameID != "" {
		query = query.Where("game_id = ?", gameID)
	}
	if consoleID := c.Query("consoleId"); consoleID != "" {
		query = query.Joins("JOIN games ON games.id = challenges.game_id").
			Where("games.console_id = ?", consoleID)
	}
	if difficulty := c.Query("difficulty"); difficulty != "" {
		query = query.Where("challenges.difficulty = ?", difficulty)
	}
	if challengeType := c.Query("type"); challengeType != "" {
		query = query.Where("challenges.type = ?", challengeType)
	}
	if status := c.Query("status"); status != "" {
		query = query.Where("challenges.status = ?", status)
	} else {
		query = query.Where("challenges.status = ?", ChallengeStatusActive)
	}
	if creatorID := c.Query("creatorId"); creatorID != "" {
		query = query.Where("challenges.creator_id = ?", creatorID)
	}

	var total int64
	query.Count(&total)

	// Sort
	sort := c.DefaultQuery("sort", "newest")
	switch sort {
	case "popular":
		query = query.Order("challenges.attempt_count DESC, challenges.created_at DESC")
	case "oldest":
		query = query.Order("challenges.created_at ASC")
	default: // "newest"
		query = query.Order("challenges.created_at DESC")
	}

	var challenges []db.Challenge
	if err := query.
		Preload("Creator").Preload("Game").Preload("Game.Console").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&challenges).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch challenges"})
		return
	}

	// Lazy expire check
	now := time.Now()
	for i := range challenges {
		h.lazyExpire(&challenges[i], now)
	}

	result := make([]ChallengeResponse, 0, len(challenges))
	for _, ch := range challenges {
		result = append(result, h.toChallengeResponse(ch))
	}

	c.JSON(http.StatusOK, PaginatedResponse{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// GetChallenge returns a single challenge's details.
func (h *ChallengeHandler) GetChallenge(c *gin.Context) {
	id := c.Param("id")

	var challenge db.Challenge
	if err := h.DB.Preload("Creator").Preload("Game").Preload("Game.Console").
		First(&challenge, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "challenge not found"})
		return
	}

	h.lazyExpire(&challenge, time.Now())

	c.JSON(http.StatusOK, h.toChallengeResponse(challenge))
}

// UpdateChallenge updates a challenge's metadata (creator only).
func (h *ChallengeHandler) UpdateChallenge(c *gin.Context) {
	uid := getUserID(c)
	id := c.Param("id")

	var challenge db.Challenge
	if err := h.DB.First(&challenge, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "challenge not found"})
		return
	}

	role, _ := c.Get("role")
	if challenge.CreatorID != uid && !db.IsAdminOrOwner(role.(string)) {
		c.JSON(http.StatusForbidden, gin.H{"error": "not authorized to update this challenge"})
		return
	}

	var req struct {
		Name        *string `json:"name"`
		Description *string `json:"description"`
		Status      *string `json:"status"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	if req.Name != nil {
		name := strings.TrimSpace(*req.Name)
		if name == "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "name cannot be empty"})
			return
		}
		if len(name) > maxChallengeNameLength {
			c.JSON(http.StatusBadRequest, gin.H{"error": fmt.Sprintf("name must be %d characters or fewer", maxChallengeNameLength)})
			return
		}
		challenge.Name = name
	}
	if req.Description != nil {
		if len(*req.Description) > 2048 {
			c.JSON(http.StatusBadRequest, gin.H{"error": "description must be 2048 characters or fewer"})
			return
		}
		challenge.Description = *req.Description
	}
	if req.Status != nil {
		s := *req.Status
		if s != ChallengeStatusActive && s != ChallengeStatusClosed {
			c.JSON(http.StatusBadRequest, gin.H{"error": "status must be active or closed"})
			return
		}
		challenge.Status = s
	}

	if err := h.DB.Save(&challenge).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update challenge"})
		return
	}

	h.DB.Preload("Creator").Preload("Game").Preload("Game.Console").First(&challenge, challenge.ID)
	c.JSON(http.StatusOK, h.toChallengeResponse(challenge))
}

// DeleteChallenge deletes a challenge and all its data (creator or admin).
func (h *ChallengeHandler) DeleteChallenge(c *gin.Context) {
	uid := getUserID(c)
	id := c.Param("id")

	var challenge db.Challenge
	if err := h.DB.First(&challenge, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "challenge not found"})
		return
	}

	role, _ := c.Get("role")
	if challenge.CreatorID != uid && !db.IsAdminOrOwner(role.(string)) {
		c.JSON(http.StatusForbidden, gin.H{"error": "not authorized to delete this challenge"})
		return
	}

	// Delete all attempts
	if err := h.DB.Where("challenge_id = ?", challenge.ID).Delete(&db.ChallengeAttempt{}).Error; err != nil {
		slog.Error("failed to delete challenge attempts", "challengeId", challenge.ID, "error", err)
	}

	// Delete files
	if err := h.Storage.DeleteChallengeSave(challenge.ID); err != nil {
		slog.Error("failed to delete challenge save files", "challengeId", challenge.ID, "error", err)
	}

	// Delete challenge record
	h.DB.Delete(&challenge)

	c.JSON(http.StatusOK, gin.H{"message": "challenge deleted"})
}

// DownloadChallengeSave serves the challenge's starting save state.
func (h *ChallengeHandler) DownloadChallengeSave(c *gin.Context) {
	id := c.Param("id")

	var challenge db.Challenge
	if err := h.DB.First(&challenge, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "challenge not found"})
		return
	}

	path := h.Storage.ChallengeSavePath(challenge.ID)
	if !storage.ValidateROMPath(path, []string{h.Storage.SaveDir}) {
		c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
		return
	}
	c.Header("Content-Disposition", "attachment; filename=\"challenge_save\"")
	c.File(path)
}

// GetChallengeScreenshot serves the challenge's screenshot.
func (h *ChallengeHandler) GetChallengeScreenshot(c *gin.Context) {
	id := c.Param("id")

	var challenge db.Challenge
	if err := h.DB.First(&challenge, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "challenge not found"})
		return
	}

	if challenge.ScreenshotPath == "" {
		c.JSON(http.StatusNotFound, gin.H{"error": "no screenshot available"})
		return
	}

	path := h.Storage.ChallengeScreenshotPath(challenge.ID)
	if !storage.ValidateROMPath(path, []string{h.Storage.SaveDir}) {
		c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
		return
	}
	c.File(path)
}

// StartAttempt begins a new challenge attempt (server records StartedAt).
func (h *ChallengeHandler) StartAttempt(c *gin.Context) {
	uid := getUserID(c)
	challengeID := c.Param("id")

	cid, err := strconv.ParseUint(challengeID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid challenge ID"})
		return
	}

	var challenge db.Challenge
	if err := h.DB.First(&challenge, cid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "challenge not found"})
		return
	}

	// Check if challenge is active (with lazy expiration)
	h.lazyExpire(&challenge, time.Now())
	if challenge.Status != ChallengeStatusActive {
		c.JSON(http.StatusBadRequest, gin.H{"error": "challenge is not active"})
		return
	}

	// Rate limit: configurable interval between attempt starts per user per challenge.
	if h.AttemptRateLimitSeconds > 0 {
		rateLimitKey := fmt.Sprintf("%d:%d", uid, cid)
		h.attemptMu.Lock()
		if lastStart, ok := h.attemptLastStart[rateLimitKey]; ok {
			if time.Since(lastStart) < time.Duration(h.AttemptRateLimitSeconds)*time.Second {
				h.attemptMu.Unlock()
				c.JSON(http.StatusTooManyRequests, gin.H{"error": "please wait before starting another attempt"})
				return
			}
		}
		h.attemptLastStart[rateLimitKey] = time.Now()

		// Inline cleanup: prune stale entries to prevent memory leak.
		if len(h.attemptLastStart) > rateLimiterCleanupThreshold {
			cutoff := time.Now().Add(-time.Duration(h.AttemptRateLimitSeconds) * time.Second)
			for k, v := range h.attemptLastStart {
				if v.Before(cutoff) {
					delete(h.attemptLastStart, k)
				}
			}
		}
		h.attemptMu.Unlock()
	}

	// Abandon any existing in_progress attempt for this user+challenge
	h.DB.Model(&db.ChallengeAttempt{}).
		Where("challenge_id = ? AND user_id = ? AND status = ?", cid, uid, AttemptStatusInProgress).
		Update("status", AttemptStatusAbandoned)

	now := time.Now()
	attempt := db.ChallengeAttempt{
		ChallengeID: uint(cid),
		UserID:      uid,
		Status:      AttemptStatusInProgress,
		StartedAt:   now,
	}
	if err := h.DB.Create(&attempt).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to start attempt"})
		return
	}

	// Increment attempt count
	h.DB.Model(&challenge).UpdateColumn("attempt_count", gorm.Expr("attempt_count + ?", 1))

	h.DB.Preload("User").First(&attempt, attempt.ID)
	c.JSON(http.StatusCreated, h.toAttemptResponse(attempt))
}

// CompleteAttempt marks an attempt as completed (server records CompletedAt).
func (h *ChallengeHandler) CompleteAttempt(c *gin.Context) {
	uid := getUserID(c)
	challengeID := c.Param("id")
	attemptID := c.Param("aid")

	var attempt db.ChallengeAttempt
	if err := h.DB.Preload("Challenge").First(&attempt, attemptID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "attempt not found"})
		return
	}

	// Verify ownership and challenge match
	if attempt.UserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "not your attempt"})
		return
	}
	if strconv.FormatUint(uint64(attempt.ChallengeID), 10) != challengeID {
		c.JSON(http.StatusBadRequest, gin.H{"error": "attempt does not belong to this challenge"})
		return
	}
	if attempt.Status != AttemptStatusInProgress {
		c.JSON(http.StatusBadRequest, gin.H{"error": "attempt is not in progress"})
		return
	}

	now := time.Now()
	durationMs := now.Sub(attempt.StartedAt).Milliseconds()

	attempt.Status = AttemptStatusCompleted
	attempt.CompletedAt = &now
	attempt.DurationMs = durationMs

	isSurvival := attempt.Challenge.Type == ChallengeTypeSurvival

	// Determine if this is the user's best attempt
	var currentBest db.ChallengeAttempt
	hasPreviousBest := h.DB.Where("challenge_id = ? AND user_id = ? AND is_best = ? AND id != ?",
		attempt.ChallengeID, uid, true, attempt.ID).
		First(&currentBest).Error == nil

	if !hasPreviousBest {
		// First completed attempt — it's the best
		attempt.IsBest = true
	} else {
		// For survival: longer is better. For speedrun/completion: shorter is better.
		isBetter := durationMs < currentBest.DurationMs
		if isSurvival {
			isBetter = durationMs > currentBest.DurationMs
		}
		if isBetter {
			h.DB.Model(&currentBest).Update("is_best", false)
			attempt.IsBest = true
		}
	}

	if err := h.DB.Save(&attempt).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to complete attempt"})
		return
	}

	// Increment completion count
	h.DB.Model(&db.Challenge{}).Where("id = ?", attempt.ChallengeID).
		UpdateColumn("completion_count", gorm.Expr("completion_count + ?", 1))

	// Load user for response
	h.DB.Preload("User").First(&attempt, attempt.ID)

	// Activity events
	var challenge db.Challenge
	h.DB.First(&challenge, attempt.ChallengeID)

	CreateActivityEvent(h.DB, h.Hub, uid, "challenge_completed", challenge.GameID, map[string]interface{}{
		"challengeId":   challenge.ID,
		"challengeName": challenge.Name,
		"durationMs":    durationMs,
	})

	// Check if this is an overall record (rank 1)
	if attempt.IsBest {
		var betterCount int64
		comparison := "duration_ms < ?"
		if isSurvival {
			comparison = "duration_ms > ?"
		}
		h.DB.Model(&db.ChallengeAttempt{}).
			Where("challenge_id = ? AND is_best = ? AND "+comparison+" AND id != ?",
				attempt.ChallengeID, true, durationMs, attempt.ID).
			Count(&betterCount)

		if betterCount == 0 {
			CreateActivityEvent(h.DB, h.Hub, uid, "challenge_record", challenge.GameID, map[string]interface{}{
				"challengeId":   challenge.ID,
				"challengeName": challenge.Name,
				"durationMs":    durationMs,
			})
		}
	}

	// Broadcast leaderboard update
	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{
			Type: "challenge_leaderboard_updated",
			Payload: gin.H{
				"challengeId": strconv.FormatUint(uint64(attempt.ChallengeID), 10),
			},
		})
	}

	c.JSON(http.StatusOK, h.toAttemptResponse(attempt))
}

// AbandonAttempt marks an attempt as abandoned.
func (h *ChallengeHandler) AbandonAttempt(c *gin.Context) {
	uid := getUserID(c)
	challengeID := c.Param("id")
	attemptID := c.Param("aid")

	var attempt db.ChallengeAttempt
	if err := h.DB.First(&attempt, attemptID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "attempt not found"})
		return
	}

	if attempt.UserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "not your attempt"})
		return
	}
	if strconv.FormatUint(uint64(attempt.ChallengeID), 10) != challengeID {
		c.JSON(http.StatusBadRequest, gin.H{"error": "attempt does not belong to this challenge"})
		return
	}
	if attempt.Status != AttemptStatusInProgress {
		c.JSON(http.StatusBadRequest, gin.H{"error": "attempt is not in progress"})
		return
	}

	attempt.Status = AttemptStatusAbandoned
	if err := h.DB.Save(&attempt).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to abandon attempt"})
		return
	}

	h.DB.Preload("User").First(&attempt, attempt.ID)
	c.JSON(http.StatusOK, h.toAttemptResponse(attempt))
}

// GetMyAttempts returns the current user's attempts for a challenge.
func (h *ChallengeHandler) GetMyAttempts(c *gin.Context) {
	uid := getUserID(c)
	challengeID := c.Param("id")

	var attempts []db.ChallengeAttempt
	if err := h.DB.Where("challenge_id = ? AND user_id = ?", challengeID, uid).
		Preload("User").
		Order("created_at DESC").
		Find(&attempts).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch attempts"})
		return
	}

	result := make([]ChallengeAttemptResponse, 0, len(attempts))
	for _, a := range attempts {
		result = append(result, h.toAttemptResponse(a))
	}

	c.JSON(http.StatusOK, result)
}

// GetLeaderboard returns ranked challenge attempts (best per user, fastest first).
func (h *ChallengeHandler) GetLeaderboard(c *gin.Context) {
	challengeID := c.Param("id")

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "50"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 50
	}

	// Verify challenge exists
	var challenge db.Challenge
	if err := h.DB.First(&challenge, challengeID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "challenge not found"})
		return
	}

	var total int64
	h.DB.Model(&db.ChallengeAttempt{}).
		Where("challenge_id = ? AND is_best = ?", challengeID, true).
		Count(&total)

	// Sort order depends on challenge type: survival = longest wins, others = fastest wins.
	orderClause := "duration_ms ASC, completed_at ASC"
	if challenge.Type == ChallengeTypeSurvival {
		orderClause = "duration_ms DESC, completed_at ASC"
	}

	var attempts []db.ChallengeAttempt
	if err := h.DB.Where("challenge_id = ? AND is_best = ?", challengeID, true).
		Preload("User").
		Order(orderClause).
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&attempts).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch leaderboard"})
		return
	}

	startRank := (page-1)*pageSize + 1
	result := make([]ChallengeLeaderboardEntry, 0, len(attempts))
	for i, a := range attempts {
		var completedAt time.Time
		if a.CompletedAt != nil {
			completedAt = *a.CompletedAt
		}
		result = append(result, ChallengeLeaderboardEntry{
			Rank:        startRank + i,
			UserID:      strconv.FormatUint(uint64(a.UserID), 10),
			Username:    a.User.Username,
			AvatarURL:   a.User.AvatarURL,
			DurationMs:  a.DurationMs,
			AttemptID:   strconv.FormatUint(uint64(a.ID), 10),
			CompletedAt: completedAt,
		})
	}

	c.JSON(http.StatusOK, PaginatedResponse{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// ListGameChallenges returns challenges for a specific game.
func (h *ChallengeHandler) ListGameChallenges(c *gin.Context) {
	gameID := c.Param("id")

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.Challenge{}).Where("game_id = ? AND status = ?", gameID, ChallengeStatusActive).Count(&total)

	var challenges []db.Challenge
	if err := h.DB.Where("game_id = ? AND status = ?", gameID, ChallengeStatusActive).
		Preload("Creator").Preload("Game").Preload("Game.Console").
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&challenges).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch challenges"})
		return
	}

	now := time.Now()
	for i := range challenges {
		h.lazyExpire(&challenges[i], now)
	}

	result := make([]ChallengeResponse, 0, len(challenges))
	for _, ch := range challenges {
		result = append(result, h.toChallengeResponse(ch))
	}

	c.JSON(http.StatusOK, PaginatedResponse{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// ListMyChallenges returns challenges created by the current user.
func (h *ChallengeHandler) ListMyChallenges(c *gin.Context) {
	uid := getUserID(c)

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.Challenge{}).Where("creator_id = ?", uid).Count(&total)

	var challenges []db.Challenge
	if err := h.DB.Where("creator_id = ?", uid).
		Preload("Creator").Preload("Game").Preload("Game.Console").
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&challenges).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch challenges"})
		return
	}

	result := make([]ChallengeResponse, 0, len(challenges))
	for _, ch := range challenges {
		result = append(result, h.toChallengeResponse(ch))
	}

	c.JSON(http.StatusOK, PaginatedResponse{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// lazyExpire checks if a challenge has expired and updates its status.
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
