package api

import (
	"crypto/md5"
	"encoding/hex"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/bios"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

const maxBiosUploadSize = 16 << 20 // 16 MB

// BiosHandler handles BIOS file management endpoints.
type BiosHandler struct {
	Storage *storage.Storage
	DB      *gorm.DB
}

// BiosFileResponse represents an individual BIOS file in the response.
type BiosFileResponse struct {
	Name        string  `json:"name"`
	Size        int64   `json:"size"`
	MD5         string  `json:"md5"`
	ExpectedMD5 string  `json:"expectedMd5,omitempty"`
	ConsoleID   *string `json:"consoleId"`
	ConsoleName *string `json:"consoleName,omitempty"`
	Description *string `json:"description,omitempty"`
	Required    bool    `json:"required"`
	Status      string  `json:"status"` // "valid", "present", "invalid", "missing"
}

// ConsoleFileStatus represents a single BIOS file within a console summary.
type ConsoleFileStatus struct {
	FileName    string `json:"fileName"`
	Description string `json:"description"`
	Required    bool   `json:"required"`
	MD5         string `json:"md5"`
	Status      string `json:"status"` // "valid", "present", "invalid", "missing"
}

// ConsoleBiosStatus represents the BIOS status summary for one console.
type ConsoleBiosStatus struct {
	ConsoleID       string              `json:"consoleId"`
	ConsoleName     string              `json:"consoleName"`
	BiosRequired    bool                `json:"biosRequired"`
	Status          string              `json:"status"` // "ready", "missing", "invalid", "not_required"
	RequiredPresent int                 `json:"requiredPresent"`
	RequiredTotal   int                 `json:"requiredTotal"`
	OptionalPresent int                 `json:"optionalPresent"`
	OptionalTotal   int                 `json:"optionalTotal"`
	Files           []ConsoleFileStatus `json:"files"`
}

// BiosListResponse is the top-level response for GET /api/bios.
type BiosListResponse struct {
	Files    []BiosFileResponse  `json:"files"`
	Consoles []ConsoleBiosStatus `json:"consoles"`
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

// ListBiosFiles returns enriched BIOS file data with per-file metadata and
// per-console summary status. Note: computes MD5 for every file on disk per
// request. Fine for the typical ~15 small BIOS files; consider caching if the
// directory grows significantly.
func (h *BiosHandler) ListBiosFiles(c *gin.Context) {
	names := consoleNameMap(h.DB)
	allEntries := bios.All()

	// Read files on disk
	diskFiles := make(map[string]os.FileInfo)
	entries, err := os.ReadDir(h.Storage.BiosDir)
	if err == nil {
		for _, entry := range entries {
			if entry.IsDir() {
				continue
			}
			info, err := entry.Info()
			if err != nil {
				continue
			}
			diskFiles[entry.Name()] = info
		}
	}

	// Track which disk files are matched to registry entries
	matchedFiles := make(map[string]bool)

	// Build per-file responses for registry entries
	var fileResponses []BiosFileResponse
	for _, e := range allEntries {
		consoleName := names[e.ConsoleID]
		consoleID := e.ConsoleID
		desc := e.Description

		info, onDisk := diskFiles[e.FileName]
		if !onDisk {
			fileResponses = append(fileResponses, BiosFileResponse{
				Name:        e.FileName,
				Size:        0,
				MD5:         e.MD5,
				ConsoleID:   &consoleID,
				ConsoleName: &consoleName,
				Description: &desc,
				Required:    e.Required,
				Status:      "missing",
			})
			continue
		}

		matchedFiles[e.FileName] = true

		// Compute MD5 of the file on disk
		fileMD5, err := computeFileMD5(filepath.Join(h.Storage.BiosDir, e.FileName))
		status := "present"
		if err == nil {
			if e.MD5 == "" {
				// No known checksum in registry — accept the file as-is
				status = "present"
			} else if fileMD5 == e.MD5 {
				status = "valid"
			} else {
				status = "invalid"
			}
		}

		fileResponses = append(fileResponses, BiosFileResponse{
			Name:        e.FileName,
			Size:        info.Size(),
			MD5:         fileMD5,
			ConsoleID:   &consoleID,
			ConsoleName: &consoleName,
			Description: &desc,
			Required:    e.Required,
			Status:      status,
		})
	}

	// Add unknown files (on disk but not in registry)
	for name, info := range diskFiles {
		if matchedFiles[name] {
			continue
		}
		fileMD5, _ := computeFileMD5(filepath.Join(h.Storage.BiosDir, name))
		fileResponses = append(fileResponses, BiosFileResponse{
			Name:     name,
			Size:     info.Size(),
			MD5:      fileMD5,
			Required: false,
			Status:   "present",
		})
	}

	// Build per-console summaries
	consoleOrder := bios.ConsoleIDs()
	var consoleSummaries []ConsoleBiosStatus
	for _, cid := range consoleOrder {
		cEntries := bios.ByConsole(cid)
		consoleName := names[cid]

		hasRequired := false
		requiredTotal := 0
		requiredPresent := 0
		optionalTotal := 0
		optionalPresent := 0
		hasInvalidRequired := false
		hasMissingRequired := false

		var consoleFiles []ConsoleFileStatus
		for _, e := range cEntries {
			if e.Required {
				hasRequired = true
				requiredTotal++
			} else {
				optionalTotal++
			}

			_, onDisk := diskFiles[e.FileName]
			status := "missing"
			if onDisk {
				fileMD5, err := computeFileMD5(filepath.Join(h.Storage.BiosDir, e.FileName))
				if err == nil && e.MD5 == "" {
					status = "present"
				} else if err == nil && fileMD5 == e.MD5 {
					status = "valid"
				} else if err == nil {
					status = "invalid"
				} else {
					status = "present"
				}

				if e.Required {
					if status == "valid" || status == "present" {
						requiredPresent++
					} else if status == "invalid" {
						hasInvalidRequired = true
					}
				} else {
					if status != "missing" {
						optionalPresent++
					}
				}
			} else {
				if e.Required {
					hasMissingRequired = true
				}
			}

			consoleFiles = append(consoleFiles, ConsoleFileStatus{
				FileName:    e.FileName,
				Description: e.Description,
				Required:    e.Required,
				MD5:         e.MD5,
				Status:      status,
			})
		}

		consoleStatus := "not_required"
		if hasRequired {
			if hasMissingRequired {
				consoleStatus = "missing"
			} else if hasInvalidRequired {
				consoleStatus = "invalid"
			} else {
				consoleStatus = "ready"
			}
		}

		consoleSummaries = append(consoleSummaries, ConsoleBiosStatus{
			ConsoleID:       cid,
			ConsoleName:     consoleName,
			BiosRequired:    hasRequired,
			Status:          consoleStatus,
			RequiredPresent: requiredPresent,
			RequiredTotal:   requiredTotal,
			OptionalPresent: optionalPresent,
			OptionalTotal:   optionalTotal,
			Files:           consoleFiles,
		})
	}

	c.JSON(http.StatusOK, BiosListResponse{
		Files:    fileResponses,
		Consoles: consoleSummaries,
	})
}

// GetBiosFile serves a BIOS file by filename.
func (h *BiosHandler) GetBiosFile(c *gin.Context) {
	filename := c.Param("filename")
	path := h.Storage.BiosFilePath(filename)

	if _, err := os.Stat(path); os.IsNotExist(err) {
		c.JSON(http.StatusNotFound, gin.H{"error": "bios file not found"})
		return
	}

	// Validate the resolved path stays within BiosDir
	absPath, err := filepath.Abs(path)
	if err != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
		return
	}
	absBiosDir, err := filepath.Abs(h.Storage.BiosDir)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "internal error"})
		return
	}
	if _, statErr := os.Stat(absPath); statErr == nil {
		realPath, evalErr := filepath.EvalSymlinks(absPath)
		if evalErr != nil {
			c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
			return
		}
		absPath = realPath
	}
	if realDir, e := filepath.EvalSymlinks(absBiosDir); e == nil {
		absBiosDir = realDir
	}
	if !strings.HasPrefix(absPath, absBiosDir+string(filepath.Separator)) {
		c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
		return
	}

	c.File(absPath)
}

