package api

import (
	"context"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Inputs ------------------------------------------------------------------

// SessionSaveUploadBody is the multipart body shared by the manual save,
// auto-save, and slot-save uploads. The save file is required (validated in
// the handler so the existing "save file required" error message is preserved
// instead of huma's generic 422). The screenshot is genuinely optional — if
// omitted the previous screenshot stays in place.
type SessionSaveUploadBody struct {
	Save       huma.FormFile `form:"save" required:"false" contentType:"application/octet-stream" doc:"Save state file. Required."`
	Screenshot huma.FormFile `form:"screenshot" required:"false" contentType:"application/octet-stream" doc:"Optional screenshot to attach to the save (typically PNG/JPEG)."`
	Name       string        `form:"name" required:"false" doc:"Display name for the save (defaults to the uploaded filename)."`
	CoreName   string        `form:"coreName" required:"false" doc:"Identifier of the libretro core that produced the save."`
	CoreSha256 string        `form:"coreSha256" required:"false" doc:"Hex sha256 of the core binary that produced this save state. Optional — the server records it alongside the save for diagnostics and future rollback UX. Invalid values (not 64 hex chars) are silently dropped. See #555 Phase 3."`
}

// SessionSaveUploadInput wraps the path parameter and multipart body for
// POST /api/sessions/{id}/saves.
type SessionSaveUploadInput struct {
	ID      string `path:"id" doc:"Session ID."`
	RawBody huma.MultipartFormFiles[SessionSaveUploadBody]
}

// SessionSaveUploadOutput wraps the upload result with a 201 status (matches
// the gin handler).
type SessionSaveUploadOutput struct {
	Body SessionSaveResponse
}

// SessionAutoSaveUploadInput is the multipart body for POST
// /api/sessions/{id}/saves/auto. Same shape as a manual save.
type SessionAutoSaveUploadInput struct {
	ID      string `path:"id" doc:"Session ID."`
	RawBody huma.MultipartFormFiles[SessionSaveUploadBody]
}

// SessionSlotSaveUploadInput is the multipart body for PUT
// /api/sessions/{id}/saves/slot/{slot}. Slot saves don't have a name.
type SessionSlotSaveUploadInput struct {
	ID      string `path:"id" doc:"Session ID."`
	Slot    string `path:"slot" doc:"Slot number 1-10."`
	RawBody huma.MultipartFormFiles[SessionSaveUploadBody]
}

// SessionSRAMUploadBody is the multipart body for POST /api/sessions/{id}/sram.
type SessionSRAMUploadBody struct {
	File huma.FormFile `form:"file" required:"false" contentType:"application/octet-stream" doc:"SRAM/battery save file."`
}

// SessionSRAMUploadInput wraps the path parameter and multipart body.
type SessionSRAMUploadInput struct {
	ID      string `path:"id" doc:"Session ID."`
	RawBody huma.MultipartFormFiles[SessionSRAMUploadBody]
}

// SessionSRAMUploadOutput wraps the SRAM upload response. Status alternates
// between 201 (first upload) and 200 (subsequent overwrites); we use the
// dynamic-status pattern so the OpenAPI spec covers both.
type SessionSRAMUploadOutput struct {
	Status int
	Body   db.SessionSaveData
}

// --- Registration ------------------------------------------------------------

// RegisterSessionSaveUploadRoutes wires the four session multipart upload
// endpoints into the huma API. They previously stayed on raw gin while the
// JSON session endpoints were migrated; this completes the session surface.
//
// Each operation uses MaxBodyBytes for upload-size enforcement (replacing the
// per-handler http.MaxBytesReader call) and runs through the standard
// auth + rate-limit + upload-rate-limit middleware stack.
func RegisterSessionSaveUploadRoutes(
	api huma.API,
	h *SessionHandler,
	jwtSecret string,
	database *gorm.DB,
	userLimiter *RateLimiter,
	uploadLimiter *RateLimiter,
) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	uploadRL := UserRateLimitMiddleware(uploadLimiter)
	uploadMW := huma.Middlewares{requireAuth, rateLimit, uploadRL}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID:   "uploadSessionSave",
		Method:        http.MethodPost,
		Path:          "/api/sessions/{id}/saves",
		Summary:       "Upload a save state to a session",
		Description:   "Stores a manual save state for the session and returns the resulting save record. Optionally attaches a screenshot.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"sessions"},
		Middlewares:   uploadMW,
		Security:      sec,
		MaxBodyBytes:  maxSaveUploadBytes(),
	}, h.HumaUploadSessionSave)

	huma.Register(api, huma.Operation{
		OperationID:   "uploadAutoSave",
		Method:        http.MethodPost,
		Path:          "/api/sessions/{id}/saves/auto",
		Summary:       "Upload an auto-save to a session",
		Description:   "Stores an auto-save for the session and returns the resulting save record. Older auto-saves beyond the retention limit are pruned. Optionally attaches a screenshot.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"sessions"},
		Middlewares:   uploadMW,
		Security:      sec,
		MaxBodyBytes:  maxSaveUploadBytes(),
	}, h.HumaUploadAutoSave)

	huma.Register(api, huma.Operation{
		OperationID:  "upsertSlotSave",
		Method:       http.MethodPut,
		Path:         "/api/sessions/{id}/saves/slot/{slot}",
		Summary:      "Upload or overwrite a slot save",
		Description:  "Stores a save state in the specified slot (1-10). If the slot already has a save, it is replaced.",
		Tags:         []string{"sessions"},
		Middlewares:  uploadMW,
		Security:     sec,
		MaxBodyBytes: maxSaveUploadBytes(),
	}, h.HumaUpsertSlotSave)

	huma.Register(api, huma.Operation{
		OperationID:  "uploadSRAM",
		Method:       http.MethodPost,
		Path:         "/api/sessions/{id}/sram",
		Summary:      "Upload SRAM data for a session",
		Description:  "Stores or replaces the SRAM/battery save data for a session. Returns 201 on first upload, 200 on overwrite.",
		Tags:         []string{"sessions"},
		Middlewares:  uploadMW,
		Security:     sec,
		MaxBodyBytes: maxSaveUploadBytes(),
	}, h.HumaUploadSRAM)
}

