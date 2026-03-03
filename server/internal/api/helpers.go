package api

import (
	"fmt"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// errNonPlayableConsole is returned when a play-related feature is attempted
// on a game belonging to a non-playable console.
var errNonPlayableConsole = fmt.Errorf("feature not available for non-playable consoles")

// requirePlayableConsole checks that the game with the given ID belongs to
// a playable console. Returns errNonPlayableConsole if the console has
// Playable == false.
func requirePlayableConsole(database *gorm.DB, gameID uint) error {
	var game db.Game
	if err := database.Preload("Console").First(&game, gameID).Error; err != nil {
		return fmt.Errorf("loading game: %w", err)
	}
	if !game.Console.Playable {
		return errNonPlayableConsole
	}
	return nil
}
