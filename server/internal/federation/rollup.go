package federation

import (
	"strings"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// gameStatKey derives the cross-server identity for a game: prefer the IGDB
// scraper id, then CRC32, falling back to a normalized title. Games that share a
// key across servers aggregate together.
func gameStatKey(g db.Game) string {
	if g.ScraperID != "" {
		return g.ScraperID
	}
	if g.CRC32 != "" {
		return "crc:" + g.CRC32
	}
	return "title:" + strings.ToLower(strings.TrimSpace(g.Title))
}

// BuildLocalRollup computes this server's own aggregate stats as source-stamped
// StatEntry values (origin = selfFingerprint, hop 0). Game play time + distinct
// players are keyed by cross-server game identity; player play time is included
// only for users whose ProfileVisibility is public — the origin-side consent
// gate, so private users never enter the mesh.
func BuildLocalRollup(database *gorm.DB, selfFingerprint string) ([]StatEntry, error) {
	entries := []StatEntry{}

	// --- game_play: play time + distinct players per game ---
	type gameRow struct {
		GameID   uint
		Players  int64
		PlayTime int64
	}
	var grows []gameRow
	if err := database.Model(&db.PlayHistory{}).
		Select("game_id, COUNT(DISTINCT user_id) as players, SUM(play_time) as play_time").
		Group("game_id").
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
			key := gameStatKey(g)
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
		Where("users.profile_visibility = ?", "public").
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
