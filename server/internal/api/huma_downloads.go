package api

import (
	"archive/zip"
	"context"
	"fmt"
	"io"
	"io/fs"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/bios"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scanner"
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
			// Large game/disc downloads (multi-GB ISOs) routinely exceed the
			// server's global http.Server WriteTimeout: the connection is
			// closed mid-stream after WriteTimeout seconds regardless of
			// progress, so e.g. a 4.5GB ISO dies at ~120s / ~3.5GB every time
			// (the cut point varies with bandwidth). Clear the write deadline
			// for this streaming response so the transfer can take as long as
			// it needs; the global timeout still guards normal API responses.
			// Covers disc + save downloads too — both stream through here.
			//
			// BodyWriter() is the underlying http.ResponseWriter under the
			// humago adapter; under huma's test adapter it's a buffer, so the
			// type check keeps this a no-op there (humago.Unwrap would panic on
			// a non-humago context).
			if rw, ok := hctx.BodyWriter().(http.ResponseWriter); ok {
				_ = http.NewResponseController(rw).SetWriteDeadline(time.Time{})
			}
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
			// Advertise the exact byte count so clients can show accurate
			// download progress. Without this the body is chunked (no
			// Content-Length), the player can't learn the transfer size, and
			// it falls back to the DB file size — which for compressed
			// single-file formats (e.g. .rvz) is the logical/uncompressed
			// size, leaving the progress bar stuck well under 100%. (#1235)
			if info, serr := f.Stat(); serr == nil {
				hctx.SetHeader("Content-Length", strconv.FormatInt(info.Size(), 10))
			}
			_, _ = io.Copy(hctx.BodyWriter(), f)
		},
	}
}

// streamSaveFromDisk wraps [streamFileFromDisk] with an X-Compression
// response header so the downloading player knows whether to gunzip
// before passing the bytes to retro_unserialize. Empty compression
// means uncompressed (the only case before #804 phase 2). Players
// that don't recognise a value should refuse to load the save rather
// than feed garbage to the core.
func streamSaveFromDisk(absPath, downloadName, compression string) *huma.StreamResponse {
	inner := streamFileFromDisk(absPath, downloadName, "application/octet-stream")
	return &huma.StreamResponse{
		Body: func(hctx huma.Context) {
			if compression != "" {
				hctx.SetHeader("X-Compression", compression)
			}
			inner.Body(hctx)
		},
	}
}

// streamBundleAsZip walks rootDir and streams a zip of every regular
// file under it (paths inside the zip are relative to rootDir, with
// forward slashes). Used by the BIOS bundle download endpoint (#911)
// to ship a Bundle entry's extracted directory tree as a single
// archive the client can unzip on its end.
//
// We re-zip on every request rather than caching: the bundle is small
// (PPSSPP is ~10MB raw, ~zip), single-digit clients per server, and
// the alternative (keep the originally-fetched archive on disk
// alongside the extracted tree) doubles disk usage and adds a
// staleness window when the bundle is re-downloaded.
func streamBundleAsZip(rootDir, downloadFileName string) *huma.StreamResponse {
	return &huma.StreamResponse{
		Body: func(hctx huma.Context) {
			hctx.SetHeader("Content-Type", "application/zip")
			hctx.SetHeader("Content-Disposition", fmt.Sprintf("attachment; filename=%q", downloadFileName+".zip"))

			zw := zip.NewWriter(hctx.BodyWriter())
			defer zw.Close()

			err := filepath.WalkDir(rootDir, func(path string, d fs.DirEntry, walkErr error) error {
				if walkErr != nil {
					return walkErr
				}
				if d.IsDir() {
					return nil
				}
				if !d.Type().IsRegular() {
					return nil
				}
				rel, err := filepath.Rel(rootDir, path)
				if err != nil {
					return err
				}
				zipName := filepath.ToSlash(rel)
				zfw, err := zw.Create(zipName)
				if err != nil {
					return err
				}
				f, err := os.Open(path)
				if err != nil {
					return err
				}
				defer f.Close()
				_, err = io.Copy(zfw, f)
				return err
			})
			if err != nil {
				slog.Warn("bundle stream walk failed", "root", rootDir, "err", err)
			}
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
	ID       string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Core ID."`
	Platform string `query:"platform" required:"false" default:"linux" doc:"Target platform: linux, macos, windows, android."`
	Sha256   string `query:"sha256" required:"false" doc:"Optional hex sha256 of a historical core binary. When set, serves that specific version from the history snapshot; returns 404 if it's been pruned. Defaults to serving the current binary."`
}

// BiosDownloadInput is the input for GET /api/bios/{filename}.
type BiosDownloadInput struct {
	Filename string `path:"filename" pattern:"^[A-Za-z0-9._()\\[\\],'&! +-]+$" maxLength:"128" doc:"BIOS file name."`
}

// BiosArchiveDownloadInput is the input for GET /api/bios/archive/{filename}.
// Filename matches the registry entry's FileName for a Bundle entry; the
// server returns a zip of `<biosDir>/<SubDir>/` so the client can extract
// the directory tree on the device.
type BiosArchiveDownloadInput struct {
	Filename string `path:"filename" pattern:"^[A-Za-z0-9._()\\[\\],'&! +-]+$" maxLength:"128" doc:"Sentinel filename of the bundle entry (matches registry FileName)."`
}

// SessionSaveDownloadInput is the input for GET /api/sessions/{id}/saves/{saveId}.
type SessionSaveDownloadInput struct {
	ID     string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Session ID."`
	SaveID string `path:"saveId" pattern:"^[0-9]+$" maxLength:"20" doc:"Save state ID."`
}

// SessionAutoSaveDownloadInput is the input for GET /api/sessions/{id}/saves/auto.
type SessionAutoSaveDownloadInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Session ID."`
}

// SessionSlotSaveDownloadInput is the input for GET /api/sessions/{id}/saves/slot/{slot}.
type SessionSlotSaveDownloadInput struct {
	ID   string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Session ID."`
	Slot string `path:"slot" pattern:"^[0-9]+$" maxLength:"20" doc:"Save slot number 1-10."`
}

// SessionSaveDirBundleDownloadInput is the input for GET /api/sessions/{id}/save-dir.
// Returns the previously-uploaded tarball or 404 if the session has no bundle.
type SessionSaveDirBundleDownloadInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Session ID."`
}

