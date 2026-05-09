package api

import (
	"context"
	"crypto/subtle"
	"net/http"
	"strconv"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// --- Inputs ------------------------------------------------------------------

// SharedSaveUploadBody is the multipart body for POST /api/games/{id}/shared-saves.
// Required fields are validated in the handler so the existing 1:1 gin error
// messages are preserved instead of huma's generic 422.
type SharedSaveUploadBody struct {
	Save          huma.FormFile `form:"save" required:"false" contentType:"application/octet-stream" doc:"Save state file. Required."`
	Name          string        `form:"name" required:"false" doc:"Display name for the shared save (defaults to the uploaded filename)."`
	Description   string        `form:"description" required:"false" doc:"Optional description shown alongside the save."`
	ScreenshotURL string        `form:"screenshotUrl" required:"false" doc:"Optional pre-existing screenshot URL to attach."`
}

// SharedSaveUploadInput is the input for POST /api/games/{id}/shared-saves.
type SharedSaveUploadInput struct {
	ID      string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
	RawBody huma.MultipartFormFiles[SharedSaveUploadBody]
}

// SharedSaveUploadOutput wraps the shared-save creation response. Returns 201
// Created via huma.Operation.DefaultStatus.
type SharedSaveUploadOutput struct {
	Body SharedSaveResponse
}

// SharedSessionUploadSaveBody is the multipart body for the two
// shared-session multipart upload endpoints. Auto-save uses screenshotUrl
// only — the manual save additionally accepts a name.
type SharedSessionUploadSaveBody struct {
	Save          huma.FormFile `form:"save" required:"false" contentType:"application/octet-stream" doc:"Save state file. Required."`
	Name          string        `form:"name" required:"false" doc:"Display name for the save (defaults to the uploaded filename). Manual saves only."`
	ScreenshotURL string        `form:"screenshotUrl" required:"false" doc:"Optional pre-existing screenshot URL to attach."`
}

// SharedSessionUploadSaveInput wraps the path parameter and multipart body
// plus the X-Turn-Token header that authorises the upload.
type SharedSessionUploadSaveInput struct {
	ID        string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Shared session ID."`
	TurnToken string `header:"X-Turn-Token" required:"false" doc:"Token proving the caller currently holds the turn (returned by joinSharedSession / takeTurn)."`
	RawBody   huma.MultipartFormFiles[SharedSessionUploadSaveBody]
}

// SharedSessionUploadSaveOutput wraps the resulting shared-session save
// response. Manual saves return 201, auto-saves return 200 — DefaultStatus on
// the operation handles the differentiation.
type SharedSessionUploadSaveOutput struct {
	Body SharedSessionSaveResponse
}

// --- Registration ------------------------------------------------------------

// RegisterSharedUploadRoutes wires the three shared-save / shared-session
// multipart upload endpoints into the huma API:
//
//	POST /api/games/{id}/shared-saves                community shared-save creation
//	POST /api/shared-sessions/{id}/saves             manual save in a shared session (turn-locked)
//	POST /api/shared-sessions/{id}/saves/auto        auto-save in a shared session (turn-locked)
//
// All three accept multipart uploads and were previously the last raw-gin
// endpoints in this surface. Body-size enforcement moves from the per-handler
// http.MaxBytesReader call to huma.Operation.MaxBodyBytes.
func RegisterSharedUploadRoutes(
	api huma.API,
	sharedSaveH *SharedSaveHandler,
	sharedSessionH *SharedSessionHandler,
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
		OperationID:   "shareSave",
		Method:        http.MethodPost,
		Path:          "/api/games/{id}/shared-saves",
		Summary:       "Share a save state with the community",
		Description:   "Uploads a save state for the specified game and publishes it as a community shared save. Other users can browse/download it from the game page.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"shared-saves"},
		Middlewares:   uploadMW,
		Security:      sec,
		MaxBodyBytes:  maxSaveUploadBytes(),
	}, sharedSaveH.HumaShareSave)

	huma.Register(api, huma.Operation{
		OperationID:   "uploadSharedSessionSave",
		Method:        http.MethodPost,
		Path:          "/api/shared-sessions/{id}/saves",
		Summary:       "Upload a manual save to a shared session",
		Description:   "Stores a manual save state in the shared session. Requires the caller to currently hold the turn and to present a matching X-Turn-Token header. If the shared session has a backing GameSession, a parallel SessionSaveState row is created so the host can resume locally.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"shared-sessions"},
		Middlewares:   uploadMW,
		Security:      sec,
		MaxBodyBytes:  maxSaveUploadBytes(),
	}, sharedSessionH.HumaUploadSharedSessionSave)

	huma.Register(api, huma.Operation{
		OperationID:  "uploadSharedSessionAutoSave",
		Method:       http.MethodPost,
		Path:         "/api/shared-sessions/{id}/saves/auto",
		Summary:      "Upload an auto-save to a shared session",
		Description:  "Replaces the shared session's auto-save (one auto-save is kept per shared session). Same turn + X-Turn-Token rules as the manual upload.",
		Tags:         []string{"shared-sessions"},
		Middlewares:  uploadMW,
		Security:     sec,
		MaxBodyBytes: maxSaveUploadBytes(),
	}, sharedSessionH.HumaUploadSharedSessionAutoSave)
}

