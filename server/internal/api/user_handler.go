package api

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"log/slog"
	"net"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// UserHandler handles user profile and preference endpoints.
type UserHandler struct {
	DB        *gorm.DB
	Hub       *ws.Hub
	JWTSecret string
}

// GetProfile returns the current user's profile.
func (h *UserHandler) GetProfile(c *gin.Context) {
	userID, _ := c.Get("userId")
	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}
	c.JSON(http.StatusOK, ToUserResponse(user))
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
		Email           string `json:"email"`
		AvatarURL       string `json:"avatarUrl"`
		CurrentPassword string `json:"currentPassword"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	if req.Email != "" {
		// Require password confirmation to change email
		if req.CurrentPassword == "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "current password is required to change email"})
			return
		}
		if !auth.CheckPassword(req.CurrentPassword, user.PasswordHash) {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "incorrect password"})
			return
		}
		// Check uniqueness before updating
		var existing db.User
		if err := h.DB.Where("email = ? AND id != ?", req.Email, user.ID).First(&existing).Error; err == nil {
			c.JSON(http.StatusConflict, gin.H{"error": "email already in use"})
			return
		}
		user.Email = req.Email
	}
	if req.AvatarURL != "" {
		parsed, err := url.Parse(req.AvatarURL)
		if err != nil || (parsed.Scheme != "https" && parsed.Scheme != "http") {
			c.JSON(http.StatusBadRequest, gin.H{"error": "avatar URL must be a valid HTTP(S) URL"})
			return
		}
		if len(req.AvatarURL) > 512 {
			c.JSON(http.StatusBadRequest, gin.H{"error": "avatar URL too long"})
			return
		}
		if isPrivateURL(req.AvatarURL) {
			c.JSON(http.StatusBadRequest, gin.H{"error": "avatar URL must not point to internal networks"})
			return
		}
		user.AvatarURL = req.AvatarURL
	}

	if err := h.DB.Save(&user).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update profile"})
		return
	}

	c.JSON(http.StatusOK, ToUserResponse(user))
}

// changePasswordRequest is the JSON body for PUT /api/user/password.
type changePasswordRequest struct {
	CurrentPassword string `json:"currentPassword" binding:"required"`
	NewPassword     string `json:"newPassword" binding:"required,min=8,max=72"`
}

// ChangePassword lets the authenticated user change their own password.
func (h *UserHandler) ChangePassword(c *gin.Context) {
	userID, _ := c.Get("userId")
	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}

	var req changePasswordRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	if !auth.CheckPassword(req.CurrentPassword, user.PasswordHash) {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "incorrect current password"})
		return
	}

	hash, err := auth.HashPassword(req.NewPassword)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to hash password"})
		return
	}

	user.PasswordHash = hash
	user.TokenVersion++

	if err := h.DB.Save(&user).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update password"})
		return
	}

	// Revoke all refresh tokens for this user
	h.DB.Where("user_id = ?", user.ID).Delete(&db.RefreshToken{})

	// Blacklist the current access token
	var token string
	header := c.GetHeader("Authorization")
	if header != "" {
		parts := strings.SplitN(header, " ", 2)
		if len(parts) == 2 && parts[0] == "Bearer" {
			token = parts[1]
		}
	}
	if token != "" {
		claims, err := auth.ValidateAccessToken(token, h.JWTSecret)
		if err == nil && claims.ExpiresAt != nil {
			tokenHash := sha256.Sum256([]byte(token))
			bl := db.TokenBlacklist{
				TokenHash: hex.EncodeToString(tokenHash[:]),
				ExpiresAt: claims.ExpiresAt.Time,
			}
			h.DB.Create(&bl)
		}
	}

	c.JSON(http.StatusOK, gin.H{"message": "password changed"})
}

// preferencesResponse is the JSON shape for the preferences endpoints.
type preferencesResponse struct {
	ShowPerformanceOverlay  bool                           `json:"showPerformanceOverlay"`
	AutoSaveEnabled         bool                           `json:"autoSaveEnabled"`
	AutoLoadSaveEnabled     bool                           `json:"autoLoadSaveEnabled"`
	SelectedShader          string                         `json:"selectedShader"`
	SelectedTheme           string                         `json:"selectedTheme"`
	DefaultSecondScreenPage string                         `json:"defaultSecondScreenPage"`
	ConsoleShaders          map[string]string              `json:"consoleShaders"`
	SelectedKeyMapping      string                         `json:"selectedKeyMapping"`
	CustomKeyMapping        map[string]string              `json:"customKeyMapping"`
	ConsoleKeyMappings      map[string]consoleKeyMappingDTO `json:"consoleKeyMappings"`
	PreferredRegions        []string                       `json:"preferredRegions"`
	RALinked                bool                           `json:"raLinked"`
	RAUsername              string                         `json:"raUsername"`
	RAHardcoreEnabled       bool                           `json:"raHardcoreEnabled"`
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

	selectedTheme := user.SelectedTheme
	if selectedTheme == "" {
		selectedTheme = "default-dark"
	}

	defaultSecondScreenPage := user.DefaultSecondScreenPage
	if defaultSecondScreenPage == "" {
		defaultSecondScreenPage = "art"
	}

	preferredRegions := parsePreferredRegions(user.PreferredRegions)

	var raCred db.RetroAchievementCredential
	raLinked := h.DB.Where("user_id = ?", uid).First(&raCred).Error == nil

	c.JSON(http.StatusOK, preferencesResponse{
		ShowPerformanceOverlay:  user.ShowPerfOverlay,
		AutoSaveEnabled:         user.AutoSaveEnabled,
		AutoLoadSaveEnabled:     user.AutoLoadSaveEnabled,
		SelectedShader:          user.SelectedShader,
		SelectedTheme:           selectedTheme,
		DefaultSecondScreenPage: defaultSecondScreenPage,
		ConsoleShaders:          consoleShaders,
		SelectedKeyMapping:      selectedKeyMapping,
		CustomKeyMapping:        customKeyMapping,
		ConsoleKeyMappings:      consoleKeyMappings,
		PreferredRegions:        preferredRegions,
		RALinked:                raLinked,
		RAUsername:              raCred.RAUsername,
		RAHardcoreEnabled:       raCred.HardcoreEnabled,
	})
}

// parsePreferredRegions splits a comma-separated region string into a slice,
// trimming whitespace and filtering empty strings. Returns an empty slice (not nil)
// when the input is empty.
func parsePreferredRegions(s string) []string {
	if s == "" {
		return []string{}
	}
	parts := strings.Split(s, ",")
	result := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			result = append(result, p)
		}
	}
	return result
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
		ShowPerformanceOverlay  *bool                            `json:"showPerformanceOverlay"`
		AutoSaveEnabled         *bool                            `json:"autoSaveEnabled"`
		AutoLoadSaveEnabled     *bool                            `json:"autoLoadSaveEnabled"`
		SelectedShader          *string                          `json:"selectedShader"`
		SelectedTheme           *string                          `json:"selectedTheme"`
		DefaultSecondScreenPage *string                          `json:"defaultSecondScreenPage"`
		ConsoleShaders          map[string]string                `json:"consoleShaders"`
		SelectedKeyMapping      *string                          `json:"selectedKeyMapping"`
		CustomKeyMapping        map[string]string                `json:"customKeyMapping"`
		ConsoleKeyMappings      map[string]consoleKeyMappingDTO  `json:"consoleKeyMappings"`
		PreferredRegions        *[]string                        `json:"preferredRegions"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
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
	if req.SelectedTheme != nil {
		user.SelectedTheme = *req.SelectedTheme
	}
	if req.DefaultSecondScreenPage != nil {
		valid := map[string]bool{"art": true, "controls": true, "dashboard": true, "save_slots": true}
		if !valid[*req.DefaultSecondScreenPage] {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid defaultSecondScreenPage value; must be one of: art, controls, dashboard, save_slots"})
			return
		}
		user.DefaultSecondScreenPage = *req.DefaultSecondScreenPage
	}
	if req.SelectedKeyMapping != nil {
		user.SelectedKeyMapping = *req.SelectedKeyMapping
	}
	if req.CustomKeyMapping != nil {
		b, _ := json.Marshal(req.CustomKeyMapping)
		user.CustomKeyMapping = string(b)
	}
	if req.PreferredRegions != nil {
		user.PreferredRegions = strings.Join(*req.PreferredRegions, ",")
	}

	if err := h.DB.Save(&user).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update preferences"})
		return
	}

	// Process per-console shader updates (keys are console abbreviations)
	if req.ConsoleShaders != nil {
		for consoleAbbr, shader := range req.ConsoleShaders {
			var console db.Console
			if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleAbbr).First(&console).Error; err != nil {
				continue
			}
			if shader == "" || shader == "none" {
				// Delete the row to revert to global default
				h.DB.Where("user_id = ? AND console_id = ?", uid, console.ID).
					Delete(&db.ConsoleShaderPreference{})
			} else {
				// Upsert: update if exists, create if not
				var existing db.ConsoleShaderPreference
				result := h.DB.Where("user_id = ? AND console_id = ?", uid, console.ID).First(&existing)
				if result.Error == nil {
					h.DB.Model(&existing).Update("shader", shader)
				} else {
					h.DB.Create(&db.ConsoleShaderPreference{
						UserID:    uid,
						ConsoleID: console.ID,
						Shader:    shader,
					})
				}
			}
		}
	}

	// Process per-console key mapping updates (keys are console abbreviations)
	if req.ConsoleKeyMappings != nil {
		for consoleAbbr, km := range req.ConsoleKeyMappings {
			var console db.Console
			if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleAbbr).First(&console).Error; err != nil {
				continue
			}
			if km.SelectedMapping == "" {
				// Hard delete to avoid soft-delete + unique constraint conflict
				h.DB.Unscoped().Where("user_id = ? AND console_id = ?", uid, console.ID).
					Delete(&db.ConsoleKeyMappingPreference{})
			} else {
				customJSON := ""
				if km.CustomMapping != nil {
					b, _ := json.Marshal(km.CustomMapping)
					customJSON = string(b)
				}
				// Upsert: use Unscoped to find soft-deleted rows and avoid unique constraint conflict
				var existing db.ConsoleKeyMappingPreference
				result := h.DB.Unscoped().Where("user_id = ? AND console_id = ?", uid, console.ID).First(&existing)
				if result.Error == nil {
					existing.SelectedMapping = km.SelectedMapping
					existing.CustomMapping = customJSON
					existing.DeletedAt = gorm.DeletedAt{}
					h.DB.Unscoped().Save(&existing)
				} else {
					h.DB.Create(&db.ConsoleKeyMappingPreference{
						UserID:          uid,
						ConsoleID:       console.ID,
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

	selectedTheme := user.SelectedTheme
	if selectedTheme == "" {
		selectedTheme = "default-dark"
	}

	defaultSecondScreenPage := user.DefaultSecondScreenPage
	if defaultSecondScreenPage == "" {
		defaultSecondScreenPage = "art"
	}

	preferredRegions := parsePreferredRegions(user.PreferredRegions)

	var raCred db.RetroAchievementCredential
	raLinked := h.DB.Where("user_id = ?", uid).First(&raCred).Error == nil

	c.JSON(http.StatusOK, preferencesResponse{
		ShowPerformanceOverlay:  user.ShowPerfOverlay,
		AutoSaveEnabled:         user.AutoSaveEnabled,
		AutoLoadSaveEnabled:     user.AutoLoadSaveEnabled,
		SelectedShader:          user.SelectedShader,
		SelectedTheme:           selectedTheme,
		DefaultSecondScreenPage: defaultSecondScreenPage,
		ConsoleShaders:          consoleShaders,
		SelectedKeyMapping:      selectedKeyMapping,
		CustomKeyMapping:        customKeyMapping,
		ConsoleKeyMappings:      consoleKeyMappings,
		PreferredRegions:        preferredRegions,
		RALinked:                raLinked,
		RAUsername:              raCred.RAUsername,
		RAHardcoreEnabled:       raCred.HardcoreEnabled,
	})
}

// buildConsoleShaderMap queries all ConsoleShaderPreference rows for the user
// and returns a map keyed by console abbreviation (lowercase).
func (h *UserHandler) buildConsoleShaderMap(userID uint) map[string]string {
	var prefs []db.ConsoleShaderPreference
	h.DB.Where("user_id = ?", userID).Find(&prefs)

	// Batch-load console abbreviations for all referenced console IDs
	consoleIDs := make([]uint, 0, len(prefs))
	for _, p := range prefs {
		consoleIDs = append(consoleIDs, p.ConsoleID)
	}
	abbrMap := resolveConsoleAbbrs(h.DB, consoleIDs)

	m := make(map[string]string, len(prefs))
	for _, p := range prefs {
		if abbr, ok := abbrMap[p.ConsoleID]; ok {
			m[abbr] = p.Shader
		}
	}
	return m
}

// buildConsoleKeyMappingMap queries all ConsoleKeyMappingPreference rows for the user
// and returns a map keyed by console abbreviation (lowercase).
func (h *UserHandler) buildConsoleKeyMappingMap(userID uint) map[string]consoleKeyMappingDTO {
	var prefs []db.ConsoleKeyMappingPreference
	h.DB.Where("user_id = ?", userID).Find(&prefs)

	// Batch-load console abbreviations for all referenced console IDs
	consoleIDs := make([]uint, 0, len(prefs))
	for _, p := range prefs {
		consoleIDs = append(consoleIDs, p.ConsoleID)
	}
	abbrMap := resolveConsoleAbbrs(h.DB, consoleIDs)

	m := make(map[string]consoleKeyMappingDTO, len(prefs))
	for _, p := range prefs {
		if abbr, ok := abbrMap[p.ConsoleID]; ok {
			m[abbr] = consoleKeyMappingDTO{
				SelectedMapping: p.SelectedMapping,
				CustomMapping:   parseJSONMap(p.CustomMapping),
			}
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

// privateNetworks defines the IP ranges considered internal/private.
var privateNetworks = func() []*net.IPNet {
	cidrs := []string{
		"0.0.0.0/8",       // Current network
		"10.0.0.0/8",      // Private (RFC 1918)
		"100.64.0.0/10",   // Carrier-grade NAT (RFC 6598)
		"127.0.0.0/8",     // Loopback
		"169.254.0.0/16",  // Link-local
		"172.16.0.0/12",   // Private (RFC 1918)
		"192.0.0.0/24",    // IETF protocol assignments
		"192.168.0.0/16",  // Private (RFC 1918)
		"198.18.0.0/15",   // Benchmarking (RFC 2544)
		"::1/128",         // IPv6 loopback
		"fc00::/7",        // IPv6 unique local
		"fe80::/10",       // IPv6 link-local
	}
	var nets []*net.IPNet
	for _, cidr := range cidrs {
		_, n, _ := net.ParseCIDR(cidr)
		nets = append(nets, n)
	}
	return nets
}()

// isPrivateURL returns true if the URL's hostname resolves to a private/internal IP.
func isPrivateURL(rawURL string) bool {
	parsed, err := url.Parse(rawURL)
	if err != nil {
		return true
	}
	hostname := parsed.Hostname()
	if strings.EqualFold(hostname, "localhost") {
		return true
	}
	ips, err := net.LookupIP(hostname)
	if err != nil {
		// Cannot resolve — treat as potentially private
		return true
	}
	for _, ip := range ips {
		// Normalize IPv4-mapped IPv6 addresses (e.g. ::ffff:127.0.0.1) to
		// their IPv4 form so they match the IPv4 CIDR ranges above.
		if v4 := ip.To4(); v4 != nil {
			ip = v4
		}
		for _, n := range privateNetworks {
			if n.Contains(ip) {
				return true
			}
		}
	}
	return false
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

	CreateActivityEvent(h.DB, h.Hub, uid, "favorited_game", game.ID, nil)

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

// PlayStatsEntry represents play stats for a single game.
type PlayStatsEntry struct {
	GameID       uint   `json:"gameId"`
	PlayTime     int64  `json:"playTime"`
	LastPlayedAt string `json:"lastPlayedAt"`
}

// GetPlayStats returns the current user's play history for all games they've played.
// Returns a flat array of {gameId, playTime, lastPlayedAt} entries.
func (h *UserHandler) GetPlayStats(c *gin.Context) {
	userID, _ := c.Get("userId")

	var histories []db.PlayHistory
	if err := h.DB.Where("user_id = ?", userID).Find(&histories).Error; err != nil {
		slog.Error("failed to fetch play stats", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch play stats"})
		return
	}

	result := make([]PlayStatsEntry, 0, len(histories))
	for _, ph := range histories {
		if ph.PlayTime <= 0 && ph.LastPlayed.IsZero() {
			continue
		}
		result = append(result, PlayStatsEntry{
			GameID:       ph.GameID,
			PlayTime:     ph.PlayTime,
			LastPlayedAt: ph.LastPlayed.Format(time.RFC3339),
		})
	}

	c.JSON(http.StatusOK, result)
}