// SessionSRAMDownloadInput is the input for GET /api/sessions/{id}/sram.
type SessionSRAMDownloadInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Session ID."`
}

// SharedSaveDownloadInput is the input for GET /api/games/{id}/shared-saves/{saveId}/download.
type SharedSaveDownloadInput struct {
	ID     string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
	SaveID string `path:"saveId" pattern:"^[0-9]+$" maxLength:"20" doc:"Shared save state ID."`
}

// SharedSessionSaveDownloadInput is the input for
// GET /api/shared-sessions/{id}/saves/{saveId}.
type SharedSessionSaveDownloadInput struct {
	ID     string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Shared session ID."`
	SaveID string `path:"saveId" pattern:"^[0-9]+$" maxLength:"20" doc:"Shared session save ID."`
}

// SharedSessionAutoSaveDownloadInput is the input for
// GET /api/shared-sessions/{id}/saves/auto.
type SharedSessionAutoSaveDownloadInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Shared session ID."`
}

// ConsoleAssetInput is the input for the public console-image endpoints
// (icon / logo / logo.png / preview-screenshot). The id resolves a console
// by abbreviation or code.
type ConsoleAssetInput struct {
	ID string `path:"id" pattern:"^[a-zA-Z0-9_-]+$" maxLength:"32" doc:"Console abbreviation or code."`
}

// BrandingLogoInput is the empty input for GET /api/branding/logo.
type BrandingLogoInput struct{}

// GameDownloadInput is the input for GET /api/games/{id}/download(/{filename}).
// The filename is captured but unused — it lets the client request the file
// under a download-friendly name without changing the served bytes.
type GameDownloadInput struct {
	ID       string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
	Filename string `path:"filename" required:"false" pattern:"^[A-Za-z0-9._()\\[\\],'&! +-]+$" maxLength:"128" doc:"Optional download filename — server ignores it; lets clients control the saved filename."`
	Format   string `query:"format" required:"false" doc:"'zip' to force ZIP packaging for multi-file disc games. Default is .tar (or single-file passthrough)."`
}

// GameDiscDownloadInput is the input for GET
// /api/games/{id}/discs/{discNumber}/download.
type GameDiscDownloadInput struct {
	ID         string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
	DiscNumber string `path:"discNumber" pattern:"^[0-9]+$" maxLength:"20" doc:"Disc number (1-based)."`
	Format     string `query:"format" required:"false" doc:"'zip' for ZIP packaging (used by EmulatorJS), default is .tar."`
}

