package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// exploreTestEnv holds shared test fixtures for explore handler tests.
type exploreTestEnv struct {
	database *gorm.DB
	router   http.Handler
	token    string
}

func setupExploreTestEnv(t *testing.T) *exploreTestEnv {
	t.Helper()
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)
	return &exploreTestEnv{
		database: database,
		router:   router,
		token:    token,
	}
}

// createExploreGame creates a game with the given properties for testing.
func createExploreGame(t *testing.T, database *gorm.DB, consoleAbbr, title string, rating float64) db.Game {
	t.Helper()
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", consoleAbbr).First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     title,
		FileName:  title + ".rom",
		FilePath:  consoleAbbr + "/" + title + ".rom",
		Rating:    rating,
		Genre:     "Action",
	}
	require.NoError(t, database.Create(&game).Error)
	return game
}

// createExploreGameWithTime creates a game with a specific created_at time.
func createExploreGameWithTime(t *testing.T, database *gorm.DB, consoleAbbr, title string, rating float64, createdAt time.Time) db.Game {
	t.Helper()
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", consoleAbbr).First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     title,
		FileName:  title + ".rom",
		FilePath:  consoleAbbr + "/" + title + ".rom",
		Rating:    rating,
		Genre:     "Action",
	}
	require.NoError(t, database.Create(&game).Error)
	// Update created_at directly
	database.Model(&game).Update("created_at", createdAt)
	game.CreatedAt = createdAt
	return game
}

// --- Featured endpoint tests ---

func TestGetExploreFeatured_NoHeroArt(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create a game without hero art
	createExploreGame(t, env.database, "NES", "Super Mario Bros", 90.0)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/featured", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []FeaturedGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp)
}

func TestGetExploreFeatured_WithHeroArt(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create games with different ratings
	game1 := createExploreGame(t, env.database, "SNES", "Chrono Trigger", 95.0)
	game2 := createExploreGame(t, env.database, "NES", "Super Mario Bros 3", 90.0)
	game3 := createExploreGame(t, env.database, "GBA", "Metroid Fusion", 85.0)

	// Only game1 and game2 have both hero and logo art
	env.database.Create(&db.GameArtwork{
		GameID:  game1.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/chrono.png",
		LogoURL: "https://cdn.steamgriddb.com/logo/chrono.png",
	})
	env.database.Create(&db.GameArtwork{
		GameID:  game2.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/smb3.png",
		LogoURL: "https://cdn.steamgriddb.com/logo/smb3.png",
	})
	// game3 has hero but no logo — should NOT appear
	env.database.Create(&db.GameArtwork{
		GameID:  game3.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/metroid.png",
		LogoURL: "",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/featured", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []FeaturedGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Len(t, resp, 2)

	// Should be sorted by rating DESC
	assert.Equal(t, "Chrono Trigger", resp[0].Title)
	assert.Equal(t, 95.0, resp[0].Rating)
	assert.Equal(t, "https://cdn.steamgriddb.com/hero/chrono.png", resp[0].HeroURL)
	assert.Equal(t, "https://cdn.steamgriddb.com/logo/chrono.png", resp[0].LogoURL)
	assert.Equal(t, "Action", resp[0].Genre)

	assert.Equal(t, "Super Mario Bros 3", resp[1].Title)
	assert.Equal(t, 90.0, resp[1].Rating)
}

func TestGetExploreFeatured_ConsoleInfo(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Super Mario World", 95.0)
	env.database.Create(&db.GameArtwork{
		GameID:  game.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/smw.png",
		LogoURL: "https://cdn.steamgriddb.com/logo/smw.png",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/featured", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []FeaturedGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp, 1)

	assert.Equal(t, "snes", resp[0].ConsoleAbbreviation)
	assert.Equal(t, "snes", resp[0].ConsoleID)
	assert.Equal(t, "Super Nintendo", resp[0].ConsoleName)
	assert.NotEmpty(t, resp[0].ConsoleColor)
	assert.NotEmpty(t, resp[0].GameID)
}

func TestGetExploreFeatured_FavoriteAndPlayLater(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Super Mario World", 95.0)
	env.database.Create(&db.GameArtwork{
		GameID:  game.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/smw.png",
		LogoURL: "https://cdn.steamgriddb.com/logo/smw.png",
	})

	// Get the user ID from the token
	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Add to favorites
	env.database.Create(&db.Favorite{UserID: user.ID, GameID: game.ID})
	// Add to play later
	env.database.Create(&db.PlayLaterItem{UserID: user.ID, GameID: game.ID})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/featured", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []FeaturedGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp, 1)
	assert.True(t, resp[0].IsFavorite)
	assert.True(t, resp[0].IsPlayLater)
}

func TestGetExploreFeatured_Limit8(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create 10 games with hero art — should only return 8
	for i := 0; i < 10; i++ {
		game := createExploreGame(t, env.database, "NES", fmt.Sprintf("Game %d", i), float64(90-i))
		env.database.Create(&db.GameArtwork{
			GameID:  game.ID,
			HeroURL: fmt.Sprintf("https://cdn.steamgriddb.com/hero/game%d.png", i),
			LogoURL: fmt.Sprintf("https://cdn.steamgriddb.com/logo/game%d.png", i),
		})
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/featured", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []FeaturedGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Len(t, resp, 8)
}

// --- Rows endpoint tests ---

func TestGetExploreRows_EmptyLibrary(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Rows)

	// Verify the JSON contains an empty array "[]", not "null" for rows
	assert.Contains(t, w.Body.String(), `"rows":[]`)
}

func TestGetExploreRows_TopRated(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGame(t, env.database, "SNES", "Top Game", 95.0)
	createExploreGame(t, env.database, "NES", "Good Game", 80.0)
	createExploreGame(t, env.database, "GBA", "Average Game", 60.0)
	// Game with no rating should not appear in top-rated
	createExploreGame(t, env.database, "NES", "Unrated Game", 0)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Find the top-rated row
	var topRated *ExploreRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].ID == "top-rated" {
			topRated = &resp.Rows[i]
			break
		}
	}
	require.NotNil(t, topRated, "top-rated row should exist")
	assert.Equal(t, "Top Rated", topRated.Title)

	// Should have 3 rated games, sorted by rating DESC
	assert.Len(t, topRated.Games, 3)
	assert.Equal(t, "Top Game", topRated.Games[0].Title)
	assert.Equal(t, 95.0, topRated.Games[0].Rating)
	assert.Equal(t, "Good Game", topRated.Games[1].Title)
	assert.Equal(t, "Average Game", topRated.Games[2].Title)
}

func TestGetExploreRows_RecentlyAdded(t *testing.T) {
	env := setupExploreTestEnv(t)

	now := time.Now()
	createExploreGameWithTime(t, env.database, "SNES", "Oldest Game", 80.0, now.Add(-72*time.Hour))
	createExploreGameWithTime(t, env.database, "NES", "Middle Game", 75.0, now.Add(-24*time.Hour))
	createExploreGameWithTime(t, env.database, "GBA", "Newest Game", 70.0, now)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	var recentlyAdded *ExploreRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].ID == "recently-added" {
			recentlyAdded = &resp.Rows[i]
			break
		}
	}
	require.NotNil(t, recentlyAdded, "recently-added row should exist")
	assert.Equal(t, "Recently Added", recentlyAdded.Title)

	// Should be sorted by created_at DESC (newest first)
	require.Len(t, recentlyAdded.Games, 3)
	assert.Equal(t, "Newest Game", recentlyAdded.Games[0].Title)
	assert.Equal(t, "Middle Game", recentlyAdded.Games[1].Title)
	assert.Equal(t, "Oldest Game", recentlyAdded.Games[2].Title)
}

func TestGetExploreRows_HiddenGems(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Need at least 5 games for hidden gems to appear
	highRatedUnplayed := createExploreGame(t, env.database, "SNES", "Hidden Gem 1", 85.0)
	createExploreGame(t, env.database, "NES", "Hidden Gem 2", 80.0)
	highRatedPlayed := createExploreGame(t, env.database, "GBA", "Popular Game", 90.0)
	createExploreGame(t, env.database, "NES", "Filler 1", 50.0)
	createExploreGame(t, env.database, "NES", "Filler 2", 40.0)

	// Create a user to add play history
	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Give "Popular Game" significant play time
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     highRatedPlayed.ID,
		PlayTime:   10000,
		LastPlayed: time.Now(),
	})
	// Give "Hidden Gem 1" zero play time (no entry = zero)
	_ = highRatedUnplayed

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	var hiddenGems *ExploreRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].ID == "hidden-gems" {
			hiddenGems = &resp.Rows[i]
			break
		}
	}
	require.NotNil(t, hiddenGems, "hidden-gems row should exist")
	assert.Equal(t, "Hidden Gems", hiddenGems.Title)

	// Hidden Gem 1 and Hidden Gem 2 should appear (high rating, low/no play time)
	// Popular Game has high play time, so depending on threshold it might be excluded
	foundGem1 := false
	foundGem2 := false
	for _, g := range hiddenGems.Games {
		if g.Title == "Hidden Gem 1" {
			foundGem1 = true
		}
		if g.Title == "Hidden Gem 2" {
			foundGem2 = true
		}
	}
	assert.True(t, foundGem1, "Hidden Gem 1 should be in hidden gems (no play time)")
	assert.True(t, foundGem2, "Hidden Gem 2 should be in hidden gems (no play time)")
}

func TestGetExploreRows_HiddenGems_NotShownForSmallLibrary(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create only 3 games (< 5 threshold)
	createExploreGame(t, env.database, "SNES", "Game 1", 90.0)
	createExploreGame(t, env.database, "NES", "Game 2", 85.0)
	createExploreGame(t, env.database, "GBA", "Game 3", 80.0)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	for _, row := range resp.Rows {
		assert.NotEqual(t, "hidden-gems", row.ID, "hidden gems should not appear for < 5 games")
	}
}

func TestGetExploreRows_HiddenGems_NoPlayHistory(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create 6 games with high ratings, no play history at all
	for i := 0; i < 6; i++ {
		createExploreGame(t, env.database, "NES", fmt.Sprintf("Gem %d", i), float64(90-i))
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	var hiddenGems *ExploreRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].ID == "hidden-gems" {
			hiddenGems = &resp.Rows[i]
			break
		}
	}
	// With no play history and all games >= 75 rating, all qualify as hidden gems
	require.NotNil(t, hiddenGems, "hidden gems should appear when no play history exists")
	assert.Greater(t, len(hiddenGems.Games), 0)
}

func TestGetExploreRows_MostPlayed(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "SNES", "Most Played", 80.0)
	game2 := createExploreGame(t, env.database, "NES", "Second Most Played", 75.0)
	createExploreGame(t, env.database, "GBA", "Never Played", 70.0)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Add play history
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     game1.ID,
		PlayTime:   5000,
		LastPlayed: time.Now(),
	})
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     game2.ID,
		PlayTime:   2000,
		LastPlayed: time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	var mostPlayed *ExploreRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].ID == "most-played" {
			mostPlayed = &resp.Rows[i]
			break
		}
	}
	require.NotNil(t, mostPlayed, "most-played row should exist when play history exists")
	assert.Equal(t, "Most Played on Your Server", mostPlayed.Title)

	// Should be sorted by total play time DESC
	require.Len(t, mostPlayed.Games, 2)
	assert.Equal(t, "Most Played", mostPlayed.Games[0].Title)
	assert.Equal(t, "Second Most Played", mostPlayed.Games[1].Title)
}

func TestGetExploreRows_MostPlayed_AggregatesAcrossUsers(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "SNES", "Community Favorite", 80.0)
	game2 := createExploreGame(t, env.database, "NES", "Single User Game", 75.0)

	// Get owner user
	var user1 db.User
	require.NoError(t, env.database.First(&user1).Error)

	// Create a second user
	ownerToken := env.token
	user2Token := createNonOwnerUser(t, env.router, ownerToken, "player2", "player2@example.com", "password123")
	_ = user2Token
	var user2 db.User
	require.NoError(t, env.database.Where("username = ?", "player2").First(&user2).Error)

	// Both users play game1
	env.database.Create(&db.PlayHistory{
		UserID:     user1.ID,
		GameID:     game1.ID,
		PlayTime:   3000,
		LastPlayed: time.Now(),
	})
	env.database.Create(&db.PlayHistory{
		UserID:     user2.ID,
		GameID:     game1.ID,
		PlayTime:   3000,
		LastPlayed: time.Now(),
	})
	// Only user1 plays game2 with more time
	env.database.Create(&db.PlayHistory{
		UserID:     user1.ID,
		GameID:     game2.ID,
		PlayTime:   5000,
		LastPlayed: time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	var mostPlayed *ExploreRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].ID == "most-played" {
			mostPlayed = &resp.Rows[i]
			break
		}
	}
	require.NotNil(t, mostPlayed)

	// game1 has 6000 total (3000+3000), game2 has 5000 total
	// game1 should be first
	require.Len(t, mostPlayed.Games, 2)
	assert.Equal(t, "Community Favorite", mostPlayed.Games[0].Title)
	assert.Equal(t, "Single User Game", mostPlayed.Games[1].Title)
}

func TestGetExploreRows_MostPlayed_OmittedWhenNoPlayHistory(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGame(t, env.database, "SNES", "Some Game", 80.0)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	for _, row := range resp.Rows {
		assert.NotEqual(t, "most-played", row.ID, "most-played row should be omitted when no play history")
	}
}

func TestGetExploreRows_AllFourRows(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create enough games for all rows to appear
	// Need at least 5 games, some with rating >= 75, and play history
	var games []db.Game
	for i := 0; i < 6; i++ {
		game := createExploreGame(t, env.database, "NES", fmt.Sprintf("Game %d", i), float64(90-i*5))
		games = append(games, game)
	}

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Add play history to some games (not all, so hidden gems can work)
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     games[0].ID,
		PlayTime:   10000,
		LastPlayed: time.Now(),
	})
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     games[1].ID,
		PlayTime:   5000,
		LastPlayed: time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// All 4 row types should be present
	rowIDs := make(map[string]bool)
	for _, row := range resp.Rows {
		rowIDs[row.ID] = true
	}
	assert.True(t, rowIDs["top-rated"], "top-rated row should exist")
	assert.True(t, rowIDs["recently-added"], "recently-added row should exist")
	assert.True(t, rowIDs["hidden-gems"], "hidden-gems row should exist")
	assert.True(t, rowIDs["most-played"], "most-played row should exist")

	// Verify ordering: top-rated, recently-added, hidden-gems, most-played
	require.Len(t, resp.Rows, 4)
	assert.Equal(t, "top-rated", resp.Rows[0].ID)
	assert.Equal(t, "recently-added", resp.Rows[1].ID)
	assert.Equal(t, "hidden-gems", resp.Rows[2].ID)
	assert.Equal(t, "most-played", resp.Rows[3].ID)
}

func TestGetExploreRows_UserFavoriteStatus(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "NES", "Favorited Game", 90.0)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Favorite this game
	env.database.Create(&db.Favorite{UserID: user.ID, GameID: game.ID})
	// Add to play later
	env.database.Create(&db.PlayLaterItem{UserID: user.ID, GameID: game.ID})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Find top-rated row and check the game
	var topRated *ExploreRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].ID == "top-rated" {
			topRated = &resp.Rows[i]
			break
		}
	}
	require.NotNil(t, topRated)
	require.Len(t, topRated.Games, 1)
	assert.True(t, topRated.Games[0].IsFavorite, "game should be marked as favorite")
	assert.True(t, topRated.Games[0].IsInPlayLater, "game should be marked as play later")
}

func TestGetExploreRows_GamesIncludeConsoleInfo(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGame(t, env.database, "SNES", "SNES Game", 90.0)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	var topRated *ExploreRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].ID == "top-rated" {
			topRated = &resp.Rows[i]
			break
		}
	}
	require.NotNil(t, topRated)
	require.Len(t, topRated.Games, 1)

	game := topRated.Games[0]
	assert.Equal(t, "snes", game.ConsoleID)
	assert.NotEmpty(t, game.ConsoleName)
	assert.NotEmpty(t, game.ID)
	assert.Equal(t, "SNES Game", game.Title)
	assert.Equal(t, 90.0, game.Rating)
}

func TestGetExploreRows_TopRated_CrossConsole(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGame(t, env.database, "SNES", "Best SNES", 95.0)
	createExploreGame(t, env.database, "NES", "Best NES", 90.0)
	createExploreGame(t, env.database, "GBA", "Best GBA", 85.0)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	var topRated *ExploreRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].ID == "top-rated" {
			topRated = &resp.Rows[i]
			break
		}
	}
	require.NotNil(t, topRated)
	require.Len(t, topRated.Games, 3)

	// Games from different consoles should be mixed together
	consoles := make(map[string]bool)
	for _, g := range topRated.Games {
		consoles[g.ConsoleID] = true
	}
	assert.Len(t, consoles, 3, "top-rated should include games from multiple consoles")
}

func TestGetExploreRows_EmptyRowsOmitted(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create only unrated games (no games with rating > 0)
	createExploreGame(t, env.database, "NES", "Unrated", 0)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// top-rated should not appear (no rated games)
	for _, row := range resp.Rows {
		assert.NotEqual(t, "top-rated", row.ID, "top-rated row should be omitted when no games have rating > 0")
	}

	// recently-added SHOULD appear (we have 1 game)
	found := false
	for _, row := range resp.Rows {
		if row.ID == "recently-added" {
			found = true
			break
		}
	}
	assert.True(t, found, "recently-added should appear even with unrated games")
}

