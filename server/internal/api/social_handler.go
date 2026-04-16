package api

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// SocialHandler handles activity feed and online status endpoints.
type SocialHandler struct {
	DB  *gorm.DB
	Hub *ws.Hub
}

// GetOnlineUsers returns users currently connected via WebSocket.
func (h *SocialHandler) GetOnlineUsers(c *gin.Context) {
	onlineIDs := h.Hub.GetOnlineUserIDs()

	if len(onlineIDs) == 0 {
		c.JSON(http.StatusOK, gin.H{"users": []OnlineUserResponse{}})
		return
	}

	var users []db.User
	if err := h.DB.Where("id IN ?", onlineIDs).Find(&users).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch online users"})
		return
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

	c.JSON(http.StatusOK, gin.H{"users": result})
}

// GetPublicProfile returns a user's public profile with stats and game lists.
func (h *SocialHandler) GetPublicProfile(c *gin.Context) {
	userID := c.Param("id")

	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "user not found"})
		return
	}

	uid := user.ID

	// Aggregate stats
	var agg struct {
		TotalPlayTime int64
		GamesPlayed   int64
	}
	h.DB.Model(&db.PlayHistory{}).
		Where("user_id = ?", uid).
		Select("COALESCE(SUM(play_time), 0) as total_play_time, COUNT(*) as games_played").
		Scan(&agg)

	// Favorite games (up to 6)
	var favorites []db.Favorite
	h.DB.Where("user_id = ?", uid).
		Preload("Game").Preload("Game.Console").
		Limit(6).
		Find(&favorites)

	favGames := make([]PublicProfileGame, 0, len(favorites))
	for _, f := range favorites {
		if f.Game.ID == 0 {
			continue
		}
		favGames = append(favGames, toPublicProfileGame(f.Game, 0))
	}

	// Recent games (up to 6)
	var recentHistory []db.PlayHistory
	h.DB.Where("user_id = ?", uid).
		Preload("Game").Preload("Game.Console").
		Order("last_played DESC").
		Limit(6).
		Find(&recentHistory)

	recentGames := make([]PublicProfileGame, 0, len(recentHistory))
	for _, ph := range recentHistory {
		if ph.Game.ID == 0 {
			continue
		}
		recentGames = append(recentGames, toPublicProfileGame(ph.Game, ph.PlayTime))
	}

	// Top games by play time (up to 6)
	var topHistory []db.PlayHistory
	h.DB.Where("user_id = ?", uid).
		Preload("Game").Preload("Game.Console").
		Order("play_time DESC").
		Limit(6).
		Find(&topHistory)

	topGames := make([]PublicProfileGame, 0, len(topHistory))
	for _, ph := range topHistory {
		if ph.Game.ID == 0 {
			continue
		}
		topGames = append(topGames, toPublicProfileGame(ph.Game, ph.PlayTime))
	}

	// Online status
	isOnline := false
	var currentGame *OnlineUserGameResponse
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

	c.JSON(http.StatusOK, PublicProfileResponse{
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
	})
}

func toPublicProfileGame(g db.Game, playTime int64) PublicProfileGame {
	coverURL := g.CoverURL
	if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
		coverURL = "/api/images/" + coverURL
	}
	consoleName := ""
	if g.Console.ID != 0 {
		consoleName = g.Console.Name
	}
	return PublicProfileGame{
		ID:          strconv.FormatUint(uint64(g.ID), 10),
		Title:       g.Title,
		CoverURL:    coverURL,
		ConsoleName: consoleName,
		PlayTime:    playTime,
	}
}

