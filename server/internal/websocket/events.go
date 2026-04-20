package websocket

// This file defines the canonical WebSocket event type names (as string
// constants) and the typed payload structs that accompany them. Producers
// should always use these constants for the Event.Type field and one of the
// typed payload structs for the Event.Payload field — never literal strings
// or ad-hoc maps. This gives the compiler a chance to catch typos and
// field-shape drift at the point of broadcast.
//
// The wire format is unchanged: each event is marshalled as
// {"type": "...", "payload": {...}}. Typed payload structs carry explicit
// `json:"..."` tags so the rendered JSON matches the legacy gin.H / map
// shapes byte-for-byte.
//
// Some payloads are typed response structs defined in the api package
// (NetplaySessionResponse, SharedSessionResponse, ActivityEventResponse,
// etc.); those are passed directly and only the event-type constant is
// declared here. The scraper package exposes ScrapeProgress and
// EnrichProgress similarly.

// --- Event type constants ---

const (
	// Online presence.
	EventOnlineStatus = "online_status"

	// Game scanner.
	EventScanStarted  = "scan_started"
	EventScanProgress = "scan_progress"
	EventScanComplete = "scan_complete"
	EventScanError    = "scan_error"

	// Scraper pipeline.
	EventScrapeStarted     = "scrape_started"
	EventScrapeProgress    = "scrape_progress"
	EventScrapeComplete    = "scrape_complete"
	EventScrapeCancelled   = "scrape_cancelled"
	EventGameScrapeStatus  = "game_scrape_status"

	// Metadata enrichment.
	EventEnrichStarted  = "enrich_started"
	EventEnrichProgress = "enrich_progress"
	EventEnrichComplete = "enrich_complete"
	EventEnrichError    = "enrich_error"

	// BIOS downloads.
	EventBiosDownloadStarted  = "bios_download_started"
	EventBiosDownloadProgress = "bios_download_progress"
	EventBiosDownloadComplete = "bios_download_complete"

	// Activity feed.
	EventActivityNew = "activity_new"

	// Netplay sessions.
	EventNetplaySessionCreated  = "netplay_session_created"
	EventNetplayPlayerJoined    = "netplay_player_joined"
	EventNetplaySettingsUpdated = "netplay_settings_updated"
	EventNetplaySessionEnded    = "netplay_session_ended"
	EventNetplaySessionDeleted  = "netplay_session_deleted"

	// Netplay invites.
	EventNetplayInviteSent     = "netplay_invite_sent"
	EventNetplayInviteAccepted = "netplay_invite_accepted"
	EventNetplayInviteDeclined = "netplay_invite_declined"

	// Shared sessions.
	EventSharedSessionCreated         = "shared_session_created"
	EventSharedSessionUpdated         = "shared_session_updated"
	EventSharedSessionDeleted         = "shared_session_deleted"
	EventSharedSessionInviteSent      = "shared_session_invite_sent"
	EventSharedSessionInviteAccepted  = "shared_session_invite_accepted"
	EventSharedSessionInviteDeclined  = "shared_session_invite_declined"
	EventSharedSessionMemberLeft      = "shared_session_member_left"
	EventSharedSessionMemberRemoved   = "shared_session_member_removed"
	EventSharedSessionTurnAcquired    = "shared_session_turn_acquired"
	EventSharedSessionTurnReleased    = "shared_session_turn_released"
	EventSharedSessionTurnExpired     = "shared_session_turn_expired"
	EventSharedSessionSaveUploaded    = "shared_session_save_uploaded"

	// Challenges.
	EventChallengeLeaderboardUpdated = "challenge_leaderboard_updated"
)

// --- Typed payloads ---

// OnlineStatusPayload is the payload for EventOnlineStatus.
// Status is either "online" or "offline".
type OnlineStatusPayload struct {
	UserID uint   `json:"userId"`
	Status string `json:"status"`
}

// ScrapeStartedPayload is the payload for EventScrapeStarted.
type ScrapeStartedPayload struct {
	JobID uint   `json:"jobId"`
	Total int    `json:"total"`
	Mode  string `json:"mode"`
}

// ScrapeCompletePayload is the payload for EventScrapeComplete.
type ScrapeCompletePayload struct {
	Scraped int `json:"scraped"`
	Total   int `json:"total"`
}

