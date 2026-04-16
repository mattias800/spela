package db

import "time"

// Test-only exports for the db package. Kept in a dedicated file so the
// surface area is obvious at a glance and so the `ForTest` suffix is easy
// to grep for.

// ResetSystemEventDedupForTest clears the process-level system event dedup
// cache. Tests that exercise RecordSecurityEvent or RecordOperationalEvent
// must call this in their setup because the same singleton is shared with
// production code, and state would otherwise leak between test cases.
func ResetSystemEventDedupForTest() {
	globalSystemEventDedup.mu.Lock()
	defer globalSystemEventDedup.mu.Unlock()
	globalSystemEventDedup.lastSeen = make(map[systemEventDedupKey]time.Time)
}

// ResetCategoryIDCacheForTest clears the cached category ID lookup so tests
// using fresh in-memory databases don't get stale IDs from a prior test.
func ResetCategoryIDCacheForTest() {
	categoryIDCacheMu.Lock()
	defer categoryIDCacheMu.Unlock()
	categoryIDCache = nil
}
