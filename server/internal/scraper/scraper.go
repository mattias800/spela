package scraper

import (
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
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
	"gorm.io/gorm"
)

// Scraper fetches game metadata from IGDB and box art from LibRetro Thumbnails (preferred) with IGDB fallback.
type Scraper struct {
	DB               *gorm.DB
	Storage          *storage.Storage
	HTTPClient       *http.Client
	IGDBClient       *igdb.Client
	SteamGridDBClient *SteamGridDBClient
	DATCache         *DATCache
	GameDirs         []string
	cache            *nameCache

	// Scrape state tracking (shared across handlers)
	scrapeMu       sync.Mutex
	scraping       bool
	scrapeProgress *ScrapeProgress

	// Enrichment state tracking
	enrichMu       sync.Mutex
	enriching      bool
	enrichProgress *EnrichProgress
}

// NewScraper creates a new metadata scraper instance.
func NewScraper(database *gorm.DB, store *storage.Storage, datDir string, gameDirs []string) *Scraper {
	httpClient := &http.Client{
		Timeout: 30 * time.Second,
	}
	return &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: httpClient,
		DATCache:   NewDATCache(datDir, httpClient),
		GameDirs:   gameDirs,
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}
}

// IsIGDBConfigured returns whether the scraper has a configured IGDB client.
func (s *Scraper) IsIGDBConfigured() bool {
	return s.IGDBClient != nil && s.IGDBClient.IsConfigured()
}

// TryStartScrape attempts to acquire the scrape lock.
// Returns true if the lock was acquired (caller must call FinishScrape when done).
// Also checks the enrichment lock — scraping and enrichment are mutually exclusive.
func (s *Scraper) TryStartScrape() bool {
	s.scrapeMu.Lock()
	defer s.scrapeMu.Unlock()
	s.enrichMu.Lock()
	defer s.enrichMu.Unlock()
	if s.scraping || s.enriching {
		return false
	}
	s.scraping = true
	return true
}

// FinishScrape releases the scrape lock and clears progress.
func (s *Scraper) FinishScrape() {
	s.scrapeMu.Lock()
	s.scraping = false
	s.scrapeProgress = nil
	s.scrapeMu.Unlock()
}

// SetScrapeProgress updates the current scrape progress.
func (s *Scraper) SetScrapeProgress(p *ScrapeProgress) {
	s.scrapeMu.Lock()
	s.scrapeProgress = p
	s.scrapeMu.Unlock()
}

// GetScrapeStatus returns whether a scrape is active and the current progress.
func (s *Scraper) GetScrapeStatus() (bool, *ScrapeProgress) {
	s.scrapeMu.Lock()
	defer s.scrapeMu.Unlock()
	if s.scrapeProgress == nil {
		return s.scraping, nil
	}
	p := *s.scrapeProgress
	return s.scraping, &p
}

// TryStartEnrich attempts to acquire the enrichment lock.
// Returns true if the lock was acquired (caller must call FinishEnrich when done).
// Also checks the scrape lock — enrichment and scraping are mutually exclusive.
func (s *Scraper) TryStartEnrich() bool {
	s.scrapeMu.Lock()
	defer s.scrapeMu.Unlock()
	s.enrichMu.Lock()
	defer s.enrichMu.Unlock()
	if s.scraping || s.enriching {
		return false
	}
	s.enriching = true
	return true
}

// FinishEnrich releases the enrichment lock and clears progress.
func (s *Scraper) FinishEnrich() {
	s.enrichMu.Lock()
	s.enriching = false
	s.enrichProgress = nil
	s.enrichMu.Unlock()
}

// SetEnrichProgress updates the current enrichment progress.
func (s *Scraper) SetEnrichProgress(p *EnrichProgress) {
	s.enrichMu.Lock()
	s.enrichProgress = p
	s.enrichMu.Unlock()
}

// GetEnrichStatus returns whether an enrichment is active and the current progress.
func (s *Scraper) GetEnrichStatus() (bool, *EnrichProgress) {
	s.enrichMu.Lock()
	defer s.enrichMu.Unlock()
	if s.enrichProgress == nil {
		return s.enriching, nil
	}
	p := *s.enrichProgress
	return s.enriching, &p
}

