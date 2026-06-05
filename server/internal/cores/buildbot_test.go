package cores

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// Mirrors player/shared/src/commonTest/.../util/BuildbotUrlTest.kt — the
// player and the server must agree on every URL the poller writes
// through to a CorePlatformBinary row. If a player ever fetches from a
// URL the server didn't poll, staleness goes wrong silently.

func TestBuildbotURL_StandardConvention(t *testing.T) {
	cases := []struct {
		name     string
		core     string
		platform string
		arch     string
		want     string
	}{
		{"macos arm64", "nestopia", "macos", "arm64",
			"https://buildbot.libretro.com/nightly/apple/osx/arm64/latest/nestopia_libretro.dylib.zip"},
		{"macos x86_64", "snes9x", "macos", "x86_64",
			"https://buildbot.libretro.com/nightly/apple/osx/x86_64/latest/snes9x_libretro.dylib.zip"},
		{"android arm64-v8a", "nestopia", "android", "arm64-v8a",
			"https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/nestopia_libretro_android.so.zip"},
		{"linux x86_64", "mupen64plus_next", "linux", "x86_64",
			"https://buildbot.libretro.com/nightly/linux/x86_64/latest/mupen64plus_next_libretro.so.zip"},
		{"linux arm64 → aarch64", "nestopia", "linux", "arm64",
			"https://buildbot.libretro.com/nightly/linux/aarch64/latest/nestopia_libretro.so.zip"},
		{"windows x86_64", "mgba", "windows", "x86_64",
			"https://buildbot.libretro.com/nightly/windows/x86_64/latest/mgba_libretro.dll.zip"},
		{"windows x86_64 parallel_n64", "parallel_n64", "windows", "x86_64",
			"https://buildbot.libretro.com/nightly/windows/x86_64/latest/parallel_n64_libretro.dll.zip"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			assert.Equal(t, c.want, BuildbotURL(c.core, c.platform, c.arch))
		})
	}
}

// Azahar deviates: its Android nightly is shipped as `azahar_libretro.so.zip`
// (no _android suffix). Both the URL and the unzipped filename follow the
// same exception. See #1188 and ANDROID_NO_SUFFIX_CORES.
func TestBuildbotURL_AzaharAndroidNoSuffix(t *testing.T) {
	assert.Equal(t,
		"https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/azahar_libretro.so.zip",
		BuildbotURL("azahar", "android", "arm64-v8a"),
	)
	assert.Equal(t, "azahar_libretro.so", CoreBinaryFilename("azahar", "android"))
}

func TestBuildbotURL_AzaharNonAndroidUnchanged(t *testing.T) {
	// Azahar's macOS / Linux / Windows nightlies follow the standard
	// convention. The deviation is Android-only.
	assert.Equal(t,
		"https://buildbot.libretro.com/nightly/apple/osx/arm64/latest/azahar_libretro.dylib.zip",
		BuildbotURL("azahar", "macos", "arm64"),
	)
	assert.Equal(t, "azahar_libretro.dylib", CoreBinaryFilename("azahar", "macos"))
}

func TestCoreBinaryFilename(t *testing.T) {
	assert.Equal(t, "nestopia_libretro.dylib", CoreBinaryFilename("nestopia", "macos"))
	assert.Equal(t, "nestopia_libretro_android.so", CoreBinaryFilename("nestopia", "android"))
	assert.Equal(t, "nestopia_libretro.so", CoreBinaryFilename("nestopia", "linux"))
	assert.Equal(t, "nestopia_libretro.dll", CoreBinaryFilename("nestopia", "windows"))
}

func TestPlatformArch_RoundTrip(t *testing.T) {
	cases := []string{
		"linux-x86_64",
		"linux-aarch64",
		"macos-arm64",
		"macos-x86_64",
		"windows-x86_64",
		"android-arm64-v8a", // multi-dash arch — parser keeps everything after the first dash
	}
	for _, s := range cases {
		t.Run(s, func(t *testing.T) {
			p, err := ParsePlatformArch(s)
			require.NoError(t, err)
			assert.Equal(t, s, p.String())
		})
	}
}

func TestPlatformArch_ParseRejectsMalformed(t *testing.T) {
	for _, bad := range []string{"", "linux", "-x86_64", "linux-"} {
		t.Run("bad/"+bad, func(t *testing.T) {
			_, err := ParsePlatformArch(bad)
			assert.Error(t, err)
		})
	}
}

func TestMatrixForPlatforms(t *testing.T) {
	t.Run("empty input → empty matrix", func(t *testing.T) {
		assert.Empty(t, MatrixForPlatforms(""))
	})

	t.Run("subset → only matching tuples", func(t *testing.T) {
		got := MatrixForPlatforms("linux,macos")
		// linux × {x86_64, aarch64} + macos × {arm64, x86_64} = 4 tuples
		assert.Len(t, got, 4)
		for _, p := range got {
			assert.Contains(t, []string{"linux", "macos"}, p.Platform)
		}
	})

	t.Run("full seed shape", func(t *testing.T) {
		got := MatrixForPlatforms("windows,linux,macos,android")
		assert.Equal(t, len(DefaultPollMatrix), len(got))
	})

	t.Run("unknown platform is dropped, not errored", func(t *testing.T) {
		got := MatrixForPlatforms("linux,emacs")
		// emacs is filtered out, linux's two arches survive
		assert.Len(t, got, 2)
		for _, p := range got {
			assert.Equal(t, "linux", p.Platform)
		}
	})
}
