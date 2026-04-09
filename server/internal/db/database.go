package db

import (
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"strings"

	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
	"gorm.io/gorm/logger"
)

// checkDatabaseDirectory verifies that the database directory exists and is
// writable, producing clear error messages for common deployment problems.
func checkDatabaseDirectory(dir, dbPath string) error {
	// Check if the directory exists.
	info, err := os.Stat(dir)
	if os.IsNotExist(err) {
		// Try to create it.
		if mkErr := os.MkdirAll(dir, 0700); mkErr != nil {
			return fmt.Errorf("database directory %q does not exist and could not be created: %w\n"+
				"  Hint: Create the directory on the host and ensure it is writable by the container user.\n"+
				"  Example: sudo mkdir -p %s && sudo chown 1000:1000 %s", dir, mkErr, dir, dir)
		}
		slog.Info("created database directory", "path", dir)
		return nil
	}
	if err != nil {
		return fmt.Errorf("cannot access database directory %q: %w", dir, err)
	}

	// Exists but is not a directory.
	if !info.IsDir() {
		return fmt.Errorf("database directory path %q exists but is not a directory (mode: %s)", dir, info.Mode())
	}

	// Check if we can write to the directory by creating a temp file.
	testFile := filepath.Join(dir, ".spela-write-test")
	f, err := os.Create(testFile)
	if err != nil {
		uid := os.Getuid()
		gid := os.Getgid()
		return fmt.Errorf("database directory %q exists but is not writable: %w\n"+
			"  Directory permissions: %s\n"+
			"  Container is running as uid=%d gid=%d\n"+
			"  Hint: Fix permissions on the host with:\n"+
			"    sudo chown %d:%d %s\n"+
			"  Or more permissive:\n"+
			"    sudo chmod 777 %s",
			dir, err, info.Mode().Perm(), uid, gid, uid, gid, dir, dir)
	}
	f.Close()
	os.Remove(testFile)

	// If the database file already exists, check if it's readable/writable.
	if dbInfo, err := os.Stat(dbPath); err == nil {
		file, err := os.OpenFile(dbPath, os.O_RDWR, 0)
		if err != nil {
			uid := os.Getuid()
			gid := os.Getgid()
			return fmt.Errorf("database file %q exists but cannot be opened for read/write: %w\n"+
				"  File permissions: %s\n"+
				"  Container is running as uid=%d gid=%d\n"+
				"  Hint: Fix permissions with:\n"+
				"    sudo chown %d:%d %s",
				dbPath, err, dbInfo.Mode().Perm(), uid, gid, uid, gid, dbPath)
		}
		file.Close()
	}

	return nil
}

