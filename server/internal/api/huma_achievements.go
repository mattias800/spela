package api

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"path/filepath"
	"strconv"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// --- Input / output types ---

// GetShowcaseInput is the input for GET /api/user/achievements/showcase.
type GetShowcaseInput struct{}

// GetShowcaseOutput wraps the showcase response.
type GetShowcaseOutput struct {
	Body []ShowcaseEntryResponse
}

// UpdateShowcaseInput is the input for PUT /api/user/achievements/showcase.
type UpdateShowcaseInput struct {
	Body []ShowcaseEntryInput
}

// UpdateShowcaseOutput wraps the updated showcase response.
type UpdateShowcaseOutput struct {
	Body []ShowcaseEntryResponse
}

// GetPublicShowcaseInput is the input for GET /api/users/{id}/achievements/showcase.
type GetPublicShowcaseInput struct {
	ID string `path:"id" doc:"User ID."`
}

// GetPublicShowcaseOutput wraps the public showcase response.
type GetPublicShowcaseOutput struct {
	Body []ShowcaseEntryResponse
}

// GetGameAchievementsInput is the input for GET /api/games/{id}/achievements.
type GetGameAchievementsInput struct {
	ID string `path:"id" doc:"Game ID."`
}

// GameAchievementsResponse is the wire format for GET /api/games/{id}/achievements.
// The union covers both the resolved-data response (raGameId/achievements set,
// HTTP 200) and the pending-queued-fetch response (status="pending", HTTP 202).
// Fields are omitempty so the empty side of the union doesn't leak.
type GameAchievementsResponse struct {
	RAGameID     uint                           `json:"raGameId" doc:"RetroAchievements game ID (0 when unknown)."`
	Title        string                         `json:"title" doc:"RetroAchievements game title; present when achievement data is available."`
	Achievements []retroachievements.Achievement `json:"achievements" doc:"Achievement definitions for this game (empty when unknown)."`
	TotalCount   int                            `json:"totalCount" doc:"Total number of achievements for this game."`
	TotalPoints  int                            `json:"totalPoints" doc:"Total number of points available across all achievements."`
	Status       string                         `json:"status" doc:"Only set to 'pending' when the data is being fetched asynchronously; clients should retry." enum:"pending"`
}

// GetGameAchievementsOutput wraps the achievements response.
// The Status field lets huma pick between 200 OK (resolved data) and
// 202 Accepted (pending async fetch) per-response.
type GetGameAchievementsOutput struct {
	Status int
	Body   GameAchievementsResponse
}

// RegisterGameAchievementsRoute wires GET /api/games/{id}/achievements into the
// huma API. Separate from the showcase routes because the handler lives on
// RAHandler (which owns the RA client + cache + queue) rather than the
// showcase handler.
func RegisterGameAchievementsRoute(api huma.API, h *RAHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getGameAchievements",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/achievements",
		Summary:     "Get achievements for a game",
		Description: "Returns cached achievement metadata for a game. The server may serve stale cache, a freshly fetched set, or — when the data must be re-fetched asynchronously — HTTP 202 with {\"status\": \"pending\"}; clients should retry shortly afterwards.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetGameAchievements)
}

