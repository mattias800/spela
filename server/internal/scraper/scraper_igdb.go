package scraper

import (
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/storage"
)

// ScrapeGame fetches metadata from IGDB (if configured) and images from IGDB/LibRetro.
func (s *Scraper) ScrapeGame(game *db.Game) error {
	// Load console if not preloaded
	var console db.Console
	if game.Console.ID != 0 {
		console = game.Console
	} else {
		if err := s.DB.First(&console, game.ConsoleID).Error; err != nil {
			return fmt.Errorf("loading console for game: %w", err)
		}
	}

	// On re-scrape, clear stale images so fresh ones are downloaded.
	// Remember the admin's manual cover choice so we can restore it after.
	manualOverride := game.CoverManuallySet
	prevCoverSource := ""
	if manualOverride {
		switch game.CoverURL {
		case game.LibRetroCoverURL:
			prevCoverSource = "libretro"
		case game.IGDBCoverURL:
			prevCoverSource = "igdb"
		}
	}
	if game.ScrapeAttempts > 0 {
		game.CoverURL = ""
		game.ScreenshotURL = ""
		game.LibRetroCoverURL = ""
		game.IGDBCoverURL = ""
		// Clear IGDB-sourced scalar metadata so new match can overwrite
		game.Storyline = ""
		game.TotalRating = 0
		game.TotalRatingCount = 0
		game.IGDBUserRating = 0
		game.IGDBUserRatingCount = 0
		game.TimeToBeatHastily = 0
		game.TimeToBeatNormally = 0
		game.TimeToBeatCompletely = 0
		// Delete old normalized screenshots and release dates
		s.DB.Where("game_id = ?", game.ID).Delete(&db.GameScreenshot{})
		s.DB.Where("game_id = ?", game.ID).Delete(&db.GameReleaseDate{})
	}

	// Mark disc-based systems as not applicable for CRC verification
	if DiscBasedSystems[console.Abbreviation] {
		game.VerificationStatus = "not_applicable"
	}

	gameName := gameNameFromFileName(game.FileName)
	gameIDStr := strconv.FormatUint(uint64(game.ID), 10)

	// --- IGDB (primary metadata + images, when configured) ---
	igdbAttempted := false
	if s.IGDBClient != nil && s.IGDBClient.IsConfigured() {
		igdbAttempted = true
		if err := s.scrapeIGDB(game, console, gameIDStr); err != nil {
			slog.Warn("IGDB scrape failed, falling back to LibRetro", "game", game.Title, "error", err)
		}
	}
	if !igdbAttempted {
		slog.Warn("IGDB not configured, scraping with LibRetro only", "game", game.Title)
	}

	// --- LibRetro Thumbnails (preferred for box art, fallback for screenshots) ---
	libRetroSystem, hasLibRetro := AbbreviationToLibRetro[console.Abbreviation]
	if hasLibRetro {
		var libRetroWg sync.WaitGroup
		var libRetroCoverPath string

		// Box art: always try LibRetro (preferred source for box art)
		libRetroWg.Add(1)
		go func() {
			defer libRetroWg.Done()
			boxartSubpath := fmt.Sprintf("%s/%s/boxart-libretro.png", console.Abbreviation, gameIDStr)
			libRetroCoverPath = s.downloadLibRetroImage(libRetroSystem, gameName, "Named_Boxarts", boxartSubpath)
		}()

		// Screenshot fallback: only if no IGDB screenshots were saved
		var screenshotCount int64
		s.DB.Model(&db.GameScreenshot{}).Where("game_id = ?", game.ID).Count(&screenshotCount)
		if screenshotCount == 0 {
			libRetroWg.Add(1)
			go func() {
				defer libRetroWg.Done()
				snapSubpath := fmt.Sprintf("%s/%s/screenshot.png", console.Abbreviation, gameIDStr)
				if path := s.downloadLibRetroImage(libRetroSystem, gameName, "Named_Snaps", snapSubpath); path != "" {
					s.DB.Create(&db.GameScreenshot{GameID: game.ID, URL: path, Position: 0})
				}
			}()
		}

		libRetroWg.Wait()
		if libRetroCoverPath != "" {
			game.LibRetroCoverURL = libRetroCoverPath
		}
	}

	// Set active cover: restore admin's manual choice if still available,
	// otherwise prefer LibRetro box art, fall back to IGDB.
	if manualOverride {
		switch prevCoverSource {
		case "libretro":
			if game.LibRetroCoverURL != "" {
				game.CoverURL = game.LibRetroCoverURL
			}
		case "igdb":
			if game.IGDBCoverURL != "" {
				game.CoverURL = game.IGDBCoverURL
			}
		}
	}
	if game.CoverURL == "" {
		if game.LibRetroCoverURL != "" {
			game.CoverURL = game.LibRetroCoverURL
		} else if game.IGDBCoverURL != "" {
			game.CoverURL = game.IGDBCoverURL
		}
		// Admin's chosen source is no longer available; clear the flag
		if manualOverride {
			game.CoverManuallySet = false
		}
	}

	// Set scraper ID if not already set by IGDB
	if game.ScraperID == "" {
		game.ScraperID = "libretro"
	}

	// Extract region from filename (works for both verified and unverified ROMs)
	if game.Region == "" {
		game.Region = ExtractRegion(game.FileName)
	}

	// --- SteamGridDB artwork (best-effort) ---
	s.scrapeSteamGridDBArtwork(game, console)

	game.ScrapeAttempts++

	if err := s.DB.Save(game).Error; err != nil {
		return fmt.Errorf("saving scraped metadata: %w", err)
	}

	slog.Info("scraped metadata", "game", game.Title, "scraperId", game.ScraperID, "rating", game.Rating)
	return nil
}

