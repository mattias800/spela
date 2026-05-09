package api

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/bios"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/patcher"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// --- Inputs ------------------------------------------------------------------

// AdminUploadBiosBody is the multipart body for POST /api/admin/bios. The
// file field is marked optional so the handler can emit "file upload required"
// (matching the gin handler) instead of huma's generic 422 validation error.
type AdminUploadBiosBody struct {
	File huma.FormFile `form:"file" required:"false" contentType:"application/octet-stream" doc:"BIOS file to upload (max 16 MB)."`
}

// AdminUploadBiosInput wraps the multipart body for the BIOS upload operation.
type AdminUploadBiosInput struct {
	RawBody huma.MultipartFormFiles[AdminUploadBiosBody]
}

// AdminUploadBiosOutput wraps the BIOS upload result.
type AdminUploadBiosOutput struct {
	Body BiosFileResponse
}

// AdminReplaceROMBody is the multipart body for PUT
// /api/admin/games/{id}/replace-rom. File is marked optional so the handler
// can return "file required in 'file' field" instead of huma's 422.
type AdminReplaceROMBody struct {
	File huma.FormFile `form:"file" required:"false" contentType:"application/octet-stream" doc:"Replacement ROM file (or .zip containing one). Max 50 GB."`
}

// AdminReplaceROMInput wraps the path parameter and multipart body.
type AdminReplaceROMInput struct {
	ID      string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Numeric game ID."`
	RawBody huma.MultipartFormFiles[AdminReplaceROMBody]
}

// AdminReplaceROMOutput wraps the replace-rom result.
type AdminReplaceROMOutput struct {
	Body ReplaceROMResponse
}

// AdminCreateRomHackBody is the multipart body for POST /api/admin/rom-hacks.
// All fields are marked optional in the schema so the handler can return the
// existing conditional-validation error messages (e.g. "baseGameId is
// required", "mode must be 'variant' or 'standalone'") instead of huma's
// generic "required form parameter is missing" 422 response.
//
// Form-field names are camelCase to keep the API consistent with the rest of
// the surface and to work around an openapi-generator-kotlin bug that emits
// snake_case identifier references inside the generated formData{} block,
// breaking compilation when a FormFile field name contains underscores.
type AdminCreateRomHackBody struct {
	BaseGameID string        `form:"baseGameId" required:"false" doc:"Numeric ID of the base game whose ROM the patch applies to."`
	Mode       string        `form:"mode" required:"false" doc:"'variant' (groups with the base game) or 'standalone' (creates an independent game)."`
	Label      string        `form:"label" required:"false" doc:"Variant label (required when mode='variant'). Appears in brackets after the base title."`
	Title      string        `form:"title" required:"false" doc:"Standalone title (required when mode='standalone')."`
	PatchFile  huma.FormFile `form:"patchFile" required:"false" contentType:"application/octet-stream" doc:"Patch file (.ips, .bps, .ups, .xdelta, .vcdiff). Max 100 MB."`
}

// AdminCreateRomHackInput wraps the multipart body.
type AdminCreateRomHackInput struct {
	RawBody huma.MultipartFormFiles[AdminCreateRomHackBody]
}

// AdminCreateRomHackResponse mirrors the historical gin response body.
type AdminCreateRomHackResponse struct {
	ID    string `json:"id" doc:"Newly-created game ID (numeric, encoded as string)."`
	Title string `json:"title" doc:"Newly-created game title."`
}

// AdminCreateRomHackOutput wraps the rom-hack creation result, with a 201
// Created status to match the gin handler.
type AdminCreateRomHackOutput struct {
	Body AdminCreateRomHackResponse
}

// AdminUploadROMsBody is the multipart body for POST /api/admin/uploads.
// Files is marked optional so the handler emits "at least one file required"
// (matching the gin handler) on empty submissions instead of a huma 422.
type AdminUploadROMsBody struct {
	Files []huma.FormFile `form:"files" required:"false" contentType:"application/octet-stream" doc:"One or more ROM files (or .zip archives containing ROMs). Each archive is extracted and its ROMs are staged individually."`
}

// AdminUploadROMsInput wraps the multipart body.
type AdminUploadROMsInput struct {
	RawBody huma.MultipartFormFiles[AdminUploadROMsBody]
}

