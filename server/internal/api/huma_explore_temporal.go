package api

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Inputs / outputs ---

// GetOnThisDayInput is the input for GET /api/explore/on-this-day.
type GetOnThisDayInput struct{}

// GetOnThisDayOutput wraps the on-this-day response.
type GetOnThisDayOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         OnThisDayResponse
}

// GetBestOfYearInput is the input for GET /api/explore/best-of-year/{year}.
type GetBestOfYearInput struct {
	Year string `path:"year" pattern:"^(19|20|21)[0-9]{2}$" maxLength:"4" doc:"Release year (1970-2100)."`
}

// GetBestOfYearOutput wraps the best-of-year response.
type GetBestOfYearOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         BestOfYearResponse
}

// GetYourAnniversariesInput is the input for GET /api/explore/your-anniversaries.
type GetYourAnniversariesInput struct{}

// GetYourAnniversariesOutput wraps the anniversaries response.
type GetYourAnniversariesOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         AnniversariesResponse
}

// GetDecadesInput is the input for GET /api/explore/decades/{decade}.
type GetDecadesInput struct {
	Decade string `path:"decade" pattern:"^[0-9]{2}s$" maxLength:"3" doc:"Decade identifier: '80s', '90s', or '00s'."`
}

// GetDecadesOutput wraps the decades response.
type GetDecadesOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         DecadesResponse
}

