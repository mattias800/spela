package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func setupSearchEnv(t *testing.T) (*gorm.DB, http.Handler, string) {
	t.Helper()
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)
	return database, router, token
}

// searchGet performs a GET /api/search request and returns the parsed response.
func searchGet(t *testing.T, router http.Handler, token, url string) (int, SearchResponse) {
	t.Helper()
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", url, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	var resp SearchResponse
	if w.Code == http.StatusOK {
		require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	}
	return w.Code, resp
}

// seedSearchData creates test data across all entity types for search tests.
func seedSearchData(t *testing.T, database *gorm.DB) {
	t.Helper()

	// Find SNES and NES console IDs
	var snes, nes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&snes).Error)
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)

	// Create games
	games := []db.Game{
		{ConsoleID: snes.ID, Title: "Super Mario World", FileName: "smw.smc", FilePath: "/roms/snes/smw.smc", Developer: "Nintendo", Publisher: "Nintendo", Genre: "Platformer", IGDBCriticsRating: 95},
		{ConsoleID: snes.ID, Title: "Super Metroid", FileName: "sm.smc", FilePath: "/roms/snes/sm.smc", Developer: "Nintendo", Publisher: "Nintendo", Genre: "Action", IGDBCriticsRating: 96},
		{ConsoleID: nes.ID, Title: "Super Mario Bros", FileName: "smb.nes", FilePath: "/roms/nes/smb.nes", Developer: "Nintendo", Publisher: "Nintendo", Genre: "Platformer", IGDBCriticsRating: 90},
		{ConsoleID: snes.ID, Title: "Chrono Trigger", FileName: "ct.smc", FilePath: "/roms/snes/ct.smc", Developer: "Square", Publisher: "Square", Genre: "RPG", IGDBCriticsRating: 98},
		{ConsoleID: nes.ID, Title: "Mega Man 2", FileName: "mm2.nes", FilePath: "/roms/nes/mm2.nes", Developer: "Capcom", Publisher: "Capcom", Genre: "Action", IGDBCriticsRating: 85},
	}
	for i := range games {
		require.NoError(t, database.Create(&games[i]).Error)
	}

	// Create a series
	series := db.GameSeries{
		IGDBCollectionID: 100,
		Name:             "Super Mario Series",
	}
	require.NoError(t, database.Create(&series).Error)
	// Add entries
	entry1 := db.GameSeriesEntry{SeriesID: series.ID, GameID: &games[0].ID, IGDBGameID: 1001, Name: "Super Mario World"}
	entry2 := db.GameSeriesEntry{SeriesID: series.ID, GameID: &games[2].ID, IGDBGameID: 1002, Name: "Super Mario Bros"}
	entry3 := db.GameSeriesEntry{SeriesID: series.ID, GameID: nil, IGDBGameID: 1003, Name: "Super Mario 64"}
	require.NoError(t, database.Create(&entry1).Error)
	require.NoError(t, database.Create(&entry2).Error)
	require.NoError(t, database.Create(&entry3).Error)

	// Create a franchise group
	franchise := db.GameFranchiseGroup{
		IGDBFranchiseID: 200,
		Name:            "Mario Franchise",
	}
	require.NoError(t, database.Create(&franchise).Error)
	fEntry1 := db.GameFranchiseEntry{FranchiseGroupID: franchise.ID, GameID: &games[0].ID, IGDBGameID: 2001, Name: "Super Mario World"}
	fEntry2 := db.GameFranchiseEntry{FranchiseGroupID: franchise.ID, GameID: nil, IGDBGameID: 2002, Name: "Mario Kart 8"}
	require.NoError(t, database.Create(&fEntry1).Error)
	require.NoError(t, database.Create(&fEntry2).Error)
}

