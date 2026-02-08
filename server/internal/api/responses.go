package api

import (
	"strconv"
	"strings"
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// ConsoleResponse is the API response for a console, with extensions as an array
// and coverAspectRatio as a number.
type ConsoleResponse struct {
	ID               uint      `json:"id"`
	CreatedAt        time.Time `json:"createdAt"`
	UpdatedAt        time.Time `json:"updatedAt"`
	Name             string    `json:"name"`
	Abbreviation     string    `json:"abbreviation"`
	Extensions       []string  `json:"extensions"`
	DefaultCore      string    `json:"defaultCore"`
	CoverAspectRatio float64   `json:"coverAspectRatio"`
	ColorTheme       string    `json:"colorTheme"`
	IconURL          string    `json:"iconUrl"`
	GameCount        int       `json:"gameCount"`
}

// GameResponse is the enriched API response for a game.
type GameResponse struct {
	ID             uint     `json:"id"`
	CreatedAt      time.Time `json:"createdAt"`
	UpdatedAt      time.Time `json:"updatedAt"`
	ConsoleID      uint     `json:"consoleId"`
	ConsoleName    string   `json:"consoleName"`
	Title          string   `json:"title"`
	FileName       string   `json:"fileName"`
	FileSize       int64    `json:"fileSize"`
	Description    string   `json:"description"`
	CoverURL       string   `json:"coverUrl"`
	ScreenshotURLs []string `json:"screenshotUrls"`
	Developer      string   `json:"developer"`
	Publisher      string   `json:"publisher"`
	ReleaseDate    string   `json:"releaseDate"`
	Genre          string   `json:"genre"`
	Players        int      `json:"players"`
	Rating         float64  `json:"rating"`
	CoreOverride   string   `json:"coreOverride,omitempty"`
	ScraperID      string   `json:"scraperId,omitempty"`
	IsFavorite     bool     `json:"isFavorite"`
	LastPlayedAt   *time.Time `json:"lastPlayedAt"`
	TotalPlayTime  int64    `json:"totalPlayTime"`
}

// PaginatedResponse wraps a paginated list with standard keys.
type PaginatedResponse struct {
	Data     interface{} `json:"data"`
	Total    int64       `json:"total"`
	Page     int         `json:"page"`
	PageSize int         `json:"pageSize"`
}

// ToConsoleResponse converts a db.Console to its API response.
func ToConsoleResponse(c db.Console) ConsoleResponse {
	exts := strings.Split(c.Extensions, ",")
	for i := range exts {
		exts[i] = strings.TrimSpace(exts[i])
	}

	ratio := parseAspectRatio(c.CoverAspect)

	return ConsoleResponse{
		ID:               c.ID,
		CreatedAt:        c.CreatedAt,
		UpdatedAt:        c.UpdatedAt,
		Name:             c.Name,
		Abbreviation:     c.Abbreviation,
		Extensions:       exts,
		DefaultCore:      c.DefaultCore,
		CoverAspectRatio: ratio,
		ColorTheme:       c.ColorTheme,
		IconURL:          "/api/consoles/" + strconv.FormatUint(uint64(c.ID), 10) + "/icon",
		GameCount:        c.GameCount,
	}
}

// ToGameResponse converts a db.Game to its enriched API response.
// Pass the gorm.DB and userID to look up favorites and play history.
func ToGameResponse(g db.Game, database *gorm.DB, userID uint) GameResponse {
	var screenshots []string
	if g.ScreenshotURL != "" {
		screenshots = strings.Split(g.ScreenshotURL, ",")
		for i := range screenshots {
			screenshots[i] = strings.TrimSpace(screenshots[i])
		}
	} else {
		screenshots = []string{}
	}

	consoleName := ""
	if g.Console.ID != 0 {
		consoleName = g.Console.Name
	}

	resp := GameResponse{
		ID:             g.ID,
		CreatedAt:      g.CreatedAt,
		UpdatedAt:      g.UpdatedAt,
		ConsoleID:      g.ConsoleID,
		ConsoleName:    consoleName,
		Title:          g.Title,
		FileName:       g.FileName,
		FileSize:       g.FileSize,
		Description:    g.Description,
		CoverURL:       g.CoverURL,
		ScreenshotURLs: screenshots,
		Developer:      g.Developer,
		Publisher:       g.Publisher,
		ReleaseDate:    g.ReleaseDate,
		Genre:          g.Genre,
		Players:        g.Players,
		Rating:         g.Rating,
		CoreOverride:   g.CoreOverride,
		ScraperID:      g.ScraperID,
	}

	if database != nil && userID > 0 {
		// Check favorite
		var favCount int64
		database.Model(&db.Favorite{}).Where("user_id = ? AND game_id = ?", userID, g.ID).Count(&favCount)
		resp.IsFavorite = favCount > 0

		// Get play history
		var ph db.PlayHistory
		if err := database.Where("user_id = ? AND game_id = ?", userID, g.ID).First(&ph).Error; err == nil {
			resp.LastPlayedAt = &ph.LastPlayed
			resp.TotalPlayTime = ph.PlayTime
		}
	}

	return resp
}

// ToGameResponses converts a slice of db.Game to API responses.
func ToGameResponses(games []db.Game, database *gorm.DB, userID uint) []GameResponse {
	result := make([]GameResponse, len(games))
	for i, g := range games {
		result[i] = ToGameResponse(g, database, userID)
	}
	return result
}

// parseAspectRatio converts a string like "3:4" to a float like 0.75.
func parseAspectRatio(aspect string) float64 {
	parts := strings.SplitN(aspect, ":", 2)
	if len(parts) != 2 {
		return 0.75
	}
	w, err1 := strconv.ParseFloat(parts[0], 64)
	h, err2 := strconv.ParseFloat(parts[1], 64)
	if err1 != nil || err2 != nil || h == 0 {
		return 0.75
	}
	return w / h
}
