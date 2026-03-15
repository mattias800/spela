package scraper

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"

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

// DownloadExternalImage downloads an image from an external URL and saves it locally.
// Returns the relative path for DB storage, or "" on failure.
func (s *Scraper) DownloadExternalImage(imageURL, subpath string) string {
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
	gameIDStr := strconv.FormatUint(uint64(game.ID), 10)

	// Download images locally instead of storing CDN URLs
	if artwork.HeroURL != "" {
		if path := s.DownloadExternalImage(artwork.HeroURL, fmt.Sprintf("%s/%s/artwork-hero.jpg", console.Abbreviation, gameIDStr)); path != "" {
			artwork.HeroURL = path
		}
	}
	if artwork.GridURL != "" {
		if path := s.DownloadExternalImage(artwork.GridURL, fmt.Sprintf("%s/%s/artwork-grid.jpg", console.Abbreviation, gameIDStr)); path != "" {
			artwork.GridURL = path
		}
	}
	if artwork.LogoURL != "" {
		if path := s.DownloadExternalImage(artwork.LogoURL, fmt.Sprintf("%s/%s/artwork-logo.png", console.Abbreviation, gameIDStr)); path != "" {
			artwork.LogoURL = path
		}
	}
	if artwork.IconURL != "" {
		if path := s.DownloadExternalImage(artwork.IconURL, fmt.Sprintf("%s/%s/artwork-icon.png", console.Abbreviation, gameIDStr)); path != "" {
			artwork.IconURL = path
		}
	}

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
	Current     int    `json:"current"`
	Total       int    `json:"total"`
	GameID      uint   `json:"gameId"`
	GameName    string `json:"gameName"`
	ConsoleName string `json:"consoleName"`
	ConsoleAbbr string `json:"consoleAbbr"`
	Successes   int    `json:"successes"`
	Failures    int    `json:"failures"`
	Verified    int    `json:"verified"`
}

// ScrapeAll fetches metadata for games.
// Mode controls which games are scraped:
//   - "new": only games without scraper IDs (default)
//   - "all": re-scrape every game
//   - "fallback": re-scrape games that were only scraped via LibRetro fallback (no IGDB match)
//
// If consoleID is non-zero, only games belonging to that console are scraped.
// If onProgress is non-nil, it is called after each game attempt with the current progress.
// Returns the number of successes, the total number of games attempted, and any error.
func (s *Scraper) ScrapeAll(ctx context.Context, mode string, consoleID uint, onProgress func(ScrapeProgress)) (int, int, error) {
	var games []db.Game
	q := s.DB.Preload("Console")
	if consoleID > 0 {
		q = q.Where("console_id = ?", consoleID)
	}
	switch mode {
	case "all":
		if err := q.Find(&games).Error; err != nil {
			return 0, 0, fmt.Errorf("loading all games: %w", err)
		}
	case "fallback":
		if err := q.Where("scraper_id = 'libretro'").Find(&games).Error; err != nil {
			return 0, 0, fmt.Errorf("loading fallback-scraped games: %w", err)
		}
	default:
		if err := q.Where("scraper_id = '' OR scraper_id IS NULL").Find(&games).Error; err != nil {
			return 0, 0, fmt.Errorf("loading unscraped games: %w", err)
		}
	}

	total := len(games)
	successes := 0
	failures := 0
	verified := 0

	progress := func(i int, game *db.Game) ScrapeProgress {
		return ScrapeProgress{
			Current:     i + 1,
			Total:       total,
			GameID:      game.ID,
			GameName:    game.Title,
			ConsoleName: game.Console.Name,
			ConsoleAbbr: game.Console.Abbreviation,
			Successes:   successes,
			Failures:    failures,
			Verified:    verified,
		}
	}

	// Track groups we've already scraped a primary for, so we can propagate
	// metadata to other variants in the same group instead of re-scraping.
	scrapedGroups := make(map[string]uint) // "consoleID:groupKey" -> scraped game ID
	for i := range games {
		// Check for cancellation before each game
		if ctx.Err() != nil {
			slog.Info("scrape cancelled", "completed", i, "total", total)
			return successes, total, ctx.Err()
		}

		game := &games[i]

		// Smart scraping: if this game belongs to a variant group, try to
		// propagate metadata from an already-scraped sibling instead of
		// hitting external APIs again.
		if game.GroupKey != "" && mode != "all" {
			groupID := fmt.Sprintf("%d:%s", game.ConsoleID, game.GroupKey)

			// Check if we've already scraped a game in this group during this run
			if _, done := scrapedGroups[groupID]; done {
				if s.propagateGroupMetadata(game) {
					successes++
					if onProgress != nil {
						onProgress(progress(i, game))
					}
					continue
				}
			}

			// Check if any sibling in the DB already has metadata
			if s.propagateGroupMetadata(game) {
				scrapedGroups[groupID] = game.ID
				successes++
				if onProgress != nil {
					onProgress(progress(i, game))
				}
				continue
			}
		}

		if err := s.ScrapeGame(game); err != nil {
			slog.Warn("failed to scrape game", "game", game.Title, "error", err)
			failures++
		} else {
			successes++
			if game.VerificationStatus == "verified" {
				verified++
			}
			// After scraping, propagate to other unscraped variants in the group
			if game.GroupKey != "" {
				groupID := fmt.Sprintf("%d:%s", game.ConsoleID, game.GroupKey)
				scrapedGroups[groupID] = game.ID
				s.propagateToGroup(game)
			}
		}

		if onProgress != nil {
			onProgress(progress(i, game))
		}
	}

	return successes, total, nil
}