// AdminUploadROMsOutput wraps the staged-upload result list. The body shape is
// `[]StagedUploadResponse` directly, matching the gin response 1:1.
type AdminUploadROMsOutput struct {
	Body []StagedUploadResponse
}

// --- Registration ------------------------------------------------------------

// RegisterAdminMultipartRoutes wires the four admin file-upload endpoints into
// the huma API. These were previously the last raw-gin admin routes; moving
// them lets the entire /api/admin tree appear in the OpenAPI spec and be
// callable from the generated TypeScript and Kotlin clients.
//
// Each operation is composed with the same admin middleware stack the other
// huma admin endpoints use (RequireAuth → UserRateLimit → RequireAdmin) plus
// MaxBodyBytes set to the same per-endpoint ceiling the gin handlers enforce
// via http.MaxBytesReader.
func RegisterAdminMultipartRoutes(
	api huma.API,
	biosH *BiosHandler,
	gameH *GameHandler,
	romHackH *RomHackHandler,
	uploadH *UploadHandler,
	jwtSecret string,
	database *gorm.DB,
	userLimiter *RateLimiter,
	uploadLimiter *RateLimiter,
) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	uploadRL := UserRateLimitMiddleware(uploadLimiter)
	requireAdmin := RequireAdmin(api)
	adminMW := huma.Middlewares{requireAuth, rateLimit, requireAdmin}
	uploadMW := huma.Middlewares{requireAuth, uploadRL, requireAdmin}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID:   "adminUploadBios",
		Method:        http.MethodPost,
		Path:          "/api/admin/bios",
		Summary:       "Upload a BIOS file",
		Description:   "Admin-only. Stores the uploaded file in the BIOS directory and matches it against the registry by filename, falling back to MD5 lookup. Returns the resulting BIOS file metadata. Max 16 MB.",
		Tags:          []string{"admin"},
		Middlewares:   adminMW,
		Security:      sec,
		MaxBodyBytes:  maxBiosUploadSize,
	}, biosH.HumaUploadBiosFile)

	huma.Register(api, huma.Operation{
		OperationID:   "adminReplaceROM",
		Method:        http.MethodPut,
		Path:          "/api/admin/games/{id}/replace-rom",
		Summary:       "Replace a game's ROM",
		Description:   "Admin-only. Replaces the existing ROM file for the given game and re-verifies it against the DAT registry. Accepts a raw ROM file or a .zip containing one.",
		Tags:          []string{"admin"},
		Middlewares:   uploadMW,
		Security:      sec,
		MaxBodyBytes:  maxROMUploadSize,
	}, gameH.HumaReplaceROM)

	huma.Register(api, huma.Operation{
		OperationID:   "adminCreateRomHack",
		Method:        http.MethodPost,
		Path:          "/api/admin/rom-hacks",
		Summary:       "Create a ROM hack from a patch file",
		Description:   "Admin-only. Applies the supplied patch file to a base game's ROM and creates a new game record. In 'variant' mode the new game shares the base game's group and inherits its metadata; in 'standalone' mode it gets its own group with the base game as parent.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"admin"},
		Middlewares:   uploadMW,
		Security:      sec,
		MaxBodyBytes:  maxPatchUploadSize,
	}, romHackH.HumaCreateRomHack)

	huma.Register(api, huma.Operation{
		OperationID:   "adminUploadROMs",
		Method:        http.MethodPost,
		Path:          "/api/admin/uploads",
		Summary:       "Stage ROM file uploads",
		Description:   "Admin-only. Accepts one or more ROM files (or .zip archives containing ROMs) and stages them for review. Each archive is extracted and its individual ROMs are staged separately. Returns one StagedUploadResponse per resulting file.",
		Tags:          []string{"admin"},
		Middlewares:   adminMW,
		Security:      sec,
		MaxBodyBytes:  maxROMUploadSize,
	}, uploadH.HumaUploadROMs)
}

// --- Handlers ----------------------------------------------------------------

