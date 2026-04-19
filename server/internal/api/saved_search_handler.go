package api

import (
	"encoding/json"
	"time"

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
