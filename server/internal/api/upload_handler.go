package api

import (
	"archive/zip"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

const (
	// maxZipExtractedSize is the maximum total extracted size from a single zip file (50 GB).
	maxZipExtractedSize = 50 << 30
	// maxZipFileCount is the maximum number of files allowed in a single zip archive.
	maxZipFileCount = 10000
)

const maxROMUploadSize = 50 << 30 // 50 GB

// UploadHandler handles ROM upload staging endpoints (admin only).
type UploadHandler struct {
	DB         *gorm.DB
	Storage    *storage.Storage
	Scraper    *scraper.Scraper
	GameDirs   []string
	StagingDir string
}

// PossibleConsoleResponse is a console option for ambiguous extension selection.
type PossibleConsoleResponse struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

// StagedUploadResponse is the API response for a staged upload.
type StagedUploadResponse struct {
	ID                 string                    `json:"id"`
	FileName           string                    `json:"fileName"`
	OriginalFileName   string                    `json:"originalFileName"`
	FileSize           int64                     `json:"fileSize"`
	ConsoleID          *string                   `json:"consoleId,omitempty"`
	ConsoleName        *string                   `json:"consoleName,omitempty"`
	PossibleConsoles   []PossibleConsoleResponse `json:"possibleConsoles,omitempty"`
	Status             string   `json:"status"`
	Title              string   `json:"title,omitempty"`
	CoverURL           string   `json:"coverUrl,omitempty"`
	Description        string   `json:"description,omitempty"`
	Rating             float64  `json:"rating,omitempty"`
	Developer          string   `json:"developer,omitempty"`
	Publisher          string   `json:"publisher,omitempty"`
	Genre              string   `json:"genre,omitempty"`
	Players            int      `json:"players,omitempty"`
	ReleaseDate        string   `json:"releaseDate,omitempty"`
	VerificationStatus string   `json:"verificationStatus,omitempty"`
	CRC32              string   `json:"crc32,omitempty"`
	CanonicalName      string   `json:"canonicalName,omitempty"`
	DuplicateOfGameID  *string  `json:"duplicateOfGameId,omitempty"`
}

// toStagedUploadResponse converts a db.StagedUpload to its API response.
func toStagedUploadResponse(su db.StagedUpload, database *gorm.DB) StagedUploadResponse {
	resp := StagedUploadResponse{
		ID:                 strconv.FormatUint(uint64(su.ID), 10),
		FileName:           su.FileName,
		OriginalFileName:   su.OriginalFileName,
		FileSize:           su.FileSize,
		Status:             su.Status,
		Title:              su.Title,
		CoverURL:           resolveImageURL(su.CoverURL),
		Description:        su.Description,
		Rating:             su.Rating,
		Developer:          su.Developer,
		Publisher:          su.Publisher,
		Genre:              su.Genre,
		Players:            su.Players,
		ReleaseDate:        su.ReleaseDate,
		VerificationStatus: su.VerificationStatus,
		CRC32:              su.CRC32,
		CanonicalName:      su.CanonicalName,
	}

	if su.ConsoleID != nil {
		var console db.Console
		if err := database.First(&console, *su.ConsoleID).Error; err == nil {
			abbr := strings.ToLower(console.Abbreviation)
			resp.ConsoleID = &abbr
			resp.ConsoleName = &console.Name
		}
	}

	if su.PossibleConsoles != "" {
		var abbreviations []string
		if err := json.Unmarshal([]byte(su.PossibleConsoles), &abbreviations); err == nil {
			for _, abbr := range abbreviations {
				var c db.Console
				if err := database.Where("LOWER(abbreviation) = LOWER(?)", abbr).First(&c).Error; err == nil {
					resp.PossibleConsoles = append(resp.PossibleConsoles, PossibleConsoleResponse{
						ID:   strings.ToLower(c.Abbreviation),
						Name: c.Name,
					})
				}
			}
		}
	}

	if su.DuplicateOfGameID != nil {
		idStr := strconv.FormatUint(uint64(*su.DuplicateOfGameID), 10)
		resp.DuplicateOfGameID = &idStr
	}

	return resp
}

// ambiguousExtensions returns extensions that map to multiple consoles.
// These require admin input to determine the correct console.
func ambiguousExtensions() map[string]bool {
	return map[string]bool{
		".bin": true,
		".iso": true,
		".pkg": true, // PS3, PS4, PS5
		".xvd": true, // XONE, XSX
	}
}

// consolesForExtension returns all console abbreviations that support a given extension.
func consolesForExtension(database *gorm.DB, ext string) []string {
	var consoles []db.Console
	database.Find(&consoles)

	var result []string
	for _, c := range consoles {
		for _, e := range strings.Split(c.Extensions, ",") {
			if strings.TrimSpace(e) == ext {
				result = append(result, strings.ToLower(c.Abbreviation))
				break
			}
		}
	}
	return result
}

// detectConsole determines the console for an uploaded ROM based on file extension.
// Returns the console ID and a flag indicating if the extension is ambiguous.
func detectConsole(database *gorm.DB, ext string) (*uint, bool) {
	// Check if extension is ambiguous
	if ambiguousExtensions()[ext] {
		return nil, true
	}

	// Try the unambiguous extension map
	if abbrev, ok := scanner.ConsoleExtMap[ext]; ok {
		var console db.Console
		if err := database.Where("abbreviation = ?", abbrev).First(&console).Error; err == nil {
			return &console.ID, false
		}
	}

	return nil, false
}

// CheckWritable checks whether the game library directory is writable.
// GET /api/admin/uploads/writable
func (h *UploadHandler) CheckWritable(c *gin.Context) {
	gameDir := h.GameDirs[0]
	tmpPath := filepath.Join(gameDir, ".spela-write-test")
	f, err := os.Create(tmpPath)
	if err != nil {
		c.JSON(http.StatusOK, gin.H{
			"writable": false,
			"reason":   "Game library directory is read-only",
		})
		return
	}
	f.Close()
	os.Remove(tmpPath)
	c.JSON(http.StatusOK, gin.H{"writable": true})
}

// UploadROMs handles multipart file uploads of ROM files to the staging area.
// Zip files are automatically extracted and individual ROMs inside are staged.
// POST /api/admin/uploads
func (h *UploadHandler) UploadROMs(c *gin.Context) {
	if err := os.MkdirAll(h.StagingDir, 0700); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create staging directory"})
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxROMUploadSize)
	form, err := c.MultipartForm()
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "multipart form required"})
		return
	}

	files := form.File["files"]
	if len(files) == 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "at least one file required"})
		return
	}

	var results []StagedUploadResponse

	for _, header := range files {
		ext := strings.ToLower(filepath.Ext(header.Filename))

		// Handle zip files: extract and process contained ROMs individually
		if ext == ".zip" {
			zipResults := h.processZipUpload(header)
			results = append(results, zipResults...)
			continue
		}

		// Validate extension is a recognized ROM type
		if !scanner.RomExtensions[ext] {
			results = append(results, StagedUploadResponse{
				OriginalFileName: header.Filename,
				Status:           "rejected",
			})
			continue
		}

		result := h.stageROMFile(header.Filename, ext, func(destPath string) (int64, error) {
			file, err := header.Open()
			if err != nil {
				return 0, err
			}
			defer file.Close()

			dst, err := os.OpenFile(destPath, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0644)
			if err != nil {
				return 0, err
			}

			written, err := io.Copy(dst, file)
			dst.Close()
			if err != nil {
				os.Remove(destPath)
				return 0, err
			}
			return written, nil
		})
		results = append(results, result)
	}

	c.JSON(http.StatusOK, results)
}

