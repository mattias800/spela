package api

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// humaFilepathBase returns the final element of a filesystem path. Thin wrapper
// kept local so nothing else in the package competes for the name `filepathBase`.
func humaFilepathBase(p string) string {
	return filepath.Base(p)
}

// --- Input / output types ---

// ListGameSessionsInput is the input for GET /api/games/{id}/sessions.
type ListGameSessionsInput struct {
	ID string `path:"id" doc:"Game ID."`
}

// ListGameSessionsOutput wraps the list response.
type ListGameSessionsOutput struct {
	Body []GameSessionResponse
}

// GetSessionInput is the input for GET /api/sessions/{id}.
type GetSessionInput struct {
	ID string `path:"id" doc:"Session ID."`
}

// GetSessionOutput wraps a single session response.
type GetSessionOutput struct {
	Body GameSessionResponse
}

// CreateSessionInput is the input for POST /api/games/{id}/sessions.
type CreateSessionInput struct {
	ID   string `path:"id" doc:"Game ID."`
	Body CreateSessionRequest
}

// CreateSessionOutput wraps the created session response (201).
type CreateSessionOutput struct {
	Body GameSessionResponse
}

// CreateSessionFromSharedSaveInput is the input for POST
// /api/games/{id}/sessions/from-shared-save/{saveId}.
type CreateSessionFromSharedSaveInput struct {
	ID     string `path:"id" doc:"Game ID."`
	SaveID string `path:"saveId" doc:"Shared save ID to seed the new session from."`
}

// CreateSessionFromSharedSaveOutput wraps the created session response (201).
type CreateSessionFromSharedSaveOutput struct {
	Body GameSessionResponse
}

// UpdateSessionInput is the input for PUT /api/sessions/{id}.
type UpdateSessionInput struct {
	ID   string `path:"id" doc:"Session ID."`
	Body UpdateSessionRequest
}

// UpdateSessionOutput wraps the updated session response.
type UpdateSessionOutput struct {
	Body GameSessionResponse
}

// DeleteSessionInput is the input for DELETE /api/sessions/{id}.
type DeleteSessionInput struct {
	ID string `path:"id" doc:"Session ID."`
}

// DeleteSessionOutput wraps the delete success message.
type DeleteSessionOutput struct {
	Body MessageResponse
}

// DuplicateSessionInput is the input for POST /api/sessions/{id}/duplicate
// (and its alias POST /api/sessions/{id}/clone). The request body is optional
// — a missing body defaults to copying the source session's name with
// " (Copy)" appended. SaveID optionally seeds the new session from a
// specific save instead of the most recent; 0 (the default for a missing
// query param) means "use the most-recent save".
type DuplicateSessionInput struct {
	ID     string `path:"id" doc:"Session ID to clone."`
	SaveID uint   `query:"saveId" required:"false" doc:"Optional save ID to clone from. Defaults to the most recent save when omitted or zero."`
	Body   *DuplicateSessionRequest
}

// DuplicateSessionOutput wraps the duplicated session response (201 Created).
type DuplicateSessionOutput struct {
	Body GameSessionResponse
}

// ListSessionSavesInput is the input for GET /api/sessions/{id}/saves and the slot variant.
type ListSessionSavesInput struct {
	ID string `path:"id" doc:"Session ID."`
}

// ListSessionSavesOutput wraps the saves list.
type ListSessionSavesOutput struct {
	Body []SessionSaveResponse
}

// DeleteSessionSaveInput is the input for DELETE /api/sessions/{id}/saves/{saveId}.
type DeleteSessionSaveInput struct {
	ID     string `path:"id" doc:"Session ID."`
	SaveID string `path:"saveId" doc:"Save ID."`
}

// DeleteSessionSaveOutput wraps the delete success message.
type DeleteSessionSaveOutput struct {
	Body MessageResponse
}

// UpdateSessionSaveInput is the input for PUT /api/sessions/{id}/saves/{saveId}.
type UpdateSessionSaveInput struct {
	ID     string `path:"id" doc:"Session ID."`
	SaveID string `path:"saveId" doc:"Save ID."`
	Body   UpdateSessionSaveRequest
}

// UpdateSessionSaveOutput wraps the updated save.
type UpdateSessionSaveOutput struct {
	Body SessionSaveResponse
}

// BulkDeleteSessionSavesInput is the input for DELETE /api/sessions/{id}/saves.
type BulkDeleteSessionSavesInput struct {
	ID string `path:"id" doc:"Session ID."`
}

// BulkDeleteSessionSavesResponse mirrors the raw gin handler body.
type BulkDeleteSessionSavesResponse struct {
	DeletedCount int   `json:"deletedCount"`
	FreedBytes   int64 `json:"freedBytes"`
}

// BulkDeleteSessionSavesOutput wraps the bulk-delete summary.
type BulkDeleteSessionSavesOutput struct {
	Body BulkDeleteSessionSavesResponse
}

