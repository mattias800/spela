package api

import (
	"log/slog"
	"math"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// GetDevelopers returns a list of developers with game counts, average ratings, and console lists.
// GET /api/explore/developers
func (h *ExploreHandler) GetDevelopers(c *gin.Context) {
	type devRow struct {
		Developer string
		GameCount int
		AvgRating float64
	}

	var rows []devRow
	if err := h.DB.
		Table("games").
		Select("developer, COUNT(*) as game_count, AVG(CASE WHEN rating > 0 THEN rating ELSE NULL END) as avg_rating").
		Where("games.deleted_at IS NULL AND developer != ''").
		Group("developer").
		Order("game_count DESC").
		Limit(50).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch developers", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch developers"})
		return
	}

	if len(rows) == 0 {
		c.Header("Cache-Control", "private, max-age=300")
		c.JSON(http.StatusOK, DeveloperListResponse{Developers: []DeveloperSummary{}})
		return
	}

	// Collect developer names for batch console lookup
	devNames := make([]string, len(rows))
	for i, r := range rows {
		devNames[i] = r.Developer
	}

	// Batch-load distinct consoles per developer
	type devConsoleRow struct {
		Developer    string
		Abbreviation string
	}
	var consoleRows []devConsoleRow
	if err := h.DB.
		Table("games").
		Select("DISTINCT games.developer, consoles.abbreviation").
		Joins("JOIN consoles ON consoles.id = games.console_id").
		Where("games.deleted_at IS NULL AND games.developer IN ?", devNames).
		Scan(&consoleRows).Error; err != nil {
		slog.Error("failed to fetch developer consoles", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch developers"})
		return
	}

	devConsoles := make(map[string][]string)
	for _, cr := range consoleRows {
		abbr := strings.ToLower(cr.Abbreviation)
		devConsoles[cr.Developer] = append(devConsoles[cr.Developer], abbr)
	}

	developers := make([]DeveloperSummary, len(rows))
	for i, r := range rows {
		avgRating := 0.0
		if r.AvgRating > 0 {
			avgRating = math.Round(r.AvgRating*10) / 10
		}
		consoles := devConsoles[r.Developer]
		if consoles == nil {
			consoles = []string{}
		}
		developers[i] = DeveloperSummary{
			Name:      r.Developer,
			GameCount: r.GameCount,
			AvgRating: avgRating,
			Consoles:  consoles,
		}
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, DeveloperListResponse{Developers: developers})
}

// GetDeveloperDetail returns all games by a specific developer with user context.
// GET /api/explore/developers/:name
func (h *ExploreHandler) GetDeveloperDetail(c *gin.Context) {
	userID := getUserID(c)
	name := c.Param("name")

	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("LOWER(games.developer) = LOWER(?) AND games.deleted_at IS NULL", name).
		Order("games.rating DESC, games.title ASC").
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch developer games", "developer", name, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch developer games"})
		return
	}

	// Calculate stats
	avgRating, consoles := calcRatingAndConsoles(games)

	// Use canonical casing from the first game's developer field
	canonicalName := name
	if len(games) > 0 && games[0].Developer != "" {
		canonicalName = games[0].Developer
	}

	gameResponses := ToGameResponses(games, h.DB, userID)

	// Hero URL from highest-rated game with hero artwork
	heroURL := h.findHeroURL(games)

	// Top 8 highest-rated games (only those with rating > 0)
	topGames := buildTopGames(gameResponses, 8)

	// Genre breakdown
	genreBreakdown := buildGenreBreakdownFromGames(games)

	// Platform breakdown
	platformBreakdown := buildPlatformBreakdown(games)

	// Publishers breakdown
	publishers := buildNameCountBreakdown(games, func(g db.Game) string { return g.Publisher })

	// User stats
	var userStats *EntityUserStats
	if userID > 0 {
		userStats = h.buildEntityUserStats(userID, games, gameResponses)
	}

	// Look up company metadata (lazy-fetched from IGDB)
	companyInfo := h.lookupCompanyInfo(canonicalName)

	// Statistics fields
	activeYears := buildActiveYears(games)
	ratingDist := buildRatingDistribution(games)
	primaryGenre := buildPrimaryGenre(games)
	timeline := buildTimeline(games)

	// Related developers (other developers sharing publishers with this one)
	relatedDevelopers := buildRelatedDevelopers(h.DB, canonicalName, publishers)

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, DeveloperDetailResponse{
		Name:               canonicalName,
		GameCount:          len(games),
		AvgRating:          avgRating,
		Consoles:           consoles,
		Games:              gameResponses,
		HeroURL:            heroURL,
		TopGames:           topGames,
		GenreBreakdown:     genreBreakdown,
		PlatformBreakdown:  platformBreakdown,
		UserStats:          userStats,
		Publishers:         publishers,
		CompanyInfo:        companyInfo,
		ActiveYears:        activeYears,
		RatingDistribution: ratingDist,
		PrimaryGenre:       primaryGenre,
		Timeline:           timeline,
		RelatedDevelopers:  relatedDevelopers,
	})
}

