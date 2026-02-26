package api

import (
	"net/http"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/retroachievements"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// Config holds configuration for the API router.
type Config struct {
	DB          *gorm.DB
	JWTSecret   string
	GameDirs    []string
	Storage     *storage.Storage
	Scanner     *scanner.Scanner
	Scraper     *scraper.Scraper
	Hub         *ws.Hub
	NetplayHub  *ws.NetplayHub
	CoreDir     string
	CORSOrigins                  []string
	RAClient                     *retroachievements.RAClient // optional; defaults to production RA client
	ChallengeAttemptRateLimitSec int                         // 0 = disabled; default 30 in production
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
		// Prevent clickjacking
		c.Header("X-Frame-Options", "DENY")
		// Minimal referrer info to external sites
		c.Header("Referrer-Policy", "strict-origin-when-cross-origin")
		// Content Security Policy
		c.Header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https: data:; connect-src 'self' wss: ws:; frame-ancestors 'none'")
		c.Next()
	})

	// Global request body size limit for JSON endpoints (1 MB).
	// File upload endpoints (multipart) are excluded and use their own limits.
	r.Use(BodySizeLimiter(MaxJSONBodySize))

	// CORS - configurable origins; AllowCredentials only when origins are explicit
	corsOrigins := cfg.CORSOrigins
	if len(corsOrigins) == 0 {
		corsOrigins = []string{"*"}
	}
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

	// Serve images — save screenshots require auth + ownership; everything else is public
	imageHandler := &ImageHandler{ImageDir: cfg.Storage.ImageDir, JWTSecret: cfg.JWTSecret}
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
			"version": "0.1.0",
		})
	})

	// Rate limiter for auth endpoints
	authLimiter := NewRateLimiter(120, time.Minute)

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
	userHandler := &UserHandler{DB: cfg.DB, Hub: cfg.Hub}
	deviceHandler := &DeviceHandler{DB: cfg.DB}
	statsHandler := &StatsHandler{DB: cfg.DB}
	adminHandler := &AdminHandler{DB: cfg.DB, Scraper: cfg.Scraper, Hub: cfg.Hub, Storage: cfg.Storage}
	igdbHandler := &IGDBHandler{DB: cfg.DB}
	coreHandler := &CoreHandler{DB: cfg.DB, CoreDir: cfg.CoreDir}
	raClient := cfg.RAClient
	if raClient == nil {
		raClient = retroachievements.NewRAClient()
	}
	socialHandler := &SocialHandler{DB: cfg.DB, Hub: cfg.Hub}
	ratingHandler := &RatingHandler{DB: cfg.DB, Hub: cfg.Hub}
	sharedSaveHandler := &SharedSaveHandler{DB: cfg.DB, Storage: cfg.Storage, Hub: cfg.Hub}
	collectionHandler := &CollectionHandler{DB: cfg.DB, Hub: cfg.Hub}
	playLaterHandler := &PlayLaterHandler{DB: cfg.DB, Hub: cfg.Hub}
	relayHandler := &RelayHandler{DB: cfg.DB, Storage: cfg.Storage, Hub: cfg.Hub}
	netplayHandler := &NetplayHandler{DB: cfg.DB, Hub: cfg.Hub, NetplayHub: cfg.NetplayHub}
	raHandler := &RAHandler{DB: cfg.DB, RAClient: raClient, GameDir: cfg.GameDirs[0]}
	biosHandler := &BiosHandler{Storage: cfg.Storage, DB: cfg.DB}
	gameKeyMappingHandler := &GameKeyMappingHandler{DB: cfg.DB}
	saveDataHandler := &SaveDataHandler{DB: cfg.DB, Storage: cfg.Storage}
	saveHandler := &SaveHandler{DB: cfg.DB, Storage: cfg.Storage}
	challengeHandler := NewChallengeHandler(cfg.DB, cfg.Storage, cfg.Hub)
	challengeHandler.AttemptRateLimitSeconds = cfg.ChallengeAttemptRateLimitSec
	discoveryHandler := &GameDiscoveryHandler{DB: cfg.DB, Scraper: cfg.Scraper}
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
		authGroup.POST("/refresh", authLimiter.RateLimit(), authHandler.Refresh)
		authGroup.GET("/setup-status", authHandler.SetupStatus)
	}

	// Console preview screenshots (public — cached libretro thumbnails, loaded by <img> tags)
	r.GET("/api/consoles/:id/preview-screenshot", consoleHandler.GetPreviewScreenshot)

	// Console icons (public — embedded PNGs, loaded by <img> tags)
	r.GET("/api/consoles/:id/icon", consoleHandler.GetConsoleIcon)

	// Console logos (public — embedded SVGs/PNGs, loaded by <img> tags)
	r.GET("/api/consoles/:id/logo", consoleHandler.GetConsoleLogo)
	r.GET("/api/consoles/:id/logo.png", consoleHandler.GetConsoleLogoPng)

	// Protected routes
	api := r.Group("/api")
	api.Use(AuthMiddleware(cfg.JWTSecret))
	{
		// Consoles
		api.GET("/consoles", consoleHandler.ListConsoles)
		api.GET("/consoles/:id/games", consoleHandler.ListConsoleGames)
		api.GET("/consoles/:id/top-rated", consoleHandler.GetTopRated)
		api.GET("/top-rated", consoleHandler.GetTopRatedGlobal)

		// Games
		api.GET("/games", gameHandler.ListGames)
		api.GET("/games/:id", gameHandler.GetGame)
		api.GET("/games/:id/download", gameHandler.DownloadGame)
		api.GET("/games/:id/discs/:discNumber/download", gameHandler.DownloadDisc)
		api.POST("/games/:id/scrape-if-needed", gameHandler.ScrapeIfNeeded)
		api.POST("/games/:id/play-time", gameHandler.UpdatePlayTime)
		api.GET("/games/:id/stats", gameHandler.GetGameStats)
		api.GET("/games/:id/similar", discoveryHandler.GetSimilarGames)
		api.GET("/games/:id/developer-games", discoveryHandler.GetDeveloperGames)

		// Ratings
		api.POST("/games/:id/ratings", ratingHandler.CreateOrUpdateRating)
		api.GET("/games/:id/ratings", ratingHandler.GetRatings)
		api.GET("/games/:id/ratings/summary", ratingHandler.GetRatingSummary)
		api.GET("/games/:id/ratings/mine", ratingHandler.GetMyRating)
		api.DELETE("/games/:id/ratings", ratingHandler.DeleteRating)

		// Shared saves
		api.POST("/games/:id/shared-saves", sharedSaveHandler.ShareSave)
		api.GET("/games/:id/shared-saves", sharedSaveHandler.ListSharedSaves)
		api.GET("/games/:id/shared-saves/:saveId/download", sharedSaveHandler.DownloadSharedSave)
		api.DELETE("/games/:id/shared-saves/:saveId", sharedSaveHandler.DeleteSharedSave)

		// Save states
		api.GET("/games/:id/saves", gameHandler.ListSaves)
		api.POST("/games/:id/saves", gameHandler.UploadSave)
		api.POST("/games/:id/saves/import", saveHandler.ImportSave)
		api.GET("/games/:id/saves/auto", gameHandler.GetAutoSave)
		api.POST("/games/:id/saves/auto", gameHandler.UploadAutoSave)
		api.GET("/games/:id/saves/auto/history", saveHandler.GetAutoSaveHistory)
		api.DELETE("/games/:id/saves/bulk", saveHandler.BulkDeleteSaves)
		api.GET("/games/:id/saves/slots", saveHandler.ListSlotSaves)
		api.PUT("/games/:id/saves/slot/:slot", saveHandler.UpsertSlotSave)
		api.GET("/games/:id/saves/slot/:slot", saveHandler.DownloadSlotSave)
		api.GET("/games/:id/saves/:saveId", gameHandler.DownloadSave)
		api.PUT("/games/:id/saves/:saveId", saveHandler.RenameSave)
		api.DELETE("/games/:id/saves/:saveId", gameHandler.DeleteSave)
		api.PUT("/games/:id/saves/:saveId/notes", saveHandler.UpdateNotes)
		api.GET("/games/:id/saves/:saveId/screenshot", saveHandler.GetSaveScreenshot)

		// Save data (SRAM/battery saves)
		api.GET("/games/:id/save-data", saveDataHandler.ListSaveData)
		api.POST("/games/:id/save-data", saveDataHandler.UploadSaveData)
		api.POST("/games/:id/save-data/active", saveDataHandler.UploadActiveSaveData)
		api.GET("/games/:id/save-data/active", saveDataHandler.DownloadActiveSaveData)
		api.GET("/games/:id/save-data/:sdId/download", saveDataHandler.DownloadSaveData)
		api.PUT("/games/:id/save-data/:sdId/activate", saveDataHandler.ActivateSaveData)
		api.PUT("/games/:id/save-data/:sdId", saveDataHandler.RenameSaveData)
		api.DELETE("/games/:id/save-data/:sdId", saveDataHandler.DeleteSaveData)

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
		api.GET("/user/preferences", userHandler.GetPreferences)
		api.PUT("/user/preferences", userHandler.UpdatePreferences)
		api.GET("/user/stats", userHandler.GetUserStats)
		api.GET("/user/storage-usage", saveHandler.GetStorageUsage)
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

		// Relays
		api.GET("/games/:id/relays", relayHandler.ListGameRelays)
		api.POST("/relays", relayHandler.CreateRelay)
		api.GET("/relays", relayHandler.ListRelays)
		api.GET("/relays/:id", relayHandler.GetRelay)
		api.PUT("/relays/:id", relayHandler.UpdateRelay)
		api.DELETE("/relays/:id", relayHandler.DeleteRelay)
		api.POST("/relays/:id/invites", relayHandler.InviteUser)
		api.POST("/relays/:id/leave", relayHandler.LeaveRelay)
		api.DELETE("/relays/:id/members/:userId", relayHandler.RemoveMember)
		api.POST("/relays/:id/take-turn", relayHandler.TakeTurn)
		api.POST("/relays/:id/release-turn", relayHandler.ReleaseTurn)
		api.POST("/relays/:id/heartbeat", relayHandler.Heartbeat)
		api.GET("/relays/:id/saves", relayHandler.ListSaves)
		api.POST("/relays/:id/saves", relayHandler.UploadSave)
		api.GET("/relays/:id/saves/auto", relayHandler.GetAutoSave)
		api.POST("/relays/:id/saves/auto", relayHandler.UploadAutoSave)
		api.GET("/relays/:id/saves/:saveId", relayHandler.DownloadSave)
		api.PUT("/relays/:id/saves/:saveId/rename", saveHandler.RenameRelaySave)
		api.DELETE("/relays/:id/saves/:saveId", relayHandler.DeleteSave)
		api.POST("/relays/:id/saves/:saveId/copy-to-game", relayHandler.CopyRelaySaveToGame)
		api.GET("/user/relay-invites", relayHandler.ListMyInvites)
		api.GET("/user/relay-invites/count", relayHandler.GetPendingInviteCount)
		api.POST("/user/relay-invites/:id/accept", relayHandler.AcceptInvite)
		api.POST("/user/relay-invites/:id/decline", relayHandler.DeclineInvite)

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
		}

		// Social
		api.GET("/social/online", socialHandler.GetOnlineUsers)
		api.GET("/social/activity", socialHandler.GetActivityFeed)
		api.GET("/users/search", socialHandler.SearchUsers)
		api.GET("/users/:id/profile", socialHandler.GetPublicProfile)

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
			admin.GET("/settings", adminHandler.GetSettings)
			admin.PUT("/settings", adminHandler.UpdateSettings)
			admin.POST("/scrape", adminHandler.TriggerScrape)
			admin.GET("/scrape/status", adminHandler.ScrapeStatus)
			admin.POST("/games/:id/scrape", adminHandler.ScrapeGame)
			admin.GET("/games/:id/covers", adminHandler.GetGameCovers)
			admin.PUT("/games/:id/covers", adminHandler.SetGameCover)
			admin.GET("/metadata-matches", adminHandler.MetadataMatches)
			admin.POST("/igdb/test", igdbHandler.TestIGDB)
			admin.GET("/igdb/status", igdbHandler.GetIGDBStatus)
			admin.GET("/stats", adminHandler.GetStats)
			admin.GET("/users/:id/devices", deviceHandler.AdminGetUserDevices)
			admin.POST("/bios", biosHandler.UploadBiosFile)
			admin.DELETE("/bios/:filename", biosHandler.DeleteBiosFile)
			admin.PUT("/games/:id/verification-tag", gameHandler.UpdateVerificationTag)

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

	return r
}

