package db

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func TestGameAutoMigrateAddsTitleRootColumnsAndIndex(t *testing.T) {
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&Console{}, &Game{}))

	migrator := database.Migrator()
	for _, column := range []string{
		"igdb_parent_game_id",
		"igdb_version_parent_id",
		"igdb_category",
		"title_root_igdb_id",
	} {
		assert.True(t, migrator.HasColumn(&Game{}, column), "missing games.%s", column)
	}
	assert.True(t, migrator.HasIndex(&Game{}, "idx_game_title_root_igdb"))
}