// GetPublisherDetail returns all games by a specific publisher with user context.
// GET /api/explore/publishers/:name
func (h *ExploreHandler) GetPublisherDetail(c *gin.Context) {
	userID := getUserID(c)
	name := c.Param("name")

	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("LOWER(games.publisher) = LOWER(?) AND games.deleted_at IS NULL", name).
		Order("games.rating DESC, games.title ASC").
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch publisher games", "publisher", name, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch publisher games"})
		return
	}

	// Calculate stats
	avgRating, consoles := calcRatingAndConsoles(games)

	// Use canonical casing from the first game's publisher field
	canonicalName := name
	if len(games) > 0 && games[0].Publisher != "" {
		canonicalName = games[0].Publisher
	}

	gameResponses := ToGameResponses(games, h.DB, userID)

	// Hero URL from highest-rated game with hero artwork
	heroURL := h.findHeroURL(games)

	// Top 8 highest-rated games (only those with rating > 0)
	topGames := buildTopGames(gameResponses, 8)

	// Genre breakdown
	genreBreakdown := buildGenreBreakdownFromGames(games)

	// Platform breakdown
	platformBreakdown := buildPlatformBreakdown(games)

	// Developers breakdown
	developers := buildNameCountBreakdown(games, func(g db.Game) string { return g.Developer })

	// User stats
	var userStats *EntityUserStats
	if userID > 0 {
		userStats = h.buildEntityUserStats(userID, games, gameResponses)
	}

	// Look up company metadata (lazy-fetched from IGDB)
	companyInfo := h.lookupCompanyInfo(canonicalName)

	// Statistics fields
	activeYears := buildActiveYears(games)
	ratingDist := buildRatingDistribution(games)
	primaryGenre := buildPrimaryGenre(games)
	timeline := buildTimeline(games)

	// Related publishers (other publishers sharing developers with this one)
	relatedPublishers := buildRelatedPublishers(h.DB, canonicalName, developers)

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, PublisherDetailResponse{
		Name:               canonicalName,
		GameCount:          len(games),
		AvgRating:          avgRating,
		Consoles:           consoles,
		Games:              gameResponses,
		HeroURL:            heroURL,
		TopGames:           topGames,
		GenreBreakdown:     genreBreakdown,
		PlatformBreakdown:  platformBreakdown,
		UserStats:          userStats,
		Developers:         developers,
		CompanyInfo:        companyInfo,
		ActiveYears:        activeYears,
		RatingDistribution: ratingDist,
		PrimaryGenre:       primaryGenre,
		Timeline:           timeline,
		RelatedPublishers:  relatedPublishers,
	})
}

