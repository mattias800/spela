package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"sort"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// UserHandler handles user profile and preference endpoints.
type UserHandler struct {
	DB *gorm.DB
}

// GetProfile returns the current user's profile.
func (h *UserHandler) GetProfile(c *gin.Context) {
	userID, _ := c.Get("userId")
	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}
	c.JSON(http.StatusOK, user)
}

// UpdateProfile updates the current user's profile.
func (h *UserHandler) UpdateProfile(c *gin.Context) {
	userID, _ := c.Get("userId")
	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}

	var req struct {
		Email     string `json:"email"`
		AvatarURL string `json:"avatarUrl"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	if req.Email != "" {
		user.Email = req.Email
	}
	if req.AvatarURL != "" {
		user.AvatarURL = req.AvatarURL
	}

	if err := h.DB.Save(&user).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update profile"})
		return
	}

	c.JSON(http.StatusOK, user)
}

// preferencesResponse is the JSON shape for the preferences endpoints.
type preferencesResponse struct {
	ShowPerformanceOverlay bool                           `json:"showPerformanceOverlay"`
	AutoSaveEnabled        bool                           `json:"autoSaveEnabled"`
	AutoLoadSaveEnabled    bool                           `json:"autoLoadSaveEnabled"`
	SelectedShader         string                         `json:"selectedShader"`
	ConsoleShaders         map[string]string              `json:"consoleShaders"`
	SelectedKeyMapping     string                         `json:"selectedKeyMapping"`
	CustomKeyMapping       map[string]string              `json:"customKeyMapping"`
	ConsoleKeyMappings     map[string]consoleKeyMappingDTO `json:"consoleKeyMappings"`
	RALinked               bool                           `json:"raLinked"`
	RAUsername             string                         `json:"raUsername"`
	RAHardcoreEnabled      bool                           `json:"raHardcoreEnabled"`
}

type consoleKeyMappingDTO struct {
	SelectedMapping string            `json:"selectedMapping"`
	CustomMapping   map[string]string `json:"customMapping,omitempty"`
}

// GetPreferences returns the current user's emulation preferences.
func (h *UserHandler) GetPreferences(c *gin.Context) {
	userID, _ := c.Get("userId")
	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}

	uid := userID.(uint)
	consoleShaders := h.buildConsoleShaderMap(uid)
	consoleKeyMappings := h.buildConsoleKeyMappingMap(uid)
	customKeyMapping := parseJSONMap(user.CustomKeyMapping)

	selectedKeyMapping := user.SelectedKeyMapping
	if selectedKeyMapping == "" {
		selectedKeyMapping = "arrows-left"
	}

	var raCred db.RetroAchievementCredential
	raLinked := h.DB.Where("user_id = ?", uid).First(&raCred).Error == nil

	c.JSON(http.StatusOK, preferencesResponse{
		ShowPerformanceOverlay: user.ShowPerfOverlay,
		AutoSaveEnabled:        user.AutoSaveEnabled,
		AutoLoadSaveEnabled:    user.AutoLoadSaveEnabled,
		SelectedShader:         user.SelectedShader,
		ConsoleShaders:         consoleShaders,
		SelectedKeyMapping:     selectedKeyMapping,
		CustomKeyMapping:       customKeyMapping,
		ConsoleKeyMappings:     consoleKeyMappings,
		RALinked:               raLinked,
		RAUsername:             raCred.RAUsername,
		RAHardcoreEnabled:      raCred.HardcoreEnabled,
	})
}

// UpdatePreferences partially updates the current user's emulation preferences.
func (h *UserHandler) UpdatePreferences(c *gin.Context) {
	userID, _ := c.Get("userId")
	uid := userID.(uint)
	var user db.User
	if err := h.DB.First(&user, uid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}

	var req struct {
		ShowPerformanceOverlay *bool                            `json:"showPerformanceOverlay"`
		AutoSaveEnabled        *bool                            `json:"autoSaveEnabled"`
		AutoLoadSaveEnabled    *bool                            `json:"autoLoadSaveEnabled"`
		SelectedShader         *string                          `json:"selectedShader"`
		ConsoleShaders         map[string]string                `json:"consoleShaders"`
		SelectedKeyMapping     *string                          `json:"selectedKeyMapping"`
		CustomKeyMapping       map[string]string                `json:"customKeyMapping"`
		ConsoleKeyMappings     map[string]consoleKeyMappingDTO  `json:"consoleKeyMappings"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: " + err.Error()})
		return
	}

	if req.ShowPerformanceOverlay != nil {
		user.ShowPerfOverlay = *req.ShowPerformanceOverlay
	}
	if req.AutoSaveEnabled != nil {
		user.AutoSaveEnabled = *req.AutoSaveEnabled
	}
	if req.AutoLoadSaveEnabled != nil {
		user.AutoLoadSaveEnabled = *req.AutoLoadSaveEnabled
	}
	if req.SelectedShader != nil {
		user.SelectedShader = *req.SelectedShader
	}
	if req.SelectedKeyMapping != nil {
		user.SelectedKeyMapping = *req.SelectedKeyMapping
	}
	if req.CustomKeyMapping != nil {
		b, _ := json.Marshal(req.CustomKeyMapping)
		user.CustomKeyMapping = string(b)
	}

	if err := h.DB.Save(&user).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update preferences"})
		return
	}

	// Process per-console shader updates
	if req.ConsoleShaders != nil {
		for consoleIDStr, shader := range req.ConsoleShaders {
			var consoleID uint
			if _, err := fmt.Sscanf(consoleIDStr, "%d", &consoleID); err != nil {
				continue
			}
			if shader == "" || shader == "none" {
				// Delete the row to revert to global default
				h.DB.Where("user_id = ? AND console_id = ?", uid, consoleID).
					Delete(&db.ConsoleShaderPreference{})
			} else {
				// Upsert: update if exists, create if not
				var existing db.ConsoleShaderPreference
				result := h.DB.Where("user_id = ? AND console_id = ?", uid, consoleID).First(&existing)
				if result.Error == nil {
					h.DB.Model(&existing).Update("shader", shader)
				} else {
					h.DB.Create(&db.ConsoleShaderPreference{
						UserID:    uid,
						ConsoleID: consoleID,
						Shader:    shader,
					})
				}
			}
		}
	}

	// Process per-console key mapping updates
	if req.ConsoleKeyMappings != nil {
		for consoleIDStr, km := range req.ConsoleKeyMappings {
			var consoleID uint
			if _, err := fmt.Sscanf(consoleIDStr, "%d", &consoleID); err != nil {
				continue
			}
			if km.SelectedMapping == "" {
				// Hard delete to avoid soft-delete + unique constraint conflict
				h.DB.Unscoped().Where("user_id = ? AND console_id = ?", uid, consoleID).
					Delete(&db.ConsoleKeyMappingPreference{})
			} else {
				customJSON := ""
				if km.CustomMapping != nil {
					b, _ := json.Marshal(km.CustomMapping)
					customJSON = string(b)
				}
				// Upsert: use Unscoped to find soft-deleted rows and avoid unique constraint conflict
				var existing db.ConsoleKeyMappingPreference
				result := h.DB.Unscoped().Where("user_id = ? AND console_id = ?", uid, consoleID).First(&existing)
				if result.Error == nil {
					existing.SelectedMapping = km.SelectedMapping
					existing.CustomMapping = customJSON
					existing.DeletedAt = gorm.DeletedAt{}
					h.DB.Unscoped().Save(&existing)
				} else {
					h.DB.Create(&db.ConsoleKeyMappingPreference{
						UserID:          uid,
						ConsoleID:       consoleID,
						SelectedMapping: km.SelectedMapping,
						CustomMapping:   customJSON,
					})
				}
			}
		}
	}

	consoleShaders := h.buildConsoleShaderMap(uid)
	consoleKeyMappings := h.buildConsoleKeyMappingMap(uid)
	customKeyMapping := parseJSONMap(user.CustomKeyMapping)

	selectedKeyMapping := user.SelectedKeyMapping
	if selectedKeyMapping == "" {
		selectedKeyMapping = "arrows-left"
	}

	var raCred db.RetroAchievementCredential
	raLinked := h.DB.Where("user_id = ?", uid).First(&raCred).Error == nil

	c.JSON(http.StatusOK, preferencesResponse{
		ShowPerformanceOverlay: user.ShowPerfOverlay,
		AutoSaveEnabled:        user.AutoSaveEnabled,
		AutoLoadSaveEnabled:    user.AutoLoadSaveEnabled,
		SelectedShader:         user.SelectedShader,
		ConsoleShaders:         consoleShaders,
		SelectedKeyMapping:     selectedKeyMapping,
		CustomKeyMapping:       customKeyMapping,
		ConsoleKeyMappings:     consoleKeyMappings,
		RALinked:               raLinked,
		RAUsername:             raCred.RAUsername,
		RAHardcoreEnabled:      raCred.HardcoreEnabled,
	})
}

