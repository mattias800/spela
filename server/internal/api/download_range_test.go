package api

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestParseByteRange(t *testing.T) {
	const size = int64(1000)
	tests := []struct {
		header             string
		wantStart, wantEnd int64
		wantOK             bool
	}{
		{"", 0, 0, false},
		{"bytes=0-", 0, 999, true},
		{"bytes=500-", 500, 999, true}, // the resume case
		{"bytes=100-199", 100, 199, true},
		{"bytes=900-99999", 900, 999, true}, // end clamped to size-1
		{"bytes=-100", 900, 999, true},      // suffix: last 100 bytes
		{"bytes=-2000", 0, 999, true},       // suffix larger than file → whole file
		{"bytes=abc-", 0, 0, false},
		{"bytes=100-50", 0, 0, false},        // end < start
		{"bytes=0-100,200-300", 0, 0, false}, // multi-range unsupported → serve full
		{"items=0-", 0, 0, false},            // wrong unit
		{"bytes=-", 0, 0, false},
	}
	for _, tc := range tests {
		t.Run(tc.header, func(t *testing.T) {
			start, end, ok := parseByteRange(tc.header, size)
			assert.Equal(t, tc.wantOK, ok)
			if tc.wantOK {
				assert.Equal(t, tc.wantStart, start, "start")
				assert.Equal(t, tc.wantEnd, end, "end")
			}
		})
	}
}

// TestDownloadGame_RangeRequests verifies the game-download endpoint supports
// HTTP Range so an interrupted download can resume (#1296): a Range request
// returns 206 with just the tail, a stale If-Range validator falls back to a
// full 200 (so the client restarts cleanly), and an offset past EOF is 416.
func TestDownloadGame_RangeRequests(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	psxDir := filepath.Join(cfg.GameDirs[0], "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))
	content := []byte("0123456789abcdefghijklmnopqrstuvwxyz-RESUME-ME-FROM-THE-MIDDLE!!")
	size := len(content)
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "game.chd"), content, 0644))

	var psxConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "PSX").First(&psxConsole).Error)
	game := db.Game{
		ConsoleID: psxConsole.ID,
		Title:     "Range Test",
		FileName:  "game.chd",
		FilePath:  filepath.Join("psx", "game.chd"),
		FileSize:  int64(size),
	}
	require.NoError(t, database.Create(&game).Error)
	url := fmt.Sprintf("/api/games/%d/download", game.ID)

	get := func(headers map[string]string) *httptest.ResponseRecorder {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", url, nil)
		req.Header.Set("Authorization", "Bearer "+token)
		for k, v := range headers {
			req.Header.Set(k, v)
		}
		router.ServeHTTP(w, req)
		return w
	}

	// Full download advertises range support + a strong validator.
	full := get(nil)
	require.Equal(t, http.StatusOK, full.Code)
	assert.Equal(t, "bytes", full.Header().Get("Accept-Ranges"))
	etag := full.Header().Get("ETag")
	assert.NotEmpty(t, etag, "a validator (ETag) is required for safe resume")
	assert.Equal(t, content, full.Body.Bytes())

	// Resume: Range from an offset → 206 with only the tail.
	const offset = 36
	part := get(map[string]string{"Range": fmt.Sprintf("bytes=%d-", offset)})
	assert.Equal(t, http.StatusPartialContent, part.Code)
	assert.Equal(t, fmt.Sprintf("bytes %d-%d/%d", offset, size-1, size), part.Header().Get("Content-Range"))
	assert.Equal(t, strconv.Itoa(size-offset), part.Header().Get("Content-Length"))
	assert.Equal(t, content[offset:], part.Body.Bytes())

	// Resume with the matching validator still serves the partial.
	ok := get(map[string]string{"Range": fmt.Sprintf("bytes=%d-", offset), "If-Range": etag})
	assert.Equal(t, http.StatusPartialContent, ok.Code)
	assert.Equal(t, content[offset:], ok.Body.Bytes())

	// File changed under the client (stale validator) → ignore the range and
	// serve the full file (200) so the client restarts rather than splices.
	stale := get(map[string]string{"Range": fmt.Sprintf("bytes=%d-", offset), "If-Range": `"stale-0-0"`})
	assert.Equal(t, http.StatusOK, stale.Code)
	assert.Equal(t, content, stale.Body.Bytes())

	// Offset at/past EOF → 416 Range Not Satisfiable.
	past := get(map[string]string{"Range": fmt.Sprintf("bytes=%d-", size)})
	assert.Equal(t, http.StatusRequestedRangeNotSatisfiable, past.Code)
}
