package api

import (
	"fmt"
	"log/slog"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

// --- Phase 11: Temporal Discovery ---

// OnThisDayResponse is the API response for the on-this-day endpoint.
type OnThisDayResponse struct {
	Date  string         `json:"date"`
	Games []GameResponse `json:"games"`
}

// BestOfYearResponse is the API response for the best-of-year endpoint.
type BestOfYearResponse struct {
	Year  int            `json:"year"`
	Games []GameResponse `json:"games"`
}

// AnniversaryItem represents a game the user played roughly N years ago.
type AnniversaryItem struct {
	Game     GameResponse `json:"game"`
	YearsAgo int         `json:"yearsAgo"`
	PlayedAt time.Time    `json:"playedAt"`
}

// AnniversariesResponse is the API response for the your-anniversaries endpoint.
type AnniversariesResponse struct {
	Anniversaries []AnniversaryItem `json:"anniversaries"`
}

// DecadesResponse is the API response for the decades endpoint.
type DecadesResponse struct {
	Decade string         `json:"decade"`
	Label  string         `json:"label"`
	Games  []GameResponse `json:"games"`
}

// parseReleaseDateMonthDay attempts to extract month and day from a release_date string.
// Supports formats like "2000-03-10", "March 10, 2000", "Mar 10, 2000".
// Returns (month, day, true) on success, or (0, 0, false) if unparseable or year-only.
func parseReleaseDateMonthDay(releaseDate string) (time.Month, int, bool) {
	releaseDate = strings.TrimSpace(releaseDate)
	if releaseDate == "" {
		return 0, 0, false
	}

	// Try ISO format: "2000-03-10" or "2000-3-10"
	if len(releaseDate) >= 10 && releaseDate[4] == '-' {
		t, err := time.Parse("2006-01-02", releaseDate[:10])
		if err == nil {
			return t.Month(), t.Day(), true
		}
	}

	// Try "January 2, 2006" / "Jan 2, 2006" formats
	layouts := []string{
		"January 2, 2006",
		"Jan 2, 2006",
		"January 02, 2006",
		"Jan 02, 2006",
		"2 January 2006",
		"02 January 2006",
	}
	for _, layout := range layouts {
		t, err := time.Parse(layout, releaseDate)
		if err == nil {
			return t.Month(), t.Day(), true
		}
	}

	return 0, 0, false
}

// parseReleaseDateYear attempts to extract the year from a release_date string.
// Supports "2000-03-10", "March 10, 2000", "1996", etc.
func parseReleaseDateYear(releaseDate string) (int, bool) {
	releaseDate = strings.TrimSpace(releaseDate)
	if releaseDate == "" {
		return 0, false
	}

	// Try ISO format first
	if len(releaseDate) >= 4 {
		year, err := strconv.Atoi(releaseDate[:4])
		if err == nil && year >= 1970 && year <= 2100 {
			return year, true
		}
	}

	// Try text formats like "March 10, 2000"
	layouts := []string{
		"January 2, 2006",
		"Jan 2, 2006",
		"January 02, 2006",
		"Jan 02, 2006",
	}
	for _, layout := range layouts {
		t, err := time.Parse(layout, releaseDate)
		if err == nil {
			return t.Year(), true
		}
	}

	// Try plain 4-digit year at end (e.g. "Q4 1996" — just grab trailing year)
	parts := strings.Fields(releaseDate)
	for i := len(parts) - 1; i >= 0; i-- {
		year, err := strconv.Atoi(parts[i])
		if err == nil && year >= 1970 && year <= 2100 {
			return year, true
		}
	}

	return 0, false
}

// GetOnThisDay returns games released on today's month/day across all years.
// GET /api/explore/on-this-day
func (h *ExploreHandler) GetOnThisDay(c *gin.Context) {
	userID := getUserID(c)
	now := time.Now()
	targetMonth := now.Month()
	targetDay := now.Day()

	dateLabel := now.Format("January 2")

	// Load all games that have a release_date set
	var allGames []db.Game
	if err := h.DB.Preload("Console").
		Where("release_date != '' AND release_date IS NOT NULL AND deleted_at IS NULL").
		Find(&allGames).Error; err != nil {
		slog.Error("failed to fetch games for on-this-day", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch games"})
		return
	}

	// Filter to games matching today's month+day
	type matchedGame struct {
		game db.Game
		year int
	}
	var matched []matchedGame
	for _, g := range allGames {
		month, day, ok := parseReleaseDateMonthDay(g.ReleaseDate)
		if !ok {
			continue
		}
		if month == targetMonth && day == targetDay {
			year, _ := parseReleaseDateYear(g.ReleaseDate)
			matched = append(matched, matchedGame{game: g, year: year})
		}
	}

	// Sort by year ascending (oldest first)
	sort.Slice(matched, func(i, j int) bool {
		return matched[i].year < matched[j].year
	})

	// Limit to 20
	if len(matched) > 20 {
		matched = matched[:20]
	}

	if len(matched) == 0 {
		c.Header("Cache-Control", "private, max-age=300")
		c.JSON(http.StatusOK, OnThisDayResponse{Date: dateLabel, Games: []GameResponse{}})
		return
	}

	games := make([]db.Game, len(matched))
	gameIDs := make([]uint, len(matched))
	for i, m := range matched {
		games[i] = m.game
		gameIDs[i] = m.game.ID
	}

	userData := loadUserGameData(h.DB, userID, gameIDs)
	result := make([]GameResponse, len(games))
	for i, g := range games {
		result[i] = toGameResponseWithData(g, &userData)
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, OnThisDayResponse{Date: dateLabel, Games: result})
}

// GetBestOfYear returns the top-rated games from a specific year.
// GET /api/explore/best-of-year/:year
func (h *ExploreHandler) GetBestOfYear(c *gin.Context) {
	userID := getUserID(c)

	yearStr := c.Param("year")
	year, err := strconv.Atoi(yearStr)
	if err != nil || year < 1970 || year > 2100 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid year"})
		return
	}

	yearPrefix := fmt.Sprintf("%d", year)

	// Find games whose release_date starts with the year
	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("release_date LIKE ? AND deleted_at IS NULL", yearPrefix+"%").
		Where("rating > 0").
		Order("rating DESC").
		Limit(30).
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch best-of-year games", "error", err, "year", year)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch games"})
		return
	}

	if len(games) == 0 {
		c.Header("Cache-Control", "private, max-age=300")
		c.JSON(http.StatusOK, BestOfYearResponse{Year: year, Games: []GameResponse{}})
		return
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, BestOfYearResponse{
		Year:  year,
		Games: ToGameResponses(games, h.DB, userID),
	})
}

