package scraper

import (
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// Scraper fetches metadata from ScreenScraper.
type Scraper struct {
	DB         *gorm.DB
	DevID      string
	DevPass    string
	SoftName   string
	UserName   string
	UserPass   string
	HTTPClient *http.Client
}

// NewScraper creates a new metadata scraper instance.
func NewScraper(database *gorm.DB) *Scraper {
	return &Scraper{
		DB:       database,
		SoftName: "spela",
		HTTPClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// Configure sets the ScreenScraper API credentials.
func (s *Scraper) Configure(devID, devPass, userName, userPass string) {
	s.DevID = devID
	s.DevPass = devPass
	s.UserName = userName
	s.UserPass = userPass
}

// IsConfigured returns whether scraper credentials are set.
func (s *Scraper) IsConfigured() bool {
	return s.DevID != "" && s.DevPass != ""
}

// screenScraperAPIBase is the API base URL.
const screenScraperAPIBase = "https://api.screenscraper.fr/api2"

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

// ScrapeGame fetches metadata for a single game from ScreenScraper.
func (s *Scraper) ScrapeGame(game *db.Game) error {
	if !s.IsConfigured() {
		return fmt.Errorf("scraper not configured: set ScreenScraper credentials")
	}

	// Look up console for system ID
	var console db.Console
	if err := s.DB.First(&console, game.ConsoleID).Error; err != nil {
		return fmt.Errorf("loading console for game: %w", err)
	}

	systemID, ok := abbreviationToSystemID[console.Abbreviation]
	if !ok {
		return fmt.Errorf("no ScreenScraper system ID for console %s", console.Abbreviation)
	}

	params := url.Values{
		"devid":      {s.DevID},
		"devpassword": {s.DevPass},
		"softname":   {s.SoftName},
		"output":     {"json"},
		"systemeid":  {systemID},
		"romnom":     {game.FileName},
	}
	if s.UserName != "" {
		params.Set("ssid", s.UserName)
		params.Set("sspassword", s.UserPass)
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

	// Update game metadata
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

	// Cover art and screenshot
	for _, media := range jeu.Medias {
		switch {
		case strings.Contains(media.Type, "box-2D") && game.CoverURL == "":
			game.CoverURL = media.URL
		case strings.Contains(media.Type, "ss") && game.ScreenshotURL == "":
			game.ScreenshotURL = media.URL
		}
	}

	if err := s.DB.Save(game).Error; err != nil {
		return fmt.Errorf("saving scraped metadata: %w", err)
	}

	slog.Info("scraped metadata", "game", game.Title, "scraperId", game.ScraperID)
	return nil
}

// ScrapeAll fetches metadata for all games that don't have scraper IDs.
func (s *Scraper) ScrapeAll() (int, error) {
	if !s.IsConfigured() {
		return 0, fmt.Errorf("scraper not configured")
	}

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
		// Rate limit: ScreenScraper requires 1 request per second for non-registered devs
		time.Sleep(1100 * time.Millisecond)
	}

	return scraped, nil
}
