package scraper

import (
	"fmt"
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
	root, err := resolveTitleRootIGDBIDStrict(match, fetch)
	if err != nil {
		slog.Warn("IGDB title-root resolution failed", "startIgdbID", match.ID, "error", err)
		return nil
	}
	return root
}

func resolveTitleRootIGDBID(match igdb.Game, fetch igdbGameFetcher) *uint {
	root, err := resolveTitleRootIGDBIDStrict(match, fetch)
	if err != nil {
		slog.Warn("IGDB title-root resolution failed", "startIgdbID", match.ID, "error", err)
		return nil
	}
	return root
}

func resolveTitleRootIGDBIDStrict(match igdb.Game, fetch igdbGameFetcher) (*uint, error) {
	if match.ID <= 0 {
		return nil, nil
	}

	current := match
	currentID := match.ID
	visited := map[int]bool{currentID: true}

	for depth := 0; depth < maxTitleRootDepth; depth++ {
		nextID := nextTitleAncestorID(current)
		if nextID == 0 {
			return uintPtrFromInt(currentID), nil
		}
		if visited[nextID] {
			slog.Warn("IGDB title-root cycle detected", "startIgdbID", match.ID, "currentIgdbID", currentID, "nextIgdbID", nextID)
			return uintPtrFromInt(currentID), nil
		}

		visited[nextID] = true
		currentID = nextID
		if fetch == nil {
			return nil, fmt.Errorf("fetching IGDB title-root ancestor %d: no fetcher configured", nextID)
		}

		next, err := fetch(nextID)
		if err != nil {
			return nil, fmt.Errorf("fetching IGDB title-root ancestor %d: %w", nextID, err)
		}
		if next == nil {
			slog.Warn("IGDB title-root ancestor not found", "startIgdbID", match.ID, "ancestorIgdbID", nextID)
			return nil, nil
		}
		current = *next
	}

	slog.Warn("IGDB title-root depth cap reached", "startIgdbID", match.ID, "maxDepth", maxTitleRootDepth, "deepestIgdbID", currentID)
	return uintPtrFromInt(currentID), nil
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
