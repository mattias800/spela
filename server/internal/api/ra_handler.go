package api

import (
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"gorm.io/gorm"
)

// RAHandler handles RetroAchievements endpoints.
type RAHandler struct {
	DB       *gorm.DB
	RAClient *retroachievements.RAClient
	GameDir  string
}

// LinkAccount links a user's RetroAchievements account by exchanging credentials for a token.
func (h *RAHandler) LinkAccount(c *gin.Context) {
	uid := getUserID(c)

	var req struct {
		Username string `json:"username" binding:"required"`
		Password string `json:"password" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "username and password are required"})
		return
	}

	token, err := h.RAClient.LoginWithPassword(req.Username, req.Password)
	if err != nil {
		slog.Warn("RA login failed", "user_id", uid, "error", err)
		c.JSON(http.StatusUnauthorized, gin.H{"error": "RetroAchievements login failed: invalid credentials"})
		return
	}

	// Upsert credential
	var cred db.RetroAchievementCredential
	result := h.DB.Where("user_id = ?", uid).First(&cred)
	if result.Error == nil {
		cred.RAUsername = req.Username
		cred.RAToken = token
		if err := h.DB.Save(&cred).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update RA credentials"})
			return
		}
	} else {
		cred = db.RetroAchievementCredential{
			UserID:     uid,
			RAUsername:  req.Username,
			RAToken:    token,
		}
		if err := h.DB.Create(&cred).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to store RA credentials"})
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
		c.JSON(http.StatusNotFound, gin.H{"error": "no RA account linked"})
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

	var req struct {
		HardcoreEnabled *bool `json:"hardcoreEnabled"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "no RA account linked"})
		return
	}

	if req.HardcoreEnabled != nil {
		cred.HardcoreEnabled = *req.HardcoreEnabled
	}

	if err := h.DB.Save(&cred).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update RA settings"})
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
		c.JSON(http.StatusNotFound, gin.H{"error": "no RA account linked"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"username": cred.RAUsername,
		"token":    cred.RAToken,
	})
}