// AbbreviationToLibRetro maps console abbreviations to LibRetro system names.
var AbbreviationToLibRetro = map[string]string{
	"NES":    "Nintendo - Nintendo Entertainment System",
	"SNES":   "Nintendo - Super Nintendo Entertainment System",
	"GB":     "Nintendo - Game Boy",
	"GBC":    "Nintendo - Game Boy Color",
	"GBA":    "Nintendo - Game Boy Advance",
	"N64":    "Nintendo - Nintendo 64",
	"NDS":    "Nintendo - Nintendo DS",
	"SMS":    "Sega - Master System - Mark III",
	"GEN":    "Sega - Mega Drive - Genesis",
	"SAT":    "Sega - Saturn",
	"PSX":    "Sony - PlayStation",
	"PSP":    "Sony - PlayStation Portable",
	"NEOGEO": "SNK - Neo Geo",
	"ARCADE": "MAME",
	"PCE":    "NEC - PC Engine - TurboGrafx 16",
	"A26":    "Atari - 2600",
	"GG":    "Sega - Game Gear",
	"SCD":   "Sega - Mega CD - Sega CD",
	"32X":   "Sega - 32X",
	"DC":    "Sega - Dreamcast",
	"VB":    "Nintendo - Virtual Boy",
	"3DS":   "Nintendo - Nintendo 3DS",
	"GC":    "Nintendo - GameCube",
	"A52":   "Atari - 5200",
	"A78":   "Atari - 7800",
	"LYNX":  "Atari - Lynx",
	"JAG":   "Atari - Jaguar",
	"NGP":   "SNK - Neo Geo Pocket",
	"WS":    "Bandai - WonderSwan",
	"PCFX":  "NEC - PC-FX",
	"CV":    "Coleco - ColecoVision",
	"PKMN":  "Nintendo - Pokemon Mini",
	"PS2":   "Sony - PlayStation 2",
	"C64":   "Commodore - 64",
	"DOS":   "DOS",
	"AMIGA": "Commodore - Amiga",
	"PS3":   "Sony - PlayStation 3",
	"WII":   "Nintendo - Wii",
}

var libRetroThumbnailBase = "https://thumbnails.libretro.com"

// gameNameFromFileName strips the file extension to derive the game name.
func gameNameFromFileName(fileName string) string {
	ext := filepath.Ext(fileName)
	return strings.TrimSuffix(fileName, ext)
}

// tryDownloadImage attempts to download a single image by exact name from LibRetro Thumbnails.
// Returns the relative storage path on success, or empty string on failure.
func (s *Scraper) tryDownloadImage(system, name, imageType, subpath string) string {
	imageURL := fmt.Sprintf("%s/%s/%s/%s.png",
		libRetroThumbnailBase,
		url.PathEscape(system),
		imageType,
		url.PathEscape(name),
	)

	resp, err := s.HTTPClient.Get(imageURL)
	if err != nil {
		slog.Debug("failed to fetch LibRetro image", "url", imageURL, "error", err)
		return ""
	}

	if resp.StatusCode != http.StatusOK {
		resp.Body.Close()
		slog.Debug("LibRetro image not found", "url", imageURL, "status", resp.StatusCode)
		return ""
	}

	savedPath, err := s.Storage.WriteImage(subpath, resp.Body)
	resp.Body.Close()
	if err != nil {
		slog.Warn("failed to save LibRetro image", "subpath", subpath, "error", err)
		return ""
	}

	slog.Debug("downloaded LibRetro image", "name", name, "type", imageType)
	return savedPath
}