func TestSearch_MultiTypeResults(t *testing.T) {
	database, router, token := setupSearchEnv(t)
	seedSearchData(t, database)

	// "Super" matches games (Super Mario World, Super Metroid, Super Mario Bros),
	// the series (Super Mario Series), and developer Nintendo has "Super" games
	code, resp := searchGet(t, router, token, "/api/search?q=Super")
	assert.Equal(t, http.StatusOK, code)

	// Games: should find 3 "Super" games
	assert.Equal(t, 3, resp.Games.Total)
	assert.Len(t, resp.Games.Results, 3)

	// Verify game fields are populated
	found := false
	for _, g := range resp.Games.Results {
		if g.Title == "Super Mario World" {
			found = true
			assert.NotEmpty(t, g.ID)
			assert.Equal(t, "snes", g.ConsoleID)
			assert.Equal(t, "Super Nintendo", g.ConsoleName)
			assert.Equal(t, "Nintendo", g.Developer)
			assert.Equal(t, "Platformer", g.Genre)
			break
		}
	}
	assert.True(t, found, "expected to find Super Mario World in results")

	// Series: "Super Mario Series" matches
	assert.GreaterOrEqual(t, resp.Series.Total, 1)
	assert.GreaterOrEqual(t, len(resp.Series.Results), 1)
	assert.Equal(t, "Super Mario Series", resp.Series.Results[0].Name)
	assert.Equal(t, 3, resp.Series.Results[0].TotalGames)   // 3 entries
	assert.Equal(t, 2, resp.Series.Results[0].LibraryGames)  // 2 are local
}

func TestSearch_SingleTypeResults(t *testing.T) {
	database, router, token := setupSearchEnv(t)
	seedSearchData(t, database)

	// "Chrono" only matches the game "Chrono Trigger"
	code, resp := searchGet(t, router, token, "/api/search?q=Chrono")
	assert.Equal(t, http.StatusOK, code)

	assert.Equal(t, 1, resp.Games.Total)
	assert.Len(t, resp.Games.Results, 1)
	assert.Equal(t, "Chrono Trigger", resp.Games.Results[0].Title)

	// Other categories should be empty
	assert.Equal(t, 0, resp.Consoles.Total)
	assert.Empty(t, resp.Consoles.Results)
	assert.Equal(t, 0, resp.Series.Total)
	assert.Empty(t, resp.Series.Results)
	assert.Equal(t, 0, resp.Franchises.Total)
	assert.Empty(t, resp.Franchises.Results)
}

func TestSearch_EmptyResults(t *testing.T) {
	_, router, token := setupSearchEnv(t)

	code, resp := searchGet(t, router, token, "/api/search?q=zzzznonexistent")
	assert.Equal(t, http.StatusOK, code)

	assert.Equal(t, 0, resp.Games.Total)
	assert.Empty(t, resp.Games.Results)
	assert.Equal(t, 0, resp.Consoles.Total)
	assert.Empty(t, resp.Consoles.Results)
	assert.Equal(t, 0, resp.Developers.Total)
	assert.Empty(t, resp.Developers.Results)
	assert.Equal(t, 0, resp.Publishers.Total)
	assert.Empty(t, resp.Publishers.Results)
	assert.Equal(t, 0, resp.Collections.Total)
	assert.Empty(t, resp.Collections.Results)
	assert.Equal(t, 0, resp.Series.Total)
	assert.Empty(t, resp.Series.Results)
	assert.Equal(t, 0, resp.Franchises.Total)
	assert.Empty(t, resp.Franchises.Results)
}

func TestSearch_ShortQueryRejected(t *testing.T) {
	_, router, token := setupSearchEnv(t)

	// Single character -> 400
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/search?q=a", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)

	var errResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &errResp)
	assert.Contains(t, errResp["error"], "at least 2 characters")

	// Empty query -> 400
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/search?q=", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)

	// No query parameter -> 400
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/search", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestSearch_CaseInsensitive(t *testing.T) {
	database, router, token := setupSearchEnv(t)
	seedSearchData(t, database)

	// Lowercase search should find "Super Mario World"
	code1, resp1 := searchGet(t, router, token, "/api/search?q="+url.QueryEscape("super mario"))
	assert.Equal(t, http.StatusOK, code1)
	assert.GreaterOrEqual(t, resp1.Games.Total, 1)

	// Uppercase search should find the same results
	code2, resp2 := searchGet(t, router, token, "/api/search?q="+url.QueryEscape("SUPER MARIO"))
	assert.Equal(t, http.StatusOK, code2)
	assert.Equal(t, resp1.Games.Total, resp2.Games.Total)

	// Mixed case
	code3, resp3 := searchGet(t, router, token, "/api/search?q="+url.QueryEscape("SuPeR MaRiO"))
	assert.Equal(t, http.StatusOK, code3)
	assert.Equal(t, resp1.Games.Total, resp3.Games.Total)
}

