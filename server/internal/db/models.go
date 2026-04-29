package db

import (
	"time"

	"gorm.io/gorm"
)

// UserRole is the typed user role enumeration. The wire format remains a
// plain string ("owner" / "admin" / "user") since UserRole's underlying type
// is string. The named type lets handlers and OpenAPI annotations express
// the bounded set of valid values precisely.
type UserRole string

// Role constants for the role hierarchy: owner > admin > user.
const (
	RoleOwner UserRole = "owner"
	RoleAdmin UserRole = "admin"
	RoleUser  UserRole = "user"
)

// AllUserRoles is the canonical list of valid user roles. Used by the
// OpenAPI generator and any handler that needs to enumerate valid roles.
var AllUserRoles = []UserRole{RoleOwner, RoleAdmin, RoleUser}

// IsAdminOrOwner returns true if the role is admin or owner.
func IsAdminOrOwner(role UserRole) bool {
	return role == RoleAdmin || role == RoleOwner
}

// User represents an application user.
type User struct {
	ID           uint           `gorm:"primarykey" json:"id"`
	CreatedAt    time.Time      `json:"createdAt"`
	UpdatedAt    time.Time      `json:"updatedAt"`
	DeletedAt    gorm.DeletedAt `gorm:"index" json:"-"`
	Username     string         `gorm:"uniqueIndex;size:64;not null" json:"username"`
	Email        string         `gorm:"uniqueIndex;size:255;not null" json:"email"`
	PasswordHash string         `gorm:"not null" json:"-"`
	Role         UserRole       `gorm:"size:16;default:user" json:"role"`
	AvatarURL    string         `gorm:"size:512" json:"avatarUrl"`
	TokenVersion        int            `gorm:"default:0" json:"-"`
	Disabled            bool           `gorm:"default:false" json:"disabled"`
	PendingApproval     bool           `gorm:"default:false" json:"pendingApproval"`
	ShowPerfOverlay     bool           `gorm:"default:false" json:"showPerformanceOverlay"`
	AutoSaveEnabled     bool           `gorm:"default:true" json:"autoSaveEnabled"`
	AutoLoadSaveEnabled bool           `gorm:"default:true" json:"autoLoadSaveEnabled"`
	// When true, the player silently re-downloads a cached core binary
	// whose sha256 no longer matches the server's current fingerprint.
	// When false, the player trusts its local cache even if the server
	// has a newer build — session pinning keeps long-running saves
	// loadable without a version mismatch. See #555 Phase 2.
	AutoUpdateCoresEnabled bool           `gorm:"default:true" json:"autoUpdateCoresEnabled"`
	SelectedShader      string         `gorm:"size:64;default:none" json:"selectedShader"`
	SelectedTheme       string         `gorm:"size:64;default:default-dark" json:"selectedTheme"`
	SelectedKeyMapping  string         `gorm:"size:64;default:arrows-left" json:"selectedKeyMapping"`
	CustomKeyMapping         string         `gorm:"type:text" json:"customKeyMapping"` // JSON: {"0":"z","1":"x",...}
	DefaultSecondScreenPage string         `gorm:"size:64;default:art" json:"defaultSecondScreenPage"`
	PreferredRegions         string         `gorm:"size:255" json:"preferredRegions"` // comma-separated ordered list, e.g. "USA,Europe,World"
}

// LoginAttempt tracks failed login attempts per username for account lockout.
type LoginAttempt struct {
	ID          uint      `gorm:"primarykey"`
	Username    string    `gorm:"size:64;uniqueIndex"`
	FailedCount int       `gorm:"default:0"`
	LockedUntil time.Time
	UpdatedAt   time.Time
}

// MediaTypeCategory represents a broad category of game media (e.g. cartridge, optical disc).
type MediaTypeCategory struct {
	ID   uint   `gorm:"primarykey" json:"id"`
	Code string `gorm:"uniqueIndex;size:32;not null" json:"code"`
	Name string `gorm:"size:64;not null" json:"name"`
}

// MediaType represents a specific game media format (e.g. ROM cartridge, CD-ROM).
type MediaType struct {
	ID         uint              `gorm:"primarykey" json:"id"`
	Code       string            `gorm:"uniqueIndex;size:32;not null" json:"code"`
	Name       string            `gorm:"size:64;not null" json:"name"`
	CategoryID uint              `gorm:"not null" json:"categoryId"`
	Category   MediaTypeCategory `gorm:"foreignKey:CategoryID" json:"category"`
}

