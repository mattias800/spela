package api

import (
	"encoding/json"
	"log/slog"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"gorm.io/gorm"
)

const maxShowcaseEntries = 5

// AchievementShowcaseHandler handles achievement showcase endpoints.
// All HTTP methods are served by huma — see HumaGetShowcase /
// HumaGetPublicShowcase / HumaUpdateShowcase in huma_achievements.go.
// This struct + enrichShowcaseEntries helper are referenced from those
// huma handlers.
type AchievementShowcaseHandler struct {
	DB *gorm.DB
}

// enrichShowcaseEntries loads GameAchievementCache data and enriches showcase entries.
func (h *AchievementShowcaseHandler) enrichShowcaseEntries(entries []db.UserAchievementShowcase) []ShowcaseEntryResponse {
	if len(entries) == 0 {
		return []ShowcaseEntryResponse{}
	}

	// Collect unique RA game IDs
	raGameIDSet := make(map[uint]struct{})
	for _, e := range entries {
		raGameIDSet[e.RAGameID] = struct{}{}
	}
	raGameIDs := make([]uint, 0, len(raGameIDSet))
	for id := range raGameIDSet {
		raGameIDs = append(raGameIDs, id)
	}

	// Load achievement caches for these games
	var caches []db.GameAchievementCache
	h.DB.Where("ra_game_id IN ?", raGameIDs).Find(&caches)

	// Build a map of raGameID -> (gameTitle, achievement map by RA ID)
	type gameData struct {
		Title        string
		Achievements map[uint]retroachievements.Achievement
	}
	cacheMap := make(map[uint]gameData)
	for _, cache := range caches {
		var achievements []retroachievements.Achievement
		if err := json.Unmarshal([]byte(cache.AchievementJSON), &achievements); err != nil {
			slog.Warn("failed to unmarshal cached achievement data for showcase", "ra_game_id", cache.RAGameID, "error", err)
			continue
		}
		achMap := make(map[uint]retroachievements.Achievement, len(achievements))
		for _, a := range achievements {
			achMap[a.ID] = a
		}
		cacheMap[cache.RAGameID] = gameData{
			Title:        cache.Title,
			Achievements: achMap,
		}
	}

	// Build enriched responses
	results := make([]ShowcaseEntryResponse, 0, len(entries))
	for _, e := range entries {
		resp := ShowcaseEntryResponse{
			AchievementRAID: e.AchievementRAID,
			RAGameID:        e.RAGameID,
			ShowcaseOrder:   e.ShowcaseOrder,
		}
		if gd, ok := cacheMap[e.RAGameID]; ok {
			resp.GameTitle = gd.Title
			if ach, found := gd.Achievements[e.AchievementRAID]; found {
				resp.Title = ach.Title
				resp.Description = ach.Description
				resp.Points = ach.Points
				resp.BadgeURL = ach.BadgeURL
				resp.RarityPercent = ach.RarityPercent
			}
		}
		results = append(results, resp)
	}

	return results
}