// --- Helper ------------------------------------------------------------------

// humaCheckSharedSessionTurn returns nil when no shared session backs this
// session (normal case) or when the caller currently holds the turn. Otherwise
// returns a 403 huma error matching the gin handler's wording exactly.
func (h *SessionHandler) humaCheckSharedSessionTurn(sessionID, uid uint) error {
	var sharedSession db.SharedSession
	if err := h.DB.Where("session_id = ?", sessionID).First(&sharedSession).Error; err != nil {
		return nil
	}
	if sharedSession.ActiveUserID == nil || *sharedSession.ActiveUserID != uid {
		return huma.Error403Forbidden("shared session turn required: you must hold the turn to upload saves")
	}
	return nil
}

// --- Handlers ----------------------------------------------------------------

// HumaUploadSessionSave is the huma implementation of POST
// /api/sessions/{id}/saves. Mirrors the gin handler 1:1: ownership check,
// shared-session turn check, storage-quota check, optional screenshot, write
// to disk, create DB record, update last-played metadata, return the save.
func (h *SessionHandler) HumaUploadSessionSave(ctx context.Context, in *SessionSaveUploadInput) (*SessionSaveUploadOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}
	if err := h.humaCheckSharedSessionTurn(session.ID, uid); err != nil {
		return nil, err
	}

	body := in.RawBody.Data()
	save := body.Save
	if !save.IsSet {
		return nil, huma.Error400BadRequest("save file required")
	}
	defer save.Close()

	if err := checkStorageQuota(h.DB, uid, save.Size); err != nil {
		return nil, huma.Error413RequestEntityTooLarge("storage quota exceeded")
	}

	name := body.Name
	if name == "" {
		name = save.Filename
	}

	screenshotURL := h.tryWriteSessionScreenshot(session.ID, body.Screenshot)

	path, size, err := h.Storage.WriteSessionSave(session.ID, save.Filename, save)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to save file")
	}

	rec := db.SessionSaveState{
		SessionID:     session.ID,
		UserID:        uid,
		Name:          name,
		FilePath:      path,
		FileSize:      size,
		ScreenshotURL: screenshotURL,
		CoreName:      body.CoreName,
		CoreSha256:    sanitizeCoreSha256(body.CoreSha256),
		IsAuto:        false,
	}
	if err := h.DB.Create(&rec).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to create save record")
	}

	h.touchSessionAfterUpload(session, uid, screenshotURL, body.CoreName)
	h.DB.Preload("User").First(&rec, rec.ID)

	return &SessionSaveUploadOutput{
		Body: h.toSaveResponse(rec, h.getRecommendedCoreName(session.GameID)),
	}, nil
}

