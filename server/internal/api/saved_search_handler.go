package api

import (
	"gorm.io/gorm"
)

// SavedSearchHandler handles saved search CRUD endpoints. Request and response
// types live in requests.go / responses.go.
type SavedSearchHandler struct {
	DB *gorm.DB
}

// maxSavedSearchesPerUser is the maximum number of saved searches a user can have.
const maxSavedSearchesPerUser = 50
