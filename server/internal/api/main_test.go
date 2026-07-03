package api

import (
	"os"
	"testing"

	"github.com/spela/server/internal/auth"
	"golang.org/x/crypto/bcrypt"
)

// TestMain lowers the bcrypt cost for the whole api test package.
//
// Password hashing at the production cost (auth.BcryptCost = 12, ~250 ms per
// hash) dominates this package's runtime: nearly every test registers a user
// via registerAndGetToken, and the full suite otherwise hits the 10-minute
// timeout. bcrypt.MinCost keeps the hashing logic exercised while removing the
// deliberate slowdown. Production is unaffected — it always uses BcryptCost.
// See #1572.
func TestMain(m *testing.M) {
	restore := auth.SetBcryptCostForTesting(bcrypt.MinCost)
	code := m.Run()
	restore()
	os.Exit(code)
}
