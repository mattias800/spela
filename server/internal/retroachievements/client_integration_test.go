package retroachievements

import (
	"os"
	"testing"
)

// TestGetGameExtendedIntegration calls the real RA API.
// Run with: SPELA_RA_API_KEY=... go test ./internal/retroachievements/ -run TestGetGameExtendedIntegration -v
func TestGetGameExtendedIntegration(t *testing.T) {
	apiKey := os.Getenv("SPELA_RA_API_KEY")
	if apiKey == "" {
		t.Skip("SPELA_RA_API_KEY not set, skipping integration test")
	}

	client := NewRAClient()

	// Super Mario World = RA game ID 228
	gameInfo, err := client.GetGameExtended(apiKey, 228)
	if err != nil {
		t.Fatalf("GetGameExtended failed: %v", err)
	}

	if gameInfo.Title == "" {
		t.Error("expected non-empty title")
	}
	t.Logf("Title: %s", gameInfo.Title)

	if gameInfo.TotalCount == 0 {
		t.Error("expected achievements, got 0")
	}
	t.Logf("Total achievements: %d", gameInfo.TotalCount)
	t.Logf("Total points: %d", gameInfo.TotalPoints)

	// Verify at least one achievement has proper data
	if len(gameInfo.Achievements) > 0 {
		a := gameInfo.Achievements[0]
		if a.ID == 0 {
			t.Error("expected achievement ID > 0")
		}
		if a.Title == "" {
			t.Error("expected achievement title")
		}
		if a.BadgeURL == "" {
			t.Error("expected badge URL")
		}
		t.Logf("Sample: %s (%d pts, rarity %.1f%%, type=%s)", a.Title, a.Points, a.RarityPercent, a.Type)
	}

	// Verify hash lookup also works
	// Super Mario World (USA) MD5 varies by ROM, so let's just test the game ID lookup
	t.Logf("SUCCESS: Parsed %d achievements for %s", gameInfo.TotalCount, gameInfo.Title)
}
