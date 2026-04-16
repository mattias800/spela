package api

import (
	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// recordSecurityEventCtx is a thin convenience wrapper that extracts the
// client IP and request path from a gin.Context and then delegates to
// db.RecordSecurityEvent. Handlers should use this variant when they have
// the gin context in scope; non-HTTP callers (background jobs, WebSocket
// handlers) should call db.RecordSecurityEvent directly.
func recordSecurityEventCtx(database *gorm.DB, c *gin.Context, in db.SystemEventInput) {
	if in.IP == "" {
		in.IP = c.ClientIP()
	}
	if in.Path == "" && c.Request != nil && c.Request.URL != nil {
		in.Path = c.Request.URL.Path
	}
	db.RecordSecurityEvent(database, in)
}
