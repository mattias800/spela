package api

import (
	"archive/zip"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
)

// ReplaceROMResult holds the verification result from a ROM replacement.
type ReplaceROMResult struct {
	Verified       bool   `json:"verified"`
	CRC32          string `json:"crc32"`
	CanonicalName  string `json:"canonicalName,omitempty"`
	PreviousStatus string `json:"previousStatus"`
	PreviousCRC32  string `json:"previousCrc32"`
}

// ReplaceROMResponse is the API response for a ROM replacement.
type ReplaceROMResponse struct {
	Game              GameResponse     `json:"game"`
	ReplacementResult ReplaceROMResult `json:"replacementResult"`
}

// ReplaceROM replaces a game's ROM file with a new one, re-verifying against DAT files.
// PUT /api/admin/games/:id/replace-rom
func (h *GameHandler) ReplaceROM(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	// Accept the uploaded file
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxROMUploadSize)
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "file required in 'file' field"})
		return
	}
	defer file.Close()

	ext := strings.ToLower(filepath.Ext(header.Filename))

	// Write uploaded file to a temp location
	tmpDir := os.TempDir()
	tmpFile, err := os.CreateTemp(tmpDir, "replace-rom-*"+ext)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create temp file"})
		return
	}
	tmpPath := tmpFile.Name()
	defer os.Remove(tmpPath)

	written, err := io.Copy(tmpFile, file)
	tmpFile.Close()
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to write uploaded file"})
		return
	}

	// If zip, extract the first ROM file
	var romPath string
	var romExt string
	var romSize int64
	var extractedTmpPath string

	if ext == ".zip" {
		extractedPath, extractedExt, extractedSize, extractErr := extractFirstROMFromZip(tmpPath, maxROMUploadSize)
		if extractErr != nil {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: extractErr.Error()})
			return
		}
		romPath = extractedPath
		romExt = extractedExt
		romSize = extractedSize
		extractedTmpPath = extractedPath
		defer os.Remove(extractedTmpPath)
	} else {
		// Validate extension is a recognized ROM type
		if !scanner.RomExtensions[ext] {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "unrecognized ROM file extension: " + ext})
			return
		}
		romPath = tmpPath
		romExt = ext
		romSize = written
	}

	// Compute CRC32 of the new ROM
	crc, err := scraper.ComputeFileCRC32(romPath)
	if err != nil {
		slog.Warn("failed to compute CRC32 for replacement ROM", "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to compute CRC32"})
		return
	}

	// Save previous state for response
	previousStatus := game.VerificationStatus
	previousCRC32 := game.CRC32

	// Verify against DAT files
	verified := false
	canonicalName := ""
	newVerificationStatus := "unverified"

	if h.Scraper != nil && h.Scraper.DATCache != nil {
		consoleAbbrev := game.Console.Abbreviation

		if scraper.DiscBasedSystems[consoleAbbrev] {
			// Try Redump DAT for disc-based systems
			newVerificationStatus = "not_applicable"
			if idx, err := h.Scraper.DATCache.GetRedumpIndex(consoleAbbrev); err == nil && idx != nil {
				if entry, ok := idx.LookupCRC(crc); ok {
					verified = true
					canonicalName = entry.ROMName
					newVerificationStatus = "verified"
				}
			}
		} else {
			// Try No-Intro DAT for cartridge-based systems
			if idx, err := h.Scraper.DATCache.GetIndex(consoleAbbrev); err == nil && idx != nil {
				if entry, ok := idx.LookupCRC(crc); ok {
					verified = true
					canonicalName = entry.ROMName
					newVerificationStatus = "verified"
				}
			}
		}
	}

	// Resolve the current ROM path on disk
	oldAbsPath, err := storage.ResolveGamePath(game.FilePath, h.GameDirs)
	if err != nil {
		slog.Warn("could not resolve existing game file path", "filePath", game.FilePath, "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to locate existing ROM file"})
		return
	}

	// Determine the target filename and path
	targetDir := filepath.Dir(oldAbsPath)
	targetName := game.FileName
	if verified && canonicalName != "" {
		// Use canonical name with proper extension
		targetName = canonicalName
		if filepath.Ext(canonicalName) == "" {
			targetName = canonicalName + romExt
		}
	}
	targetPath := filepath.Join(targetDir, targetName)

	// Copy new ROM to target location
	if err := copyFile(romPath, targetPath); err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to write replacement ROM"})
		return
	}

	// Update game record in DB before removing old file to avoid inconsistent state
	game.CRC32 = crc
	game.VerificationStatus = newVerificationStatus
	game.FileName = targetName
	game.FilePath = filepath.Join(game.Console.FolderName, targetName)
	game.FileSize = romSize

	if err := h.DB.Save(&game).Error; err != nil {
		// DB save failed — remove the new file if it differs from old to avoid orphans
		if targetPath != oldAbsPath {
			os.Remove(targetPath)
		}
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to update game record"})
		return
	}

	// Remove old file if target path changed (rename case)
	if targetPath != oldAbsPath {
		if err := os.Remove(oldAbsPath); err != nil {
			slog.Warn("failed to remove old ROM file after replacement", "path", oldAbsPath, "error", err)
		}
	}

	// Reload with associations for the response
	h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").First(&game, game.ID)
	userID := getUserID(c)

	c.JSON(http.StatusOK, ReplaceROMResponse{
		Game: ToGameResponse(game, h.DB, userID),
		ReplacementResult: ReplaceROMResult{
			Verified:       verified,
			CRC32:          crc,
			CanonicalName:  canonicalName,
			PreviousStatus: previousStatus,
			PreviousCRC32:  previousCRC32,
		},
	})
}

// extractFirstROMFromZip opens a zip archive and extracts the first recognized ROM file
// to a temporary location. Returns the path, extension, size, and any error.
// maxSize limits the decompressed output to prevent zip bomb attacks.
func extractFirstROMFromZip(zipPath string, maxSize int64) (string, string, int64, error) {
	r, err := zip.OpenReader(zipPath)
	if err != nil {
		return "", "", 0, fmt.Errorf("failed to open zip file: %w", err)
	}
	defer r.Close()

	for _, zf := range r.File {
		if zf.FileInfo().IsDir() {
			continue
		}

		ext := strings.ToLower(filepath.Ext(zf.Name))
		if !scanner.RomExtensions[ext] {
			continue
		}

		// Found a ROM file — extract it
		zfReader, err := zf.Open()
		if err != nil {
			return "", "", 0, fmt.Errorf("failed to open zip entry %s: %w", zf.Name, err)
		}

		tmpFile, err := os.CreateTemp("", "replace-rom-zip-*"+ext)
		if err != nil {
			zfReader.Close()
			return "", "", 0, fmt.Errorf("failed to create temp file for extraction: %w", err)
		}

		// Use LimitedReader to prevent zip bomb decompression attacks
		limited := &io.LimitedReader{R: zfReader, N: maxSize + 1}
		written, err := io.Copy(tmpFile, limited)
		tmpFile.Close()
		zfReader.Close()
		if err != nil {
			os.Remove(tmpFile.Name())
			return "", "", 0, fmt.Errorf("failed to extract zip entry %s: %w", zf.Name, err)
		}
		if written > maxSize {
			os.Remove(tmpFile.Name())
			return "", "", 0, fmt.Errorf("extracted ROM exceeds maximum size limit")
		}

		return tmpFile.Name(), ext, written, nil
	}

	return "", "", 0, fmt.Errorf("no recognized ROM file found in zip archive")
}
