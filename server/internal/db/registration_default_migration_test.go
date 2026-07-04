package db

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func openRegistrationTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&User{}, &ServerSetting{}))
	return database
}

func registrationSetting(t *testing.T, database *gorm.DB) (string, bool) {
	t.Helper()
	var s ServerSetting
	err := database.Where("key = ?", "registration_enabled").First(&s).Error
	if err == gorm.ErrRecordNotFound {
		return "", false
	}
	require.NoError(t, err)
	return s.Value, true
}

// Fresh install (no users): the flag stays absent so registration is
// closed-by-default (#1319).
func TestMigratePreserveOpenRegistration_FreshInstallLeavesClosed(t *testing.T) {
	database := openRegistrationTestDB(t)
	require.NoError(t, MigratePreserveOpenRegistration(database))
	_, exists := registrationSetting(t, database)
	assert.False(t, exists, "fresh install must not seed registration_enabled")
}

// Existing install (users present, flag never configured): preserve the prior
// open-registration behaviour by seeding the flag to "true".
func TestMigratePreserveOpenRegistration_ExistingInstallStaysOpen(t *testing.T) {
	database := openRegistrationTestDB(t)
	require.NoError(t, database.Create(&User{Username: "owner", PasswordHash: "x"}).Error)

	require.NoError(t, MigratePreserveOpenRegistration(database))

	val, exists := registrationSetting(t, database)
	require.True(t, exists, "existing install must seed registration_enabled")
	assert.Equal(t, "true", val)
}

// An operator's explicit choice is respected and never overwritten; the
// migration is idempotent.
func TestMigratePreserveOpenRegistration_RespectsExplicitSetting(t *testing.T) {
	database := openRegistrationTestDB(t)
	require.NoError(t, database.Create(&User{Username: "owner", PasswordHash: "x"}).Error)
	require.NoError(t, database.Create(&ServerSetting{Key: "registration_enabled", Value: "false"}).Error)

	require.NoError(t, MigratePreserveOpenRegistration(database))
	require.NoError(t, MigratePreserveOpenRegistration(database)) // idempotent

	val, _ := registrationSetting(t, database)
	assert.Equal(t, "false", val, "explicit operator setting must be preserved")
}
