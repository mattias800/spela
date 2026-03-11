package api

import (
	"gorm.io/gorm"
)

// ExploreHandler handles explore page endpoints.
type ExploreHandler struct {
	DB *gorm.DB
}

// FeaturedGameResponse is the API response for a featured game in the hero carousel.
type FeaturedGameResponse struct {
	GameID              string  `json:"gameId"`
	Title               string  `json:"title"`
	HeroURL             string  `json:"heroUrl"`
	LogoURL             string  `json:"logoUrl"`
	ConsoleID           string  `json:"consoleId"`
	ConsoleName         string  `json:"consoleName"`
	ConsoleAbbreviation string  `json:"consoleAbbreviation"`
	ConsoleColor        string  `json:"consoleColor"`
	Rating              float64 `json:"rating"`
	Genre               string  `json:"genre"`
	IsFavorite          bool    `json:"isFavorite"`
	IsPlayLater         bool    `json:"isPlayLater"`
}

// ExploreRowResponse is the API response for a single curated row on the explore page.
type ExploreRowResponse struct {
	ID    string         `json:"id"`
	Title string         `json:"title"`
	Games []GameResponse `json:"games"`
}

// ExploreRowsResponse is the API response for all explore rows.
type ExploreRowsResponse struct {
	Rows []ExploreRowResponse `json:"rows"`
}

// FeaturedSeriesResponse is the API response for a featured series on the Explore page.
type FeaturedSeriesResponse struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	LibraryGames int    `json:"libraryGames"`
	TotalGames   int    `json:"totalGames"`
	ConsoleCount int    `json:"consoleCount"`
	HeroURL      string `json:"heroUrl,omitempty"`
}

// DeveloperSummary is the API response for a single developer in the developer list.
type DeveloperSummary struct {
	Name      string   `json:"name"`
	GameCount int      `json:"gameCount"`
	AvgRating float64  `json:"avgRating"`
	Consoles  []string `json:"consoles"`
}

// DeveloperListResponse is the API response for the developers list endpoint.
type DeveloperListResponse struct {
	Developers []DeveloperSummary `json:"developers"`
}

// DeveloperDetailResponse is the API response for a developer detail page.
type DeveloperDetailResponse struct {
	Name              string              `json:"name"`
	GameCount         int                 `json:"gameCount"`
	AvgRating         float64             `json:"avgRating"`
	Consoles          []string            `json:"consoles"`
	Games             []GameResponse      `json:"games"`
	HeroURL           string              `json:"heroUrl,omitempty"`
	TopGames          []GameResponse      `json:"topGames"`
	GenreBreakdown    []GenreCount        `json:"genreBreakdown"`
	PlatformBreakdown []PlatformCount     `json:"platformBreakdown"`
	UserStats         *EntityUserStats    `json:"userStats,omitempty"`
	Publishers        []NameCount         `json:"publishers"`
}

// PublisherDetailResponse is the API response for a publisher detail page.
type PublisherDetailResponse struct {
	Name              string              `json:"name"`
	GameCount         int                 `json:"gameCount"`
	AvgRating         float64             `json:"avgRating"`
	Consoles          []string            `json:"consoles"`
	Games             []GameResponse      `json:"games"`
	HeroURL           string              `json:"heroUrl,omitempty"`
	TopGames          []GameResponse      `json:"topGames"`
	GenreBreakdown    []GenreCount        `json:"genreBreakdown"`
	PlatformBreakdown []PlatformCount     `json:"platformBreakdown"`
	UserStats         *EntityUserStats    `json:"userStats,omitempty"`
	Developers        []NameCount         `json:"developers"`
}

// PlatformCount holds a console name/ID and the number of games on that platform.
type PlatformCount struct {
	ConsoleName string `json:"consoleName"`
	ConsoleID   string `json:"consoleId"`
	Count       int    `json:"count"`
}

// NameCount holds a name and a count, used for publishers and developers breakdowns.
type NameCount struct {
	Name  string `json:"name"`
	Count int    `json:"count"`
}

// EntityUserStats holds user-specific stats for a developer or publisher.
type EntityUserStats struct {
	TotalPlayTime  int64         `json:"totalPlayTime"`
	GamesPlayed    int           `json:"gamesPlayed"`
	FavoriteCount  int           `json:"favoriteCount"`
	MostPlayedGame *GameResponse `json:"mostPlayedGame"`
}

// DeveloperSpotlightResponse is the API response for the featured developer spotlight.
type DeveloperSpotlightResponse struct {
	Name      string         `json:"name"`
	GameCount int            `json:"gameCount"`
	AvgRating float64        `json:"avgRating"`
	Consoles  []string       `json:"consoles"`
	TopGames  []GameResponse `json:"topGames"`
	HeroURL   string         `json:"heroUrl"`
}