// UpdateSessionPlayTimeInput is the input for POST /api/sessions/{id}/play-time.
type UpdateSessionPlayTimeInput struct {
	ID   string `path:"id" doc:"Session ID."`
	Body UpdateSessionPlayTimeRequest
}

// UpdateSessionPlayTimeOutput wraps the updated session response.
type UpdateSessionPlayTimeOutput struct {
	Body GameSessionResponse
}

// StopPlayingSessionInput is the input for DELETE /api/sessions/{id}/play-time.
type StopPlayingSessionInput struct {
	ID string `path:"id" doc:"Session ID."`
}

// StopPlayingSessionOutput wraps the stop-playing success message.
type StopPlayingSessionOutput struct {
	Body MessageResponse
}

// SessionCheatsResponse mirrors the raw gin wire format for GET/PUT cheats.
type SessionCheatsResponse struct {
	CheatsEnabled  bool  `json:"cheatsEnabled"`
	EnabledIndices []int `json:"enabledIndices"`
}

// GetSessionCheatsInput is the input for GET /api/sessions/{id}/cheats.
type GetSessionCheatsInput struct {
	ID string `path:"id" doc:"Session ID."`
}

// GetSessionCheatsOutput wraps the cheats response.
type GetSessionCheatsOutput struct {
	Body SessionCheatsResponse
}

// UpdateSessionCheatsInput is the input for PUT /api/sessions/{id}/cheats.
type UpdateSessionCheatsInput struct {
	ID   string `path:"id" doc:"Session ID."`
	Body UpdateSessionCheatsRequest
}

// UpdateSessionCheatsOutput wraps the updated cheats response.
type UpdateSessionCheatsOutput struct {
	Body SessionCheatsResponse
}

