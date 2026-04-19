package api

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/bios"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// streamFileFromDisk returns a huma.StreamResponse that copies the file at
// absPath to the response body after setting Content-Disposition (attachment
// with the provided download name) and Content-Type. If the file can't be
// opened at stream time, it writes a 500 status with no body — callers are
// expected to validate path existence before returning this, so this path
// covers transient failures only (file deleted between validation and
// streaming).
func streamFileFromDisk(absPath, downloadName, contentType string) *huma.StreamResponse {
	return &huma.StreamResponse{
		Body: func(hctx huma.Context) {
			f, err := os.Open(absPath)
			if err != nil {
				hctx.SetStatus(http.StatusInternalServerError)
				return
			}
			defer f.Close()
			if contentType != "" {
				hctx.SetHeader("Content-Type", contentType)
			}
			hctx.SetHeader("Content-Disposition", fmt.Sprintf("attachment; filename=%q", downloadName))
			_, _ = io.Copy(hctx.BodyWriter(), f)
		},
	}
}

// streamBytesInline returns a huma.StreamResponse that writes the given bytes
// inline (no Content-Disposition — the browser renders them directly,
// typical for console icons / logos). Used by endpoints that serve embedded
// assets from the binary.
func streamBytesInline(data []byte, contentType string) *huma.StreamResponse {
	return &huma.StreamResponse{
		Body: func(hctx huma.Context) {
			hctx.SetHeader("Content-Type", contentType)
			hctx.SetHeader("Cache-Control", "public, max-age=604800")
			_, _ = hctx.BodyWriter().Write(data)
		},
	}
}

// --- Inputs ------------------------------------------------------------------

// CoreDownloadInput is the input for GET /api/cores/{id}/download.
type CoreDownloadInput struct {
	ID       string `path:"id" doc:"Core ID."`
	Platform string `query:"platform" required:"false" default:"linux" doc:"Target platform: linux, macos, windows, android."`
}

// BiosDownloadInput is the input for GET /api/bios/{filename}.
type BiosDownloadInput struct {
	Filename string `path:"filename" doc:"BIOS file name."`
}

// SessionSaveDownloadInput is the input for GET /api/sessions/{id}/saves/{saveId}.
type SessionSaveDownloadInput struct {
	ID     string `path:"id" doc:"Session ID."`
	SaveID string `path:"saveId" doc:"Save state ID."`
}

// SessionAutoSaveDownloadInput is the input for GET /api/sessions/{id}/saves/auto.
type SessionAutoSaveDownloadInput struct {
	ID string `path:"id" doc:"Session ID."`
}

// SessionSlotSaveDownloadInput is the input for GET /api/sessions/{id}/saves/slot/{slot}.
type SessionSlotSaveDownloadInput struct {
	ID   string `path:"id" doc:"Session ID."`
	Slot string `path:"slot" doc:"Save slot number 1-10."`
}

// SessionSRAMDownloadInput is the input for GET /api/sessions/{id}/sram.
type SessionSRAMDownloadInput struct {
	ID string `path:"id" doc:"Session ID."`
}

// SharedSaveDownloadInput is the input for GET /api/games/{id}/shared-saves/{saveId}/download.
type SharedSaveDownloadInput struct {
	ID     string `path:"id" doc:"Game ID."`
	SaveID string `path:"saveId" doc:"Shared save state ID."`
}

// SharedSessionSaveDownloadInput is the input for
// GET /api/shared-sessions/{id}/saves/{saveId}.
type SharedSessionSaveDownloadInput struct {
	ID     string `path:"id" doc:"Shared session ID."`
	SaveID string `path:"saveId" doc:"Shared session save ID."`
}

// SharedSessionAutoSaveDownloadInput is the input for
// GET /api/shared-sessions/{id}/saves/auto.
type SharedSessionAutoSaveDownloadInput struct {
	ID string `path:"id" doc:"Shared session ID."`
}

// ConsoleAssetInput is the input for the public console-image endpoints
// (icon / logo / logo.png / preview-screenshot). The id resolves a console
// by abbreviation or code.
type ConsoleAssetInput struct {
	ID string `path:"id" doc:"Console abbreviation or code."`
}

// BrandingLogoInput is the empty input for GET /api/branding/logo.
type BrandingLogoInput struct{}

// --- Registration ------------------------------------------------------------

