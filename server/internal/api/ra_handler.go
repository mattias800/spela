package api

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// RAHandler handles RetroAchievements endpoints.
type RAHandler struct {
	DB            *gorm.DB
	RAClient      *retroachievements.RAClient
	GameDir       string
	EncryptionKey []byte                // AES-256 key for encrypting RA tokens at rest
	Queue         *scraper.ScrapeQueue  // Scrape queue for async RA fetch jobs
	RAAPIKey      string                // Server-level RA API key; empty = auto-fetch disabled
}

// decryptRAToken decrypts the RA token from a credential record.
// Handles both encrypted and legacy plaintext values transparently.
func (h *RAHandler) decryptRAToken(cred *db.RetroAchievementCredential) (string, error) {
	return auth.Decrypt(cred.RAToken, h.EncryptionKey)
}

// LinkAccount links a user's RetroAchievements account by exchanging credentials for a token.
func (h *RAHandler) LinkAccount(c *gin.Context) {
	uid := getUserID(c)

	var req LinkRAAccountRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "username and password are required"})
		return
	}

	token, err := h.RAClient.LoginWithPassword(req.Username, req.Password)
	if err != nil {
		slog.Warn("RA login failed", "user_id", uid, "error", err)
		c.JSON(http.StatusUnauthorized, ErrorResponse{Error: "RetroAchievements login failed: invalid credentials"})
		return
	}

	// Encrypt the RA token before storing it in the database
	encryptedToken, err := auth.Encrypt(token, h.EncryptionKey)
	if err != nil {
		slog.Error("failed to encrypt RA token", "error", err)
		if strings.Contains(err.Error(), "invalid key size") {
			c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "encryption key misconfigured: SPELA_ENCRYPTION_KEY must be exactly 16, 24, or 32 bytes"})
		} else {
			c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to store RA credentials"})
		}
		return
	}

	// Upsert credential
	var cred db.RetroAchievementCredential
	result := h.DB.Where("user_id = ?", uid).First(&cred)
	if result.Error == nil {
		cred.RAUsername = req.Username
		cred.RAToken = encryptedToken
		if err := h.DB.Save(&cred).Error; err != nil {
			c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to update RA credentials"})
			return
		}
	} else {
		cred = db.RetroAchievementCredential{
			UserID:     uid,
			RAUsername:  req.Username,
			RAToken:    encryptedToken,
		}
		if err := h.DB.Create(&cred).Error; err != nil {
			c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to store RA credentials"})
			return
		}
	}

	c.JSON(http.StatusOK, gin.H{"linked": true, "username": req.Username})
}

// UnlinkAccount removes a user's RetroAchievements credentials.
func (h *RAHandler) UnlinkAccount(c *gin.Context) {
	uid := getUserID(c)

	result := h.DB.Where("user_id = ?", uid).Delete(&db.RetroAchievementCredential{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "no RA account linked"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"linked": false})
}

// GetStatus returns the user's RA link status.
func (h *RAHandler) GetStatus(c *gin.Context) {
	uid := getUserID(c)

	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		c.JSON(http.StatusOK, gin.H{"linked": false, "username": "", "hardcoreEnabled": false})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"linked":          true,
		"username":        cred.RAUsername,
		"hardcoreEnabled": cred.HardcoreEnabled,
	})
}

// UpdateSettings updates RA-specific settings for the user.
func (h *RAHandler) UpdateSettings(c *gin.Context) {
	uid := getUserID(c)

	var req UpdateRASettingsRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request body"})
		return
	}

	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "no RA account linked"})
		return
	}

	if req.HardcoreEnabled != nil {
		cred.HardcoreEnabled = *req.HardcoreEnabled
	}

	if err := h.DB.Save(&cred).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to update RA settings"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"linked":          true,
		"username":        cred.RAUsername,
		"hardcoreEnabled": cred.HardcoreEnabled,
	})
}

// GetToken returns the user's RA credentials for the player app.
func (h *RAHandler) GetToken(c *gin.Context) {
	uid := getUserID(c)

	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "no RA account linked"})
		return
	}

	// Decrypt the RA token (handles both encrypted and legacy plaintext values)
	token, err := auth.Decrypt(cred.RAToken, h.EncryptionKey)
	if err != nil {
		slog.Error("failed to decrypt RA token", "user_id", uid, "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to retrieve RA token"})
		return
	}

	// Prevent caching of sensitive credential by proxies and browsers.
	c.Header("Cache-Control", "no-store, must-revalidate")
	c.Header("Pragma", "no-cache")

	c.JSON(http.StatusOK, gin.H{
		"username": cred.RAUsername,
		"token":    token,
	})
}

