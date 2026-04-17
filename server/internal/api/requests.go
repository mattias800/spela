package api

// This file holds all named request body types accepted by the api handlers.
// Naming each request shape (instead of declaring inline `var req struct { ... }`
// in the handler body) lets the future OpenAPI spec reference these types
// by name and gives callers a single place to find request schemas.

import (
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
	ShowPerformanceOverlay  *bool                           `json:"showPerformanceOverlay,omitempty"`
	AutoSaveEnabled         *bool                           `json:"autoSaveEnabled,omitempty"`
	AutoLoadSaveEnabled     *bool                           `json:"autoLoadSaveEnabled,omitempty"`
	SelectedShader          *string                         `json:"selectedShader,omitempty"`
	SelectedTheme           *string                         `json:"selectedTheme,omitempty"`
	DefaultSecondScreenPage *string                         `json:"defaultSecondScreenPage,omitempty"`
	ConsoleShaders          map[string]string               `json:"consoleShaders,omitempty"`
	SelectedKeyMapping      *string                         `json:"selectedKeyMapping,omitempty"`
	CustomKeyMapping        map[string]string               `json:"customKeyMapping,omitempty"`
	ConsoleKeyMappings      map[string]ConsoleKeyMappingDTO `json:"consoleKeyMappings,omitempty"`
	PreferredRegions        *[]string                       `json:"preferredRegions,omitempty"`
}

// UpdateGameKeyMappingRequest is the body for PUT /api/user/games/:gameId/keymapping.
type UpdateGameKeyMappingRequest struct {
	CustomMapping map[string]string `json:"customMapping"`
}

// --- Admin user management ---

// AdminCreateUserRequest is the body for POST /api/admin/users.
type AdminCreateUserRequest struct {
	Username string      `json:"username" binding:"required,min=3,max=64"`
	Email    string      `json:"email" binding:"required,email"`
	Password string      `json:"password" binding:"required,min=8,max=72"`
	Role     db.UserRole `json:"role,omitempty"`
}

// AdminUpdateUserRequest is the body for PUT /api/admin/users/:id.
type AdminUpdateUserRequest struct {
	Role            db.UserRole `json:"role,omitempty"`
	Email           string      `json:"email,omitempty"`
	Password        string      `json:"password,omitempty"`
	Disabled        *bool       `json:"disabled,omitempty"`
	PendingApproval *bool       `json:"pendingApproval,omitempty"`
}

// --- Games (admin metadata + play time) ---

// UpdateGameMetadataRequest is the body for PUT /api/admin/games/:id/metadata.
type UpdateGameMetadataRequest struct {
	Title             string  `json:"title"`
	Description       string  `json:"description"`
	CoverURL          string  `json:"coverUrl"`
	ScreenshotURL     string  `json:"screenshotUrl"`
	Developer         string  `json:"developer"`
	Publisher         string  `json:"publisher"`
	ReleaseDate       string  `json:"releaseDate"`
	Genre             string  `json:"genre"`
	Players           int     `json:"players"`
	IGDBCriticsRating float64 `json:"igdbCriticsRating"`
	CoreOverride      string  `json:"coreOverride"`
	PartyInfo         string  `json:"partyInfo"`
}

// UpdateGamePlayTimeRequest is the body for POST /api/games/:id/play-time.
// The seconds field defaults to 0 when omitted, matching the raw gin handler's
// tolerant binding (gin does not enforce the min/max tag unless the field is
// supplied — huma replicates that behaviour via omitempty + handler-side range
// validation).
type UpdateGamePlayTimeRequest struct {
	Seconds int64 `json:"seconds,omitempty" binding:"min=0,max=86400"`
}

// UpdateVerificationTagRequest is the body for PUT /api/admin/games/:id/verification-tag.
type UpdateVerificationTagRequest struct {
	Tag string `json:"tag"`
}

// SetGameCoverRequest is the body for POST /api/admin/games/:id/cover.
type SetGameCoverRequest struct {
	Source       string `json:"source" binding:"required"`
	LibRetroName string `json:"libretroName,omitempty"`
}

// SetGameHeroRequest is the body for POST /api/admin/games/:id/hero.
type SetGameHeroRequest struct {
	URL string `json:"url" binding:"required"`
}