// HumaGetGameAchievements is the huma handler for GET /api/games/{id}/achievements.
// Mirrors the raw gin handler: cache-first, then user-credential fetch, then
// server-key auto-fetch via the scrape queue (returning 202 pending), finally
// stale cache or empty response.
func (h *RAHandler) HumaGetGameAchievements(ctx context.Context, in *GetGameAchievementsInput) (*GetGameAchievementsOutput, error) {
	uid := UserIDFromContext(ctx)
	gameID := in.ID

	empty := func() *GetGameAchievementsOutput {
		return &GetGameAchievementsOutput{
			Status: http.StatusOK,
			Body: GameAchievementsResponse{
				Achievements: []retroachievements.Achievement{},
			},
		}
	}

	var game db.Game
	if err := h.DB.First(&game, "id = ?", gameID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}
	if err := requirePlayableConsole(h.DB, game.ID); err != nil {
		return nil, huma.Error400BadRequest(errNonPlayableConsole.Error())
	}

	raGameID := game.RAGameID

	// Previously checked + no RA match recorded — short-circuit to empty.
	if game.RAHashChecked && raGameID == 0 {
		return empty(), nil
	}

	if raGameID == 0 {
		romPath := filepath.Join(h.GameDir, game.FilePath)
		if !storage.ValidateROMPath(romPath, []string{h.GameDir}) {
			slog.Warn("RA: ROM path failed validation", "path", romPath, "gameId", gameID)
			return empty(), nil
		}
		hash, err := computeMD5(romPath)
		if err != nil {
			slog.Error("failed to compute ROM hash", "path", romPath, "error", err)
			return empty(), nil
		}

		var lookupErr error
		raGameID, lookupErr = h.RAClient.GetGameIDFromHash(hash)
		if lookupErr != nil {
			slog.Warn("RA game ID lookup failed", "hash", hash, "error", lookupErr)
			return empty(), nil
		}

		h.DB.Model(&db.Game{}).Where("id = ?", game.ID).
			Updates(map[string]interface{}{"ra_game_id": raGameID, "ra_hash_checked": true})
	}

	var cache db.GameAchievementCache
	cacheHit := h.DB.Where("ra_game_id = ?", raGameID).First(&cache).Error == nil
	if cacheHit && time.Since(cache.CachedAt) < 24*time.Hour {
		var achievements []retroachievements.Achievement
		if err := json.Unmarshal([]byte(cache.AchievementJSON), &achievements); err != nil {
			slog.Warn("failed to unmarshal cached achievement data, will re-fetch", "ra_game_id", raGameID, "error", err)
		} else {
			return &GetGameAchievementsOutput{
				Status: http.StatusOK,
				Body: GameAchievementsResponse{
					RAGameID:     raGameID,
					Title:        cache.Title,
					Achievements: achievements,
					TotalCount:   cache.TotalCount,
					TotalPoints:  cache.TotalPoints,
				},
			}, nil
		}
	}

	// Cache missing/stale — try user-credential refresh first.
	var cred db.RetroAchievementCredential
	hasUserCreds := h.DB.Where("user_id = ?", uid).First(&cred).Error == nil

	if hasUserCreds {
		raToken, err := h.decryptRAToken(&cred)
		if err != nil {
			slog.Error("failed to decrypt RA token", "user_id", uid, "error", err)
		} else {
			gameInfo, _, err := h.RAClient.GetGameInfoAndUserProgress(cred.RAUsername, raToken, raGameID)
			if err != nil {
				slog.Error("failed to fetch RA game info", "ra_game_id", raGameID, "error", err)
			} else {
				achJSON, _ := json.Marshal(gameInfo.Achievements)
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
				return &GetGameAchievementsOutput{
					Status: http.StatusOK,
					Body: GameAchievementsResponse{
						RAGameID:     raGameID,
						Title:        gameInfo.Title,
						Achievements: gameInfo.Achievements,
						TotalCount:   gameInfo.TotalCount,
						TotalPoints:  gameInfo.TotalPoints,
					},
				}, nil
			}
		}
	}

	// No user credentials or user fetch failed — fall back to server-key auto-fetch.
	if h.Queue != nil && h.RAAPIKey != "" {
		queued, _ := h.Queue.IsGameQueuedForType(game.ID, "ra_fetch")
		if !queued {
			if err := h.Queue.EnqueueGameWithType(game.ID, nil, 100, "ra_fetch"); err != nil {
				slog.Warn("RA auto-fetch: failed to enqueue", "game", game.Title, "error", err)
			}
		}
		return &GetGameAchievementsOutput{
			Status: http.StatusAccepted,
			Body: GameAchievementsResponse{
				Achievements: []retroachievements.Achievement{},
				Status:       "pending",
			},
		}, nil
	}

	// No server RA key — return stale cache if possible, otherwise empty.
	if cacheHit {
		var achievements []retroachievements.Achievement
		if err := json.Unmarshal([]byte(cache.AchievementJSON), &achievements); err == nil {
			return &GetGameAchievementsOutput{
				Status: http.StatusOK,
				Body: GameAchievementsResponse{
					RAGameID:     raGameID,
					Title:        cache.Title,
					Achievements: achievements,
					TotalCount:   cache.TotalCount,
					TotalPoints:  cache.TotalPoints,
				},
			}, nil
		}
	}
	return empty(), nil
}