// GetGameAchievements has been migrated to huma — see
// HumaGetGameAchievements in huma_achievements.go.

// GetAchievementProgress returns the user's achievement progress for a game.
func (h *RAHandler) GetAchievementProgress(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")

	// Look up the game
	var game db.Game
	if err := h.DB.First(&game, "id = ?", gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	if err := requirePlayableConsole(h.DB, game.ID); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: errNonPlayableConsole.Error()})
		return
	}

	// Get user's RA credentials — return empty progress if not linked
	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		c.JSON(http.StatusOK, []any{})
		return
	}

	// Use cached RA game ID if available, otherwise compute hash and look up
	raGameID := game.RAGameID
	if raGameID == 0 {
		romPath := filepath.Join(h.GameDir, game.FilePath)
		if !storage.ValidateROMPath(romPath, []string{h.GameDir}) {
			c.JSON(http.StatusOK, []any{})
			return
		}
		hash, err := computeMD5(romPath)
		if err != nil {
			c.JSON(http.StatusOK, []any{})
			return
		}
		var lookupErr error
		raGameID, lookupErr = h.RAClient.GetGameIDFromHash(hash)
		if lookupErr != nil {
			c.JSON(http.StatusOK, []any{})
			return
		}
		// Cache for next time
		h.DB.Model(&db.Game{}).Where("id = ?", game.ID).Update("ra_game_id", raGameID)
	}

	// Decrypt RA token for API call
	raToken, err := h.decryptRAToken(&cred)
	if err != nil {
		slog.Error("failed to decrypt RA token", "user_id", uid, "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to access RA credentials"})
		return
	}

	// Fetch fresh progress from RA API
	_, userProgress, err := h.RAClient.GetGameInfoAndUserProgress(cred.RAUsername, raToken, raGameID)
	if err != nil {
		slog.Error("failed to fetch RA progress", "ra_game_id", raGameID, "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch progress from RetroAchievements"})
		return
	}

	// Look up current play time for this game
	var playTimeAtUnlock int64
	var playHistory db.PlayHistory
	if err := h.DB.Where("user_id = ? AND game_id = ?", uid, game.ID).First(&playHistory).Error; err == nil {
		playTimeAtUnlock = playHistory.PlayTime
	}

	// Store/update progress in DB
	for _, p := range userProgress {
		unlockedAt, _ := time.Parse("2006-01-02 15:04:05", p.UnlockedAt)
		var existing db.UserAchievementProgress
		result := h.DB.Where("user_id = ? AND achievement_ra_id = ?", uid, p.AchievementID).First(&existing)
		if result.Error == nil {
			// Update existing record but preserve the original PlayTimeAtUnlock
			existing.UnlockedAt = unlockedAt
			existing.IsHardcore = p.IsHardcore
			h.DB.Save(&existing)
		} else {
			// New unlock — capture current play time
			h.DB.Create(&db.UserAchievementProgress{
				UserID:           uid,
				AchievementRAID:  p.AchievementID,
				RAGameID:         raGameID,
				UnlockedAt:       unlockedAt,
				IsHardcore:       p.IsHardcore,
				PlayTimeAtUnlock: playTimeAtUnlock,
			})
		}
	}

	// Build enriched progress response with per-achievement playTimeAtUnlock from DB
	// Read back stored values to get per-achievement PlayTimeAtUnlock
	achIDs := make([]uint, 0, len(userProgress))
	for _, p := range userProgress {
		achIDs = append(achIDs, p.AchievementID)
	}
	var storedProgress []db.UserAchievementProgress
	h.DB.Where("user_id = ? AND achievement_ra_id IN ?", uid, achIDs).Find(&storedProgress)
	storedMap := make(map[uint]db.UserAchievementProgress, len(storedProgress))
	for _, sp := range storedProgress {
		storedMap[sp.AchievementRAID] = sp
	}

	enrichedProgress := make([]RAProgressEntry, 0, len(userProgress))
	for _, p := range userProgress {
		var ptau int64
		if sp, ok := storedMap[p.AchievementID]; ok {
			ptau = sp.PlayTimeAtUnlock
		}
		enrichedProgress = append(enrichedProgress, RAProgressEntry{
			AchievementID:    p.AchievementID,
			UnlockedAt:       p.UnlockedAt,
			IsHardcore:       p.IsHardcore,
			PlayTimeAtUnlock: ptau,
		})
	}

	c.JSON(http.StatusOK, gin.H{
		"raGameId": raGameID,
		"progress": enrichedProgress,
	})
}