// GetGameAchievements returns achievements for a game, using cache when available.
func (h *RAHandler) GetGameAchievements(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")

	// Look up the game
	var game db.Game
	if err := h.DB.First(&game, "id = ?", gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	// Get user's RA credentials — return empty achievements if not linked
	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		c.JSON(http.StatusOK, gin.H{
			"raGameId": 0, "totalCount": 0, "totalPoints": 0, "achievements": []any{},
		})
		return
	}

	// Compute MD5 hash of the ROM file
	romPath := filepath.Join(h.GameDir, game.FilePath)
	hash, err := computeMD5(romPath)
	if err != nil {
		slog.Error("failed to compute ROM hash", "path", romPath, "error", err)
		c.JSON(http.StatusOK, gin.H{
			"raGameId": 0, "totalCount": 0, "totalPoints": 0, "achievements": []any{},
		})
		return
	}

	// Look up RA game ID from hash
	raGameID, err := h.RAClient.GetGameIDFromHash(hash)
	if err != nil {
		slog.Warn("RA game ID lookup failed", "hash", hash, "error", err)
		c.JSON(http.StatusOK, gin.H{
			"raGameId": 0, "totalCount": 0, "totalPoints": 0, "achievements": []any{},
		})
		return
	}

	// Check cache (valid for 24 hours)
	var cache db.GameAchievementCache
	cacheHit := h.DB.Where("ra_game_id = ?", raGameID).First(&cache).Error == nil
	if cacheHit && time.Since(cache.CachedAt) < 24*time.Hour {
		var achievements []retroachievements.Achievement
		if err := json.Unmarshal([]byte(cache.AchievementJSON), &achievements); err != nil {
			slog.Warn("failed to unmarshal cached achievement data, will re-fetch", "ra_game_id", raGameID, "error", err)
		} else {
			c.JSON(http.StatusOK, gin.H{
				"raGameId":     raGameID,
				"title":        cache.Title,
				"achievements": achievements,
				"totalCount":   cache.TotalCount,
				"totalPoints":  cache.TotalPoints,
			})
			return
		}
	}

	// Fetch from RA API
	gameInfo, _, err := h.RAClient.GetGameInfoAndUserProgress(cred.RAUsername, cred.RAToken, raGameID)
	if err != nil {
		slog.Error("failed to fetch RA game info", "ra_game_id", raGameID, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch achievements from RetroAchievements"})
		return
	}

	// Cache the result
	achJSON, err := json.Marshal(gameInfo.Achievements)
	if err != nil {
		slog.Warn("failed to marshal achievement data for cache", "ra_game_id", raGameID, "error", err)
	}
	if cacheHit {
		cache.Title = gameInfo.Title
		cache.AchievementJSON = string(achJSON)
		cache.TotalCount = gameInfo.TotalCount
		cache.TotalPoints = gameInfo.TotalPoints
		cache.CachedAt = time.Now()
		cache.GameID = game.ID
		h.DB.Save(&cache)
	} else {
		h.DB.Create(&db.GameAchievementCache{
			RAGameID:        raGameID,
			GameID:          game.ID,
			Title:           gameInfo.Title,
			AchievementJSON: string(achJSON),
			TotalCount:      gameInfo.TotalCount,
			TotalPoints:     gameInfo.TotalPoints,
			CachedAt:        time.Now(),
		})
	}

	c.JSON(http.StatusOK, gin.H{
		"raGameId":     raGameID,
		"title":        gameInfo.Title,
		"achievements": gameInfo.Achievements,
		"totalCount":   gameInfo.TotalCount,
		"totalPoints":  gameInfo.TotalPoints,
	})
}

// GetAchievementProgress returns the user's achievement progress for a game.
func (h *RAHandler) GetAchievementProgress(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")

	// Look up the game
	var game db.Game
	if err := h.DB.First(&game, "id = ?", gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	// Get user's RA credentials — return empty progress if not linked
	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		c.JSON(http.StatusOK, []any{})
		return
	}

	// Compute MD5 hash of the ROM file
	romPath := filepath.Join(h.GameDir, game.FilePath)
	hash, err := computeMD5(romPath)
	if err != nil {
		slog.Error("failed to compute ROM hash", "path", romPath, "error", err)
		c.JSON(http.StatusOK, []any{})
		return
	}

	// Look up RA game ID from hash
	raGameID, err := h.RAClient.GetGameIDFromHash(hash)
	if err != nil {
		c.JSON(http.StatusOK, []any{})
		return
	}

	// Fetch fresh progress from RA API
	_, userProgress, err := h.RAClient.GetGameInfoAndUserProgress(cred.RAUsername, cred.RAToken, raGameID)
	if err != nil {
		slog.Error("failed to fetch RA progress", "ra_game_id", raGameID, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch progress from RetroAchievements"})
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
	type progressEntry struct {
		AchievementID    uint   `json:"achievementId"`
		UnlockedAt       string `json:"unlockedAt"`
		IsHardcore       bool   `json:"isHardcore"`
		PlayTimeAtUnlock int64  `json:"playTimeAtUnlock"`
	}

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

	enrichedProgress := make([]progressEntry, 0, len(userProgress))
	for _, p := range userProgress {
		var ptau int64
		if sp, ok := storedMap[p.AchievementID]; ok {
			ptau = sp.PlayTimeAtUnlock
		}
		enrichedProgress = append(enrichedProgress, progressEntry{
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch recent achievements"})
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
	type recentAchievement struct {
		AchievementRAID  uint      `json:"achievementRaId"`
		Title            string    `json:"title"`
		Description      string    `json:"description"`
		Points           int       `json:"points"`
		BadgeURL         string    `json:"badgeUrl"`
		UnlockedAt       time.Time `json:"unlockedAt"`
		IsHardcore       bool      `json:"isHardcore"`
		PlayTimeAtUnlock int64     `json:"playTimeAtUnlock"`
		GameID           string    `json:"gameId"`
		GameTitle        string    `json:"gameTitle"`
		ConsoleName      string    `json:"consoleName"`
		CoverURL         string    `json:"coverUrl"`
	}

	results := make([]recentAchievement, 0, len(progressRows))
	for _, p := range progressRows {
		entry := recentAchievement{
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

// GetAchievementTimeline returns the user's achievement timeline for a specific game.
func (h *RAHandler) GetAchievementTimeline(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")

	// Look up the game
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, "id = ?", gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
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
	type timelineEntry struct {
		AchievementRAID  uint      `json:"achievementRaId"`
		Title            string    `json:"title"`
		Description      string    `json:"description"`
		Points           int       `json:"points"`
		BadgeURL         string    `json:"badgeUrl"`
		UnlockedAt       time.Time `json:"unlockedAt"`
		IsHardcore       bool      `json:"isHardcore"`
		PlayTimeAtUnlock int64     `json:"playTimeAtUnlock"`
	}

	timeline := make([]timelineEntry, 0, len(progressRows))
	earnedPoints := 0
	for _, p := range progressRows {
		entry := timelineEntry{
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
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
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
	type leaderboardEntry struct {
		UserID          string    `json:"userId"`
		Username        string    `json:"username"`
		AvatarURL       string    `json:"avatarUrl"`
		UnlockedCount   int       `json:"unlockedCount"`
		EarnedPoints    int       `json:"earnedPoints"`
		LastUnlockedAt  time.Time `json:"lastUnlockedAt"`
		FirstUnlockedAt time.Time `json:"firstUnlockedAt"`
		IsComplete      bool      `json:"isComplete"`
	}

	entries := make([]leaderboardEntry, 0, len(userStatsMap))
	for uid, stats := range userStatsMap {
		user := userMap[uid]
		entries = append(entries, leaderboardEntry{
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
func computeMD5(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", fmt.Errorf("opening file for hash: %w", err)
	}
	defer f.Close()

	h := md5.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", fmt.Errorf("computing file hash: %w", err)
	}

	return hex.EncodeToString(h.Sum(nil)), nil
}