func TestGetExploreRows_HiddenGems_OnlyHighRated(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create 5 games but none with rating >= 75
	for i := 0; i < 5; i++ {
		createExploreGame(t, env.database, "NES", fmt.Sprintf("Low Rated %d", i), float64(50+i))
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	for _, row := range resp.Rows {
		assert.NotEqual(t, "hidden-gems", row.ID, "hidden gems should not appear when no games have rating >= 75")
	}
}

func TestGetExploreRows_TopRated_Max20(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create 25 rated games
	for i := 0; i < 25; i++ {
		createExploreGame(t, env.database, "NES", fmt.Sprintf("Rated Game %d", i), float64(95-i))
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ExploreRowsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	var topRated *ExploreRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].ID == "top-rated" {
			topRated = &resp.Rows[i]
			break
		}
	}
	require.NotNil(t, topRated)
	assert.Len(t, topRated.Games, 20, "top-rated should be limited to 20 games")
}

func TestGetExploreRows_RequiresAuth(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/rows", nil)
	// No auth header
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestGetExploreFeatured_RequiresAuth(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/featured", nil)
	// No auth header
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

// --- Featured Series endpoint tests ---

func TestGetExploreFeaturedSeries_Empty(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/series/featured", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []FeaturedSeriesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp)
}

func TestGetExploreFeaturedSeries_RequiresMinTwoLibraryGames(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "NES", "Mario1", 90)

	series := db.GameSeries{IGDBCollectionID: 100, Name: "Super Mario"}
	require.NoError(t, env.database.Create(&series).Error)

	// Only 1 library game + 1 non-library game = should NOT appear
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game.ID, IGDBGameID: 100, Name: "Mario 1",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: nil, IGDBGameID: 200, Name: "Mario 2",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/series/featured", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []FeaturedSeriesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp, "series with only 1 library game should not appear")
}

func TestGetExploreFeaturedSeries_WithData(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "NES", "Zelda1", 90)
	game2 := createExploreGame(t, env.database, "SNES", "Zelda2", 95)
	game3 := createExploreGame(t, env.database, "NES", "Mario1", 85)
	game4 := createExploreGame(t, env.database, "NES", "Mario2", 80)
	game5 := createExploreGame(t, env.database, "NES", "Mario3", 75)

	// Zelda series: 2 library games
	zeldaSeries := db.GameSeries{IGDBCollectionID: 100, Name: "The Legend of Zelda"}
	require.NoError(t, env.database.Create(&zeldaSeries).Error)
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: zeldaSeries.ID, GameID: &game1.ID, IGDBGameID: 100, Name: "Zelda 1",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: zeldaSeries.ID, GameID: &game2.ID, IGDBGameID: 200, Name: "Zelda 2",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: zeldaSeries.ID, GameID: nil, IGDBGameID: 300, Name: "Zelda 3",
	})

	// Mario series: 3 library games (should appear first)
	marioSeries := db.GameSeries{IGDBCollectionID: 200, Name: "Super Mario"}
	require.NoError(t, env.database.Create(&marioSeries).Error)
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: marioSeries.ID, GameID: &game3.ID, IGDBGameID: 400, Name: "Mario 1",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: marioSeries.ID, GameID: &game4.ID, IGDBGameID: 500, Name: "Mario 2",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: marioSeries.ID, GameID: &game5.ID, IGDBGameID: 600, Name: "Mario 3",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/series/featured", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []FeaturedSeriesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp, 2)

	// Sorted by library game count DESC
	assert.Equal(t, "Super Mario", resp[0].Name)
	assert.Equal(t, 3, resp[0].LibraryGames)
	assert.Equal(t, 3, resp[0].TotalGames)
	assert.Equal(t, 1, resp[0].ConsoleCount) // All NES

	assert.Equal(t, "The Legend of Zelda", resp[1].Name)
	assert.Equal(t, 2, resp[1].LibraryGames)
	assert.Equal(t, 3, resp[1].TotalGames)
	assert.Equal(t, 2, resp[1].ConsoleCount) // NES + SNES
}

func TestGetExploreFeaturedSeries_HeroArt(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "NES", "LowRated", 50)
	game2 := createExploreGame(t, env.database, "NES", "HighRated", 95)

	// Both have hero art, highest rated should win
	env.database.Create(&db.GameArtwork{
		GameID:  game1.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/low.png",
	})
	env.database.Create(&db.GameArtwork{
		GameID:  game2.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/high.png",
	})

	series := db.GameSeries{IGDBCollectionID: 100, Name: "Test Series"}
	require.NoError(t, env.database.Create(&series).Error)

	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game1.ID, IGDBGameID: 100, Name: "Low",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game2.ID, IGDBGameID: 200, Name: "High",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/series/featured", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []FeaturedSeriesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp, 1)
	assert.Equal(t, "https://cdn.steamgriddb.com/hero/high.png", resp[0].HeroURL)
}

func TestGetExploreFeaturedSeries_ExcludesSoftDeletedGames(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "NES", "Alive1", 90)
	game2 := createExploreGame(t, env.database, "NES", "Alive2", 85)
	game3 := createExploreGame(t, env.database, "NES", "Deleted", 80)

	series := db.GameSeries{IGDBCollectionID: 100, Name: "Test"}
	require.NoError(t, env.database.Create(&series).Error)

	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game1.ID, IGDBGameID: 100, Name: "G1",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game2.ID, IGDBGameID: 200, Name: "G2",
	})
	env.database.Create(&db.GameSeriesEntry{
		SeriesID: series.ID, GameID: &game3.ID, IGDBGameID: 300, Name: "G3",
	})

	// Soft-delete one game — should drop library count to 2, still >= 2 threshold
	env.database.Delete(&game3)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/series/featured", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []FeaturedSeriesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp, 1)
	assert.Equal(t, 2, resp[0].LibraryGames)
}

func TestGetExploreFeaturedSeries_RequiresAuth(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/series/featured", nil)
	// No auth header
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestGetExploreFeaturedSeries_Limit20(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create 25 series, each with 2 library games
	for i := 0; i < 25; i++ {
		g1 := createExploreGame(t, env.database, "NES", fmt.Sprintf("Game%d_A", i), float64(90-i))
		g2 := createExploreGame(t, env.database, "NES", fmt.Sprintf("Game%d_B", i), float64(85-i))

		series := db.GameSeries{IGDBCollectionID: 1000 + i, Name: fmt.Sprintf("Series %d", i)}
		require.NoError(t, env.database.Create(&series).Error)

		env.database.Create(&db.GameSeriesEntry{
			SeriesID: series.ID, GameID: &g1.ID, IGDBGameID: 1000 + i*2, Name: fmt.Sprintf("Game%d_A", i),
		})
		env.database.Create(&db.GameSeriesEntry{
			SeriesID: series.ID, GameID: &g2.ID, IGDBGameID: 1001 + i*2, Name: fmt.Sprintf("Game%d_B", i),
		})
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/series/featured", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []FeaturedSeriesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Len(t, resp, 20, "should be limited to 20 series")
}


// --- Helper: create game with custom genre and players ---

func createExploreGameWithGenre(t *testing.T, database *gorm.DB, consoleAbbr, title string, rating float64, genre string, players int) db.Game {
	t.Helper()
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", consoleAbbr).First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     title,
		FileName:  title + ".rom",
		FilePath:  consoleAbbr + "/" + title + ".rom",
		Rating:    rating,
		Genre:     genre,
		Players:   players,
	}
	require.NoError(t, database.Create(&game).Error)
	return game
}

// createExploreGameWithCover creates a game with cover art for surprise endpoint testing.
func createExploreGameWithCover(t *testing.T, database *gorm.DB, consoleAbbr, title string, rating float64) db.Game {
	t.Helper()
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", consoleAbbr).First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     title,
		FileName:  title + ".rom",
		FilePath:  consoleAbbr + "/" + title + ".rom",
		Rating:    rating,
		Genre:     "Action",
		CoverURL:  "https://images.igdb.com/cover/" + title + ".jpg",
	}
	require.NoError(t, database.Create(&game).Error)
	return game
}

// --- Mood endpoint tests ---

func TestGetExploreMoods(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/moods", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []MoodResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Len(t, resp, 6)

	// Verify all mood IDs are present
	ids := make(map[string]bool)
	for _, m := range resp {
		ids[m.ID] = true
		assert.NotEmpty(t, m.Name)
		assert.NotEmpty(t, m.Description)
		assert.NotEmpty(t, m.Icon)
		assert.Len(t, m.Gradient, 2)
	}
	assert.True(t, ids["chill"])
	assert.True(t, ids["challenge"])
	assert.True(t, ids["nostalgia"])
	assert.True(t, ids["something-new"])
	assert.True(t, ids["quick"])
	assert.True(t, ids["together"])
}

func TestGetMoodGames_Chill(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create games matching chill criteria
	puzzleGame := createExploreGameWithGenre(t, env.database, "NES", "Puzzle Paradise", 80, "Puzzle", 1)
	simGame := createExploreGameWithGenre(t, env.database, "SNES", "SimCity", 75, "Simulation", 1)

	// Create a game with Fantasy theme
	fantasyGame := createExploreGame(t, env.database, "GBA", "Fantasy Quest", 85)
	env.database.Create(&db.GameTheme{GameID: fantasyGame.ID, IGDBThemeID: 1, Name: "Fantasy"})

	// Create a game with "casual" keyword
	casualGame := createExploreGame(t, env.database, "NES", "Casual Fun", 70)
	env.database.Create(&db.GameKeyword{GameID: casualGame.ID, IGDBKeywordID: 1, Name: "casual"})

	// Create a game that does NOT match chill criteria
	createExploreGame(t, env.database, "NES", "Dark Shooter", 90)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/mood/chill", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Should have the 4 matching games
	assert.Len(t, resp, 4)

	// Collect returned titles
	titles := make(map[string]bool)
	for _, g := range resp {
		titles[g.Title] = true
	}
	assert.True(t, titles[puzzleGame.Title], "puzzle game should be included")
	assert.True(t, titles[simGame.Title], "simulation game should be included")
	assert.True(t, titles[fantasyGame.Title], "fantasy theme game should be included")
	assert.True(t, titles[casualGame.Title], "casual keyword game should be included")
	assert.False(t, titles["Dark Shooter"], "non-chill game should not be included")
}

func TestGetMoodGames_Challenge(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Game with Horror theme
	horrorGame := createExploreGame(t, env.database, "NES", "Resident Evil", 85)
	env.database.Create(&db.GameTheme{GameID: horrorGame.ID, IGDBThemeID: 2, Name: "Horror"})

	// Game with Survival theme
	survivalGame := createExploreGame(t, env.database, "SNES", "Survival Island", 80)
	env.database.Create(&db.GameTheme{GameID: survivalGame.ID, IGDBThemeID: 3, Name: "Survival"})

	// Game with "difficult" keyword
	hardGame := createExploreGame(t, env.database, "GBA", "Super Hard Game", 75)
	env.database.Create(&db.GameKeyword{GameID: hardGame.ID, IGDBKeywordID: 2, Name: "difficult"})

	// Non-matching game
	createExploreGame(t, env.database, "NES", "Easy Game", 90)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/mood/challenge", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Len(t, resp, 3)

	titles := make(map[string]bool)
	for _, g := range resp {
		titles[g.Title] = true
	}
	assert.True(t, titles["Resident Evil"])
	assert.True(t, titles["Survival Island"])
	assert.True(t, titles["Super Hard Game"])
	assert.False(t, titles["Easy Game"])
}

func TestGetMoodGames_Nostalgia(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "NES", "Most Played Classic", 90)
	game2 := createExploreGame(t, env.database, "SNES", "Second Most Played", 85)
	createExploreGame(t, env.database, "GBA", "Never Played", 80)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Add play history for user
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     game1.ID,
		PlayTime:   5000,
		LastPlayed: time.Now(),
	})
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     game2.ID,
		PlayTime:   2000,
		LastPlayed: time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/mood/nostalgia", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Should return only played games, sorted by play time DESC
	assert.Len(t, resp, 2)
	assert.Equal(t, "Most Played Classic", resp[0].Title)
	assert.Equal(t, "Second Most Played", resp[1].Title)
}

func TestGetMoodGames_SomethingNew(t *testing.T) {
	env := setupExploreTestEnv(t)

	playedGame := createExploreGame(t, env.database, "NES", "Already Played", 90)
	unplayedGame1 := createExploreGame(t, env.database, "SNES", "Brand New 1", 85)
	unplayedGame2 := createExploreGame(t, env.database, "GBA", "Brand New 2", 80)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// User has played one game
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     playedGame.ID,
		PlayTime:   3000,
		LastPlayed: time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/mood/something-new", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Should return only unplayed games, sorted by rating DESC
	assert.Len(t, resp, 2)
	titles := make(map[string]bool)
	for _, g := range resp {
		titles[g.Title] = true
	}
	assert.True(t, titles[unplayedGame1.Title])
	assert.True(t, titles[unplayedGame2.Title])
	assert.False(t, titles[playedGame.Title])
}

func TestGetMoodGames_Quick(t *testing.T) {
	env := setupExploreTestEnv(t)

	quickGame := createExploreGame(t, env.database, "NES", "Quick Game", 80)
	longGame := createExploreGame(t, env.database, "SNES", "Long Game", 85)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Quick game: avg session time < 900 seconds (short sessions)
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     quickGame.ID,
		PlayTime:   300, // 5 minutes
		LastPlayed: time.Now(),
	})
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     quickGame.ID,
		PlayTime:   400, // ~6.7 minutes
		LastPlayed: time.Now(),
	})

	// Long game: avg session time > 900 seconds
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     longGame.ID,
		PlayTime:   3600, // 1 hour
		LastPlayed: time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/mood/quick", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Should only return the quick game
	assert.Len(t, resp, 1)
	assert.Equal(t, "Quick Game", resp[0].Title)
}

func TestGetMoodGames_Together(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Multiplayer game
	multiGame := createExploreGameWithGenre(t, env.database, "NES", "Mario Kart", 90, "Racing", 4)
	// Another multiplayer
	coopGame := createExploreGameWithGenre(t, env.database, "SNES", "Contra", 85, "Action", 2)
	// Single player only
	createExploreGameWithGenre(t, env.database, "GBA", "Solo RPG", 80, "RPG", 1)
	// Players = 0 (unset, should not match)
	createExploreGame(t, env.database, "NES", "Unknown Players", 75)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/mood/together", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Len(t, resp, 2)
	titles := make(map[string]bool)
	for _, g := range resp {
		titles[g.Title] = true
	}
	assert.True(t, titles[multiGame.Title])
	assert.True(t, titles[coopGame.Title])
}

func TestGetMoodGames_InvalidMood(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/mood/nonexistent", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestGetMoodGames_Empty(t *testing.T) {
	env := setupExploreTestEnv(t)

	// No games with Horror/Survival themes or difficult/hardcore keywords exist
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/mood/challenge", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp)

	// Verify JSON is empty array, not null
	assert.Contains(t, w.Body.String(), "[]")
}

// --- Surprise endpoint tests ---

func TestGetSurpriseGame(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create eligible games (rating > 70 with cover art)
	createExploreGameWithCover(t, env.database, "NES", "Great Game 1", 80)
	createExploreGameWithCover(t, env.database, "SNES", "Great Game 2", 85)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/surprise", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.NotEmpty(t, resp.ID)
	assert.NotEmpty(t, resp.Title)
	assert.Greater(t, resp.Rating, 70.0)
	assert.NotEmpty(t, resp.CoverURL)
}

func TestGetSurpriseGame_NoGames(t *testing.T) {
	env := setupExploreTestEnv(t)

	// No games with rating > 70 and cover art
	createExploreGame(t, env.database, "NES", "Low Rated", 50)          // low rating
	createExploreGame(t, env.database, "NES", "High Rated No Cover", 90) // no cover

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/surprise", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetMoodGames_AuthRequired(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Test mood endpoint without auth
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/mood/chill", nil)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)

	// Test moods list without auth
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/explore/moods", nil)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)

	// Test surprise without auth
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/explore/surprise", nil)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

// --- For You endpoint tests ---

func TestGetForYou_BecauseYouPlayed(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create an RPG that the user has played a lot
	playedGame := createExploreGameWithGenre(t, env.database, "SNES", "Chrono Trigger", 95, "RPG", 1)
	// Create RPGs that the user hasn't played
	recGame1 := createExploreGameWithGenre(t, env.database, "SNES", "Final Fantasy VI", 92, "RPG", 1)
	recGame2 := createExploreGameWithGenre(t, env.database, "SNES", "Earthbound", 88, "RPG", 1)
	// Create an Action game (different genre, should NOT appear in this row)
	createExploreGameWithGenre(t, env.database, "NES", "Contra", 85, "Action", 2)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// User has played Chrono Trigger for 5 hours
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     playedGame.ID,
		PlayTime:   18000,
		LastPlayed: time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/for-you", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ForYouResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Should have a "because_you_played" row
	var becauseRow *ForYouRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].Type == "because_you_played" {
			becauseRow = &resp.Rows[i]
			break
		}
	}
	require.NotNil(t, becauseRow, "should have a because_you_played row")
	assert.Contains(t, becauseRow.Title, "Chrono Trigger")
	assert.NotNil(t, becauseRow.SourceGame)
	assert.Equal(t, "Chrono Trigger", becauseRow.SourceGame.Title)

	// Should recommend the unplayed RPGs, NOT the played game, NOT the Action game
	titles := make(map[string]bool)
	for _, g := range becauseRow.Games {
		titles[g.Title] = true
	}
	assert.True(t, titles[recGame1.Title], "should recommend Final Fantasy VI")
	assert.True(t, titles[recGame2.Title], "should recommend Earthbound")
	assert.False(t, titles[playedGame.Title], "should not recommend the played game")
	assert.False(t, titles["Contra"], "should not recommend different genre")
}

