package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// setupFilterEnv creates a test environment for ListGames filter tests.
func setupFilterEnv(t *testing.T) (*gorm.DB, http.Handler, string) {
	t.Helper()
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)
	return database, router, token
}

// listGamesWithParams calls GET /api/games with the given query string and returns the parsed response.
func listGamesWithParams(t *testing.T, router http.Handler, token, queryString string) ([]map[string]interface{}, int64) {
	t.Helper()
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games?"+queryString, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code, "ListGames failed: %s", w.Body.String())

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	data := resp["data"].([]interface{})
	total := int64(resp["total"].(float64))

	var games []map[string]interface{}
	for _, d := range data {
		games = append(games, d.(map[string]interface{}))
	}
	return games, total
}

// createFilterGame creates a game with customizable metadata fields for filter testing.
func createFilterGame(t *testing.T, database *gorm.DB, consoleAbbr, title string, opts ...func(*db.Game)) db.Game {
	t.Helper()
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", consoleAbbr).First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     title,
		FileName:  title + ".rom",
		FilePath:  consoleAbbr + "/" + title + ".rom",
		IsPrimary: true,
	}
	for _, opt := range opts {
		opt(&game)
	}
	require.NoError(t, database.Create(&game).Error)
	return game
}

func withDeveloper(dev string) func(*db.Game) {
	return func(g *db.Game) { g.Developer = dev }
}

func withPublisher(pub string) func(*db.Game) {
	return func(g *db.Game) { g.Publisher = pub }
}

func withReleaseDate(rd string) func(*db.Game) {
	return func(g *db.Game) { g.ReleaseDate = rd }
}

func withRating(r float64) func(*db.Game) {
	return func(g *db.Game) { g.Rating = r }
}

func withGenre(genre string) func(*db.Game) {
	return func(g *db.Game) { g.Genre = genre }
}

func TestListGames_FilterByDeveloper(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	createFilterGame(t, database, "SNES", "Mario World", withDeveloper("Nintendo"))
	createFilterGame(t, database, "SNES", "Street Fighter", withDeveloper("Capcom"))
	createFilterGame(t, database, "SNES", "Donkey Kong Country", withDeveloper("Nintendo R&D"))

	games, total := listGamesWithParams(t, router, token, "developer=Nintendo")
	assert.Equal(t, int64(2), total, "should match both Nintendo and Nintendo R&D")
	assert.Len(t, games, 2)
}

func TestListGames_FilterByPublisher(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	createFilterGame(t, database, "SNES", "Game A", withPublisher("Konami"))
	createFilterGame(t, database, "SNES", "Game B", withPublisher("Capcom"))
	createFilterGame(t, database, "SNES", "Game C", withPublisher("Konami Digital"))

	games, total := listGamesWithParams(t, router, token, "publisher=Konami")
	assert.Equal(t, int64(2), total)
	assert.Len(t, games, 2)
}

func TestListGames_FilterByYearRange(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	createFilterGame(t, database, "SNES", "Game 1991", withReleaseDate("1991-06-15"))
	createFilterGame(t, database, "SNES", "Game 1993", withReleaseDate("1993-11-21"))
	createFilterGame(t, database, "SNES", "Game 1995", withReleaseDate("1995-03-10"))
	createFilterGame(t, database, "SNES", "Game 1997", withReleaseDate("1997-01-01"))

	// yearMin only
	games, _ := listGamesWithParams(t, router, token, "yearMin=1993")
	assert.Len(t, games, 3, "should include 1993, 1995, 1997")

	// yearMax only
	games, _ = listGamesWithParams(t, router, token, "yearMax=1993")
	assert.Len(t, games, 2, "should include 1991, 1993")

	// Both yearMin and yearMax
	games, _ = listGamesWithParams(t, router, token, "yearMin=1993&yearMax=1995")
	assert.Len(t, games, 2, "should include 1993 and 1995")
}

