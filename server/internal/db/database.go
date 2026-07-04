package db

import (
	"database/sql"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"time"

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

	// _foreign_keys=1 enables FK constraint enforcement for every
	// connection in the pool. SQLite has FK constraints disabled by
	// default for backwards compatibility — without this, our
	// OnDelete:CASCADE / SET NULL declarations on user-owned tables
	// (#971) would have no effect.
	dsn := dbPath + "?_foreign_keys=1"
	db, err := gorm.Open(sqlite.Open(dsn), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Warn),
	})
	if err != nil {
		return nil, fmt.Errorf("opening database: %w", err)
	}

	// SQLite performance pragmas:
	// - WAL mode: allows concurrent reads during writes, reduces fsync overhead.
	// - synchronous=NORMAL: safe with WAL — fsyncs on checkpoint, not every commit.
	//   (FULL fsyncs every commit, which is the default and causes I/O saturation during bulk writes.)
	// - journal_size_limit: cap WAL file growth at 64MB.
	// - busy_timeout: wait up to 5 seconds for locks instead of failing immediately.
	sqlDB, err := db.DB()
	if err != nil {
		return nil, fmt.Errorf("getting underlying sql.DB: %w", err)
	}
	for _, pragma := range []string{
		"PRAGMA journal_mode=WAL",
		"PRAGMA synchronous=NORMAL",
		"PRAGMA journal_size_limit=67108864",
		"PRAGMA busy_timeout=5000",
	} {
		if _, err := sqlDB.Exec(pragma); err != nil {
			slog.Warn("failed to set SQLite pragma", "pragma", pragma, "error", err)
		}
	}
	slog.Info("SQLite pragmas configured", "journal_mode", "WAL", "synchronous", "NORMAL")

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

	// Dedupe pre-existing rows that would violate uniqueness constraints
	// added by AutoMigrate below. Without this, AutoMigrate's CREATE
	// UNIQUE INDEX would fail on existing installations that already
	// have duplicates. See #970 (consoles.abbreviation), #973
	// (play_history.user_id+game_id).
	if err := dedupePreMigration(db); err != nil {
		return nil, fmt.Errorf("dedupe pre-migration: %w", err)
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
		&PlayTimeReportReceipt{},
		&RefreshToken{},
		&ServerSetting{},
		&Core{},
		&CorePlatformBinary{},
		&UserTitlePlatformPreference{},
		&ConsoleShaderPreference{},
		&ConsoleRenderScalePreference{},
		&ConsoleKeyMappingPreference{},
		&ConsoleSaveStatePolicy{},
		&GameSaveStatePolicy{},
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
		&SystemEventCategory{},
		&SystemEvent{},
		&CheatCode{},
		&GameSession{},
		&SessionSaveState{},
		&SessionSaveData{},
		&SessionSaveDirBundle{},
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
		// Persistent scrape queue
		&ScrapeJob{},
		&ScrapeQueueItem{},
		// Privacy: per-user block list (issue #1121)
		&Block{},
		// Federation (epic #1343): friend registry, pairing nonces, and the
		// observability exchange ledger (#1350).
		&FederationPeer{},
		&FederationInviteNonce{},
		&FederationExchange{},
		// Phase 2 (#1347): cached friend rollups for transitive re-serving.
		&FederationStatSnapshot{},
		// Phase 3 (#1348): cached friend catalogs for game discovery.
		&FederationCatalogSnapshot{},
		// Importing a connected-server game into the local library (#1350).
		&ImportJob{},
	)
	if err != nil {
		return nil, fmt.Errorf("running migrations: %w", err)
	}
	if err := MigrateDropUserEmail(db); err != nil {
		return nil, fmt.Errorf("dropping users.email: %w", err)
	}

	// Seed system event categories (security, operational).
	if err := seedSystemEventCategories(db); err != nil {
		return nil, fmt.Errorf("seeding system event categories: %w", err)
	}

	// Migrate old security_events table to system_events if it still exists.
	if err := migrateSecurityEventsToSystemEvents(db); err != nil {
		slog.Warn("security_events migration failed (may already be done)", "error", err)
	}

	// Defensive: ensure the token_blacklist token_hash index exists.
	// AutoMigrate creates indexes for new tables, but databases that predate
	// the uniqueIndex annotation can end up without it, causing the auth
	// middleware's lookup to fall back to a table scan on every request.
	if err := db.Exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_token_blacklists_token_hash ON token_blacklists(token_hash)").Error; err != nil {
		slog.Warn("failed to ensure token_blacklists token_hash index", "error", err)
	}
	if err := db.Exec("CREATE INDEX IF NOT EXISTS idx_token_blacklists_expires_at ON token_blacklists(expires_at)").Error; err != nil {
		slog.Warn("failed to ensure token_blacklists expires_at index", "error", err)
	}

	// Performance indexes for hot-path lookups. SQLite functional
	// indexes (CREATE INDEX ... (LOWER(col))) are required for the
	// many handlers that compare case-insensitively against
	// games.title / games.developer / games.publisher — without these
	// every `WHERE LOWER(col) = LOWER(?)` is a full table scan. See
	// also the plain indexes for join columns (scraper_id,
	// game_keywords.igdb_keyword_id) that GORM AutoMigrate doesn't
	// derive from the model struct tags because they're not declared
	// as standalone indexes on the column.
	//
	// All idempotent: IF NOT EXISTS means the second startup is a
	// no-op. Adding a new index here only costs the create-index
	// time on the next upgrade.
	for _, ddl := range []struct {
		name string
		stmt string
	}{
		// /api/games/{id}/similar matches cached IGDB titles to local
		// games via `WHERE LOWER(title) = LOWER(?)` (one query per
		// cached row). Same for several explore-developer / -publisher
		// handlers and the global top-rated join.
		{"idx_games_title_lower", "CREATE INDEX IF NOT EXISTS idx_games_title_lower ON games(LOWER(title))"},
		// /api/explore/developers/{name} and
		// /api/explore/developers/spotlight scope to a developer with
		// `WHERE LOWER(developer) = LOWER(?)`.
		{"idx_games_developer_lower", "CREATE INDEX IF NOT EXISTS idx_games_developer_lower ON games(LOWER(developer))"},
		// /api/explore/publishers/{name} — same pattern as developer.
		{"idx_games_publisher_lower", "CREATE INDEX IF NOT EXISTS idx_games_publisher_lower ON games(LOWER(publisher))"},
		// games.scraper_id ("igdb:<id>") is the join key for the
		// top-rated and discovery joins. The model has no `gorm:"index"`
		// tag on this column, so without this entry the joins were
		// scanning the full games table.
		{"idx_games_scraper_id", "CREATE INDEX IF NOT EXISTS idx_games_scraper_id ON games(scraper_id)"},
		// /api/keywords aggregates by igdb_keyword_id; the existing
		// unique compound index (game_id, igdb_keyword_id) doesn't help
		// because igdb_keyword_id isn't the leading column.
		{"idx_game_keywords_igdb_keyword_id", "CREATE INDEX IF NOT EXISTS idx_game_keywords_igdb_keyword_id ON game_keywords(igdb_keyword_id)"},
		// top_rated_games.name is matched case-insensitively against
		// games.title in the humaTopListByRating JOIN.
		{"idx_top_rated_games_name_lower", "CREATE INDEX IF NOT EXISTS idx_top_rated_games_name_lower ON top_rated_games(LOWER(name))"},
	} {
		if err := db.Exec(ddl.stmt).Error; err != nil {
			slog.Warn("failed to ensure performance index", "index", ddl.name, "error", err)
		}
	}

	// One-time backfill: games on consoles where CRC verification doesn't
	// apply (Amiga, demos, ScummVM) used to land with status "unverified"
	// because the scraper's skip-list didn't cover them. Flip those to
	// "not_applicable" so the verification badge stops showing on those
	// game detail pages without forcing a full rescrape.
	if err := db.Exec(`
		UPDATE games SET verification_status = 'not_applicable'
		WHERE verification_status = 'unverified'
		  AND console_id IN (
		    SELECT id FROM consoles WHERE abbreviation IN
		      ('AMIGA','ACD32','ADEMO','DDEMO','SCUMMVM')
		  )
	`).Error; err != nil {
		slog.Warn("verification_status backfill for skip-list consoles failed", "error", err)
	}

	// One-time backfill: clear EmulatorJSCore on consoles whose libretro
	// core has no upstream EmulatorJS build. The seed previously stamped
	// values like "freechaf" and "vecx" — EmulatorJS just tries to
	// download "<core>-thread-legacy-wasm.data" verbatim and 404s. The
	// regular seed loop only writes when the new value is non-empty, so
	// existing wrong values won't clear without an explicit UPDATE.
	if err := db.Exec(`
		UPDATE consoles SET emulator_js_core = ''
		WHERE abbreviation IN
		  ('CHAF','VEC','O2','INTV','GW','A800','A52','ATARIST','SGX')
	`).Error; err != nil {
		slog.Warn("emulator_js_core backfill for unsupported consoles failed", "error", err)
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

	// One-time cleanup: collapse the historical "started_playing" activity
	// flood (the play-time handler used to emit one event per 30s heartbeat)
	// down to one event per session. Idempotent — safe on every startup.
	if err := dedupeStartedPlayingEvents(db); err != nil {
		slog.Warn("started_playing activity dedupe failed", "error", err)
	}

	return db, nil
}

