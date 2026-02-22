package scraper

import (
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// Scraper fetches game metadata and images from IGDB (primary) and LibRetro Thumbnails (fallback).
type Scraper struct {
	DB         *gorm.DB
	Storage    *storage.Storage
	HTTPClient *http.Client
	IGDBClient *igdb.Client
	cache      *nameCache
}

// NewScraper creates a new metadata scraper instance.
func NewScraper(database *gorm.DB, store *storage.Storage) *Scraper {
	return &Scraper{
		DB:      database,
		Storage: store,
		HTTPClient: &http.Client{
			Timeout: 30 * time.Second,
		},
		cache: &nameCache{entries: make(map[string][]nameEntry)},
	}
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
}

const libRetroThumbnailBase = "https://thumbnails.libretro.com"

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
// Tries the exact game name first, then falls back to fuzzy matching against the
// full LibRetro directory listing for the system.
// Returns the relative storage path on success, or empty string if the image was not found.
func (s *Scraper) downloadLibRetroImage(system, gameName, imageType, subpath string) string {
	// Try exact name first (single HTTP request)
	if path := s.tryDownloadImage(system, gameName, imageType, subpath); path != "" {
		return path
	}

	// Fuzzy fallback: fetch the full name listing and find the best match
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

	gameName := gameNameFromFileName(game.FileName)
	gameIDStr := strconv.FormatUint(uint64(game.ID), 10)

	// --- IGDB (primary metadata + images, when configured) ---
	if s.IGDBClient != nil && s.IGDBClient.IsConfigured() {
		if err := s.scrapeIGDB(game, console, gameIDStr); err != nil {
			slog.Warn("IGDB scrape failed, falling back to LibRetro", "game", game.Title, "error", err)
		}
	}

	// --- LibRetro Thumbnails (fallback for images) ---
	libRetroSystem, hasLibRetro := AbbreviationToLibRetro[console.Abbreviation]
	if hasLibRetro {
		// Box art fallback
		if game.CoverURL == "" {
			boxartSubpath := fmt.Sprintf("%s/%s/boxart.png", console.Abbreviation, gameIDStr)
			if path := s.downloadLibRetroImage(libRetroSystem, gameName, "Named_Boxarts", boxartSubpath); path != "" {
				game.CoverURL = path
			}
		}

		// Screenshot fallback
		if game.ScreenshotURL == "" {
			snapSubpath := fmt.Sprintf("%s/%s/screenshot.png", console.Abbreviation, gameIDStr)
			if path := s.downloadLibRetroImage(libRetroSystem, gameName, "Named_Snaps", snapSubpath); path != "" {
				game.ScreenshotURL = path
			}
		}
	}

	// Set scraper ID if not already set by IGDB
	if game.ScraperID == "" {
		game.ScraperID = "libretro"
	}

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

	games, err := s.IGDBClient.SearchGame(cleanName, platformID)
	if err != nil {
		return fmt.Errorf("IGDB search: %w", err)
	}

	if len(games) == 0 {
		slog.Debug("no IGDB results", "game", cleanName, "platform", console.Abbreviation)
		return nil
	}

	// Pick the first (best) match
	match := games[0]

	// Set scraper ID
	game.ScraperID = fmt.Sprintf("igdb:%d", match.ID)

	// Populate metadata — don't overwrite existing non-empty fields with empty values
	if match.Name != "" {
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

	// Download cover art from IGDB
	if match.Cover != nil && match.Cover.ImageID != "" {
		coverURL := igdb.ImageURL(match.Cover.ImageID, "cover_big")
		coverSubpath := fmt.Sprintf("%s/%s/boxart.jpg", console.Abbreviation, gameIDStr)
		if path := s.downloadExternalImage(coverURL, coverSubpath); path != "" {
			game.CoverURL = path
		}
	}

	// Download screenshot from IGDB
	if len(match.Screenshots) > 0 && match.Screenshots[0].ImageID != "" {
		screenshotURL := igdb.ImageURL(match.Screenshots[0].ImageID, "screenshot_big")
		screenshotSubpath := fmt.Sprintf("%s/%s/screenshot.jpg", console.Abbreviation, gameIDStr)
		if path := s.downloadExternalImage(screenshotURL, screenshotSubpath); path != "" {
			game.ScreenshotURL = path
		}
	}

	slog.Info("IGDB match found", "game", cleanName, "matched", match.Name, "igdbId", match.ID)
	return nil
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

// ScrapeAll fetches metadata for all games that don't have scraper IDs.
func (s *Scraper) ScrapeAll() (int, error) {
	var games []db.Game
	if err := s.DB.Where("scraper_id = '' OR scraper_id IS NULL").Find(&games).Error; err != nil {
		return 0, fmt.Errorf("loading unscraped games: %w", err)
	}

	scraped := 0
	for i := range games {
		if err := s.ScrapeGame(&games[i]); err != nil {
			slog.Warn("failed to scrape game", "game", games[i].Title, "error", err)
			continue
		}
		scraped++
		// Small delay to avoid hammering the thumbnail server
		time.Sleep(200 * time.Millisecond)
	}

	return scraped, nil
}