func TestSearch_LimitParameter(t *testing.T) {
	database, router, token := setupSearchEnv(t)
	seedSearchData(t, database)

	// Default limit should return all matching games (3 "Super" games, limit=5)
	code, resp := searchGet(t, router, token, "/api/search?q=Super")
	assert.Equal(t, http.StatusOK, code)
	assert.Equal(t, 3, resp.Games.Total)
	assert.Len(t, resp.Games.Results, 3)

	// Limit to 1 should return 1 result but total stays 3
	code, resp = searchGet(t, router, token, "/api/search?q=Super&limit=1")
	assert.Equal(t, http.StatusOK, code)
	assert.Equal(t, 3, resp.Games.Total) // total is still 3
	assert.Len(t, resp.Games.Results, 1)  // only 1 returned
}

func TestSearch_GamesExcludeSoftDeleted(t *testing.T) {
	database, router, token := setupSearchEnv(t)

	var snes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&snes).Error)

	// Create a normal game
	game1 := db.Game{ConsoleID: snes.ID, Title: "Active Game Zelda", FileName: "zelda.smc", FilePath: "/roms/snes/zelda.smc"}
	require.NoError(t, database.Create(&game1).Error)

	// Create and soft-delete a game
	game2 := db.Game{ConsoleID: snes.ID, Title: "Deleted Game Zelda", FileName: "zelda2.smc", FilePath: "/roms/snes/zelda2.smc"}
	require.NoError(t, database.Create(&game2).Error)
	require.NoError(t, database.Delete(&game2).Error)

	code, resp := searchGet(t, router, token, "/api/search?q=Zelda")
	assert.Equal(t, http.StatusOK, code)

	// Only the active game should be returned
	assert.Equal(t, 1, resp.Games.Total)
	assert.Len(t, resp.Games.Results, 1)
	assert.Equal(t, "Active Game Zelda", resp.Games.Results[0].Title)
}

func TestSearch_Collections_PublicAndOwnPrivate(t *testing.T) {
	database, router, token := setupSearchEnv(t)

	// Get the owner user
	var owner db.User
	require.NoError(t, database.First(&owner).Error)

	// Create a second user
	token2 := createNonOwnerUser(t, router, token, "user2", "user2@test.com", "SecureTestPass!2024")
	var user2 db.User
	require.NoError(t, database.Where("username = ?", "user2").First(&user2).Error)

	// Owner creates a public collection
	publicCol := db.GameCollection{UserID: owner.ID, Name: "Public Search Collection", IsPublic: true}
	require.NoError(t, database.Create(&publicCol).Error)

	// Owner creates a private collection
	ownerPrivateCol := db.GameCollection{UserID: owner.ID, Name: "Private Search Collection", IsPublic: false}
	require.NoError(t, database.Create(&ownerPrivateCol).Error)

	// User2 creates a private collection
	user2PrivateCol := db.GameCollection{UserID: user2.ID, Name: "Secret Search Collection", IsPublic: false}
	require.NoError(t, database.Create(&user2PrivateCol).Error)

	// Owner searching for "Search Collection": should see public + own private
	code, resp := searchGet(t, router, token, "/api/search?q="+url.QueryEscape("Search Collection"))
	assert.Equal(t, http.StatusOK, code)
	assert.Equal(t, 2, resp.Collections.Total) // public + owner's private
	names := make([]string, len(resp.Collections.Results))
	for i, c := range resp.Collections.Results {
		names[i] = c.Name
	}
	assert.Contains(t, names, "Public Search Collection")
	assert.Contains(t, names, "Private Search Collection")
	assert.NotContains(t, names, "Secret Search Collection")

	// User2 searching: should see public + own private
	code2, resp2 := searchGet(t, router, token2, "/api/search?q="+url.QueryEscape("Search Collection"))
	assert.Equal(t, http.StatusOK, code2)
	assert.Equal(t, 2, resp2.Collections.Total) // public + user2's private
	names2 := make([]string, len(resp2.Collections.Results))
	for i, c := range resp2.Collections.Results {
		names2[i] = c.Name
	}
	assert.Contains(t, names2, "Public Search Collection")
	assert.Contains(t, names2, "Secret Search Collection")
	assert.NotContains(t, names2, "Private Search Collection")
}

