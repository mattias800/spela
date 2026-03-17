package scraper

import (
	"context"
	"net/http"
	"sync"
	"time"

	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/pouet"
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
	PouetClient       *pouet.Client
	DATCache         *DATCache
	GameDirs         []string
	cache            *nameCache

	// Scrape state tracking (shared across handlers)
	scrapeMu       sync.Mutex
	scraping       bool
	scrapeProgress *ScrapeProgress
	scrapeCancel   context.CancelFunc

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
		DB:          database,
		Storage:     store,
		HTTPClient:  httpClient,
		PouetClient: pouet.NewClient(),
		DATCache:    NewDATCache(datDir, httpClient),
		GameDirs:    gameDirs,
		cache:       &nameCache{entries: make(map[string][]nameEntry)},
	}
}

// IsIGDBConfigured returns whether the scraper has a configured IGDB client.
func (s *Scraper) IsIGDBConfigured() bool {
	return s.IGDBClient != nil && s.IGDBClient.IsConfigured()
}

// TryStartScrape attempts to acquire the scrape lock.
// Returns a context and true if the lock was acquired. The context is cancelled
// when CancelScrape is called or when FinishScrape cleans up.
// Also checks the enrichment lock — scraping and enrichment are mutually exclusive.
func (s *Scraper) TryStartScrape() (context.Context, bool) {
	s.scrapeMu.Lock()
	defer s.scrapeMu.Unlock()
	s.enrichMu.Lock()
	defer s.enrichMu.Unlock()
	if s.scraping || s.enriching {
		return nil, false
	}
	s.scraping = true
	ctx, cancel := context.WithCancel(context.Background())
	s.scrapeCancel = cancel
	return ctx, true
}

// CancelScrape signals the running scrape to stop gracefully.
// Returns true if a scrape was running and was cancelled.
func (s *Scraper) CancelScrape() bool {
	s.scrapeMu.Lock()
	defer s.scrapeMu.Unlock()
	if !s.scraping || s.scrapeCancel == nil {
		return false
	}
	s.scrapeCancel()
	return true
}

// FinishScrape releases the scrape lock and clears progress.
func (s *Scraper) FinishScrape() {
	s.scrapeMu.Lock()
	s.scraping = false
	s.scrapeProgress = nil
	if s.scrapeCancel != nil {
		s.scrapeCancel()
		s.scrapeCancel = nil
	}
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
	"PCECD":  "NEC - PC Engine CD - TurboGrafx-CD",
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
	"DDEMO": "DOS",
	"AMIGA": "Commodore - Amiga",
	"ADEMO": "Commodore - Amiga",
	"MSX1":  "Microsoft - MSX",
	"MSX2":  "Microsoft - MSX2",
	"PS3":   "Sony - PlayStation 3",
	"WII":   "Nintendo - Wii",
}
