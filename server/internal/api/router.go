package api

import (
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// Config holds configuration for the API router.
type Config struct {
	DB        *gorm.DB
	JWTSecret string
	GameDirs  []string
	Storage   *storage.Storage
	Scanner   *scanner.Scanner
	Scraper   *scraper.Scraper
	Hub       *ws.Hub
	CoreDir   string
}

// NewRouter creates and configures the Gin router with all endpoints.
func NewRouter(cfg Config) *gin.Engine {
	r := gin.Default()

	// CORS
	r.Use(cors.New(cors.Config{
		AllowOrigins:     []string{"*"},
		AllowMethods:     []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowHeaders:     []string{"Origin", "Content-Type", "Authorization"},
		ExposeHeaders:    []string{"Content-Length"},
		AllowCredentials: true,
		MaxAge:           12 * time.Hour,
	}))

	// Rate limiter for auth endpoints
	authLimiter := NewRateLimiter(10, time.Minute)

	// Handlers
	authHandler := &AuthHandler{DB: cfg.DB, JWTSecret: cfg.JWTSecret}
	gameHandler := &GameHandler{
		DB:       cfg.DB,
		Scanner:  cfg.Scanner,
		Storage:  cfg.Storage,
		Hub:      cfg.Hub,
		GameDirs: cfg.GameDirs,
	}
	consoleHandler := &ConsoleHandler{DB: cfg.DB}
	userHandler := &UserHandler{DB: cfg.DB}
	adminHandler := &AdminHandler{DB: cfg.DB, Scraper: cfg.Scraper, Hub: cfg.Hub}
	coreHandler := &CoreHandler{DB: cfg.DB, CoreDir: cfg.CoreDir}

	// Public auth routes with rate limiting
	authGroup := r.Group("/api/auth")
	authGroup.Use(authLimiter.RateLimit())
	{
		authGroup.POST("/login", authHandler.Login)
		authGroup.POST("/register", authHandler.Register)
		authGroup.POST("/refresh", authHandler.Refresh)
	}

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
		api.GET("/user/recent", userHandler.GetRecentGames)
		api.GET("/user/favorites", userHandler.GetFavorites)
		api.POST("/user/favorites/:gameId", userHandler.AddFavorite)
		api.DELETE("/user/favorites/:gameId", userHandler.RemoveFavorite)

		// Admin routes
		admin := api.Group("/admin")
		admin.Use(AdminMiddleware())
		{
			admin.GET("/users", adminHandler.ListUsers)
			admin.PUT("/users/:id", adminHandler.UpdateUser)
			admin.GET("/settings", adminHandler.GetSettings)
			admin.PUT("/settings", adminHandler.UpdateSettings)
			admin.POST("/scrape", adminHandler.TriggerScrape)
		}

		// WebSocket
		api.GET("/ws", cfg.Hub.HandleWebSocket)
	}

	return r
}