// buildConsoleShaderMap queries all ConsoleShaderPreference rows for the user
// and returns a map keyed by console ID string.
func (h *UserHandler) buildConsoleShaderMap(userID uint) map[string]string {
	var prefs []db.ConsoleShaderPreference
	h.DB.Where("user_id = ?", userID).Find(&prefs)
	m := make(map[string]string, len(prefs))
	for _, p := range prefs {
		m[fmt.Sprintf("%d", p.ConsoleID)] = p.Shader
	}
	return m
}

// buildConsoleKeyMappingMap queries all ConsoleKeyMappingPreference rows for the user
// and returns a map keyed by console ID string.
func (h *UserHandler) buildConsoleKeyMappingMap(userID uint) map[string]consoleKeyMappingDTO {
	var prefs []db.ConsoleKeyMappingPreference
	h.DB.Where("user_id = ?", userID).Find(&prefs)
	m := make(map[string]consoleKeyMappingDTO, len(prefs))
	for _, p := range prefs {
		m[fmt.Sprintf("%d", p.ConsoleID)] = consoleKeyMappingDTO{
			SelectedMapping: p.SelectedMapping,
			CustomMapping:   parseJSONMap(p.CustomMapping),
		}
	}
	return m
}

// parseJSONMap parses a JSON string into a map[string]string, returning an empty map on error.
func parseJSONMap(s string) map[string]string {
	if s == "" {
		return map[string]string{}
	}
	var m map[string]string
	if err := json.Unmarshal([]byte(s), &m); err != nil {
		return map[string]string{}
	}
	return m
}

