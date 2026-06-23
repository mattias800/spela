package federation

import (
	"sort"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// AchievementEntry is one source-stamped "player has unlocked N achievements"
// datum from a single origin server. Hops is the graph distance from the holder
// to the origin (0 = originated here, 1 = a direct connected server).
//
// Like presence (and unlike stats), this is pulled live from direct connected
// servers at read time rather than cached in a snapshot table. Players are
// scoped to their origin — cross-server player identity is a later concern — so
// the same username on two servers is two distinct leaderboard rows.
type AchievementEntry struct {
	OriginFingerprint string `json:"originFingerprint"`
	Hops              int    `json:"hops"`
	Username          string `json:"username"`
	Count             int64  `json:"count"` // achievements unlocked
	// ServerName is a friendly label for the origin server, filled in by the
	// consumer from its peer roster. Empty means this (local) server — it keeps
	// the admin-only peer fingerprint out of the user-facing response.
	ServerName string `json:"serverName"`
}

// achievementDedupeKey identifies a datum by origin + user, so the same row
// reaching us via multiple paths is counted exactly once.
type achievementDedupeKey struct {
	origin   string
	username string
}

// DedupeAchievementEntries removes duplicate (origin, username) entries, keeping
// the FIRST occurrence (diamond-safe, idempotent) — same rationale as
// DedupeStatEntries / DedupePresenceEntries.
func DedupeAchievementEntries(entries []AchievementEntry) []AchievementEntry {
	seen := make(map[achievementDedupeKey]bool, len(entries))
	out := make([]AchievementEntry, 0, len(entries))
	for _, e := range entries {
		k := achievementDedupeKey{e.OriginFingerprint, e.Username}
		if seen[k] {
			continue
		}
		seen[k] = true
		out = append(out, e)
	}
	return out
}

// SortAchievementEntries orders a leaderboard by unlock count descending, then
// by username for a stable tie-break. Returns a new slice.
func SortAchievementEntries(entries []AchievementEntry) []AchievementEntry {
	out := make([]AchievementEntry, len(entries))
	copy(out, entries)
	sort.SliceStable(out, func(i, j int) bool {
		if out[i].Count != out[j].Count {
			return out[i].Count > out[j].Count
		}
		return out[i].Username < out[j].Username
	})
	return out
}

// BuildLocalAchievements computes this server's per-player achievement-unlock
// counts as source-stamped AchievementEntry values (origin = selfFingerprint,
// hop 0). Gated by publicActiveUserFilter so private/disabled/pending users
// never federate.
func BuildLocalAchievements(database *gorm.DB, selfFingerprint string) ([]AchievementEntry, error) {
	type row struct {
		Username string
		Count    int64
	}
	var rows []row
	if err := database.Model(&db.UserAchievementProgress{}).
		Select("users.username as username, COUNT(*) as count").
		Joins("JOIN users ON users.id = user_achievement_progresses.user_id").
		Where(publicActiveUserFilter, "public", false, false).
		Group("user_achievement_progresses.user_id").
		Scan(&rows).Error; err != nil {
		return nil, err
	}
	entries := make([]AchievementEntry, 0, len(rows))
	for _, r := range rows {
		entries = append(entries, AchievementEntry{
			OriginFingerprint: selfFingerprint, Hops: 0,
			Username: r.Username, Count: r.Count,
		})
	}
	return entries, nil
}