// ChallengeAssetInput is the input for the two challenge download endpoints.
type ChallengeAssetInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Challenge ID."`
}

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
	gameH *GameHandler,
	challengeH *ChallengeHandler,
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
		OperationID: "downloadBiosArchive",
		Method:      http.MethodGet,
		Path:        "/api/bios/archive/{filename}",
		Summary:     "Download a BIOS bundle archive",
		Description: "Streams a zip of `<biosDir>/<SubDir>/` for a Bundle registry entry. Clients unzip on the device. 404 when the entry isn't a Bundle or the SubDir hasn't been populated yet on the server. See #911.",
		Tags:        []string{"bios"},
		Middlewares: authedMW,
		Security:    sec,
	}, biosH.HumaDownloadBiosArchive)

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
		OperationID: "downloadSessionSaveDirBundle",
		Method:      http.MethodGet,
		Path:        "/api/sessions/{id}/save-dir",
		Summary:     "Download the libretro save_dir tarball for a session",
		Description: "Owner-only. Responds with application/x-tar containing the save_dir bytes uploaded on the most recent exit. 404 if the session has never had a save_dir bundle. See #864.",
		Tags:        []string{"sessions"},
		Middlewares: authedMW,
		Security:    sec,
	}, sessionH.HumaDownloadSaveDirBundle)

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

	huma.Register(api, huma.Operation{
		OperationID: "downloadGame",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/download",
		Summary:     "Download a game's ROM file",
		Description: "Serves the game's ROM file. For .scummvm games, packages the entire game directory as tar. For .cue/.gdi multi-file disc games, serves a tar (default) or zip (?format=zip) bundle of all companion files.",
		Tags:        []string{"games"},
		Middlewares: authedDownloadMW,
		Security:    sec,
	}, gameH.HumaDownloadGame)

	huma.Register(api, huma.Operation{
		OperationID: "downloadGameWithFilename",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/download/{filename}",
		Summary:     "Download a game's ROM file (with explicit filename)",
		Description: "Same as downloadGame; the filename path segment is captured but ignored server-side and lets clients control the saved filename.",
		Tags:        []string{"games"},
		Middlewares: authedDownloadMW,
		Security:    sec,
	}, gameH.HumaDownloadGame)

	huma.Register(api, huma.Operation{
		OperationID: "downloadGameDisc",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/discs/{discNumber}/download",
		Summary:     "Download a specific disc of a multi-disc game",
		Description: "For single-file disc formats (.iso, .chd) serves the file directly; for multi-file (.cue+.bin) serves an uncompressed tar (or zip when ?format=zip).",
		Tags:        []string{"games"},
		Middlewares: authedDownloadMW,
		Security:    sec,
	}, gameH.HumaDownloadDisc)

	huma.Register(api, huma.Operation{
		OperationID: "downloadChallengeSave",
		Method:      http.MethodGet,
		Path:        "/api/challenges/{id}/save/download",
		Summary:     "Download a challenge's starting save state",
		Description: "Returns the save file that seeds the challenge run. Responds with application/octet-stream.",
		Tags:        []string{"challenges"},
		Middlewares: authedMW,
		Security:    sec,
	}, challengeH.HumaDownloadChallengeSave)

	huma.Register(api, huma.Operation{
		OperationID: "getChallengeScreenshot",
		Method:      http.MethodGet,
		Path:        "/api/challenges/{id}/screenshot",
		Summary:     "Get a challenge's screenshot",
		Description: "Returns the screenshot image that illustrates the challenge state.",
		Tags:        []string{"challenges"},
		Middlewares: authedMW,
		Security:    sec,
	}, challengeH.HumaGetChallengeScreenshot)
}

// --- Handlers ----------------------------------------------------------------