// GetRecentAchievements returns the user's most recently unlocked achievements across all games.
func (h *RAHandler) GetRecentAchievements(c *gin.Context) {
	uid := getUserID(c)

	// Fetch 20 most recent unlocks
	var progressRows []db.UserAchievementProgress
	if err := h.DB.Where("user_id = ?", uid).
		Order("unlocked_at DESC").
		Limit(20).
		Find(&progressRows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch recent achievements"})
		return
	}

	if len(progressRows) == 0 {
		c.JSON(http.StatusOK, gin.H{"achievements": []any{}})
		return
	}

	// Collect unique RA game IDs to load caches
	raGameIDSet := make(map[uint]bool)
	for _, p := range progressRows {
		raGameIDSet[p.RAGameID] = true
	}
	raGameIDs := make([]uint, 0, len(raGameIDSet))
	for id := range raGameIDSet {
		raGameIDs = append(raGameIDs, id)
	}

	// Load achievement caches
	var caches []db.GameAchievementCache
	h.DB.Where("ra_game_id IN ?", raGameIDs).Find(&caches)

	type cacheData struct {
		Cache        db.GameAchievementCache
		Achievements []retroachievements.Achievement
	}
	cacheMap := make(map[uint]cacheData)
	for _, cache := range caches {
		var achievements []retroachievements.Achievement
		if err := json.Unmarshal([]byte(cache.AchievementJSON), &achievements); err != nil {
			slog.Warn("failed to unmarshal cached achievement data", "ra_game_id", cache.RAGameID, "error", err)
		}
		cacheMap[cache.RAGameID] = cacheData{Cache: cache, Achievements: achievements}
	}

	// Load games by cache GameIDs
	gameIDs := make([]uint, 0, len(caches))
	for _, cache := range caches {
		if cache.GameID > 0 {
			gameIDs = append(gameIDs, cache.GameID)
		}
	}
	var games []db.Game
	if len(gameIDs) > 0 {
		h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games)
	}
	gameMap := make(map[uint]db.Game)
	for _, g := range games {
		gameMap[g.ID] = g
	}

	// Build response
	results := make([]RARecentAchievementResponse, 0, len(progressRows))
	for _, p := range progressRows {
		entry := RARecentAchievementResponse{
			AchievementRAID:  p.AchievementRAID,
			UnlockedAt:       p.UnlockedAt,
			IsHardcore:       p.IsHardcore,
			PlayTimeAtUnlock: p.PlayTimeAtUnlock,
		}

		// Enrich from cache
		if cd, ok := cacheMap[p.RAGameID]; ok {
			for _, a := range cd.Achievements {
				if a.ID == p.AchievementRAID {
					entry.Title = a.Title
					entry.Description = a.Description
					entry.Points = a.Points
					entry.BadgeURL = a.BadgeURL
					break
				}
			}
			if game, ok := gameMap[cd.Cache.GameID]; ok {
				entry.GameID = strconv.FormatUint(uint64(game.ID), 10)
				entry.GameTitle = game.Title
				entry.CoverURL = game.CoverURL
				entry.ConsoleName = game.Console.Name
			}
		}

		results = append(results, entry)
	}

	c.JSON(http.StatusOK, gin.H{"achievements": results})
}

