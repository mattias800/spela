package main

import (
	"log/slog"
	"os"
	"strings"

	"github.com/spela/server/internal/api"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	"github.com/spela/server/internal/websocket"
)

func main() {
	// Configuration from environment variables with sensible defaults
	port := getEnv("SPELA_PORT", "8080")
	dbPath := getEnv("SPELA_DB_PATH", "spela.db")
	jwtSecret := getEnv("SPELA_JWT_SECRET", "change-me-in-production")
	gameDirsRaw := getEnv("SPELA_GAME_DIRS", "./games")
	saveDir := getEnv("SPELA_SAVE_DIR", "./saves")
	coreDir := getEnv("SPELA_CORE_DIR", "./cores")
	scraperDevID := getEnv("SPELA_SCRAPER_DEV_ID", "")
	scraperDevPass := getEnv("SPELA_SCRAPER_DEV_PASS", "")
	scraperUser := getEnv("SPELA_SCRAPER_USER", "")
	scraperUserPass := getEnv("SPELA_SCRAPER_USER_PASS", "")
	wsOriginsRaw := getEnv("SPELA_WS_ORIGINS", "")
	corsOriginsRaw := getEnv("SPELA_CORS_ORIGINS", "")

	gameDirs := strings.Split(gameDirsRaw, ",")
	var wsOrigins []string
	if wsOriginsRaw != "" {
		wsOrigins = strings.Split(wsOriginsRaw, ",")
	}
	var corsOrigins []string
	if corsOriginsRaw != "" {
		corsOrigins = strings.Split(corsOriginsRaw, ",")
	}

	// Initialize structured logging
	slog.SetDefault(slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})))

	// Warn about insecure default JWT secret
	if jwtSecret == "change-me-in-production" {
		if os.Getenv("GIN_MODE") == "release" {
			slog.Error("FATAL: using default JWT secret in release mode; set SPELA_JWT_SECRET")
			os.Exit(1)
		}
		slog.Warn("using default JWT secret - set SPELA_JWT_SECRET for production")
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

	// Initialize storage
	store, err := storage.NewStorage(saveDir, coreDir)
	if err != nil {
		slog.Error("failed to initialize storage", "error", err)
		os.Exit(1)
	}

	// Initialize scanner
	gameScanner := scanner.NewScanner(database, gameDirs)

	// Initialize scraper
	metaScraper := scraper.NewScraper(database)
	if scraperDevID != "" {
		metaScraper.Configure(scraperDevID, scraperDevPass, scraperUser, scraperUserPass)
		slog.Info("ScreenScraper integration configured")
	}

	// Initialize WebSocket hub
	hub := websocket.NewHub(wsOrigins)
	go hub.Run()

	// Create router
	router := api.NewRouter(api.Config{
		DB:          database,
		JWTSecret:   jwtSecret,
		GameDirs:    gameDirs,
		Storage:     store,
		Scanner:     gameScanner,
		Scraper:     metaScraper,
		Hub:         hub,
		CoreDir:     coreDir,
		CORSOrigins: corsOrigins,
	})

	slog.Info("server listening", "port", port)
	if err := router.Run(":" + port); err != nil {
		slog.Error("server failed", "error", err)
		os.Exit(1)
	}
}

func getEnv(key, defaultVal string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return defaultVal
}
