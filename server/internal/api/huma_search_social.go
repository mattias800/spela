package api

import (
	"context"
	"net/http"
	"strconv"
	"strings"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Search ---

// SearchInput is the input for GET /api/search. Validation of the query
// length and the limit bounds happens in the handler (not as huma schema
// constraints) so failures report 400 with the historical error message
// instead of huma's 422 "validation failed".
type SearchInput struct {
	Q     string `query:"q" doc:"Search query (minimum 2 characters)."`
	Limit int    `query:"limit" doc:"Maximum number of results per category. Defaults to 5, clamped to 1-50."`
}

// SearchOutput wraps the global search response.
type SearchOutput struct {
	Body SearchResponse
}

// --- Users / social ---

// SearchUsersInput is the input for GET /api/users/search. Page and pageSize
// bounds are enforced in the handler (not as huma constraints) so out-of-range
// values fall back to defaults instead of returning a 422 "validation failed".
type SearchUsersInput struct {
	Q        string `query:"q" doc:"Optional username prefix filter."`
	Page     int    `query:"page" doc:"1-based page number (defaults to 1)."`
	PageSize int    `query:"pageSize" doc:"Page size (defaults to 20, clamped to 1-50)."`
}

// SearchUsersOutput wraps the paginated user search results.
type SearchUsersOutput struct {
	Body PaginatedResponse[UserSearchResult]
}

// GetOnlineUsersInput is the input for GET /api/social/online.
type GetOnlineUsersInput struct{}

// OnlineUsersResponse wraps the online-users list in a {users: [...]} object,
// matching the raw gin handler wire format.
type OnlineUsersResponse struct {
	Users []OnlineUserResponse `json:"users"`
}

// GetOnlineUsersOutput wraps the online-users response envelope.
type GetOnlineUsersOutput struct {
	Body OnlineUsersResponse
}

// --- Registration ---

// RegisterSearchRoutes wires the global /api/search endpoint into the huma API.
func RegisterSearchRoutes(api huma.API, h *SearchHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "globalSearch",
		Method:      http.MethodGet,
		Path:        "/api/search",
		Summary:     "Global search across games, consoles, people and lists",
		Description: "Performs a global search across games, consoles, developers, publishers, collections, series and franchises and returns each category's top matches plus a total count.",
		Tags:        []string{"search"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaSearch)
}

// RegisterSocialRoutes wires the user-search and online-users endpoints into
// the huma API. The activity feed stays on raw gin because it requires the
// typed-metadata shim that the huma migration hasn't adopted yet.
func RegisterSocialRoutes(api huma.API, h *SocialHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "searchUsers",
		Method:      http.MethodGet,
		Path:        "/api/users/search",
		Summary:     "Search for users",
		Description: "Returns a paginated list of users matching the supplied username prefix (or all non-disabled users when the query is empty), excluding the authenticated user.",
		Tags:        []string{"social"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaSearchUsers)

	huma.Register(api, huma.Operation{
		OperationID: "getOnlineUsers",
		Method:      http.MethodGet,
		Path:        "/api/social/online",
		Summary:     "List users currently online",
		Description: "Returns users currently connected via WebSocket, optionally with the game they are currently playing.",
		Tags:        []string{"social"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetOnlineUsers)
}

// --- Handler implementations ---

// HumaSearch is the huma handler for GET /api/search.
func (h *SearchHandler) HumaSearch(ctx context.Context, in *SearchInput) (*SearchOutput, error) {
	userID := UserIDFromContext(ctx)

	query := strings.TrimSpace(in.Q)
	if len(query) < 2 {
		return nil, huma.Error400BadRequest("query must be at least 2 characters")
	}

	limit := in.Limit
	if limit < 1 || limit > 50 {
		limit = 5
	}

	escapedQuery := escapeLikePattern(query)
	likePattern := "%" + escapedQuery + "%"

	games := h.searchGames(likePattern, limit)
	consoles := h.searchConsoles(likePattern, limit)
	developers := h.searchDevelopers(likePattern, limit)
	publishers := h.searchPublishers(likePattern, limit)
	collections := h.searchCollections(likePattern, limit, userID)
	series := h.searchSeries(likePattern, limit)
	franchises := h.searchFranchises(likePattern, limit)

	return &SearchOutput{Body: SearchResponse{
		Games:       games,
		Consoles:    consoles,
		Developers:  developers,
		Publishers:  publishers,
		Collections: collections,
		Series:      series,
		Franchises:  franchises,
	}}, nil
}

// HumaSearchUsers is the huma handler for GET /api/users/search.
func (h *SocialHandler) HumaSearchUsers(ctx context.Context, in *SearchUsersInput) (*SearchUsersOutput, error) {
	uid := UserIDFromContext(ctx)
	query := strings.TrimSpace(in.Q)

	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 50 {
		pageSize = 20
	}

	base := h.DB.Where("id != ? AND disabled = ?", uid, false)
	if query != "" {
		base = base.Where("username LIKE ? ESCAPE '\\'", escapeLikePattern(query)+"%")
	}

	var total int64
	base.Model(&db.User{}).Count(&total)

	var users []db.User
	if err := base.Order("username ASC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&users).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to search users")
	}

	result := make([]UserSearchResult, 0, len(users))
	for _, u := range users {
		result = append(result, UserSearchResult{
			ID:        strconv.FormatUint(uint64(u.ID), 10),
			Username:  u.Username,
			AvatarURL: u.AvatarURL,
		})
	}

	return &SearchUsersOutput{
		Body: PaginatedResponse[UserSearchResult]{
			Data:     result,
			Total:    total,
			Page:     page,
			PageSize: pageSize,
		},
	}, nil
}

// HumaGetOnlineUsers is the huma handler for GET /api/social/online.
func (h *SocialHandler) HumaGetOnlineUsers(_ context.Context, _ *GetOnlineUsersInput) (*GetOnlineUsersOutput, error) {
	onlineIDs := h.Hub.GetOnlineUserIDs()

	if len(onlineIDs) == 0 {
		return &GetOnlineUsersOutput{Body: OnlineUsersResponse{Users: []OnlineUserResponse{}}}, nil
	}

	var users []db.User
	if err := h.DB.Where("id IN ?", onlineIDs).Find(&users).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch online users")
	}

	result := make([]OnlineUserResponse, 0, len(users))
	for _, u := range users {
		resp := OnlineUserResponse{
			ID:        strconv.FormatUint(uint64(u.ID), 10),
			Username:  u.Username,
			AvatarURL: u.AvatarURL,
		}
		if gameID := h.Hub.GetUserGame(u.ID); gameID != 0 {
			var game db.Game
			if err := h.DB.Preload("Console").First(&game, gameID).Error; err == nil {
				coverURL := game.CoverURL
				if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
					coverURL = "/api/images/" + coverURL
				}
				consoleName := ""
				if game.Console.ID != 0 {
					consoleName = game.Console.Name
				}
				resp.CurrentGame = &OnlineUserGameResponse{
					ID:          strconv.FormatUint(uint64(game.ID), 10),
					Title:       game.Title,
					CoverURL:    coverURL,
					ConsoleName: consoleName,
				}
			}
		}
		result = append(result, resp)
	}

	return &GetOnlineUsersOutput{Body: OnlineUsersResponse{Users: result}}, nil
}
