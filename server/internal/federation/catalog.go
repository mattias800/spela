package federation

import (
	"sort"
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// CatalogEntry is a source-stamped record that a game is available on some
// server in the mesh. Key is the cross-server game id (IGDB scraper id / CRC32).
type CatalogEntry struct {
	OriginFingerprint string `json:"originFingerprint"`
	Hops              int    `json:"hops"`
	Key               string `json:"key"`
	Title             string `json:"title"`
	Console           string `json:"console"` // console abbreviation, e.g. "SNES"
}

// crossGameKey derives a game's cross-server identity from its scraper id /
// CRC32. ok=false when neither is present (the game can't be reliably matched
// across servers, so it isn't federated).
func crossGameKey(scraperID, crc32 string) (string, bool) {
	if scraperID != "" {
		return scraperID, true
	}
	if crc32 != "" {
		return "crc:" + crc32, true
	}
	return "", false
}

// BuildLocalCatalog lists this server's games as source-stamped catalog entries
// (origin = self, hop 0), one per cross-identifiable game key. Catalog data is
// non-personal (it's about the library, not users), so there is no per-user
// gate — exposure is governed per-friend by SharePolicy(catalog).
func BuildLocalCatalog(database *gorm.DB, selfFingerprint string) ([]CatalogEntry, error) {
	type row struct {
		ScraperID string
		CRC32     string
		Title     string
		Console   string
	}
	var rows []row
	if err := database.Model(&db.Game{}).
		Select("games.scraper_id as scraper_id, games.crc32 as crc32, games.title as title, consoles.abbreviation as console").
		Joins("JOIN consoles ON consoles.id = games.console_id").
		Scan(&rows).Error; err != nil {
		return nil, err
	}

	seen := make(map[string]bool, len(rows))
	out := make([]CatalogEntry, 0, len(rows))
	for _, r := range rows {
		key, ok := crossGameKey(r.ScraperID, r.CRC32)
		if !ok || seen[key] {
			continue
		}
		seen[key] = true
		out = append(out, CatalogEntry{
			OriginFingerprint: selfFingerprint, Hops: 0,
			Key: key, Title: r.Title, Console: r.Console,
		})
	}
	return out, nil
}

// CatalogSnapshotStore persists catalog entries pulled from direct friends for
// transitive discovery (#1348). Mirrors SnapshotStore.
type CatalogSnapshotStore struct {
	DB *gorm.DB
}

func (s CatalogSnapshotStore) ReplacePeerSnapshot(sourcePeerFingerprint string, entries []CatalogEntry, fetchedAt time.Time) error {
	return s.DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Where("source_peer_fingerprint = ?", sourcePeerFingerprint).
			Delete(&db.FederationCatalogSnapshot{}).Error; err != nil {
			return err
		}
		if len(entries) == 0 {
			return nil
		}
		rows := make([]db.FederationCatalogSnapshot, 0, len(entries))
		for _, e := range entries {
			rows = append(rows, db.FederationCatalogSnapshot{
				SourcePeerFingerprint: sourcePeerFingerprint,
				OriginFingerprint:     e.OriginFingerprint,
				Hops:                  e.Hops,
				Key:                   e.Key,
				Title:                 e.Title,
				Console:               e.Console,
				FetchedAt:             fetchedAt,
			})
		}
		return tx.Create(&rows).Error
	})
}

func (s CatalogSnapshotStore) RemovePeerSnapshot(sourcePeerFingerprint string) error {
	return s.DB.Where("source_peer_fingerprint = ?", sourcePeerFingerprint).
		Delete(&db.FederationCatalogSnapshot{}).Error
}

// SourcePeersForKey returns the distinct DIRECT friends (source peers) whose
// catalog offers the given game key, at ANY depth — the friends we can request a
// download from (they either have it locally or relay it onward). Used by the
// Phase 3b download paths.
func (s CatalogSnapshotStore) SourcePeersForKey(key string) ([]string, error) {
	var fps []string
	if err := s.DB.Model(&db.FederationCatalogSnapshot{}).
		Where("key = ?", key).
		Distinct("source_peer_fingerprint").
		Pluck("source_peer_fingerprint", &fps).Error; err != nil {
		return nil, err
	}
	return fps, nil
}

func (s CatalogSnapshotStore) EntriesWithinHops(maxHops int) ([]CatalogEntry, error) {
	q := s.DB.Model(&db.FederationCatalogSnapshot{})
	if maxHops >= 0 {
		q = q.Where("hops <= ?", maxHops)
	}
	var rows []db.FederationCatalogSnapshot
	if err := q.Find(&rows).Error; err != nil {
		return nil, err
	}
	out := make([]CatalogEntry, 0, len(rows))
	for _, r := range rows {
		out = append(out, CatalogEntry{
			OriginFingerprint: r.OriginFingerprint, Hops: r.Hops,
			Key: r.Key, Title: r.Title, Console: r.Console,
		})
	}
	return out, nil
}

// CatalogAvailability is a per-game discovery row: how many distinct servers in
// reach have it, and whether THIS server already has it. Peer fingerprints are
// not exposed (admin-only) — only counts.
type CatalogAvailability struct {
	Key         string `json:"key"`
	Title       string `json:"title"`
	Console     string `json:"console"`
	OriginCount int    `json:"originCount"` // distinct servers (incl. local) that have it
	Local       bool   `json:"local"`       // does this server have it
}

// AggregateCatalog dedupes (by origin, key) then groups by game key: counts the
// distinct origins offering each game and flags local availability. Sorted by
// title for stable output.
func AggregateCatalog(entries []CatalogEntry, selfFingerprint string) []CatalogAvailability {
	type acc struct {
		title   string
		console string
		origins map[string]bool
		local   bool
	}
	byKey := map[string]*acc{}
	order := []string{}
	seen := map[statCatalogDedupeKey]bool{}
	for _, e := range entries {
		dk := statCatalogDedupeKey{e.OriginFingerprint, e.Key}
		if seen[dk] {
			continue
		}
		seen[dk] = true

		a, ok := byKey[e.Key]
		if !ok {
			a = &acc{title: e.Title, console: e.Console, origins: map[string]bool{}}
			byKey[e.Key] = a
			order = append(order, e.Key)
		}
		if a.title == "" {
			a.title = e.Title
		}
		if a.console == "" {
			a.console = e.Console
		}
		a.origins[e.OriginFingerprint] = true
		if e.OriginFingerprint == selfFingerprint {
			a.local = true
		}
	}

	out := make([]CatalogAvailability, 0, len(order))
	for _, key := range order {
		a := byKey[key]
		out = append(out, CatalogAvailability{
			Key: key, Title: a.title, Console: a.console,
			OriginCount: len(a.origins), Local: a.local,
		})
	}
	sort.SliceStable(out, func(i, j int) bool { return out[i].Title < out[j].Title })
	return out
}

type statCatalogDedupeKey struct {
	origin string
	key    string
}