// StartedPlayingSessionGap is how long after the previous play-time report a
// new report must arrive to count as a new play session. Shared by the
// play-time handler (which emits a "started_playing" feed event only when a
// session starts) and dedupeStartedPlayingEvents. The player heartbeats play
// time every 30s, so this sits well above the heartbeat cadence and brief
// pauses, but low enough that resuming after a real break reads as new.
const StartedPlayingSessionGap = 10 * time.Minute

// dedupeStartedPlayingEvents collapses historical "started_playing" activity
// events down to one per play session. Before the heartbeat fix, every 30s
// play-time report created its own event, flooding the activity feed with
// hundreds of identical rows per session. Two events for the same
// (user, game) within StartedPlayingSessionGap of each other belong to the
// same session; only the first is kept. Idempotent: once collapsed, the
// survivors are more than a gap apart, so a second run deletes nothing.
func dedupeStartedPlayingEvents(database *gorm.DB) error {
	type evRow struct {
		ID        uint
		UserID    uint
		GameID    *uint
		CreatedAt time.Time
	}
	var rows []evRow
	if err := database.Model(&ActivityEvent{}).
		Where("event_type = ? AND game_id IS NOT NULL", "started_playing").
		Order("user_id, game_id, created_at").
		Find(&rows).Error; err != nil {
		return err
	}

	type sessionKey struct {
		userID uint
		gameID uint
	}
	lastSeen := make(map[sessionKey]time.Time)
	var toDelete []uint
	for _, r := range rows {
		k := sessionKey{r.UserID, *r.GameID}
		// A report within the gap of the previous one for the same game is a
		// continuation heartbeat, not a new session — drop it.
		if prev, ok := lastSeen[k]; ok && r.CreatedAt.Sub(prev) <= StartedPlayingSessionGap {
			toDelete = append(toDelete, r.ID)
		}
		lastSeen[k] = r.CreatedAt
	}
	if len(toDelete) == 0 {
		return nil
	}

	// Hard-delete (Unscoped) in batches to stay under SQLite's bound-parameter
	// limit on deployments with a large backlog.
	const batchSize = 500
	for i := 0; i < len(toDelete); i += batchSize {
		end := i + batchSize
		if end > len(toDelete) {
			end = len(toDelete)
		}
		if err := database.Unscoped().Where("id IN ?", toDelete[i:end]).
			Delete(&ActivityEvent{}).Error; err != nil {
			return err
		}
	}
	return nil
}

