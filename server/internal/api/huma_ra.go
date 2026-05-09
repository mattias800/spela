package api

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// --- Input / output types ---

// LinkRAAccountInput is the input for POST /api/user/ra/link.
type LinkRAAccountInput struct {
	Body LinkRAAccountRequest
}

// LinkRAAccountOutput wraps the linked response.
type LinkRAAccountOutput struct {
	Body RAStatusResponse
}

// UnlinkRAAccountInput is the input for DELETE /api/user/ra/link.
type UnlinkRAAccountInput struct{}

// UnlinkRAAccountOutput wraps the unlink response.
type UnlinkRAAccountOutput struct {
	Body RAStatusResponse
}

// GetRAAccountStatusInput is the input for GET /api/user/ra/status.
type GetRAAccountStatusInput struct{}

// RAStatusResponse is the wire format for RA status responses.
type RAStatusResponse struct {
	Linked          bool   `json:"linked"`
	Username        string `json:"username"`
	HardcoreEnabled bool   `json:"hardcoreEnabled"`
}

// GetRAAccountStatusOutput wraps the status response.
type GetRAAccountStatusOutput struct {
	Body RAStatusResponse
}

// UpdateRASettingsInput is the input for PUT /api/user/ra/settings.
type UpdateRASettingsInput struct {
	Body UpdateRASettingsRequest
}

// UpdateRASettingsOutput wraps the status response.
type UpdateRASettingsOutput struct {
	Body RAStatusResponse
}

// GetRATokenInput is the input for GET /api/user/ra/token.
type GetRATokenInput struct{}

// RATokenResponse is the wire format for the RA token response.
type RATokenResponse struct {
	Username string `json:"username"`
	Token    string `json:"token"`
}

// GetRATokenOutput wraps the RA token response and sets no-cache headers.
type GetRATokenOutput struct {
	CacheControl string `header:"Cache-Control"`
	Pragma       string `header:"Pragma"`
	Body         RATokenResponse
}

// GetAchievementProgressInput is the input for GET /api/games/{id}/achievements/progress.
type GetAchievementProgressInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
}

// GameAchievementProgressResponse is the wire format for per-game user progress.
type GameAchievementProgressResponse struct {
	RAGameID uint              `json:"raGameId"`
	Progress []RAProgressEntry `json:"progress"`
}

// GetAchievementProgressOutput wraps the progress response.
type GetAchievementProgressOutput struct {
	Body GameAchievementProgressResponse
}

// GetAchievementTimelineInput is the input for GET /api/games/{id}/achievements/timeline.
type GetAchievementTimelineInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
}

// AchievementTimelineResponse is the wire format for the timeline response.
type AchievementTimelineResponse struct {
	RAGameID          uint                      `json:"raGameId"`
	GameTitle         string                    `json:"gameTitle"`
	TotalPlayTime     int64                     `json:"totalPlayTime"`
	Timeline          []RATimelineEntryResponse `json:"timeline"`
	TotalAchievements int                       `json:"totalAchievements"`
	UnlockedCount     int                       `json:"unlockedCount"`
	TotalPoints       int                       `json:"totalPoints"`
	EarnedPoints      int                       `json:"earnedPoints"`
}

// GetAchievementTimelineOutput wraps the timeline response.
type GetAchievementTimelineOutput struct {
	Body AchievementTimelineResponse
}

// GetAchievementLeaderboardInput is the input for GET /api/games/{id}/achievements/leaderboard.
type GetAchievementLeaderboardInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
}

// AchievementLeaderboardResponse is the wire format for the leaderboard response.
type AchievementLeaderboardResponse struct {
	RAGameID          uint                         `json:"raGameId"`
	TotalAchievements int                          `json:"totalAchievements"`
	Leaderboard       []RALeaderboardEntryResponse `json:"leaderboard"`
}

// GetAchievementLeaderboardOutput wraps the leaderboard response.
type GetAchievementLeaderboardOutput struct {
	Body AchievementLeaderboardResponse
}