// stageROMFile handles the common logic for staging a single ROM file.
// The writeFn callback writes the file content to the given destPath and returns
// the number of bytes written. The caller must not close the destination file;
// writeFn handles that. If writeFn returns an error, the file at destPath is
// expected to be cleaned up by writeFn.
func (h *UploadHandler) stageROMFile(originalFilename string, ext string, writeFn func(destPath string) (int64, error)) StagedUploadResponse {
	// Sanitize filename
	safeName := filepath.Base(originalFilename)
	if safeName == "." || safeName == ".." {
		safeName = "unnamed" + ext
	}

	// Ensure unique filename in staging
	destPath := filepath.Join(h.StagingDir, safeName)
	if _, err := os.Stat(destPath); err == nil {
		// File already exists, add numeric suffix
		nameNoExt := strings.TrimSuffix(safeName, ext)
		for i := 1; ; i++ {
			destPath = filepath.Join(h.StagingDir, fmt.Sprintf("%s_%d%s", nameNoExt, i, ext))
			if _, err := os.Stat(destPath); os.IsNotExist(err) {
				safeName = fmt.Sprintf("%s_%d%s", nameNoExt, i, ext)
				break
			}
		}
	}

	written, err := writeFn(destPath)
	if err != nil {
		return StagedUploadResponse{
			OriginalFileName: originalFilename,
			Status:           "rejected",
		}
	}

	// Sanitize NES ROM headers (clean dirty padding bytes that cause load failures).
	if ext == ".nes" {
		if result := scanner.SanitizeNESHeader(destPath); result == "sanitized" {
			slog.Info("sanitized dirty iNES header on upload", "file", safeName)
		}
		if err := scanner.ValidateNESHeader(destPath); err != nil {
			slog.Warn("NES ROM header validation failed", "file", safeName, "error", err)
		}
	}

	// Detect console from extension
	consoleID, isAmbiguous := detectConsole(h.DB, ext)

	status := "pending_scrape"
	var possibleConsolesJSON string
	var crc32Str string
	var verificationStatus string
	var canonicalName string
	if isAmbiguous {
		// Try auto-identification via CRC32 + DAT lookup before falling back to pending_console
		if identified, identCRC, identVerification, identCanonical := h.tryIdentifyByDAT(destPath, written); identified != nil {
			consoleID = identified
			crc32Str = identCRC
			verificationStatus = identVerification
			canonicalName = identCanonical
		} else {
			status = "pending_console"
			possibles := consolesForExtension(h.DB, ext)
			if data, err := json.Marshal(possibles); err == nil {
				possibleConsolesJSON = string(data)
			}
		}
	}

	// Create staged upload record
	staged := db.StagedUpload{
		FileName:           safeName,
		OriginalFileName:   originalFilename,
		FilePath:           destPath,
		FileSize:           written,
		ConsoleID:          consoleID,
		PossibleConsoles:   possibleConsolesJSON,
		Status:             status,
		CRC32:              crc32Str,
		VerificationStatus: verificationStatus,
		CanonicalName:      canonicalName,
	}
	if err := h.DB.Create(&staged).Error; err != nil {
		os.Remove(destPath)
		slog.Warn("failed to create staged upload record", "file", safeName, "error", err)
		return StagedUploadResponse{
			OriginalFileName: originalFilename,
			Status:           "rejected",
		}
	}

	return toStagedUploadResponse(staged, h.DB)
}

