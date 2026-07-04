package scraper

import (
	"log/slog"

	"github.com/spela/server/internal/igdb"
)

const maxTitleRootDepth = 5

type igdbGameFetcher func(id int) (*igdb.Game, error)

func (s *Scraper) resolveTitleRootIGDBID(match igdb.Game) *uint {
	var fetch igdbGameFetcher
	if s.IGDBClient != nil {
		fetch = s.IGDBClient.GetGameByID
	}
	return resolveTitleRootIGDBID(match, fetch)
}

func resolveTitleRootIGDBID(match igdb.Game, fetch igdbGameFetcher) *uint {
	if match.ID <= 0 {
		return nil
	}

	current := match
	currentID := match.ID
	visited := map[int]bool{currentID: true}

	for depth := 0; depth < maxTitleRootDepth; depth++ {
		nextID := nextTitleAncestorID(current)
		if nextID == 0 {
			return uintPtrFromInt(currentID)
		}
		if visited[nextID] {
			slog.Warn("IGDB title-root cycle detected", "startIgdbID", match.ID, "currentIgdbID", currentID, "nextIgdbID", nextID)
			return uintPtrFromInt(currentID)
		}

		visited[nextID] = true
		currentID = nextID
		if fetch == nil {
			slog.Warn("IGDB title-root ancestor cannot be fetched", "startIgdbID", match.ID, "ancestorIgdbID", nextID)
			return nil
		}

		next, err := fetch(nextID)
		if err != nil {
			slog.Warn("IGDB title-root ancestor fetch failed", "startIgdbID", match.ID, "ancestorIgdbID", nextID, "error", err)
			return nil
		}
		if next == nil {
			slog.Warn("IGDB title-root ancestor not found", "startIgdbID", match.ID, "ancestorIgdbID", nextID)
			return nil
		}
		current = *next
	}

	slog.Warn("IGDB title-root depth cap reached", "startIgdbID", match.ID, "maxDepth", maxTitleRootDepth, "deepestIgdbID", currentID)
	return uintPtrFromInt(currentID)
}

func nextTitleAncestorID(game igdb.Game) int {
	if game.ParentGameID != nil && *game.ParentGameID > 0 {
		return *game.ParentGameID
	}
	if game.VersionParentID != nil && *game.VersionParentID > 0 {
		return *game.VersionParentID
	}
	return 0
}

func uintPtrFromInt(v int) *uint {
	if v <= 0 {
		return nil
	}
	u := uint(v)
	return &u
}

func uintPtrFromOptionalInt(v *int) *uint {
	if v == nil {
		return nil
	}
	return uintPtrFromInt(*v)
}

func intPtrCopy(v *int) *int {
	if v == nil {
		return nil
	}
	copied := *v
	return &copied
}
