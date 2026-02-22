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
	Disabled            bool           `gorm:"default:false" json:"disabled"`
	ShowPerfOverlay     bool           `gorm:"default:false" json:"showPerformanceOverlay"`
	AutoSaveEnabled     bool           `gorm:"default:true" json:"autoSaveEnabled"`
	AutoLoadSaveEnabled bool           `gorm:"default:true" json:"autoLoadSaveEnabled"`
	SelectedShader      string         `gorm:"size:64;default:none" json:"selectedShader"`
	SelectedTheme       string         `gorm:"size:64;default:default-dark" json:"selectedTheme"`
	SelectedKeyMapping  string         `gorm:"size:64;default:arrows-left" json:"selectedKeyMapping"`
	CustomKeyMapping    string         `gorm:"type:text" json:"customKeyMapping,omitempty"` // JSON: {"0":"z","1":"x",...}
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
	SaveStateSupport bool           `gorm:"default:true" json:"saveStateSupport"`
	Games            []Game         `gorm:"foreignKey:ConsoleID" json:"games,omitempty"`
	GameCount      int            `gorm:"-" json:"gameCount,omitempty"`
}

// Game represents a detected ROM/game file.
type Game struct {
	ID            uint           `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time      `json:"createdAt"`
	UpdatedAt     time.Time      `json:"updatedAt"`
	DeletedAt     gorm.DeletedAt `gorm:"index" json:"-"`
	ConsoleID     uint           `gorm:"index;not null" json:"consoleId"`
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
	Genre         string         `gorm:"size:128" json:"genre,omitempty"`
	Players       int            `json:"players,omitempty"`
	Rating        float64        `json:"rating,omitempty"`
	CoreOverride        string         `gorm:"size:128" json:"coreOverride,omitempty"`
	ScrapeAttempts      int            `json:"scrapeAttempts"`
	ScraperID           string         `gorm:"size:128" json:"scraperId,omitempty"`
	AchievementsWarning string         `gorm:"size:512" json:"achievementsWarning,omitempty"`
	VerificationStatus  string         `gorm:"size:32" json:"verificationStatus,omitempty"`
	VerificationTag     string         `gorm:"size:128" json:"verificationTag,omitempty"`
	CRC32               string         `gorm:"size:16" json:"-"`
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

// SaveState represents a user's save state for a game.
type SaveState struct {
	ID            uint           `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time      `json:"createdAt"`
	UpdatedAt     time.Time      `json:"updatedAt"`
	DeletedAt     gorm.DeletedAt `gorm:"index" json:"-"`
	UserID        uint           `gorm:"index;not null" json:"userId"`
	User          User           `gorm:"foreignKey:UserID" json:"-"`
	GameID        uint           `gorm:"index;not null" json:"gameId"`
	Game          Game           `gorm:"foreignKey:GameID" json:"-"`
	Name          string         `gorm:"size:255;not null" json:"name"`
	FilePath      string         `gorm:"size:1024;not null" json:"-"`
	FileSize      int64          `json:"fileSize"`
	ScreenshotURL string         `gorm:"size:512" json:"screenshotUrl,omitempty"`
	IsAuto        bool           `gorm:"default:false" json:"isAuto"`
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

// RefreshToken stores issued refresh tokens.
type RefreshToken struct {
	ID        uint           `gorm:"primarykey"`
	CreatedAt time.Time
	DeletedAt gorm.DeletedAt `gorm:"index"`
	UserID    uint           `gorm:"index;not null"`
	User      User           `gorm:"foreignKey:UserID"`
	Token     string         `gorm:"uniqueIndex;size:512;not null"`
	ExpiresAt time.Time      `gorm:"not null"`
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

// Relay represents a shared play session where friends take turns playing a game.
type Relay struct {
	ID           uint           `gorm:"primarykey" json:"id"`
	CreatedAt    time.Time      `json:"createdAt"`
	UpdatedAt    time.Time      `json:"updatedAt"`
	DeletedAt    gorm.DeletedAt `gorm:"index" json:"-"`
	OwnerID      uint           `gorm:"index;not null" json:"ownerId"`
	Owner        User           `gorm:"foreignKey:OwnerID" json:"-"`
	GameID       uint           `gorm:"index;not null" json:"gameId"`
	Game         Game           `gorm:"foreignKey:GameID" json:"-"`
	Name         string         `gorm:"size:255;not null" json:"name"`
	Status       string         `gorm:"size:32;default:active;not null" json:"status"` // "active", "completed", "archived"
	ActiveUserID *uint          `json:"activeUserId,omitempty"`
	TurnToken    string         `gorm:"size:64" json:"-"`
	TurnTakenAt  *time.Time     `json:"turnTakenAt,omitempty"`
	Members      []RelayMember  `gorm:"foreignKey:RelayID" json:"members,omitempty"`
}

// RelayMember represents a user's membership in a relay.
type RelayMember struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	RelayID   uint           `gorm:"uniqueIndex:idx_relay_user;not null" json:"relayId"`
	Relay     Relay          `gorm:"foreignKey:RelayID" json:"-"`
	UserID    uint           `gorm:"uniqueIndex:idx_relay_user;not null" json:"userId"`
	User      User           `gorm:"foreignKey:UserID" json:"-"`
	Role      string         `gorm:"size:16;default:member;not null" json:"role"` // "owner", "member"
	JoinedAt  time.Time      `json:"joinedAt"`
}

// RelayInvite represents a pending invitation to join a relay.
type RelayInvite struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	RelayID   uint           `gorm:"uniqueIndex:idx_relay_invitee;not null" json:"relayId"`
	Relay     Relay          `gorm:"foreignKey:RelayID" json:"-"`
	InviterID uint           `gorm:"not null" json:"inviterId"`
	Inviter   User           `gorm:"foreignKey:InviterID" json:"-"`
	InviteeID uint           `gorm:"uniqueIndex:idx_relay_invitee;not null" json:"inviteeId"`
	Invitee   User           `gorm:"foreignKey:InviteeID" json:"-"`
	Status    string         `gorm:"size:32;default:pending;not null" json:"status"` // "pending", "accepted", "declined"
}

// RelaySave represents a save state within a relay.
type RelaySave struct {
	ID            uint           `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time      `json:"createdAt"`
	UpdatedAt     time.Time      `json:"updatedAt"`
	DeletedAt     gorm.DeletedAt `gorm:"index" json:"-"`
	RelayID       uint           `gorm:"index;not null" json:"relayId"`
	Relay         Relay          `gorm:"foreignKey:RelayID" json:"-"`
	UserID        uint           `gorm:"not null" json:"userId"`
	User          User           `gorm:"foreignKey:UserID" json:"-"`
	Name          string         `gorm:"size:255;not null" json:"name"`
	FilePath      string         `gorm:"size:1024;not null" json:"-"`
	FileSize      int64          `json:"fileSize"`
	ScreenshotURL string         `gorm:"size:512" json:"screenshotUrl,omitempty"`
	IsAuto        bool           `gorm:"default:false" json:"isAuto"`
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

// SaveData represents a user's SRAM/battery save data for a game.
type SaveData struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	UserID    uint           `gorm:"index;not null" json:"userId"`
	User      User           `gorm:"foreignKey:UserID" json:"-"`
	GameID    uint           `gorm:"index;not null" json:"gameId"`
	Game      Game           `gorm:"foreignKey:GameID" json:"-"`
	Name      string         `gorm:"size:255;not null" json:"name"`
	FilePath  string         `gorm:"size:1024;not null" json:"-"`
	FileSize  int64          `json:"fileSize"`
	IsActive  bool           `gorm:"default:false" json:"isActive"`
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
}