// GetUnlockedAchievements returns ALL of the user's unlocked achievements across all games,
// enriched with title, badge, rarity, and game context. Used by the showcase picker.
func (h *RAHandler) GetUnlockedAchievements(c *gin.Context) {
	uid := getUserID(c)

	var progressRows []db.UserAchievementProgress
	if err := h.DB.Where("user_id = ?", uid).
		Order("unlocked_at DESC").
		Find(&progressRows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch unlocked achievements"})
		return
	}

	if len(progressRows) == 0 {
		c.JSON(http.StatusOK, gin.H{"achievements": []any{}})
		return
	}

	// Collect unique RA game IDs
	raGameIDSet := make(map[uint]bool)
	for _, p := range progressRows {
		raGameIDSet[p.RAGameID] = true
	}
	raGameIDs := make([]uint, 0, len(raGameIDSet))
	for id := range raGameIDSet {
		raGameIDs = append(raGameIDs, id)
	}

	// Load achievement caches
	var caches []db.GameAchievementCache
	h.DB.Where("ra_game_id IN ?", raGameIDs).Find(&caches)

	type cacheData struct {
		Cache        db.GameAchievementCache
		Achievements []retroachievements.Achievement
	}
	cacheMap := make(map[uint]cacheData)
	for _, cache := range caches {
		var achievements []retroachievements.Achievement
		if err := json.Unmarshal([]byte(cache.AchievementJSON), &achievements); err != nil {
			slog.Warn("failed to unmarshal cached achievement data", "ra_game_id", cache.RAGameID, "error", err)
		}
		cacheMap[cache.RAGameID] = cacheData{Cache: cache, Achievements: achievements}
	}

	// Load games
	gameIDs := make([]uint, 0, len(caches))
	for _, cache := range caches {
		if cache.GameID > 0 {
			gameIDs = append(gameIDs, cache.GameID)
		}
	}
	var games []db.Game
	if len(gameIDs) > 0 {
		h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games)
	}
	gameMap := make(map[uint]db.Game)
	for _, g := range games {
		gameMap[g.ID] = g
	}

	results := make([]RAUnlockedAchievementResponse, 0, len(progressRows))
	for _, p := range progressRows {
		entry := RAUnlockedAchievementResponse{
			AchievementRAID: p.AchievementRAID,
			RAGameID:        p.RAGameID,
		}

		if cd, ok := cacheMap[p.RAGameID]; ok {
			for _, a := range cd.Achievements {
				if a.ID == p.AchievementRAID {
					entry.Title = a.Title
					entry.Description = a.Description
					entry.Points = a.Points
					entry.BadgeURL = a.BadgeURL
					entry.RarityPercent = a.RarityPercent
					break
				}
			}
			if game, ok := gameMap[cd.Cache.GameID]; ok {
				entry.GameTitle = game.Title
				entry.ConsoleName = game.Console.Name
			}
		}

		results = append(results, entry)
	}

	c.JSON(http.StatusOK, gin.H{"achievements": results})
}

// GetAchievementTimeline returns the user's achievement timeline for a specific game.
func (h *RAHandler) GetAchievementTimeline(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")

	// Look up the game
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, "id = ?", gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	if err := requirePlayableConsole(h.DB, game.ID); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: errNonPlayableConsole.Error()})
		return
	}

	// Find the achievement cache for this game
	var cache db.GameAchievementCache
	if err := h.DB.Where("game_id = ?", game.ID).First(&cache).Error; err != nil {
		c.JSON(http.StatusOK, gin.H{
			"raGameId":          0,
			"gameTitle":         game.Title,
			"totalPlayTime":     0,
			"timeline":          []any{},
			"totalAchievements": 0,
			"unlockedCount":     0,
			"totalPoints":       0,
			"earnedPoints":      0,
		})
		return
	}

	// Parse cached achievements
	var achievements []retroachievements.Achievement
	if err := json.Unmarshal([]byte(cache.AchievementJSON), &achievements); err != nil {
		slog.Warn("failed to unmarshal cached achievement data", "ra_game_id", cache.RAGameID, "error", err)
	}
	achMap := make(map[uint]retroachievements.Achievement)
	for _, a := range achievements {
		achMap[a.ID] = a
	}

	// Get user's progress for this RA game, ordered chronologically
	var progressRows []db.UserAchievementProgress
	h.DB.Where("user_id = ? AND ra_game_id = ?", uid, cache.RAGameID).
		Order("unlocked_at ASC").
		Find(&progressRows)

	// Get total play time
	var totalPlayTime int64
	var playHistory db.PlayHistory
	if err := h.DB.Where("user_id = ? AND game_id = ?", uid, game.ID).First(&playHistory).Error; err == nil {
		totalPlayTime = playHistory.PlayTime
	}

	// Build timeline
	timeline := make([]RATimelineEntryResponse, 0, len(progressRows))
	earnedPoints := 0
	for _, p := range progressRows {
		entry := RATimelineEntryResponse{
			AchievementRAID:  p.AchievementRAID,
			UnlockedAt:       p.UnlockedAt,
			IsHardcore:       p.IsHardcore,
			PlayTimeAtUnlock: p.PlayTimeAtUnlock,
		}
		if a, ok := achMap[p.AchievementRAID]; ok {
			entry.Title = a.Title
			entry.Description = a.Description
			entry.Points = a.Points
			entry.BadgeURL = a.BadgeURL
			earnedPoints += a.Points
		}
		timeline = append(timeline, entry)
	}

	c.JSON(http.StatusOK, gin.H{
		"raGameId":          cache.RAGameID,
		"gameTitle":         game.Title,
		"totalPlayTime":     totalPlayTime,
		"timeline":          timeline,
		"totalAchievements": cache.TotalCount,
		"unlockedCount":     len(progressRows),
		"totalPoints":       cache.TotalPoints,
		"earnedPoints":      earnedPoints,
	})
}

