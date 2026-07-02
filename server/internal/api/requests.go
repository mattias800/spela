package api

// This file holds all named request body types accepted by the api handlers.
// Naming each request shape (instead of declaring inline `var req struct { ... }`
// in the handler body) lets the future OpenAPI spec reference these types
// by name and gives callers a single place to find request schemas.

import (
	"encoding/json"

	"github.com/spela/server/internal/db"
)

// --- User ---

// UpdateProfileRequest is the body for PUT /api/user/profile. All fields are
// optional — unset fields are left untouched on the user record. Email
// changes also require CurrentPassword so the server can re-authenticate.
type UpdateProfileRequest struct {
	Email           string `json:"email,omitempty"`
	AvatarURL       string `json:"avatarUrl,omitempty"`
	CurrentPassword string `json:"currentPassword,omitempty"`
}

// UpdatePreferencesRequest is the body for PUT /api/user/preferences. Every
// field is optional — nil fields are left untouched, matching the partial-update
// semantics of the raw gin handler.
type UpdatePreferencesRequest struct {
	ShowPerformanceOverlay  *bool             `json:"showPerformanceOverlay,omitempty"`
	AutoSaveEnabled         *bool             `json:"autoSaveEnabled,omitempty"`
	AutoLoadSaveEnabled     *bool             `json:"autoLoadSaveEnabled,omitempty"`
	AutoUpdateCoresEnabled  *bool             `json:"autoUpdateCoresEnabled,omitempty"`
	SelectedShader          *string           `json:"selectedShader,omitempty" maxLength:"128"`
	SelectedTheme           *string           `json:"selectedTheme,omitempty" maxLength:"128"`
	DefaultSecondScreenPage *string           `json:"defaultSecondScreenPage,omitempty" maxLength:"128"`
	ConsoleShaders          map[string]string `json:"consoleShaders,omitempty"`
	// Per-console internal render-scale upserts. Keys are console
	// abbreviations (case-insensitive on the server). Allowed values
	// are "2x", "3x", "4x". Sending "", "native", or "1x" clears
	// the row so the console uses the native/core default. Unknown
	// values are ignored to preserve existing overrides.
	ConsoleRenderScales map[string]string `json:"consoleRenderScales,omitempty"`
	// Per-console save-state opt-out upserts. Keys are console
	// abbreviations (case-insensitive on the server). Allowed values
	// are "enabled", "disabled", "ask-once". Sending "" clears the
	// row so the console reverts to its tier-driven default. Unknown
	// abbreviations are silently skipped, matching ConsoleShaders.
	// See #804 phase 4.
	ConsoleSaveStatePolicies map[string]string `json:"consoleSaveStatePolicies,omitempty"`
	// Per-game save-state opt-out upserts keyed by game ID string.
	// Same sanitiser semantics as ConsoleSaveStatePolicies — empty
	// string clears the row, unknown values are silently dropped.
	// Unknown game IDs are skipped (no row written). See #804
	// phase 4b spec point (c).
	GameSaveStatePolicies map[string]string               `json:"gameSaveStatePolicies,omitempty"`
	SelectedKeyMapping    *string                         `json:"selectedKeyMapping,omitempty" maxLength:"128"`
	CustomKeyMapping      map[string]string               `json:"customKeyMapping,omitempty"`
	ConsoleKeyMappings    map[string]ConsoleKeyMappingDTO `json:"consoleKeyMappings,omitempty"`
	PreferredRegions      *[]string                       `json:"preferredRegions,omitempty"`
}

// UpdateGameKeyMappingRequest is the body for PUT /api/user/games/:gameId/keymapping.
// CustomMapping is marked optional in the huma schema so handler-side validation
// owns the 400 response (the raw gin handler used a manual presence check).
type UpdateGameKeyMappingRequest struct {
	CustomMapping map[string]string `json:"customMapping,omitempty" required:"false"`
}

// --- Admin user management ---

// AdminCreateUserRequest is the body for POST /api/admin/users.
// Validation is lighter than AuthRegisterRequest (the self-signup endpoint):
// admins can create users with usernames that the public pattern rejects
// (e.g. containing underscores / dashes) to accommodate migration imports
// and reserved-name allocation. Length and password strength still apply.
type AdminCreateUserRequest struct {
	Username string      `json:"username" minLength:"3" maxLength:"64" doc:"New account username (3-64 characters)."`
	Email    string      `json:"email" format:"email" minLength:"1" maxLength:"254" doc:"New account email (RFC 5321 cap)."`
	Password string      `json:"password" minLength:"8" maxLength:"72" doc:"New account password (8-72 characters)."`
	Role     db.UserRole `json:"role,omitempty"`
}