// UploadBiosFile handles multipart file upload for BIOS files (admin only).
func (h *BiosHandler) UploadBiosFile(c *gin.Context) {
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxBiosUploadSize)

	file, header, err := c.Request.FormFile("file")
	if err != nil {
		if err.Error() == "http: request body too large" {
			c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "file too large, maximum 16 MB"})
			return
		}
		c.JSON(http.StatusBadRequest, gin.H{"error": "file upload required"})
		return
	}
	defer file.Close()

	if header.Size > maxBiosUploadSize {
		c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "file too large, maximum 16 MB"})
		return
	}

	// Sanitize filename via storage helper
	safePath := h.Storage.BiosFilePath(header.Filename)
	safeName := filepath.Base(safePath)

	// Write the file
	dst, err := os.Create(safePath)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
		return
	}
	defer dst.Close()

	written, err := io.Copy(dst, file)
	if err != nil {
		os.Remove(safePath)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
		return
	}

	// Compute MD5
	fileMD5, _ := computeFileMD5(safePath)

	// Look up in registry: first by filename, then by MD5
	names := consoleNameMap(h.DB)
	matches := bios.ByFileName(safeName)

	// If filename didn't match, try MD5-based lookup and auto-rename
	if len(matches) == 0 {
		if md5Match := bios.ByMD5(fileMD5); md5Match != nil {
			canonicalPath := h.Storage.BiosFilePath(md5Match.FileName)
			if err := os.Rename(safePath, canonicalPath); err == nil {
				safePath = canonicalPath
				safeName = md5Match.FileName
			}
			matches = []bios.Entry{*md5Match}
		}
	}

	resp := BiosFileResponse{
		Name:     safeName,
		Size:     written,
		MD5:      fileMD5,
		Required: false,
		Status:   "present",
	}

	if len(matches) > 0 {
		match := matches[0]
		consoleName := names[match.ConsoleID]
		consoleID := match.ConsoleID
		desc := match.Description
		resp.ConsoleID = &consoleID
		resp.ConsoleName = &consoleName
		resp.Description = &desc
		resp.Required = match.Required
		if match.MD5 == "" {
			// No known checksum in registry — accept the file as-is
			resp.Status = "present"
		} else if fileMD5 == match.MD5 {
			resp.Status = "valid"
		} else {
			resp.Status = "invalid"
			resp.ExpectedMD5 = match.MD5
		}
	}

	c.JSON(http.StatusOK, resp)
}

// DeleteBiosFile deletes a BIOS file from disk (admin only).
func (h *BiosHandler) DeleteBiosFile(c *gin.Context) {
	filename := c.Param("filename")
	path := h.Storage.BiosFilePath(filename)

	if _, err := os.Stat(path); os.IsNotExist(err) {
		c.JSON(http.StatusNotFound, gin.H{"error": "bios file not found"})
		return
	}

	// Validate the resolved path stays within BiosDir (same check as GetBiosFile)
	absPath, err := filepath.Abs(path)
	if err != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
		return
	}
	absBiosDir, err := filepath.Abs(h.Storage.BiosDir)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "internal error"})
		return
	}
	if realPath, e := filepath.EvalSymlinks(absPath); e == nil {
		absPath = realPath
	}
	if realDir, e := filepath.EvalSymlinks(absBiosDir); e == nil {
		absBiosDir = realDir
	}
	if !strings.HasPrefix(absPath, absBiosDir+string(filepath.Separator)) {
		c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
		return
	}

	if err := os.Remove(absPath); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to delete file"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "file deleted"})
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

		filePath := filepath.Join(biosDir, e.FileName)
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