// HardwareMaker represents a console/hardware manufacturer.
type HardwareMaker struct {
	ID        uint      `gorm:"primarykey" json:"id"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
	Code      string    `gorm:"uniqueIndex;size:32;not null" json:"code"`
	Name      string    `gorm:"size:128;not null" json:"name"`
	Consoles  []Console `gorm:"foreignKey:HardwareMakerID" json:"consoles"`
}

// Console represents a detected game console/platform.
type Console struct {
	ID             uint           `gorm:"primarykey" json:"id"`
	CreatedAt      time.Time      `json:"createdAt"`
	UpdatedAt      time.Time      `json:"updatedAt"`
	DeletedAt      gorm.DeletedAt `gorm:"index" json:"-"`
	Name           string         `gorm:"uniqueIndex;size:128;not null" json:"name"`
	Abbreviation   string         `gorm:"size:16;not null" json:"abbreviation"`
	Extensions     string         `gorm:"size:255;not null" json:"extensions"` // comma-separated
	DefaultCore    string         `gorm:"size:128" json:"defaultCore"`
	EmulatorJSCore string         `gorm:"size:64" json:"emulatorJsCore"`
	FolderName     string         `gorm:"size:64" json:"folderName"`
	CoverAspect    string         `gorm:"size:16;default:3:4" json:"coverAspect"`
	ColorTheme       string         `gorm:"size:7;default:#6366f1" json:"colorTheme"`
	Generation       int            `gorm:"default:0" json:"generation"`
	SaveStateSupport bool           `gorm:"default:true" json:"saveStateSupport"`
	Playable         bool           `gorm:"default:true" json:"playable"`
	Code             *string        `gorm:"uniqueIndex;size:32" json:"code"`
	HardwareMakerID  *uint          `json:"hardwareMakerId"`
	HardwareMaker    *HardwareMaker `gorm:"foreignKey:HardwareMakerID" json:"hardwareMaker"`
	MediaTypeID      *uint          `json:"mediaTypeId"`
	MediaType        *MediaType     `gorm:"foreignKey:MediaTypeID" json:"mediaType"`
	ReleaseYear      *int           `json:"releaseYear"`
	UnitsSold        *int64         `json:"unitsSold"`
	Summary          *string        `gorm:"type:text" json:"summary"`
	Games            []Game         `gorm:"foreignKey:ConsoleID" json:"games"`
	GameCount      int            `gorm:"-" json:"gameCount"`
}

// Game represents a detected ROM/game file.
type Game struct {
	ID            uint           `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time      `json:"createdAt"`
	UpdatedAt     time.Time      `json:"updatedAt"`
	DeletedAt     gorm.DeletedAt `gorm:"index" json:"-"`
	ConsoleID     uint           `gorm:"index;not null;index:idx_console_group_key,priority:1;index:idx_console_is_primary,priority:1" json:"consoleId"`
	Console       Console        `gorm:"foreignKey:ConsoleID" json:"console"`
	Title         string         `gorm:"size:255;not null" json:"title"`
	FileName      string         `gorm:"size:512;not null" json:"fileName"`
	FilePath      string         `gorm:"uniqueIndex;size:1024;not null" json:"-"`
	FileSize      int64          `json:"fileSize"`
	DiscCount     int            `json:"discCount"`                                    // 0 = single-disc legacy, 2+ = multi-disc
	Discs         []GameDisc     `gorm:"foreignKey:GameID" json:"discs"`
	Description   string         `gorm:"type:text" json:"description"`
	CoverURL      string         `gorm:"size:512" json:"coverUrl"`
	ScreenshotURL string         `gorm:"size:512" json:"screenshotUrl"`
	Developer     string         `gorm:"size:255" json:"developer"`
	Publisher     string         `gorm:"size:255" json:"publisher"`
	ReleaseDate   string         `gorm:"size:32" json:"releaseDate"`
	Genre         string         `gorm:"size:512" json:"genre"`
	GameModes            string  `gorm:"size:255" json:"gameModes"`
	Storyline            string  `gorm:"type:text" json:"storyline"`
	TotalRating          float64 `json:"totalRating"`
	TotalRatingCount     int     `json:"totalRatingCount"`
	IGDBUserRating       float64 `json:"igdbUserRating"`
	IGDBUserRatingCount  int     `json:"igdbUserRatingCount"`
	TimeToBeatHastily    int     `json:"timeToBeatHastily"`
	TimeToBeatNormally   int     `json:"timeToBeatNormally"`
	TimeToBeatCompletely int     `json:"timeToBeatCompletely"`
	Players              int     `json:"players"`
	IGDBCriticsRating    float64 `gorm:"column:rating" json:"igdbCriticsRating"`
	CoreOverride         string  `gorm:"size:128" json:"coreOverride"`
	LibRetroCoverURL    string         `gorm:"size:512" json:"-"`
	IGDBCoverURL        string         `gorm:"size:512" json:"-"`
	CoverManuallySet    bool           `gorm:"default:false" json:"-"`
	ScrapeAttempts      int            `json:"scrapeAttempts"`
	ScraperID           string         `gorm:"size:128" json:"scraperId"`
	AchievementsWarning string         `gorm:"size:512" json:"achievementsWarning"`
	VerificationStatus  string         `gorm:"size:32" json:"verificationStatus"`
	VerificationTag     string         `gorm:"size:128" json:"verificationTag"`
	Region              string         `gorm:"size:128" json:"region"`
	Revision            string         `gorm:"size:64" json:"revision"`
	Tags                string         `gorm:"size:255" json:"tags"`
	IsPreRelease        bool           `gorm:"default:false;index:idx_game_is_pre_release" json:"isPreRelease"`
	GroupKey            string         `gorm:"size:255;index:idx_game_group_key;index:idx_console_group_key,priority:2" json:"groupKey"`
	IsPrimary           bool           `gorm:"default:false;index:idx_game_is_primary;index:idx_console_is_primary,priority:2" json:"isPrimary"`
	PrimaryGameID       *uint          `json:"primaryGameId"`
	ParentGameID        *uint          `gorm:"index:idx_game_parent" json:"parentGameId"` // links standalone ROM hacks to their base game
	PartyInfo           string         `gorm:"size:512" json:"partyInfo"`                // Demo party and placement, e.g. "Assembly 1993, 1st place"
	CRC32               string         `gorm:"size:16" json:"-"`
	RAGameID            uint           `gorm:"index" json:"-"` // RetroAchievements game ID (cached from hash lookup)
	// RAHashChecked + RAGameID sentinel logic:
	//   RAHashChecked=false, RAGameID=0  → Not yet looked up. Compute ROM MD5 and query RA.
	//   RAHashChecked=true,  RAGameID=0  → Looked up, but RA doesn't have this game. Do NOT retry.
	//   RAHashChecked=true,  RAGameID>0  → Valid RA game ID cached.
	// RAHashChecked is ONLY set to true after a successful API response (even if RA returned no match).
	// Transient errors (network, 403) leave RAHashChecked=false so the next visit retries.
	RAHashChecked       bool           `gorm:"default:false" json:"-"`
	Screenshots      []GameScreenshot      `gorm:"foreignKey:GameID" json:"-"`
	ReleaseDates     []GameReleaseDate     `gorm:"foreignKey:GameID" json:"-"`
	Videos           []GameVideo           `gorm:"foreignKey:GameID" json:"-"`
	LanguageSupports []GameLanguageSupport `gorm:"foreignKey:GameID" json:"-"`
	AgeRatings       []GameAgeRating       `gorm:"foreignKey:GameID" json:"-"`
}

// GameReleaseDate represents a regional release date for a game.
type GameReleaseDate struct {
	ID       uint   `gorm:"primarykey" json:"id"`
	GameID   uint   `gorm:"uniqueIndex:idx_game_release_region;not null" json:"gameId"`
	Region   string `gorm:"uniqueIndex:idx_game_release_region;size:64;not null" json:"region"`
	Date     string `gorm:"size:32" json:"date"`
	Platform string `gorm:"size:128" json:"platform"`
}

// GameVideo stores a video associated with a game (typically YouTube).
type GameVideo struct {
	ID      uint   `gorm:"primarykey" json:"id"`
	GameID  uint   `gorm:"uniqueIndex:idx_game_video;not null" json:"gameId"`
	VideoID string `gorm:"uniqueIndex:idx_game_video;size:32;not null" json:"videoId"`
	Name    string `gorm:"size:512" json:"name"`
}

// GameLanguageSupport stores a language support entry for a game.
type GameLanguageSupport struct {
	ID          uint   `gorm:"primarykey" json:"id"`
	GameID      uint   `gorm:"uniqueIndex:idx_game_lang_support;not null" json:"gameId"`
	Language    string `gorm:"uniqueIndex:idx_game_lang_support;size:128;not null" json:"language"`
	SupportType string `gorm:"uniqueIndex:idx_game_lang_support;size:64;not null" json:"supportType"`
}

// GameAgeRating stores an age rating classification for a game.
type GameAgeRating struct {
	ID       uint   `gorm:"primarykey" json:"id"`
	GameID   uint   `gorm:"uniqueIndex:idx_game_age_rating;not null" json:"gameId"`
	Category string `gorm:"uniqueIndex:idx_game_age_rating;size:16;not null" json:"category"`
	Rating   string `gorm:"size:32;not null" json:"rating"`
}

// GameScreenshot represents a single screenshot image for a game.
type GameScreenshot struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	GameID    uint           `gorm:"index;not null" json:"gameId"`
	URL       string         `gorm:"size:512;not null" json:"url"`
	Position  int            `gorm:"default:0" json:"position"`
}

// GameDisc represents a single disc in a multi-disc game.
type GameDisc struct {
	ID         uint           `gorm:"primarykey" json:"id"`
	CreatedAt  time.Time      `json:"createdAt"`
	DeletedAt  gorm.DeletedAt `gorm:"index" json:"-"`
	GameID     uint           `gorm:"uniqueIndex:idx_game_disc;not null" json:"gameId"`
	DiscNumber int            `gorm:"uniqueIndex:idx_game_disc;not null" json:"discNumber"`
	FilePath   string         `gorm:"size:1024;not null" json:"-"`
	FileName   string         `gorm:"size:512;not null" json:"fileName"`
	FileSize   int64          `json:"fileSize"`
}

// Favorite represents a user's favorited game.
type Favorite struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	UserID    uint           `gorm:"uniqueIndex:idx_user_game;not null" json:"userId"`
	User      User           `gorm:"foreignKey:UserID" json:"-"`
	GameID    uint           `gorm:"uniqueIndex:idx_user_game;not null" json:"gameId"`
	Game      Game           `gorm:"foreignKey:GameID" json:"game"`
}

// PlayHistory records when a user plays a game.
type PlayHistory struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	UserID    uint           `gorm:"index;not null" json:"userId"`
	User      User           `gorm:"foreignKey:UserID" json:"-"`
	GameID    uint           `gorm:"index;not null" json:"gameId"`
	Game      Game           `gorm:"foreignKey:GameID" json:"game"`
	LastPlayed time.Time     `json:"lastPlayed"`
	PlayTime   int64         `json:"playTime"` // seconds
}