// scrapeIGDB searches IGDB for game metadata and downloads images.
func (s *Scraper) scrapeIGDB(game *db.Game, console db.Console, gameIDStr string) error {
	platformID, ok := igdb.AbbreviationToIGDBPlatform[console.Abbreviation]
	if !ok {
		return fmt.Errorf("no IGDB platform ID for console %s", console.Abbreviation)
	}

	cleanName := igdb.CleanGameName(game.FileName)
	if cleanName == "" {
		return fmt.Errorf("empty game name after cleaning: %s", game.FileName)
	}

	// CRC-based identification: look up ROM in No-Intro DAT
	searchName := cleanName
	crcVerified := false
	if idx, err := s.DATCache.GetIndex(console.Abbreviation); err == nil && idx != nil {
		// Resolve relative path to absolute for filesystem access
		absFilePath, resolveErr := storage.ResolveGamePath(game.FilePath, s.GameDirs)
		if resolveErr == nil {
			if crc, err := ComputeFileCRC32(absFilePath); err == nil {
				game.CRC32 = crc
				if entry, ok := idx.LookupCRC(crc); ok {
					slog.Info("CRC match found in No-Intro DAT", "game", game.FileName, "crc", crc, "canonical", entry.ROMName)
					game.VerificationStatus = "verified"
					crcVerified = true

					// Set authoritative title from the canonical DAT name
					game.Title = scanner.GameTitle(entry.ROMName)

					// Rename ROM file to canonical No-Intro name
					newAbsPath := filepath.Join(filepath.Dir(absFilePath), entry.ROMName)
					if newAbsPath != absFilePath {
						if err := os.Rename(absFilePath, newAbsPath); err == nil {
							oldRelPath := game.FilePath
							oldName := game.FileName
							game.FilePath = storage.RelativeGamePath(newAbsPath, s.GameDirs)
							game.FileName = entry.ROMName

							// Persist new path immediately so the DB never references a stale file location.
							if dbErr := s.DB.Model(game).Updates(map[string]interface{}{
								"file_path": game.FilePath,
								"file_name": game.FileName,
							}).Error; dbErr != nil {
								slog.Warn("failed to persist renamed path, rolling back rename", "error", dbErr)
								if rbErr := os.Rename(newAbsPath, absFilePath); rbErr != nil {
									slog.Error("rollback rename also failed", "error", rbErr)
								}
								game.FilePath = oldRelPath
								game.FileName = oldName
							}
						} else {
							slog.Warn("failed to rename ROM to canonical name", "from", absFilePath, "to", newAbsPath, "error", err)
						}
					}

					// Use canonical game name (strip region tags) for IGDB search
					searchName = igdb.CleanGameName(entry.ROMName)
				} else {
					game.VerificationStatus = "unverified"
				}
			} else {
				slog.Debug("failed to compute CRC32", "file", absFilePath, "error", err)
			}
		} else {
			slog.Debug("failed to resolve game path for CRC", "path", game.FilePath, "error", resolveErr)
		}
	}

	// Always try exact name match first. IGDB's fulltext search can omit the
	// original game (e.g. "Super Mario 64" returns the unreleased sequel but
	// not the original).
	var games []igdb.Game
	var err error
	games, err = s.IGDBClient.SearchGameExact(searchName, platformID)
	if err != nil {
		slog.Warn("IGDB exact search failed, falling back to text search", "game", searchName, "error", err)
	}

	// Fall back to text search if exact match found nothing
	if len(games) == 0 {
		games, err = s.IGDBClient.SearchGame(searchName, platformID)
		if err != nil {
			return fmt.Errorf("IGDB search: %w", err)
		}
	}

	if len(games) == 0 {
		slog.Debug("no IGDB results", "game", searchName, "platform", console.Abbreviation)
		return nil
	}

	// Pick the result whose name best matches the search name
	match := bestIGDBMatch(searchName, games)

	// When CRC-verified, only overwrite the title if the IGDB name is an exact match
	// (e.g. don't replace "Aladdin" with "Aladdin 2000" from a fuzzy IGDB result).
	forceTitle := !crcVerified || normalizeName(match.Name) == normalizeName(searchName)

	s.applyIGDBMatch(game, console, match, gameIDStr, forceTitle)

	slog.Info("IGDB match found", "game", searchName, "matched", match.Name, "igdbId", match.ID)

	// Enrichment: fetch themes, keywords, perspectives, franchises, artworks
	s.enrichGameMetadata(game, match.ID)

	return nil
}

