package scanner

import (
	"encoding/binary"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// writeCHDV5 creates a minimal CHD v5 file with the given metadata entries.
// Each entry is a (tag, next) pair; the linked list is wired automatically.
func writeCHDV5(t *testing.T, dir, name string, tags [][4]byte) string {
	t.Helper()
	path := filepath.Join(dir, name)
	f, err := os.Create(path)
	require.NoError(t, err)
	defer f.Close()

	// Signature
	_, err = f.Write([]byte("MComprHD"))
	require.NoError(t, err)

	// Header length (124 for v5)
	require.NoError(t, binary.Write(f, binary.BigEndian, uint32(124)))

	// Version = 5
	require.NoError(t, binary.Write(f, binary.BigEndian, uint32(5)))

	// Pad the header up to offset 104 (metadata offset field position)
	pad := make([]byte, 104-16) // 16 bytes already written (8 sig + 4 headerLen + 4 version)
	_, err = f.Write(pad)
	require.NoError(t, err)

	// Write metadata offset — starts right after the 124-byte header
	var metaStart uint64
	if len(tags) > 0 {
		metaStart = 124
	}
	require.NoError(t, binary.Write(f, binary.BigEndian, metaStart))

	// Pad the rest of the header (offset is now 104+8=112, header is 124 bytes)
	headerPad := make([]byte, 124-112)
	_, err = f.Write(headerPad)
	require.NoError(t, err)

	// Write metadata entries: 4-byte tag + 4-byte flags/length + 8-byte next
	for i, tag := range tags {
		_, err = f.Write(tag[:])
		require.NoError(t, err)

		require.NoError(t, binary.Write(f, binary.BigEndian, uint32(0))) // flags/length

		var next uint64
		if i < len(tags)-1 {
			next = 124 + uint64((i+1)*16)
		}
		require.NoError(t, binary.Write(f, binary.BigEndian, next))
	}

	return path
}

func TestDetectCHDMode_CreateCD(t *testing.T) {
	dir := t.TempDir()
	path := writeCHDV5(t, dir, "game.chd", [][4]byte{
		{'C', 'H', 'T', '2'}, // CD track v2 metadata
	})

	assert.Equal(t, "createcd", DetectCHDMode(path))
}

func TestDetectCHDMode_CreateDVD(t *testing.T) {
	dir := t.TempDir()
	path := writeCHDV5(t, dir, "game.chd", [][4]byte{
		{'A', 'V', 'D', 'D'}, // some non-CD tag
	})

	assert.Equal(t, "createdvd", DetectCHDMode(path))
}

func TestDetectCHDMode_NoMetadata(t *testing.T) {
	dir := t.TempDir()
	path := writeCHDV5(t, dir, "game.chd", nil)

	assert.Equal(t, "createdvd", DetectCHDMode(path))
}

func TestDetectCHDMode_NotCHD(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "not-a-chd.bin")
	require.NoError(t, os.WriteFile(path, []byte("hello world"), 0644))

	assert.Equal(t, "", DetectCHDMode(path))
}

func TestDetectCHDMode_NonexistentFile(t *testing.T) {
	assert.Equal(t, "", DetectCHDMode("/tmp/nonexistent-chd-file.chd"))
}

func TestDetectCHDMode_MultipleTagsWithCDLast(t *testing.T) {
	dir := t.TempDir()
	path := writeCHDV5(t, dir, "game.chd", [][4]byte{
		{'G', 'D', 'D', 'D'}, // non-CD tag
		{'X', 'Y', 'Z', 'W'}, // non-CD tag
		{'C', 'H', 'C', 'D'}, // CD metadata (last in chain)
	})

	assert.Equal(t, "createcd", DetectCHDMode(path))
}

func TestPSPCHDAchievementsWarning_PSP_CreateDVD(t *testing.T) {
	dir := t.TempDir()
	path := writeCHDV5(t, dir, "game.chd", [][4]byte{
		{'R', 'A', 'W', 'H'}, // raw/no CD tags → createdvd
	})

	warning := PSPCHDAchievementsWarning(path, "PSP")
	assert.Contains(t, warning, "createdvd")
	assert.Contains(t, warning, "incompatible")
}

func TestPSPCHDAchievementsWarning_PSP_CreateCD(t *testing.T) {
	dir := t.TempDir()
	path := writeCHDV5(t, dir, "game.chd", [][4]byte{
		{'C', 'H', 'T', 'R'},
	})

	assert.Empty(t, PSPCHDAchievementsWarning(path, "PSP"))
}

func TestPSPCHDAchievementsWarning_NonPSP(t *testing.T) {
	dir := t.TempDir()
	path := writeCHDV5(t, dir, "game.chd", [][4]byte{
		{'R', 'A', 'W', 'H'},
	})

	assert.Empty(t, PSPCHDAchievementsWarning(path, "PSX"))
}

func TestPSPCHDAchievementsWarning_CSO(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "game.cso")
	require.NoError(t, os.WriteFile(path, []byte("dummy"), 0644))

	warning := PSPCHDAchievementsWarning(path, "PSP")
	assert.Contains(t, warning, "CSO")
}

func TestPSPCHDAchievementsWarning_ISO(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "game.iso")
	require.NoError(t, os.WriteFile(path, []byte("dummy"), 0644))

	assert.Empty(t, PSPCHDAchievementsWarning(path, "PSP"))
}
