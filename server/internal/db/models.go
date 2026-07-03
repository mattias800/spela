package db

import (
	"strings"
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
	ID                  uint           `gorm:"primarykey" json:"id"`
	CreatedAt           time.Time      `json:"createdAt"`
	UpdatedAt           time.Time      `json:"updatedAt"`
	DeletedAt           gorm.DeletedAt `gorm:"index" json:"-"`
	Username            string         `gorm:"uniqueIndex;size:64;not null" json:"username"`
	Email               string         `gorm:"uniqueIndex;size:255;not null" json:"email"`
	PasswordHash        string         `gorm:"not null" json:"-"`
	Role                UserRole       `gorm:"size:16;default:user" json:"role"`
	AvatarURL           string         `gorm:"size:512" json:"avatarUrl"`
	TokenVersion        int            `gorm:"default:0" json:"-"`
	Disabled            bool           `gorm:"default:false" json:"disabled"`
	PendingApproval     bool           `gorm:"default:false" json:"pendingApproval"`
	ShowPerfOverlay     bool           `gorm:"default:false" json:"showPerformanceOverlay"`
	AutoSaveEnabled     bool           `gorm:"default:true" json:"autoSaveEnabled"`
	AutoLoadSaveEnabled bool           `gorm:"default:true" json:"autoLoadSaveEnabled"`
	// CanImportGames grants a non-admin user the ability to import games from
	// connected federation servers into the shared library. Admins always can;
	// this flag opts a specific user in, granted by an admin. See #1350.
	CanImportGames bool `gorm:"default:false" json:"canImportGames"`
	// When true, the player silently re-downloads a cached core binary
	// whose sha256 no longer matches the server's current fingerprint.
	// When false, the player trusts its local cache even if the server
	// has a newer build — session pinning keeps long-running saves
	// loadable without a version mismatch. See #555 Phase 2.
	AutoUpdateCoresEnabled  bool   `gorm:"default:true" json:"autoUpdateCoresEnabled"`
	SelectedShader          string `gorm:"size:64;default:none" json:"selectedShader"`
	SelectedTheme           string `gorm:"size:64;default:default-dark" json:"selectedTheme"`
	SelectedKeyMapping      string `gorm:"size:64;default:arrows-left" json:"selectedKeyMapping"`
	CustomKeyMapping        string `gorm:"type:text" json:"customKeyMapping"` // JSON: {"0":"z","1":"x",...}
	DefaultSecondScreenPage string `gorm:"size:64;default:art" json:"defaultSecondScreenPage"`
	PreferredRegions        string `gorm:"size:255" json:"preferredRegions"` // comma-separated ordered list, e.g. "USA,Europe,World"
	// ProfileVisibility controls whether the user's public profile
	// exposes detailed activity (current game, recent games, top
	// played, play time). Issue #1121: previously every authenticated
	// user could scrape every other user's gaming habits in real time.
	// Values: "public" (default), "private". The "friends" tier is
	// reserved for a future friend graph.
	ProfileVisibility string `gorm:"size:16;default:public" json:"profileVisibility"`
}

