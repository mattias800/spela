package api

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/danielgtaylor/huma/v2/adapters/humago"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// deadlineRecorder is an http.ResponseWriter that records SetWriteDeadline
// calls and captures the response body. Used to assert that file-download
// streaming clears the per-connection write deadline — without that, the
// server's global http.Server WriteTimeout cuts multi-GB game/disc downloads
// mid-stream (a 4.5GB ISO died at ~120s / ~3.5GB every time).
type deadlineRecorder struct {
	header        http.Header
	body          []byte
	writeDeadline time.Time
	deadlineSet   bool
}

func (d *deadlineRecorder) Header() http.Header {
	if d.header == nil {
		d.header = http.Header{}
	}
	return d.header
}

func (d *deadlineRecorder) Write(p []byte) (int, error) {
	d.body = append(d.body, p...)
	return len(p), nil
}

func (d *deadlineRecorder) WriteHeader(int) {}

func (d *deadlineRecorder) SetWriteDeadline(t time.Time) error {
	d.writeDeadline = t
	d.deadlineSet = true
	return nil
}

func TestStreamFileFromDiskClearsWriteDeadline(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "game.iso")
	content := []byte("pretend this is a multi-GB ISO")
	require.NoError(t, os.WriteFile(path, content, 0o644))

	rec := &deadlineRecorder{}
	req := httptest.NewRequest(http.MethodGet, "/api/games/1/download", nil)
	ctx := humago.NewContext(
		&huma.Operation{Method: http.MethodGet, Path: "/api/games/{id}/download"},
		req, rec,
	)

	streamFileFromDisk(path, "game.iso", "application/octet-stream").Body(ctx)

	assert.True(t, rec.deadlineSet,
		"download streaming must clear the write deadline so large transfers aren't cut off by the global WriteTimeout")
	assert.True(t, rec.writeDeadline.IsZero(),
		"write deadline should be cleared (zero time), got %v", rec.writeDeadline)
	assert.Equal(t, content, rec.body, "the full file should stream through")
}
