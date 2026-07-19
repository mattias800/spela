package api

// Activity event metadata variants. The wire format for each is a flat JSON
// object — these structs exist to make producers type-safe at the call site.
// Each struct's JSON tags match the historical map[string]interface{} keys
// exactly so the on-the-wire shape (consumed by web and player clients) is
// unchanged.

// ActivityEventMetadata is the response-side union of every per-event-type
// metadata shape below. Exactly the fields of the producing struct are set;
// all others are omitted (pointer + omitempty), so the wire shape matches the
// historical map[string]interface{} representation. Keeping this a closed,
// typed schema — instead of a free-form map — means the OpenAPI spec carries
// real field types and generated clients (Kotlin player, web TS) catch shape
// drift at build time rather than failing to deserialize at runtime (#1675).
// When adding a new metadata struct below, add its fields here too.
type ActivityEventMetadata struct {
	Seconds           *int64  `json:"seconds,omitempty"`
	Rating            *int    `json:"rating,omitempty"`
	SaveName          *string `json:"saveName,omitempty"`
	CollectionName    *string `json:"collectionName,omitempty"`
	SharedSessionName *string `json:"sharedSessionName,omitempty"`
	ChallengeID       *uint   `json:"challengeId,omitempty"`
	ChallengeName     *string `json:"challengeName,omitempty"`
	DurationMs        *int64  `json:"durationMs,omitempty"`
}

// StartedPlayingMetadata is the metadata shape for the "started_playing"
// activity event. Seconds is the user's reported play-time increment for
// this session.
type StartedPlayingMetadata struct {
	Seconds int64 `json:"seconds"`
}

// RatedGameMetadata is the metadata shape for the "rated_game" activity event.
type RatedGameMetadata struct {
	Rating int `json:"rating"`
}

// SharedSaveMetadata is the metadata shape for the "shared_save" activity event.
type SharedSaveMetadata struct {
	SaveName string `json:"saveName"`
}

// CreatedCollectionMetadata is the metadata shape for the
// "created_collection" activity event.
type CreatedCollectionMetadata struct {
	CollectionName string `json:"collectionName"`
}

// CreatedSharedSessionMetadata is the metadata shape for the
// "created_shared_session" activity event.
type CreatedSharedSessionMetadata struct {
	SharedSessionName string `json:"sharedSessionName"`
}

// JoinedSharedSessionMetadata is the metadata shape for the
// "joined_shared_session" activity event.
type JoinedSharedSessionMetadata struct {
	SharedSessionName string `json:"sharedSessionName"`
}

// ChallengeCreatedMetadata is the metadata shape for the "challenge_created"
// activity event.
type ChallengeCreatedMetadata struct {
	ChallengeID   uint   `json:"challengeId"`
	ChallengeName string `json:"challengeName"`
}

// ChallengeCompletedMetadata is the metadata shape for the
// "challenge_completed" activity event.
type ChallengeCompletedMetadata struct {
	ChallengeID   uint   `json:"challengeId"`
	ChallengeName string `json:"challengeName"`
	DurationMs    int64  `json:"durationMs"`
}

// ChallengeRecordMetadata is the metadata shape for the "challenge_record"
// activity event (fired when an attempt becomes the new #1 leaderboard entry).
type ChallengeRecordMetadata struct {
	ChallengeID   uint   `json:"challengeId"`
	ChallengeName string `json:"challengeName"`
	DurationMs    int64  `json:"durationMs"`
}