// processZipUpload extracts a zip file upload and stages each ROM file found inside.
// Non-ROM files inside the zip are silently skipped. Inner .zip files are treated
// as ROM files (libretro cores can load zipped ROMs).
func (h *UploadHandler) processZipUpload(header *multipart.FileHeader) []StagedUploadResponse {
	// Save the zip to a temp file in the staging dir
	tmpZipFile, err := os.CreateTemp(h.StagingDir, "upload-*.zip")
	if err != nil {
		slog.Warn("failed to create temp zip file", "error", err)
		return []StagedUploadResponse{{
			OriginalFileName: header.Filename,
			Status:           "rejected",
		}}
	}
	tmpZipPath := tmpZipFile.Name()
	defer os.Remove(tmpZipPath) // Always clean up the temp zip

	src, err := header.Open()
	if err != nil {
		tmpZipFile.Close()
		slog.Warn("failed to open uploaded zip", "file", header.Filename, "error", err)
		return []StagedUploadResponse{{
			OriginalFileName: header.Filename,
			Status:           "rejected",
		}}
	}

	_, err = io.Copy(tmpZipFile, src)
	src.Close()
	tmpZipFile.Close()
	if err != nil {
		slog.Warn("failed to write uploaded zip to temp file", "file", header.Filename, "error", err)
		return []StagedUploadResponse{{
			OriginalFileName: header.Filename,
			Status:           "rejected",
		}}
	}

	return h.extractAndStageZip(tmpZipPath, header.Filename)
}

