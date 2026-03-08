package scanner

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// makeNESROM creates a minimal valid iNES ROM file with the given header bytes.
// prgBanks and chrBanks control the data size; padding is bytes 8-15.
func makeNESROM(t *testing.T, dir string, name string, prgBanks, chrBanks byte, flags6, flags7 byte, padding [8]byte) string {
	t.Helper()
	path := filepath.Join(dir, name)

	header := [16]byte{
		'N', 'E', 'S', 0x1A, // magic
		prgBanks,             // PRG ROM banks
		chrBanks,             // CHR ROM banks
		flags6,               // flags 6
		flags7,               // flags 7
	}
	copy(header[8:], padding[:])

	// Create minimal ROM data (just enough to satisfy size check)
	dataSize := int(prgBanks)*prgROMBankSize + int(chrBanks)*chrROMBankSize
	data := make([]byte, len(header)+dataSize)
	copy(data, header[:])

	require.NoError(t, os.WriteFile(path, data, 0644))
	return path
}

func TestSanitizeNESHeader_CleanHeader(t *testing.T) {
	dir := t.TempDir()
	path := makeNESROM(t, dir, "clean.nes", 2, 1, 0, 0, [8]byte{})

	result := SanitizeNESHeader(path)
	assert.Equal(t, "clean", result)

	// Verify file unchanged
	data, _ := os.ReadFile(path)
	for i := 8; i < 16; i++ {
		assert.Equal(t, byte(0), data[i], "byte %d should still be zero", i)
	}
}

func TestSanitizeNESHeader_DirtyHeader(t *testing.T) {
	dir := t.TempDir()
	// Simulate "DiskDude!" signature in padding (common dirty header)
	padding := [8]byte{'D', 'i', 's', 'k', 'D', 'u', 'd', 'e'}
	path := makeNESROM(t, dir, "dirty.nes", 2, 1, 0, 0, padding)

	result := SanitizeNESHeader(path)
	assert.Equal(t, "sanitized", result)

	// Verify padding bytes are now zero
	data, _ := os.ReadFile(path)
	for i := 8; i < 16; i++ {
		assert.Equal(t, byte(0), data[i], "byte %d should be zeroed", i)
	}

	// Verify rest of header is preserved
	assert.Equal(t, byte('N'), data[0])
	assert.Equal(t, byte('E'), data[1])
	assert.Equal(t, byte('S'), data[2])
	assert.Equal(t, byte(0x1A), data[3])
	assert.Equal(t, byte(2), data[4]) // PRG banks
	assert.Equal(t, byte(1), data[5]) // CHR banks
}

func TestSanitizeNESHeader_NES20NotTouched(t *testing.T) {
	dir := t.TempDir()
	// NES 2.0: bits 2-3 of byte 7 = 0b10, so byte 7 = 0x08
	padding := [8]byte{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}
	path := makeNESROM(t, dir, "nes20.nes", 2, 1, 0, 0x08, padding)

	result := SanitizeNESHeader(path)
	assert.Equal(t, "clean", result)

	// Verify padding bytes are NOT zeroed (NES 2.0 uses them)
	data, _ := os.ReadFile(path)
	assert.Equal(t, byte(0x01), data[8])
	assert.Equal(t, byte(0x02), data[9])
}

func TestSanitizeNESHeader_NotNES(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "notnes.nes")
	require.NoError(t, os.WriteFile(path, []byte("this is not a NES ROM"), 0644))

	result := SanitizeNESHeader(path)
	assert.Equal(t, "", result)
}

func TestSanitizeNESHeader_FileTooSmall(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "tiny.nes")
	require.NoError(t, os.WriteFile(path, []byte{0x4E, 0x45, 0x53}, 0644))

	result := SanitizeNESHeader(path)
	assert.Equal(t, "", result)
}

func TestSanitizeNESHeader_NI21Signature(t *testing.T) {
	dir := t.TempDir()
	// "NI2.1" tool signature at bytes 11-15 (bytes 8-10 are zero)
	padding := [8]byte{0, 0, 0, 'N', 'I', '2', '.', '1'}
	path := makeNESROM(t, dir, "ni21.nes", 2, 1, 0, 0, padding)

	result := SanitizeNESHeader(path)
	assert.Equal(t, "sanitized", result)

	data, _ := os.ReadFile(path)
	for i := 8; i < 16; i++ {
		assert.Equal(t, byte(0), data[i])
	}
}

func TestValidateNESHeader_Valid(t *testing.T) {
	dir := t.TempDir()
	path := makeNESROM(t, dir, "valid.nes", 2, 1, 0, 0, [8]byte{})

	err := ValidateNESHeader(path)
	assert.NoError(t, err)
}

func TestValidateNESHeader_WithTrainer(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "trainer.nes")

	header := [16]byte{'N', 'E', 'S', 0x1A, 1, 0, 0x04, 0} // flags6 bit 2 = trainer
	dataSize := iNESHeaderSize + trainerSize + prgROMBankSize
	data := make([]byte, dataSize)
	copy(data, header[:])
	require.NoError(t, os.WriteFile(path, data, 0644))

	err := ValidateNESHeader(path)
	assert.NoError(t, err)
}

func TestValidateNESHeader_TooSmall(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "small.nes")

	// Valid header claiming 2 PRG banks but file is too short
	header := [16]byte{'N', 'E', 'S', 0x1A, 2, 0, 0, 0}
	data := make([]byte, iNESHeaderSize+100) // way too small for 2x16KB
	copy(data, header[:])
	require.NoError(t, os.WriteFile(path, data, 0644))

	err := ValidateNESHeader(path)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "file too small")
}

func TestValidateNESHeader_ZeroPRG(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "zeroprog.nes")

	header := [16]byte{'N', 'E', 'S', 0x1A, 0, 1, 0, 0} // 0 PRG banks
	require.NoError(t, os.WriteFile(path, header[:], 0644))

	err := ValidateNESHeader(path)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "PRG ROM bank count is zero")
}

func TestValidateNESHeader_BadMagic(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "badmagic.nes")

	data := make([]byte, 100)
	data[0] = 'X'
	require.NoError(t, os.WriteFile(path, data, 0644))

	err := ValidateNESHeader(path)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "invalid iNES magic")
}

func TestValidateNESHeader_NotNESFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "readme.txt")
	require.NoError(t, os.WriteFile(path, []byte("hello"), 0644))

	err := ValidateNESHeader(path)
	assert.Error(t, err)
}