// --- Handlers ----------------------------------------------------------------

// HumaShareSave is the huma implementation of POST /api/games/{id}/shared-saves.
// Mirrors the gin handler 1:1: validates the game, creates the DB record with
// a placeholder file path so the ID is allocated, writes the file, then
// updates the record with the final path and size.
func (h *SharedSaveHandler) HumaShareSave(ctx context.Context, in *SharedSaveUploadInput) (*SharedSaveUploadOutput, error) {
	uid := UserIDFromContext(ctx)

	gid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	body := in.RawBody.Data()
	save := body.Save
	if !save.IsSet {
		return nil, huma.Error400BadRequest("save file required")
	}
	defer save.Close()

	name := body.Name
	if name == "" {
		name = save.Filename
	}

	shared := db.SharedSaveState{
		UserID:        uid,
		GameID:        uint(gid),
		Name:          name,
		Description:   body.Description,
		ScreenshotURL: body.ScreenshotURL,
	}
	if err := h.DB.Create(&shared).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to create shared save record")
	}

	filePath, fileSize, err := h.Storage.WriteSharedSave(uint(gid), shared.ID, save.Filename, save)
	if err != nil {
		h.DB.Unscoped().Delete(&shared)
		return nil, huma.Error500InternalServerError("failed to save file")
	}

	shared.FilePath = filePath
	shared.FileSize = fileSize
	if err := h.DB.Save(&shared).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update shared save record")
	}

	CreateActivityEvent(h.DB, h.Hub, uid, "shared_save", uint(gid), SharedSaveMetadata{
		SaveName: name,
	})

	return &SharedSaveUploadOutput{Body: h.toResponse(shared, uid)}, nil
}

// HumaUploadSharedSessionSave is the huma implementation of POST
// /api/shared-sessions/{id}/saves. Manual save with turn enforcement.
func (h *SharedSessionHandler) HumaUploadSharedSessionSave(ctx context.Context, in *SharedSessionUploadSaveInput) (*SharedSessionUploadSaveOutput, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}
	if err := h.requireTurnAndToken(ss, uid, in.TurnToken); err != nil {
		return nil, err
	}

	body := in.RawBody.Data()
	save := body.Save
	if !save.IsSet {
		return nil, huma.Error400BadRequest("save file required")
	}
	defer save.Close()

	name := body.Name
	if name == "" {
		name = save.Filename
	}

	filePath, size, err := h.Storage.WriteSharedSessionSave(ss.ID, save.Filename, save)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to save file")
	}

	rec := db.SharedSessionSave{
		SharedSessionID: ss.ID,
		UserID:          uid,
		Name:            name,
		FilePath:        filePath,
		FileSize:        size,
		ScreenshotURL:   body.ScreenshotURL,
		IsAuto:          false,
	}
	if err := h.DB.Create(&rec).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to create save record")
	}

	if ss.SessionID != nil {
		sessionSave := db.SessionSaveState{
			SessionID:     *ss.SessionID,
			UserID:        uid,
			Name:          name,
			FilePath:      filePath,
			FileSize:      size,
			ScreenshotURL: body.ScreenshotURL,
			IsAuto:        false,
		}
		h.DB.Create(&sessionSave)
	}

	h.DB.Preload("User").First(&rec, rec.ID)
	h.broadcastSharedSessionSaveUploaded(ss.ID, rec.ID, uid, false)

	return &SharedSessionUploadSaveOutput{Body: h.toSharedSessionSaveResponse(rec)}, nil
}

