package api

import (
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
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
	CoreDir     string
	CORSOrigins []string
}

// NewRouter creates and configures the Gin router with all endpoints.
func NewRouter(cfg Config) *gin.Engine {
	r := gin.Default()

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
		AllowHeaders:     []string{"Origin", "Content-Type", "Authorization"},
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
	userHandler := &UserHandler{DB: cfg.DB}
	deviceHandler := &DeviceHandler{DB: cfg.DB}
	adminHandler := &AdminHandler{DB: cfg.DB, Scraper: cfg.Scraper, Hub: cfg.Hub, Storage: cfg.Storage}
	coreHandler := &CoreHandler{DB: cfg.DB, CoreDir: cfg.CoreDir}

	// Public auth routes with rate limiting
	authGroup := r.Group("/api/auth")
	authGroup.Use(authLimiter.RateLimit())
	{
		authGroup.POST("/login", authHandler.Login)
		authGroup.POST("/register", authHandler.Register)
		authGroup.POST("/refresh", authHandler.Refresh)
		authGroup.GET("/setup-status", authHandler.SetupStatus)
		authGroup.POST("/setup", authHandler.Setup)
	}

	// Console preview screenshots (public — cached libretro thumbnails, loaded by <img> tags)
	r.GET("/api/consoles/:id/preview-screenshot", consoleHandler.GetPreviewScreenshot)

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
		api.POST("/games/:id/metadata", gameHandler.UpdateMetadata)
		api.POST("/games/:id/scrape-if-needed", gameHandler.ScrapeIfNeeded)
		api.POST("/games/:id/play-time", gameHandler.UpdatePlayTime)
		api.POST("/games/scan", gameHandler.ScanGames)

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

		// User
		api.GET("/user/profile", userHandler.GetProfile)
		api.PUT("/user/profile", userHandler.UpdateProfile)
		api.GET("/user/preferences", userHandler.GetPreferences)
		api.PUT("/user/preferences", userHandler.UpdatePreferences)
		api.GET("/user/recent", userHandler.GetRecentGames)
		api.GET("/user/favorites", userHandler.GetFavorites)
		api.POST("/user/favorites/:gameId", userHandler.AddFavorite)
		api.DELETE("/user/favorites/:gameId", userHandler.RemoveFavorite)

		// Devices
		api.POST("/user/devices", deviceHandler.RegisterDevice)
		api.GET("/user/devices", deviceHandler.GetDevices)
		api.PUT("/user/devices/:id", deviceHandler.UpdateDevice)
		api.DELETE("/user/devices/:id", deviceHandler.DeleteDevice)
		api.GET("/user/devices/:id/preferences", deviceHandler.GetDevicePreferences)
		api.PUT("/user/devices/:id/preferences", deviceHandler.UpdateDevicePreferences)

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
