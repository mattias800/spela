package federation

import "sort"

// StatMetric identifies an aggregate leaderboard metric.
type StatMetric string

const (
	MetricGamePlay   StatMetric = "game_play"   // play time + distinct players per game
	MetricPlayerPlay StatMetric = "player_play" // play time per player
)

// StatEntry is one source-stamped aggregate datum from a single origin server.
// Hops is the graph distance from the holder to the origin (0 = originated
// here). Phase 1 only produces/consumes hop 0 (own) and hop 1 (direct friend);
// deeper hops arrive in Phase 2.
type StatEntry struct {
	OriginFingerprint string     `json:"originFingerprint"`
	Hops              int        `json:"hops"`
	Metric            StatMetric `json:"metric"`
	// Key is the stable identity within a metric: for game_play the cross-server
	// game id (IGDB scraper id); for player_play the player's identity. The same
	// game across servers shares a Key and is summed; players are scoped to their
	// origin (cross-server player identity is a later concern).
	Key             string `json:"key"`
	Label           string `json:"label"` // display label (game title / username)
	PlayTimeSeconds int64  `json:"playTimeSeconds"`
	Players         int64  `json:"players"` // distinct players (game metric); 0 for player metric
}

// statDedupeKey identifies a datum by origin + metric + key, so the same source
// datum arriving via multiple paths is counted exactly once.
type statDedupeKey struct {
	origin string
	metric StatMetric
	key    string
}

// DedupeStatEntries removes duplicate (origin, metric, key) entries, keeping the
// copy with the smallest hop count. This is what makes mesh aggregation correct
// when one source is reachable via more than one friend (the diamond case): a
// source must not be counted twice. Idempotent —
// DedupeStatEntries(DedupeStatEntries(x)) == DedupeStatEntries(x).
func DedupeStatEntries(entries []StatEntry) []StatEntry {
	best := make(map[statDedupeKey]StatEntry, len(entries))
	order := make([]statDedupeKey, 0, len(entries))
	for _, e := range entries {
		k := statDedupeKey{e.OriginFingerprint, e.Metric, e.Key}
		if existing, ok := best[k]; ok {
			if e.Hops < existing.Hops {
				best[k] = e
			}
			continue
		}
		best[k] = e
		order = append(order, k)
	}
	out := make([]StatEntry, 0, len(order))
	for _, k := range order {
		out = append(out, best[k])
	}
	return out
}

// AggregatedStat is a leaderboard row summed across all contributing origins,
// retaining the per-source breakdown for transparency and anti-inflation.
type AggregatedStat struct {
	Metric        StatMetric  `json:"metric"`
	Key           string      `json:"key"`
	Label         string      `json:"label"`
	TotalPlayTime int64       `json:"totalPlayTimeSeconds"`
	TotalPlayers  int64       `json:"totalPlayers"`
	Sources       []StatEntry `json:"sources"`
}

// AggregateStatEntries dedupes (by origin) then sums entries by (metric, key)
// across origins, producing leaderboard rows sorted by total play time
// descending. Each row keeps its per-source breakdown so an admin can see who
// contributed what.
func AggregateStatEntries(entries []StatEntry) []AggregatedStat {
	deduped := DedupeStatEntries(entries)

	type aggKey struct {
		metric StatMetric
		key    string
	}
	agg := make(map[aggKey]*AggregatedStat)
	order := make([]aggKey, 0)
	for _, e := range deduped {
		k := aggKey{e.Metric, e.Key}
		a, ok := agg[k]
		if !ok {
			a = &AggregatedStat{Metric: e.Metric, Key: e.Key, Label: e.Label}
			agg[k] = a
			order = append(order, k)
		}
		if a.Label == "" {
			a.Label = e.Label
		}
		a.TotalPlayTime += e.PlayTimeSeconds
		a.TotalPlayers += e.Players
		a.Sources = append(a.Sources, e)
	}

	out := make([]AggregatedStat, 0, len(order))
	for _, k := range order {
		out = append(out, *agg[k])
	}
	sort.SliceStable(out, func(i, j int) bool {
		return out[i].TotalPlayTime > out[j].TotalPlayTime
	})
	return out
}