// HumaDownloadCore is the huma implementation of GET /api/cores/{id}/download.
func (h *CoreHandler) HumaDownloadCore(_ context.Context, in *CoreDownloadInput) (*huma.StreamResponse, error) {
	parsedID, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid core ID")
	}
	var core db.Core
	if err := h.DB.First(&core, uint(parsedID)).Error; err != nil {
		return nil, huma.Error404NotFound("core not found")
	}

	platform := in.Platform
	if platform == "" {
		platform = "linux"
	}

	ext := platformExtension(platform)

	// Historical version requested: serve from {CoreDir}/history/{sha}/...
	// without running the metadata backfill — the metadata corresponds to
	// whatever's current, not to the historical file.
	if in.Sha256 != "" {
		// Validate sha256 is a 64-char hex string before any path
		// construction. filepath.Join normalises paths, so without this
		// guard a value like "../" can escape {CoreDir}/history into
		// CoreDir itself or beyond. Reject anything that isn't [0-9a-fA-F]{64}.
		if !isValidSha256Hex(in.Sha256) {
			return nil, huma.Error400BadRequest("invalid sha256 — expected 64 hex characters")
		}
		if h.CoreDir == "" {
			return nil, huma.Error404NotFound("core history not available")
		}
		historyRoot := filepath.Join(h.CoreDir, "history")
		historyPath := h.historyBinaryPath(core, in.Sha256, ext)
		// Defence in depth: confirm the resolved path stays inside
		// {CoreDir}/history. The hex guard above already prevents
		// traversal, but a second check costs nothing.
		if !storage.ValidateROMPath(historyPath, []string{historyRoot}) {
			return nil, huma.Error403Forbidden("core file access denied")
		}
		if _, err := os.Stat(historyPath); os.IsNotExist(err) {
			return nil, huma.Error404NotFound("core binary not available for requested sha256")
		}
		return streamFileFromDisk(historyPath, core.Name+"_libretro"+ext, "application/octet-stream"), nil
	}

	corePath := h.resolveCorePath(core, platform)
	if corePath == "" {
		return nil, huma.Error404NotFound("core binary not available")
	}

	if h.CoreDir == "" || !storage.ValidateROMPath(corePath, []string{h.CoreDir}) {
		return nil, huma.Error403Forbidden("core file access denied")
	}

	// Lazy backfill of factual core metadata. Only runs until the row
	// has all fields populated; afterwards it's a no-op. See #555.
	// Also snapshots the binary into {CoreDir}/history/{sha256}/... so
	// future ?sha256= requests can serve it after a newer binary lands.
	h.ensureCoreMetadata(&core, corePath)

	return streamFileFromDisk(corePath, core.Name+"_libretro"+ext, "application/octet-stream"), nil
}

// HumaDownloadBiosArchive is the huma implementation of
// GET /api/bios/archive/{filename}. For a Bundle registry entry whose
// FileName matches the path parameter, it streams a zip of the
// `<biosDir>/<SubDir>/` tree so the client can extract on the device.
// 404 when the entry isn't a Bundle, the registry doesn't know it, or
// the SubDir on disk is empty (i.e. the bundle hasn't been
// downloaded/extracted yet on the server).
func (h *BiosHandler) HumaDownloadBiosArchive(_ context.Context, in *BiosArchiveDownloadInput) (*huma.StreamResponse, error) {
	matches := bios.ByFileName(in.Filename)
	var entry *bios.Entry
	for i := range matches {
		if matches[i].Bundle {
			cp := matches[i]
			entry = &cp
			break
		}
	}
	if entry == nil {
		return nil, huma.Error404NotFound("no bundle entry registered for this filename")
	}

	subDir := entry.SubDir
	if subDir == "" {
		// Defensive — every current Bundle entry sets SubDir, but if
		// one didn't we'd be exposing the entire BIOS dir which we
		// never want to do.
		return nil, huma.Error404NotFound("bundle entry has no SubDir")
	}

	rootDir := filepath.Join(h.Storage.BiosDir, filepath.FromSlash(subDir))
	info, err := os.Stat(rootDir)
	if err != nil || !info.IsDir() {
		return nil, huma.Error404NotFound("bundle not extracted on server")
	}

	absRoot, err := filepath.Abs(rootDir)
	if err != nil {
		return nil, huma.Error500InternalServerError("internal error")
	}
	absBios, err := filepath.Abs(h.Storage.BiosDir)
	if err != nil {
		return nil, huma.Error500InternalServerError("internal error")
	}
	if !strings.HasPrefix(absRoot, absBios+string(filepath.Separator)) {
		return nil, huma.Error403Forbidden("access denied")
	}

	return streamBundleAsZip(absRoot, in.Filename), nil
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
	return streamSaveFromDisk(save.FilePath, filepath.Base(save.FilePath), save.Compression), nil
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
	return streamSaveFromDisk(save.FilePath, filepath.Base(save.FilePath), save.Compression), nil
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
	return streamSaveFromDisk(save.FilePath, filepath.Base(save.FilePath), save.Compression), nil
}

