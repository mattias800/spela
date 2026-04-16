package api

import "github.com/gin-gonic/gin"

// apiError sends a JSON error response with a technical message (for logging)
// and a separate user-facing message.
func apiError(c *gin.Context, status int, technical, userMessage string) {
	c.JSON(status, ErrorResponse{Error: technical, Message: userMessage})
}

// abortWithError aborts the middleware chain and sends a dual-field error response.
func abortWithError(c *gin.Context, status int, technical, userMessage string) {
	c.AbortWithStatusJSON(status, ErrorResponse{Error: technical, Message: userMessage})
}