// RegisterDownloadRoutes wires the non-wildcard binary download endpoints
// into the huma API. These previously lived on raw gin because gin's
// c.File() pattern doesn't have a 1:1 counterpart in huma; we rebuild the
// pattern on top of huma.StreamResponse. The OpenAPI spec will carry these
// as octet-stream responses so generated clients know they exist, though the
// response body is still opaque bytes (type safety applies to paths, not
// payloads).
//
// The wildcard image route /api/images/*filepath stays on raw gin because
// OpenAPI 3.1 path parameters are single-segment by design and the image
// paths legitimately contain slashes (console/screenshot sub-paths).
func RegisterDownloadRoutes(
	api huma.API,
	coreH *CoreHandler,
	biosH *BiosHandler,
	sessionH *SessionHandler,
	sharedSaveH *SharedSaveHandler,
	sharedSessionH *SharedSessionHandler,
	consoleH *ConsoleHandler,
	jwtSecret string,
	database *gorm.DB,
	userLimiter *RateLimiter,
	downloadLimiter *RateLimiter,
) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	downloadRL := UserRateLimitMiddleware(downloadLimiter)
	authedMW := huma.Middlewares{requireAuth, rateLimit}
	authedDownloadMW := huma.Middlewares{requireAuth, rateLimit, downloadRL}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "downloadCore",
		Method:      http.MethodGet,
		Path:        "/api/cores/{id}/download",
		Summary:     "Download a core binary",
		Description: "Serves the core binary for the requested platform (defaults to linux). Responds with application/octet-stream.",
		Tags:        []string{"cores"},
		Middlewares: authedMW,
		Security:    sec,
	}, coreH.HumaDownloadCore)

	huma.Register(api, huma.Operation{
		OperationID: "downloadBios",
		Method:      http.MethodGet,
		Path:        "/api/bios/{filename}",
		Summary:     "Download a BIOS file",
		Description: "Serves the BIOS file with the given name, resolving registry-declared subdirectories when needed. Responds with application/octet-stream.",
		Tags:        []string{"bios"},
		Middlewares: authedMW,
		Security:    sec,
	}, biosH.HumaDownloadBios)

	huma.Register(api, huma.Operation{
		OperationID: "downloadSessionSave",
		Method:      http.MethodGet,
		Path:        "/api/sessions/{id}/saves/{saveId}",
		Summary:     "Download a session save state",
		Description: "Owner-only. Responds with application/octet-stream.",
		Tags:        []string{"sessions"},
		Middlewares: authedMW,
		Security:    sec,
	}, sessionH.HumaDownloadSessionSave)

	huma.Register(api, huma.Operation{
		OperationID: "downloadSessionAutoSave",
		Method:      http.MethodGet,
		Path:        "/api/sessions/{id}/saves/auto",
		Summary:     "Download the latest auto-save of a session",
		Description: "Owner-only. Responds with application/octet-stream; 404 when no auto-save has been recorded yet.",
		Tags:        []string{"sessions"},
		Middlewares: authedMW,
		Security:    sec,
	}, sessionH.HumaDownloadAutoSave)

	huma.Register(api, huma.Operation{
		OperationID: "downloadSessionSlotSave",
		Method:      http.MethodGet,
		Path:        "/api/sessions/{id}/saves/slot/{slot}",
		Summary:     "Download a session slot save",
		Description: "Owner-only. Slot is 1-10. Responds with application/octet-stream.",
		Tags:        []string{"sessions"},
		Middlewares: authedMW,
		Security:    sec,
	}, sessionH.HumaDownloadSlotSave)

	huma.Register(api, huma.Operation{
		OperationID: "downloadSessionSRAM",
		Method:      http.MethodGet,
		Path:        "/api/sessions/{id}/sram",
		Summary:     "Download SRAM data for a session",
		Description: "Owner-only. Responds with application/octet-stream.",
		Tags:        []string{"sessions"},
		Middlewares: authedMW,
		Security:    sec,
	}, sessionH.HumaDownloadSRAM)

	huma.Register(api, huma.Operation{
		OperationID: "downloadSharedSave",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/shared-saves/{saveId}/download",
		Summary:     "Download a community shared save",
		Description: "Any authenticated user can download. Responds with application/octet-stream.",
		Tags:        []string{"shared-saves"},
		Middlewares: authedDownloadMW,
		Security:    sec,
	}, sharedSaveH.HumaDownloadSharedSave)

	huma.Register(api, huma.Operation{
		OperationID: "downloadSharedSessionSave",
		Method:      http.MethodGet,
		Path:        "/api/shared-sessions/{id}/saves/{saveId}",
		Summary:     "Download a save from a shared session",
		Description: "Member-only. Responds with application/octet-stream.",
		Tags:        []string{"shared-sessions"},
		Middlewares: authedMW,
		Security:    sec,
	}, sharedSessionH.HumaDownloadSharedSessionSave)

	huma.Register(api, huma.Operation{
		OperationID: "downloadSharedSessionAutoSave",
		Method:      http.MethodGet,
		Path:        "/api/shared-sessions/{id}/saves/auto",
		Summary:     "Download the current auto-save of a shared session",
		Description: "Member-only. Responds with application/octet-stream.",
		Tags:        []string{"shared-sessions"},
		Middlewares: authedMW,
		Security:    sec,
	}, sharedSessionH.HumaDownloadSharedSessionAutoSave)

	// Public console-image endpoints. No auth, no rate-limit group —
	// matches the gin routes these replace (loaded by <img> tags).
	huma.Register(api, huma.Operation{
		OperationID: "getConsoleIcon",
		Method:      http.MethodGet,
		Path:        "/api/consoles/{id}/icon",
		Summary:     "Get a console icon (PNG)",
		Description: "Serves an embedded PNG icon for the console. Public endpoint; cached aggressively.",
		Tags:        []string{"consoles"},
	}, consoleH.HumaGetConsoleIcon)

	huma.Register(api, huma.Operation{
		OperationID: "getConsoleLogo",
		Method:      http.MethodGet,
		Path:        "/api/consoles/{id}/logo",
		Summary:     "Get a console logo (SVG)",
		Description: "Serves an embedded SVG logo for the console with inlined class→attribute styling so renderers without CSS support (e.g. Coil on JVM) display colors correctly.",
		Tags:        []string{"consoles"},
	}, consoleH.HumaGetConsoleLogo)

	huma.Register(api, huma.Operation{
		OperationID: "getConsoleLogoPng",
		Method:      http.MethodGet,
		Path:        "/api/consoles/{id}/logo.png",
		Summary:     "Get a console logo (pre-rendered PNG)",
		Description: "Serves a pre-rendered PNG version of the console logo, for clients that can't or don't want to rasterize SVG at runtime.",
		Tags:        []string{"consoles"},
	}, consoleH.HumaGetConsoleLogoPng)

	huma.Register(api, huma.Operation{
		OperationID: "getConsolePreviewScreenshot",
		Method:      http.MethodGet,
		Path:        "/api/consoles/{id}/preview-screenshot",
		Summary:     "Get a representative screenshot for a console",
		Description: "Returns a canonical screenshot from the LibRetro thumbnails CDN, cached locally after the first download. Redirects to /api/images/previews/{abbr}/preview.png when cached.",
		Tags:        []string{"consoles"},
	}, consoleH.HumaGetPreviewScreenshot)

	huma.Register(api, huma.Operation{
		OperationID: "getBrandingLogo",
		Method:      http.MethodGet,
		Path:        "/api/branding/logo",
		Summary:     "Get the Spela branding logo",
		Description: "Serves the embedded spela-logo.png branding asset.",
		Tags:        []string{"branding"},
	}, humaGetBrandingLogo)
}