// Block represents a one-way "I do not want to interact with this user"
// relationship (issue #1121). Filtered in both directions on profile,
// search, and invite endpoints. The unique constraint on the pair
// prevents duplicates without a separate idempotency key.
type Block struct {
	ID            uint           `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time      `json:"createdAt"`
	UpdatedAt     time.Time      `json:"updatedAt"`
	DeletedAt     gorm.DeletedAt `gorm:"index" json:"-"`
	UserID        uint           `gorm:"uniqueIndex:idx_block_user_blocked;not null" json:"userId"`
	User          User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	BlockedUserID uint           `gorm:"uniqueIndex:idx_block_user_blocked;not null" json:"blockedUserId"`
	BlockedUser   User           `gorm:"foreignKey:BlockedUserID;constraint:OnDelete:CASCADE" json:"-"`
}

// LoginAttempt tracks failed login attempts per username for account lockout.
type LoginAttempt struct {
	ID          uint   `gorm:"primarykey"`
	Username    string `gorm:"size:64;uniqueIndex"`
	FailedCount int    `gorm:"default:0"`
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

// SaveStatePolicy is the size tier of a console's save states. It
// drives per-console retention, slot count, and UX affordances —
// see #804 phase 3. Cores within a tier behave similarly enough that
// one policy is enough; the tiers are coarse on purpose so the
// player UI can switch on one value rather than per-console magic.
//
//	"small"  — < ~5 MB. Named manual saves, unlimited (or current quota).
//	"medium" — ~5–30 MB. Slot-based primary; named saves secondary.
//	"large"  — ~30 MB+. Slots only, compression mandatory, opt-out by default.
type SaveStatePolicy string

const (
	SaveStatePolicySmall  SaveStatePolicy = "small"
	SaveStatePolicyMedium SaveStatePolicy = "medium"
	SaveStatePolicyLarge  SaveStatePolicy = "large"
)

// Console represents a detected game console/platform.
type Console struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	// Name is a static display name owned by the console registry, derived
	// in AfterFind (#1443) — not stored on the row.
	Name string `gorm:"-" json:"name"`
	// Abbreviation is the canonical short identifier (e.g. "NES", "SNES",
	// "PS2"). Every seed function and backfill loop in database.go uses
	// `WHERE abbreviation = ?` as the lookup key, so the schema must
	// enforce uniqueness — otherwise a race or bug could insert two
	// rows for the same abbreviation and subsequent `First()` calls
	// would silently pick whichever SQLite returns first. See #970.
	Abbreviation   string `gorm:"uniqueIndex;size:16;not null" json:"abbreviation"`
	Extensions     string `gorm:"-" json:"extensions"` // registry-derived in AfterFind (#1513), not stored
	DefaultCore    string `gorm:"size:128" json:"defaultCore"`
	EmulatorJSCore string `gorm:"size:64" json:"emulatorJsCore"`
	FolderName     string `gorm:"-" json:"folderName"` // registry-derived in AfterFind (#1513), not stored
	// CoverAspect (box-art ratio), Generation and ColorTheme (card-gradient
	// colour) are static facts owned by the console registry, not stored:
	// AfterFind derives them on load. (LogoAspectRatio is likewise derived —
	// from the logo SVG's viewBox at response time, see consoleLogoAspect-
	// Ratio.) See #1443 / #1166.
	CoverAspect      string `gorm:"-" json:"coverAspect"`
	ColorTheme       string `gorm:"-" json:"colorTheme"`
	Generation       int    `gorm:"-" json:"generation"`
	SaveStateSupport bool   `gorm:"default:true" json:"saveStateSupport"`
	// Size tier driving retention/slot/UX behaviour for save states on
	// this console. See [SaveStatePolicy]. The column has NO default
	// at the schema level — empty is the "needs seeding" sentinel that
	// SeedConsoles uses to distinguish a fresh row from an admin
	// override (which it must preserve). The API response falls back
	// to "small" so clients still see a closed-set value. See #804
	// phase 3.
	SaveStatePolicy SaveStatePolicy `gorm:"type:varchar(16);not null;default:''" json:"saveStatePolicy"`
	Playable        bool            `gorm:"default:true" json:"playable"`
	Code            *string         `gorm:"uniqueIndex;size:32" json:"code"`
	HardwareMakerID *uint           `json:"hardwareMakerId"`
	HardwareMaker   *HardwareMaker  `gorm:"foreignKey:HardwareMakerID" json:"hardwareMaker"`
	MediaTypeID     *uint           `json:"mediaTypeId"`
	MediaType       *MediaType      `gorm:"foreignKey:MediaTypeID" json:"mediaType"`
	// ReleaseYear, UnitsSold, Summary and Tag are static catalog facts now
	// owned by the console code registry (see ConsoleSpec / #1443) and
	// derived into responses there — no longer stored on this row.
	Games     []Game `gorm:"foreignKey:ConsoleID" json:"games"`
	GameCount int    `gorm:"-" json:"gameCount"`
}

// AfterFind derives the registry-owned fields (Name, CoverAspect,
// Generation) whenever a Console is loaded — including as a preloaded
// association on a Game. These are not stored on the row (#1443); the
// registry is authoritative. Consoles absent from the registry fall back to
// the abbreviation (Name) / the historical default (CoverAspect) so the API
// always emits valid values; Generation stays zero.
func (c *Console) AfterFind(*gorm.DB) error {
	c.CoverAspect = ConsoleCoverAspect(c.Abbreviation)
	c.ColorTheme = ConsoleColorTheme(c.Abbreviation)
	c.Name = ConsoleName(c.Abbreviation)
	c.Extensions = ConsoleExtensions(c.Abbreviation)
	c.FolderName = ConsoleFolderName(c.Abbreviation)
	if spec, ok := ConsoleSpecByAbbreviation(strings.ToUpper(c.Abbreviation)); ok {
		c.Generation = spec.Generation
	}
	return nil
}

// Game represents a detected ROM/game file.
type Game struct {
	ID                   uint           `gorm:"primarykey" json:"id"`
	CreatedAt            time.Time      `json:"createdAt"`
	UpdatedAt            time.Time      `json:"updatedAt"`
	DeletedAt            gorm.DeletedAt `gorm:"index" json:"-"`
	ConsoleID            uint           `gorm:"index;not null;index:idx_console_group_key,priority:1;index:idx_console_is_primary,priority:1" json:"consoleId"`
	Console              Console        `gorm:"foreignKey:ConsoleID" json:"console"`
	Title                string         `gorm:"size:255;not null" json:"title"`
	FileName             string         `gorm:"size:512;not null" json:"fileName"`
	FilePath             string         `gorm:"uniqueIndex;size:1024;not null" json:"-"`
	FileSize             int64          `json:"fileSize"`
	DiscCount            int            `json:"discCount"` // 0 = single-disc legacy, 2+ = multi-disc
	Discs                []GameDisc     `gorm:"foreignKey:GameID" json:"discs"`
	Description          string         `gorm:"type:text" json:"description"`
	CoverURL             string         `gorm:"size:512" json:"coverUrl"`
	ScreenshotURL        string         `gorm:"size:512" json:"screenshotUrl"`
	Developer            string         `gorm:"size:255" json:"developer"`
	Publisher            string         `gorm:"size:255" json:"publisher"`
	ReleaseDate          string         `gorm:"size:32" json:"releaseDate"`
	Genre                string         `gorm:"size:512" json:"genre"`
	GameModes            string         `gorm:"size:255" json:"gameModes"`
	Storyline            string         `gorm:"type:text" json:"storyline"`
	TotalRating          float64        `json:"totalRating"`
	TotalRatingCount     int            `json:"totalRatingCount"`
	IGDBUserRating       float64        `json:"igdbUserRating"`
	IGDBUserRatingCount  int            `json:"igdbUserRatingCount"`
	TimeToBeatHastily    int            `json:"timeToBeatHastily"`
	TimeToBeatNormally   int            `json:"timeToBeatNormally"`
	TimeToBeatCompletely int            `json:"timeToBeatCompletely"`
	Players              int            `json:"players"`
	IGDBCriticsRating    float64        `gorm:"column:rating" json:"igdbCriticsRating"`
	CoreOverride         string         `gorm:"size:128" json:"coreOverride"`
	LibRetroCoverURL     string         `gorm:"size:512" json:"-"`
	IGDBCoverURL         string         `gorm:"size:512" json:"-"`
	CoverManuallySet     bool           `gorm:"default:false" json:"-"`
	ScrapeAttempts       int            `json:"scrapeAttempts"`
	ScraperID            string         `gorm:"size:128" json:"scraperId"`
	AchievementsWarning  string         `gorm:"size:512" json:"achievementsWarning"`
	VerificationStatus   string         `gorm:"size:32" json:"verificationStatus"`
	VerificationTag      string         `gorm:"size:128" json:"verificationTag"`
	Region               string         `gorm:"size:128" json:"region"`
	Revision             string         `gorm:"size:64" json:"revision"`
	Tags                 string         `gorm:"size:255" json:"tags"`
	IsPreRelease         bool           `gorm:"default:false;index:idx_game_is_pre_release" json:"isPreRelease"`
	GroupKey             string         `gorm:"size:255;index:idx_game_group_key;index:idx_console_group_key,priority:2" json:"groupKey"`
	IsPrimary            bool           `gorm:"default:false;index:idx_game_is_primary;index:idx_console_is_primary,priority:2" json:"isPrimary"`
	PrimaryGameID        *uint          `json:"primaryGameId"`
	ParentGameID         *uint          `gorm:"index:idx_game_parent" json:"parentGameId"` // links standalone ROM hacks to their base game
	PartyInfo            string         `gorm:"size:512" json:"partyInfo"`                 // Demo party and placement, e.g. "Assembly 1993, 1st place"
	CRC32                string         `gorm:"size:16" json:"-"`
	RAGameID             uint           `gorm:"index" json:"-"` // RetroAchievements game ID (cached from hash lookup)
	// RAHashChecked + RAGameID sentinel logic:
	//   RAHashChecked=false, RAGameID=0  → Not yet looked up. Compute ROM MD5 and query RA.
	//   RAHashChecked=true,  RAGameID=0  → Looked up, but RA doesn't have this game. Do NOT retry.
	//   RAHashChecked=true,  RAGameID>0  → Valid RA game ID cached.
	// RAHashChecked is ONLY set to true after a successful API response (even if RA returned no match).
	// Transient errors (network, 403) leave RAHashChecked=false so the next visit retries.
	RAHashChecked    bool                  `gorm:"default:false" json:"-"`
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
	User      User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	GameID    uint           `gorm:"uniqueIndex:idx_user_game;not null" json:"gameId"`
	Game      Game           `gorm:"foreignKey:GameID" json:"game"`
}

// PlayHistory records when a user plays a game. There must be at most
// one row per (user, game) — `mergeGameData` and the various
// "total play time" / "last played" aggregations all assume this. The
// composite uniqueIndex enforces it at the schema level so application
// bugs or races (concurrent session ends, retry-on-partial-failure)
// can't insert duplicates that silently distort aggregations. See #973.
type PlayHistory struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	UserID    uint           `gorm:"uniqueIndex:idx_user_game_play_history;not null" json:"userId"`
	User      User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	// Standalone index on GameID — the composite uniqueIndex above has
	// (UserID, GameID) so SQLite cannot use it for queries that filter
	// by GameID alone (GET /api/games/{id}/stats and the top-player JOIN
	// were full-scanning play_histories).
	GameID     uint      `gorm:"uniqueIndex:idx_user_game_play_history;not null;index:idx_play_history_game" json:"gameId"`
	Game       Game      `gorm:"foreignKey:GameID" json:"game"`
	LastPlayed time.Time `json:"lastPlayed"`
	PlayTime   int64     `json:"playTime"` // seconds
}

// PlayTimeReportReceipt records client-supplied play-time report IDs that have
// already been applied, so offline retry uploads do not double-count.
type PlayTimeReportReceipt struct {
	ID             uint      `gorm:"primarykey" json:"id"`
	CreatedAt      time.Time `json:"createdAt"`
	UserID         uint      `gorm:"uniqueIndex:idx_play_time_receipt_user_report;not null" json:"userId"`
	User           User      `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	ClientReportID string    `gorm:"uniqueIndex:idx_play_time_receipt_user_report;size:128;not null" json:"clientReportId"`
	GameID         uint      `gorm:"index;not null" json:"gameId"`
	Game           Game      `gorm:"foreignKey:GameID;constraint:OnDelete:CASCADE" json:"-"`
	PlayedAt       time.Time `json:"playedAt"`
	Seconds        int64     `json:"seconds"`
}