func TestGetForYou_MoreGenre(t *testing.T) {
	env := setupExploreTestEnv(t)

	// User plays RPGs mostly, Action a little
	rpgGame := createExploreGameWithGenre(t, env.database, "SNES", "RPG Played", 85, "RPG", 1)
	actionGame := createExploreGameWithGenre(t, env.database, "NES", "Action Played", 80, "Action", 1)
	// Unplayed RPG (should be recommended)
	unplayedRPG := createExploreGameWithGenre(t, env.database, "SNES", "Unplayed RPG", 90, "RPG", 1)
	// Unplayed Action (should NOT be in this row — RPG is the top genre)
	createExploreGameWithGenre(t, env.database, "NES", "Unplayed Action", 88, "Action", 1)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     rpgGame.ID,
		PlayTime:   10000,
		LastPlayed: time.Now(),
	})
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     actionGame.ID,
		PlayTime:   2000,
		LastPlayed: time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/for-you", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ForYouResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	var moreGenreRow *ForYouRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].Type == "more_genre" {
			moreGenreRow = &resp.Rows[i]
			break
		}
	}
	require.NotNil(t, moreGenreRow, "should have a more_genre row")
	assert.Contains(t, moreGenreRow.Title, "RPG")

	titles := make(map[string]bool)
	for _, g := range moreGenreRow.Games {
		titles[g.Title] = true
	}
	assert.True(t, titles[unplayedRPG.Title], "should recommend unplayed RPG")
	assert.False(t, titles[rpgGame.Title], "should not include already-played RPG")
}

func TestGetForYou_UnfinishedBusiness(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Game played for 10 minutes (< 30 min), last played 10 days ago (> 7 days)
	unfinishedGame := createExploreGame(t, env.database, "NES", "Started But Not Finished", 85)
	// Game played for 2 hours (> 30 min, should NOT be "unfinished")
	finishedGame := createExploreGame(t, env.database, "SNES", "Played A Lot", 90)
	// Game played 2 minutes ago (< 7 days, should NOT be "unfinished")
	recentGame := createExploreGame(t, env.database, "GBA", "Just Started", 80)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	tenDaysAgo := time.Now().Add(-10 * 24 * time.Hour)
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     unfinishedGame.ID,
		PlayTime:   600, // 10 minutes
		LastPlayed: tenDaysAgo,
	})
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     finishedGame.ID,
		PlayTime:   7200, // 2 hours
		LastPlayed: tenDaysAgo,
	})
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     recentGame.ID,
		PlayTime:   300, // 5 minutes
		LastPlayed: time.Now().Add(-1 * time.Hour),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/for-you", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ForYouResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	var unfinishedRow *ForYouRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].Type == "unfinished" {
			unfinishedRow = &resp.Rows[i]
			break
		}
	}
	require.NotNil(t, unfinishedRow, "should have an unfinished row")
	assert.Equal(t, "Your unfinished business", unfinishedRow.Title)

	titles := make(map[string]bool)
	for _, g := range unfinishedRow.Games {
		titles[g.Title] = true
	}
	assert.True(t, titles[unfinishedGame.Title], "should include short/old game")
	assert.False(t, titles[finishedGame.Title], "should not include game with > 30 min play time")
	assert.False(t, titles[recentGame.Title], "should not include recently played game")
}

func TestGetForYou_ExpandHorizons(t *testing.T) {
	env := setupExploreTestEnv(t)

	// User has only played RPGs
	playedRPG := createExploreGameWithGenre(t, env.database, "SNES", "RPG Game", 90, "RPG", 1)
	// Racing games exist but user hasn't played any (rating > 70)
	racingGame1 := createExploreGameWithGenre(t, env.database, "SNES", "Mario Kart", 88, "Racing", 2)
	racingGame2 := createExploreGameWithGenre(t, env.database, "NES", "Excitebike", 75, "Racing", 1)
	// Low-rated Puzzle game (rating <= 70, should not make Puzzle qualify)
	createExploreGameWithGenre(t, env.database, "NES", "Bad Puzzle", 50, "Puzzle", 1)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     playedRPG.ID,
		PlayTime:   5000,
		LastPlayed: time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/for-you", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ForYouResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	var expandRow *ForYouRowResponse
	for i := range resp.Rows {
		if resp.Rows[i].Type == "expand_horizons" {
			expandRow = &resp.Rows[i]
			break
		}
	}
	require.NotNil(t, expandRow, "should have an expand_horizons row")
	assert.Contains(t, expandRow.Title, "Racing")
	assert.Equal(t, "Racing", expandRow.Genre)

	titles := make(map[string]bool)
	for _, g := range expandRow.Games {
		titles[g.Title] = true
	}
	assert.True(t, titles[racingGame1.Title])
	assert.True(t, titles[racingGame2.Title])
	assert.False(t, titles["Bad Puzzle"], "low-rated puzzle should not appear")
}

func TestGetForYou_EmptyHistory(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create some games but no play history
	createExploreGame(t, env.database, "NES", "Game 1", 90)
	createExploreGame(t, env.database, "SNES", "Game 2", 85)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/for-you", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ForYouResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// With no play history, all personalized rows should be empty
	assert.Empty(t, resp.Rows, "user with no play history should have no recommendation rows")
}

func TestGetForYou_AuthRequired(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/for-you", nil)
	// No auth header
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

// --- Taste Profile endpoint tests ---

func TestGetTasteProfile(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create games in different genres and consoles
	rpgGame := createExploreGameWithGenre(t, env.database, "SNES", "Chrono Trigger", 95, "RPG", 1)
	actionGame := createExploreGameWithGenre(t, env.database, "NES", "Contra", 85, "Action", 2)
	rpgGame2 := createExploreGameWithGenre(t, env.database, "SNES", "FF6", 92, "RPG", 1)

	// Add themes to RPG games
	env.database.Create(&db.GameTheme{GameID: rpgGame.ID, IGDBThemeID: 1, Name: "Fantasy"})
	env.database.Create(&db.GameTheme{GameID: rpgGame2.ID, IGDBThemeID: 1, Name: "Fantasy"})
	env.database.Create(&db.GameTheme{GameID: actionGame.ID, IGDBThemeID: 2, Name: "Sci-Fi"})

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// RPG: 7000 seconds, Action: 3000 seconds => total 10000
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     rpgGame.ID,
		PlayTime:   4000,
		LastPlayed: time.Now(),
	})
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     rpgGame2.ID,
		PlayTime:   3000,
		LastPlayed: time.Now(),
	})
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     actionGame.ID,
		PlayTime:   3000,
		LastPlayed: time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/taste-profile", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp TasteProfileResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, int64(10000), resp.TotalPlayTime)

	// Genre breakdown
	require.GreaterOrEqual(t, len(resp.Genres), 2)
	// RPG should be first (70% of play time)
	assert.Equal(t, "RPG", resp.Genres[0].Name)
	assert.Equal(t, float64(70), resp.Genres[0].Percentage)
	assert.Equal(t, int64(7000), resp.Genres[0].PlayTime)
	assert.Equal(t, 2, resp.Genres[0].GameCount)

	assert.Equal(t, "Action", resp.Genres[1].Name)
	assert.Equal(t, float64(30), resp.Genres[1].Percentage)
	assert.Equal(t, int64(3000), resp.Genres[1].PlayTime)
	assert.Equal(t, 1, resp.Genres[1].GameCount)

	// Theme breakdown
	require.GreaterOrEqual(t, len(resp.Themes), 1)
	// Fantasy should have the most play time (both RPG games = 7000s)
	fantasyFound := false
	for _, theme := range resp.Themes {
		if theme.Name == "Fantasy" {
			fantasyFound = true
			assert.Equal(t, int64(7000), theme.PlayTime)
			assert.Equal(t, 2, theme.GameCount)
		}
	}
	assert.True(t, fantasyFound, "Fantasy theme should be present")

	// Console breakdown
	require.GreaterOrEqual(t, len(resp.TopConsoles), 1)
	// SNES should be first (7000s play time)
	snesFound := false
	for _, con := range resp.TopConsoles {
		if con.Abbreviation == "snes" {
			snesFound = true
			assert.Equal(t, int64(7000), con.PlayTime)
			assert.Equal(t, 2, con.GameCount)
			assert.Equal(t, "Super Nintendo", con.Name)
		}
	}
	assert.True(t, snesFound, "SNES console should be in top consoles")
}

func TestGetTasteProfile_EmptyHistory(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/taste-profile", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp TasteProfileResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, int64(0), resp.TotalPlayTime)
	assert.Empty(t, resp.Genres)
	assert.Empty(t, resp.Themes)
	assert.Empty(t, resp.TopConsoles)
}

func TestGetTasteProfile_AuthRequired(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/taste-profile", nil)
	// No auth header
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

// --- Players Like You endpoint tests ---

func TestGetPlayersLikeYou(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create games
	game1 := createExploreGame(t, env.database, "NES", "Mario", 90)
	game2 := createExploreGame(t, env.database, "SNES", "Zelda", 95)
	game3 := createExploreGame(t, env.database, "GBA", "Metroid", 88)
	game4 := createExploreGame(t, env.database, "NES", "Castlevania", 85)
	game5 := createExploreGame(t, env.database, "SNES", "Mega Man", 82)

	// Current user (owner) favorites: Mario, Zelda
	var user db.User
	require.NoError(t, env.database.First(&user).Error)
	env.database.Create(&db.Favorite{UserID: user.ID, GameID: game1.ID}) // Mario
	env.database.Create(&db.Favorite{UserID: user.ID, GameID: game2.ID}) // Zelda

	// Create similar user2: favorites Mario, Zelda, Metroid (overlap: 2)
	user2Token := createNonOwnerUser(t, env.router, env.token, "user2", "user2@test.com", "password123")
	_ = user2Token
	var user2 db.User
	require.NoError(t, env.database.Where("username = ?", "user2").First(&user2).Error)
	env.database.Create(&db.Favorite{UserID: user2.ID, GameID: game1.ID}) // Mario
	env.database.Create(&db.Favorite{UserID: user2.ID, GameID: game2.ID}) // Zelda
	env.database.Create(&db.Favorite{UserID: user2.ID, GameID: game3.ID}) // Metroid

	// Create user3: favorites Mario, Castlevania, Mega Man (overlap: 1)
	user3Token := createNonOwnerUser(t, env.router, env.token, "user3", "user3@test.com", "password123")
	_ = user3Token
	var user3 db.User
	require.NoError(t, env.database.Where("username = ?", "user3").First(&user3).Error)
	env.database.Create(&db.Favorite{UserID: user3.ID, GameID: game1.ID}) // Mario
	env.database.Create(&db.Favorite{UserID: user3.ID, GameID: game4.ID}) // Castlevania
	env.database.Create(&db.Favorite{UserID: user3.ID, GameID: game5.ID}) // Mega Man

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/players-like-you", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PlayersLikeYouResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, 2, resp.SimilarUsersCount)
	assert.GreaterOrEqual(t, len(resp.Games), 1, "should recommend at least 1 game")

	// The recommended games should NOT include the user's own favorites (Mario, Zelda)
	titles := make(map[string]bool)
	for _, g := range resp.Games {
		titles[g.Title] = true
	}
	assert.False(t, titles["Mario"], "should not recommend user's own favorite")
	assert.False(t, titles["Zelda"], "should not recommend user's own favorite")

	// Metroid should be recommended (favorited by user2 who has highest overlap)
	assert.True(t, titles["Metroid"], "Metroid should be recommended (from most similar user)")
}

func TestGetPlayersLikeYou_NoSimilarUsers(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create a game and favorite it — but no other users exist with favorites
	game := createExploreGame(t, env.database, "NES", "Lonely Game", 85)
	var user db.User
	require.NoError(t, env.database.First(&user).Error)
	env.database.Create(&db.Favorite{UserID: user.ID, GameID: game.ID})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/players-like-you", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PlayersLikeYouResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, 0, resp.SimilarUsersCount)
	assert.Empty(t, resp.Games)
}

func TestGetPlayersLikeYou_NoFavorites(t *testing.T) {
	env := setupExploreTestEnv(t)

	// User has no favorites at all
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/players-like-you", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PlayersLikeYouResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, 0, resp.SimilarUsersCount)
	assert.Empty(t, resp.Games)
}

func TestGetPlayersLikeYou_AuthRequired(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/players-like-you", nil)
	// No auth header
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

// --- Phase 7: Developer & Publisher Spotlight tests ---

// createExploreGameWithDev creates a game with developer and publisher fields set.
func createExploreGameWithDev(t *testing.T, database *gorm.DB, consoleAbbr, title string, rating float64, developer, publisher string) db.Game {
	t.Helper()
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", consoleAbbr).First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     title,
		FileName:  title + ".rom",
		FilePath:  consoleAbbr + "/" + title + ".rom",
		Rating:    rating,
		Genre:     "Action",
		Developer: developer,
		Publisher: publisher,
	}
	require.NoError(t, database.Create(&game).Error)
	return game
}

func TestGetDevelopers_SortedByGameCount(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create games with different developers
	createExploreGameWithDev(t, env.database, "NES", "Game A", 90, "Nintendo", "Nintendo")
	createExploreGameWithDev(t, env.database, "SNES", "Game B", 80, "Nintendo", "Nintendo")
	createExploreGameWithDev(t, env.database, "NES", "Game C", 85, "Nintendo", "Capcom")
	createExploreGameWithDev(t, env.database, "GBA", "Game D", 70, "Capcom", "Capcom")
	createExploreGameWithDev(t, env.database, "SNES", "Game E", 60, "Square", "Square")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.GreaterOrEqual(t, len(resp.Developers), 3)

	// Nintendo has 3 games, should be first
	assert.Equal(t, "Nintendo", resp.Developers[0].Name)
	assert.Equal(t, 3, resp.Developers[0].GameCount)
	assert.Greater(t, resp.Developers[0].AvgRating, 0.0)
	assert.NotEmpty(t, resp.Developers[0].Consoles)

	// Capcom has 1, Square has 1 — both after Nintendo
	assert.Equal(t, 1, resp.Developers[1].GameCount)
	assert.Equal(t, 1, resp.Developers[2].GameCount)
}

func TestGetDevelopers_EmptyDevelopersExcluded(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create a game with empty developer — should not appear
	createExploreGameWithDev(t, env.database, "NES", "No Dev Game", 90, "", "SomePub")
	createExploreGameWithDev(t, env.database, "NES", "Has Dev Game", 80, "Nintendo", "Nintendo")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Only "Nintendo" should appear, not the empty developer
	for _, d := range resp.Developers {
		assert.NotEmpty(t, d.Name, "developer name should not be empty")
	}
}

func TestGetDeveloperDetail_ReturnsGames(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameWithDev(t, env.database, "NES", "Mega Man 2", 92, "Capcom", "Capcom")
	createExploreGameWithDev(t, env.database, "SNES", "Mega Man X", 90, "Capcom", "Capcom")
	createExploreGameWithDev(t, env.database, "GBA", "Zelda LTTP", 95, "Nintendo", "Nintendo")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/Capcom", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "Capcom", resp.Name)
	assert.Equal(t, 2, resp.GameCount)
	assert.Greater(t, resp.AvgRating, 0.0)
	assert.Len(t, resp.Games, 2)
	assert.NotEmpty(t, resp.Consoles)

	// Games should be sorted by rating DESC
	assert.Equal(t, "Mega Man 2", resp.Games[0].Title)
	assert.Equal(t, "Mega Man X", resp.Games[1].Title)
}

func TestGetDeveloperDetail_CaseInsensitive(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameWithDev(t, env.database, "NES", "Game A", 85, "Capcom", "Capcom")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/capcom", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "Capcom", resp.Name) // canonical casing from game data
	assert.Equal(t, 1, resp.GameCount)
	assert.Len(t, resp.Games, 1)
}

func TestGetDeveloperDetail_URLEncodedName(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameWithDev(t, env.database, "SNES", "Final Fantasy VI", 96, "Square Enix", "Square Enix")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/Square%20Enix", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "Square Enix", resp.Name)
	assert.Equal(t, 1, resp.GameCount)
	assert.Len(t, resp.Games, 1)
	assert.Equal(t, "Final Fantasy VI", resp.Games[0].Title)
}

func TestGetDeveloperDetail_NonExistentDeveloper(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/NonExistentDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "NonExistentDev", resp.Name)
	assert.Equal(t, 0, resp.GameCount)
	assert.Empty(t, resp.Games)
}

func TestGetPublisherDetail_ReturnsGames(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameWithDev(t, env.database, "NES", "Castlevania", 88, "Konami", "Konami")
	createExploreGameWithDev(t, env.database, "SNES", "Contra III", 85, "Konami", "Konami")
	createExploreGameWithDev(t, env.database, "GBA", "Metroid Fusion", 92, "Nintendo R&D1", "Nintendo")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/publishers/Konami", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PublisherDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "Konami", resp.Name)
	assert.Equal(t, 2, resp.GameCount)
	assert.Greater(t, resp.AvgRating, 0.0)
	assert.Len(t, resp.Games, 2)
	assert.NotEmpty(t, resp.Consoles)

	// Games sorted by rating DESC
	assert.Equal(t, "Castlevania", resp.Games[0].Title)
	assert.Equal(t, "Contra III", resp.Games[1].Title)
}

func TestGetPublisherDetail_CaseInsensitive(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameWithDev(t, env.database, "NES", "Game A", 85, "Dev", "Konami")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/publishers/konami", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PublisherDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "Konami", resp.Name) // canonical casing from game data
	assert.Equal(t, 1, resp.GameCount)
	assert.Len(t, resp.Games, 1)
}

