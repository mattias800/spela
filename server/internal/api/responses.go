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
	ID               string    `json:"id"`
	CreatedAt        time.Time `json:"createdAt"`
	UpdatedAt        time.Time `json:"updatedAt"`
	Name             string    `json:"name"`
	Abbreviation     string    `json:"abbreviation"`
	Extensions       []string  `json:"extensions"`
	DefaultCore      string    `json:"defaultCore"`
	EmulatorJSCore   string    `json:"emulatorJsCore"`
	CoverAspectRatio float64   `json:"coverAspectRatio"`
	ColorTheme       string    `json:"colorTheme"`
	IconURL          string    `json:"iconUrl"`
	GameCount        int       `json:"gameCount"`
}

// GameResponse is the enriched API response for a game.
type GameResponse struct {
	ID             string   `json:"id"`
	CreatedAt      time.Time `json:"createdAt"`
	UpdatedAt      time.Time `json:"updatedAt"`
	ConsoleID      string   `json:"consoleId"`
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
	ScrapeAttempts int      `json:"scrapeAttempts"`
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
		ID:               strconv.FormatUint(uint64(c.ID), 10),
		CreatedAt:        c.CreatedAt,
		UpdatedAt:        c.UpdatedAt,
		Name:             c.Name,
		Abbreviation:     c.Abbreviation,
		Extensions:       exts,
		DefaultCore:      c.DefaultCore,
		EmulatorJSCore:   c.EmulatorJSCore,
		CoverAspectRatio: ratio,
		ColorTheme:       c.ColorTheme,
		IconURL:          "/api/consoles/" + strconv.FormatUint(uint64(c.ID), 10) + "/icon",
		GameCount:        c.GameCount,
	}
}

// userGameData holds pre-loaded per-user enrichment data for games.
type userGameData struct {
	favorites   map[uint]bool
	playHistory map[uint]*db.PlayHistory
}

// loadUserGameData batch-loads favorites and play history for a set of game IDs.
// This runs 2 queries total regardless of the number of games.
func loadUserGameData(database *gorm.DB, userID uint, gameIDs []uint) userGameData {
	data := userGameData{
		favorites:   make(map[uint]bool, len(gameIDs)),
		playHistory: make(map[uint]*db.PlayHistory, len(gameIDs)),
	}
	if database == nil || userID == 0 || len(gameIDs) == 0 {
		return data
	}

	// Batch-load favorites
	var favs []db.Favorite
	database.Where("user_id = ? AND game_id IN ?", userID, gameIDs).Find(&favs)
	for _, f := range favs {
		data.favorites[f.GameID] = true
	}

	// Batch-load play history
	var histories []db.PlayHistory
	database.Where("user_id = ? AND game_id IN ?", userID, gameIDs).Find(&histories)
	for i := range histories {
		data.playHistory[histories[i].GameID] = &histories[i]
	}

	return data
}

// toGameResponseWithData converts a db.Game using pre-loaded enrichment data.
func toGameResponseWithData(g db.Game, data *userGameData) GameResponse {
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

	coverURL := g.CoverURL
	if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
		coverURL = "/api/images/" + coverURL
	}

	for i, s := range screenshots {
		if s != "" && !strings.HasPrefix(s, "http") {
			screenshots[i] = "/api/images/" + s
		}
	}

	resp := GameResponse{
		ID:             strconv.FormatUint(uint64(g.ID), 10),
		CreatedAt:      g.CreatedAt,
		UpdatedAt:      g.UpdatedAt,
		ConsoleID:      strconv.FormatUint(uint64(g.ConsoleID), 10),
		ConsoleName:    consoleName,
		Title:          g.Title,
		FileName:       g.FileName,
		FileSize:       g.FileSize,
		Description:    g.Description,
		CoverURL:       coverURL,
		ScreenshotURLs: screenshots,
		Developer:      g.Developer,
		Publisher:       g.Publisher,
		ReleaseDate:    g.ReleaseDate,
		Genre:          g.Genre,
		Players:        g.Players,
		Rating:         g.Rating,
		CoreOverride:   g.CoreOverride,
		ScraperID:      g.ScraperID,
		ScrapeAttempts: g.ScrapeAttempts,
	}

	if data != nil {
		resp.IsFavorite = data.favorites[g.ID]
		if ph, ok := data.playHistory[g.ID]; ok {
			resp.LastPlayedAt = &ph.LastPlayed
			resp.TotalPlayTime = ph.PlayTime
		}
	}

	return resp
}

// ToGameResponse converts a single db.Game to its enriched API response.
// For single-game lookups this runs 2 queries. For batch conversions use ToGameResponses.
func ToGameResponse(g db.Game, database *gorm.DB, userID uint) GameResponse {
	data := loadUserGameData(database, userID, []uint{g.ID})
	return toGameResponseWithData(g, &data)
}

// ToGameResponses converts a slice of db.Game to API responses.
// Batch-loads favorites and play history in 2 queries total.
func ToGameResponses(games []db.Game, database *gorm.DB, userID uint) []GameResponse {
	gameIDs := make([]uint, len(games))
	for i, g := range games {
		gameIDs[i] = g.ID
	}

	data := loadUserGameData(database, userID, gameIDs)

	result := make([]GameResponse, len(games))
	for i, g := range games {
		result[i] = toGameResponseWithData(g, &data)
	}
	return result
}

// UserResponse is the API response for a user, with string ID for consistency.
type UserResponse struct {
	ID        string    `json:"id"`
	Username  string    `json:"username"`
	Email     string    `json:"email"`
	Role      string    `json:"role"`
	AvatarURL string    `json:"avatarUrl,omitempty"`
	Disabled  bool      `json:"disabled"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}

// ToUserResponse converts a db.User to its API response.
func ToUserResponse(u db.User) UserResponse {
	return UserResponse{
		ID:        strconv.FormatUint(uint64(u.ID), 10),
		Username:  u.Username,
		Email:     u.Email,
		Role:      u.Role,
		AvatarURL: u.AvatarURL,
		Disabled:  u.Disabled,
		CreatedAt: u.CreatedAt,
		UpdatedAt: u.UpdatedAt,
	}
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