// buildRelatedDevelopers finds other developers that share publishers with the given developer.
// It returns up to 5 related developers, ranked by the number of shared publishers (descending).
// Returns nil if no related developers are found.
func buildRelatedDevelopers(database *gorm.DB, developerName string, publishers []NameCount) []RelatedDeveloper {
	if len(publishers) == 0 {
		return nil
	}

	publisherNames := make([]string, len(publishers))
	for i, p := range publishers {
		publisherNames[i] = p.Name
	}

	// Find other developers whose games have the same publishers
	type devPubRow struct {
		Developer string
		Publisher string
		GameCount int
	}
	var rows []devPubRow
	if err := database.
		Table("games").
		Select("developer, publisher, COUNT(*) as game_count").
		Where("deleted_at IS NULL AND developer != '' AND publisher IN ? AND LOWER(developer) != LOWER(?)", publisherNames, developerName).
		Group("developer, publisher").
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch related developers", "error", err)
		return nil
	}

	if len(rows) == 0 {
		return nil
	}

	// Aggregate: for each developer, collect shared publishers and total game count
	type devInfo struct {
		totalGames       int
		sharedPublishers map[string]bool
	}
	devMap := make(map[string]*devInfo)
	for _, r := range rows {
		di, ok := devMap[r.Developer]
		if !ok {
			di = &devInfo{sharedPublishers: make(map[string]bool)}
			devMap[r.Developer] = di
		}
		di.sharedPublishers[r.Publisher] = true
	}

	// Get total game count per developer (across all their games, not just shared-publisher ones)
	relatedDevNames := make([]string, 0, len(devMap))
	for name := range devMap {
		relatedDevNames = append(relatedDevNames, name)
	}

	type devCountRow struct {
		Developer string
		GameCount int
	}
	var countRows []devCountRow
	if err := database.
		Table("games").
		Select("developer, COUNT(*) as game_count").
		Where("deleted_at IS NULL AND developer IN ?", relatedDevNames).
		Group("developer").
		Scan(&countRows).Error; err != nil {
		slog.Error("failed to fetch related developer game counts", "error", err)
		return nil
	}

	for _, cr := range countRows {
		if di, ok := devMap[cr.Developer]; ok {
			di.totalGames = cr.GameCount
		}
	}

	// Build result
	result := make([]RelatedDeveloper, 0, len(devMap))
	for name, di := range devMap {
		pubs := make([]string, 0, len(di.sharedPublishers))
		for p := range di.sharedPublishers {
			pubs = append(pubs, p)
		}
		sort.Strings(pubs)
		result = append(result, RelatedDeveloper{
			Name:             name,
			GameCount:        di.totalGames,
			SharedPublishers: pubs,
		})
	}

	// Sort by number of shared publishers DESC, then by game count DESC, then by name ASC
	sort.Slice(result, func(i, j int) bool {
		if len(result[i].SharedPublishers) != len(result[j].SharedPublishers) {
			return len(result[i].SharedPublishers) > len(result[j].SharedPublishers)
		}
		if result[i].GameCount != result[j].GameCount {
			return result[i].GameCount > result[j].GameCount
		}
		return result[i].Name < result[j].Name
	})

	// Limit to top 5
	if len(result) > 5 {
		result = result[:5]
	}

	return result
}

