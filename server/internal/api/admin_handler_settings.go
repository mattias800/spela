package api

// secretSettingKeys are settings that should be masked in GET responses.
var secretSettingKeys = map[string]bool{
	"igdb_client_secret":  true,
	"steamgriddb_api_key": true,
	"ra_api_key":          true,
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
