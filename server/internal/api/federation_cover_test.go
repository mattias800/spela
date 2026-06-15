package api

import (
	"testing"

	"github.com/spela/server/internal/igdb"
	"github.com/stretchr/testify/assert"
)

// fakeGameFetcher stands in for *igdb.Client so the resolver's happy path is
// testable without IGDB credentials or network.
type fakeGameFetcher struct {
	configured bool
	games      map[int]*igdb.Game
	calls      int
}

func (f *fakeGameFetcher) IsConfigured() bool { return f.configured }
func (f *fakeGameFetcher) GetGameByID(id int) (*igdb.Game, error) {
	f.calls++
	return f.games[id], nil
}

func TestCoverResolver_ResolvesIgdbCover(t *testing.T) {
	f := &fakeGameFetcher{configured: true, games: map[int]*igdb.Game{
		5: {ID: 5, Cover: &igdb.Image{ImageID: "co1abc"}},
	}}
	r := newIGDBCoverResolver(func() igdbGameFetcher { return f })
	assert.Equal(t, igdb.ImageURL("co1abc", "cover_big"), r.CoverURL("igdb:5"))
}

func TestCoverResolver_NonIgdbKeysNeverHitIGDB(t *testing.T) {
	f := &fakeGameFetcher{configured: true}
	r := newIGDBCoverResolver(func() igdbGameFetcher { return f })
	assert.Empty(t, r.CoverURL("crc:deadbeef"))
	assert.Empty(t, r.CoverURL("notakey"))
	assert.Empty(t, r.CoverURL("igdb:notanumber"))
	assert.Equal(t, 0, f.calls, "non-igdb keys must not call IGDB")
}

func TestCoverResolver_CachesHitsAndMisses(t *testing.T) {
	f := &fakeGameFetcher{configured: true, games: map[int]*igdb.Game{
		7: {Cover: &igdb.Image{ImageID: "co7"}},
		8: {}, // scraped, but IGDB has no cover for it
	}}
	r := newIGDBCoverResolver(func() igdbGameFetcher { return f })

	_ = r.CoverURL("igdb:7")
	_ = r.CoverURL("igdb:7")
	assert.Equal(t, 1, f.calls, "a resolved cover is cached")

	assert.Empty(t, r.CoverURL("igdb:8"))
	assert.Empty(t, r.CoverURL("igdb:8"))
	assert.Equal(t, 2, f.calls, "a miss is cached too — no re-query")
}

func TestCoverResolver_UnconfiguredNotCached(t *testing.T) {
	f := &fakeGameFetcher{configured: false, games: map[int]*igdb.Game{
		9: {Cover: &igdb.Image{ImageID: "co9"}},
	}}
	r := newIGDBCoverResolver(func() igdbGameFetcher { return f })
	assert.Empty(t, r.CoverURL("igdb:9"), "no cover while IGDB is unconfigured")

	// Configuring IGDB later must take effect (the empty result wasn't cached).
	f.configured = true
	assert.Equal(t, igdb.ImageURL("co9", "cover_big"), r.CoverURL("igdb:9"))
}

func TestCoverResolver_NilFetcher(t *testing.T) {
	r := newIGDBCoverResolver(func() igdbGameFetcher { return nil })
	assert.Empty(t, r.CoverURL("igdb:1"))
}
