package api

import (
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// SetupHandler handles first-time setup endpoints.
type SetupHandler struct {
	DB            *gorm.DB
	JWTSecret     string
	EncryptionKey []byte
	GameDirs      []string
	Storage       *storage.Storage
}

// DiagnosticCheck represents a single server health check result.
type DiagnosticCheck struct {
	ID     string `json:"id"`
	Label  string `json:"label"`
	Status string `json:"status"` // "ok", "warning", "error"
	Detail string `json:"detail,omitempty"`
	Fix    string `json:"fix,omitempty"`
}

// Diagnostics returns server configuration health checks.
// Public when no users exist (fresh install); requires admin auth otherwise.
func (h *SetupHandler) Diagnostics(c *gin.Context) {
	// Check if setup is needed (no users = public access)
	var userCount int64
	h.DB.Model(&db.User{}).Count(&userCount)

	if userCount > 0 {
		// Require admin auth
		token := ""
		header := c.GetHeader("Authorization")
		if header != "" {
			parts := strings.SplitN(header, " ", 2)
			if len(parts) == 2 && parts[0] == "Bearer" {
				token = parts[1]
			}
		}
		if token == "" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "authentication required"})
			return
		}

		claims, err := auth.ValidateAccessToken(token, h.JWTSecret)
		if err != nil {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid token"})
			return
		}

		// Check blacklist
		if IsTokenBlacklisted(h.DB, token) {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "token revoked"})
			return
		}

		// Check user is admin/owner
		var user db.User
		if err := h.DB.Select("id", "role", "disabled", "token_version").First(&user, claims.UserID).Error; err != nil {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "user not found"})
			return
		}
		if user.Disabled {
			c.JSON(http.StatusForbidden, gin.H{"error": "account disabled"})
			return
		}
		if claims.TokenVersion != user.TokenVersion {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "token invalidated"})
			return
		}
		if user.Role != "admin" && user.Role != "owner" {
			c.JSON(http.StatusForbidden, gin.H{"error": "admin access required"})
			return
		}
	}

	checks := h.runDiagnostics()
	c.JSON(http.StatusOK, gin.H{"checks": checks})
}

func (h *SetupHandler) runDiagnostics() []DiagnosticCheck {
	var checks []DiagnosticCheck

	checks = append(checks, h.checkDatabase())
	checks = append(checks, h.checkJWTSecret())
	checks = append(checks, h.checkEncryptionKey())
	checks = append(checks, h.checkGameDirs())
	checks = append(checks, h.checkStorageWritable())
	checks = append(checks, h.checkIGDB())
	checks = append(checks, h.checkSteamGridDB())

	return checks
}

func (h *SetupHandler) checkDatabase() DiagnosticCheck {
	check := DiagnosticCheck{ID: "database", Label: "Database"}
	sqlDB, err := h.DB.DB()
	if err != nil {
		check.Status = "error"
		check.Detail = "Cannot access database"
		return check
	}
	if err := sqlDB.Ping(); err != nil {
		check.Status = "error"
		check.Detail = "Database not responding"
		return check
	}
	check.Status = "ok"
	check.Detail = "Connected"
	return check
}

func (h *SetupHandler) checkJWTSecret() DiagnosticCheck {
	check := DiagnosticCheck{ID: "jwt_secret", Label: "JWT Secret"}
	if len(h.JWTSecret) >= 32 {
		check.Status = "ok"
		check.Detail = "Configured"
	} else {
		check.Status = "warning"
		check.Detail = "Secret is shorter than recommended 32 characters"
		check.Fix = "Set SPELA_JWT_SECRET to a random string of at least 32 characters"
	}
	return check
}

func (h *SetupHandler) checkEncryptionKey() DiagnosticCheck {
	check := DiagnosticCheck{ID: "encryption_key", Label: "Encryption Key"}
	envKey := os.Getenv("SPELA_ENCRYPTION_KEY")
	if envKey != "" {
		if err := auth.ValidateEncryptionKey([]byte(envKey)); err != nil {
			check.Status = "error"
			check.Detail = fmt.Sprintf("Invalid key length: %d bytes", len(envKey))
			check.Fix = "Set SPELA_ENCRYPTION_KEY to exactly 16, 24, or 32 bytes (32 recommended)"
		} else {
			check.Status = "ok"
			check.Detail = "Set via environment variable"
		}
	} else {
		check.Status = "warning"
		check.Detail = "Derived from JWT secret (not recommended for production)"
		check.Fix = "Set SPELA_ENCRYPTION_KEY to a random string of at least 32 characters for production use"
	}
	return check
}

