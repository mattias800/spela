package api

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"gorm.io/gorm"
)

const maxShowcaseEntries = 5

// AchievementShowcaseHandler handles achievement showcase endpoints.
type AchievementShowcaseHandler struct {
	DB *gorm.DB
}

// showcaseResponse is the enriched response for a single showcased achievement.
type showcaseResponse struct {
	AchievementRAID uint    `json:"achievementRaId"`
	RAGameID        uint    `json:"raGameId"`
	ShowcaseOrder   int     `json:"showcaseOrder"`
	Title           string  `json:"title,omitempty"`
	Description     string  `json:"description,omitempty"`
	Points          int     `json:"points,omitempty"`
	BadgeURL        string  `json:"badgeUrl,omitempty"`
	RarityPercent   float64 `json:"rarityPercent,omitempty"`
	GameTitle       string  `json:"gameTitle,omitempty"`
}

// GetShowcase returns the current user's showcased achievements.
func (h *AchievementShowcaseHandler) GetShowcase(c *gin.Context) {
	uid := getUserID(c)

	var entries []db.UserAchievementShowcase
	if err := h.DB.Where("user_id = ?", uid).Order("showcase_order ASC").Find(&entries).Error; err != nil {
		slog.Error("failed to load achievement showcase", "user_id", uid, "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to load showcase"})
		return
	}

	results := h.enrichShowcaseEntries(entries)
	c.JSON(http.StatusOK, results)
}

// GetPublicShowcase returns a user's showcased achievements by user ID (public).
func (h *AchievementShowcaseHandler) GetPublicShowcase(c *gin.Context) {
	userID, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid user ID"})
		return
	}

	var entries []db.UserAchievementShowcase
	if err := h.DB.Where("user_id = ?", userID).Order("showcase_order ASC").Find(&entries).Error; err != nil {
		slog.Error("failed to load public achievement showcase", "user_id", userID, "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to load showcase"})
		return
	}

	results := h.enrichShowcaseEntries(entries)
	c.JSON(http.StatusOK, results)
}

// UpdateShowcase replaces the current user's showcased achievements.
func (h *AchievementShowcaseHandler) UpdateShowcase(c *gin.Context) {
	uid := getUserID(c)

	var req []ShowcaseEntryInput
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request body"})
		return
	}

	if len(req) > maxShowcaseEntries {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "maximum 5 showcase entries allowed"})
		return
	}

	// Replace all entries in a transaction
	if err := h.DB.Transaction(func(tx *gorm.DB) error {
		// Delete existing entries
		if err := tx.Where("user_id = ?", uid).Delete(&db.UserAchievementShowcase{}).Error; err != nil {
			return err
		}

		// Insert new entries
		for i, item := range req {
			entry := db.UserAchievementShowcase{
				UserID:          uid,
				AchievementRAID: item.AchievementRAID,
				RAGameID:        item.RAGameID,
				ShowcaseOrder:   i,
			}
			if err := tx.Create(&entry).Error; err != nil {
				return err
			}
		}
		return nil
	}); err != nil {
		slog.Error("failed to update achievement showcase", "user_id", uid, "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to update showcase"})
		return
	}

	// Return the updated showcase
	var entries []db.UserAchievementShowcase
	h.DB.Where("user_id = ?", uid).Order("showcase_order ASC").Find(&entries)
	results := h.enrichShowcaseEntries(entries)
	c.JSON(http.StatusOK, results)
}

// enrichShowcaseEntries loads GameAchievementCache data and enriches showcase entries.
func (h *AchievementShowcaseHandler) enrichShowcaseEntries(entries []db.UserAchievementShowcase) []showcaseResponse {
	if len(entries) == 0 {
		return []showcaseResponse{}
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
	results := make([]showcaseResponse, 0, len(entries))
	for _, e := range entries {
		resp := showcaseResponse{
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
