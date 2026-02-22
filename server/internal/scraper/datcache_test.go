package scraper

import (
	"net/http"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestDATCache_GetIndex_FromDisk(t *testing.T) {
	dir := t.TempDir()

	// Write a sample DAT file to disk with the correct system name
	systemName := AbbreviationToLibRetro["NES"]
	datPath := filepath.Join(dir, systemName+".dat")
	datContent := `game (
	name "Test Game (USA)"
	rom ( name "Test Game (USA).nes" size 1024 crc AABBCCDD md5 abc sha1 def )
)
`
	require.NoError(t, os.WriteFile(datPath, []byte(datContent), 0o644))

	cache := NewDATCache(dir, &http.Client{})
	idx, err := cache.GetIndex("NES")
	require.NoError(t, err)
	require.NotNil(t, idx)

	entry, ok := idx.LookupCRC("AABBCCDD")
	assert.True(t, ok)
	assert.Equal(t, "Test Game (USA)", entry.GameName)
	assert.Equal(t, "Test Game (USA).nes", entry.ROMName)
}

func TestDATCache_GetIndex_DiscSystemReturnsNil(t *testing.T) {
	dir := t.TempDir()
	cache := NewDATCache(dir, &http.Client{})

	for _, system := range []string{"PSX", "SAT", "DC", "SCD", "PS2"} {
		idx, err := cache.GetIndex(system)
		assert.NoError(t, err, "system: %s", system)
		assert.Nil(t, idx, "system: %s", system)
	}
}

func TestDATCache_GetIndex_UnknownSystemReturnsNil(t *testing.T) {
	dir := t.TempDir()
	cache := NewDATCache(dir, &http.Client{})

	idx, err := cache.GetIndex("NOSUCHSYSTEM")
	assert.NoError(t, err)
	assert.Nil(t, idx)
}

func TestDATCache_GetIndex_MemoryCache(t *testing.T) {
	dir := t.TempDir()

	// Write a sample DAT file
	systemName := AbbreviationToLibRetro["GB"]
	datPath := filepath.Join(dir, systemName+".dat")
	datContent := `game (
	name "Tetris (World)"
	rom ( name "Tetris (World).gb" size 32768 crc 46DF91AD md5 abc sha1 def )
)
`
	require.NoError(t, os.WriteFile(datPath, []byte(datContent), 0o644))

	cache := NewDATCache(dir, &http.Client{})

	// First call loads from disk
	idx1, err := cache.GetIndex("GB")
	require.NoError(t, err)
	require.NotNil(t, idx1)

	// Delete the file — second call should still work from memory cache
	require.NoError(t, os.Remove(datPath))

	idx2, err := cache.GetIndex("GB")
	require.NoError(t, err)
	assert.Equal(t, idx1, idx2)
}

func TestDATCache_GetIndex_MissingFileReturnsNil(t *testing.T) {
	// Empty dir — no DAT files on disk. GetIndex should return nil, nil
	// for a valid mapped system without attempting a download.
	dir := t.TempDir()
	cache := NewDATCache(dir, &http.Client{})

	idx, err := cache.GetIndex("SNES")
	assert.NoError(t, err)
	assert.Nil(t, idx)
}

func TestDATCache_RefreshAll_EmptyDir(t *testing.T) {
	dir := t.TempDir()
	cache := NewDATCache(dir, &http.Client{})

	// Should not panic or error on empty dir
	cache.RefreshAll()
}

func TestDATCache_RefreshAll_NonExistentDir(t *testing.T) {
	cache := NewDATCache("/nonexistent/path/that/does/not/exist", &http.Client{})

	// Should not panic — just logs and returns
	cache.RefreshAll()
}

func TestDATCache_GetIndex_MultipleSystems(t *testing.T) {
	dir := t.TempDir()

	// Write DAT files for two different systems
	for abbrev, content := range map[string]string{
		"NES": `game (
	name "NES Game (USA)"
	rom ( name "NES Game (USA).nes" size 100 crc 11111111 md5 a sha1 b )
)
`,
		"SNES": `game (
	name "SNES Game (USA)"
	rom ( name "SNES Game (USA).sfc" size 200 crc 22222222 md5 c sha1 d )
)
`,
	} {
		systemName := AbbreviationToLibRetro[abbrev]
		datPath := filepath.Join(dir, systemName+".dat")
		require.NoError(t, os.WriteFile(datPath, []byte(content), 0o644))
	}

	cache := NewDATCache(dir, &http.Client{})

	nesIdx, err := cache.GetIndex("NES")
	require.NoError(t, err)
	require.NotNil(t, nesIdx)
	_, ok := nesIdx.LookupCRC("11111111")
	assert.True(t, ok)

	snesIdx, err := cache.GetIndex("SNES")
	require.NoError(t, err)
	require.NotNil(t, snesIdx)
	_, ok = snesIdx.LookupCRC("22222222")
	assert.True(t, ok)

	// NES index should not contain SNES CRC
	_, ok = nesIdx.LookupCRC("22222222")
	assert.False(t, ok)
}
