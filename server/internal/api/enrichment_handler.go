package api

import (
	"sync"
	"time"

	"github.com/spela/server/internal/scraper"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// EnrichmentHandler handles enrichment-related API endpoints.
type EnrichmentHandler struct {
	DB      *gorm.DB
	Scraper *scraper.Scraper
	Hub     *ws.Hub

	// In-process cache for GET /api/keywords — the result is a function
	// of library-wide game→keyword aggregation, not user-specific, and
	// only changes when the scraper writes new game_keywords rows.
	// See [listKeywordsTTL] for the cache window.
	listKeywordsCacheMu sync.Mutex
	listKeywordsCache   map[int]cachedKeywords
}

type cachedKeywords struct {
	at     time.Time
	result []KeywordResponse
}

// --- Theme endpoints ---

// ThemeResponse is the API response for a theme with game count.
type ThemeResponse struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	GameCount int    `json:"gameCount"`
}

// --- Keyword endpoints ---

// KeywordResponse is the API response for a keyword with game count.
type KeywordResponse struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	GameCount int    `json:"gameCount"`
}

// --- Series endpoints ---

// SeriesListResponse is the API response for a series in the list view.
type SeriesListResponse struct {
	ID               string `json:"id"`
	IGDBCollectionID int    `json:"igdbCollectionId"`
	Name             string `json:"name"`
	TotalGames       int    `json:"totalGames"`
	LibraryGames     int    `json:"libraryGames"`
}

// SeriesDetailResponse is the API response for a series detail view.
type SeriesDetailResponse struct {
	ID               string               `json:"id"`
	IGDBCollectionID int                  `json:"igdbCollectionId"`
	Name             string               `json:"name"`
	HeroURL          string               `json:"heroUrl"`
	LogoURL          string               `json:"logoUrl"`
	LibraryGames     int                  `json:"libraryGames"`
	TotalGames       int                  `json:"totalGames"`
	Consoles         []SeriesConsoleInfo  `json:"consoles"`
	Games            []SeriesGameResponse `json:"games"`
}

// SeriesConsoleInfo represents a console with game count in a series.
type SeriesConsoleInfo struct {
	Abbreviation string `json:"abbreviation"`
	Name         string `json:"name"`
	Color        string `json:"color"`
	GameCount    int    `json:"gameCount"`
}

// SeriesGameResponse is the API response for a game within a series.
type SeriesGameResponse struct {
	IGDBGameID          int     `json:"igdbGameId"`
	Name                string  `json:"name"`
	InLibrary           bool    `json:"inLibrary"`
	LocalGameID         *string `json:"localGameId"`
	CoverURL            *string `json:"coverUrl"`
	ReleaseDate         string  `json:"releaseDate"`
	IGDBCriticsRating   float64 `json:"igdbCriticsRating"`
	ConsoleAbbreviation string  `json:"consoleAbbreviation"`
	ConsoleName         string  `json:"consoleName"`
	ConsoleColor        string  `json:"consoleColor"`
}

// --- Franchise endpoints ---

// FranchiseResponse is the API response for a franchise with game count.
type FranchiseResponse struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	GameCount int    `json:"gameCount"`
}

// FranchiseDetailResponse is the API response for a franchise detail view.
type FranchiseDetailResponse struct {
	ID              string               `json:"id"`
	IGDBFranchiseID int                  `json:"igdbFranchiseId"`
	Name            string               `json:"name"`
	HeroURL         string               `json:"heroUrl"`
	LogoURL         string               `json:"logoUrl"`
	LibraryGames    int                  `json:"libraryGames"`
	TotalGames      int                  `json:"totalGames"`
	Consoles        []SeriesConsoleInfo  `json:"consoles"`
	Games           []SeriesGameResponse `json:"games"`
}

// --- Per-game series/franchise endpoints ---

// GameSeriesResponse is the API response for a series that a game belongs to.
type GameSeriesResponse struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	TotalGames   int    `json:"totalGames"`
	LibraryGames int    `json:"libraryGames"`
}

// GameFranchiseResponse is the API response for a franchise that a game belongs to.
type GameFranchiseResponse struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	TotalGames   int    `json:"totalGames"`
	LibraryGames int    `json:"libraryGames"`
}