// DailyPlayActivity aggregates play time per user per day for heatmap display.
type DailyPlayActivity struct {
	ID       uint      `gorm:"primarykey" json:"id"`
	UserID   uint      `gorm:"uniqueIndex:idx_daily_play_user_date;not null" json:"userId"`
	Date     time.Time `gorm:"uniqueIndex:idx_daily_play_user_date;not null;type:date" json:"date"`
	PlayTime int64     `json:"playTime"` // seconds added on this day
}

// RefreshToken stores issued refresh tokens.
type RefreshToken struct {
	ID          uint           `gorm:"primarykey"`
	CreatedAt   time.Time
	DeletedAt   gorm.DeletedAt `gorm:"index"`
	UserID      uint           `gorm:"index;not null"`
	User        User           `gorm:"foreignKey:UserID"`
	Token       string         `gorm:"uniqueIndex;size:512;not null"`
	ExpiresAt   time.Time      `gorm:"not null;index"`
	TokenFamily string         `gorm:"size:64;index"` // groups related tokens for replay detection
	Consumed    bool           `gorm:"default:false"`  // marked true on rotation instead of deleted
}

// TokenBlacklist stores revoked access tokens until they expire naturally.
type TokenBlacklist struct {
	ID        uint      `gorm:"primarykey"`
	TokenHash string    `gorm:"uniqueIndex;size:64;not null"` // SHA-256 hex of the JWT
	ExpiresAt time.Time `gorm:"not null;index"`
}

// SystemEventCategory groups system events into logical categories (security,
// operational, etc.). Rows are code-seeded on startup — admins cannot create or
// delete categories because no code path emits events into custom categories.
type SystemEventCategory struct {
	ID   uint   `gorm:"primarykey"`
	Code string `gorm:"size:32;uniqueIndex;not null"`
	Name string `gorm:"size:64;not null"`
}

const (
	CategorySecurity    = "security"
	CategoryOperational = "operational"
)

// System event types. Keep in sync with the slog event= discriminators
// emitted by auth_handler.go and middleware.go.
const (
	SystemEventLoginSuccess         = "login_success"
	SystemEventLoginFailed          = "login_failed"
	SystemEventLoginLocked          = "login_locked"
	SystemEventLoginBlocked         = "login_blocked"
	SystemEventAccountLocked        = "account_locked"
	SystemEventRevokedTokenUsed     = "revoked_token_used"
	SystemEventDisabledAccountToken = "disabled_account_token"
	SystemEventTokenUserMissing     = "token_user_missing"
	SystemEventStaleTokenVersion    = "stale_token_version"
)

// Operational event types.
const (
	SystemEventRACircuitBreakerTripped = "ra_circuit_breaker_tripped"
	SystemEventScraperRepeatedErrors   = "scraper_repeated_errors"
	SystemEventROMFileMissing          = "rom_file_missing"
	SystemEventAPICredentialsInvalid   = "api_credentials_invalid"
	SystemEventEmulatorJSLoadFailed    = "emulatorjs_load_failed"
	// Emitted when the server observes a new sha256 for a core binary —
	// either an admin-triggered force-refresh or (future) a background
	// buildbot poll. Metadata carries old_sha256 and new_sha256 so the
	// audit trail shows which version replaced which. See #555 Phase 2.
	SystemEventCoreUpdated = "core_updated"
)

// AllSystemEventTypes is the canonical catalog of system event type strings.
// Every type above must appear here so API responses and UI filters stay in
// sync with the emitted events.
var AllSystemEventTypes = []string{
	SystemEventLoginSuccess,
	SystemEventLoginFailed,
	SystemEventLoginLocked,
	SystemEventLoginBlocked,
	SystemEventAccountLocked,
	SystemEventRevokedTokenUsed,
	SystemEventDisabledAccountToken,
	SystemEventTokenUserMissing,
	SystemEventStaleTokenVersion,
	SystemEventRACircuitBreakerTripped,
	SystemEventScraperRepeatedErrors,
	SystemEventROMFileMissing,
	SystemEventAPICredentialsInvalid,
	SystemEventEmulatorJSLoadFailed,
	SystemEventCoreUpdated,
}

// SystemEventTypeCategory maps each event type to its category code. Used by
// the recorder to auto-resolve CategoryID and by the types endpoint to tell
// the frontend which types belong to which category.
var SystemEventTypeCategory = map[string]string{
	SystemEventLoginSuccess:            CategorySecurity,
	SystemEventLoginFailed:             CategorySecurity,
	SystemEventLoginLocked:             CategorySecurity,
	SystemEventLoginBlocked:            CategorySecurity,
	SystemEventAccountLocked:           CategorySecurity,
	SystemEventRevokedTokenUsed:        CategorySecurity,
	SystemEventDisabledAccountToken:    CategorySecurity,
	SystemEventTokenUserMissing:        CategorySecurity,
	SystemEventStaleTokenVersion:       CategorySecurity,
	SystemEventRACircuitBreakerTripped: CategoryOperational,
	SystemEventScraperRepeatedErrors:   CategoryOperational,
	SystemEventROMFileMissing:          CategoryOperational,
	SystemEventAPICredentialsInvalid:   CategoryOperational,
	SystemEventEmulatorJSLoadFailed:    CategoryOperational,
	SystemEventCoreUpdated:             CategoryOperational,
}

// SystemEvent records an admin-only audit entry for an authentication,
// session, or operational event. These rows back the /admin/system-events
// page so admins can investigate suspicious activity and operational issues
// without tailing container logs.
//
// Unlike LoginAttempt (which stores a SHA-256 hash of the username for lockout
// counters), SystemEvent deliberately stores the raw username — admins need
// to read it to investigate incidents, and the table has a short retention
// window (see systemEventRetention in auth_handler.go) to limit exposure.
//
// Username is stored as a free-form string (not a foreign key) so events
// referencing deleted or never-existed accounts are still useful. UserID is
// optional for the same reason. Metadata is a JSON blob for any extra
// per-event-type fields (failedCount, lockedUntil, etc.).
//
// UsernameLower is a denormalized lowercased copy of Username, used by the
// case-insensitive filter query so an index lookup can be used instead of a
// `LOWER(username) LIKE ?` table scan (SQLite has no functional indexes).
// It is populated automatically by the recorder on every write.
type SystemEvent struct {
	ID            uint                `gorm:"primarykey"`
	CreatedAt     time.Time           `gorm:"index;not null"`
	CategoryID    uint                `gorm:"not null;index"`
	Category      SystemEventCategory `gorm:"foreignKey:CategoryID"`
	EventType     string              `gorm:"size:64;not null;index"`
	Reason        string              `gorm:"size:64;index"`
	Username      string              `gorm:"size:128;index"`
	UsernameLower string              `gorm:"size:128;index"`
	UserID        *uint               `gorm:"index"`
	IP            string              `gorm:"size:64;index"`
	Path          string              `gorm:"size:256"`
	Metadata      string              `gorm:"type:text"`
	DismissedAt   *time.Time          `gorm:"index"`
}

// ServerSetting stores key-value server configuration.
type ServerSetting struct {
	Key   string `gorm:"primarykey;size:128" json:"key"`
	Value string `gorm:"type:text" json:"value"`
}

// ConsoleShaderPreference stores a user's per-console shader override.
type ConsoleShaderPreference struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	UserID    uint           `gorm:"uniqueIndex:idx_user_console_shader;not null" json:"userId"`
	ConsoleID uint           `gorm:"uniqueIndex:idx_user_console_shader;not null" json:"consoleId"`
	Shader    string         `gorm:"size:64;not null" json:"shader"`
}

// ConsoleKeyMappingPreference stores a user's per-console key mapping override.
type ConsoleKeyMappingPreference struct {
	ID              uint           `gorm:"primarykey" json:"id"`
	CreatedAt       time.Time      `json:"createdAt"`
	UpdatedAt       time.Time      `json:"updatedAt"`
	DeletedAt       gorm.DeletedAt `gorm:"index" json:"-"`
	UserID          uint           `gorm:"uniqueIndex:idx_user_console_keymapping;not null" json:"userId"`
	ConsoleID       uint           `gorm:"uniqueIndex:idx_user_console_keymapping;not null" json:"consoleId"`
	SelectedMapping string         `gorm:"size:64;not null" json:"selectedMapping"`
	CustomMapping   string         `gorm:"type:text" json:"customMapping"` // JSON
}