// extractAndStageZip opens a zip file, validates it, extracts ROM files, and
// stages each one. Returns a list of StagedUploadResponse entries.
func (h *UploadHandler) extractAndStageZip(zipPath string, originalZipName string) []StagedUploadResponse {
	r, err := zip.OpenReader(zipPath)
	if err != nil {
		slog.Warn("failed to open zip archive", "file", originalZipName, "error", err)
		return []StagedUploadResponse{{
			OriginalFileName: originalZipName,
			Status:           "rejected",
		}}
	}
	defer r.Close()

	// Zip bomb protection: check file count
	if len(r.File) > maxZipFileCount {
		slog.Warn("zip archive exceeds maximum file count", "file", originalZipName, "count", len(r.File), "max", maxZipFileCount)
		return []StagedUploadResponse{{
			OriginalFileName: originalZipName,
			Status:           "rejected",
		}}
	}

	// Zip bomb protection: check total uncompressed size
	var totalUncompressed uint64
	for _, f := range r.File {
		totalUncompressed += f.UncompressedSize64
		if totalUncompressed > maxZipExtractedSize {
			slog.Warn("zip archive exceeds maximum extracted size", "file", originalZipName, "max", maxZipExtractedSize)
			return []StagedUploadResponse{{
				OriginalFileName: originalZipName,
				Status:           "rejected",
			}}
		}
	}

	var results []StagedUploadResponse
	romFound := false
	var totalExtracted int64

	for _, zf := range r.File {
		// Skip directories
		if zf.FileInfo().IsDir() {
			continue
		}

		ext := strings.ToLower(filepath.Ext(zf.Name))

		// Skip files that aren't recognized ROM types.
		// Inner .zip files are treated as ROMs (libretro can load them).
		if !scanner.RomExtensions[ext] {
			continue
		}

		romFound = true

		// Use the base filename from the zip entry (strip directory paths)
		entryBaseName := filepath.Base(zf.Name)

		result := h.stageROMFile(entryBaseName, ext, func(destPath string) (int64, error) {
			zfReader, err := zf.Open()
			if err != nil {
				return 0, fmt.Errorf("opening zip entry %s: %w", zf.Name, err)
			}
			defer zfReader.Close()

			dst, err := os.OpenFile(destPath, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0644)
			if err != nil {
				return 0, err
			}

			// Limit extraction to prevent zip bombs even if uncompressed size was spoofed.
			// Use remaining budget so cumulative extraction is bounded.
			remaining := int64(maxZipExtractedSize) - totalExtracted
			if remaining <= 0 {
				dst.Close()
				os.Remove(destPath)
				return 0, fmt.Errorf("zip extraction exceeded maximum total size")
			}
			limitedReader := io.LimitReader(zfReader, remaining)
			written, err := io.Copy(dst, limitedReader)
			dst.Close()
			if err != nil {
				os.Remove(destPath)
				return 0, fmt.Errorf("extracting zip entry %s: %w", zf.Name, err)
			}
			totalExtracted += written
			return written, nil
		})
		results = append(results, result)
	}

	if !romFound {
		return []StagedUploadResponse{{
			OriginalFileName: originalZipName,
			Status:           "rejected",
		}}
	}

	return results
}

// ListUploads returns all staged uploads.
// GET /api/admin/uploads
func (h *UploadHandler) ListUploads(c *gin.Context) {
	var uploads []db.StagedUpload
	if err := h.DB.Order("created_at desc").Find(&uploads).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch uploads"})
		return
	}

	results := make([]StagedUploadResponse, len(uploads))
	for i, u := range uploads {
		results[i] = toStagedUploadResponse(u, h.DB)
	}
	c.JSON(http.StatusOK, results)
}

