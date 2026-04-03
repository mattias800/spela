package api

import (
	"math/rand"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

// GetExploreFeatured returns featured games for the hero carousel.
// Selects 10 games via weighted random sampling (higher-rated games are more
// likely to appear). The selection is seeded by the current date so results
// are stable within a day but rotate daily. Games must have both hero art
// and logo art.
func (h *ExploreHandler) GetExploreFeatured(c *gin.Context) {
	userID := getUserID(c)

	// Load all games that have hero art AND logo art
	type featuredRow struct {
		GameID    uint
		Title     string
		HeroURL   string
		LogoURL   string
		Rating    float64
		Genre     string
		ConsoleID uint
	}

	var allRows []featuredRow
	err := h.DB.
		Table("games").
		Select("games.id AS game_id, games.title, game_artworks.hero_url, game_artworks.logo_url, "+effectiveRatingPrefixed+" AS rating, games.genre, games.console_id").
		Joins("JOIN game_artworks ON game_artworks.game_id = games.id").
		Where("games.deleted_at IS NULL").
		Where("games.is_primary = true").
		Where("game_artworks.hero_url != '' AND game_artworks.logo_url != ''").
		Scan(&allRows).Error
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch featured games"})
		return
	}

	// Weighted random sampling: pick 10 games, seeded by today's date
	rows := weightedSample(allRows, 10, func(r featuredRow) float64 {
		if r.Rating > 0 {
			return r.Rating
		}
		return 10 // base weight for unrated games so they're not excluded
	})

	if len(rows) == 0 {
		c.Header("Cache-Control", "private, max-age=300")
		c.JSON(http.StatusOK, []FeaturedGameResponse{})
		return
	}

	// Batch-load console data for these games
	consoleIDs := make([]uint, 0, len(rows))
	for _, r := range rows {
		consoleIDs = append(consoleIDs, r.ConsoleID)
	}
	var consoles []db.Console
	if err := h.DB.Where("id IN ?", consoleIDs).Find(&consoles).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch console data"})
		return
	}
	consoleMap := make(map[uint]db.Console, len(consoles))
	for _, con := range consoles {
		consoleMap[con.ID] = con
	}

	// Batch-load user data (favorites, play later)
	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}
	favorites := make(map[uint]bool)
	playLater := make(map[uint]bool)
	if userID > 0 {
		var favs []db.Favorite
		if err := h.DB.Where("user_id = ? AND game_id IN ?", userID, gameIDs).Find(&favs).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch favorites"})
			return
		}
		for _, f := range favs {
			favorites[f.GameID] = true
		}
		var plItems []db.PlayLaterItem
		if err := h.DB.Where("user_id = ? AND game_id IN ?", userID, gameIDs).Find(&plItems).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch play later items"})
			return
		}
		for _, item := range plItems {
			playLater[item.GameID] = true
		}
	}

	result := make([]FeaturedGameResponse, len(rows))
	for i, r := range rows {
		con := consoleMap[r.ConsoleID]
		abbr := strings.ToLower(con.Abbreviation)
		result[i] = FeaturedGameResponse{
			GameID:              strconv.FormatUint(uint64(r.GameID), 10),
			Title:               r.Title,
			HeroURL:             resolveImageURL(r.HeroURL),
			LogoURL:             resolveImageURL(r.LogoURL),
			ConsoleID:           abbr,
			ConsoleName:         con.Name,
			ConsoleAbbreviation: abbr,
			ConsoleColor:        con.ColorTheme,
			Rating:              r.Rating,
			Genre:               r.Genre,
			IsFavorite:          favorites[r.GameID],
			IsPlayLater:         playLater[r.GameID],
		}
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, result)
}

// GetExploreRows returns all curated shelf rows for the explore page.
// Empty rows are omitted from the response.
func (h *ExploreHandler) GetExploreRows(c *gin.Context) {
	userID := getUserID(c)

	rows := []ExploreRowResponse{}

	// Top Rated: top 20 games by IGDB rating, rating > 0
	if row, err := h.buildTopRatedRow(userID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build top-rated row"})
		return
	} else if row != nil {
		rows = append(rows, *row)
	}

	// Recently Added: top 20 games by created_at DESC
	if row, err := h.buildRecentlyAddedRow(userID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build recently-added row"})
		return
	} else if row != nil {
		rows = append(rows, *row)
	}

	// Hidden Gems: high rating + low play time across all users
	if row, err := h.buildHiddenGemsRow(userID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build hidden-gems row"})
		return
	} else if row != nil {
		rows = append(rows, *row)
	}

	// Most Played on Your Server: top 20 by total play time across all users
	if row, err := h.buildMostPlayedRow(userID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build most-played row"})
		return
	} else if row != nil {
		rows = append(rows, *row)
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, ExploreRowsResponse{Rows: rows})
}

// buildTopRatedRow returns the top 20 games by IGDB rating, cross-console.
func (h *ExploreHandler) buildTopRatedRow(userID uint) (*ExploreRowResponse, error) {
	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("is_primary = true").
		Where(effectiveRating + " > 0").
		Order(effectiveRating + " DESC").
		Limit(20).
		Find(&games).Error; err != nil {
		return nil, err
	}

	if len(games) == 0 {
		return nil, nil
	}

	return &ExploreRowResponse{
		ID:    "top-rated",
		Title: "Top Rated",
		Games: ToGameResponses(games, h.DB, userID),
	}, nil
}

// buildRecentlyAddedRow returns the 20 most recently added games.
func (h *ExploreHandler) buildRecentlyAddedRow(userID uint) (*ExploreRowResponse, error) {
	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("is_primary = true").
		Order("created_at DESC").
		Limit(20).
		Find(&games).Error; err != nil {
		return nil, err
	}

	if len(games) == 0 {
		return nil, nil
	}

	return &ExploreRowResponse{
		ID:    "recently-added",
		Title: "Recently Added",
		Games: ToGameResponses(games, h.DB, userID),
	}, nil
}

