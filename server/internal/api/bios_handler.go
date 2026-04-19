package api

import (
	"crypto/md5"
	"encoding/hex"
	"io"
	"os"
	"strings"
	"sync"

	"github.com/spela/server/internal/bios"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

const maxBiosUploadSize = 16 << 20 // 16 MB

// BiosHandler handles BIOS file management endpoints.
type BiosHandler struct {
	Storage *storage.Storage
	DB      *gorm.DB
	Hub     *ws.Hub

	downloadMu  sync.Mutex
	downloading bool
}

// consoleNameMap builds a map from lowercase abbreviation to console name.
func consoleNameMap(database *gorm.DB) map[string]string {
	names := make(map[string]string)
	if database == nil {
		return names
	}
	var consoles []db.Console
	database.Select("abbreviation, name").Find(&consoles)
	for _, c := range consoles {
		names[strings.ToLower(c.Abbreviation)] = c.Name
	}
	return names
}

// computeFileMD5 computes the MD5 checksum of a file.
func computeFileMD5(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := md5.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

// GetConsoleStatus returns the BIOS status string for a given console abbreviation.
// This is used by other handlers (e.g., GameHandler) to add biosStatus to responses.
func GetConsoleStatus(biosDir string, consoleAbbr string) string {
	cid := strings.ToLower(consoleAbbr)
	entries := bios.ByConsole(cid)

	if len(entries) == 0 {
		return "not_required"
	}

	hasRequired := false
	hasMissingRequired := false
	hasInvalidRequired := false

	for _, e := range entries {
		if !e.Required {
			continue
		}
		hasRequired = true

		filePath := e.FilePath(biosDir)
		if _, err := os.Stat(filePath); os.IsNotExist(err) {
			hasMissingRequired = true
			continue
		}

		fileMD5, err := computeFileMD5(filePath)
		if err == nil && e.MD5 != "" && fileMD5 != e.MD5 {
			hasInvalidRequired = true
		}
	}

	if !hasRequired {
		return "not_required"
	}
	if hasMissingRequired {
		return "missing"
	}
	if hasInvalidRequired {
		return "invalid"
	}
	return "ready"
}
