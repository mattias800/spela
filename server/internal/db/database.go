package db

import (
	"fmt"
	"log/slog"
	"strings"

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
		&GameDisc{},
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
		&ActivityEvent{},
		&GameRating{},
		&SharedSaveState{},
		&GameCollection{},
		&CollectionItem{},
		&PlayLaterItem{},
		&Relay{},
		&RelayMember{},
		&RelayInvite{},
		&RelaySave{},
		&NetplaySession{},
		&Challenge{},
		&ChallengeAttempt{},
		&GameKeyMappingPreference{},
		&SaveData{},
		&GameScreenshot{},
		&StagedUpload{},
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
		{Name: "Nintendo Entertainment System", Abbreviation: "NES", Extensions: ".nes,.fds", DefaultCore: "nestopia", EmulatorJSCore: "nestopia", FolderName: "nes", ColorTheme: "#e60012", CoverAspect: "5:7", SaveStateSupport: true},
		{Name: "Super Nintendo", Abbreviation: "SNES", Extensions: ".sfc,.smc", DefaultCore: "snes9x", EmulatorJSCore: "snes9x", FolderName: "snes", ColorTheme: "#7b7db5", CoverAspect: "4:3", SaveStateSupport: true},
		{Name: "Game Boy", Abbreviation: "GB", Extensions: ".gb", DefaultCore: "gambatte", EmulatorJSCore: "gambatte", FolderName: "gb", ColorTheme: "#8bac0f", CoverAspect: "7:8", SaveStateSupport: true},
		{Name: "Game Boy Color", Abbreviation: "GBC", Extensions: ".gbc", DefaultCore: "gambatte", EmulatorJSCore: "gambatte", FolderName: "gbc", ColorTheme: "#6638a8", CoverAspect: "7:8", SaveStateSupport: true},
		{Name: "Game Boy Advance", Abbreviation: "GBA", Extensions: ".gba", DefaultCore: "mgba", EmulatorJSCore: "mgba", FolderName: "gba", ColorTheme: "#2e17a3", CoverAspect: "1:1", SaveStateSupport: true},
		{Name: "Nintendo 64", Abbreviation: "N64", Extensions: ".n64,.z64,.v64", DefaultCore: "mupen64plus_next", EmulatorJSCore: "mupen64plus_next", FolderName: "n64", ColorTheme: "#009e60", CoverAspect: "10:7", SaveStateSupport: true},
		{Name: "Nintendo DS", Abbreviation: "NDS", Extensions: ".nds", DefaultCore: "desmume", EmulatorJSCore: "melonds", FolderName: "nds", ColorTheme: "#b0b0b0", CoverAspect: "10:9", SaveStateSupport: true},
		{Name: "Sega Master System", Abbreviation: "SMS", Extensions: ".sms", DefaultCore: "genesis_plus_gx", EmulatorJSCore: "genesis_plus_gx", FolderName: "mastersystem", ColorTheme: "#0060a8", SaveStateSupport: true},
		{Name: "Sega Genesis", Abbreviation: "GEN", Extensions: ".md,.gen,.bin", DefaultCore: "genesis_plus_gx", EmulatorJSCore: "genesis_plus_gx", FolderName: "genesis", ColorTheme: "#171717", SaveStateSupport: true},
		{Name: "Sega Saturn", Abbreviation: "SAT", Extensions: ".iso,.bin,.cue,.m3u", DefaultCore: "beetle_saturn", EmulatorJSCore: "yabause", FolderName: "saturn", ColorTheme: "#0a4da2", SaveStateSupport: true},
		{Name: "PlayStation", Abbreviation: "PSX", Extensions: ".bin,.cue,.iso,.pbp,.m3u", DefaultCore: "beetle_psx_hw", EmulatorJSCore: "pcsx_rearmed", FolderName: "psx", ColorTheme: "#003087", CoverAspect: "1:1", SaveStateSupport: true},
		{Name: "PlayStation Portable", Abbreviation: "PSP", Extensions: ".iso,.cso,.chd", DefaultCore: "ppsspp", EmulatorJSCore: "ppsspp", FolderName: "psp", ColorTheme: "#000000", SaveStateSupport: true},
		{Name: "Neo Geo", Abbreviation: "NEOGEO", Extensions: ".zip", DefaultCore: "fbneo", EmulatorJSCore: "fbneo", FolderName: "neogeo", ColorTheme: "#ffcc00", SaveStateSupport: true},
		{Name: "Arcade", Abbreviation: "ARCADE", Extensions: ".zip", DefaultCore: "mame2003_plus", EmulatorJSCore: "fbneo", FolderName: "arcade", ColorTheme: "#ff4444", SaveStateSupport: true},
		{Name: "TurboGrafx-16", Abbreviation: "PCE", Extensions: ".pce", DefaultCore: "beetle_pce", EmulatorJSCore: "mednafen_pce_fast", FolderName: "tg16", ColorTheme: "#ff6600", SaveStateSupport: true},
		{Name: "Atari 2600", Abbreviation: "A26", Extensions: ".a26,.bin", DefaultCore: "stella", EmulatorJSCore: "stella2014", FolderName: "atari2600", ColorTheme: "#8b4513", SaveStateSupport: true},
		// New consoles
		{Name: "Game Gear", Abbreviation: "GG", Extensions: ".gg", DefaultCore: "genesis_plus_gx", EmulatorJSCore: "genesis_plus_gx", FolderName: "gamegear", ColorTheme: "#1a1a1a", CoverAspect: "1:1", SaveStateSupport: true},
		{Name: "Sega CD", Abbreviation: "SCD", Extensions: ".iso,.bin,.cue,.m3u", DefaultCore: "genesis_plus_gx", EmulatorJSCore: "genesis_plus_gx", FolderName: "segacd", ColorTheme: "#1a1a1a", SaveStateSupport: true},
		{Name: "Sega 32X", Abbreviation: "32X", Extensions: ".32x", DefaultCore: "picodrive", EmulatorJSCore: "picodrive", FolderName: "sega32x", ColorTheme: "#1a1a1a", SaveStateSupport: true},
		{Name: "Dreamcast", Abbreviation: "DC", Extensions: ".gdi,.cdi,.chd", DefaultCore: "flycast", EmulatorJSCore: "", FolderName: "dreamcast", ColorTheme: "#c0c0c0", SaveStateSupport: true},
		{Name: "Virtual Boy", Abbreviation: "VB", Extensions: ".vb,.vboy", DefaultCore: "beetle_vb", EmulatorJSCore: "beetle_vb", FolderName: "virtualboy", ColorTheme: "#ff0000", SaveStateSupport: true},
		{Name: "Nintendo 3DS", Abbreviation: "3DS", Extensions: ".3ds,.cia", DefaultCore: "citra", EmulatorJSCore: "", FolderName: "3ds", ColorTheme: "#ce181e", SaveStateSupport: true},
		{Name: "Atari 5200", Abbreviation: "A52", Extensions: ".a52,.bin", DefaultCore: "atari800", EmulatorJSCore: "atari800", FolderName: "atari5200", ColorTheme: "#8b4513", SaveStateSupport: true},
		{Name: "Atari 7800", Abbreviation: "A78", Extensions: ".a78,.bin", DefaultCore: "prosystem", EmulatorJSCore: "prosystem", FolderName: "atari7800", ColorTheme: "#8b4513", SaveStateSupport: true},
		{Name: "Atari Lynx", Abbreviation: "LYNX", Extensions: ".lnx", DefaultCore: "handy", EmulatorJSCore: "handy", FolderName: "atarilynx", ColorTheme: "#8b4513", CoverAspect: "1:1", SaveStateSupport: true},
		{Name: "Atari Jaguar", Abbreviation: "JAG", Extensions: ".j64,.jag", DefaultCore: "virtualjaguar", EmulatorJSCore: "", FolderName: "atarijaguar", ColorTheme: "#8b4513", SaveStateSupport: false},
		{Name: "Neo Geo Pocket", Abbreviation: "NGP", Extensions: ".ngp,.ngc", DefaultCore: "beetle_ngp", EmulatorJSCore: "mednafen_ngp", FolderName: "ngp", ColorTheme: "#1a75bc", CoverAspect: "1:1", SaveStateSupport: true},
		{Name: "WonderSwan", Abbreviation: "WS", Extensions: ".ws,.wsc", DefaultCore: "beetle_wswan", EmulatorJSCore: "mednafen_wswan", FolderName: "wonderswan", ColorTheme: "#4b0082", CoverAspect: "1:1", SaveStateSupport: true},
		{Name: "PC-FX", Abbreviation: "PCFX", Extensions: ".iso,.cue,.m3u", DefaultCore: "beetle_pcfx", EmulatorJSCore: "mednafen_pcfx", FolderName: "pcfx", ColorTheme: "#ff6600", SaveStateSupport: true},
		{Name: "ColecoVision", Abbreviation: "CV", Extensions: ".col,.rom", DefaultCore: "bluemsx", EmulatorJSCore: "", FolderName: "colecovision", ColorTheme: "#000000", SaveStateSupport: true},
		{Name: "Pokemon Mini", Abbreviation: "PKMN", Extensions: ".min", DefaultCore: "pokemini", EmulatorJSCore: "", FolderName: "pokemonmini", ColorTheme: "#ffcc00", CoverAspect: "1:1", SaveStateSupport: true},
		{Name: "PlayStation 2", Abbreviation: "PS2", Extensions: ".iso,.bin,.chd,.m3u", DefaultCore: "play", EmulatorJSCore: "", FolderName: "ps2", ColorTheme: "#003087", SaveStateSupport: true},
		{Name: "Commodore 64", Abbreviation: "C64", Extensions: ".d64,.t64,.prg,.crt", DefaultCore: "vice_x64", EmulatorJSCore: "vice_x64", FolderName: "c64", ColorTheme: "#6c5eb5", SaveStateSupport: true},
		{Name: "DOS", Abbreviation: "DOS", Extensions: ".exe,.com,.bat,.conf", DefaultCore: "dosbox_pure", EmulatorJSCore: "dosbox_pure", FolderName: "dos", ColorTheme: "#000000", SaveStateSupport: true},
		{Name: "Commodore Amiga", Abbreviation: "AMIGA", Extensions: ".adf,.hdf,.lha", DefaultCore: "puae", EmulatorJSCore: "", FolderName: "amiga", ColorTheme: "#6c5eb5", SaveStateSupport: true},
	}

	for _, c := range consoles {
		var existing Console
		result := db.Where("abbreviation = ?", c.Abbreviation).First(&existing)
		if result.Error == gorm.ErrRecordNotFound {
			if err := db.Create(&c).Error; err != nil {
				return fmt.Errorf("seeding console %s: %w", c.Name, err)
			}
			slog.Info("seeded console", "name", c.Name)
		} else {
			if existing.EmulatorJSCore == "" && c.EmulatorJSCore != "" {
				db.Model(&existing).Update("emulator_js_core", c.EmulatorJSCore)
				slog.Info("backfilled EmulatorJSCore", "name", existing.Name, "core", c.EmulatorJSCore)
			}
			if c.CoverAspect != "" && existing.CoverAspect != c.CoverAspect {
				db.Model(&existing).Update("cover_aspect", c.CoverAspect)
				slog.Info("backfilled CoverAspect", "name", existing.Name, "aspect", c.CoverAspect)
			}
			if existing.FolderName == "" && c.FolderName != "" {
				db.Model(&existing).Update("folder_name", c.FolderName)
				slog.Info("backfilled FolderName", "name", existing.Name, "folder", c.FolderName)
			}
			if !existing.SaveStateSupport && c.SaveStateSupport {
				db.Model(&existing).Update("save_state_support", true)
				slog.Info("backfilled SaveStateSupport", "name", existing.Name)
			}
			// Backfill .m3u extension for disc-based consoles
			if strings.Contains(c.Extensions, ".m3u") && !strings.Contains(existing.Extensions, ".m3u") {
				newExts := existing.Extensions + ",.m3u"
				db.Model(&existing).Update("extensions", newExts)
				slog.Info("backfilled .m3u extension", "name", existing.Name)
			}
			// Backfill .chd extension for disc-based consoles
			if strings.Contains(c.Extensions, ".chd") && !strings.Contains(existing.Extensions, ".chd") {
				newExts := existing.Extensions + ",.chd"
				db.Model(&existing).Update("extensions", newExts)
				slog.Info("backfilled .chd extension", "name", existing.Name)
			}
		}
	}

	return nil
}
