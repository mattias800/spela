package main

import (
	"log/slog"
	"os"

	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
)

// seed populates the database with demo data for development.
func main() {
	dbPath := "spela.db"
	if v := os.Getenv("SPELA_DB_PATH"); v != "" {
		dbPath = v
	}

	slog.SetDefault(slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})))

	database, err := db.Initialize(dbPath)
	if err != nil {
		slog.Error("failed to initialize database", "error", err)
		os.Exit(1)
	}

	// Refuse to seed a database that already has users. This tool creates demo
	// accounts with well-known passwords (admin/admin123, player/player123), so
	// running it against a real/populated database would inject weak
	// credentials. Override with SPELA_SEED_FORCE=true for an intentional dev
	// re-seed. (#1330)
	var userCount int64
	if err := database.Model(&db.User{}).Count(&userCount).Error; err != nil {
		slog.Error("failed to count existing users", "error", err)
		os.Exit(1)
	}
	if userCount > 0 && os.Getenv("SPELA_SEED_FORCE") != "true" {
		slog.Error("refusing to seed: database already has users; set SPELA_SEED_FORCE=true to override", "users", userCount)
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

	// Create demo admin user
	adminHash, err := auth.HashPassword("admin123")
	if err != nil {
		slog.Error("failed to hash password", "error", err)
		os.Exit(1)
	}

	admin := db.User{
		Username:     "admin",
		PasswordHash: adminHash,
		Role:         "admin",
	}
	result := database.Where("username = ?", admin.Username).FirstOrCreate(&admin)
	if result.RowsAffected > 0 {
		slog.Info("created admin user", "username", admin.Username, "password", "admin123")
	} else {
		slog.Info("admin user already exists", "username", admin.Username)
	}

	// Create demo regular user
	userHash, err := auth.HashPassword("player123")
	if err != nil {
		slog.Error("failed to hash password", "error", err)
		os.Exit(1)
	}

	player := db.User{
		Username:     "player",
		PasswordHash: userHash,
		Role:         "user",
	}
	result = database.Where("username = ?", player.Username).FirstOrCreate(&player)
	if result.RowsAffected > 0 {
		slog.Info("created demo user", "username", player.Username, "password", "player123")
	} else {
		slog.Info("demo user already exists", "username", player.Username)
	}

	if err := db.SeedCores(database); err != nil {
		slog.Error("failed to seed cores", "error", err)
		os.Exit(1)
	}

	slog.Info("seed complete",
		"hint", "Login with admin/admin123 (admin) or player/player123 (user)",
	)
}
