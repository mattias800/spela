package federation

import (
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// PresenceEntry is one source-stamped "user is playing this game right now" datum
// from a single origin server. Hops is the graph distance from the holder to the
// origin (0 = originated here, 1 = a direct connected server).
//
// Unlike StatEntry, presence is EPHEMERAL: it is never cached in a snapshot table
// and never re-served transitively. Each consumer pulls it live from its direct
// connected servers (one hop) at read time, so a stale 15-minute cache can't
// claim someone is playing a game they quit ten minutes ago.
type PresenceEntry struct {
	OriginFingerprint string `json:"originFingerprint"`
	Hops              int    `json:"hops"`
	Username          string `json:"username"`
	// GameKey is the cross-server game identity (IGDB scraper id, else CRC32), so
	// a client can link a presence to a remote game it can browse/import. Sessions
	// on games with no cross-identifier are not federated (see BuildLocalPresence).
	GameKey   string `json:"gameKey"`
	GameTitle string `json:"gameTitle"`
	// ServerName is a friendly label for the origin server, filled in by the
	// consumer from its peer roster. Empty means this (local) server. It exists so
	// the user-facing view can show "playing on <server>" without exposing the
	// admin-only peer fingerprints.
	ServerName string `json:"serverName"`
}

// PlayingSession is one active local game session (userID + gameID), the input
// to BuildLocalPresence. It mirrors the websocket hub's snapshot so the
// federation domain stays free of a websocket dependency — the api layer maps
// the hub's sessions onto this type.
type PlayingSession struct {
	UserID uint
	GameID uint
}

// presenceDedupeKey identifies a presence by origin + user, so the same session
// reaching us via multiple paths is shown exactly once.
type presenceDedupeKey struct {
	origin   string
	username string
}

// DedupePresenceEntries removes duplicate (origin, username) entries, keeping the
// FIRST occurrence (diamond-safe, idempotent). Same rationale as
// DedupeStatEntries: first-seen prevents a near (possibly misbehaving) relay's
// copy from overriding the origin's honest copy arriving via a longer path.
func DedupePresenceEntries(entries []PresenceEntry) []PresenceEntry {
	seen := make(map[presenceDedupeKey]bool, len(entries))
	out := make([]PresenceEntry, 0, len(entries))
	for _, e := range entries {
		k := presenceDedupeKey{e.OriginFingerprint, e.Username}
		if seen[k] {
			continue
		}
		seen[k] = true
		out = append(out, e)
	}
	return out
}

// BuildLocalPresence turns the hub's active sessions into source-stamped
// PresenceEntry values (origin = selfFingerprint, hop 0). It applies the same
// origin-side privacy gate as the stats rollup (publicActiveUserFilter), so
// private, disabled, and pending-approval users never appear in the mesh, and it
// drops sessions on games with no cross-server identifier (a title fallback
// would falsely merge distinct games).
func BuildLocalPresence(database *gorm.DB, selfFingerprint string, sessions []PlayingSession) ([]PresenceEntry, error) {
	if len(sessions) == 0 {
		return []PresenceEntry{}, nil
	}

	userIDs := make([]uint, 0, len(sessions))
	gameIDs := make([]uint, 0, len(sessions))
	seenU := make(map[uint]bool, len(sessions))
	seenG := make(map[uint]bool, len(sessions))
	for _, s := range sessions {
		if !seenU[s.UserID] {
			seenU[s.UserID] = true
			userIDs = append(userIDs, s.UserID)
		}
		if !seenG[s.GameID] {
			seenG[s.GameID] = true
			gameIDs = append(gameIDs, s.GameID)
		}
	}

	// Public + active users only: this is the origin-side privacy gate.
	var users []db.User
	if err := database.Where("id IN ?", userIDs).
		Where(publicActiveUserFilter, "public", false, false).
		Find(&users).Error; err != nil {
		return nil, err
	}
	userByID := make(map[uint]db.User, len(users))
	for _, u := range users {
		userByID[u.ID] = u
	}

	var games []db.Game
	if err := database.Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		return nil, err
	}
	gameByID := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameByID[g.ID] = g
	}

	entries := make([]PresenceEntry, 0, len(sessions))
	for _, s := range sessions {
		u, ok := userByID[s.UserID]
		if !ok {
			continue // private / disabled / pending — not federated
		}
		g, ok := gameByID[s.GameID]
		if !ok {
			continue
		}
		key, hasKey := gameStatKey(g)
		if !hasKey {
			continue // can't cross-identify this game — don't federate it
		}
		entries = append(entries, PresenceEntry{
			OriginFingerprint: selfFingerprint, Hops: 0,
			Username: u.Username, GameKey: key, GameTitle: g.Title,
		})
	}
	return entries, nil
}
