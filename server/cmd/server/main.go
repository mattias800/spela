package main

import (
	"context"
	"log/slog"
	"net/http"
	_ "net/http/pprof" // profiling endpoint at /debug/pprof/
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/spela/server/internal/api"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/bios"
	"github.com/spela/server/internal/cheats"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/retroachievements"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	"github.com/spela/server/internal/websocket"
)

// version is set at build time via -ldflags "-X main.version=..."
var version = "dev"

func main() {
	// Start pprof profiling server on :6060
	go func() {
		slog.Info("pprof profiling server started on :6060")
		if err := http.ListenAndServe(":6060", nil); err != nil {
			slog.Warn("pprof server failed", "error", err)
		}
	}()

	slog.Info("Spela", "version", version)
	// Configuration from environment variables with sensible defaults
	port := getEnv("SPELA_PORT", "8080")
	dbPath := getEnv("SPELA_DB_PATH", "spela.db")
	jwtSecret := getEnv("SPELA_JWT_SECRET", "change-me-in-production")
	gameDirsRaw := getEnv("SPELA_GAME_DIRS", "./games")
	saveDir := getEnv("SPELA_SAVE_DIR", "./saves")
	coreDir := getEnv("SPELA_CORE_DIR", "./cores")
	imageDir := getEnv("SPELA_IMAGE_DIR", "./images")
	biosDir := getEnv("SPELA_BIOS_DIR", "./bios")
	datDir := getEnv("SPELA_DAT_DIR", "./dats")
	wsOriginsRaw := getEnv("SPELA_WS_ORIGINS", "")
	corsOriginsRaw := getEnv("SPELA_CORS_ORIGINS", "")
	encryptionKeyRaw := os.Getenv("SPELA_ENCRYPTION_KEY")
	frontendDir := os.Getenv("SPELA_FRONTEND_DIR")
	challengeRateLimitRaw := getEnv("SPELA_CHALLENGE_RATE_LIMIT_SEC", "30")
	testMode := os.Getenv("SPELA_TEST_MODE") == "true"

	gameDirs := strings.Split(gameDirsRaw, ",")
	var wsOrigins []string
	if wsOriginsRaw != "" {
		wsOrigins = strings.Split(wsOriginsRaw, ",")
	}
	var corsOrigins []string
	if corsOriginsRaw != "" {
		corsOrigins = strings.Split(corsOriginsRaw, ",")
	}
	challengeRateLimit, err := strconv.Atoi(challengeRateLimitRaw)
	if err != nil {
		challengeRateLimit = 30
	}

	// Initialize structured logging
	slog.SetDefault(slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})))

	// Enforce secure JWT secret — always required, regardless of GIN_MODE.
	// Self-hosted users may not know to set GIN_MODE=release, so we never
	// allow a known default or short secret to protect against token forgery.
	// In test mode, skip this check to allow known test secrets.
	if !testMode {
		if jwtSecret == "change-me-in-production" {
			slog.Error("FATAL: using default JWT secret; set SPELA_JWT_SECRET to a random value (>= 32 chars)")
			os.Exit(1)
		}
		if len(jwtSecret) < 32 {
			slog.Error("FATAL: JWT secret must be at least 32 characters; set a stronger SPELA_JWT_SECRET")
			os.Exit(1)
		}
	}

	// Derive or use explicit encryption key.
	// A separate key is strongly recommended so that JWT secret rotation
	// does not require re-encrypting stored data.
	var encryptionKey []byte
	if encryptionKeyRaw != "" {
		encryptionKey = []byte(encryptionKeyRaw)
		if err := auth.ValidateEncryptionKey(encryptionKey); err != nil {
			slog.Error("FATAL: SPELA_ENCRYPTION_KEY has invalid length", "error", err)
			os.Exit(1)
		}
	} else {
		if os.Getenv("GIN_MODE") == "release" {
			slog.Error("FATAL: SPELA_ENCRYPTION_KEY must be set in release mode (separate from JWT secret)")
			os.Exit(1)
		}
		slog.Warn("SPELA_ENCRYPTION_KEY not set; deriving from JWT secret (set a separate key for production)")
	}

	if len(corsOrigins) == 0 {
		slog.Info("CORS: same-origin only (set SPELA_CORS_ORIGINS to allow cross-origin requests)")
	}

	slog.Info("starting Spela server", "port", port, "gameDirs", gameDirs)

	// Initialize database
	database, err := db.Initialize(dbPath)
	if err != nil {
		slog.Error("failed to initialize database", "error", err)
		os.Exit(1)
	}
	if err := db.SeedConsoles(database); err != nil {
		slog.Error("failed to seed consoles", "error", err)
		os.Exit(1)
	}
	if err := db.SeedMediaTypeCategories(database); err != nil {
		slog.Error("failed to seed media type categories", "error", err)
		os.Exit(1)
	}
	if err := db.SeedMediaTypes(database); err != nil {
		slog.Error("failed to seed media types", "error", err)
		os.Exit(1)
	}
	if err := db.SeedHardwareMakers(database); err != nil {
		slog.Error("failed to seed hardware makers", "error", err)
		os.Exit(1)
	}
	if err := db.SeedConsoleMetadata(database); err != nil {
		slog.Error("failed to seed console metadata", "error", err)
		os.Exit(1)
	}
	if err := db.SeedCores(database); err != nil {
		slog.Error("failed to seed cores", "error", err)
		os.Exit(1)
	}

	// Backfill per-source scrape results from legacy ScraperID field (one-time on upgrade)
	if err := db.MigrateScrapeResults(database); err != nil {
		slog.Warn("failed to migrate scrape results", "error", err)
	}

	// Migrate absolute file paths to relative (one-time on upgrade)
	if err := db.MigrateToRelativePaths(database, gameDirs); err != nil {
		slog.Warn("failed to migrate game paths", "error", err)
	}

	// Deduplicate games with identical file paths (merges user data into keeper)
	if err := db.DeduplicateGames(database); err != nil {
		slog.Warn("failed to deduplicate games", "error", err)
	}

	// Create sessions for existing shared sessions (one-time on upgrade)
	if err := db.MigrateSharedSessions(database); err != nil {
		slog.Warn("failed to migrate shared sessions", "error", err)
	}

	// Create ES-DE console subdirectories in game dirs
	if err := scanner.CreateConsoleFolders(database, gameDirs); err != nil {
		slog.Warn("failed to create console folders", "error", err)
	}

	// Initialize storage
	store, err := storage.NewStorage(saveDir, coreDir, imageDir, biosDir)
	if err != nil {
		slog.Error("failed to initialize storage", "error", err)
		os.Exit(1)
	}

	// Initialize scanner
	gameScanner := scanner.NewScanner(database, gameDirs)

	// Initialize scraper
	metaScraper := scraper.NewScraper(database, store, datDir, gameDirs)
	go metaScraper.DATCache.RefreshAll()

	// Configure scraper clients synchronously before starting the worker,
	// so resumed scrape jobs have access to IGDB/SteamGridDB/RA immediately.
	{
		clientID := os.Getenv("SPELA_IGDB_CLIENT_ID")
		clientSecret := os.Getenv("SPELA_IGDB_CLIENT_SECRET")
		if clientID == "" || clientSecret == "" {
			var settings []db.ServerSetting
			database.Where("key IN ?", []string{"igdb_client_id", "igdb_client_secret"}).Find(&settings)
			for _, s := range settings {
				switch s.Key {
				case "igdb_client_id":
					clientID = s.Value
				case "igdb_client_secret":
					clientSecret = s.Value
				}
			}
		}
		if clientID != "" && clientSecret != "" {
			metaScraper.IGDBClient = igdb.NewClient(clientID, clientSecret)
		}

		raAPIKey := os.Getenv("SPELA_RA_API_KEY")
		if raAPIKey == "" {
			var setting db.ServerSetting
			database.Where("key = ?", "ra_api_key").First(&setting)
			raAPIKey = setting.Value
		}
		if raAPIKey != "" {
			metaScraper.RAClient = retroachievements.NewRAClient()
			metaScraper.RAAPIKey = raAPIKey
			slog.Info("RA API key configured for achievement scraping")
		}

		if sgdbKey := os.Getenv("SPELA_STEAMGRIDDB_API_KEY"); sgdbKey != "" {
			metaScraper.ConfigureSteamGridDB(sgdbKey)
		} else {
			var setting db.ServerSetting
			if err := database.Where("key = ?", "steamgriddb_api_key").First(&setting).Error; err == nil && setting.Value != "" {
				metaScraper.ConfigureSteamGridDB(setting.Value)
			}
		}
	}

	// Context for background workers — cancelled on shutdown
	workerCtx, workerCancel := context.WithCancel(context.Background())
	defer workerCancel()

	// Start periodic expired refresh token cleanup (every hour)
	api.StartTokenCleanup(database, 1*time.Hour)

	// Start the core-history prune job (runs once at startup + every 24h).
	// See #555 Phase 3 — keeps the last 3 versions per core and everything
	// from the last 90 days; deletes the rest.
	(&api.CoreHandler{DB: database, CoreDir: coreDir}).StartCoreHistoryPruner(workerCtx)

	// Auto-download missing BIOS files at startup (non-blocking)
	bios.StartAutoDownload(store.BiosDir, database)

	// Auto-import cheat codes on first boot (non-blocking)
	cheats.StartAutoImport(database)

	// Derive WebSocket origins: prefer explicit WS origins, fall back to CORS origins.
	// This ensures WebSocket origin checking is not more permissive than CORS by default.
	effectiveWSOrigins := wsOrigins
	if len(effectiveWSOrigins) == 0 && len(corsOrigins) > 0 {
		effectiveWSOrigins = corsOrigins
	}

	// Initialize WebSocket hub
	hub := websocket.NewHub(effectiveWSOrigins)
	go hub.Run()

	// Initialize Netplay WebSocket hub
	netplayHub := websocket.NewNetplayHub(effectiveWSOrigins)
	netplayHub.StartCleanup(database)

	// Log frontend serving mode
	if frontendDir != "" {
		slog.Info("serving frontend from disk", "dir", frontendDir)
	}

	// Create router
	router, _ := api.NewRouter(api.Config{
		DB:            database,
		JWTSecret:     jwtSecret,
		EncryptionKey: encryptionKey,
		GameDirs:      gameDirs,
		Storage:       store,
		Scanner:       gameScanner,
		Scraper:       metaScraper,
		Hub:           hub,
		NetplayHub:    netplayHub,
		CoreDir:       coreDir,
		FrontendDir:   frontendDir,
		CORSOrigins:                  corsOrigins,
		ChallengeAttemptRateLimitSec: challengeRateLimit,
		Version:                      version,
		TestMode:                     testMode,
	})

	// Start background scrape worker (must start before auto-scan goroutine
	// so any enqueued items get processed)
	scrapeWorker := scraper.NewScrapeWorker(database, metaScraper.Queue, metaScraper, hub, func() {
		if err := scanner.RecomputeGroupKeys(database, nil); err != nil {
			slog.Warn("post-scrape group key recompute failed", "error", err)
		}
		if err := scanner.GroupAndElectPrimaries(database); err != nil {
			slog.Warn("post-scrape regrouping failed", "error", err)
		}
		if merged, err := scanner.MergeGroupsByIGDBID(database); err != nil {
			slog.Warn("post-scrape IGDB group merge failed", "error", err)
		} else if merged > 0 {
			slog.Info("post-scrape IGDB group merge complete", "merged", merged)
		}
	})
	go scrapeWorker.Run(workerCtx)

	// Rebuild variant groups from scratch on startup. This is idempotent —
	// the result depends only on filenames and IGDB IDs, not on existing
	// group state. Fixes any corruption from previous runs.
	go func() {
		broadcastGrouping := func(message string, current, total int) {
			hub.Broadcast(websocket.Event{Type: "grouping_progress", Payload: map[string]interface{}{
				"message": message,
				"current": current,
				"total":   total,
			}})
		}

		broadcastGrouping("Recomputing group keys...", 0, 0)
		if err := scanner.RecomputeGroupKeys(database, broadcastGrouping); err != nil {
			slog.Warn("startup group key recompute failed", "error", err)
		}
		broadcastGrouping("Electing primary variants...", 0, 0)
		if err := scanner.GroupAndElectPrimaries(database); err != nil {
			slog.Warn("startup regrouping failed", "error", err)
		}
		broadcastGrouping("Merging cross-language variants...", 0, 0)
		if merged, err := scanner.MergeGroupsByIGDBID(database); err != nil {
			slog.Warn("startup IGDB group merge failed", "error", err)
		} else if merged > 0 {
			slog.Info("startup IGDB group merge complete", "merged", merged)
		}
		hub.Broadcast(websocket.Event{Type: "grouping_complete", Payload: nil})
		slog.Info("startup variant grouping complete")
	}()

	// Auto-scan game library on startup (non-blocking).
	// Uses the same scan flow as the manual "Scan library" button so progress
	// is visible in the web UI via WebSocket events.
	go func() {
		if !gameScanner.TryStartScan() {
			slog.Warn("skipping startup scan: scan already in progress")
			return
		}

		hub.Broadcast(websocket.Event{Type: "scan_started", Payload: nil})
		result, scanErr := gameScanner.Scan(func(p scanner.ScanProgress) {
			gameScanner.SetScanProgress(&p)
			hub.Broadcast(websocket.Event{Type: "scan_progress", Payload: p})
		})

		// Finish the scan BEFORE starting scraping, so the scan status API
		// reports active=false immediately. Otherwise the web UI shows the
		// stale "Grouping variants..." message during the entire IGDB scrape.
		gameScanner.FinishScan()

		if scanErr != nil {
			slog.Error("startup library scan failed", "error", scanErr)
			hub.Broadcast(websocket.Event{Type: "scan_error", Payload: map[string]string{"error": "startup scan failed"}})
			return
		}

		hub.Broadcast(websocket.Event{Type: "scan_complete", Payload: map[string]interface{}{
			"newGames":     result.NewGames,
			"updatedGames": result.UpdatedGames,
			"removedGames": result.RemovedGames,
			"totalGames":   result.TotalGames,
		}})
		slog.Info("startup scan complete",
			"new", result.NewGames,
			"updated", result.UpdatedGames,
			"removed", result.RemovedGames,
			"total", result.TotalGames,
		)

		if result.NewGames > 0 && metaScraper.IsIGDBConfigured() {
			// Collect new (unscraped) game IDs
			var gameIDs []uint
			database.Model(&db.Game{}).
				Where("scraper_id = '' OR scraper_id IS NULL").
				Pluck("id", &gameIDs)

			if len(gameIDs) > 0 {
				// Use merge if there's an existing job (e.g., resuming after restart)
				activeJob, _ := metaScraper.Queue.GetActiveJob()
				if activeJob != nil {
					added, mergeErr := metaScraper.Queue.MergeGames(activeJob.ID, gameIDs)
					if mergeErr != nil {
						slog.Error("failed to merge new games into active scrape job", "error", mergeErr)
					} else if added > 0 {
						slog.Info("merged new games into active scrape job", "added", added, "jobId", activeJob.ID)
					}
				} else {
					job, jobErr := metaScraper.Queue.CreateJob("new", "", "", "", len(gameIDs))
					if jobErr != nil {
						slog.Error("failed to create startup scrape job", "error", jobErr)
					} else {
						if enqErr := metaScraper.Queue.EnqueueGames(job.ID, gameIDs, 0); enqErr != nil {
							slog.Error("failed to enqueue startup scrape games", "error", enqErr)
						} else {
							slog.Info("startup scrape job created", "jobId", job.ID, "total", len(gameIDs))
							hub.Broadcast(websocket.Event{Type: "scrape_started", Payload: map[string]interface{}{
								"jobId": job.ID,
								"total": len(gameIDs),
								"mode":  "new",
							}})
						}
					}
				}
			}
		}

		// Standalone RA achievement scraping for games that weren't covered by the scrape queue
		// (e.g., when IGDB is not configured but RA is, or for existing games)
		if metaScraper.IsRAConfigured() {
			slog.Info("scraping RetroAchievements data...")
			raSuccesses, raTotal, raErr := metaScraper.ScrapeRAAchievements(context.Background(), nil)
			if raErr != nil {
				slog.Error("RA achievement scraping failed", "error", raErr)
			} else if raSuccesses > 0 {
				slog.Info("RA achievement scraping complete", "successes", raSuccesses, "total", raTotal)
			}
		}
	}()

	slog.Info("server listening", "port", port)
	srv := &http.Server{
		Addr:         ":" + port,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 120 * time.Second, // generous for large file downloads
		IdleTimeout:  120 * time.Second,
	}

	// Graceful shutdown: listen for SIGINT/SIGTERM and drain in-flight requests
	// before exiting. This prevents data loss (e.g. interrupted save uploads)
	// when the server is stopped or restarted.
	shutdownCh := make(chan os.Signal, 1)
	signal.Notify(shutdownCh, os.Interrupt, syscall.SIGTERM)
	go func() {
		sig := <-shutdownCh
		slog.Info("shutdown signal received, draining connections", "signal", sig)
		// Stop the scrape worker (finishes current game, then exits)
		workerCancel()
		ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer cancel()
		if err := srv.Shutdown(ctx); err != nil {
			slog.Error("graceful shutdown failed", "error", err)
		}
	}()

	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		slog.Error("server failed", "error", err)
		os.Exit(1)
	}
	slog.Info("server stopped gracefully")
}

func getEnv(key, defaultVal string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return defaultVal
}
