package api

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestWeightedSample_ReturnsAllWhenPoolSmallerThanN(t *testing.T) {
	pool := []string{"a", "b", "c"}
	result := weightedSample(pool, 10, func(s string) float64 { return 1.0 })
	assert.Equal(t, pool, result)
}

func TestWeightedSample_ReturnsExactlyNItems(t *testing.T) {
	pool := make([]int, 100)
	for i := range pool {
		pool[i] = i
	}
	result := weightedSample(pool, 10, func(i int) float64 { return 1.0 })
	assert.Len(t, result, 10)
}

func TestWeightedSample_NoDuplicates(t *testing.T) {
	pool := make([]int, 50)
	for i := range pool {
		pool[i] = i
	}
	result := weightedSample(pool, 20, func(i int) float64 { return float64(i + 1) })

	seen := make(map[int]bool)
	for _, v := range result {
		require.False(t, seen[v], "duplicate item %d", v)
		seen[v] = true
	}
}

func TestWeightedSample_HighWeightItemsAppearMoreOften(t *testing.T) {
	type item struct {
		name   string
		weight float64
	}
	pool := []item{
		{"heavy", 1000},
		{"light1", 1},
		{"light2", 1},
		{"light3", 1},
		{"light4", 1},
	}

	// Run many trials with different seeds by varying the pool order
	heavyCount := 0
	trials := 1000
	for i := 0; i < trials; i++ {
		// Shift pool to get different RNG paths
		shifted := make([]item, len(pool))
		copy(shifted, pool)
		// Add a varying-weight dummy item to perturb the RNG
		trial := append(shifted, item{"dummy", float64(i + 1)})
		result := weightedSample(trial, 3, func(it item) float64 { return it.weight })
		for _, r := range result {
			if r.name == "heavy" {
				heavyCount++
				break
			}
		}
	}

	// "heavy" has weight 1000 vs others with weight 1 — it should appear in
	// the vast majority of samples
	assert.Greater(t, heavyCount, trials*9/10,
		"heavy item (weight 1000) should appear in >90%% of samples, got %d/%d", heavyCount, trials)
}

func TestWeightedSample_DeterministicForSameDay(t *testing.T) {
	pool := make([]int, 100)
	for i := range pool {
		pool[i] = i
	}
	weight := func(i int) float64 { return float64(i + 1) }

	result1 := weightedSample(pool, 10, weight)
	result2 := weightedSample(pool, 10, weight)
	assert.Equal(t, result1, result2, "same day should produce same selection")
}