// HumaUploadAutoSave is the huma implementation of POST
// /api/sessions/{id}/saves/auto. Mirrors the gin auto-save behaviour including
// the autoSaveRetentionLimit-based pruning of older auto-saves.
func (h *SessionHandler) HumaUploadAutoSave(ctx context.Context, in *SessionAutoSaveUploadInput) (*SessionSaveUploadOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}
	if err := h.humaCheckSharedSessionTurn(session.ID, uid); err != nil {
		return nil, err
	}

	body := in.RawBody.Data()
	save := body.Save
	if !save.IsSet {
		return nil, huma.Error400BadRequest("save file required")
	}
	defer save.Close()

	if err := checkStorageQuota(h.DB, uid, save.Size); err != nil {
		return nil, huma.Error413RequestEntityTooLarge("storage quota exceeded")
	}

	filename := fmt.Sprintf("autosave_%d.sav", time.Now().UnixNano())
	screenshotURL := h.tryWriteSessionScreenshot(session.ID, body.Screenshot)

	path, size, err := h.Storage.WriteSessionSave(session.ID, filename, save)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to save file")
	}

	rec := db.SessionSaveState{
		SessionID:     session.ID,
		UserID:        uid,
		Name:          "Auto Save",
		FilePath:      path,
		FileSize:      size,
		ScreenshotURL: screenshotURL,
		CoreName:      body.CoreName,
		CoreSha256:    sanitizeCoreSha256(body.CoreSha256),
		IsAuto:        true,
		IsCurrent:     true,
	}
	if err := h.DB.Create(&rec).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to create save record")
	}

	// Prune old auto-saves — retention count depends on save size.
	maxAutoSaves := autoSaveRetentionLimit(size)
	var oldSaves []db.SessionSaveState
	h.DB.Where("session_id = ? AND is_auto = ? AND id != ?", session.ID, true, rec.ID).
		Order("created_at DESC").Offset(maxAutoSaves - 1).Find(&oldSaves)
	for _, old := range oldSaves {
		h.Storage.DeleteSave(old.FilePath)
		if old.ScreenshotURL != "" {
			h.Storage.DeleteSave(h.Storage.ImagePath(old.ScreenshotURL))
		}
		h.DB.Delete(&old)
	}

	h.touchSessionAfterUpload(session, uid, screenshotURL, body.CoreName)
	h.DB.Preload("User").First(&rec, rec.ID)

	return &SessionSaveUploadOutput{
		Body: h.toSaveResponse(rec, h.getRecommendedCoreName(session.GameID)),
	}, nil
}

// HumaUpsertSlotSave is the huma implementation of PUT
// /api/sessions/{id}/saves/slot/{slot}. Validates the slot range, then either
// inserts a new slot save or updates the existing one in place.
func (h *SessionHandler) HumaUpsertSlotSave(ctx context.Context, in *SessionSlotSaveUploadInput) (*SessionSaveUploadOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	slotNum, err := strconv.Atoi(in.Slot)
	if err != nil || slotNum < 1 || slotNum > 10 {
		return nil, huma.Error400BadRequest("slot must be between 1 and 10")
	}

	body := in.RawBody.Data()
	save := body.Save
	if !save.IsSet {
		return nil, huma.Error400BadRequest("save file required")
	}
	defer save.Close()

	if err := checkStorageQuota(h.DB, uid, save.Size); err != nil {
		return nil, huma.Error413RequestEntityTooLarge("storage quota exceeded")
	}

	filename := fmt.Sprintf("slot_%d.sav", slotNum)
	screenshotURL := h.tryWriteSessionScreenshot(session.ID, body.Screenshot)

	path, size, err := h.Storage.WriteSessionSave(session.ID, filename, save)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to save file")
	}

	var rec db.SessionSaveState
	result := h.DB.Where("session_id = ? AND slot = ?", session.ID, slotNum).First(&rec)
	if result.Error == gorm.ErrRecordNotFound {
		rec = db.SessionSaveState{
			SessionID:     session.ID,
			UserID:        uid,
			Name:          fmt.Sprintf("Slot %d", slotNum),
			FilePath:      path,
			FileSize:      size,
			ScreenshotURL: screenshotURL,
			CoreName:      body.CoreName,
			CoreSha256:    sanitizeCoreSha256(body.CoreSha256),
			IsAuto:        false,
			Slot:          &slotNum,
		}
		h.DB.Create(&rec)
	} else {
		rec.FilePath = path
		rec.FileSize = size
		rec.ScreenshotURL = screenshotURL
		rec.CoreName = body.CoreName
		rec.CoreSha256 = sanitizeCoreSha256(body.CoreSha256)
		h.DB.Save(&rec)
	}

	now := time.Now()
	updates := map[string]interface{}{"last_played_at": now, "last_played_by": uid}
	if screenshotURL != "" {
		updates["screenshot_url"] = screenshotURL
	}
	if sha := h.pinSessionCoreSha256(session, body.CoreName); sha != "" {
		updates["pinned_core_sha256"] = sha
	}
	h.DB.Model(&session).Updates(updates)

	h.DB.Preload("User").First(&rec, rec.ID)
	return &SessionSaveUploadOutput{
		Body: h.toSaveResponse(rec, h.getRecommendedCoreName(session.GameID)),
	}, nil
}