// Initialize opens the SQLite database and runs auto-migrations.
// The database file is restricted to owner-only access (0600) to prevent
// other users on the system from reading tokens and password hashes.
func Initialize(dbPath string) (*gorm.DB, error) {
	dir := filepath.Dir(dbPath)
	if dir == "" || dir == "." {
		dir = "."
	}

	// Preflight checks: verify the database directory exists and is writable.
	if err := checkDatabaseDirectory(dir, dbPath); err != nil {
		return nil, err
	}

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
		&MediaTypeCategory{},
		&MediaType{},
		&HardwareMaker{},
		&Console{},
		&Game{},
		&GameDisc{},
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
		&SharedSession{},
		&SharedSessionMember{},
		&SharedSessionInvite{},
		&SharedSessionSave{},
		&NetplaySession{},
		&NetplayInvite{},
		&Challenge{},
		&ChallengeAttempt{},
		&GameKeyMappingPreference{},
		&GameScreenshot{},
		&StagedUpload{},
		&TopRatedGame{},
		&SimilarGame{},
		&LoginAttempt{},
		&TokenBlacklist{},
		&CheatCode{},
		&GameSession{},
		&SessionSaveState{},
		&SessionSaveData{},
		&SessionCheatSetting{},
		&DailyPlayActivity{},
		&GameArtwork{},
		// Company metadata
		&Company{},
		// Phase 2 Explore: IGDB enrichment
		&GameTheme{},
		&GameKeyword{},
		&GamePlayerPerspective{},
		&GameFranchise{},
		&GameSeries{},
		&GameSeriesEntry{},
		&GameFranchiseGroup{},
		&GameFranchiseEntry{},
		&GameArtworkImage{},
		// Regional release dates
		&GameReleaseDate{},
		// Videos, language supports, age ratings
		&GameVideo{},
		&GameLanguageSupport{},
		&GameAgeRating{},
		// Phase 13: Saved Searches
		&SavedSearch{},
		// Achievement Showcase
		&UserAchievementShowcase{},
		// Scrape results per source
		&GameScrapeResult{},
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
	// GameScreenshot — move all
	database.Model(&GameScreenshot{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// SharedSaveState — move all
	database.Model(&SharedSaveState{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// GameArtwork — delete duplicate (keeper keeps its own or we skip)
	database.Where("game_id = ?", dupID).Delete(&GameArtwork{})

	// GameAchievementCache — move all
	database.Model(&GameAchievementCache{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// ActivityEvent — move all
	database.Model(&ActivityEvent{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// Challenge — move all
	database.Model(&Challenge{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// NetplaySession — move all
	database.Model(&NetplaySession{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// SharedSession — move all
	database.Model(&SharedSession{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// GameSession — move all
	database.Model(&GameSession{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

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

	// --- Phase 2 Enrichment tables ---

	// GameTheme — move, skip if keeper already has same theme
	var dupThemes []GameTheme
	database.Where("game_id = ?", dupID).Find(&dupThemes)
	for _, t := range dupThemes {
		var count int64
		database.Model(&GameTheme{}).Where("game_id = ? AND igdb_theme_id = ?", keeperID, t.IGDBThemeID).Count(&count)
		if count == 0 {
			database.Model(&t).Update("game_id", keeperID)
		} else {
			database.Unscoped().Delete(&t)
		}
	}

	// GameKeyword — move, skip if keeper already has same keyword
	var dupKeywords []GameKeyword
	database.Where("game_id = ?", dupID).Find(&dupKeywords)
	for _, k := range dupKeywords {
		var count int64
		database.Model(&GameKeyword{}).Where("game_id = ? AND igdb_keyword_id = ?", keeperID, k.IGDBKeywordID).Count(&count)
		if count == 0 {
			database.Model(&k).Update("game_id", keeperID)
		} else {
			database.Unscoped().Delete(&k)
		}
	}

	// GamePlayerPerspective — move, skip if keeper already has same perspective
	var dupPerspectives []GamePlayerPerspective
	database.Where("game_id = ?", dupID).Find(&dupPerspectives)
	for _, p := range dupPerspectives {
		var count int64
		database.Model(&GamePlayerPerspective{}).Where("game_id = ? AND igdb_perspective_id = ?", keeperID, p.IGDBPerspectiveID).Count(&count)
		if count == 0 {
			database.Model(&p).Update("game_id", keeperID)
		} else {
			database.Unscoped().Delete(&p)
		}
	}

	// GameFranchise — move, skip if keeper already has same franchise
	var dupFranchises []GameFranchise
	database.Where("game_id = ?", dupID).Find(&dupFranchises)
	for _, f := range dupFranchises {
		var count int64
		database.Model(&GameFranchise{}).Where("game_id = ? AND igdb_franchise_id = ?", keeperID, f.IGDBFranchiseID).Count(&count)
		if count == 0 {
			database.Model(&f).Update("game_id", keeperID)
		} else {
			database.Unscoped().Delete(&f)
		}
	}

	// GameArtworkImage — move, skip if keeper already has same image
	var dupArtworks []GameArtworkImage
	database.Where("game_id = ?", dupID).Find(&dupArtworks)
	for _, a := range dupArtworks {
		var count int64
		database.Model(&GameArtworkImage{}).Where("game_id = ? AND igdb_image_id = ?", keeperID, a.IGDBImageID).Count(&count)
		if count == 0 {
			database.Model(&a).Update("game_id", keeperID)
		} else {
			database.Unscoped().Delete(&a)
		}
	}

	// GameReleaseDate — move, skip if keeper already has same region
	var dupReleaseDates []GameReleaseDate
	database.Where("game_id = ?", dupID).Find(&dupReleaseDates)
	for _, rd := range dupReleaseDates {
		var count int64
		database.Model(&GameReleaseDate{}).Where("game_id = ? AND region = ?", keeperID, rd.Region).Count(&count)
		if count == 0 {
			database.Model(&rd).Update("game_id", keeperID)
		} else {
			database.Unscoped().Delete(&rd)
		}
	}

	// GameVideo — move, skip if keeper already has same video
	var dupVideos []GameVideo
	database.Where("game_id = ?", dupID).Find(&dupVideos)
	for _, v := range dupVideos {
		var count int64
		database.Model(&GameVideo{}).Where("game_id = ? AND video_id = ?", keeperID, v.VideoID).Count(&count)
		if count == 0 {
			database.Model(&v).Update("game_id", keeperID)
		} else {
			database.Unscoped().Delete(&v)
		}
	}

	// GameLanguageSupport — move, skip if keeper already has same entry
	var dupLangSupports []GameLanguageSupport
	database.Where("game_id = ?", dupID).Find(&dupLangSupports)
	for _, ls := range dupLangSupports {
		var count int64
		database.Model(&GameLanguageSupport{}).Where("game_id = ? AND language = ? AND support_type = ?", keeperID, ls.Language, ls.SupportType).Count(&count)
		if count == 0 {
			database.Model(&ls).Update("game_id", keeperID)
		} else {
			database.Unscoped().Delete(&ls)
		}
	}

	// GameAgeRating — move, skip if keeper already has same category
	var dupAgeRatings []GameAgeRating
	database.Where("game_id = ?", dupID).Find(&dupAgeRatings)
	for _, ar := range dupAgeRatings {
		var count int64
		database.Model(&GameAgeRating{}).Where("game_id = ? AND category = ?", keeperID, ar.Category).Count(&count)
		if count == 0 {
			database.Model(&ar).Update("game_id", keeperID)
		} else {
			database.Unscoped().Delete(&ar)
		}
	}

	// GameSeriesEntry — update any entries pointing to the duplicate game
	database.Model(&GameSeriesEntry{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

	// GameFranchiseEntry — update any entries pointing to the duplicate game
	database.Model(&GameFranchiseEntry{}).Where("game_id = ?", dupID).Update("game_id", keeperID)

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
	if keeper.GameModes == "" && dup.GameModes != "" {
		updates["game_modes"] = dup.GameModes
	}
	if keeper.Storyline == "" && dup.Storyline != "" {
		updates["storyline"] = dup.Storyline
	}
	if keeper.TotalRating == 0 && dup.TotalRating > 0 {
		updates["total_rating"] = dup.TotalRating
	}
	if keeper.TotalRatingCount == 0 && dup.TotalRatingCount > 0 {
		updates["total_rating_count"] = dup.TotalRatingCount
	}
	if keeper.IGDBUserRating == 0 && dup.IGDBUserRating > 0 {
		updates["igdb_user_rating"] = dup.IGDBUserRating
	}
	if keeper.IGDBUserRatingCount == 0 && dup.IGDBUserRatingCount > 0 {
		updates["igdb_user_rating_count"] = dup.IGDBUserRatingCount
	}
	if keeper.TimeToBeatHastily == 0 && dup.TimeToBeatHastily > 0 {
		updates["time_to_beat_hastily"] = dup.TimeToBeatHastily
	}
	if keeper.TimeToBeatNormally == 0 && dup.TimeToBeatNormally > 0 {
		updates["time_to_beat_normally"] = dup.TimeToBeatNormally
	}
	if keeper.TimeToBeatCompletely == 0 && dup.TimeToBeatCompletely > 0 {
		updates["time_to_beat_completely"] = dup.TimeToBeatCompletely
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
	if keeper.IGDBCriticsRating == 0 && dup.IGDBCriticsRating > 0 {
		updates["rating"] = dup.IGDBCriticsRating
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

// MigrateSharedSessions creates GameSession records for existing SharedSessions that
// don't have a SessionID, and copies their SharedSessionSave records into
// SessionSaveState. Guarded by the "relay_sessions_migrated" ServerSetting.
func MigrateSharedSessions(database *gorm.DB) error {
	// Check if already migrated
	var setting ServerSetting
	if err := database.Where("key = ?", "relay_sessions_migrated").First(&setting).Error; err == nil {
		if setting.Value == "true" {
			return nil
		}
	}

	// Find shared sessions without a session
	var sharedSessions []SharedSession
	if err := database.Where("session_id IS NULL").Find(&sharedSessions).Error; err != nil {
		return fmt.Errorf("loading shared sessions without sessions: %w", err)
	}

	if len(sharedSessions) == 0 {
		database.Create(&ServerSetting{Key: "relay_sessions_migrated", Value: "true"})
		return nil
	}

	slog.Info("migrating shared sessions to sessions", "count", len(sharedSessions))

	for _, r := range sharedSessions {
		session := GameSession{
			OwnerID: r.OwnerID,
			GameID:  r.GameID,
			Name:    "Shared Session: " + r.Name,
		}
		if err := database.Create(&session).Error; err != nil {
			slog.Warn("failed to create session for shared session",
				"sharedSessionId", r.ID, "error", err)
			continue
		}

		// Copy SharedSessionSave records into SessionSaveState
		var saves []SharedSessionSave
		database.Where("shared_session_id = ?", r.ID).Find(&saves)
		for _, s := range saves {
			ss := SessionSaveState{
				SessionID:     session.ID,
				UserID:        s.UserID,
				Name:          s.Name,
				FilePath:      s.FilePath,
				FileSize:      s.FileSize,
				ScreenshotURL: s.ScreenshotURL,
				IsAuto:        s.IsAuto,
			}
			ss.CreatedAt = s.CreatedAt
			ss.UpdatedAt = s.UpdatedAt
			database.Create(&ss)
		}

		// Link the shared session to its new session
		database.Model(&r).Update("session_id", session.ID)
	}

	database.Create(&ServerSetting{Key: "relay_sessions_migrated", Value: "true"})
	slog.Info("shared-session migration completed", "shared_sessions_migrated", len(sharedSessions))
	return nil
}

// SeedConsoles inserts the default console definitions if they don't exist.
// For existing consoles, it backfills the EmulatorJSCore field if empty.
func SeedConsoles(db *gorm.DB) error {
	consoles := []Console{
		// 3rd Generation
		{Name: "Nintendo Entertainment System", Abbreviation: "NES", Extensions: ".nes,.fds", DefaultCore: "nestopia", EmulatorJSCore: "nestopia", FolderName: "nes", ColorTheme: "#e60012", CoverAspect: "5:7", Generation: 3, SaveStateSupport: true, Playable: true},
		{Name: "Famicom Disk System", Abbreviation: "FDS", Extensions: ".fds", DefaultCore: "nestopia", EmulatorJSCore: "nestopia", FolderName: "fds", ColorTheme: "#e60012", CoverAspect: "1:1", Generation: 3, SaveStateSupport: true, Playable: true},
		{Name: "Sega Master System", Abbreviation: "SMS", Extensions: ".sms", DefaultCore: "genesis_plus_gx", EmulatorJSCore: "segaMS", FolderName: "mastersystem", ColorTheme: "#0060a8", Generation: 3, SaveStateSupport: true, Playable: true},
		{Name: "Sega SG-1000", Abbreviation: "SG1K", Extensions: ".sg,.sc,.sf7,.bin", DefaultCore: "genesis_plus_gx", EmulatorJSCore: "segaMS", FolderName: "sg-1000", ColorTheme: "#0060a8", Generation: 3, SaveStateSupport: true, Playable: true},
		{Name: "Atari 7800", Abbreviation: "A78", Extensions: ".a78,.bin", DefaultCore: "prosystem", EmulatorJSCore: "prosystem", FolderName: "atari7800", ColorTheme: "#8b4513", Generation: 3, SaveStateSupport: true, Playable: true},
		// 4th Generation
		{Name: "Super Nintendo", Abbreviation: "SNES", Extensions: ".sfc,.smc", DefaultCore: "snes9x", EmulatorJSCore: "snes9x", FolderName: "snes", ColorTheme: "#7b7db5", CoverAspect: "4:3", Generation: 4, SaveStateSupport: true, Playable: true},
		{Name: "Sega Genesis", Abbreviation: "GEN", Extensions: ".md,.gen,.bin", DefaultCore: "genesis_plus_gx", EmulatorJSCore: "segaMD", FolderName: "genesis", ColorTheme: "#171717", Generation: 4, SaveStateSupport: true, Playable: true},
		{Name: "Game Boy", Abbreviation: "GB", Extensions: ".gb", DefaultCore: "gambatte", EmulatorJSCore: "gambatte", FolderName: "gb", ColorTheme: "#8bac0f", CoverAspect: "7:8", Generation: 4, SaveStateSupport: true, Playable: true},
		{Name: "Game Gear", Abbreviation: "GG", Extensions: ".gg", DefaultCore: "genesis_plus_gx", EmulatorJSCore: "segaGG", FolderName: "gamegear", ColorTheme: "#1a1a1a", CoverAspect: "1:1", Generation: 4, SaveStateSupport: true, Playable: true},
		{Name: "TurboGrafx-16", Abbreviation: "PCE", Extensions: ".pce", DefaultCore: "mednafen_pce", EmulatorJSCore: "mednafen_pce", FolderName: "tg16", ColorTheme: "#ff6600", Generation: 4, SaveStateSupport: true, Playable: true},
		{Name: "TurboGrafx-CD", Abbreviation: "PCECD", Extensions: ".chd,.cue,.iso,.m3u", DefaultCore: "mednafen_pce", EmulatorJSCore: "mednafen_pce", FolderName: "tg16cd", ColorTheme: "#ff6600", Generation: 4, SaveStateSupport: true, Playable: true},
		{Name: "PC Engine SuperGrafx", Abbreviation: "SGX", Extensions: ".sgx,.pce", DefaultCore: "mednafen_supergrafx", EmulatorJSCore: "mednafen_supergrafx", FolderName: "supergrafx", ColorTheme: "#ff6600", Generation: 4, SaveStateSupport: true, Playable: true},
		{Name: "Neo Geo", Abbreviation: "NEOGEO", Extensions: ".zip", DefaultCore: "fbneo", EmulatorJSCore: "fbneo", FolderName: "neogeo", ColorTheme: "#ffcc00", Generation: 4, SaveStateSupport: true, Playable: true},
		{Name: "Neo Geo CD", Abbreviation: "NEOCD", Extensions: ".chd,.cue,.iso", DefaultCore: "neocd", EmulatorJSCore: "", FolderName: "neogeocd", ColorTheme: "#ffcc00", Generation: 4, SaveStateSupport: true, Playable: true},
		{Name: "Atari Lynx", Abbreviation: "LYNX", Extensions: ".lnx,.lyx", DefaultCore: "handy", EmulatorJSCore: "handy", FolderName: "atarilynx", ColorTheme: "#8b4513", CoverAspect: "1:1", Generation: 4, SaveStateSupport: true, Playable: true},
		{Name: "Sega CD", Abbreviation: "SCD", Extensions: ".iso,.bin,.cue,.m3u", DefaultCore: "clownmdemu", EmulatorJSCore: "segaCD", FolderName: "segacd", ColorTheme: "#1a1a1a", Generation: 4, SaveStateSupport: true, Playable: true},
		{Name: "Philips CD-i", Abbreviation: "CDI", Extensions: ".chd,.cue,.iso", DefaultCore: "same_cdi", EmulatorJSCore: "same_cdi", FolderName: "cdi", ColorTheme: "#006633", Generation: 4, SaveStateSupport: true, Playable: true},
		// 5th Generation
		{Name: "PlayStation", Abbreviation: "PSX", Extensions: ".bin,.cue,.iso,.pbp,.m3u", DefaultCore: "beetle_psx_hw", EmulatorJSCore: "mednafen_psx_hw", FolderName: "psx", ColorTheme: "#003087", CoverAspect: "1:1", Generation: 5, SaveStateSupport: true, Playable: true},
		{Name: "Nintendo 64", Abbreviation: "N64", Extensions: ".n64,.z64,.v64", DefaultCore: "mupen64plus_next", EmulatorJSCore: "mupen64plus_next", FolderName: "n64", ColorTheme: "#009e60", CoverAspect: "10:7", Generation: 5, SaveStateSupport: true, Playable: true},
		{Name: "Sega Saturn", Abbreviation: "SAT", Extensions: ".iso,.bin,.cue,.chd,.m3u", DefaultCore: "yabause", EmulatorJSCore: "yabause", FolderName: "saturn", ColorTheme: "#0a4da2", Generation: 5, SaveStateSupport: true, Playable: true},
		{Name: "Game Boy Color", Abbreviation: "GBC", Extensions: ".gbc", DefaultCore: "gambatte", EmulatorJSCore: "gambatte", FolderName: "gbc", ColorTheme: "#6638a8", CoverAspect: "7:8", Generation: 5, SaveStateSupport: true, Playable: true},
		{Name: "Atari Jaguar", Abbreviation: "JAG", Extensions: ".j64,.jag", DefaultCore: "virtualjaguar", EmulatorJSCore: "virtualjaguar", FolderName: "atarijaguar", ColorTheme: "#8b4513", Generation: 5, SaveStateSupport: false, Playable: true},
		{Name: "Virtual Boy", Abbreviation: "VB", Extensions: ".vb,.vboy", DefaultCore: "beetle_vb", EmulatorJSCore: "beetle_vb", FolderName: "virtualboy", ColorTheme: "#ff0000", Generation: 5, SaveStateSupport: true, Playable: true},
		{Name: "3DO", Abbreviation: "3DO", Extensions: ".iso,.bin,.cue,.chd", DefaultCore: "opera", EmulatorJSCore: "opera", FolderName: "3do", ColorTheme: "#c0c0c0", Generation: 5, SaveStateSupport: true, Playable: true},
		{Name: "Neo Geo Pocket / Color", Abbreviation: "NGP", Extensions: ".ngp,.ngc", DefaultCore: "beetle_ngp", EmulatorJSCore: "mednafen_ngp", FolderName: "ngp", ColorTheme: "#1a75bc", CoverAspect: "1:1", Generation: 5, SaveStateSupport: true, Playable: true},
		{Name: "WonderSwan", Abbreviation: "WS", Extensions: ".ws,.wsc", DefaultCore: "beetle_wswan", EmulatorJSCore: "mednafen_wswan", FolderName: "wonderswan", ColorTheme: "#4b0082", CoverAspect: "1:1", Generation: 5, SaveStateSupport: true, Playable: true},
		{Name: "Sega 32X", Abbreviation: "32X", Extensions: ".32x", DefaultCore: "picodrive", EmulatorJSCore: "sega32x", FolderName: "sega32x", ColorTheme: "#1a1a1a", Generation: 5, SaveStateSupport: true, Playable: true},
		{Name: "PC-FX", Abbreviation: "PCFX", Extensions: ".iso,.cue,.m3u", DefaultCore: "beetle_pcfx", EmulatorJSCore: "mednafen_pcfx", FolderName: "pcfx", ColorTheme: "#ff6600", Generation: 5, SaveStateSupport: true, Playable: true},
		{Name: "Amiga CD32", Abbreviation: "ACD32", Extensions: ".iso,.cue,.bin,.chd", DefaultCore: "", EmulatorJSCore: "", FolderName: "acd32", ColorTheme: "#6c5eb5", CoverAspect: "8:11", Generation: 5, SaveStateSupport: false, Playable: false},
		// 6th Generation
		{Name: "PlayStation 2", Abbreviation: "PS2", Extensions: ".iso,.bin,.cue,.chd,.m3u", DefaultCore: "play", EmulatorJSCore: "", FolderName: "ps2", ColorTheme: "#003087", Generation: 6, SaveStateSupport: true, Playable: true},
		{Name: "Dreamcast", Abbreviation: "DC", Extensions: ".gdi,.cdi,.chd,.cue,.bin,.m3u", DefaultCore: "flycast", EmulatorJSCore: "", FolderName: "dreamcast", ColorTheme: "#c0c0c0", Generation: 6, SaveStateSupport: true, Playable: true},
		{Name: "Nintendo GameCube", Abbreviation: "GC", Extensions: ".iso,.gcm,.gcz,.ciso,.rvz", DefaultCore: "dolphin", EmulatorJSCore: "", FolderName: "gc", ColorTheme: "#6f5fa6", CoverAspect: "1:1", Generation: 6, SaveStateSupport: true, Playable: true},
		{Name: "Game Boy Advance", Abbreviation: "GBA", Extensions: ".gba", DefaultCore: "mgba", EmulatorJSCore: "mgba", FolderName: "gba", ColorTheme: "#2e17a3", CoverAspect: "1:1", Generation: 6, SaveStateSupport: true, Playable: true},
		{Name: "Pokemon Mini", Abbreviation: "PKMN", Extensions: ".min", DefaultCore: "pokemini", EmulatorJSCore: "", FolderName: "pokemonmini", ColorTheme: "#ffcc00", CoverAspect: "1:1", Generation: 6, SaveStateSupport: true, Playable: true},
		{Name: "Xbox", Abbreviation: "XBOX", Extensions: ".iso,.xbe", DefaultCore: "", EmulatorJSCore: "", FolderName: "xbox", ColorTheme: "#107c10", CoverAspect: "8:11", Generation: 6, SaveStateSupport: false, Playable: false},
		// 7th Generation
		{Name: "Nintendo Wii", Abbreviation: "WII", Extensions: ".iso,.wbfs,.gcz,.rvz,.ciso", DefaultCore: "", EmulatorJSCore: "", FolderName: "wii", ColorTheme: "#c0c0c0", CoverAspect: "8:11", Generation: 7, SaveStateSupport: false, Playable: false},
		{Name: "PlayStation 3", Abbreviation: "PS3", Extensions: ".iso,.bin,.pkg", DefaultCore: "", EmulatorJSCore: "", FolderName: "ps3", ColorTheme: "#003087", CoverAspect: "8:11", Generation: 7, SaveStateSupport: false, Playable: false},
		{Name: "Xbox 360", Abbreviation: "X360", Extensions: ".iso,.xex,.god", DefaultCore: "", EmulatorJSCore: "", FolderName: "xbox360", ColorTheme: "#107c10", CoverAspect: "8:11", Generation: 7, SaveStateSupport: false, Playable: false},
		{Name: "PlayStation Portable", Abbreviation: "PSP", Extensions: ".iso,.cso,.chd", DefaultCore: "ppsspp", EmulatorJSCore: "ppsspp", FolderName: "psp", ColorTheme: "#000000", Generation: 7, SaveStateSupport: true, Playable: true},
		{Name: "Nintendo DS", Abbreviation: "NDS", Extensions: ".nds", DefaultCore: "desmume", EmulatorJSCore: "desmume", FolderName: "nds", ColorTheme: "#b0b0b0", CoverAspect: "10:9", Generation: 7, SaveStateSupport: true, Playable: true},
		// 8th Generation
		{Name: "Nintendo Wii U", Abbreviation: "WIIU", Extensions: ".rpx,.wud,.wux", DefaultCore: "", EmulatorJSCore: "", FolderName: "wiiu", ColorTheme: "#009ac7", CoverAspect: "8:11", Generation: 8, SaveStateSupport: false, Playable: false},
		{Name: "PlayStation Vita", Abbreviation: "VITA", Extensions: ".vpk,.mai,.zip", DefaultCore: "", EmulatorJSCore: "", FolderName: "psvita", ColorTheme: "#003087", CoverAspect: "8:11", Generation: 8, SaveStateSupport: false, Playable: false},
		{Name: "PlayStation 4", Abbreviation: "PS4", Extensions: ".pkg", DefaultCore: "", EmulatorJSCore: "", FolderName: "ps4", ColorTheme: "#003087", CoverAspect: "8:11", Generation: 8, SaveStateSupport: false, Playable: false},
		{Name: "Xbox One", Abbreviation: "XONE", Extensions: ".xvd", DefaultCore: "", EmulatorJSCore: "", FolderName: "xboxone", ColorTheme: "#107c10", CoverAspect: "8:11", Generation: 8, SaveStateSupport: false, Playable: false},
		{Name: "Nintendo 3DS", Abbreviation: "3DS", Extensions: ".3ds,.cci,.cia", DefaultCore: "azahar", EmulatorJSCore: "", FolderName: "n3ds", ColorTheme: "#ce181e", Generation: 8, SaveStateSupport: true, Playable: true},
		// 9th Generation
		{Name: "Nintendo Switch", Abbreviation: "NSW", Extensions: ".nsp,.xci", DefaultCore: "", EmulatorJSCore: "", FolderName: "switch", ColorTheme: "#e60012", CoverAspect: "8:11", Generation: 9, SaveStateSupport: false, Playable: false},
		{Name: "PlayStation 5", Abbreviation: "PS5", Extensions: ".pkg", DefaultCore: "", EmulatorJSCore: "", FolderName: "ps5", ColorTheme: "#003087", CoverAspect: "8:11", Generation: 9, SaveStateSupport: false, Playable: false},
		{Name: "Xbox Series", Abbreviation: "XSX", Extensions: ".xvd", DefaultCore: "", EmulatorJSCore: "", FolderName: "xboxseries", ColorTheme: "#107c10", CoverAspect: "8:11", Generation: 9, SaveStateSupport: false, Playable: false},
		// 2nd Generation
		{Name: "Atari 2600", Abbreviation: "A26", Extensions: ".a26,.bin", DefaultCore: "stella", EmulatorJSCore: "stella2014", FolderName: "atari2600", ColorTheme: "#8b4513", Generation: 2, SaveStateSupport: true, Playable: true},
		{Name: "Atari 5200", Abbreviation: "A52", Extensions: ".a52,.bin", DefaultCore: "atari800", EmulatorJSCore: "atari800", FolderName: "atari5200", ColorTheme: "#8b4513", Generation: 2, SaveStateSupport: true, Playable: true},
		{Name: "ColecoVision", Abbreviation: "CV", Extensions: ".col,.rom", DefaultCore: "gearcoleco", EmulatorJSCore: "gearcoleco", FolderName: "colecovision", ColorTheme: "#000000", Generation: 2, SaveStateSupport: true, Playable: true},
		{Name: "Fairchild Channel F", Abbreviation: "CHAF", Extensions: ".bin,.chf", DefaultCore: "freechaf", EmulatorJSCore: "freechaf", FolderName: "channelf", ColorTheme: "#cc6600", Generation: 2, SaveStateSupport: true, Playable: true},
		{Name: "Magnavox Odyssey 2", Abbreviation: "O2", Extensions: ".bin,.o2", DefaultCore: "o2em", EmulatorJSCore: "o2em", FolderName: "odyssey2", ColorTheme: "#b8860b", Generation: 2, SaveStateSupport: true, Playable: true},
		{Name: "Mattel Intellivision", Abbreviation: "INTV", Extensions: ".int,.bin,.rom", DefaultCore: "freeintv", EmulatorJSCore: "freeintv", FolderName: "intellivision", ColorTheme: "#8b6914", Generation: 2, SaveStateSupport: true, Playable: true},
		{Name: "GCE Vectrex", Abbreviation: "VEC", Extensions: ".bin,.vec", DefaultCore: "vecx", EmulatorJSCore: "vecx", FolderName: "vectrex", ColorTheme: "#333333", Generation: 2, SaveStateSupport: true, Playable: true},
		// Handheld / Pre-generation
		{Name: "Nintendo Game & Watch", Abbreviation: "GW", Extensions: ".mgw", DefaultCore: "gw", EmulatorJSCore: "gw", FolderName: "gameandwatch", ColorTheme: "#c0392b", CoverAspect: "1:1", Generation: 1, SaveStateSupport: true, Playable: true},
		// Home Computers (generation = 100)
		{Name: "Atari 8-bit", Abbreviation: "A800", Extensions: ".a52,.atr,.atx,.bas,.bin,.car,.cas,.com,.rom,.xex,.xfd", DefaultCore: "atari800", EmulatorJSCore: "atari800", FolderName: "atari800", ColorTheme: "#8b4513", Generation: 100, SaveStateSupport: true, Playable: true},
		{Name: "Atari ST", Abbreviation: "ATARIST", Extensions: ".st,.stx,.msa,.dim,.ipf,.m3u", DefaultCore: "hatari", EmulatorJSCore: "hatari", FolderName: "atarist", ColorTheme: "#8b4513", Generation: 100, SaveStateSupport: true, Playable: true},
		{Name: "Commodore 64", Abbreviation: "C64", Extensions: ".d64,.t64,.prg,.crt,.tap", DefaultCore: "vice_x64sc", EmulatorJSCore: "vice_x64sc", FolderName: "c64", ColorTheme: "#6c5eb5", Generation: 100, SaveStateSupport: true, Playable: true},
		{Name: "Commodore 128", Abbreviation: "C128", Extensions: ".d64,.d71,.d81,.t64,.prg,.crt", DefaultCore: "vice_x128", EmulatorJSCore: "vice_x128", FolderName: "c128", ColorTheme: "#6c5eb5", Generation: 100, SaveStateSupport: true, Playable: true},
		{Name: "Commodore PET", Abbreviation: "PET", Extensions: ".prg,.d64,.t64", DefaultCore: "vice_xpet", EmulatorJSCore: "vice_xpet", FolderName: "pet", ColorTheme: "#6c5eb5", Generation: 100, SaveStateSupport: true, Playable: true},
		{Name: "Commodore Plus/4", Abbreviation: "PLUS4", Extensions: ".prg,.d64,.t64", DefaultCore: "vice_xplus4", EmulatorJSCore: "vice_xplus4", FolderName: "plus4", ColorTheme: "#6c5eb5", Generation: 100, SaveStateSupport: true, Playable: true},
		{Name: "Commodore VIC-20", Abbreviation: "VIC20", Extensions: ".prg,.d64,.t64,.crt", DefaultCore: "vice_xvic", EmulatorJSCore: "vice_xvic", FolderName: "vic20", ColorTheme: "#6c5eb5", Generation: 100, SaveStateSupport: true, Playable: true},
		{Name: "Commodore Amiga", Abbreviation: "AMIGA", Extensions: ".adf,.hdf,.lha,.ipf,.dms,.zip", DefaultCore: "puae", EmulatorJSCore: "puae", FolderName: "amiga", ColorTheme: "#6c5eb5", Generation: 100, SaveStateSupport: true, Playable: true},
		{Name: "Amiga Demos", Abbreviation: "ADEMO", Extensions: ".adf,.hdf,.lha,.dms,.zip", DefaultCore: "puae", EmulatorJSCore: "puae", FolderName: "amiga-demos", ColorTheme: "#8b7fc7", Generation: 100, SaveStateSupport: false, Playable: true},
		{Name: "DOS", Abbreviation: "DOS", Extensions: ".zip,.dosz,.conf", DefaultCore: "dosbox_pure", EmulatorJSCore: "dosbox_pure", FolderName: "dos", ColorTheme: "#000000", Generation: 100, SaveStateSupport: true, Playable: true},
		{Name: "DOS Demos", Abbreviation: "DDEMO", Extensions: ".zip,.dosz,.conf", DefaultCore: "dosbox_pure", EmulatorJSCore: "dosbox_pure", FolderName: "dos-demos", ColorTheme: "#333333", Generation: 100, SaveStateSupport: false, Playable: true},
		{Name: "MSX", Abbreviation: "MSX1", Extensions: ".rom,.mx1,.dsk,.cas", DefaultCore: "bluemsx", EmulatorJSCore: "", FolderName: "msx1", ColorTheme: "#4a86c8", Generation: 100, SaveStateSupport: true, Playable: true},
		{Name: "MSX2", Abbreviation: "MSX2", Extensions: ".rom,.mx2,.dsk,.cas", DefaultCore: "bluemsx", EmulatorJSCore: "", FolderName: "msx2", ColorTheme: "#4a86c8", Generation: 100, SaveStateSupport: true, Playable: true},
		// Arcade (generation = 101)
		{Name: "Arcade", Abbreviation: "ARCADE", Extensions: ".zip", DefaultCore: "mame2003_plus", EmulatorJSCore: "fbneo", FolderName: "arcade", ColorTheme: "#ff4444", Generation: 101, SaveStateSupport: true, Playable: true},
		// ScummVM (generation = 100, alongside home computers)
		{Name: "ScummVM", Abbreviation: "SCUMMVM", Extensions: ".scummvm", DefaultCore: "scummvm", EmulatorJSCore: "", FolderName: "scummvm", ColorTheme: "#6b8e23", CoverAspect: "5:7", Generation: 100, SaveStateSupport: false, Playable: true},
	}

	for _, c := range consoles {
		wantPlayable := c.Playable
		var existing Console
		result := db.Where("abbreviation = ?", c.Abbreviation).First(&existing)
		if result.Error == gorm.ErrRecordNotFound {
			if err := db.Create(&c).Error; err != nil {
				return fmt.Errorf("seeding console %s: %w", c.Name, err)
			}
			// GORM's Create mutates bool fields with default:true, setting them
			// to true even when the struct had false. Fix by comparing against
			// the original value saved before Create.
			if !wantPlayable {
				db.Exec("UPDATE consoles SET playable = 0 WHERE abbreviation = ?", c.Abbreviation)
			}
			slog.Info("seeded console", "name", c.Name)
		} else {
			// Backfill name changes (e.g., "Neo Geo Pocket" → "Neo Geo Pocket / Color")
			if c.Name != "" && existing.Name != c.Name {
				db.Model(&existing).Update("name", c.Name)
				slog.Info("backfilled Name", "old", existing.Name, "new", c.Name)
			}
			if c.EmulatorJSCore != "" && existing.EmulatorJSCore != c.EmulatorJSCore {
				db.Model(&existing).Update("emulator_js_core", c.EmulatorJSCore)
				slog.Info("backfilled EmulatorJSCore", "name", existing.Name, "old", existing.EmulatorJSCore, "new", c.EmulatorJSCore)
			}
			if c.DefaultCore != "" && existing.DefaultCore != c.DefaultCore {
				db.Model(&existing).Update("default_core", c.DefaultCore)
				slog.Info("backfilled DefaultCore", "name", existing.Name, "core", c.DefaultCore)
			}
			if c.CoverAspect != "" && existing.CoverAspect != c.CoverAspect {
				db.Model(&existing).Update("cover_aspect", c.CoverAspect)
				slog.Info("backfilled CoverAspect", "name", existing.Name, "aspect", c.CoverAspect)
			}
			if c.FolderName != "" && existing.FolderName != c.FolderName {
				db.Model(&existing).Update("folder_name", c.FolderName)
				slog.Info("backfilled FolderName", "name", existing.Name, "folder", c.FolderName)
			}
			if !existing.SaveStateSupport && c.SaveStateSupport {
				db.Model(&existing).Update("save_state_support", true)
				slog.Info("backfilled SaveStateSupport", "name", existing.Name)
			}
			// Backfill Playable flag for existing consoles (handles upgrade from
			// before the non-playable console feature was introduced).
			if existing.Playable != wantPlayable {
				// Use raw SQL because GORM's Update ignores false for bool
				// fields with default:true.
				playableInt := 1
				if !wantPlayable {
					playableInt = 0
				}
				db.Exec("UPDATE consoles SET playable = ? WHERE abbreviation = ?", playableInt, c.Abbreviation)
				slog.Info("backfilled Playable", "name", existing.Name, "playable", wantPlayable)
			}
			// Backfill Generation for existing consoles
			if existing.Generation != c.Generation && c.Generation != 0 {
				db.Model(&existing).Update("generation", c.Generation)
				slog.Info("backfilled Generation", "name", existing.Name, "generation", c.Generation)
			}
			// Backfill any new extensions from the seed that are missing in the DB
			for _, ext := range strings.Split(c.Extensions, ",") {
				ext = strings.TrimSpace(ext)
				if ext != "" && !strings.Contains(existing.Extensions, ext) {
					existing.Extensions = existing.Extensions + "," + ext
					db.Model(&existing).Update("extensions", existing.Extensions)
					slog.Info("backfilled extension", "name", existing.Name, "ext", ext)
				}
			}
		}
	}

	return nil
}

// SeedCores inserts the default libretro core definitions if they don't exist,
// and backfills DownloadURL for existing rows when the seed has a value set.
func SeedCores(db *gorm.DB) error {
	cores := []Core{
		{Name: "nestopia", DisplayName: "Nestopia UE", Description: "Accurate NES/Famicom emulator", Platforms: "windows,linux,macos,android"},
		{Name: "snes9x", DisplayName: "Snes9x", Description: "Portable SNES emulator", Platforms: "windows,linux,macos,android"},
		{Name: "gambatte", DisplayName: "Gambatte", Description: "Game Boy / Game Boy Color emulator", Platforms: "windows,linux,macos,android"},
		{Name: "mgba", DisplayName: "mGBA", Description: "Game Boy Advance emulator", Platforms: "windows,linux,macos,android"},
		{Name: "mupen64plus_next", DisplayName: "Mupen64Plus-Next", Description: "Nintendo 64 emulator", Platforms: "windows,linux,macos,android"},
		{Name: "genesis_plus_gx", DisplayName: "Genesis Plus GX", Description: "Sega 8/16-bit emulator", Platforms: "windows,linux,macos,android"},
		{Name: "genesis_plus_gx_wide", DisplayName: "Genesis Plus GX Wide", Description: "Sega 8/16-bit emulator (widescreen)", Platforms: "windows,linux,macos,android"},
		{Name: "picodrive", DisplayName: "PicoDrive", Description: "Sega 8/16-bit + Sega CD/32X emulator", Platforms: "windows,linux,macos,android"},
		{Name: "clownmdemu", DisplayName: "ClownMDEmu", Description: "Sega Mega Drive/CD emulator (pure C, no JIT)", Platforms: "windows,linux,macos,android"},
		{Name: "mednafen_saturn", DisplayName: "Mednafen Saturn", Description: "Sega Saturn emulator (Beetle Saturn, requires BIOS)", Platforms: "windows,linux,macos"},
		{Name: "yabasanshiro", DisplayName: "YabaSanshiro", Description: "Sega Saturn emulator (HLE BIOS, no external files needed)", Platforms: "windows,linux,macos,android"},
		{Name: "yabause", DisplayName: "Yabause", Description: "Sega Saturn emulator (same core as EmulatorJS)", Platforms: "windows,linux,macos,android"},
		{Name: "beetle_psx_hw", DisplayName: "Beetle PSX HW", Description: "PlayStation emulator with hardware rendering", Platforms: "windows,linux,macos"},
		{Name: "desmume", DisplayName: "DeSmuME", Description: "Nintendo DS emulator", Platforms: "windows,linux,macos"},
		{Name: "dolphin", DisplayName: "Dolphin", Description: "GameCube and Wii emulator", Platforms: "windows,linux,macos,android"},
		{Name: "opera", DisplayName: "Opera", Description: "3DO Interactive Multiplayer emulator", Platforms: "windows,linux,macos,android"},
		{Name: "vice_x128", DisplayName: "VICE x128", Description: "Commodore 128 emulator", Platforms: "windows,linux,macos,android"},
		{Name: "vice_xpet", DisplayName: "VICE xpet", Description: "Commodore PET emulator", Platforms: "windows,linux,macos,android"},
		{Name: "vice_xplus4", DisplayName: "VICE xplus4", Description: "Commodore Plus/4 emulator", Platforms: "windows,linux,macos,android"},
		{Name: "vice_xvic", DisplayName: "VICE xvic", Description: "Commodore VIC-20 emulator", Platforms: "windows,linux,macos,android"},
		{Name: "same_cdi", DisplayName: "SAME CDi", Description: "Philips CD-i emulator", Platforms: "windows,linux,macos,android"},
		{Name: "virtualjaguar", DisplayName: "Virtual Jaguar", Description: "Atari Jaguar emulator", Platforms: "windows,linux,macos,android"},
		{Name: "gearcoleco", DisplayName: "Gearcoleco", Description: "ColecoVision emulator", Platforms: "windows,linux,macos,android"},
		{Name: "puae", DisplayName: "PUAE", Description: "Commodore Amiga emulator", Platforms: "windows,linux,macos,android"},
		{Name: "neocd", DisplayName: "NeoCD", Description: "Neo Geo CD emulator", Platforms: "windows,linux,macos,android"},
		{
			Name:        "azahar",
			DisplayName: "Azahar",
			Description: "Nintendo 3DS emulator (Citra successor)",
			Platforms:   "windows,linux,macos,android",
			Version:     "2125.0.1",
			DownloadURL: "https://github.com/azahar-emu/azahar/releases/download/2125.0.1/azahar-libretro-{platform}-2125.0.1.zip",
		},
		{Name: "scummvm", DisplayName: "ScummVM", Description: "Point-and-click adventure game engine", Platforms: "windows,linux,macos,android"},
		{Name: "freechaf", DisplayName: "FreeChaF", Description: "Fairchild Channel F emulator", Platforms: "windows,linux,macos,android"},
		{Name: "o2em", DisplayName: "O2EM", Description: "Magnavox Odyssey 2 / Philips Videopac emulator", Platforms: "windows,linux,macos,android"},
		{Name: "freeintv", DisplayName: "FreeIntv", Description: "Mattel Intellivision emulator", Platforms: "windows,linux,macos,android"},
		{Name: "vecx", DisplayName: "vecx", Description: "GCE Vectrex emulator", Platforms: "windows,linux,macos,android"},
		{Name: "mednafen_supergrafx", DisplayName: "Beetle SuperGrafx", Description: "NEC PC Engine SuperGrafx emulator", Platforms: "windows,linux,macos,android"},
		{Name: "gw", DisplayName: "GW", Description: "Nintendo Game & Watch simulator", Platforms: "windows,linux,macos,android"},
		{Name: "hatari", DisplayName: "Hatari", Description: "Atari ST/STE/TT/Falcon emulator", Platforms: "windows,linux,macos,android"},
	}

	for _, c := range cores {
		var existing Core
		result := db.Where("name = ?", c.Name).First(&existing)
		if result.Error == gorm.ErrRecordNotFound {
			if err := db.Create(&c).Error; err != nil {
				return fmt.Errorf("seeding core %s: %w", c.Name, err)
			}
			slog.Info("seeded core", "name", c.Name)
		} else {
			if existing.DownloadURL == "" && c.DownloadURL != "" {
				db.Model(&existing).Update("download_url", c.DownloadURL)
				slog.Info("backfilled DownloadURL", "name", existing.Name)
			}
			if existing.Version == "" && c.Version != "" {
				db.Model(&existing).Update("version", c.Version)
			}
		}
	}

	return nil
}

// MigrateScrapeResults backfills GameScrapeResult rows from the legacy
// ScraperID / ScrapeAttempts fields already present on each Game row.
// It is idempotent: if any rows already exist the function returns immediately.
func MigrateScrapeResults(database *gorm.DB) error {
	var count int64
	if err := database.Model(&GameScrapeResult{}).Count(&count).Error; err != nil {
		return fmt.Errorf("counting existing scrape results: %w", err)
	}
	if count > 0 {
		slog.Info("skipping scrape-result migration: rows already present", "count", count)
		return nil
	}

	// Build a set of game IDs that have SteamGridDB artwork (hero URL present).
	// Only load the IDs, not full artwork structs.
	var artworkGameIDs []uint
	database.Model(&GameArtwork{}).Where("hero_url != ''").Pluck("game_id", &artworkGameIDs)
	hasHero := make(map[uint]bool, len(artworkGameIDs))
	for _, id := range artworkGameIDs {
		hasHero[id] = true
	}

	// Process games in batches to avoid loading all 40k+ games into memory
	const batchSize = 500
	var totalGames int64
	database.Model(&Game{}).Where("scrape_attempts > 0").Count(&totalGames)

	totalInserted := 0
	for offset := 0; offset < int(totalGames); offset += batchSize {
		var batch []Game
		if err := database.Where("scrape_attempts > 0").
			Select("id, scraper_id, scrape_attempts, updated_at").
			Limit(batchSize).Offset(offset).Find(&batch).Error; err != nil {
			return fmt.Errorf("loading game batch at offset %d: %w", offset, err)
		}

		var rows []GameScrapeResult
		for _, g := range batch {
			t := g.UpdatedAt

			if strings.HasPrefix(g.ScraperID, "igdb:") {
				rows = append(rows, GameScrapeResult{
					GameID: g.ID, Source: "igdb", Status: "matched",
					SourceID: strings.TrimPrefix(g.ScraperID, "igdb:"), LastAttemptAt: &t,
				})
				rows = append(rows, GameScrapeResult{
					GameID: g.ID, Source: "libretro", Status: "matched", LastAttemptAt: &t,
				})
				if hasHero[g.ID] {
					rows = append(rows, GameScrapeResult{
						GameID: g.ID, Source: "steamgriddb", Status: "matched", LastAttemptAt: &t,
					})
				}
			} else if g.ScraperID == "libretro" {
				rows = append(rows, GameScrapeResult{
					GameID: g.ID, Source: "igdb", Status: "not_found", LastAttemptAt: &t,
				})
				rows = append(rows, GameScrapeResult{
					GameID: g.ID, Source: "libretro", Status: "matched", LastAttemptAt: &t,
				})
				if hasHero[g.ID] {
					rows = append(rows, GameScrapeResult{
						GameID: g.ID, Source: "steamgriddb", Status: "matched", LastAttemptAt: &t,
					})
				}
			} else if g.ScraperID == "" {
				rows = append(rows, GameScrapeResult{
					GameID: g.ID, Source: "igdb", Status: "not_found", LastAttemptAt: &t,
				})
				rows = append(rows, GameScrapeResult{
					GameID: g.ID, Source: "libretro", Status: "not_found", LastAttemptAt: &t,
				})
			}
		}

		if len(rows) > 0 {
			if err := database.Clauses(clause.OnConflict{DoNothing: true}).
				CreateInBatches(rows, 200).Error; err != nil {
				return fmt.Errorf("inserting scrape result batch at offset %d: %w", offset, err)
			}
			totalInserted += len(rows)
		}
	}

	slog.Info("scrape-result migration completed", "rows_inserted", totalInserted)
	return nil
}