func TestGetDeveloperSpotlight_ReturnsSpotlight(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create games for a developer with hero art
	game1 := createExploreGameWithDev(t, env.database, "NES", "Mega Man 2", 92, "Capcom", "Capcom")
	game2 := createExploreGameWithDev(t, env.database, "SNES", "Mega Man X", 90, "Capcom", "Capcom")
	createExploreGameWithDev(t, env.database, "GBA", "Street Fighter", 85, "Capcom", "Capcom")

	// Add hero art to some games
	env.database.Create(&db.GameArtwork{
		GameID:  game1.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/megaman2.png",
	})
	env.database.Create(&db.GameArtwork{
		GameID:  game2.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/megamanx.png",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/spotlight", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperSpotlightResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.NotEmpty(t, resp.Name)
	assert.Greater(t, resp.GameCount, 0)
	assert.Greater(t, resp.AvgRating, 0.0)
	assert.NotEmpty(t, resp.Consoles)
	assert.NotEmpty(t, resp.TopGames)
	assert.NotEmpty(t, resp.HeroURL)
	assert.LessOrEqual(t, len(resp.TopGames), 8)
}

func TestGetDeveloperSpotlight_NoHeroArt(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create games without hero art
	createExploreGameWithDev(t, env.database, "NES", "Game A", 90, "Nintendo", "Nintendo")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/spotlight", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

// --- Phase 8: Console Showcase tests ---

// createExploreGameFull creates a game with developer, publisher, and genre.
func createExploreGameFull(t *testing.T, database *gorm.DB, consoleAbbr, title string, rating float64, developer, publisher, genre string) db.Game {
	t.Helper()
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", consoleAbbr).First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     title,
		FileName:  title + ".rom",
		FilePath:  consoleAbbr + "/" + title + ".rom",
		Rating:    rating,
		Genre:     genre,
		Developer: developer,
		Publisher: publisher,
	}
	require.NoError(t, database.Create(&game).Error)
	return game
}

func TestGetConsoleShowcase_Success(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create games on SNES
	createExploreGameFull(t, env.database, "SNES", "Chrono Trigger", 95, "Square", "Square", "RPG")
	createExploreGameFull(t, env.database, "SNES", "Super Mario World", 92, "Nintendo", "Nintendo", "Platformer")
	createExploreGameFull(t, env.database, "SNES", "Zelda LTTP", 94, "Nintendo", "Nintendo", "Action RPG")

	// Create a game on NES — should NOT appear in SNES showcase
	createExploreGameFull(t, env.database, "NES", "Super Mario Bros", 90, "Nintendo", "Nintendo", "Platformer")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/consoles/snes/showcase", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ConsoleShowcaseResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Console info
	assert.Equal(t, "snes", resp.Console.ID)
	assert.Equal(t, "Super Nintendo", resp.Console.Name)
	assert.Equal(t, 3, resp.Console.GameCount)

	// Essentials should be sorted by rating DESC and only include SNES games
	require.Len(t, resp.Essentials, 3)
	assert.Equal(t, "Chrono Trigger", resp.Essentials[0].Title)
	assert.Equal(t, "Zelda LTTP", resp.Essentials[1].Title)
	assert.Equal(t, "Super Mario World", resp.Essentials[2].Title)

	// NES game should not be in essentials
	for _, g := range resp.Essentials {
		assert.Equal(t, "snes", g.ConsoleID)
	}

	// Genre breakdown should have 3 genres
	assert.Len(t, resp.GenreBreakdown, 3)

	// Top developers — Nintendo has 2 games, Square has 1
	require.NotEmpty(t, resp.TopDevelopers)
	assert.Equal(t, "Nintendo", resp.TopDevelopers[0].Name)
	assert.Equal(t, 2, resp.TopDevelopers[0].GameCount)
}

func TestGetConsoleShowcase_GenreBreakdown(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create games with various genres on SNES
	createExploreGameWithGenre(t, env.database, "SNES", "Game A", 90, "RPG", 1)
	createExploreGameWithGenre(t, env.database, "SNES", "Game B", 85, "RPG", 1)
	createExploreGameWithGenre(t, env.database, "SNES", "Game C", 80, "RPG", 1)
	createExploreGameWithGenre(t, env.database, "SNES", "Game D", 75, "Platformer", 1)
	createExploreGameWithGenre(t, env.database, "SNES", "Game E", 70, "Platformer", 1)
	createExploreGameWithGenre(t, env.database, "SNES", "Game F", 65, "Action", 1)

	// NES game should not be counted
	createExploreGameWithGenre(t, env.database, "NES", "NES Game", 90, "RPG", 1)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/consoles/snes/showcase", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ConsoleShowcaseResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Genre breakdown sorted by count desc
	require.Len(t, resp.GenreBreakdown, 3)
	assert.Equal(t, "RPG", resp.GenreBreakdown[0].Name)
	assert.Equal(t, 3, resp.GenreBreakdown[0].GameCount)
	assert.Equal(t, "Platformer", resp.GenreBreakdown[1].Name)
	assert.Equal(t, 2, resp.GenreBreakdown[1].GameCount)
	assert.Equal(t, "Action", resp.GenreBreakdown[2].Name)
	assert.Equal(t, 1, resp.GenreBreakdown[2].GameCount)
}

func TestGetConsoleShowcase_TopDevelopers(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create games with specific developers on SNES
	createExploreGameWithDev(t, env.database, "SNES", "FF6", 96, "Square", "Square")
	createExploreGameWithDev(t, env.database, "SNES", "Chrono Trigger", 95, "Square", "Square")
	createExploreGameWithDev(t, env.database, "SNES", "Secret of Mana", 88, "Square", "Square")
	createExploreGameWithDev(t, env.database, "SNES", "Super Mario World", 92, "Nintendo", "Nintendo")
	createExploreGameWithDev(t, env.database, "SNES", "Donkey Kong Country", 87, "Rare", "Nintendo")
	createExploreGameWithDev(t, env.database, "SNES", "Mega Man X", 90, "Capcom", "Capcom")
	createExploreGameWithDev(t, env.database, "SNES", "Street Fighter 2", 85, "Capcom", "Capcom")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/consoles/snes/showcase", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ConsoleShowcaseResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Top developers sorted by game count: Square(3), Capcom(2), Nintendo(1), Rare(1)
	require.GreaterOrEqual(t, len(resp.TopDevelopers), 4)
	assert.Equal(t, "Square", resp.TopDevelopers[0].Name)
	assert.Equal(t, 3, resp.TopDevelopers[0].GameCount)
	assert.Equal(t, "Capcom", resp.TopDevelopers[1].Name)
	assert.Equal(t, 2, resp.TopDevelopers[1].GameCount)

	// All developers should list SNES as their console
	for _, dev := range resp.TopDevelopers {
		assert.Contains(t, dev.Consoles, "Super Nintendo")
		assert.Greater(t, dev.AvgRating, 0.0)
	}

	// Limit to 5 developers max
	assert.LessOrEqual(t, len(resp.TopDevelopers), 5)
}

func TestGetConsoleShowcase_NotFound(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/consoles/nonexistent/showcase", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetConsoleShowcase_RecentlyPlayed(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "SNES", "Game A", 90)
	game2 := createExploreGame(t, env.database, "SNES", "Game B", 85)
	createExploreGame(t, env.database, "NES", "NES Game", 80) // Different console

	// Get user ID
	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Add play history for SNES games
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     game1.ID,
		LastPlayed: time.Now().Add(-1 * time.Hour),
		PlayTime:   3600,
	})
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     game2.ID,
		LastPlayed: time.Now(),
		PlayTime:   1800,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/consoles/snes/showcase", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ConsoleShowcaseResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Recently played should have 2 SNES games, ordered by last_played DESC
	require.Len(t, resp.RecentlyPlayed, 2)
	assert.Equal(t, "Game B", resp.RecentlyPlayed[0].Title) // played more recently
	assert.Equal(t, "Game A", resp.RecentlyPlayed[1].Title)
}

func TestGetConsoleShowcase_CaseInsensitive(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGame(t, env.database, "SNES", "Test Game", 85)

	// Use uppercase in URL — should still work
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/consoles/SNES/showcase", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ConsoleShowcaseResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "snes", resp.Console.ID)
}

func TestGetConsoleShowcase_RecentlyAdded(t *testing.T) {
	env := setupExploreTestEnv(t)

	now := time.Now()

	// Create 12 games on SNES with different created_at times
	for i := 0; i < 12; i++ {
		createExploreGameWithTime(t, env.database, "SNES",
			fmt.Sprintf("SNES Game %02d", i), 70,
			now.Add(time.Duration(-i)*time.Hour))
	}

	// Create a game on NES — should NOT appear in SNES showcase
	createExploreGameWithTime(t, env.database, "NES", "NES Game", 90, now)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/consoles/snes/showcase", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ConsoleShowcaseResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// At most 10 games returned
	require.Len(t, resp.RecentlyAdded, 10)

	// Games are ordered by created_at DESC (most recent first)
	assert.Equal(t, "SNES Game 00", resp.RecentlyAdded[0].Title)
	assert.Equal(t, "SNES Game 01", resp.RecentlyAdded[1].Title)
	assert.Equal(t, "SNES Game 09", resp.RecentlyAdded[9].Title)

	// NES game should not be in recently added
	for _, g := range resp.RecentlyAdded {
		assert.Equal(t, "snes", g.ConsoleID)
	}
}

func TestGetConsoleHighlights(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create games across consoles
	game1 := createExploreGame(t, env.database, "SNES", "SNES Best Game", 95)
	createExploreGame(t, env.database, "NES", "NES Game", 88)
	createExploreGame(t, env.database, "GBA", "GBA Game", 82)

	// Add hero art for the SNES game (top game requires hero art)
	env.database.Create(&db.GameArtwork{
		GameID:  game1.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/snes_best.png",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/console-highlights", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ConsoleHighlightsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Should have entries for consoles that have games
	require.GreaterOrEqual(t, len(resp.Consoles), 3)

	// Find the SNES entry
	var snesEntry *ConsoleHighlight
	for i := range resp.Consoles {
		if resp.Consoles[i].ID == "snes" {
			snesEntry = &resp.Consoles[i]
			break
		}
	}
	require.NotNil(t, snesEntry)
	assert.Equal(t, "Super Nintendo", snesEntry.Name)
	assert.Equal(t, 1, snesEntry.GameCount)
	assert.NotEmpty(t, snesEntry.ColorTheme)
	assert.NotEmpty(t, snesEntry.IconURL)
	assert.NotEmpty(t, snesEntry.LogoURL)

	// SNES should have a top game (it has hero art)
	require.NotNil(t, snesEntry.TopGame)
	assert.Equal(t, "SNES Best Game", snesEntry.TopGame.Title)
}

func TestGetConsoleHighlights_NoGames(t *testing.T) {
	env := setupExploreTestEnv(t)

	// No games at all — should return empty consoles list (all skipped)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/console-highlights", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ConsoleHighlightsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Empty(t, resp.Consoles)
}

func TestGetConsoleHighlights_TopGameRequiresHeroArt(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create a game without hero art
	createExploreGame(t, env.database, "NES", "No Art Game", 95)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/console-highlights", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ConsoleHighlightsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// NES should appear but without a top game (no hero art)
	var nesEntry *ConsoleHighlight
	for i := range resp.Consoles {
		if resp.Consoles[i].ID == "nes" {
			nesEntry = &resp.Consoles[i]
			break
		}
	}
	require.NotNil(t, nesEntry)
	assert.Equal(t, 1, nesEntry.GameCount)
	assert.Nil(t, nesEntry.TopGame) // No hero art = no top game
}

// --- Phase 9: Visual Browsing — Gallery & Art Modes ---

func TestGetScreenshotGallery_Success(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "SNES", "Chrono Trigger", 95.0)
	game2 := createExploreGame(t, env.database, "NES", "Super Mario Bros", 90.0)

	// Create screenshots
	env.database.Create(&db.GameScreenshot{GameID: game1.ID, URL: "snes/chrono/screenshot_0.jpg", Position: 0})
	env.database.Create(&db.GameScreenshot{GameID: game1.ID, URL: "snes/chrono/screenshot_1.jpg", Position: 1})
	env.database.Create(&db.GameScreenshot{GameID: game2.ID, URL: "nes/smb/screenshot_0.jpg", Position: 0})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/screenshots", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ScreenshotGalleryResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 3, resp.TotalCount)
	assert.Equal(t, 1, resp.Page)
	assert.Equal(t, 1, resp.TotalPages)
	assert.Len(t, resp.Screenshots, 3)

	// Verify screenshot fields are populated
	for _, s := range resp.Screenshots {
		assert.NotEmpty(t, s.URL)
		assert.NotEmpty(t, s.GameID)
		assert.NotEmpty(t, s.GameTitle)
		assert.NotEmpty(t, s.ConsoleName)
		assert.NotEmpty(t, s.ConsoleAbbr)
		assert.NotEmpty(t, s.ConsoleColor)
	}
}

func TestGetScreenshotGallery_ConsoleFilter(t *testing.T) {
	env := setupExploreTestEnv(t)

	snesGame := createExploreGame(t, env.database, "SNES", "Chrono Trigger", 95.0)
	nesGame := createExploreGame(t, env.database, "NES", "Super Mario Bros", 90.0)

	env.database.Create(&db.GameScreenshot{GameID: snesGame.ID, URL: "snes/chrono/screenshot_0.jpg", Position: 0})
	env.database.Create(&db.GameScreenshot{GameID: nesGame.ID, URL: "nes/smb/screenshot_0.jpg", Position: 0})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/screenshots?console=snes", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ScreenshotGalleryResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 1, resp.TotalCount)
	require.Len(t, resp.Screenshots, 1)
	assert.Equal(t, "snes", resp.Screenshots[0].ConsoleAbbr)
	assert.Equal(t, "Chrono Trigger", resp.Screenshots[0].GameTitle)
}

func TestGetScreenshotGallery_Empty(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/screenshots", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ScreenshotGalleryResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 0, resp.TotalCount)
	assert.Equal(t, 1, resp.Page)
	assert.Equal(t, 0, resp.TotalPages)
	assert.Empty(t, resp.Screenshots)
}

func TestGetArtworkGallery_Success(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "SNES", "Chrono Trigger", 95.0)
	game2 := createExploreGame(t, env.database, "NES", "Super Mario Bros", 85.0)

	// Create IGDB artwork images
	env.database.Create(&db.GameArtworkImage{GameID: game1.ID, IGDBImageID: "abc123", LocalPath: "snes/1/artwork_abc123.jpg", Width: 1920, Height: 1080})
	env.database.Create(&db.GameArtworkImage{GameID: game2.ID, IGDBImageID: "def456", LocalPath: "nes/2/artwork_def456.jpg", Width: 1280, Height: 720})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/artwork", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp ArtworkGalleryResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 2, resp.TotalCount)
	assert.Equal(t, 1, resp.Page)
	assert.Equal(t, 1, resp.TotalPages)
	require.Len(t, resp.Artworks, 2)

	// Ordered by rating DESC, so Chrono Trigger (95) first
	assert.Equal(t, "Chrono Trigger", resp.Artworks[0].GameTitle)
	assert.Equal(t, 1920, resp.Artworks[0].Width)
	assert.Equal(t, 1080, resp.Artworks[0].Height)
	// Verify local image path
	assert.Contains(t, resp.Artworks[0].URL, "/api/images/")
	assert.Contains(t, resp.Artworks[0].URL, "artwork_abc123")

	assert.Equal(t, "Super Mario Bros", resp.Artworks[1].GameTitle)
	assert.NotEmpty(t, resp.Artworks[1].ConsoleAbbr)
	assert.NotEmpty(t, resp.Artworks[1].ConsoleColor)
}

func TestGetCoverGallery_Success(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "SNES", "Chrono Trigger", 95.0)
	game2 := createExploreGame(t, env.database, "NES", "Super Mario Bros", 85.0)
	// game3 has no cover — should not appear
	createExploreGame(t, env.database, "GBA", "No Cover Game", 80.0)

	// Set cover URLs
	env.database.Model(&game1).Update("cover_url", "snes/chrono/cover.jpg")
	env.database.Model(&game2).Update("cover_url", "nes/smb/cover.jpg")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/covers", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp CoverGalleryResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 2, resp.TotalCount)
	assert.Equal(t, 1, resp.Page)
	require.Len(t, resp.Covers, 2)

	// Ordered by rating DESC
	assert.Equal(t, "Chrono Trigger", resp.Covers[0].GameTitle)
	assert.Equal(t, 95.0, resp.Covers[0].Rating)
	assert.Contains(t, resp.Covers[0].CoverURL, "snes/chrono/cover.jpg")
	assert.Greater(t, resp.Covers[0].CoverAspectRatio, 0.0)

	assert.Equal(t, "Super Mario Bros", resp.Covers[1].GameTitle)
	assert.Equal(t, 85.0, resp.Covers[1].Rating)
}

func TestGetCoverGallery_ConsoleFilter(t *testing.T) {
	env := setupExploreTestEnv(t)

	snesGame := createExploreGame(t, env.database, "SNES", "Chrono Trigger", 95.0)
	nesGame := createExploreGame(t, env.database, "NES", "Super Mario Bros", 85.0)

	env.database.Model(&snesGame).Update("cover_url", "snes/chrono/cover.jpg")
	env.database.Model(&nesGame).Update("cover_url", "nes/smb/cover.jpg")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/covers?console=NES", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp CoverGalleryResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 1, resp.TotalCount)
	require.Len(t, resp.Covers, 1)
	assert.Equal(t, "Super Mario Bros", resp.Covers[0].GameTitle)
	assert.Equal(t, "nes", resp.Covers[0].ConsoleAbbr)
}