// DailyPlayActivity aggregates play time per user per day for heatmap display.
type DailyPlayActivity struct {
	ID       uint      `gorm:"primarykey" json:"id"`
	UserID   uint      `gorm:"uniqueIndex:idx_daily_play_user_date;not null" json:"userId"`
	User     User      `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	Date     time.Time `gorm:"uniqueIndex:idx_daily_play_user_date;not null;type:date" json:"date"`
	PlayTime int64     `json:"playTime"` // seconds added on this day
}

// RefreshToken stores issued refresh tokens.
type RefreshToken struct {
	ID          uint `gorm:"primarykey"`
	CreatedAt   time.Time
	DeletedAt   gorm.DeletedAt `gorm:"index"`
	UserID      uint           `gorm:"index;not null"`
	User        User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE"`
	Token       string         `gorm:"uniqueIndex;size:512;not null"`
	ExpiresAt   time.Time      `gorm:"not null;index"`
	TokenFamily string         `gorm:"size:64;index"` // groups related tokens for replay detection
	Consumed    bool           `gorm:"default:false"` // marked true on rotation instead of deleted
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
	// either an admin-triggered force-refresh or a background buildbot
	// poll. Metadata carries old_sha256 and new_sha256 so the audit trail
	// shows which version replaced which. See #555 Phase 2 and #1190.
	SystemEventCoreUpdated = "core_updated"
	// Emitted by the libretro buildbot poller when a (core, platform)
	// fetch fails — HTTP error, zip parse failure, hash mismatch, etc.
	// Lets admins notice when buildbot goes down or shifts URL layouts.
	// Metadata: CoreUpdateFailedMetadata. See #1190.
	SystemEventCoreUpdateFailed = "core_update_failed"
	// Emitted by the BIOS auto-downloader when an entry fails to fetch
	// or extract — HTTP error, non-200 status, MD5 mismatch, archive
	// extraction failure, filesystem error. Lets admins notice when
	// upstream sources go down or shift without tailing container
	// logs. See #918.
	SystemEventBIOSDownloadFailed = "bios_download_failed"
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
	SystemEventCoreUpdateFailed,
	SystemEventBIOSDownloadFailed,
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
	SystemEventCoreUpdateFailed:        CategoryOperational,
	SystemEventBIOSDownloadFailed:      CategoryOperational,
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
	// SET NULL not CASCADE: SystemEvent is the audit log. Deleting a
	// user must not erase the record of what they did. We keep the
	// event row and null the FK so nothing dangles.
	User        *User      `gorm:"foreignKey:UserID;constraint:OnDelete:SET NULL"`
	IP          string     `gorm:"size:64;index"`
	Path        string     `gorm:"size:256"`
	Metadata    string     `gorm:"type:text"`
	DismissedAt *time.Time `gorm:"index"`
}

