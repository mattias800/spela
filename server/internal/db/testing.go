package db

import "time"

// Test-only exports for the db package. Kept in a dedicated file so the
// surface area is obvious at a glance and so the `ForTest` suffix is easy
// to grep for.

// ResetSecurityEventDedupForTest clears the process-level security event
// dedup cache. Tests that exercise RecordSecurityEvent must call this in
// their setup because the same singleton is shared with production code,
// and state would otherwise leak between test cases.
func ResetSecurityEventDedupForTest() {
	globalSecurityEventDedup.mu.Lock()
	defer globalSecurityEventDedup.mu.Unlock()
	globalSecurityEventDedup.lastSeen = make(map[securityEventDedupKey]time.Time)
}