// Device represents a registered user device.
type Device struct {
	ID         uint           `gorm:"primarykey" json:"id"`
	CreatedAt  time.Time      `json:"createdAt"`
	UpdatedAt  time.Time      `json:"updatedAt"`
	DeletedAt  gorm.DeletedAt `gorm:"index" json:"-"`
	UserID     uint           `gorm:"index;not null" json:"userId"`
	DeviceUUID string         `gorm:"uniqueIndex;size:64;not null" json:"deviceUuid"`
	Name       string         `gorm:"size:128;not null" json:"name"`
	Platform   string         `gorm:"size:32;not null" json:"platform"` // "android", "macos", "linux", "windows"
	LastSeenAt time.Time      `json:"lastSeenAt"`
}

// DeviceShaderPreference stores a per-device per-console shader override.
type DeviceShaderPreference struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	DeviceID  uint           `gorm:"uniqueIndex:idx_device_console_shader;not null" json:"deviceId"`
	ConsoleID uint           `gorm:"uniqueIndex:idx_device_console_shader;not null" json:"consoleId"`
	Shader    string         `gorm:"size:64;not null" json:"shader"`
}

// RetroAchievementCredential stores a user's RA token (never the password).
type RetroAchievementCredential struct {
	ID              uint           `gorm:"primarykey" json:"id"`
	CreatedAt       time.Time      `json:"createdAt"`
	UpdatedAt       time.Time      `json:"updatedAt"`
	DeletedAt       gorm.DeletedAt `gorm:"index" json:"-"`
	UserID          uint           `gorm:"uniqueIndex;not null" json:"userId"`
	RAUsername      string         `gorm:"size:128;not null" json:"raUsername"`
	RAToken         string         `gorm:"size:512;not null" json:"-"`
	HardcoreEnabled bool           `gorm:"default:false" json:"hardcoreEnabled"`
}

// GameAchievementCache caches RA achievement data per game (by RA game ID).
type GameAchievementCache struct {
	ID              uint           `gorm:"primarykey" json:"id"`
	CreatedAt       time.Time      `json:"createdAt"`
	UpdatedAt       time.Time      `json:"updatedAt"`
	DeletedAt       gorm.DeletedAt `gorm:"index" json:"-"`
	RAGameID        uint           `gorm:"uniqueIndex;not null" json:"raGameId"`
	GameID          uint           `gorm:"index" json:"gameId"`
	Title           string         `gorm:"size:255" json:"title"`
	AchievementJSON string         `gorm:"type:text" json:"-"`
	TotalCount      int            `json:"totalCount"`
	TotalPoints     int            `json:"totalPoints"`
	CachedAt        time.Time      `json:"cachedAt"`
}

// UserAchievementProgress tracks per-user achievement unlocks.
type UserAchievementProgress struct {
	ID               uint           `gorm:"primarykey" json:"id"`
	CreatedAt        time.Time      `json:"createdAt"`
	UpdatedAt        time.Time      `json:"updatedAt"`
	DeletedAt        gorm.DeletedAt `gorm:"index" json:"-"`
	UserID           uint           `gorm:"uniqueIndex:idx_user_achievement;not null" json:"userId"`
	AchievementRAID  uint           `gorm:"uniqueIndex:idx_user_achievement;not null" json:"achievementRaId"`
	RAGameID         uint           `gorm:"index;not null" json:"raGameId"`
	UnlockedAt       time.Time      `json:"unlockedAt"`
	IsHardcore       bool           `json:"isHardcore"`
	PlayTimeAtUnlock int64          `json:"playTimeAtUnlock"` // cumulative play time (seconds) at sync time
}