func TestSearch_Consoles_OnlyWithGames(t *testing.T) {
	database, router, token := setupSearchEnv(t)

	// "Nintendo" matches several console names (e.g. "Super Nintendo Entertainment System",
	// "Nintendo Entertainment System", "Nintendo 64", etc.) but only those with games
	// should appear.
	var snes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&snes).Error)

	// Add a game to SNES only
	game := db.Game{ConsoleID: snes.ID, Title: "Test Game", FileName: "test.smc", FilePath: "/roms/snes/test.smc"}
	require.NoError(t, database.Create(&game).Error)

	code, resp := searchGet(t, router, token, "/api/search?q=Nintendo")
	assert.Equal(t, http.StatusOK, code)

	// Many console names contain "Nintendo" (NES, N64, GameCube, ...) but only
	// SNES has a game, so it must be the sole result — and it must actually be
	// there. A bare range loop would pass on an empty result set, so assert the
	// exact result explicitly (the name match + games filter both ran).
	require.Len(t, resp.Consoles.Results, 1)
	assert.Equal(t, 1, resp.Consoles.Total)
	assert.Equal(t, "snes", resp.Consoles.Results[0].ID)
	assert.Equal(t, "Super Nintendo", resp.Consoles.Results[0].Name)
	assert.Equal(t, 1, resp.Consoles.Results[0].GameCount)
}

// TestSearch_Consoles_MatchesRegistryName locks in that console search matches
// against the registry-derived name (#1443) — case-insensitively — and returns
// the correct console, discriminating between candidates. Console names are no
// longer a DB column, so this match happens in Go; without a positive
// assertion a broken match would silently return nothing.
func TestSearch_Consoles_MatchesRegistryName(t *testing.T) {
	database, router, token := setupSearchEnv(t)

	var snes, gen db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&snes).Error)
	require.NoError(t, database.Where("abbreviation = ?", "GEN").First(&gen).Error)
	require.NoError(t, database.Create(&db.Game{ConsoleID: snes.ID, Title: "A", FileName: "a.smc", FilePath: "/roms/snes/a.smc"}).Error)
	require.NoError(t, database.Create(&db.Game{ConsoleID: gen.ID, Title: "B", FileName: "b.md", FilePath: "/roms/gen/b.md"}).Error)

	// "genesis" (lowercase) must match "Sega Genesis" and not "Super Nintendo".
	code, resp := searchGet(t, router, token, "/api/search?q=genesis")
	assert.Equal(t, http.StatusOK, code)
	require.Len(t, resp.Consoles.Results, 1)
	assert.Equal(t, "gen", resp.Consoles.Results[0].ID)
	assert.Equal(t, "Sega Genesis", resp.Consoles.Results[0].Name)

	// A term in neither name returns no consoles.
	code, resp = searchGet(t, router, token, "/api/search?q=zzznoconsole")
	assert.Equal(t, http.StatusOK, code)
	assert.Equal(t, 0, resp.Consoles.Total)
	assert.Empty(t, resp.Consoles.Results)
}

func TestSearch_Developers(t *testing.T) {
	database, router, token := setupSearchEnv(t)
	seedSearchData(t, database)

	// "Nintendo" is the developer for 3 games
	code, resp := searchGet(t, router, token, "/api/search?q=Nintendo")
	assert.Equal(t, http.StatusOK, code)

	assert.GreaterOrEqual(t, resp.Developers.Total, 1)
	found := false
	for _, d := range resp.Developers.Results {
		if d.Name == "Nintendo" {
			found = true
			assert.Equal(t, 3, d.GameCount)
			assert.Greater(t, d.AvgRating, 0.0)
			break
		}
	}
	assert.True(t, found, "expected to find Nintendo in developers")
}

func TestSearch_Publishers(t *testing.T) {
	database, router, token := setupSearchEnv(t)
	seedSearchData(t, database)

	// "Capcom" is publisher for 1 game
	code, resp := searchGet(t, router, token, "/api/search?q=Capcom")
	assert.Equal(t, http.StatusOK, code)

	assert.GreaterOrEqual(t, resp.Publishers.Total, 1)
	found := false
	for _, p := range resp.Publishers.Results {
		if p.Name == "Capcom" {
			found = true
			assert.Equal(t, 1, p.GameCount)
			break
		}
	}
	assert.True(t, found, "expected to find Capcom in publishers")
}