// applyIGDBMatch populates a game's metadata and downloads images from a specific
// IGDB game result. If forceTitle is true, the game title is always overwritten
// with the IGDB name; otherwise it is left unchanged.
func (s *Scraper) applyIGDBMatch(game *db.Game, console db.Console, match igdb.Game, gameIDStr string, forceTitle bool) {
	// Set scraper ID
	game.ScraperID = fmt.Sprintf("igdb:%d", match.ID)

	// Populate metadata — don't overwrite existing non-empty fields with empty values.
	if match.Name != "" && forceTitle {
		game.Title = match.Name
	}
	if match.Summary != "" {
		game.Description = match.Summary
	}
	if match.Storyline != "" && game.Storyline == "" {
		game.Storyline = match.Storyline
	}
	if match.AggregatedRating > 0 {
		game.Rating = match.AggregatedRating
	}
	slog.Info("IGDB rating data",
		"game", game.Title,
		"aggregatedRating", match.AggregatedRating,
		"totalRating", match.TotalRating,
		"igdbRating", match.IGDBRating,
	)
	if match.TotalRating > 0 && game.TotalRating == 0 {
		game.TotalRating = match.TotalRating
	}
	if match.TotalRatingCount > 0 && game.TotalRatingCount == 0 {
		game.TotalRatingCount = match.TotalRatingCount
	}
	if match.IGDBRating > 0 && game.IGDBUserRating == 0 {
		game.IGDBUserRating = match.IGDBRating
	}
	if match.IGDBRatingCount > 0 && game.IGDBUserRatingCount == 0 {
		game.IGDBUserRatingCount = match.IGDBRatingCount
	}
	// Fetch time-to-beat from the dedicated endpoint (the inline field was deprecated).
	if s.IGDBClient != nil && game.TimeToBeatHastily == 0 && game.TimeToBeatNormally == 0 && game.TimeToBeatCompletely == 0 {
		if ttb, err := s.IGDBClient.GetTimeToBeat(match.ID); err != nil {
			slog.Warn("failed to fetch time to beat", "igdbID", match.ID, "error", err)
		} else if ttb != nil {
			if ttb.Hastily > 0 {
				game.TimeToBeatHastily = ttb.Hastily
			}
			if ttb.Normally > 0 {
				game.TimeToBeatNormally = ttb.Normally
			}
			if ttb.Completely > 0 {
				game.TimeToBeatCompletely = ttb.Completely
			}
		}
	}
	// Use platform-specific release date when available (e.g. SNES release of
	// a game that originally launched on another platform), falling back to
	// the global first release date.
	platformID, _ := igdb.AbbreviationToIGDBPlatform[console.Abbreviation]
	if rd := earliestPlatformReleaseDate(match.ReleaseDates, platformID); rd > 0 {
		t := time.Unix(rd, 0)
		game.ReleaseDate = t.Format("2006-01-02")
	} else if match.FirstReleaseDate > 0 {
		t := time.Unix(match.FirstReleaseDate, 0)
		game.ReleaseDate = t.Format("2006-01-02")
	}

	// Extract developer and publisher, store company logos
	for _, ic := range match.InvolvedCompanies {
		if ic.Developer && game.Developer == "" {
			game.Developer = ic.Company.Name
		}
		if ic.Publisher && game.Publisher == "" {
			game.Publisher = ic.Company.Name
		}
		// Store company logo if available (download locally)
		if ic.Company.Logo != nil && ic.Company.Logo.ImageID != "" {
			cdnURL := igdb.CompanyLogoURL(ic.Company.Logo.ImageID)
			subpath := fmt.Sprintf("companies/%d/logo.png", ic.Company.ID)
			logoPath := s.DownloadExternalImage(cdnURL, subpath)
			if logoPath == "" {
				logoPath = cdnURL // fallback to CDN URL if download fails
			}
			var existing db.Company
			if err := s.DB.Where("igdb_company_id = ?", ic.Company.ID).First(&existing).Error; err == nil {
				if existing.LogoURL == "" || strings.HasPrefix(existing.LogoURL, "http") {
					s.DB.Model(&existing).Update("logo_url", logoPath)
				}
			}
		}
	}

	// Genre (all genres, comma-separated)
	if len(match.Genres) > 0 && game.Genre == "" {
		names := make([]string, len(match.Genres))
		for i, g := range match.Genres {
			names[i] = g.Name
		}
		game.Genre = strings.Join(names, ", ")
	}

	// Game modes (all modes, comma-separated)
	if len(match.GameModes) > 0 && game.GameModes == "" {
		modeNames := make([]string, len(match.GameModes))
		for i, mode := range match.GameModes {
			modeNames[i] = mode.Name
		}
		game.GameModes = strings.Join(modeNames, ", ")
	}

	// Players: 1 if only single-player, 2 if any multiplayer mode exists
	if len(match.GameModes) > 0 && game.Players == 0 {
		game.Players = 1
		for _, mode := range match.GameModes {
			if mode.Name != "Single player" {
				game.Players = 2
				break
			}
		}
	}

	// Regional release dates
	if len(match.ReleaseDates) > 0 {
		for _, rd := range match.ReleaseDates {
			regionName := igdb.RegionName(rd.Region)
			if regionName == "" || rd.Date == 0 {
				continue
			}
			t := time.Unix(rd.Date, 0)
			platformName := ""
			if rd.Platform != nil {
				platformName = rd.Platform.Name
			}
			releaseDate := db.GameReleaseDate{
				GameID:   game.ID,
				Region:   regionName,
				Date:     t.Format("2006-01-02"),
				Platform: platformName,
			}
			s.DB.Where("game_id = ? AND region = ?", game.ID, regionName).
				Assign(releaseDate).
				FirstOrCreate(&releaseDate)
		}
	}

	// Download cover art and screenshots concurrently
	var imgWg sync.WaitGroup

	if match.Cover != nil && match.Cover.ImageID != "" {
		imgWg.Add(1)
		go func(imageID string) {
			defer imgWg.Done()
			coverURL := igdb.ImageURL(imageID, "cover_big")
			coverSubpath := fmt.Sprintf("%s/%s/boxart-igdb.jpg", console.Abbreviation, gameIDStr)
			if path := s.DownloadExternalImage(coverURL, coverSubpath); path != "" {
				game.IGDBCoverURL = path
			}
		}(match.Cover.ImageID)
	}

	maxScreenshots := 10
	if len(match.Screenshots) < maxScreenshots {
		maxScreenshots = len(match.Screenshots)
	}
	for i := 0; i < maxScreenshots; i++ {
		ss := match.Screenshots[i]
		if ss.ImageID == "" {
			continue
		}
		imgWg.Add(1)
		go func(idx int, imageID string) {
			defer imgWg.Done()
			screenshotURL := igdb.ImageURL(imageID, "original")
			screenshotSubpath := fmt.Sprintf("%s/%s/screenshot_%d.jpg", console.Abbreviation, gameIDStr, idx)
			if path := s.DownloadExternalImage(screenshotURL, screenshotSubpath); path != "" {
				s.DB.Create(&db.GameScreenshot{GameID: game.ID, URL: path, Position: idx})
			}
		}(i, ss.ImageID)
	}

	imgWg.Wait()
}

