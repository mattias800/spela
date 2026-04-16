package scraper

import (
	"log/slog"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/pouet"
	"github.com/spela/server/internal/retroachievements"
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
	RAClient          *retroachievements.RAClient
	RAAPIKey          string
	DATCache         *DATCache
	GameDirs         []string
	cache            *nameCache

	// Persistent scrape queue (replaces in-memory lock)
	Queue *ScrapeQueue

	// Enrichment state tracking
	enrichMu       sync.Mutex
	enriching      bool
	enrichProgress *EnrichProgress

	// RA circuit breaker — trips after consecutive RA API failures during a
	// scrape to avoid hammering a broken/blocked endpoint for thousands of games.
	// These are non-persisted struct fields that reset on server restart or new
	// Scraper instance, so the circuit automatically re-closes on the next scrape.
	raCircuitOpen          bool
	raConsecutiveFailures  int
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
		Queue:       NewScrapeQueue(database),
	}
}

// IsIGDBConfigured returns whether the scraper has a configured IGDB client.
func (s *Scraper) IsIGDBConfigured() bool {
	return s.IGDBClient != nil && s.IGDBClient.IsConfigured()
}

// IsRAConfigured returns whether the scraper has a configured RA client and API key.
func (s *Scraper) IsRAConfigured() bool {
	return s.RAClient != nil && s.RAAPIKey != ""
}

const raCircuitBreakerThreshold = 5

// tryFetchRAAchievements attempts to fetch RA achievements for a game during
// a scrape. Respects the circuit breaker and never returns an error — RA
// failures are logged but don't fail the overall scrape.
func (s *Scraper) tryFetchRAAchievements(game *db.Game) {
	if !s.IsRAConfigured() || s.raCircuitOpen {
		return
	}
	if err := s.FetchRAAchievements(game); err != nil {
		errStr := err.Error()
		// Detect auth/blocked errors to emit a credentials event
		if strings.Contains(errStr, "status 401") || strings.Contains(errStr, "status 403") {
			db.RecordOperationalEvent(s.DB, db.SystemEventInput{
				EventType: db.SystemEventAPICredentialsInvalid,
				Metadata: map[string]any{
					"service": "retroachievements",
					"error":   errStr,
				},
			})
		}
		s.raConsecutiveFailures++
		if s.raConsecutiveFailures >= raCircuitBreakerThreshold {
			s.raCircuitOpen = true
			slog.Warn("RA achievements disabled for remainder of scrape",
				"consecutiveFailures", s.raConsecutiveFailures, "lastError", err)
			db.RecordOperationalEvent(s.DB, db.SystemEventInput{
				EventType: db.SystemEventRACircuitBreakerTripped,
				Metadata: map[string]any{
					"consecutiveFailures": s.raConsecutiveFailures,
					"lastError":           errStr,
				},
			})
		} else {
			slog.Warn("RA achievement fetch failed", "game", game.Title, "error", err)
		}
	} else {
		s.raConsecutiveFailures = 0
	}
}

// TryStartEnrich attempts to acquire the enrichment lock.
// Returns true if the lock was acquired (caller must call FinishEnrich when done).
// Also checks the scrape queue — enrichment and scraping are mutually exclusive.
func (s *Scraper) TryStartEnrich() bool {
	s.enrichMu.Lock()
	defer s.enrichMu.Unlock()
	if s.enriching {
		return false
	}
	// Scraping and enrichment are mutually exclusive
	activeJob, _ := s.Queue.GetActiveJob()
	if activeJob != nil {
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
	"ARCADE": "FBNeo - Arcade Games",
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
	"ACD32": "Commodore - Amiga CD32",
	"ADEMO": "Commodore - Amiga",
	"MSX1":  "Microsoft - MSX",
	"MSX2":  "Microsoft - MSX2",
	"PS3":   "Sony - PlayStation 3",
	"XBOX":  "Microsoft - Xbox",
	"WII":   "Nintendo - Wii",
}
