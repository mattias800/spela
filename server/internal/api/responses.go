package api

import (
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// ErrorResponse is the standard JSON shape returned by every handler that
// reports an error to the client. Wire format is { "error": "..." } or
// { "error": "...", "message": "..." } when a user-facing message is
// available. This matches the historical ad-hoc shape so existing consumers
// keep working unchanged.
//
// Error  — technical message (for logging / forensic context)
// Message — user-facing message intended for display in the UI; omit if
// the same as Error.
type ErrorResponse struct {
	Error   string `json:"error"`
	Message string `json:"message,omitempty"`
}

// MediaTypeCategoryResponse is the API response for a media type category.
type MediaTypeCategoryResponse struct {
	Code string `json:"code"`
	Name string `json:"name"`
}

// MediaTypeResponse is the API response for a media type.
type MediaTypeResponse struct {
	Code     string                    `json:"code"`
	Name     string                    `json:"name"`
	Category MediaTypeCategoryResponse `json:"category"`
}

// HardwareMakerResponse is the API response for a hardware maker.
type HardwareMakerResponse struct {
	Code string `json:"code"`
	Name string `json:"name"`
}

// MakerDetailResponse is the API response for a hardware maker with its consoles.
type MakerDetailResponse struct {
	Code         string            `json:"code"`
	Name         string            `json:"name"`
	ConsoleCount int               `json:"consoleCount"`
	Consoles     []ConsoleResponse `json:"consoles,omitempty"`
}

// ConsoleResponse is the API response for a console, with extensions as an array
// and coverAspectRatio as a number.
type ConsoleResponse struct {
	ID               string                 `json:"id"`
	Code             string                 `json:"code"`
	CreatedAt        time.Time              `json:"createdAt"`
	UpdatedAt        time.Time              `json:"updatedAt"`
	Name             string                 `json:"name"`
	Abbreviation     string                 `json:"abbreviation"`
	Maker            *HardwareMakerResponse `json:"maker"`
	MediaType        *MediaTypeResponse     `json:"mediaType"`
	ReleaseYear      *int                   `json:"releaseYear"`
	UnitsSold        *int64                 `json:"unitsSold"`
	Summary          *string                `json:"summary"`
	Extensions       []string               `json:"extensions"`
	DefaultCore      string                 `json:"defaultCore"`
	EmulatorJSCore   string                 `json:"emulatorJsCore"`
	CoverAspectRatio float64                `json:"coverAspectRatio"`
	ColorTheme       string                 `json:"colorTheme"`
	Generation       int                    `json:"generation"`
	IconURL          string                 `json:"iconUrl"`
	LogoURL          string                 `json:"logoUrl"`
	LogoPngURL       string                 `json:"logoPngUrl"`
	GameCount        int                    `json:"gameCount"`
	SaveStateSupport bool                   `json:"saveStateSupport"`
	BrowserPlayable  bool                   `json:"browserPlayable"`
	Playable         bool                   `json:"playable"`
}

// DiscResponse is the API response for a single disc in a multi-disc game.
type DiscResponse struct {
	DiscNumber int    `json:"discNumber"`
	FileName   string `json:"fileName"`
	FileSize   int64  `json:"fileSize"`
}

// GameResponse is the enriched API response for a game.
type GameResponse struct {
	ID               string         `json:"id"`
	CreatedAt        time.Time      `json:"createdAt"`
	UpdatedAt        time.Time      `json:"updatedAt"`
	ConsoleID        string         `json:"consoleId"`
	ConsoleName      string         `json:"consoleName"`
	CoverAspectRatio float64        `json:"coverAspectRatio"`
	Title            string         `json:"title"`
	FileName       string         `json:"fileName"`
	FileSize       int64          `json:"fileSize"`
	DiscCount      int            `json:"discCount"`
	Discs          []DiscResponse `json:"discs,omitempty"`
	Description    string         `json:"description"`
	CoverURL       string         `json:"coverUrl"`
	ScreenshotURLs []string       `json:"screenshotUrls"`
	Developer      string         `json:"developer"`
	Publisher      string         `json:"publisher"`
	ReleaseDate    string         `json:"releaseDate"`
	Genre          string                `json:"genre"`
	GameModes            string                      `json:"gameModes,omitempty"`
	Storyline            string                      `json:"storyline,omitempty"`
	TotalRating          float64                     `json:"totalRating,omitempty"`
	TotalRatingCount     int                         `json:"totalRatingCount,omitempty"`
	IGDBUserRating       float64                     `json:"igdbUserRating,omitempty"`
	IGDBUserRatingCount  int                         `json:"igdbUserRatingCount,omitempty"`
	TimeToBeatHastily    int                         `json:"timeToBeatHastily,omitempty"`
	TimeToBeatNormally   int                         `json:"timeToBeatNormally,omitempty"`
	TimeToBeatCompletely int                         `json:"timeToBeatCompletely,omitempty"`
	Players              int                         `json:"players"`
	IGDBCriticsRating    float64                     `json:"igdbCriticsRating"`
	ReleaseDates         []ReleaseDateResponse       `json:"releaseDates,omitempty"`
	Videos               []VideoResponse             `json:"videos,omitempty"`
	LanguageSupports     []LanguageSupportResponse   `json:"languageSupports,omitempty"`
	AgeRatings           []AgeRatingResponse         `json:"ageRatings,omitempty"`
	PartyInfo           string         `json:"partyInfo,omitempty"`
	Playable            bool           `json:"playable"`
	CoreOverride        string         `json:"coreOverride,omitempty"`
	ScraperID           string         `json:"scraperId,omitempty"`
	ScrapeAttempts      int            `json:"scrapeAttempts"`
	AchievementsWarning string         `json:"achievementsWarning,omitempty"`
	VerificationStatus  string         `json:"verificationStatus,omitempty"`
	VerificationTag     string         `json:"verificationTag,omitempty"`
	Region              string         `json:"region,omitempty"`
	Revision       string         `json:"revision,omitempty"`
	Tags           string         `json:"tags,omitempty"`
	IsPreRelease   bool           `json:"isPreRelease"`
	VariantCount   int            `json:"variantCount,omitempty"`
	GroupKey       string         `json:"groupKey,omitempty"`
	Variants       []VariantResponse `json:"variants,omitempty"`
	ParentGame     *ParentGameResponse  `json:"parentGame,omitempty"`
	RomHacks       []RomHackGameResponse `json:"romHacks,omitempty"`
	HeroURL        string         `json:"heroUrl,omitempty"`
	LogoURL        string         `json:"logoUrl,omitempty"`
	BiosStatus     string         `json:"biosStatus,omitempty"`
	IsFavorite     bool           `json:"isFavorite"`
	IsInPlayLater  bool           `json:"isInPlayLater"`
	LastPlayedAt   *time.Time     `json:"lastPlayedAt"`
	TotalPlayTime  int64          `json:"totalPlayTime"`
	AverageRating  float64        `json:"averageRating"`
	RatingCount    int64          `json:"ratingCount"`
	UserRating     *int           `json:"userRating,omitempty"`
}

// ReleaseDateResponse represents a regional release date in the API response.
type ReleaseDateResponse struct {
	Region   string `json:"region"`
	Date     string `json:"date"`
	Platform string `json:"platform,omitempty"`
}

// VideoResponse represents a video in the API response.
type VideoResponse struct {
	VideoID string `json:"videoId"`
	Name    string `json:"name,omitempty"`
}

// LanguageSupportResponse represents a language support entry in the API response.
type LanguageSupportResponse struct {
	Language    string `json:"language"`
	SupportType string `json:"supportType"`
}

// AgeRatingResponse represents an age rating in the API response.
type AgeRatingResponse struct {
	Category string `json:"category"`
	Rating   string `json:"rating"`
}

// VariantResponse is a simplified game entry for listing variants of a game.
type VariantResponse struct {
	ID                 string `json:"id"`
	Title              string `json:"title"`
	FileName           string `json:"fileName"`
	Region             string `json:"region,omitempty"`
	Revision           string `json:"revision,omitempty"`
	Tags               string `json:"tags,omitempty"`
	IsPreRelease       bool   `json:"isPreRelease"`
	FileSize           int64  `json:"fileSize"`
	VerificationStatus string `json:"verificationStatus,omitempty"`
}

// ParentGameResponse is a minimal reference to a parent game for standalone ROM hacks.
type ParentGameResponse struct {
	ID       string `json:"id"`
	Title    string `json:"title"`
	CoverURL string `json:"coverUrl,omitempty"`
}

// RomHackGameResponse is a minimal reference to a standalone ROM hack.
type RomHackGameResponse struct {
	ID       string `json:"id"`
	Title    string `json:"title"`
	CoverURL string `json:"coverUrl,omitempty"`
}

// PaginatedResponse wraps a paginated list with standard keys. The type
// parameter is the element type of Data so each call site declares exactly
// what is being paginated (e.g. PaginatedResponse[GameResponse]). Tests that
// don't care about element shape can use PaginatedResponse[json.RawMessage].
type PaginatedResponse[T any] struct {
	Data     []T   `json:"data"`
	Total    int64 `json:"total"`
	Page     int   `json:"page"`
	PageSize int   `json:"pageSize"`
}

// ToConsoleResponse converts a db.Console to its API response.
func ToConsoleResponse(c db.Console) ConsoleResponse {
	exts := strings.Split(c.Extensions, ",")
	for i := range exts {
		exts[i] = strings.TrimSpace(exts[i])
	}

	ratio := parseAspectRatio(c.CoverAspect)

	abbr := strings.ToLower(c.Abbreviation)

	code := abbr
	if c.Code != nil && *c.Code != "" {
		code = *c.Code
	}

	var maker *HardwareMakerResponse
	if c.HardwareMaker != nil {
		maker = &HardwareMakerResponse{
			Code: c.HardwareMaker.Code,
			Name: c.HardwareMaker.Name,
		}
	}

	var mediaType *MediaTypeResponse
	if c.MediaType != nil {
		mediaType = &MediaTypeResponse{
			Code: c.MediaType.Code,
			Name: c.MediaType.Name,
			Category: MediaTypeCategoryResponse{
				Code: c.MediaType.Category.Code,
				Name: c.MediaType.Category.Name,
			},
		}
	}

	return ConsoleResponse{
		ID:               abbr,
		Code:             code,
		CreatedAt:        c.CreatedAt,
		UpdatedAt:        c.UpdatedAt,
		Name:             c.Name,
		Abbreviation:     c.Abbreviation,
		Maker:            maker,
		MediaType:        mediaType,
		ReleaseYear:      c.ReleaseYear,
		UnitsSold:        c.UnitsSold,
		Summary:          c.Summary,
		Extensions:       exts,
		DefaultCore:      c.DefaultCore,
		EmulatorJSCore:   c.EmulatorJSCore,
		CoverAspectRatio: ratio,
		ColorTheme:       c.ColorTheme,
		Generation:       c.Generation,
		IconURL:          "/api/consoles/" + abbr + "/icon",
		LogoURL:          "/api/consoles/" + abbr + "/logo",
		LogoPngURL:       "/api/consoles/" + abbr + "/logo.png",
		GameCount:        c.GameCount,
		SaveStateSupport: c.SaveStateSupport,
		BrowserPlayable:  c.EmulatorJSCore != "",
		Playable:         c.Playable,
	}
}

// ratingAggregate holds average rating and count for a game.
type ratingAggregate struct {
	AverageRating float64
	RatingCount   int64
}

// userGameData holds pre-loaded per-user enrichment data for games.
type userGameData struct {
	favorites   map[uint]bool
	playLater   map[uint]bool
	playHistory map[uint]*db.PlayHistory
	userRatings map[uint]int            // gameID -> user's rating (1-5)
	ratingAggs  map[uint]ratingAggregate // gameID -> aggregate rating data
	artworks    map[uint]*db.GameArtwork // gameID -> artwork (hero, logo, etc.)
}

// loadUserGameData batch-loads favorites, play later, play history, ratings, and
// user ratings for a set of game IDs. This runs 5 queries total regardless of the
// number of games (1 for rating aggregates + 4 user-scoped queries).
func loadUserGameData(database *gorm.DB, userID uint, gameIDs []uint) userGameData {
	data := userGameData{
		favorites:   make(map[uint]bool, len(gameIDs)),
		playLater:   make(map[uint]bool, len(gameIDs)),
		playHistory: make(map[uint]*db.PlayHistory, len(gameIDs)),
		userRatings: make(map[uint]int, len(gameIDs)),
		ratingAggs:  make(map[uint]ratingAggregate, len(gameIDs)),
		artworks:    make(map[uint]*db.GameArtwork, len(gameIDs)),
	}
	if database == nil || len(gameIDs) == 0 {
		return data
	}

	// Batch-load artwork (hero/logo URLs from SteamGridDB)
	var artworks []db.GameArtwork
	database.Where("game_id IN ?", gameIDs).Find(&artworks)
	for i := range artworks {
		data.artworks[artworks[i].GameID] = &artworks[i]
	}

	// Batch-load rating aggregates (works even without a logged-in user)
	type aggRow struct {
		GameID        uint
		AverageRating float64
		RatingCount   int64
	}
	var aggRows []aggRow
	database.Model(&db.GameRating{}).
		Where("game_id IN ?", gameIDs).
		Select("game_id, AVG(rating) as average_rating, COUNT(*) as rating_count").
		Group("game_id").
		Scan(&aggRows)
	for _, r := range aggRows {
		data.ratingAggs[r.GameID] = ratingAggregate{
			AverageRating: r.AverageRating,
			RatingCount:   r.RatingCount,
		}
	}

	if userID == 0 {
		return data
	}

	// Batch-load favorites
	var favs []db.Favorite
	database.Where("user_id = ? AND game_id IN ?", userID, gameIDs).Find(&favs)
	for _, f := range favs {
		data.favorites[f.GameID] = true
	}

	// Batch-load play later items
	var playLaterItems []db.PlayLaterItem
	database.Where("user_id = ? AND game_id IN ?", userID, gameIDs).Find(&playLaterItems)
	for _, item := range playLaterItems {
		data.playLater[item.GameID] = true
	}

	// Batch-load play history
	var histories []db.PlayHistory
	database.Where("user_id = ? AND game_id IN ?", userID, gameIDs).Find(&histories)
	for i := range histories {
		data.playHistory[histories[i].GameID] = &histories[i]
	}

	// Batch-load user ratings
	var ratings []db.GameRating
	database.Where("user_id = ? AND game_id IN ?", userID, gameIDs).Find(&ratings)
	for _, r := range ratings {
		data.userRatings[r.GameID] = r.Rating
	}

	return data
}

// toGameResponseWithData converts a db.Game using pre-loaded enrichment data.
func toGameResponseWithData(g db.Game, data *userGameData) GameResponse {
	// Build screenshot URLs from the normalized GameScreenshot table.
	// Fall back to the legacy comma-separated ScreenshotURL field for
	// games that haven't been re-scraped yet.
	var screenshots []string
	if len(g.Screenshots) > 0 {
		screenshots = make([]string, len(g.Screenshots))
		for i, ss := range g.Screenshots {
			screenshots[i] = ss.URL
		}
	} else if g.ScreenshotURL != "" {
		screenshots = strings.Split(g.ScreenshotURL, ",")
		for i := range screenshots {
			screenshots[i] = strings.TrimSpace(screenshots[i])
		}
	} else {
		screenshots = []string{}
	}

	consoleName := ""
	coverAspectRatio := 0.75
	if g.Console.ID != 0 {
		consoleName = g.Console.Name
		coverAspectRatio = parseAspectRatio(g.Console.CoverAspect)
	}

	coverURL := resolveImageURL(g.CoverURL)

	for i, s := range screenshots {
		screenshots[i] = resolveImageURL(s)
	}

	var discs []DiscResponse
	for _, d := range g.Discs {
		discs = append(discs, DiscResponse{
			DiscNumber: d.DiscNumber,
			FileName:   d.FileName,
			FileSize:   d.FileSize,
		})
	}

	consoleAbbr := ""
	if g.Console.ID != 0 {
		consoleAbbr = strings.ToLower(g.Console.Abbreviation)
	}

	resp := GameResponse{
		ID:               strconv.FormatUint(uint64(g.ID), 10),
		CreatedAt:        g.CreatedAt,
		UpdatedAt:        g.UpdatedAt,
		ConsoleID:        consoleAbbr,
		ConsoleName:      consoleName,
		CoverAspectRatio: coverAspectRatio,
		Title:            g.Title,
		FileName:       g.FileName,
		FileSize:       g.FileSize,
		DiscCount:      g.DiscCount,
		Discs:          discs,
		Description:    g.Description,
		CoverURL:       coverURL,
		ScreenshotURLs: screenshots,
		Developer:      g.Developer,
		Publisher:       g.Publisher,
		ReleaseDate:    g.ReleaseDate,
		Genre:          g.Genre,
		GameModes:            g.GameModes,
		Storyline:            g.Storyline,
		TotalRating:          g.TotalRating,
		TotalRatingCount:     g.TotalRatingCount,
		IGDBUserRating:       g.IGDBUserRating,
		IGDBUserRatingCount:  g.IGDBUserRatingCount,
		TimeToBeatHastily:    g.TimeToBeatHastily,
		TimeToBeatNormally:   g.TimeToBeatNormally,
		TimeToBeatCompletely: g.TimeToBeatCompletely,
		Players:              g.Players,
		IGDBCriticsRating:    g.IGDBCriticsRating,
		PartyInfo:           g.PartyInfo,
		Playable:            g.Console.Playable,
		CoreOverride:        g.CoreOverride,
		ScraperID:           g.ScraperID,
		ScrapeAttempts:      g.ScrapeAttempts,
		AchievementsWarning: g.AchievementsWarning,
		VerificationStatus:  g.VerificationStatus,
		VerificationTag:     g.VerificationTag,
		Region:              g.Region,
		Revision:            g.Revision,
		Tags:                g.Tags,
		IsPreRelease:        g.IsPreRelease,
		GroupKey:            g.GroupKey,
	}

	// Map release dates
	if len(g.ReleaseDates) > 0 {
		resp.ReleaseDates = make([]ReleaseDateResponse, len(g.ReleaseDates))
		for i, rd := range g.ReleaseDates {
			resp.ReleaseDates[i] = ReleaseDateResponse{
				Region:   rd.Region,
				Date:     rd.Date,
				Platform: rd.Platform,
			}
		}
	}

	// Map videos
	if len(g.Videos) > 0 {
		resp.Videos = make([]VideoResponse, len(g.Videos))
		for i, v := range g.Videos {
			resp.Videos[i] = VideoResponse{VideoID: v.VideoID, Name: v.Name}
		}
	}

	// Map language supports
	if len(g.LanguageSupports) > 0 {
		resp.LanguageSupports = make([]LanguageSupportResponse, len(g.LanguageSupports))
		for i, ls := range g.LanguageSupports {
			resp.LanguageSupports[i] = LanguageSupportResponse{Language: ls.Language, SupportType: ls.SupportType}
		}
	}

	// Map age ratings
	if len(g.AgeRatings) > 0 {
		resp.AgeRatings = make([]AgeRatingResponse, len(g.AgeRatings))
		for i, ar := range g.AgeRatings {
			resp.AgeRatings[i] = AgeRatingResponse{Category: ar.Category, Rating: ar.Rating}
		}
	}

	if data != nil {
		resp.IsFavorite = data.favorites[g.ID]
		resp.IsInPlayLater = data.playLater[g.ID]
		if ph, ok := data.playHistory[g.ID]; ok {
			resp.LastPlayedAt = &ph.LastPlayed
			resp.TotalPlayTime = ph.PlayTime
		}
		if agg, ok := data.ratingAggs[g.ID]; ok {
			resp.AverageRating = agg.AverageRating
			resp.RatingCount = agg.RatingCount
		}
		if rating, ok := data.userRatings[g.ID]; ok {
			resp.UserRating = &rating
		}
		if artwork, ok := data.artworks[g.ID]; ok {
			resp.HeroURL = resolveImageURL(artwork.HeroURL)
			resp.LogoURL = resolveImageURL(artwork.LogoURL)
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

	// Batch-load variant counts for all games in the page.
	// Group by (console_id, group_key) to count how many games share each key.
	variantCounts := make(map[string]int) // "consoleID:groupKey" -> count
	if database != nil && len(games) > 0 {
		// Collect unique group keys (with console_id) for this page
		type groupKeyPair struct {
			ConsoleID uint
			GroupKey  string
		}
		seen := make(map[string]bool)
		var pairs []groupKeyPair
		for _, g := range games {
			if g.GroupKey == "" {
				continue
			}
			key := fmt.Sprintf("%d:%s", g.ConsoleID, g.GroupKey)
			if !seen[key] {
				seen[key] = true
				pairs = append(pairs, groupKeyPair{ConsoleID: g.ConsoleID, GroupKey: g.GroupKey})
			}
		}

		if len(pairs) > 0 {
			type countRow struct {
				ConsoleID uint
				GroupKey  string
				Cnt       int
			}
			var rows []countRow

			// Build OR conditions for each (consoleID, groupKey) pair to avoid
			// the cross-product issue with separate IN clauses.
			var conditions []string
			var args []interface{}
			for _, p := range pairs {
				conditions = append(conditions, "(console_id = ? AND group_key = ?)")
				args = append(args, p.ConsoleID, p.GroupKey)
			}

			database.Model(&db.Game{}).
				Select("console_id, group_key, COUNT(*) as cnt").
				Where(strings.Join(conditions, " OR "), args...).
				Group("console_id, group_key").
				Find(&rows)

			for _, r := range rows {
				key := fmt.Sprintf("%d:%s", r.ConsoleID, r.GroupKey)
				variantCounts[key] = r.Cnt
			}
		}
	}

	result := make([]GameResponse, len(games))
	for i, g := range games {
		result[i] = toGameResponseWithData(g, &data)
		if g.GroupKey != "" {
			key := fmt.Sprintf("%d:%s", g.ConsoleID, g.GroupKey)
			if count, ok := variantCounts[key]; ok && count > 1 {
				result[i].VariantCount = count
			}
		}
	}
	return result
}

// UserResponse is the API response for a user, with string ID for consistency.
type UserResponse struct {
	ID              string      `json:"id"`
	Username        string      `json:"username"`
	Email           string      `json:"email"`
	Role            db.UserRole `json:"role"`
	AvatarURL       string    `json:"avatarUrl,omitempty"`
	Disabled        bool      `json:"disabled"`
	PendingApproval bool      `json:"pendingApproval"`
	CreatedAt       time.Time `json:"createdAt"`
	UpdatedAt       time.Time `json:"updatedAt"`
}

// ToUserResponse converts a db.User to its API response.
func ToUserResponse(u db.User) UserResponse {
	return UserResponse{
		ID:              strconv.FormatUint(uint64(u.ID), 10),
		Username:        u.Username,
		Email:           u.Email,
		Role:            u.Role,
		AvatarURL:       u.AvatarURL,
		Disabled:        u.Disabled,
		PendingApproval: u.PendingApproval,
		CreatedAt:       u.CreatedAt,
		UpdatedAt:       u.UpdatedAt,
	}
}

// DeletedUserResponse is the API response for a soft-deleted user.
type DeletedUserResponse struct {
	ID              string      `json:"id"`
	Username        string      `json:"username"`
	Email           string      `json:"email"`
	Role            db.UserRole `json:"role"`
	Disabled        bool        `json:"disabled"`
	CreatedAt       time.Time `json:"createdAt"`
	DeletedAt       time.Time `json:"deletedAt"`
}

// ToDeletedUserResponse converts a soft-deleted db.User to its API response.
func ToDeletedUserResponse(u db.User) DeletedUserResponse {
	var deletedAt time.Time
	if u.DeletedAt.Valid {
		deletedAt = u.DeletedAt.Time
	}
	return DeletedUserResponse{
		ID:        strconv.FormatUint(uint64(u.ID), 10),
		Username:  u.Username,
		Email:     u.Email,
		Role:      u.Role,
		Disabled:  u.Disabled,
		CreatedAt: u.CreatedAt,
		DeletedAt: deletedAt,
	}
}

// UserSearchResult is the API response for a user search result.
type UserSearchResult struct {
	ID        string `json:"id"`
	Username  string `json:"username"`
	AvatarURL string `json:"avatarUrl,omitempty"`
}

// OnlineUserResponse is the API response for an online user.
type OnlineUserResponse struct {
	ID          string                  `json:"id"`
	Username    string                  `json:"username"`
	AvatarURL   string                  `json:"avatarUrl,omitempty"`
	CurrentGame *OnlineUserGameResponse `json:"currentGame,omitempty"`
}

// OnlineUserGameResponse contains game details for an online user's current game.
type OnlineUserGameResponse struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	CoverURL    string `json:"coverUrl,omitempty"`
	ConsoleName string `json:"consoleName"`
}

// PublicProfileResponse is the API response for a user's public profile.
type PublicProfileResponse struct {
	ID            string                  `json:"id"`
	Username      string                  `json:"username"`
	AvatarURL     string                  `json:"avatarUrl,omitempty"`
	MemberSince   time.Time               `json:"memberSince"`
	IsOnline      bool                    `json:"isOnline"`
	CurrentGame   *OnlineUserGameResponse `json:"currentGame,omitempty"`
	TotalPlayTime int64                   `json:"totalPlayTime"`
	GamesPlayed   int64                   `json:"gamesPlayed"`
	FavoriteGames []PublicProfileGame     `json:"favoriteGames"`
	RecentGames   []PublicProfileGame     `json:"recentGames"`
	TopGames      []PublicProfileGame     `json:"topGames"`
}

// PublicProfileGame represents a game in a public profile response.
type PublicProfileGame struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	CoverURL    string `json:"coverUrl,omitempty"`
	ConsoleName string `json:"consoleName"`
	PlayTime    int64  `json:"playTime,omitempty"`
}

// ActivityEventResponse is the API response for an activity feed event.
type ActivityEventResponse struct {
	ID          string    `json:"id"`
	EventType   string    `json:"eventType"`
	CreatedAt   time.Time `json:"createdAt"`
	UserID      string    `json:"userId"`
	Username    string    `json:"username"`
	AvatarURL   string    `json:"avatarUrl,omitempty"`
	GameID      string    `json:"gameId"`
	GameTitle   string    `json:"gameTitle"`
	GameCoverURL string  `json:"gameCoverUrl,omitempty"`
	ConsoleName string    `json:"consoleName,omitempty"`
	Metadata    map[string]interface{} `json:"metadata,omitempty"`
}

// GameRatingResponse is the API response for a single game rating.
type GameRatingResponse struct {
	ID        string    `json:"id"`
	UserID    string    `json:"userId"`
	Username  string    `json:"username"`
	AvatarURL string    `json:"avatarUrl,omitempty"`
	GameID    string    `json:"gameId"`
	Rating    int       `json:"rating"`
	Review    string    `json:"review,omitempty"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}

// RatingSummaryResponse is the API response for a game's rating summary.
type RatingSummaryResponse struct {
	AverageRating float64        `json:"averageRating"`
	TotalRatings  int64          `json:"totalRatings"`
	Distribution  map[string]int `json:"distribution"` // "1" through "5" -> count
}

// SharedSaveResponse is the API response for a shared save state.
type SharedSaveResponse struct {
	ID            string    `json:"id"`
	UserID        string    `json:"userId"`
	Username      string    `json:"username"`
	AvatarURL     string    `json:"avatarUrl,omitempty"`
	GameID        string    `json:"gameId"`
	Name          string    `json:"name"`
	Description   string    `json:"description,omitempty"`
	FileSize      int64     `json:"fileSize"`
	ScreenshotURL string    `json:"screenshotUrl,omitempty"`
	DownloadCount int       `json:"downloadCount"`
	CreatedAt     time.Time `json:"createdAt"`
}

// CollectionResponse is the API response for a game collection.
type CollectionResponse struct {
	ID          string    `json:"id"`
	UserID      string    `json:"userId"`
	Username    string    `json:"username"`
	AvatarURL   string    `json:"avatarUrl,omitempty"`
	Name        string    `json:"name"`
	Description string    `json:"description,omitempty"`
	IsPublic    bool      `json:"isPublic"`
	CoverURL    string    `json:"coverUrl,omitempty"`
	GameCount   int       `json:"gameCount"`
	CreatedAt   time.Time `json:"createdAt"`
	UpdatedAt   time.Time `json:"updatedAt"`
}

// CollectionDetailResponse is the API response for a collection with its games.
type CollectionDetailResponse struct {
	CollectionResponse
	Games []GameResponse `json:"games"`
}

// SharedSessionResponse is the API response for a shared session (list view).
type SharedSessionResponse struct {
	ID             string     `json:"id"`
	OwnerID        string     `json:"ownerId"`
	OwnerUsername  string     `json:"ownerUsername"`
	GameID         string     `json:"gameId"`
	GameTitle      string     `json:"gameTitle"`
	GameCoverURL   string     `json:"gameCoverUrl,omitempty"`
	ConsoleName    string     `json:"consoleName,omitempty"`
	Name           string     `json:"name"`
	Status         string     `json:"status"`
	ActiveUserID   *string    `json:"activeUserId"`
	ActiveUsername string     `json:"activeUsername,omitempty"`
	TurnTakenAt    *time.Time `json:"turnTakenAt"`
	CoreName       string     `json:"coreName,omitempty"`
	MemberCount    int        `json:"memberCount"`
	SessionID      *string    `json:"sessionId,omitempty"`
	CreatedAt      time.Time  `json:"createdAt"`
	UpdatedAt      time.Time  `json:"updatedAt"`
}

// SharedSessionDetailResponse is the API response for a shared session with full member list.
type SharedSessionDetailResponse struct {
	SharedSessionResponse
	Members []SharedSessionMemberResponse `json:"members"`
}

// SharedSessionMemberResponse is the API response for a shared session member.
type SharedSessionMemberResponse struct {
	ID        string    `json:"id"`
	UserID    string    `json:"userId"`
	Username  string    `json:"username"`
	AvatarURL string    `json:"avatarUrl,omitempty"`
	Role      string    `json:"role"`
	JoinedAt  time.Time `json:"joinedAt"`
}

// SharedSessionInviteResponse is the API response for a shared session invite.
type SharedSessionInviteResponse struct {
	ID                  string    `json:"id"`
	SharedSessionID     string    `json:"sharedSessionId"`
	SharedSessionName   string    `json:"sharedSessionName"`
	GameTitle           string    `json:"gameTitle"`
	InviterID           string    `json:"inviterId"`
	InviterUsername     string    `json:"inviterUsername"`
	InviteeID           string    `json:"inviteeId"`
	InviteeUsername     string    `json:"inviteeUsername"`
	Status              string    `json:"status"`
	CreatedAt           time.Time `json:"createdAt"`
}

// SharedSessionSaveResponse is the API response for a shared session save state.
type SharedSessionSaveResponse struct {
	ID              string    `json:"id"`
	SharedSessionID string    `json:"sharedSessionId"`
	UserID          string    `json:"userId"`
	Username        string    `json:"username"`
	Name            string    `json:"name"`
	FileSize        int64     `json:"fileSize"`
	ScreenshotURL   string    `json:"screenshotUrl,omitempty"`
	IsAuto          bool      `json:"isAuto"`
	CreatedAt       time.Time `json:"createdAt"`
	UpdatedAt       time.Time `json:"updatedAt"`
}

// NetplaySessionResponse is the API response for a netplay session.
type NetplaySessionResponse struct {
	ID               string     `json:"id"`
	HostUserID       string     `json:"hostId"`
	HostUsername     string     `json:"hostUsername"`
	HostAvatarURL    string     `json:"hostAvatarUrl,omitempty"`
	ClientUserID     *string    `json:"clientId"`
	ClientUsername   string     `json:"clientUsername,omitempty"`
	ClientAvatarURL  string     `json:"clientAvatarUrl,omitempty"`
	GameID           string     `json:"gameId"`
	GameTitle        string     `json:"gameTitle"`
	GameCoverURL     string     `json:"gameCoverUrl,omitempty"`
	ConsoleName      string     `json:"consoleName,omitempty"`
	ConsoleID        string     `json:"consoleId,omitempty"`
	CoverAspectRatio float64    `json:"coverAspectRatio"`
	Status           string     `json:"status"`
	EndReason        string     `json:"endReason,omitempty"`
	InputDelay       int        `json:"inputDelay"`
	CoreName         string     `json:"coreName,omitempty"`
	InviteCode       string     `json:"inviteCode"`
	CreatedAt        time.Time  `json:"createdAt"`
	StartedAt        *time.Time `json:"startedAt,omitempty"`
	EndedAt          *time.Time `json:"endedAt,omitempty"`
}

// NetplayInviteResponse is the API response for a netplay session invite.
type NetplayInviteResponse struct {
	ID               string    `json:"id"`
	NetplaySessionID string    `json:"netplaySessionId"`
	InviterID        string    `json:"inviterId"`
	InviterUsername  string    `json:"inviterUsername"`
	InviterAvatarURL string    `json:"inviterAvatarUrl,omitempty"`
	InviteeID        string    `json:"inviteeId"`
	InviteeUsername  string    `json:"inviteeUsername"`
	InviteeAvatarURL string    `json:"inviteeAvatarUrl,omitempty"`
	GameID           string    `json:"gameId"`
	GameTitle        string    `json:"gameTitle"`
	GameCoverURL     string    `json:"gameCoverUrl,omitempty"`
	ConsoleName      string    `json:"consoleName,omitempty"`
	HostUsername     string    `json:"hostUsername"`
	InputDelay       int       `json:"inputDelay"`
	Status           string    `json:"status"`
	CreatedAt        time.Time `json:"createdAt"`
}

// ChallengeResponse is the API response for a game challenge.
type ChallengeResponse struct {
	ID              string     `json:"id"`
	CreatorID       string     `json:"creatorId"`
	CreatorUsername string     `json:"creatorUsername"`
	CreatorAvatar   string     `json:"creatorAvatar,omitempty"`
	GameID          string     `json:"gameId"`
	GameTitle       string     `json:"gameTitle"`
	GameCoverURL    string     `json:"gameCoverUrl,omitempty"`
	ConsoleName     string     `json:"consoleName,omitempty"`
	Name            string     `json:"name"`
	Description     string     `json:"description,omitempty"`
	Type            string     `json:"type"`
	Difficulty      string     `json:"difficulty"`
	Status          string     `json:"status"`
	SaveFileSize    int64      `json:"saveFileSize"`
	ScreenshotURL   string     `json:"screenshotUrl,omitempty"`
	CoreName        string     `json:"coreName,omitempty"`
	AttemptCount    int        `json:"attemptCount"`
	CompletionCount int        `json:"completionCount"`
	ExpiresAt       *time.Time `json:"expiresAt,omitempty"`
	CreatedAt       time.Time  `json:"createdAt"`
	UpdatedAt       time.Time  `json:"updatedAt"`
}

// ChallengeAttemptResponse is the API response for a challenge attempt.
type ChallengeAttemptResponse struct {
	ID          string     `json:"id"`
	ChallengeID string     `json:"challengeId"`
	UserID      string     `json:"userId"`
	Username    string     `json:"username"`
	AvatarURL   string     `json:"avatarUrl,omitempty"`
	Status      string     `json:"status"`
	StartedAt   time.Time  `json:"startedAt"`
	CompletedAt *time.Time `json:"completedAt,omitempty"`
	DurationMs  int64      `json:"durationMs"`
	IsBest      bool       `json:"isBest"`
}

// ChallengeLeaderboardEntry is the API response for a leaderboard entry.
type ChallengeLeaderboardEntry struct {
	Rank       int        `json:"rank"`
	UserID     string     `json:"userId"`
	Username   string     `json:"username"`
	AvatarURL  string     `json:"avatarUrl,omitempty"`
	DurationMs int64      `json:"durationMs"`
	AttemptID  string     `json:"attemptId"`
	CompletedAt time.Time `json:"completedAt"`
}

// RateLimitResponse is the API response for a user's rate limit status.
type RateLimitResponse struct {
	FailedCount int        `json:"failedCount"`
	LockedUntil *time.Time `json:"lockedUntil"`
	IsLockedOut bool       `json:"isLockedOut"`
}

// GameSessionResponse is the API response for a game session.
type GameSessionResponse struct {
	ID                   string     `json:"id"`
	OwnerID              string     `json:"ownerId"`
	OwnerUsername        string     `json:"ownerUsername"`
	GameID               string     `json:"gameId"`
	Name                 string     `json:"name"`
	LastPlayedAt         *time.Time `json:"lastPlayedAt,omitempty"`
	LastPlayedBy         *string    `json:"lastPlayedBy,omitempty"`
	LastPlayedByUsername *string    `json:"lastPlayedByUsername,omitempty"`
	TotalPlayTime        int64      `json:"totalPlayTime"`
	ScreenshotURL        string     `json:"screenshotUrl,omitempty"`
	CoreName             string     `json:"coreName,omitempty"`
	CheatsEnabled        bool       `json:"cheatsEnabled"`
	SaveCount            int        `json:"saveCount"`
	IsSharedSession      bool       `json:"isSharedSession"`
	SharedSessionID      *string    `json:"sharedSessionId,omitempty"`
	MemberCount          int        `json:"memberCount"`
	MemberUsernames      []string   `json:"memberUsernames"`
	MemberAvatars        []string   `json:"memberAvatars"`
	CreatedAt            time.Time  `json:"createdAt"`
	UpdatedAt            time.Time  `json:"updatedAt"`
}

// SessionSaveResponse is the API response for a session save state.
type SessionSaveResponse struct {
	ID            string    `json:"id"`
	SessionID     string    `json:"sessionId"`
	UserID        string    `json:"userId"`
	Username      string    `json:"username"`
	Name          string    `json:"name"`
	FileSize      int64     `json:"fileSize"`
	ScreenshotURL string    `json:"screenshotUrl,omitempty"`
	IsAuto        bool      `json:"isAuto"`
	IsCurrent     bool      `json:"isCurrent"`
	CoreName      string    `json:"coreName,omitempty"`
	CoreMatch     *bool     `json:"coreMatch,omitempty"`
	CurrentCore   string    `json:"currentCore,omitempty"`
	Notes         string    `json:"notes,omitempty"`
	Slot          *int      `json:"slot,omitempty"`
	CreatedAt     time.Time `json:"createdAt"`
	UpdatedAt     time.Time `json:"updatedAt"`
}

// resolveImageURL prefixes relative image paths with /api/images/.
// External URLs (starting with http) are returned unchanged.
func resolveImageURL(path string) string {
	if path == "" || strings.HasPrefix(path, "http") {
		return path
	}
	return "/api/images/" + path
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

// --- Auth ---

// AuthLoginResponse is the response payload for login/register/refresh endpoints.
type AuthLoginResponse struct {
	AccessToken  string       `json:"accessToken"`
	RefreshToken string       `json:"refreshToken"`
	User         UserResponse `json:"user"`
}

// --- User ---

// UserPreferencesResponse is the JSON shape for the user preferences endpoints.
type UserPreferencesResponse struct {
	ShowPerformanceOverlay  bool                            `json:"showPerformanceOverlay"`
	AutoSaveEnabled         bool                            `json:"autoSaveEnabled"`
	AutoLoadSaveEnabled     bool                            `json:"autoLoadSaveEnabled"`
	SelectedShader          string                          `json:"selectedShader"`
	SelectedTheme           string                          `json:"selectedTheme"`
	DefaultSecondScreenPage string                          `json:"defaultSecondScreenPage"`
	ConsoleShaders          map[string]string               `json:"consoleShaders"`
	SelectedKeyMapping      string                          `json:"selectedKeyMapping"`
	CustomKeyMapping        map[string]string               `json:"customKeyMapping"`
	ConsoleKeyMappings      map[string]ConsoleKeyMappingDTO `json:"consoleKeyMappings"`
	PreferredRegions        []string                        `json:"preferredRegions"`
	RALinked                bool                            `json:"raLinked"`
	RAUsername              string                          `json:"raUsername"`
	RAHardcoreEnabled       bool                            `json:"raHardcoreEnabled"`
}

// ConsoleKeyMappingDTO is a per-console key mapping entry in the preferences payload.
type ConsoleKeyMappingDTO struct {
	SelectedMapping string            `json:"selectedMapping"`
	CustomMapping   map[string]string `json:"customMapping,omitempty"`
}

// UserStatsResponse is the response for GET /api/user/stats.
type UserStatsResponse struct {
	TotalPlayTime      int64         `json:"totalPlayTime"`
	GamesPlayed        int64         `json:"gamesPlayed"`
	CurrentStreak      int           `json:"currentStreak"`
	LongestStreak      int           `json:"longestStreak"`
	MostPlayedGame     *GameResponse `json:"mostPlayedGame"`
	MostPlayedGameTime int64         `json:"mostPlayedGameTime"`
	LastPlayedAt       *time.Time    `json:"lastPlayedAt"`
}

// PlayStatsEntry is a single entry in the play stats (per-game history) response.
type PlayStatsEntry struct {
	GameID       uint   `json:"gameId"`
	PlayTime     int64  `json:"playTime"`
	LastPlayedAt string `json:"lastPlayedAt"`
}

// --- Device ---

// DeviceResponse is the API response for a registered device. It embeds the
// db.Device model (for historical compatibility) and augments it with the
// device's per-console shader preferences.
type DeviceResponse struct {
	db.Device
	ConsoleShaders map[string]string `json:"consoleShaders"`
}

// --- Achievement Showcase ---

// ShowcaseEntryResponse is the enriched response for a single showcased achievement.
type ShowcaseEntryResponse struct {
	AchievementRAID uint    `json:"achievementRaId"`
	RAGameID        uint    `json:"raGameId"`
	ShowcaseOrder   int     `json:"showcaseOrder"`
	Title           string  `json:"title,omitempty"`
	Description     string  `json:"description,omitempty"`
	Points          int     `json:"points,omitempty"`
	BadgeURL        string  `json:"badgeUrl,omitempty"`
	RarityPercent   float64 `json:"rarityPercent,omitempty"`
	GameTitle       string  `json:"gameTitle,omitempty"`
}

// --- RetroAchievements ---

// RAProgressEntry is a single achievement progress entry returned by GetGameProgress.
type RAProgressEntry struct {
	AchievementID    uint   `json:"achievementId"`
	UnlockedAt       string `json:"unlockedAt"`
	IsHardcore       bool   `json:"isHardcore"`
	PlayTimeAtUnlock int64  `json:"playTimeAtUnlock"`
}

// RARecentAchievementResponse is an enriched recent achievement unlock across games.
type RARecentAchievementResponse struct {
	AchievementRAID  uint      `json:"achievementRaId"`
	Title            string    `json:"title"`
	Description      string    `json:"description"`
	Points           int       `json:"points"`
	BadgeURL         string    `json:"badgeUrl"`
	UnlockedAt       time.Time `json:"unlockedAt"`
	IsHardcore       bool      `json:"isHardcore"`
	PlayTimeAtUnlock int64     `json:"playTimeAtUnlock"`
	GameID           string    `json:"gameId"`
	GameTitle        string    `json:"gameTitle"`
	ConsoleName      string    `json:"consoleName"`
	CoverURL         string    `json:"coverUrl"`
}

// RAUnlockedAchievementResponse is a single unlocked achievement in the showcase picker.
type RAUnlockedAchievementResponse struct {
	AchievementRAID uint    `json:"achievementRaId"`
	RAGameID        uint    `json:"raGameId"`
	Title           string  `json:"title"`
	Description     string  `json:"description"`
	Points          int     `json:"points"`
	BadgeURL        string  `json:"badgeUrl"`
	RarityPercent   float64 `json:"rarityPercent"`
	GameTitle       string  `json:"gameTitle"`
	ConsoleName     string  `json:"consoleName"`
}

// RATimelineEntryResponse is a single entry in a per-game achievement timeline.
type RATimelineEntryResponse struct {
	AchievementRAID  uint      `json:"achievementRaId"`
	Title            string    `json:"title"`
	Description      string    `json:"description"`
	Points           int       `json:"points"`
	BadgeURL         string    `json:"badgeUrl"`
	UnlockedAt       time.Time `json:"unlockedAt"`
	IsHardcore       bool      `json:"isHardcore"`
	PlayTimeAtUnlock int64     `json:"playTimeAtUnlock"`
}

// RALeaderboardEntryResponse is a single entry in an achievement leaderboard.
type RALeaderboardEntryResponse struct {
	UserID          string    `json:"userId"`
	Username        string    `json:"username"`
	AvatarURL       string    `json:"avatarUrl"`
	UnlockedCount   int       `json:"unlockedCount"`
	EarnedPoints    int       `json:"earnedPoints"`
	LastUnlockedAt  time.Time `json:"lastUnlockedAt"`
	FirstUnlockedAt time.Time `json:"firstUnlockedAt"`
	IsComplete      bool      `json:"isComplete"`
}

// --- Game ---

// GameCheatResponse is a single cheat entry in the game cheats response.
type GameCheatResponse struct {
	ID          uint   `json:"id"`
	Index       int    `json:"index"`
	Description string `json:"description"`
	Code        string `json:"code"`
}

// GameScanSummary is a lightweight game entry returned in scan results.
type GameScanSummary struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	ConsoleName string `json:"consoleName"`
}

// GameScanResultResponse is the scan result response with new-game summaries and auto-scrape status.
type GameScanResultResponse struct {
	NewGames     int               `json:"newGames"`
	UpdatedGames int               `json:"updatedGames"`
	RemovedGames int               `json:"removedGames"`
	TotalGames   int               `json:"totalGames"`
	NewGamesList []GameScanSummary `json:"newGamesList,omitempty"`
	AutoScraping bool              `json:"autoScraping"`
}

// GameStatsTopPlayer is a player entry in the game stats response.
type GameStatsTopPlayer struct {
	UserID    string `json:"userId"`
	Username  string `json:"username"`
	AvatarURL string `json:"avatarUrl"`
	PlayTime  int64  `json:"playTime"`
}

// GameStatsResponse is the response for GET /api/games/:id/stats.
type GameStatsResponse struct {
	TotalPlayers    int64                `json:"totalPlayers"`
	TotalPlayTime   int64                `json:"totalPlayTime"`
	AveragePlayTime int64                `json:"averagePlayTime"`
	TopPlayers      []GameStatsTopPlayer `json:"topPlayers"`
}

// GameKeyMappingResponse is the JSON shape returned by the per-game key mapping endpoints.
type GameKeyMappingResponse struct {
	GameID        uint              `json:"gameId"`
	CustomMapping map[string]string `json:"customMapping"`
}

// --- Session ---

// SessionStorageConsoleBreakdown is per-console storage usage in the session storage response.
type SessionStorageConsoleBreakdown struct {
	ConsoleID   string `json:"consoleId"`
	ConsoleName string `json:"consoleName"`
	Bytes       int64  `json:"bytes"`
	SaveCount   int64  `json:"saveCount"`
}

// --- System Events ---

// SystemEventCategoryResponse is a single entry in the system event categories response.
type SystemEventCategoryResponse struct {
	Code string `json:"code"`
	Name string `json:"name"`
}

// --- Stats ---

// MostPlayedEntry is a single entry in the most-played games response.
type MostPlayedEntry struct {
	Game          GameResponse `json:"game"`
	TotalPlayers  int64        `json:"totalPlayers"`
	TotalPlayTime int64        `json:"totalPlayTime"`
}

// MostPlayedResponse is the response for GET /api/stats/most-played.
type MostPlayedResponse struct {
	Games []MostPlayedEntry `json:"games"`
}

// ActivePlayerEntry is a single entry in the most-active-players response.
type ActivePlayerEntry struct {
	UserID        string    `json:"userId"`
	Username      string    `json:"username"`
	AvatarURL     string    `json:"avatarUrl"`
	TotalPlayTime int64     `json:"totalPlayTime"`
	GamesPlayed   int64     `json:"gamesPlayed"`
	LastPlayed    time.Time `json:"lastPlayed"`
}

// MostActivePlayersResponse is the response for GET /api/stats/most-active-players.
type MostActivePlayersResponse struct {
	Players []ActivePlayerEntry `json:"players"`
}

// HeatmapEntry is a single day's play activity for the heatmap response.
type HeatmapEntry struct {
	Date     string `json:"date"`
	PlayTime int64  `json:"playTime"`
}

// --- Artwork ---

// GameArtworkResponse is the API response for a game's artwork URLs.
type GameArtworkResponse struct {
	HeroURL string `json:"heroUrl,omitempty"`
	GridURL string `json:"gridUrl,omitempty"`
	LogoURL string `json:"logoUrl,omitempty"`
	IconURL string `json:"iconUrl,omitempty"`
}

// --- BIOS ---

// BiosFileResponse represents an individual BIOS file in the response.
type BiosFileResponse struct {
	Name        string  `json:"name"`
	Size        int64   `json:"size"`
	MD5         string  `json:"md5"`
	ExpectedMD5 string  `json:"expectedMd5,omitempty"`
	SubDir      string  `json:"subDir,omitempty"`
	ConsoleID   *string `json:"consoleId"`
	ConsoleName *string `json:"consoleName,omitempty"`
	Description *string `json:"description,omitempty"`
	Required    bool    `json:"required"`
	Status      string  `json:"status"` // "valid", "present", "invalid", "missing"
}

// ConsoleFileStatus represents a single BIOS file within a console summary.
type ConsoleFileStatus struct {
	FileName    string `json:"fileName"`
	Description string `json:"description"`
	Required    bool   `json:"required"`
	MD5         string `json:"md5"`
	Status      string `json:"status"`           // "valid", "present", "invalid", "missing"
	SubDir      string `json:"subDir,omitempty"` // subdirectory within system_dir
}

// ConsoleBiosStatus represents the BIOS status summary for one console.
type ConsoleBiosStatus struct {
	ConsoleID       string              `json:"consoleId"`
	ConsoleName     string              `json:"consoleName"`
	BiosRequired    bool                `json:"biosRequired"`
	Status          string              `json:"status"` // "ready", "missing", "invalid", "not_required"
	RequiredPresent int                 `json:"requiredPresent"`
	RequiredTotal   int                 `json:"requiredTotal"`
	OptionalPresent int                 `json:"optionalPresent"`
	OptionalTotal   int                 `json:"optionalTotal"`
	Files           []ConsoleFileStatus `json:"files"`
}

// BiosListResponse is the top-level response for GET /api/bios.
type BiosListResponse struct {
	Files    []BiosFileResponse  `json:"files"`
	Consoles []ConsoleBiosStatus `json:"consoles"`
}

// --- Admin / Backfill ---

// BackfillImagesResponse is the result of a backfill-images operation.
type BackfillImagesResponse struct {
	ArtworkDownloaded  int `json:"artworkDownloaded"`
	TopRatedDownloaded int `json:"topRatedDownloaded"`
	SimilarDownloaded  int `json:"similarDownloaded"`
	GalleryDownloaded  int `json:"galleryDownloaded"`
	CompanyDownloaded  int `json:"companyDownloaded"`
	Errors             int `json:"errors"`
}

// --- Scraper ---

// ScraperSourceResultResponse is a single source's scrape result counts.
type ScraperSourceResultResponse struct {
	Source           string `json:"source"`
	Matched          int64  `json:"matched"`
	NotFound         int64  `json:"notFound"`
	NotFoundEligible int64  `json:"notFoundEligible"`
	Error            int64  `json:"error"`
	ErrorEligible    int64  `json:"errorEligible"`
	NotAttempted     int64  `json:"notAttempted"`
}
