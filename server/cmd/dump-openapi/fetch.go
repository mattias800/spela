package main

import (
	"fmt"
	"io"
	"net/http/httptest"

	"github.com/gin-gonic/gin"
)

// fetchSpec issues an internal GET against /api/openapi.json via httptest
// against the router. Saves us from binding a port just to dump the spec.
func fetchSpec(router *gin.Engine) ([]byte, error) {
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/openapi.json", nil)
	router.ServeHTTP(w, req)
	if w.Code != 200 {
		return nil, fmt.Errorf("openapi endpoint returned status %d", w.Code)
	}
	body, err := io.ReadAll(w.Body)
	if err != nil {
		return nil, err
	}
	return body, nil
}
