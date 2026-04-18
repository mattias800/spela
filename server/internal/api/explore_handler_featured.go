package api

import (
	"math/rand"
	"time"

	"github.com/spela/server/internal/db"
)

// The gin handlers GetExploreFeatured and GetExploreRows have been migrated to
// huma — see huma_explore_featured.go. The helper functions (buildTopRatedRow,
// buildRecentlyAddedRow, buildHiddenGemsRow, buildMostPlayedRow, weightedSample)
// are kept here because the huma handlers and tests still reference them.

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
