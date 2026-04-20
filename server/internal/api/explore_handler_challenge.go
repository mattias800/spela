package api

import (
	"time"
)

// --- Phase 12: Achievement & Challenge-Driven Discovery ---
//
// The gin handlers GetEasyToComplete, GetHardestGames, GetAlmostDone,
// GetFreshChallenges, and GetActiveChallenges have been migrated to huma —
// see huma_explore_challenge.go. Only the shared wire-format types remain
// here because they are still referenced by the huma handlers and by the
// existing test suite.

// AchievementGameResponse is one game with aggregated achievement stats across all players.
type AchievementGameResponse struct {
	Game              GameResponse `json:"game"`
	TotalAchievements int          `json:"totalAchievements"`
	AvgCompletion     float64      `json:"avgCompletion"`
	PlayersAttempted  int          `json:"playersAttempted"`
	PlayersCompleted  int          `json:"playersCompleted"`
}

// EasyToCompleteResponse is the API response for the easy-to-complete endpoint.
type EasyToCompleteResponse struct {
	Games []AchievementGameResponse `json:"games"`
}

// HardestGamesResponse is the API response for the hardest-games endpoint.
type HardestGamesResponse struct {
	Games []AchievementGameResponse `json:"games"`
}

// AlmostDoneGame is one game the current user has nearly completed (80-99%).
type AlmostDoneGame struct {
	Game              GameResponse `json:"game"`
	UnlockedCount     int          `json:"unlockedCount"`
	TotalCount        int          `json:"totalCount"`
	CompletionPercent float64      `json:"completionPercent"`
}

// AlmostDoneResponse is the API response for the almost-done endpoint.
type AlmostDoneResponse struct {
	Games []AlmostDoneGame `json:"games"`
}

// FreshChallengeGame is a game with cached achievements that the user hasn't started.
type FreshChallengeGame struct {
	Game              GameResponse `json:"game"`
	TotalAchievements int          `json:"totalAchievements"`
	TotalPoints       int          `json:"totalPoints"`
}

// FreshChallengesResponse is the API response for the fresh-challenges endpoint.
type FreshChallengesResponse struct {
	Games []FreshChallengeGame `json:"games"`
}

// ExploreChallengeResponse is a lightweight challenge representation for the explore page.
type ExploreChallengeResponse struct {
	ID              string     `json:"id"`
	CreatorUsername string     `json:"creatorUsername"`
	GameID          string     `json:"gameId"`
	GameTitle       string     `json:"gameTitle"`
	GameCoverURL    string     `json:"gameCoverUrl"`
	ConsoleName     string     `json:"consoleName"`
	Name            string     `json:"name"`
	Description     string     `json:"description"`
	Type            string     `json:"type"`
	Difficulty      string     `json:"difficulty"`
	AttemptCount    int        `json:"attemptCount"`
	CompletionCount int        `json:"completionCount"`
	ExpiresAt       *time.Time `json:"expiresAt"`
	CreatedAt       time.Time  `json:"createdAt"`
}

// ActiveChallengesResponse is the API response for the active-challenges endpoint.
type ActiveChallengesResponse struct {
	Challenges []ExploreChallengeResponse `json:"challenges"`
}
