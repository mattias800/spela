package api

import (
	"context"
	"net/http"
	"strconv"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Favorites ---

// ListFavoritesInput is the input for GET /api/user/favorites.
type ListFavoritesInput struct{}

// ListFavoritesOutput wraps the flat game list for favorites.
type ListFavoritesOutput struct {
	Body []GameResponse
}

// AddFavoriteInput is the input for POST /api/user/favorites/{gameId}.
type AddFavoriteInput struct {
	GameID string `path:"gameId" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
}

// AddFavoriteOutput wraps the create-favorite success message (201 Created).
type AddFavoriteOutput struct {
	Body MessageResponse
}

// RemoveFavoriteInput is the input for DELETE /api/user/favorites/{gameId}.
type RemoveFavoriteInput struct {
	GameID string `path:"gameId" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
}

// RemoveFavoriteOutput wraps the delete-favorite success message.
type RemoveFavoriteOutput struct {
	Body MessageResponse
}

// --- Play Later ---

// ListPlayLaterInput is the input for GET /api/user/play-later.
type ListPlayLaterInput struct{}

// ListPlayLaterOutput wraps the flat game list for the Play Later queue.
type ListPlayLaterOutput struct {
	Body []GameResponse
}

// AddToPlayLaterInput is the input for POST /api/user/play-later/{gameId}.
type AddToPlayLaterInput struct {
	GameID string `path:"gameId" doc:"Game ID."`
}

// AddToPlayLaterOutput uses a dynamic status code — 201 when we added a new
// entry, 200 when the game was already in the queue (idempotent add), matching
// the raw gin handler.
type AddToPlayLaterOutput struct {
	Status int
	Body   MessageResponse
}

// RemoveFromPlayLaterInput is the input for DELETE /api/user/play-later/{gameId}.
type RemoveFromPlayLaterInput struct {
	GameID string `path:"gameId" doc:"Game ID."`
}

// RemoveFromPlayLaterOutput wraps the delete-from-play-later success message.
type RemoveFromPlayLaterOutput struct {
	Body MessageResponse
}

// ReorderPlayLaterInput is the input for PUT /api/user/play-later/reorder.
type ReorderPlayLaterInput struct {
	Body ReorderPlayLaterRequest
}

// ReorderPlayLaterOutput wraps the reorder success message.
type ReorderPlayLaterOutput struct {
	Body MessageResponse
}

// RegisterFavoriteRoutes wires the favorites endpoints into the huma API.
func RegisterFavoriteRoutes(api huma.API, h *UserHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "listFavorites",
		Method:      http.MethodGet,
		Path:        "/api/user/favorites",
		Summary:     "List the caller's favorite games",
		Description: "Returns a flat list of the authenticated user's favorited games with enrichment data (play history, ratings, artwork).",
		Tags:        []string{"favorites"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListFavorites)

	huma.Register(api, huma.Operation{
		OperationID:   "addFavorite",
		Method:        http.MethodPost,
		Path:          "/api/user/favorites/{gameId}",
		Summary:       "Add a game to the caller's favorites",
		Description:   "Marks the specified game as favorited for the authenticated user. Emits a favorited_game activity event on success.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"favorites"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaAddFavorite)

	huma.Register(api, huma.Operation{
		OperationID: "removeFavorite",
		Method:      http.MethodDelete,
		Path:        "/api/user/favorites/{gameId}",
		Summary:     "Remove a game from the caller's favorites",
		Description: "Unfavorites the specified game for the authenticated user.",
		Tags:        []string{"favorites"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaRemoveFavorite)
}

// RegisterPlayLaterRoutes wires the Play Later queue endpoints into the huma API.
func RegisterPlayLaterRoutes(api huma.API, h *PlayLaterHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "listPlayLater",
		Method:      http.MethodGet,
		Path:        "/api/user/play-later",
		Summary:     "List the caller's Play Later queue",
		Description: "Returns the authenticated user's Play Later queue as a flat game list, ordered by the user-defined position.",
		Tags:        []string{"play-later"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListPlayLater)

	huma.Register(api, huma.Operation{
		OperationID: "addToPlayLater",
		Method:      http.MethodPost,
		Path:        "/api/user/play-later/{gameId}",
		Summary:     "Append a game to the caller's Play Later queue",
		Description: "Idempotent: adds the game at the end of the queue (position = max+1). Returns 201 for new entries, 200 when the game was already present.",
		Tags:        []string{"play-later"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaAddToPlayLater)

	huma.Register(api, huma.Operation{
		OperationID: "removeFromPlayLater",
		Method:      http.MethodDelete,
		Path:        "/api/user/play-later/{gameId}",
		Summary:     "Remove a game from the caller's Play Later queue",
		Description: "Removes the specified game from the authenticated user's Play Later queue.",
		Tags:        []string{"play-later"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaRemoveFromPlayLater)

	huma.Register(api, huma.Operation{
		OperationID: "reorderPlayLater",
		Method:      http.MethodPut,
		Path:        "/api/user/play-later/reorder",
		Summary:     "Reorder the caller's Play Later queue",
		Description: "Replaces the user-defined order of the caller's Play Later queue. The supplied gameIds list must include every game currently in the queue.",
		Tags:        []string{"play-later"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaReorderPlayLater)
}

// --- Favorite handlers ---

// HumaListFavorites is the huma handler for GET /api/user/favorites.
func (h *UserHandler) HumaListFavorites(ctx context.Context, _ *ListFavoritesInput) (*ListFavoritesOutput, error) {
	uid := UserIDFromContext(ctx)

	var favorites []db.Favorite
	if err := h.DB.Where("user_id = ?", uid).
		Preload("Game").Preload("Game.Console").
		Find(&favorites).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch favorites")
	}

	gameIDs := make([]uint, 0, len(favorites))
	for _, fav := range favorites {
		if fav.Game.ID != 0 {
			gameIDs = append(gameIDs, fav.Game.ID)
		}
	}
	data := loadUserGameData(h.DB, uid, gameIDs)

	games := make([]GameResponse, 0, len(favorites))
	for _, fav := range favorites {
		if fav.Game.ID == 0 {
			continue
		}
		resp := toGameResponseWithData(fav.Game, &data)
		resp.IsFavorite = true
		games = append(games, resp)
	}

	return &ListFavoritesOutput{Body: games}, nil
}

// HumaAddFavorite is the huma handler for POST /api/user/favorites/{gameId}.
func (h *UserHandler) HumaAddFavorite(ctx context.Context, in *AddFavoriteInput) (*AddFavoriteOutput, error) {
	uid := UserIDFromContext(ctx)

	parsedID, err := strconv.ParseUint(in.GameID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var game db.Game
	if err := h.DB.First(&game, uint(parsedID)).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	fav := db.Favorite{
		UserID: uid,
		GameID: game.ID,
	}

	if err := h.DB.Create(&fav).Error; err != nil {
		return nil, huma.Error409Conflict("already favorited")
	}

	CreateActivityEvent(h.DB, h.Hub, uid, "favorited_game", game.ID, nil)

	return &AddFavoriteOutput{Body: MessageResponse{Message: "favorite added"}}, nil
}

// HumaRemoveFavorite is the huma handler for DELETE /api/user/favorites/{gameId}.
func (h *UserHandler) HumaRemoveFavorite(ctx context.Context, in *RemoveFavoriteInput) (*RemoveFavoriteOutput, error) {
	uid := UserIDFromContext(ctx)

	result := h.DB.Where("user_id = ? AND game_id = ?", uid, in.GameID).Delete(&db.Favorite{})
	if result.RowsAffected == 0 {
		return nil, huma.Error404NotFound("favorite not found")
	}

	return &RemoveFavoriteOutput{Body: MessageResponse{Message: "favorite removed"}}, nil
}

// --- Play Later handlers ---

// HumaListPlayLater is the huma handler for GET /api/user/play-later.
func (h *PlayLaterHandler) HumaListPlayLater(ctx context.Context, _ *ListPlayLaterInput) (*ListPlayLaterOutput, error) {
	uid := UserIDFromContext(ctx)

	var items []db.PlayLaterItem
	if err := h.DB.Where("user_id = ?", uid).
		Preload("Game").Preload("Game.Console").
		Order("position ASC").
		Find(&items).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch play later queue")
	}

	games := make([]db.Game, 0, len(items))
	for _, item := range items {
		if item.Game.ID != 0 {
			games = append(games, item.Game)
		}
	}

	return &ListPlayLaterOutput{Body: ToGameResponses(games, h.DB, uid)}, nil
}

// HumaAddToPlayLater is the huma handler for POST /api/user/play-later/{gameId}.
func (h *PlayLaterHandler) HumaAddToPlayLater(ctx context.Context, in *AddToPlayLaterInput) (*AddToPlayLaterOutput, error) {
	uid := UserIDFromContext(ctx)

	gameID, err := strconv.ParseUint(in.GameID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var game db.Game
	if err := h.DB.First(&game, gameID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	var existing db.PlayLaterItem
	err = h.DB.Unscoped().Where("user_id = ? AND game_id = ?", uid, gameID).First(&existing).Error
	if err == nil {
		if existing.DeletedAt.Valid {
			existing.DeletedAt = gorm.DeletedAt{}
			h.DB.Unscoped().Save(&existing)
		}
		return &AddToPlayLaterOutput{
			Status: http.StatusOK,
			Body:   MessageResponse{Message: "already in play later"},
		}, nil
	}

	var maxPos struct{ MaxPos int }
	h.DB.Model(&db.PlayLaterItem{}).
		Where("user_id = ?", uid).
		Select("COALESCE(MAX(position), -1) as max_pos").
		Scan(&maxPos)

	item := db.PlayLaterItem{
		UserID:   uid,
		GameID:   uint(gameID),
		Position: maxPos.MaxPos + 1,
	}
	if err := h.DB.Create(&item).Error; err != nil {
		// Unique constraint violation from race condition — treat as success.
		return &AddToPlayLaterOutput{
			Status: http.StatusOK,
			Body:   MessageResponse{Message: "already in play later"},
		}, nil
	}

	CreateActivityEvent(h.DB, h.Hub, uid, "queued_play_later", uint(gameID), nil)

	return &AddToPlayLaterOutput{
		Status: http.StatusCreated,
		Body:   MessageResponse{Message: "added to play later"},
	}, nil
}

// HumaRemoveFromPlayLater is the huma handler for DELETE /api/user/play-later/{gameId}.
func (h *PlayLaterHandler) HumaRemoveFromPlayLater(ctx context.Context, in *RemoveFromPlayLaterInput) (*RemoveFromPlayLaterOutput, error) {
	uid := UserIDFromContext(ctx)

	gameID, err := strconv.ParseUint(in.GameID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	result := h.DB.Unscoped().Where("user_id = ? AND game_id = ?", uid, gameID).Delete(&db.PlayLaterItem{})
	if result.RowsAffected == 0 {
		return nil, huma.Error404NotFound("game not in play later queue")
	}

	return &RemoveFromPlayLaterOutput{Body: MessageResponse{Message: "removed from play later"}}, nil
}

// HumaReorderPlayLater is the huma handler for PUT /api/user/play-later/reorder.
func (h *PlayLaterHandler) HumaReorderPlayLater(ctx context.Context, in *ReorderPlayLaterInput) (*ReorderPlayLaterOutput, error) {
	uid := UserIDFromContext(ctx)

	if len(in.Body.GameIDs) == 0 {
		return nil, huma.Error400BadRequest("gameIds required")
	}

	parsedIDs := make([]uint, 0, len(in.Body.GameIDs))
	for _, idStr := range in.Body.GameIDs {
		id, err := strconv.ParseUint(idStr, 10, 64)
		if err != nil {
			return nil, huma.Error400BadRequest("invalid game IDs")
		}
		parsedIDs = append(parsedIDs, uint(id))
	}

	var items []db.PlayLaterItem
	h.DB.Where("user_id = ?", uid).Find(&items)

	itemByGameID := make(map[uint]*db.PlayLaterItem, len(items))
	for i := range items {
		itemByGameID[items[i].GameID] = &items[i]
	}

	for _, gid := range parsedIDs {
		if _, ok := itemByGameID[gid]; !ok {
			return nil, huma.Error400BadRequest("invalid game IDs")
		}
	}

	if len(parsedIDs) != len(items) {
		return nil, huma.Error400BadRequest("gameIds must include all games in queue")
	}

	if err := h.DB.Transaction(func(tx *gorm.DB) error {
		for i, gid := range parsedIDs {
			item := itemByGameID[gid]
			if err := tx.Model(item).Update("position", i).Error; err != nil {
				return err
			}
		}
		return nil
	}); err != nil {
		return nil, huma.Error500InternalServerError("failed to reorder queue")
	}

	return &ReorderPlayLaterOutput{Body: MessageResponse{Message: "queue reordered"}}, nil
}
