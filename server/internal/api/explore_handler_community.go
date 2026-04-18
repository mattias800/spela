package api

import (
	"time"
)

// --- Phase 10: Social & Community Discovery ---
//
// The gin handlers that lived here (GetTrending, GetCommunityTop, GetCultClassics,
// GetRecentlyReviewed, GetActiveNow) have been migrated to huma — see
// huma_explore_community.go. Only the shared wire-format types remain here
// because they are still referenced by the huma handlers and by the existing
// test suite.

// TrendingGameResponse is one game in the trending shelf, with player count this week.
type TrendingGameResponse struct {
	Game            GameResponse `json:"game"`
	PlayersThisWeek int          `json:"playersThisWeek"`
}

// TrendingResponse is the API response for the trending endpoint.
type TrendingResponse struct {
	Games []TrendingGameResponse `json:"games"`
}

// CommunityTopGame is one game in the community-top shelf.
type CommunityTopGame struct {
	Game        GameResponse `json:"game"`
	AvgRating   float64      `json:"avgRating"`
	RatingCount int          `json:"ratingCount"`
}

// CommunityTopResponse is the API response for the community-top endpoint.
type CommunityTopResponse struct {
	Games []CommunityTopGame `json:"games"`
}

// CultClassicGame is one game in the cult classics shelf.
type CultClassicGame struct {
	Game              GameResponse `json:"game"`
	CommunityRating   float64      `json:"communityRating"`
	IGDBCriticsRating float64      `json:"igdbCriticsRating"`
	RatingCount       int          `json:"ratingCount"`
}

// CultClassicsResponse is the API response for the cult-classics endpoint.
type CultClassicsResponse struct {
	Games []CultClassicGame `json:"games"`
}

// RecentReviewItem is one review in the recently-reviewed shelf.
type RecentReviewItem struct {
	Game         GameResponse `json:"game"`
	Rating       int          `json:"rating"`
	Review       string       `json:"review"`
	ReviewerName string       `json:"reviewerName"`
	ReviewedAt   time.Time    `json:"reviewedAt"`
}

// RecentlyReviewedResponse is the API response for the recently-reviewed endpoint.
type RecentlyReviewedResponse struct {
	Reviews []RecentReviewItem `json:"reviews"`
}

// ActiveNowItem is one active game in the active-now shelf.
type ActiveNowItem struct {
	Game             GameResponse `json:"game"`
	ActiveSessions   int          `json:"activeSessions"`
	ActiveChallenges int          `json:"activeChallenges"`
}

// ActiveNowResponse is the API response for the active-now endpoint.
type ActiveNowResponse struct {
	Games []ActiveNowItem `json:"games"`
}