func TestGetCoverGallery_Pagination(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create enough games to require multiple pages (using limit=2 for easy testing)
	for i := 0; i < 5; i++ {
		g := createExploreGame(t, env.database, "SNES", fmt.Sprintf("Game %d", i), float64(90-i))
		env.database.Model(&g).Update("cover_url", fmt.Sprintf("snes/game%d/cover.jpg", i))
	}

	// Page 1 with limit=2
	w1 := httptest.NewRecorder()
	req1 := httptest.NewRequest("GET", "/api/explore/covers?page=1&limit=2", nil)
	req1.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w1, req1)

	assert.Equal(t, http.StatusOK, w1.Code)

	var resp1 CoverGalleryResponse
	require.NoError(t, json.Unmarshal(w1.Body.Bytes(), &resp1))
	assert.Equal(t, 5, resp1.TotalCount)
	assert.Equal(t, 3, resp1.TotalPages)
	assert.Equal(t, 1, resp1.Page)
	assert.Len(t, resp1.Covers, 2)

	// Page 2 with limit=2
	w2 := httptest.NewRecorder()
	req2 := httptest.NewRequest("GET", "/api/explore/covers?page=2&limit=2", nil)
	req2.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w2, req2)

	assert.Equal(t, http.StatusOK, w2.Code)

	var resp2 CoverGalleryResponse
	require.NoError(t, json.Unmarshal(w2.Body.Bytes(), &resp2))
	assert.Equal(t, 2, resp2.Page)
	assert.Len(t, resp2.Covers, 2)

	// Page 2 should have different games than page 1
	assert.NotEqual(t, resp1.Covers[0].GameID, resp2.Covers[0].GameID)
}

// --- Phase 10: Social & Community Discovery tests ---

func TestGetTrending_Empty(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/trending", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp TrendingResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Games)
}

func TestGetTrending_ReturnsGamesByPlayerCount(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "SNES", "Trending Game 1", 85.0)
	game2 := createExploreGame(t, env.database, "NES", "Trending Game 2", 75.0)

	var user1 db.User
	require.NoError(t, env.database.First(&user1).Error)

	user2Token := createNonOwnerUser(t, env.router, env.token, "trending_player", "trending@example.com", "password123")
	_ = user2Token
	var user2 db.User
	require.NoError(t, env.database.Where("username = ?", "trending_player").First(&user2).Error)

	// game1: played by 2 users this week
	env.database.Create(&db.PlayHistory{UserID: user1.ID, GameID: game1.ID, PlayTime: 100, LastPlayed: time.Now()})
	env.database.Create(&db.PlayHistory{UserID: user2.ID, GameID: game1.ID, PlayTime: 200, LastPlayed: time.Now()})
	// game2: played by 1 user this week
	env.database.Create(&db.PlayHistory{UserID: user1.ID, GameID: game2.ID, PlayTime: 300, LastPlayed: time.Now()})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/trending", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp TrendingResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Games, 2)
	// game1 (2 players) should come first
	assert.Equal(t, "Trending Game 1", resp.Games[0].Game.Title)
	assert.Equal(t, 2, resp.Games[0].PlayersThisWeek)
	assert.Equal(t, "Trending Game 2", resp.Games[1].Game.Title)
	assert.Equal(t, 1, resp.Games[1].PlayersThisWeek)
}

func TestGetTrending_ExcludesOldActivity(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Old Game", 90.0)
	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Play history from 10 days ago — should NOT appear in trending
	env.database.Create(&db.PlayHistory{UserID: user.ID, GameID: game.ID, PlayTime: 500, LastPlayed: time.Now().AddDate(0, 0, -10)})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/trending", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp TrendingResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Games)
}

func TestGetCommunityTop_Empty(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/community-top", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp CommunityTopResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Games)
}

func TestGetCommunityTop_RequiresMinRatings(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "One Rating Game", 80.0)
	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Only 1 rating — should not appear (minimum is 2)
	env.database.Create(&db.GameRating{UserID: user.ID, GameID: game.ID, Rating: 5})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/community-top", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp CommunityTopResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Games)
}

func TestGetCommunityTop_ReturnsRankedGames(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "SNES", "Community Best", 60.0)
	game2 := createExploreGame(t, env.database, "NES", "Community Good", 90.0)

	var user1 db.User
	require.NoError(t, env.database.First(&user1).Error)
	user2Token := createNonOwnerUser(t, env.router, env.token, "rater2", "rater2@example.com", "password123")
	_ = user2Token
	var user2 db.User
	require.NoError(t, env.database.Where("username = ?", "rater2").First(&user2).Error)

	// game1: avg 5.0
	env.database.Create(&db.GameRating{UserID: user1.ID, GameID: game1.ID, Rating: 5})
	env.database.Create(&db.GameRating{UserID: user2.ID, GameID: game1.ID, Rating: 5})
	// game2: avg 3.5
	env.database.Create(&db.GameRating{UserID: user1.ID, GameID: game2.ID, Rating: 4})
	env.database.Create(&db.GameRating{UserID: user2.ID, GameID: game2.ID, Rating: 3})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/community-top", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp CommunityTopResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Games, 2)
	assert.Equal(t, "Community Best", resp.Games[0].Game.Title)
	assert.Equal(t, 5.0, resp.Games[0].AvgRating)
	assert.Equal(t, 2, resp.Games[0].RatingCount)
}

func TestGetCultClassics_ReturnsHighCommunityLowIGDB(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Cult classic: high community rating but low IGDB
	cult := createExploreGame(t, env.database, "SNES", "Cult Classic", 50.0)
	// Not a cult classic: high IGDB rating too
	nonCult := createExploreGame(t, env.database, "NES", "Mainstream Hit", 90.0)

	var user1 db.User
	require.NoError(t, env.database.First(&user1).Error)
	user2Token := createNonOwnerUser(t, env.router, env.token, "cult_rater", "cult@example.com", "password123")
	_ = user2Token
	var user2 db.User
	require.NoError(t, env.database.Where("username = ?", "cult_rater").First(&user2).Error)

	// Both get high community ratings
	env.database.Create(&db.GameRating{UserID: user1.ID, GameID: cult.ID, Rating: 5})
	env.database.Create(&db.GameRating{UserID: user2.ID, GameID: cult.ID, Rating: 4})
	env.database.Create(&db.GameRating{UserID: user1.ID, GameID: nonCult.ID, Rating: 5})
	env.database.Create(&db.GameRating{UserID: user2.ID, GameID: nonCult.ID, Rating: 5})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/cult-classics", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp CultClassicsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	// Only the cult classic (IGDB < 75) should appear
	require.Len(t, resp.Games, 1)
	assert.Equal(t, "Cult Classic", resp.Games[0].Game.Title)
	assert.GreaterOrEqual(t, resp.Games[0].CommunityRating, 4.0)
	assert.Less(t, resp.Games[0].IgdbRating, 75.0)
}

func TestGetRecentlyReviewed_Empty(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/recently-reviewed", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp RecentlyReviewedResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Reviews)
}

func TestGetRecentlyReviewed_ReturnsReviewsWithText(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Reviewed Game", 85.0)
	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Rating with review text — should appear
	env.database.Create(&db.GameRating{UserID: user.ID, GameID: game.ID, Rating: 4, Review: "Great classic RPG!"})
	// Rating without review text — should NOT appear
	game2 := createExploreGame(t, env.database, "NES", "No Review Game", 70.0)
	user2Token := createNonOwnerUser(t, env.router, env.token, "reviewer2", "reviewer2@example.com", "password123")
	_ = user2Token
	var user2 db.User
	require.NoError(t, env.database.Where("username = ?", "reviewer2").First(&user2).Error)
	env.database.Create(&db.GameRating{UserID: user2.ID, GameID: game2.ID, Rating: 3})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/recently-reviewed", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp RecentlyReviewedResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Reviews, 1)
	assert.Equal(t, "Reviewed Game", resp.Reviews[0].Game.Title)
	assert.Equal(t, 4, resp.Reviews[0].Rating)
	assert.Equal(t, "Great classic RPG!", resp.Reviews[0].Review)
}

func TestGetActiveNow_Empty(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/active-now", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp ActiveNowResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Games)
}

func TestGetActiveNow_ReturnsMergedSessions(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Active Game", 88.0)
	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Create active shared session
	env.database.Create(&db.SharedSession{
		OwnerID: user.ID,
		GameID:  game.ID,
		Name:    "Test Session",
		Status:  "active",
	})
	// Create active challenge
	env.database.Create(&db.Challenge{
		CreatorID:    user.ID,
		GameID:       game.ID,
		Name:         "Speed Run",
		Status:       "active",
		SaveFilePath: "/tmp/test.sav",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/active-now", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp ActiveNowResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Games, 1)
	assert.Equal(t, "Active Game", resp.Games[0].Game.Title)
	assert.Equal(t, 1, resp.Games[0].ActiveSessions)
	assert.Equal(t, 1, resp.Games[0].ActiveChallenges)
}

// --- Phase 11: Temporal Discovery tests ---

// createExploreGameWithRelease creates a game with a specific release date and rating.
func createExploreGameWithRelease(t *testing.T, database *gorm.DB, consoleAbbr, title string, rating float64, releaseDate string) db.Game {
	t.Helper()
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", consoleAbbr).First(&console).Error)
	game := db.Game{
		ConsoleID:   console.ID,
		Title:       title,
		FileName:    title + ".rom",
		FilePath:    consoleAbbr + "/" + title + ".rom",
		Rating:      rating,
		Genre:       "Action",
		ReleaseDate: releaseDate,
	}
	require.NoError(t, database.Create(&game).Error)
	return game
}

// --- On This Day tests ---

func TestGetOnThisDay_NoGames(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/on-this-day", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp OnThisDayResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.NotEmpty(t, resp.Date) // e.g. "March 10"
	assert.Empty(t, resp.Games)
}

func TestGetOnThisDay_MatchesToday(t *testing.T) {
	env := setupExploreTestEnv(t)

	now := time.Now()
	todayISO := now.Format("2006-01-02")
	otherDate := fmt.Sprintf("1995-%02d-%02d", (now.Month()%12)+1, 15) // Different month

	// Game matching today's date
	createExploreGameWithRelease(t, env.database, "SNES", "Birthday Game", 90.0, todayISO)
	// Game on a different date — should NOT appear
	createExploreGameWithRelease(t, env.database, "NES", "Other Game", 85.0, otherDate)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/on-this-day", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp OnThisDayResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Games, 1)
	assert.Equal(t, "Birthday Game", resp.Games[0].Title)
}

func TestGetOnThisDay_MultipleYearsSortedOldestFirst(t *testing.T) {
	env := setupExploreTestEnv(t)

	now := time.Now()
	todayMD := now.Format("01-02") // MM-DD

	// Games released on today's date in different years
	createExploreGameWithRelease(t, env.database, "SNES", "Newer Game", 80.0, "2005-"+todayMD)
	createExploreGameWithRelease(t, env.database, "NES", "Older Game", 70.0, "1990-"+todayMD)
	createExploreGameWithRelease(t, env.database, "GBA", "Mid Game", 75.0, "1998-"+todayMD)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/on-this-day", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp OnThisDayResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Games, 3)
	// Sorted oldest first
	assert.Equal(t, "Older Game", resp.Games[0].Title)
	assert.Equal(t, "Mid Game", resp.Games[1].Title)
	assert.Equal(t, "Newer Game", resp.Games[2].Title)
}

func TestGetOnThisDay_TextDateFormat(t *testing.T) {
	env := setupExploreTestEnv(t)

	now := time.Now()
	// Use "January 2, 2006" text format for today's date
	textDate := now.Format("January 2, 2006")
	// Use ISO format for a different day
	otherDate := fmt.Sprintf("2000-%02d-%02d", (now.Month()%12)+1, 15)

	createExploreGameWithRelease(t, env.database, "SNES", "Text Date Game", 88.0, textDate)
	createExploreGameWithRelease(t, env.database, "NES", "Wrong Date Game", 85.0, otherDate)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/on-this-day", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp OnThisDayResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Games, 1)
	assert.Equal(t, "Text Date Game", resp.Games[0].Title)
}

// --- Best of Year tests ---

func TestGetBestOfYear_InvalidYear(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/best-of-year/abc", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestGetBestOfYear_NoGames(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/best-of-year/1995", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp BestOfYearResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 1995, resp.Year)
	assert.Empty(t, resp.Games)
}

func TestGetBestOfYear_ReturnsSortedByRating(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameWithRelease(t, env.database, "SNES", "Top Game 1995", 95.0, "1995-06-15")
	createExploreGameWithRelease(t, env.database, "NES", "Mid Game 1995", 80.0, "1995-03-01")
	createExploreGameWithRelease(t, env.database, "GBA", "Low Game 1995", 70.0, "1995-12-25")
	// Different year — should NOT appear
	createExploreGameWithRelease(t, env.database, "SNES", "Game 1996", 99.0, "1996-01-01")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/best-of-year/1995", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp BestOfYearResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 1995, resp.Year)
	require.Len(t, resp.Games, 3)
	assert.Equal(t, "Top Game 1995", resp.Games[0].Title)
	assert.Equal(t, "Mid Game 1995", resp.Games[1].Title)
	assert.Equal(t, "Low Game 1995", resp.Games[2].Title)
}

func TestGetBestOfYear_ExcludesZeroRating(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameWithRelease(t, env.database, "SNES", "Rated Game", 85.0, "2000-05-10")
	createExploreGameWithRelease(t, env.database, "NES", "Unrated Game", 0.0, "2000-08-20")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/best-of-year/2000", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp BestOfYearResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Games, 1)
	assert.Equal(t, "Rated Game", resp.Games[0].Title)
}

// --- Your Anniversaries tests ---

func TestGetYourAnniversaries_NoHistory(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/your-anniversaries", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp AnniversariesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Anniversaries)
}

func TestGetYourAnniversaries_FindsAnniversary(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Anniversary Game", 90.0)
	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Create play history exactly 1 year ago
	oneYearAgo := time.Now().AddDate(-1, 0, 0)
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     game.ID,
		LastPlayed: oneYearAgo,
		PlayTime:   3600,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/your-anniversaries", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp AnniversariesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Anniversaries, 1)
	assert.Equal(t, "Anniversary Game", resp.Anniversaries[0].Game.Title)
	assert.Equal(t, 1, resp.Anniversaries[0].YearsAgo)
}

func TestGetYourAnniversaries_WithinWindow(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Window Game", 85.0)
	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Create play history 2 years ago + 2 days (within 3-day window)
	twoYearsAgoPlus2 := time.Now().AddDate(-2, 0, 2)
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     game.ID,
		LastPlayed: twoYearsAgoPlus2,
		PlayTime:   1800,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/your-anniversaries", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp AnniversariesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Anniversaries, 1)
	assert.Equal(t, "Window Game", resp.Anniversaries[0].Game.Title)
	assert.Equal(t, 2, resp.Anniversaries[0].YearsAgo)
}

func TestGetYourAnniversaries_OutsideWindow(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Far Game", 85.0)
	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Create play history 1 year ago + 5 days (outside 3-day window)
	oneYearAgoPlus5 := time.Now().AddDate(-1, 0, 5)
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     game.ID,
		LastPlayed: oneYearAgoPlus5,
		PlayTime:   1800,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/your-anniversaries", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp AnniversariesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Anniversaries)
}

func TestGetYourAnniversaries_MultipleYears(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "SNES", "One Year Game", 90.0)
	game2 := createExploreGame(t, env.database, "NES", "Three Year Game", 85.0)
	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     game1.ID,
		LastPlayed: time.Now().AddDate(-1, 0, 0),
		PlayTime:   3600,
	})
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     game2.ID,
		LastPlayed: time.Now().AddDate(-3, 0, 1),
		PlayTime:   7200,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/your-anniversaries", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp AnniversariesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Anniversaries, 2)
	// Sorted by yearsAgo ascending
	assert.Equal(t, 1, resp.Anniversaries[0].YearsAgo)
	assert.Equal(t, "One Year Game", resp.Anniversaries[0].Game.Title)
	assert.Equal(t, 3, resp.Anniversaries[1].YearsAgo)
	assert.Equal(t, "Three Year Game", resp.Anniversaries[1].Game.Title)
}

// --- Decades tests ---

func TestGetDecades_InvalidDecade(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/decades/70s", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestGetDecades_NoGames(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/decades/80s", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp DecadesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "80s", resp.Decade)
	assert.Equal(t, "The 80s", resp.Label)
	assert.Empty(t, resp.Games)
}

func TestGetDecades_Returns90sGames(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameWithRelease(t, env.database, "SNES", "90s Best", 95.0, "1995-06-15")
	createExploreGameWithRelease(t, env.database, "NES", "90s Good", 80.0, "1990-01-01")
	createExploreGameWithRelease(t, env.database, "GBA", "90s End", 85.0, "1999-12-31")
	// 80s game — should NOT appear
	createExploreGameWithRelease(t, env.database, "NES", "80s Game", 99.0, "1989-05-01")
	// 00s game — should NOT appear
	createExploreGameWithRelease(t, env.database, "GBA", "00s Game", 99.0, "2000-01-01")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/decades/90s", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp DecadesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "90s", resp.Decade)
	assert.Equal(t, "The 90s", resp.Label)
	require.Len(t, resp.Games, 3)
	// Sorted by rating DESC
	assert.Equal(t, "90s Best", resp.Games[0].Title)
	assert.Equal(t, "90s End", resp.Games[1].Title)
	assert.Equal(t, "90s Good", resp.Games[2].Title)
}

