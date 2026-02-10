package api

import (
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"os"
	"path/filepath"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// previewFallbackGames maps console abbreviations to well-known game titles
// used as fallback when no local game has a scraped screenshot.
var previewFallbackGames = map[string]string{
	"NES":    "Super Mario Bros. (World)",
	"SNES":   "Super Mario World (USA)",
	"GB":     "Pokemon Red Version (USA, Europe) (SGB Enhanced)",
	"GBC":    "Pokemon Crystal Version (USA, Europe)",
	"GBA":    "Pokemon - Fire Red Version (USA, Europe)",
	"N64":    "Super Mario 64 (USA)",
	"NDS":    "New Super Mario Bros. (USA, Europe)",
	"SMS":    "Sonic the Hedgehog (USA, Europe)",
	"GEN":    "Sonic The Hedgehog (USA, Europe)",
	"SAT":    "Nights Into Dreams... (USA)",
	"PSX":    "Crash Bandicoot (USA)",
	"PSP":    "God of War - Chains of Olympus (USA)",
	"NEOGEO": "Metal Slug - Super Vehicle-001 (NGM-006)(NGH-006)",
	"ARCADE": "Street Fighter II - The World Warrior (World 910522)",
	"PCE":    "Bonk's Adventure (USA)",
	"A26":    "Pitfall! (USA)",
}

// ConsoleHandler handles console-related endpoints.
type ConsoleHandler struct {
	DB      *gorm.DB
	Storage *storage.Storage
}

// ListConsoles returns all consoles with game counts.
func (h *ConsoleHandler) ListConsoles(c *gin.Context) {
	var consoles []db.Console
	if err := h.DB.Find(&consoles).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch consoles"})
		return
	}

	// Attach game counts
	for i := range consoles {
		var count int64
		h.DB.Model(&db.Game{}).Where("console_id = ?", consoles[i].ID).Count(&count)
		consoles[i].GameCount = int(count)
	}

	// Convert to API response format
	result := make([]ConsoleResponse, len(consoles))
	for i, con := range consoles {
		result[i] = ToConsoleResponse(con)
	}

	c.JSON(http.StatusOK, result)
}

// ListConsoleGames returns games for a specific console as a flat Game array.
func (h *ConsoleHandler) ListConsoleGames(c *gin.Context) {
	consoleID := c.Param("id")

	var console db.Console
	if err := h.DB.First(&console, consoleID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "console not found"})
		return
	}

	var games []db.Game
	if err := h.DB.Where("console_id = ?", consoleID).
		Preload("Console").
		Order("title asc").
		Find(&games).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch games"})
		return
	}

	userID := getUserID(c)
	c.JSON(http.StatusOK, ToGameResponses(games, h.DB, userID))
}

// GetPreviewScreenshot returns a representative screenshot for a console.
// It tries: (1) a local game with a scraped screenshot, (2) a cached CDN preview,
// (3) downloading from the LibRetro CDN and caching it.
func (h *ConsoleHandler) GetPreviewScreenshot(c *gin.Context) {
	consoleID := c.Param("id")

	var console db.Console
	if err := h.DB.First(&console, consoleID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "console not found"})
		return
	}

	// Strategy 1: Find a local game with a scraped screenshot
	var game db.Game
	err := h.DB.Where("console_id = ? AND screenshot_url != ''", console.ID).
		First(&game).Error
	if err == nil && game.ScreenshotURL != "" {
		c.Header("Cache-Control", "public, max-age=86400")
		c.Redirect(http.StatusFound, "/api/images/"+game.ScreenshotURL)
		return
	}

	// Strategy 2: Check for cached preview
	cachedPath := filepath.Join("previews", console.Abbreviation, "preview.png")
	fullCachedPath := h.Storage.ImagePath(cachedPath)
	if _, err := os.Stat(fullCachedPath); err == nil {
		c.Header("Cache-Control", "public, max-age=86400")
		c.Redirect(http.StatusFound, "/api/images/"+cachedPath)
		return
	}

	// Strategy 3: Download from LibRetro CDN
	libRetroSystem, ok := scraper.AbbreviationToLibRetro[console.Abbreviation]
	if !ok {
		c.JSON(http.StatusNotFound, gin.H{"error": "no preview available for this console"})
		return
	}

	fallbackGame, ok := previewFallbackGames[console.Abbreviation]
	if !ok {
		c.JSON(http.StatusNotFound, gin.H{"error": "no preview available for this console"})
		return
	}

	imageURL := fmt.Sprintf("https://thumbnails.libretro.com/%s/Named_Snaps/%s.png",
		url.PathEscape(libRetroSystem),
		url.PathEscape(fallbackGame),
	)

	slog.Info("downloading preview screenshot from CDN", "console", console.Abbreviation, "url", imageURL)

	resp, err := http.Get(imageURL)
	if err != nil {
		slog.Warn("failed to download preview screenshot", "console", console.Abbreviation, "error", err)
		c.JSON(http.StatusNotFound, gin.H{"error": "failed to download preview"})
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		slog.Warn("CDN returned non-200 for preview", "console", console.Abbreviation, "status", resp.StatusCode)
		c.JSON(http.StatusNotFound, gin.H{"error": "preview not available from CDN"})
		return
	}

	// Cache the downloaded image
	savedPath, err := h.Storage.WriteImage(cachedPath, resp.Body)
	if err != nil {
		slog.Warn("failed to cache preview screenshot", "console", console.Abbreviation, "error", err)
		// Serve directly from CDN body if caching fails - but body is already consumed.
		// Re-download is expensive; return error.
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to cache preview"})
		return
	}

	c.Header("Cache-Control", "public, max-age=86400")
	c.Redirect(http.StatusFound, "/api/images/"+savedPath)
}


// getUserID extracts the authenticated user's ID from the context.
func getUserID(c *gin.Context) uint {
	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)
	return uid
}