// ServerSetting stores key-value server configuration.
type ServerSetting struct {
	Key   string `gorm:"primarykey;size:128" json:"key"`
	Value string `gorm:"type:text" json:"value"`
}

// --- Federation (epic #1343) ------------------------------------------------

// Peer pairing status values.
const (
	PeerStatusPending = "pending" // invite accepted locally, awaiting mutual confirmation
	PeerStatusActive  = "active"  // mutually confirmed; federation requests honored
)

// FederationPeer is a friend server we have paired with. We communicate ONLY
// with direct peers in this table; transitive reach is achieved by peers
// re-serving (relaying/aggregating) on our behalf. A peer is identified by its
// key fingerprint, not its address. See docs/federation-mesh-exploration.md.
type FederationPeer struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	// Fingerprint is the peer's stable anonymous origin ID (base32 of
	// SHA-256 of its public key).
	Fingerprint string `gorm:"uniqueIndex;size:64;not null" json:"fingerprint"`
	// PublicKey is the peer's Ed25519 public key, base64 (std) encoded.
	PublicKey string `gorm:"size:128;not null" json:"publicKey"`
	// Name is an operator-chosen label for the friend.
	Name string `gorm:"size:128" json:"name"`
	// BaseURL is the peer's reachable federation endpoint (direct friends only).
	BaseURL string `gorm:"size:512;not null" json:"baseUrl"`
	// Status is "pending" or "active".
	Status string `gorm:"size:16;not null;default:pending" json:"status"`
	// SharePolicy / ConsumePolicy are JSON maps of data-class -> bool: what we
	// expose to this peer, and what we accept from it (bidirectional per-class
	// consent). Empty/absent = deny.
	SharePolicy   string `gorm:"type:text" json:"sharePolicy"`
	ConsumePolicy string `gorm:"type:text" json:"consumePolicy"`

	// --- Observability: per-peer health (#1350) ---
	// LastContactAt is the last time we exchanged anything with the peer
	// (success or failure); LastSuccessAt only on success. LastError captures
	// the most recent failure reason for the admin health view.
	LastContactAt *time.Time `json:"lastContactAt"`
	LastSuccessAt *time.Time `json:"lastSuccessAt"`
	LastError     string     `gorm:"size:512" json:"lastError"`
	LastErrorAt   *time.Time `json:"lastErrorAt"`
	// Reachable is the latest known reachability (true after a successful
	// exchange, false after a failure).
	Reachable bool `gorm:"default:false" json:"reachable"`
}

// FederationInviteNonce records a nonce embedded in an invite this server
// issued, so the pairing callback can prove it holds a genuine invite from us.
type FederationInviteNonce struct {
	ID        uint      `gorm:"primarykey" json:"id"`
	CreatedAt time.Time `json:"createdAt"`
	Nonce     string    `gorm:"uniqueIndex;size:64;not null" json:"nonce"`
	ExpiresAt time.Time `gorm:"not null;index" json:"expiresAt"`
	Used      bool      `gorm:"default:false" json:"used"`
}

// Exchange direction and outcome values for FederationExchange (#1350).
const (
	ExchangeOutbound = "outbound" // we initiated the request to a peer
	ExchangeInbound  = "inbound"  // a peer initiated the request to us

	ExchangeOK       = "ok"       // completed successfully
	ExchangeError    = "error"    // failed (network, peer error, internal)
	ExchangeRejected = "rejected" // refused (bad signature, policy, expired nonce)
)