// HumaUploadBiosFile is the huma implementation of POST /api/admin/bios.
// Mirrors the gin handler 1:1: writes the uploaded file to the BIOS directory,
// matches it against the registry first by filename then by MD5 (auto-renaming
// when the MD5 matches a known canonical name), moves it into the registry's
// SubDir if any, and returns the resulting BiosFileResponse.
func (h *BiosHandler) HumaUploadBiosFile(_ context.Context, in *AdminUploadBiosInput) (*AdminUploadBiosOutput, error) {
	body := in.RawBody.Data()
	file := body.File
	if !file.IsSet {
		return nil, huma.NewError(http.StatusBadRequest, "file upload required")
	}
	defer file.Close()
	if file.Size > maxBiosUploadSize {
		return nil, huma.NewError(http.StatusRequestEntityTooLarge, "file too large, maximum 16 MB")
	}

	// Sanitize filename via storage helper.
	safePath := h.Storage.BiosFilePath(file.Filename)
	safeName := filepath.Base(safePath)

	dst, err := os.Create(safePath)
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "failed to save file")
	}
	defer dst.Close()

	written, err := io.Copy(dst, file)
	if err != nil {
		os.Remove(safePath)
		return nil, huma.NewError(http.StatusInternalServerError, "failed to save file")
	}

	fileMD5, _ := computeFileMD5(safePath)
	names := consoleNameMap(h.DB)
	matches := bios.ByFileName(safeName)

	// If filename didn't match, try MD5-based lookup and auto-rename.
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
		if match.SubDir != "" {
			subDirPath := match.FilePath(h.Storage.BiosDir)
			if err := os.MkdirAll(filepath.Dir(subDirPath), 0755); err != nil {
				_ = os.Remove(safePath)
				return nil, huma.Error500InternalServerError("failed to create BIOS subdirectory: " + err.Error())
			}
			if err := os.Rename(safePath, subDirPath); err != nil {
				// Cross-device or other rename failure leaves the file at
				// safePath (top-level BIOS dir) where libretro cores can't
				// find it. Surface the error rather than silently misplacing
				// the upload.
				_ = os.Remove(safePath)
				return nil, huma.Error500InternalServerError("failed to move BIOS file to subdirectory: " + err.Error())
			}
			safePath = subDirPath
		}
		consoleName := names[match.ConsoleID]
		consoleID := match.ConsoleID
		desc := match.Description
		resp.ConsoleID = &consoleID
		resp.ConsoleName = &consoleName
		resp.Description = &desc
		resp.Required = match.Required
		switch {
		case match.MD5 == "":
			resp.Status = "present"
		case fileMD5 == match.MD5:
			resp.Status = "valid"
		default:
			// Issue #1124(A): hash mismatch — delete the upload so a
			// libretro core that doesn't strictly check BIOS hash at
			// load time cannot boot with attacker-supplied bytes.
			_ = os.Remove(safePath)
			return nil, huma.Error400BadRequest(fmt.Sprintf("BIOS file hash mismatch (expected %s, got %s); upload discarded",
				match.MD5, fileMD5))
		}
	}

	return &AdminUploadBiosOutput{Body: resp}, nil
}

