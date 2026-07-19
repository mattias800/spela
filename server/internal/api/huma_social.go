package api

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Activity feed ---

// GetActivityFeedInput is the input for GET /api/social/activity.
type GetActivityFeedInput struct {
	Page     int `query:"page" doc:"1-based page number (defaults to 1)."`
	PageSize int `query:"pageSize" doc:"Page size (defaults to 20, clamped to 1-100)."`
}

// GetActivityFeedOutput wraps the paginated activity feed.
type GetActivityFeedOutput struct {
	Body PaginatedResponse[ActivityEventResponse]
}

// --- Recent partners ---

// GetRecentPartnersInput is the input for GET /api/users/recent-partners.
type GetRecentPartnersInput struct{}

// GetRecentPartnersOutput wraps the partners list.
type GetRecentPartnersOutput struct {
	Body []UserSearchResult
}

// --- Public profile ---

// GetPublicProfileInput is the input for GET /api/users/{id}/profile.
type GetPublicProfileInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"User ID."`
}

// GetPublicProfileOutput wraps the public profile response.
type GetPublicProfileOutput struct {
	Body PublicProfileResponse
}

// RegisterSocialExtraRoutes wires the remaining social endpoints (activity
// feed, recent partners, public profile) into the huma API.
func RegisterSocialExtraRoutes(api huma.API, h *SocialHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getActivityFeed",
		Method:      http.MethodGet,
		Path:        "/api/social/activity",
		Summary:     "Get the global activity feed",
		Description: "Returns a paginated activity feed (most recent first) with per-event user and game metadata.",
		Tags:        []string{"social"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetActivityFeed)

	huma.Register(api, huma.Operation{
		OperationID: "getRecentPartners",
		Method:      http.MethodGet,
		Path:        "/api/users/recent-partners",
		Summary:     "Get the caller's recent play partners",
		Description: "Returns users the caller has shared netplay or shared sessions with in the last 6 months, sorted by most recent interaction.",
		Tags:        []string{"social"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetRecentPartners)

	huma.Register(api, huma.Operation{
		OperationID: "getPublicProfile",
		Method:      http.MethodGet,
		Path:        "/api/users/{id}/profile",
		Summary:     "Get a user's public profile",
		Description: "Returns a user's public profile including stats, favorite games, recent games, and top-played games.",
		Tags:        []string{"social"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetPublicProfile)
}

// --- Handlers ---

// HumaGetActivityFeed is the huma handler for GET /api/social/activity.
func (h *SocialHandler) HumaGetActivityFeed(_ context.Context, in *GetActivityFeedInput) (*GetActivityFeedOutput, error) {
	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.ActivityEvent{}).Count(&total)

	var events []db.ActivityEvent
	if err := h.DB.Preload("User").Preload("Game").Preload("Game.Console").
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&events).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch activity feed")
	}

	result := make([]ActivityEventResponse, 0, len(events))
	for _, e := range events {
		coverURL := e.Game.CoverURL
		if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
			coverURL = "/api/images/" + coverURL
		}

		consoleName := ""
		if e.Game.Console.ID != 0 {
			consoleName = e.Game.Console.Name
		}

		var metadata *ActivityEventMetadata
		if e.Metadata != "" {
			var m ActivityEventMetadata
			if err := json.Unmarshal([]byte(e.Metadata), &m); err != nil {
				slog.Warn("failed to parse activity event metadata", "error", err, "eventId", e.ID)
			} else {
				metadata = &m
			}
		}

		result = append(result, ActivityEventResponse{
			ID:           strconv.FormatUint(uint64(e.ID), 10),
			EventType:    e.EventType,
			CreatedAt:    e.CreatedAt,
			UserID:       strconv.FormatUint(uint64(e.UserID), 10),
			Username:     e.User.Username,
			AvatarURL:    e.User.AvatarURL,
			GameID:       activityGameIDString(e.GameID),
			GameTitle:    e.Game.Title,
			GameCoverURL: coverURL,
			ConsoleName:  consoleName,
			Metadata:     metadata,
		})
	}

	return &GetActivityFeedOutput{Body: PaginatedResponse[ActivityEventResponse]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}}, nil
}

// HumaGetRecentPartners is the huma handler for GET /api/users/recent-partners.
func (h *SocialHandler) HumaGetRecentPartners(ctx context.Context, _ *GetRecentPartnersInput) (*GetRecentPartnersOutput, error) {
	uid := UserIDFromContext(ctx)
	sixMonthsAgo := time.Now().AddDate(0, -6, 0)

	type partnerInfo struct {
		lastSeen time.Time
	}
	partners := make(map[uint]*partnerInfo)

	var netplaySessions []db.NetplaySession
	h.DB.Where(
		"(host_user_id = ? OR client_user_id = ?) AND created_at >= ?",
		uid, uid, sixMonthsAgo,
	).Find(&netplaySessions)

	for _, s := range netplaySessions {
		var partnerID uint
		if s.HostUserID == uid {
			if s.ClientUserID == nil {
				continue
			}
			partnerID = *s.ClientUserID
		} else {
			partnerID = s.HostUserID
		}
		if partnerID == uid {
			continue
		}
		ts := s.CreatedAt
		if p, ok := partners[partnerID]; ok {
			if ts.After(p.lastSeen) {
				p.lastSeen = ts
			}
		} else {
			partners[partnerID] = &partnerInfo{lastSeen: ts}
		}
	}

	var myMemberships []db.SharedSessionMember
	h.DB.Where("user_id = ?", uid).Find(&myMemberships)

	if len(myMemberships) > 0 {
		sessionIDs := make([]uint, 0, len(myMemberships))
		for _, m := range myMemberships {
			sessionIDs = append(sessionIDs, m.SharedSessionID)
		}

		var otherMembers []db.SharedSessionMember
		h.DB.Where("shared_session_id IN ? AND user_id != ?", sessionIDs, uid).
			Find(&otherMembers)

		var sharedSessions []db.SharedSession
		h.DB.Where("id IN ? AND created_at >= ?", sessionIDs, sixMonthsAgo).
			Find(&sharedSessions)
		sessionCreatedAt := make(map[uint]time.Time, len(sharedSessions))
		for _, ss := range sharedSessions {
			sessionCreatedAt[ss.ID] = ss.CreatedAt
		}

		for _, m := range otherMembers {
			ts, ok := sessionCreatedAt[m.SharedSessionID]
			if !ok {
				continue
			}
			if p, exists := partners[m.UserID]; exists {
				if ts.After(p.lastSeen) {
					p.lastSeen = ts
				}
			} else {
				partners[m.UserID] = &partnerInfo{lastSeen: ts}
			}
		}
	}

	if len(partners) == 0 {
		return &GetRecentPartnersOutput{Body: []UserSearchResult{}}, nil
	}

	partnerIDs := make([]uint, 0, len(partners))
	for id := range partners {
		partnerIDs = append(partnerIDs, id)
	}

	var users []db.User
	h.DB.Where("id IN ? AND disabled = ?", partnerIDs, false).Find(&users)

	type sortableResult struct {
		result   UserSearchResult
		lastSeen time.Time
	}
	sortable := make([]sortableResult, 0, len(users))
	for _, u := range users {
		p := partners[u.ID]
		sortable = append(sortable, sortableResult{
			result: UserSearchResult{
				ID:        strconv.FormatUint(uint64(u.ID), 10),
				Username:  u.Username,
				AvatarURL: u.AvatarURL,
			},
			lastSeen: p.lastSeen,
		})
	}

	for i := 0; i < len(sortable); i++ {
		for j := i + 1; j < len(sortable); j++ {
			if sortable[j].lastSeen.After(sortable[i].lastSeen) {
				sortable[i], sortable[j] = sortable[j], sortable[i]
			}
		}
	}

	if len(sortable) > 10 {
		sortable = sortable[:10]
	}

	result := make([]UserSearchResult, len(sortable))
	for i, s := range sortable {
		result[i] = s.result
	}

	return &GetRecentPartnersOutput{Body: result}, nil
}

// HumaGetPublicProfile is the huma handler for GET /api/users/{id}/profile.
func (h *SocialHandler) HumaGetPublicProfile(ctx context.Context, in *GetPublicProfileInput) (*GetPublicProfileOutput, error) {
	parsedID, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid user ID")
	}
	var user db.User
	if err := h.DB.First(&user, uint(parsedID)).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}

	uid := user.ID
	callerID := UserIDFromContext(ctx)

	// Issue #1121: respect block relationships in BOTH directions and the
	// target's profile_visibility setting. A block surfaces the same 404
	// we'd return for a nonexistent user (don't leak existence); a private
	// profile yields an empty view. The caller still sees their own profile.
	visible, blocked := publicProfileAccess(h.DB, callerID, user)
	if blocked {
		return nil, huma.Error404NotFound("user not found")
	}

	var agg struct {
		TotalPlayTime int64
		GamesPlayed   int64
	}
	favGames := []PublicProfileGame{}
	recentGames := []PublicProfileGame{}
	topGames := []PublicProfileGame{}
	isOnline := false
	var currentGame *OnlineUserGameResponse

	if visible {
		h.DB.Model(&db.PlayHistory{}).
			Where("user_id = ?", uid).
			Select("COALESCE(SUM(play_time), 0) as total_play_time, COUNT(*) as games_played").
			Scan(&agg)

		var favorites []db.Favorite
		h.DB.Where("user_id = ?", uid).
			Preload("Game").Preload("Game.Console").
			Limit(6).
			Find(&favorites)
		for _, f := range favorites {
			if f.Game.ID == 0 {
				continue
			}
			favGames = append(favGames, toPublicProfileGame(f.Game, 0))
		}

		var recentHistory []db.PlayHistory
		h.DB.Where("user_id = ?", uid).
			Preload("Game").Preload("Game.Console").
			Order("last_played DESC").
			Limit(6).
			Find(&recentHistory)
		for _, ph := range recentHistory {
			if ph.Game.ID == 0 {
				continue
			}
			recentGames = append(recentGames, toPublicProfileGame(ph.Game, ph.PlayTime))
		}

		var topHistory []db.PlayHistory
		h.DB.Where("user_id = ?", uid).
			Preload("Game").Preload("Game.Console").
			Order("play_time DESC").
			Limit(6).
			Find(&topHistory)
		for _, ph := range topHistory {
			if ph.Game.ID == 0 {
				continue
			}
			topGames = append(topGames, toPublicProfileGame(ph.Game, ph.PlayTime))
		}

		for _, oid := range h.Hub.GetOnlineUserIDs() {
			if oid == uid {
				isOnline = true
				break
			}
		}
		if isOnline {
			if gameID := h.Hub.GetUserGame(uid); gameID != 0 {
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
					currentGame = &OnlineUserGameResponse{
						ID:          strconv.FormatUint(uint64(game.ID), 10),
						Title:       game.Title,
						CoverURL:    coverURL,
						ConsoleName: consoleName,
					}
				}
			}
		}
	}

	return &GetPublicProfileOutput{Body: PublicProfileResponse{
		ID:            strconv.FormatUint(uint64(user.ID), 10),
		Username:      user.Username,
		AvatarURL:     user.AvatarURL,
		MemberSince:   user.CreatedAt,
		IsOnline:      isOnline,
		CurrentGame:   currentGame,
		TotalPlayTime: agg.TotalPlayTime,
		GamesPlayed:   agg.GamesPlayed,
		FavoriteGames: favGames,
		RecentGames:   recentGames,
		TopGames:      topGames,
	}}, nil
}

// isBlockedEitherWay returns true if a or b has blocked the other.
// Issue #1121: lookups respect blocks symmetrically — a blocked user
// shouldn't be able to scrape the blocker either.
func isBlockedEitherWay(database *gorm.DB, a, b uint) bool {
	var count int64
	database.Model(&db.Block{}).
		Where("(user_id = ? AND blocked_user_id = ?) OR (user_id = ? AND blocked_user_id = ?)", a, b, b, a).
		Count(&count)
	return count > 0
}

// publicProfileAccess centralises the #1121 privacy gate for every
// /api/users/{id}/... read that exposes per-user activity. When blocked is
// true the caller must return 404 (don't leak existence); when visible is
// false the target is private and the caller must return an empty/limited
// view. Issue #1316/#1320: the play-heatmap and achievement-showcase reads
// previously skipped this gate, so it now lives in one helper called by all
// three handlers.
func publicProfileAccess(database *gorm.DB, callerID uint, target db.User) (visible, blocked bool) {
	if callerID != 0 && callerID != target.ID && isBlockedEitherWay(database, callerID, target.ID) {
		return false, true
	}
	visible = target.ProfileVisibility == "" || target.ProfileVisibility == "public" || callerID == target.ID
	return visible, false
}