// GetAchievementLeaderboard returns achievement stats per player for a specific game.
func (h *RAHandler) GetAchievementLeaderboard(c *gin.Context) {
	gameID := c.Param("id")

	// Look up the game
	var game db.Game
	if err := h.DB.First(&game, "id = ?", gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	if err := requirePlayableConsole(h.DB, game.ID); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: errNonPlayableConsole.Error()})
		return
	}

	// Find the achievement cache for this game
	var cache db.GameAchievementCache
	if err := h.DB.Where("game_id = ?", game.ID).First(&cache).Error; err != nil {
		c.JSON(http.StatusOK, gin.H{
			"raGameId":          0,
			"totalAchievements": 0,
			"leaderboard":       []any{},
		})
		return
	}

	// Query grouped stats per user
	type leaderboardRow struct {
		UserID         uint
		Username       string
		AvatarURL      string
		UnlockedCount  int
		EarnedPoints   int
		LastUnlockedAt string
		FirstUnlockedAt string
	}

	// Parse cached achievements to build points map
	var achievements []retroachievements.Achievement
	if err := json.Unmarshal([]byte(cache.AchievementJSON), &achievements); err != nil {
		slog.Warn("failed to unmarshal cached achievement data", "ra_game_id", cache.RAGameID, "error", err)
	}
	pointsMap := make(map[uint]int)
	for _, a := range achievements {
		pointsMap[a.ID] = a.Points
	}

	// Get all progress for this RA game grouped by user
	var progressRows []db.UserAchievementProgress
	h.DB.Where("ra_game_id = ?", cache.RAGameID).Find(&progressRows)

	// Group by user
	type userStats struct {
		UnlockedCount   int
		EarnedPoints    int
		FirstUnlockedAt time.Time
		LastUnlockedAt  time.Time
	}
	userStatsMap := make(map[uint]*userStats)
	for _, p := range progressRows {
		stats, ok := userStatsMap[p.UserID]
		if !ok {
			stats = &userStats{
				FirstUnlockedAt: p.UnlockedAt,
				LastUnlockedAt:  p.UnlockedAt,
			}
			userStatsMap[p.UserID] = stats
		}
		stats.UnlockedCount++
		stats.EarnedPoints += pointsMap[p.AchievementRAID]
		if p.UnlockedAt.Before(stats.FirstUnlockedAt) {
			stats.FirstUnlockedAt = p.UnlockedAt
		}
		if p.UnlockedAt.After(stats.LastUnlockedAt) {
			stats.LastUnlockedAt = p.UnlockedAt
		}
	}

	if len(userStatsMap) == 0 {
		c.JSON(http.StatusOK, gin.H{
			"raGameId":          cache.RAGameID,
			"totalAchievements": cache.TotalCount,
			"leaderboard":       []any{},
		})
		return
	}

	// Load users
	userIDs := make([]uint, 0, len(userStatsMap))
	for uid := range userStatsMap {
		userIDs = append(userIDs, uid)
	}
	var users []db.User
	h.DB.Where("id IN ?", userIDs).Find(&users)
	userMap := make(map[uint]db.User)
	for _, u := range users {
		userMap[u.ID] = u
	}

	// Build response sorted by unlocked count descending
	entries := make([]RALeaderboardEntryResponse, 0, len(userStatsMap))
	for uid, stats := range userStatsMap {
		user := userMap[uid]
		entries = append(entries, RALeaderboardEntryResponse{
			UserID:          strconv.FormatUint(uint64(uid), 10),
			Username:        user.Username,
			AvatarURL:       user.AvatarURL,
			UnlockedCount:   stats.UnlockedCount,
			EarnedPoints:    stats.EarnedPoints,
			LastUnlockedAt:  stats.LastUnlockedAt,
			FirstUnlockedAt: stats.FirstUnlockedAt,
			IsComplete:      stats.UnlockedCount >= cache.TotalCount,
		})
	}

	// Sort by unlocked count descending
	sort.Slice(entries, func(i, j int) bool {
		return entries[i].UnlockedCount > entries[j].UnlockedCount
	})

	c.JSON(http.StatusOK, gin.H{
		"raGameId":          cache.RAGameID,
		"totalAchievements": cache.TotalCount,
		"leaderboard":       entries,
	})
}

// computeMD5 calculates the MD5 hash of a file.
// computeMD5 delegates to the shared implementation in the retroachievements package.
func computeMD5(path string) (string, error) {
	return retroachievements.ComputeMD5(path)
}
