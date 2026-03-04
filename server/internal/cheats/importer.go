package cheats

import (
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// DefaultBaseURL is the base URL for the libretro cheat database.
const DefaultBaseURL = "https://raw.githubusercontent.com/libretro/libretro-database/master/cht"

// systemFolders maps console folder names to libretro-database system folder names.
var systemFolders = map[string]string{
	"nes":          "Nintendo - Nintendo Entertainment System",
	"snes":         "Nintendo - Super Nintendo Entertainment System",
	"gb":           "Nintendo - Game Boy",
	"gbc":          "Nintendo - Game Boy Color",
	"gba":          "Nintendo - Game Boy Advance",
	"n64":          "Nintendo - Nintendo 64",
	"nds":          "Nintendo - Nintendo DS",
	"genesis":      "Sega - Mega Drive - Genesis",
	"mastersystem": "Sega - Master System - Mark III",
	"gamegear":     "Sega - Game Gear",
	"psx":          "Sony - PlayStation",
	"psp":          "Sony - PlayStation Portable",
	"tg16":         "NEC - PC Engine - TurboGrafx 16",
	"atari2600":    "Atari - 2600",
}

// CheatEntry holds a parsed cheat code entry.
type CheatEntry struct {
	Index       int
	Description string
	Code        string
}

// ParseChtContent parses the libretro .cht file format.
// Lines look like: cheat0_desc = "Description" or cheat0_code = "CODE"
func ParseChtContent(content string) ([]CheatEntry, error) {
	// Map from index -> partial CheatEntry
	byIndex := map[int]*CheatEntry{}

	for _, rawLine := range strings.Split(content, "\n") {
		line := strings.TrimSpace(rawLine)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		eqIdx := strings.Index(line, "=")
		if eqIdx < 0 {
			continue
		}
		key := strings.TrimSpace(line[:eqIdx])
		val := strings.TrimSpace(line[eqIdx+1:])
		// Strip surrounding quotes
		if len(val) >= 2 && val[0] == '"' && val[len(val)-1] == '"' {
			val = val[1 : len(val)-1]
		}

		// Parse key: cheatN_desc or cheatN_code
		if !strings.HasPrefix(key, "cheat") {
			continue
		}
		rest := key[5:] // strip "cheat"
		underIdx := strings.Index(rest, "_")
		if underIdx < 0 {
			continue
		}
		idxStr := rest[:underIdx]
		field := rest[underIdx+1:]
		idx, err := strconv.Atoi(idxStr)
		if err != nil {
			continue
		}

		if byIndex[idx] == nil {
			byIndex[idx] = &CheatEntry{Index: idx}
		}

		switch field {
		case "desc":
			byIndex[idx].Description = val
		case "code":
			byIndex[idx].Code = val
		}
	}

	// Collect entries that have both description and code
	var result []CheatEntry
	for _, entry := range byIndex {
		if entry.Description != "" && entry.Code != "" {
			result = append(result, *entry)
		}
	}

	// Sort by index for stable ordering
	for i := 0; i < len(result); i++ {
		for j := i + 1; j < len(result); j++ {
			if result[j].Index < result[i].Index {
				result[i], result[j] = result[j], result[i]
			}
		}
	}
	return result, nil
}

// ImportCheatsForGame fetches and imports cheat codes for a single game.
// Returns nil if the game's console has no cheat folder or if the game has no .cht file.
func ImportCheatsForGame(database *gorm.DB, game db.Game, console db.Console, baseURL string, httpClient *http.Client) error {
	if game.VerificationStatus != "verified" {
		return nil
	}

	folder, ok := systemFolders[console.FolderName]
	if !ok {
		return nil // No cheats for this console
	}

	// Strip extension from file name to build the cheat file name
	baseName := strings.TrimSuffix(game.FileName, filepath.Ext(game.FileName))
	if baseName == "" {
		return nil
	}

	url := fmt.Sprintf("%s/%s/%s.cht", baseURL, folder, baseName)

	resp, err := httpClient.Get(url)
	if err != nil {
		return fmt.Errorf("fetching cheats for %s: %w", game.FileName, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return nil // No cheats for this game, not an error
	}
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("unexpected status %d for %s", resp.StatusCode, url)
	}

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("reading cheat content for %s: %w", game.FileName, err)
	}

	entries, err := ParseChtContent(string(data))
	if err != nil {
		return fmt.Errorf("parsing cheats for %s: %w", game.FileName, err)
	}

	// Upsert each cheat entry
	for _, entry := range entries {
		cheat := db.CheatCode{
			GameID:      game.ID,
			CheatIndex:  entry.Index,
			Description: entry.Description,
			Code:        entry.Code,
		}
		if err := database.Clauses(clause.OnConflict{
			Columns:   []clause.Column{{Name: "game_id"}, {Name: "cheat_index"}},
			DoUpdates: clause.AssignmentColumns([]string{"description", "code"}),
		}).Create(&cheat).Error; err != nil {
			slog.Warn("failed to upsert cheat", "gameID", game.ID, "index", entry.Index, "error", err)
		}
	}

	if len(entries) > 0 {
		slog.Info("imported cheats", "game", game.Title, "count", len(entries))
	}
	return nil
}

// StartAutoImport checks if cheats have already been imported and, if not,
// spawns a goroutine that imports cheats for all verified games at startup.
func StartAutoImport(database *gorm.DB) {
	var count int64
	database.Model(&db.CheatCode{}).Count(&count)
	if count > 0 {
		slog.Info("cheats already imported, skipping auto-import", "count", count)
		return
	}

	go func() {
		slog.Info("cheat auto-import starting")
		if err := ImportAllCheats(database, DefaultBaseURL); err != nil {
			slog.Warn("cheat auto-import failed", "error", err)
		} else {
			var imported int64
			database.Model(&db.CheatCode{}).Count(&imported)
			slog.Info("cheat auto-import complete", "imported", imported)
		}
	}()
}

// ImportAllCheats imports cheat codes for all games that have a matching .cht file.
func ImportAllCheats(database *gorm.DB, baseURL string) error {
	// Load all games with their consoles
	var games []db.Game
	if err := database.Preload("Console").Find(&games).Error; err != nil {
		return fmt.Errorf("loading games: %w", err)
	}

	httpClient := &http.Client{Timeout: 15 * time.Second}
	for _, game := range games {
		if err := ImportCheatsForGame(database, game, game.Console, baseURL, httpClient); err != nil {
			slog.Warn("cheat import error", "game", game.Title, "error", err)
			// Continue with other games
		}
	}
	return nil
}