func TestGetDecades_00s(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameWithRelease(t, env.database, "GBA", "GBA Classic", 88.0, "2004-11-15")
	createExploreGameWithRelease(t, env.database, "GBA", "GBA Gem", 82.0, "2009-06-01")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/decades/00s", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp DecadesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "00s", resp.Decade)
	assert.Equal(t, "The 00s", resp.Label)
	require.Len(t, resp.Games, 2)
	assert.Equal(t, "GBA Classic", resp.Games[0].Title)
	assert.Equal(t, "GBA Gem", resp.Games[1].Title)
}

func TestGetDecades_ExcludesZeroRating(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameWithRelease(t, env.database, "SNES", "Rated 90s", 85.0, "1995-06-15")
	createExploreGameWithRelease(t, env.database, "NES", "Unrated 90s", 0.0, "1993-03-01")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/decades/90s", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp DecadesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Games, 1)
	assert.Equal(t, "Rated 90s", resp.Games[0].Title)
}

// --- parseReleaseDateMonthDay unit tests ---

func TestParseReleaseDateMonthDay_ISOFormat(t *testing.T) {
	month, day, ok := parseReleaseDateMonthDay("2000-03-10")
	assert.True(t, ok)
	assert.Equal(t, time.March, month)
	assert.Equal(t, 10, day)
}

func TestParseReleaseDateMonthDay_TextFormat(t *testing.T) {
	month, day, ok := parseReleaseDateMonthDay("March 10, 2000")
	assert.True(t, ok)
	assert.Equal(t, time.March, month)
	assert.Equal(t, 10, day)
}

func TestParseReleaseDateMonthDay_ShortTextFormat(t *testing.T) {
	month, day, ok := parseReleaseDateMonthDay("Jan 5, 1999")
	assert.True(t, ok)
	assert.Equal(t, time.January, month)
	assert.Equal(t, 5, day)
}

func TestParseReleaseDateMonthDay_YearOnly(t *testing.T) {
	_, _, ok := parseReleaseDateMonthDay("1996")
	assert.False(t, ok)
}

func TestParseReleaseDateMonthDay_Empty(t *testing.T) {
	_, _, ok := parseReleaseDateMonthDay("")
	assert.False(t, ok)
}

func TestParseReleaseDateYear_ISOFormat(t *testing.T) {
	year, ok := parseReleaseDateYear("1995-06-15")
	assert.True(t, ok)
	assert.Equal(t, 1995, year)
}

func TestParseReleaseDateYear_TextFormat(t *testing.T) {
	year, ok := parseReleaseDateYear("March 10, 2000")
	assert.True(t, ok)
	assert.Equal(t, 2000, year)
}

func TestParseReleaseDateYear_PlainYear(t *testing.T) {
	year, ok := parseReleaseDateYear("1996")
	assert.True(t, ok)
	assert.Equal(t, 1996, year)
}

func TestParseReleaseDateYear_Empty(t *testing.T) {
	_, ok := parseReleaseDateYear("")
	assert.False(t, ok)
}

// --- Phase 12: Achievement & Challenge-Driven Discovery tests ---

func TestGetEasyToComplete_WithData(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "SNES", "Easy Game", 90.0)
	game2 := createExploreGame(t, env.database, "NES", "Medium Game", 80.0)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Easy Game: 10 achievements, user unlocked 9 (90%)
	env.database.Create(&db.GameAchievementCache{
		RAGameID:    100,
		GameID:      game1.ID,
		TotalCount:  10,
		TotalPoints: 100,
		CachedAt:    time.Now(),
	})
	for i := 1; i <= 9; i++ {
		env.database.Create(&db.UserAchievementProgress{
			UserID:          user.ID,
			AchievementRAID: uint(i),
			RAGameID:        100,
			UnlockedAt:      time.Now(),
		})
	}

	// Medium Game: 20 achievements, user unlocked 10 (50%)
	env.database.Create(&db.GameAchievementCache{
		RAGameID:    200,
		GameID:      game2.ID,
		TotalCount:  20,
		TotalPoints: 200,
		CachedAt:    time.Now(),
	})
	for i := 1; i <= 10; i++ {
		env.database.Create(&db.UserAchievementProgress{
			UserID:          user.ID,
			AchievementRAID: uint(100 + i),
			RAGameID:        200,
			UnlockedAt:      time.Now(),
		})
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/easy-to-complete", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp EasyToCompleteResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Games, 2)

	// Easy Game (90%) should be first
	assert.Equal(t, "Easy Game", resp.Games[0].Game.Title)
	assert.Equal(t, 10, resp.Games[0].TotalAchievements)
	assert.Equal(t, 90.0, resp.Games[0].AvgCompletion)
	assert.Equal(t, 1, resp.Games[0].PlayersAttempted)

	// Medium Game (50%) should be second
	assert.Equal(t, "Medium Game", resp.Games[1].Game.Title)
	assert.Equal(t, 20, resp.Games[1].TotalAchievements)
	assert.Equal(t, 50.0, resp.Games[1].AvgCompletion)
}

func TestGetEasyToComplete_Empty(t *testing.T) {
	env := setupExploreTestEnv(t)

	// No achievement data at all
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/easy-to-complete", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp EasyToCompleteResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Games)
}

func TestGetHardestGames_WithData(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "SNES", "Hard Game", 85.0)
	game2 := createExploreGame(t, env.database, "NES", "Easier Game", 80.0)

	var user1 db.User
	require.NoError(t, env.database.First(&user1).Error)

	// Create a second user
	user2Token := createNonOwnerUser(t, env.router, env.token, "hardplayer", "hard@example.com", "password123")
	_ = user2Token
	var user2 db.User
	require.NoError(t, env.database.Where("username = ?", "hardplayer").First(&user2).Error)

	// Hard Game: 20 achievements, user1 unlocked 2, user2 unlocked 4 => avg 3/20 = 15%
	env.database.Create(&db.GameAchievementCache{
		RAGameID:    300,
		GameID:      game1.ID,
		TotalCount:  20,
		TotalPoints: 200,
		CachedAt:    time.Now(),
	})
	for i := 1; i <= 2; i++ {
		env.database.Create(&db.UserAchievementProgress{
			UserID:          user1.ID,
			AchievementRAID: uint(i),
			RAGameID:        300,
			UnlockedAt:      time.Now(),
		})
	}
	for i := 1; i <= 4; i++ {
		env.database.Create(&db.UserAchievementProgress{
			UserID:          user2.ID,
			AchievementRAID: uint(200 + i),
			RAGameID:        300,
			UnlockedAt:      time.Now(),
		})
	}

	// Easier Game: 10 achievements, user1 unlocked 5, user2 unlocked 7 => avg 6/10 = 60%
	env.database.Create(&db.GameAchievementCache{
		RAGameID:    400,
		GameID:      game2.ID,
		TotalCount:  10,
		TotalPoints: 100,
		CachedAt:    time.Now(),
	})
	for i := 1; i <= 5; i++ {
		env.database.Create(&db.UserAchievementProgress{
			UserID:          user1.ID,
			AchievementRAID: uint(300 + i),
			RAGameID:        400,
			UnlockedAt:      time.Now(),
		})
	}
	for i := 1; i <= 7; i++ {
		env.database.Create(&db.UserAchievementProgress{
			UserID:          user2.ID,
			AchievementRAID: uint(400 + i),
			RAGameID:        400,
			UnlockedAt:      time.Now(),
		})
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/hardest-games", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp HardestGamesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Games, 2)

	// Hard Game (15%) should be first (hardest)
	assert.Equal(t, "Hard Game", resp.Games[0].Game.Title)
	assert.Equal(t, 20, resp.Games[0].TotalAchievements)
	assert.Equal(t, 2, resp.Games[0].PlayersAttempted)

	// Easier Game (60%) should be second
	assert.Equal(t, "Easier Game", resp.Games[1].Game.Title)
}

