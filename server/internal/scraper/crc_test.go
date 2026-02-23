package scraper

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestComputeFileCRC32(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "test.bin")

	// Known CRC32: "hello" = 0x3610A686
	require.NoError(t, os.WriteFile(path, []byte("hello"), 0o644))

	crc, err := ComputeFileCRC32(path)
	require.NoError(t, err)
	assert.Equal(t, "3610A686", crc)
}

func TestComputeFileCRC32_EmptyFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "empty.bin")

	require.NoError(t, os.WriteFile(path, []byte{}, 0o644))

	crc, err := ComputeFileCRC32(path)
	require.NoError(t, err)
	assert.Equal(t, "00000000", crc)
}

func TestComputeFileCRC32_FileNotFound(t *testing.T) {
	_, err := ComputeFileCRC32("/nonexistent/path/file.bin")
	assert.Error(t, err)
}
