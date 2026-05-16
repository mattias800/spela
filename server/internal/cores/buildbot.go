// Package cores provides the libretro-buildbot polling worker that keeps
// the server's per-platform core fingerprints fresh. See #1190.
//
// The package's responsibilities split cleanly:
//
//   - buildbot.go: URL composition. Mirrors player/.../util/BuildbotUrl.kt
//     so the server and the client agree on where to fetch each (core,
//     platform, arch) tuple from. Pure functions, no side effects.
//
//   - poller.go: the worker itself. Iterates buildbot-default cores on
//     a schedule, fetches each platform binary, compares sha256 against
//     the CorePlatformBinary row, writes through on change, and emits
//     audit events.
//
// New cores that follow the standard libretro packaging convention need
// no code changes here — adding a Core row to the seed is enough. The
// only file that needs touching is androidNoSuffixCores when a core
// ships an Android nightly without the `_android` suffix in its asset
// name (today: just azahar).
package cores

import (
	"fmt"
	"strings"
)

const buildbotBase = "https://buildbot.libretro.com/nightly"

// androidNoSuffixCores names libretro cores whose Android nightly is
// packaged without the `_android` suffix. Buildbot publishes most Android
// cores as `<name>_libretro_android.so` inside `<name>_libretro_android.so.zip`
// but a handful (azahar) ship as `<name>_libretro.so` inside
// `<name>_libretro.so.zip`. The convention is set upstream — when adding
// a new entry here, HEAD both URL shapes against buildbot to confirm.
//
// Kept in sync with the player's commonMain ANDROID_NO_SUFFIX_CORES set
// in BuildbotUrl.kt.
var androidNoSuffixCores = map[string]struct{}{
	"azahar": {},
}

// PlatformArch is the player's runtime platform tag concatenated with
// its CPU arch, e.g. "linux-x86_64", "macos-arm64", "android-arm64-v8a".
// Used as the primary key column on CorePlatformBinary and as the
// `?platform=` query parameter on the manifest endpoint.
type PlatformArch struct {
	Platform string // "linux" | "macos" | "windows" | "android"
	Arch     string // arch-as-the-player-reports-it
}

// String returns the canonical "<platform>-<arch>" form persisted to the
// CorePlatformBinary.PlatformArch column.
func (p PlatformArch) String() string {
	return p.Platform + "-" + p.Arch
}

// ParsePlatformArch splits a "<platform>-<arch>" tag back into its
// components. Returns an error when the tag is malformed; production
// callers should treat that as a bad-request signal rather than a
// best-effort parse.
func ParsePlatformArch(s string) (PlatformArch, error) {
	idx := strings.Index(s, "-")
	if idx <= 0 || idx == len(s)-1 {
		return PlatformArch{}, fmt.Errorf("cores: malformed platform tag %q (want <platform>-<arch>)", s)
	}
	return PlatformArch{Platform: s[:idx], Arch: s[idx+1:]}, nil
}

// DefaultPollMatrix is the canonical set of (platform, arch) tuples the
// server polls buildbot for. It mirrors the platforms Spela's player
// ships on, with the carve-out from #1188 that Android only targets
// arm64-v8a (the other two ABIs aren't built by buildbot for any of the
// HW-render-heavy cores we care about).
var DefaultPollMatrix = []PlatformArch{
	{Platform: "linux", Arch: "x86_64"},
	{Platform: "linux", Arch: "aarch64"},
	{Platform: "macos", Arch: "arm64"},
	{Platform: "macos", Arch: "x86_64"},
	{Platform: "windows", Arch: "x86_64"},
	{Platform: "android", Arch: "arm64-v8a"},
}

// MatrixForPlatforms filters DefaultPollMatrix down to the tuples whose
// platform appears in the comma-separated list (matching Core.Platforms).
// Returns an empty slice when the input is empty or has no overlap with
// the matrix.
func MatrixForPlatforms(platformsCSV string) []PlatformArch {
	if platformsCSV == "" {
		return nil
	}
	wanted := make(map[string]struct{})
	for _, p := range strings.Split(platformsCSV, ",") {
		p = strings.TrimSpace(p)
		if p != "" {
			wanted[p] = struct{}{}
		}
	}
	out := make([]PlatformArch, 0, len(DefaultPollMatrix))
	for _, m := range DefaultPollMatrix {
		if _, ok := wanted[m.Platform]; ok {
			out = append(out, m)
		}
	}
	return out
}

// BuildbotURL returns the libretro buildbot download URL for a given
// (coreName, platform, arch). Mirrors the player's buildbotCoreUrl
// helper — when one moves, the other must too.
//
// URL shapes:
//
//	android: /nightly/android/latest/{arch}/{name}_libretro_android.so.zip
//	         (or {name}_libretro.so.zip when coreName is in androidNoSuffixCores)
//	macos:   /nightly/apple/osx/{arch}/latest/{name}_libretro.dylib.zip
//	linux:   /nightly/linux/{buildbotArch}/latest/{name}_libretro.so.zip
//	         (arm64 → aarch64; otherwise pass-through)
//	windows: /nightly/windows/{arch}/latest/{name}_libretro.dll.zip
func BuildbotURL(coreName, platform, arch string) string {
	switch platform {
	case "android":
		asset := fmt.Sprintf("%s_libretro_android.so.zip", coreName)
		if _, ok := androidNoSuffixCores[coreName]; ok {
			asset = fmt.Sprintf("%s_libretro.so.zip", coreName)
		}
		return fmt.Sprintf("%s/android/latest/%s/%s", buildbotBase, arch, asset)
	case "macos":
		return fmt.Sprintf("%s/apple/osx/%s/latest/%s_libretro.dylib.zip", buildbotBase, arch, coreName)
	case "windows":
		return fmt.Sprintf("%s/windows/%s/latest/%s_libretro.dll.zip", buildbotBase, arch, coreName)
	default: // linux + anything else falls back to linux conventions
		buildbotArch := arch
		if arch == "arm64" {
			buildbotArch = "aarch64"
		}
		return fmt.Sprintf("%s/linux/%s/latest/%s_libretro.so.zip", buildbotBase, buildbotArch, coreName)
	}
}

// CoreBinaryFilename returns the filename of the extracted core binary
// once unzipped from a buildbot download. Mirrors the player's
// coreFileName — same naming convention has to live on both ends so
// the server stores it under the name the client expects.
func CoreBinaryFilename(coreName, platform string) string {
	switch platform {
	case "android":
		if _, ok := androidNoSuffixCores[coreName]; ok {
			return coreName + "_libretro.so"
		}
		return coreName + "_libretro_android.so"
	case "macos":
		return coreName + "_libretro.dylib"
	case "windows":
		return coreName + "_libretro.dll"
	default:
		return coreName + "_libretro.so"
	}
}