// FederationExchange is the per-interaction ledger powering admin visibility:
// one row per cross-server interaction (handshake, stats pull, relay, ...), with
// timing and outcome. High-volume by design — pruned on a retention window. The
// "what data is being fetched, from whom, and when" record. See #1350.
type FederationExchange struct {
	ID        uint      `gorm:"primarykey" json:"id"`
	CreatedAt time.Time `gorm:"index" json:"createdAt"`
	// RequestID correlates both ends of one logical operation (also emitted in
	// logs and propagated via the X-Spela-Request-Id header).
	RequestID       string    `gorm:"size:64;index" json:"requestId"`
	PeerFingerprint string    `gorm:"size:64;index" json:"peerFingerprint"`
	PeerName        string    `gorm:"size:128" json:"peerName"`
	Direction       string    `gorm:"size:16;index" json:"direction"`
	Operation       string    `gorm:"size:64;index" json:"operation"`
	DataClass       string    `gorm:"size:32" json:"dataClass"`
	MaxHops         int       `json:"maxHops"`
	Status          string    `gorm:"size:16;index" json:"status"`
	HTTPStatus      int       `json:"httpStatus"`
	ItemCount       int       `json:"itemCount"`
	Bytes           int64     `json:"bytes"`
	DurationMs      int64     `json:"durationMs"`
	StartedAt       time.Time `json:"startedAt"`
	FinishedAt      time.Time `json:"finishedAt"`
	Error           string    `gorm:"size:512" json:"error"`
}

// FederationStatSnapshot caches a stat datum pulled from a direct friend so this
// server can re-serve it transitively (Phase 2, #1347) without re-pulling on
// every request. SourcePeerFingerprint is the direct friend we got it from (used
// to replace that peer's rows on refresh); OriginFingerprint is where the datum
// actually originated, which may be several hops away. Hops is the distance from
// THIS server to the origin.
type FederationStatSnapshot struct {
	ID                    uint      `gorm:"primarykey" json:"id"`
	CreatedAt             time.Time `json:"createdAt"`
	SourcePeerFingerprint string    `gorm:"size:64;index" json:"sourcePeerFingerprint"`
	OriginFingerprint     string    `gorm:"size:64;index" json:"originFingerprint"`
	Hops                  int       `json:"hops"`
	Metric                string    `gorm:"size:32;index" json:"metric"`
	Key                   string    `gorm:"size:255" json:"key"`
	Label                 string    `gorm:"size:255" json:"label"`
	PlayTimeSeconds       int64     `json:"playTimeSeconds"`
	Players               int64     `json:"players"`
	FetchedAt             time.Time `json:"fetchedAt"`
}

// FederationCatalogSnapshot caches a "game available on some server" record
// pulled from a direct friend, for transitive catalog discovery (Phase 3,
// #1348). Same source/origin/hops semantics as FederationStatSnapshot. Key is
// the cross-server game id (IGDB scraper id / CRC32).
type FederationCatalogSnapshot struct {
	ID                    uint      `gorm:"primarykey" json:"id"`
	CreatedAt             time.Time `json:"createdAt"`
	SourcePeerFingerprint string    `gorm:"size:64;index" json:"sourcePeerFingerprint"`
	OriginFingerprint     string    `gorm:"size:64;index" json:"originFingerprint"`
	Hops                  int       `json:"hops"`
	Key                   string    `gorm:"size:255;index" json:"key"`
	Title                 string    `gorm:"size:255" json:"title"`
	Console               string    `gorm:"size:32" json:"console"`
	FetchedAt             time.Time `json:"fetchedAt"`
}

// ConsoleSaveStateChoice is the user's per-console save-state opt-out
// state. Drives whether the in-game overlay shows the save/load
// buttons or grays them out, and whether the first-launch prompt
// fires for a large-tier console. See #804 phase 4.
//
//	"enabled"  — save states allowed for this console.
//	"disabled" — save states hidden in the overlay; a tooltip points
//	             back to Settings.
//	"ask-once" — fire the first-launch prompt again on the next start.
//	             The default for large-tier consoles when no row exists.
type ConsoleSaveStateChoice string

const (
	ConsoleSaveStateChoiceEnabled  ConsoleSaveStateChoice = "enabled"
	ConsoleSaveStateChoiceDisabled ConsoleSaveStateChoice = "disabled"
	ConsoleSaveStateChoiceAskOnce  ConsoleSaveStateChoice = "ask-once"
)