// RegisterSessionRoutes wires session endpoints into the huma API. File-based
// endpoints (save uploads + downloads, auto-save upload/download, SRAM
// upload/download) stay on raw gin in this batch because they rely on
// multipart form handling or c.File() streaming.
func RegisterSessionRoutes(api huma.API, h *SessionHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "listGameSessions",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/sessions",
		Summary:     "List sessions for a game",
		Description: "Returns the caller's sessions for the specified game plus any backing sessions from shared sessions they're a member of.",
		Tags:        []string{"sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListSessions)

	huma.Register(api, huma.Operation{
		OperationID:   "createSession",
		Method:        http.MethodPost,
		Path:          "/api/games/{id}/sessions",
		Summary:       "Create a session for a game",
		Description:   "Creates a new game session owned by the authenticated user.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"sessions"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaCreateSession)

	huma.Register(api, huma.Operation{
		OperationID:   "createSessionFromSharedSave",
		Method:        http.MethodPost,
		Path:          "/api/games/{id}/sessions/from-shared-save/{saveId}",
		Summary:       "Create a session from a community shared save",
		Description:   "Creates a new game session for the caller seeded from a community shared save. Copies the shared save file into the new session and increments the shared save's download counter.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"sessions"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaCreateSessionFromSharedSave)

	huma.Register(api, huma.Operation{
		OperationID: "getSession",
		Method:      http.MethodGet,
		Path:        "/api/sessions/{id}",
		Summary:     "Get a session by ID",
		Description: "Returns session details including shared-session membership metadata when applicable.",
		Tags:        []string{"sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetSession)

	huma.Register(api, huma.Operation{
		OperationID: "updateSession",
		Method:      http.MethodPut,
		Path:        "/api/sessions/{id}",
		Summary:     "Update a session's name, cheats flag, or core",
		Description: "Owner-only. Partial update — nil fields are left unchanged.",
		Tags:        []string{"sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateSession)

	huma.Register(api, huma.Operation{
		OperationID: "deleteSession",
		Method:      http.MethodDelete,
		Path:        "/api/sessions/{id}",
		Summary:     "Delete a session and its saves",
		Description: "Owner-only. Deletes all save state files and records, SRAM data, and the session directory.",
		Tags:        []string{"sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDeleteSession)

	huma.Register(api, huma.Operation{
		OperationID:   "cloneSession",
		Method:        http.MethodPost,
		Path:          "/api/sessions/{id}/clone",
		Summary:       "Clone a session",
		Description:   "Creates a copy of the session owned by the caller. Inherits TotalPlayTime and PinnedCoreSha256 from the source. Copies SRAM, cheats, the session screenshot, and one save state (the most recent, or the save identified by ?saveId= when provided). Access: owner or any shared-session member.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"sessions"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaDuplicateSession)

	huma.Register(api, huma.Operation{
		OperationID:   "duplicateSession",
		Method:        http.MethodPost,
		Path:          "/api/sessions/{id}/duplicate",
		Summary:       "Duplicate a session (deprecated alias of clone)",
		Description:   "Deprecated — use POST /api/sessions/{id}/clone instead. Behaviour is identical; this path is kept so in-flight clients keep working.",
		DefaultStatus: http.StatusCreated,
		Deprecated:    true,
		Tags:          []string{"sessions"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaDuplicateSession)

	huma.Register(api, huma.Operation{
		OperationID: "listSessionSaves",
		Method:      http.MethodGet,
		Path:        "/api/sessions/{id}/saves",
		Summary:     "List save states in a session",
		Description: "Returns all save states (manual, auto, slot) for the specified session ordered by creation time descending.",
		Tags:        []string{"sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListSessionSaves)

	huma.Register(api, huma.Operation{
		OperationID: "listSlotSaves",
		Method:      http.MethodGet,
		Path:        "/api/sessions/{id}/saves/slots",
		Summary:     "List slot saves for a session",
		Description: "Returns all slot-based saves (slots 1-10) for the specified session ordered by slot number.",
		Tags:        []string{"sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListSlotSaves)

	huma.Register(api, huma.Operation{
		OperationID: "bulkDeleteSessionSaves",
		Method:      http.MethodDelete,
		Path:        "/api/sessions/{id}/saves",
		Summary:     "Delete all saves for a session",
		Description: "Owner-only. Removes every save state (auto, manual, slot) from disk and DB. The session itself is kept.",
		Tags:        []string{"sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaBulkDeleteSessionSaves)

	huma.Register(api, huma.Operation{
		OperationID: "deleteSessionSave",
		Method:      http.MethodDelete,
		Path:        "/api/sessions/{id}/saves/{saveId}",
		Summary:     "Delete a single save state",
		Description: "Owner-only. Removes the specified save state's file and record.",
		Tags:        []string{"sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDeleteSessionSave)

	huma.Register(api, huma.Operation{
		OperationID: "updateSessionSave",
		Method:      http.MethodPut,
		Path:        "/api/sessions/{id}/saves/{saveId}",
		Summary:     "Update a save state's name or notes",
		Description: "Owner-only. Partial update — nil fields are left unchanged.",
		Tags:        []string{"sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateSessionSave)

	huma.Register(api, huma.Operation{
		OperationID: "updateSessionPlayTime",
		Method:      http.MethodPost,
		Path:        "/api/sessions/{id}/play-time",
		Summary:     "Record play time for a session",
		Description: "Owner-only. Adds the supplied seconds to the session's total play time and marks the caller as last played by.",
		Tags:        []string{"sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateSessionPlayTime)

	huma.Register(api, huma.Operation{
		OperationID: "stopPlayingSession",
		Method:      http.MethodDelete,
		Path:        "/api/sessions/{id}/play-time",
		Summary:     "Clear the last-played-by marker on a session",
		Description: "Owner-only. Clears the session's last_played_by field.",
		Tags:        []string{"sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaStopPlayingSession)

	huma.Register(api, huma.Operation{
		OperationID: "getSessionCheats",
		Method:      http.MethodGet,
		Path:        "/api/sessions/{id}/cheats",
		Summary:     "Get cheat configuration for a session",
		Description: "Returns cheatsEnabled flag and the list of enabled cheat indices.",
		Tags:        []string{"sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetSessionCheats)

	huma.Register(api, huma.Operation{
		OperationID: "updateSessionCheats",
		Method:      http.MethodPut,
		Path:        "/api/sessions/{id}/cheats",
		Summary:     "Update cheat configuration for a session",
		Description: "Owner-only. Replaces the session's enabled-cheats set and updates the cheatsEnabled flag.",
		Tags:        []string{"sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateSessionCheats)

}

// --- Handlers ---

// humaLoadSessionWithOwnerCheck is the huma-flavoured equivalent of the gin
// loadSessionWithOwnerCheck helper: it loads a session by string ID and
// verifies ownership, returning a typed huma error on failure.
func (h *SessionHandler) humaLoadSessionWithOwnerCheck(sessionID string, uid uint) (db.GameSession, error) {
	sid, err := strconv.ParseUint(sessionID, 10, 64)
	if err != nil {
		return db.GameSession{}, huma.Error400BadRequest("invalid session ID")
	}
	var session db.GameSession
	if err := h.DB.Preload("Owner").First(&session, sid).Error; err != nil {
		return db.GameSession{}, huma.Error404NotFound("session not found")
	}
	if session.OwnerID != uid {
		return db.GameSession{}, huma.Error403Forbidden("not the session owner")
	}
	return session, nil
}

// HumaListSessions is the huma handler for GET /api/games/{id}/sessions.
func (h *SessionHandler) HumaListSessions(ctx context.Context, in *ListGameSessionsInput) (*ListGameSessionsOutput, error) {
	uid := UserIDFromContext(ctx)
	gid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var ownSessions []db.GameSession
	if err := h.DB.Where("owner_id = ? AND game_id = ?", uid, gid).
		Preload("Owner").
		Order("updated_at DESC").
		Find(&ownSessions).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch sessions")
	}

	ownSessionIDs := make(map[uint]bool, len(ownSessions))
	for _, s := range ownSessions {
		ownSessionIDs[s.ID] = true
	}

	var sharedSessions []db.SharedSession
	h.DB.Joins("JOIN shared_session_members ON shared_session_members.shared_session_id = shared_sessions.id").
		Where("shared_session_members.user_id = ? AND shared_sessions.game_id = ? AND shared_sessions.session_id IS NOT NULL AND shared_session_members.deleted_at IS NULL", uid, gid).
		Preload("Members.User").
		Find(&sharedSessions)

	sharedSessionMap := make(map[uint]*db.SharedSession, len(sharedSessions))
	for i := range sharedSessions {
		if sharedSessions[i].SessionID != nil {
			sharedSessionMap[*sharedSessions[i].SessionID] = &sharedSessions[i]
		}
	}

	var extraSessionIDs []uint
	for sid := range sharedSessionMap {
		if !ownSessionIDs[sid] {
			extraSessionIDs = append(extraSessionIDs, sid)
		}
	}

	var extraSessions []db.GameSession
	if len(extraSessionIDs) > 0 {
		h.DB.Where("id IN ?", extraSessionIDs).
			Preload("Owner").
			Find(&extraSessions)
	}

	allSessions := append(ownSessions, extraSessions...)
	saveCounts := h.getSaveCounts(allSessions)
	lastPlayedByUsernames := h.resolveLastPlayedByUsernames(allSessions)

	result := make([]GameSessionResponse, len(allSessions))
	for i, s := range allSessions {
		resp := h.toSessionResponse(s, saveCounts[s.ID])
		if username, ok := lastPlayedByUsernames[s.ID]; ok {
			resp.LastPlayedByUsername = &username
		}
		if ss, ok := sharedSessionMap[s.ID]; ok {
			ssID := strconv.FormatUint(uint64(ss.ID), 10)
			resp.IsSharedSession = true
			resp.SharedSessionID = &ssID
			resp.MemberCount = len(ss.Members)
			usernames := make([]string, 0, len(ss.Members))
			avatars := make([]string, 0, len(ss.Members))
			for _, m := range ss.Members {
				usernames = append(usernames, m.User.Username)
				if m.User.AvatarURL != "" {
					avatars = append(avatars, m.User.AvatarURL)
				}
			}
			resp.MemberUsernames = usernames
			resp.MemberAvatars = avatars
		}
		result[i] = resp
	}

	return &ListGameSessionsOutput{Body: result}, nil
}

// HumaCreateSessionFromSharedSave is the huma handler for
// POST /api/games/{id}/sessions/from-shared-save/{saveId}.
func (h *SessionHandler) HumaCreateSessionFromSharedSave(ctx context.Context, in *CreateSessionFromSharedSaveInput) (*CreateSessionFromSharedSaveOutput, error) {
	uid := UserIDFromContext(ctx)

	gid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}
	sid, err := strconv.ParseUint(in.SaveID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid save ID")
	}

	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	var sharedSave db.SharedSaveState
	if err := h.DB.First(&sharedSave, sid).Error; err != nil {
		return nil, huma.Error404NotFound("shared save not found")
	}
	if sharedSave.GameID != uint(gid) {
		return nil, huma.Error400BadRequest("shared save does not belong to this game")
	}

	// Create the session record.
	session := db.GameSession{
		OwnerID: uid,
		GameID:  uint(gid),
		Name:    fmt.Sprintf("From: %s", sharedSave.Name),
	}
	if err := h.DB.Create(&session).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to create session")
	}

	// Copy the shared save file to the new session.
	srcFile, err := os.Open(sharedSave.FilePath)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to read shared save file")
	}
	defer srcFile.Close()

	filename := filepath.Base(sharedSave.FilePath)
	path, size, err := h.Storage.WriteSessionSave(session.ID, filename, srcFile)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to copy save file")
	}

	save := db.SessionSaveState{
		SessionID:     session.ID,
		UserID:        uid,
		Name:          sharedSave.Name,
		FilePath:      path,
		FileSize:      size,
		ScreenshotURL: sharedSave.ScreenshotURL,
		IsAuto:        false,
	}
	if err := h.DB.Create(&save).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to create save record")
	}

	// Bump the shared save's download counter.
	h.DB.Model(&sharedSave).UpdateColumn("download_count", gorm.Expr("download_count + ?", 1))

	h.DB.Preload("Owner").First(&session, session.ID)
	return &CreateSessionFromSharedSaveOutput{Body: h.toSessionResponse(session, 1)}, nil
}

// HumaCreateSession is the huma handler for POST /api/games/{id}/sessions.
func (h *SessionHandler) HumaCreateSession(ctx context.Context, in *CreateSessionInput) (*CreateSessionOutput, error) {
	uid := UserIDFromContext(ctx)
	gid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	if in.Body.Name == "" || len(in.Body.Name) > 255 {
		return nil, huma.Error400BadRequest("name is required and must be 255 characters or fewer")
	}
	if strings.TrimSpace(in.Body.Name) == "" {
		return nil, huma.Error400BadRequest("name cannot be empty")
	}

	session := db.GameSession{
		OwnerID: uid,
		GameID:  uint(gid),
		Name:    in.Body.Name,
	}
	if err := h.DB.Create(&session).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to create session")
	}

	h.DB.Preload("Owner").First(&session, session.ID)
	return &CreateSessionOutput{Body: h.toSessionResponse(session, 0)}, nil
}

// HumaGetSession is the huma handler for GET /api/sessions/{id}.
func (h *SessionHandler) HumaGetSession(ctx context.Context, in *GetSessionInput) (*GetSessionOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var saveCount int64
	h.DB.Model(&db.SessionSaveState{}).Where("session_id = ?", session.ID).Count(&saveCount)

	resp := h.toSessionResponse(session, int(saveCount))
	h.enrichWithSharedSessionData(&resp, session.ID)
	return &GetSessionOutput{Body: resp}, nil
}

// HumaUpdateSession is the huma handler for PUT /api/sessions/{id}.
func (h *SessionHandler) HumaUpdateSession(ctx context.Context, in *UpdateSessionInput) (*UpdateSessionOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	req := in.Body
	if req.Name != nil {
		if len(*req.Name) > 255 || strings.TrimSpace(*req.Name) == "" {
			return nil, huma.Error400BadRequest("name must be between 1 and 255 characters")
		}
		session.Name = *req.Name
	}
	if req.CheatsEnabled != nil {
		session.CheatsEnabled = *req.CheatsEnabled
	}
	if req.CoreName != nil {
		session.CoreName = *req.CoreName
	}
	if req.UserLockedCoreVersion != nil {
		session.UserLockedCoreVersion = *req.UserLockedCoreVersion
	}
	if req.AutoLoadSuppressed != nil {
		session.AutoLoadSuppressed = *req.AutoLoadSuppressed
	}
	if req.RehearsalCrashPending != nil {
		session.RehearsalCrashPending = *req.RehearsalCrashPending
	}

	if err := h.DB.Save(&session).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update session")
	}

	var saveCount int64
	h.DB.Model(&db.SessionSaveState{}).Where("session_id = ?", session.ID).Count(&saveCount)
	return &UpdateSessionOutput{Body: h.toSessionResponse(session, int(saveCount))}, nil
}

// HumaDeleteSession is the huma handler for DELETE /api/sessions/{id}.
func (h *SessionHandler) HumaDeleteSession(ctx context.Context, in *DeleteSessionInput) (*DeleteSessionOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var saves []db.SessionSaveState
	h.DB.Where("session_id = ?", session.ID).Find(&saves)
	for _, save := range saves {
		h.Storage.DeleteSave(save.FilePath)
		if save.ScreenshotURL != "" {
			h.Storage.DeleteSave(h.Storage.ImagePath(save.ScreenshotURL))
		}
	}
	h.DB.Where("session_id = ?", session.ID).Delete(&db.SessionSaveState{})

	var sramData []db.SessionSaveData
	h.DB.Where("session_id = ?", session.ID).Find(&sramData)
	for _, sd := range sramData {
		h.Storage.DeleteSave(sd.FilePath)
	}
	h.DB.Where("session_id = ?", session.ID).Delete(&db.SessionSaveData{})

	// SaveDir bundles (#864): the row pairs 1:1 with the session.
	// On-disk file lives under session_X/save_dir/ which is cleaned up by
	// the DeleteSessionDir call below — no per-file delete needed here.
	h.DB.Where("session_id = ?", session.ID).Delete(&db.SessionSaveDirBundle{})

	h.Storage.DeleteSessionDir(session.ID)
	h.DB.Delete(&session)

	return &DeleteSessionOutput{Body: MessageResponse{Message: "session deleted"}}, nil
}

// HumaDuplicateSession is the huma handler for
// POST /api/sessions/{id}/clone (and its deprecated alias .../duplicate).
//
// All DB writes happen inside a single gorm transaction so partial failures
// (a failed save-state copy, for example) don't leak a half-built session.
// Filesystem writes are done lazily alongside the DB writes; errors on a
// single save's file copy skip that save but keep the session — matching
// the pre-transaction behaviour that treated per-save file failures as
// recoverable.
//
// The three US-1/US-2/US-3 semantics come out of one code path:
//   - Access: owner OR member of a shared session backing this session.
//   - Name: body.name > "{source.Name} (Copy)".
//   - Seed save: SaveID (if provided) > most-recent save > no save.
//   - Inherited fields: CoreName, CheatsEnabled, ScreenshotURL, TotalPlayTime,
//     PinnedCoreSha256, plus a copy of every SessionCheatSetting and the
//     chosen SessionSaveState (with its screenshot) and the session's SRAM
//     if present.
func (h *SessionHandler) HumaDuplicateSession(ctx context.Context, in *DuplicateSessionInput) (*DuplicateSessionOutput, error) {
	uid := UserIDFromContext(ctx)
	sid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid session ID")
	}

	var session db.GameSession
	if err := h.DB.Preload("Owner").First(&session, sid).Error; err != nil {
		return nil, huma.Error404NotFound("session not found")
	}

	// Access check: owner OR member of shared session that backs this session.
	if session.OwnerID != uid {
		var count int64
		h.DB.Model(&db.SharedSessionMember{}).
			Joins("JOIN shared_sessions ON shared_sessions.id = shared_session_members.shared_session_id").
			Where("shared_sessions.session_id = ? AND shared_session_members.user_id = ?", session.ID, uid).
			Count(&count)
		if count == 0 {
			return nil, huma.Error403Forbidden("not the session owner or a shared session member")
		}
	}

	name := ""
	if in.Body != nil {
		name = strings.TrimSpace(in.Body.Name)
	}
	if name == "" {
		name = session.Name + " (Copy)"
	}
	if len(name) > 255 {
		name = name[:255]
	}

	// Resolve the seed save: explicit SaveID > most recent. Done before the
	// transaction so an invalid SaveID surfaces as 404 without any writes.
	var explicitSaveID *uint
	if in.SaveID != 0 {
		id := in.SaveID
		explicitSaveID = &id
	}
	seedSave, err := h.resolveCloneSeedSave(session.ID, explicitSaveID)
	if err != nil {
		return nil, err
	}

	var newSession db.GameSession
	txErr := h.DB.Transaction(func(tx *gorm.DB) error {
		newSession = db.GameSession{
			OwnerID:          uid,
			GameID:           session.GameID,
			Name:             name,
			CoreName:         session.CoreName,
			CheatsEnabled:    session.CheatsEnabled,
			TotalPlayTime:    session.TotalPlayTime,
			PinnedCoreSha256: session.PinnedCoreSha256,
		}
		if err := tx.Create(&newSession).Error; err != nil {
			return fmt.Errorf("create session: %w", err)
		}

		if seedSave != nil {
			if err := h.cloneSaveInto(tx, *seedSave, newSession.ID, uid); err != nil {
				return fmt.Errorf("clone save: %w", err)
			}
		}

		var sram db.SessionSaveData
		if err := tx.Where("session_id = ?", session.ID).First(&sram).Error; err == nil {
			newSramPath := h.Storage.SessionSRAMPath(newSession.ID, humaFilepathBase(sram.FilePath))
			size, copyErr := h.Storage.CopyFile(sram.FilePath, newSramPath)
			if copyErr != nil {
				return fmt.Errorf("copy sram: %w", copyErr)
			}
			newSram := db.SessionSaveData{
				SessionID: newSession.ID,
				FilePath:  newSramPath,
				FileSize:  size,
			}
			if err := tx.Create(&newSram).Error; err != nil {
				return fmt.Errorf("create sram record: %w", err)
			}
		}

		var cheats []db.SessionCheatSetting
		if err := tx.Where("session_id = ?", session.ID).Find(&cheats).Error; err != nil {
			return fmt.Errorf("load cheats: %w", err)
		}
		for _, cheat := range cheats {
			newCheat := db.SessionCheatSetting{
				SessionID:  newSession.ID,
				CheatIndex: cheat.CheatIndex,
				Enabled:    cheat.Enabled,
			}
			if err := tx.Create(&newCheat).Error; err != nil {
				return fmt.Errorf("create cheat: %w", err)
			}
		}

		if session.ScreenshotURL != "" {
			dstSubpath := h.Storage.SessionScreenshotSubpath(newSession.ID, humaFilepathBase(session.ScreenshotURL))
			if _, cpErr := h.Storage.CopyImageFile(session.ScreenshotURL, dstSubpath); cpErr == nil {
				if err := tx.Model(&newSession).Update("screenshot_url", dstSubpath).Error; err != nil {
					return fmt.Errorf("update screenshot url: %w", err)
				}
			}
			// A screenshot copy failure is non-fatal: we continue without the
			// screenshot rather than rolling back the whole clone.
		}
		return nil
	})
	if txErr != nil {
		return nil, huma.Error500InternalServerError("failed to clone session: " + txErr.Error())
	}

	h.DB.Preload("Owner").First(&newSession, newSession.ID)
	var saveCount int64
	h.DB.Model(&db.SessionSaveState{}).Where("session_id = ?", newSession.ID).Count(&saveCount)

	resp := h.toSessionResponse(newSession, int(saveCount))
	h.enrichWithSharedSessionData(&resp, newSession.ID)
	return &DuplicateSessionOutput{Body: resp}, nil
}

// resolveCloneSeedSave returns the SessionSaveState that should seed the
// clone. When explicitSaveID is nil it picks the most-recently-created save
// in the source session (or returns nil if the source has no saves — a
// valid case: a brand-new session with only SRAM can still be cloned).
// When explicitSaveID is set, the save must exist and belong to the source
// session; otherwise a 404 is returned.
func (h *SessionHandler) resolveCloneSeedSave(sessionID uint, explicitSaveID *uint) (*db.SessionSaveState, error) {
	if explicitSaveID != nil {
		var save db.SessionSaveState
		if err := h.DB.Where("id = ? AND session_id = ?", *explicitSaveID, sessionID).First(&save).Error; err != nil {
			return nil, huma.Error404NotFound("save not found in source session")
		}
		return &save, nil
	}
	var save db.SessionSaveState
	err := h.DB.Where("session_id = ?", sessionID).Order("created_at DESC").First(&save).Error
	if err != nil {
		// No saves is fine — the clone just starts empty.
		return nil, nil
	}
	return &save, nil
}

// cloneSaveInto copies a single SessionSaveState (file + screenshot) into
// newSessionID under the ownership of uid and persists a new row via the
// supplied tx. Errors bubble up so the caller can roll back the transaction.
func (h *SessionHandler) cloneSaveInto(tx *gorm.DB, save db.SessionSaveState, newSessionID, uid uint) error {
	newPath := h.Storage.SessionSaveStatePath(newSessionID, humaFilepathBase(save.FilePath))
	if _, copyErr := h.Storage.CopyFile(save.FilePath, newPath); copyErr != nil {
		return fmt.Errorf("copy save file: %w", copyErr)
	}
	var screenshotURL string
	if save.ScreenshotURL != "" {
		dstSubpath := h.Storage.SessionScreenshotSubpath(newSessionID, humaFilepathBase(save.ScreenshotURL))
		if _, cpErr := h.Storage.CopyImageFile(save.ScreenshotURL, dstSubpath); cpErr == nil {
			screenshotURL = dstSubpath
		}
		// A missing screenshot is not fatal — the save itself is what matters.
	}
	newSave := db.SessionSaveState{
		SessionID:     newSessionID,
		UserID:        uid,
		Name:          save.Name,
		FilePath:      newPath,
		FileSize:      save.FileSize,
		ScreenshotURL: screenshotURL,
		IsAuto:        save.IsAuto,
		CoreName:      save.CoreName,
		Notes:         save.Notes,
		Slot:          save.Slot,
	}
	return tx.Create(&newSave).Error
}

// HumaListSessionSaves is the huma handler for GET /api/sessions/{id}/saves.
func (h *SessionHandler) HumaListSessionSaves(ctx context.Context, in *ListSessionSavesInput) (*ListSessionSavesOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var saves []db.SessionSaveState
	if err := h.DB.Where("session_id = ?", session.ID).
		Preload("User").
		Order("created_at DESC").
		Find(&saves).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch saves")
	}

	currentCore := h.getRecommendedCoreName(session.GameID)
	result := make([]SessionSaveResponse, len(saves))
	for i, s := range saves {
		result[i] = h.toSaveResponse(s, currentCore)
	}
	return &ListSessionSavesOutput{Body: result}, nil
}

// HumaListSlotSaves is the huma handler for GET /api/sessions/{id}/saves/slots.
func (h *SessionHandler) HumaListSlotSaves(ctx context.Context, in *ListSessionSavesInput) (*ListSessionSavesOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var saves []db.SessionSaveState
	if err := h.DB.Where("session_id = ? AND slot IS NOT NULL", session.ID).
		Preload("User").
		Order("slot ASC").
		Find(&saves).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch slot saves")
	}

	currentCore := h.getRecommendedCoreName(session.GameID)
	result := make([]SessionSaveResponse, len(saves))
	for i, s := range saves {
		result[i] = h.toSaveResponse(s, currentCore)
	}
	return &ListSessionSavesOutput{Body: result}, nil
}

// HumaBulkDeleteSessionSaves is the huma handler for DELETE /api/sessions/{id}/saves.
func (h *SessionHandler) HumaBulkDeleteSessionSaves(ctx context.Context, in *BulkDeleteSessionSavesInput) (*BulkDeleteSessionSavesOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var saves []db.SessionSaveState
	if err := h.DB.Where("session_id = ?", session.ID).Find(&saves).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch saves")
	}

	var freedBytes int64
	for _, save := range saves {
		freedBytes += save.FileSize
		h.Storage.DeleteSave(save.FilePath)
		if save.ScreenshotURL != "" {
			h.Storage.DeleteSave(h.Storage.ImagePath(save.ScreenshotURL))
		}
	}

	h.DB.Where("session_id = ?", session.ID).Delete(&db.SessionSaveState{})

	return &BulkDeleteSessionSavesOutput{Body: BulkDeleteSessionSavesResponse{
		DeletedCount: len(saves),
		FreedBytes:   freedBytes,
	}}, nil
}

// HumaDeleteSessionSave is the huma handler for DELETE /api/sessions/{id}/saves/{saveId}.
func (h *SessionHandler) HumaDeleteSessionSave(ctx context.Context, in *DeleteSessionSaveInput) (*DeleteSessionSaveOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var save db.SessionSaveState
	if err := h.DB.Where("id = ? AND session_id = ?", in.SaveID, session.ID).First(&save).Error; err != nil {
		return nil, huma.Error404NotFound("save not found")
	}

	h.Storage.DeleteSave(save.FilePath)
	if save.ScreenshotURL != "" {
		h.Storage.DeleteSave(h.Storage.ImagePath(save.ScreenshotURL))
	}
	h.DB.Delete(&save)

	return &DeleteSessionSaveOutput{Body: MessageResponse{Message: "save deleted"}}, nil
}

// HumaUpdateSessionSave is the huma handler for PUT /api/sessions/{id}/saves/{saveId}.
func (h *SessionHandler) HumaUpdateSessionSave(ctx context.Context, in *UpdateSessionSaveInput) (*UpdateSessionSaveOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var save db.SessionSaveState
	if err := h.DB.Where("id = ? AND session_id = ?", in.SaveID, session.ID).First(&save).Error; err != nil {
		return nil, huma.Error404NotFound("save not found")
	}

	req := in.Body
	if req.Name != nil {
		if len(*req.Name) > 255 || strings.TrimSpace(*req.Name) == "" {
			return nil, huma.Error400BadRequest("name must be between 1 and 255 characters")
		}
		save.Name = *req.Name
	}
	if req.Notes != nil {
		if len(*req.Notes) > 200 {
			return nil, huma.Error400BadRequest("notes must be 200 characters or less")
		}
		save.Notes = *req.Notes
	}

	if err := h.DB.Save(&save).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update save")
	}

	h.DB.Preload("User").First(&save, save.ID)
	return &UpdateSessionSaveOutput{Body: h.toSaveResponse(save, h.getRecommendedCoreName(session.GameID))}, nil
}

// HumaUpdateSessionPlayTime is the huma handler for POST /api/sessions/{id}/play-time.
func (h *SessionHandler) HumaUpdateSessionPlayTime(ctx context.Context, in *UpdateSessionPlayTimeInput) (*UpdateSessionPlayTimeOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	if in.Body.Seconds < 0 {
		return nil, huma.Error400BadRequest("seconds must be non-negative")
	}

	now := time.Now()
	h.DB.Model(&session).Updates(map[string]interface{}{
		"total_play_time": gorm.Expr("total_play_time + ?", in.Body.Seconds),
		"last_played_at":  now,
		"last_played_by":  uid,
	})

	h.DB.Preload("Owner").First(&session, session.ID)
	var saveCount int64
	h.DB.Model(&db.SessionSaveState{}).Where("session_id = ?", session.ID).Count(&saveCount)
	return &UpdateSessionPlayTimeOutput{Body: h.toSessionResponse(session, int(saveCount))}, nil
}

// HumaStopPlayingSession is the huma handler for DELETE /api/sessions/{id}/play-time.
func (h *SessionHandler) HumaStopPlayingSession(ctx context.Context, in *StopPlayingSessionInput) (*StopPlayingSessionOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	h.DB.Model(&session).Update("last_played_by", nil)
	return &StopPlayingSessionOutput{Body: MessageResponse{Message: "stopped playing"}}, nil
}

// HumaGetSessionCheats is the huma handler for GET /api/sessions/{id}/cheats.
func (h *SessionHandler) HumaGetSessionCheats(ctx context.Context, in *GetSessionCheatsInput) (*GetSessionCheatsOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var settings []db.SessionCheatSetting
	h.DB.Where("session_id = ? AND enabled = ?", session.ID, true).Find(&settings)
	indices := make([]int, len(settings))
	for i, s := range settings {
		indices[i] = s.CheatIndex
	}

	return &GetSessionCheatsOutput{Body: SessionCheatsResponse{
		CheatsEnabled:  session.CheatsEnabled,
		EnabledIndices: indices,
	}}, nil
}

// HumaUpdateSessionCheats is the huma handler for PUT /api/sessions/{id}/cheats.
func (h *SessionHandler) HumaUpdateSessionCheats(ctx context.Context, in *UpdateSessionCheatsInput) (*UpdateSessionCheatsOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadSessionWithOwnerCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	h.DB.Model(&session).Update("cheats_enabled", in.Body.CheatsEnabled)
	h.DB.Where("session_id = ?", session.ID).Delete(&db.SessionCheatSetting{})
	for _, idx := range in.Body.EnabledIndices {
		setting := db.SessionCheatSetting{
			SessionID:  session.ID,
			CheatIndex: idx,
			Enabled:    true,
		}
		h.DB.Create(&setting)
	}

	// Echo the request back, mirroring the raw gin wire format.
	indices := in.Body.EnabledIndices
	if indices == nil {
		indices = []int{}
	}
	return &UpdateSessionCheatsOutput{Body: SessionCheatsResponse{
		CheatsEnabled:  in.Body.CheatsEnabled,
		EnabledIndices: indices,
	}}, nil
}

