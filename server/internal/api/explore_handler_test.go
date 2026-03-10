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
