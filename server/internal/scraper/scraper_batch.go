package scraper

import (
	"fmt"
	"log/slog"
	"net/http"

	"github.com/spela/server/internal/db"
)

// EnrichProgress holds progress information for a metadata enrichment operation.
type EnrichProgress struct {
	Current   int    `json:"current"`
	Total     int    `json:"total"`
	GameName  string `json:"gameName"`
	Successes int    `json:"successes"`
	Failures  int    `json:"failures"`
}

// EnrichAll enriches games with IGDB metadata.
// Mode "missing" (default) only enriches games without themes; "all" re-enriches everything.
// If onProgress is non-nil, it is called after each game.
func (s *Scraper) EnrichAll(mode string, onProgress func(EnrichProgress)) (int, int, error) {
	var games []db.Game
	switch mode {
	case "all":
		if err := s.DB.Where("scraper_id LIKE 'igdb:%'").Find(&games).Error; err != nil {
			return 0, 0, fmt.Errorf("loading IGDB-matched games: %w", err)
		}
	default: // "missing"
		// Games with IGDB match but no themes (never enriched)
		if err := s.DB.Where("scraper_id LIKE 'igdb:%'").
			Where("id NOT IN (SELECT DISTINCT game_id FROM game_themes)").
			Find(&games).Error; err != nil {
			return 0, 0, fmt.Errorf("loading unenriched games: %w", err)
		}
	}

	total := len(games)
	successes := 0
	failures := 0

	// Track series/franchises we've populated to avoid redundant API calls
	populatedSeries := make(map[uint]bool)
	populatedFranchises := make(map[uint]bool)

	for i := range games {
		if err := s.EnrichGameOnly(&games[i]); err != nil {
			slog.Warn("enrichment failed for game", "game", games[i].Title, "error", err)
			failures++
		} else {
			successes++

			// Check if this game's series needs full population
			var entries []db.GameSeriesEntry
			s.DB.Where("game_id = ?", games[i].ID).Find(&entries)
			for _, entry := range entries {
				if !populatedSeries[entry.SeriesID] {
					var series db.GameSeries
					if err := s.DB.First(&series, entry.SeriesID).Error; err == nil {
						// Only populate if series has few entries (likely not yet populated)
						var entryCount int64
						s.DB.Model(&db.GameSeriesEntry{}).Where("series_id = ?", series.ID).Count(&entryCount)
						if entryCount <= 1 {
							if popErr := s.PopulateSeriesEntries(&series); popErr != nil {
								slog.Warn("failed to populate series entries", "series", series.Name, "error", popErr)
							}
						}
						populatedSeries[entry.SeriesID] = true
					}
				}
			}

			// Check if this game's franchises need full population
			var gameFranchises []db.GameFranchise
			s.DB.Where("game_id = ?", games[i].ID).Find(&gameFranchises)
			for _, gf := range gameFranchises {
				var group db.GameFranchiseGroup
				if err := s.DB.Where("igdb_franchise_id = ?", gf.IGDBFranchiseID).First(&group).Error; err == nil {
					if !populatedFranchises[group.ID] {
						var entryCount int64
						s.DB.Model(&db.GameFranchiseEntry{}).Where("franchise_group_id = ?", group.ID).Count(&entryCount)
						if entryCount <= 1 {
							if popErr := s.PopulateFranchiseEntries(&group); popErr != nil {
								slog.Warn("failed to populate franchise entries", "franchise", group.Name, "error", popErr)
							}
						}
						populatedFranchises[group.ID] = true
					}
				}
			}
		}

		if onProgress != nil {
			onProgress(EnrichProgress{
				Current:   i + 1,
				Total:     total,
				GameName:  games[i].Title,
				Successes: successes,
				Failures:  failures,
			})
		}
	}

	return successes, total, nil
}