// AdminUpdateUserRequest is the body for PUT /api/admin/users/:id.
type AdminUpdateUserRequest struct {
	Role            db.UserRole `json:"role,omitempty"`
	Email           string      `json:"email,omitempty" format:"email" maxLength:"254"`
	Password        string      `json:"password,omitempty" maxLength:"72"`
	Disabled        *bool       `json:"disabled,omitempty"`
	PendingApproval *bool       `json:"pendingApproval,omitempty"`
	CanImportGames  *bool       `json:"canImportGames,omitempty"`
}

// --- Games (admin metadata + play time) ---

// UpdateGameMetadataRequest is the body for PUT /api/admin/games/:id/metadata.
// Every field is a pointer so admin UIs can clear a value (send null or
// explicit empty string / zero) vs. leave it untouched (omit the key). Fields
// are declared optional in the huma schema so partial updates succeed with
// the historical 200 rather than huma's 422 "missing fields" validation.
type UpdateGameMetadataRequest struct {
	Title             *string  `json:"title,omitempty"`
	Description       *string  `json:"description,omitempty"`
	CoverURL          *string  `json:"coverUrl,omitempty"`
	ScreenshotURL     *string  `json:"screenshotUrl,omitempty"`
	Developer         *string  `json:"developer,omitempty"`
	Publisher         *string  `json:"publisher,omitempty"`
	ReleaseDate       *string  `json:"releaseDate,omitempty"`
	Genre             *string  `json:"genre,omitempty"`
	Players           *int     `json:"players,omitempty"`
	IGDBCriticsRating *float64 `json:"igdbCriticsRating,omitempty"`
	CoreOverride      *string  `json:"coreOverride,omitempty"`
	PartyInfo         *string  `json:"partyInfo,omitempty"`
}

// UpdateGamePlayTimeRequest is the body for POST /api/games/:id/play-time.
// The seconds field defaults to 0 when omitted, matching the raw gin handler's
// tolerant binding (gin does not enforce the min/max tag unless the field is
// supplied — huma replicates that behaviour via omitempty + handler-side range
// validation).
type UpdateGamePlayTimeRequest struct {
	Seconds int64 `json:"seconds,omitempty"`
}

// UpdateVerificationTagRequest is the body for PUT /api/admin/games/:id/verification-tag.
// Tag is optional in the huma schema so empty body requests return the handler's
// 400 rather than huma's 422 validation shape.
type UpdateVerificationTagRequest struct {
	Tag string `json:"tag,omitempty"`
}

// SetGameCoverRequest is the body for POST /api/admin/games/:id/cover.
// Source is optional in the huma schema so missing-field requests return the
// historical 400 from the handler rather than huma's 422 validation shape.
type SetGameCoverRequest struct {
	Source       string `json:"source,omitempty" required:"false"`
	LibRetroName string `json:"libretroName,omitempty"`
}

// SetGameHeroRequest is the body for POST /api/admin/games/:id/hero.
// URL is optional in the huma schema so missing-field requests return the
// historical 400 from the handler rather than huma's 422 validation shape.
type SetGameHeroRequest struct {
	URL string `json:"url,omitempty" required:"false"`
}

// ApplyIGDBMatchRequest is the body for POST /api/admin/games/:id/igdb-match.
// IGDBID is optional in the huma schema so missing-field requests return the
// historical 400 from the handler rather than huma's 422 validation shape.
type ApplyIGDBMatchRequest struct {
	IGDBID int `json:"igdbId,omitempty" required:"false"`
}

// --- Sessions ---

// CreateSessionRequest is the body for POST /api/games/:id/sessions.
// Name is marked optional in the huma schema so missing-name requests return
// the historical 400 from the handler rather than huma's 422 validation shape.
type CreateSessionRequest struct {
	Name string `json:"name,omitempty" required:"false"`
}

// UpdateSessionRequest is the body for PUT /api/sessions/:id. Every
// field is optional — nil fields are left untouched.
type UpdateSessionRequest struct {
	Name          *string `json:"name,omitempty"`
	CheatsEnabled *bool   `json:"cheatsEnabled,omitempty"`
	CoreName      *string `json:"coreName,omitempty"`
	// UserLockedCoreVersion toggles the session's core-version lock.
	// True = the decision UI marked this session as locked to its
	// PinnedCoreSha256; false = clear the lock so the pre-play sheet
	// runs on next launch. See #672.
	UserLockedCoreVersion *bool `json:"userLockedCoreVersion,omitempty"`
	// AutoLoadSuppressed asks the player to skip auto-load of a save
	// state on next launch (set when the user picked "Start fresh on
	// the new version"). Cleared on first successful manual save.
	// See #672.
	AutoLoadSuppressed *bool `json:"autoLoadSuppressed,omitempty"`
	// RehearsalCrashPending is set by the player before entering
	// rehearsal mode and cleared on clean resolution. A true value
	// surviving an app relaunch drives sheet D. See #672.
	RehearsalCrashPending *bool `json:"rehearsalCrashPending,omitempty"`
}