// buildRelatedPublishers finds other publishers that share developers with the given publisher.
// It returns up to 5 related publishers, ranked by the number of shared developers (descending).
// Returns nil if no related publishers are found.
func buildRelatedPublishers(database *gorm.DB, publisherName string, developers []NameCount) []RelatedPublisher {
	if len(developers) == 0 {
		return nil
	}

	developerNames := make([]string, len(developers))
	for i, d := range developers {
		developerNames[i] = d.Name
	}

	// Find other publishers whose games have the same developers
	type pubDevRow struct {
		Publisher string
		Developer string
		GameCount int
	}
	var rows []pubDevRow
	if err := database.
		Table("games").
		Select("publisher, developer, COUNT(*) as game_count").
		Where("deleted_at IS NULL AND publisher != '' AND developer IN ? AND LOWER(publisher) != LOWER(?)", developerNames, publisherName).
		Group("publisher, developer").
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch related publishers", "error", err)
		return nil
	}

	if len(rows) == 0 {
		return nil
	}

	// Aggregate: for each publisher, collect shared developers and total game count
	type pubInfo struct {
		totalGames       int
		sharedDevelopers map[string]bool
	}
	pubMap := make(map[string]*pubInfo)
	for _, r := range rows {
		pi, ok := pubMap[r.Publisher]
		if !ok {
			pi = &pubInfo{sharedDevelopers: make(map[string]bool)}
			pubMap[r.Publisher] = pi
		}
		pi.sharedDevelopers[r.Developer] = true
	}

	// Get total game count per publisher (across all their games, not just shared-developer ones)
	relatedPubNames := make([]string, 0, len(pubMap))
	for name := range pubMap {
		relatedPubNames = append(relatedPubNames, name)
	}

	type pubCountRow struct {
		Publisher string
		GameCount int
	}
	var countRows []pubCountRow
	if err := database.
		Table("games").
		Select("publisher, COUNT(*) as game_count").
		Where("deleted_at IS NULL AND publisher IN ?", relatedPubNames).
		Group("publisher").
		Scan(&countRows).Error; err != nil {
		slog.Error("failed to fetch related publisher game counts", "error", err)
		return nil
	}

	for _, cr := range countRows {
		if pi, ok := pubMap[cr.Publisher]; ok {
			pi.totalGames = cr.GameCount
		}
	}

	// Build result
	result := make([]RelatedPublisher, 0, len(pubMap))
	for name, pi := range pubMap {
		devs := make([]string, 0, len(pi.sharedDevelopers))
		for d := range pi.sharedDevelopers {
			devs = append(devs, d)
		}
		sort.Strings(devs)
		result = append(result, RelatedPublisher{
			Name:             name,
			GameCount:        pi.totalGames,
			SharedDevelopers: devs,
		})
	}

	// Sort by number of shared developers DESC, then by game count DESC, then by name ASC
	sort.Slice(result, func(i, j int) bool {
		if len(result[i].SharedDevelopers) != len(result[j].SharedDevelopers) {
			return len(result[i].SharedDevelopers) > len(result[j].SharedDevelopers)
		}
		if result[i].GameCount != result[j].GameCount {
			return result[i].GameCount > result[j].GameCount
		}
		return result[i].Name < result[j].Name
	})

	// Limit to top 5
	if len(result) > 5 {
		result = result[:5]
	}

	return result
}

// findHeroURL returns the hero artwork URL from the highest-rated game that has hero artwork.
// Games are expected to be sorted by rating DESC already.
func (h *ExploreHandler) findHeroURL(games []db.Game) string {
	if len(games) == 0 {
		return ""
	}
	gameIDs := make([]uint, len(games))
	for i, g := range games {
		gameIDs[i] = g.ID
	}
	var artwork db.GameArtwork
	if err := h.DB.
		Joins("JOIN games ON games.id = game_artworks.game_id").
		Where("game_artworks.game_id IN ? AND game_artworks.hero_url != ''", gameIDs).
		Order("games.rating DESC").
		First(&artwork).Error; err == nil {
		return artwork.HeroURL
	}
	return ""
}

// buildTopGames returns up to limit highest-rated games (only those with rating > 0).
// gameResponses is expected to be sorted by rating DESC already.
func buildTopGames(gameResponses []GameResponse, limit int) []GameResponse {
	var top []GameResponse
	for _, gr := range gameResponses {
		if gr.Rating > 0 {
			top = append(top, gr)
			if len(top) >= limit {
				break
			}
		}
	}
	if top == nil {
		top = []GameResponse{}
	}
	return top
}