// ConsoleSaveStatePolicy stores a user's per-console save-state opt-out
// override. Mirrors the shape of [ConsoleShaderPreference]: a row only
// exists once the user has made a deliberate choice. The absence of a
// row resolves to a tier-driven default on the client (small/medium =
// enabled, large = ask-once). See #804 phase 4.
type ConsoleSaveStatePolicy struct {
	ID        uint                   `gorm:"primarykey" json:"id"`
	CreatedAt time.Time              `json:"createdAt"`
	UpdatedAt time.Time              `json:"updatedAt"`
	DeletedAt gorm.DeletedAt         `gorm:"index" json:"-"`
	UserID    uint                   `gorm:"uniqueIndex:idx_user_console_savestate;not null" json:"userId"`
	User      User                   `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	ConsoleID uint                   `gorm:"uniqueIndex:idx_user_console_savestate;not null" json:"consoleId"`
	Choice    ConsoleSaveStateChoice `gorm:"type:varchar(16);not null" json:"choice"`
}

// GameSaveStatePolicy stores a user's per-game save-state opt-out
// override that takes precedence over the per-console choice
// (ConsoleSaveStatePolicy). The "save states on for Mario Sunshine,
// off for Metroid Prime" case from #804 phase 4b spec point (c).
//
// A row only exists once the user has made a deliberate per-game
// choice. The absence of a row means "use the per-console override
// (if any), otherwise the tier-driven default" — same precedence
// as the existing resolver.
type GameSaveStatePolicy struct {
	ID        uint                   `gorm:"primarykey" json:"id"`
	CreatedAt time.Time              `json:"createdAt"`
	UpdatedAt time.Time              `json:"updatedAt"`
	DeletedAt gorm.DeletedAt         `gorm:"index" json:"-"`
	UserID    uint                   `gorm:"uniqueIndex:idx_user_game_savestate;not null" json:"userId"`
	User      User                   `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	GameID    uint                   `gorm:"uniqueIndex:idx_user_game_savestate;not null" json:"gameId"`
	Choice    ConsoleSaveStateChoice `gorm:"type:varchar(16);not null" json:"choice"`
}

// ConsoleShaderPreference stores a user's per-console shader override.
type ConsoleShaderPreference struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	UserID    uint           `gorm:"uniqueIndex:idx_user_console_shader;not null" json:"userId"`
	User      User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	ConsoleID uint           `gorm:"uniqueIndex:idx_user_console_shader;not null" json:"consoleId"`
	Shader    string         `gorm:"size:64;not null" json:"shader"`
}

// ConsoleRenderScalePreference stores a user's per-console internal
// render-scale override. A missing row means native/core default.
type ConsoleRenderScalePreference struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	UserID    uint           `gorm:"uniqueIndex:idx_user_console_render_scale;not null" json:"userId"`
	User      User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	ConsoleID uint           `gorm:"uniqueIndex:idx_user_console_render_scale;not null" json:"consoleId"`
	Scale     string         `gorm:"size:16;not null" json:"scale"`
}

// ConsoleKeyMappingPreference stores a user's per-console key mapping override.
type ConsoleKeyMappingPreference struct {
	ID              uint           `gorm:"primarykey" json:"id"`
	CreatedAt       time.Time      `json:"createdAt"`
	UpdatedAt       time.Time      `json:"updatedAt"`
	DeletedAt       gorm.DeletedAt `gorm:"index" json:"-"`
	UserID          uint           `gorm:"uniqueIndex:idx_user_console_keymapping;not null" json:"userId"`
	User            User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	ConsoleID       uint           `gorm:"uniqueIndex:idx_user_console_keymapping;not null" json:"consoleId"`
	SelectedMapping string         `gorm:"size:64;not null" json:"selectedMapping"`
	CustomMapping   string         `gorm:"type:text" json:"customMapping"` // JSON map[string]string (retroId -> keycode)
	// PositionMappings is the brand-independent positional gamepad mapping layer
	// (#1334): JSON map[string]int of GamepadPosition name -> libretro RetroPad id.
	// Platform-independent, so it syncs across devices without keycode validation.
	PositionMappings string `gorm:"type:text" json:"positionMappings"` // JSON map[string]int
}

// Device represents a registered user device.
type Device struct {
	ID         uint           `gorm:"primarykey" json:"id"`
	CreatedAt  time.Time      `json:"createdAt"`
	UpdatedAt  time.Time      `json:"updatedAt"`
	DeletedAt  gorm.DeletedAt `gorm:"index" json:"-"`
	UserID     uint           `gorm:"index;not null" json:"userId"`
	User       User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
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
	User            User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
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
	User             User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
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
	User          User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
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
	User      User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
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
	User      User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	EventType string         `gorm:"size:64;not null;index" json:"eventType"` // started_playing, favorited_game, rated_game, shared_save
	// GameID is nullable: events like 'created_collection' don't have a
	// game. Pre-#971 the column was non-null uint with the zero value 0
	// stored, but with FK enforcement enabled the FK to games(id=0)
	// fails. SET NULL on Game so deleting a game preserves the activity
	// row but disconnects the FK.
	GameID   *uint  `gorm:"index" json:"gameId"`
	Game     Game   `gorm:"foreignKey:GameID;constraint:OnDelete:SET NULL" json:"-"`
	Metadata string `gorm:"type:text" json:"metadata"` // JSON
}

