package scanner

import (
	"fmt"
	"log/slog"
	"sort"
	"strings"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// GroupAndElectPrimaries groups games by (console_id, group_key) and elects
// the best variant in each group as primary.
// All operations run inside a single transaction to avoid a window where no
// game is primary.
func GroupAndElectPrimaries(database *gorm.DB) error {
	// Load all games with a non-empty group key in one query
	var allGames []db.Game
	if err := database.Where("group_key != ''").Find(&allGames).Error; err != nil {
		return fmt.Errorf("loading grouped games: %w", err)
	}

	// Group games in memory by (console_id, group_key)
	type groupKey struct {
		ConsoleID uint
		GroupKey  string
	}
	groups := make(map[groupKey][]db.Game)
	for _, g := range allGames {
		k := groupKey{ConsoleID: g.ConsoleID, GroupKey: g.GroupKey}
		groups[k] = append(groups[k], g)
	}

	slog.Info("electing primary variants", "groups", len(groups), "games", len(allGames))

	// Compute primaries in memory, then batch-update in a transaction
	err := database.Transaction(func(tx *gorm.DB) error {
		processed := 0
		for k, games := range groups {
			// Sort by election priority (stable sort + ID tiebreaker for determinism)
			sort.SliceStable(games, func(i, j int) bool {
				return betterVariant(games[i], games[j])
			})

			primary := games[0]

			// Batch update: set all games in group to non-primary
			if err := tx.Model(&db.Game{}).
				Where("console_id = ? AND group_key = ?", k.ConsoleID, k.GroupKey).
				Updates(map[string]interface{}{
					"is_primary":      false,
					"primary_game_id": primary.ID,
				}).Error; err != nil {
				slog.Warn("failed to clear primary flags",
					"consoleId", k.ConsoleID, "groupKey", k.GroupKey, "error", err)
				continue
			}

			// Set the winner as primary
			if err := tx.Model(&db.Game{}).
				Where("id = ?", primary.ID).
				Updates(map[string]interface{}{
					"is_primary":      true,
					"primary_game_id": nil,
				}).Error; err != nil {
				slog.Warn("failed to set primary",
					"consoleId", k.ConsoleID, "groupKey", k.GroupKey, "error", err)
				continue
			}

			processed++
			if processed%500 == 0 {
				slog.Info("primary election progress", "processed", processed, "total", len(groups))
			}
		}

		// Handle games with empty group key: each is its own primary
		if err := tx.Model(&db.Game{}).
			Where("group_key = '' OR group_key IS NULL").
			Updates(map[string]interface{}{
				"is_primary":      true,
				"primary_game_id": nil,
			}).Error; err != nil {
			return fmt.Errorf("setting empty-groupkey games as primary: %w", err)
		}

		return nil
	})
	if err != nil {
		return fmt.Errorf("primary election transaction: %w", err)
	}

	slog.Info("primary variant election complete", "groups", len(groups))
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
// 2. Region preference: USA > World > Europe > other
// 3. Latest revision (Rev B > Rev A > no revision; v1.1 > v1.0)
// 4. Has metadata (non-empty Description or CoverURL)
// 5. CRC verified (VerificationStatus = "verified")
// 6. Shortest FileName
func betterVariant(a, b db.Game) bool {
	// 1. Not pre-release beats pre-release
	if a.IsPreRelease != b.IsPreRelease {
		return !a.IsPreRelease
	}

	// 2. Region preference
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

	var allGames []db.Game
	if err := database.Where("group_key != ''").Find(&allGames).Error; err != nil {
		return fmt.Errorf("loading grouped games: %w", err)
	}

	type groupKeyType struct {
		ConsoleID uint
		GroupKey  string
	}
	groups := make(map[groupKeyType][]db.Game)
	for _, g := range allGames {
		k := groupKeyType{ConsoleID: g.ConsoleID, GroupKey: g.GroupKey}
		groups[k] = append(groups[k], g)
	}

	slog.Info("electing primary variants with custom region order", "groups", len(groups), "regionOrder", regionOrder)

	err := database.Transaction(func(tx *gorm.DB) error {
		for k, games := range groups {
			sort.SliceStable(games, func(i, j int) bool {
				return betterVariantWithRegions(games[i], games[j], regionOrder)
			})

			primary := games[0]

			if err := tx.Model(&db.Game{}).
				Where("console_id = ? AND group_key = ?", k.ConsoleID, k.GroupKey).
				Updates(map[string]interface{}{
					"is_primary":      false,
					"primary_game_id": primary.ID,
				}).Error; err != nil {
				slog.Warn("failed to clear primary flags",
					"consoleId", k.ConsoleID, "groupKey", k.GroupKey, "error", err)
				continue
			}

			if err := tx.Model(&db.Game{}).
				Where("id = ?", primary.ID).
				Updates(map[string]interface{}{
					"is_primary":      true,
					"primary_game_id": nil,
				}).Error; err != nil {
				slog.Warn("failed to set primary",
					"consoleId", k.ConsoleID, "groupKey", k.GroupKey, "error", err)
				continue
			}
		}

		if err := tx.Model(&db.Game{}).
			Where("group_key = '' OR group_key IS NULL").
			Updates(map[string]interface{}{
				"is_primary":      true,
				"primary_game_id": nil,
			}).Error; err != nil {
			return fmt.Errorf("setting empty-groupkey games as primary: %w", err)
		}

		return nil
	})
	if err != nil {
		return fmt.Errorf("primary election transaction: %w", err)
	}

	slog.Info("primary variant election with custom regions complete", "groups", len(groups))
	return nil
}

// betterVariantWithRegions is like betterVariant but uses a custom region order.
func betterVariantWithRegions(a, b db.Game, regionOrder []string) bool {
	if a.IsPreRelease != b.IsPreRelease {
		return !a.IsPreRelease
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