// SetConsole sets the console for an ambiguous staged upload.
// POST /api/admin/uploads/:id/console
func (h *UploadHandler) SetConsole(c *gin.Context) {
	id := c.Param("id")
	var staged db.StagedUpload
	if err := h.DB.First(&staged, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "upload not found"})
		return
	}

	var req struct {
		ConsoleID string `json:"consoleId" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "consoleId required"})
		return
	}

	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", req.ConsoleID).First(&console).Error; err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "unknown console"})
		return
	}

	staged.ConsoleID = &console.ID
	staged.Status = "pending_scrape"
	if err := h.DB.Save(&staged).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update upload"})
		return
	}

	c.JSON(http.StatusOK, toStagedUploadResponse(staged, h.DB))
}

// ScrapeUpload triggers scraping for a single staged upload.
// POST /api/admin/uploads/:id/scrape
func (h *UploadHandler) ScrapeUpload(c *gin.Context) {
	id := c.Param("id")
	var staged db.StagedUpload
	if err := h.DB.First(&staged, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "upload not found"})
		return
	}

	if staged.ConsoleID == nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "console must be set before scraping"})
		return
	}

	h.scrapeStaged(&staged)

	c.JSON(http.StatusOK, toStagedUploadResponse(staged, h.DB))
}

// ScrapeAllUploads triggers scraping for all uploads pending scrape.
// POST /api/admin/uploads/scrape
func (h *UploadHandler) ScrapeAllUploads(c *gin.Context) {
	var uploads []db.StagedUpload
	if err := h.DB.Where("status = ?", "pending_scrape").Find(&uploads).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch uploads"})
		return
	}

	for i := range uploads {
		h.scrapeStaged(&uploads[i])
	}

	results := make([]StagedUploadResponse, len(uploads))
	for i, u := range uploads {
		results[i] = toStagedUploadResponse(u, h.DB)
	}
	c.JSON(http.StatusOK, results)
}

// tryIdentifyByDAT attempts to identify the console for a ROM file by computing its CRC32
// and searching all applicable No-Intro DAT indices, then Redump indices for disc-based systems.
// Returns the console ID if a match is found, along with CRC32, verification status, and canonical name.
// Returns nil if no match.
func (h *UploadHandler) tryIdentifyByDAT(filePath string, fileSize int64) (consoleID *uint, crc string, verificationStatus string, canonicalName string) {
	if h.Scraper == nil || h.Scraper.DATCache == nil {
		return nil, "", "", ""
	}

	crc, err := scraper.ComputeFileCRC32(filePath)
	if err != nil {
		slog.Warn("failed to compute CRC32 for auto-identification", "file", filePath, "error", err)
		return nil, "", "", ""
	}

	// Try No-Intro DATs for cartridge-based systems
	for abbrev := range scraper.AbbreviationToLibRetro {
		if scraper.DiscBasedSystems[abbrev] {
			continue
		}
		maxSize, ok := scraper.MaxROMSize[abbrev]
		if !ok {
			continue
		}
		if fileSize > maxSize {
			continue
		}

		idx, err := h.Scraper.DATCache.GetIndex(abbrev)
		if err != nil || idx == nil {
			continue
		}

		entry, ok := idx.LookupCRC(crc)
		if !ok {
			continue
		}

		// Verify size matches for safety
		if entry.Size != 0 && entry.Size != fileSize {
			continue
		}

		// Found a match — resolve console ID from DB
		var console db.Console
		if err := h.DB.Where("abbreviation = ?", abbrev).First(&console).Error; err != nil {
			continue
		}

		slog.Info("auto-identified ROM via CRC32+No-Intro DAT", "file", filePath, "crc", crc, "console", abbrev, "canonical", entry.ROMName)
		return &console.ID, crc, "verified", entry.ROMName
	}

	// Try Redump DATs for disc-based systems
	for abbrev := range scraper.AbbreviationToRedump {
		idx, err := h.Scraper.DATCache.GetRedumpIndex(abbrev)
		if err != nil || idx == nil {
			continue
		}

		entry, ok := idx.LookupCRC(crc)
		if !ok {
			continue
		}

		// Verify size matches for safety
		if entry.Size != 0 && entry.Size != fileSize {
			continue
		}

		// Found a match — resolve console ID from DB
		var console db.Console
		if err := h.DB.Where("abbreviation = ?", abbrev).First(&console).Error; err != nil {
			continue
		}

		slog.Info("auto-identified ROM via CRC32+Redump DAT", "file", filePath, "crc", crc, "console", abbrev, "canonical", entry.ROMName)
		return &console.ID, crc, "verified", entry.ROMName
	}

	return nil, "", "", ""
}

// scrapeStaged performs metadata lookup and duplicate detection for a staged upload.
func (h *UploadHandler) scrapeStaged(staged *db.StagedUpload) {
	var console db.Console
	if staged.ConsoleID == nil {
		return
	}
	if err := h.DB.First(&console, *staged.ConsoleID).Error; err != nil {
		slog.Warn("failed to load console for staged upload", "id", staged.ID, "error", err)
		return
	}

	// Set title from original filename (not the deduplicated on-disk name)
	staged.Title = scanner.GameTitle(staged.OriginalFileName)

	// CRC32 verification for cartridge-based systems.
	// Skip recomputation if CRC was already set during auto-identification.
	if staged.CRC32 != "" && staged.VerificationStatus == "verified" {
		// Already identified via tryIdentifyByDAT — update title from canonical name
		if staged.CanonicalName != "" {
			staged.Title = scanner.GameTitle(staged.CanonicalName)
		}
	} else if scraper.DiscBasedSystems[console.Abbreviation] {
		// Disc-based system: try Redump DAT verification if available
		staged.VerificationStatus = "not_applicable"
		if h.Scraper != nil && h.Scraper.DATCache != nil {
			if crc, err := scraper.ComputeFileCRC32(staged.FilePath); err == nil {
				staged.CRC32 = crc
				if idx, err := h.Scraper.DATCache.GetRedumpIndex(console.Abbreviation); err == nil && idx != nil {
					if entry, ok := idx.LookupCRC(crc); ok {
						staged.VerificationStatus = "verified"
						staged.CanonicalName = entry.ROMName
						staged.Title = scanner.GameTitle(entry.ROMName)
					}
				}
			}
		}
	} else {
		if crc, err := scraper.ComputeFileCRC32(staged.FilePath); err == nil {
			staged.CRC32 = crc
			staged.VerificationStatus = "unverified"

			// Check No-Intro DAT for canonical name
			if h.Scraper != nil && h.Scraper.DATCache != nil {
				if idx, err := h.Scraper.DATCache.GetIndex(console.Abbreviation); err == nil && idx != nil {
					if entry, ok := idx.LookupCRC(crc); ok {
						staged.VerificationStatus = "verified"
						staged.CanonicalName = entry.ROMName
						staged.Title = scanner.GameTitle(entry.ROMName)
					}
				}
			}
		}
	}

	// Duplicate detection: check by CRC32 or filename+console
	if staged.CRC32 != "" {
		var existingGame db.Game
		if err := h.DB.Where("crc32 = ? AND crc32 != ''", staged.CRC32).First(&existingGame).Error; err == nil {
			staged.DuplicateOfGameID = &existingGame.ID
			staged.Status = "duplicate"
			h.DB.Save(staged)
			return
		}
	}
	var existingGame db.Game
	if err := h.DB.Where("file_name = ? AND console_id = ?", staged.FileName, *staged.ConsoleID).First(&existingGame).Error; err == nil {
		staged.DuplicateOfGameID = &existingGame.ID
		staged.Status = "duplicate"
		h.DB.Save(staged)
		return
	}

	staged.Status = "ready"
	h.DB.Save(staged)
}

// AcceptUpload moves a staged ROM to the library and creates a Game record.
// POST /api/admin/uploads/:id/accept
func (h *UploadHandler) AcceptUpload(c *gin.Context) {
	id := c.Param("id")
	var staged db.StagedUpload
	if err := h.DB.First(&staged, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "upload not found"})
		return
	}

	if staged.ConsoleID == nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "console must be set before accepting"})
		return
	}

	game, err := h.acceptStaged(&staged)
	if err != nil {
		slog.Warn("failed to accept upload", "id", id, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to accept upload"})
		return
	}

	userID := getUserID(c)
	h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").First(game, game.ID)
	c.JSON(http.StatusOK, ToGameResponse(*game, h.DB, userID))
}

// acceptStaged performs the actual accept: move file, create Game record, delete staged record.
func (h *UploadHandler) acceptStaged(staged *db.StagedUpload) (*db.Game, error) {
	var console db.Console
	if err := h.DB.First(&console, *staged.ConsoleID).Error; err != nil {
		return nil, fmt.Errorf("loading console: %w", err)
	}

	// Determine target directory
	gameDir := h.GameDirs[0]
	targetDir := filepath.Join(gameDir, console.FolderName)
	if err := os.MkdirAll(targetDir, 0755); err != nil {
		return nil, fmt.Errorf("creating target directory: %w", err)
	}

	// Determine target filename (use canonical name if verified)
	targetName := staged.FileName
	if staged.VerificationStatus == "verified" && staged.CanonicalName != "" {
		targetName = staged.CanonicalName
	}

	targetPath := filepath.Join(targetDir, targetName)

	// Move file from staging to library
	if err := os.Rename(staged.FilePath, targetPath); err != nil {
		// Rename might fail across filesystems, try copy+delete
		if err := copyFile(staged.FilePath, targetPath); err != nil {
			return nil, fmt.Errorf("moving file: %w", err)
		}
		os.Remove(staged.FilePath)
	}

	// Create Game record
	title := staged.Title
	if title == "" {
		title = scanner.GameTitle(targetName)
	}

	game := db.Game{
		ConsoleID:          console.ID,
		Title:              title,
		FileName:           targetName,
		FilePath:           filepath.Join(console.FolderName, targetName),
		FileSize:           staged.FileSize,
		Description:        staged.Description,
		CoverURL:           staged.CoverURL,
		Developer:          staged.Developer,
		Publisher:          staged.Publisher,
		ReleaseDate:        staged.ReleaseDate,
		Genre:              staged.Genre,
		Players:            staged.Players,
		Rating:             staged.Rating,
		VerificationStatus: staged.VerificationStatus,
		CRC32:              staged.CRC32,
	}
	if err := h.DB.Create(&game).Error; err != nil {
		return nil, fmt.Errorf("creating game record: %w", err)
	}

	// Delete staged upload record
	h.DB.Delete(&staged)

	return &game, nil
}

// RejectUpload deletes a staged ROM from the staging area.
// POST /api/admin/uploads/:id/reject
func (h *UploadHandler) RejectUpload(c *gin.Context) {
	id := c.Param("id")
	var staged db.StagedUpload
	if err := h.DB.First(&staged, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "upload not found"})
		return
	}

	os.Remove(staged.FilePath)
	h.DB.Delete(&staged)

	c.JSON(http.StatusOK, gin.H{"message": "upload rejected"})
}

// AcceptAllUploads accepts all non-duplicate staged uploads.
// POST /api/admin/uploads/accept-all
func (h *UploadHandler) AcceptAllUploads(c *gin.Context) {
	var uploads []db.StagedUpload
	if err := h.DB.Where("status IN ? AND console_id IS NOT NULL", []string{"ready", "pending_scrape"}).Find(&uploads).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch uploads"})
		return
	}

	var accepted int
	for i := range uploads {
		if _, err := h.acceptStaged(&uploads[i]); err != nil {
			slog.Warn("failed to accept staged upload", "id", uploads[i].ID, "error", err)
			continue
		}
		accepted++
	}

	c.JSON(http.StatusOK, gin.H{"accepted": accepted})
}

// RejectAllUploads rejects all staged uploads.
// POST /api/admin/uploads/reject-all
func (h *UploadHandler) RejectAllUploads(c *gin.Context) {
	var uploads []db.StagedUpload
	if err := h.DB.Find(&uploads).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch uploads"})
		return
	}

	for _, u := range uploads {
		os.Remove(u.FilePath)
	}
	h.DB.Where("1 = 1").Delete(&db.StagedUpload{})

	c.JSON(http.StatusOK, gin.H{"rejected": len(uploads)})
}

// ClearStaging deletes all staged uploads and files.
// DELETE /api/admin/uploads
func (h *UploadHandler) ClearStaging(c *gin.Context) {
	var uploads []db.StagedUpload
	if err := h.DB.Find(&uploads).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch uploads"})
		return
	}

	for _, u := range uploads {
		os.Remove(u.FilePath)
	}
	h.DB.Where("1 = 1").Delete(&db.StagedUpload{})

	c.JSON(http.StatusOK, gin.H{"cleared": len(uploads)})
}

// copyFile copies a file from src to dst.
func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()

	if _, err := io.Copy(out, in); err != nil {
		return err
	}
	return out.Close()
}
