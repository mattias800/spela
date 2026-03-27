package scraper

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"strings"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scanner"
	"gorm.io/gorm"
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
	// Build base query for counting and pagination
	baseQ := func() *gorm.DB {
		q := s.DB.Model(&db.Game{}).Where("scraper_id LIKE 'igdb:%'")
		if mode != "all" {
			q = q.Where("id NOT IN (SELECT DISTINCT game_id FROM game_themes)")
		}
		return q
	}

	var total64 int64
	if err := baseQ().Count(&total64).Error; err != nil {
		return 0, 0, fmt.Errorf("counting games for enrichment: %w", err)
	}
	total := int(total64)

	successes := 0
	failures := 0
	processed := 0

	// Track series/franchises we've populated to avoid redundant API calls
	populatedSeries := make(map[uint]bool)
	populatedFranchises := make(map[uint]bool)

	const batchSize = 100
	for offset := 0; offset < total; offset += batchSize {
		var batch []db.Game
		if err := baseQ().Limit(batchSize).Offset(offset).Find(&batch).Error; err != nil {
			return successes, total, fmt.Errorf("loading enrichment batch at offset %d: %w", offset, err)
		}

		for i := range batch {
			game := &batch[i]
			processed++

			if err := s.EnrichGameOnly(game); err != nil {
				slog.Warn("enrichment failed for game", "game", game.Title, "error", err)
				failures++
			} else {
				successes++

				// Check if this game's series needs full population
				var entries []db.GameSeriesEntry
				s.DB.Where("game_id = ?", game.ID).Find(&entries)
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
				s.DB.Where("game_id = ?", game.ID).Find(&gameFranchises)
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
					Current:   processed,
					Total:     total,
					GameName:  game.Title,
					Successes: successes,
					Failures:  failures,
				})
			}
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
		errStr := err.Error()
		if strings.Contains(errStr, "no results found") {
			RecordScrapeResult(s.DB, game.ID, "steamgriddb", "not_found", "", "")
		} else {
			RecordScrapeResult(s.DB, game.ID, "steamgriddb", "error", "", errStr)
		}
		return
	}
	if artwork == nil {
		RecordScrapeResult(s.DB, game.ID, "steamgriddb", "not_found", "", "")
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
		RecordScrapeResult(s.DB, game.ID, "steamgriddb", "error", "", err.Error())
		return
	}

	RecordScrapeResult(s.DB, game.ID, "steamgriddb", "matched", "", "")
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
	// Build base query for counting and pagination
	baseQ := func() *gorm.DB {
		q := s.DB.Model(&db.Game{})
		if consoleID > 0 {
			q = q.Where("console_id = ?", consoleID)
		}
		switch mode {
		case "all":
			// no additional filter
		case "fallback":
			q = q.Where("scraper_id = 'libretro'")
		default:
			q = q.Where("scraper_id = '' OR scraper_id IS NULL")
		}
		return q
	}

	var total64 int64
	if err := baseQ().Count(&total64).Error; err != nil {
		return 0, 0, fmt.Errorf("counting games: %w", err)
	}
	total := int(total64)

	// Load consoles into a map to avoid Preload on every batch
	var consoles []db.Console
	s.DB.Find(&consoles)
	consoleMap := make(map[uint]db.Console, len(consoles))
	for _, c := range consoles {
		consoleMap[c.ID] = c
	}

	successes := 0
	failures := 0
	verified := 0
	processed := 0

	progress := func(game *db.Game) ScrapeProgress {
		consoleName := ""
		consoleAbbr := ""
		if c, ok := consoleMap[game.ConsoleID]; ok {
			consoleName = c.Name
			consoleAbbr = c.Abbreviation
		}
		return ScrapeProgress{
			Current:     processed,
			Total:       total,
			GameID:      game.ID,
			GameName:    game.Title,
			ConsoleName: consoleName,
			ConsoleAbbr: consoleAbbr,
			Successes:   successes,
			Failures:    failures,
			Verified:    verified,
		}
	}

	// Track groups we've already scraped a primary for, so we can propagate
	// metadata to other variants in the same group instead of re-scraping.
	scrapedGroups := make(map[string]uint) // "consoleID:groupKey" -> scraped game ID

	const batchSize = 100
	for offset := 0; offset < total; offset += batchSize {
		var batch []db.Game
		if err := baseQ().Limit(batchSize).Offset(offset).Find(&batch).Error; err != nil {
			return successes, total, fmt.Errorf("loading game batch at offset %d: %w", offset, err)
		}

		for i := range batch {
			// Check for cancellation before each game
			if ctx.Err() != nil {
				slog.Info("scrape cancelled", "completed", processed, "total", total)
				return successes, total, ctx.Err()
			}

			game := &batch[i]
			// Attach console from map instead of Preload
			if c, ok := consoleMap[game.ConsoleID]; ok {
				game.Console = c
			}

			processed++

			// Smart scraping: if this game belongs to a variant group, try to
			// propagate metadata from an already-scraped sibling instead of
			// hitting external APIs again.
			// Skip propagation in "all" mode (re-scrape everything) and "fallback"
			// mode (retry IGDB for games that only had LibRetro matches — propagating
			// from other LibRetro siblings would defeat the purpose).
			if game.GroupKey != "" && mode == "new" {
				groupID := fmt.Sprintf("%d:%s", game.ConsoleID, game.GroupKey)

				// Check if we've already scraped a game in this group during this run
				if _, done := scrapedGroups[groupID]; done {
					if s.propagateGroupMetadata(game) {
						successes++
						if onProgress != nil {
							onProgress(progress(game))
						}
						continue
					}
				}

				// Check if any sibling in the DB already has metadata
				if s.propagateGroupMetadata(game) {
					scrapedGroups[groupID] = game.ID
					successes++
					if onProgress != nil {
						onProgress(progress(game))
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
				onProgress(progress(game))
			}
		}
	}

	// Merge games that share the same IGDB ID but have different GroupKeys
	// (e.g., "Sonic CD" USA and "Sonic the Hedgehog CD" Japan).
	// Protected by a title similarity check to prevent false merges.
	if merged, err := scanner.MergeGroupsByIGDBID(s.DB); err != nil {
		slog.Warn("IGDB group merge failed", "error", err)
	} else if merged > 0 {
		slog.Info("merged groups by IGDB ID", "merged", merged)
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
// siblings in the same variant group. Also upgrades LibRetro-only siblings when
// the source has IGDB metadata (so fallback re-scrapes benefit the whole group).
func (s *Scraper) propagateToGroup(source *db.Game) {
	if source.GroupKey == "" {
		return
	}

	var siblings []db.Game
	q := s.DB.Where("console_id = ? AND group_key = ? AND id != ?",
		source.ConsoleID, source.GroupKey, source.ID)

	// If the source has IGDB metadata, also upgrade LibRetro-only siblings
	if strings.HasPrefix(source.ScraperID, "igdb:") {
		q = q.Where("scraper_id = '' OR scraper_id IS NULL OR scraper_id = 'libretro'")
	} else {
		q = q.Where("scraper_id = '' OR scraper_id IS NULL")
	}
	q.Find(&siblings)

	for i := range siblings {
		s.propagateGroupMetadata(&siblings[i])
	}
}