// HumaDownloadSaveDirBundle is the huma implementation of GET
// /api/sessions/{id}/save-dir. Streams the previously-uploaded tarball back to
// the player. 404 when the session has never had a save_dir bundle (the
// player treats that as "fresh save_dir, nothing to populate"). See #864.
func (h *SessionHandler) HumaDownloadSaveDirBundle(ctx context.Context, in *SessionSaveDirBundleDownloadInput) (*huma.StreamResponse, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var bundle db.SessionSaveDirBundle
	if err := h.DB.Where("session_id = ?", session.ID).First(&bundle).Error; err != nil {
		return nil, huma.Error404NotFound("no save_dir bundle found")
	}
	if !storage.ValidateROMPath(bundle.FilePath, []string{h.Storage.SaveDir}) {
		return nil, huma.Error403Forbidden("file access denied")
	}
	return streamFileFromDisk(bundle.FilePath, filepath.Base(bundle.FilePath), "application/x-tar"), nil
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

// consoleLogoFallbacks maps a console abbreviation (lowercase) to the
// abbreviation of the parent platform whose logo should be served when
// the child console has no dedicated asset yet. Demo-scene consoles
// inherit their parent platform's logo by default — Amiga Demos uses
// the Amiga logo, DOS Demos uses the DOS logo. Adding a row here only
// affects consoles that don't have their own SVG / PNG in the embedded
// asset dirs; once an asset is shipped for the child, it wins.
//
// Future child consoles get a sensible default with one map entry.
// See #888.
var consoleLogoFallbacks = map[string]string{
	"ademo": "amiga",
	"ddemo": "dos",
}

// HumaGetConsoleLogo is the huma implementation of GET /api/consoles/{id}/logo.
// Inlines CSS styles in the SVG so renderers without CSS support (e.g. Coil
// on JVM) display colors correctly — same post-processing as the gin version.
func (h *ConsoleHandler) HumaGetConsoleLogo(_ context.Context, in *ConsoleAssetInput) (*huma.StreamResponse, error) {
	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?) OR code = ?", in.ID, in.ID).First(&console).Error; err != nil {
		return nil, huma.Error404NotFound("console not found")
	}
	abbr := strings.ToLower(console.Abbreviation)
	data, err := consoleLogos.ReadFile("static/console-logos/" + abbr + ".svg")
	if err != nil {
		// Fall back to a parent-platform logo when registered (#888).
		if parent, ok := consoleLogoFallbacks[abbr]; ok {
			if pdata, perr := consoleLogos.ReadFile("static/console-logos/" + parent + ".svg"); perr == nil {
				data = pdata
				err = nil
			}
		}
	}
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
	abbr := strings.ToLower(console.Abbreviation)
	data, err := consoleLogosPng.ReadFile("static/console-logos-png/" + abbr + ".png")
	if err != nil {
		// Same parent-platform fallback as the SVG variant (#888).
		if parent, ok := consoleLogoFallbacks[abbr]; ok {
			if pdata, perr := consoleLogosPng.ReadFile("static/console-logos-png/" + parent + ".png"); perr == nil {
				data = pdata
				err = nil
			}
		}
	}
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