func TestListGames_FilterByRatingRange(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	createFilterGame(t, database, "SNES", "Low Rated", withRating(30.0))
	createFilterGame(t, database, "SNES", "Medium Rated", withRating(65.0))
	createFilterGame(t, database, "SNES", "High Rated", withRating(90.0))

	// ratingMin only
	games, _ := listGamesWithParams(t, router, token, "ratingMin=60")
	assert.Len(t, games, 2, "should include medium and high rated games")

	// ratingMax only
	games, _ = listGamesWithParams(t, router, token, "ratingMax=70")
	assert.Len(t, games, 2, "should include low and medium rated games")

	// Both ratingMin and ratingMax
	games, _ = listGamesWithParams(t, router, token, "ratingMin=50&ratingMax=80")
	assert.Len(t, games, 1, "should include only medium rated game")
	assert.Equal(t, "Medium Rated", games[0]["title"])
}

func TestListGames_FilterByPlayStatus_Unplayed(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	game1 := createFilterGame(t, database, "SNES", "Played Game", withRating(80))
	_ = createFilterGame(t, database, "SNES", "Unplayed Game", withRating(70))

	// Mark game1 as played by the test user
	var user db.User
	database.First(&user)
	database.Create(&db.PlayHistory{UserID: user.ID, GameID: game1.ID, PlayTime: 60})

	games, total := listGamesWithParams(t, router, token, "playStatus=unplayed")
	assert.Equal(t, int64(1), total)
	assert.Len(t, games, 1)
	assert.Equal(t, "Unplayed Game", games[0]["title"])
}

func TestListGames_FilterByPlayStatus_Played(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	game1 := createFilterGame(t, database, "SNES", "Played Game", withRating(80))
	_ = createFilterGame(t, database, "SNES", "Unplayed Game", withRating(70))

	var user db.User
	database.First(&user)
	database.Create(&db.PlayHistory{UserID: user.ID, GameID: game1.ID, PlayTime: 120})

	games, total := listGamesWithParams(t, router, token, "playStatus=played")
	assert.Equal(t, int64(1), total)
	assert.Len(t, games, 1)
	assert.Equal(t, "Played Game", games[0]["title"])
}

func TestListGames_FilterByPlayStatus_Favorited(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	game1 := createFilterGame(t, database, "SNES", "Fav Game", withRating(90))
	_ = createFilterGame(t, database, "SNES", "Not Fav Game", withRating(70))

	var user db.User
	database.First(&user)
	database.Create(&db.Favorite{UserID: user.ID, GameID: game1.ID})

	games, total := listGamesWithParams(t, router, token, "playStatus=favorited")
	assert.Equal(t, int64(1), total)
	assert.Len(t, games, 1)
	assert.Equal(t, "Fav Game", games[0]["title"])
}

func TestListGames_FilterByPlayStatus_PlayLater(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	game1 := createFilterGame(t, database, "SNES", "Queued Game", withRating(85))
	_ = createFilterGame(t, database, "SNES", "Regular Game", withRating(75))

	var user db.User
	database.First(&user)
	database.Create(&db.PlayLaterItem{UserID: user.ID, GameID: game1.ID})

	games, total := listGamesWithParams(t, router, token, "playStatus=play-later")
	assert.Equal(t, int64(1), total)
	assert.Len(t, games, 1)
	assert.Equal(t, "Queued Game", games[0]["title"])
}

func TestListGames_MultipleThemes(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	game1 := createFilterGame(t, database, "SNES", "Fantasy Game", withRating(80))
	game2 := createFilterGame(t, database, "SNES", "Sci-Fi Game", withRating(75))
	_ = createFilterGame(t, database, "SNES", "No Theme Game", withRating(70))

	database.Create(&db.GameTheme{GameID: game1.ID, IGDBThemeID: 17, Name: "Fantasy"})
	database.Create(&db.GameTheme{GameID: game2.ID, IGDBThemeID: 18, Name: "Sci-Fi"})

	// Multi-select themes
	games, total := listGamesWithParams(t, router, token, "themes=17,18")
	assert.Equal(t, int64(2), total)
	assert.Len(t, games, 2)

	// Single theme backward compat
	games, _ = listGamesWithParams(t, router, token, "theme=17")
	assert.Len(t, games, 1)
	assert.Equal(t, "Fantasy Game", games[0]["title"])
}