// SharedSaveState represents a save state shared by a user for others to download.
type SharedSaveState struct {
	ID            uint           `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time      `json:"createdAt"`
	UpdatedAt     time.Time      `json:"updatedAt"`
	DeletedAt     gorm.DeletedAt `gorm:"index" json:"-"`
	UserID        uint           `gorm:"index;not null" json:"userId"`
	User          User           `gorm:"foreignKey:UserID" json:"-"`
	GameID        uint           `gorm:"index;not null" json:"gameId"`
	Game          Game           `gorm:"foreignKey:GameID" json:"-"`
	Name          string         `gorm:"size:255;not null" json:"name"`
	Description   string         `gorm:"type:text" json:"description"`
	FilePath      string         `gorm:"size:1024;not null" json:"-"`
	FileSize      int64          `json:"fileSize"`
	ScreenshotURL string         `gorm:"size:512" json:"screenshotUrl"`
	DownloadCount int            `gorm:"default:0" json:"downloadCount"`
}

// GameRating represents a user's rating and optional review for a game.
type GameRating struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	UserID    uint           `gorm:"uniqueIndex:idx_user_game_rating;not null" json:"userId"`
	User      User           `gorm:"foreignKey:UserID" json:"-"`
	GameID    uint           `gorm:"uniqueIndex:idx_user_game_rating;not null;index" json:"gameId"`
	Game      Game           `gorm:"foreignKey:GameID" json:"-"`
	Rating    int            `gorm:"not null" json:"rating"` // 1-5
	Review    string         `gorm:"type:text" json:"review"`
}

// ActivityEvent represents a social activity event in the feed.
type ActivityEvent struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	UserID    uint           `gorm:"index;not null" json:"userId"`
	User      User           `gorm:"foreignKey:UserID" json:"-"`
	EventType string         `gorm:"size:64;not null;index" json:"eventType"` // started_playing, favorited_game, rated_game, shared_save
	GameID    uint           `gorm:"index" json:"gameId"`
	Game      Game           `gorm:"foreignKey:GameID" json:"-"`
	Metadata  string         `gorm:"type:text" json:"metadata"` // JSON
}

// GameCollection represents a user-created collection of games.
type GameCollection struct {
	ID          uint           `gorm:"primarykey" json:"id"`
	CreatedAt   time.Time      `json:"createdAt"`
	UpdatedAt   time.Time      `json:"updatedAt"`
	DeletedAt   gorm.DeletedAt `gorm:"index" json:"-"`
	UserID      uint           `gorm:"index;not null" json:"userId"`
	User        User           `gorm:"foreignKey:UserID" json:"-"`
	Name        string         `gorm:"size:255;not null" json:"name"`
	Description string         `gorm:"type:text" json:"description"`
	IsPublic    bool           `gorm:"default:false" json:"isPublic"`
	Items       []CollectionItem `gorm:"foreignKey:CollectionID" json:"items"`
}

// CollectionItem represents a game within a collection.
type CollectionItem struct {
	ID           uint           `gorm:"primarykey" json:"id"`
	CreatedAt    time.Time      `json:"createdAt"`
	DeletedAt    gorm.DeletedAt `gorm:"index" json:"-"`
	CollectionID uint           `gorm:"uniqueIndex:idx_collection_game;not null" json:"collectionId"`
	GameID       uint           `gorm:"uniqueIndex:idx_collection_game;not null" json:"gameId"`
	Game         Game           `gorm:"foreignKey:GameID" json:"game"`
	Position     int            `gorm:"default:0" json:"position"`
}

// PlayLaterItem represents a game in a user's Play Later queue.
type PlayLaterItem struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	UserID    uint           `gorm:"uniqueIndex:idx_play_later_user_game;not null" json:"userId"`
	User      User           `gorm:"foreignKey:UserID" json:"-"`
	GameID    uint           `gorm:"uniqueIndex:idx_play_later_user_game;not null" json:"gameId"`
	Game      Game           `gorm:"foreignKey:GameID" json:"game"`
	Position  int            `gorm:"not null;default:0" json:"position"`
}

// SharedSession represents a shared play session where friends take turns playing a game.
type SharedSession struct {
	ID           uint                   `gorm:"primarykey" json:"id"`
	CreatedAt    time.Time              `json:"createdAt"`
	UpdatedAt    time.Time              `json:"updatedAt"`
	DeletedAt    gorm.DeletedAt         `gorm:"index" json:"-"`
	OwnerID      uint                   `gorm:"index;not null" json:"ownerId"`
	Owner        User                   `gorm:"foreignKey:OwnerID" json:"-"`
	GameID       uint                   `gorm:"index;not null" json:"gameId"`
	Game         Game                   `gorm:"foreignKey:GameID" json:"-"`
	Name         string                 `gorm:"size:255;not null" json:"name"`
	Status       string                 `gorm:"size:32;default:active;not null" json:"status"` // "active", "completed", "archived"
	ActiveUserID *uint                  `json:"activeUserId"`
	TurnToken    string                 `gorm:"size:64" json:"-"`
	TurnTakenAt  *time.Time             `json:"turnTakenAt"`
	CoreName     string                 `gorm:"size:128" json:"coreName"`
	SessionID    *uint                  `gorm:"index" json:"sessionId"`
	Session      *GameSession           `json:"-"`
	Members      []SharedSessionMember  `gorm:"foreignKey:SharedSessionID" json:"members"`
}

// SharedSessionMember represents a user's membership in a shared session.
type SharedSessionMember struct {
	ID              uint           `gorm:"primarykey" json:"id"`
	CreatedAt       time.Time      `json:"createdAt"`
	DeletedAt       gorm.DeletedAt `gorm:"index" json:"-"`
	SharedSessionID uint           `gorm:"uniqueIndex:idx_shared_session_user;not null" json:"sharedSessionId"`
	SharedSession   SharedSession  `gorm:"foreignKey:SharedSessionID" json:"-"`
	UserID          uint           `gorm:"uniqueIndex:idx_shared_session_user;not null" json:"userId"`
	User            User           `gorm:"foreignKey:UserID" json:"-"`
	Role            string         `gorm:"size:16;default:member;not null" json:"role"` // "owner", "member"
	JoinedAt        time.Time      `json:"joinedAt"`
}

// SharedSessionInvite represents a pending invitation to join a shared session.
type SharedSessionInvite struct {
	ID              uint           `gorm:"primarykey" json:"id"`
	CreatedAt       time.Time      `json:"createdAt"`
	DeletedAt       gorm.DeletedAt `gorm:"index" json:"-"`
	SharedSessionID uint           `gorm:"uniqueIndex:idx_shared_session_invitee;not null" json:"sharedSessionId"`
	SharedSession   SharedSession  `gorm:"foreignKey:SharedSessionID" json:"-"`
	InviterID       uint           `gorm:"not null" json:"inviterId"`
	Inviter         User           `gorm:"foreignKey:InviterID" json:"-"`
	InviteeID       uint           `gorm:"uniqueIndex:idx_shared_session_invitee;not null" json:"inviteeId"`
	Invitee         User           `gorm:"foreignKey:InviteeID" json:"-"`
	Status          string         `gorm:"size:32;default:pending;not null" json:"status"` // "pending", "accepted", "declined"
}

// SharedSessionSave represents a save state within a shared session.
type SharedSessionSave struct {
	ID              uint           `gorm:"primarykey" json:"id"`
	CreatedAt       time.Time      `json:"createdAt"`
	UpdatedAt       time.Time      `json:"updatedAt"`
	DeletedAt       gorm.DeletedAt `gorm:"index" json:"-"`
	SharedSessionID uint           `gorm:"index;not null" json:"sharedSessionId"`
	SharedSession   SharedSession  `gorm:"foreignKey:SharedSessionID" json:"-"`
	UserID          uint           `gorm:"not null" json:"userId"`
	User            User           `gorm:"foreignKey:UserID" json:"-"`
	Name            string         `gorm:"size:255;not null" json:"name"`
	FilePath        string         `gorm:"size:1024;not null" json:"-"`
	FileSize        int64          `json:"fileSize"`
	ScreenshotURL   string         `gorm:"size:512" json:"screenshotUrl"`
	IsAuto          bool           `gorm:"default:false" json:"isAuto"`
}

// NetplaySession represents a real-time two-player netplay session.
type NetplaySession struct {
	ID           uint           `gorm:"primarykey" json:"id"`
	CreatedAt    time.Time      `json:"createdAt"`
	UpdatedAt    time.Time      `json:"updatedAt"`
	DeletedAt    gorm.DeletedAt `gorm:"index" json:"-"`
	HostUserID   uint           `gorm:"index;not null" json:"hostUserId"`
	HostUser     User           `gorm:"foreignKey:HostUserID" json:"-"`
	ClientUserID *uint          `gorm:"index" json:"clientUserId"`
	ClientUser   User           `gorm:"foreignKey:ClientUserID" json:"-"`
	GameID       uint           `gorm:"index;not null" json:"gameId"`
	Game         Game           `gorm:"foreignKey:GameID" json:"-"`
	Status       string         `gorm:"size:32;default:waiting;not null" json:"status"` // "waiting", "in_progress", "ended"
	EndReason    string         `gorm:"size:32" json:"endReason"`              // "host_left", "client_left", "timeout", "completed"
	InputDelay   int            `gorm:"default:3" json:"inputDelay"`
	CoreName     string         `gorm:"size:128" json:"coreName"`
	InviteCode   string         `gorm:"uniqueIndex;size:6;not null" json:"inviteCode"`
	StartedAt    *time.Time     `json:"startedAt"`
	EndedAt      *time.Time     `json:"endedAt"`
}

// NetplayInvite represents a user-to-user invitation to join a netplay session.
type NetplayInvite struct {
	ID               uint           `gorm:"primarykey" json:"id"`
	CreatedAt        time.Time      `json:"createdAt"`
	DeletedAt        gorm.DeletedAt `gorm:"index" json:"-"`
	NetplaySessionID uint           `gorm:"uniqueIndex:idx_netplay_invite_session_invitee;not null" json:"netplaySessionId"`
	NetplaySession   NetplaySession `gorm:"foreignKey:NetplaySessionID" json:"-"`
	InviterID        uint           `gorm:"not null" json:"inviterId"`
	Inviter          User           `gorm:"foreignKey:InviterID" json:"-"`
	InviteeID        uint           `gorm:"uniqueIndex:idx_netplay_invite_session_invitee;not null" json:"inviteeId"`
	Invitee          User           `gorm:"foreignKey:InviteeID" json:"-"`
	Status           string         `gorm:"size:32;default:pending;not null" json:"status"` // "pending", "accepted", "declined", "expired"
}

// Challenge represents a user-created game challenge.
type Challenge struct {
	ID              uint           `gorm:"primarykey" json:"id"`
	CreatedAt       time.Time      `json:"createdAt"`
	UpdatedAt       time.Time      `json:"updatedAt"`
	DeletedAt       gorm.DeletedAt `gorm:"index" json:"-"`
	CreatorID       uint           `gorm:"index;not null" json:"creatorId"`
	Creator         User           `gorm:"foreignKey:CreatorID" json:"-"`
	GameID          uint           `gorm:"index;not null" json:"gameId"`
	Game            Game           `gorm:"foreignKey:GameID" json:"-"`
	Name            string         `gorm:"size:255;not null" json:"name"`
	Description     string         `gorm:"type:text" json:"description"`
	Type            string         `gorm:"size:32;not null;default:completion" json:"type"`       // "completion", "speedrun", "survival"
	Difficulty      string         `gorm:"size:32;not null;default:medium" json:"difficulty"`     // "easy", "medium", "hard"
	Status          string         `gorm:"size:32;not null;default:active;index" json:"status"`   // "active", "closed", "expired"
	SaveFilePath    string         `gorm:"size:1024;not null" json:"-"`
	SaveFileSize    int64          `json:"saveFileSize"`
	ScreenshotPath  string         `gorm:"size:512" json:"-"`
	CoreName        string         `gorm:"size:128" json:"coreName"`
	AttemptCount    int            `gorm:"default:0" json:"attemptCount"`
	CompletionCount int            `gorm:"default:0" json:"completionCount"`
	ExpiresAt       *time.Time     `json:"expiresAt"`
}

// ChallengeAttempt represents a user's attempt at a challenge.
type ChallengeAttempt struct {
	ID          uint           `gorm:"primarykey" json:"id"`
	CreatedAt   time.Time      `json:"createdAt"`
	UpdatedAt   time.Time      `json:"updatedAt"`
	DeletedAt   gorm.DeletedAt `gorm:"index" json:"-"`
	ChallengeID uint           `gorm:"index:idx_challenge_attempt;not null" json:"challengeId"`
	Challenge   Challenge      `gorm:"foreignKey:ChallengeID" json:"-"`
	UserID      uint           `gorm:"index:idx_challenge_attempt;not null" json:"userId"`
	User        User           `gorm:"foreignKey:UserID" json:"-"`
	Status      string         `gorm:"size:32;not null;default:in_progress" json:"status"` // "in_progress", "completed", "abandoned"
	StartedAt   time.Time      `gorm:"not null" json:"startedAt"`
	CompletedAt *time.Time     `json:"completedAt"`
	DurationMs  int64          `gorm:"default:0" json:"durationMs"`
	IsBest      bool           `gorm:"default:false" json:"isBest"`
}

// GameKeyMappingPreference stores a user's per-game key mapping override.
type GameKeyMappingPreference struct {
	gorm.Model
	UserID        uint   `json:"userId" gorm:"not null;uniqueIndex:idx_user_game_keymapping"`
	GameID        uint   `json:"gameId" gorm:"not null;uniqueIndex:idx_user_game_keymapping"`
	CustomMapping string `json:"customMapping" gorm:"type:text"` // JSON string of map[string]string
}

// TopRatedGame caches IGDB top-rated games per console.
type TopRatedGame struct {
	ID                uint           `gorm:"primarykey" json:"id"`
	CreatedAt         time.Time      `json:"createdAt"`
	UpdatedAt         time.Time      `json:"updatedAt"`
	DeletedAt         gorm.DeletedAt `gorm:"index" json:"-"`
	ConsoleID         uint           `gorm:"uniqueIndex:idx_console_igdb_game;not null" json:"consoleId"`
	Console           Console        `gorm:"foreignKey:ConsoleID" json:"-"`
	IGDBGameID        int            `gorm:"uniqueIndex:idx_console_igdb_game;not null" json:"igdbGameId"`
	Name              string         `gorm:"size:255;not null" json:"name"`
	CoverImageID      string         `gorm:"size:128" json:"coverImageId"`
	CoverLocalPath    string         `gorm:"size:512" json:"-"`
	TotalRating       float64        `json:"totalRating"`
	TotalRatingCount  int            `json:"totalRatingCount"`
	UserRating        float64        `json:"userRating"`
	UserRatingCount   int            `json:"userRatingCount"`
	CriticRating      float64        `json:"criticRating"`
	CriticRatingCount int            `json:"criticRatingCount"`
	Rank              int            `json:"rank"`
}

// SimilarGame caches IGDB similar games for a local game.
type SimilarGame struct {
	ID           uint           `gorm:"primarykey" json:"id"`
	CreatedAt    time.Time      `json:"createdAt"`
	UpdatedAt    time.Time      `json:"updatedAt"`
	DeletedAt    gorm.DeletedAt `gorm:"index" json:"-"`
	GameID       uint           `gorm:"index;not null" json:"gameId"`
	Game         Game           `gorm:"foreignKey:GameID" json:"-"`
	IGDBGameID   int            `gorm:"not null" json:"igdbGameId"`
	Name         string         `gorm:"size:255;not null" json:"name"`
	CoverImageID   string         `gorm:"size:128" json:"coverImageId"`
	CoverLocalPath    string         `gorm:"size:512" json:"-"`
	IGDBCriticsRating float64        `gorm:"column:rating" json:"igdbCriticsRating"`
	LocalGameID       *uint          `json:"localGameId"`
}

// Core represents a libretro core.
type Core struct {
	ID          uint           `gorm:"primarykey" json:"id"`
	CreatedAt   time.Time      `json:"createdAt"`
	UpdatedAt   time.Time      `json:"updatedAt"`
	DeletedAt   gorm.DeletedAt `gorm:"index" json:"-"`
	Name        string         `gorm:"uniqueIndex;size:128;not null" json:"name"`
	DisplayName string         `gorm:"size:255" json:"displayName"`
	Description string         `gorm:"type:text" json:"description"`
	Version     string         `gorm:"size:64" json:"version"`
	Platforms   string         `gorm:"size:255" json:"platforms"` // comma-separated: windows,linux,macos,android
	FilePath    string         `gorm:"size:1024" json:"-"`
	// CustomDownloadURL is an OPTIONAL override URL — set only for cores
	// that aren't pulled from the libretro buildbot (e.g. the seeded
	// `azahar` 3DS core). When unset/empty, the player constructs the
	// buildbot URL from the core name. {platform} in the template is
	// substituted with the player's runtime platform.
	//
	// Old name was `DownloadURL` / json:`downloadUrl`, which read like
	// "the URL" and led to consumers checking `!= null` only and
	// passing empty strings into URL resolution. Renamed to make the
	// override semantics explicit. The DB column stays `download_url`
	// to avoid a migration.
	CustomDownloadURL string `gorm:"column:download_url;size:1024" json:"customDownloadUrl"`

	// Factual metadata about the cached binary, populated by the server
	// whenever it downloads or serves a core for the first time. See #555.
	// Nullable pointer for FetchedAt so admin UI can distinguish "never
	// fetched" from "fetched long ago".
	Sha256    string     `gorm:"size:64" json:"sha256"`    // hex sha256 of the cached binary
	SizeBytes int64      `json:"sizeBytes"`                // byte length of the cached binary
	FetchedAt *time.Time `json:"fetchedAt"`                // when the binary was last downloaded
	SourceURL string     `gorm:"size:1024" json:"sourceUrl"` // URL we pulled the binary from
}

// CheatCode represents a cheat code for a specific game.
type CheatCode struct {
	ID          uint   `gorm:"primarykey"`
	GameID      uint   `gorm:"uniqueIndex:idx_game_cheat;not null"`
	CheatIndex  int    `gorm:"uniqueIndex:idx_game_cheat;not null"`
	Description string `gorm:"size:512;not null"`
	Code        string `gorm:"size:1024;not null"`
}

// GameSession represents a user's play session for a game.
// Each session groups save states and SRAM data together as a "playthrough".
type GameSession struct {
	ID             uint           `gorm:"primarykey" json:"id"`
	CreatedAt      time.Time      `json:"createdAt"`
	UpdatedAt      time.Time      `json:"updatedAt"`
	DeletedAt      gorm.DeletedAt `gorm:"index" json:"-"`
	OwnerID        uint           `gorm:"index;not null" json:"ownerId"`
	Owner          User           `gorm:"foreignKey:OwnerID" json:"-"`
	GameID         uint           `gorm:"index;not null" json:"gameId"`
	Game           Game           `gorm:"foreignKey:GameID" json:"-"`
	Name           string         `gorm:"size:255;not null" json:"name"`
	LastPlayedAt   *time.Time     `json:"lastPlayedAt"`
	LastPlayedBy   *uint          `json:"lastPlayedBy"`
	TotalPlayTime  int64          `gorm:"default:0" json:"totalPlayTime"` // seconds
	ScreenshotURL  string         `gorm:"size:512" json:"screenshotUrl"`
	CoreName       string         `gorm:"size:128" json:"coreName"`
	CheatsEnabled  bool           `gorm:"default:false" json:"cheatsEnabled"`
	// PinnedCoreSha256 is the sha256 of the libretro core binary this session
	// was first manually saved with. Set lazily on the first manual save (or
	// auto-save) once we can resolve the matching Core row by name; never
	// overwritten afterwards. Sessions cloned from another session inherit
	// this pin so the player can request the exact historical binary via
	// /api/cores/{id}/download?sha256=… (see #555 Phase 3).
	PinnedCoreSha256 string `gorm:"size:64" json:"pinnedCoreSha256"`

	// UserLockedCoreVersion is true when the user explicitly locked this
	// session to PinnedCoreSha256 via the core-upgrade decision UI (issue
	// #672 sheets A / C / D). Separate from "pin was seeded from the
	// first save" — a lock always beats the global
	// User.AutoUpdateCoresEnabled preference; the lock is cleared only
	// by an explicit unlock from the session detail screen or by
	// PATCH /api/sessions/{id} with userLockedCoreVersion=false.
	UserLockedCoreVersion bool `gorm:"default:false" json:"userLockedCoreVersion"`

	// AutoLoadSuppressed is true when the next launch of this session
	// should skip automatic save-state load (set after the user picked
	// "Start fresh on the new version" on sheet D, or picks the same
	// option on sheet B). Cleared on the first successful manual save
	// written against this session. See #672.
	AutoLoadSuppressed bool `gorm:"default:false" json:"autoLoadSuppressed"`

	// RehearsalCrashPending is set by the player before entering the
	// "try with my save" rehearsal mode for this session; it is cleared
	// on clean resolution of sheet C or D. If the flag is still set on
	// next app launch we treat that as an app-level crash during
	// rehearsal and route the user directly to sheet D. See #672.
	RehearsalCrashPending bool `gorm:"default:false" json:"rehearsalCrashPending"`
}

// SessionSaveState represents a save state within a game session.
type SessionSaveState struct {
	ID            uint           `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time      `json:"createdAt"`
	UpdatedAt     time.Time      `json:"updatedAt"`
	DeletedAt     gorm.DeletedAt `gorm:"index" json:"-"`
	SessionID     uint           `gorm:"index;not null" json:"sessionId"`
	UserID        uint           `gorm:"index;not null" json:"userId"`
	User          User           `gorm:"foreignKey:UserID" json:"-"`
	Name          string         `gorm:"size:255;not null" json:"name"`
	FilePath      string         `gorm:"size:1024;not null" json:"-"`
	FileSize      int64          `json:"fileSize"`
	ScreenshotURL string         `gorm:"size:512" json:"screenshotUrl"`
	IsAuto        bool           `gorm:"default:false" json:"isAuto"`
	IsCurrent     bool           `gorm:"default:false" json:"isCurrent"`
	CoreName      string         `gorm:"size:128" json:"coreName"`
	// CoreSha256 is the hex sha256 of the core binary that produced
	// this save state. Populated by the player at save-upload time when
	// available. Used for diagnostics (matching a failing load against
	// the specific binary that wrote the save) and — eventually — for
	// rollback UX that offers the exact core version the save was made
	// with. Empty when the player didn't supply one. See #555 Phase 3.
	CoreSha256    string         `gorm:"size:64" json:"coreSha256"`
	Notes         string         `gorm:"type:text" json:"notes"`
	Slot          *int           `json:"slot"`
	// Compression algorithm applied to the bytes at FilePath. Empty
	// string = uncompressed (the only case for pre-#804 saves). Known
	// values: "" | "gzip". Players that don't recognise a value should
	// reject the save with a "newer client required" error rather than
	// load garbage. See #804 phase 2.
	Compression   string         `gorm:"size:16" json:"compression"`
}

