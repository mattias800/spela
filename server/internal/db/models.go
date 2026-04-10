package db

import (
	"time"

	"gorm.io/gorm"
)

// Role constants for the role hierarchy: owner > admin > user.
const (
	RoleOwner = "owner"
	RoleAdmin = "admin"
	RoleUser  = "user"
)

// IsAdminOrOwner returns true if the role is admin or owner.
func IsAdminOrOwner(role string) bool {
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
	Role         string         `gorm:"size:16;default:user" json:"role"` // "owner", "admin", or "user"
	AvatarURL    string         `gorm:"size:512" json:"avatarUrl,omitempty"`
	TokenVersion        int            `gorm:"default:0" json:"-"`
	Disabled            bool           `gorm:"default:false" json:"disabled"`
	PendingApproval     bool           `gorm:"default:false" json:"pendingApproval"`
	ShowPerfOverlay     bool           `gorm:"default:false" json:"showPerformanceOverlay"`
	AutoSaveEnabled     bool           `gorm:"default:true" json:"autoSaveEnabled"`
	AutoLoadSaveEnabled bool           `gorm:"default:true" json:"autoLoadSaveEnabled"`
	SelectedShader      string         `gorm:"size:64;default:none" json:"selectedShader"`
	SelectedTheme       string         `gorm:"size:64;default:default-dark" json:"selectedTheme"`
	SelectedKeyMapping  string         `gorm:"size:64;default:arrows-left" json:"selectedKeyMapping"`
	CustomKeyMapping         string         `gorm:"type:text" json:"customKeyMapping,omitempty"` // JSON: {"0":"z","1":"x",...}
	DefaultSecondScreenPage string         `gorm:"size:64;default:art" json:"defaultSecondScreenPage"`
	PreferredRegions         string         `gorm:"size:255" json:"preferredRegions,omitempty"` // comma-separated ordered list, e.g. "USA,Europe,World"
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
	Consoles  []Console `gorm:"foreignKey:HardwareMakerID" json:"consoles,omitempty"`
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
	HardwareMaker    *HardwareMaker `gorm:"foreignKey:HardwareMakerID" json:"hardwareMaker,omitempty"`
	MediaTypeID      *uint          `json:"mediaTypeId"`
	MediaType        *MediaType     `gorm:"foreignKey:MediaTypeID" json:"mediaType,omitempty"`
	ReleaseYear      *int           `json:"releaseYear"`
	UnitsSold        *int64         `json:"unitsSold"`
	Summary          *string        `gorm:"type:text" json:"summary"`
	Games            []Game         `gorm:"foreignKey:ConsoleID" json:"games,omitempty"`
	GameCount      int            `gorm:"-" json:"gameCount,omitempty"`
}