func TestListGames_MultipleConsoles(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	createFilterGame(t, database, "SNES", "SNES Game", withRating(80))
	createFilterGame(t, database, "NES", "NES Game", withRating(75))
	createFilterGame(t, database, "GBA", "GBA Game", withRating(70))

	// Multi-select
	games, total := listGamesWithParams(t, router, token, "consoles=SNES,NES")
	assert.Equal(t, int64(2), total)
	assert.Len(t, games, 2)

	// Backward compat (consoleId singular)
	games, _ = listGamesWithParams(t, router, token, "consoleId=GBA")
	assert.Len(t, games, 1)
	assert.Equal(t, "GBA Game", games[0]["title"])
}

func TestListGames_MultipleGenres(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	createFilterGame(t, database, "SNES", "Action Game", withGenre("Action"))
	createFilterGame(t, database, "SNES", "RPG Game", withGenre("RPG"))
	createFilterGame(t, database, "SNES", "Puzzle Game", withGenre("Puzzle"))

	games, total := listGamesWithParams(t, router, token, "genres=Action,RPG")
	assert.Equal(t, int64(2), total)
	assert.Len(t, games, 2)

	// Backward compat
	games, _ = listGamesWithParams(t, router, token, "genre=Puzzle")
	assert.Len(t, games, 1)
	assert.Equal(t, "Puzzle Game", games[0]["title"])
}

func TestListGames_MultipleKeywords(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	game1 := createFilterGame(t, database, "SNES", "Zombie Game", withRating(80))
	game2 := createFilterGame(t, database, "SNES", "Time Travel Game", withRating(75))
	_ = createFilterGame(t, database, "SNES", "Plain Game", withRating(70))

	database.Create(&db.GameKeyword{GameID: game1.ID, IGDBKeywordID: 100, Name: "Zombies"})
	database.Create(&db.GameKeyword{GameID: game2.ID, IGDBKeywordID: 200, Name: "Time Travel"})

	games, total := listGamesWithParams(t, router, token, "keywords=100,200")
	assert.Equal(t, int64(2), total)
	assert.Len(t, games, 2)

	// Backward compat
	games, _ = listGamesWithParams(t, router, token, "keyword=100")
	assert.Len(t, games, 1)
	assert.Equal(t, "Zombie Game", games[0]["title"])
}

func TestListGames_MultiplePerspectives(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	game1 := createFilterGame(t, database, "SNES", "FPS Game", withRating(80))
	game2 := createFilterGame(t, database, "SNES", "Side Scroller", withRating(75))
	_ = createFilterGame(t, database, "SNES", "No Perspective Game", withRating(70))

	database.Create(&db.GamePlayerPerspective{GameID: game1.ID, IGDBPerspectiveID: 1, Name: "First person"})
	database.Create(&db.GamePlayerPerspective{GameID: game2.ID, IGDBPerspectiveID: 3, Name: "Side view"})

	games, total := listGamesWithParams(t, router, token, "perspectives=1,3")
	assert.Equal(t, int64(2), total)
	assert.Len(t, games, 2)

	// Backward compat
	games, _ = listGamesWithParams(t, router, token, "perspective=1")
	assert.Len(t, games, 1)
	assert.Equal(t, "FPS Game", games[0]["title"])
}

func TestListGames_CombinedFilters(t *testing.T) {
	database, router, token := setupFilterEnv(t)

	// Create a game that matches all filters
	createFilterGame(t, database, "SNES", "Super Action",
		withDeveloper("Capcom"), withPublisher("Capcom"),
		withGenre("Action"), withReleaseDate("1994-08-15"), withRating(85.0))

	// Create games that only match some filters
	createFilterGame(t, database, "SNES", "Bad Action",
		withDeveloper("Capcom"), withPublisher("Capcom"),
		withGenre("Action"), withReleaseDate("1994-05-01"), withRating(40.0))

	createFilterGame(t, database, "NES", "NES RPG",
		withDeveloper("Square"), withPublisher("Square"),
		withGenre("RPG"), withReleaseDate("1994-06-01"), withRating(90.0))

	// Combine: SNES + Action + Capcom developer + rating >= 80 + year 1994
	games, total := listGamesWithParams(t, router, token,
		"consoles=SNES&genres=Action&developer=Capcom&ratingMin=80&yearMin=1994&yearMax=1994")
	assert.Equal(t, int64(1), total)
	assert.Len(t, games, 1)
	assert.Equal(t, "Super Action", games[0]["title"])
}