// GetRecentGames returns the user's recently played games as a flat Game array.
func (h *UserHandler) GetRecentGames(c *gin.Context) {
	uid := getUserID(c)

	var history []db.PlayHistory
	if err := h.DB.Where("user_id = ?", uid).
		Preload("Game").Preload("Game.Console").
		Order("last_played desc").
		Limit(20).
		Find(&history).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch recent games"})
		return
	}

	// Collect game IDs for batch enrichment
	gameIDs := make([]uint, 0, len(history))
	for _, ph := range history {
		if ph.Game.ID != 0 {
			gameIDs = append(gameIDs, ph.Game.ID)
		}
	}
	data := loadUserGameData(h.DB, uid, gameIDs)

	// Flatten: return Game[] with play history data merged in
	games := make([]GameResponse, 0, len(history))
	for _, ph := range history {
		if ph.Game.ID == 0 {
			continue
		}
		resp := toGameResponseWithData(ph.Game, &data)
		resp.LastPlayedAt = &ph.LastPlayed
		resp.TotalPlayTime = ph.PlayTime
		games = append(games, resp)
	}

	c.JSON(http.StatusOK, games)
}

// GetFavorites returns the user's favorite games as a flat Game array.
func (h *UserHandler) GetFavorites(c *gin.Context) {
	uid := getUserID(c)

	var favorites []db.Favorite
	if err := h.DB.Where("user_id = ?", uid).
		Preload("Game").Preload("Game.Console").
		Find(&favorites).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch favorites"})
		return
	}

	// Collect game IDs for batch enrichment
	gameIDs := make([]uint, 0, len(favorites))
	for _, fav := range favorites {
		if fav.Game.ID != 0 {
			gameIDs = append(gameIDs, fav.Game.ID)
		}
	}
	data := loadUserGameData(h.DB, uid, gameIDs)

	// Flatten: return Game[] with isFavorite=true
	games := make([]GameResponse, 0, len(favorites))
	for _, fav := range favorites {
		if fav.Game.ID == 0 {
			continue
		}
		resp := toGameResponseWithData(fav.Game, &data)
		resp.IsFavorite = true
		games = append(games, resp)
	}

	c.JSON(http.StatusOK, games)
}

