package api

import (
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// batchLoadGamesWithConsole loads games with their consoles in a single query.
func batchLoadGamesWithConsole(database *gorm.DB, gameIDs []uint) map[uint]db.Game {
	if len(gameIDs) == 0 {
		return nil
	}
	// Deduplicate
	seen := make(map[uint]bool, len(gameIDs))
	unique := make([]uint, 0, len(gameIDs))
	for _, id := range gameIDs {
		if !seen[id] {
			seen[id] = true
			unique = append(unique, id)
		}
	}

	var games []db.Game
	database.Preload("Console").Where("id IN ?", unique).Find(&games)
	result := make(map[uint]db.Game, len(games))
	for _, g := range games {
		result[g.ID] = g
	}
	return result
}