// Game represents a detected ROM/game file.
type Game struct {
	ID            uint           `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time      `json:"createdAt"`
	UpdatedAt     time.Time      `json:"updatedAt"`
	DeletedAt     gorm.DeletedAt `gorm:"index" json:"-"`
	ConsoleID     uint           `gorm:"index;not null;index:idx_console_group_key,priority:1;index:idx_console_is_primary,priority:1" json:"consoleId"`
	Console       Console        `gorm:"foreignKey:ConsoleID" json:"console,omitempty"`
	Title         string         `gorm:"size:255;not null" json:"title"`
	FileName      string         `gorm:"size:512;not null" json:"fileName"`
	FilePath      string         `gorm:"uniqueIndex;size:1024;not null" json:"-"`
	FileSize      int64          `json:"fileSize"`
	DiscCount     int            `json:"discCount"`                                    // 0 = single-disc legacy, 2+ = multi-disc
	Discs         []GameDisc     `gorm:"foreignKey:GameID" json:"discs,omitempty"`
	Description   string         `gorm:"type:text" json:"description,omitempty"`
	CoverURL      string         `gorm:"size:512" json:"coverUrl,omitempty"`
	ScreenshotURL string         `gorm:"size:512" json:"screenshotUrl,omitempty"`
	Developer     string         `gorm:"size:255" json:"developer,omitempty"`
	Publisher     string         `gorm:"size:255" json:"publisher,omitempty"`
	ReleaseDate   string         `gorm:"size:32" json:"releaseDate,omitempty"`
	Genre         string         `gorm:"size:512" json:"genre,omitempty"`
	GameModes            string  `gorm:"size:255" json:"gameModes,omitempty"`
	Storyline            string  `gorm:"type:text" json:"storyline,omitempty"`
	TotalRating          float64 `json:"totalRating,omitempty"`
	TotalRatingCount     int     `json:"totalRatingCount,omitempty"`
	IGDBUserRating       float64 `json:"igdbUserRating,omitempty"`
	IGDBUserRatingCount  int     `json:"igdbUserRatingCount,omitempty"`
	TimeToBeatHastily    int     `json:"timeToBeatHastily,omitempty"`
	TimeToBeatNormally   int     `json:"timeToBeatNormally,omitempty"`
	TimeToBeatCompletely int     `json:"timeToBeatCompletely,omitempty"`
	Players              int     `json:"players,omitempty"`
	IGDBCriticsRating    float64 `gorm:"column:rating" json:"igdbCriticsRating,omitempty"`
	CoreOverride         string  `gorm:"size:128" json:"coreOverride,omitempty"`
	LibRetroCoverURL    string         `gorm:"size:512" json:"-"`
	IGDBCoverURL        string         `gorm:"size:512" json:"-"`
	CoverManuallySet    bool           `gorm:"default:false" json:"-"`
	ScrapeAttempts      int            `json:"scrapeAttempts"`
	ScraperID           string         `gorm:"size:128" json:"scraperId,omitempty"`
	AchievementsWarning string         `gorm:"size:512" json:"achievementsWarning,omitempty"`
	VerificationStatus  string         `gorm:"size:32" json:"verificationStatus,omitempty"`
	VerificationTag     string         `gorm:"size:128" json:"verificationTag,omitempty"`
	Region              string         `gorm:"size:128" json:"region,omitempty"`
	Revision            string         `gorm:"size:64" json:"revision,omitempty"`
	Tags                string         `gorm:"size:255" json:"tags,omitempty"`
	IsPreRelease        bool           `gorm:"default:false;index:idx_game_is_pre_release" json:"isPreRelease"`
	GroupKey            string         `gorm:"size:255;index:idx_game_group_key;index:idx_console_group_key,priority:2" json:"groupKey,omitempty"`
	IsPrimary           bool           `gorm:"default:false;index:idx_game_is_primary;index:idx_console_is_primary,priority:2" json:"isPrimary"`
	PrimaryGameID       *uint          `json:"primaryGameId,omitempty"`
	ParentGameID        *uint          `gorm:"index:idx_game_parent" json:"parentGameId,omitempty"` // links standalone ROM hacks to their base game
	PartyInfo           string         `gorm:"size:512" json:"partyInfo,omitempty"`                // Demo party and placement, e.g. "Assembly 1993, 1st place"
	CRC32               string         `gorm:"size:16" json:"-"`
	RAGameID            uint           `gorm:"index" json:"-"` // RetroAchievements game ID (cached from hash lookup)
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
	Platform string `gorm:"size:128" json:"platform,omitempty"`
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
	Game      Game           `gorm:"foreignKey:GameID" json:"game,omitempty"`
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
	Game      Game           `gorm:"foreignKey:GameID" json:"game,omitempty"`
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

// Security event types. Keep in sync with the slog event= discriminators
// emitted by auth_handler.go and middleware.go.
const (
	SecurityEventLoginSuccess         = "login_success"
	SecurityEventLoginFailed          = "login_failed"
	SecurityEventLoginLocked          = "login_locked"
	SecurityEventLoginBlocked         = "login_blocked"
	SecurityEventAccountLocked        = "account_locked"
	SecurityEventRevokedTokenUsed     = "revoked_token_used"
	SecurityEventDisabledAccountToken = "disabled_account_token"
	SecurityEventTokenUserMissing     = "token_user_missing"
	SecurityEventStaleTokenVersion    = "stale_token_version"
)

// SecurityEvent records an admin-only audit entry for an authentication or
// session-related event. These rows back the /admin/security-events page so
// admins can investigate suspicious activity without tailing container logs.
//
// Unlike LoginAttempt (which stores a SHA-256 hash of the username for lockout
// counters), SecurityEvent deliberately stores the raw username — admins need
// to read it to investigate incidents, and the table has a short retention
// window (see securityEventRetention in auth_handler.go) to limit exposure.
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
type SecurityEvent struct {
	ID            uint      `gorm:"primarykey"`
	CreatedAt     time.Time `gorm:"index;not null"`
	EventType     string    `gorm:"size:64;not null;index"`
	Reason        string    `gorm:"size:64;index"`
	Username      string    `gorm:"size:128;index"`
	UsernameLower string    `gorm:"size:128;index"`
	UserID        *uint     `gorm:"index"`
	IP            string    `gorm:"size:64;index"`
	Path          string    `gorm:"size:256"`
	Metadata      string    `gorm:"type:text"`
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
	CustomMapping   string         `gorm:"type:text" json:"customMapping,omitempty"` // JSON
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
	Description   string         `gorm:"type:text" json:"description,omitempty"`
	FilePath      string         `gorm:"size:1024;not null" json:"-"`
	FileSize      int64          `json:"fileSize"`
	ScreenshotURL string         `gorm:"size:512" json:"screenshotUrl,omitempty"`
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
	Review    string         `gorm:"type:text" json:"review,omitempty"`
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
	Metadata  string         `gorm:"type:text" json:"metadata,omitempty"` // JSON
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
	Description string         `gorm:"type:text" json:"description,omitempty"`
	IsPublic    bool           `gorm:"default:false" json:"isPublic"`
	Items       []CollectionItem `gorm:"foreignKey:CollectionID" json:"items,omitempty"`
}

// CollectionItem represents a game within a collection.
type CollectionItem struct {
	ID           uint           `gorm:"primarykey" json:"id"`
	CreatedAt    time.Time      `json:"createdAt"`
	DeletedAt    gorm.DeletedAt `gorm:"index" json:"-"`
	CollectionID uint           `gorm:"uniqueIndex:idx_collection_game;not null" json:"collectionId"`
	GameID       uint           `gorm:"uniqueIndex:idx_collection_game;not null" json:"gameId"`
	Game         Game           `gorm:"foreignKey:GameID" json:"game,omitempty"`
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
	Game      Game           `gorm:"foreignKey:GameID" json:"game,omitempty"`
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
	ActiveUserID *uint                  `json:"activeUserId,omitempty"`
	TurnToken    string                 `gorm:"size:64" json:"-"`
	TurnTakenAt  *time.Time             `json:"turnTakenAt,omitempty"`
	CoreName     string                 `gorm:"size:128" json:"coreName,omitempty"`
	SessionID    *uint                  `gorm:"index" json:"sessionId"`
	Session      *GameSession           `json:"-"`
	Members      []SharedSessionMember  `gorm:"foreignKey:SharedSessionID" json:"members,omitempty"`
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
	ScreenshotURL   string         `gorm:"size:512" json:"screenshotUrl,omitempty"`
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
	ClientUserID *uint          `gorm:"index" json:"clientUserId,omitempty"`
	ClientUser   User           `gorm:"foreignKey:ClientUserID" json:"-"`
	GameID       uint           `gorm:"index;not null" json:"gameId"`
	Game         Game           `gorm:"foreignKey:GameID" json:"-"`
	Status       string         `gorm:"size:32;default:waiting;not null" json:"status"` // "waiting", "in_progress", "ended"
	EndReason    string         `gorm:"size:32" json:"endReason,omitempty"`              // "host_left", "client_left", "timeout", "completed"
	InputDelay   int            `gorm:"default:3" json:"inputDelay"`
	CoreName     string         `gorm:"size:128" json:"coreName"`
	InviteCode   string         `gorm:"uniqueIndex;size:6;not null" json:"inviteCode"`
	StartedAt    *time.Time     `json:"startedAt,omitempty"`
	EndedAt      *time.Time     `json:"endedAt,omitempty"`
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
	Description     string         `gorm:"type:text" json:"description,omitempty"`
	Type            string         `gorm:"size:32;not null;default:completion" json:"type"`       // "completion", "speedrun", "survival"
	Difficulty      string         `gorm:"size:32;not null;default:medium" json:"difficulty"`     // "easy", "medium", "hard"
	Status          string         `gorm:"size:32;not null;default:active;index" json:"status"`   // "active", "closed", "expired"
	SaveFilePath    string         `gorm:"size:1024;not null" json:"-"`
	SaveFileSize    int64          `json:"saveFileSize"`
	ScreenshotPath  string         `gorm:"size:512" json:"-"`
	CoreName        string         `gorm:"size:128" json:"coreName,omitempty"`
	AttemptCount    int            `gorm:"default:0" json:"attemptCount"`
	CompletionCount int            `gorm:"default:0" json:"completionCount"`
	ExpiresAt       *time.Time     `json:"expiresAt,omitempty"`
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
	CompletedAt *time.Time     `json:"completedAt,omitempty"`
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
	LocalGameID       *uint          `json:"localGameId,omitempty"`
}

// Core represents a libretro core.
type Core struct {
	ID          uint           `gorm:"primarykey" json:"id"`
	CreatedAt   time.Time      `json:"createdAt"`
	UpdatedAt   time.Time      `json:"updatedAt"`
	DeletedAt   gorm.DeletedAt `gorm:"index" json:"-"`
	Name        string         `gorm:"uniqueIndex;size:128;not null" json:"name"`
	DisplayName string         `gorm:"size:255" json:"displayName"`
	Description string         `gorm:"type:text" json:"description,omitempty"`
	Version     string         `gorm:"size:64" json:"version,omitempty"`
	Platforms   string         `gorm:"size:255" json:"platforms"` // comma-separated: windows,linux,macos,android
	FilePath    string         `gorm:"size:1024" json:"-"`
	DownloadURL string         `gorm:"size:1024" json:"downloadUrl,omitempty"` // URL template for non-buildbot cores; {platform} is replaced by the player
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
	LastPlayedAt   *time.Time     `json:"lastPlayedAt,omitempty"`
	LastPlayedBy   *uint          `json:"lastPlayedBy,omitempty"`
	TotalPlayTime  int64          `gorm:"default:0" json:"totalPlayTime"` // seconds
	ScreenshotURL  string         `gorm:"size:512" json:"screenshotUrl,omitempty"`
	CoreName       string         `gorm:"size:128" json:"coreName,omitempty"`
	CheatsEnabled  bool           `gorm:"default:false" json:"cheatsEnabled"`
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
	ScreenshotURL string         `gorm:"size:512" json:"screenshotUrl,omitempty"`
	IsAuto        bool           `gorm:"default:false" json:"isAuto"`
	IsCurrent     bool           `gorm:"default:false" json:"isCurrent"`
	CoreName      string         `gorm:"size:128" json:"coreName,omitempty"`
	Notes         string         `gorm:"type:text" json:"notes,omitempty"`
	Slot          *int           `json:"slot,omitempty"`
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
	ID            uint      `gorm:"primarykey" json:"id"`
	GameID        uint      `gorm:"uniqueIndex;not null" json:"gameId"`
	SteamGridDBID int       `json:"steamGridDbId,omitempty"`
	HeroURL       string    `gorm:"size:1024" json:"heroUrl,omitempty"`
	GridURL       string    `gorm:"size:1024" json:"gridUrl,omitempty"`
	LogoURL       string    `gorm:"size:1024" json:"logoUrl,omitempty"`
	IconURL       string    `gorm:"size:1024" json:"iconUrl,omitempty"`
	CreatedAt     time.Time `json:"createdAt"`
	UpdatedAt     time.Time `json:"updatedAt"`
}

// Company represents an IGDB game development/publishing company with metadata.
type Company struct {
	ID            uint      `gorm:"primarykey" json:"id"`
	IGDBCompanyID int       `gorm:"uniqueIndex" json:"igdbCompanyId"`
	Name          string    `gorm:"index" json:"name"`
	Description   string    `gorm:"type:text" json:"description,omitempty"`
	LogoURL       string    `gorm:"size:512" json:"logoUrl,omitempty"`
	LogoImageID   string    `gorm:"size:128" json:"-"`
	Country       int       `json:"country,omitempty"`
	FoundedYear   int       `json:"foundedYear,omitempty"`
	WebsiteURL    string    `gorm:"size:512" json:"websiteUrl,omitempty"`
	WikipediaURL  string    `gorm:"size:512" json:"wikipediaUrl,omitempty"`
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
	Entries          []GameSeriesEntry `gorm:"foreignKey:SeriesID" json:"entries,omitempty"`
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
	Entries         []GameFranchiseEntry `gorm:"foreignKey:FranchiseGroupID" json:"entries,omitempty"`
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
	SourceID      string     `gorm:"size:128" json:"sourceId,omitempty"`
	LastAttemptAt *time.Time `json:"lastAttemptAt,omitempty"`
	ErrorMessage  string     `gorm:"size:512" json:"errorMessage,omitempty"`
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
	ConsoleID        *uint          `json:"consoleId,omitempty"`
	Console          Console        `gorm:"foreignKey:ConsoleID" json:"-"`
	PossibleConsoles string         `gorm:"size:512" json:"possibleConsoles,omitempty"` // JSON array of abbreviations
	Status           string         `gorm:"size:32;not null;default:pending_console" json:"status"`
	// Scrape results
	Title       string  `gorm:"size:255" json:"title,omitempty"`
	CoverURL    string  `gorm:"size:512" json:"coverUrl,omitempty"`
	Description       string  `gorm:"type:text" json:"description,omitempty"`
	IGDBCriticsRating float64 `gorm:"column:rating" json:"igdbCriticsRating,omitempty"`
	Developer         string  `gorm:"size:255" json:"developer,omitempty"`
	Publisher   string  `gorm:"size:255" json:"publisher,omitempty"`
	Genre       string  `gorm:"size:128" json:"genre,omitempty"`
	Players     int     `json:"players,omitempty"`
	ReleaseDate string  `gorm:"size:32" json:"releaseDate,omitempty"`
	// Verification
	VerificationStatus string `gorm:"size:32" json:"verificationStatus,omitempty"` // verified, unverified, not_applicable
	CRC32              string `gorm:"size:16" json:"crc32,omitempty"`
	CanonicalName      string `gorm:"size:512" json:"canonicalName,omitempty"`
	// Duplicate detection
	DuplicateOfGameID *uint `json:"duplicateOfGameId,omitempty"`
}