// RegisterAchievementShowcaseRoutes wires achievement showcase endpoints into the huma API.
func RegisterAchievementShowcaseRoutes(api huma.API, h *AchievementShowcaseHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getAchievementShowcase",
		Method:      http.MethodGet,
		Path:        "/api/user/achievements/showcase",
		Summary:     "Get achievement showcase",
		Description: "Returns the caller's showcased achievements in display order.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetShowcase)

	huma.Register(api, huma.Operation{
		OperationID: "updateAchievementShowcase",
		Method:      http.MethodPut,
		Path:        "/api/user/achievements/showcase",
		Summary:     "Replace the achievement showcase",
		Description: "Replaces the caller's showcased achievements. Maximum 5 entries.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateShowcase)

	huma.Register(api, huma.Operation{
		OperationID: "getPublicAchievementShowcase",
		Method:      http.MethodGet,
		Path:        "/api/users/{id}/achievements/showcase",
		Summary:     "Get a user's achievement showcase",
		Description: "Returns another user's showcased achievements.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetPublicShowcase)
}

// --- Handlers ---

// HumaGetShowcase is the huma handler for GET /api/user/achievements/showcase.
func (h *AchievementShowcaseHandler) HumaGetShowcase(ctx context.Context, _ *GetShowcaseInput) (*GetShowcaseOutput, error) {
	uid := UserIDFromContext(ctx)

	var entries []db.UserAchievementShowcase
	if err := h.DB.Where("user_id = ?", uid).Order("showcase_order ASC").Find(&entries).Error; err != nil {
		slog.Error("failed to load achievement showcase", "user_id", uid, "error", err)
		return nil, huma.Error500InternalServerError("failed to load showcase")
	}
	return &GetShowcaseOutput{Body: h.enrichShowcaseEntries(entries)}, nil
}

// HumaGetPublicShowcase is the huma handler for GET /api/users/{id}/achievements/showcase.
func (h *AchievementShowcaseHandler) HumaGetPublicShowcase(_ context.Context, in *GetPublicShowcaseInput) (*GetPublicShowcaseOutput, error) {
	userID, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid user ID")
	}

	var entries []db.UserAchievementShowcase
	if err := h.DB.Where("user_id = ?", userID).Order("showcase_order ASC").Find(&entries).Error; err != nil {
		slog.Error("failed to load public achievement showcase", "user_id", userID, "error", err)
		return nil, huma.Error500InternalServerError("failed to load showcase")
	}
	return &GetPublicShowcaseOutput{Body: h.enrichShowcaseEntries(entries)}, nil
}

// HumaUpdateShowcase is the huma handler for PUT /api/user/achievements/showcase.
func (h *AchievementShowcaseHandler) HumaUpdateShowcase(ctx context.Context, in *UpdateShowcaseInput) (*UpdateShowcaseOutput, error) {
	uid := UserIDFromContext(ctx)
	req := in.Body

	if len(req) > maxShowcaseEntries {
		return nil, huma.Error400BadRequest("maximum 5 showcase entries allowed")
	}

	if err := h.DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Where("user_id = ?", uid).Delete(&db.UserAchievementShowcase{}).Error; err != nil {
			return err
		}
		for i, item := range req {
			entry := db.UserAchievementShowcase{
				UserID:          uid,
				AchievementRAID: item.AchievementRAID,
				RAGameID:        item.RAGameID,
				ShowcaseOrder:   i,
			}
			if err := tx.Create(&entry).Error; err != nil {
				return err
			}
		}
		return nil
	}); err != nil {
		slog.Error("failed to update achievement showcase", "user_id", uid, "error", err)
		return nil, huma.Error500InternalServerError("failed to update showcase")
	}

	var entries []db.UserAchievementShowcase
	h.DB.Where("user_id = ?", uid).Order("showcase_order ASC").Find(&entries)
	return &UpdateShowcaseOutput{Body: h.enrichShowcaseEntries(entries)}, nil
}