// --- Handlers ----------------------------------------------------------------

// HumaDownloadCore is the huma implementation of GET /api/cores/{id}/download.
func (h *CoreHandler) HumaDownloadCore(_ context.Context, in *CoreDownloadInput) (*huma.StreamResponse, error) {
	var core db.Core
	if err := h.DB.First(&core, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("core not found")
	}

	platform := in.Platform
	if platform == "" {
		platform = "linux"
	}

	corePath := h.resolveCorePath(core, platform)
	if corePath == "" {
		return nil, huma.Error404NotFound("core binary not available")
	}

	if h.CoreDir == "" || !storage.ValidateROMPath(corePath, []string{h.CoreDir}) {
		return nil, huma.Error403Forbidden("core file access denied")
	}

	ext := platformExtension(platform)
	return streamFileFromDisk(corePath, core.Name+"_libretro"+ext, "application/octet-stream"), nil
}

// HumaDownloadBios is the huma implementation of GET /api/bios/{filename}.
func (h *BiosHandler) HumaDownloadBios(_ context.Context, in *BiosDownloadInput) (*huma.StreamResponse, error) {
	path := h.Storage.BiosFilePath(in.Filename)
	if _, err := os.Stat(path); os.IsNotExist(err) {
		for _, e := range bios.ByFileName(in.Filename) {
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
	if _, statErr := os.Stat(absPath); statErr == nil {
		realPath, evalErr := filepath.EvalSymlinks(absPath)
		if evalErr != nil {
			return nil, huma.Error403Forbidden("access denied")
		}
		absPath = realPath
	}
	if realDir, e := filepath.EvalSymlinks(absBiosDir); e == nil {
		absBiosDir = realDir
	}
	if !strings.HasPrefix(absPath, absBiosDir+string(filepath.Separator)) {
		return nil, huma.Error403Forbidden("access denied")
	}

	return streamFileFromDisk(absPath, filepath.Base(absPath), "application/octet-stream"), nil
}

// HumaDownloadSessionSave is the huma implementation of GET
// /api/sessions/{id}/saves/{saveId}.
func (h *SessionHandler) HumaDownloadSessionSave(ctx context.Context, in *SessionSaveDownloadInput) (*huma.StreamResponse, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var save db.SessionSaveState
	if err := h.DB.Where("id = ? AND session_id = ?", in.SaveID, session.ID).First(&save).Error; err != nil {
		return nil, huma.Error404NotFound("save not found")
	}
	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		return nil, huma.Error403Forbidden("file access denied")
	}
	return streamFileFromDisk(save.FilePath, filepath.Base(save.FilePath), "application/octet-stream"), nil
}

// HumaDownloadAutoSave is the huma implementation of GET
// /api/sessions/{id}/saves/auto.
func (h *SessionHandler) HumaDownloadAutoSave(ctx context.Context, in *SessionAutoSaveDownloadInput) (*huma.StreamResponse, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var save db.SessionSaveState
	if err := h.DB.Where("session_id = ? AND is_auto = ?", session.ID, true).
		Preload("User").
		Order("created_at DESC").
		First(&save).Error; err != nil {
		return nil, huma.Error404NotFound("no auto-save found")
	}
	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		return nil, huma.Error403Forbidden("file access denied")
	}
	return streamFileFromDisk(save.FilePath, filepath.Base(save.FilePath), "application/octet-stream"), nil
}

