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
	if err := db.SeedConsoles(database); err != nil {
		slog.Error("failed to seed consoles", "error", err)
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
		Email:        "admin@spela.local",
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
		Email:        "player@spela.local",
		PasswordHash: userHash,
		Role:         "user",
	}
	result = database.Where("username = ?", player.Username).FirstOrCreate(&player)
	if result.RowsAffected > 0 {
		slog.Info("created demo user", "username", player.Username, "password", "player123")
	} else {
		slog.Info("demo user already exists", "username", player.Username)
	}

	// Create sample games for a few consoles
	type sampleGame struct {
		abbrev      string
		title       string
		fileName    string
		description string
		developer   string
		genre       string
		players     int
	}

	samples := []sampleGame{
		{"NES", "Super Mario Bros.", "Super Mario Bros.nes", "The classic platformer that started it all.", "Nintendo", "Platformer", 2},
		{"NES", "The Legend of Zelda", "Legend of Zelda, The.nes", "An epic action-adventure through Hyrule.", "Nintendo", "Action-Adventure", 1},
		{"NES", "Mega Man 2", "Mega Man 2.nes", "Run, jump, and shoot through eight robot master stages.", "Capcom", "Platformer", 1},
		{"SNES", "Chrono Trigger", "Chrono Trigger.sfc", "A timeless JRPG spanning multiple eras.", "Square", "RPG", 1},
		{"SNES", "Super Metroid", "Super Metroid.sfc", "Explore the depths of planet Zebes.", "Nintendo", "Action-Adventure", 1},
		{"SNES", "Final Fantasy VI", "Final Fantasy VI.smc", "An epic tale of rebellion against an empire.", "Square", "RPG", 1},
		{"GB", "Pokemon Red", "Pokemon Red.gb", "Catch, train, and battle Pokemon on your journey.", "Game Freak", "RPG", 1},
		{"GBA", "Pokemon Emerald", "Pokemon Emerald.gba", "The definitive Hoenn Pokemon experience.", "Game Freak", "RPG", 1},
		{"GBA", "Metroid Fusion", "Metroid Fusion.gba", "Samus faces a terrifying new parasite threat.", "Nintendo", "Action-Adventure", 1},
		{"N64", "Super Mario 64", "Super Mario 64.z64", "Mario's groundbreaking 3D platforming debut.", "Nintendo", "Platformer", 1},
		{"N64", "The Legend of Zelda: Ocarina of Time", "Zelda - Ocarina of Time.z64", "The legendary adventure through time.", "Nintendo", "Action-Adventure", 1},
		{"GEN", "Sonic the Hedgehog 2", "Sonic the Hedgehog 2.md", "Blast through zones at supersonic speed.", "Sega", "Platformer", 2},
	}

	for _, sg := range samples {
		var console db.Console
		if err := database.Where("abbreviation = ?", sg.abbrev).First(&console).Error; err != nil {
			slog.Warn("console not found, skipping game", "abbreviation", sg.abbrev, "game", sg.title)
			continue
		}

		game := db.Game{
			ConsoleID:   console.ID,
			Title:       sg.title,
			FileName:    sg.fileName,
			FilePath:    "/demo/" + sg.abbrev + "/" + sg.fileName,
			FileSize:    262144, // placeholder 256KB
			Description: sg.description,
			Developer:   sg.developer,
			Genre:       sg.genre,
			Players:     sg.players,
		}
		result := database.Where("title = ? AND console_id = ?", game.Title, game.ConsoleID).FirstOrCreate(&game)
		if result.RowsAffected > 0 {
			slog.Info("created sample game", "title", game.Title, "console", sg.abbrev)
		}
	}

	// Add some favorites for the demo user
	var allGames []db.Game
	database.Limit(3).Find(&allGames)
	for _, g := range allGames {
		fav := db.Favorite{UserID: player.ID, GameID: g.ID}
		database.Where("user_id = ? AND game_id = ?", fav.UserID, fav.GameID).FirstOrCreate(&fav)
	}
	slog.Info("added demo favorites for player user")

	// Create some libretro core entries
	cores := []db.Core{
		{Name: "nestopia", DisplayName: "Nestopia UE", Description: "Accurate NES/Famicom emulator", Platforms: "windows,linux,macos,android"},
		{Name: "snes9x", DisplayName: "Snes9x", Description: "Portable SNES emulator", Platforms: "windows,linux,macos,android"},
		{Name: "gambatte", DisplayName: "Gambatte", Description: "Game Boy / Game Boy Color emulator", Platforms: "windows,linux,macos,android"},
		{Name: "mgba", DisplayName: "mGBA", Description: "Game Boy Advance emulator", Platforms: "windows,linux,macos,android"},
		{Name: "mupen64plus_next", DisplayName: "Mupen64Plus-Next", Description: "Nintendo 64 emulator", Platforms: "windows,linux,macos,android"},
		{Name: "genesis_plus_gx", DisplayName: "Genesis Plus GX", Description: "Sega 8/16-bit emulator", Platforms: "windows,linux,macos,android"},
		{Name: "beetle_psx_hw", DisplayName: "Beetle PSX HW", Description: "PlayStation emulator with hardware rendering", Platforms: "windows,linux,macos"},
		{Name: "desmume", DisplayName: "DeSmuME", Description: "Nintendo DS emulator", Platforms: "windows,linux,macos"},
	}
	for _, core := range cores {
		result := database.Where("name = ?", core.Name).FirstOrCreate(&core)
		if result.RowsAffected > 0 {
			slog.Info("created core entry", "name", core.Name)
		}
	}

	slog.Info("seed complete",
		"hint", "Login with admin/admin123 (admin) or player/player123 (user)",
	)
}
