package scraper

import (
	"net/http"
	"testing"
	"time"
)

func TestDebugLibRetroSmashBros(t *testing.T) {
	httpClient := &http.Client{Timeout: 30 * time.Second}
	cache := &nameCache{entries: make(map[string][]nameEntry)}

	system := "Nintendo - GameCube"
	gameName := "Super Smash Bros. Melee (USA) (En,Ja)"

	t.Logf("gameName: %q", gameName)
	t.Logf("hasNonPreferredRegion: %v", hasNonPreferredRegion(gameName))
	t.Logf("ExtractRegion: %q", ExtractRegion(gameName))

	// Load the LibRetro name cache
	entries, err := cache.getOrLoad(system, httpClient)
	if err != nil {
		t.Fatalf("failed to load cache: %v", err)
	}
	t.Logf("loaded %d entries for %s", len(entries), system)

	// Find all entries matching "smash"
	for _, e := range entries {
		if containsIgnoreCase(e.Raw, "smash") {
			t.Logf("  LibRetro entry: %q (normalized: %q, priority: %d)", e.Raw, e.Normalized, e.Priority)
		}
	}

	// Try exact match
	normalized := normalizeName(gameName)
	t.Logf("normalized gameName: %q", normalized)

	// Find best match
	match, score, found := findBestMatch(normalized, entries, 0.88)
	if found {
		t.Logf("BEST MATCH: %q (score: %.4f, priority: %d, region: %q)",
			match.Raw, score, match.Priority, ExtractRegion(match.Raw))
	} else {
		t.Logf("NO MATCH FOUND")
	}

	// Find all matches above threshold
	allMatches := findAllMatches(normalized, entries, 0.88)
	t.Logf("all matches above 0.88:")
	for _, m := range allMatches {
		t.Logf("  %q (priority: %d, region: %q)", m.Raw, m.Priority, ExtractRegion(m.Raw))
	}
}

func containsIgnoreCase(s, substr string) bool {
	return len(s) >= len(substr) && func() bool {
		for i := 0; i <= len(s)-len(substr); i++ {
			match := true
			for j := 0; j < len(substr); j++ {
				c1, c2 := s[i+j], substr[j]
				if c1 >= 'A' && c1 <= 'Z' {
					c1 += 32
				}
				if c2 >= 'A' && c2 <= 'Z' {
					c2 += 32
				}
				if c1 != c2 {
					match = false
					break
				}
			}
			if match {
				return true
			}
		}
		return false
	}()
}