func TestGetHardestGames_MinPlayers(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Solo Game", 85.0)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Only 1 user has attempted — should be excluded (minimum 2 required)
	env.database.Create(&db.GameAchievementCache{
		RAGameID:    500,
		GameID:      game.ID,
		TotalCount:  10,
		TotalPoints: 100,
		CachedAt:    time.Now(),
	})
	env.database.Create(&db.UserAchievementProgress{
		UserID:          user.ID,
		AchievementRAID: 1,
		RAGameID:        500,
		UnlockedAt:      time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/hardest-games", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp HardestGamesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Games)
}

func TestGetAlmostDone_WithData(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Almost Done Game", 90.0)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// 10 achievements, user unlocked 9 (90%)
	env.database.Create(&db.GameAchievementCache{
		RAGameID:    600,
		GameID:      game.ID,
		TotalCount:  10,
		TotalPoints: 100,
		CachedAt:    time.Now(),
	})
	for i := 1; i <= 9; i++ {
		env.database.Create(&db.UserAchievementProgress{
			UserID:          user.ID,
			AchievementRAID: uint(600 + i),
			RAGameID:        600,
			UnlockedAt:      time.Now(),
		})
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/almost-done", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp AlmostDoneResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Games, 1)
	assert.Equal(t, "Almost Done Game", resp.Games[0].Game.Title)
	assert.Equal(t, 9, resp.Games[0].UnlockedCount)
	assert.Equal(t, 10, resp.Games[0].TotalCount)
	assert.Equal(t, 90.0, resp.Games[0].CompletionPercent)
}

func TestGetAlmostDone_ExcludesCompleted(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Completed Game", 90.0)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// 10 achievements, user unlocked all 10 (100%) — should NOT appear
	env.database.Create(&db.GameAchievementCache{
		RAGameID:    700,
		GameID:      game.ID,
		TotalCount:  10,
		TotalPoints: 100,
		CachedAt:    time.Now(),
	})
	for i := 1; i <= 10; i++ {
		env.database.Create(&db.UserAchievementProgress{
			UserID:          user.ID,
			AchievementRAID: uint(700 + i),
			RAGameID:        700,
			UnlockedAt:      time.Now(),
		})
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/almost-done", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp AlmostDoneResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Games)
}

func TestGetAlmostDone_ExcludesLowProgress(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Low Progress Game", 85.0)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// 10 achievements, user unlocked 5 (50%) — should NOT appear (below 80%)
	env.database.Create(&db.GameAchievementCache{
		RAGameID:    800,
		GameID:      game.ID,
		TotalCount:  10,
		TotalPoints: 100,
		CachedAt:    time.Now(),
	})
	for i := 1; i <= 5; i++ {
		env.database.Create(&db.UserAchievementProgress{
			UserID:          user.ID,
			AchievementRAID: uint(800 + i),
			RAGameID:        800,
			UnlockedAt:      time.Now(),
		})
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/almost-done", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp AlmostDoneResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Games)
}

func TestGetAlmostDone_NoProgress(t *testing.T) {
	env := setupExploreTestEnv(t)

	// No achievement progress at all
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/almost-done", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp AlmostDoneResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Games)
}

func TestGetFreshChallenges_WithData(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGame(t, env.database, "SNES", "Fresh RA Game", 90.0)
	game2 := createExploreGame(t, env.database, "NES", "Started RA Game", 85.0)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Fresh RA Game: has achievements, user has 0 unlocks — should appear
	env.database.Create(&db.GameAchievementCache{
		RAGameID:    900,
		GameID:      game1.ID,
		TotalCount:  15,
		TotalPoints: 150,
		CachedAt:    time.Now(),
	})

	// Started RA Game: user has 1 unlock — should NOT appear
	env.database.Create(&db.GameAchievementCache{
		RAGameID:    1000,
		GameID:      game2.ID,
		TotalCount:  10,
		TotalPoints: 100,
		CachedAt:    time.Now(),
	})
	env.database.Create(&db.UserAchievementProgress{
		UserID:          user.ID,
		AchievementRAID: 999,
		RAGameID:        1000,
		UnlockedAt:      time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/fresh-challenges", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp FreshChallengesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Games, 1)
	assert.Equal(t, "Fresh RA Game", resp.Games[0].Game.Title)
	assert.Equal(t, 15, resp.Games[0].TotalAchievements)
	assert.Equal(t, 150, resp.Games[0].TotalPoints)
}

func TestGetFreshChallenges_ExcludesStarted(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Started Game", 90.0)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Game has achievements and user has already started
	env.database.Create(&db.GameAchievementCache{
		RAGameID:    1100,
		GameID:      game.ID,
		TotalCount:  10,
		TotalPoints: 100,
		CachedAt:    time.Now(),
	})
	env.database.Create(&db.UserAchievementProgress{
		UserID:          user.ID,
		AchievementRAID: 1100,
		RAGameID:        1100,
		UnlockedAt:      time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/fresh-challenges", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp FreshChallengesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Games)
}

func TestGetActiveChallenges_WithData(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Challenge Game", 88.0)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	env.database.Create(&db.Challenge{
		CreatorID:    user.ID,
		GameID:       game.ID,
		Name:         "Speed Run Challenge",
		Description:  "Beat the game fast",
		Type:         "speedrun",
		Difficulty:   "hard",
		Status:       "active",
		SaveFilePath: "/tmp/test.sav",
		AttemptCount: 5,
		CompletionCount: 2,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/active-challenges", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp ActiveChallengesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	require.Len(t, resp.Challenges, 1)
	assert.Equal(t, "Speed Run Challenge", resp.Challenges[0].Name)
	assert.Equal(t, "Beat the game fast", resp.Challenges[0].Description)
	assert.Equal(t, "speedrun", resp.Challenges[0].Type)
	assert.Equal(t, "hard", resp.Challenges[0].Difficulty)
	assert.Equal(t, "apitest", resp.Challenges[0].CreatorUsername)
	assert.Equal(t, "Challenge Game", resp.Challenges[0].GameTitle)
	assert.Equal(t, 5, resp.Challenges[0].AttemptCount)
	assert.Equal(t, 2, resp.Challenges[0].CompletionCount)
}

func TestGetActiveChallenges_ExcludesExpired(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Expired Challenge Game", 85.0)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	past := time.Now().Add(-24 * time.Hour)
	env.database.Create(&db.Challenge{
		CreatorID:    user.ID,
		GameID:       game.ID,
		Name:         "Expired Challenge",
		Type:         "completion",
		Difficulty:   "easy",
		Status:       "active",
		SaveFilePath: "/tmp/test.sav",
		ExpiresAt:    &past,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/active-challenges", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp ActiveChallengesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Challenges)
}

func TestGetActiveChallenges_Empty(t *testing.T) {
	env := setupExploreTestEnv(t)

	// No challenges at all
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/active-challenges", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp ActiveChallengesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.Challenges)
}

// --- Phase 14: Wild Features tests ---

func TestGetSurpriseGame_WithFilters(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create games with different consoles and genres
	game := createExploreGame(t, env.database, "SNES", "SNES Action Game", 85.0)
	env.database.Model(&game).Updates(map[string]interface{}{"cover_url": "http://example.com/cover.jpg", "genre": "Action"})
	game2 := createExploreGame(t, env.database, "NES", "NES Puzzle Game", 75.0)
	env.database.Model(&game2).Updates(map[string]interface{}{"cover_url": "http://example.com/cover2.jpg", "genre": "Puzzle"})

	// Filter by console
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/surprise?console=SNES", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var gameResp GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &gameResp))
	assert.Equal(t, "SNES Action Game", gameResp.Title)
}

func TestGetSurpriseGame_WithGenreFilter(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Puzzle Master", 80.0)
	env.database.Model(&game).Updates(map[string]interface{}{"cover_url": "http://example.com/cover.jpg", "genre": "Puzzle"})
	game2 := createExploreGame(t, env.database, "SNES", "Action Hero", 80.0)
	env.database.Model(&game2).Updates(map[string]interface{}{"cover_url": "http://example.com/cover2.jpg", "genre": "Action"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/surprise?genre=Puzzle", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var gameResp GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &gameResp))
	assert.Equal(t, "Puzzle Master", gameResp.Title)
}

func TestGetSurpriseGame_WithMinRating(t *testing.T) {
	env := setupExploreTestEnv(t)

	game := createExploreGame(t, env.database, "SNES", "Low Rated", 30.0)
	env.database.Model(&game).Update("cover_url", "http://example.com/cover.jpg")
	game2 := createExploreGame(t, env.database, "SNES", "High Rated", 90.0)
	env.database.Model(&game2).Update("cover_url", "http://example.com/cover2.jpg")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/surprise?minRating=80", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var gameResp GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &gameResp))
	assert.Equal(t, "High Rated", gameResp.Title)
}

func TestGetSurpriseGame_NoMatch(t *testing.T) {
	env := setupExploreTestEnv(t)

	// No games at all
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/surprise?console=SNES&minRating=99", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetWizardSteps(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/wizard", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp WizardResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Len(t, resp.Steps, 3)
	assert.Equal(t, "mood", resp.Steps[0].Type)
	assert.Equal(t, "era", resp.Steps[1].Type)
	assert.Equal(t, "vibe", resp.Steps[2].Type)
	assert.Len(t, resp.Steps[0].Options, 5)
}

func TestGetWizardResults_ActionMood(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create action games with covers
	for i := 0; i < 6; i++ {
		g := createExploreGame(t, env.database, "SNES", fmt.Sprintf("Action Game %d", i), 80.0+float64(i))
		env.database.Model(&g).Updates(map[string]interface{}{
			"cover_url":    fmt.Sprintf("http://example.com/cover%d.jpg", i),
			"genre":        "Action",
			"release_date": "1993-01-01",
		})
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/wizard/results?mood=action&era=early90s&vibe=solo", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp WizardResultsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.LessOrEqual(t, len(resp.Games), 5)
	assert.Equal(t, "Action-Packed Picks", resp.Title)
}

func TestGetWizardResults_Empty(t *testing.T) {
	env := setupExploreTestEnv(t)

	// No games, should return empty but still succeed
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/wizard/results?mood=story&era=80s&vibe=long", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp WizardResultsResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.NotNil(t, resp.Games)
}

func TestGetExplorerBadges_NeverPlayed(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/explorer-badges", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp ExplorerBadgesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Len(t, resp.Badges, 6)
	// All badges should have progress 0 and not be earned
	for _, b := range resp.Badges {
		assert.False(t, b.Earned, "Badge %s should not be earned", b.ID)
		assert.Equal(t, 0, b.Progress, "Badge %s should have 0 progress", b.ID)
	}
}

func TestGetExplorerBadges_WithPlayHistory(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Get user ID
	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Create games on different consoles and genres
	consoles := []string{"SNES", "NES", "GBA"}
	genres := []string{"Action", "RPG", "Puzzle"}
	for i, abbr := range consoles {
		game := createExploreGame(t, env.database, abbr, fmt.Sprintf("Game %d", i), 80.0)
		env.database.Model(&game).Update("genre", genres[i])
		// Add play history
		ph := db.PlayHistory{
			UserID:   user.ID,
			GameID:   game.ID,
			PlayTime: 3600, // 1 hour each
		}
		require.NoError(t, env.database.Create(&ph).Error)
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/explorer-badges", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp ExplorerBadgesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Find specific badges
	var consoleBadge, genreBadge, gamesBadge, timeBadge ExplorerBadge
	for _, b := range resp.Badges {
		switch b.ID {
		case "console-explorer":
			consoleBadge = b
		case "genre-master":
			genreBadge = b
		case "centurion":
			gamesBadge = b
		case "dedicated-gamer":
			timeBadge = b
		}
	}

	assert.Equal(t, 3, consoleBadge.Progress)
	assert.Equal(t, 3, genreBadge.Progress)
	assert.Equal(t, 3, gamesBadge.Progress)
	assert.Equal(t, 3, timeBadge.Progress) // 3 hours total
}

func TestGetExplorerBadges_Unauthenticated(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/explorer-badges", nil)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestGetCompletionistMap_Empty(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create some games
	createExploreGame(t, env.database, "SNES", "SNES Game 1", 80.0)
	createExploreGame(t, env.database, "NES", "NES Game 1", 80.0)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/completionist-map", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp CompletionistMapResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.GreaterOrEqual(t, len(resp.Consoles), 2)
	assert.Equal(t, 0, resp.TotalPlayed)
	assert.Equal(t, 0, resp.OverallPct)
	// Every console should have 0 played
	for _, c := range resp.Consoles {
		assert.Equal(t, 0, c.PlayedGames)
		assert.Equal(t, 0, c.Percentage)
	}
}

func TestGetCompletionistMap_WithPlayHistory(t *testing.T) {
	env := setupExploreTestEnv(t)

	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Create 3 SNES games, play 2 of them
	g1 := createExploreGame(t, env.database, "SNES", "SNES 1", 80.0)
	g2 := createExploreGame(t, env.database, "SNES", "SNES 2", 80.0)
	createExploreGame(t, env.database, "SNES", "SNES 3", 80.0)

	// Create 1 NES game, play it
	g4 := createExploreGame(t, env.database, "NES", "NES 1", 80.0)

	for _, gid := range []uint{g1.ID, g2.ID, g4.ID} {
		ph := db.PlayHistory{UserID: user.ID, GameID: gid, PlayTime: 600}
		require.NoError(t, env.database.Create(&ph).Error)
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/completionist-map", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp CompletionistMapResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, 3, resp.TotalPlayed)
	assert.Greater(t, resp.TotalGames, 0)

	// Find SNES and NES in the response
	for _, c := range resp.Consoles {
		if c.Name == "Super Nintendo Entertainment System" || c.Name == "SNES" {
			assert.Equal(t, 2, c.PlayedGames)
			assert.Equal(t, 3, c.TotalGames)
			assert.Equal(t, 66, c.Percentage) // 2/3 = 66%
		}
	}
}

func TestGetCompletionistMap_Unauthenticated(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/completionist-map", nil)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

// --- Developer/Publisher Detail enrichment tests ---

func TestGetDeveloperDetail_HeroURL(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGameWithDev(t, env.database, "NES", "Mega Man 2", 92, "Capcom", "Capcom")
	game2 := createExploreGameWithDev(t, env.database, "SNES", "Mega Man X", 95, "Capcom", "Capcom")

	// Add hero art to the lower-rated game only
	env.database.Create(&db.GameArtwork{GameID: game1.ID, HeroURL: "http://hero1.jpg"})
	// Add hero art to the higher-rated game
	env.database.Create(&db.GameArtwork{GameID: game2.ID, HeroURL: "http://hero2.jpg"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/Capcom", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// heroUrl should be from the highest-rated game with hero art
	assert.Equal(t, "http://hero2.jpg", resp.HeroURL)
}

func TestGetDeveloperDetail_HeroURL_NoArt(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameWithDev(t, env.database, "NES", "Game A", 85, "DevCo", "PubCo")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/DevCo", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Empty(t, resp.HeroURL)
}

func TestGetDeveloperDetail_TopGames(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Create 10 games, only 8 with rating > 0
	for i := 0; i < 8; i++ {
		createExploreGameFull(t, env.database, "NES", fmt.Sprintf("Rated Game %d", i), float64(90-i), "DevTop", "Pub", "Action")
	}
	// 2 games with no rating
	createExploreGameFull(t, env.database, "SNES", "Unrated A", 0, "DevTop", "Pub", "RPG")
	createExploreGameFull(t, env.database, "SNES", "Unrated B", 0, "DevTop", "Pub", "RPG")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/DevTop", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// topGames should include exactly 8 rated games
	assert.Len(t, resp.TopGames, 8)
	// All topGames should have rating > 0
	for _, g := range resp.TopGames {
		assert.Greater(t, g.Rating, 0.0)
	}
	// games should include all 10
	assert.Len(t, resp.Games, 10)
}

func TestGetDeveloperDetail_TopGames_LessThan8(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameFull(t, env.database, "NES", "Only Rated", 85, "SmallDev", "Pub", "Action")
	createExploreGameFull(t, env.database, "SNES", "Unrated", 0, "SmallDev", "Pub", "RPG")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/SmallDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Len(t, resp.TopGames, 1)
	assert.Equal(t, "Only Rated", resp.TopGames[0].Title)
}

func TestGetDeveloperDetail_TopGames_NoRated(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameFull(t, env.database, "NES", "Unrated Only", 0, "NoRateDev", "Pub", "Action")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/NoRateDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Empty(t, resp.TopGames)
}

func TestGetDeveloperDetail_GenreBreakdown(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameFull(t, env.database, "NES", "Action 1", 85, "GenreDev", "Pub", "Action")
	createExploreGameFull(t, env.database, "NES", "Action 2", 80, "GenreDev", "Pub", "Action")
	createExploreGameFull(t, env.database, "SNES", "RPG 1", 90, "GenreDev", "Pub", "RPG")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/GenreDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.Len(t, resp.GenreBreakdown, 2)
	// Sorted by count DESC
	assert.Equal(t, "Action", resp.GenreBreakdown[0].Name)
	assert.Equal(t, 2, resp.GenreBreakdown[0].GameCount)
	assert.Equal(t, "RPG", resp.GenreBreakdown[1].Name)
	assert.Equal(t, 1, resp.GenreBreakdown[1].GameCount)
}

func TestGetDeveloperDetail_GenreBreakdown_CommaSeparated(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameFull(t, env.database, "NES", "Multi Genre", 85, "CSVDev", "Pub", "Action, RPG")
	createExploreGameFull(t, env.database, "SNES", "Pure Action", 80, "CSVDev", "Pub", "Action")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/CSVDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.Len(t, resp.GenreBreakdown, 2)
	// Action appears in both games
	assert.Equal(t, "Action", resp.GenreBreakdown[0].Name)
	assert.Equal(t, 2, resp.GenreBreakdown[0].GameCount)
	// RPG appears in one game (from comma-separated value)
	assert.Equal(t, "RPG", resp.GenreBreakdown[1].Name)
	assert.Equal(t, 1, resp.GenreBreakdown[1].GameCount)
}

func TestGetDeveloperDetail_GenreBreakdown_EmptyGenres(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameFull(t, env.database, "NES", "No Genre", 85, "NoGenreDev", "Pub", "")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/NoGenreDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Empty(t, resp.GenreBreakdown)
}

func TestGetDeveloperDetail_PlatformBreakdown(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameFull(t, env.database, "NES", "NES Game 1", 85, "PlatDev", "Pub", "Action")
	createExploreGameFull(t, env.database, "NES", "NES Game 2", 80, "PlatDev", "Pub", "Action")
	createExploreGameFull(t, env.database, "SNES", "SNES Game 1", 90, "PlatDev", "Pub", "RPG")
	createExploreGameFull(t, env.database, "GBA", "GBA Game 1", 75, "PlatDev", "Pub", "Puzzle")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/PlatDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.Len(t, resp.PlatformBreakdown, 3)
	// Sorted by count DESC
	assert.Equal(t, "nes", resp.PlatformBreakdown[0].ConsoleID)
	assert.Equal(t, 2, resp.PlatformBreakdown[0].Count)
	assert.NotEmpty(t, resp.PlatformBreakdown[0].ConsoleName)
}

func TestGetDeveloperDetail_Publishers(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameFull(t, env.database, "NES", "Game A", 85, "MultiPubDev", "Nintendo", "Action")
	createExploreGameFull(t, env.database, "SNES", "Game B", 80, "MultiPubDev", "Nintendo", "RPG")
	createExploreGameFull(t, env.database, "GBA", "Game C", 75, "MultiPubDev", "Capcom", "Action")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/MultiPubDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.Len(t, resp.Publishers, 2)
	// Sorted by count DESC
	assert.Equal(t, "Nintendo", resp.Publishers[0].Name)
	assert.Equal(t, 2, resp.Publishers[0].Count)
	assert.Equal(t, "Capcom", resp.Publishers[1].Name)
	assert.Equal(t, 1, resp.Publishers[1].Count)
}

func TestGetDeveloperDetail_UserStats(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGameFull(t, env.database, "NES", "Played Game 1", 85, "StatDev", "Pub", "Action")
	game2 := createExploreGameFull(t, env.database, "SNES", "Played Game 2", 90, "StatDev", "Pub", "RPG")
	createExploreGameFull(t, env.database, "GBA", "Unplayed Game", 75, "StatDev", "Pub", "Puzzle")

	// Get the user
	var user db.User
	require.NoError(t, env.database.First(&user).Error)

	// Create play history
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     game1.ID,
		PlayTime:   3600,
		LastPlayed: time.Now(),
	})
	env.database.Create(&db.PlayHistory{
		UserID:     user.ID,
		GameID:     game2.ID,
		PlayTime:   7200,
		LastPlayed: time.Now(),
	})

	// Favorite one game
	env.database.Create(&db.Favorite{UserID: user.ID, GameID: game1.ID})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/StatDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.NotNil(t, resp.UserStats)
	assert.Equal(t, int64(10800), resp.UserStats.TotalPlayTime) // 3600 + 7200
	assert.Equal(t, 2, resp.UserStats.GamesPlayed)
	assert.Equal(t, 1, resp.UserStats.FavoriteCount)
	require.NotNil(t, resp.UserStats.MostPlayedGame)
	assert.Equal(t, "Played Game 2", resp.UserStats.MostPlayedGame.Title) // 7200s > 3600s
}

func TestGetDeveloperDetail_UserStats_NoPlayHistory(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameFull(t, env.database, "NES", "Game A", 85, "NoPlayDev", "Pub", "Action")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/NoPlayDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// userStats should be nil/omitted when no play history
	assert.Nil(t, resp.UserStats)
}

func TestGetDeveloperDetail_NonExistent_EmptyArrays(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/GhostDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "GhostDev", resp.Name)
	assert.Equal(t, 0, resp.GameCount)
	assert.Empty(t, resp.Games)
	assert.Empty(t, resp.TopGames)
	assert.Empty(t, resp.GenreBreakdown)
	assert.Empty(t, resp.PlatformBreakdown)
	assert.Empty(t, resp.Publishers)
	assert.Nil(t, resp.UserStats)
	assert.Empty(t, resp.HeroURL)
}

// --- Publisher Detail enrichment tests ---

func TestGetPublisherDetail_Enriched(t *testing.T) {
	env := setupExploreTestEnv(t)

	game1 := createExploreGameFull(t, env.database, "NES", "Pub Game 1", 85, "Dev A", "EnrichPub", "Action")
	game2 := createExploreGameFull(t, env.database, "SNES", "Pub Game 2", 92, "Dev A", "EnrichPub", "RPG")
	createExploreGameFull(t, env.database, "GBA", "Pub Game 3", 78, "Dev B", "EnrichPub", "Puzzle")

	// Add hero art to highest-rated
	env.database.Create(&db.GameArtwork{GameID: game2.ID, HeroURL: "http://pub-hero.jpg"})

	// Get user, add play history
	var user db.User
	require.NoError(t, env.database.First(&user).Error)
	env.database.Create(&db.PlayHistory{UserID: user.ID, GameID: game1.ID, PlayTime: 1800, LastPlayed: time.Now()})
	env.database.Create(&db.Favorite{UserID: user.ID, GameID: game2.ID})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/publishers/EnrichPub", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PublisherDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Basic fields
	assert.Equal(t, "EnrichPub", resp.Name)
	assert.Equal(t, 3, resp.GameCount)
	assert.Len(t, resp.Games, 3)

	// Hero URL
	assert.Equal(t, "http://pub-hero.jpg", resp.HeroURL)

	// Top games (all 3 have rating > 0)
	assert.Len(t, resp.TopGames, 3)

	// Genre breakdown
	require.Len(t, resp.GenreBreakdown, 3)

	// Platform breakdown
	require.Len(t, resp.PlatformBreakdown, 3)

	// Developers (publisher detail shows developers)
	require.Len(t, resp.Developers, 2)
	assert.Equal(t, "Dev A", resp.Developers[0].Name)
	assert.Equal(t, 2, resp.Developers[0].Count)
	assert.Equal(t, "Dev B", resp.Developers[1].Name)
	assert.Equal(t, 1, resp.Developers[1].Count)

	// User stats
	require.NotNil(t, resp.UserStats)
	assert.Equal(t, int64(1800), resp.UserStats.TotalPlayTime)
	assert.Equal(t, 1, resp.UserStats.GamesPlayed)
	assert.Equal(t, 1, resp.UserStats.FavoriteCount)
	require.NotNil(t, resp.UserStats.MostPlayedGame)
	assert.Equal(t, "Pub Game 1", resp.UserStats.MostPlayedGame.Title)
}

func TestGetPublisherDetail_NoPlayHistory(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameFull(t, env.database, "NES", "Pub No Play", 85, "Dev", "NoPlayPub", "Action")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/publishers/NoPlayPub", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PublisherDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Nil(t, resp.UserStats)
}

// --- Phase 3: Statistics Fields tests ---

// createExploreGameComplete creates a game with developer, publisher, genre, rating, and release date.
func createExploreGameComplete(t *testing.T, database *gorm.DB, consoleAbbr, title string, rating float64, developer, publisher, genre, releaseDate string) db.Game {
	t.Helper()
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", consoleAbbr).First(&console).Error)
	game := db.Game{
		ConsoleID:   console.ID,
		Title:       title,
		FileName:    title + ".rom",
		FilePath:    consoleAbbr + "/" + title + ".rom",
		Rating:      rating,
		Genre:       genre,
		Developer:   developer,
		Publisher:   publisher,
		ReleaseDate: releaseDate,
	}
	require.NoError(t, database.Create(&game).Error)
	return game
}

// --- ActiveYears tests ---

func TestGetDeveloperDetail_ActiveYears(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Early Game", 80, "YearsDev", "Pub", "Action", "1987-03-15")
	createExploreGameComplete(t, env.database, "SNES", "Mid Game", 85, "YearsDev", "Pub", "Action", "1995-06-20")
	createExploreGameComplete(t, env.database, "GBA", "Late Game", 90, "YearsDev", "Pub", "Action", "2003-11-10")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/YearsDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.NotNil(t, resp.ActiveYears)
	assert.Equal(t, 1987, resp.ActiveYears.First)
	assert.Equal(t, 2003, resp.ActiveYears.Last)
}

func TestGetDeveloperDetail_ActiveYears_SingleGame(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Only Game", 80, "SingleYearDev", "Pub", "Action", "1991-08-12")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/SingleYearDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.NotNil(t, resp.ActiveYears)
	assert.Equal(t, 1991, resp.ActiveYears.First)
	assert.Equal(t, 1991, resp.ActiveYears.Last)
}

func TestGetDeveloperDetail_ActiveYears_NoDates(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "No Date Game", 80, "NoDateDev", "Pub", "Action", "")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/NoDateDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Nil(t, resp.ActiveYears)
}

func TestGetDeveloperDetail_ActiveYears_MixedDates(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Dated Game", 80, "MixDateDev", "Pub", "Action", "1993-05-01")
	createExploreGameComplete(t, env.database, "SNES", "Undated Game", 85, "MixDateDev", "Pub", "Action", "")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/MixDateDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.NotNil(t, resp.ActiveYears)
	assert.Equal(t, 1993, resp.ActiveYears.First)
	assert.Equal(t, 1993, resp.ActiveYears.Last)
}

// --- RatingDistribution tests ---

func TestGetDeveloperDetail_RatingDistribution(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Excellent Game", 95, "RatingDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "NES", "Good Game 1", 80, "RatingDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "SNES", "Good Game 2", 75, "RatingDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "SNES", "Average Game", 60, "RatingDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "GBA", "Poor Game", 30, "RatingDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "GBA", "Unrated Game", 0, "RatingDev", "Pub", "Action", "")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/RatingDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, 1, resp.RatingDistribution.Excellent)
	assert.Equal(t, 2, resp.RatingDistribution.Good)
	assert.Equal(t, 1, resp.RatingDistribution.Average)
	assert.Equal(t, 1, resp.RatingDistribution.Poor)
	assert.Equal(t, 1, resp.RatingDistribution.Unrated)
}

func TestGetDeveloperDetail_RatingDistribution_AllUnrated(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Unrated 1", 0, "AllUnratedDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "SNES", "Unrated 2", 0, "AllUnratedDev", "Pub", "Action", "")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/AllUnratedDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, 0, resp.RatingDistribution.Excellent)
	assert.Equal(t, 0, resp.RatingDistribution.Good)
	assert.Equal(t, 0, resp.RatingDistribution.Average)
	assert.Equal(t, 0, resp.RatingDistribution.Poor)
	assert.Equal(t, 2, resp.RatingDistribution.Unrated)
}

func TestGetDeveloperDetail_RatingDistribution_BoundaryValues(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Boundary 90", 90, "BoundaryDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "NES", "Boundary 70", 70, "BoundaryDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "SNES", "Boundary 50", 50, "BoundaryDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "GBA", "Boundary 49", 49, "BoundaryDev", "Pub", "Action", "")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/BoundaryDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, 1, resp.RatingDistribution.Excellent) // 90 is in excellent
	assert.Equal(t, 1, resp.RatingDistribution.Good)      // 70 is in good
	assert.Equal(t, 1, resp.RatingDistribution.Average)    // 50 is in average
	assert.Equal(t, 1, resp.RatingDistribution.Poor)       // 49 is in poor
	assert.Equal(t, 0, resp.RatingDistribution.Unrated)
}

// --- PrimaryGenre tests ---

func TestGetDeveloperDetail_PrimaryGenre(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Action 1", 85, "PGenreDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "NES", "Action 2", 80, "PGenreDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "SNES", "RPG 1", 90, "PGenreDev", "Pub", "RPG", "")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/PGenreDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "Action", resp.PrimaryGenre)
}

func TestGetDeveloperDetail_PrimaryGenre_CommaSeparated(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Multi 1", 85, "CSVGenreDev", "Pub", "Action, RPG", "")
	createExploreGameComplete(t, env.database, "SNES", "Multi 2", 80, "CSVGenreDev", "Pub", "Action", "")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/CSVGenreDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "Action", resp.PrimaryGenre)
}

func TestGetDeveloperDetail_PrimaryGenre_NoGenres(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "No Genre", 85, "NoGenrePDev", "Pub", "", "")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/NoGenrePDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Empty(t, resp.PrimaryGenre)
}

func TestGetDeveloperDetail_PrimaryGenre_Tie(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Action Game", 85, "TieGenreDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "SNES", "RPG Game", 80, "TieGenreDev", "Pub", "RPG", "")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/TieGenreDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// On a tie, alphabetically first genre wins
	assert.Equal(t, "Action", resp.PrimaryGenre)
}

// --- Timeline tests ---

func TestGetDeveloperDetail_Timeline(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Game 1991a", 80, "TimelineDev", "Pub", "Action", "1991-03-15")
	createExploreGameComplete(t, env.database, "NES", "Game 1991b", 85, "TimelineDev", "Pub", "Action", "1991-11-20")
	createExploreGameComplete(t, env.database, "SNES", "Game 1995", 90, "TimelineDev", "Pub", "RPG", "1995-06-01")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/TimelineDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.Len(t, resp.Timeline, 2)
	// Sorted by year ascending
	assert.Equal(t, 1991, resp.Timeline[0].Year)
	assert.Len(t, resp.Timeline[0].Games, 2)
	assert.Equal(t, 1995, resp.Timeline[1].Year)
	assert.Len(t, resp.Timeline[1].Games, 1)

	// Check timeline game fields
	game := resp.Timeline[1].Games[0]
	assert.NotEmpty(t, game.ID)
	assert.Equal(t, "Game 1995", game.Title)
	assert.Equal(t, 90.0, game.Rating)
}

func TestGetDeveloperDetail_Timeline_TooFewGames(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Only 2 games with dates — needs at least 3 for timeline
	createExploreGameComplete(t, env.database, "NES", "Game A", 80, "FewTimelineDev", "Pub", "Action", "1991-01-01")
	createExploreGameComplete(t, env.database, "SNES", "Game B", 85, "FewTimelineDev", "Pub", "Action", "1995-01-01")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/FewTimelineDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Nil(t, resp.Timeline)
}

func TestGetDeveloperDetail_Timeline_SingleYear(t *testing.T) {
	env := setupExploreTestEnv(t)

	// 3 games but all same year — needs at least 2 distinct years
	createExploreGameComplete(t, env.database, "NES", "Game X1", 80, "OneYearDev", "Pub", "Action", "1991-01-01")
	createExploreGameComplete(t, env.database, "NES", "Game X2", 85, "OneYearDev", "Pub", "Action", "1991-06-15")
	createExploreGameComplete(t, env.database, "SNES", "Game X3", 90, "OneYearDev", "Pub", "Action", "1991-12-25")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/OneYearDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Nil(t, resp.Timeline)
}

func TestGetDeveloperDetail_Timeline_NoDates(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "No Date A", 80, "NoDateTimeDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "SNES", "No Date B", 85, "NoDateTimeDev", "Pub", "Action", "")
	createExploreGameComplete(t, env.database, "GBA", "No Date C", 90, "NoDateTimeDev", "Pub", "Action", "")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/NoDateTimeDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Nil(t, resp.Timeline)
}

// --- Publisher statistics fields tests ---

func TestGetPublisherDetail_ActiveYears(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Early Pub Game", 80, "Dev", "YearsPub", "Action", "1989-01-20")
	createExploreGameComplete(t, env.database, "SNES", "Late Pub Game", 90, "Dev", "YearsPub", "RPG", "2001-07-15")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/publishers/YearsPub", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PublisherDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.NotNil(t, resp.ActiveYears)
	assert.Equal(t, 1989, resp.ActiveYears.First)
	assert.Equal(t, 2001, resp.ActiveYears.Last)
}

func TestGetPublisherDetail_RatingDistribution(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Pub Ex", 92, "Dev", "DistPub", "Action", "")
	createExploreGameComplete(t, env.database, "SNES", "Pub Good", 75, "Dev", "DistPub", "Action", "")
	createExploreGameComplete(t, env.database, "GBA", "Pub Unrated", 0, "Dev", "DistPub", "Action", "")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/publishers/DistPub", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PublisherDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, 1, resp.RatingDistribution.Excellent)
	assert.Equal(t, 1, resp.RatingDistribution.Good)
	assert.Equal(t, 0, resp.RatingDistribution.Average)
	assert.Equal(t, 0, resp.RatingDistribution.Poor)
	assert.Equal(t, 1, resp.RatingDistribution.Unrated)
}

func TestGetPublisherDetail_PrimaryGenre(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Pub Plat 1", 80, "Dev", "PGenrePub", "Platformer", "")
	createExploreGameComplete(t, env.database, "NES", "Pub Plat 2", 85, "Dev", "PGenrePub", "Platformer", "")
	createExploreGameComplete(t, env.database, "SNES", "Pub RPG", 90, "Dev", "PGenrePub", "RPG", "")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/publishers/PGenrePub", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PublisherDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Equal(t, "Platformer", resp.PrimaryGenre)
}

func TestGetPublisherDetail_Timeline(t *testing.T) {
	env := setupExploreTestEnv(t)

	createExploreGameComplete(t, env.database, "NES", "Pub TL 1a", 80, "Dev", "TimelinePub", "Action", "1990-01-01")
	createExploreGameComplete(t, env.database, "NES", "Pub TL 1b", 85, "Dev", "TimelinePub", "Action", "1990-06-01")
	createExploreGameComplete(t, env.database, "SNES", "Pub TL 2", 90, "Dev", "TimelinePub", "RPG", "1997-03-15")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/publishers/TimelinePub", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PublisherDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.Len(t, resp.Timeline, 2)
	assert.Equal(t, 1990, resp.Timeline[0].Year)
	assert.Len(t, resp.Timeline[0].Games, 2)
	assert.Equal(t, 1997, resp.Timeline[1].Year)
	assert.Len(t, resp.Timeline[1].Games, 1)
}

// --- Non-existent developer/publisher should have sensible defaults ---

func TestGetDeveloperDetail_NonExistent_StatisticsDefaults(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/GhostDev", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Nil(t, resp.ActiveYears)
	assert.Equal(t, 0, resp.RatingDistribution.Excellent)
	assert.Equal(t, 0, resp.RatingDistribution.Good)
	assert.Equal(t, 0, resp.RatingDistribution.Average)
	assert.Equal(t, 0, resp.RatingDistribution.Poor)
	assert.Equal(t, 0, resp.RatingDistribution.Unrated)
	assert.Empty(t, resp.PrimaryGenre)
	assert.Nil(t, resp.Timeline)
}

func TestGetPublisherDetail_NonExistent_StatisticsDefaults(t *testing.T) {
	env := setupExploreTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/publishers/GhostPub", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PublisherDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Nil(t, resp.ActiveYears)
	assert.Equal(t, 0, resp.RatingDistribution.Excellent)
	assert.Equal(t, 0, resp.RatingDistribution.Good)
	assert.Equal(t, 0, resp.RatingDistribution.Average)
	assert.Equal(t, 0, resp.RatingDistribution.Poor)
	assert.Equal(t, 0, resp.RatingDistribution.Unrated)
	assert.Empty(t, resp.PrimaryGenre)
	assert.Nil(t, resp.Timeline)
}

// --- Related Developers tests ---

func TestGetDeveloperDetail_RelatedDevelopers_SharedPublishers(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Developer "Intelligent Systems" published by "Nintendo"
	createExploreGameWithDev(t, env.database, "NES", "Fire Emblem", 88, "Intelligent Systems", "Nintendo")
	createExploreGameWithDev(t, env.database, "SNES", "Paper Mario", 85, "Intelligent Systems", "Nintendo")

	// Developer "HAL Laboratory" also published by "Nintendo"
	createExploreGameWithDev(t, env.database, "NES", "Kirby", 82, "HAL Laboratory", "Nintendo")
	createExploreGameWithDev(t, env.database, "SNES", "Smash Bros", 90, "HAL Laboratory", "Nintendo")

	// Developer "Retro Studios" also published by "Nintendo"
	createExploreGameWithDev(t, env.database, "GBA", "Metroid Prime", 95, "Retro Studios", "Nintendo")

	// Developer "Capcom" published by "Capcom" — NOT related
	createExploreGameWithDev(t, env.database, "NES", "Mega Man", 80, "Capcom", "Capcom")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/Intelligent%20Systems", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.NotNil(t, resp.RelatedDevelopers)
	require.Len(t, resp.RelatedDevelopers, 2) // HAL Laboratory and Retro Studios

	// Both share "Nintendo" as publisher; HAL has more games so should come first
	assert.Equal(t, "HAL Laboratory", resp.RelatedDevelopers[0].Name)
	assert.Equal(t, 2, resp.RelatedDevelopers[0].GameCount)
	assert.Equal(t, []string{"Nintendo"}, resp.RelatedDevelopers[0].SharedPublishers)

	assert.Equal(t, "Retro Studios", resp.RelatedDevelopers[1].Name)
	assert.Equal(t, 1, resp.RelatedDevelopers[1].GameCount)
	assert.Equal(t, []string{"Nintendo"}, resp.RelatedDevelopers[1].SharedPublishers)
}

func TestGetDeveloperDetail_RelatedDevelopers_NoSharedPublishers(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Developer "Capcom" published only by "Capcom"
	createExploreGameWithDev(t, env.database, "NES", "Mega Man", 80, "Capcom", "Capcom")
	createExploreGameWithDev(t, env.database, "SNES", "Street Fighter", 85, "Capcom", "Capcom")

	// Developer "Square" published only by "Square" — no overlap
	createExploreGameWithDev(t, env.database, "SNES", "FF6", 96, "Square", "Square")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/Square", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// No related developers since no other developer publishes with "Square"
	assert.Nil(t, resp.RelatedDevelopers)
}

func TestGetDeveloperDetail_RelatedDevelopers_SelfPublished(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Developer "Nintendo" publishes its own games
	createExploreGameWithDev(t, env.database, "NES", "Mario Bros", 90, "Nintendo", "Nintendo")

	// Developer "HAL Laboratory" also published by "Nintendo"
	createExploreGameWithDev(t, env.database, "SNES", "Kirby", 82, "HAL Laboratory", "Nintendo")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/Nintendo", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Nintendo (the developer) shares publisher "Nintendo" with HAL Laboratory
	require.NotNil(t, resp.RelatedDevelopers)
	require.Len(t, resp.RelatedDevelopers, 1)
	assert.Equal(t, "HAL Laboratory", resp.RelatedDevelopers[0].Name)
	assert.Equal(t, []string{"Nintendo"}, resp.RelatedDevelopers[0].SharedPublishers)
}

func TestGetDeveloperDetail_RelatedDevelopers_MultipleSharedPublishers(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Developer "DevA" published by both "PubX" and "PubY"
	createExploreGameWithDev(t, env.database, "NES", "Game A1", 80, "DevA", "PubX")
	createExploreGameWithDev(t, env.database, "SNES", "Game A2", 85, "DevA", "PubY")

	// Developer "DevB" also published by both "PubX" and "PubY"
	createExploreGameWithDev(t, env.database, "NES", "Game B1", 75, "DevB", "PubX")
	createExploreGameWithDev(t, env.database, "SNES", "Game B2", 70, "DevB", "PubY")

	// Developer "DevC" published only by "PubX"
	createExploreGameWithDev(t, env.database, "GBA", "Game C1", 60, "DevC", "PubX")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/developers/DevA", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp DeveloperDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.NotNil(t, resp.RelatedDevelopers)
	require.Len(t, resp.RelatedDevelopers, 2)

	// DevB shares 2 publishers, DevC shares 1 — DevB should rank first
	assert.Equal(t, "DevB", resp.RelatedDevelopers[0].Name)
	assert.Equal(t, 2, resp.RelatedDevelopers[0].GameCount)
	assert.Equal(t, []string{"PubX", "PubY"}, resp.RelatedDevelopers[0].SharedPublishers)

	assert.Equal(t, "DevC", resp.RelatedDevelopers[1].Name)
	assert.Equal(t, 1, resp.RelatedDevelopers[1].GameCount)
	assert.Equal(t, []string{"PubX"}, resp.RelatedDevelopers[1].SharedPublishers)
}

// --- Related Publishers tests ---

func TestGetPublisherDetail_RelatedPublishers_SharedDevelopers(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Publisher "Nintendo" publishes games by "Intelligent Systems"
	createExploreGameWithDev(t, env.database, "NES", "Fire Emblem", 88, "Intelligent Systems", "Nintendo")

	// Publisher "Atlus" also publishes games by "Intelligent Systems"
	createExploreGameWithDev(t, env.database, "SNES", "SMT", 85, "Intelligent Systems", "Atlus")
	createExploreGameWithDev(t, env.database, "GBA", "Persona", 90, "Intelligent Systems", "Atlus")

	// Publisher "Capcom" publishes only its own games — NOT related
	createExploreGameWithDev(t, env.database, "NES", "Mega Man", 80, "Capcom", "Capcom")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/publishers/Nintendo", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PublisherDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.NotNil(t, resp.RelatedPublishers)
	require.Len(t, resp.RelatedPublishers, 1)
	assert.Equal(t, "Atlus", resp.RelatedPublishers[0].Name)
	assert.Equal(t, 2, resp.RelatedPublishers[0].GameCount)
	assert.Equal(t, []string{"Intelligent Systems"}, resp.RelatedPublishers[0].SharedDevelopers)
}

func TestGetPublisherDetail_RelatedPublishers_NoSharedDevelopers(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Publisher "Capcom" with developer "Capcom"
	createExploreGameWithDev(t, env.database, "NES", "Mega Man", 80, "Capcom", "Capcom")

	// Publisher "Square" with developer "Square" — no overlap
	createExploreGameWithDev(t, env.database, "SNES", "FF6", 96, "Square", "Square")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/publishers/Capcom", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PublisherDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Nil(t, resp.RelatedPublishers)
}

func TestGetPublisherDetail_RelatedPublishers_SelfPublished(t *testing.T) {
	env := setupExploreTestEnv(t)

	// Publisher "Nintendo" publishes games by developer "Nintendo"
	createExploreGameWithDev(t, env.database, "NES", "Mario Bros", 90, "Nintendo", "Nintendo")

	// Publisher "Sega" also publishes games by developer "Nintendo" (hypothetical cross-publish)
	createExploreGameWithDev(t, env.database, "SNES", "Sonic Crossover", 75, "Nintendo", "Sega")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/explore/publishers/Nintendo", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp PublisherDetailResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Nintendo (the publisher) shares developer "Nintendo" with Sega
	require.NotNil(t, resp.RelatedPublishers)
	require.Len(t, resp.RelatedPublishers, 1)
	assert.Equal(t, "Sega", resp.RelatedPublishers[0].Name)
	assert.Equal(t, []string{"Nintendo"}, resp.RelatedPublishers[0].SharedDevelopers)
}