func TestSearch_Franchises(t *testing.T) {
	database, router, token := setupSearchEnv(t)
	seedSearchData(t, database)

	// "Mario Franchise" should be found
	code, resp := searchGet(t, router, token, "/api/search?q="+url.QueryEscape("Mario Franchise"))
	assert.Equal(t, http.StatusOK, code)

	assert.GreaterOrEqual(t, resp.Franchises.Total, 1)
	assert.Equal(t, "Mario Franchise", resp.Franchises.Results[0].Name)
	assert.Equal(t, 2, resp.Franchises.Results[0].TotalGames)   // 2 entries
	assert.Equal(t, 1, resp.Franchises.Results[0].LibraryGames)  // 1 has local game
}

func TestSearch_RequiresAuth(t *testing.T) {
	_, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/search?q=test", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

// TestSearch_PlatformCodeHintBoostsMatchingConsole covers the "jurassic
// park nes" pattern: a multi-token query containing a console
// abbreviation prioritises games on that console without filtering out
// other consoles' results.
func TestSearch_PlatformCodeHintBoostsMatchingConsole(t *testing.T) {
	database, router, token := setupSearchEnv(t)

	var snes, nes, gba db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&snes).Error)
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)
	require.NoError(t, database.Where("abbreviation = ?", "GBA").First(&gba).Error)

	// Same title across three platforms — alphabetical order without
	// the hint would put SNES before NES (S > N is false; actually
	// SNES sorts after NES alphabetically by title which is identical,
	// so order would come down to whatever the ORM returns).
	// With a NES hint, NES must surface first regardless of SNES /
	// GBA being in the result set.
	titles := []db.Game{
		{ConsoleID: snes.ID, Title: "Jurassic Park", FileName: "jp.smc", FilePath: "/roms/snes/jp.smc"},
		{ConsoleID: nes.ID, Title: "Jurassic Park", FileName: "jp.nes", FilePath: "/roms/nes/jp.nes"},
		{ConsoleID: gba.ID, Title: "Jurassic Park", FileName: "jp.gba", FilePath: "/roms/gba/jp.gba"},
	}
	for i := range titles {
		require.NoError(t, database.Create(&titles[i]).Error)
	}

	cases := []struct {
		name   string
		query  string
		expect string
	}{
		{"suffix", "jurassic park nes", "nes"},
		{"prefix", "nes jurassic park", "nes"},
		{"mixed case", "Jurassic Park NES", "nes"},
		{"uppercase abbrev", "JURASSIC PARK NES", "nes"},
		// Different console — make sure the hint is being read, not
		// hard-coded.
		{"snes hint", "jurassic park snes", "snes"},
		{"gba hint", "jurassic park gba", "gba"},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			code, resp := searchGet(t, router, token,
				"/api/search?q="+url.QueryEscape(tc.query))
			require.Equal(t, http.StatusOK, code)
			require.GreaterOrEqual(t, len(resp.Games.Results), 3,
				"all three platform variants should still be in the result set (hint priortises, doesn't filter)")
			assert.Equal(t, tc.expect, resp.Games.Results[0].ConsoleID,
				"first result should be on the hinted console")
		})
	}
}

// TestSearch_PlatformCodeHintSingleTokenStillSearchesEverything verifies
// the "user typed just `nes` alone" case isn't stripped to an empty
// query. A single token shouldn't trigger console-hint extraction; the
// token has to remain in the games-title LIKE pattern.
func TestSearch_PlatformCodeHintSingleTokenStillSearchesEverything(t *testing.T) {
	database, router, token := setupSearchEnv(t)

	var nes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)

	// A game whose title contains "nes" — confirms the unmodified
	// single-token query still runs the LIKE search instead of being
	// stripped to nothing by the hint extractor.
	g := db.Game{ConsoleID: nes.ID, Title: "Jonesy NES", FileName: "jn.nes", FilePath: "/roms/nes/jn.nes"}
	require.NoError(t, database.Create(&g).Error)

	code, resp := searchGet(t, router, token, "/api/search?q=nes")
	require.Equal(t, http.StatusOK, code)
	assert.NotEmpty(t, resp.Games.Results,
		"single-token query should not strip the token; it should LIKE-match game titles")
}

