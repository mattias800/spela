package api

import (
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
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

	// COOP/COEP headers — required for SharedArrayBuffer (EmulatorJS threaded cores)
	r.Use(func(c *gin.Context) {
		c.Header("Cross-Origin-Opener-Policy", "same-origin")
		c.Header("Cross-Origin-Embedder-Policy", "credentialless")
		c.Next()
	})

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

	// Serve downloaded images (public, no auth required — just box art)
	r.Static("/api/images", cfg.Storage.ImageDir)

	// Health check (public, no auth required)
	r.GET("/api/health", func(c *gin.Context) {
		sqlDB, err := cfg.DB.DB()
		dbStatus := "ok"
		if err != nil {
			dbStatus = "error: " + err.Error()
		} else if err := sqlDB.Ping(); err != nil {
			dbStatus = "error: " + err.Error()
		}

		var gameCount, userCount int64
		cfg.DB.Model(&db.Game{}).Count(&gameCount)
		cfg.DB.Model(&db.User{}).Count(&userCount)

		c.JSON(200, gin.H{
			"status":   "ok",
			"version":  "0.1.0",
			"database": dbStatus,
			"games":    gameCount,
			"users":    userCount,
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
	consoleHandler := &ConsoleHandler{DB: cfg.DB, Storage: cfg.Storage}
	userHandler := &UserHandler{DB: cfg.DB, Hub: cfg.Hub}
	deviceHandler := &DeviceHandler{DB: cfg.DB}
	statsHandler := &StatsHandler{DB: cfg.DB}
	adminHandler := &AdminHandler{DB: cfg.DB, Scraper: cfg.Scraper, Hub: cfg.Hub, Storage: cfg.Storage}
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
	biosHandler := &BiosHandler{Storage: cfg.Storage}
	gameKeyMappingHandler := &GameKeyMappingHandler{DB: cfg.DB}
	challengeHandler := NewChallengeHandler(cfg.DB, cfg.Storage, cfg.Hub)
	challengeHandler.AttemptRateLimitSeconds = cfg.ChallengeAttemptRateLimitSec

	// Public auth routes — rate limit login/register/setup to prevent brute force,
	// but leave refresh and setup-status unrestricted (called frequently during normal use).
	authGroup := r.Group("/api/auth")
	{
		authGroup.POST("/login", authLimiter.RateLimit(), authHandler.Login)
		authGroup.POST("/register", authLimiter.RateLimit(), authHandler.Register)
		authGroup.POST("/setup", authLimiter.RateLimit(), authHandler.Setup)
		authGroup.POST("/refresh", authHandler.Refresh)
		authGroup.GET("/setup-status", authHandler.SetupStatus)
	}

	// Console preview screenshots (public — cached libretro thumbnails, loaded by <img> tags)
	r.GET("/api/consoles/:id/preview-screenshot", consoleHandler.GetPreviewScreenshot)

	// Console icons (public — embedded PNGs, loaded by <img> tags)
	r.GET("/api/consoles/:id/icon", consoleHandler.GetConsoleIcon)

	// Protected routes
	api := r.Group("/api")
	api.Use(AuthMiddleware(cfg.JWTSecret))
	{
		// Consoles
		api.GET("/consoles", consoleHandler.ListConsoles)
		api.GET("/consoles/:id/games", consoleHandler.ListConsoleGames)

		// Games
		api.GET("/games", gameHandler.ListGames)
		api.GET("/games/:id", gameHandler.GetGame)
		api.GET("/games/:id/download", gameHandler.DownloadGame)
		api.GET("/games/:id/discs/:discNumber/download", gameHandler.DownloadDisc)
		api.POST("/games/:id/metadata", gameHandler.UpdateMetadata)
		api.POST("/games/:id/scrape-if-needed", gameHandler.ScrapeIfNeeded)
		api.POST("/games/:id/play-time", gameHandler.UpdatePlayTime)
		api.GET("/games/:id/stats", gameHandler.GetGameStats)
		api.POST("/games/scan", gameHandler.ScanGames)

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
		api.GET("/games/:id/saves/:saveId", gameHandler.DownloadSave)
		api.DELETE("/games/:id/saves/:saveId", gameHandler.DeleteSave)
		api.POST("/games/:id/saves/auto", gameHandler.UploadAutoSave)
		api.GET("/games/:id/saves/auto", gameHandler.GetAutoSave)

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
		api.DELETE("/relays/:id/saves/:saveId", relayHandler.DeleteSave)
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
			admin.GET("/users", adminHandler.ListUsers)
			admin.POST("/users", adminHandler.CreateUser)
			admin.PUT("/users/:id", adminHandler.UpdateUser)
			admin.DELETE("/users/:id", adminHandler.DeleteUser)
			admin.GET("/settings", adminHandler.GetSettings)
			admin.PUT("/settings", adminHandler.UpdateSettings)
			admin.POST("/scrape", adminHandler.TriggerScrape)
			admin.POST("/games/:id/scrape", adminHandler.ScrapeGame)
			admin.GET("/metadata-matches", adminHandler.MetadataMatches)
			admin.GET("/stats", adminHandler.GetStats)
			admin.GET("/users/:id/devices", deviceHandler.AdminGetUserDevices)
		}

		// WebSocket
		api.GET("/ws", cfg.Hub.HandleWebSocket)
	}

	return r
}
