package api

import (
	"fmt"
	"sort"
	"strconv"
	"strings"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

const (
	gamePlatformGroupIGDBRoot = "igdb"
	gamePlatformGroupTitle    = "title"
	gamePlatformGroupID       = "id"
)

// loadGamePlatforms returns the platform releases for each selected game,
// grouped by IGDB title root when available and by the existing normalized
// title fallback otherwise.
func loadGamePlatforms(database *gorm.DB, userID uint, games []db.Game) map[uint][]GamePlatformResponse {
	result := make(map[uint][]GamePlatformResponse, len(games))
	if len(games) == 0 {
		return result
	}

	wantedKeys := make(map[string]struct{}, len(games))
	rootIDs := make(map[uint]struct{})
	keyByGameID := make(map[uint]string, len(games))

	for _, game := range games {
		key := gamePlatformGroupKey(game)
		keyByGameID[game.ID] = key
		wantedKeys[key] = struct{}{}
		result[game.ID] = []GamePlatformResponse{toGamePlatformResponse(game, true)}

		if game.TitleRootIGDBID != nil {
			rootIDs[*game.TitleRootIGDBID] = struct{}{}
		}
	}

	preferredByTitle := fetchPreferredTitlePlatformMap(database, userID, wantedKeys)

	grouped := make(map[string][]db.Game, len(wantedKeys))
	if database != nil {
		for _, game := range loadGamesByTitleRoots(database, rootIDs) {
			key := gamePlatformGroupKey(game)
			if _, ok := wantedKeys[key]; ok {
				appendUniquePlatformGame(grouped, key, game)
			}
		}
	}

	// Keep the selected games in their own groups even when the database is
	// nil, the row has not been persisted yet, or the caller supplied a game
	// whose Console relation is already richer than the minimal sibling query.
	// This also provides the normalized-title fallback within the current
	// response batch without scanning the full table for no-root games.
	for _, game := range games {
		appendUniquePlatformGame(grouped, keyByGameID[game.ID], game)
	}

	for _, game := range games {
		key := keyByGameID[game.ID]
		result[game.ID] = buildGamePlatformResponses(grouped[key], game, preferredByTitle[key])
	}
	return result
}

func fetchPreferredTitlePlatformMap(database *gorm.DB, userID uint, keys map[string]struct{}) map[string]uint {
	result := make(map[string]uint)
	if database == nil || userID == 0 || len(keys) == 0 {
		return result
	}

	titleKeys := make([]string, 0, len(keys))
	for key := range keys {
		titleKeys = append(titleKeys, key)
	}
	sort.Strings(titleKeys)

	var prefs []db.UserTitlePlatformPreference
	database.
		Select("title_key", "preferred_game_id").
		Where("user_id = ? AND title_key IN ?", userID, titleKeys).
		Find(&prefs)
	for _, pref := range prefs {
		result[pref.TitleKey] = pref.PreferredGameID
	}
	return result
}

func loadGamesByTitleRoots(database *gorm.DB, rootIDs map[uint]struct{}) []db.Game {
	if len(rootIDs) == 0 {
		return nil
	}

	ids := make([]uint, 0, len(rootIDs))
	for id := range rootIDs {
		ids = append(ids, id)
	}
	sort.Slice(ids, func(i, j int) bool { return ids[i] < ids[j] })

	var games []db.Game
	database.
		Select("id", "console_id", "title", "title_root_igdb_id").
		Preload("Console").
		Where("title_root_igdb_id IN ?", ids).
		Find(&games)
	return games
}

func gamePlatformGroupKey(game db.Game) string {
	if game.TitleRootIGDBID != nil {
		return fmt.Sprintf("%s:%d", gamePlatformGroupIGDBRoot, *game.TitleRootIGDBID)
	}
	if key := normalizeTitleKey(game.Title); key != "" {
		return gamePlatformGroupTitle + ":" + key
	}
	return fmt.Sprintf("%s:%d", gamePlatformGroupID, game.ID)
}

func appendUniquePlatformGame(groups map[string][]db.Game, key string, game db.Game) {
	for _, existing := range groups[key] {
		if existing.ID == game.ID {
			return
		}
	}
	groups[key] = append(groups[key], game)
}

func buildGamePlatformResponses(games []db.Game, current db.Game, savedPreferredID uint) []GamePlatformResponse {
	if len(games) == 0 {
		return []GamePlatformResponse{toGamePlatformResponse(current, true)}
	}

	preferredID := current.ID
	if savedPreferredID != 0 && containsPlatformGameID(games, savedPreferredID) {
		preferredID = savedPreferredID
	}

	gamesByConsole := make(map[string]db.Game, len(games))
	for _, game := range games {
		key := gamePlatformConsoleKey(game)
		if existing, ok := gamesByConsole[key]; !ok || shouldReplacePlatformCandidate(game, existing, preferredID, current.ID) {
			gamesByConsole[key] = game
		}
	}

	deduped := make([]db.Game, 0, len(gamesByConsole))
	currentIncluded := false
	currentConsoleIncluded := false
	currentConsoleKey := gamePlatformConsoleKey(current)
	for _, game := range gamesByConsole {
		if game.ID == current.ID {
			currentIncluded = true
		}
		if currentConsoleKey != "" && gamePlatformConsoleKey(game) == currentConsoleKey {
			currentConsoleIncluded = true
		}
		deduped = append(deduped, game)
	}
	if !currentIncluded && !currentConsoleIncluded && current.ID != 0 {
		deduped = append(deduped, current)
	}

	sort.SliceStable(deduped, func(i, j int) bool {
		leftPreferred := deduped[i].ID == preferredID
		rightPreferred := deduped[j].ID == preferredID
		if leftPreferred != rightPreferred {
			return leftPreferred
		}

		leftGen, rightGen := deduped[i].Console.Generation, deduped[j].Console.Generation
		if leftGen != rightGen {
			if leftGen == 0 {
				return false
			}
			if rightGen == 0 {
				return true
			}
			return leftGen < rightGen
		}

		leftName := strings.ToLower(deduped[i].Console.Name)
		rightName := strings.ToLower(deduped[j].Console.Name)
		if leftName != rightName {
			return leftName < rightName
		}
		return deduped[i].ID < deduped[j].ID
	})

	platforms := make([]GamePlatformResponse, 0, len(deduped))
	for _, game := range deduped {
		platforms = append(platforms, toGamePlatformResponse(game, game.ID == preferredID))
	}
	return platforms
}

func containsPlatformGameID(games []db.Game, gameID uint) bool {
	for _, game := range games {
		if game.ID == gameID {
			return true
		}
	}
	return false
}

func shouldReplacePlatformCandidate(candidate db.Game, existing db.Game, preferredID uint, currentID uint) bool {
	candidatePriority := platformCandidatePriority(candidate.ID, preferredID, currentID)
	existingPriority := platformCandidatePriority(existing.ID, preferredID, currentID)
	if candidatePriority != existingPriority {
		return candidatePriority < existingPriority
	}
	return candidate.ID < existing.ID
}

func platformCandidatePriority(gameID uint, preferredID uint, currentID uint) int {
	if preferredID != 0 && gameID == preferredID {
		return 0
	}
	if currentID != 0 && gameID == currentID {
		return 1
	}
	return 2
}

func toGamePlatformResponse(game db.Game, preferred bool) GamePlatformResponse {
	return GamePlatformResponse{
		GameID:      strconv.FormatUint(uint64(game.ID), 10),
		ConsoleID:   gamePlatformConsoleID(game),
		ConsoleName: game.Console.Name,
		IsPreferred: preferred,
	}
}

func gamePlatformConsoleKey(game db.Game) string {
	if id := gamePlatformConsoleID(game); id != "" {
		return id
	}
	return fmt.Sprintf("game:%d", game.ID)
}

func gamePlatformConsoleID(game db.Game) string {
	if game.Console.ID == 0 {
		return ""
	}
	return strings.ToLower(game.Console.Abbreviation)
}
