package api

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

// Defaults to 256 MB so realistic uncompressed Dolphin GameCube saves fit
// (typical ~80–100 MB). Below that, the server returned 413 to every
// large-state save (#805).
func TestMaxSaveUploadBytes_Default(t *testing.T) {
	t.Setenv("SPELA_MAX_SAVE_UPLOAD_MB", "")
	got := maxSaveUploadBytes()
	const expected = int64(256) << 20
	assert.Equal(t, expected, got, "default cap should be 256 MB")
}

func TestMaxSaveUploadBytes_EnvOverride(t *testing.T) {
	t.Setenv("SPELA_MAX_SAVE_UPLOAD_MB", "512")
	got := maxSaveUploadBytes()
	const expected = int64(512) << 20
	assert.Equal(t, expected, got, "env override should set cap to 512 MB")
}

// Garbage values fall back to the default rather than crashing the server
// or accidentally setting a tiny cap.
func TestMaxSaveUploadBytes_InvalidEnvFallsBackToDefault(t *testing.T) {
	t.Setenv("SPELA_MAX_SAVE_UPLOAD_MB", "not-a-number")
	got := maxSaveUploadBytes()
	const expected = int64(256) << 20
	assert.Equal(t, expected, got, "invalid env value should fall back to default")
}

// Zero / negative values fall back to the default — protects against an
// operator setting "0" and silently disabling all uploads.
func TestMaxSaveUploadBytes_NonPositiveEnvFallsBackToDefault(t *testing.T) {
	for _, v := range []string{"0", "-1"} {
		t.Run("env="+v, func(t *testing.T) {
			t.Setenv("SPELA_MAX_SAVE_UPLOAD_MB", v)
			got := maxSaveUploadBytes()
			const expected = int64(256) << 20
			assert.Equal(t, expected, got, "non-positive env value should fall back to default")
		})
	}
}