// SearchUsers returns users matching a search query, excluding the current user.
// Supports pagination via page and pageSize query params (defaults: page=1, pageSize=20, max 50).
// If q is empty, returns all non-disabled users (excluding self).
func (h *SocialHandler) SearchUsers(c *gin.Context) {
	uid := getUserID(c)
	query := strings.TrimSpace(c.Query("q"))

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to search users"})
		return
	}

	result := make([]UserSearchResult, 0, len(users))
	for _, u := range users {
		result = append(result, UserSearchResult{
			ID:        strconv.FormatUint(uint64(u.ID), 10),
			Username:  u.Username,
			AvatarURL: u.AvatarURL,
		})
	}

	c.JSON(http.StatusOK, PaginatedResponse[UserSearchResult]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// GetRecentPartners returns users the current user has played with recently
// (last 6 months) via netplay sessions or shared sessions.
func (h *SocialHandler) GetRecentPartners(c *gin.Context) {
	uid := getUserID(c)
	sixMonthsAgo := time.Now().AddDate(0, -6, 0)

	// partnerInfo tracks the most recent interaction time per partner.
	type partnerInfo struct {
		lastSeen time.Time
	}
	partners := make(map[uint]*partnerInfo)

	// 1. NetplaySession: find sessions where the current user was host or client.
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

	// 2. SharedSessionMember: find shared sessions the user is a member of,
	//    then find other members of those sessions.
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

		// Look up creation time of the shared sessions for recency sorting.
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
				// Session was created more than 6 months ago; skip.
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
		c.JSON(http.StatusOK, []UserSearchResult{})
		return
	}

	// Collect partner IDs and sort by most recent first.
	partnerIDs := make([]uint, 0, len(partners))
	for id := range partners {
		partnerIDs = append(partnerIDs, id)
	}

	// Fetch user records, excluding disabled users.
	var users []db.User
	h.DB.Where("id IN ? AND disabled = ?", partnerIDs, false).Find(&users)

	// Build response sorted by most recent interaction.
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

	// Sort by lastSeen descending (most recent first).
	for i := 0; i < len(sortable); i++ {
		for j := i + 1; j < len(sortable); j++ {
			if sortable[j].lastSeen.After(sortable[i].lastSeen) {
				sortable[i], sortable[j] = sortable[j], sortable[i]
			}
		}
	}

	// Limit to 10 results.
	if len(sortable) > 10 {
		sortable = sortable[:10]
	}

	result := make([]UserSearchResult, len(sortable))
	for i, s := range sortable {
		result[i] = s.result
	}

	c.JSON(http.StatusOK, result)
}

// GetActivityFeed returns a paginated activity feed (most recent first).
func (h *SocialHandler) GetActivityFeed(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch activity feed"})
		return
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

		var metadata map[string]interface{}
		if e.Metadata != "" {
			if err := json.Unmarshal([]byte(e.Metadata), &metadata); err != nil {
				slog.Warn("failed to parse activity event metadata", "error", err, "eventId", e.ID)
			}
		}

		result = append(result, ActivityEventResponse{
			ID:           strconv.FormatUint(uint64(e.ID), 10),
			EventType:    e.EventType,
			CreatedAt:    e.CreatedAt,
			UserID:       strconv.FormatUint(uint64(e.UserID), 10),
			Username:     e.User.Username,
			AvatarURL:    e.User.AvatarURL,
			GameID:       strconv.FormatUint(uint64(e.GameID), 10),
			GameTitle:    e.Game.Title,
			GameCoverURL: coverURL,
			ConsoleName:  consoleName,
			Metadata:     metadata,
		})
	}

	c.JSON(http.StatusOK, PaginatedResponse[ActivityEventResponse]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// CreateActivityEvent creates a new activity event and broadcasts it via WebSocket.
func CreateActivityEvent(database *gorm.DB, hub *ws.Hub, userID uint, eventType string, gameID uint, metadata map[string]interface{}) {
	metadataJSON := ""
	if metadata != nil {
		b, err := json.Marshal(metadata)
		if err == nil {
			metadataJSON = string(b)
		}
	}

	event := db.ActivityEvent{
		UserID:    userID,
		EventType: eventType,
		GameID:    gameID,
		Metadata:  metadataJSON,
	}

	if err := database.Create(&event).Error; err != nil {
		slog.Error("failed to create activity event", "error", err, "eventType", eventType, "userId", userID)
		return
	}

	// Load user and game info for the broadcast
	var user db.User
	database.First(&user, userID)
	var game db.Game
	database.Preload("Console").First(&game, gameID)

	coverURL := game.CoverURL
	if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
		coverURL = "/api/images/" + coverURL
	}

	consoleName := ""
	if game.Console.ID != 0 {
		consoleName = game.Console.Name
	}

	resp := ActivityEventResponse{
		ID:           strconv.FormatUint(uint64(event.ID), 10),
		EventType:    eventType,
		CreatedAt:    event.CreatedAt,
		UserID:       strconv.FormatUint(uint64(userID), 10),
		Username:     user.Username,
		AvatarURL:    user.AvatarURL,
		GameID:       strconv.FormatUint(uint64(gameID), 10),
		GameTitle:    game.Title,
		GameCoverURL: coverURL,
		ConsoleName:  consoleName,
		Metadata:     metadata,
	}

	if hub != nil {
		hub.Broadcast(ws.Event{Type: "activity_new", Payload: resp})
	}
}
