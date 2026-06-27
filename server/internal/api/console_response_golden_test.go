package api

import (
	"encoding/json"
	"os"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// TestConsoleResponseGolden freezes the exact ConsoleResponse wire shape
// for every seeded console. The #1443 work moves static console fields out
// of the DB and derives them from the code registry instead; this golden
// guarantees the API contract stays byte-for-byte identical through that
// migration (derive-from-registry, then drop the columns).
//
// Regenerate after an intentional contract change with:
//
//	UPDATE_CONSOLE_RESPONSE_GOLDEN=1 go test ./internal/api -run TestConsoleResponseGolden
func TestConsoleResponseGolden(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(
		&db.Console{}, &db.HardwareMaker{}, &db.MediaType{}, &db.MediaTypeCategory{},
	))
	require.NoError(t, db.SeedConsoles(database))
	require.NoError(t, db.SeedMediaTypeCategories(database))
	require.NoError(t, db.SeedMediaTypes(database))
	require.NoError(t, db.SeedHardwareMakers(database))
	require.NoError(t, db.SeedConsoleMetadata(database))

	var consoles []db.Console
	require.NoError(t, database.
		Preload("HardwareMaker").
		Preload("MediaType").
		Preload("MediaType.Category").
		Order("abbreviation").
		Find(&consoles).Error)

	responses := make([]ConsoleResponse, 0, len(consoles))
	for _, c := range consoles {
		r := ToConsoleResponse(c)
		// Timestamps are row-creation noise, not part of the static
		// contract under test; zero them for a stable snapshot.
		r.CreatedAt = time.Time{}
		r.UpdatedAt = time.Time{}
		responses = append(responses, r)
	}

	got, err := json.MarshalIndent(responses, "", "  ")
	require.NoError(t, err)
	got = append(got, '\n')

	goldenPath := "console_response_golden.json"
	if os.Getenv("UPDATE_CONSOLE_RESPONSE_GOLDEN") == "1" {
		require.NoError(t, os.WriteFile(goldenPath, got, 0o644))
		t.Logf("wrote %s (%d consoles)", goldenPath, len(responses))
		return
	}

	want, err := os.ReadFile(goldenPath)
	require.NoErrorf(t, err, "missing golden; regenerate with UPDATE_CONSOLE_RESPONSE_GOLDEN=1")
	assert.Equal(t, string(want), string(got),
		"ConsoleResponse contract drifted; if intentional, regenerate with UPDATE_CONSOLE_RESPONSE_GOLDEN=1")
}
