package api

import "github.com/spela/server/internal/auth"

// secretSettingKeys are settings that should be masked in GET responses and
// encrypted at rest (#1318).
var secretSettingKeys = map[string]bool{
	"igdb_client_secret":  true,
	"steamgriddb_api_key": true,
	"ra_api_key":          true,
}

// serverSettingsKey is the AES key used to encrypt/decrypt secret server
// settings at rest. It is process-global (the same key for the lifetime of
// the server) and set once from NewRouter via setServerSettingsKey. Stored as
// a package var rather than threaded through the ~7 free functions that read
// these settings.
var serverSettingsKey []byte

// setServerSettingsKey records the AES key used for secret settings. Called
// once during router construction.
func setServerSettingsKey(key []byte) { serverSettingsKey = key }

// encryptSecretSetting encrypts a secret setting value for storage. Empty
// values and a missing key pass through unchanged.
func encryptSecretSetting(value string) (string, error) {
	if value == "" || len(serverSettingsKey) == 0 {
		return value, nil
	}
	return auth.Encrypt(value, serverSettingsKey)
}

// decryptSecretSetting decrypts a stored secret setting value. Legacy
// plaintext (no "enc:" prefix) is returned as-is by auth.Decrypt; on any
// decryption error we fall back to the raw value so a key mishap degrades to
// the prior behaviour rather than silently disabling the integration.
func decryptSecretSetting(value string) string {
	if value == "" || len(serverSettingsKey) == 0 {
		return value
	}
	plain, err := auth.Decrypt(value, serverSettingsKey)
	if err != nil {
		return value
	}
	return plain
}

// secretMaskPlaceholder is the masked value returned for secret settings in GET responses.
const secretMaskPlaceholder = "********"

// allowedSettingKeys is the allowlist of setting keys that may be written via the admin API.
var allowedSettingKeys = map[string]bool{
	"registration_enabled":     true,
	"igdb_client_id":           true,
	"igdb_client_secret":       true,
	"bios_auto_download":       true,
	"steamgriddb_api_key":      true,
	"default_region":           true,
	"hide_pre_release_default": true,
	"ra_api_key":               true,
}