// downloadLibRetroImage downloads an image from LibRetro Thumbnails and saves it locally.
// When the game name contains a non-preferred region (e.g. Japan, Europe), it skips
// the exact name match and goes straight to fuzzy matching, which prefers USA/World
// entries via hasPreferredRegion tie-breaking. For games with preferred regions only
// (USA, World) or no region tags, it tries the exact name first as a fast path.
// Returns the relative storage path on success, or empty string if the image was not found.
func (s *Scraper) downloadLibRetroImage(system, gameName, imageType, subpath string) string {
	// Fast path: try exact name match when the name already has a preferred
	// region or no region at all. Skip this for non-preferred regions so the
	// fuzzy matcher can find a USA/World alternative.
	if !hasNonPreferredRegion(gameName) {
		if path := s.tryDownloadImage(system, gameName, imageType, subpath); path != "" {
			return path
		}
	}

	// Fuzzy matching: prefers USA/World entries via hasPreferredRegion tie-breaking.
	entries, err := s.cache.getOrLoad(system, s.HTTPClient)
	if err != nil {
		slog.Warn("failed to load LibRetro name listing", "system", system, "error", err)
		return ""
	}

	normalized := normalizeName(gameName)
	match, score, found := findBestMatch(normalized, entries, 0.88)
	if !found {
		slog.Debug("no fuzzy match found", "game", gameName, "normalized", normalized)
		return ""
	}

	slog.Info("fuzzy matched", "original", gameName, "matched", match.Raw, "score", score)
	return s.tryDownloadImage(system, match.Raw, imageType, subpath)
}

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
		// Delete old normalized screenshots
		s.DB.Where("game_id = ?", game.ID).Delete(&db.GameScreenshot{})
	}

	// Mark disc-based systems as not applicable for CRC verification
	if DiscBasedSystems[console.Abbreviation] {
		game.VerificationStatus = "not_applicable"
	}

	gameName := gameNameFromFileName(game.FileName)
	gameIDStr := strconv.FormatUint(uint64(game.ID), 10)

	// --- IGDB (primary metadata + images, when configured) ---
	if s.IGDBClient != nil && s.IGDBClient.IsConfigured() {
		if err := s.scrapeIGDB(game, console, gameIDStr); err != nil {
			slog.Warn("IGDB scrape failed, falling back to LibRetro", "game", game.Title, "error", err)
		}
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

	slog.Info("scraped metadata", "game", game.Title, "scraperId", game.ScraperID)
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
	if match.AggregatedRating > 0 {
		game.Rating = match.AggregatedRating
	}
	if match.FirstReleaseDate > 0 {
		t := time.Unix(match.FirstReleaseDate, 0)
		game.ReleaseDate = t.Format("2006-01-02")
	}

	// Extract developer and publisher
	for _, ic := range match.InvolvedCompanies {
		if ic.Developer && game.Developer == "" {
			game.Developer = ic.Company.Name
		}
		if ic.Publisher && game.Publisher == "" {
			game.Publisher = ic.Company.Name
		}
	}

	// Genre (first genre)
	if len(match.Genres) > 0 && game.Genre == "" {
		game.Genre = match.Genres[0].Name
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

	// Download cover art and screenshots concurrently
	var imgWg sync.WaitGroup

	if match.Cover != nil && match.Cover.ImageID != "" {
		imgWg.Add(1)
		go func(imageID string) {
			defer imgWg.Done()
			coverURL := igdb.ImageURL(imageID, "cover_big")
			coverSubpath := fmt.Sprintf("%s/%s/boxart-igdb.jpg", console.Abbreviation, gameIDStr)
			if path := s.downloadExternalImage(coverURL, coverSubpath); path != "" {
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
			if path := s.downloadExternalImage(screenshotURL, screenshotSubpath); path != "" {
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
	game.Rating = 0
	game.Players = 0
	game.ReleaseDate = ""
	// Delete old IGDB screenshots
	s.DB.Where("game_id = ?", game.ID).Delete(&db.GameScreenshot{})

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

// enrichGameMetadata fetches extended IGDB metadata (themes, keywords, perspectives,
// franchises, artworks) and stores them in the database. Errors are logged but do not
// fail the overall scrape — the game will simply lack enrichment data.
func (s *Scraper) enrichGameMetadata(game *db.Game, igdbGameID int) {
	if s.IGDBClient == nil || !s.IGDBClient.IsConfigured() {
		return
	}

	enrichment, err := s.IGDBClient.GetGameEnrichment(igdbGameID)
	if err != nil {
		slog.Warn("IGDB enrichment failed, skipping", "game", game.Title, "igdbId", igdbGameID, "error", err)
		return
	}
	if enrichment == nil {
		return
	}

	s.storeEnrichmentData(game, enrichment, igdbGameID)
}

// storeEnrichmentData persists enrichment data to the DB, replacing any existing data.
func (s *Scraper) storeEnrichmentData(game *db.Game, enrichment *igdb.GameEnrichment, igdbGameID int) {
	// Delete old enrichment data to prevent duplicates on re-scrape
	s.DB.Where("game_id = ?", game.ID).Delete(&db.GameTheme{})
	s.DB.Where("game_id = ?", game.ID).Delete(&db.GameKeyword{})
	s.DB.Where("game_id = ?", game.ID).Delete(&db.GamePlayerPerspective{})
	s.DB.Where("game_id = ?", game.ID).Delete(&db.GameFranchise{})
	s.DB.Where("game_id = ?", game.ID).Delete(&db.GameArtworkImage{})

	// Store themes
	for _, t := range enrichment.Themes {
		s.DB.Create(&db.GameTheme{
			GameID:      game.ID,
			IGDBThemeID: t.ID,
			Name:        t.Name,
		})
	}

	// Store keywords
	for _, k := range enrichment.Keywords {
		s.DB.Create(&db.GameKeyword{
			GameID:        game.ID,
			IGDBKeywordID: k.ID,
			Name:          k.Name,
		})
	}

	// Store player perspectives
	for _, p := range enrichment.PlayerPerspectives {
		s.DB.Create(&db.GamePlayerPerspective{
			GameID:            game.ID,
			IGDBPerspectiveID: p.ID,
			Name:              p.Name,
		})
	}

	// Store franchises (reuse name from existing DB entries when possible)
	for _, fID := range enrichment.Franchises {
		var existing db.GameFranchise
		if err := s.DB.Where("igdb_franchise_id = ?", fID).First(&existing).Error; err == nil {
			// Reuse the cached franchise name
			s.DB.Create(&db.GameFranchise{
				GameID:          game.ID,
				IGDBFranchiseID: fID,
				FranchiseName:   existing.FranchiseName,
			})
		} else if s.IGDBClient != nil {
			// Fetch franchise name from IGDB
			franchise, fetchErr := s.IGDBClient.GetFranchise(fID)
			if fetchErr != nil {
				slog.Warn("failed to fetch franchise from IGDB", "franchiseId", fID, "error", fetchErr)
				continue
			}
			if franchise != nil {
				s.DB.Create(&db.GameFranchise{
					GameID:          game.ID,
					IGDBFranchiseID: fID,
					FranchiseName:   franchise.Name,
				})
			}
		}
	}

	// Store artworks
	for _, a := range enrichment.Artworks {
		if a.ImageID == "" {
			continue
		}
		s.DB.Create(&db.GameArtworkImage{
			GameID:      game.ID,
			IGDBImageID: a.ImageID,
			Width:       a.Width,
			Height:      a.Height,
		})
	}

	// Handle series (IGDB collection)
	if enrichment.CollectionID != nil {
		s.handleSeriesForGame(game, *enrichment.CollectionID)
	}
}

// handleSeriesForGame creates or reuses a GameSeries for the IGDB collection
// and links the current game to it via GameSeriesEntry.
func (s *Scraper) handleSeriesForGame(game *db.Game, igdbCollectionID int) {
	var series db.GameSeries
	if err := s.DB.Where("igdb_collection_id = ?", igdbCollectionID).First(&series).Error; err != nil {
		// Series doesn't exist yet — fetch from IGDB
		if s.IGDBClient == nil {
			return
		}
		collectionData, fetchErr := s.IGDBClient.GetCollection(igdbCollectionID)
		if fetchErr != nil {
			slog.Warn("failed to fetch collection from IGDB", "collectionId", igdbCollectionID, "error", fetchErr)
			return
		}
		if collectionData == nil {
			return
		}
		series = db.GameSeries{
			IGDBCollectionID: igdbCollectionID,
			Name:             collectionData.Name,
		}
		if err := s.DB.Create(&series).Error; err != nil {
			slog.Warn("failed to create game series", "name", collectionData.Name, "error", err)
			return
		}
	}

	// Create/update the entry for this local game
	igdbID := 0
	if _, parseErr := fmt.Sscanf(game.ScraperID, "igdb:%d", &igdbID); parseErr != nil || igdbID == 0 {
		return
	}

	var entry db.GameSeriesEntry
	result := s.DB.Where("series_id = ? AND igdb_game_id = ?", series.ID, igdbID).First(&entry)
	if result.Error != nil {
		// Create new entry
		gameID := game.ID
		s.DB.Create(&db.GameSeriesEntry{
			SeriesID:   series.ID,
			GameID:     &gameID,
			IGDBGameID: igdbID,
			Name:       game.Title,
		})
	} else {
		// Update existing entry to link to local game
		if entry.GameID == nil {
			gameID := game.ID
			s.DB.Model(&entry).Update("game_id", &gameID)
		}
	}
}

// EnrichGameOnly fetches and stores enrichment data for a game that already has
// an IGDB match. Unlike ScrapeGame, it does NOT re-download cover art, screenshots,
// or any other basic metadata. Used by the backfill endpoint.
func (s *Scraper) EnrichGameOnly(game *db.Game) error {
	if s.IGDBClient == nil || !s.IGDBClient.IsConfigured() {
		return fmt.Errorf("IGDB client is not configured")
	}

	// Extract IGDB ID from scraper ID
	var igdbID int
	if _, err := fmt.Sscanf(game.ScraperID, "igdb:%d", &igdbID); err != nil || igdbID == 0 {
		return fmt.Errorf("game %q has no valid IGDB scraper ID: %s", game.Title, game.ScraperID)
	}

	enrichment, err := s.IGDBClient.GetGameEnrichment(igdbID)
	if err != nil {
		return fmt.Errorf("fetching enrichment for %q (igdb:%d): %w", game.Title, igdbID, err)
	}
	if enrichment == nil {
		return nil // game not found on IGDB — not an error
	}

	s.storeEnrichmentData(game, enrichment, igdbID)
	return nil
}

// PopulateSeriesEntries fetches all games in a series from IGDB and populates
// GameSeriesEntry rows (with GameID null for non-library games).
// This is called during backfill to power "You own 8 of 15 games" displays.
func (s *Scraper) PopulateSeriesEntries(series *db.GameSeries) error {
	if s.IGDBClient == nil || !s.IGDBClient.IsConfigured() {
		return fmt.Errorf("IGDB client is not configured")
	}

	collectionData, err := s.IGDBClient.GetCollection(series.IGDBCollectionID)
	if err != nil {
		return fmt.Errorf("fetching collection %d: %w", series.IGDBCollectionID, err)
	}
	if collectionData == nil || len(collectionData.GameIDs) == 0 {
		return nil
	}

	// Fetch game names and covers from IGDB
	gameInfos, err := s.IGDBClient.GetCollectionGames(collectionData.GameIDs)
	if err != nil {
		return fmt.Errorf("fetching collection game details: %w", err)
	}

	// Build a map of IGDB game ID -> local game ID
	var localGames []db.Game
	s.DB.Select("id, scraper_id").Where("scraper_id LIKE 'igdb:%'").Find(&localGames)
	localMap := make(map[int]uint) // igdbID -> local game ID
	for _, g := range localGames {
		var igdbID int
		if _, parseErr := fmt.Sscanf(g.ScraperID, "igdb:%d", &igdbID); parseErr == nil && igdbID > 0 {
			localMap[igdbID] = g.ID
		}
	}

	// Upsert entries
	for _, info := range gameInfos {
		var entry db.GameSeriesEntry
		result := s.DB.Where("series_id = ? AND igdb_game_id = ?", series.ID, info.ID).First(&entry)
		if result.Error != nil {
			// Create new entry
			newEntry := db.GameSeriesEntry{
				SeriesID:     series.ID,
				IGDBGameID:   info.ID,
				Name:         info.Name,
				CoverImageID: info.CoverImageID,
			}
			if localID, ok := localMap[info.ID]; ok {
				newEntry.GameID = &localID
			}
			s.DB.Create(&newEntry)
		} else {
			// Update existing entry
			updates := map[string]interface{}{
				"name":           info.Name,
				"cover_image_id": info.CoverImageID,
			}
			if localID, ok := localMap[info.ID]; ok {
				updates["game_id"] = localID
			}
			s.DB.Model(&entry).Updates(updates)
		}
	}

	return nil
}

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

	// Track series we've populated to avoid redundant GetCollection calls
	populatedSeries := make(map[uint]bool)

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
