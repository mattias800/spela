package db

import (
	"fmt"
	"log/slog"

	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// Initialize opens the SQLite database and runs auto-migrations.
func Initialize(dbPath string) (*gorm.DB, error) {
	db, err := gorm.Open(sqlite.Open(dbPath), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Warn),
	})
	if err != nil {
		return nil, fmt.Errorf("opening database: %w", err)
	}

	slog.Info("running database migrations")
	err = db.AutoMigrate(
		&User{},
		&Console{},
		&Game{},
		&SaveState{},
		&Favorite{},
		&PlayHistory{},
		&RefreshToken{},
		&ServerSetting{},
		&Core{},
		&ConsoleShaderPreference{},
		&ConsoleKeyMappingPreference{},
		&Device{},
		&DeviceShaderPreference{},
		&RetroAchievementCredential{},
		&GameAchievementCache{},
		&UserAchievementProgress{},
	)
	if err != nil {
		return nil, fmt.Errorf("running migrations: %w", err)
	}

	// Promote the first user to owner if no owner exists (handles upgrades).
	var ownerCount int64
	db.Model(&User{}).Where("role = ?", RoleOwner).Count(&ownerCount)
	if ownerCount == 0 {
		var firstUser User
		if err := db.Order("id ASC").First(&firstUser).Error; err == nil {
			if firstUser.Role == RoleAdmin {
				db.Model(&firstUser).Update("role", RoleOwner)
				slog.Info("promoted first user to owner", "username", firstUser.Username)
			}
		}
	}

	return db, nil
}

// SeedConsoles inserts the default console definitions if they don't exist.
// For existing consoles, it backfills the EmulatorJSCore field if empty.
func SeedConsoles(db *gorm.DB) error {
	consoles := []Console{
		{Name: "Nintendo Entertainment System", Abbreviation: "NES", Extensions: ".nes,.fds", DefaultCore: "nestopia", EmulatorJSCore: "nestopia", ColorTheme: "#e60012"},
		{Name: "Super Nintendo", Abbreviation: "SNES", Extensions: ".sfc,.smc", DefaultCore: "snes9x", EmulatorJSCore: "snes9x", ColorTheme: "#7b7db5"},
		{Name: "Game Boy", Abbreviation: "GB", Extensions: ".gb", DefaultCore: "gambatte", EmulatorJSCore: "gambatte", ColorTheme: "#8bac0f"},
		{Name: "Game Boy Color", Abbreviation: "GBC", Extensions: ".gbc", DefaultCore: "gambatte", EmulatorJSCore: "gambatte", ColorTheme: "#6638a8"},
		{Name: "Game Boy Advance", Abbreviation: "GBA", Extensions: ".gba", DefaultCore: "mgba", EmulatorJSCore: "mgba", ColorTheme: "#2e17a3"},
		{Name: "Nintendo 64", Abbreviation: "N64", Extensions: ".n64,.z64,.v64", DefaultCore: "mupen64plus_next", EmulatorJSCore: "mupen64plus_next", ColorTheme: "#009e60"},
		{Name: "Nintendo DS", Abbreviation: "NDS", Extensions: ".nds", DefaultCore: "desmume", EmulatorJSCore: "melonds", ColorTheme: "#b0b0b0"},
		{Name: "Sega Master System", Abbreviation: "SMS", Extensions: ".sms", DefaultCore: "genesis_plus_gx", EmulatorJSCore: "genesis_plus_gx", ColorTheme: "#0060a8"},
		{Name: "Sega Genesis", Abbreviation: "GEN", Extensions: ".md,.gen,.bin", DefaultCore: "genesis_plus_gx", EmulatorJSCore: "genesis_plus_gx", ColorTheme: "#171717"},
		{Name: "Sega Saturn", Abbreviation: "SAT", Extensions: ".iso,.bin,.cue", DefaultCore: "beetle_saturn", EmulatorJSCore: "yabause", ColorTheme: "#0a4da2"},
		{Name: "PlayStation", Abbreviation: "PSX", Extensions: ".bin,.cue,.iso,.pbp", DefaultCore: "beetle_psx_hw", EmulatorJSCore: "pcsx_rearmed", ColorTheme: "#003087"},
		{Name: "PlayStation Portable", Abbreviation: "PSP", Extensions: ".iso,.cso", DefaultCore: "ppsspp", EmulatorJSCore: "ppsspp", ColorTheme: "#000000"},
		{Name: "Neo Geo", Abbreviation: "NEOGEO", Extensions: ".zip", DefaultCore: "fbneo", EmulatorJSCore: "fbneo", ColorTheme: "#ffcc00"},
		{Name: "Arcade", Abbreviation: "ARCADE", Extensions: ".zip", DefaultCore: "mame2003_plus", EmulatorJSCore: "fbneo", ColorTheme: "#ff4444"},
		{Name: "TurboGrafx-16", Abbreviation: "PCE", Extensions: ".pce", DefaultCore: "beetle_pce", EmulatorJSCore: "mednafen_pce_fast", ColorTheme: "#ff6600"},
		{Name: "Atari 2600", Abbreviation: "A26", Extensions: ".a26,.bin", DefaultCore: "stella", EmulatorJSCore: "stella2014", ColorTheme: "#8b4513"},
	}

	for _, c := range consoles {
		var existing Console
		result := db.Where("abbreviation = ?", c.Abbreviation).First(&existing)
		if result.Error == gorm.ErrRecordNotFound {
			if err := db.Create(&c).Error; err != nil {
				return fmt.Errorf("seeding console %s: %w", c.Name, err)
			}
			slog.Info("seeded console", "name", c.Name)
		} else if existing.EmulatorJSCore == "" && c.EmulatorJSCore != "" {
			// Backfill EmulatorJSCore for existing consoles
			db.Model(&existing).Update("emulator_js_core", c.EmulatorJSCore)
			slog.Info("backfilled EmulatorJSCore", "name", existing.Name, "core", c.EmulatorJSCore)
		}
	}

	return nil
}
