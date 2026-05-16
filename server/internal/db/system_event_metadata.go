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
	GameID string `json:"gameId"`
	Core   string `json:"core"`
}

// BIOSDownloadFailedMetadata is the metadata shape for the
// SystemEventBIOSDownloadFailed event. Emitted by the BIOS auto-downloader
// for each registry entry that fails to fetch or extract.
type BIOSDownloadFailedMetadata struct {
	FileName  string `json:"fileName"`
	ConsoleID string `json:"consoleId"`
	URL       string `json:"url"`
	Error     string `json:"error"`
}

// CoreUpdatedMetadata is the metadata shape for SystemEventCoreUpdated.
// Emitted whenever the server observes a new sha256 for a core binary —
// admin-triggered refresh, or a buildbot poll that landed a new nightly.
// Trigger discriminates the two so the audit trail tells the story.
type CoreUpdatedMetadata struct {
	Core         string `json:"core"`
	PlatformArch string `json:"platformArch,omitempty"` // empty for legacy admin-refresh that doesn't carry one
	OldSha256    string `json:"oldSha256"`
	NewSha256    string `json:"newSha256"`
	SizeBytes    int64  `json:"sizeBytes"`
	SourceURL    string `json:"sourceUrl"`
	Trigger      string `json:"trigger"` // "admin_refresh" | "buildbot_poll"
}

// CoreUpdateFailedMetadata is the metadata shape for
// SystemEventCoreUpdateFailed. Emitted by the buildbot poller when a
// fetch or unzip fails — analogous to BIOSDownloadFailedMetadata. Lets
// admins notice when upstream goes down or shifts the URL layout
// without tailing container logs. See #1190.
type CoreUpdateFailedMetadata struct {
	Core         string `json:"core"`
	PlatformArch string `json:"platformArch"`
	URL          string `json:"url"`
	Error        string `json:"error"`
}
