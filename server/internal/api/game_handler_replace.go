package api

import (
	"archive/zip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/spela/server/internal/scanner"
)

// ReplaceROMResult holds the verification result from a ROM replacement.
type ReplaceROMResult struct {
	Verified       bool   `json:"verified"`
	CRC32          string `json:"crc32"`
	CanonicalName  string `json:"canonicalName"`
	PreviousStatus string `json:"previousStatus"`
	PreviousCRC32  string `json:"previousCrc32"`
}

// ReplaceROMResponse is the API response for a ROM replacement.
type ReplaceROMResponse struct {
	Game              GameResponse     `json:"game"`
	ReplacementResult ReplaceROMResult `json:"replacementResult"`
}

// ReplaceROM has been migrated to huma — see (*GameHandler).HumaReplaceROM in
// huma_admin_multipart.go. The gin handler was removed once nothing referenced
// it; the OpenAPI spec is now the single source of truth for this endpoint.

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
