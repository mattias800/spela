package scanner

import (
	"fmt"
	"log/slog"
	"sort"
	"strings"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// RecomputeGroupKeys recomputes every game's group_key from its filename.
// This ensures group keys are always derived from source data, not from
// potentially corrupted state. Safe to run at any time — idempotent.
// GroupingProgress is called during long-running grouping operations
// to report progress. The message describes the current phase, and
// current/total indicate progress within that phase (0/0 if unknown).
type GroupingProgress func(message string, current, total int)

func RecomputeGroupKeys(database *gorm.DB, onProgress GroupingProgress) error {
	const batchSize = 500
	var offset int
	updated := 0

	// Count total games for progress reporting
	var totalGames int64
	database.Model(&db.Game{}).Where("deleted_at IS NULL").Count(&totalGames)

	for {
		var games []db.Game
		if err := database.Select("id, file_name, group_key").
			Where("deleted_at IS NULL").
			Order("id ASC").
			Limit(batchSize).Offset(offset).
			Find(&games).Error; err != nil {
			return fmt.Errorf("loading games for group key recompute: %w", err)
		}
		if len(games) == 0 {
			break
		}

		// Batch updates in a single transaction per batch
		err := database.Transaction(func(tx *gorm.DB) error {
			for i := range games {
				newKey := normalizeGroupKey(games[i].FileName)
				if newKey != games[i].GroupKey {
					if err := tx.Model(&games[i]).Update("group_key", newKey).Error; err != nil {
						return err
					}
					updated++
				}
			}
			return nil
		})
		if err != nil {
			return fmt.Errorf("updating group keys: %w", err)
		}

		offset += batchSize
		if onProgress != nil {
			processed := offset
			if processed > int(totalGames) {
				processed = int(totalGames)
			}
			onProgress("Recomputing group keys...", processed, int(totalGames))
		}
	}

	if updated > 0 {
		slog.Info("recomputed group keys", "updated", updated)
	}
	return nil
}

// GroupAndElectPrimaries groups games by (console_id, group_key) and elects
// the best variant in each group as primary.
// Processes one console at a time to bound memory usage for large libraries.
func GroupAndElectPrimaries(database *gorm.DB) error {
	return groupAndElectWithComparator(database, func(a, b db.Game) bool {
		return betterVariant(a, b)
	})
}

// groupAndElectWithComparator is the shared implementation for primary election.
// It processes games one console at a time and uses the given comparator for sorting.
// Uses batched transactions (50 groups per tx) to avoid huge transactions with SQLite.
func groupAndElectWithComparator(database *gorm.DB, less func(a, b db.Game) bool) error {
	// Get distinct console IDs that have grouped games
	var consoleIDs []uint
	if err := database.Model(&db.Game{}).
		Where("group_key != ''").
		Distinct("console_id").
		Pluck("console_id", &consoleIDs).Error; err != nil {
		return fmt.Errorf("loading console IDs for grouping: %w", err)
	}

	totalGroups := 0
	const batchSize = 50 // groups per transaction

	// Process one console at a time to bound memory
	for _, cid := range consoleIDs {
		var games []db.Game
		if err := database.Where("console_id = ? AND group_key != ''", cid).Find(&games).Error; err != nil {
			return fmt.Errorf("loading games for console %d: %w", cid, err)
		}

		// Group by group_key within this console
		groups := make(map[string][]db.Game)
		for _, g := range games {
			groups[g.GroupKey] = append(groups[g.GroupKey], g)
		}

		// Collect group keys for deterministic ordering
		groupKeys := make([]string, 0, len(groups))
		for gk := range groups {
			groupKeys = append(groupKeys, gk)
		}
		sort.Strings(groupKeys)

		// Elect primaries: collect all primary IDs first, then batch update
		primaryIDs := make([]uint, 0, len(groups))
		groupPrimaryMap := make(map[string]uint, len(groups)) // groupKey -> primaryID

		for _, gk := range groupKeys {
			gGames := groups[gk]
			sort.SliceStable(gGames, func(i, j int) bool {
				return less(gGames[i], gGames[j])
			})
			primaryIDs = append(primaryIDs, gGames[0].ID)
			groupPrimaryMap[gk] = gGames[0].ID
		}

		// Process in batches of batchSize groups per transaction
		for batchStart := 0; batchStart < len(groupKeys); batchStart += batchSize {
			batchEnd := batchStart + batchSize
			if batchEnd > len(groupKeys) {
				batchEnd = len(groupKeys)
			}
			batchKeys := groupKeys[batchStart:batchEnd]

			err := database.Transaction(func(tx *gorm.DB) error {
				for _, gk := range batchKeys {
					pid := groupPrimaryMap[gk]

					if err := tx.Model(&db.Game{}).
						Where("console_id = ? AND group_key = ?", cid, gk).
						Updates(map[string]interface{}{
							"is_primary":      false,
							"primary_game_id": pid,
						}).Error; err != nil {
						slog.Warn("failed to clear primary flags",
							"consoleId", cid, "groupKey", gk, "error", err)
						continue
					}

					if err := tx.Model(&db.Game{}).
						Where("id = ?", pid).
						Updates(map[string]interface{}{
							"is_primary":      true,
							"primary_game_id": nil,
						}).Error; err != nil {
						slog.Warn("failed to set primary",
							"consoleId", cid, "groupKey", gk, "error", err)
						continue
					}
				}
				return nil
			})
			if err != nil {
				return fmt.Errorf("primary election for console %d batch %d: %w", cid, batchStart, err)
			}
		}

		totalGroups += len(groups)
		slog.Info("primary election progress", "consoleId", cid, "groups", len(groups), "games", len(games))
	}

	// Handle games with empty group key: each is its own primary
	if err := database.Model(&db.Game{}).
		Where("group_key = '' OR group_key IS NULL").
		Updates(map[string]interface{}{
			"is_primary":      true,
			"primary_game_id": nil,
		}).Error; err != nil {
		return fmt.Errorf("setting empty-groupkey games as primary: %w", err)
	}

	slog.Info("primary variant election complete", "groups", totalGroups)
	return nil
}

// electPrimaryForGroup elects the best variant in a single group and updates the DB.
// All updates are wrapped in a transaction so there is no window where no game is primary.
func electPrimaryForGroup(database *gorm.DB, consoleID uint, groupKey string) error {
	var games []db.Game
	if err := database.Where("console_id = ? AND group_key = ?", consoleID, groupKey).
		Find(&games).Error; err != nil {
		return fmt.Errorf("loading group games: %w", err)
	}

	if len(games) == 0 {
		return nil
	}

	// Sort by election priority (stable sort + ID tiebreaker for determinism)
	sort.SliceStable(games, func(i, j int) bool {
		return betterVariant(games[i], games[j])
	})

	primary := games[0]

	return database.Transaction(func(tx *gorm.DB) error {
		// Batch update: set all games in group to non-primary
		if err := tx.Model(&db.Game{}).
			Where("console_id = ? AND group_key = ?", consoleID, groupKey).
			Updates(map[string]interface{}{
				"is_primary":      false,
				"primary_game_id": primary.ID,
			}).Error; err != nil {
			return fmt.Errorf("clearing primary flags: %w", err)
		}

		// Set the winner as primary
		if err := tx.Model(&db.Game{}).
			Where("id = ?", primary.ID).
			Updates(map[string]interface{}{
				"is_primary":      true,
				"primary_game_id": nil,
			}).Error; err != nil {
			return fmt.Errorf("setting primary: %w", err)
		}

		return nil
	})
}

// betterVariant returns true if game a is a better variant than game b.
// Election priority (highest priority first):
// 1. Not pre-release (IsPreRelease = false beats true)
// 2. Not a hack (non-hack beats hack-tagged games)
// 3. Region preference: USA > World > Europe > other
// 4. Latest revision (Rev B > Rev A > no revision; v1.1 > v1.0)
// 5. Has metadata (non-empty Description or CoverURL)
// 6. CRC verified (VerificationStatus = "verified")
// 7. Shortest FileName
func betterVariant(a, b db.Game) bool {
	// 1. Not pre-release beats pre-release
	if a.IsPreRelease != b.IsPreRelease {
		return !a.IsPreRelease
	}

	// 2. Non-hack beats hack (deprioritize ROM hacks in primary election)
	aIsHack := isHackTagged(a.Tags)
	bIsHack := isHackTagged(b.Tags)
	if aIsHack != bIsHack {
		return !aIsHack
	}

	// 3. Region preference
	aRegion := regionPriority(a.Region)
	bRegion := regionPriority(b.Region)
	if aRegion != bRegion {
		return aRegion < bRegion // lower = better
	}

	// 3. Latest revision
	aRev := revisionOrder(a.Revision)
	bRev := revisionOrder(b.Revision)
	if aRev != bRev {
		return aRev > bRev // higher = later revision = better
	}

	// 4. Has metadata
	aHasMeta := a.Description != "" || a.CoverURL != ""
	bHasMeta := b.Description != "" || b.CoverURL != ""
	if aHasMeta != bHasMeta {
		return aHasMeta
	}

	// 5. CRC verified
	aVerified := a.VerificationStatus == "verified"
	bVerified := b.VerificationStatus == "verified"
	if aVerified != bVerified {
		return aVerified
	}

	// 6. Shortest filename
	if len(a.FileName) != len(b.FileName) {
		return len(a.FileName) < len(b.FileName)
	}

	// 7. Lower ID wins (deterministic tiebreaker)
	return a.ID < b.ID
}

// isHackTagged returns true if the comma-separated tags string contains "hack".
func isHackTagged(tags string) bool {
	for _, t := range strings.Split(tags, ",") {
		if strings.TrimSpace(t) == "hack" {
			return true
		}
	}
	return false
}

// defaultRegionOrder is the default region preference for primary election.
var defaultRegionOrder = []string{"usa", "world", "europe"}

// regionPriority returns a priority value for a region string.
// Lower is better. USA=0, World=1, Europe=2, other=3.
func regionPriority(region string) int {
	return regionPriorityWithOrder(region, defaultRegionOrder)
}

// regionPriorityWithOrder returns a priority value for a region string using
// a custom region order. Lower is better. Regions not in the order list get
// the lowest priority (len(order)).
func regionPriorityWithOrder(region string, order []string) int {
	lower := strings.ToLower(region)
	for i, r := range order {
		if strings.Contains(lower, strings.ToLower(r)) {
			return i
		}
	}
	return len(order)
}

// GroupAndElectPrimariesWithRegions groups games by (console_id, group_key) and
// elects the best variant in each group as primary, using a custom region preference order.
// If regionOrder is nil or empty, the default order (USA > World > Europe) is used.
func GroupAndElectPrimariesWithRegions(database *gorm.DB, regionOrder []string) error {
	if len(regionOrder) == 0 {
		return GroupAndElectPrimaries(database)
	}
	return groupAndElectWithComparator(database, func(a, b db.Game) bool {
		return betterVariantWithRegions(a, b, regionOrder)
	})
}

// betterVariantWithRegions is like betterVariant but uses a custom region order.
func betterVariantWithRegions(a, b db.Game, regionOrder []string) bool {
	if a.IsPreRelease != b.IsPreRelease {
		return !a.IsPreRelease
	}
	aIsHack := isHackTagged(a.Tags)
	bIsHack := isHackTagged(b.Tags)
	if aIsHack != bIsHack {
		return !aIsHack
	}
	aRegion := regionPriorityWithOrder(a.Region, regionOrder)
	bRegion := regionPriorityWithOrder(b.Region, regionOrder)
	if aRegion != bRegion {
		return aRegion < bRegion
	}
	aRev := revisionOrder(a.Revision)
	bRev := revisionOrder(b.Revision)
	if aRev != bRev {
		return aRev > bRev
	}
	aHasMeta := a.Description != "" || a.CoverURL != ""
	bHasMeta := b.Description != "" || b.CoverURL != ""
	if aHasMeta != bHasMeta {
		return aHasMeta
	}
	aVerified := a.VerificationStatus == "verified"
	bVerified := b.VerificationStatus == "verified"
	if aVerified != bVerified {
		return aVerified
	}
	if len(a.FileName) != len(b.FileName) {
		return len(a.FileName) < len(b.FileName)
	}
	return a.ID < b.ID
}

// revisionOrder returns a numeric order for revision strings.
// Higher is later/better. No revision = 0, Rev A or Rev 1 = ordinal value.
func revisionOrder(revision string) int {
	if revision == "" {
		return 0
	}

	lower := strings.ToLower(revision)

	// Handle "v1.0", "v1.1", "v2.0" etc.
	if strings.HasPrefix(lower, "v") {
		// Parse version: v1.0 -> 100, v1.1 -> 110, v2.0 -> 200
		parts := strings.SplitN(lower[1:], ".", 2)
		major := 0
		minor := 0
		if len(parts) >= 1 {
			for _, c := range parts[0] {
				if c >= '0' && c <= '9' {
					major = major*10 + int(c-'0')
				}
			}
		}
		if len(parts) >= 2 {
			for _, c := range parts[1] {
				if c >= '0' && c <= '9' {
					minor = minor*10 + int(c-'0')
				}
			}
		}
		return major*100 + minor
	}

	// Handle "Rev A", "Rev B", "Rev 1", "Rev 2"
	if strings.HasPrefix(lower, "rev ") {
		rest := strings.TrimSpace(lower[4:])
		if len(rest) == 0 {
			return 1
		}
		// Single letter: A=1, B=2, etc.
		if len(rest) == 1 && rest[0] >= 'a' && rest[0] <= 'z' {
			return int(rest[0]-'a') + 1
		}
		// Numeric: 1, 2, etc.
		val := 0
		for _, c := range rest {
			if c >= '0' && c <= '9' {
				val = val*10 + int(c-'0')
			}
		}
		if val > 0 {
			return val
		}
		return 1
	}

	return 1
}

// ReElectPrimaryForGroup re-runs election for a specific (consoleID, groupKey) group.
// Used when a primary game is removed.
func ReElectPrimaryForGroup(database *gorm.DB, consoleID uint, groupKey string) error {
	if groupKey == "" {
		return nil
	}
	return electPrimaryForGroup(database, consoleID, groupKey)
}

// igdbMergeExcludedConsoles lists console abbreviations where IGDB-based
// group merging is skipped. Demoscene platforms don't have regional releases
// and their short/generic titles cause false IGDB matches that lead to
// incorrect metadata propagation.
var igdbMergeExcludedConsoles = []string{
	"ADEMO", // Amiga demos
	"DDEMO", // DOS demos
}

// MergeGroupsByIGDBID finds games that share the same IGDB game ID on the
// same console but have different GroupKeys (e.g., "Sonic CD" USA and
// "Sonic the Hedgehog CD" Japan). It merges them into a single group by
// assigning the same GroupKey, then re-elects primaries for affected groups.
//
// This should be called after scraping, when ScraperID is populated.
func MergeGroupsByIGDBID(database *gorm.DB) (int, error) {
	// Look up console IDs to exclude
	var excludedConsoleIDs []uint
	if len(igdbMergeExcludedConsoles) > 0 {
		database.Model(&db.Console{}).
			Where("abbreviation IN ?", igdbMergeExcludedConsoles).
			Pluck("id", &excludedConsoleIDs)
	}

	// Find IGDB IDs that appear on 2+ games with different GroupKeys (same console)
	type mergeCandidate struct {
		ConsoleID uint
		ScraperID string
		GroupKeys int
	}
	var candidates []mergeCandidate
	q := database.Model(&db.Game{}).
		Select("console_id, scraper_id, COUNT(DISTINCT group_key) as group_keys").
		Where("scraper_id != '' AND group_key != '' AND deleted_at IS NULL")
	if len(excludedConsoleIDs) > 0 {
		q = q.Where("console_id NOT IN ?", excludedConsoleIDs)
	}
	if err := q.Group("console_id, scraper_id").
		Having("group_keys > 1").
		Scan(&candidates).Error; err != nil {
		return 0, fmt.Errorf("finding IGDB merge candidates: %w", err)
	}

	if len(candidates) == 0 {
		return 0, nil
	}

	mergedCount := 0
	for _, c := range candidates {
		// Get all games with this IGDB ID on this console
		var games []db.Game
		if err := database.
			Where("console_id = ? AND scraper_id = ? AND deleted_at IS NULL", c.ConsoleID, c.ScraperID).
			Order("group_key ASC").
			Find(&games).Error; err != nil {
			slog.Warn("failed to load games for IGDB merge", "scraperID", c.ScraperID, "error", err)
			continue
		}

		if len(games) < 2 {
			continue
		}

		// Safety check: verify titles are similar enough to merge.
		// This prevents false merges when IGDB returns the same match
		// for unrelated games (e.g., DOS games with similar names).
		if !titlesRelated(games) {
			slog.Debug("skipping IGDB merge: titles not related",
				"scraperID", c.ScraperID,
				"titles", fmt.Sprintf("%v", gameTitles(games)))
			continue
		}

		// Pick the best group key: prefer one that doesn't contain games
		// with different IGDB IDs (avoids merging into corrupted catch-all groups).
		canonicalGroupKey := pickCanonicalGroupKey(database, games, c.ScraperID)
		affectedGroupKeys := make(map[string]bool)

		for _, g := range games {
			if g.GroupKey != canonicalGroupKey {
				oldKey := g.GroupKey
				affectedGroupKeys[oldKey] = true
				// Update this game's GroupKey to the canonical one
				if err := database.Model(&g).Update("group_key", canonicalGroupKey).Error; err != nil {
					slog.Warn("failed to update group key for IGDB merge",
						"gameID", g.ID, "old", oldKey, "new", canonicalGroupKey, "error", err)
					continue
				}
				mergedCount++
				slog.Info("merged game into IGDB group",
					"game", g.Title, "oldGroupKey", oldKey,
					"newGroupKey", canonicalGroupKey, "scraperID", c.ScraperID)
			}
		}

		// Re-elect primary for the merged group
		if err := electPrimaryForGroup(database, c.ConsoleID, canonicalGroupKey); err != nil {
			slog.Warn("failed to re-elect primary after IGDB merge",
				"consoleID", c.ConsoleID, "groupKey", canonicalGroupKey, "error", err)
		}

		// Re-elect primaries for old groups (may still contain other games)
		for oldKey := range affectedGroupKeys {
			if err := electPrimaryForGroup(database, c.ConsoleID, oldKey); err != nil {
				slog.Warn("failed to re-elect primary for vacated group",
					"consoleID", c.ConsoleID, "groupKey", oldKey, "error", err)
			}
		}
	}

	if mergedCount > 0 {
		slog.Info("IGDB group merge complete", "merged", mergedCount, "candidates", len(candidates))
	}
	return mergedCount, nil
}

// pickCanonicalGroupKey selects the best group key for merging. It prefers
// a group key whose existing members all share the same IGDB ID (a "pure"
// group). This avoids merging into corrupted catch-all groups that contain
// hundreds of unrelated games. Falls back to alphabetically first if all
// groups are equally pure (or equally impure).
func pickCanonicalGroupKey(database *gorm.DB, games []db.Game, scraperID string) string {
	// Collect unique group keys
	groupKeys := make([]string, 0)
	seen := make(map[string]bool)
	for _, g := range games {
		if !seen[g.GroupKey] {
			seen[g.GroupKey] = true
			groupKeys = append(groupKeys, g.GroupKey)
		}
	}

	// For each group key, count how many distinct IGDB IDs it contains
	type purity struct {
		key          string
		distinctIDs  int64
	}
	best := purity{key: groupKeys[0], distinctIDs: 999999}

	for _, gk := range groupKeys {
		var count int64
		database.Model(&db.Game{}).
			Where("group_key = ? AND scraper_id != '' AND deleted_at IS NULL", gk).
			Select("COUNT(DISTINCT scraper_id)").
			Scan(&count)

		if count < best.distinctIDs || (count == best.distinctIDs && gk < best.key) {
			best = purity{key: gk, distinctIDs: count}
		}
	}

	return best.key
}

// titlesRelated checks if a set of games have related titles (at least one
// significant word in common). This prevents merging "Alley Cat" with
// "Alley Master" just because IGDB returned the same match.
func titlesRelated(games []db.Game) bool {
	if len(games) < 2 {
		return true
	}

	// Extract significant words (3+ chars) from each title
	wordSets := make([]map[string]bool, len(games))
	for i, g := range games {
		words := make(map[string]bool)
		for _, w := range strings.Fields(strings.ToLower(g.Title)) {
			// Skip short words (a, of, the, etc.)
			if len(w) >= 3 {
				words[w] = true
			}
		}
		wordSets[i] = words
	}

	// Check that every pair shares at least one significant word
	for i := 1; i < len(wordSets); i++ {
		shared := false
		for w := range wordSets[0] {
			if wordSets[i][w] {
				shared = true
				break
			}
		}
		if !shared {
			return false
		}
	}
	return true
}

func gameTitles(games []db.Game) []string {
	titles := make([]string, len(games))
	for i, g := range games {
		titles[i] = g.Title
	}
	return titles
}