// buildGenreBreakdownFromGames computes genre distribution from a slice of games.
// Handles comma-separated genre values by splitting and trimming each part.
func buildGenreBreakdownFromGames(games []db.Game) []GenreCount {
	genreCounts := make(map[string]int)
	for _, g := range games {
		if g.Genre == "" {
			continue
		}
		// Handle comma-separated genres
		parts := strings.Split(g.Genre, ",")
		for _, part := range parts {
			genre := strings.TrimSpace(part)
			if genre != "" {
				genreCounts[genre]++
			}
		}
	}

	result := make([]GenreCount, 0, len(genreCounts))
	for name, count := range genreCounts {
		result = append(result, GenreCount{Name: name, GameCount: count})
	}
	sort.Slice(result, func(i, j int) bool {
		if result[i].GameCount != result[j].GameCount {
			return result[i].GameCount > result[j].GameCount
		}
		return result[i].Name < result[j].Name
	})
	return result
}

// buildPlatformBreakdown computes games-per-console from a slice of games.
func buildPlatformBreakdown(games []db.Game) []PlatformCount {
	type platformInfo struct {
		name  string
		abbr  string
		count int
	}
	platformMap := make(map[uint]*platformInfo)
	for _, g := range games {
		if g.Console.ID == 0 {
			continue
		}
		if pi, ok := platformMap[g.Console.ID]; ok {
			pi.count++
		} else {
			platformMap[g.Console.ID] = &platformInfo{
				name:  g.Console.Name,
				abbr:  strings.ToLower(g.Console.Abbreviation),
				count: 1,
			}
		}
	}

	result := make([]PlatformCount, 0, len(platformMap))
	for _, pi := range platformMap {
		result = append(result, PlatformCount{
			ConsoleName: pi.name,
			ConsoleID:   pi.abbr,
			Count:       pi.count,
		})
	}
	sort.Slice(result, func(i, j int) bool {
		if result[i].Count != result[j].Count {
			return result[i].Count > result[j].Count
		}
		return result[i].ConsoleName < result[j].ConsoleName
	})
	return result
}

// buildNameCountBreakdown computes a name-count breakdown using a field extractor function.
func buildNameCountBreakdown(games []db.Game, extractField func(db.Game) string) []NameCount {
	counts := make(map[string]int)
	for _, g := range games {
		name := extractField(g)
		if name != "" {
			counts[name]++
		}
	}

	result := make([]NameCount, 0, len(counts))
	for name, count := range counts {
		result = append(result, NameCount{Name: name, Count: count})
	}
	sort.Slice(result, func(i, j int) bool {
		if result[i].Count != result[j].Count {
			return result[i].Count > result[j].Count
		}
		return result[i].Name < result[j].Name
	})
	return result
}

// buildEntityUserStats computes user-specific stats for a set of games.
// Returns nil if the user has no play history for any of the games.
func (h *ExploreHandler) buildEntityUserStats(userID uint, games []db.Game, gameResponses []GameResponse) *EntityUserStats {
	if len(games) == 0 {
		return nil
	}

	gameIDs := make([]uint, len(games))
	for i, g := range games {
		gameIDs[i] = g.ID
	}

	// Get play history for this user across these games
	var playHistories []db.PlayHistory
	if err := h.DB.
		Where("user_id = ? AND game_id IN ? AND deleted_at IS NULL", userID, gameIDs).
		Find(&playHistories).Error; err != nil {
		slog.Error("failed to fetch play history for entity stats", "error", err)
		return nil
	}

	if len(playHistories) == 0 {
		return nil
	}

	// Compute total play time, games played, most played
	var totalPlayTime int64
	var mostPlayedGameID uint
	var mostPlayedTime int64
	playedGameIDs := make(map[uint]bool)

	for _, ph := range playHistories {
		totalPlayTime += ph.PlayTime
		playedGameIDs[ph.GameID] = true
		if ph.PlayTime > mostPlayedTime {
			mostPlayedTime = ph.PlayTime
			mostPlayedGameID = ph.GameID
		}
	}

	// Count favorites
	var favoriteCount int64
	if err := h.DB.Model(&db.Favorite{}).
		Where("user_id = ? AND game_id IN ? AND deleted_at IS NULL", userID, gameIDs).
		Count(&favoriteCount).Error; err != nil {
		slog.Error("failed to count favorites for entity stats", "error", err)
	}

	// Find the most-played game response
	var mostPlayedGame *GameResponse
	if mostPlayedGameID > 0 {
		mostPlayedIDStr := strconv.FormatUint(uint64(mostPlayedGameID), 10)
		for i := range gameResponses {
			if gameResponses[i].ID == mostPlayedIDStr {
				mostPlayedGame = &gameResponses[i]
				break
			}
		}
	}

	return &EntityUserStats{
		TotalPlayTime:  totalPlayTime,
		GamesPlayed:    len(playedGameIDs),
		FavoriteCount:  int(favoriteCount),
		MostPlayedGame: mostPlayedGame,
	}
}