// ScrapeGameWithIGDBMatch re-scrapes a game using a specific IGDB game ID
// chosen by an admin, bypassing the automatic search and ranking.
// It clears stale metadata, fetches the IGDB game by ID, applies its metadata
// and images, re-fetches LibRetro thumbnails, and persists the result.
func (s *Scraper) ScrapeGameWithIGDBMatch(game *db.Game, igdbID int) error {
	// Load console if not preloaded
	var console db.Console
	if game.Console.ID != 0 {
		console = game.Console
	} else {
		if err := s.DB.First(&console, game.ConsoleID).Error; err != nil {
			return fmt.Errorf("loading console for game: %w", err)
		}
	}

	if s.IGDBClient == nil || !s.IGDBClient.IsConfigured() {
		return fmt.Errorf("IGDB client is not configured")
	}

	// Fetch the specific IGDB game
	igdbGame, err := s.IGDBClient.GetGameByID(igdbID)
	if err != nil {
		return fmt.Errorf("fetching IGDB game %d: %w", igdbID, err)
	}
	if igdbGame == nil {
		return fmt.Errorf("IGDB game %d not found", igdbID)
	}

	// Clear stale IGDB metadata for re-scrape. Preserve LibRetro cover and
	// manual cover choice handling (same logic as ScrapeGame).
	manualOverride := game.CoverManuallySet
	prevCoverSource := ""
	if manualOverride {
		switch game.CoverURL {
		case game.LibRetroCoverURL:
			prevCoverSource = "libretro"
		case game.IGDBCoverURL:
			prevCoverSource = "igdb"
		}
	}
	game.CoverURL = ""
	game.ScreenshotURL = ""
	game.IGDBCoverURL = ""
	// Clear IGDB-sourced metadata so the new match fully replaces it
	game.Description = ""
	game.Developer = ""
	game.Publisher = ""
	game.Genre = ""
	game.GameModes = ""
	game.Storyline = ""
	game.Rating = 0
	game.TotalRating = 0
	game.TotalRatingCount = 0
	game.IGDBUserRating = 0
	game.IGDBUserRatingCount = 0
	game.TimeToBeatHastily = 0
	game.TimeToBeatNormally = 0
	game.TimeToBeatCompletely = 0
	game.Players = 0
	game.ReleaseDate = ""
	// Delete old IGDB screenshots, release dates, videos, language supports, age ratings
	s.DB.Where("game_id = ?", game.ID).Delete(&db.GameScreenshot{})
	s.DB.Where("game_id = ?", game.ID).Delete(&db.GameReleaseDate{})
	s.DB.Where("game_id = ?", game.ID).Delete(&db.GameVideo{})
	s.DB.Where("game_id = ?", game.ID).Delete(&db.GameLanguageSupport{})
	s.DB.Where("game_id = ?", game.ID).Delete(&db.GameAgeRating{})

	gameIDStr := strconv.FormatUint(uint64(game.ID), 10)

	// Apply the admin-selected IGDB match (always overwrite title)
	s.applyIGDBMatch(game, console, *igdbGame, gameIDStr, true)

	// Re-fetch LibRetro thumbnails (filename-based, independent of IGDB match)
	gameName := gameNameFromFileName(game.FileName)
	libRetroSystem, hasLibRetro := AbbreviationToLibRetro[console.Abbreviation]
	if hasLibRetro {
		boxartSubpath := fmt.Sprintf("%s/%s/boxart-libretro.png", console.Abbreviation, gameIDStr)
		if path := s.downloadLibRetroImage(libRetroSystem, gameName, "Named_Boxarts", boxartSubpath); path != "" {
			game.LibRetroCoverURL = path
		}

		var screenshotCount int64
		s.DB.Model(&db.GameScreenshot{}).Where("game_id = ?", game.ID).Count(&screenshotCount)
		if screenshotCount == 0 {
			snapSubpath := fmt.Sprintf("%s/%s/screenshot.png", console.Abbreviation, gameIDStr)
			if path := s.downloadLibRetroImage(libRetroSystem, gameName, "Named_Snaps", snapSubpath); path != "" {
				s.DB.Create(&db.GameScreenshot{GameID: game.ID, URL: path, Position: 0})
			}
		}
	}

	// Set active cover: restore admin's manual choice if still available,
	// otherwise prefer LibRetro box art, fall back to IGDB.
	if manualOverride {
		switch prevCoverSource {
		case "libretro":
			if game.LibRetroCoverURL != "" {
				game.CoverURL = game.LibRetroCoverURL
			}
		case "igdb":
			if game.IGDBCoverURL != "" {
				game.CoverURL = game.IGDBCoverURL
			}
		}
	}
	if game.CoverURL == "" {
		if game.LibRetroCoverURL != "" {
			game.CoverURL = game.LibRetroCoverURL
		} else if game.IGDBCoverURL != "" {
			game.CoverURL = game.IGDBCoverURL
		}
		if manualOverride {
			game.CoverManuallySet = false
		}
	}

	// --- SteamGridDB artwork (best-effort) ---
	s.scrapeSteamGridDBArtwork(game, console)

	game.ScrapeAttempts++

	if err := s.DB.Save(game).Error; err != nil {
		return fmt.Errorf("saving scraped metadata: %w", err)
	}

	// Enrichment: fetch themes, keywords, perspectives, franchises, artworks
	s.enrichGameMetadata(game, igdbID)

	slog.Info("re-scraped with manual IGDB match", "game", game.Title, "igdbId", igdbID, "scraperId", game.ScraperID)
	return nil
}

// earliestPlatformReleaseDate returns the earliest release date (unix timestamp)
// from the given release dates that matches the specified IGDB platform ID.
// Returns 0 if no matching platform release date is found.
func earliestPlatformReleaseDate(dates []igdb.ReleaseDate, platformID int) int64 {
	var earliest int64
	for _, rd := range dates {
		if rd.Platform == nil || rd.Date == 0 {
			continue
		}
		if rd.Platform.ID == platformID {
			if earliest == 0 || rd.Date < earliest {
				earliest = rd.Date
			}
		}
	}
	return earliest
}
