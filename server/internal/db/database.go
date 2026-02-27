package db

import (
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"strings"

	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// Initialize opens the SQLite database and runs auto-migrations.
// The database file is restricted to owner-only access (0600) to prevent
// other users on the system from reading tokens and password hashes.
func Initialize(dbPath string) (*gorm.DB, error) {
	db, err := gorm.Open(sqlite.Open(dbPath), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Warn),
	})
	if err != nil {
		return nil, fmt.Errorf("opening database: %w", err)
	}

	// Restrict database file permissions to owner-only (0600).
	if err := os.Chmod(dbPath, 0600); err != nil {
		slog.Warn("failed to set database file permissions", "path", dbPath, "error", err)
	}
	// SQLite also creates -journal and -wal files; restrict those too.
	for _, suffix := range []string{"-journal", "-wal", "-shm"} {
		sidePath := dbPath + suffix
		if _, statErr := os.Stat(sidePath); statErr == nil {
			if err := os.Chmod(sidePath, 0600); err != nil {
				slog.Warn("failed to set database sidecar permissions", "path", sidePath, "error", err)
			}
		}
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
		&TopRatedGame{},
		&SimilarGame{},
		&LoginAttempt{},
		&TokenBlacklist{},
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

// MigrateToRelativePaths converts absolute game file paths to relative paths.
// On startup, any Game or GameDisc record whose FilePath starts with "/" is
// converted by stripping the matching gameDirs prefix. If no prefix matches
// (e.g. SPELA_GAME_DIRS changed), it falls back to detecting a known console
// FolderName in the path segments.
func MigrateToRelativePaths(database *gorm.DB, gameDirs []string) error {
	// Load console folder names for fallback detection
	var consoles []Console
	if err := database.Select("folder_name").Where("folder_name != ''").Find(&consoles).Error; err != nil {
		return fmt.Errorf("loading console folder names: %w", err)
	}
	folderNames := make(map[string]bool, len(consoles))
	for _, c := range consoles {
		folderNames[c.FolderName] = true
	}

	// Migrate Game records: absolute paths AND stale relative paths (e.g. ../testdata/roms/nes/...)
	var games []Game
	if err := database.Where("file_path LIKE '/%' OR file_path LIKE '../%'").Find(&games).Error; err != nil {
		return fmt.Errorf("loading non-canonical-path games: %w", err)
	}
	if len(games) > 0 {
		slog.Info("migrating game paths to relative", "count", len(games))
	}

	for _, g := range games {
		relPath := toRelativePath(g.FilePath, gameDirs)
		// Fallback: if prefix stripping didn't work, detect console folder in path
		if relPath == g.FilePath {
			relPath = extractRelativeByConsoleFolders(g.FilePath, folderNames)
		}
		if relPath == g.FilePath {
			slog.Warn("could not convert path to canonical relative",
				"id", g.ID, "path", g.FilePath)
			continue
		}

		// Check for duplicates: another game already has this relative path
		var existing Game
		if err := database.Where("file_path = ? AND id != ?", relPath, g.ID).First(&existing).Error; err == nil {
			// Keep the lower ID, delete the higher
			victim := g
			if existing.ID > g.ID {
				victim = existing
			}
			slog.Info("removing duplicate game during path migration",
				"title", victim.Title, "id", victim.ID, "path", relPath)
			database.Unscoped().Where("game_id = ?", victim.ID).Delete(&GameDisc{})
			database.Unscoped().Delete(&victim)
			if victim.ID == g.ID {
				continue
			}
		}

		database.Model(&g).Update("file_path", relPath)
	}

	// Migrate GameDisc records
	var discs []GameDisc
	if err := database.Where("file_path LIKE '/%' OR file_path LIKE '../%'").Find(&discs).Error; err != nil {
		return fmt.Errorf("loading non-canonical-path discs: %w", err)
	}
	for _, d := range discs {
		relPath := toRelativePath(d.FilePath, gameDirs)
		if relPath == d.FilePath {
			relPath = extractRelativeByConsoleFolders(d.FilePath, folderNames)
		}
		if relPath != d.FilePath {
			database.Model(&d).Update("file_path", relPath)
		}
	}

	return nil
}

// toRelativePath strips a gameDirs prefix from an absolute path.
func toRelativePath(absPath string, gameDirs []string) string {
	for _, dir := range gameDirs {
		absDir := dir
		if !filepath.IsAbs(absDir) {
			var err error
			absDir, err = filepath.Abs(absDir)
			if err != nil {
				continue
			}
		}
		prefix := absDir + string(filepath.Separator)
		if strings.HasPrefix(absPath, prefix) {
			return absPath[len(prefix):]
		}
	}
	return absPath
}

// extractRelativeByConsoleFolders finds a known console FolderName in the path
// segments and returns everything from that segment onward.
// e.g. "/old/docker/path/nes/game.nes" → "nes/game.nes"
func extractRelativeByConsoleFolders(absPath string, folderNames map[string]bool) string {
	// Split into segments: "/old/path/nes/game.nes" → ["old", "path", "nes", "game.nes"]
	parts := strings.Split(filepath.ToSlash(absPath), "/")
	for i, part := range parts {
		if folderNames[part] && i < len(parts)-1 {
			return filepath.Join(parts[i:]...)
		}
	}
	return absPath
}

// duplicateGroup holds a file_path and the count of games sharing it.
type duplicateGroup struct {
	FilePath string
	Cnt      int
}

// DeduplicateGames finds games with identical file_path values and merges user
// data (favorites, play history, save states, etc.) from duplicates into the
// best keeper before deleting the duplicates.
func DeduplicateGames(database *gorm.DB) error {
	var groups []duplicateGroup
	if err := database.Model(&Game{}).
		Select("file_path, COUNT(*) as cnt").
		Where("deleted_at IS NULL").
		Group("file_path").
		Having("cnt > 1").
		Find(&groups).Error; err != nil {
		return fmt.Errorf("finding duplicate games: %w", err)
	}
	if len(groups) == 0 {
		return nil
	}
	slog.Info("deduplicating games", "duplicateGroups", len(groups))

	for _, g := range groups {
		var dupes []Game
		if err := database.Where("file_path = ?", g.FilePath).Order("id ASC").Find(&dupes).Error; err != nil {
			slog.Warn("loading duplicates", "path", g.FilePath, "error", err)
			continue
		}
		if len(dupes) < 2 {
			continue
		}

		// Pick the keeper: prefer the one with best metadata
		keeper := pickKeeper(dupes)

		for _, dup := range dupes {
			if dup.ID == keeper.ID {
				continue
			}
			slog.Info("merging duplicate into keeper",
				"keeperID", keeper.ID, "dupID", dup.ID, "path", g.FilePath)
			mergeGameData(database, keeper.ID, dup.ID)
			mergeGameMetadata(database, &keeper, &dup)
			// Hard-delete the duplicate
			database.Unscoped().Where("game_id = ?", dup.ID).Delete(&GameDisc{})
			database.Unscoped().Delete(&dup)
		}
	}
	return nil
}

// pickKeeper selects the best game to keep: has ScraperID > has CoverURL > lowest ID.
func pickKeeper(games []Game) Game {
	best := games[0]
	for _, g := range games[1:] {
		if betterMetadata(g, best) {
			best = g
		}
	}
	return best
}

func betterMetadata(a, b Game) bool {
	aScore := metadataScore(a)
	bScore := metadataScore(b)
	if aScore != bScore {
		return aScore > bScore
	}
	return a.ID < b.ID
}

func metadataScore(g Game) int {
	score := 0
	if g.ScraperID != "" {
		score += 2
	}
	if g.CoverURL != "" {
		score++
	}
	return score
}

// mergeGameData reassigns user data from dupID to keeperID.
func mergeGameData(database *gorm.DB, keeperID, dupID uint) {
	// SaveState — move all
	database.Model(&SaveState{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// SaveData — move all
	database.Model(&SaveData{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// GameScreenshot — move all
	database.Model(&GameScreenshot{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// SharedSaveState — move all
	database.Model(&SharedSaveState{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// GameAchievementCache — move all
	database.Model(&GameAchievementCache{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// ActivityEvent — move all
	database.Model(&ActivityEvent{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// Challenge — move all
	database.Model(&Challenge{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// NetplaySession — move all
	database.Model(&NetplaySession{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// Relay — move all
	database.Model(&Relay{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// GameKeyMappingPreference — move, skip conflicts
	database.Model(&GameKeyMappingPreference{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// Favorite — skip if keeper already has one for same user (unique constraint)
	var dupFavs []Favorite
	database.Where("game_id = ?", dupID).Find(&dupFavs)
	for _, f := range dupFavs {
		var count int64
		database.Model(&Favorite{}).Where("user_id = ? AND game_id = ?", f.UserID, keeperID).Count(&count)
		if count == 0 {
			database.Model(&f).Update("game_id", keeperID)
		} else {
			database.Unscoped().Delete(&f)
		}
	}

	// PlayLaterItem — skip if keeper already has one for same user
	var dupPLI []PlayLaterItem
	database.Where("game_id = ?", dupID).Find(&dupPLI)
	for _, p := range dupPLI {
		var count int64
		database.Model(&PlayLaterItem{}).Where("user_id = ? AND game_id = ?", p.UserID, keeperID).Count(&count)
		if count == 0 {
			database.Model(&p).Update("game_id", keeperID)
		} else {
			database.Unscoped().Delete(&p)
		}
	}

	// GameRating — skip if keeper already has one for same user
	var dupRatings []GameRating
	database.Where("game_id = ?", dupID).Find(&dupRatings)
	for _, r := range dupRatings {
		var count int64
		database.Model(&GameRating{}).Where("user_id = ? AND game_id = ?", r.UserID, keeperID).Count(&count)
		if count == 0 {
			database.Model(&r).Update("game_id", keeperID)
		} else {
			database.Unscoped().Delete(&r)
		}
	}

	// CollectionItem — skip if keeper already in same collection
	var dupCI []CollectionItem
	database.Where("game_id = ?", dupID).Find(&dupCI)
	for _, ci := range dupCI {
		var count int64
		database.Model(&CollectionItem{}).Where("collection_id = ? AND game_id = ?", ci.CollectionID, keeperID).Count(&count)
		if count == 0 {
			database.Model(&ci).Update("game_id", keeperID)
		} else {
			database.Unscoped().Delete(&ci)
		}
	}

	// PlayHistory — merge: keep highest play time and latest timestamp per user
	var dupPH []PlayHistory
	database.Where("game_id = ?", dupID).Find(&dupPH)
	for _, ph := range dupPH {
		var existing PlayHistory
		err := database.Where("user_id = ? AND game_id = ?", ph.UserID, keeperID).First(&existing).Error
		if err != nil {
			// No existing entry, just move it
			database.Model(&ph).Update("game_id", keeperID)
		} else {
			// Merge: keep best values
			updates := map[string]interface{}{}
			if ph.PlayTime > existing.PlayTime {
				updates["play_time"] = ph.PlayTime
			}
			if ph.LastPlayed.After(existing.LastPlayed) {
				updates["last_played"] = ph.LastPlayed
			}
			if len(updates) > 0 {
				database.Model(&existing).Updates(updates)
			}
			database.Unscoped().Delete(&ph)
		}
	}
}

// mergeGameMetadata copies metadata fields from dup to keeper if keeper is missing them.
func mergeGameMetadata(database *gorm.DB, keeper, dup *Game) {
	updates := map[string]interface{}{}
	if keeper.ScraperID == "" && dup.ScraperID != "" {
		updates["scraper_id"] = dup.ScraperID
	}
	if keeper.CoverURL == "" && dup.CoverURL != "" {
		updates["cover_url"] = dup.CoverURL
	}
	if keeper.Description == "" && dup.Description != "" {
		updates["description"] = dup.Description
	}
	if keeper.Developer == "" && dup.Developer != "" {
		updates["developer"] = dup.Developer
	}
	if keeper.Publisher == "" && dup.Publisher != "" {
		updates["publisher"] = dup.Publisher
	}
	if keeper.Genre == "" && dup.Genre != "" {
		updates["genre"] = dup.Genre
	}
	if keeper.ReleaseDate == "" && dup.ReleaseDate != "" {
		updates["release_date"] = dup.ReleaseDate
	}
	if keeper.ScreenshotURL == "" && dup.ScreenshotURL != "" {
		updates["screenshot_url"] = dup.ScreenshotURL
	}
	if keeper.IGDBCoverURL == "" && dup.IGDBCoverURL != "" {
		updates["igdb_cover_url"] = dup.IGDBCoverURL
	}
	if keeper.LibRetroCoverURL == "" && dup.LibRetroCoverURL != "" {
		updates["lib_retro_cover_url"] = dup.LibRetroCoverURL
	}
	if keeper.Players == 0 && dup.Players > 0 {
		updates["players"] = dup.Players
	}
	if keeper.Rating == 0 && dup.Rating > 0 {
		updates["rating"] = dup.Rating
	}
	if keeper.Region == "" && dup.Region != "" {
		updates["region"] = dup.Region
	}
	if keeper.CRC32 == "" && dup.CRC32 != "" {
		updates["crc32"] = dup.CRC32
	}
	if len(updates) > 0 {
		database.Model(keeper).Updates(updates)
	}
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