// SessionSaveData represents SRAM/battery save data within a game session.
type SessionSaveData struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	SessionID uint           `gorm:"index;not null" json:"sessionId"`
	FilePath  string         `gorm:"size:1024;not null" json:"-"`
	FileSize  int64          `json:"fileSize"`
}

// SessionCheatSetting stores per-cheat enable/disable state within a game session.
type SessionCheatSetting struct {
	ID         uint      `gorm:"primarykey" json:"id"`
	CreatedAt  time.Time `json:"createdAt"`
	SessionID  uint      `gorm:"uniqueIndex:idx_session_cheat;not null" json:"sessionId"`
	CheatIndex int       `gorm:"uniqueIndex:idx_session_cheat;not null" json:"cheatIndex"`
	Enabled    bool      `gorm:"default:false" json:"enabled"`
}

// GameArtwork stores SteamGridDB artwork URLs for a game (hero banners, grids, logos, icons).
type GameArtwork struct {
	ID              uint      `gorm:"primarykey" json:"id"`
	GameID          uint      `gorm:"uniqueIndex;not null" json:"gameId"`
	SteamGridDBID   int       `json:"steamGridDbId"`
	HeroURL         string    `gorm:"size:1024" json:"heroUrl"`
	HeroManuallySet bool      `gorm:"default:false" json:"-"` // When true, scraper skips overwriting hero during rescrapes
	GridURL         string    `gorm:"size:1024" json:"gridUrl"`
	LogoURL         string    `gorm:"size:1024" json:"logoUrl"`
	IconURL         string    `gorm:"size:1024" json:"iconUrl"`
	CreatedAt       time.Time `json:"createdAt"`
	UpdatedAt       time.Time `json:"updatedAt"`
}

