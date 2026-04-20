package api

import (
	"github.com/danielgtaylor/huma/v2"
)

// humaError is the error shape used by huma-registered operations. It matches
// the historical ErrorResponse wire format (`{"error": "...", "message": "..."}`)
// so consumers that were built against the raw gin handlers keep working
// unchanged as we migrate endpoints off gin.
type humaError struct {
	status  int
	Err     string `json:"error"`
	Message string `json:"message"`
}

// Error implements the built-in error interface.
func (e *humaError) Error() string { return e.Err }

// GetStatus implements huma.StatusError so huma writes the correct HTTP status.
func (e *humaError) GetStatus() int { return e.status }

// init overrides huma.NewError so every huma.ErrorXXX helper (Error404NotFound,
// Error400BadRequest, ...) produces the ErrorResponse wire format instead of
// huma's default RFC 7807-style shape. Called once at package load.
func init() {
	huma.NewError = func(status int, message string, errs ...error) huma.StatusError {
		return &humaError{
			status:  status,
			Err:     message,
			Message: message,
		}
	}
}