// HumaUploadSharedSessionAutoSave is the huma implementation of POST
// /api/shared-sessions/{id}/saves/auto. Auto-save with turn enforcement;
// upserts so only one auto-save is retained per shared session.
func (h *SharedSessionHandler) HumaUploadSharedSessionAutoSave(ctx context.Context, in *SharedSessionUploadSaveInput) (*SharedSessionUploadSaveOutput, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}
	if err := h.requireTurnAndToken(ss, uid, in.TurnToken); err != nil {
		return nil, err
	}

	body := in.RawBody.Data()
	save := body.Save
	if !save.IsSet {
		return nil, huma.Error400BadRequest("save file required")
	}
	defer save.Close()

	const autoSaveFilename = "autosave.sav"
	filePath, size, err := h.Storage.WriteSharedSessionSave(ss.ID, autoSaveFilename, save)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to save file")
	}

	var rec db.SharedSessionSave
	result := h.DB.Where("shared_session_id = ? AND is_auto = ?", ss.ID, true).First(&rec)
	if result.Error == gorm.ErrRecordNotFound {
		rec = db.SharedSessionSave{
			SharedSessionID: ss.ID,
			UserID:          uid,
			Name:            "Auto Save",
			FilePath:        filePath,
			FileSize:        size,
			ScreenshotURL:   body.ScreenshotURL,
			IsAuto:          true,
		}
		h.DB.Create(&rec)
	} else {
		rec.UserID = uid
		rec.FilePath = filePath
		rec.FileSize = size
		rec.ScreenshotURL = body.ScreenshotURL
		h.DB.Save(&rec)
	}

	if ss.SessionID != nil {
		sessionSave := db.SessionSaveState{
			SessionID:     *ss.SessionID,
			UserID:        uid,
			Name:          "Auto Save",
			FilePath:      filePath,
			FileSize:      size,
			ScreenshotURL: body.ScreenshotURL,
			IsAuto:        true,
		}
		h.DB.Create(&sessionSave)
	}

	h.DB.Preload("User").First(&rec, rec.ID)
	h.broadcastSharedSessionSaveUploaded(ss.ID, rec.ID, uid, true)

	return &SharedSessionUploadSaveOutput{Body: h.toSharedSessionSaveResponse(rec)}, nil
}

// --- Helpers shared by the three handlers above ------------------------------

// requireTurnAndToken enforces both turn ownership and the constant-time
// X-Turn-Token comparison the gin handlers performed inline.
func (h *SharedSessionHandler) requireTurnAndToken(ss db.SharedSession, uid uint, token string) error {
	if ss.ActiveUserID == nil || *ss.ActiveUserID != uid {
		return huma.Error403Forbidden("you must hold the turn to upload saves")
	}
	if token == "" || subtle.ConstantTimeCompare([]byte(token), []byte(ss.TurnToken)) != 1 {
		return huma.Error403Forbidden("invalid turn token")
	}
	return nil
}

// broadcastSharedSessionSaveUploaded fans out the WS event for both the
// manual-save and auto-save handlers; pulled into a helper so the two
// handlers share the encoding logic.
func (h *SharedSessionHandler) broadcastSharedSessionSaveUploaded(sharedSessionID, saveID, uid uint, isAuto bool) {
	if h.Hub == nil {
		return
	}
	h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionSaveUploaded, Payload: ws.SharedSessionSaveUploadedPayload{
		SharedSessionID: strconv.FormatUint(uint64(sharedSessionID), 10),
		SaveID:          strconv.FormatUint(uint64(saveID), 10),
		UserID:          strconv.FormatUint(uint64(uid), 10),
		IsAuto:          isAuto,
	}})
}