// Company represents an IGDB game development/publishing company with metadata.
type Company struct {
	ID            uint      `gorm:"primarykey" json:"id"`
	IGDBCompanyID int       `gorm:"uniqueIndex" json:"igdbCompanyId"`
	Name          string    `gorm:"index" json:"name"`
	Description   string    `gorm:"type:text" json:"description"`
	LogoURL       string    `gorm:"size:512" json:"logoUrl"`
	LogoImageID   string    `gorm:"size:128" json:"-"`
	Country       int       `json:"country"`
	FoundedYear   int       `json:"foundedYear"`
	WebsiteURL    string    `gorm:"size:512" json:"websiteUrl"`
	WikipediaURL  string    `gorm:"size:512" json:"wikipediaUrl"`
	CreatedAt     time.Time `json:"createdAt"`
	UpdatedAt     time.Time `json:"updatedAt"`
}

// --- Phase 2 Explore: IGDB Enrichment models ---

// GameTheme stores an IGDB theme associated with a game (e.g., "Fantasy", "Sci-Fi").
type GameTheme struct {
	ID          uint      `gorm:"primarykey" json:"id"`
	CreatedAt   time.Time `json:"createdAt"`
	GameID      uint      `gorm:"uniqueIndex:idx_game_theme;not null" json:"gameId"`
	IGDBThemeID int       `gorm:"uniqueIndex:idx_game_theme;not null" json:"igdbThemeId"`
	Name        string    `gorm:"size:255;not null" json:"name"`
}

