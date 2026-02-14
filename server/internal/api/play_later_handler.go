package api

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// PlayLaterHandler handles Play Later queue endpoints.
type PlayLaterHandler struct {
	DB  *gorm.DB
	Hub *ws.Hub
}

// ListPlayLater returns the user's Play Later queue as GameResponse[], ordered by position.
func (h *PlayLaterHandler) ListPlayLater(c *gin.Context) {
	uid := getUserID(c)

	var items []db.PlayLaterItem
	if err := h.DB.Where("user_id = ?", uid).
		Preload("Game").Preload("Game.Console").
		Order("position ASC").
		Find(&items).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch play later queue"})
		return
	}

	// Collect games, skipping any with deleted/nil game references
	games := make([]db.Game, 0, len(items))
	for _, item := range items {
		if item.Game.ID != 0 {
			games = append(games, item.Game)
		}
	}

	gameResponses := ToGameResponses(games, h.DB, uid)

	c.JSON(http.StatusOK, gameResponses)
}

// AddToPlayLater adds a game to the end of the user's Play Later queue.
func (h *PlayLaterHandler) AddToPlayLater(c *gin.Context) {
	uid := getUserID(c)
	gameIDStr := c.Param("gameId")

	gameID, err := strconv.ParseUint(gameIDStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	// Verify game exists
	var game db.Game
	if err := h.DB.First(&game, gameID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	// Idempotent add: if already in queue, return OK without creating another activity event.
	var existing db.PlayLaterItem
	err = h.DB.Unscoped().Where("user_id = ? AND game_id = ?", uid, gameID).First(&existing).Error
	if err == nil {
		// Record exists (possibly soft-deleted) — restore if needed
		if existing.DeletedAt.Valid {
			existing.DeletedAt = gorm.DeletedAt{}
			h.DB.Unscoped().Save(&existing)
		}
		c.JSON(http.StatusOK, gin.H{"message": "already in play later"})
		return
	}

	// Get max position
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
		// Unique constraint violation from race condition — treat as success
		c.JSON(http.StatusOK, gin.H{"message": "already in play later"})
		return
	}

	CreateActivityEvent(h.DB, h.Hub, uid, "queued_play_later", uint(gameID), nil)

	c.JSON(http.StatusCreated, gin.H{"message": "added to play later"})
}

// RemoveFromPlayLater removes a game from the user's Play Later queue.
func (h *PlayLaterHandler) RemoveFromPlayLater(c *gin.Context) {
	uid := getUserID(c)
	gameIDStr := c.Param("gameId")

	gameID, err := strconv.ParseUint(gameIDStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	result := h.DB.Unscoped().Where("user_id = ? AND game_id = ?", uid, gameID).Delete(&db.PlayLaterItem{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not in play later queue"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "removed from play later"})
}

// ReorderPlayLater reorders the user's Play Later queue.
func (h *PlayLaterHandler) ReorderPlayLater(c *gin.Context) {
	uid := getUserID(c)

	var req struct {
		GameIDs []string `json:"gameIds"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request"})
		return
	}

	if len(req.GameIDs) == 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "gameIds required"})
		return
	}

	// Parse game IDs
	parsedIDs := make([]uint, 0, len(req.GameIDs))
	for _, idStr := range req.GameIDs {
		id, err := strconv.ParseUint(idStr, 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game IDs"})
			return
		}
		parsedIDs = append(parsedIDs, uint(id))
	}

	// Load all items in the user's queue
	var items []db.PlayLaterItem
	h.DB.Where("user_id = ?", uid).Find(&items)

	// Build a lookup for quick validation
	itemByGameID := make(map[uint]*db.PlayLaterItem, len(items))
	for i := range items {
		itemByGameID[items[i].GameID] = &items[i]
	}

	// Validate all provided game IDs are in the queue
	for _, gid := range parsedIDs {
		if _, ok := itemByGameID[gid]; !ok {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game IDs"})
			return
		}
	}

	// Validate completeness: all queue items must be included
	if len(parsedIDs) != len(items) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "gameIds must include all games in queue"})
		return
	}

	// Update positions in a transaction for atomicity
	if err := h.DB.Transaction(func(tx *gorm.DB) error {
		for i, gid := range parsedIDs {
			item := itemByGameID[gid]
			if err := tx.Model(item).Update("position", i).Error; err != nil {
				return err
			}
		}
		return nil
	}); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to reorder queue"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "queue reordered"})
}
