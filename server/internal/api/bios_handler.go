package api

import (
	"net/http"
	"os"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/storage"
)

// BiosHandler handles BIOS file management endpoints.
type BiosHandler struct {
	Storage *storage.Storage
}

// BiosFileInfo represents a BIOS file in the listing response.
type BiosFileInfo struct {
	Name string `json:"name"`
	Size int64  `json:"size"`
}

// ListBiosFiles returns all BIOS files in the configured BIOS directory.
func (h *BiosHandler) ListBiosFiles(c *gin.Context) {
	entries, err := os.ReadDir(h.Storage.BiosDir)
	if err != nil {
		c.JSON(http.StatusOK, []BiosFileInfo{})
		return
	}

	files := make([]BiosFileInfo, 0)
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		info, err := entry.Info()
		if err != nil {
			continue
		}
		files = append(files, BiosFileInfo{
			Name: entry.Name(),
			Size: info.Size(),
		})
	}

	c.JSON(http.StatusOK, files)
}

// GetBiosFile serves a BIOS file by filename.
func (h *BiosHandler) GetBiosFile(c *gin.Context) {
	filename := c.Param("filename")
	path := h.Storage.BiosFilePath(filename)

	if _, err := os.Stat(path); os.IsNotExist(err) {
		c.JSON(http.StatusNotFound, gin.H{"error": "bios file not found"})
		return
	}

	c.File(path)
}