// MigrateToRelativePaths converts absolute game file paths to relative paths.
// On startup, any Game or GameDisc record whose FilePath starts with "/" is
// converted by stripping the matching gameDirs prefix. If no prefix matches
// (e.g. SPELA_GAME_DIRS changed), it falls back to detecting a known console
// FolderName in the path segments.
// dedupePreMigration removes rows that would violate uniqueness
// constraints added by the model schema. Runs before AutoMigrate so
// existing installations don't fail on `CREATE UNIQUE INDEX`.
//
// Idempotent: a fresh DB has no rows, so the queries are no-ops; a DB
// that's already been deduped has no duplicates left for the queries
// to find. Safe to call on every startup.
func dedupePreMigration(database *gorm.DB) error {
	// PlayHistory: at most one row per (user_id, game_id). Where
	// duplicates exist, sum playtime into the earliest row, take the
	// latest LastPlayed, and delete the rest. See #973.
	if database.Migrator().HasTable(&PlayHistory{}) {
		var groups []struct {
			UserID uint
			GameID uint
			Count  int
		}
		err := database.Model(&PlayHistory{}).
			Select("user_id, game_id, COUNT(*) as count").
			Where("deleted_at IS NULL").
			Group("user_id, game_id").
			Having("COUNT(*) > 1").
			Find(&groups).Error
		if err != nil {
			return fmt.Errorf("scan play_history dupes: %w", err)
		}
		for _, g := range groups {
			var rows []PlayHistory
			if err := database.Where("user_id = ? AND game_id = ?", g.UserID, g.GameID).
				Order("created_at ASC").Find(&rows).Error; err != nil {
				return fmt.Errorf("load play_history dupes: %w", err)
			}
			if len(rows) <= 1 {
				continue
			}
			keeper := rows[0]
			for _, dup := range rows[1:] {
				keeper.PlayTime += dup.PlayTime
				if dup.LastPlayed.After(keeper.LastPlayed) {
					keeper.LastPlayed = dup.LastPlayed
				}
				if err := database.Delete(&dup).Error; err != nil {
					return fmt.Errorf("delete play_history dupe id=%d: %w", dup.ID, err)
				}
			}
			if err := database.Save(&keeper).Error; err != nil {
				return fmt.Errorf("save merged play_history: %w", err)
			}
			slog.Info("merged duplicate play_history rows",
				"userId", g.UserID, "gameId", g.GameID, "merged", len(rows)-1)
		}
	}

	// Console: at most one row per abbreviation. Multiple rows with the
	// same abbreviation are a bug — keep the lowest-id row, delete the
	// rest. See #970.
	if database.Migrator().HasTable(&Console{}) {
		var dupes []struct {
			Abbreviation string
			Count        int
		}
		err := database.Model(&Console{}).
			Select("abbreviation, COUNT(*) as count").
			Where("deleted_at IS NULL").
			Group("abbreviation").
			Having("COUNT(*) > 1").
			Find(&dupes).Error
		if err != nil {
			return fmt.Errorf("scan console dupes: %w", err)
		}
		for _, d := range dupes {
			var rows []Console
			if err := database.Where("abbreviation = ?", d.Abbreviation).
				Order("id ASC").Find(&rows).Error; err != nil {
				return fmt.Errorf("load console dupes: %w", err)
			}
			for _, dup := range rows[1:] {
				if err := database.Delete(&dup).Error; err != nil {
					return fmt.Errorf("delete duplicate console id=%d: %w", dup.ID, err)
				}
				slog.Warn("deleted duplicate console row", "abbreviation", d.Abbreviation, "id", dup.ID)
			}
		}
	}

	return nil
}