// DuplicateSessionRequest is the body for POST /api/sessions/:id/duplicate.
type DuplicateSessionRequest struct {
	Name string `json:"name,omitempty"`
}

// UpdateSessionSaveRequest is the body for PUT /api/sessions/:id/saves/:saveId.
type UpdateSessionSaveRequest struct {
	Name  *string `json:"name,omitempty"`
	Notes *string `json:"notes,omitempty"`
}

// UpdateSessionPlayTimeRequest is the body for POST /api/sessions/:id/play-time.
// Seconds is marked optional in the huma schema so handler-side range
// validation owns the 400 response (the raw gin handler used the same
// tolerant binding).
type UpdateSessionPlayTimeRequest struct {
	Seconds int64 `json:"seconds,omitempty" required:"false"`
}

// UpdateSessionCheatsRequest is the body for PUT /api/sessions/:id/cheats.
type UpdateSessionCheatsRequest struct {
	CheatsEnabled  bool  `json:"cheatsEnabled"`
	EnabledIndices []int `json:"enabledIndices"`
}

// --- Shared sessions ---

// CreateSharedSessionRequest is the body for POST /api/shared-sessions.
// Both fields are optional in the huma schema so missing-field requests
// return the historical 400 from the handler rather than huma's 422
// validation shape.
type CreateSharedSessionRequest struct {
	GameID string `json:"gameId,omitempty" required:"false"`
	Name   string `json:"name,omitempty" required:"false"`
	// SourceSessionID, when set, seeds the new shared session with a
	// copy of that local session's most recent save state — i.e.
	// "share THIS playthrough's progress, not a fresh start." The
	// caller must own the source session. The source session's GameID
	// must match GameID in this request. See #885.
	SourceSessionID *uint `json:"sourceSessionId,omitempty" required:"false"`
}

// UpdateSharedSessionRequest is the body for PUT /api/shared-sessions/:id.
type UpdateSharedSessionRequest struct {
	Name   *string `json:"name,omitempty"`
	Status *string `json:"status,omitempty"`
}

// InviteToSharedSessionRequest is the body for POST /api/shared-sessions/:id/invites.
// Username is optional in the huma schema so missing-field requests return the
// historical 400 from the handler rather than huma's 422 validation shape.
type InviteToSharedSessionRequest struct {
	Username string `json:"username,omitempty" required:"false"`
}

// --- Netplay ---

// CreateNetplaySessionRequest is the body for POST /api/netplay/sessions.
// GameID is marked optional in the huma schema (via `required:"false"`) so
// missing-field requests return the historical 400 from the handler rather
// than huma's 422 "validation failed" shape.
type CreateNetplaySessionRequest struct {
	GameID     string `json:"gameId,omitempty" required:"false"`
	InputDelay *int   `json:"inputDelay,omitempty"`
	CoreName   string `json:"coreName,omitempty"`
}

// JoinByInviteCodeRequest is the body for POST /api/netplay/sessions/join.
// InviteCode is optional in the huma schema so missing-field requests return
// the historical 400 from the handler rather than huma's 422 validation shape.
type JoinByInviteCodeRequest struct {
	InviteCode string `json:"inviteCode,omitempty" required:"false"`
}

// UpdateNetplaySettingsRequest is the body for PUT /api/netplay/sessions/:id/settings.
type UpdateNetplaySettingsRequest struct {
	InputDelay *int `json:"inputDelay,omitempty"`
}

// NetplayInviteUserRequest is the body for POST /api/netplay/sessions/:id/invites.
// Username is optional in the huma schema so missing-field requests return
// the historical 400 from the handler rather than huma's 422 validation shape.
type NetplayInviteUserRequest struct {
	Username string `json:"username,omitempty" required:"false"`
}

// --- Collections ---

// CreateCollectionRequest is the body for POST /api/collections.
// Name is optional in the huma schema so missing-field requests return the
// historical 400 from the handler rather than huma's 422 validation shape.
type CreateCollectionRequest struct {
	Name        string `json:"name,omitempty" required:"false"`
	Description string `json:"description,omitempty"`
	IsPublic    bool   `json:"isPublic,omitempty"`
}

