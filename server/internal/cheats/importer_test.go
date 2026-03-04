package cheats

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func setupTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.Console{}, &db.Game{}, &db.CheatCode{}))
	return database
}

func TestParseChtContent_basic(t *testing.T) {
	content := `cheat0_desc = "Infinite Health"
cheat0_code = "ABCD-1234"
cheat0_enable = false
cheat1_desc = "Max Money"
cheat1_code = "EFGH+5678"`

	entries, err := ParseChtContent(content)
	require.NoError(t, err)
	require.Len(t, entries, 2)
	assert.Equal(t, 0, entries[0].Index)
	assert.Equal(t, "Infinite Health", entries[0].Description)
	assert.Equal(t, "ABCD-1234", entries[0].Code)
	assert.Equal(t, 1, entries[1].Index)
	assert.Equal(t, "Max Money", entries[1].Description)
	assert.Equal(t, "EFGH+5678", entries[1].Code)
}

func TestParseChtContent_empty(t *testing.T) {
	entries, err := ParseChtContent("")
	require.NoError(t, err)
	assert.Empty(t, entries)
}

func TestParseChtContent_missingCode(t *testing.T) {
	content := `cheat0_desc = "No Code Cheat"`
	entries, err := ParseChtContent(content)
	require.NoError(t, err)
	assert.Empty(t, entries)
}

func TestParseChtContent_missingDesc(t *testing.T) {
	content := `cheat0_code = "ABCD1234"`
	entries, err := ParseChtContent(content)
	require.NoError(t, err)
	assert.Empty(t, entries)
}

func TestSystemFolders_knownConsoles(t *testing.T) {
	assert.Equal(t, "Nintendo - Nintendo Entertainment System", systemFolders["nes"])
	assert.Equal(t, "Nintendo - Super Nintendo Entertainment System", systemFolders["snes"])
	assert.Equal(t, "Sega - Mega Drive - Genesis", systemFolders["genesis"])
	assert.Equal(t, "Nintendo - Game Boy", systemFolders["gb"])
}

func TestSystemFolders_unknownConsole(t *testing.T) {
	_, ok := systemFolders["nonexistent"]
	assert.False(t, ok)
}

func TestImportCheatsForGame_SkipsUnverifiedGames(t *testing.T) {
	database := setupTestDB(t)

	var requestCount atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requestCount.Add(1)
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	console := db.Console{Name: "SNES", Abbreviation: "snes", Extensions: ".sfc", FolderName: "snes"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID:          console.ID,
		Title:              "Unverified Game",
		FileName:           "Unverified Game.sfc",
		FilePath:            "/games/snes/Unverified Game.sfc",
		VerificationStatus: "unverified",
	}
	require.NoError(t, database.Create(&game).Error)

	client := &http.Client{Timeout: 5 * time.Second}
	err := ImportCheatsForGame(database, game, console, srv.URL, client)
	require.NoError(t, err)

	assert.Equal(t, int32(0), requestCount.Load(), "should make zero HTTP requests for unverified games")

	var count int64
	database.Model(&db.CheatCode{}).Count(&count)
	assert.Equal(t, int64(0), count)
}

func TestImportCheatsForGame_ImportsVerifiedGames(t *testing.T) {
	database := setupTestDB(t)

	chtContent := `cheat0_desc = "Infinite Lives"
cheat0_code = "7E0DB2:09"
cheat1_desc = "Invincible"
cheat1_code = "7E14A2:01"`

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		expectedPath := fmt.Sprintf("/%s/%s.cht",
			"Nintendo - Super Nintendo Entertainment System",
			"Super Mario World (USA)")
		if r.URL.Path == expectedPath {
			w.WriteHeader(http.StatusOK)
			w.Write([]byte(chtContent))
		} else {
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	console := db.Console{Name: "SNES", Abbreviation: "snes", Extensions: ".sfc", FolderName: "snes"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID:          console.ID,
		Title:              "Super Mario World",
		FileName:           "Super Mario World (USA).sfc",
		FilePath:            "/games/snes/Super Mario World (USA).sfc",
		VerificationStatus: "verified",
	}
	require.NoError(t, database.Create(&game).Error)

	client := &http.Client{Timeout: 5 * time.Second}
	err := ImportCheatsForGame(database, game, console, srv.URL, client)
	require.NoError(t, err)

	var cheats []db.CheatCode
	require.NoError(t, database.Where("game_id = ?", game.ID).Find(&cheats).Error)
	assert.Len(t, cheats, 2)
}

func TestStartAutoImport_SkipsWhenCheatsExist(t *testing.T) {
	database := setupTestDB(t)

	console := db.Console{Name: "SNES", Abbreviation: "snes", Extensions: ".sfc", FolderName: "snes"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID:          console.ID,
		Title:              "Test Game",
		FileName:           "Test Game.sfc",
		FilePath:            "/games/snes/Test Game.sfc",
		VerificationStatus: "verified",
	}
	require.NoError(t, database.Create(&game).Error)

	// Pre-populate a cheat so auto-import should skip
	cheat := db.CheatCode{
		GameID:      game.ID,
		CheatIndex:  0,
		Description: "Existing cheat",
		Code:        "ABCD",
	}
	require.NoError(t, database.Create(&cheat).Error)

	// StartAutoImport should return immediately without spawning import
	StartAutoImport(database)

	// Give a moment for any goroutine to start (it shouldn't)
	time.Sleep(50 * time.Millisecond)

	var count int64
	database.Model(&db.CheatCode{}).Count(&count)
	assert.Equal(t, int64(1), count, "should still have only the pre-existing cheat")
}

func TestStartAutoImport_RunsWhenNoCheats(t *testing.T) {
	database := setupTestDB(t)

	chtContent := `cheat0_desc = "Test Cheat"
cheat0_code = "AAAA:01"`

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(chtContent))
	}))
	defer srv.Close()

	console := db.Console{Name: "SNES", Abbreviation: "snes", Extensions: ".sfc", FolderName: "snes"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID:          console.ID,
		Title:              "Test Game",
		FileName:           "Test Game.sfc",
		FilePath:            "/games/snes/Test Game.sfc",
		VerificationStatus: "verified",
		Console:            console,
	}
	require.NoError(t, database.Create(&game).Error)

	// No cheats exist, so auto-import should run.
	// We can't use StartAutoImport directly because it uses DefaultBaseURL.
	// Instead, test the logic: count == 0 means import should happen.
	var count int64
	database.Model(&db.CheatCode{}).Count(&count)
	assert.Equal(t, int64(0), count, "no cheats should exist before import")

	// Directly call ImportAllCheats with the test server URL to verify it works
	err := ImportAllCheats(database, srv.URL)
	require.NoError(t, err)

	database.Model(&db.CheatCode{}).Count(&count)
	assert.Greater(t, count, int64(0), "cheats should be imported")
}