// ApplyIGDBMatchRequest is the body for POST /api/admin/games/:id/igdb-match.
type ApplyIGDBMatchRequest struct {
	IGDBID int `json:"igdbId" binding:"required,min=1"`
}

// --- Sessions ---

// CreateSessionRequest is the body for POST /api/games/:id/sessions.
// Name is marked optional in the huma schema so missing-name requests return
// the historical 400 from the handler rather than huma's 422 validation shape.
type CreateSessionRequest struct {
	Name string `json:"name,omitempty" required:"false"`
}

// UpdateSessionRequest is the body for PUT /api/sessions/:id.
type UpdateSessionRequest struct {
	Name          *string `json:"name,omitempty"`
	CheatsEnabled *bool   `json:"cheatsEnabled,omitempty"`
	CoreName      *string `json:"coreName,omitempty"`
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
}

// UpdateSharedSessionRequest is the body for PUT /api/shared-sessions/:id.
type UpdateSharedSessionRequest struct {
	Name   *string `json:"name,omitempty"`
	Status *string `json:"status,omitempty"`
}

// InviteToSharedSessionRequest is the body for POST /api/shared-sessions/:id/invites.
type InviteToSharedSessionRequest struct {
	Username string `json:"username" binding:"required"`
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
type CreateCollectionRequest struct {
	Name        string `json:"name" binding:"required,max=255"`
	Description string `json:"description" binding:"max=2048"`
	IsPublic    bool   `json:"isPublic"`
}

// UpdateCollectionRequest is the body for PUT /api/collections/:id.
type UpdateCollectionRequest struct {
	Name        *string `json:"name"`
	Description *string `json:"description"`
	IsPublic    *bool   `json:"isPublic"`
}

// AddGameToCollectionRequest is the body for POST /api/collections/:id/games.
type AddGameToCollectionRequest struct {
	GameID uint `json:"gameId" binding:"required"`
}

// --- Ratings ---

// CreateOrUpdateRatingRequest is the body for PUT /api/games/:id/rating.
// Validation (rating in [1,5]) happens in the handler so out-of-range values
// (including a missing field that deserialises as 0) report 400 Bad Request
// with the historical error message rather than huma's 422 "validation
// failed" shape.
type CreateOrUpdateRatingRequest struct {
	Rating int    `json:"rating,omitempty" binding:"required,min=1,max=5"`
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
type RegisterDeviceRequest struct {
	DeviceUUID string `json:"deviceUuid" binding:"required"`
	Name       string `json:"name" binding:"required"`
	Platform   string `json:"platform" binding:"required"`
}

// UpdateDeviceRequest is the body for PUT /api/user/devices/:id.
type UpdateDeviceRequest struct {
	Name string `json:"name" binding:"required"`
}

// UpdateDevicePreferencesRequest is the body for PUT /api/user/devices/:id/preferences.
type UpdateDevicePreferencesRequest struct {
	ConsoleShaders map[string]string `json:"consoleShaders"`
}

// --- System events ---

// ReportEmulatorErrorRequest is the body for POST /api/system-events/emulator-error.
type ReportEmulatorErrorRequest struct {
	Error  string `json:"error" binding:"required"`
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
type SetUploadConsoleRequest struct {
	ConsoleID string `json:"consoleId" binding:"required"`
}

// --- RetroAchievements ---

// LinkRAAccountRequest is the body for POST /api/user/ra/link.
type LinkRAAccountRequest struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required"`
}

// UpdateRASettingsRequest is the body for PUT /api/user/ra/settings.
type UpdateRASettingsRequest struct {
	HardcoreEnabled *bool `json:"hardcoreEnabled"`
}

// --- IGDB (admin) ---

// TestIGDBRequest is the body for POST /api/admin/igdb/test.
type TestIGDBRequest struct {
	ClientID     string `json:"clientId"`
	ClientSecret string `json:"clientSecret"`
}

// --- Achievements ---

// ShowcaseEntryInput is one entry in the body for PUT /api/user/achievements/showcase.
// The full request body is a JSON array of these.
type ShowcaseEntryInput struct {
	AchievementRAID uint `json:"achievementRaId" binding:"required"`
	RAGameID        uint `json:"raGameId" binding:"required"`
}