// ScrapeCancelledPayload is the payload for EventScrapeCancelled.
// JobID is zero when cancellation is not tied to a specific job
// (for example, pre-empting a queued job before a new one starts).
type ScrapeCancelledPayload struct {
	JobID uint `json:"jobId"`
}

// GameScrapeStatusPayload is the payload for EventGameScrapeStatus.
// Status is typically "scraping" or "idle".
type GameScrapeStatusPayload struct {
	GameID uint   `json:"gameId"`
	Status string `json:"status"`
}

// EnrichStartedPayload is the payload for EventEnrichStarted.
type EnrichStartedPayload struct {
	Total int64 `json:"total"`
}

// EnrichCompletePayload is the payload for EventEnrichComplete.
type EnrichCompletePayload struct {
	Enriched int `json:"enriched"`
	Total    int `json:"total"`
}

// BiosDownloadStartedPayload is the payload for EventBiosDownloadStarted.
type BiosDownloadStartedPayload struct {
	Total int `json:"total"`
}

// BiosDownloadCompletePayload is the payload for EventBiosDownloadComplete.
type BiosDownloadCompletePayload struct {
	Downloaded int `json:"downloaded"`
	Skipped    int `json:"skipped"`
	Failed     int `json:"failed"`
}

// NetplaySessionEndedPayload is the payload for EventNetplaySessionEnded.
type NetplaySessionEndedPayload struct {
	SessionID string `json:"sessionId"`
	EndReason string `json:"endReason"`
}

// NetplaySessionDeletedPayload is the payload for EventNetplaySessionDeleted.
type NetplaySessionDeletedPayload struct {
	SessionID string `json:"sessionId"`
}

// NetplayInviteDeclinedPayload is the payload for EventNetplayInviteDeclined.
type NetplayInviteDeclinedPayload struct {
	NetplaySessionID string `json:"netplaySessionId"`
	InviteeID        string `json:"inviteeId"`
}

// SharedSessionDeletedPayload is the payload for EventSharedSessionDeleted.
type SharedSessionDeletedPayload struct {
	SharedSessionID string `json:"sharedSessionId"`
}

// SharedSessionInviteAcceptedPayload is the payload for EventSharedSessionInviteAccepted.
type SharedSessionInviteAcceptedPayload struct {
	SharedSessionID string `json:"sharedSessionId"`
	UserID          string `json:"userId"`
	Username        string `json:"username"`
}

// SharedSessionInviteDeclinedPayload is the payload for EventSharedSessionInviteDeclined.
type SharedSessionInviteDeclinedPayload struct {
	SharedSessionID string `json:"sharedSessionId"`
	InviteeID       string `json:"inviteeId"`
}

// SharedSessionMemberLeftPayload is the payload for EventSharedSessionMemberLeft.
type SharedSessionMemberLeftPayload struct {
	SharedSessionID string `json:"sharedSessionId"`
	UserID          string `json:"userId"`
}

// SharedSessionMemberRemovedPayload is the payload for EventSharedSessionMemberRemoved.
type SharedSessionMemberRemovedPayload struct {
	SharedSessionID string `json:"sharedSessionId"`
	UserID          string `json:"userId"`
}

// SharedSessionTurnAcquiredPayload is the payload for EventSharedSessionTurnAcquired.
type SharedSessionTurnAcquiredPayload struct {
	SharedSessionID string `json:"sharedSessionId"`
	UserID          string `json:"userId"`
	Username        string `json:"username"`
}

// SharedSessionTurnReleasedPayload is the payload for EventSharedSessionTurnReleased.
type SharedSessionTurnReleasedPayload struct {
	SharedSessionID string `json:"sharedSessionId"`
	UserID          string `json:"userId"`
}

// SharedSessionTurnExpiredPayload is the payload for EventSharedSessionTurnExpired.
type SharedSessionTurnExpiredPayload struct {
	SharedSessionID string `json:"sharedSessionId"`
	PreviousUserID  string `json:"previousUserId"`
}

// SharedSessionSaveUploadedPayload is the payload for EventSharedSessionSaveUploaded.
type SharedSessionSaveUploadedPayload struct {
	SharedSessionID string `json:"sharedSessionId"`
	SaveID          string `json:"saveId"`
	UserID          string `json:"userId"`
	IsAuto          bool   `json:"isAuto"`
}

// ChallengeLeaderboardUpdatedPayload is the payload for
// EventChallengeLeaderboardUpdated.
type ChallengeLeaderboardUpdatedPayload struct {
	ChallengeID string `json:"challengeId"`
}
