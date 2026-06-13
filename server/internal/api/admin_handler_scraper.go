package api

import (
	"fmt"
	"os"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"gorm.io/gorm"
)

// collectGameIDs builds the game query based on filters and returns matching IDs.
func (h *AdminHandler) collectGameIDs(mode string, consoleID uint, source, status string) ([]uint, error) {
	q := h.DB.Model(&db.Game{})
	if consoleID > 0 {
		q = q.Where("console_id = ?", consoleID)
	}

	if source != "" && status != "" {
		cooldownCutoff := time.Now().AddDate(0, 0, -7)
		if status == "not_attempted" {
			q = q.Where("id NOT IN (SELECT game_id FROM game_scrape_results WHERE source = ?)", source)
		} else {
			subQ := "id IN (SELECT game_id FROM game_scrape_results WHERE source = ? AND status = ?"
			args := []interface{}{source, status}
			if status == "not_found" || status == "error" {
				subQ += " AND (last_attempt_at IS NULL OR last_attempt_at < ?)"
				args = append(args, cooldownCutoff)
			}
			subQ += ")"
			q = q.Where(subQ, args...)
		}
	} else {
		switch mode {
		case "all":
			// no filter
		case "fallback":
			q = q.Where("scraper_id = 'libretro'")
		case "ra":
			// Games on playable consoles that either:
			//   - Haven't been checked against RA yet (RAHashChecked=false), OR
			//   - Have a known RA game ID but no fresh achievement cache
			// Excludes known non-matches (RAHashChecked=true, RAGameID=0).
			q = q.Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.playable = ?", true).
				Where(`(games.ra_hash_checked = ? OR (games.ra_game_id > 0 AND games.ra_game_id NOT IN (
					SELECT ra_game_id FROM game_achievement_caches
					WHERE ra_game_id = games.ra_game_id AND cached_at > ?
				)))`, false, time.Now().Add(-24*time.Hour))
		default:
			q = q.Where("scraper_id = '' OR scraper_id IS NULL")
		}
	}

	var gameIDs []uint
	if err := q.Pluck("id", &gameIDs).Error; err != nil {
		return nil, fmt.Errorf("collecting game IDs: %w", err)
	}
	return gameIDs, nil
}

// tryConfigureIGDB loads IGDB credentials and configures the scraper's IGDB client.
// Environment variables take precedence over database settings.
func (h *AdminHandler) tryConfigureIGDB() {
	clientID, clientSecret := igdbCredentials(h.DB)

	if clientID == "" || clientSecret == "" {
		return
	}

	// Skip re-creation if credentials haven't changed
	if h.Scraper.IGDBClient != nil &&
		h.Scraper.IGDBClient.ClientID == clientID &&
		h.Scraper.IGDBClient.ClientSecret == clientSecret {
		return
	}

	// Close old client to release its rate limiter ticker
	if h.Scraper.IGDBClient != nil {
		h.Scraper.IGDBClient.Close()
	}
	h.Scraper.IGDBClient = igdb.NewClient(clientID, clientSecret)
}

// tryConfigureSteamGridDB loads the SteamGridDB API key from environment or database
// settings and configures the scraper's SteamGridDB client.
func (h *AdminHandler) tryConfigureSteamGridDB() {
	apiKey := steamGridDBAPIKey(h.DB)
	h.Scraper.ConfigureSteamGridDB(apiKey)
}

// steamGridDBAPIKey returns the SteamGridDB API key from the environment
// variable SPELA_STEAMGRIDDB_API_KEY, falling back to the database setting.
func steamGridDBAPIKey(database *gorm.DB) string {
	if key := os.Getenv("SPELA_STEAMGRIDDB_API_KEY"); key != "" {
		return key
	}
	var setting db.ServerSetting
	if err := database.Where("key = ?", "steamgriddb_api_key").First(&setting).Error; err != nil {
		return ""
	}
	return decryptSecretSetting(setting.Value)
}

// igdbCredentials returns the IGDB client ID and secret.
// Environment variables SPELA_IGDB_CLIENT_ID / SPELA_IGDB_CLIENT_SECRET take
// precedence over database settings.
func igdbCredentials(database *gorm.DB) (clientID, clientSecret string) {
	clientID = os.Getenv("SPELA_IGDB_CLIENT_ID")
	clientSecret = os.Getenv("SPELA_IGDB_CLIENT_SECRET")
	if clientID != "" && clientSecret != "" {
		return clientID, clientSecret
	}

	var settings []db.ServerSetting
	database.Where("key IN ?", []string{
		"igdb_client_id", "igdb_client_secret",
	}).Find(&settings)

	sm := make(map[string]string)
	for _, s := range settings {
		sm[s.Key] = s.Value
	}
	// igdb_client_id is not secret; igdb_client_secret is encrypted at rest (#1318).
	return sm["igdb_client_id"], decryptSecretSetting(sm["igdb_client_secret"])
}

func SteamGridDBSource(database *gorm.DB) string {
	if os.Getenv("SPELA_STEAMGRIDDB_API_KEY") != "" {
		return "env"
	}

	var count int64
	database.Model(&db.ServerSetting{}).
		Where("key = ? AND value != ''", "steamgriddb_api_key").
		Count(&count)
	if count == 1 {
		return "database"
	}
	return "none"
}

// RASource returns "env" if RA API key is set via environment variables,
// "database" if set via admin settings, or "none" if not configured.
func RASource(database *gorm.DB) string {
	if os.Getenv("SPELA_RA_API_KEY") != "" {
		return "env"
	}
	var count int64
	database.Model(&db.ServerSetting{}).
		Where("key = ? AND value != ''", "ra_api_key").
		Count(&count)
	if count == 1 {
		return "database"
	}
	return "none"
}

// IGDBSource returns "env" if IGDB credentials are set via environment variables,
// "database" if set via admin settings, or "none" if not configured.
func IGDBSource(database *gorm.DB) string {
	if os.Getenv("SPELA_IGDB_CLIENT_ID") != "" && os.Getenv("SPELA_IGDB_CLIENT_SECRET") != "" {
		return "env"
	}

	var count int64
	database.Model(&db.ServerSetting{}).
		Where("key IN ? AND value != ''", []string{"igdb_client_id", "igdb_client_secret"}).
		Count(&count)
	if count == 2 {
		return "database"
	}
	return "none"
}

