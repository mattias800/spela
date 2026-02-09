package scraper

import (
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// Scraper fetches game images from LibRetro Thumbnails and optionally
// enriches text metadata from ScreenScraper.
type Scraper struct {
	DB         *gorm.DB
	Storage    *storage.Storage
	HTTPClient *http.Client
	// ScreenScraper credentials (optional)
	SSDevID    string
	SSDevPass  string
	SSSoftName string
	SSUserName string
	SSUserPass string
}

// NewScraper creates a new metadata scraper instance.
func NewScraper(database *gorm.DB, store *storage.Storage) *Scraper {
	return &Scraper{
		DB:         database,
		Storage:    store,
		SSSoftName: "spela",
		SSDevID:    "spela",
		SSDevPass:  "spela",
		HTTPClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// Configure sets the ScreenScraper user credentials for optional text metadata enrichment.
func (s *Scraper) Configure(userName, userPass string) {
	s.SSUserName = userName
	s.SSUserPass = userPass
}

// IsConfigured returns whether ScreenScraper user credentials are set.
func (s *Scraper) IsConfigured() bool {
	return s.SSUserName != "" && s.SSUserPass != ""
}

// abbreviationToLibRetro maps console abbreviations to LibRetro system names.
var abbreviationToLibRetro = map[string]string{
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
}

// abbreviationToSystemID maps console abbreviations to ScreenScraper system IDs.
var abbreviationToSystemID = map[string]string{
	"NES":    "3",
	"SNES":   "4",
	"GB":     "9",
	"GBC":    "10",
	"GBA":    "12",
	"N64":    "14",
	"NDS":    "15",
	"SMS":    "2",
	"GEN":    "1",
	"SAT":    "22",
	"PSX":    "57",
	"PSP":    "61",
	"NEOGEO": "142",
	"ARCADE": "75",
	"PCE":    "31",
	"A26":    "26",
}

const libRetroThumbnailBase = "https://thumbnails.libretro.com"

// screenScraperAPIBase is the API base URL.
const screenScraperAPIBase = "https://api.screenscraper.fr/api2"

// gameNameFromFileName strips the file extension to derive the game name.
func gameNameFromFileName(fileName string) string {
	ext := filepath.Ext(fileName)
	return strings.TrimSuffix(fileName, ext)
}

// downloadLibRetroImage downloads an image from LibRetro Thumbnails and saves it locally.
// Returns the relative storage path on success, or empty string if the image was not found.
func (s *Scraper) downloadLibRetroImage(system, gameName, imageType, subpath string) string {
	imageURL := fmt.Sprintf("%s/%s/%s/%s.png",
		libRetroThumbnailBase,
		url.PathEscape(system),
		imageType,
		url.PathEscape(gameName),
	)

	resp, err := s.HTTPClient.Get(imageURL)
	if err != nil {
		slog.Debug("failed to fetch LibRetro image", "url", imageURL, "error", err)
		return ""
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		slog.Debug("LibRetro image not found", "url", imageURL, "status", resp.StatusCode)
		return ""
	}

	savedPath, err := s.Storage.WriteImage(subpath, resp.Body)
	if err != nil {
		slog.Warn("failed to save LibRetro image", "subpath", subpath, "error", err)
		return ""
	}

	return savedPath
}

// ScrapeGame fetches images from LibRetro Thumbnails and optionally enriches
// text metadata from ScreenScraper.
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

	// --- LibRetro Thumbnails (always available, no credentials needed) ---
	libRetroSystem, hasLibRetro := abbreviationToLibRetro[console.Abbreviation]
	if hasLibRetro {
		// Box art (primary)
		boxartSubpath := fmt.Sprintf("%s/%s/boxart.png", console.Abbreviation, gameIDStr)
		if path := s.downloadLibRetroImage(libRetroSystem, gameName, "Named_Boxarts", boxartSubpath); path != "" {
			game.CoverURL = path
		}

		// Screenshot
		snapSubpath := fmt.Sprintf("%s/%s/screenshot.png", console.Abbreviation, gameIDStr)
		if path := s.downloadLibRetroImage(libRetroSystem, gameName, "Named_Snaps", snapSubpath); path != "" {
			game.ScreenshotURL = path
		}
	}

	// --- ScreenScraper (optional, for text metadata only) ---
	if s.IsConfigured() {
		if err := s.scrapeScreenScraperMetadata(game, console); err != nil {
			slog.Warn("ScreenScraper metadata enrichment failed", "game", game.Title, "error", err)
		}
	}

	// Set scraper ID
	if game.ScraperID == "" {
		game.ScraperID = "libretro"
	}

	if err := s.DB.Save(game).Error; err != nil {
		return fmt.Errorf("saving scraped metadata: %w", err)
	}

	slog.Info("scraped metadata", "game", game.Title, "scraperId", game.ScraperID)
	return nil
}

// ssResponse represents a ScreenScraper API game info response (simplified).
type ssResponse struct {
	Response struct {
		Jeu struct {
			ID    string `json:"id"`
			Noms  []struct {
				Region string `json:"region"`
				Text   string `json:"text"`
			} `json:"noms"`
			Synopsis []struct {
				Langue string `json:"langue"`
				Text   string `json:"text"`
			} `json:"synopsis"`
			Developpeur struct {
				Text string `json:"text"`
			} `json:"developpeur"`
			Editeur struct {
				Text string `json:"text"`
			} `json:"editeur"`
			Dates []struct {
				Region string `json:"region"`
				Text   string `json:"text"`
			} `json:"dates"`
			Genres []struct {
				Noms []struct {
					Langue string `json:"langue"`
					Text   string `json:"text"`
				} `json:"noms_genre"`
			} `json:"genres"`
			Joueurs struct {
				Text string `json:"text"`
			} `json:"joueurs"`
			Note struct {
				Text string `json:"text"`
			} `json:"note"`
			Medias []struct {
				Type   string `json:"type"`
				URL    string `json:"url"`
				Region string `json:"region"`
				Format string `json:"format"`
			} `json:"medias"`
		} `json:"jeu"`
	} `json:"response"`
}

// scrapeScreenScraperMetadata fetches text metadata (description, developer, etc.) from ScreenScraper.
// It does not fetch images — those come from LibRetro.
func (s *Scraper) scrapeScreenScraperMetadata(game *db.Game, console db.Console) error {
	systemID, ok := abbreviationToSystemID[console.Abbreviation]
	if !ok {
		return fmt.Errorf("no ScreenScraper system ID for console %s", console.Abbreviation)
	}

	params := url.Values{
		"devid":       {s.SSDevID},
		"devpassword": {s.SSDevPass},
		"softname":    {s.SSSoftName},
		"output":      {"json"},
		"systemeid":   {systemID},
		"romnom":      {game.FileName},
	}
	if s.SSUserName != "" {
		params.Set("ssid", s.SSUserName)
		params.Set("sspassword", s.SSUserPass)
	}

	apiURL := fmt.Sprintf("%s/jeuInfos.php?%s", screenScraperAPIBase, params.Encode())

	resp, err := s.HTTPClient.Get(apiURL)
	if err != nil {
		return fmt.Errorf("calling ScreenScraper API: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("ScreenScraper API returned %d: %s", resp.StatusCode, string(body))
	}

	var ssResp ssResponse
	if err := json.NewDecoder(resp.Body).Decode(&ssResp); err != nil {
		return fmt.Errorf("decoding ScreenScraper response: %w", err)
	}

	jeu := ssResp.Response.Jeu

	game.ScraperID = jeu.ID

	// Title: prefer world/us region
	for _, nom := range jeu.Noms {
		if nom.Region == "wor" || nom.Region == "us" || nom.Region == "eu" {
			game.Title = nom.Text
			break
		}
	}

	// Description: prefer English
	for _, syn := range jeu.Synopsis {
		if syn.Langue == "en" {
			game.Description = syn.Text
			break
		}
	}

	game.Developer = jeu.Developpeur.Text
	game.Publisher = jeu.Editeur.Text

	// Release date
	for _, d := range jeu.Dates {
		if d.Region == "wor" || d.Region == "us" || d.Region == "eu" {
			game.ReleaseDate = d.Text
			break
		}
	}

	// Genre
	if len(jeu.Genres) > 0 && len(jeu.Genres[0].Noms) > 0 {
		for _, n := range jeu.Genres[0].Noms {
			if n.Langue == "en" {
				game.Genre = n.Text
				break
			}
		}
	}

	// Players
	if jeu.Joueurs.Text != "" {
		fmt.Sscanf(jeu.Joueurs.Text, "%d", &game.Players)
	}

	return nil
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
