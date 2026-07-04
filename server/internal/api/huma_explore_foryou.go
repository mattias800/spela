package api

import (
	"context"
	"log/slog"
	"net/http"
	"sort"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Inputs / outputs ---

// GetForYouInput is the input for GET /api/explore/for-you.
type GetForYouInput struct{}

// GetForYouOutput wraps the personalized recommendation rows.
type GetForYouOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         ForYouResponse
}

// GetPlayersLikeYouInput is the input for GET /api/explore/players-like-you.
type GetPlayersLikeYouInput struct{}

// GetPlayersLikeYouOutput wraps the collaborative-filter recommendations.
type GetPlayersLikeYouOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         PlayersLikeYouResponse
}

// RegisterExploreForYouRoutes wires the personalized recommendation endpoints.
func RegisterExploreForYouRoutes(api huma.API, h *ExploreHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getForYou",
		Method:      http.MethodGet,
		Path:        "/api/explore/for-you",
		Summary:     "Get personalized recommendation rows",
		Description: "Returns personalized recommendation shelves derived from the caller's play history.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetForYou)

	huma.Register(api, huma.Operation{
		OperationID: "getPlayersLikeYou",
		Method:      http.MethodGet,
		Path:        "/api/explore/players-like-you",
		Summary:     "Get collaborative-filter game recommendations",
		Description: "Returns games favourited by users whose favourites overlap with the caller's. Uses Jaccard similarity.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetPlayersLikeYou)
}

// --- Handlers ---

// HumaGetForYou is the huma handler for GET /api/explore/for-you.
func (h *ExploreHandler) HumaGetForYou(ctx context.Context, _ *GetForYouInput) (*GetForYouOutput, error) {
	userID := UserIDFromContext(ctx)
	if userID == 0 {
		return nil, huma.Error401Unauthorized("authentication required")
	}

	// Compute the cross-platform-dedupe tiebreak hint once and share it
	// with every row builder. The map is read-only after construction so
	// the parallel goroutines below can safely read it without locking.
	// See [explore_handler_foryou_dedup.go] for the dedupe rules
	// (issue #1186, with #1183's normalized-title fallback).
	mostPlayedByTitle := h.fetchMostPlayedTitleMap(userID)

	// Run the four row builders in parallel — they're independent and
	// each one does its own DB queries. Same pattern as /explore/rows.
	type result struct {
		idx           int
		becauseRows   []ForYouRowResponse
		row           *ForYouRowResponse
		err           error
		errLogContext string
	}
	results := make(chan result, 4)

	go func() {
		rows, err := h.buildBecauseYouPlayedRows(userID, mostPlayedByTitle)
		results <- result{idx: 0, becauseRows: rows, err: err, errLogContext: "because-you-played rows"}
	}()
	go func() {
		row, err := h.buildMoreGenreRow(userID, mostPlayedByTitle)
		results <- result{idx: 1, row: row, err: err, errLogContext: "more-genre row"}
	}()
	go func() {
		row, err := h.buildUnfinishedRow(userID, mostPlayedByTitle)
		results <- result{idx: 2, row: row, err: err, errLogContext: "unfinished row"}
	}()
	go func() {
		row, err := h.buildExpandHorizonsRow(userID, mostPlayedByTitle)
		results <- result{idx: 3, row: row, err: err, errLogContext: "expand-horizons row"}
	}()

	collected := make([]result, 4)
	var firstErr error
	for range collected {
		r := <-results
		collected[r.idx] = r
		if r.err != nil {
			slog.Error("failed to build for-you row", "row", r.errLogContext, "error", r.err)
			if firstErr == nil {
				firstErr = r.err
			}
		}
	}
	if firstErr != nil {
		return nil, huma.Error500InternalServerError("failed to build recommendations")
	}

	rows := make([]ForYouRowResponse, 0, 6)
	rows = append(rows, collected[0].becauseRows...) // because-you-played (may be many)
	for i := 1; i < 4; i++ {
		if collected[i].row != nil {
			rows = append(rows, *collected[i].row)
		}
	}

	return &GetForYouOutput{
		CacheControl: "private, max-age=120",
		Body:         ForYouResponse{Rows: rows},
	}, nil
}

// HumaGetPlayersLikeYou is the huma handler for GET /api/explore/players-like-you.
func (h *ExploreHandler) HumaGetPlayersLikeYou(ctx context.Context, _ *GetPlayersLikeYouInput) (*GetPlayersLikeYouOutput, error) {
	userID := UserIDFromContext(ctx)
	if userID == 0 {
		return nil, huma.Error401Unauthorized("authentication required")
	}

	myFavoriteRows, err := h.fetchFavoriteTitleRowsForUser(userID)
	if err != nil {
		slog.Error("failed to get user favorites", "error", err)
		return nil, huma.Error500InternalServerError("failed to get recommendations")
	}

	myFavoriteKeys := titleKeySet(myFavoriteRows)
	if len(myFavoriteKeys) == 0 {
		return &GetPlayersLikeYouOutput{
			CacheControl: "private, max-age=120",
			Body: PlayersLikeYouResponse{
				Games:             []GameResponse{},
				SimilarUsersCount: 0,
			},
		}, nil
	}

	overlapRows, err := h.fetchPotentialOverlapFavoriteTitleRows(userID, myFavoriteRows)
	if err != nil {
		slog.Error("failed to find similar users", "error", err)
		return nil, huma.Error500InternalServerError("failed to get recommendations")
	}

	candidateUserIDs := usersWithTitleKeyOverlap(userID, myFavoriteKeys, overlapRows)
	if len(candidateUserIDs) == 0 {
		return &GetPlayersLikeYouOutput{
			CacheControl: "private, max-age=120",
			Body: PlayersLikeYouResponse{
				Games:             []GameResponse{},
				SimilarUsersCount: 0,
			},
		}, nil
	}

	candidateFavoriteRows, err := h.fetchFavoriteTitleRowsForUsers(candidateUserIDs)
	if err != nil {
		slog.Error("failed to get candidate favorite rows", "error", err)
		return nil, huma.Error500InternalServerError("failed to get recommendations")
	}

	favoriteKeysByUser := make(map[uint]map[string]struct{}, len(candidateUserIDs))
	for _, row := range candidateFavoriteRows {
		if favoriteKeysByUser[row.UserID] == nil {
			favoriteKeysByUser[row.UserID] = make(map[string]struct{})
		}
		favoriteKeysByUser[row.UserID][row.titleKey()] = struct{}{}
	}

	type similarUser struct {
		userID     uint
		similarity float64
	}

	var similarUsers []similarUser
	for _, candidateUserID := range candidateUserIDs {
		candidateKeys := favoriteKeysByUser[candidateUserID]
		intersection := countTitleKeyOverlap(myFavoriteKeys, candidateKeys)
		if intersection == 0 {
			continue
		}
		union := len(myFavoriteKeys) + len(candidateKeys) - intersection
		if union == 0 {
			continue
		}
		similarity := float64(intersection) / float64(union)
		similarUsers = append(similarUsers, similarUser{
			userID:     candidateUserID,
			similarity: similarity,
		})
	}

	if len(similarUsers) == 0 {
		return &GetPlayersLikeYouOutput{
			CacheControl: "private, max-age=120",
			Body: PlayersLikeYouResponse{
				Games:             []GameResponse{},
				SimilarUsersCount: 0,
			},
		}, nil
	}

	sort.SliceStable(similarUsers, func(i, j int) bool {
		if similarUsers[i].similarity != similarUsers[j].similarity {
			return similarUsers[i].similarity > similarUsers[j].similarity
		}
		return similarUsers[i].userID < similarUsers[j].userID
	})

	if len(similarUsers) > 5 {
		similarUsers = similarUsers[:5]
	}

	topUserSet := make(map[uint]struct{}, len(similarUsers))
	for _, su := range similarUsers {
		topUserSet[su.userID] = struct{}{}
	}

	type recommendedTitle struct {
		key       string
		userIDs   map[uint]struct{}
		gameIDs   []uint
		gameIDSet map[uint]struct{}
	}
	recommendedByKey := make(map[string]*recommendedTitle)
	for _, row := range candidateFavoriteRows {
		if _, ok := topUserSet[row.UserID]; !ok {
			continue
		}
		key := row.titleKey()
		if _, alreadyFavorite := myFavoriteKeys[key]; alreadyFavorite {
			continue
		}
		rec := recommendedByKey[key]
		if rec == nil {
			rec = &recommendedTitle{
				key:       key,
				userIDs:   make(map[uint]struct{}),
				gameIDSet: make(map[uint]struct{}),
			}
			recommendedByKey[key] = rec
		}
		rec.userIDs[row.UserID] = struct{}{}
		if _, exists := rec.gameIDSet[row.GameID]; !exists {
			rec.gameIDSet[row.GameID] = struct{}{}
			rec.gameIDs = append(rec.gameIDs, row.GameID)
		}
	}

	recommendedTitles := make([]*recommendedTitle, 0, len(recommendedByKey))
	for _, rec := range recommendedByKey {
		sort.Slice(rec.gameIDs, func(i, j int) bool { return rec.gameIDs[i] < rec.gameIDs[j] })
		recommendedTitles = append(recommendedTitles, rec)
	}
	sort.SliceStable(recommendedTitles, func(i, j int) bool {
		leftCount, rightCount := len(recommendedTitles[i].userIDs), len(recommendedTitles[j].userIDs)
		if leftCount != rightCount {
			return leftCount > rightCount
		}
		return recommendedTitles[i].key < recommendedTitles[j].key
	})
	if len(recommendedTitles) > 20 {
		recommendedTitles = recommendedTitles[:20]
	}

	if len(recommendedTitles) == 0 {
		return &GetPlayersLikeYouOutput{
			CacheControl: "private, max-age=120",
			Body: PlayersLikeYouResponse{
				Games:             []GameResponse{},
				SimilarUsersCount: len(similarUsers),
			},
		}, nil
	}

	gameIDs := make([]uint, 0, len(recommendedTitles))
	for _, rec := range recommendedTitles {
		gameIDs = append(gameIDs, rec.gameIDs...)
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ? AND is_primary = true", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load recommended games", "error", err)
		return nil, huma.Error500InternalServerError("failed to get recommendations")
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}
	sorted := make([]db.Game, 0, len(gameIDs))
	for _, rec := range recommendedTitles {
		for _, gameID := range rec.gameIDs {
			if g, ok := gameMap[gameID]; ok {
				sorted = append(sorted, g)
			}
		}
	}

	// Cross-platform title dedupe. Without it, a similar user
	// who has Street Fighter II on SNES, Genesis, and Arcade favourited
	// would inflate this shelf with all three platform variants.
	sorted = dedupeGamesByTitleForUserWithMostPlayed(sorted, h.DB, userID, h.fetchMostPlayedTitleMap(userID))
	if len(sorted) > 20 {
		sorted = sorted[:20]
	}

	return &GetPlayersLikeYouOutput{
		CacheControl: "private, max-age=120",
		Body: PlayersLikeYouResponse{
			Games:             ToGameResponses(sorted, h.DB, userID),
			SimilarUsersCount: len(similarUsers),
		},
	}, nil
}

type favoriteTitleRow struct {
	UserID          uint
	GameID          uint
	Title           string
	TitleRootIGDBID *uint
}

func (r favoriteTitleRow) titleKey() string {
	return titleDedupeKey(db.Game{ID: r.GameID, Title: r.Title, TitleRootIGDBID: r.TitleRootIGDBID})
}

func (h *ExploreHandler) fetchFavoriteTitleRowsForUser(userID uint) ([]favoriteTitleRow, error) {
	var rows []favoriteTitleRow
	err := h.favoriteTitleRowsQuery().
		Where("favorites.user_id = ?", userID).
		Scan(&rows).Error
	return rows, err
}

func (h *ExploreHandler) fetchFavoriteTitleRowsForUsers(userIDs []uint) ([]favoriteTitleRow, error) {
	if len(userIDs) == 0 {
		return nil, nil
	}
	var rows []favoriteTitleRow
	err := h.favoriteTitleRowsQuery().
		Where("favorites.user_id IN ?", userIDs).
		Scan(&rows).Error
	return rows, err
}

func (h *ExploreHandler) fetchPotentialOverlapFavoriteTitleRows(userID uint, myFavoriteRows []favoriteTitleRow) ([]favoriteTitleRow, error) {
	rootIDs, includeNoRoot := overlapCandidateFilters(myFavoriteRows)
	if len(rootIDs) == 0 && !includeNoRoot {
		return nil, nil
	}

	query := h.favoriteTitleRowsQuery().
		Where("favorites.user_id != ?", userID)
	switch {
	case len(rootIDs) > 0 && includeNoRoot:
		query = query.Where("(games.title_root_igdb_id IN ? OR games.title_root_igdb_id IS NULL)", rootIDs)
	case len(rootIDs) > 0:
		query = query.Where("games.title_root_igdb_id IN ?", rootIDs)
	default:
		query = query.Where("games.title_root_igdb_id IS NULL")
	}

	var rows []favoriteTitleRow
	err := query.Scan(&rows).Error
	return rows, err
}

func (h *ExploreHandler) favoriteTitleRowsQuery() *gorm.DB {
	return h.DB.Table("favorites").
		Select("favorites.user_id, favorites.game_id, games.title, games.title_root_igdb_id").
		Joins("JOIN games ON games.id = favorites.game_id AND games.deleted_at IS NULL").
		Where("favorites.deleted_at IS NULL")
}

func titleKeySet(rows []favoriteTitleRow) map[string]struct{} {
	keys := make(map[string]struct{}, len(rows))
	for _, row := range rows {
		keys[row.titleKey()] = struct{}{}
	}
	return keys
}

func overlapCandidateFilters(rows []favoriteTitleRow) ([]uint, bool) {
	rootIDSet := make(map[uint]struct{})
	includeNoRoot := false
	for _, row := range rows {
		if row.TitleRootIGDBID == nil {
			includeNoRoot = true
			continue
		}
		rootIDSet[*row.TitleRootIGDBID] = struct{}{}
	}

	rootIDs := make([]uint, 0, len(rootIDSet))
	for id := range rootIDSet {
		rootIDs = append(rootIDs, id)
	}
	sort.Slice(rootIDs, func(i, j int) bool { return rootIDs[i] < rootIDs[j] })
	return rootIDs, includeNoRoot
}

func usersWithTitleKeyOverlap(userID uint, myFavoriteKeys map[string]struct{}, rows []favoriteTitleRow) []uint {
	keysByUser := make(map[uint]map[string]struct{})
	for _, row := range rows {
		if row.UserID == userID {
			continue
		}
		if keysByUser[row.UserID] == nil {
			keysByUser[row.UserID] = make(map[string]struct{})
		}
		keysByUser[row.UserID][row.titleKey()] = struct{}{}
	}

	userIDs := make([]uint, 0, len(keysByUser))
	for candidateUserID, candidateKeys := range keysByUser {
		if countTitleKeyOverlap(myFavoriteKeys, candidateKeys) > 0 {
			userIDs = append(userIDs, candidateUserID)
		}
	}
	sort.Slice(userIDs, func(i, j int) bool { return userIDs[i] < userIDs[j] })
	return userIDs
}

func countTitleKeyOverlap(left map[string]struct{}, right map[string]struct{}) int {
	if len(left) > len(right) {
		left, right = right, left
	}
	count := 0
	for key := range left {
		if _, ok := right[key]; ok {
			count++
		}
	}
	return count
}
