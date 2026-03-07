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

// mostPlayedEntry is a single entry in the most-played games response.
type mostPlayedEntry struct {
	Game         GameResponse `json:"game"`
	TotalPlayers int64        `json:"totalPlayers"`
	TotalPlayTime int64       `json:"totalPlayTime"`
}

// mostPlayedResponse is the response for GET /api/stats/most-played.
type mostPlayedResponse struct {
	Games []mostPlayedEntry `json:"games"`
}

// MostPlayedGames returns the top 25 most played games across all users.
func (h *StatsHandler) MostPlayedGames(c *gin.Context) {
	type row struct {
		GameID        uint
		TotalPlayers  int64
		TotalPlayTime int64
	}
	var rows []row
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("game_id, COUNT(DISTINCT user_id) as total_players, SUM(play_time) as total_play_time").
		Group("game_id").
		Order("total_play_time DESC").
		Limit(25).
		Scan(&rows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch most played games"})
		return
	}

	if len(rows) == 0 {
		c.JSON(http.StatusOK, mostPlayedResponse{Games: []mostPlayedEntry{}})
		return
	}

	// Load full games with console preloaded
	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}
	var games []db.Game
	h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games)

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	userID := getUserID(c)
	data := loadUserGameData(h.DB, userID, gameIDs)

	entries := make([]mostPlayedEntry, 0, len(rows))
	for _, r := range rows {
		game, ok := gameMap[r.GameID]
		if !ok {
			continue
		}
		entries = append(entries, mostPlayedEntry{
			Game:          toGameResponseWithData(game, &data),
			TotalPlayers:  r.TotalPlayers,
			TotalPlayTime: r.TotalPlayTime,
		})
	}

	c.JSON(http.StatusOK, mostPlayedResponse{Games: entries})
}

// activePlayerEntry is a single entry in the most-active players response.
type activePlayerEntry struct {
	UserID        string     `json:"userId"`
	Username      string     `json:"username"`
	AvatarURL     string     `json:"avatarUrl"`
	TotalPlayTime int64      `json:"totalPlayTime"`
	GamesPlayed   int64      `json:"gamesPlayed"`
	LastPlayed    time.Time  `json:"lastPlayed"`
}

// mostActivePlayersResponse is the response for GET /api/stats/most-active-players.
type mostActivePlayersResponse struct {
	Players []activePlayerEntry `json:"players"`
}

// MostActivePlayers returns the top 25 most active players.
func (h *StatsHandler) MostActivePlayers(c *gin.Context) {
	type row struct {
		UserID        uint
		Username      string
		AvatarURL     string
		TotalPlayTime int64
		GamesPlayed   int64
		LastPlayed    string
	}
	var rows []row
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("play_histories.user_id, users.username, users.avatar_url, SUM(play_histories.play_time) as total_play_time, COUNT(DISTINCT play_histories.game_id) as games_played, MAX(play_histories.last_played) as last_played").
		Joins("JOIN users ON users.id = play_histories.user_id").
		Group("play_histories.user_id").
		Order("total_play_time DESC").
		Limit(25).
		Scan(&rows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch most active players"})
		return
	}

	players := make([]activePlayerEntry, 0, len(rows))
	for _, r := range rows {
		lastPlayed := parseTimeString(r.LastPlayed)
		players = append(players, activePlayerEntry{
			UserID:        strconv.FormatUint(uint64(r.UserID), 10),
			Username:      r.Username,
			AvatarURL:     r.AvatarURL,
			TotalPlayTime: r.TotalPlayTime,
			GamesPlayed:   r.GamesPlayed,
			LastPlayed:    lastPlayed,
		})
	}

	c.JSON(http.StatusOK, mostActivePlayersResponse{Players: players})
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

// heatmapEntry is a single day's play activity for the heatmap response.
type heatmapEntry struct {
	Date     string `json:"date"`
	PlayTime int64  `json:"playTime"`
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
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid user id"})
		return
	}

	var user db.User
	if err := h.DB.First(&user, uint(id)).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
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

	entries := make([]heatmapEntry, 0, len(activities))
	for _, a := range activities {
		entries = append(entries, heatmapEntry{
			Date:     a.Date.Format("2006-01-02"),
			PlayTime: a.PlayTime,
		})
	}

	c.JSON(http.StatusOK, entries)
}