func MigrateToRelativePaths(database *gorm.DB, gameDirs []string) error {
	// Console folder names are registry-owned (#1513); build the fallback
	// detection set from the registry rather than a now-dropped DB column.
	folderNames := make(map[string]bool)
	for _, spec := range ConsoleRegistry() {
		if spec.FolderName != "" {
			folderNames[spec.FolderName] = true
		}
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
			// Join with "/" (not filepath.Join) so the stored relative
			// path is canonical across host OSes — filepath.Join would
			// emit backslashes on Windows, breaking dedup idempotency.
			return strings.Join(parts[i:], "/")
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
// MigratePreserveOpenRegistration keeps self-service registration working for
// installs that existed before #1319 changed the default to closed. If the
// registration_enabled setting has never been configured AND at least one user
// already exists (the server was set up before this upgrade), seed the flag to
// "true" so the prior open-registration behaviour is preserved. Fresh installs
// (no users yet) are left untouched and default to closed (secure-by-default).
// Idempotent: a no-op once the setting exists.
func MigratePreserveOpenRegistration(database *gorm.DB) error {
	var setting ServerSetting
	err := database.Where("key = ?", "registration_enabled").First(&setting).Error
	if err == nil {
		return nil // already configured — respect the operator's choice
	}
	if !errors.Is(err, gorm.ErrRecordNotFound) {
		return fmt.Errorf("checking registration_enabled: %w", err)
	}

	var userCount int64
	if err := database.Model(&User{}).Count(&userCount).Error; err != nil {
		return fmt.Errorf("counting users: %w", err)
	}
	if userCount == 0 {
		return nil // fresh install — leave closed-by-default
	}
	return database.Create(&ServerSetting{Key: "registration_enabled", Value: "true"}).Error
}

// MigrateDropUserEmail removes the legacy users.email column and its GORM
// unique index. Spela accounts are username-based and the server never used
// email for delivery or recovery, so keeping historical addresses around is
// unnecessary data retention. Idempotent for fresh databases and repeat runs.
func MigrateDropUserEmail(database *gorm.DB) error {
	if !database.Migrator().HasTable(&User{}) {
		return nil
	}

	hasEmail, err := sqliteTableHasColumn(database, "users", "email")
	if err != nil {
		return fmt.Errorf("checking users.email: %w", err)
	}
	if !hasEmail {
		return nil
	}

	indexes, err := sqliteIndexesReferencingColumn(database, "users", "email")
	if err != nil {
		return fmt.Errorf("finding users.email indexes: %w", err)
	}
	for _, indexName := range indexes {
		if err := database.Exec("DROP INDEX IF EXISTS " + quoteSQLiteIdentifier(indexName)).Error; err != nil {
			return fmt.Errorf("dropping users.email index %q: %w", indexName, err)
		}
	}
	if err := database.Exec("ALTER TABLE users DROP COLUMN email").Error; err != nil {
		return fmt.Errorf("dropping users.email column: %w", err)
	}
	return nil
}

func sqliteIndexesReferencingColumn(database *gorm.DB, tableName string, columnName string) ([]string, error) {
	rows, err := database.Raw(
		`SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ? AND sql IS NOT NULL`,
		tableName,
	).Rows()
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var indexNames []string
	for rows.Next() {
		var name string
		if err := rows.Scan(&name); err != nil {
			return nil, err
		}
		indexNames = append(indexNames, name)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}

	var indexes []string
	for _, name := range indexNames {
		hasColumn, err := sqliteIndexHasColumn(database, name, columnName)
		if err != nil {
			return nil, err
		}
		if hasColumn {
			indexes = append(indexes, name)
		}
	}
	return indexes, nil
}

func sqliteIndexHasColumn(database *gorm.DB, indexName string, columnName string) (bool, error) {
	rows, err := database.Raw("PRAGMA index_info(" + quoteSQLiteIdentifier(indexName) + ")").Rows()
	if err != nil {
		return false, err
	}
	defer rows.Close()

	for rows.Next() {
		var seqno int
		var cid int
		var name sql.NullString
		if err := rows.Scan(&seqno, &cid, &name); err != nil {
			return false, err
		}
		if name.Valid && strings.EqualFold(name.String, columnName) {
			return true, nil
		}
	}
	if err := rows.Err(); err != nil {
		return false, err
	}
	return false, nil
}

func sqliteTableHasColumn(database *gorm.DB, tableName string, columnName string) (bool, error) {
	rows, err := database.Raw("PRAGMA table_info(" + quoteSQLiteIdentifier(tableName) + ")").Rows()
	if err != nil {
		return false, err
	}
	defer rows.Close()

	for rows.Next() {
		var cid int
		var name string
		var typ string
		var notNull int
		var defaultValue any
		var pk int
		if err := rows.Scan(&cid, &name, &typ, &notNull, &defaultValue, &pk); err != nil {
			return false, err
		}
		if name == columnName {
			return true, nil
		}
	}
	if err := rows.Err(); err != nil {
		return false, err
	}
	return false, nil
}

func quoteSQLiteIdentifier(name string) string {
	return `"` + strings.ReplaceAll(name, `"`, `""`) + `"`
}

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
	for _, spec := range consoleRegistry {
		c := spec.toConsole()
		wantPlayable := c.Playable
		var existing Console
		result := db.Where("abbreviation = ?", c.Abbreviation).First(&existing)
		if result.Error == gorm.ErrRecordNotFound {
			if err := db.Create(&c).Error; err != nil {
				return fmt.Errorf("seeding console %s: %w", spec.Abbreviation, err)
			}
			// GORM's Create mutates bool fields with default:true, setting them
			// to true even when the struct had false. Fix by comparing against
			// the original value saved before Create.
			if !wantPlayable {
				db.Exec("UPDATE consoles SET playable = 0 WHERE abbreviation = ?", c.Abbreviation)
			}
			slog.Info("seeded console", "name", spec.Name)
		} else {
			if c.EmulatorJSCore != "" && existing.EmulatorJSCore != c.EmulatorJSCore {
				db.Model(&existing).Update("emulator_js_core", c.EmulatorJSCore)
				slog.Info("backfilled EmulatorJSCore", "name", existing.Name, "old", existing.EmulatorJSCore, "new", c.EmulatorJSCore)
			}
			if c.DefaultCore != "" && existing.DefaultCore != c.DefaultCore {
				db.Model(&existing).Update("default_core", c.DefaultCore)
				slog.Info("backfilled DefaultCore", "name", existing.Name, "core", c.DefaultCore)
			}
			if !existing.SaveStateSupport && c.SaveStateSupport {
				db.Model(&existing).Update("save_state_support", true)
				slog.Info("backfilled SaveStateSupport", "name", existing.Name)
			}
			// Backfill SaveStatePolicy ONLY when the existing column is
			// empty (pre-#804 row that predates the column). Any non-
			// empty value is treated as authoritative — once an admin
			// overrides a tier (via direct SQL today, future admin UI
			// later), we must not fight that override on every boot.
			// The seed's value is the migration default, not a recurring
			// truth. See #804 phase 3.
			if c.SaveStatePolicy != "" && existing.SaveStatePolicy == "" {
				db.Model(&existing).Update("save_state_policy", string(c.SaveStatePolicy))
				slog.Info("backfilled SaveStatePolicy", "name", existing.Name, "policy", c.SaveStatePolicy)
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
		{Name: "parallel_n64", DisplayName: "ParaLLEl N64", Description: "Nintendo 64 emulator (default native core)", Platforms: "windows,linux,macos,android"},
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
		{Name: "azahar", DisplayName: "Azahar", Description: "Nintendo 3DS emulator (Citra successor)", Platforms: "windows,linux,macos,android"},
		{Name: "scummvm", DisplayName: "ScummVM", Description: "Point-and-click adventure game engine", Platforms: "windows,linux,macos,android"},
		{Name: "freechaf", DisplayName: "FreeChaF", Description: "Fairchild Channel F emulator", Platforms: "windows,linux,macos,android"},
		{Name: "o2em", DisplayName: "O2EM", Description: "Magnavox Odyssey 2 / Philips Videopac emulator", Platforms: "windows,linux,macos,android"},
		{Name: "freeintv", DisplayName: "FreeIntv", Description: "Mattel Intellivision emulator", Platforms: "windows,linux,macos,android"},
		{Name: "vecx", DisplayName: "vecx", Description: "GCE Vectrex emulator", Platforms: "windows,linux,macos,android"},
		{Name: "mednafen_supergrafx", DisplayName: "Beetle SuperGrafx", Description: "NEC PC Engine SuperGrafx emulator", Platforms: "windows,linux,macos,android"},
		{Name: "gw", DisplayName: "GW", Description: "Nintendo Game & Watch simulator", Platforms: "windows,linux,macos,android"},
		{Name: "hatari", DisplayName: "Hatari", Description: "Atari ST/STE/TT/Falcon emulator", Platforms: "windows,linux,macos,android"},
		{Name: "atari800", DisplayName: "Atari800", Description: "Atari 8-bit / 5200 emulator", Platforms: "windows,linux,macos,android"},
		{Name: "beetle_ngp", DisplayName: "Beetle NeoPop", Description: "Neo Geo Pocket / Color emulator (Mednafen NGP)", Platforms: "windows,linux,macos,android"},
		{Name: "beetle_pcfx", DisplayName: "Beetle PC-FX", Description: "NEC PC-FX emulator (Mednafen PC-FX)", Platforms: "windows,linux,macos,android"},
		{Name: "beetle_vb", DisplayName: "Beetle VB", Description: "Virtual Boy emulator (Mednafen VB)", Platforms: "windows,linux,macos,android"},
		{Name: "beetle_wswan", DisplayName: "Beetle WonderSwan", Description: "WonderSwan / WonderSwan Color emulator (Mednafen WSwan)", Platforms: "windows,linux,macos,android"},
		{Name: "bluemsx", DisplayName: "blueMSX", Description: "MSX / MSX2 / MSX2+ / Turbo-R emulator", Platforms: "windows,linux,macos,android"},
		{Name: "dosbox_pure", DisplayName: "DOSBox Pure", Description: "MS-DOS emulator (enhanced DOSBox fork)", Platforms: "windows,linux,macos,android"},
		{Name: "fbneo", DisplayName: "FinalBurn Neo", Description: "Arcade / Neo Geo emulator", Platforms: "windows,linux,macos,android"},
		{Name: "flycast", DisplayName: "Flycast", Description: "Sega Dreamcast / Naomi / Atomiswave emulator", Platforms: "windows,linux,macos,android"},
		{Name: "handy", DisplayName: "Handy", Description: "Atari Lynx emulator", Platforms: "windows,linux,macos,android"},
		{Name: "mame2003_plus", DisplayName: "MAME 2003-Plus", Description: "Arcade emulator (MAME 0.78 + fixes)", Platforms: "windows,linux,macos,android"},
		{Name: "mednafen_pce", DisplayName: "Beetle PCE", Description: "NEC PC Engine / TurboGrafx-16 / CD emulator (Mednafen PCE)", Platforms: "windows,linux,macos,android"},
		{Name: "play", DisplayName: "Play!", Description: "PlayStation 2 emulator", Platforms: "windows,linux,macos,android"},
		{Name: "pokemini", DisplayName: "PokeMini", Description: "Pokemon Mini emulator", Platforms: "windows,linux,macos,android"},
		{Name: "ppsspp", DisplayName: "PPSSPP", Description: "Sony PlayStation Portable emulator", Platforms: "windows,linux,macos,android"},
		{Name: "prosystem", DisplayName: "ProSystem", Description: "Atari 7800 ProSystem emulator", Platforms: "windows,linux,macos,android"},
		{Name: "stella", DisplayName: "Stella", Description: "Atari 2600 VCS emulator", Platforms: "windows,linux,macos,android"},
		{Name: "vice_x64sc", DisplayName: "VICE x64sc", Description: "Commodore 64 emulator (cycle-accurate)", Platforms: "windows,linux,macos,android"},
		{Name: "fuse", DisplayName: "Fuse", Description: "Sinclair ZX Spectrum emulator (Fuse — Free Unix Spectrum Emulator)", Platforms: "windows,linux,macos,android"},
		{Name: "cap32", DisplayName: "Caprice32", Description: "Amstrad CPC emulator", Platforms: "windows,linux,macos,android"},
		{Name: "px68k", DisplayName: "PX68k", Description: "Sharp X68000 emulator (requires IPLROM30.DAT + CGROM.DAT BIOS)", Platforms: "windows,linux,macos,android"},
		{Name: "tic80", DisplayName: "TIC-80", Description: "TIC-80 fantasy console (no BIOS required)", Platforms: "windows,linux,macos,android"},
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
			if existing.CustomDownloadURL == "" && c.CustomDownloadURL != "" {
				db.Model(&existing).Update("download_url", c.CustomDownloadURL)
				slog.Info("backfilled DownloadURL", "name", existing.Name)
			}
			if existing.Version == "" && c.Version != "" {
				db.Model(&existing).Update("version", c.Version)
			}
		}
	}

	return nil
}

// MigrateAzaharToBuildbot clears the legacy CustomDownloadURL / Version
// override on the azahar core row so downloads fall back to the libretro
// buildbot nightly endpoint. See #1187.
//
// Pre-#1187 the row was seeded with a GitHub release URL pinned to
// 2125.0-alpha4 (later 2125.0.1). That URL 404'd on Android arm64-v8a
// — the only Android ABI we support for 3DS — and shipped an Azahar
// build with a Vulkan tooling_info crash on Apple Silicon. Buildbot
// publishes fresh nightlies that fix both. SeedCores only backfilled
// empty rows, so live deployments never picked up the corrected URL;
// this migration force-clears the override so every existing row joins
// the buildbot path.
//
// Idempotent: once the row is buildbot-default (both fields empty),
// this is a no-op.
func MigrateAzaharToBuildbot(database *gorm.DB) error {
	var azahar Core
	err := database.Where("name = ?", "azahar").First(&azahar).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil
	}
	if err != nil {
		return fmt.Errorf("loading azahar core: %w", err)
	}
	if azahar.CustomDownloadURL == "" && azahar.Version == "" {
		return nil
	}
	if err := database.Model(&azahar).Updates(map[string]interface{}{
		"download_url": "",
		"version":      "",
	}).Error; err != nil {
		return fmt.Errorf("clearing azahar override: %w", err)
	}
	slog.Info("migrated azahar core to buildbot default",
		"previous_url", azahar.CustomDownloadURL,
		"previous_version", azahar.Version)
	return nil
}

// scrapeResultsMigratedKey is the ServerSetting sentinel that marks
// MigrateScrapeResults as fully completed. Pre-#972 the migration
// gated on `count > 0` in game_scrape_results, which was unsafe: a
// crash partway through (OOM on a 50k-game library, deploy
// interruption) left games in unprocessed batches without rows, and
// the next restart saw count > 0 and skipped the rest permanently.
const scrapeResultsMigratedKey = "scrape_results_migrated"

// MigrateScrapeResults backfills GameScrapeResult rows from the legacy
// ScraperID / ScrapeAttempts fields already present on each Game row.
// Idempotent: gated on a ServerSetting sentinel so re-runs are no-ops
// after the first successful completion. Crash-safe: the sentinel is
// only written after the full loop completes, so a crash mid-loop
// just resumes from where it left off on the next start (the
// OnConflict{DoNothing:true} on inserts makes re-processing of
// already-done rows a no-op).
func MigrateScrapeResults(database *gorm.DB) error {
	var sentinel ServerSetting
	if err := database.Where("key = ?", scrapeResultsMigratedKey).First(&sentinel).Error; err == nil {
		// Sentinel present → migration already completed.
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

	// Write sentinel only after the full loop completes. Idempotent:
	// the ServerSetting primary key is the unique key, so re-creating
	// the same row is a no-op (or fails harmlessly on a duplicate
	// insert; we ignore the error).
	if err := database.Save(&ServerSetting{Key: scrapeResultsMigratedKey, Value: "true"}).Error; err != nil {
		slog.Warn("failed to write scrape-results-migrated sentinel — migration will retry on next start (no harm)", "error", err)
	}
	return nil
}

// seedSystemEventCategories inserts the code-defined categories if they don't
// already exist. Called on startup after AutoMigrate.
func seedSystemEventCategories(database *gorm.DB) error {
	categories := []SystemEventCategory{
		{Code: CategorySecurity, Name: "Security"},
		{Code: CategoryOperational, Name: "Operational"},
	}
	for _, cat := range categories {
		if err := database.Where("code = ?", cat.Code).FirstOrCreate(&cat).Error; err != nil {
			return fmt.Errorf("seeding category %q: %w", cat.Code, err)
		}
	}
	return nil
}

// migrateSecurityEventsToSystemEvents renames the old security_events table
// and backfills category_id for existing rows. Idempotent — skips if the old
// table no longer exists.
func migrateSecurityEventsToSystemEvents(database *gorm.DB) error {
	if !database.Migrator().HasTable("security_events") {
		return nil
	}

	var cat SystemEventCategory
	if err := database.Where("code = ?", CategorySecurity).First(&cat).Error; err != nil {
		return fmt.Errorf("finding security category for migration: %w", err)
	}

	err := database.Exec(
		"INSERT INTO system_events (id, created_at, category_id, event_type, reason, username, username_lower, user_id, ip, path, metadata) "+
			"SELECT id, created_at, ?, event_type, reason, username, username_lower, user_id, ip, path, metadata FROM security_events",
		cat.ID,
	).Error
	if err != nil {
		return fmt.Errorf("migrating security_events rows: %w", err)
	}

	if err := database.Exec("DROP TABLE security_events").Error; err != nil {
		return fmt.Errorf("dropping security_events: %w", err)
	}

	slog.Info("migrated security_events to system_events")
	return nil
}