// GetDeveloperSpotlight returns a featured developer with top games and hero art.
// The spotlight rotates weekly using a deterministic selection from the top 5 developers.
// GET /api/explore/developers/spotlight
func (h *ExploreHandler) GetDeveloperSpotlight(c *gin.Context) {
	userID := getUserID(c)

	// Find the top 5 developers by game count (only those with games that have hero art)
	type devRow struct {
		Developer string
		GameCount int
	}
	var rows []devRow
	if err := h.DB.
		Table("games").
		Select("games.developer, COUNT(DISTINCT games.id) as game_count").
		Joins("JOIN game_artworks ON game_artworks.game_id = games.id AND game_artworks.hero_url != ''").
		Where("games.deleted_at IS NULL AND games.developer != ''").
		Group("games.developer").
		Order("game_count DESC").
		Limit(5).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch developer spotlight candidates", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch developer spotlight"})
		return
	}

	if len(rows) == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "no developers with hero art found"})
		return
	}

	// Pick based on week number for deterministic weekly rotation
	_, weekNumber := time.Now().ISOWeek()
	selectedDev := rows[weekNumber%len(rows)]

	// Load all games for this developer
	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("LOWER(games.developer) = LOWER(?) AND games.deleted_at IS NULL", selectedDev.Developer).
		Order("games.rating DESC, games.title ASC").
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch spotlight developer games", "developer", selectedDev.Developer, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch developer spotlight"})
		return
	}

	avgRating, consoles := calcRatingAndConsoles(games)

	// Take top 8 for the response
	topGames := games
	if len(topGames) > 8 {
		topGames = topGames[:8]
	}

	// Find hero URL from the highest-rated game with hero art
	heroURL := ""
	if len(games) > 0 {
		gameIDs := make([]uint, len(games))
		for i, g := range games {
			gameIDs[i] = g.ID
		}
		var artwork db.GameArtwork
		if err := h.DB.
			Joins("JOIN games ON games.id = game_artworks.game_id").
			Where("game_artworks.game_id IN ? AND game_artworks.hero_url != ''", gameIDs).
			Order("games.rating DESC").
			First(&artwork).Error; err == nil {
			heroURL = artwork.HeroURL
		}
	}

	// Count total games by this developer (including those without hero art)
	var totalGameCount int64
	h.DB.Model(&db.Game{}).
		Where("LOWER(developer) = LOWER(?) AND deleted_at IS NULL", selectedDev.Developer).
		Count(&totalGameCount)

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, DeveloperSpotlightResponse{
		Name:      selectedDev.Developer,
		GameCount: int(totalGameCount),
		AvgRating: avgRating,
		Consoles:  consoles,
		TopGames:  ToGameResponses(topGames, h.DB, userID),
		HeroURL:   heroURL,
	})
}

// parseReleaseYear extracts the year from a release date string (format "2006-01-02").
// Returns 0 if the string is empty or cannot be parsed.
func parseReleaseYear(releaseDate string) int {
	if releaseDate == "" {
		return 0
	}
	t, err := time.Parse("2006-01-02", releaseDate)
	if err != nil {
		return 0
	}
	return t.Year()
}

