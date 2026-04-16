package api

import (
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/patcher"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// RomHackHandler handles ROM hack creation endpoints.
type RomHackHandler struct {
	DB       *gorm.DB
	GameDirs []string
}

// maxPatchUploadSize limits patch file uploads to 100 MB.
const maxPatchUploadSize = 100 << 20

// CreateRomHack applies a patch file to a base game's ROM and creates a new game entry.
// POST /api/admin/rom-hacks
func (h *RomHackHandler) CreateRomHack(c *gin.Context) {
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxPatchUploadSize)

	// Parse form data
	baseGameID := c.PostForm("base_game_id")
	if baseGameID == "" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "base_game_id is required"})
		return
	}
	if _, err := strconv.ParseUint(baseGameID, 10, 64); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "base_game_id must be a valid numeric ID"})
		return
	}

	mode := c.PostForm("mode")
	if mode != "variant" && mode != "standalone" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "mode must be 'variant' or 'standalone'"})
		return
	}

	label := c.PostForm("label")
	title := c.PostForm("title")

	if mode == "variant" && label == "" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "label is required for variant mode"})
		return
	}
	if mode == "standalone" && title == "" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "title is required for standalone mode"})
		return
	}

	// Read patch file
	patchFile, patchHeader, err := c.Request.FormFile("patch_file")
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "patch_file is required"})
		return
	}
	defer patchFile.Close()

	// Validate patch extension
	patchExt := strings.ToLower(filepath.Ext(patchHeader.Filename))
	supportedExts := map[string]bool{".ips": true, ".bps": true, ".ups": true, ".xdelta": true, ".vcdiff": true}
	if !supportedExts[patchExt] {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "unsupported patch format: " + patchExt + "; supported: .ips, .bps, .ups, .xdelta"})
		return
	}

	patchData, err := io.ReadAll(patchFile)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "failed to read patch file"})
		return
	}

	// Load base game
	var baseGame db.Game
	if err := h.DB.Preload("Console").First(&baseGame, baseGameID).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "base game not found"})
		return
	}

	// Read the base game's ROM file
	romAbsPath, err := storage.ResolveGamePath(baseGame.FilePath, h.GameDirs)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "base game ROM file not found on disk"})
		return
	}

	romData, err := os.ReadFile(romAbsPath)
	if err != nil {
		slog.Warn("failed to read base ROM", "path", romAbsPath, "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to read base game ROM"})
		return
	}

	// Apply the patch
	patchedData, err := patcher.Apply(romData, patchData, patchHeader.Filename)
	if err != nil {
		slog.Info("patch application failed", "baseGame", baseGame.Title, "patchFile", patchHeader.Filename, "error", err)
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: fmt.Sprintf("patch failed: %s", err)})
		return
	}

	// Generate descriptive filename for the patched ROM
	romExt := filepath.Ext(baseGame.FileName)
	var patchedFileName string
	if mode == "variant" {
		// e.g. "Super Mario Bros [English Translation].nes"
		baseName := strings.TrimSuffix(baseGame.FileName, romExt)
		patchedFileName = baseName + " [" + label + "]" + romExt
	} else {
		// e.g. "My Cool Hack.nes"
		patchedFileName = sanitizePatchedFilename(title) + romExt
	}

	// Write the patched ROM to the games directory
	targetDir := filepath.Join(h.GameDirs[0], baseGame.Console.FolderName)
	if err := os.MkdirAll(targetDir, 0755); err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create target directory"})
		return
	}

	targetPath := filepath.Join(targetDir, patchedFileName)

	// Avoid overwriting existing files
	if _, err := os.Stat(targetPath); err == nil {
		c.JSON(http.StatusConflict, ErrorResponse{Error: "a ROM file with this name already exists: " + patchedFileName})
		return
	}

	if err := os.WriteFile(targetPath, patchedData, 0644); err != nil {
		slog.Warn("failed to write patched ROM", "path", targetPath, "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to write patched ROM file"})
		return
	}

	// Create the game record
	relPath := filepath.Join(baseGame.Console.FolderName, patchedFileName)

	var newGame db.Game
	if mode == "variant" {
		// Variant: same GroupKey and ConsoleID, tagged with "hack"
		variantTitle := baseGame.Title + " [" + label + "]"

		tags := "hack"
		if baseGame.Tags != "" {
			// Merge existing tags with "hack" if not already present
			existingTags := strings.Split(baseGame.Tags, ",")
			hasHack := false
			for _, t := range existingTags {
				if strings.TrimSpace(t) == "hack" {
					hasHack = true
					break
				}
			}
			if !hasHack {
				tags = baseGame.Tags + ",hack"
			} else {
				tags = baseGame.Tags
			}
		}

		newGame = db.Game{
			ConsoleID:   baseGame.ConsoleID,
			Title:       variantTitle,
			FileName:    patchedFileName,
			FilePath:    relPath,
			FileSize:    int64(len(patchedData)),
			Description: baseGame.Description,
			CoverURL:    baseGame.CoverURL,
			Developer:   baseGame.Developer,
			Publisher:   baseGame.Publisher,
			ReleaseDate: baseGame.ReleaseDate,
			Genre:       baseGame.Genre,
			GroupKey:    baseGame.GroupKey,
			Tags:        tags,
			Region:      baseGame.Region,
		}
	} else {
		// Standalone: unique GroupKey, own title, ParentGameID set
		parentID := baseGame.ID
		groupKey := scanner.NormalizeGroupKey(title)

		newGame = db.Game{
			ConsoleID:    baseGame.ConsoleID,
			Title:        title,
			FileName:     patchedFileName,
			FilePath:     relPath,
			FileSize:     int64(len(patchedData)),
			GroupKey:     groupKey,
			ParentGameID: &parentID,
			IsPrimary:    true,
		}
	}

	if err := h.DB.Create(&newGame).Error; err != nil {
		// Clean up the file we wrote
		os.Remove(targetPath)
		slog.Warn("failed to create game record for ROM hack", "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create game record"})
		return
	}

	// For variants, re-elect primary in the group to ensure the hack doesn't
	// accidentally become primary (betterVariant deprioritizes hack-tagged games
	// via shorter filenames, but let's be explicit).
	if mode == "variant" && baseGame.GroupKey != "" {
		if err := scanner.ReElectPrimaryForGroup(h.DB, baseGame.ConsoleID, baseGame.GroupKey); err != nil {
			slog.Warn("failed to re-elect primary after ROM hack creation", "error", err)
		}
	}

	slog.Info("ROM hack created",
		"mode", mode,
		"baseGame", baseGame.Title,
		"newGameID", newGame.ID,
		"newTitle", newGame.Title,
	)

	c.JSON(http.StatusCreated, gin.H{
		"id":    strconv.FormatUint(uint64(newGame.ID), 10),
		"title": newGame.Title,
	})
}

// sanitizePatchedFilename creates a safe filename from a title.
func sanitizePatchedFilename(title string) string {
	// Replace characters that are problematic in filenames
	replacer := strings.NewReplacer(
		"/", "-",
		"\\", "-",
		":", "-",
		"*", "",
		"?", "",
		"\"", "",
		"<", "",
		">", "",
		"|", "",
	)
	safe := replacer.Replace(title)
	safe = strings.TrimSpace(safe)
	if safe == "" {
		safe = "rom-hack"
	}
	return safe
}
