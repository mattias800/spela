package federation

import (
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// MaxFederationHops bounds how far stats propagate across the mesh: a datum more
// than this many hops from a server is dropped on ingest. This caps storage
// growth and prevents runaway loops — combined with dedupe-by-origin, a datum
// circulating the mesh can never be counted twice or grow without bound.
const MaxFederationHops = 4

// SnapshotStore persists stat entries pulled from direct friends so this server
// can re-serve them transitively without re-pulling on every request (#1347).
type SnapshotStore struct {
	DB *gorm.DB
}

// ReplacePeerSnapshot atomically replaces all cached entries from one direct
// friend with a fresh set. Idempotent per peer: refreshing with the same data
// yields the same rows.
func (s SnapshotStore) ReplacePeerSnapshot(sourcePeerFingerprint string, entries []StatEntry, fetchedAt time.Time) error {
	return s.DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Where("source_peer_fingerprint = ?", sourcePeerFingerprint).
			Delete(&db.FederationStatSnapshot{}).Error; err != nil {
			return err
		}
		if len(entries) == 0 {
			return nil
		}
		rows := make([]db.FederationStatSnapshot, 0, len(entries))
		for _, e := range entries {
			rows = append(rows, db.FederationStatSnapshot{
				SourcePeerFingerprint: sourcePeerFingerprint,
				OriginFingerprint:     e.OriginFingerprint,
				Hops:                  e.Hops,
				Metric:                string(e.Metric),
				Key:                   e.Key,
				Label:                 e.Label,
				PlayTimeSeconds:       e.PlayTimeSeconds,
				Players:               e.Players,
				FetchedAt:             fetchedAt,
			})
		}
		return tx.Create(&rows).Error
	})
}

// RemovePeerSnapshot drops a direct friend's cached entries (e.g. on revocation).
func (s SnapshotStore) RemovePeerSnapshot(sourcePeerFingerprint string) error {
	return s.DB.Where("source_peer_fingerprint = ?", sourcePeerFingerprint).
		Delete(&db.FederationStatSnapshot{}).Error
}

// EntriesWithinHops returns cached entries whose hop count is <= maxHops, as
// StatEntry values. A negative maxHops means no limit.
func (s SnapshotStore) EntriesWithinHops(maxHops int) ([]StatEntry, error) {
	q := s.DB.Model(&db.FederationStatSnapshot{})
	if maxHops >= 0 {
		q = q.Where("hops <= ?", maxHops)
	}
	var rows []db.FederationStatSnapshot
	if err := q.Find(&rows).Error; err != nil {
		return nil, err
	}
	out := make([]StatEntry, 0, len(rows))
	for _, r := range rows {
		out = append(out, StatEntry{
			OriginFingerprint: r.OriginFingerprint,
			Hops:              r.Hops,
			Metric:            StatMetric(r.Metric),
			Key:               r.Key,
			Label:             r.Label,
			PlayTimeSeconds:   r.PlayTimeSeconds,
			Players:           r.Players,
		})
	}
	return out, nil
}
