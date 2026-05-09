package scraper

import (
	"os"
	"testing"

	"github.com/spela/server/internal/safehttp"
)

// TestMain unlocks the safehttp private-IP block for the duration of
// the scraper test suite. Many of these tests spin up `httptest.Server`
// instances which bind to 127.0.0.1; without the override, every
// scraper image fetch (#1120 hardening) would refuse the test URL.
// Production code never flips this — see safehttp.SetAllowPrivateForTest.
func TestMain(m *testing.M) {
	safehttp.SetAllowPrivateForTest(true)
	defer safehttp.SetAllowPrivateForTest(false)
	os.Exit(m.Run())
}