// RegisterExploreTemporalRoutes wires the temporal discovery endpoints.
func RegisterExploreTemporalRoutes(api huma.API, h *ExploreHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getOnThisDay",
		Method:      http.MethodGet,
		Path:        "/api/explore/on-this-day",
		Summary:     "Get games released on today's month/day",
		Description: "Returns up to 20 games released on today's month/day across all years, sorted oldest first.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetOnThisDay)

	huma.Register(api, huma.Operation{
		OperationID: "getBestOfYear",
		Method:      http.MethodGet,
		Path:        "/api/explore/best-of-year/{year}",
		Summary:     "Get top-rated games from a specific release year",
		Description: "Returns up to 30 highest-rated games released in the given year.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetBestOfYear)

	huma.Register(api, huma.Operation{
		OperationID: "getYourAnniversaries",
		Method:      http.MethodGet,
		Path:        "/api/explore/your-anniversaries",
		Summary:     "Get personal gameplay anniversaries",
		Description: "Returns games the caller played roughly 1..10 years ago (within a 3-day window of today's date).",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetYourAnniversaries)

	huma.Register(api, huma.Operation{
		OperationID: "getDecades",
		Method:      http.MethodGet,
		Path:        "/api/explore/decades/{decade}",
		Summary:     "Get best games of a decade",
		Description: "Returns up to 30 highest-rated games for the given decade (80s, 90s, or 00s).",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetDecades)
}

// --- Handlers ---

// HumaGetOnThisDay is the huma handler for GET /api/explore/on-this-day.
func (h *ExploreHandler) HumaGetOnThisDay(ctx context.Context, _ *GetOnThisDayInput) (*GetOnThisDayOutput, error) {
	userID := UserIDFromContext(ctx)
	now := time.Now()
	targetMonth := now.Month()
	targetDay := now.Day()

	dateLabel := now.Format("January 2")

	// Pre-filter at SQL with LIKE patterns covering every release_date
	// format parseReleaseDateMonthDay accepts. The old version loaded
	// every release-dated game (could be 50k+ rows) into Go memory and
	// ran time.Parse per row — the bottleneck per the perf audit.
	// SQL filtering still scans the table (no index on release_date)
	// but skips the wire transfer + reflection cost of building Game
	// structs for rows we'll throw away.
	likePatterns := onThisDayLikePatterns(targetMonth, targetDay)
	whereSQL := "release_date IS NOT NULL AND release_date != '' AND is_primary = true AND deleted_at IS NULL AND ("
	whereArgs := make([]interface{}, 0, len(likePatterns))
	for i, p := range likePatterns {
		if i > 0 {
			whereSQL += " OR "
		}
		whereSQL += "release_date LIKE ?"
		whereArgs = append(whereArgs, p)
	}
	whereSQL += ")"

	var candidates []db.Game
	if err := h.DB.Preload("Console").
		Where(whereSQL, whereArgs...).
		Find(&candidates).Error; err != nil {
		slog.Error("failed to fetch games for on-this-day", "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch games")
	}

	// LIKE patterns are intentionally over-inclusive (e.g. "%-05-13%"
	// could match a substring inside a larger string). Re-validate by
	// parsing the exact month/day per candidate row.
	type matchedGame struct {
		game db.Game
		year int
	}
	var matched []matchedGame
	for _, g := range candidates {
		month, day, ok := parseReleaseDateMonthDay(g.ReleaseDate)
		if !ok {
			continue
		}
		if month == targetMonth && day == targetDay {
			year, _ := parseReleaseDateYear(g.ReleaseDate)
			matched = append(matched, matchedGame{game: g, year: year})
		}
	}

	sort.Slice(matched, func(i, j int) bool {
		return matched[i].year < matched[j].year
	})

	if len(matched) > 20 {
		matched = matched[:20]
	}

	if len(matched) == 0 {
		return &GetOnThisDayOutput{
			CacheControl: "private, max-age=300",
			Body:         OnThisDayResponse{Date: dateLabel, Games: []GameResponse{}},
		}, nil
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

	return &GetOnThisDayOutput{
		CacheControl: "private, max-age=300",
		Body:         OnThisDayResponse{Date: dateLabel, Games: result},
	}, nil
}

// HumaGetBestOfYear is the huma handler for GET /api/explore/best-of-year/{year}.
func (h *ExploreHandler) HumaGetBestOfYear(ctx context.Context, in *GetBestOfYearInput) (*GetBestOfYearOutput, error) {
	userID := UserIDFromContext(ctx)

	year, err := strconv.Atoi(in.Year)
	if err != nil || year < 1970 || year > 2100 {
		return nil, huma.Error400BadRequest("invalid year")
	}

	yearPrefix := fmt.Sprintf("%d", year)

	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("release_date LIKE ? AND is_primary = true AND deleted_at IS NULL", yearPrefix+"%").
		Where("rating > 0").
		Order("rating DESC").
		Limit(30).
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch best-of-year games", "error", err, "year", year)
		return nil, huma.Error500InternalServerError("failed to fetch games")
	}

	if len(games) == 0 {
		return &GetBestOfYearOutput{
			CacheControl: "private, max-age=300",
			Body:         BestOfYearResponse{Year: year, Games: []GameResponse{}},
		}, nil
	}

	return &GetBestOfYearOutput{
		CacheControl: "private, max-age=300",
		Body: BestOfYearResponse{
			Year:  year,
			Games: ToGameResponses(games, h.DB, userID),
		},
	}, nil
}

// HumaGetYourAnniversaries is the huma handler for GET /api/explore/your-anniversaries.
func (h *ExploreHandler) HumaGetYourAnniversaries(ctx context.Context, _ *GetYourAnniversariesInput) (*GetYourAnniversariesOutput, error) {
	userID := UserIDFromContext(ctx)

	now := time.Now()

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

		gameMap := make(map[uint]db.PlayHistory)
		for _, ph := range histories {
			existing, exists := gameMap[ph.GameID]
			if !exists {
				gameMap[ph.GameID] = ph
			} else {
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
				Game:     GameResponse{ID: strconv.FormatUint(uint64(ph.GameID), 10)},
			})
		}
	}

	if len(allAnniversaries) == 0 {
		return &GetYourAnniversariesOutput{
			CacheControl: "private, max-age=120",
			Body:         AnniversariesResponse{Anniversaries: []AnniversaryItem{}},
		}, nil
	}

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
		return nil, huma.Error500InternalServerError("failed to load games")
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

	sort.Slice(result, func(i, j int) bool {
		if result[i].YearsAgo != result[j].YearsAgo {
			return result[i].YearsAgo < result[j].YearsAgo
		}
		return result[i].Game.Title < result[j].Game.Title
	})

	return &GetYourAnniversariesOutput{
		CacheControl: "private, max-age=120",
		Body:         AnniversariesResponse{Anniversaries: result},
	}, nil
}

// HumaGetDecades is the huma handler for GET /api/explore/decades/{decade}.
func (h *ExploreHandler) HumaGetDecades(ctx context.Context, in *GetDecadesInput) (*GetDecadesOutput, error) {
	userID := UserIDFromContext(ctx)

	yearRange, ok := decadeRange[in.Decade]
	if !ok {
		return nil, huma.Error400BadRequest("invalid decade; valid values: 80s, 90s, 00s")
	}
	label := decadeLabel[in.Decade]

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
		Where("rating > 0 AND is_primary = true AND deleted_at IS NULL").
		Order("rating DESC").
		Limit(30).
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch decade games", "error", err, "decade", in.Decade)
		return nil, huma.Error500InternalServerError("failed to fetch games")
	}

	if len(games) == 0 {
		return &GetDecadesOutput{
			CacheControl: "private, max-age=300",
			Body:         DecadesResponse{Decade: in.Decade, Label: label, Games: []GameResponse{}},
		}, nil
	}

	return &GetDecadesOutput{
		CacheControl: "private, max-age=300",
		Body: DecadesResponse{
			Decade: in.Decade,
			Label:  label,
			Games:  ToGameResponses(games, h.DB, userID),
		},
	}, nil
}