// UpdateCollectionRequest is the body for PUT /api/collections/:id.
type UpdateCollectionRequest struct {
	Name        *string `json:"name,omitempty"`
	Description *string `json:"description,omitempty"`
	IsPublic    *bool   `json:"isPublic,omitempty"`
}

// AddGameToCollectionRequest is the body for POST /api/collections/:id/games.
// GameID is optional in the huma schema so missing-field requests return the
// historical 400 from the handler rather than huma's 422 validation shape.
type AddGameToCollectionRequest struct {
	GameID uint `json:"gameId,omitempty" required:"false"`
}

// --- Ratings ---

// CreateOrUpdateRatingRequest is the body for PUT /api/games/:id/rating.
// Validation (rating in [1,5]) happens in the handler so out-of-range values
// (including a missing field that deserialises as 0) report 400 Bad Request
// with the historical error message rather than huma's 422 "validation
// failed" shape.
type CreateOrUpdateRatingRequest struct {
	Rating int    `json:"rating,omitempty"`
	Review string `json:"review,omitempty"`
}

// --- Challenges ---

// UpdateChallengeRequest is the body for PUT /api/challenges/:id.
type UpdateChallengeRequest struct {
	Name        *string `json:"name,omitempty"`
	Description *string `json:"description,omitempty"`
	Status      *string `json:"status,omitempty"`
}

// --- Devices ---

// RegisterDeviceRequest is the body for POST /api/user/devices.
// Fields are marked optional in the huma schema so handler-side validation
// owns the 400 response (the raw gin handler used tags).
type RegisterDeviceRequest struct {
	DeviceUUID string `json:"deviceUuid,omitempty" required:"false"`
	Name       string `json:"name,omitempty" required:"false"`
	Platform   string `json:"platform,omitempty" required:"false"`
}

// UpdateDeviceRequest is the body for PUT /api/user/devices/:id.
type UpdateDeviceRequest struct {
	Name string `json:"name,omitempty" required:"false"`
}

// UpdateDevicePreferencesRequest is the body for PUT /api/user/devices/:id/preferences.
type UpdateDevicePreferencesRequest struct {
	ConsoleShaders map[string]string `json:"consoleShaders"`
}

// --- System events ---

// ReportEmulatorErrorRequest is the body for POST /api/system-events/emulator-error.
type ReportEmulatorErrorRequest struct {
	Error  string `json:"error"`
	GameID string `json:"gameId"`
	Core   string `json:"core"`
}

// --- Play later ---

// ReorderPlayLaterRequest is the body for PUT /api/user/play-later/reorder.
type ReorderPlayLaterRequest struct {
	GameIDs []string `json:"gameIds"`
}

// --- Upload (admin) ---

// SetUploadConsoleRequest is the body for POST /api/admin/uploads/:id/console.
// ConsoleID is optional in the huma schema so missing-field requests return the
// historical 400 from the handler rather than huma's 422 validation shape.
type SetUploadConsoleRequest struct {
	ConsoleID string `json:"consoleId,omitempty" required:"false"`
}

// --- RetroAchievements ---

// LinkRAAccountRequest is the body for POST /api/user/ra/link.
// Fields are marked optional in the huma schema so handler-side validation
// owns the 400 response (the raw gin handler used tags).
type LinkRAAccountRequest struct {
	Username string `json:"username,omitempty" required:"false"`
	Password string `json:"password,omitempty" required:"false"`
}

// UpdateRASettingsRequest is the body for PUT /api/user/ra/settings.
type UpdateRASettingsRequest struct {
	HardcoreEnabled *bool `json:"hardcoreEnabled"`
}

// --- IGDB (admin) ---

// TestIGDBRequest is the body for POST /api/admin/igdb/test.
type TestIGDBRequest struct {
	ClientID     string `json:"clientId,omitempty"`
	ClientSecret string `json:"clientSecret,omitempty"`
}

// --- Achievements ---

// ShowcaseEntryInput is one entry in the body for PUT /api/user/achievements/showcase.
// The full request body is a JSON array of these.
type ShowcaseEntryInput struct {
	AchievementRAID uint `json:"achievementRaId"`
	RAGameID        uint `json:"raGameId"`
}

// --- Saved searches ---

// SavedSearchRequest is the request body for creating a saved search.
// Fields are marked optional in the huma schema so handler-side validation
// owns the 400 response (the raw gin handler used tags).
type SavedSearchRequest struct {
	Name    string          `json:"name,omitempty" required:"false"`
	Filters json.RawMessage `json:"filters,omitempty" required:"false"`
}
