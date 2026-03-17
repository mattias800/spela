package scraper

import (
	"fmt"
	"log/slog"
	"strings"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/pouet"
)

// DemoConsoles maps console abbreviations that should use Pouet for scraping.
var DemoConsoles = map[string]bool{
	"ADEMO": true,
	"DDEMO": true,
}

// scrapePouet searches Pouet.net for a matching demoscene production and applies metadata.
func (s *Scraper) scrapePouet(game *db.Game, console db.Console, gameIDStr string) error {
	if s.PouetClient == nil {
		return fmt.Errorf("pouet client not initialized")
	}

	platformIDs, ok := pouet.AbbreviationToPouetPlatforms[console.Abbreviation]
	if !ok {
		return fmt.Errorf("no Pouet platform mapping for console %s", console.Abbreviation)
	}

	// Clean the demo filename for searching
	cleanName := pouet.CleanDemoName(game.FileName)
	slog.Info("searching Pouet", "game", game.Title, "query", cleanName)

	// Try searching with the full name first (includes group name in parens)
	prods, err := s.PouetClient.SearchProd(cleanName)
	if err != nil {
		return fmt.Errorf("pouet search: %w", err)
	}

	// Filter by platform
	filtered := pouet.FilterByPlatform(prods, platformIDs)

	// If no results with full name, try without the group name in parentheses
	if len(filtered) == 0 {
		nameWithoutGroup := stripParenContent(cleanName)
		if nameWithoutGroup != cleanName && nameWithoutGroup != "" {
			slog.Debug("retrying Pouet search without group name", "query", nameWithoutGroup)
			prods, err = s.PouetClient.SearchProd(nameWithoutGroup)
			if err != nil {
				return fmt.Errorf("pouet search retry: %w", err)
			}
			filtered = pouet.FilterByPlatform(prods, platformIDs)
		}
	}

	if len(filtered) == 0 {
		slog.Debug("no Pouet results", "game", game.Title)
		return nil
	}

	// Pick the best match using name similarity
	best := bestPouetMatch(cleanName, filtered)
	if best == nil {
		return nil
	}

	slog.Info("pouet match found", "game", game.Title, "match", best.Name, "id", best.ID)

	// Fetch full production details (search results don't include screenshots)
	fullProd, err := s.PouetClient.GetProd(best.ID)
	if err != nil {
		slog.Warn("failed to fetch full Pouet prod", "id", best.ID, "error", err)
		fullProd = best // Fall back to search result data
	}

	// Apply metadata
	s.applyPouetMetadata(game, fullProd, console, gameIDStr)

	return nil
}

// applyPouetMetadata maps Pouet production data to Game model fields.
func (s *Scraper) applyPouetMetadata(game *db.Game, prod *pouet.Prod, console db.Console, gameIDStr string) {
	game.ScraperID = "pouet:" + prod.ID

	// Title: use the Pouet name if available
	if prod.Name != "" {
		game.Title = prod.Name
	}

	// Developer = group names
	if groups := pouet.GroupNames(prod); groups != "" {
		game.Developer = groups
	}

	// Genre = production type
	game.Genre = pouet.TypesString(prod)

	// Party info
	if info := pouet.PartyInfo(prod); info != "" {
		game.PartyInfo = info
	}

	// Description
	game.Description = buildDemoDescription(prod)

	// Release date
	if prod.ReleaseDate != "" && len(prod.ReleaseDate) >= 4 {
		game.ReleaseDate = prod.ReleaseDate
	}

	// Rating
	if rating := pouet.Rating(prod); rating > 0 {
		game.Rating = rating
	}

	// Download screenshot
	if prod.Screenshot != "" {
		subpath := fmt.Sprintf("%s/%s/cover.jpg", console.Abbreviation, gameIDStr)
		if path := s.DownloadExternalImage(prod.Screenshot, subpath); path != "" {
			game.CoverURL = path
			game.IGDBCoverURL = path
		}
	}

	game.ScrapeAttempts++

	if err := s.DB.Save(game).Error; err != nil {
		slog.Warn("failed to save Pouet metadata", "game", game.Title, "error", err)
	}
}

// ScrapeGameWithPouetMatch applies metadata from a specific Pouet production ID.
func (s *Scraper) ScrapeGameWithPouetMatch(game *db.Game, pouetID string) error {
	prod, err := s.PouetClient.GetProd(pouetID)
	if err != nil {
		return fmt.Errorf("fetching Pouet prod %s: %w", pouetID, err)
	}

	var console db.Console
	if game.Console.ID != 0 {
		console = game.Console
	} else {
		if err := s.DB.First(&console, game.ConsoleID).Error; err != nil {
			return fmt.Errorf("loading console: %w", err)
		}
	}

	gameIDStr := fmt.Sprintf("%d", game.ID)
	s.applyPouetMetadata(game, prod, console, gameIDStr)
	return nil
}

// bestPouetMatch picks the best matching production from search results.
func bestPouetMatch(query string, prods []pouet.Prod) *pouet.Prod {
	if len(prods) == 0 {
		return nil
	}

	normalizedQuery := normalizeDemoName(query)
	var bestProd *pouet.Prod
	var bestScore float64

	for i := range prods {
		normalizedName := normalizeDemoName(prods[i].Name)
		score := jaroWinkler(normalizedQuery, normalizedName)

		// Bonus for exact match
		if strings.EqualFold(normalizedQuery, normalizedName) {
			score = 1.0
		}

		if score > bestScore {
			bestScore = score
			bestProd = &prods[i]
		}
	}

	// Require a minimum similarity threshold
	if bestScore < 0.75 {
		slog.Debug("no Pouet match above threshold", "query", query, "bestScore", bestScore)
		return nil
	}

	return bestProd
}

// buildDemoDescription creates a human-readable description from Pouet data.
func buildDemoDescription(prod *pouet.Prod) string {
	var parts []string

	typeStr := pouet.TypesString(prod)
	groups := pouet.GroupNames(prod)

	if groups != "" {
		parts = append(parts, fmt.Sprintf("%s by %s", capitalize(typeStr), groups))
	} else {
		parts = append(parts, capitalize(typeStr))
	}

	if info := pouet.PartyInfo(prod); info != "" {
		parts = append(parts, "Released at "+info)
	} else if prod.ReleaseDate != "" && len(prod.ReleaseDate) >= 4 {
		parts = append(parts, "Released "+prod.ReleaseDate[:4])
	}

	return strings.Join(parts, ". ") + "."
}

// normalizeDemoName normalizes a demo name for comparison.
func normalizeDemoName(name string) string {
	name = strings.ToLower(name)
	// Strip content in parentheses (group names)
	name = stripParenContent(name)
	name = strings.TrimSpace(name)
	return name
}

// stripParenContent removes all parenthesized content from a string.
func stripParenContent(s string) string {
	result := s
	for {
		start := strings.Index(result, "(")
		if start < 0 {
			break
		}
		end := strings.Index(result[start:], ")")
		if end < 0 {
			break
		}
		result = result[:start] + result[start+end+1:]
	}
	return strings.TrimSpace(result)
}

func capitalize(s string) string {
	if s == "" {
		return s
	}
	return strings.ToUpper(s[:1]) + s[1:]
}
