package federation

import (
	"encoding/json"

	"github.com/spela/server/internal/db"
)

// DataClass enumerates federated data categories, each with its own reach and
// consent policy. See the matrix in docs/federation-mesh-exploration.md.
type DataClass string

const (
	DataClassStats       DataClass = "stats"        // aggregate top-lists / playtime
	DataClassTopPlayers  DataClass = "top_players"  // personal leaderboards
	DataClassCatalog     DataClass = "catalog"      // game availability metadata
	DataClassDownload    DataClass = "download"     // ROM relay
	DataClassPresence    DataClass = "presence"     // who's playing now
	DataClassReviews     DataClass = "reviews"      // ratings/reviews
	DataClassAchievement DataClass = "achievements" // achievements/challenges
)

// AllDataClasses is the canonical list, for UI enumeration and defaults.
var AllDataClasses = []DataClass{
	DataClassStats, DataClassTopPlayers, DataClassCatalog, DataClassDownload,
	DataClassPresence, DataClassReviews, DataClassAchievement,
}

// MarshalPolicy serializes a data-class -> allowed map to JSON for storage.
func MarshalPolicy(p map[DataClass]bool) (string, error) {
	if p == nil {
		p = map[DataClass]bool{}
	}
	b, err := json.Marshal(p)
	if err != nil {
		return "", err
	}
	return string(b), nil
}

// ParsePolicy parses a stored policy JSON string. Empty string => deny-all.
func ParsePolicy(s string) (map[DataClass]bool, error) {
	out := map[DataClass]bool{}
	if s == "" {
		return out, nil
	}
	if err := json.Unmarshal([]byte(s), &out); err != nil {
		return nil, err
	}
	return out, nil
}

// CanShare reports whether we expose the given class to this peer.
func CanShare(peer db.FederationPeer, class DataClass) bool {
	p, err := ParsePolicy(peer.SharePolicy)
	if err != nil {
		return false
	}
	return p[class]
}

// CanConsume reports whether we accept the given class from this peer.
func CanConsume(peer db.FederationPeer, class DataClass) bool {
	p, err := ParsePolicy(peer.ConsumePolicy)
	if err != nil {
		return false
	}
	return p[class]
}
