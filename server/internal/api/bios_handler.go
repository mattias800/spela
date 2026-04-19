package api

import (
	"crypto/md5"
	"encoding/hex"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"github.com/gin-gonic/gin"
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

		// Check if file exists: flat first, then subdirectory path
		fullPath := e.FilePath(h.Storage.BiosDir)
		info, onDisk := diskFiles[e.FileName]
		if !onDisk && e.SubDir != "" {
			// Check subdirectory path
			if fi, err := os.Stat(fullPath); err == nil {
				info = fi
				onDisk = true
			}
		}
		if !onDisk {
			fileResponses = append(fileResponses, BiosFileResponse{
				Name:        e.FileName,
				Size:        0,
				MD5:         e.MD5,
				SubDir:      e.SubDir,
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
		fileMD5, err := computeFileMD5(fullPath)
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
			SubDir:      e.SubDir,
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

			fullPath := e.FilePath(h.Storage.BiosDir)
			_, onDisk := diskFiles[e.FileName]
			if !onDisk && e.SubDir != "" {
				if _, err := os.Stat(fullPath); err == nil {
					onDisk = true
				}
			}
			status := "missing"
			if onDisk {
				fileMD5, err := computeFileMD5(fullPath)
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
				SubDir:      e.SubDir,
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

// GetBiosFile has been migrated to huma — see HumaDownloadBios in
// huma_downloads.go.

// UploadBiosFile has been migrated to huma — see HumaUploadBiosFile in
// huma_admin_multipart.go.

// DeleteBiosFile deletes a BIOS file from disk (admin only).
func (h *BiosHandler) DeleteBiosFile(c *gin.Context) {
	filename := c.Param("filename")
	path := h.Storage.BiosFilePath(filename)

	if _, err := os.Stat(path); os.IsNotExist(err) {
		// Check subdirectory paths for entries with SubDir
		for _, e := range bios.ByFileName(filename) {
			if e.SubDir != "" {
				subPath := e.FilePath(h.Storage.BiosDir)
				if _, serr := os.Stat(subPath); serr == nil {
					path = subPath
					break
				}
			}
		}
		if _, err := os.Stat(path); os.IsNotExist(err) {
			c.JSON(http.StatusNotFound, ErrorResponse{Error: "bios file not found"})
			return
		}
	}

	// Validate the resolved path stays within BiosDir (same check as GetBiosFile)
	absPath, err := filepath.Abs(path)
	if err != nil {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "access denied"})
		return
	}
	absBiosDir, err := filepath.Abs(h.Storage.BiosDir)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "internal error"})
		return
	}
	if realPath, e := filepath.EvalSymlinks(absPath); e == nil {
		absPath = realPath
	}
	if realDir, e := filepath.EvalSymlinks(absBiosDir); e == nil {
		absBiosDir = realDir
	}
	if !strings.HasPrefix(absPath, absBiosDir+string(filepath.Separator)) {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "access denied"})
		return
	}

	if err := os.Remove(absPath); err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to delete file"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "file deleted"})
}

// TriggerDownload starts a background download of missing BIOS files (admin only).
// Only one download can run at a time; concurrent requests are rejected.
func (h *BiosHandler) TriggerDownload(c *gin.Context) {
	h.downloadMu.Lock()
	if h.downloading {
		h.downloadMu.Unlock()
		c.JSON(http.StatusConflict, ErrorResponse{Error: "A BIOS download is already in progress"})
		return
	}
	h.downloading = true
	h.downloadMu.Unlock()

	// Count missing files for the response
	entries := bios.Downloadable()
	missing := 0
	for _, e := range entries {
		path := e.FilePath(h.Storage.BiosDir)
		if _, err := os.Stat(path); os.IsNotExist(err) {
			missing++
		}
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventBiosDownloadStarted, Payload: ws.BiosDownloadStartedPayload{Total: missing}})
	}

	go func() {
		defer func() {
			h.downloadMu.Lock()
			h.downloading = false
			h.downloadMu.Unlock()
		}()

		result := bios.DownloadMissing(h.Storage.BiosDir, bios.DefaultRepoBaseURL, func(p bios.DownloadProgress) {
			if h.Hub != nil {
				h.Hub.Broadcast(ws.Event{Type: ws.EventBiosDownloadProgress, Payload: p})
			}
		})

		slog.Info("BIOS manual download complete",
			"downloaded", result.Downloaded,
			"skipped", result.Skipped,
			"failed", result.Failed,
		)

		if h.Hub != nil {
			h.Hub.Broadcast(ws.Event{Type: ws.EventBiosDownloadComplete, Payload: ws.BiosDownloadCompletePayload{
				Downloaded: result.Downloaded,
				Skipped:    result.Skipped,
				Failed:     result.Failed,
			}})
		}
	}()

	adminID, _ := c.Get("userId")
	slog.Info("audit: admin triggered BIOS download", "admin_id", adminID, "missing", missing)
	c.JSON(http.StatusAccepted, gin.H{"message": "BIOS download started in background", "missing": missing})
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
