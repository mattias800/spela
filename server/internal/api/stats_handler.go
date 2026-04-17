package api

import (
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// StatsHandler handles community statistics endpoints.
type StatsHandler struct {
	DB *gorm.DB
}

// parseTimeString attempts to parse a time string in common formats returned by
// SQLite aggregate functions (which return strings rather than native time types).
func parseTimeString(s string) time.Time {
	formats := []string{
		time.RFC3339Nano,
		time.RFC3339,
		"2006-01-02 15:04:05.999999999-07:00",
		"2006-01-02 15:04:05+00:00",
		"2006-01-02T15:04:05Z",
		"2006-01-02 15:04:05",
		"2006-01-02",
	}
	for _, f := range formats {
		if t, err := time.Parse(f, s); err == nil {
			return t
		}
	}
	return time.Time{}
}

// RecordDailyPlayActivity upserts a DailyPlayActivity row for today, incrementing
// the play time by the given number of seconds. Uses an atomic upsert to avoid
// race conditions under concurrent requests.
func RecordDailyPlayActivity(database *gorm.DB, userID uint, seconds int64) {
	today := time.Now().UTC().Truncate(24 * time.Hour)
	result := database.Exec(
		`INSERT INTO daily_play_activities (user_id, date, play_time)
		 VALUES (?, ?, ?)
		 ON CONFLICT (user_id, date) DO UPDATE SET play_time = play_time + ?`,
		userID, today, seconds, seconds,
	)
	if result.Error != nil {
		slog.Error("failed to record daily play activity", "error", result.Error, "userId", userID)
	}
}

// GetPlayHeatmap returns the current user's daily play activity for the past 365 days.
// GET /api/user/play-heatmap
func (h *StatsHandler) GetPlayHeatmap(c *gin.Context) {
	uid := getUserID(c)
	h.getHeatmapForUser(c, uid)
}

// GetPublicPlayHeatmap returns a user's daily play activity for the past 365 days.
// GET /api/users/:id/play-heatmap
func (h *StatsHandler) GetPublicPlayHeatmap(c *gin.Context) {
	idParam := c.Param("id")
	id, err := strconv.ParseUint(idParam, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid user id"})
		return
	}

	var user db.User
	if err := h.DB.First(&user, uint(id)).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "user not found"})
		return
	}

	h.getHeatmapForUser(c, uint(id))
}

func (h *StatsHandler) getHeatmapForUser(c *gin.Context, userID uint) {
	since := time.Now().UTC().Truncate(24*time.Hour).AddDate(0, 0, -365)

	var activities []db.DailyPlayActivity
	h.DB.Where("user_id = ? AND date >= ?", userID, since).
		Order("date ASC").
		Find(&activities)

	entries := make([]HeatmapEntry, 0, len(activities))
	for _, a := range activities {
		entries = append(entries, HeatmapEntry{
			Date:     a.Date.Format("2006-01-02"),
			PlayTime: a.PlayTime,
		})
	}

	c.JSON(http.StatusOK, entries)
}