// HumaReplaceROM is the huma implementation of PUT
// /api/admin/games/{id}/replace-rom. Mirrors the gin handler: writes the
// uploaded file to a temp location, optionally extracts the first ROM from a
// zip, computes CRC32, looks up the canonical name in the DAT cache, copies
// the new ROM into place, updates the game record, and returns the result.
func (h *GameHandler) HumaReplaceROM(ctx context.Context, in *AdminReplaceROMInput) (*AdminReplaceROMOutput, error) {
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, in.ID).Error; err != nil {
		return nil, huma.NewError(http.StatusNotFound, "game not found")
	}

	body := in.RawBody.Data()
	file := body.File
	if !file.IsSet {
		return nil, huma.NewError(http.StatusBadRequest, "file required in 'file' field")
	}
	defer file.Close()

	ext := strings.ToLower(filepath.Ext(file.Filename))

	tmpFile, err := os.CreateTemp(os.TempDir(), "replace-rom-*"+ext)
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "failed to create temp file")
	}
	tmpPath := tmpFile.Name()
	defer os.Remove(tmpPath)

	written, err := io.Copy(tmpFile, file)
	tmpFile.Close()
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "failed to write uploaded file")
	}

	var romPath string
	var romExt string
	var romSize int64
	var extractedTmpPath string

	if ext == ".zip" {
		extractedPath, extractedExt, extractedSize, extractErr := extractFirstROMFromZip(tmpPath, maxROMUploadSize)
		if extractErr != nil {
			return nil, huma.NewError(http.StatusBadRequest, extractErr.Error())
		}
		romPath = extractedPath
		romExt = extractedExt
		romSize = extractedSize
		extractedTmpPath = extractedPath
		defer os.Remove(extractedTmpPath)
	} else {
		if !scanner.RomExtensions[ext] {
			return nil, huma.NewError(http.StatusBadRequest, "unrecognized ROM file extension: "+ext)
		}
		romPath = tmpPath
		romExt = ext
		romSize = written
	}

	crc, err := scraper.ComputeFileCRC32(romPath)
	if err != nil {
		slog.Warn("failed to compute CRC32 for replacement ROM", "error", err)
		return nil, huma.NewError(http.StatusInternalServerError, "failed to compute CRC32")
	}

	previousStatus := game.VerificationStatus
	previousCRC32 := game.CRC32

	verified := false
	canonicalName := ""
	newVerificationStatus := "unverified"

	if h.Scraper != nil && h.Scraper.DATCache != nil {
		consoleAbbrev := game.Console.Abbreviation
		if scraper.VerificationSkipSystems[consoleAbbrev] {
			newVerificationStatus = "not_applicable"
			if idx, err := h.Scraper.DATCache.GetRedumpIndex(consoleAbbrev); err == nil && idx != nil {
				if entry, ok := idx.LookupCRC(crc); ok {
					verified = true
					canonicalName = entry.ROMName
					newVerificationStatus = "verified"
				}
			}
		} else {
			if idx, err := h.Scraper.DATCache.GetIndex(consoleAbbrev); err == nil && idx != nil {
				if entry, ok := idx.LookupCRC(crc); ok {
					verified = true
					canonicalName = entry.ROMName
					newVerificationStatus = "verified"
				}
			}
		}
	}

	oldAbsPath, err := storage.ResolveGamePath(game.FilePath, h.GameDirs)
	if err != nil {
		slog.Warn("could not resolve existing game file path", "filePath", game.FilePath, "error", err)
		return nil, huma.NewError(http.StatusInternalServerError, "failed to locate existing ROM file")
	}

	targetDir := filepath.Dir(oldAbsPath)
	targetName := game.FileName
	if verified && canonicalName != "" {
		targetName = canonicalName
		if filepath.Ext(canonicalName) == "" {
			targetName = canonicalName + romExt
		}
	}
	targetPath := filepath.Join(targetDir, targetName)

	if err := copyFile(romPath, targetPath); err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "failed to write replacement ROM")
	}

	game.CRC32 = crc
	game.VerificationStatus = newVerificationStatus
	game.FileName = targetName
	game.FilePath = filepath.Join(game.Console.FolderName, targetName)
	game.FileSize = romSize

	if err := h.DB.Save(&game).Error; err != nil {
		if targetPath != oldAbsPath {
			os.Remove(targetPath)
		}
		return nil, huma.NewError(http.StatusInternalServerError, "failed to update game record")
	}

	if targetPath != oldAbsPath {
		if err := os.Remove(oldAbsPath); err != nil {
			slog.Warn("failed to remove old ROM file after replacement", "path", oldAbsPath, "error", err)
		}
	}

	h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").First(&game, game.ID)

	// userID is needed by ToGameResponse to compute per-user fields like
	// favorite/last-played. Read it from the huma context (set by RequireAuth).
	uid := UserIDFromContext(ctx)

	return &AdminReplaceROMOutput{Body: ReplaceROMResponse{
		Game: ToGameResponse(game, h.DB, uid),
		ReplacementResult: ReplaceROMResult{
			Verified:       verified,
			CRC32:          crc,
			CanonicalName:  canonicalName,
			PreviousStatus: previousStatus,
			PreviousCRC32:  previousCRC32,
		},
	}}, nil
}