// HumaDownloadGame is the huma implementation of GET /api/games/{id}/download
// (and the {filename}-suffixed variant). Handles three packaging modes:
//   - .scummvm games: tar of the entire game directory.
//   - .cue/.gdi multi-file disc games: tar (default) or zip (?format=zip).
//   - everything else: served inline.
func (h *GameHandler) HumaDownloadGame(_ context.Context, in *GameDownloadInput) (*huma.StreamResponse, error) {
	var game db.Game
	if err := h.DB.First(&game, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	absPath, err := storage.ResolveGamePath(game.FilePath, h.GameDirs)
	if err != nil {
		db.RecordOperationalEvent(h.DB, db.SystemEventInput{
			EventType: db.SystemEventROMFileMissing,
			Metadata: db.ROMFileMissingMetadata{
				GameID:       game.ID,
				GameTitle:    game.Title,
				ExpectedPath: game.FilePath,
			},
		})
		return nil, huma.Error404NotFound("game file not found")
	}

	if !storage.ValidateROMPath(absPath, h.GameDirs) {
		return nil, huma.Error403Forbidden("file access denied")
	}

	lower := strings.ToLower(game.FileName)

	// .scummvm — tar of the directory
	if strings.HasSuffix(lower, ".scummvm") {
		var files []string
		filepath.WalkDir(absPath, func(p string, d fs.DirEntry, err error) error {
			if err != nil || d.IsDir() {
				return nil
			}
			files = append(files, p)
			return nil
		})
		if len(files) > 0 {
			downloadName := game.FileName + ".tar"
			return streamArchiveTar(files, downloadName, game.Title), nil
		}
	}

	// .cue/.gdi — multi-file disc; tar or zip
	if strings.HasSuffix(lower, ".cue") || strings.HasSuffix(lower, ".gdi") {
		companions, _, err := scanner.DiscCompanionFiles(absPath)
		if err != nil {
			return nil, huma.Error500InternalServerError("failed to read disc files")
		}
		if in.Format == "zip" {
			return streamArchiveZip(companions, game.FileName+".zip", game.Title), nil
		}
		if len(companions) > 1 {
			return streamArchiveTar(companions, game.FileName+".tar", game.Title), nil
		}
	}

	// Fallback: inline single file. Match the gin "inline" Content-Disposition
	// (the file may be a ROM that the client renders inline rather than saves).
	//
	// For .nes ROMs, the first 16 bytes are normalized in memory so iNES 1.0
	// headers polluted with tool-provenance markers (e.g. "DiskDude!", "NI2.1"
	// from old GoodNES dumps) don't trip strict cores like nestopia. The
	// on-disk file is never modified — only the bytes streamed to the client.
	normalizeINES := strings.HasSuffix(lower, ".nes")
	return &huma.StreamResponse{
		Body: func(hctx huma.Context) {
			f, err := os.Open(absPath)
			if err != nil {
				hctx.SetStatus(http.StatusInternalServerError)
				return
			}
			defer f.Close()
			hctx.SetHeader("Content-Type", "application/octet-stream")
			hctx.SetHeader("Content-Disposition", fmt.Sprintf("inline; filename=%q", game.FileName))
			// Advertise the exact transfer size so the player shows accurate
			// download progress. Without it the body is chunked (no
			// Content-Length) and the client falls back to the DB file size,
			// which for compressed formats (PSP, .rvz, .chd) is the
			// logical/uncompressed size — leaving the bar stuck well under
			// 100%. The streamed byte count equals the on-disk size; the
			// iNES normalization below rewrites the first 16 bytes in place,
			// so the length is unchanged. (#1261, follow-up to #1235/#1248)
			if info, serr := f.Stat(); serr == nil {
				hctx.SetHeader("Content-Length", strconv.FormatInt(info.Size(), 10))
			}
			w := hctx.BodyWriter()
			if normalizeINES {
				var head [16]byte
				n, _ := io.ReadFull(f, head[:])
				if n == len(head) {
					if scanner.NormalizeINESHeaderBytes(head[:]) {
						slog.Info("normalized iNES header for download",
							"game", game.Title, "file", game.FileName)
					}
					if _, err := w.Write(head[:]); err != nil {
						return
					}
				} else if n > 0 {
					if _, err := w.Write(head[:n]); err != nil {
						return
					}
				}
			}
			_, _ = io.Copy(w, f)
		},
	}, nil
}

// HumaDownloadDisc is the huma implementation of GET
// /api/games/{id}/discs/{discNumber}/download.
func (h *GameHandler) HumaDownloadDisc(_ context.Context, in *GameDiscDownloadInput) (*huma.StreamResponse, error) {
	discNumber, err := strconv.Atoi(in.DiscNumber)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid disc number")
	}

	var disc db.GameDisc
	if err := h.DB.Where("game_id = ? AND disc_number = ?", in.ID, discNumber).First(&disc).Error; err != nil {
		return nil, huma.Error404NotFound("disc not found")
	}

	absDiscPath, err := storage.ResolveGamePath(disc.FilePath, h.GameDirs)
	if err != nil {
		return nil, huma.Error404NotFound("disc file not found")
	}
	if !storage.ValidateROMPath(absDiscPath, h.GameDirs) {
		return nil, huma.Error403Forbidden("file access denied")
	}

	companions, _, err := scanner.DiscCompanionFiles(absDiscPath)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to read disc files")
	}

	if in.Format == "zip" {
		return streamArchiveZip(companions, disc.FileName+".zip", "disc "+in.DiscNumber), nil
	}
	if len(companions) == 1 {
		// Single file (.iso, .chd) — serve inline
		return &huma.StreamResponse{
			Body: func(hctx huma.Context) {
				f, err := os.Open(companions[0])
				if err != nil {
					hctx.SetStatus(http.StatusInternalServerError)
					return
				}
				defer f.Close()
				hctx.SetHeader("Content-Type", "application/octet-stream")
				hctx.SetHeader("Content-Disposition", fmt.Sprintf("inline; filename=%q", disc.FileName))
				_, _ = io.Copy(hctx.BodyWriter(), f)
			},
		}, nil
	}

	return streamArchiveTar(companions, disc.FileName+".tar", "disc "+in.DiscNumber), nil
}

