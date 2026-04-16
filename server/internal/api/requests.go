package api

// This file holds all named request body types accepted by the api handlers.
// Naming each request shape (instead of declaring inline `var req struct { ... }`
// in the handler body) lets the future OpenAPI spec reference these types
// by name and gives callers a single place to find request schemas.

import (
	"github.com/spela/server/internal/db"
)

// --- User ---

// UpdateProfileRequest is the body for PUT /api/user/profile.
type UpdateProfileRequest struct {
	Email           string `json:"email"`
	AvatarURL       string `json:"avatarUrl"`
	CurrentPassword string `json:"currentPassword"`
}

// UpdatePreferencesRequest is the body for PUT /api/user/preferences.
type UpdatePreferencesRequest struct {
	ShowPerformanceOverlay  *bool                           `json:"showPerformanceOverlay"`
	AutoSaveEnabled         *bool                           `json:"autoSaveEnabled"`
	AutoLoadSaveEnabled     *bool                           `json:"autoLoadSaveEnabled"`
	SelectedShader          *string                         `json:"selectedShader"`
	SelectedTheme           *string                         `json:"selectedTheme"`
	DefaultSecondScreenPage *string                         `json:"defaultSecondScreenPage"`
	ConsoleShaders          map[string]string               `json:"consoleShaders"`
	SelectedKeyMapping      *string                         `json:"selectedKeyMapping"`
	CustomKeyMapping        map[string]string               `json:"customKeyMapping"`
	ConsoleKeyMappings      map[string]ConsoleKeyMappingDTO `json:"consoleKeyMappings"`
	PreferredRegions        *[]string                       `json:"preferredRegions"`
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
	Role     db.UserRole `json:"role"`
}

// AdminUpdateUserRequest is the body for PUT /api/admin/users/:id.
type AdminUpdateUserRequest struct {
	Role            db.UserRole `json:"role"`
	Email           string      `json:"email"`
	Password        string      `json:"password"`
	Disabled        *bool       `json:"disabled"`
	PendingApproval *bool       `json:"pendingApproval"`
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
type UpdateGamePlayTimeRequest struct {
	Seconds int64 `json:"seconds" binding:"min=0,max=86400"`
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
type CreateSessionRequest struct {
	Name string `json:"name" binding:"required,max=255"`
}

// UpdateSessionRequest is the body for PUT /api/sessions/:id.
type UpdateSessionRequest struct {
	Name          *string `json:"name"`
	CheatsEnabled *bool   `json:"cheatsEnabled"`
	CoreName      *string `json:"coreName"`
}

// DuplicateSessionRequest is the body for POST /api/sessions/:id/duplicate.
type DuplicateSessionRequest struct {
	Name string `json:"name"`
}

// UpdateSessionSaveRequest is the body for PUT /api/sessions/:id/saves/:saveId.
type UpdateSessionSaveRequest struct {
	Name  *string `json:"name"`
	Notes *string `json:"notes"`
}

// UpdateSessionPlayTimeRequest is the body for POST /api/sessions/:id/play-time.
type UpdateSessionPlayTimeRequest struct {
	Seconds int64 `json:"seconds" binding:"required"`
}

// UpdateSessionCheatsRequest is the body for PUT /api/sessions/:id/cheats.
type UpdateSessionCheatsRequest struct {
	CheatsEnabled  bool  `json:"cheatsEnabled"`
	EnabledIndices []int `json:"enabledIndices"`
}

// --- Shared sessions ---

// CreateSharedSessionRequest is the body for POST /api/shared-sessions.
type CreateSharedSessionRequest struct {
	GameID string `json:"gameId" binding:"required"`
	Name   string `json:"name" binding:"required,max=255"`
}

// UpdateSharedSessionRequest is the body for PUT /api/shared-sessions/:id.
type UpdateSharedSessionRequest struct {
	Name   *string `json:"name"`
	Status *string `json:"status"`
}

// InviteToSharedSessionRequest is the body for POST /api/shared-sessions/:id/invites.
type InviteToSharedSessionRequest struct {
	Username string `json:"username" binding:"required"`
}

// --- Netplay ---

// CreateNetplaySessionRequest is the body for POST /api/netplay/sessions.
type CreateNetplaySessionRequest struct {
	GameID     string `json:"gameId" binding:"required"`
	InputDelay *int   `json:"inputDelay"`
	CoreName   string `json:"coreName"`
}

// JoinByInviteCodeRequest is the body for POST /api/netplay/sessions/join.
type JoinByInviteCodeRequest struct {
	InviteCode string `json:"inviteCode" binding:"required"`
}

// UpdateNetplaySettingsRequest is the body for PUT /api/netplay/sessions/:id/settings.
type UpdateNetplaySettingsRequest struct {
	InputDelay *int `json:"inputDelay"`
}

// NetplayInviteUserRequest is the body for POST /api/netplay/sessions/:id/invites.
type NetplayInviteUserRequest struct {
	Username string `json:"username" binding:"required"`
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
type CreateOrUpdateRatingRequest struct {
	Rating int    `json:"rating" binding:"required,min=1,max=5"`
	Review string `json:"review"`
}

// --- Challenges ---

// UpdateChallengeRequest is the body for PUT /api/challenges/:id.
type UpdateChallengeRequest struct {
	Name        *string `json:"name"`
	Description *string `json:"description"`
	Status      *string `json:"status"`
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
