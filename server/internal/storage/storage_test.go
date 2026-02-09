package storage

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewStorage(t *testing.T) {
	dir := t.TempDir()
	saveDir := filepath.Join(dir, "saves")
	coreDir := filepath.Join(dir, "cores")

	store, err := NewStorage(saveDir, coreDir, filepath.Join(dir, "images"))
	require.NoError(t, err)
	assert.DirExists(t, saveDir)
	assert.DirExists(t, coreDir)
	assert.Equal(t, saveDir, store.SaveDir)
	assert.Equal(t, coreDir, store.CoreDir)
}

func TestSaveStatePath(t *testing.T) {
	store := &Storage{SaveDir: "/data/saves"}
	path := store.SaveStatePath(1, 42, "save.sav")
	assert.Equal(t, "/data/saves/user_1/game_42/save.sav", path)
}

func TestWriteAndReadSave(t *testing.T) {
	dir := t.TempDir()
	store, err := NewStorage(filepath.Join(dir, "saves"), filepath.Join(dir, "cores"), filepath.Join(dir, "images"))
	require.NoError(t, err)

	data := strings.NewReader("save state data here")
	n, err := store.WriteSave(1, 1, "test.sav", data)
	require.NoError(t, err)
	assert.Equal(t, int64(20), n)

	path := store.SaveStatePath(1, 1, "test.sav")
	f, err := store.ReadSave(path)
	require.NoError(t, err)
	defer f.Close()

	content := make([]byte, 100)
	read, _ := f.Read(content)
	assert.Equal(t, "save state data here", string(content[:read]))
}

func TestDeleteSave(t *testing.T) {
	dir := t.TempDir()
	store, err := NewStorage(filepath.Join(dir, "saves"), filepath.Join(dir, "cores"), filepath.Join(dir, "images"))
	require.NoError(t, err)

	data := strings.NewReader("save data")
	_, err = store.WriteSave(1, 1, "delete.sav", data)
	require.NoError(t, err)

	path := store.SaveStatePath(1, 1, "delete.sav")
	assert.FileExists(t, path)

	err = store.DeleteSave(path)
	require.NoError(t, err)
	assert.NoFileExists(t, path)
}

func TestDeleteSave_NonExistent(t *testing.T) {
	store := &Storage{SaveDir: t.TempDir()}
	err := store.DeleteSave("/nonexistent/path.sav")
	assert.NoError(t, err)
}

// BUG: SaveStatePath does not sanitize the filename parameter.
// A filename containing path traversal sequences (../) can write
// files outside the intended save directory.
func TestSaveStatePath_PathTraversal(t *testing.T) {
	store := &Storage{SaveDir: "/data/saves"}

	// A malicious filename with path traversal
	maliciousFilename := "../../../etc/evil.txt"
	path := store.SaveStatePath(1, 42, maliciousFilename)

	// The path should NOT escape the save directory.
	// Currently it produces: /data/saves/user_1/game_42/../../../etc/evil.txt
	// which resolves to /data/etc/evil.txt (outside the save dir).
	absPath, _ := filepath.Abs(path)
	expectedBase, _ := filepath.Abs(store.SaveDir)

	assert.True(t,
		strings.HasPrefix(absPath, expectedBase+string(filepath.Separator)),
		"path %q should be under %q but resolves to %q - path traversal vulnerability",
		path, expectedBase, absPath,
	)
}

// BUG: WriteSave passes filename directly to SaveStatePath without sanitization.
// This allows writing files anywhere on the filesystem.
func TestWriteSave_PathTraversal(t *testing.T) {
	dir := t.TempDir()
	store, err := NewStorage(filepath.Join(dir, "saves"), filepath.Join(dir, "cores"), filepath.Join(dir, "images"))
	require.NoError(t, err)

	// Create a target file outside the save directory
	evilTarget := filepath.Join(dir, "evil.txt")

	// The malicious filename traverses up to write outside the save dir
	maliciousFilename := "../../evil.txt"
	data := strings.NewReader("malicious content")

	_, err = store.WriteSave(1, 1, maliciousFilename, data)
	// Either the write should fail, or the file should NOT exist outside save dir
	if err == nil {
		// If it didn't error, check that the file wasn't written outside save dir
		_, statErr := os.Stat(evilTarget)
		assert.True(t, os.IsNotExist(statErr),
			"file was written outside save directory via path traversal - security vulnerability")
	}
}

func TestValidateROMPath(t *testing.T) {
	dir := t.TempDir()
	gameDir := filepath.Join(dir, "games")
	require.NoError(t, os.MkdirAll(gameDir, 0755))

	romPath := filepath.Join(gameDir, "test.nes")
	require.NoError(t, os.WriteFile(romPath, []byte("rom"), 0644))

	tests := []struct {
		name     string
		path     string
		allowed  []string
		expected bool
	}{
		{"valid path in allowed dir", romPath, []string{gameDir}, true},
		{"path outside allowed dir", "/etc/passwd", []string{gameDir}, false},
		{"path traversal attempt", filepath.Join(gameDir, "..", "etc", "passwd"), []string{gameDir}, false},
		{"empty allowed dirs", romPath, []string{}, false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := ValidateROMPath(tt.path, tt.allowed)
			assert.Equal(t, tt.expected, result)
		})
	}
}