// streamArchiveTar returns a StreamResponse that writes an uncompressed tar
// of the given files. Errors during streaming are logged but not surfaced —
// once headers are sent the response body is one-way.
//
// Sets X-Logical-Size to the sum of file sizes inside the tar (not the
// over-the-wire byte count, which includes 512-byte tar headers and
// per-file padding). The player app uses this value as the
// authoritative download-progress total; without it, the client has to
// fall back to game.fileSize, which for ScummVM and old .cue/.gdi
// records is the marker file size — orders of magnitude smaller than
// the real download. See #931.
func streamArchiveTar(files []string, downloadName, contextName string) *huma.StreamResponse {
	return &huma.StreamResponse{
		Body: func(hctx huma.Context) {
			var totalLogical int64
			for _, f := range files {
				if info, err := os.Stat(f); err == nil {
					totalLogical += info.Size()
				}
			}
			hctx.SetHeader("Content-Type", "application/x-tar")
			hctx.SetHeader("Content-Disposition", fmt.Sprintf("attachment; filename=%q", downloadName))
			hctx.SetHeader("X-Logical-Size", strconv.FormatInt(totalLogical, 10))
			if err := serveTar(hctx.BodyWriter(), files); err != nil {
				slog.Warn("error streaming tar", "context", contextName, "error", err)
			}
		},
	}
}

// streamArchiveZip returns a StreamResponse that writes a zip archive.
func streamArchiveZip(files []string, downloadName, contextName string) *huma.StreamResponse {
	return &huma.StreamResponse{
		Body: func(hctx huma.Context) {
			hctx.SetHeader("Content-Type", "application/zip")
			hctx.SetHeader("Content-Disposition", fmt.Sprintf("attachment; filename=%q", downloadName))
			if err := serveZip(hctx.BodyWriter(), files); err != nil {
				slog.Warn("error streaming zip", "context", contextName, "error", err)
			}
		},
	}
}

// HumaDownloadChallengeSave is the huma implementation of GET
// /api/challenges/{id}/save/download.
func (h *ChallengeHandler) HumaDownloadChallengeSave(_ context.Context, in *ChallengeAssetInput) (*huma.StreamResponse, error) {
	var challenge db.Challenge
	if err := h.DB.First(&challenge, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("challenge not found")
	}
	path := h.Storage.ChallengeSavePath(challenge.ID)
	if !storage.ValidateROMPath(path, []string{h.Storage.SaveDir}) {
		return nil, huma.Error403Forbidden("access denied")
	}
	return streamFileFromDisk(path, "challenge_save", "application/octet-stream"), nil
}

// HumaGetChallengeScreenshot is the huma implementation of GET
// /api/challenges/{id}/screenshot. Mirrors the gin handler — no
// Content-Disposition (rendered inline by <img>).
func (h *ChallengeHandler) HumaGetChallengeScreenshot(_ context.Context, in *ChallengeAssetInput) (*huma.StreamResponse, error) {
	var challenge db.Challenge
	if err := h.DB.First(&challenge, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("challenge not found")
	}
	if challenge.ScreenshotPath == "" {
		return nil, huma.Error404NotFound("no screenshot available")
	}
	path := h.Storage.ChallengeScreenshotPath(challenge.ID)
	if !storage.ValidateROMPath(path, []string{h.Storage.SaveDir}) {
		return nil, huma.Error403Forbidden("access denied")
	}
	return &huma.StreamResponse{
		Body: func(hctx huma.Context) {
			f, err := os.Open(path)
			if err != nil {
				hctx.SetStatus(http.StatusInternalServerError)
				return
			}
			defer f.Close()
			hctx.SetHeader("Content-Type", "image/png")
			_, _ = io.Copy(hctx.BodyWriter(), f)
		},
	}, nil
}
