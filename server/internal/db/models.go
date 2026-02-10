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
}

// Console represents a detected game console/platform.
type Console struct {
	ID            uint           `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time      `json:"createdAt"`
	UpdatedAt     time.Time      `json:"updatedAt"`
	DeletedAt     gorm.DeletedAt `gorm:"index" json:"-"`
	Name          string         `gorm:"uniqueIndex;size:128;not null" json:"name"`
	Abbreviation  string         `gorm:"size:16;not null" json:"abbreviation"`
	Extensions    string         `gorm:"size:255;not null" json:"extensions"` // comma-separated
	DefaultCore   string         `gorm:"size:128" json:"defaultCore"`
	CoverAspect   string         `gorm:"size:16;default:3:4" json:"coverAspect"`
	ColorTheme    string         `gorm:"size:7;default:#6366f1" json:"colorTheme"`
	Games         []Game         `gorm:"foreignKey:ConsoleID" json:"games,omitempty"`
	GameCount     int            `gorm:"-" json:"gameCount,omitempty"`
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
	Description   string         `gorm:"type:text" json:"description,omitempty"`
	CoverURL      string         `gorm:"size:512" json:"coverUrl,omitempty"`
	ScreenshotURL string         `gorm:"size:512" json:"screenshotUrl,omitempty"`
	Developer     string         `gorm:"size:255" json:"developer,omitempty"`
	Publisher     string         `gorm:"size:255" json:"publisher,omitempty"`
	ReleaseDate   string         `gorm:"size:32" json:"releaseDate,omitempty"`
	Genre         string         `gorm:"size:128" json:"genre,omitempty"`
	Players       int            `json:"players,omitempty"`
	Rating        float64        `json:"rating,omitempty"`
	CoreOverride   string         `gorm:"size:128" json:"coreOverride,omitempty"`
	ScrapeAttempts int            `json:"scrapeAttempts"`
	ScraperID      string         `gorm:"size:128" json:"scraperId,omitempty"`
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
