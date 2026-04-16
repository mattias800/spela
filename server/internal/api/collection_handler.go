package api

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// CollectionHandler handles game collection endpoints.
type CollectionHandler struct {
	DB  *gorm.DB
	Hub *ws.Hub
}

// CreateCollection creates a new game collection for the authenticated user.
func (h *CollectionHandler) CreateCollection(c *gin.Context) {
	uid := getUserID(c)

	var req struct {
		Name        string `json:"name" binding:"required,max=255"`
		Description string `json:"description" binding:"max=2048"`
		IsPublic    bool   `json:"isPublic"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: name is required"})
		return
	}

	collection := db.GameCollection{
		UserID:      uid,
		Name:        req.Name,
		Description: req.Description,
		IsPublic:    req.IsPublic,
	}
	if err := h.DB.Create(&collection).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create collection"})
		return
	}

	CreateActivityEvent(h.DB, h.Hub, uid, "created_collection", 0, map[string]interface{}{
		"collectionName": req.Name,
	})

	c.JSON(http.StatusCreated, h.toCollectionResponse(collection, uid))
}

// ListMyCollections returns the authenticated user's collections (paginated).
func (h *CollectionHandler) ListMyCollections(c *gin.Context) {
	uid := getUserID(c)

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	search := c.Query("search")

	query := h.DB.Model(&db.GameCollection{}).Where("user_id = ?", uid)
	if search != "" {
		query = query.Where("name LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(search)+"%")
	}

	var total int64
	query.Count(&total)

	var collections []db.GameCollection
	if err := h.DB.Where("user_id = ?", uid).
		Scopes(func(d *gorm.DB) *gorm.DB {
			if search != "" {
				return d.Where("name LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(search)+"%")
			}
			return d
		}).
		Preload("User").
		Preload("Items").
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&collections).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch collections"})
		return
	}

	result := make([]CollectionResponse, 0, len(collections))
	for _, col := range collections {
		result = append(result, h.toCollectionResponse(col, uid))
	}

	c.JSON(http.StatusOK, PaginatedResponse[CollectionResponse]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// ListPublicCollections returns all public collections (paginated).
func (h *CollectionHandler) ListPublicCollections(c *gin.Context) {
	uid := getUserID(c)

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	search := c.Query("search")

	query := h.DB.Model(&db.GameCollection{}).Where("is_public = ?", true)
	if search != "" {
		query = query.Where("name LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(search)+"%")
	}

	var total int64
	query.Count(&total)

	var collections []db.GameCollection
	if err := h.DB.Where("is_public = ?", true).
		Scopes(func(d *gorm.DB) *gorm.DB {
			if search != "" {
				return d.Where("name LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(search)+"%")
			}
			return d
		}).
		Preload("User").
		Preload("Items").
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&collections).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch public collections"})
		return
	}

	result := make([]CollectionResponse, 0, len(collections))
	for _, col := range collections {
		result = append(result, h.toCollectionResponse(col, uid))
	}

	c.JSON(http.StatusOK, PaginatedResponse[CollectionResponse]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// GetCollection returns a single collection with its games.
func (h *CollectionHandler) GetCollection(c *gin.Context) {
	uid := getUserID(c)
	id := c.Param("id")

	cid, err := strconv.ParseUint(id, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid collection ID"})
		return
	}

	// Check ownership/visibility before loading expensive relationships
	var collection db.GameCollection
	if err := h.DB.First(&collection, cid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "collection not found"})
		return
	}
	if collection.UserID != uid && !collection.IsPublic {
		c.JSON(http.StatusNotFound, gin.H{"error": "collection not found"})
		return
	}

	// Authorized — load full data
	h.DB.Preload("User").Preload("Items", func(db *gorm.DB) *gorm.DB {
		return db.Order("position ASC")
	}).Preload("Items.Game").Preload("Items.Game.Console").First(&collection, cid)

	games := make([]db.Game, 0, len(collection.Items))
	for _, item := range collection.Items {
		games = append(games, item.Game)
	}

	gameResponses := ToGameResponses(games, h.DB, uid)

	resp := CollectionDetailResponse{
		CollectionResponse: h.toCollectionResponse(collection, uid),
		Games:              gameResponses,
	}

	c.JSON(http.StatusOK, resp)
}

// UpdateCollection updates a collection's name, description, or visibility.
func (h *CollectionHandler) UpdateCollection(c *gin.Context) {
	uid := getUserID(c)
	id := c.Param("id")

	cid, err := strconv.ParseUint(id, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid collection ID"})
		return
	}

	var collection db.GameCollection
	if err := h.DB.Preload("User").Preload("Items").First(&collection, cid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "collection not found"})
		return
	}

	if collection.UserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "not authorized"})
		return
	}

	var req struct {
		Name        *string `json:"name"`
		Description *string `json:"description"`
		IsPublic    *bool   `json:"isPublic"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request"})
		return
	}

	if req.Name != nil && len(*req.Name) > 255 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "name must be 255 characters or fewer"})
		return
	}
	if req.Description != nil && len(*req.Description) > 2048 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "description must be 2048 characters or fewer"})
		return
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update collection"})
		return
	}

	c.JSON(http.StatusOK, h.toCollectionResponse(collection, uid))
}