// HumaDownloadSlotSave is the huma implementation of GET
// /api/sessions/{id}/saves/slot/{slot}.
func (h *SessionHandler) HumaDownloadSlotSave(ctx context.Context, in *SessionSlotSaveDownloadInput) (*huma.StreamResponse, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	slotNum, err := strconv.Atoi(in.Slot)
	if err != nil || slotNum < 1 || slotNum > 10 {
		return nil, huma.Error400BadRequest("slot must be between 1 and 10")
	}

	var save db.SessionSaveState
	if err := h.DB.Where("session_id = ? AND slot = ?", session.ID, slotNum).First(&save).Error; err != nil {
		return nil, huma.Error404NotFound("no save in this slot")
	}
	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		return nil, huma.Error403Forbidden("file access denied")
	}
	return streamFileFromDisk(save.FilePath, filepath.Base(save.FilePath), "application/octet-stream"), nil
}

// HumaDownloadSRAM is the huma implementation of GET /api/sessions/{id}/sram.
func (h *SessionHandler) HumaDownloadSRAM(ctx context.Context, in *SessionSRAMDownloadInput) (*huma.StreamResponse, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var sd db.SessionSaveData
	if err := h.DB.Where("session_id = ?", session.ID).First(&sd).Error; err != nil {
		return nil, huma.Error404NotFound("no SRAM data found")
	}
	if !storage.ValidateROMPath(sd.FilePath, []string{h.Storage.SaveDir}) {
		return nil, huma.Error403Forbidden("file access denied")
	}
	return streamFileFromDisk(sd.FilePath, filepath.Base(sd.FilePath), "application/octet-stream"), nil
}

// HumaDownloadSharedSave is the huma implementation of GET
// /api/games/{id}/shared-saves/{saveId}/download. Matches the gin handler
// exactly: lookup by save ID only (no game-id cross-check — the existing
// route doesn't enforce game scoping on download), path validation, then
// an increment of the download counter as a side effect.
func (h *SharedSaveHandler) HumaDownloadSharedSave(_ context.Context, in *SharedSaveDownloadInput) (*huma.StreamResponse, error) {
	var save db.SharedSaveState
	if err := h.DB.First(&save, in.SaveID).Error; err != nil {
		return nil, huma.Error404NotFound("shared save not found")
	}
	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		return nil, huma.Error403Forbidden("access denied")
	}
	h.DB.Model(&save).UpdateColumn("download_count", gorm.Expr("download_count + ?", 1))
	return streamFileFromDisk(save.FilePath, filepath.Base(save.FilePath), "application/octet-stream"), nil
}

// HumaDownloadSharedSessionSave is the huma implementation of GET
// /api/shared-sessions/{id}/saves/{saveId}.
func (h *SharedSessionHandler) HumaDownloadSharedSessionSave(ctx context.Context, in *SharedSessionSaveDownloadInput) (*huma.StreamResponse, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var save db.SharedSessionSave
	if err := h.DB.Where("id = ? AND shared_session_id = ?", in.SaveID, ss.ID).First(&save).Error; err != nil {
		return nil, huma.Error404NotFound("save not found")
	}
	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		return nil, huma.Error403Forbidden("access denied")
	}
	return streamFileFromDisk(save.FilePath, save.Name, "application/octet-stream"), nil
}