// AddFavorite adds a game to the user's favorites.
func (h *UserHandler) AddFavorite(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("gameId")

	// Verify game exists
	var game db.Game
	if err := h.DB.First(&game, gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	fav := db.Favorite{
		UserID: uid,
		GameID: game.ID,
	}

	if err := h.DB.Create(&fav).Error; err != nil {
		c.JSON(http.StatusConflict, gin.H{"error": "already favorited"})
		return
	}

	c.JSON(http.StatusCreated, gin.H{"message": "favorite added"})
}

// RemoveFavorite removes a game from the user's favorites.
func (h *UserHandler) RemoveFavorite(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("gameId")

	result := h.DB.Where("user_id = ? AND game_id = ?", uid, gameID).Delete(&db.Favorite{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "favorite not found"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "favorite removed"})
}

// userStatsResponse is the response for GET /api/user/stats.
type userStatsResponse struct {
	TotalPlayTime    int64         `json:"totalPlayTime"`
	GamesPlayed      int64         `json:"gamesPlayed"`
	CurrentStreak    int           `json:"currentStreak"`
	LongestStreak    int           `json:"longestStreak"`
	MostPlayedGame   *GameResponse `json:"mostPlayedGame"`
	MostPlayedGameTime int64       `json:"mostPlayedGameTime"`
	LastPlayedAt     *time.Time    `json:"lastPlayedAt"`
}

// GetUserStats returns the current user's personal gameplay statistics.
func (h *UserHandler) GetUserStats(c *gin.Context) {
	uid := getUserID(c)

	// Aggregate stats
	var agg struct {
		TotalPlayTime int64
		GamesPlayed   int64
		LastPlayed    string
	}
	if err := h.DB.Model(&db.PlayHistory{}).
		Where("user_id = ?", uid).
		Select("COALESCE(SUM(play_time), 0) as total_play_time, COUNT(*) as games_played, MAX(last_played) as last_played").
		Scan(&agg).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch user stats"})
		return
	}

	if agg.GamesPlayed == 0 {
		c.JSON(http.StatusOK, userStatsResponse{
			TotalPlayTime:    0,
			GamesPlayed:      0,
			CurrentStreak:    0,
			LongestStreak:    0,
			MostPlayedGame:   nil,
			MostPlayedGameTime: 0,
			LastPlayedAt:     nil,
		})
		return
	}

	// Most played game
	var topPH db.PlayHistory
	h.DB.Where("user_id = ?", uid).Order("play_time DESC").First(&topPH)

	var mostPlayedGame *GameResponse
	var mostPlayedGameTime int64
	if topPH.ID != 0 {
		var game db.Game
		if err := h.DB.Preload("Console").First(&game, topPH.GameID).Error; err == nil {
			resp := ToGameResponse(game, h.DB, uid)
			mostPlayedGame = &resp
			mostPlayedGameTime = topPH.PlayTime
		}
	}

	// Streak calculation
	var playDateStrings []string
	h.DB.Model(&db.PlayHistory{}).
		Where("user_id = ?", uid).
		Select("DISTINCT DATE(last_played) as play_date").
		Order("play_date DESC").
		Pluck("play_date", &playDateStrings)

	playDates := make([]time.Time, 0, len(playDateStrings))
	for _, s := range playDateStrings {
		t := parseTimeString(s)
		if !t.IsZero() {
			playDates = append(playDates, t)
		}
	}

	currentStreak, longestStreak := calculateStreaks(playDates, time.Now())

	var lastPlayedAt *time.Time
	if agg.LastPlayed != "" {
		parsed := parseTimeString(agg.LastPlayed)
		if !parsed.IsZero() {
			lastPlayedAt = &parsed
		}
	}

	c.JSON(http.StatusOK, userStatsResponse{
		TotalPlayTime:      agg.TotalPlayTime,
		GamesPlayed:        agg.GamesPlayed,
		CurrentStreak:      currentStreak,
		LongestStreak:      longestStreak,
		MostPlayedGame:     mostPlayedGame,
		MostPlayedGameTime: mostPlayedGameTime,
		LastPlayedAt:       lastPlayedAt,
	})
}

// calculateStreaks computes current and longest consecutive day streaks from a list
// of play dates (expected sorted descending). today is provided for testability.
func calculateStreaks(playDates []time.Time, today time.Time) (currentStreak, longestStreak int) {
	if len(playDates) == 0 {
		return 0, 0
	}

	// Normalize all dates to date-only in UTC
	dates := make([]time.Time, 0, len(playDates))
	seen := make(map[string]bool, len(playDates))
	for _, d := range playDates {
		day := d.UTC().Truncate(24 * time.Hour)
		key := day.Format("2006-01-02")
		if !seen[key] {
			seen[key] = true
			dates = append(dates, day)
		}
	}

	// Sort ascending for streak calculation
	sort.Slice(dates, func(i, j int) bool {
		return dates[i].Before(dates[j])
	})

	todayDate := today.UTC().Truncate(24 * time.Hour)
	yesterdayDate := todayDate.AddDate(0, 0, -1)

	// Calculate longest streak
	longestStreak = 1
	streak := 1
	for i := 1; i < len(dates); i++ {
		diff := dates[i].Sub(dates[i-1])
		if diff == 24*time.Hour {
			streak++
			if streak > longestStreak {
				longestStreak = streak
			}
		} else if diff > 24*time.Hour {
			streak = 1
		}
		// if diff == 0 (same day), skip — shouldn't happen with dedup
	}

	// Calculate current streak (walk back from today/yesterday)
	lastDate := dates[len(dates)-1]
	if lastDate != todayDate && lastDate != yesterdayDate {
		return 0, longestStreak
	}

	currentStreak = 1
	for i := len(dates) - 2; i >= 0; i-- {
		diff := dates[i+1].Sub(dates[i])
		if diff == 24*time.Hour {
			currentStreak++
		} else {
			break
		}
	}

	return currentStreak, longestStreak
}