// DeleteCollection deletes a collection and its items.
func (h *CollectionHandler) DeleteCollection(c *gin.Context) {
	uid := getUserID(c)
	id := c.Param("id")

	cid, err := strconv.ParseUint(id, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid collection ID"})
		return
	}

	var collection db.GameCollection
	if err := h.DB.First(&collection, cid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "collection not found"})
		return
	}

	if collection.UserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "not authorized"})
		return
	}

	// Delete items first, then the collection
	h.DB.Where("collection_id = ?", cid).Delete(&db.CollectionItem{})
	h.DB.Delete(&collection)

	c.JSON(http.StatusOK, gin.H{"message": "collection deleted"})
}

// AddGame adds a game to a collection.
func (h *CollectionHandler) AddGame(c *gin.Context) {
	uid := getUserID(c)
	id := c.Param("id")

	cid, err := strconv.ParseUint(id, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid collection ID"})
		return
	}

	var collection db.GameCollection
	if err := h.DB.First(&collection, cid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "collection not found"})
		return
	}

	if collection.UserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "not authorized"})
		return
	}

	var req struct {
		GameID uint `json:"gameId" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: gameId is required"})
		return
	}

	// Verify game exists
	var game db.Game
	if err := h.DB.First(&game, req.GameID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	// Check for duplicate
	var existing db.CollectionItem
	if err := h.DB.Where("collection_id = ? AND game_id = ?", cid, req.GameID).First(&existing).Error; err == nil {
		c.JSON(http.StatusConflict, gin.H{"error": "game already in collection"})
		return
	}

	// Get max position
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to add game to collection"})
		return
	}

	// Reload collection with items for response
	h.DB.Preload("User").Preload("Items").First(&collection, cid)

	c.JSON(http.StatusCreated, h.toCollectionResponse(collection, uid))
}

// RemoveGame removes a game from a collection.
func (h *CollectionHandler) RemoveGame(c *gin.Context) {
	uid := getUserID(c)
	id := c.Param("id")
	gameIDStr := c.Param("gameId")

	cid, err := strconv.ParseUint(id, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid collection ID"})
		return
	}

	gameID, err := strconv.ParseUint(gameIDStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	var collection db.GameCollection
	if err := h.DB.First(&collection, cid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "collection not found"})
		return
	}

	if collection.UserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "not authorized"})
		return
	}

	result := h.DB.Where("collection_id = ? AND game_id = ?", cid, gameID).Delete(&db.CollectionItem{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not in collection"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "game removed from collection"})
}

// toCollectionResponse converts a db.GameCollection to its API response.
func (h *CollectionHandler) toCollectionResponse(col db.GameCollection, _ uint) CollectionResponse {
	username := col.User.Username
	avatarURL := col.User.AvatarURL

	if username == "" && col.UserID != 0 {
		var user db.User
		if err := h.DB.First(&user, col.UserID).Error; err == nil {
			username = user.Username
			avatarURL = user.AvatarURL
		}
	}

	// Derive cover URL from the first game in the collection
	coverURL := ""
	if len(col.Items) > 0 {
		var firstGame db.Game
		if err := h.DB.First(&firstGame, col.Items[0].GameID).Error; err == nil {
			coverURL = firstGame.CoverURL
			if coverURL != "" && len(coverURL) > 0 && coverURL[0] != 'h' {
				coverURL = "/api/images/" + coverURL
			}
		}
	}

	return CollectionResponse{
		ID:          strconv.FormatUint(uint64(col.ID), 10),
		UserID:      strconv.FormatUint(uint64(col.UserID), 10),
		Username:    username,
		AvatarURL:   avatarURL,
		Name:        col.Name,
		Description: col.Description,
		IsPublic:    col.IsPublic,
		CoverURL:    coverURL,
		GameCount:   len(col.Items),
		CreatedAt:   col.CreatedAt,
		UpdatedAt:   col.UpdatedAt,
	}
}