// HumaDownloadSharedSessionAutoSave is the huma implementation of GET
// /api/shared-sessions/{id}/saves/auto.
func (h *SharedSessionHandler) HumaDownloadSharedSessionAutoSave(ctx context.Context, in *SharedSessionAutoSaveDownloadInput) (*huma.StreamResponse, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var save db.SharedSessionSave
	if err := h.DB.Where("shared_session_id = ? AND is_auto = ?", ss.ID, true).First(&save).Error; err != nil {
		return nil, huma.Error404NotFound("no auto-save found")
	}
	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		return nil, huma.Error403Forbidden("access denied")
	}
	return streamFileFromDisk(save.FilePath, "autosave.sav", "application/octet-stream"), nil
}

// HumaGetConsoleIcon is the huma implementation of GET /api/consoles/{id}/icon.
func (h *ConsoleHandler) HumaGetConsoleIcon(_ context.Context, in *ConsoleAssetInput) (*huma.StreamResponse, error) {
	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?) OR code = ?", in.ID, in.ID).First(&console).Error; err != nil {
		return nil, huma.Error404NotFound("console not found")
	}
	filename := strings.ToLower(console.Abbreviation) + ".png"
	data, err := consoleIcons.ReadFile("static/console-icons/" + filename)
	if err != nil {
		return nil, huma.Error404NotFound("icon not available for this console")
	}
	return streamBytesInline(data, "image/png"), nil
}

// HumaGetConsoleLogo is the huma implementation of GET /api/consoles/{id}/logo.
// Inlines CSS styles in the SVG so renderers without CSS support (e.g. Coil
// on JVM) display colors correctly — same post-processing as the gin version.
func (h *ConsoleHandler) HumaGetConsoleLogo(_ context.Context, in *ConsoleAssetInput) (*huma.StreamResponse, error) {
	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?) OR code = ?", in.ID, in.ID).First(&console).Error; err != nil {
		return nil, huma.Error404NotFound("console not found")
	}
	filename := strings.ToLower(console.Abbreviation) + ".svg"
	data, err := consoleLogos.ReadFile("static/console-logos/" + filename)
	if err != nil {
		return nil, huma.Error404NotFound("logo not available for this console")
	}
	processed := inlineSvgStyles(string(data))
	return streamBytesInline([]byte(processed), "image/svg+xml"), nil
}

// HumaGetConsoleLogoPng is the huma implementation of GET
// /api/consoles/{id}/logo.png.
func (h *ConsoleHandler) HumaGetConsoleLogoPng(_ context.Context, in *ConsoleAssetInput) (*huma.StreamResponse, error) {
	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?) OR code = ?", in.ID, in.ID).First(&console).Error; err != nil {
		return nil, huma.Error404NotFound("console not found")
	}
	filename := strings.ToLower(console.Abbreviation) + ".png"
	data, err := consoleLogosPng.ReadFile("static/console-logos-png/" + filename)
	if err != nil {
		return nil, huma.Error404NotFound("logo not available for this console")
	}
	return streamBytesInline(data, "image/png"), nil
}

// HumaGetPreviewScreenshot is the huma implementation of GET
// /api/consoles/{id}/preview-screenshot. Mirrors the gin handler: checks
// for a cached copy on disk and redirects to /api/images/...; otherwise
// downloads from LibRetro CDN, caches, and redirects.
func (h *ConsoleHandler) HumaGetPreviewScreenshot(ctx context.Context, in *ConsoleAssetInput) (*huma.StreamResponse, error) {
	redirectPath, err := h.resolvePreviewScreenshotPath(ctx, in.ID)
	if err != nil {
		return nil, err
	}
	return &huma.StreamResponse{
		Body: func(hctx huma.Context) {
			hctx.SetHeader("Cache-Control", "public, max-age=86400")
			hctx.SetHeader("Location", redirectPath)
			hctx.SetStatus(http.StatusFound)
		},
	}, nil
}

// humaGetBrandingLogo is the huma implementation of GET /api/branding/logo.
func humaGetBrandingLogo(_ context.Context, _ *BrandingLogoInput) (*huma.StreamResponse, error) {
	data, err := brandingAssets.ReadFile("static/branding/spela-logo.png")
	if err != nil {
		return nil, huma.Error404NotFound("logo not found")
	}
	return streamBytesInline(data, "image/png"), nil
}