// GetRecentAchievementsInput is the input for GET /api/user/achievements/recent.
type GetRecentAchievementsInput struct{}

// RecentAchievementsResponse wraps the recent achievements list.
type RecentAchievementsResponse struct {
	Achievements []RARecentAchievementResponse `json:"achievements"`
}

// GetRecentAchievementsOutput wraps the recent achievements body.
type GetRecentAchievementsOutput struct {
	Body RecentAchievementsResponse
}

// GetUnlockedAchievementsInput is the input for GET /api/user/achievements/unlocked.
type GetUnlockedAchievementsInput struct{}

// UnlockedAchievementsResponse wraps the unlocked achievements list.
type UnlockedAchievementsResponse struct {
	Achievements []RAUnlockedAchievementResponse `json:"achievements"`
}

// GetUnlockedAchievementsOutput wraps the unlocked achievements body.
type GetUnlockedAchievementsOutput struct {
	Body UnlockedAchievementsResponse
}

// RegisterRARoutes wires RetroAchievements endpoints into the huma API.
func RegisterRARoutes(api huma.API, h *RAHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "linkRAAccount",
		Method:      http.MethodPost,
		Path:        "/api/user/ra/link",
		Summary:     "Link a RetroAchievements account",
		Description: "Exchanges username + password for a RetroAchievements token and stores it encrypted for the caller.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaLinkRAAccount)

	huma.Register(api, huma.Operation{
		OperationID: "unlinkRAAccount",
		Method:      http.MethodDelete,
		Path:        "/api/user/ra/link",
		Summary:     "Unlink the RetroAchievements account",
		Description: "Removes the caller's stored RA credentials.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUnlinkRAAccount)

	huma.Register(api, huma.Operation{
		OperationID: "getRAAccountStatus",
		Method:      http.MethodGet,
		Path:        "/api/user/ra/status",
		Summary:     "Get RA account link status",
		Description: "Returns whether the caller has linked a RA account, the linked username, and the hardcore flag.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetRAStatus)

	huma.Register(api, huma.Operation{
		OperationID: "updateRASettings",
		Method:      http.MethodPut,
		Path:        "/api/user/ra/settings",
		Summary:     "Update RA settings",
		Description: "Updates RA-specific settings for the caller (currently only the hardcore flag).",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateRASettings)

	huma.Register(api, huma.Operation{
		OperationID: "getRAToken",
		Method:      http.MethodGet,
		Path:        "/api/user/ra/token",
		Summary:     "Get RA credentials for the native player",
		Description: "Returns the caller's RA username and decrypted token. Response is marked no-store.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetRAToken)

	huma.Register(api, huma.Operation{
		OperationID: "getAchievementProgress",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/achievements/progress",
		Summary:     "Get achievement progress",
		Description: "Returns the caller's achievement progress for a specific game, enriched with playTimeAtUnlock. Returns an empty array if no RA account is linked.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetAchievementProgress)

	huma.Register(api, huma.Operation{
		OperationID: "getAchievementTimeline",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/achievements/timeline",
		Summary:     "Get achievement timeline",
		Description: "Returns the caller's achievement timeline for a specific game, ordered chronologically.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetAchievementTimeline)

	huma.Register(api, huma.Operation{
		OperationID: "getAchievementLeaderboard",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/achievements/leaderboard",
		Summary:     "Get achievement leaderboard",
		Description: "Returns per-user achievement counts and points for a specific game, sorted by unlocked count.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetAchievementLeaderboard)

	huma.Register(api, huma.Operation{
		OperationID: "getRecentAchievements",
		Method:      http.MethodGet,
		Path:        "/api/user/achievements/recent",
		Summary:     "Get recent achievements",
		Description: "Returns the caller's 20 most recently unlocked achievements across all games.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetRecentAchievements)

	huma.Register(api, huma.Operation{
		OperationID: "getUnlockedAchievements",
		Method:      http.MethodGet,
		Path:        "/api/user/achievements/unlocked",
		Summary:     "Get all unlocked achievements",
		Description: "Returns every unlocked achievement across all games, enriched with title, badge and rarity. Used by the showcase picker.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetUnlockedAchievements)
}

// --- Handlers ---

// HumaLinkRAAccount is the huma handler for POST /api/user/ra/link.
func (h *RAHandler) HumaLinkRAAccount(ctx context.Context, in *LinkRAAccountInput) (*LinkRAAccountOutput, error) {
	uid := UserIDFromContext(ctx)
	req := in.Body
	if req.Username == "" || req.Password == "" {
		return nil, huma.Error400BadRequest("username and password are required")
	}

	token, err := h.RAClient.LoginWithPassword(req.Username, req.Password)
	if err != nil {
		// Distinguish "RA said your password is wrong" (401) from
		// "RA is unreachable / returning 5xx / DNS-broken" (502). Pre-#964
		// this collapsed to 401 in all cases, telling users their
		// credentials were wrong during an RA outage — some would change
		// their password in a panic.
		if errors.Is(err, retroachievements.ErrInvalidRACredentials) {
			slog.Warn("RA login rejected credentials", "user_id", uid)
			return nil, huma.Error401Unauthorized("RetroAchievements login failed: invalid credentials")
		}
		slog.Error("RA login upstream failure", "user_id", uid, "error", err)
		return nil, huma.NewError(http.StatusBadGateway, "RetroAchievements is unreachable. Please try again later.")
	}

	encryptedToken, err := auth.Encrypt(token, h.EncryptionKey)
	if err != nil {
		slog.Error("failed to encrypt RA token", "error", err)
		if strings.Contains(err.Error(), "invalid key size") {
			return nil, huma.Error500InternalServerError("encryption key misconfigured: SPELA_ENCRYPTION_KEY must be exactly 16, 24, or 32 bytes")
		}
		return nil, huma.Error500InternalServerError("failed to store RA credentials")
	}

	var cred db.RetroAchievementCredential
	result := h.DB.Where("user_id = ?", uid).First(&cred)
	if result.Error == nil {
		cred.RAUsername = req.Username
		cred.RAToken = encryptedToken
		if err := h.DB.Save(&cred).Error; err != nil {
			return nil, huma.Error500InternalServerError("failed to update RA credentials")
		}
	} else {
		cred = db.RetroAchievementCredential{
			UserID:     uid,
			RAUsername: req.Username,
			RAToken:    encryptedToken,
		}
		if err := h.DB.Create(&cred).Error; err != nil {
			return nil, huma.Error500InternalServerError("failed to store RA credentials")
		}
	}

	return &LinkRAAccountOutput{Body: RAStatusResponse{
		Linked:          true,
		Username:        cred.RAUsername,
		HardcoreEnabled: cred.HardcoreEnabled,
	}}, nil
}

// HumaUnlinkRAAccount is the huma handler for DELETE /api/user/ra/link.
func (h *RAHandler) HumaUnlinkRAAccount(ctx context.Context, _ *UnlinkRAAccountInput) (*UnlinkRAAccountOutput, error) {
	uid := UserIDFromContext(ctx)
	result := h.DB.Where("user_id = ?", uid).Delete(&db.RetroAchievementCredential{})
	if result.RowsAffected == 0 {
		return nil, huma.Error404NotFound("no RA account linked")
	}
	return &UnlinkRAAccountOutput{Body: RAStatusResponse{Linked: false}}, nil
}

// HumaGetRAStatus is the huma handler for GET /api/user/ra/status.
func (h *RAHandler) HumaGetRAStatus(ctx context.Context, _ *GetRAAccountStatusInput) (*GetRAAccountStatusOutput, error) {
	uid := UserIDFromContext(ctx)
	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		return &GetRAAccountStatusOutput{Body: RAStatusResponse{Linked: false}}, nil
	}
	return &GetRAAccountStatusOutput{Body: RAStatusResponse{
		Linked:          true,
		Username:        cred.RAUsername,
		HardcoreEnabled: cred.HardcoreEnabled,
	}}, nil
}

// HumaUpdateRASettings is the huma handler for PUT /api/user/ra/settings.
func (h *RAHandler) HumaUpdateRASettings(ctx context.Context, in *UpdateRASettingsInput) (*UpdateRASettingsOutput, error) {
	uid := UserIDFromContext(ctx)
	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		return nil, huma.Error404NotFound("no RA account linked")
	}
	if in.Body.HardcoreEnabled != nil {
		cred.HardcoreEnabled = *in.Body.HardcoreEnabled
	}
	if err := h.DB.Save(&cred).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update RA settings")
	}
	return &UpdateRASettingsOutput{Body: RAStatusResponse{
		Linked:          true,
		Username:        cred.RAUsername,
		HardcoreEnabled: cred.HardcoreEnabled,
	}}, nil
}

// HumaGetRAToken is the huma handler for GET /api/user/ra/token.
func (h *RAHandler) HumaGetRAToken(ctx context.Context, _ *GetRATokenInput) (*GetRATokenOutput, error) {
	uid := UserIDFromContext(ctx)
	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		return nil, huma.Error404NotFound("no RA account linked")
	}
	token, err := auth.Decrypt(cred.RAToken, h.EncryptionKey)
	if err != nil {
		slog.Error("failed to decrypt RA token", "user_id", uid, "error", err)
		return nil, huma.Error500InternalServerError("failed to retrieve RA token")
	}
	return &GetRATokenOutput{
		CacheControl: "no-store, must-revalidate",
		Pragma:       "no-cache",
		Body: RATokenResponse{
			Username: cred.RAUsername,
			Token:    token,
		},
	}, nil
}

// HumaGetAchievementProgress is the huma handler for GET /api/games/{id}/achievements/progress.
func (h *RAHandler) HumaGetAchievementProgress(ctx context.Context, in *GetAchievementProgressInput) (*GetAchievementProgressOutput, error) {
	uid := UserIDFromContext(ctx)
	gameID := in.ID

	var game db.Game
	if err := h.DB.First(&game, "id = ?", gameID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}
	if err := requirePlayableConsole(h.DB, game.ID); err != nil {
		return nil, huma.Error400BadRequest(errNonPlayableConsole.Error())
	}

	var cred db.RetroAchievementCredential
	if err := h.DB.Where("user_id = ?", uid).First(&cred).Error; err != nil {
		return &GetAchievementProgressOutput{Body: GameAchievementProgressResponse{RAGameID: 0, Progress: []RAProgressEntry{}}}, nil
	}

	raGameID := game.RAGameID
	if raGameID == 0 {
		romPath := filepath.Join(h.GameDir, game.FilePath)
		if !storage.ValidateROMPath(romPath, []string{h.GameDir}) {
			return &GetAchievementProgressOutput{Body: GameAchievementProgressResponse{RAGameID: 0, Progress: []RAProgressEntry{}}}, nil
		}
		hash, err := computeMD5(romPath)
		if err != nil {
			return &GetAchievementProgressOutput{Body: GameAchievementProgressResponse{RAGameID: 0, Progress: []RAProgressEntry{}}}, nil
		}
		var lookupErr error
		raGameID, lookupErr = h.RAClient.GetGameIDFromHash(hash)
		if lookupErr != nil {
			return &GetAchievementProgressOutput{Body: GameAchievementProgressResponse{RAGameID: 0, Progress: []RAProgressEntry{}}}, nil
		}
		h.DB.Model(&db.Game{}).Where("id = ?", game.ID).Update("ra_game_id", raGameID)
	}

	raToken, err := h.decryptRAToken(&cred)
	if err != nil {
		slog.Error("failed to decrypt RA token", "user_id", uid, "error", err)
		return nil, huma.Error500InternalServerError("failed to access RA credentials")
	}

	_, userProgress, err := h.RAClient.GetGameInfoAndUserProgress(cred.RAUsername, raToken, raGameID)
	if err != nil {
		slog.Error("failed to fetch RA progress", "ra_game_id", raGameID, "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch progress from RetroAchievements")
	}

	var playTimeAtUnlock int64
	var playHistory db.PlayHistory
	if err := h.DB.Where("user_id = ? AND game_id = ?", uid, game.ID).First(&playHistory).Error; err == nil {
		playTimeAtUnlock = playHistory.PlayTime
	}

	for _, p := range userProgress {
		unlockedAt, _ := time.Parse("2006-01-02 15:04:05", p.UnlockedAt)
		var existing db.UserAchievementProgress
		result := h.DB.Where("user_id = ? AND achievement_ra_id = ?", uid, p.AchievementID).First(&existing)
		if result.Error == nil {
			existing.UnlockedAt = unlockedAt
			existing.IsHardcore = p.IsHardcore
			h.DB.Save(&existing)
		} else {
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

	return &GetAchievementProgressOutput{Body: GameAchievementProgressResponse{
		RAGameID: raGameID,
		Progress: enrichedProgress,
	}}, nil
}

// HumaGetAchievementTimeline is the huma handler for GET /api/games/{id}/achievements/timeline.
func (h *RAHandler) HumaGetAchievementTimeline(ctx context.Context, in *GetAchievementTimelineInput) (*GetAchievementTimelineOutput, error) {
	uid := UserIDFromContext(ctx)
	gameID := in.ID

	var game db.Game
	if err := h.DB.Preload("Console").First(&game, "id = ?", gameID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}
	if err := requirePlayableConsole(h.DB, game.ID); err != nil {
		return nil, huma.Error400BadRequest(errNonPlayableConsole.Error())
	}

	var cache db.GameAchievementCache
	if err := h.DB.Where("game_id = ?", game.ID).First(&cache).Error; err != nil {
		return &GetAchievementTimelineOutput{Body: AchievementTimelineResponse{
			RAGameID:  0,
			GameTitle: game.Title,
			Timeline:  []RATimelineEntryResponse{},
		}}, nil
	}

	var achievements []retroachievements.Achievement
	if err := json.Unmarshal([]byte(cache.AchievementJSON), &achievements); err != nil {
		slog.Warn("failed to unmarshal cached achievement data", "ra_game_id", cache.RAGameID, "error", err)
	}
	achMap := make(map[uint]retroachievements.Achievement)
	for _, a := range achievements {
		achMap[a.ID] = a
	}

	var progressRows []db.UserAchievementProgress
	h.DB.Where("user_id = ? AND ra_game_id = ?", uid, cache.RAGameID).
		Order("unlocked_at ASC").
		Find(&progressRows)

	var totalPlayTime int64
	var playHistory db.PlayHistory
	if err := h.DB.Where("user_id = ? AND game_id = ?", uid, game.ID).First(&playHistory).Error; err == nil {
		totalPlayTime = playHistory.PlayTime
	}

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

	return &GetAchievementTimelineOutput{Body: AchievementTimelineResponse{
		RAGameID:          cache.RAGameID,
		GameTitle:         game.Title,
		TotalPlayTime:     totalPlayTime,
		Timeline:          timeline,
		TotalAchievements: cache.TotalCount,
		UnlockedCount:     len(progressRows),
		TotalPoints:       cache.TotalPoints,
		EarnedPoints:      earnedPoints,
	}}, nil
}

// HumaGetAchievementLeaderboard is the huma handler for GET /api/games/{id}/achievements/leaderboard.
func (h *RAHandler) HumaGetAchievementLeaderboard(_ context.Context, in *GetAchievementLeaderboardInput) (*GetAchievementLeaderboardOutput, error) {
	gameID := in.ID

	var game db.Game
	if err := h.DB.First(&game, "id = ?", gameID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}
	if err := requirePlayableConsole(h.DB, game.ID); err != nil {
		return nil, huma.Error400BadRequest(errNonPlayableConsole.Error())
	}

	var cache db.GameAchievementCache
	if err := h.DB.Where("game_id = ?", game.ID).First(&cache).Error; err != nil {
		return &GetAchievementLeaderboardOutput{Body: AchievementLeaderboardResponse{
			RAGameID:          0,
			TotalAchievements: 0,
			Leaderboard:       []RALeaderboardEntryResponse{},
		}}, nil
	}

	var achievements []retroachievements.Achievement
	if err := json.Unmarshal([]byte(cache.AchievementJSON), &achievements); err != nil {
		slog.Warn("failed to unmarshal cached achievement data", "ra_game_id", cache.RAGameID, "error", err)
	}
	pointsMap := make(map[uint]int)
	for _, a := range achievements {
		pointsMap[a.ID] = a.Points
	}

	var progressRows []db.UserAchievementProgress
	h.DB.Where("ra_game_id = ?", cache.RAGameID).Find(&progressRows)

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
		return &GetAchievementLeaderboardOutput{Body: AchievementLeaderboardResponse{
			RAGameID:          cache.RAGameID,
			TotalAchievements: cache.TotalCount,
			Leaderboard:       []RALeaderboardEntryResponse{},
		}}, nil
	}

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

	sort.Slice(entries, func(i, j int) bool {
		return entries[i].UnlockedCount > entries[j].UnlockedCount
	})

	return &GetAchievementLeaderboardOutput{Body: AchievementLeaderboardResponse{
		RAGameID:          cache.RAGameID,
		TotalAchievements: cache.TotalCount,
		Leaderboard:       entries,
	}}, nil
}

// HumaGetRecentAchievements is the huma handler for GET /api/user/achievements/recent.
func (h *RAHandler) HumaGetRecentAchievements(ctx context.Context, _ *GetRecentAchievementsInput) (*GetRecentAchievementsOutput, error) {
	uid := UserIDFromContext(ctx)

	var progressRows []db.UserAchievementProgress
	if err := h.DB.Where("user_id = ?", uid).
		Order("unlocked_at DESC").
		Limit(20).
		Find(&progressRows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch recent achievements")
	}

	if len(progressRows) == 0 {
		return &GetRecentAchievementsOutput{Body: RecentAchievementsResponse{
			Achievements: []RARecentAchievementResponse{},
		}}, nil
	}

	raGameIDSet := make(map[uint]bool)
	for _, p := range progressRows {
		raGameIDSet[p.RAGameID] = true
	}
	raGameIDs := make([]uint, 0, len(raGameIDSet))
	for id := range raGameIDSet {
		raGameIDs = append(raGameIDs, id)
	}

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

	results := make([]RARecentAchievementResponse, 0, len(progressRows))
	for _, p := range progressRows {
		entry := RARecentAchievementResponse{
			AchievementRAID:  p.AchievementRAID,
			UnlockedAt:       p.UnlockedAt,
			IsHardcore:       p.IsHardcore,
			PlayTimeAtUnlock: p.PlayTimeAtUnlock,
		}

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

	return &GetRecentAchievementsOutput{Body: RecentAchievementsResponse{Achievements: results}}, nil
}

// HumaGetUnlockedAchievements is the huma handler for GET /api/user/achievements/unlocked.
func (h *RAHandler) HumaGetUnlockedAchievements(ctx context.Context, _ *GetUnlockedAchievementsInput) (*GetUnlockedAchievementsOutput, error) {
	uid := UserIDFromContext(ctx)

	var progressRows []db.UserAchievementProgress
	if err := h.DB.Where("user_id = ?", uid).
		Order("unlocked_at DESC").
		Find(&progressRows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch unlocked achievements")
	}

	if len(progressRows) == 0 {
		return &GetUnlockedAchievementsOutput{Body: UnlockedAchievementsResponse{
			Achievements: []RAUnlockedAchievementResponse{},
		}}, nil
	}

	raGameIDSet := make(map[uint]bool)
	for _, p := range progressRows {
		raGameIDSet[p.RAGameID] = true
	}
	raGameIDs := make([]uint, 0, len(raGameIDSet))
	for id := range raGameIDSet {
		raGameIDs = append(raGameIDs, id)
	}

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

	return &GetUnlockedAchievementsOutput{Body: UnlockedAchievementsResponse{Achievements: results}}, nil
}
