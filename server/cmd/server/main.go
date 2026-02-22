package main

import (
	"log/slog"
	"os"
	"strconv"
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
	imageDir := getEnv("SPELA_IMAGE_DIR", "./images")
	biosDir := getEnv("SPELA_BIOS_DIR", "./bios")
	datDir := getEnv("SPELA_DAT_DIR", "./dats")
	wsOriginsRaw := getEnv("SPELA_WS_ORIGINS", "")
	corsOriginsRaw := getEnv("SPELA_CORS_ORIGINS", "")
	challengeRateLimitRaw := getEnv("SPELA_CHALLENGE_RATE_LIMIT_SEC", "30")

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
	metaScraper := scraper.NewScraper(database, store, datDir)
	go metaScraper.DATCache.RefreshAll()

	// Initialize WebSocket hub
	hub := websocket.NewHub(wsOrigins)
	go hub.Run()

	// Initialize Netplay WebSocket hub
	netplayHub := websocket.NewNetplayHub(wsOrigins)
	netplayHub.StartCleanup(database)

	// Create router
	router := api.NewRouter(api.Config{
		DB:          database,
		JWTSecret:   jwtSecret,
		GameDirs:    gameDirs,
		Storage:     store,
		Scanner:     gameScanner,
		Scraper:     metaScraper,
		Hub:         hub,
		NetplayHub:  netplayHub,
		CoreDir:     coreDir,
		CORSOrigins:                  corsOrigins,
		ChallengeAttemptRateLimitSec: challengeRateLimit,
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
