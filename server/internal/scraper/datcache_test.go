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

func TestDATCache_GetIndex_NewDiscSystemsReturnNil(t *testing.T) {
	dir := t.TempDir()
	cache := NewDATCache(dir, &http.Client{})

	// All 9 new non-playable consoles should return nil for No-Intro indices
	for _, system := range []string{"PS3", "PS4", "PS5", "X360", "XONE", "XSX", "WII", "WIIU", "NSW"} {
		idx, err := cache.GetIndex(system)
		assert.NoError(t, err, "system: %s", system)
		assert.Nil(t, idx, "system: %s should return nil from No-Intro GetIndex", system)
	}
}

func TestDATCache_GetRedumpIndex_FromDisk(t *testing.T) {
	dir := t.TempDir()

	// Write a Redump DAT file in the redump/ subdirectory
	systemName := AbbreviationToRedump["PSX"]
	redumpDir := filepath.Join(dir, "redump")
	require.NoError(t, os.MkdirAll(redumpDir, 0755))
	datPath := filepath.Join(redumpDir, systemName+".dat")
	datContent := `game (
	name "Crash Bandicoot (USA)"
	rom ( name "Crash Bandicoot (USA) (Track 01).bin" size 681984000 crc AABB1122 md5 abc sha1 def )
)
`
	require.NoError(t, os.WriteFile(datPath, []byte(datContent), 0o644))

	cache := NewDATCache(dir, &http.Client{})
	idx, err := cache.GetRedumpIndex("PSX")
	require.NoError(t, err)
	require.NotNil(t, idx)

	entry, ok := idx.LookupCRC("AABB1122")
	assert.True(t, ok)
	assert.Equal(t, "Crash Bandicoot (USA)", entry.GameName)
}

func TestDATCache_GetRedumpIndex_UnmappedSystemReturnsNil(t *testing.T) {
	dir := t.TempDir()
	cache := NewDATCache(dir, &http.Client{})

	// NES is not in AbbreviationToRedump
	idx, err := cache.GetRedumpIndex("NES")
	assert.NoError(t, err)
	assert.Nil(t, idx)
}

func TestDATCache_GetRedumpIndex_MissingFileReturnsNil(t *testing.T) {
	dir := t.TempDir()
	cache := NewDATCache(dir, &http.Client{})

	// PSX is mapped but no file on disk
	idx, err := cache.GetRedumpIndex("PSX")
	assert.NoError(t, err)
	assert.Nil(t, idx)
}

func TestDATCache_GetRedumpIndex_MemoryCache(t *testing.T) {
	dir := t.TempDir()

	systemName := AbbreviationToRedump["DC"]
	redumpDir := filepath.Join(dir, "redump")
	require.NoError(t, os.MkdirAll(redumpDir, 0755))
	datPath := filepath.Join(redumpDir, systemName+".dat")
	datContent := `game (
	name "Sonic Adventure (USA)"
	rom ( name "Sonic Adventure (USA).gdi" size 1200000000 crc 11223344 md5 abc sha1 def )
)
`
	require.NoError(t, os.WriteFile(datPath, []byte(datContent), 0o644))

	cache := NewDATCache(dir, &http.Client{})

	// First call loads from disk
	idx1, err := cache.GetRedumpIndex("DC")
	require.NoError(t, err)
	require.NotNil(t, idx1)

	// Delete file — second call should still work from memory cache
	require.NoError(t, os.Remove(datPath))

	idx2, err := cache.GetRedumpIndex("DC")
	require.NoError(t, err)
	assert.Equal(t, idx1, idx2)
}

func TestDATCache_GetRedumpIndex_NewConsoles(t *testing.T) {
	dir := t.TempDir()
	redumpDir := filepath.Join(dir, "redump")
	require.NoError(t, os.MkdirAll(redumpDir, 0755))

	// Write Redump DATs for new non-playable consoles that have Redump mappings
	for abbrev, content := range map[string]string{
		"PS3": `game (
	name "PS3 Game (USA)"
	rom ( name "PS3 Game (USA).iso" size 1000 crc 33333333 md5 a sha1 b )
)
`,
		"X360": `game (
	name "X360 Game (USA)"
	rom ( name "X360 Game (USA).iso" size 2000 crc 44444444 md5 c sha1 d )
)
`,
		"WII": `game (
	name "Wii Game (USA)"
	rom ( name "Wii Game (USA).iso" size 3000 crc 55555555 md5 e sha1 f )
)
`,
	} {
		systemName := AbbreviationToRedump[abbrev]
		datPath := filepath.Join(redumpDir, systemName+".dat")
		require.NoError(t, os.WriteFile(datPath, []byte(content), 0o644))
	}

	cache := NewDATCache(dir, &http.Client{})

	ps3Idx, err := cache.GetRedumpIndex("PS3")
	require.NoError(t, err)
	require.NotNil(t, ps3Idx)
	_, ok := ps3Idx.LookupCRC("33333333")
	assert.True(t, ok)

	x360Idx, err := cache.GetRedumpIndex("X360")
	require.NoError(t, err)
	require.NotNil(t, x360Idx)
	_, ok = x360Idx.LookupCRC("44444444")
	assert.True(t, ok)

	wiiIdx, err := cache.GetRedumpIndex("WII")
	require.NoError(t, err)
	require.NotNil(t, wiiIdx)
	_, ok = wiiIdx.LookupCRC("55555555")
	assert.True(t, ok)
}

func TestDATCache_RefreshRedump_EmptyDir(t *testing.T) {
	dir := t.TempDir()
	cache := NewDATCache(dir, &http.Client{})

	// Should not panic or error on empty dir
	cache.RefreshRedump()
}

func TestDATCache_RefreshRedump_NonExistentDir(t *testing.T) {
	cache := NewDATCache("/nonexistent/path/that/does/not/exist", &http.Client{})

	// Should not panic — just logs and returns
	cache.RefreshRedump()
}

func TestAbbreviationToRedump_ContainsExpectedMappings(t *testing.T) {
	// Verify all expected Redump mappings exist
	expectedMappings := map[string]string{
		"PS3":  "Sony - PlayStation 3",
		"X360": "Microsoft - Xbox 360",
		"WII":  "Nintendo - Wii",
		"PSX":  "Sony - PlayStation",
		"PS2":  "Sony - PlayStation 2",
		"SAT":  "Sega - Saturn",
		"DC":   "Sega - Dreamcast",
		"GC":   "Nintendo - GameCube",
		"SCD":  "Sega - Mega-CD - Sega CD",
		"PSP":  "Sony - PlayStation Portable",
		"PCFX": "NEC - PC-FX",
	}

	for abbrev, expectedName := range expectedMappings {
		actualName, ok := AbbreviationToRedump[abbrev]
		assert.True(t, ok, "AbbreviationToRedump should contain %s", abbrev)
		assert.Equal(t, expectedName, actualName, "AbbreviationToRedump[%s]", abbrev)
	}
}
