package api

import (
	"strconv"
	"strings"
	"sync"

	"github.com/spela/server/internal/igdb"
)

// coverResolver turns a federated catalog key into a public cover-art URL, or
// "" when none is available. Implemented by igdbCoverResolver; replaced with a
// fake in tests.
type coverResolver interface {
	CoverURL(key string) string
}

// igdbGameFetcher is the slice of *igdb.Client the resolver needs — kept small
// so it can be faked in tests.
type igdbGameFetcher interface {
	IsConfigured() bool
	GetGameByID(igdbID int) (*igdb.Game, error)
}

// igdbCoverResolver resolves covers for "igdb:<id>" keys via this server's own
// IGDB client and caches the result for the process lifetime. It needs no data
// from the origin server — the cross-server key already identifies the game —
// so covers work for any scraped game regardless of how the origin stored its
// own cover. crc:* keys (no IGDB identity) resolve to "".
type igdbCoverResolver struct {
	fetch func() igdbGameFetcher // read lazily — the client may be reconfigured at runtime
	mu    sync.Mutex
	cache map[string]string
}

func newIGDBCoverResolver(fetch func() igdbGameFetcher) *igdbCoverResolver {
	return &igdbCoverResolver{fetch: fetch, cache: map[string]string{}}
}

func (r *igdbCoverResolver) CoverURL(key string) string {
	idStr, ok := strings.CutPrefix(key, "igdb:")
	if !ok {
		return ""
	}
	id, err := strconv.Atoi(idStr)
	if err != nil {
		return ""
	}

	r.mu.Lock()
	if v, hit := r.cache[key]; hit {
		r.mu.Unlock()
		return v
	}
	r.mu.Unlock()

	f := r.fetch()
	if f == nil || !f.IsConfigured() {
		// Don't cache — IGDB may be configured later; we want to retry then.
		return ""
	}

	// We deliberately don't hold the lock across the IGDB call: two concurrent
	// cold lookups of the same key may both fetch, but the result is
	// deterministic and idempotent (same URL), and the IGDB client self-rate-
	// limits — so the only cost is a rare duplicate request, not correctness.

	url := ""
	if g, err := f.GetGameByID(id); err == nil && g != nil && g.Cover != nil && g.Cover.ImageID != "" {
		url = igdb.ImageURL(g.Cover.ImageID, "cover_big")
	}

	// Cache hits AND genuine misses (game with no IGDB cover) so we don't
	// re-query IGDB for the same key on every search.
	r.mu.Lock()
	r.cache[key] = url
	r.mu.Unlock()
	return url
}
