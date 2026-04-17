package api

import (
	"context"
	"net/http"
	"strconv"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Input / output types ---

// CreateCollectionInput is the input for POST /api/collections.
type CreateCollectionInput struct {
	Body CreateCollectionRequest
}

// CreateCollectionOutput wraps the created collection response (201 Created).
type CreateCollectionOutput struct {
	Body CollectionResponse
}

// ListCollectionsInput is the input for GET /api/collections and /api/collections/public.
type ListCollectionsInput struct {
	Page     int    `query:"page" doc:"1-based page number (defaults to 1)."`
	PageSize int    `query:"pageSize" doc:"Page size (defaults to 20, clamped to 1-100)."`
	Search   string `query:"search" doc:"Optional substring match on collection name."`
}

// ListCollectionsOutput wraps the paginated collections list.
type ListCollectionsOutput struct {
	Body PaginatedResponse[CollectionResponse]
}

// GetCollectionInput is the input for GET /api/collections/{id}.
type GetCollectionInput struct {
	ID string `path:"id" doc:"Collection ID."`
}

// GetCollectionOutput wraps the collection detail response.
type GetCollectionOutput struct {
	Body CollectionDetailResponse
}

// UpdateCollectionInput is the input for PUT /api/collections/{id}.
type UpdateCollectionInput struct {
	ID   string `path:"id" doc:"Collection ID."`
	Body UpdateCollectionRequest
}

// UpdateCollectionOutput wraps the updated collection response.
type UpdateCollectionOutput struct {
	Body CollectionResponse
}

// DeleteCollectionInput is the input for DELETE /api/collections/{id}.
type DeleteCollectionInput struct {
	ID string `path:"id" doc:"Collection ID."`
}

// DeleteCollectionOutput wraps the delete success message.
type DeleteCollectionOutput struct {
	Body MessageResponse
}

// AddGameToCollectionInput is the input for POST /api/collections/{id}/games.
type AddGameToCollectionInput struct {
	ID   string `path:"id" doc:"Collection ID."`
	Body AddGameToCollectionRequest
}

// AddGameToCollectionOutput wraps the updated collection response (201 Created).
type AddGameToCollectionOutput struct {
	Body CollectionResponse
}

// RemoveGameFromCollectionInput is the input for DELETE /api/collections/{id}/games/{gameId}.
type RemoveGameFromCollectionInput struct {
	ID     string `path:"id" doc:"Collection ID."`
	GameID string `path:"gameId" doc:"Game ID."`
}

// RemoveGameFromCollectionOutput wraps the remove-game success message.
type RemoveGameFromCollectionOutput struct {
	Body MessageResponse
}

// RegisterCollectionRoutes wires the game collection endpoints into the huma API.
func RegisterCollectionRoutes(api huma.API, h *CollectionHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID:   "createCollection",
		Method:        http.MethodPost,
		Path:          "/api/collections",
		Summary:       "Create a collection",
		Description:   "Creates a new game collection owned by the caller.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"collections"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaCreateCollection)

	huma.Register(api, huma.Operation{
		OperationID: "listMyCollections",
		Method:      http.MethodGet,
		Path:        "/api/collections",
		Summary:     "List the caller's collections",
		Description: "Paginated list of the caller's collections, newest first. Supports optional name substring search.",
		Tags:        []string{"collections"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListMyCollections)

	huma.Register(api, huma.Operation{
		OperationID: "listPublicCollections",
		Method:      http.MethodGet,
		Path:        "/api/collections/public",
		Summary:     "List all public collections",
		Description: "Paginated list of public collections across all users, newest first.",
		Tags:        []string{"collections"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListPublicCollections)

	huma.Register(api, huma.Operation{
		OperationID: "getCollection",
		Method:      http.MethodGet,
		Path:        "/api/collections/{id}",
		Summary:     "Get a collection with its games",
		Description: "Returns the collection detail and the full ordered game list. Only the owner can see private collections.",
		Tags:        []string{"collections"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetCollection)

	huma.Register(api, huma.Operation{
		OperationID: "updateCollection",
		Method:      http.MethodPut,
		Path:        "/api/collections/{id}",
		Summary:     "Update a collection",
		Description: "Owner-only. Partial update of name, description or visibility.",
		Tags:        []string{"collections"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateCollection)

	huma.Register(api, huma.Operation{
		OperationID: "deleteCollection",
		Method:      http.MethodDelete,
		Path:        "/api/collections/{id}",
		Summary:     "Delete a collection",
		Description: "Owner-only. Removes the collection and its items.",
		Tags:        []string{"collections"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDeleteCollection)

	huma.Register(api, huma.Operation{
		OperationID:   "addGameToCollection",
		Method:        http.MethodPost,
		Path:          "/api/collections/{id}/games",
		Summary:       "Add a game to a collection",
		Description:   "Owner-only. Appends the game to the end of the collection (unique per collection).",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"collections"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaAddGameToCollection)

	huma.Register(api, huma.Operation{
		OperationID: "removeGameFromCollection",
		Method:      http.MethodDelete,
		Path:        "/api/collections/{id}/games/{gameId}",
		Summary:     "Remove a game from a collection",
		Description: "Owner-only. Removes the specified game from the collection.",
		Tags:        []string{"collections"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaRemoveGameFromCollection)
}

// --- Handlers ---

// HumaCreateCollection is the huma handler for POST /api/collections.
func (h *CollectionHandler) HumaCreateCollection(ctx context.Context, in *CreateCollectionInput) (*CreateCollectionOutput, error) {
	uid := UserIDFromContext(ctx)

	req := in.Body
	if req.Name == "" {
		return nil, huma.Error400BadRequest("invalid request: name is required")
	}
	if len(req.Name) > 255 {
		return nil, huma.Error400BadRequest("name must be 255 characters or fewer")
	}
	if len(req.Description) > 2048 {
		return nil, huma.Error400BadRequest("description must be 2048 characters or fewer")
	}

	collection := db.GameCollection{
		UserID:      uid,
		Name:        req.Name,
		Description: req.Description,
		IsPublic:    req.IsPublic,
	}
	if err := h.DB.Create(&collection).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to create collection")
	}

	CreateActivityEvent(h.DB, h.Hub, uid, "created_collection", 0, CreatedCollectionMetadata{
		CollectionName: req.Name,
	})

	return &CreateCollectionOutput{Body: h.toCollectionResponse(collection, uid)}, nil
}

// HumaListMyCollections is the huma handler for GET /api/collections.
func (h *CollectionHandler) HumaListMyCollections(ctx context.Context, in *ListCollectionsInput) (*ListCollectionsOutput, error) {
	uid := UserIDFromContext(ctx)

	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	query := h.DB.Model(&db.GameCollection{}).Where("user_id = ?", uid)
	if in.Search != "" {
		query = query.Where("name LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(in.Search)+"%")
	}

	var total int64
	query.Count(&total)

	var collections []db.GameCollection
	if err := h.DB.Where("user_id = ?", uid).
		Scopes(func(d *gorm.DB) *gorm.DB {
			if in.Search != "" {
				return d.Where("name LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(in.Search)+"%")
			}
			return d
		}).
		Preload("User").
		Preload("Items").
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&collections).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch collections")
	}

	result := make([]CollectionResponse, 0, len(collections))
	for _, col := range collections {
		result = append(result, h.toCollectionResponse(col, uid))
	}

	return &ListCollectionsOutput{Body: PaginatedResponse[CollectionResponse]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}}, nil
}

// HumaListPublicCollections is the huma handler for GET /api/collections/public.
func (h *CollectionHandler) HumaListPublicCollections(ctx context.Context, in *ListCollectionsInput) (*ListCollectionsOutput, error) {
	uid := UserIDFromContext(ctx)

	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	query := h.DB.Model(&db.GameCollection{}).Where("is_public = ?", true)
	if in.Search != "" {
		query = query.Where("name LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(in.Search)+"%")
	}

	var total int64
	query.Count(&total)

	var collections []db.GameCollection
	if err := h.DB.Where("is_public = ?", true).
		Scopes(func(d *gorm.DB) *gorm.DB {
			if in.Search != "" {
				return d.Where("name LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(in.Search)+"%")
			}
			return d
		}).
		Preload("User").
		Preload("Items").
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&collections).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch public collections")
	}

	result := make([]CollectionResponse, 0, len(collections))
	for _, col := range collections {
		result = append(result, h.toCollectionResponse(col, uid))
	}

	return &ListCollectionsOutput{Body: PaginatedResponse[CollectionResponse]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}}, nil
}

// HumaGetCollection is the huma handler for GET /api/collections/{id}.
func (h *CollectionHandler) HumaGetCollection(ctx context.Context, in *GetCollectionInput) (*GetCollectionOutput, error) {
	uid := UserIDFromContext(ctx)

	cid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid collection ID")
	}

	var collection db.GameCollection
	if err := h.DB.First(&collection, cid).Error; err != nil {
		return nil, huma.Error404NotFound("collection not found")
	}
	if collection.UserID != uid && !collection.IsPublic {
		return nil, huma.Error404NotFound("collection not found")
	}

	h.DB.Preload("User").Preload("Items", func(d *gorm.DB) *gorm.DB {
		return d.Order("position ASC")
	}).Preload("Items.Game").Preload("Items.Game.Console").First(&collection, cid)

	games := make([]db.Game, 0, len(collection.Items))
	for _, item := range collection.Items {
		games = append(games, item.Game)
	}

	gameResponses := ToGameResponses(games, h.DB, uid)

	return &GetCollectionOutput{Body: CollectionDetailResponse{
		CollectionResponse: h.toCollectionResponse(collection, uid),
		Games:              gameResponses,
	}}, nil
}

// HumaUpdateCollection is the huma handler for PUT /api/collections/{id}.
func (h *CollectionHandler) HumaUpdateCollection(ctx context.Context, in *UpdateCollectionInput) (*UpdateCollectionOutput, error) {
	uid := UserIDFromContext(ctx)

	cid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid collection ID")
	}

	var collection db.GameCollection
	if err := h.DB.Preload("User").Preload("Items").First(&collection, cid).Error; err != nil {
		return nil, huma.Error404NotFound("collection not found")
	}

	if collection.UserID != uid {
		return nil, huma.Error403Forbidden("not authorized")
	}

	req := in.Body
	if req.Name != nil && len(*req.Name) > 255 {
		return nil, huma.Error400BadRequest("name must be 255 characters or fewer")
	}
	if req.Description != nil && len(*req.Description) > 2048 {
		return nil, huma.Error400BadRequest("description must be 2048 characters or fewer")
	}

	if req.Name != nil {
		collection.Name = *req.Name
	}
	if req.Description != nil {
		collection.Description = *req.Description
	}
	if req.IsPublic != nil {
		collection.IsPublic = *req.IsPublic
	}

	if err := h.DB.Save(&collection).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update collection")
	}

	return &UpdateCollectionOutput{Body: h.toCollectionResponse(collection, uid)}, nil
}

// HumaDeleteCollection is the huma handler for DELETE /api/collections/{id}.
func (h *CollectionHandler) HumaDeleteCollection(ctx context.Context, in *DeleteCollectionInput) (*DeleteCollectionOutput, error) {
	uid := UserIDFromContext(ctx)

	cid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid collection ID")
	}

	var collection db.GameCollection
	if err := h.DB.First(&collection, cid).Error; err != nil {
		return nil, huma.Error404NotFound("collection not found")
	}

	if collection.UserID != uid {
		return nil, huma.Error403Forbidden("not authorized")
	}

	h.DB.Where("collection_id = ?", cid).Delete(&db.CollectionItem{})
	h.DB.Delete(&collection)

	return &DeleteCollectionOutput{Body: MessageResponse{Message: "collection deleted"}}, nil
}

// HumaAddGameToCollection is the huma handler for POST /api/collections/{id}/games.
func (h *CollectionHandler) HumaAddGameToCollection(ctx context.Context, in *AddGameToCollectionInput) (*AddGameToCollectionOutput, error) {
	uid := UserIDFromContext(ctx)

	cid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid collection ID")
	}

	var collection db.GameCollection
	if err := h.DB.First(&collection, cid).Error; err != nil {
		return nil, huma.Error404NotFound("collection not found")
	}

	if collection.UserID != uid {
		return nil, huma.Error403Forbidden("not authorized")
	}

	req := in.Body
	if req.GameID == 0 {
		return nil, huma.Error400BadRequest("invalid request: gameId is required")
	}

	var game db.Game
	if err := h.DB.First(&game, req.GameID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	var existing db.CollectionItem
	if err := h.DB.Where("collection_id = ? AND game_id = ?", cid, req.GameID).First(&existing).Error; err == nil {
		return nil, huma.Error409Conflict("game already in collection")
	}

	var maxPos struct{ MaxPos int }
	h.DB.Model(&db.CollectionItem{}).
		Where("collection_id = ?", cid).
		Select("COALESCE(MAX(position), -1) as max_pos").
		Scan(&maxPos)

	item := db.CollectionItem{
		CollectionID: uint(cid),
		GameID:       req.GameID,
		Position:     maxPos.MaxPos + 1,
	}
	if err := h.DB.Create(&item).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to add game to collection")
	}

	h.DB.Preload("User").Preload("Items").First(&collection, cid)

	return &AddGameToCollectionOutput{Body: h.toCollectionResponse(collection, uid)}, nil
}

// HumaRemoveGameFromCollection is the huma handler for DELETE /api/collections/{id}/games/{gameId}.
func (h *CollectionHandler) HumaRemoveGameFromCollection(ctx context.Context, in *RemoveGameFromCollectionInput) (*RemoveGameFromCollectionOutput, error) {
	uid := UserIDFromContext(ctx)

	cid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid collection ID")
	}

	gameID, err := strconv.ParseUint(in.GameID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var collection db.GameCollection
	if err := h.DB.First(&collection, cid).Error; err != nil {
		return nil, huma.Error404NotFound("collection not found")
	}

	if collection.UserID != uid {
		return nil, huma.Error403Forbidden("not authorized")
	}

	result := h.DB.Where("collection_id = ? AND game_id = ?", cid, gameID).Delete(&db.CollectionItem{})
	if result.RowsAffected == 0 {
		return nil, huma.Error404NotFound("game not in collection")
	}

	return &RemoveGameFromCollectionOutput{Body: MessageResponse{Message: "game removed from collection"}}, nil
}