// buildActiveYears computes the first and last release years from a slice of games.
// Returns nil if no games have valid release dates.
func buildActiveYears(games []db.Game) *ActiveYears {
	first := 0
	last := 0
	for _, g := range games {
		year := parseReleaseYear(g.ReleaseDate)
		if year == 0 {
			continue
		}
		if first == 0 || year < first {
			first = year
		}
		if year > last {
			last = year
		}
	}
	if first == 0 {
		return nil
	}
	return &ActiveYears{First: first, Last: last}
}

// buildRatingDistribution computes rating bucket counts from a slice of games.
// Buckets: excellent (90-100), good (70-89), average (50-69), poor (0-49 excluding 0), unrated (0/no rating).
func buildRatingDistribution(games []db.Game) RatingDistribution {
	var dist RatingDistribution
	for _, g := range games {
		r := g.Rating
		switch {
		case r == 0:
			dist.Unrated++
		case r >= 90:
			dist.Excellent++
		case r >= 70:
			dist.Good++
		case r >= 50:
			dist.Average++
		default:
			dist.Poor++
		}
	}
	return dist
}

// buildPrimaryGenre returns the genre with the most games, or "" if no games have genre data.
// Handles comma-separated genre values by splitting and trimming each part.
func buildPrimaryGenre(games []db.Game) string {
	genreCounts := make(map[string]int)
	for _, g := range games {
		if g.Genre == "" {
			continue
		}
		parts := strings.Split(g.Genre, ",")
		for _, part := range parts {
			genre := strings.TrimSpace(part)
			if genre != "" {
				genreCounts[genre]++
			}
		}
	}
	if len(genreCounts) == 0 {
		return ""
	}
	bestGenre := ""
	bestCount := 0
	for genre, count := range genreCounts {
		if count > bestCount || (count == bestCount && genre < bestGenre) {
			bestGenre = genre
			bestCount = count
		}
	}
	return bestGenre
}

// buildTimeline groups games by release year for timeline visualization.
// Returns nil if fewer than 3 games have release dates or fewer than 2 distinct years.
func buildTimeline(games []db.Game) []TimelineEntry {
	yearGames := make(map[int][]TimelineGame)
	datedCount := 0
	for _, g := range games {
		year := parseReleaseYear(g.ReleaseDate)
		if year == 0 {
			continue
		}
		datedCount++
		yearGames[year] = append(yearGames[year], TimelineGame{
			ID:       strconv.FormatUint(uint64(g.ID), 10),
			Title:    g.Title,
			CoverURL: g.CoverURL,
			Rating:   g.Rating,
		})
	}
	if datedCount < 3 || len(yearGames) < 2 {
		return nil
	}

	years := make([]int, 0, len(yearGames))
	for y := range yearGames {
		years = append(years, y)
	}
	sort.Ints(years)

	timeline := make([]TimelineEntry, len(years))
	for i, y := range years {
		timeline[i] = TimelineEntry{Year: y, Games: yearGames[y]}
	}
	return timeline
}

// calcRatingAndConsoles computes the average rating and distinct console display names
// for a slice of games. Returns (avgRating, consoles) sorted alphabetically.
func calcRatingAndConsoles(games []db.Game) (float64, []string) {
	var ratingSum float64
	var ratingCount int
	consoleSet := make(map[string]bool)

	for _, g := range games {
		if g.Rating > 0 {
			ratingSum += g.Rating
			ratingCount++
		}
		if g.Console.Name != "" {
			consoleSet[g.Console.Name] = true
		}
	}

	avgRating := 0.0
	if ratingCount > 0 {
		avgRating = math.Round(ratingSum/float64(ratingCount)*10) / 10
	}

	consoles := make([]string, 0, len(consoleSet))
	for name := range consoleSet {
		consoles = append(consoles, name)
	}
	sort.Strings(consoles)
	if len(consoles) == 0 {
		consoles = []string{}
	}

	return avgRating, consoles
}