// HumaCreateRomHack is the huma implementation of POST /api/admin/rom-hacks.
// Reads the patch file + form fields, applies the patch to the base game's
// ROM, writes the patched ROM to the games directory, creates a new Game
// record, and returns its ID and title. Mode-specific validation (label
// required for 'variant', title required for 'standalone') matches the gin
// handler exactly.
func (h *RomHackHandler) HumaCreateRomHack(_ context.Context, in *AdminCreateRomHackInput) (*AdminCreateRomHackOutput, error) {
	body := in.RawBody.Data()

	if body.BaseGameID == "" {
		return nil, huma.NewError(http.StatusBadRequest, "baseGameId is required")
	}
	if _, err := strconv.ParseUint(body.BaseGameID, 10, 64); err != nil {
		return nil, huma.NewError(http.StatusBadRequest, "baseGameId must be a valid numeric ID")
	}

	mode := body.Mode
	if mode != "variant" && mode != "standalone" {
		return nil, huma.NewError(http.StatusBadRequest, "mode must be 'variant' or 'standalone'")
	}

	if mode == "variant" && body.Label == "" {
		return nil, huma.NewError(http.StatusBadRequest, "label is required for variant mode")
	}
	if mode == "standalone" && body.Title == "" {
		return nil, huma.NewError(http.StatusBadRequest, "title is required for standalone mode")
	}

	patchFile := body.PatchFile
	if !patchFile.IsSet {
		return nil, huma.NewError(http.StatusBadRequest, "patchFile is required")
	}
	defer patchFile.Close()

	patchExt := strings.ToLower(filepath.Ext(patchFile.Filename))
	supportedExts := map[string]bool{".ips": true, ".bps": true, ".ups": true, ".xdelta": true, ".vcdiff": true}
	if !supportedExts[patchExt] {
		return nil, huma.NewError(http.StatusBadRequest, "unsupported patch format: "+patchExt+"; supported: .ips, .bps, .ups, .xdelta")
	}

	patchData, err := io.ReadAll(patchFile)
	if err != nil {
		return nil, huma.NewError(http.StatusBadRequest, "failed to read patch file")
	}

	var baseGame db.Game
	if err := h.DB.Preload("Console").First(&baseGame, body.BaseGameID).Error; err != nil {
		return nil, huma.NewError(http.StatusNotFound, "base game not found")
	}

	romAbsPath, err := storage.ResolveGamePath(baseGame.FilePath, h.GameDirs)
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "base game ROM file not found on disk")
	}
	// Issue #1125: defense in depth. ResolveGamePath now refuses
	// traversal, but ValidateROMPath here matches the pattern used by
	// HumaDownloadGame / HumaReplaceROM so any future regression in
	// ResolveGamePath doesn't immediately turn into arbitrary file read.
	if !storage.ValidateROMPath(romAbsPath, h.GameDirs) {
		return nil, huma.NewError(http.StatusForbidden, "base game ROM path is outside configured game dirs")
	}

	// Issue #1134(A): refuse to apply a patch to a multi-GB base ROM.
	// os.ReadFile loads the full file into memory before patching, and
	// the patcher's MaxOutputSize is 256 MB — patching a 4 GB ISO peaks
	// at ~4 GB+ and OOM-kills small hosts. Reject up front using the
	// patcher's own MaxOutputSize as the ceiling so the in-memory
	// behaviour is consistent end-to-end.
	if info, err := os.Stat(romAbsPath); err == nil {
		if info.Size() > patcher.MaxOutputSize {
			return nil, huma.NewError(http.StatusRequestEntityTooLarge,
				fmt.Sprintf("base ROM is %d bytes; patcher refuses inputs larger than %d bytes (issue #1134)", info.Size(), patcher.MaxOutputSize))
		}
	}

	romData, err := os.ReadFile(romAbsPath)
	if err != nil {
		slog.Warn("failed to read base ROM", "path", romAbsPath, "error", err)
		return nil, huma.NewError(http.StatusInternalServerError, "failed to read base game ROM")
	}

	patchedData, err := patcher.Apply(romData, patchData, patchFile.Filename)
	if err != nil {
		slog.Info("patch application failed", "baseGame", baseGame.Title, "patchFile", patchFile.Filename, "error", err)
		return nil, huma.NewError(http.StatusBadRequest, fmt.Sprintf("patch failed: %s", err))
	}

	romExt := filepath.Ext(baseGame.FileName)
	var patchedFileName string
	if mode == "variant" {
		baseName := strings.TrimSuffix(baseGame.FileName, romExt)
		patchedFileName = baseName + " [" + body.Label + "]" + romExt
	} else {
		patchedFileName = sanitizePatchedFilename(body.Title) + romExt
	}

	targetDir := filepath.Join(h.GameDirs[0], baseGame.Console.FolderName)
	if err := os.MkdirAll(targetDir, 0755); err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "failed to create target directory")
	}

	targetPath := filepath.Join(targetDir, patchedFileName)
	if _, err := os.Stat(targetPath); err == nil {
		return nil, huma.NewError(http.StatusConflict, "a ROM file with this name already exists: "+patchedFileName)
	}

	// Atomic write: a server crash between os.WriteFile starting and
	// completing would otherwise leave a partial ROM at targetPath, and
	// the next admin request for the same name hits the os.Stat conflict
	// check above and gets locked into 409 Conflict permanently.
	tmpPath := targetPath + ".tmp"
	if err := os.WriteFile(tmpPath, patchedData, 0644); err != nil {
		slog.Warn("failed to write patched ROM tmp file", "path", tmpPath, "error", err)
		return nil, huma.NewError(http.StatusInternalServerError, "failed to write patched ROM file")
	}
	if err := os.Rename(tmpPath, targetPath); err != nil {
		_ = os.Remove(tmpPath)
		slog.Warn("failed to rename patched ROM into place", "path", targetPath, "error", err)
		return nil, huma.NewError(http.StatusInternalServerError, "failed to write patched ROM file")
	}

	relPath := filepath.Join(baseGame.Console.FolderName, patchedFileName)

	var newGame db.Game
	if mode == "variant" {
		variantTitle := baseGame.Title + " [" + body.Label + "]"
		tags := "hack"
		if baseGame.Tags != "" {
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
		parentID := baseGame.ID
		groupKey := scanner.NormalizeGroupKey(body.Title)
		newGame = db.Game{
			ConsoleID:    baseGame.ConsoleID,
			Title:        body.Title,
			FileName:     patchedFileName,
			FilePath:     relPath,
			FileSize:     int64(len(patchedData)),
			GroupKey:     groupKey,
			ParentGameID: &parentID,
			IsPrimary:    true,
		}
	}

	if err := h.DB.Create(&newGame).Error; err != nil {
		os.Remove(targetPath)
		slog.Warn("failed to create game record for ROM hack", "error", err)
		return nil, huma.NewError(http.StatusInternalServerError, "failed to create game record")
	}

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

	return &AdminCreateRomHackOutput{Body: AdminCreateRomHackResponse{
		ID:    strconv.FormatUint(uint64(newGame.ID), 10),
		Title: newGame.Title,
	}}, nil
}