// TestSearch_PlatformCodeHintDoesNotFilterOtherConsoles asserts the
// "don't filter out other platforms" contract from the spec.
func TestSearch_PlatformCodeHintDoesNotFilterOtherConsoles(t *testing.T) {
	database, router, token := setupSearchEnv(t)

	var snes, gba db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&snes).Error)
	require.NoError(t, database.Where("abbreviation = ?", "GBA").First(&gba).Error)

	// "Final Fantasy" exists only on SNES and GBA. User types
	// "final fantasy nes" — no NES game matches the title, but the
	// result must still include SNES + GBA results, not be empty.
	for i, c := range []db.Console{snes, gba} {
		g := db.Game{
			ConsoleID: c.ID,
			Title:     "Final Fantasy",
			FileName:  fmt.Sprintf("ff-%d.rom", i),
			FilePath:  fmt.Sprintf("/roms/ff-%d.rom", i),
		}
		require.NoError(t, database.Create(&g).Error)
	}

	code, resp := searchGet(t, router, token,
		"/api/search?q="+url.QueryEscape("final fantasy nes"))
	require.Equal(t, http.StatusOK, code)
	assert.Equal(t, 2, len(resp.Games.Results),
		"NES hint must not filter out SNES + GBA results when no NES title matches")
}

// TestSearch_MultiWordNonAdjacent covers #1310: a multi-word query must
// match a title where the query words appear with other words between
// them. "symphony night" must match "Castlevania: Symphony of the
// Night" — the old single-substring LIKE '%symphony night%' pattern
// required the words to be adjacent and missed it.
func TestSearch_MultiWordNonAdjacent(t *testing.T) {
	database, router, token := setupSearchEnv(t)

	var snes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&snes).Error)

	game := db.Game{ConsoleID: snes.ID, Title: "Castlevania: Symphony of the Night", FileName: "sotn.smc", FilePath: "/roms/snes/sotn.smc"}
	require.NoError(t, database.Create(&game).Error)

	code, resp := searchGet(t, router, token, "/api/search?q="+url.QueryEscape("symphony night"))
	require.Equal(t, http.StatusOK, code)

	require.Equal(t, 1, resp.Games.Total)
	require.Len(t, resp.Games.Results, 1)
	assert.Equal(t, "Castlevania: Symphony of the Night", resp.Games.Results[0].Title)
}

// TestSearch_MultiWordRequiresAllTokens asserts the AND semantics: every
// query token must appear in the title. A query mixing a matching and a
// non-matching token must not return the game.
func TestSearch_MultiWordRequiresAllTokens(t *testing.T) {
	database, router, token := setupSearchEnv(t)

	var snes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&snes).Error)

	game := db.Game{ConsoleID: snes.ID, Title: "Castlevania: Symphony of the Night", FileName: "sotn.smc", FilePath: "/roms/snes/sotn.smc"}
	require.NoError(t, database.Create(&game).Error)

	// "symphony" matches but "zelda" does not — AND of tokens means no result.
	code, resp := searchGet(t, router, token, "/api/search?q="+url.QueryEscape("symphony zelda"))
	require.Equal(t, http.StatusOK, code)

	assert.Equal(t, 0, resp.Games.Total)
	assert.Empty(t, resp.Games.Results)
}

func TestSearch_LimitDefaultsAndBounds(t *testing.T) {
	database, router, token := setupSearchEnv(t)

	var snes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&snes).Error)

	// Create 10 games to test limit
	for i := 0; i < 10; i++ {
		game := db.Game{
			ConsoleID: snes.ID,
			Title:     fmt.Sprintf("Limit Test Game %d", i),
			FileName:  fmt.Sprintf("ltg%d.smc", i),
			FilePath:  fmt.Sprintf("/roms/snes/ltg%d.smc", i),
		}
		require.NoError(t, database.Create(&game).Error)
	}

	// Default limit=5 should cap results at 5
	code, resp := searchGet(t, router, token, "/api/search?q="+url.QueryEscape("Limit Test"))
	assert.Equal(t, http.StatusOK, code)
	assert.Equal(t, 10, resp.Games.Total) // all 10 match
	assert.Len(t, resp.Games.Results, 5)  // default limit = 5

	// Invalid limit (negative) should fall back to 5
	code, resp = searchGet(t, router, token, "/api/search?q="+url.QueryEscape("Limit Test")+"&limit=-1")
	assert.Equal(t, http.StatusOK, code)
	assert.Len(t, resp.Games.Results, 5)

	// limit=10 should return all
	code, resp = searchGet(t, router, token, "/api/search?q="+url.QueryEscape("Limit Test")+"&limit=10")
	assert.Equal(t, http.StatusOK, code)
	assert.Len(t, resp.Games.Results, 10)
}
