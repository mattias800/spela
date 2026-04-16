package db

import "time"

// System event metadata variants. The wire format for each is a flat JSON
// object — these structs exist to make producers type-safe at the call site.
// Each struct's JSON tags match the historical map[string]any keys exactly
// so the on-the-wire shape is unchanged.

// LoginFailedMetadata is the metadata shape for the SystemEventLoginFailed event.
type LoginFailedMetadata struct {
	FailedCount int `json:"failedCount"`
}

// AccountLockedMetadata is the metadata shape for the SystemEventAccountLocked event.
type AccountLockedMetadata struct {
	FailedCount int       `json:"failedCount"`
	LockedUntil time.Time `json:"lockedUntil"`
}

// StaleTokenVersionMetadata is the metadata shape for the SystemEventStaleTokenVersion event.
type StaleTokenVersionMetadata struct {
	TokenVersion   int `json:"tokenVersion"`
	CurrentVersion int `json:"currentVersion"`
}

// RACircuitBreakerTrippedMetadata is the metadata shape for the
// SystemEventRACircuitBreakerTripped event.
type RACircuitBreakerTrippedMetadata struct {
	ConsecutiveFailures int    `json:"consecutiveFailures"`
	LastError           string `json:"lastError"`
}

// ScraperRepeatedErrorsMetadata is the metadata shape for the
// SystemEventScraperRepeatedErrors event.
type ScraperRepeatedErrorsMetadata struct {
	ConsecutiveFailures int    `json:"consecutiveFailures"`
	Error               string `json:"error"`
	GameTitle           string `json:"gameTitle"`
}

// ROMFileMissingMetadata is the metadata shape for the SystemEventROMFileMissing event.
type ROMFileMissingMetadata struct {
	GameID       uint   `json:"gameId"`
	GameTitle    string `json:"gameTitle"`
	ExpectedPath string `json:"expectedPath"`
}

// APICredentialsInvalidMetadata is the metadata shape for the
// SystemEventAPICredentialsInvalid event.
type APICredentialsInvalidMetadata struct {
	Service string `json:"service"`
	Error   string `json:"error"`
}

// EmulatorJSLoadFailedMetadata is the metadata shape for the
// SystemEventEmulatorJSLoadFailed event. GameID and Core are optional and
// omitted from the serialized payload when empty to preserve the historical
// "only include set fields" behavior.
type EmulatorJSLoadFailedMetadata struct {
	Error  string `json:"error"`
	GameID string `json:"gameId,omitempty"`
	Core   string `json:"core,omitempty"`
}
