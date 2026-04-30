package api

import (
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
)

// TestToGameResponseSurfacesConsoleSaveStatePolicy verifies that the
// per-game payload carries the parent console's tier so the player
// can render the first-launch prompt at game-launch time without a
// second round-trip. See #804 phase 4b.
func TestToGameResponseSurfacesConsoleSaveStatePolicy(t *testing.T) {
	cases := []struct {
		name       string
		console    db.Console
		wantPolicy string
	}{
		{
			name: "large GameCube",
			console: db.Console{
				ID:              1,
				Name:            "GameCube",
				Abbreviation:    "GC",
				CoverAspect:     "1:1",
				SaveStatePolicy: db.SaveStatePolicyLarge,
			},
			wantPolicy: "large",
		},
		{
			name: "small NES",
			console: db.Console{
				ID:              2,
				Name:            "NES",
				Abbreviation:    "NES",
				CoverAspect:     "5:7",
				SaveStatePolicy: db.SaveStatePolicySmall,
			},
			wantPolicy: "small",
		},
		{
			name: "console with no policy falls back to small",
			console: db.Console{
				ID:           3,
				Name:         "Custom",
				Abbreviation: "CUST",
				CoverAspect:  "1:1",
			},
			wantPolicy: "small",
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			resp := toGameResponseWithData(
				db.Game{ID: 99, Console: tc.console, ConsoleID: tc.console.ID, Title: "Test"},
				nil,
			)
			assert.Equal(t, tc.wantPolicy, resp.ConsoleSaveStatePolicy)
		})
	}
}

// TestToGameResponseFallsBackForOrphanedGame covers the rare case
// where a game has no joined console (Console.ID == 0). The default
// must still be a known tier value so the player switch is total.
func TestToGameResponseFallsBackForOrphanedGame(t *testing.T) {
	resp := toGameResponseWithData(
		db.Game{ID: 99, Title: "Orphan"},
		nil,
	)
	assert.Equal(t, "small", resp.ConsoleSaveStatePolicy)
}

// TestToConsoleResponseSurfacesSaveStatePolicy pins the wire shape for
// #804 phase 3: ToConsoleResponse must always emit a non-empty
// "saveStatePolicy" so the player UI can switch unconditionally.
//
// Two paths matter:
//
//	(a) row has a tier from the seed → echoed verbatim.
//	(b) row has empty SaveStatePolicy (manually-inserted custom row,
//	    or a future schema slip) → fallback to "small" rather than
//	    leaking an empty string to clients that expect a closed enum.
func TestToConsoleResponseSurfacesSaveStatePolicy(t *testing.T) {
	cases := []struct {
		name string
		in   db.SaveStatePolicy
		want string
	}{
		{"large GameCube", db.SaveStatePolicyLarge, "large"},
		{"medium PSX", db.SaveStatePolicyMedium, "medium"},
		{"small NES", db.SaveStatePolicySmall, "small"},
		{"empty falls back", "", "small"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			resp := ToConsoleResponse(db.Console{
				Abbreviation:    "TEST",
				Name:            "Test Console",
				Extensions:      "",
				CoverAspect:     "1:1",
				SaveStatePolicy: tc.in,
			})
			assert.Equal(t, tc.want, resp.SaveStatePolicy)
		})
	}
}
