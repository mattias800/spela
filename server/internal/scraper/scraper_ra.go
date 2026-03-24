package scraper

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"path/filepath"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
)

// ScrapeRAAchievements fetches RetroAchievements data for games that don't have
// a cached RA game ID yet. It uses the server-level API key (not user credentials)
// so achievement data is available to all users.
//
// For each game:
//  1. Compute MD5 hash of the ROM file
//  2. Look up the RA game ID from the hash
//  3. Store the RA game ID on the game record
//  4. Fetch achievement data via GetGameExtended (public API)
//  5. Cache in GameAchievementCache
//
// Returns (successes, total, error).
func (s *Scraper) ScrapeRAAchievements(ctx context.Context, onProgress func(current, total int)) (int, int, error) {
	if !s.IsRAConfigured() {
		return 0, 0, fmt.Errorf("RA client or API key not configured")
	}

	// Find all games without an RA game ID that are on playable consoles
	var games []db.Game
	if err := s.DB.Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.playable = ?", true).
		Where("games.ra_game_id = 0 OR games.ra_game_id IS NULL").
		Where("games.deleted_at IS NULL").
		Preload("Console").
		Find(&games).Error; err != nil {
		return 0, 0, fmt.Errorf("loading games for RA scraping: %w", err)
	}

	total := len(games)
	successes := 0

	slog.Info("starting RA achievement scraping", "total", total)

	for i, game := range games {
		// Check for cancellation
		if ctx.Err() != nil {
			slog.Info("RA scrape cancelled", "completed", i, "total", total)
			return successes, total, ctx.Err()
		}

		if onProgress != nil {
			onProgress(i, total)
		}

		// Build ROM path and compute hash — try each game dir
		var hash string
		for _, dir := range s.GameDirs {
			candidate := filepath.Join(dir, game.FilePath)
			if h, err := retroachievements.ComputeMD5(candidate); err == nil {
				hash = h
				break
			}
		}
		if hash == "" {
			slog.Debug("RA: ROM file not found for game", "game", game.Title, "path", game.FilePath)
			continue
		}

		// Look up RA game ID from hash (API call — rate limit before)
		time.Sleep(500 * time.Millisecond)
		raGameID, err := s.RAClient.GetGameIDFromHash(hash)
		if err != nil {
			slog.Debug("RA: no game ID for hash", "game", game.Title, "hash", hash, "error", err)
			continue
		}

		// Store the RA game ID on the game record
		if err := s.DB.Model(&db.Game{}).Where("id = ?", game.ID).Update("ra_game_id", raGameID).Error; err != nil {
			slog.Warn("RA: failed to update game with RA ID", "game", game.Title, "raGameId", raGameID, "error", err)
			continue
		}

		// Fetch achievement data via public API (API call — rate limit before)
		time.Sleep(500 * time.Millisecond)
		gameInfo, err := s.RAClient.GetGameExtended(s.RAAPIKey, raGameID)
		if err != nil {
			slog.Warn("RA: failed to fetch game extended", "game", game.Title, "raGameId", raGameID, "error", err)
			continue
		}

		// Cache the achievement data
		achJSON, err := json.Marshal(gameInfo.Achievements)
		if err != nil {
			slog.Warn("RA: failed to marshal achievements", "game", game.Title, "error", err)
			continue
		}

		var existing db.GameAchievementCache
		if err := s.DB.Where("ra_game_id = ?", raGameID).First(&existing).Error; err == nil {
			// Update existing cache
			existing.Title = gameInfo.Title
			existing.AchievementJSON = string(achJSON)
			existing.TotalCount = gameInfo.TotalCount
			existing.TotalPoints = gameInfo.TotalPoints
			existing.CachedAt = time.Now()
			existing.GameID = game.ID
			s.DB.Save(&existing)
		} else {
			// Create new cache entry
			s.DB.Create(&db.GameAchievementCache{
				RAGameID:        raGameID,
				GameID:          game.ID,
				Title:           gameInfo.Title,
				AchievementJSON: string(achJSON),
				TotalCount:      gameInfo.TotalCount,
				TotalPoints:     gameInfo.TotalPoints,
				CachedAt:        time.Now(),
			})
		}

		successes++
		slog.Info("RA: cached achievements", "game", game.Title, "raGameId", raGameID, "achievements", gameInfo.TotalCount)
	}

	if onProgress != nil {
		onProgress(total, total)
	}

	return successes, total, nil
}