// buildHiddenGemsRow returns games with high IGDB rating but low total play time
// across all users. Uses SQL to compute the play-time threshold and filter in a
// single bounded query instead of loading all games/play history into memory.
func (h *ExploreHandler) buildHiddenGemsRow(userID uint) (*ExploreRowResponse, error) {
	// First, check if there are at least 5 games total (per acceptance criteria,
	// hidden gems section is hidden when library is tiny)
	var totalGames int64
	if err := h.DB.Model(&db.Game{}).Where("is_primary = true").Count(&totalGames).Error; err != nil {
		return nil, err
	}
	if totalGames < 5 {
		return nil, nil
	}

	// Calculate the play-time threshold (25th percentile) in SQL.
	// We only need the count of games with play time > 0 and the threshold value.
	type thresholdRow struct {
		TotalPlayTime int64
	}
	var playTimes []thresholdRow
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("COALESCE(SUM(play_time), 0) as total_play_time").
		Group("game_id").
		Having("total_play_time > 0").
		Order("total_play_time ASC").
		Scan(&playTimes).Error; err != nil {
		return nil, err
	}

	var threshold int64
	if len(playTimes) > 0 {
		idx := len(playTimes) / 4
		threshold = playTimes[idx].TotalPlayTime
		if threshold == 0 {
			threshold = 1
		}
	}
	// If threshold is 0 (no play history at all), all rated games qualify

	// Query games with rating >= 75 and low/zero play time in a single bounded query.
	// LEFT JOIN aggregated play history, filter where total play time <= threshold or NULL.
	var games []db.Game
	query := h.DB.Preload("Console").
		Joins("LEFT JOIN (SELECT game_id, COALESCE(SUM(play_time), 0) as total_play_time FROM play_histories GROUP BY game_id) ph ON ph.game_id = games.id").
		Where(effectiveRatingPrefixed + " >= 75").
		Where("games.deleted_at IS NULL").
		Where("games.is_primary = true")

	if threshold > 0 {
		query = query.Where("ph.total_play_time IS NULL OR ph.total_play_time <= ?", threshold)
	}

	if err := query.
		Order(effectiveRatingPrefixed + " DESC").
		Limit(20).
		Find(&games).Error; err != nil {
		return nil, err
	}

	if len(games) == 0 {
		return nil, nil
	}

	return &ExploreRowResponse{
		ID:    "hidden-gems",
		Title: "Hidden Gems",
		Games: ToGameResponses(games, h.DB, userID),
	}, nil
}

// buildMostPlayedRow returns the top 20 games by total play time across all users.
func (h *ExploreHandler) buildMostPlayedRow(userID uint) (*ExploreRowResponse, error) {
	// Aggregate play time per game across all users, bounded to top 20
	type playTimeRow struct {
		GameID        uint
		TotalPlayTime int64
	}
	var playTimeRows []playTimeRow
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("game_id, COALESCE(SUM(play_time), 0) as total_play_time").
		Group("game_id").
		Having("total_play_time > 0").
		Order("total_play_time DESC").
		Limit(20).
		Scan(&playTimeRows).Error; err != nil {
		return nil, err
	}

	if len(playTimeRows) == 0 {
		return nil, nil
	}

	// Load the game objects (scoped to only the game IDs we need)
	gameIDs := make([]uint, len(playTimeRows))
	for i, r := range playTimeRows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ? AND is_primary = true", gameIDs).Find(&games).Error; err != nil {
		return nil, err
	}

	if len(games) == 0 {
		return nil, nil
	}

	// Re-sort games by total play time (the IN query may not preserve order)
	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}
	sortedGames := make([]db.Game, 0, len(playTimeRows))
	for _, r := range playTimeRows {
		if g, ok := gameMap[r.GameID]; ok {
			sortedGames = append(sortedGames, g)
		}
	}

	return &ExploreRowResponse{
		ID:    "most-played",
		Title: "Most Played on Your Server",
		Games: ToGameResponses(sortedGames, h.DB, userID),
	}, nil
}

// weightedSample picks n items from pool using weighted random sampling without
// replacement. The weight function determines each item's relative probability
// of being selected. Uses today's date as seed for stable daily results.
func weightedSample[T any](pool []T, n int, weight func(T) float64) []T {
	if len(pool) <= n {
		return pool
	}

	// Seed with today's date for daily rotation
	today := time.Now().UTC().Truncate(24 * time.Hour)
	rng := rand.New(rand.NewSource(today.UnixNano()))

	// Build cumulative weights
	weights := make([]float64, len(pool))
	for i, item := range pool {
		weights[i] = weight(item)
	}

	// Weighted sampling without replacement
	selected := make([]T, 0, n)
	remaining := make([]int, len(pool))
	for i := range remaining {
		remaining[i] = i
	}

	for len(selected) < n && len(remaining) > 0 {
		// Compute cumulative sum of remaining weights
		var totalWeight float64
		for _, idx := range remaining {
			totalWeight += weights[idx]
		}

		// Pick a random point in the weight space
		target := rng.Float64() * totalWeight
		var cumulative float64
		pickedPos := len(remaining) - 1 // fallback to last
		for pos, idx := range remaining {
			cumulative += weights[idx]
			if cumulative >= target {
				pickedPos = pos
				break
			}
		}

		selected = append(selected, pool[remaining[pickedPos]])
		// Remove picked item from remaining (swap with last, shrink)
		remaining[pickedPos] = remaining[len(remaining)-1]
		remaining = remaining[:len(remaining)-1]
	}

	return selected
}