// HumaUploadROMs is the huma implementation of POST /api/admin/uploads.
// Stages each uploaded file into the staging directory, extracting any .zip
// archives along the way. Each resulting ROM gets a console hint (or list of
// possible consoles when ambiguous) and is queued for metadata scraping.
// Returns one StagedUploadResponse per resulting file.
func (h *UploadHandler) HumaUploadROMs(_ context.Context, in *AdminUploadROMsInput) (*AdminUploadROMsOutput, error) {
	if err := os.MkdirAll(h.StagingDir, 0700); err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "failed to create staging directory")
	}

	files := in.RawBody.Data().Files
	if len(files) == 0 {
		return nil, huma.NewError(http.StatusBadRequest, "at least one file required")
	}

	results := make([]StagedUploadResponse, 0, len(files))
	for _, f := range files {
		if !f.IsSet {
			continue
		}
		ext := strings.ToLower(filepath.Ext(f.Filename))

		if ext == ".zip" {
			zipResults := h.processZipUploadFormFile(f)
			results = append(results, zipResults...)
			continue
		}

		if !scanner.RomExtensions[ext] {
			results = append(results, StagedUploadResponse{
				OriginalFileName: f.Filename,
				Status:           "rejected",
				PossibleConsoles: []PossibleConsoleResponse{},
			})
			f.Close()
			continue
		}

		// Capture the file in a closure that copies its contents to the
		// staging path. stageROMFile owns lifecycle of destPath on error.
		file := f
		result := h.stageROMFile(f.Filename, ext, func(destPath string) (int64, error) {
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

	return &AdminUploadROMsOutput{Body: results}, nil
}