func (h *SetupHandler) checkGameDirs() DiagnosticCheck {
	check := DiagnosticCheck{ID: "game_dirs", Label: "Game Directories"}

	if len(h.GameDirs) == 0 {
		check.Status = "error"
		check.Detail = "No game directories configured"
		check.Fix = "Set SPELA_GAME_DIRS or mount a volume to /app/games"
		return check
	}

	totalFiles := 0
	var missingDirs []string
	for _, dir := range h.GameDirs {
		info, err := os.Stat(dir)
		if err != nil || !info.IsDir() {
			missingDirs = append(missingDirs, dir)
			continue
		}
		totalFiles += countGameFiles(dir)
	}

	if len(missingDirs) > 0 {
		check.Status = "error"
		check.Detail = fmt.Sprintf("Missing directories: %s", strings.Join(missingDirs, ", "))
		check.Fix = "Mount your ROM directory to the configured game path"
		return check
	}

	if totalFiles == 0 {
		check.Status = "warning"
		check.Detail = fmt.Sprintf("%d directories configured, no game files found", len(h.GameDirs))
		check.Fix = "Add ROM files to your game directory and run a scan"
	} else {
		check.Status = "ok"
		check.Detail = fmt.Sprintf("%d game files found across %d directories", totalFiles, len(h.GameDirs))
	}
	return check
}

// countGameFiles counts files with known ROM extensions in a directory tree.
func countGameFiles(dir string) int {
	count := 0
	knownExts := map[string]bool{
		".nes": true, ".sfc": true, ".smc": true, ".gba": true, ".gbc": true,
		".gb": true, ".n64": true, ".z64": true, ".v64": true, ".nds": true,
		".gen": true, ".md": true, ".smd": true, ".bin": true, ".iso": true,
		".cue": true, ".chd": true, ".zip": true, ".7z": true, ".psx": true,
		".cso": true, ".pbp": true, ".nsp": true, ".xci": true, ".3ds": true,
		".cia": true, ".wad": true, ".gcm": true, ".wbfs": true, ".a26": true,
		".lnx": true, ".lyx": true, ".pce": true, ".ngp": true, ".ngc": true, ".ws": true,
		".wsc": true, ".vb": true, ".vec": true, ".col": true, ".sg": true,
		".gg": true, ".sms": true, ".32x": true,
	}
	filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil
		}
		if !info.IsDir() {
			ext := strings.ToLower(filepath.Ext(path))
			if knownExts[ext] {
				count++
			}
		}
		return nil
	})
	return count
}

func (h *SetupHandler) checkStorageWritable() DiagnosticCheck {
	check := DiagnosticCheck{ID: "storage_writable", Label: "Storage"}
	dirs := map[string]string{
		"saves":  h.Storage.SaveDir,
		"cores":  h.Storage.CoreDir,
		"images": h.Storage.ImageDir,
	}
	var unwritable []string
	for name, dir := range dirs {
		tmpPath := filepath.Join(dir, ".spela-write-test")
		f, err := os.Create(tmpPath)
		if err != nil {
			unwritable = append(unwritable, name)
			continue
		}
		f.Close()
		os.Remove(tmpPath)
	}
	if len(unwritable) > 0 {
		check.Status = "error"
		check.Detail = fmt.Sprintf("Cannot write to: %s", strings.Join(unwritable, ", "))
		check.Fix = "Check volume mount permissions for storage directories"
	} else {
		check.Status = "ok"
		check.Detail = "All storage directories writable"
	}
	return check
}

func (h *SetupHandler) checkIGDB() DiagnosticCheck {
	check := DiagnosticCheck{ID: "igdb", Label: "IGDB Metadata"}
	source := IGDBSource(h.DB)
	switch source {
	case "env":
		check.Status = "ok"
		check.Detail = "Configured via environment variables"
	case "database":
		check.Status = "ok"
		check.Detail = "Configured via settings"
	default:
		check.Status = "warning"
		check.Detail = "Not configured"
		check.Fix = "Configure IGDB in the next step for game metadata and cover art"
	}
	return check
}

func (h *SetupHandler) checkSteamGridDB() DiagnosticCheck {
	check := DiagnosticCheck{ID: "steamgriddb", Label: "SteamGridDB Artwork"}
	source := SteamGridDBSource(h.DB)
	switch source {
	case "env":
		check.Status = "ok"
		check.Detail = "Configured via environment variables"
	case "database":
		check.Status = "ok"
		check.Detail = "Configured via settings"
	default:
		check.Status = "warning"
		check.Detail = "Not configured (optional)"
		check.Fix = "Configure SteamGridDB for hero artwork, logos, and grid images"
	}
	return check
}
