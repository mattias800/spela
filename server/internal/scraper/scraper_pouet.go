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

	// Release date — sanitize invalid Pouet dates like "1989-00-15"
	if prod.ReleaseDate != "" && len(prod.ReleaseDate) >= 4 {
		game.ReleaseDate = sanitizePouetDate(prod.ReleaseDate)
	}

	// Rating
	if rating := pouet.Rating(prod); rating > 0 {
		game.IGDBCriticsRating = rating
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

// backfillDemoMisscrapeFlag is the [db.ServerSetting] key used to record
// that [Scraper.BackfillDemoConsoleMisscrapes] has run at least once on
// this database. The backfill is idempotent over its query (only acts on
// rows that still have igdb:* on a demo console), but we still gate it on
// the flag so the work doesn't repeat on every startup once cleared.
const backfillDemoMisscrapeFlag = "backfill_demo_igdb_misscrape_v1"

// BackfillDemoConsoleMisscrapes clears IGDB-sourced metadata from demo-
// console games (ADEMO / DDEMO) that have an `igdb:*` scraper ID, then
// enqueues them for a fresh Pouet-routed scrape.
//
// Why: the DemoConsoles → Pouet routing in ScrapeGame was added after
// some libraries had already been scraped through the IGDB path. The
// fuzzy-match path in IGDB matches demo filenames (e.g. "Batmanpower
// (The Goonies) [1989].adf") to real games (Ocean's Batman), and once
// those bogus matches are saved the routing rule alone won't undo them
// — re-scrape only runs on user-initiated rescrape or scanner-detected
// file changes, neither of which fire on a stable library.
//
// One-shot via [backfillDemoMisscrapeFlag]: once the flag is set, the
// backfill is a no-op. This avoids re-correcting an admin's deliberate
// IGDB-to-demo match (e.g. linking a demo to a base game) after server
// restarts. Admins who want to re-run the backfill can delete the
// ServerSetting row.
func (s *Scraper) BackfillDemoConsoleMisscrapes() error {
	var setting db.ServerSetting
	if err := s.DB.Where("key = ?", backfillDemoMisscrapeFlag).First(&setting).Error; err == nil {
		// Already run on this DB.
		return nil
	}

	demoAbbrevs := make([]string, 0, len(DemoConsoles))
	for abbr := range DemoConsoles {
		demoAbbrevs = append(demoAbbrevs, abbr)
	}

	var games []db.Game
	if err := s.DB.
		Joins("JOIN consoles ON consoles.id = games.console_id").
		Where("consoles.abbreviation IN ?", demoAbbrevs).
		Where("games.scraper_id LIKE 'igdb:%'").
		Find(&games).Error; err != nil {
		return fmt.Errorf("querying demo-console misscrapes: %w", err)
	}

	if len(games) == 0 {
		// Set the flag so we don't repeat the query on every startup.
		s.DB.Create(&db.ServerSetting{Key: backfillDemoMisscrapeFlag, Value: "done"})
		return nil
	}

	slog.Info("backfilling demo-console misscrapes", "count", len(games))

	ids := make([]uint, 0, len(games))
	for _, g := range games {
		ids = append(ids, g.ID)
	}

	// Clear the IGDB-sourced fields. Mirror the field set that
	// ScrapeGame's re-scrape path clears, so the next scrape starts
	// from a clean slate. Title is intentionally left as-is — a
	// re-scrape with a successful Pouet match will overwrite it, and
	// for demos with filenames too generic for Pouet to match
	// (e.g. "BatmanVuelve_1.adf") the existing Title is at least a
	// hint of the original even if it came from a wrong IGDB match.
	if err := s.DB.Model(&db.Game{}).
		Where("id IN ?", ids).
		Updates(map[string]interface{}{
			"scraper_id":              "",
			"cover_url":               "",
			"screenshot_url":          "",
			"libretro_cover_url":      "",
			"igdb_cover_url":          "",
			"description":             "",
			"storyline":               "",
			"developer":               "",
			"publisher":               "",
			"genre":                   "",
			"game_modes":              "",
			"players":                 0,
			"total_rating":            0,
			"total_rating_count":      0,
			"igdb_user_rating":        0,
			"igdb_user_rating_count":  0,
			"time_to_beat_hastily":    0,
			"time_to_beat_normally":   0,
			"time_to_beat_completely": 0,
			"scrape_attempts":         0,
		}).Error; err != nil {
		return fmt.Errorf("clearing demo-console misscrape metadata: %w", err)
	}

	// Drop normalised screenshot + release-date rows for these games
	// — same as ScrapeGame's re-scrape branch does inline.
	s.DB.Where("game_id IN ?", ids).Delete(&db.GameScreenshot{})
	s.DB.Where("game_id IN ?", ids).Delete(&db.GameReleaseDate{})

	// Enqueue for a fresh Pouet scrape. Low priority so user-initiated
	// scrapes still jump the queue.
	if s.Queue != nil {
		if err := s.Queue.EnqueueGames(0, ids, 10); err != nil {
			slog.Warn("failed to enqueue demo-console misscrapes for re-scrape",
				"count", len(ids), "error", err)
		}
	}

	// Mark done so the next startup is a no-op.
	if err := s.DB.Create(&db.ServerSetting{
		Key:   backfillDemoMisscrapeFlag,
		Value: "done",
	}).Error; err != nil {
		slog.Warn("failed to record demo-misscrape backfill flag", "error", err)
	}

	return nil
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

// sanitizePouetDate fixes invalid Pouet dates like "1989-00-15" where month
// or day is "00". Replaces "00" with "01" to produce a valid ISO date.
// Returns just the year if the date can't be salvaged.
func sanitizePouetDate(d string) string {
	if len(d) < 4 {
		return d
	}
	// Just a year
	if len(d) == 4 {
		return d
	}
	// Format: YYYY-MM-DD — fix zero month/day
	parts := strings.Split(d, "-")
	if len(parts) >= 2 && parts[1] == "00" {
		parts[1] = "01"
	}
	if len(parts) >= 3 && parts[2] == "00" {
		parts[2] = "01"
	}
	return strings.Join(parts, "-")
}

func capitalize(s string) string {
	if s == "" {
		return s
	}
	return strings.ToUpper(s[:1]) + s[1:]
}
