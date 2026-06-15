package federation

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestDedupeStatEntries_SameSourceCountedOnce_KeepsFirstSeen(t *testing.T) {
	// Same origin/metric/key arriving via two paths (a diamond): must collapse to
	// ONE. We keep the first occurrence (NOT the nearest) so a near malicious
	// relay can't override a distant origin's legitimate copy via a hop tiebreak.
	entries := []StatEntry{
		{OriginFingerprint: "C", Hops: 3, Metric: MetricGamePlay, Key: "g1", PlayTimeSeconds: 500, Players: 2},
		{OriginFingerprint: "C", Hops: 1, Metric: MetricGamePlay, Key: "g1", PlayTimeSeconds: 500, Players: 2},
	}
	out := DedupeStatEntries(entries)
	assert.Len(t, out, 1)
	assert.Equal(t, 3, out[0].Hops, "first occurrence kept, not the nearest")
	assert.Equal(t, int64(500), out[0].PlayTimeSeconds)
}

func TestDedupeStatEntries_Idempotent(t *testing.T) {
	entries := []StatEntry{
		{OriginFingerprint: "A", Hops: 0, Metric: MetricGamePlay, Key: "g1", PlayTimeSeconds: 10},
		{OriginFingerprint: "B", Hops: 1, Metric: MetricGamePlay, Key: "g1", PlayTimeSeconds: 20},
		{OriginFingerprint: "A", Hops: 0, Metric: MetricGamePlay, Key: "g1", PlayTimeSeconds: 10},
	}
	once := DedupeStatEntries(entries)
	twice := DedupeStatEntries(once)
	assert.Equal(t, once, twice, "dedupe must be idempotent f(f(x)) == f(x)")
	assert.Len(t, once, 2)
}

func TestAggregateStatEntries_SumsSameGameAcrossDifferentOrigins(t *testing.T) {
	// Same game (g1) played on two different servers → totals SUM (not deduped),
	// because they are distinct sources contributing to the same game.
	entries := []StatEntry{
		{OriginFingerprint: "A", Hops: 0, Metric: MetricGamePlay, Key: "g1", Label: "Game One", PlayTimeSeconds: 100, Players: 3},
		{OriginFingerprint: "B", Hops: 1, Metric: MetricGamePlay, Key: "g1", Label: "Game One", PlayTimeSeconds: 250, Players: 5},
	}
	out := AggregateStatEntries(entries)
	assert.Len(t, out, 1)
	assert.Equal(t, int64(350), out[0].TotalPlayTime)
	assert.Equal(t, int64(8), out[0].TotalPlayers)
	assert.Len(t, out[0].Sources, 2, "per-source breakdown retained")
}

func TestAggregateStatEntries_DiamondNotDoubleCounted(t *testing.T) {
	// Source C's datum reaches us via both B and D. It must be counted ONCE,
	// even though A's own contribution to the same game is summed in.
	entries := []StatEntry{
		{OriginFingerprint: "A", Hops: 0, Metric: MetricGamePlay, Key: "g1", Label: "G", PlayTimeSeconds: 100, Players: 1},
		{OriginFingerprint: "C", Hops: 2, Metric: MetricGamePlay, Key: "g1", Label: "G", PlayTimeSeconds: 500, Players: 4}, // via B
		{OriginFingerprint: "C", Hops: 2, Metric: MetricGamePlay, Key: "g1", Label: "G", PlayTimeSeconds: 500, Players: 4}, // via D
	}
	out := AggregateStatEntries(entries)
	assert.Len(t, out, 1)
	// A's 100 + C's 500 (counted once) = 600, NOT 1100.
	assert.Equal(t, int64(600), out[0].TotalPlayTime)
	assert.Equal(t, int64(5), out[0].TotalPlayers)
}

func TestAggregateStatEntries_SortedByPlayTimeDesc(t *testing.T) {
	entries := []StatEntry{
		{OriginFingerprint: "A", Metric: MetricGamePlay, Key: "low", Label: "Low", PlayTimeSeconds: 10},
		{OriginFingerprint: "A", Metric: MetricGamePlay, Key: "high", Label: "High", PlayTimeSeconds: 1000},
		{OriginFingerprint: "A", Metric: MetricGamePlay, Key: "mid", Label: "Mid", PlayTimeSeconds: 100},
	}
	out := AggregateStatEntries(entries)
	assert.Equal(t, []string{"high", "mid", "low"}, []string{out[0].Key, out[1].Key, out[2].Key})
}

func TestAggregateStatEntries_SeparatesMetrics(t *testing.T) {
	entries := []StatEntry{
		{OriginFingerprint: "A", Metric: MetricGamePlay, Key: "g1", PlayTimeSeconds: 100},
		{OriginFingerprint: "A", Metric: MetricPlayerPlay, Key: "g1", PlayTimeSeconds: 100},
	}
	out := AggregateStatEntries(entries)
	assert.Len(t, out, 2, "same key under different metrics must not merge")
}