// GameCollection represents a user-created collection of games.
type GameCollection struct {
	ID          uint             `gorm:"primarykey" json:"id"`
	CreatedAt   time.Time        `json:"createdAt"`
	UpdatedAt   time.Time        `json:"updatedAt"`
	DeletedAt   gorm.DeletedAt   `gorm:"index" json:"-"`
	UserID      uint             `gorm:"index;not null" json:"userId"`
	User        User             `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	Name        string           `gorm:"size:255;not null" json:"name"`
	Description string           `gorm:"type:text" json:"description"`
	IsPublic    bool             `gorm:"default:false" json:"isPublic"`
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
	User      User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	GameID    uint           `gorm:"uniqueIndex:idx_play_later_user_game;not null" json:"gameId"`
	Game      Game           `gorm:"foreignKey:GameID" json:"game"`
	Position  int            `gorm:"not null;default:0" json:"position"`
}

// SharedSession represents a shared play session where friends take turns playing a game.
type SharedSession struct {
	ID           uint                  `gorm:"primarykey" json:"id"`
	CreatedAt    time.Time             `json:"createdAt"`
	UpdatedAt    time.Time             `json:"updatedAt"`
	DeletedAt    gorm.DeletedAt        `gorm:"index" json:"-"`
	OwnerID      uint                  `gorm:"index;not null" json:"ownerId"`
	Owner        User                  `gorm:"foreignKey:OwnerID;constraint:OnDelete:CASCADE" json:"-"`
	GameID       uint                  `gorm:"index;not null" json:"gameId"`
	Game         Game                  `gorm:"foreignKey:GameID" json:"-"`
	Name         string                `gorm:"size:255;not null" json:"name"`
	Status       string                `gorm:"size:32;default:active;not null" json:"status"` // "active", "completed", "archived"
	ActiveUserID *uint                 `json:"activeUserId"`
	TurnToken    string                `gorm:"size:64" json:"-"`
	TurnTakenAt  *time.Time            `json:"turnTakenAt"`
	CoreName     string                `gorm:"size:128" json:"coreName"`
	SessionID    *uint                 `gorm:"index" json:"sessionId"`
	Session      *GameSession          `json:"-"`
	Members      []SharedSessionMember `gorm:"foreignKey:SharedSessionID" json:"members"`
}

// SharedSessionMember represents a user's membership in a shared session.
type SharedSessionMember struct {
	ID              uint           `gorm:"primarykey" json:"id"`
	CreatedAt       time.Time      `json:"createdAt"`
	DeletedAt       gorm.DeletedAt `gorm:"index" json:"-"`
	SharedSessionID uint           `gorm:"uniqueIndex:idx_shared_session_user;not null" json:"sharedSessionId"`
	SharedSession   SharedSession  `gorm:"foreignKey:SharedSessionID" json:"-"`
	UserID          uint           `gorm:"uniqueIndex:idx_shared_session_user;not null" json:"userId"`
	User            User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
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
	Inviter         User           `gorm:"foreignKey:InviterID;constraint:OnDelete:CASCADE" json:"-"`
	InviteeID       uint           `gorm:"uniqueIndex:idx_shared_session_invitee;not null" json:"inviteeId"`
	Invitee         User           `gorm:"foreignKey:InviteeID;constraint:OnDelete:CASCADE" json:"-"`
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
	User            User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
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
	HostUser     User           `gorm:"foreignKey:HostUserID;constraint:OnDelete:CASCADE" json:"-"`
	ClientUserID *uint          `gorm:"index" json:"clientUserId"`
	ClientUser   User           `gorm:"foreignKey:ClientUserID;constraint:OnDelete:SET NULL" json:"-"`
	GameID       uint           `gorm:"index;not null" json:"gameId"`
	Game         Game           `gorm:"foreignKey:GameID" json:"-"`
	Status       string         `gorm:"size:32;default:waiting;not null" json:"status"` // "waiting", "in_progress", "ended"
	EndReason    string         `gorm:"size:32" json:"endReason"`                       // "host_left", "client_left", "timeout", "completed"
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
	Type            string         `gorm:"size:32;not null;default:completion" json:"type"`     // "completion", "speedrun", "survival"
	Difficulty      string         `gorm:"size:32;not null;default:medium" json:"difficulty"`   // "easy", "medium", "hard"
	Status          string         `gorm:"size:32;not null;default:active;index" json:"status"` // "active", "closed", "expired"
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
	User        User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
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
	User          User   `json:"-" gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE"`
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
	ID                uint           `gorm:"primarykey" json:"id"`
	CreatedAt         time.Time      `json:"createdAt"`
	UpdatedAt         time.Time      `json:"updatedAt"`
	DeletedAt         gorm.DeletedAt `gorm:"index" json:"-"`
	GameID            uint           `gorm:"index;not null" json:"gameId"`
	Game              Game           `gorm:"foreignKey:GameID" json:"-"`
	IGDBGameID        int            `gorm:"not null" json:"igdbGameId"`
	Name              string         `gorm:"size:255;not null" json:"name"`
	CoverImageID      string         `gorm:"size:128" json:"coverImageId"`
	CoverLocalPath    string         `gorm:"size:512" json:"-"`
	IGDBCriticsRating float64        `gorm:"column:rating" json:"igdbCriticsRating"`
	LocalGameID       *uint          `json:"localGameId"`
	// Platforms is a comma-separated list of IGDB platform IDs the
	// similar game is released on (per IGDB's `platforms` field).
	// Used by GET /api/games/{id}/similar to filter the cached
	// suggestions to platforms within ± 1 console generation of the
	// source game — IGDB's similar-games picks ignore platform and
	// era, so a 1993 NES game ends up "similar to" 2019 PC shooters
	// otherwise. Legacy rows (cached before this column existed)
	// have an empty string; they fall back to local-library matching
	// for the generation check and refresh via the 7-day cache TTL.
	Platforms string `gorm:"size:255" json:"-"`
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
	//
	// These fields are platform-ambiguous — they describe whichever binary
	// happens to live at FilePath on this server. For multi-platform
	// fingerprinting (used by the buildbot poller and the per-platform
	// staleness check on the player), see CorePlatformBinary. The manifest
	// endpoint falls back to these when no per-platform row exists, so
	// existing admin upload flows keep working without changes.
	Sha256    string     `gorm:"size:64" json:"sha256"`      // hex sha256 of the cached binary
	SizeBytes int64      `json:"sizeBytes"`                  // byte length of the cached binary
	FetchedAt *time.Time `json:"fetchedAt"`                  // when the binary was last downloaded
	SourceURL string     `gorm:"size:1024" json:"sourceUrl"` // URL we pulled the binary from
}