// GameKeyword stores an IGDB keyword associated with a game (e.g., "time travel", "zombies").
type GameKeyword struct {
	ID            uint      `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time `json:"createdAt"`
	GameID        uint      `gorm:"uniqueIndex:idx_game_keyword;not null" json:"gameId"`
	IGDBKeywordID int       `gorm:"uniqueIndex:idx_game_keyword;not null" json:"igdbKeywordId"`
	Name          string    `gorm:"size:255;not null" json:"name"`
}

// GamePlayerPerspective stores a player perspective for a game (e.g., "First person", "Side view").
type GamePlayerPerspective struct {
	ID                uint      `gorm:"primarykey" json:"id"`
	CreatedAt         time.Time `json:"createdAt"`
	GameID            uint      `gorm:"uniqueIndex:idx_game_perspective;not null" json:"gameId"`
	IGDBPerspectiveID int       `gorm:"uniqueIndex:idx_game_perspective;not null" json:"igdbPerspectiveId"`
	Name              string    `gorm:"size:255;not null" json:"name"`
}

// GameFranchise stores a franchise association for a game (e.g., "Mario", "Zelda").
type GameFranchise struct {
	ID              uint      `gorm:"primarykey" json:"id"`
	CreatedAt       time.Time `json:"createdAt"`
	GameID          uint      `gorm:"uniqueIndex:idx_game_franchise;not null" json:"gameId"`
	IGDBFranchiseID int       `gorm:"uniqueIndex:idx_game_franchise;not null" json:"igdbFranchiseId"`
	FranchiseName   string    `gorm:"size:255;not null" json:"franchiseName"`
}

// GameSeries represents an IGDB collection (game series like "Super Mario", "The Legend of Zelda").
// Named "Series" to avoid collision with user-created GameCollection.
type GameSeries struct {
	ID               uint              `gorm:"primarykey" json:"id"`
	CreatedAt        time.Time         `json:"createdAt"`
	UpdatedAt        time.Time         `json:"updatedAt"`
	IGDBCollectionID int               `gorm:"uniqueIndex;not null" json:"igdbCollectionId"`
	Name             string            `gorm:"size:255;not null" json:"name"`
	Entries          []GameSeriesEntry `gorm:"foreignKey:SeriesID" json:"entries"`
}

// GameSeriesEntry represents a game within a series. GameID is nullable because the
// game may not be in the local library (supports "You own 8 of 15 games" display).
type GameSeriesEntry struct {
	ID           uint      `gorm:"primarykey" json:"id"`
	CreatedAt    time.Time `json:"createdAt"`
	SeriesID     uint      `gorm:"uniqueIndex:idx_series_igdb_game;not null" json:"seriesId"`
	GameID       *uint     `gorm:"index" json:"gameId"`
	IGDBGameID   int       `gorm:"uniqueIndex:idx_series_igdb_game;not null" json:"igdbGameId"`
	Name         string    `gorm:"size:255;not null" json:"name"`
	CoverImageID string    `gorm:"size:255" json:"coverImageId"`
}

// GameFranchiseGroup represents an IGDB franchise (broader brand like "Mario", "Zelda").
// Analogous to GameSeries but for franchises.
type GameFranchiseGroup struct {
	ID              uint                 `gorm:"primarykey" json:"id"`
	CreatedAt       time.Time            `json:"createdAt"`
	UpdatedAt       time.Time            `json:"updatedAt"`
	IGDBFranchiseID int                  `gorm:"uniqueIndex;not null" json:"igdbFranchiseId"`
	Name            string               `gorm:"size:255;not null" json:"name"`
	Entries         []GameFranchiseEntry `gorm:"foreignKey:FranchiseGroupID" json:"entries"`
}

// GameFranchiseEntry represents a game within a franchise. GameID is nullable because
// the game may not be in the local library (supports "You own 8 of 15 games" display).
type GameFranchiseEntry struct {
	ID               uint      `gorm:"primarykey" json:"id"`
	CreatedAt        time.Time `json:"createdAt"`
	FranchiseGroupID uint      `gorm:"uniqueIndex:idx_franchise_igdb_game;not null" json:"franchiseGroupId"`
	GameID           *uint     `gorm:"index" json:"gameId"`
	IGDBGameID       int       `gorm:"uniqueIndex:idx_franchise_igdb_game;not null" json:"igdbGameId"`
	Name             string    `gorm:"size:255;not null" json:"name"`
	CoverImageID     string    `gorm:"size:255" json:"coverImageId"`
}

// GameArtworkImage stores IGDB promotional artwork (distinct from SteamGridDB GameArtwork).
type GameArtworkImage struct {
	ID          uint      `gorm:"primarykey" json:"id"`
	CreatedAt   time.Time `json:"createdAt"`
	GameID      uint      `gorm:"uniqueIndex:idx_game_artwork_image;not null" json:"gameId"`
	IGDBImageID string    `gorm:"uniqueIndex:idx_game_artwork_image;size:255;not null" json:"igdbImageId"`
	LocalPath   string    `gorm:"size:512" json:"-"`
	Width       int       `json:"width"`
	Height      int       `json:"height"`
}

// SavedSearch represents a user's saved filter configuration for the game library.
type SavedSearch struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	UserID    uint           `gorm:"index;not null" json:"userId"`
	Name      string         `gorm:"size:255;not null" json:"name"`
	Filters   string         `gorm:"type:text;not null" json:"filters"` // JSON-encoded filter params
}

// UserAchievementShowcase stores a user's pinned achievement for their profile.
type UserAchievementShowcase struct {
	ID              uint      `gorm:"primarykey" json:"id"`
	UserID          uint      `gorm:"uniqueIndex:idx_user_achievement_showcase;not null" json:"userId"`
	AchievementRAID uint      `gorm:"uniqueIndex:idx_user_achievement_showcase;not null" json:"achievementRaId"`
	RAGameID        uint      `gorm:"not null" json:"raGameId"`
	ShowcaseOrder   int       `gorm:"not null" json:"showcaseOrder"`
	CreatedAt       time.Time `json:"createdAt"`
}

// GameScrapeResult tracks the outcome of a scrape attempt for a specific source.
// One row per game per source (igdb, libretro, steamgriddb).
type GameScrapeResult struct {
	ID            uint       `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time  `json:"createdAt"`
	UpdatedAt     time.Time  `json:"updatedAt"`
	GameID        uint       `gorm:"uniqueIndex:idx_scrape_result_game_source;not null" json:"gameId"`
	Source        string     `gorm:"uniqueIndex:idx_scrape_result_game_source;size:32;not null" json:"source"`
	Status        string     `gorm:"size:32;not null" json:"status"`
	SourceID      string     `gorm:"size:128" json:"sourceId"`
	LastAttemptAt *time.Time `json:"lastAttemptAt"`
	ErrorMessage  string     `gorm:"size:512" json:"errorMessage"`
}

// StagedUpload represents a ROM file uploaded to the staging area pending admin review.
type StagedUpload struct {
	ID               uint           `gorm:"primarykey" json:"id"`
	CreatedAt        time.Time      `json:"createdAt"`
	UpdatedAt        time.Time      `json:"updatedAt"`
	DeletedAt        gorm.DeletedAt `gorm:"index" json:"-"`
	FileName         string         `gorm:"size:512;not null" json:"fileName"`
	OriginalFileName string         `gorm:"size:512;not null" json:"originalFileName"`
	FilePath         string         `gorm:"size:1024;not null" json:"-"`
	FileSize         int64          `json:"fileSize"`
	ConsoleID        *uint          `json:"consoleId"`
	Console          Console        `gorm:"foreignKey:ConsoleID" json:"-"`
	PossibleConsoles string         `gorm:"size:512" json:"possibleConsoles"` // JSON array of abbreviations
	Status           string         `gorm:"size:32;not null;default:pending_console" json:"status"`
	// Scrape results
	Title       string  `gorm:"size:255" json:"title"`
	CoverURL    string  `gorm:"size:512" json:"coverUrl"`
	Description       string  `gorm:"type:text" json:"description"`
	IGDBCriticsRating float64 `gorm:"column:rating" json:"igdbCriticsRating"`
	Developer         string  `gorm:"size:255" json:"developer"`
	Publisher   string  `gorm:"size:255" json:"publisher"`
	Genre       string  `gorm:"size:128" json:"genre"`
	Players     int     `json:"players"`
	ReleaseDate string  `gorm:"size:32" json:"releaseDate"`
	// Verification
	VerificationStatus string `gorm:"size:32" json:"verificationStatus"` // verified, unverified, not_applicable
	CRC32              string `gorm:"size:16" json:"crc32"`
	CanonicalName      string `gorm:"size:512" json:"canonicalName"`
	// Duplicate detection
	DuplicateOfGameID *uint `json:"duplicateOfGameId"`
}
