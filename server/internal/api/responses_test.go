package api

import (
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
)

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
