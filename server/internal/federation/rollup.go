package federation

import (
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// gameStatKey derives the cross-server identity for a game: the IGDB scraper id,
// else CRC32. Games sharing a key across servers aggregate together. Returns
// ok=false when the game has no reliable cross-identifier — such games are NOT
// federated (a title fallback would falsely merge distinct games, both locally
// and across servers).
func gameStatKey(g db.Game) (string, bool) {
	if g.ScraperID != "" {
		return g.ScraperID, true
	}
	if g.CRC32 != "" {
		return "crc:" + g.CRC32, true
	}
	return "", false
}

// publicActiveUserFilter restricts an aggregate to users who have consented to
// public visibility AND are active accounts. This is the origin-side privacy
// gate: private, disabled, and pending-approval users never enter the mesh — for
// BOTH metrics. (Game totals therefore reflect public, active users only in
// Phase 1; full totals protected by k-anonymity are a Phase 2 concern.)
const publicActiveUserFilter = "users.profile_visibility = ? AND users.disabled = ? AND users.pending_approval = ?"

// BuildLocalRollup computes this server's own aggregate stats as source-stamped
// StatEntry values (origin = selfFingerprint, hop 0). Both metrics are gated by
// publicActiveUserFilter so private/disabled/pending users never federate.
func BuildLocalRollup(database *gorm.DB, selfFingerprint string) ([]StatEntry, error) {
	entries := []StatEntry{}

	// --- game_play: play time + distinct players per game (public users only) ---
	type gameRow struct {
		GameID   uint
		Players  int64
		PlayTime int64
	}
	var grows []gameRow
	if err := database.Model(&db.PlayHistory{}).
		Joins("JOIN users ON users.id = play_histories.user_id").
		Where(publicActiveUserFilter, "public", false, false).
		Select("play_histories.game_id as game_id, COUNT(DISTINCT play_histories.user_id) as players, SUM(play_histories.play_time) as play_time").
		Group("play_histories.game_id").
		Scan(&grows).Error; err != nil {
		return nil, err
	}
	if len(grows) > 0 {
		ids := make([]uint, len(grows))
		for i, r := range grows {
			ids[i] = r.GameID
		}
		var games []db.Game
		if err := database.Where("id IN ?", ids).Find(&games).Error; err != nil {
			return nil, err
		}
		gameByID := make(map[uint]db.Game, len(games))
		for _, g := range games {
			gameByID[g.ID] = g
		}
		// Sum by cross-server key (multiple local game rows can map to one key).
		type agg struct {
			label    string
			players  int64
			playTime int64
		}
		byKey := map[string]*agg{}
		order := []string{}
		for _, r := range grows {
			g, ok := gameByID[r.GameID]
			if !ok {
				continue
			}
			key, hasKey := gameStatKey(g)
			if !hasKey {
				continue // can't cross-identify this game — don't federate it
			}
			a, ok := byKey[key]
			if !ok {
				a = &agg{label: g.Title}
				byKey[key] = a
				order = append(order, key)
			}
			a.players += r.Players
			a.playTime += r.PlayTime
		}
		for _, key := range order {
			a := byKey[key]
			entries = append(entries, StatEntry{
				OriginFingerprint: selfFingerprint, Hops: 0, Metric: MetricGamePlay,
				Key: key, Label: a.label, PlayTimeSeconds: a.playTime, Players: a.players,
			})
		}
	}

	// --- player_play: play time per player, public profiles only ---
	type playerRow struct {
		Username string
		PlayTime int64
	}
	var prows []playerRow
	if err := database.Model(&db.PlayHistory{}).
		Select("users.username as username, SUM(play_histories.play_time) as play_time").
		Joins("JOIN users ON users.id = play_histories.user_id").
		Where(publicActiveUserFilter, "public", false, false).
		Group("play_histories.user_id").
		Scan(&prows).Error; err != nil {
		return nil, err
	}
	for _, r := range prows {
		entries = append(entries, StatEntry{
			OriginFingerprint: selfFingerprint, Hops: 0, Metric: MetricPlayerPlay,
			Key: r.Username, Label: r.Username, PlayTimeSeconds: r.PlayTime,
		})
	}

	return entries, nil
}
