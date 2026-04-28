package api

import (
	"log/slog"
	"math"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// The gin handlers GetDevelopers, GetDeveloperDetail, GetPublisherDetail, and
// GetDeveloperSpotlight have been migrated to huma — see
// huma_explore_developer.go. Only the shared helper functions remain here
// because the huma handlers still depend on them.

// buildRelatedDevelopers finds other developers that share publishers with the given developer.
// It returns up to 5 related developers, ranked by the number of shared publishers (descending).
// Returns nil if no related developers are found.
func buildRelatedDevelopers(database *gorm.DB, developerName string, publishers []NameCount) []RelatedDeveloper {
	if len(publishers) == 0 {
		return []RelatedDeveloper{}
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
		Where("deleted_at IS NULL AND is_primary = true AND developer != '' AND publisher IN ? AND LOWER(developer) != LOWER(?)", publisherNames, developerName).
		Group("developer, publisher").
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch related developers", "error", err)
		return []RelatedDeveloper{}
	}

	if len(rows) == 0 {
		return []RelatedDeveloper{}
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
		Where("deleted_at IS NULL AND is_primary = true AND developer IN ?", relatedDevNames).
		Group("developer").
		Scan(&countRows).Error; err != nil {
		slog.Error("failed to fetch related developer game counts", "error", err)
		return []RelatedDeveloper{}
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
		return []RelatedPublisher{}
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
		Where("deleted_at IS NULL AND is_primary = true AND publisher != '' AND developer IN ? AND LOWER(publisher) != LOWER(?)", developerNames, publisherName).
		Group("publisher, developer").
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch related publishers", "error", err)
		return []RelatedPublisher{}
	}

	if len(rows) == 0 {
		return []RelatedPublisher{}
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
		Where("deleted_at IS NULL AND is_primary = true AND publisher IN ?", relatedPubNames).
		Group("publisher").
		Scan(&countRows).Error; err != nil {
		slog.Error("failed to fetch related publisher game counts", "error", err)
		return []RelatedPublisher{}
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
		return resolveImageURL(artwork.HeroURL)
	}
	return ""
}

// buildTopGames returns up to limit highest-rated games (only those with rating > 0).
// gameResponses is expected to be sorted by rating DESC already.
func buildTopGames(gameResponses []GameResponse, limit int) []GameResponse {
	var top []GameResponse
	for _, gr := range gameResponses {
		if gr.IGDBCriticsRating > 0 {
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
		r := g.IGDBCriticsRating
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
			ID:                strconv.FormatUint(uint64(g.ID), 10),
			Title:             g.Title,
			CoverURL:          resolveImageURL(g.CoverURL),
			IGDBCriticsRating: g.IGDBCriticsRating,
		})
	}
	if datedCount < 3 || len(yearGames) < 2 {
		return []TimelineEntry{}
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
		if g.IGDBCriticsRating > 0 {
			ratingSum += g.IGDBCriticsRating
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