// GetYourAnniversaries returns personal milestones — games the user played
// roughly 1, 2, 3... years ago (within a 3-day window around today's date).
// GET /api/explore/your-anniversaries
func (h *ExploreHandler) GetYourAnniversaries(c *gin.Context) {
	userID := getUserID(c)

	now := time.Now()

	// Look back up to 10 years
	var allAnniversaries []AnniversaryItem
	for yearsAgo := 1; yearsAgo <= 10; yearsAgo++ {
		anniversary := now.AddDate(-yearsAgo, 0, 0)
		windowStart := anniversary.AddDate(0, 0, -3)
		windowEnd := anniversary.AddDate(0, 0, 3)

		var histories []db.PlayHistory
		if err := h.DB.
			Where("user_id = ? AND last_played BETWEEN ? AND ? AND deleted_at IS NULL",
				userID, windowStart, windowEnd).
			Find(&histories).Error; err != nil {
			slog.Error("failed to fetch anniversary play histories", "error", err, "yearsAgo", yearsAgo)
			continue
		}

		// Deduplicate by game ID (keep the one closest to the anniversary date)
		gameMap := make(map[uint]db.PlayHistory)
		for _, ph := range histories {
			existing, exists := gameMap[ph.GameID]
			if !exists {
				gameMap[ph.GameID] = ph
			} else {
				// Keep the one closest to the exact anniversary date
				existingDiff := existing.LastPlayed.Sub(anniversary)
				if existingDiff < 0 {
					existingDiff = -existingDiff
				}
				newDiff := ph.LastPlayed.Sub(anniversary)
				if newDiff < 0 {
					newDiff = -newDiff
				}
				if newDiff < existingDiff {
					gameMap[ph.GameID] = ph
				}
			}
		}

		for _, ph := range gameMap {
			allAnniversaries = append(allAnniversaries, AnniversaryItem{
				YearsAgo: yearsAgo,
				PlayedAt: ph.LastPlayed,
				Game:     GameResponse{ID: strconv.FormatUint(uint64(ph.GameID), 10)}, // placeholder, will be filled
			})
		}
	}

	if len(allAnniversaries) == 0 {
		c.Header("Cache-Control", "private, max-age=120")
		c.JSON(http.StatusOK, AnniversariesResponse{Anniversaries: []AnniversaryItem{}})
		return
	}

	// Collect unique game IDs
	gameIDSet := make(map[uint]bool)
	for _, a := range allAnniversaries {
		gid, _ := strconv.ParseUint(a.Game.ID, 10, 64)
		gameIDSet[uint(gid)] = true
	}
	gameIDs := make([]uint, 0, len(gameIDSet))
	for id := range gameIDSet {
		gameIDs = append(gameIDs, id)
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load anniversary games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load games"})
		return
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	userData := loadUserGameData(h.DB, userID, gameIDs)

	result := make([]AnniversaryItem, 0, len(allAnniversaries))
	for _, a := range allAnniversaries {
		gid, _ := strconv.ParseUint(a.Game.ID, 10, 64)
		g, ok := gameMap[uint(gid)]
		if !ok {
			continue
		}
		result = append(result, AnniversaryItem{
			Game:     toGameResponseWithData(g, &userData),
			YearsAgo: a.YearsAgo,
			PlayedAt: a.PlayedAt,
		})
	}

	// Sort by yearsAgo ascending (most recent anniversaries first)
	sort.Slice(result, func(i, j int) bool {
		if result[i].YearsAgo != result[j].YearsAgo {
			return result[i].YearsAgo < result[j].YearsAgo
		}
		return result[i].Game.Title < result[j].Game.Title
	})

	c.Header("Cache-Control", "private, max-age=120")
	c.JSON(http.StatusOK, AnniversariesResponse{Anniversaries: result})
}

// decadeRange maps a decade string to its year range.
var decadeRange = map[string][2]int{
	"80s": {1980, 1989},
	"90s": {1990, 1999},
	"00s": {2000, 2009},
}

// decadeLabel maps a decade string to a display label.
var decadeLabel = map[string]string{
	"80s": "The 80s",
	"90s": "The 90s",
	"00s": "The 00s",
}

// GetDecades returns the best games of a given decade.
// GET /api/explore/decades/:decade
func (h *ExploreHandler) GetDecades(c *gin.Context) {
	userID := getUserID(c)

	decade := c.Param("decade")
	yearRange, ok := decadeRange[decade]
	if !ok {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid decade; valid values: 80s, 90s, 00s"})
		return
	}
	label := decadeLabel[decade]

	// Build LIKE conditions for each year in the range
	conditions := make([]string, 0, yearRange[1]-yearRange[0]+1)
	args := make([]interface{}, 0, yearRange[1]-yearRange[0]+1)
	for y := yearRange[0]; y <= yearRange[1]; y++ {
		conditions = append(conditions, "release_date LIKE ?")
		args = append(args, fmt.Sprintf("%d%%", y))
	}
	whereClause := "(" + strings.Join(conditions, " OR ") + ")"

	var games []db.Game
	if err := h.DB.Preload("Console").
		Where(whereClause, args...).
		Where("rating > 0 AND deleted_at IS NULL").
		Order("rating DESC").
		Limit(30).
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch decade games", "error", err, "decade", decade)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch games"})
		return
	}

	if len(games) == 0 {
		c.Header("Cache-Control", "private, max-age=300")
		c.JSON(http.StatusOK, DecadesResponse{Decade: decade, Label: label, Games: []GameResponse{}})
		return
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, DecadesResponse{
		Decade: decade,
		Label:  label,
		Games:  ToGameResponses(games, h.DB, userID),
	})
}