// HumaUploadSRAM is the huma implementation of POST /api/sessions/{id}/sram.
// Mirrors the upsert behaviour: 201 on first upload, 200 on overwrite. The
// body shape is the raw db.SessionSaveData (matching the gin handler), kept
// for wire-format compatibility with existing clients.
func (h *SessionHandler) HumaUploadSRAM(ctx context.Context, in *SessionSRAMUploadInput) (*SessionSRAMUploadOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	body := in.RawBody.Data()
	file := body.File
	if !file.IsSet {
		return nil, huma.Error400BadRequest("file required")
	}
	defer file.Close()

	if err := checkStorageQuota(h.DB, uid, file.Size); err != nil {
		return nil, huma.Error413RequestEntityTooLarge("storage quota exceeded")
	}

	path, size, err := h.Storage.WriteSessionSRAM(session.ID, file.Filename, file)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to save SRAM file")
	}

	var existing db.SessionSaveData
	result := h.DB.Where("session_id = ?", session.ID).First(&existing)
	if result.Error == gorm.ErrRecordNotFound {
		sd := db.SessionSaveData{
			SessionID: session.ID,
			FilePath:  path,
			FileSize:  size,
		}
		h.DB.Create(&sd)
		return &SessionSRAMUploadOutput{Status: http.StatusCreated, Body: sd}, nil
	}
	if existing.FilePath != path {
		h.Storage.DeleteSave(existing.FilePath)
	}
	existing.FilePath = path
	existing.FileSize = size
	h.DB.Save(&existing)
	return &SessionSRAMUploadOutput{Status: http.StatusOK, Body: existing}, nil
}

// --- Shared helpers used by the four upload handlers above -----------------

// tryWriteSessionScreenshot writes an optional screenshot to storage. Returns
// the relative storage URL on success, "" on failure or when no screenshot
// was attached. Mirrors the gin pattern of silently ignoring screenshot
// errors so a flaky screenshot doesn't fail the save upload.
func (h *SessionHandler) tryWriteSessionScreenshot(sessionID uint, screenshot huma.FormFile) string {
	if !screenshot.IsSet {
		return ""
	}
	defer screenshot.Close()
	stored, err := h.Storage.WriteSessionScreenshot(sessionID, screenshot.Filename, screenshot)
	if err != nil {
		return ""
	}
	return stored
}

// touchSessionAfterUpload updates the session's last_played_at, last_played_by,
// optional screenshot URL and optional core name. Pulled into a helper so the
// three save-upload handlers share identical update semantics.
//
// This also lazily pins PinnedCoreSha256 on the first save that carries a
// resolvable coreName — see pinSessionCoreSha256 for the policy. We do the
// pin here (instead of inside each upload handler) so manual and auto-save
// paths stay in lockstep.
func (h *SessionHandler) touchSessionAfterUpload(session db.GameSession, uid uint, screenshotURL, coreName string) {
	now := time.Now()
	updates := map[string]interface{}{"last_played_at": now, "last_played_by": uid}
	if screenshotURL != "" {
		updates["screenshot_url"] = screenshotURL
	}
	if coreName != "" {
		updates["core_name"] = coreName
	}
	if sha := h.pinSessionCoreSha256(session, coreName); sha != "" {
		updates["pinned_core_sha256"] = sha
	}
	h.DB.Model(&session).Updates(updates)
}

// sanitizeCoreSha256 accepts the raw coreSha256 string from a save
// upload body and returns the lowercase hex form if it's a valid
// 64-char hex sha256. Anything malformed (wrong length, non-hex
// characters, whitespace) is silently dropped to "" — the field is
// diagnostic metadata, so we prefer "no value" over a 400 that would
// reject the whole save. See #555 Phase 3.
func sanitizeCoreSha256(raw string) string {
	if !isValidSha256Hex(raw) {
		return ""
	}
	return strings.ToLower(raw)
}

// pinSessionCoreSha256 returns the sha256 to persist into
// GameSession.PinnedCoreSha256, or "" if the pin should be left alone.
//
// Rules:
//   - If the session already has a pinned SHA, return "" (never overwrite).
//   - If coreName is empty, return "" (nothing to resolve).
//   - Otherwise look up the Core by name; if it has a non-empty Sha256,
//     return it. If no row matches or its SHA is still empty (e.g. the core
//     binary has never been served so ensureCoreMetadata hasn't run yet),
//     return "" — we'll pick it up on the next save.
//
// This is intentionally lossy: sessions can go a few saves before a pin
// lands, which is fine because the pin is an optimization — the player
// will fall back to the latest binary if PinnedCoreSha256 is empty.
func (h *SessionHandler) pinSessionCoreSha256(session db.GameSession, coreName string) string {
	if session.PinnedCoreSha256 != "" {
		return ""
	}
	if coreName == "" {
		return ""
	}
	var core db.Core
	if err := h.DB.Where("name = ?", coreName).First(&core).Error; err != nil {
		return ""
	}
	return core.Sha256
}