// ImageHandler serves images with access control for save screenshots.
type ImageHandler struct {
	ImageDir  string
	JWTSecret string
}

// ServeImage serves image files. Paths under save-screenshots/ require auth + ownership.
func (h *ImageHandler) ServeImage(c *gin.Context) {
	reqPath := c.Param("filepath")
	// Strip leading slash from the wildcard param
	reqPath = strings.TrimPrefix(reqPath, "/")

	// Resolve the absolute path and ensure it stays within ImageDir
	absImageDir, _ := filepath.Abs(h.ImageDir)
	absPath, _ := filepath.Abs(filepath.Join(h.ImageDir, reqPath))
	if !strings.HasPrefix(absPath, absImageDir+string(filepath.Separator)) && absPath != absImageDir {
		c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
		return
	}

	// Save screenshots require authentication and ownership check
	if strings.HasPrefix(reqPath, "save-screenshots/") {
		// Extract user_id from path pattern: save-screenshots/user_{id}/...
		parts := strings.SplitN(reqPath, "/", 3)
		if len(parts) < 2 || !strings.HasPrefix(parts[1], "user_") {
			c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
			return
		}
		pathUserIDStr := strings.TrimPrefix(parts[1], "user_")
		pathUserID, err := strconv.ParseUint(pathUserIDStr, 10, 64)
		if err != nil {
			c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
			return
		}

		// Require auth
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

		// Only the owning user or an admin can access save screenshots
		if uint64(claims.UserID) != pathUserID && claims.Role != "admin" && claims.Role != "owner" {
			c.JSON(http.StatusForbidden, gin.H{"error": "access denied"})
			return
		}
	}

	c.File(absPath)
}
