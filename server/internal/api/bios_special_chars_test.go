package api

import (
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// neoGeoPocketBiosName is a real No-Intro-style BIOS filename with spaces,
// brackets, parentheses and a comma — the case from #1208.
const neoGeoPocketBiosName = "[BIOS] SNK NeoGeo Pocket (Japan, Europe) (En).bin"

// TestGetBiosFile_SpecialCharNameNotValidationError reproduces #1208: a BIOS
// filename containing a comma (and brackets/parens/spaces) must not be
// rejected by the path-param character whitelist with a 422. When the file
// is absent the correct answer is a plain 404, same as any other missing
// BIOS, not "validation failed".
func TestGetBiosFile_SpecialCharNameNotValidationError(t *testing.T) {
	_, database, router := setupBiosTestEnv(t)
	token := createBiosTestUser(t, database, "user")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios/"+url.PathEscape(neoGeoPocketBiosName), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.NotEqual(t, http.StatusUnprocessableEntity, w.Code,
		"special-character BIOS name must not fail path validation (#1208)")
	assert.Equal(t, http.StatusNotFound, w.Code,
		"absent BIOS should return a plain 404")
}

// TestGetBiosFile_SpecialCharNameServesWhenPresent confirms the end-to-end
// path: a present BIOS file with special characters in its name is served.
func TestGetBiosFile_SpecialCharNameServesWhenPresent(t *testing.T) {
	store, database, router := setupBiosTestEnv(t)
	token := createBiosTestUser(t, database, "user")

	require.NoError(t, os.WriteFile(filepath.Join(store.BiosDir, neoGeoPocketBiosName), []byte("ngp bios"), 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/bios/"+url.PathEscape(neoGeoPocketBiosName), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "ngp bios", w.Body.String())
}
