package api

import (
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// Config holds configuration for the API router.
type Config struct {
	DB            *gorm.DB
	JWTSecret     string
	EncryptionKey []byte // separate AES key; falls back to DeriveEncryptionKey(JWTSecret) if nil
	GameDirs      []string
	Storage       *storage.Storage
	Scanner       *scanner.Scanner
	Scraper       *scraper.Scraper
	Hub           *ws.Hub
	NetplayHub    *ws.NetplayHub
	CoreDir       string
	FrontendDir   string // path to Vite dist/ output; empty = disabled
	CORSOrigins                  []string
	RAClient                     *retroachievements.RAClient // optional; defaults to production RA client
	ChallengeAttemptRateLimitSec int                         // 0 = disabled; default 30 in production
	Version                      string
	TestMode                     bool // when true, registers POST /api/test/reset for E2E test isolation
}

// NewRouter creates and configures the Gin router with all endpoints.
func NewRouter(cfg Config) *gin.Engine {
	r := gin.Default()

	// Only trust proxies on private/loopback networks (Docker internal, localhost).
	r.SetTrustedProxies([]string{"10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "::1/128"})

	// Security headers
	r.Use(func(c *gin.Context) {
		// COOP/COEP — required for SharedArrayBuffer (EmulatorJS threaded cores)
		c.Header("Cross-Origin-Opener-Policy", "same-origin")
		c.Header("Cross-Origin-Embedder-Policy", "credentialless")
		// Prevent MIME type sniffing
		c.Header("X-Content-Type-Options", "nosniff")
		// Prevent clickjacking — allow same-origin framing for the emulator iframe
		c.Header("X-Frame-Options", "SAMEORIGIN")
		// Minimal referrer info to external sites
		c.Header("Referrer-Policy", "strict-origin-when-cross-origin")
		// Content Security Policy — permissive enough for EmulatorJS (blob workers,
		// WASM eval, blob URL fetches for cores, CDN version check)
		c.Header("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-eval' blob:; style-src 'self' 'unsafe-inline'; img-src 'self' https: data:; connect-src 'self' wss: ws: blob: https://cdn.emulatorjs.org; worker-src 'self' blob:; frame-ancestors 'self'")
		// HSTS — instruct browsers to always use HTTPS. Safe even behind a reverse
		// proxy; browsers only honour this header on HTTPS responses.
		c.Header("Strict-Transport-Security", "max-age=63072000; includeSubDomains")
		// Deny access to sensitive browser features
		c.Header("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()")
		c.Next()
	})

	// Global request body size limit for JSON endpoints (1 MB).
	// File upload endpoints (multipart) are excluded and use their own limits.
	r.Use(BodySizeLimiter(MaxJSONBodySize))

	// CORS - configurable origins; empty = reject all cross-origin requests (no CORS headers sent)
	corsOrigins := cfg.CORSOrigins
	if len(corsOrigins) > 0 {
		allowCreds := true
		for _, o := range corsOrigins {
			if o == "*" {
				allowCreds = false
				break
			}
		}
		r.Use(cors.New(cors.Config{
			AllowOrigins:     corsOrigins,
			AllowMethods:     []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
			AllowHeaders:     []string{"Origin", "Content-Type", "Authorization", "X-Turn-Token"},
			ExposeHeaders:    []string{"Content-Length"},
			AllowCredentials: allowCreds,
			MaxAge:           12 * time.Hour,
		}))
	}

	// Serve images — save screenshots require auth + ownership; everything else is public
	imageHandler := &ImageHandler{ImageDir: cfg.Storage.ImageDir, JWTSecret: cfg.JWTSecret, DB: cfg.DB}
	r.GET("/api/images/*filepath", imageHandler.ServeImage)

	// Health check (public, no auth required)
	r.GET("/api/health", func(c *gin.Context) {
		status := "ok"
		sqlDB, err := cfg.DB.DB()
		if err != nil {
			status = "degraded"
		} else if err := sqlDB.Ping(); err != nil {
			status = "degraded"
		}

		c.JSON(200, gin.H{
			"status":  status,
			"version": cfg.Version,
		})
	})

	// Rate limiter for auth endpoints (login, register, setup)
	authLimiter := NewRateLimiter(120, time.Minute)

	// Separate higher-limit rate limiter for token refresh (called frequently during normal use)
	refreshLimiter := NewRateLimiter(300, time.Minute)

	// Rate limiter for file download endpoints (prevents bandwidth abuse)
	downloadLimiter := NewRateLimiter(30, time.Minute)

	// Rate limiter for file upload endpoints (prevents storage abuse)
	uploadLimiter := NewRateLimiter(30, time.Minute)

	// Global per-user rate limiter for authenticated API endpoints (prevents abuse from compromised accounts).
	// In test mode, use a much higher limit to avoid spurious 429s during E2E tests.
	userRateLimit := 300
	if cfg.TestMode {
		userRateLimit = 10000
	}
	userLimiter := NewRateLimiter(userRateLimit, time.Minute)

	// Handlers
	authHandler := &AuthHandler{DB: cfg.DB, JWTSecret: cfg.JWTSecret}
	gameHandler := &GameHandler{
		DB:       cfg.DB,
		Scanner:  cfg.Scanner,
		Storage:  cfg.Storage,
		Hub:      cfg.Hub,
		GameDirs: cfg.GameDirs,
		Scraper:  cfg.Scraper,
	}
	consoleHandler := &ConsoleHandler{DB: cfg.DB, Storage: cfg.Storage, Scraper: cfg.Scraper}
	userHandler := &UserHandler{DB: cfg.DB, Hub: cfg.Hub, JWTSecret: cfg.JWTSecret}
	deviceHandler := &DeviceHandler{DB: cfg.DB}
	statsHandler := &StatsHandler{DB: cfg.DB}
	adminHandler := &AdminHandler{DB: cfg.DB, Scraper: cfg.Scraper, Hub: cfg.Hub, Storage: cfg.Storage}
	igdbHandler := &IGDBHandler{DB: cfg.DB}
	coreHandler := &CoreHandler{DB: cfg.DB, CoreDir: cfg.CoreDir}
	raClient := cfg.RAClient
	if raClient == nil {
		raClient = retroachievements.NewRAClient()
	}
	encryptionKey := cfg.EncryptionKey
	if len(encryptionKey) == 0 {
		encryptionKey = auth.DeriveEncryptionKey(cfg.JWTSecret)
	}
	socialHandler := &SocialHandler{DB: cfg.DB, Hub: cfg.Hub}
	ratingHandler := &RatingHandler{DB: cfg.DB, Hub: cfg.Hub}
	sharedSaveHandler := &SharedSaveHandler{DB: cfg.DB, Storage: cfg.Storage, Hub: cfg.Hub}
	collectionHandler := &CollectionHandler{DB: cfg.DB, Hub: cfg.Hub}
	playLaterHandler := &PlayLaterHandler{DB: cfg.DB, Hub: cfg.Hub}
	sharedSessionHandler := &SharedSessionHandler{DB: cfg.DB, Storage: cfg.Storage, Hub: cfg.Hub}
	netplayHandler := &NetplayHandler{DB: cfg.DB, Hub: cfg.Hub, NetplayHub: cfg.NetplayHub}
	raHandler := &RAHandler{DB: cfg.DB, RAClient: raClient, GameDir: cfg.GameDirs[0], EncryptionKey: encryptionKey}
	biosHandler := &BiosHandler{Storage: cfg.Storage, DB: cfg.DB, Hub: cfg.Hub}
	gameKeyMappingHandler := &GameKeyMappingHandler{DB: cfg.DB}
	challengeHandler := NewChallengeHandler(cfg.DB, cfg.Storage, cfg.Hub)
	challengeHandler.AttemptRateLimitSeconds = cfg.ChallengeAttemptRateLimitSec
	sessionHandler := &SessionHandler{DB: cfg.DB, Storage: cfg.Storage}
	artworkHandler := &ArtworkHandler{DB: cfg.DB}
	discoveryHandler := &GameDiscoveryHandler{DB: cfg.DB, Scraper: cfg.Scraper}
	setupHandler := &SetupHandler{
		DB:            cfg.DB,
		JWTSecret:     cfg.JWTSecret,
		EncryptionKey: cfg.EncryptionKey,
		GameDirs:      cfg.GameDirs,
		Storage:       cfg.Storage,
	}
	stagingDir := filepath.Join(cfg.GameDirs[0], "staging")
	uploadHandler := &UploadHandler{
		DB:         cfg.DB,
		Storage:    cfg.Storage,
		Scraper:    cfg.Scraper,
		GameDirs:   cfg.GameDirs,
		StagingDir: stagingDir,
	}

	// Public auth routes — rate limit login/register/setup to prevent brute force,
	// but leave refresh and setup-status unrestricted (called frequently during normal use).
	authGroup := r.Group("/api/auth")
	{
		authGroup.POST("/login", authLimiter.RateLimit(), authHandler.Login)
		authGroup.POST("/register", authLimiter.RateLimit(), authHandler.Register)
		authGroup.POST("/setup", authLimiter.RateLimit(), authHandler.Setup)
		authGroup.POST("/refresh", refreshLimiter.RateLimit(), authHandler.Refresh)
		authGroup.GET("/setup-status", authHandler.SetupStatus)
	}

	// Setup diagnostics (public during first-time setup, admin-only after)
	r.GET("/api/setup/diagnostics", setupHandler.Diagnostics)

	// Logout (requires auth, placed before main protected group for clarity)
	logoutGroup := r.Group("/api/auth")
	logoutGroup.Use(AuthMiddleware(cfg.JWTSecret, cfg.DB))
	logoutGroup.POST("/logout", authHandler.Logout)

	// Console preview screenshots (public — cached libretro thumbnails, loaded by <img> tags)
	r.GET("/api/consoles/:id/preview-screenshot", consoleHandler.GetPreviewScreenshot)

	// Console icons (public — embedded PNGs, loaded by <img> tags)
	r.GET("/api/consoles/:id/icon", consoleHandler.GetConsoleIcon)

	// Console logos (public — embedded SVGs/PNGs, loaded by <img> tags)
	r.GET("/api/consoles/:id/logo", consoleHandler.GetConsoleLogo)
	r.GET("/api/consoles/:id/logo.png", consoleHandler.GetConsoleLogoPng)

	// Protected routes
	api := r.Group("/api")
	api.Use(AuthMiddleware(cfg.JWTSecret, cfg.DB))
	api.Use(userLimiter.UserRateLimit())
	{
		// Consoles
		api.GET("/consoles", consoleHandler.ListConsoles)
		api.GET("/consoles/:id/games", consoleHandler.ListConsoleGames)
		api.GET("/consoles/:id/top-rated", consoleHandler.GetTopRated)
		api.GET("/top-rated", consoleHandler.GetTopRatedGlobal)

		// Top Lists
		api.GET("/top-lists/top-rated", consoleHandler.GetTopListAvailable)

		// Games
		api.GET("/games", gameHandler.ListGames)
		api.GET("/games/:id", gameHandler.GetGame)
		api.GET("/games/:id/download", downloadLimiter.RateLimit(), gameHandler.DownloadGame)
		api.GET("/games/:id/discs/:discNumber/download", downloadLimiter.RateLimit(), gameHandler.DownloadDisc)
		api.POST("/games/:id/scrape-if-needed", gameHandler.ScrapeIfNeeded)
		api.POST("/games/:id/play-time", gameHandler.UpdatePlayTime)
		api.DELETE("/games/:id/play-time", gameHandler.StopPlaying)
		api.GET("/games/:id/stats", gameHandler.GetGameStats)
		api.GET("/games/:id/cheats", gameHandler.GetGameCheats)
		api.GET("/games/:id/artwork", artworkHandler.GetGameArtwork)
		api.GET("/games/:id/similar", discoveryHandler.GetSimilarGames)
		api.GET("/games/:id/developer-games", discoveryHandler.GetDeveloperGames)

		// Game Sessions
		api.POST("/games/:id/sessions", sessionHandler.CreateSession)
		api.POST("/games/:id/sessions/from-shared-save/:saveId", sessionHandler.CreateFromSharedSave)
		api.GET("/games/:id/sessions", sessionHandler.ListSessions)
		api.GET("/sessions/:id", sessionHandler.GetSession)
		api.PUT("/sessions/:id", sessionHandler.UpdateSession)
		api.DELETE("/sessions/:id", sessionHandler.DeleteSession)
		api.GET("/sessions/:id/saves", sessionHandler.ListSessionSaves)
		api.POST("/sessions/:id/saves", uploadLimiter.RateLimit(), sessionHandler.UploadSessionSave)
		api.POST("/sessions/:id/saves/auto", uploadLimiter.RateLimit(), sessionHandler.UploadAutoSave)
		api.GET("/sessions/:id/saves/auto", sessionHandler.GetAutoSave)
		api.GET("/sessions/:id/saves/slots", sessionHandler.ListSlotSaves)
		api.PUT("/sessions/:id/saves/slot/:slot", uploadLimiter.RateLimit(), sessionHandler.UpsertSlotSave)
		api.GET("/sessions/:id/saves/slot/:slot", sessionHandler.DownloadSlotSave)
		api.GET("/sessions/:id/saves/:saveId", sessionHandler.DownloadSessionSave)
		api.DELETE("/sessions/:id/saves/:saveId", sessionHandler.DeleteSessionSave)
		api.PUT("/sessions/:id/saves/:saveId", sessionHandler.UpdateSessionSave)
		api.POST("/sessions/:id/sram", uploadLimiter.RateLimit(), sessionHandler.UploadSRAM)
		api.GET("/sessions/:id/sram", sessionHandler.DownloadSRAM)
		api.POST("/sessions/:id/play-time", sessionHandler.UpdatePlayTime)
		api.DELETE("/sessions/:id/play-time", sessionHandler.StopPlaying)
		api.GET("/sessions/:id/cheats", sessionHandler.GetSessionCheats)
		api.PUT("/sessions/:id/cheats", sessionHandler.UpdateSessionCheats)
		api.POST("/sessions/:id/duplicate", sessionHandler.DuplicateSession)

		// Ratings
		api.POST("/games/:id/ratings", ratingHandler.CreateOrUpdateRating)
		api.GET("/games/:id/ratings", ratingHandler.GetRatings)
		api.GET("/games/:id/ratings/summary", ratingHandler.GetRatingSummary)
		api.GET("/games/:id/ratings/mine", ratingHandler.GetMyRating)
		api.DELETE("/games/:id/ratings", ratingHandler.DeleteRating)

		// Shared saves
		api.POST("/games/:id/shared-saves", uploadLimiter.RateLimit(), sharedSaveHandler.ShareSave)
		api.GET("/games/:id/shared-saves", sharedSaveHandler.ListSharedSaves)
		api.GET("/games/:id/shared-saves/:saveId/download", sharedSaveHandler.DownloadSharedSave)
		api.DELETE("/games/:id/shared-saves/:saveId", sharedSaveHandler.DeleteSharedSave)

		// Cores
		api.GET("/games/:id/core", gameHandler.GetRecommendedCore)
		api.GET("/cores", coreHandler.ListCores)
		api.GET("/cores/:id/download", coreHandler.DownloadCore)

		// BIOS files
		api.GET("/bios", biosHandler.ListBiosFiles)
		api.GET("/bios/:filename", biosHandler.GetBiosFile)

		// Stats
		api.GET("/stats/most-played", statsHandler.MostPlayedGames)
		api.GET("/stats/most-active-players", statsHandler.MostActivePlayers)

		// User
		api.GET("/user/profile", userHandler.GetProfile)
		api.PUT("/user/profile", userHandler.UpdateProfile)
		api.PUT("/user/password", authLimiter.RateLimit(), userHandler.ChangePassword)
		api.GET("/user/preferences", userHandler.GetPreferences)
		api.PUT("/user/preferences", userHandler.UpdatePreferences)
		api.GET("/user/stats", userHandler.GetUserStats)
		api.GET("/user/play-stats", userHandler.GetPlayStats)
		api.GET("/user/play-heatmap", statsHandler.GetPlayHeatmap)
		api.GET("/user/recent", userHandler.GetRecentGames)
		api.GET("/user/favorites", userHandler.GetFavorites)
		api.POST("/user/favorites/:gameId", userHandler.AddFavorite)
		api.DELETE("/user/favorites/:gameId", userHandler.RemoveFavorite)

		// Per-game key mappings
		api.GET("/user/games/:gameId/keymapping", gameKeyMappingHandler.GetGameKeyMapping)
		api.PUT("/user/games/:gameId/keymapping", gameKeyMappingHandler.UpdateGameKeyMapping)
		api.DELETE("/user/games/:gameId/keymapping", gameKeyMappingHandler.DeleteGameKeyMapping)

		// Play Later
		api.GET("/user/play-later", playLaterHandler.ListPlayLater)
		api.POST("/user/play-later/:gameId", playLaterHandler.AddToPlayLater)
		api.DELETE("/user/play-later/:gameId", playLaterHandler.RemoveFromPlayLater)
		api.PUT("/user/play-later/reorder", playLaterHandler.ReorderPlayLater)

		// Devices
		api.POST("/user/devices", deviceHandler.RegisterDevice)
		api.GET("/user/devices", deviceHandler.GetDevices)
		api.PUT("/user/devices/:id", deviceHandler.UpdateDevice)
		api.DELETE("/user/devices/:id", deviceHandler.DeleteDevice)
		api.GET("/user/devices/:id/preferences", deviceHandler.GetDevicePreferences)
		api.PUT("/user/devices/:id/preferences", deviceHandler.UpdateDevicePreferences)

		// Collections
		api.POST("/collections", collectionHandler.CreateCollection)
		api.GET("/collections", collectionHandler.ListMyCollections)
		api.GET("/collections/public", collectionHandler.ListPublicCollections)
		api.GET("/collections/:id", collectionHandler.GetCollection)
		api.PUT("/collections/:id", collectionHandler.UpdateCollection)
		api.DELETE("/collections/:id", collectionHandler.DeleteCollection)
		api.POST("/collections/:id/games", collectionHandler.AddGame)
		api.DELETE("/collections/:id/games/:gameId", collectionHandler.RemoveGame)

		// Shared Sessions
		api.GET("/games/:id/shared-sessions", sharedSessionHandler.ListGameSharedSessions)
		api.POST("/shared-sessions", sharedSessionHandler.CreateSharedSession)
		api.GET("/shared-sessions", sharedSessionHandler.ListSharedSessions)
		api.GET("/shared-sessions/:id", sharedSessionHandler.GetSharedSession)
		api.PUT("/shared-sessions/:id", sharedSessionHandler.UpdateSharedSession)
		api.DELETE("/shared-sessions/:id", sharedSessionHandler.DeleteSharedSession)
		api.POST("/shared-sessions/:id/invites", sharedSessionHandler.InviteUser)
		api.POST("/shared-sessions/:id/leave", sharedSessionHandler.LeaveSharedSession)
		api.DELETE("/shared-sessions/:id/members/:userId", sharedSessionHandler.RemoveMember)
		api.POST("/shared-sessions/:id/take-turn", sharedSessionHandler.TakeTurn)
		api.POST("/shared-sessions/:id/release-turn", sharedSessionHandler.ReleaseTurn)
		api.POST("/shared-sessions/:id/heartbeat", sharedSessionHandler.Heartbeat)
		api.GET("/shared-sessions/:id/saves", sharedSessionHandler.ListSaves)
		api.POST("/shared-sessions/:id/saves", uploadLimiter.RateLimit(), sharedSessionHandler.UploadSave)
		api.GET("/shared-sessions/:id/saves/auto", sharedSessionHandler.GetAutoSave)
		api.POST("/shared-sessions/:id/saves/auto", uploadLimiter.RateLimit(), sharedSessionHandler.UploadAutoSave)
		api.GET("/shared-sessions/:id/saves/:saveId", sharedSessionHandler.DownloadSave)
		api.PUT("/shared-sessions/:id/saves/:saveId/rename", sharedSessionHandler.RenameSharedSessionSave)
		api.DELETE("/shared-sessions/:id/saves/:saveId", sharedSessionHandler.DeleteSave)
		api.GET("/user/shared-session-invites", sharedSessionHandler.ListMyInvites)
		api.GET("/user/shared-session-invites/count", sharedSessionHandler.GetPendingInviteCount)
		api.POST("/user/shared-session-invites/:id/accept", sharedSessionHandler.AcceptInvite)
		api.POST("/user/shared-session-invites/:id/decline", sharedSessionHandler.DeclineInvite)

		// Netplay
		netplay := api.Group("/netplay")
		{
			netplay.POST("/sessions", netplayHandler.CreateSession)
			netplay.GET("/sessions", netplayHandler.ListSessions)
			netplay.GET("/sessions/:id", netplayHandler.GetSession)
			netplay.POST("/sessions/join", netplayHandler.JoinByInviteCode)
			netplay.POST("/sessions/:id/leave", netplayHandler.LeaveSession)
			netplay.DELETE("/sessions/:id", netplayHandler.DeleteSession)
			netplay.PUT("/sessions/:id/settings", netplayHandler.UpdateSettings)
			netplay.GET("/sessions/:id/ws", netplayHandler.HandleWebSocket)
			netplay.POST("/sessions/:id/invites", netplayHandler.SendInvite)
			netplay.GET("/sessions/:id/invites", netplayHandler.ListSessionInvites)
			netplay.GET("/invites", netplayHandler.ListMyNetplayInvites)
			netplay.GET("/invites/count", netplayHandler.GetPendingNetplayInviteCount)
			netplay.POST("/invites/:inviteId/accept", netplayHandler.AcceptNetplayInvite)
			netplay.POST("/invites/:inviteId/decline", netplayHandler.DeclineNetplayInvite)
		}

		// Social
		api.GET("/social/online", socialHandler.GetOnlineUsers)
		api.GET("/social/activity", socialHandler.GetActivityFeed)
		api.GET("/users/search", socialHandler.SearchUsers)
		api.GET("/users/recent-partners", socialHandler.GetRecentPartners)
		api.GET("/users/:id/profile", socialHandler.GetPublicProfile)
		api.GET("/users/:id/play-heatmap", statsHandler.GetPublicPlayHeatmap)

		// Challenges
		api.POST("/challenges", challengeHandler.CreateChallenge)
		api.GET("/challenges", challengeHandler.ListChallenges)
		api.GET("/challenges/:id", challengeHandler.GetChallenge)
		api.PUT("/challenges/:id", challengeHandler.UpdateChallenge)
		api.DELETE("/challenges/:id", challengeHandler.DeleteChallenge)
		api.GET("/challenges/:id/save/download", challengeHandler.DownloadChallengeSave)
		api.GET("/challenges/:id/screenshot", challengeHandler.GetChallengeScreenshot)
		api.POST("/challenges/:id/attempts/start", challengeHandler.StartAttempt)
		api.POST("/challenges/:id/attempts/:aid/complete", challengeHandler.CompleteAttempt)
		api.POST("/challenges/:id/attempts/:aid/abandon", challengeHandler.AbandonAttempt)
		api.GET("/challenges/:id/attempts/mine", challengeHandler.GetMyAttempts)
		api.GET("/challenges/:id/leaderboard", challengeHandler.GetLeaderboard)
		api.GET("/games/:id/challenges", challengeHandler.ListGameChallenges)
		api.GET("/user/challenges", challengeHandler.ListMyChallenges)

		// RetroAchievements
		api.POST("/user/ra/link", raHandler.LinkAccount)
		api.DELETE("/user/ra/link", raHandler.UnlinkAccount)
		api.GET("/user/ra/status", raHandler.GetStatus)
		api.PUT("/user/ra/settings", raHandler.UpdateSettings)
		api.GET("/user/ra/token", raHandler.GetToken)
		api.GET("/games/:id/achievements", raHandler.GetGameAchievements)
		api.GET("/games/:id/achievements/progress", raHandler.GetAchievementProgress)
		api.GET("/games/:id/achievements/timeline", raHandler.GetAchievementTimeline)
		api.GET("/games/:id/achievements/leaderboard", raHandler.GetAchievementLeaderboard)
		api.GET("/user/achievements/recent", raHandler.GetRecentAchievements)

		// Admin routes
		admin := api.Group("/admin")
		admin.Use(AdminMiddleware())
		{
			admin.POST("/games/:id/metadata", gameHandler.UpdateMetadata)
			admin.POST("/games/scan", gameHandler.ScanGames)
			admin.GET("/users", adminHandler.ListUsers)
			admin.POST("/users", adminHandler.CreateUser)
			admin.PUT("/users/:id", adminHandler.UpdateUser)
			admin.DELETE("/users/:id", adminHandler.DeleteUser)
			admin.GET("/users/deleted", adminHandler.ListDeletedUsers)
			admin.DELETE("/users/:id/permanent", adminHandler.HardDeleteUser)
			admin.GET("/settings", adminHandler.GetSettings)
			admin.PUT("/settings", adminHandler.UpdateSettings)
			admin.POST("/scrape", adminHandler.TriggerScrape)
			admin.GET("/scrape/status", adminHandler.ScrapeStatus)
			admin.POST("/games/:id/scrape", adminHandler.ScrapeGame)
			admin.GET("/games/:id/covers", adminHandler.GetGameCovers)
			admin.PUT("/games/:id/covers", adminHandler.SetGameCover)
			admin.GET("/metadata-matches", adminHandler.MetadataMatches)
			admin.GET("/games/:id/igdb-search", adminHandler.SearchIGDB)
			admin.POST("/games/:id/igdb-match", adminHandler.ApplyIGDBMatch)
			admin.POST("/igdb/test", igdbHandler.TestIGDB)
			admin.GET("/igdb/status", igdbHandler.GetIGDBStatus)
			admin.GET("/stats", adminHandler.GetStats)
			admin.GET("/users/:id/rate-limit", adminHandler.GetUserRateLimit)
			admin.DELETE("/users/:id/rate-limit", adminHandler.ResetUserRateLimit)
			admin.GET("/users/:id/devices", deviceHandler.AdminGetUserDevices)
			admin.POST("/bios", biosHandler.UploadBiosFile)
			admin.POST("/bios/download", biosHandler.TriggerDownload)
			admin.DELETE("/bios/:filename", biosHandler.DeleteBiosFile)
			admin.PUT("/games/:id/verification-tag", gameHandler.UpdateVerificationTag)
			admin.POST("/cheats/import", adminHandler.TriggerCheatImport)
			admin.GET("/cheats/stats", adminHandler.GetCheatStats)
			admin.GET("/core-compatibility", adminHandler.GetCoreCompatibility)

			// ROM uploads
			admin.POST("/uploads", uploadHandler.UploadROMs)
			admin.GET("/uploads", uploadHandler.ListUploads)
			admin.POST("/uploads/:id/console", uploadHandler.SetConsole)
			admin.POST("/uploads/:id/scrape", uploadHandler.ScrapeUpload)
			admin.POST("/uploads/scrape", uploadHandler.ScrapeAllUploads)
			admin.POST("/uploads/:id/accept", uploadHandler.AcceptUpload)
			admin.POST("/uploads/:id/reject", uploadHandler.RejectUpload)
			admin.POST("/uploads/accept-all", uploadHandler.AcceptAllUploads)
			admin.POST("/uploads/reject-all", uploadHandler.RejectAllUploads)
			admin.DELETE("/uploads", uploadHandler.ClearStaging)
		}

		// WebSocket
		api.GET("/ws", cfg.Hub.HandleWebSocket)
	}

	// Test-only endpoint for E2E test isolation (only registered when SPELA_TEST_MODE=true).
	// Registered outside the auth group so it can be called without a token.
	if cfg.TestMode {
		testHandler := &TestHandler{DB: cfg.DB}
		r.POST("/api/test/reset", testHandler.Reset)
	}

	// Serve frontend static files when configured (unified single-container deployment)
	if cfg.FrontendDir != "" {
		r.NoRoute(serveFrontend(cfg.FrontendDir))
	}

	return r
}

// serveFrontend returns a Gin handler that serves static files from the given
// directory with SPA fallback (unknown paths serve index.html). Hashed assets
// under /assets/ get long-lived cache headers.
func serveFrontend(frontendDir string) gin.HandlerFunc {
	fs := http.Dir(frontendDir)
	fileServer := http.FileServer(fs)

	return func(c *gin.Context) {
		reqPath := c.Request.URL.Path

		// Never intercept API or WebSocket routes
		if strings.HasPrefix(reqPath, "/api/") {
			c.JSON(http.StatusNotFound, gin.H{"error": "not found"})
			return
		}

		// Try to serve the file directly
		filePath := filepath.Join(frontendDir, filepath.Clean(reqPath))
		if info, err := os.Stat(filePath); err == nil && !info.IsDir() {
			// Hashed assets get immutable cache headers
			if strings.HasPrefix(reqPath, "/assets/") {
				c.Header("Cache-Control", "public, immutable, max-age=604800")
			}
			fileServer.ServeHTTP(c.Writer, c.Request)
			return
		}

		// Static asset paths must resolve to real files — never fall through
		// to SPA. This prevents the SPA fallback from returning index.html
		// (200 OK) for missing files, which breaks EmulatorJS CDN fallback
		// (it checks for HTTP errors, not content type).
		if strings.HasPrefix(reqPath, "/emulatorjs/") || strings.HasPrefix(reqPath, "/assets/") {
			c.Status(http.StatusNotFound)
			return
		}

		// SPA fallback: serve index.html for any non-file path
		c.File(filepath.Join(frontendDir, "index.html"))
	}
}

// ImageHandler serves images with access control for save screenshots.
type ImageHandler struct {
	ImageDir  string
	JWTSecret string
	DB        *gorm.DB
}

// ServeImage serves image files. Paths under save-screenshots/ require auth + ownership.
func (h *ImageHandler) ServeImage(c *gin.Context) {
	reqPath := c.Param("filepath")
	// Strip leading slash from the wildcard param
	reqPath = strings.TrimPrefix(reqPath, "/")

	// Resolve the absolute path and ensure it stays within ImageDir.
	// Use EvalSymlinks to prevent symlink-based escapes.
	absImageDir, _ := filepath.Abs(h.ImageDir)
	if realDir, err := filepath.EvalSymlinks(absImageDir); err == nil {
		absImageDir = realDir
	}
	absPath, _ := filepath.Abs(filepath.Join(h.ImageDir, reqPath))
	if _, statErr := os.Stat(absPath); statErr == nil {
		realPath, err := filepath.EvalSymlinks(absPath)
		if err != nil {
			c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
			return
		}
		absPath = realPath
	}
	if !strings.HasPrefix(absPath, absImageDir+string(filepath.Separator)) && absPath != absImageDir {
		c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
		return
	}

	// Save screenshots require authentication and ownership check
	if strings.HasPrefix(reqPath, "save-screenshots/") {
		// Require auth (token from header or query param)
		var token string
		header := c.GetHeader("Authorization")
		if header != "" {
			hParts := strings.SplitN(header, " ", 2)
			if len(hParts) == 2 && hParts[0] == "Bearer" {
				token = hParts[1]
			}
		}
		if token == "" {
			token = c.Query("token")
		}
		if token == "" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "authentication required"})
			return
		}

		claims, err := auth.ValidateAccessToken(token, h.JWTSecret)
		if err != nil {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid or expired token"})
			return
		}

		// Reject revoked tokens and disabled/changed users (same checks as AuthMiddleware)
		if IsTokenBlacklisted(h.DB, token) {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "token has been revoked"})
			return
		}
		var user db.User
		if err := h.DB.Select("id", "disabled", "token_version").First(&user, claims.UserID).Error; err != nil {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "user not found"})
			return
		}
		if user.Disabled {
			c.JSON(http.StatusForbidden, gin.H{"error": "account is disabled"})
			return
		}
		if claims.TokenVersion != user.TokenVersion {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "token has been invalidated"})
			return
		}

		parts := strings.SplitN(reqPath, "/", 4)

		// Legacy format: save-screenshots/user_{id}/...
		if len(parts) >= 2 && strings.HasPrefix(parts[1], "user_") {
			pathUserIDStr := strings.TrimPrefix(parts[1], "user_")
			pathUserID, parseErr := strconv.ParseUint(pathUserIDStr, 10, 64)
			if parseErr != nil {
				c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
				return
			}
			if uint64(claims.UserID) != pathUserID && claims.Role != "admin" && claims.Role != "owner" {
				c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
				return
			}
		} else if len(parts) >= 3 && parts[1] == "sessions" && strings.HasPrefix(parts[2], "session_") {
			// Session format: save-screenshots/sessions/session_{id}/...
			sessionIDStr := strings.TrimPrefix(parts[2], "session_")
			sessionID, parseErr := strconv.ParseUint(sessionIDStr, 10, 64)
			if parseErr != nil {
				c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
				return
			}
			var session db.GameSession
			if err := h.DB.Select("id", "owner_id").First(&session, sessionID).Error; err != nil {
				c.JSON(http.StatusNotFound, gin.H{"error": "session not found"})
				return
			}
			if uint64(claims.UserID) != uint64(session.OwnerID) && claims.Role != "admin" && claims.Role != "owner" {
				c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
				return
			}
		} else {
			c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
			return
		}
	}

	c.File(absPath)
}
