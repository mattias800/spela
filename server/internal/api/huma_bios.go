package api

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/bios"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// --- Input / output types ---

// ListBiosFilesInput is the input for GET /api/bios.
type ListBiosFilesInput struct{}

// ListBiosFilesOutput wraps the BIOS listing response.
type ListBiosFilesOutput struct {
	Body BiosListResponse
}

// DeleteBiosFileInput is the input for DELETE /api/admin/bios/{filename}.
type DeleteBiosFileInput struct {
	Filename string `path:"filename" doc:"BIOS file name."`
}

// DeleteBiosFileOutput wraps the delete success message.
type DeleteBiosFileOutput struct {
	Body MessageResponse
}

// TriggerBiosDownloadInput is the input for POST /api/admin/bios/download.
type TriggerBiosDownloadInput struct{}

// BiosDownloadStartedResponse is the wire format for BIOS download start.
type BiosDownloadStartedResponse struct {
	Message string `json:"message"`
	Missing int    `json:"missing"`
}

// TriggerBiosDownloadOutput wraps the started response (202).
type TriggerBiosDownloadOutput struct {
	Body BiosDownloadStartedResponse
}

// RegisterBiosRoutes wires the BIOS (non-file) endpoints into the huma API.
// ListBiosFiles is available to any authenticated user; admin endpoints require RequireAdmin.
// The raw file-download (GetBiosFile) and upload (UploadBiosFile) endpoints stay on gin.
func RegisterBiosRoutes(api huma.API, h *BiosHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	requireAdmin := RequireAdmin(api)
	mw := huma.Middlewares{requireAuth, rateLimit}
	adminMW := huma.Middlewares{requireAuth, rateLimit, requireAdmin}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "listBiosFiles",
		Method:      http.MethodGet,
		Path:        "/api/bios",
		Summary:     "List BIOS files",
		Description: "Returns registry-driven BIOS file status plus per-console summaries.",
		Tags:        []string{"bios"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListBiosFiles)

	huma.Register(api, huma.Operation{
		OperationID: "deleteBiosFile",
		Method:      http.MethodDelete,
		Path:        "/api/admin/bios/{filename}",
		Summary:     "Delete a BIOS file",
		Description: "Admin-only. Removes a BIOS file from disk.",
		Tags:        []string{"admin", "bios"},
		Middlewares: adminMW,
		Security:    sec,
	}, h.HumaDeleteBiosFile)

	huma.Register(api, huma.Operation{
		OperationID:   "triggerBiosDownload",
		Method:        http.MethodPost,
		Path:          "/api/admin/bios/download",
		Summary:       "Trigger BIOS download",
		Description:   "Admin-only. Starts a background download of missing registry-registered BIOS files.",
		DefaultStatus: http.StatusAccepted,
		Tags:          []string{"admin", "bios"},
		Middlewares:   adminMW,
		Security:      sec,
	}, h.HumaTriggerBiosDownload)
}

// --- Handlers ---

// HumaListBiosFiles is the huma handler for GET /api/bios. It re-uses the
// gin ListBiosFiles logic by materialising the response in-process without
// writing to a gin context.
func (h *BiosHandler) HumaListBiosFiles(_ context.Context, _ *ListBiosFilesInput) (*ListBiosFilesOutput, error) {
	names := consoleNameMap(h.DB)
	allEntries := bios.All()

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

	matchedFiles := make(map[string]bool)

	fileResponses := make([]BiosFileResponse, 0, len(allEntries))
	for _, e := range allEntries {
		consoleName := names[e.ConsoleID]
		consoleID := e.ConsoleID
		desc := e.Description

		fullPath := e.FilePath(h.Storage.BiosDir)
		info, onDisk := diskFiles[e.FileName]
		if !onDisk && e.SubDir != "" {
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
				Bundle:      e.Bundle,
			})
			continue
		}

		matchedFiles[e.FileName] = true

		fileMD5, err := computeFileMD5(fullPath)
		status := "present"
		if err == nil {
			if e.MD5 == "" {
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
			Bundle:      e.Bundle,
		})
	}

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

	consoleOrder := bios.ConsoleIDs()
	consoleSummaries := make([]ConsoleBiosStatus, 0, len(consoleOrder))
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

		consoleFiles := make([]ConsoleFileStatus, 0, len(cEntries))
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
				Bundle:      e.Bundle,
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

	return &ListBiosFilesOutput{Body: BiosListResponse{
		Files:    fileResponses,
		Consoles: consoleSummaries,
	}}, nil
}

// HumaDeleteBiosFile is the huma handler for DELETE /api/admin/bios/{filename}.
func (h *BiosHandler) HumaDeleteBiosFile(_ context.Context, in *DeleteBiosFileInput) (*DeleteBiosFileOutput, error) {
	filename := in.Filename
	path := h.Storage.BiosFilePath(filename)

	if _, err := os.Stat(path); os.IsNotExist(err) {
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
			return nil, huma.Error404NotFound("bios file not found")
		}
	}

	absPath, err := filepath.Abs(path)
	if err != nil {
		return nil, huma.Error403Forbidden("access denied")
	}
	absBiosDir, err := filepath.Abs(h.Storage.BiosDir)
	if err != nil {
		return nil, huma.Error500InternalServerError("internal error")
	}
	if realPath, e := filepath.EvalSymlinks(absPath); e == nil {
		absPath = realPath
	}
	if realDir, e := filepath.EvalSymlinks(absBiosDir); e == nil {
		absBiosDir = realDir
	}
	if len(absPath) < len(absBiosDir)+1 || absPath[:len(absBiosDir)+1] != absBiosDir+string(filepath.Separator) {
		return nil, huma.Error403Forbidden("access denied")
	}

	if err := os.Remove(absPath); err != nil {
		return nil, huma.Error500InternalServerError("failed to delete file")
	}

	return &DeleteBiosFileOutput{Body: MessageResponse{Message: "file deleted"}}, nil
}

// HumaTriggerBiosDownload is the huma handler for POST /api/admin/bios/download.
func (h *BiosHandler) HumaTriggerBiosDownload(ctx context.Context, _ *TriggerBiosDownloadInput) (*TriggerBiosDownloadOutput, error) {
	h.downloadMu.Lock()
	if h.downloading {
		h.downloadMu.Unlock()
		return nil, huma.Error409Conflict("A BIOS download is already in progress")
	}
	h.downloading = true
	h.downloadMu.Unlock()

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

	adminID := UserIDFromContext(ctx)
	slog.Info("audit: admin triggered BIOS download", "admin_id", adminID, "missing", missing)
	return &TriggerBiosDownloadOutput{Body: BiosDownloadStartedResponse{
		Message: "BIOS download started in background",
		Missing: missing,
	}}, nil
}