// CorePlatformBinary is the per-(core, platform) fingerprint that the
// libretro buildbot poller maintains. See #1190.
//
// `Core.Sha256` and friends describe ONE binary — whatever lives at
// `Core.FilePath` on this server. That works for admin-uploaded pinned
// cores but breaks for buildbot-default cores where each platform has
// its own distinct binary on disk; a single Sha256 means the last
// platform polled wins and every other platform's player thrashes
// (sha mismatch → redownload → next poll cycle's sha mismatch → ...).
//
// Each row pins one (core, platform-arch) tuple: the file the poller
// stored to disk, its sha256, its size, and when it was fetched.
// PlatformArch follows the player's `${currentPlatform()}-${currentArch()}`
// convention — e.g. `linux-x86_64`, `macos-arm64`, `android-arm64-v8a`.
type CorePlatformBinary struct {
	ID           uint           `gorm:"primarykey" json:"id"`
	CreatedAt    time.Time      `json:"createdAt"`
	UpdatedAt    time.Time      `json:"updatedAt"`
	DeletedAt    gorm.DeletedAt `gorm:"index" json:"-"`
	CoreID       uint           `gorm:"uniqueIndex:idx_core_platform;not null" json:"coreId"`
	PlatformArch string         `gorm:"uniqueIndex:idx_core_platform;size:64;not null" json:"platformArch"`
	FilePath     string         `gorm:"size:1024" json:"-"`
	Sha256       string         `gorm:"size:64" json:"sha256"`
	SizeBytes    int64          `json:"sizeBytes"`
	FetchedAt    *time.Time     `json:"fetchedAt"`
	SourceURL    string         `gorm:"size:1024" json:"sourceUrl"`
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
	ID            uint           `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time      `json:"createdAt"`
	UpdatedAt     time.Time      `json:"updatedAt"`
	DeletedAt     gorm.DeletedAt `gorm:"index" json:"-"`
	OwnerID       uint           `gorm:"index;not null" json:"ownerId"`
	Owner         User           `gorm:"foreignKey:OwnerID" json:"-"`
	GameID        uint           `gorm:"index;not null" json:"gameId"`
	Game          Game           `gorm:"foreignKey:GameID" json:"-"`
	Name          string         `gorm:"size:255;not null" json:"name"`
	LastPlayedAt  *time.Time     `json:"lastPlayedAt"`
	LastPlayedBy  *uint          `json:"lastPlayedBy"`
	TotalPlayTime int64          `gorm:"default:0" json:"totalPlayTime"` // seconds
	ScreenshotURL string         `gorm:"size:512" json:"screenshotUrl"`
	CoreName      string         `gorm:"size:128" json:"coreName"`
	CheatsEnabled bool           `gorm:"default:false" json:"cheatsEnabled"`
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
	User          User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
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
	CoreSha256 string `gorm:"size:64" json:"coreSha256"`
	Notes      string `gorm:"type:text" json:"notes"`
	Slot       *int   `json:"slot"`
	// Compression algorithm applied to the bytes at FilePath. Empty
	// string = uncompressed (the only case for pre-#804 saves). Known
	// values: "" | "gzip". Players that don't recognise a value should
	// reject the save with a "newer client required" error rather than
	// load garbage. See #804 phase 2.
	Compression string `gorm:"size:16" json:"compression"`
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

// SessionSaveDirBundle is a tarball of the libretro save_dir contents for a
// session. It carries on-disk save data that the core writes directly (e.g.
// ScummVM's per-game save files, DOSBox config tweaks), as a counterpart to
// SessionSaveData (SRAM) and SessionSaveState (libretro memory snapshots).
//
// One row per session — full atomic replace on each upload, no per-file
// addressing. The tarball has a 256 MB upload cap (same as save states), and
// is downloaded + extracted into the player's per-session save_dir before
// loadCore on every launch. See #864.
type SessionSaveDirBundle struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
	SessionID uint           `gorm:"uniqueIndex;not null" json:"sessionId"`
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
	User      User           `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
	Name      string         `gorm:"size:255;not null" json:"name"`
	Filters   string         `gorm:"type:text;not null" json:"filters"` // JSON-encoded filter params
}

// UserAchievementShowcase stores a user's pinned achievement for their profile.
type UserAchievementShowcase struct {
	ID              uint      `gorm:"primarykey" json:"id"`
	UserID          uint      `gorm:"uniqueIndex:idx_user_achievement_showcase;not null" json:"userId"`
	User            User      `gorm:"foreignKey:UserID;constraint:OnDelete:CASCADE" json:"-"`
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
	Title             string  `gorm:"size:255" json:"title"`
	CoverURL          string  `gorm:"size:512" json:"coverUrl"`
	Description       string  `gorm:"type:text" json:"description"`
	IGDBCriticsRating float64 `gorm:"column:rating" json:"igdbCriticsRating"`
	Developer         string  `gorm:"size:255" json:"developer"`
	Publisher         string  `gorm:"size:255" json:"publisher"`
	Genre             string  `gorm:"size:128" json:"genre"`
	Players           int     `json:"players"`
	ReleaseDate       string  `gorm:"size:32" json:"releaseDate"`
	// Verification
	VerificationStatus string `gorm:"size:32" json:"verificationStatus"` // verified, unverified, not_applicable
	CRC32              string `gorm:"size:16" json:"crc32"`
	CanonicalName      string `gorm:"size:512" json:"canonicalName"`
	// Duplicate detection
	DuplicateOfGameID *uint `json:"duplicateOfGameId"`
}
