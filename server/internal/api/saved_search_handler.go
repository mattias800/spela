package api

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// SavedSearchHandler handles saved search CRUD endpoints.
type SavedSearchHandler struct {
	DB *gorm.DB
}

// SavedSearchRequest is the request body for creating a saved search.
// Fields are marked optional in the huma schema so handler-side validation
// owns the 400 response (the raw gin handler used binding:"required" tags).
type SavedSearchRequest struct {
	Name    string          `json:"name,omitempty" required:"false" binding:"required"`
	Filters json.RawMessage `json:"filters,omitempty" required:"false" binding:"required"`
}

// SavedSearchResponse is the API response for a saved search.
type SavedSearchResponse struct {
	ID        string          `json:"id"`
	Name      string          `json:"name"`
	Filters   json.RawMessage `json:"filters"`
	CreatedAt time.Time       `json:"createdAt"`
}

// maxSavedSearchesPerUser is the maximum number of saved searches a user can have.
const maxSavedSearchesPerUser = 50

// CreateSavedSearch creates a new saved search for the authenticated user.
// POST /api/user/saved-searches
func (h *SavedSearchHandler) CreateSavedSearch(c *gin.Context) {
	userID := getUserID(c)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, ErrorResponse{Error: "unauthorized"})
		return
	}

	var req SavedSearchRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "name and filters are required"})
		return
	}

	// Validate name length
	if len(req.Name) > 255 {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "name must be 255 characters or fewer"})
		return
	}

	// Validate filters is valid JSON
	if !json.Valid(req.Filters) {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "filters must be valid JSON"})
		return
	}

	// Check per-user limit
	var count int64
	h.DB.Model(&db.SavedSearch{}).Where("user_id = ?", userID).Count(&count)
	if count >= maxSavedSearchesPerUser {
		c.JSON(http.StatusConflict, ErrorResponse{Error: "maximum of 50 saved searches reached"})
		return
	}

	savedSearch := db.SavedSearch{
		UserID:  userID,
		Name:    req.Name,
		Filters: string(req.Filters),
	}
	if err := h.DB.Create(&savedSearch).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create saved search"})
		return
	}

	c.JSON(http.StatusCreated, SavedSearchResponse{
		ID:        strconv.FormatUint(uint64(savedSearch.ID), 10),
		Name:      savedSearch.Name,
		Filters:   json.RawMessage(savedSearch.Filters),
		CreatedAt: savedSearch.CreatedAt,
	})
}

// ListSavedSearches returns all saved searches for the authenticated user.
// GET /api/user/saved-searches
func (h *SavedSearchHandler) ListSavedSearches(c *gin.Context) {
	userID := getUserID(c)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, ErrorResponse{Error: "unauthorized"})
		return
	}

	var searches []db.SavedSearch
	if err := h.DB.Where("user_id = ?", userID).Order("created_at DESC").Find(&searches).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch saved searches"})
		return
	}

	resp := make([]SavedSearchResponse, 0, len(searches))
	for _, s := range searches {
		resp = append(resp, SavedSearchResponse{
			ID:        strconv.FormatUint(uint64(s.ID), 10),
			Name:      s.Name,
			Filters:   json.RawMessage(s.Filters),
			CreatedAt: s.CreatedAt,
		})
	}

	c.JSON(http.StatusOK, resp)
}

// DeleteSavedSearch soft-deletes a saved search owned by the authenticated user.
// DELETE /api/user/saved-searches/:id
func (h *SavedSearchHandler) DeleteSavedSearch(c *gin.Context) {
	userID := getUserID(c)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, ErrorResponse{Error: "unauthorized"})
		return
	}

	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid ID"})
		return
	}

	var savedSearch db.SavedSearch
	if err := h.DB.First(&savedSearch, id).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "saved search not found"})
		return
	}

	// Verify ownership
	if savedSearch.UserID != userID {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "access denied"})
		return
	}

	if err := h.DB.Delete(&savedSearch).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to delete saved search"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"status": "deleted"})
}
