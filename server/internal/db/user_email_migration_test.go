package db

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func openUserEmailMigrationTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	return database
}

func TestMigrateDropUserEmail_RemovesLegacyColumnAndIndex(t *testing.T) {
	database := openUserEmailMigrationTestDB(t)
	require.NoError(t, database.Exec(`
		CREATE TABLE users (
			id integer PRIMARY KEY AUTOINCREMENT,
			created_at datetime,
			updated_at datetime,
			deleted_at datetime,
			username text NOT NULL,
			email text NOT NULL,
			password_hash text NOT NULL,
			role text DEFAULT 'user'
		)
	`).Error)
	require.NoError(t, database.Exec("CREATE UNIQUE INDEX idx_users_username ON users(username)").Error)
	require.NoError(t, database.Exec("CREATE UNIQUE INDEX idx_users_email ON users(email)").Error)
	require.NoError(t, database.Exec("CREATE INDEX legacy_users_email_lookup ON users(email)").Error)
	require.NoError(t, database.Exec("CREATE INDEX legacy_email_named_username_lookup ON users(username)").Error)
	require.NoError(t, database.Exec(`
		INSERT INTO users (username, email, password_hash, role)
		VALUES ('owner', 'owner@example.com', 'hash', 'owner')
	`).Error)

	hasEmail, err := sqliteTableHasColumn(database, "users", "email")
	require.NoError(t, err)
	require.True(t, hasEmail)

	require.NoError(t, MigrateDropUserEmail(database))

	hasEmail, err = sqliteTableHasColumn(database, "users", "email")
	require.NoError(t, err)
	assert.False(t, hasEmail)

	var emailIndexCount int64
	require.NoError(t, database.Raw(`
		SELECT count(*) FROM sqlite_master
		WHERE type = 'index' AND tbl_name = 'users' AND lower(sql) LIKE '%email%'
	`).Scan(&emailIndexCount).Error)
	assert.Equal(t, int64(1), emailIndexCount)

	var unrelatedIndexCount int64
	require.NoError(t, database.Raw(`
		SELECT count(*) FROM sqlite_master
		WHERE type = 'index' AND tbl_name = 'users' AND name = 'legacy_email_named_username_lookup'
	`).Scan(&unrelatedIndexCount).Error)
	assert.Equal(t, int64(1), unrelatedIndexCount)

	var user User
	require.NoError(t, database.First(&user).Error)
	assert.Equal(t, "owner", user.Username)
	assert.Equal(t, RoleOwner, user.Role)

	require.NoError(t, MigrateDropUserEmail(database))
}

func TestMigrateDropUserEmail_FreshSchemaNoop(t *testing.T) {
	database := openUserEmailMigrationTestDB(t)
	require.NoError(t, database.AutoMigrate(&User{}))

	require.NoError(t, MigrateDropUserEmail(database))

	hasEmail, err := sqliteTableHasColumn(database, "users", "email")
	require.NoError(t, err)
	assert.False(t, hasEmail)
}