// propagateGroupMetadata copies metadata from a scraped sibling in the same
// variant group to the given game. Returns true if metadata was propagated.
func (s *Scraper) propagateGroupMetadata(game *db.Game) bool {
	if game.GroupKey == "" {
		return false
	}

	// Find a sibling with metadata (has Description or CoverURL and has been scraped)
	var sibling db.Game
	err := s.DB.Where("console_id = ? AND group_key = ? AND id != ? AND scraper_id != '' AND scraper_id IS NOT NULL AND (description != '' OR cover_url != '')",
		game.ConsoleID, game.GroupKey, game.ID).
		First(&sibling).Error
	if err != nil {
		return false
	}

	// Copy metadata fields that are shared across variants (not file-specific)
	if game.Description == "" && sibling.Description != "" {
		game.Description = sibling.Description
	}
	if game.CoverURL == "" && sibling.CoverURL != "" {
		game.CoverURL = sibling.CoverURL
	}
	if game.IGDBCoverURL == "" && sibling.IGDBCoverURL != "" {
		game.IGDBCoverURL = sibling.IGDBCoverURL
	}
	if game.LibRetroCoverURL == "" && sibling.LibRetroCoverURL != "" {
		game.LibRetroCoverURL = sibling.LibRetroCoverURL
	}
	if game.Developer == "" && sibling.Developer != "" {
		game.Developer = sibling.Developer
	}
	if game.Publisher == "" && sibling.Publisher != "" {
		game.Publisher = sibling.Publisher
	}
	if game.Genre == "" && sibling.Genre != "" {
		game.Genre = sibling.Genre
	}
	if game.GameModes == "" && sibling.GameModes != "" {
		game.GameModes = sibling.GameModes
	}
	if game.Rating == 0 && sibling.Rating != 0 {
		game.Rating = sibling.Rating
	}
	if game.ReleaseDate == "" && sibling.ReleaseDate != "" {
		game.ReleaseDate = sibling.ReleaseDate
	}
	if game.Players == 0 && sibling.Players != 0 {
		game.Players = sibling.Players
	}
	if game.Storyline == "" && sibling.Storyline != "" {
		game.Storyline = sibling.Storyline
	}
	if game.TotalRating == 0 && sibling.TotalRating != 0 {
		game.TotalRating = sibling.TotalRating
	}
	if game.TotalRatingCount == 0 && sibling.TotalRatingCount != 0 {
		game.TotalRatingCount = sibling.TotalRatingCount
	}

	// Mark as propagated (use the sibling's scraper ID with a propagated suffix)
	if game.ScraperID == "" {
		game.ScraperID = sibling.ScraperID + ":propagated"
	}

	game.ScrapeAttempts++

	if err := s.DB.Save(game).Error; err != nil {
		slog.Warn("failed to save propagated metadata", "game", game.Title, "error", err)
		return false
	}

	slog.Info("propagated metadata from group sibling", "game", game.Title, "from", sibling.Title)
	return true
}

// propagateToGroup copies metadata from a freshly scraped game to all unscraped
// siblings in the same variant group.
func (s *Scraper) propagateToGroup(source *db.Game) {
	if source.GroupKey == "" {
		return
	}

	var siblings []db.Game
	s.DB.Where("console_id = ? AND group_key = ? AND id != ? AND (scraper_id = '' OR scraper_id IS NULL)",
		source.ConsoleID, source.GroupKey, source.ID).
		Find(&siblings)

	for i := range siblings {
		s.propagateGroupMetadata(&siblings[i])
	}
}
