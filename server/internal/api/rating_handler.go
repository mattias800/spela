package api

import (
	"fmt"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// RatingHandler handles game rating endpoints.
type RatingHandler struct {
	DB  *gorm.DB
	Hub *ws.Hub
}

// CreateOrUpdateRating creates or updates a user's rating for a game.
func (h *RatingHandler) CreateOrUpdateRating(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")

	gid, err := strconv.ParseUint(gameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid game ID"})
		return
	}

	// Verify game exists
	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	var req struct {
		Rating int    `json:"rating" binding:"required,min=1,max=5"`
		Review string `json:"review"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request: rating must be between 1 and 5"})
		return
	}

	var existing db.GameRating
	result := h.DB.Where("user_id = ? AND game_id = ?", uid, gid).First(&existing)
	if result.Error == gorm.ErrRecordNotFound {
		rating := db.GameRating{
			UserID: uid,
			GameID: uint(gid),
			Rating: req.Rating,
			Review: req.Review,
		}
		if err := h.DB.Create(&rating).Error; err != nil {
			c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create rating"})
			return
		}

		CreateActivityEvent(h.DB, h.Hub, uid, "rated_game", uint(gid), map[string]interface{}{
			"rating": req.Rating,
		})

		c.JSON(http.StatusCreated, h.toRatingResponse(rating, uid))
	} else {
		existing.Rating = req.Rating
		existing.Review = req.Review
		if err := h.DB.Save(&existing).Error; err != nil {
			c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to update rating"})
			return
		}
		c.JSON(http.StatusOK, h.toRatingResponse(existing, uid))
	}
}

// GetRatings returns paginated ratings for a game.
func (h *RatingHandler) GetRatings(c *gin.Context) {
	gameID := c.Param("id")

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.GameRating{}).Where("game_id = ?", gameID).Count(&total)

	var ratings []db.GameRating
	if err := h.DB.Where("game_id = ?", gameID).
		Preload("User").
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&ratings).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch ratings"})
		return
	}

	uid := getUserID(c)
	result := make([]GameRatingResponse, 0, len(ratings))
	for _, r := range ratings {
		result = append(result, h.toRatingResponse(r, uid))
	}

	c.JSON(http.StatusOK, PaginatedResponse{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// GetRatingSummary returns the average rating, count, and distribution for a game.
func (h *RatingHandler) GetRatingSummary(c *gin.Context) {
	gameID := c.Param("id")

	var agg struct {
		AverageRating float64
		TotalRatings  int64
	}
	if err := h.DB.Model(&db.GameRating{}).
		Where("game_id = ?", gameID).
		Select("COALESCE(AVG(rating), 0) as average_rating, COUNT(*) as total_ratings").
		Scan(&agg).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch rating summary"})
		return
	}

	// Distribution
	type distRow struct {
		Rating int
		Count  int
	}
	var distRows []distRow
	h.DB.Model(&db.GameRating{}).
		Where("game_id = ?", gameID).
		Select("rating, COUNT(*) as count").
		Group("rating").
		Scan(&distRows)

	distribution := map[string]int{
		"1": 0, "2": 0, "3": 0, "4": 0, "5": 0,
	}
	for _, r := range distRows {
		distribution[fmt.Sprintf("%d", r.Rating)] = r.Count
	}

	c.JSON(http.StatusOK, RatingSummaryResponse{
		AverageRating: agg.AverageRating,
		TotalRatings:  agg.TotalRatings,
		Distribution:  distribution,
	})
}

// GetMyRating returns the current user's rating for a game.
func (h *RatingHandler) GetMyRating(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")

	var rating db.GameRating
	if err := h.DB.Where("user_id = ? AND game_id = ?", uid, gameID).
		Preload("User").
		First(&rating).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "no rating found"})
		return
	}

	c.JSON(http.StatusOK, h.toRatingResponse(rating, uid))
}

// DeleteRating removes the current user's rating for a game.
func (h *RatingHandler) DeleteRating(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")

	result := h.DB.Where("user_id = ? AND game_id = ?", uid, gameID).Delete(&db.GameRating{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "no rating found"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "rating deleted"})
}

// toRatingResponse converts a db.GameRating to its API response.
func (h *RatingHandler) toRatingResponse(r db.GameRating, _ uint) GameRatingResponse {
	username := r.User.Username
	avatarURL := r.User.AvatarURL

	// If User wasn't preloaded, load it
	if username == "" && r.UserID != 0 {
		var user db.User
		if err := h.DB.First(&user, r.UserID).Error; err == nil {
			username = user.Username
			avatarURL = user.AvatarURL
		}
	}

	return GameRatingResponse{
		ID:        strconv.FormatUint(uint64(r.ID), 10),
		UserID:    strconv.FormatUint(uint64(r.UserID), 10),
		Username:  username,
		AvatarURL: avatarURL,
		GameID:    strconv.FormatUint(uint64(r.GameID), 10),
		Rating:    r.Rating,
		Review:    r.Review,
		CreatedAt: r.CreatedAt,
		UpdatedAt: r.UpdatedAt,
	}
}
