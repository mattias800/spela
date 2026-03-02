package api

import "github.com/gin-gonic/gin"

// apiError sends a JSON error response with a technical message (for logging)
// and a separate user-facing message.
func apiError(c *gin.Context, status int, technical, userMessage string) {
	c.JSON(status, gin.H{"error": technical, "message": userMessage})
}

// abortWithError aborts the middleware chain and sends a dual-field error response.
func abortWithError(c *gin.Context, status int, technical, userMessage string) {
	c.AbortWithStatusJSON(status, gin.H{"error": technical, "message": userMessage})
}