// downloadExternalImage downloads an image from an external URL and saves it locally.
func (s *Scraper) downloadExternalImage(imageURL, subpath string) string {
	resp, err := s.HTTPClient.Get(imageURL)
	if err != nil {
		slog.Debug("failed to fetch external image", "url", imageURL, "error", err)
		return ""
	}

	if resp.StatusCode != http.StatusOK {
		resp.Body.Close()
		slog.Debug("external image not found", "url", imageURL, "status", resp.StatusCode)
		return ""
	}

	savedPath, err := s.Storage.WriteImage(subpath, resp.Body)
	resp.Body.Close()
	if err != nil {
		slog.Warn("failed to save external image", "subpath", subpath, "error", err)
		return ""
	}

	slog.Debug("downloaded external image", "url", imageURL)
	return savedPath
}

// scrapeSteamGridDBArtwork fetches artwork from SteamGridDB and saves it to the DB.
// This is best-effort: if the API key is not configured, or SteamGridDB returns no results,
// the game scrape still succeeds.
func (s *Scraper) scrapeSteamGridDBArtwork(game *db.Game, console db.Console) {
	if s.SteamGridDBClient == nil {
		return
	}

	// Check if artwork already exists for this game
	var existing db.GameArtwork
	if err := s.DB.Where("game_id = ?", game.ID).First(&existing).Error; err == nil {
		// Artwork already exists, skip
		return
	}

	artwork, err := s.SteamGridDBClient.GetBestArtwork(game.Title, console.Abbreviation)
	if err != nil {
		slog.Debug("SteamGridDB artwork fetch failed", "game", game.Title, "error", err)
		return
	}

	artwork.GameID = game.ID
	if err := s.DB.Create(artwork).Error; err != nil {
		slog.Warn("failed to save SteamGridDB artwork", "game", game.Title, "error", err)
		return
	}

	slog.Info("saved SteamGridDB artwork", "game", game.Title, "steamGridDbId", artwork.SteamGridDBID)
}

// ConfigureSteamGridDB sets up the SteamGridDB client from the given API key.
// If apiKey is empty, the client is set to nil (disabled).
func (s *Scraper) ConfigureSteamGridDB(apiKey string) {
	if apiKey == "" {
		s.SteamGridDBClient = nil
		return
	}
	if s.SteamGridDBClient != nil && s.SteamGridDBClient.APIKey == apiKey {
		return
	}
	s.SteamGridDBClient = NewSteamGridDBClient(apiKey)
}

// ScrapeProgress holds progress information for a bulk scrape operation.
type ScrapeProgress struct {
	Current  int    `json:"current"`
	Total    int    `json:"total"`
	GameName string `json:"gameName"`
	Successes int   `json:"successes"`
	Failures  int   `json:"failures"`
}

// ScrapeAll fetches metadata for games.
// Mode controls which games are scraped:
//   - "new": only games without scraper IDs (default)
//   - "all": re-scrape every game
//   - "fallback": re-scrape games that were only scraped via LibRetro fallback (no IGDB match)
//
// If onProgress is non-nil, it is called after each game attempt with the current progress.
// Returns the number of successes, the total number of games attempted, and any error.
func (s *Scraper) ScrapeAll(mode string, onProgress func(ScrapeProgress)) (int, int, error) {
	var games []db.Game
	switch mode {
	case "all":
		if err := s.DB.Find(&games).Error; err != nil {
			return 0, 0, fmt.Errorf("loading all games: %w", err)
		}
	case "fallback":
		if err := s.DB.Where("scraper_id = 'libretro'").Find(&games).Error; err != nil {
			return 0, 0, fmt.Errorf("loading fallback-scraped games: %w", err)
		}
	default:
		if err := s.DB.Where("scraper_id = '' OR scraper_id IS NULL").Find(&games).Error; err != nil {
			return 0, 0, fmt.Errorf("loading unscraped games: %w", err)
		}
	}

	total := len(games)
	successes := 0
	failures := 0
	for i := range games {
		if err := s.ScrapeGame(&games[i]); err != nil {
			slog.Warn("failed to scrape game", "game", games[i].Title, "error", err)
			failures++
		} else {
			successes++
		}

		if onProgress != nil {
			onProgress(ScrapeProgress{
				Current:   i + 1,
				Total:     total,
				GameName:  games[i].Title,
				Successes: successes,
				Failures:  failures,
			})
		}
	}

	return successes, total, nil
}
