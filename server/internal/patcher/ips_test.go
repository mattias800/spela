package patcher

import (
	"encoding/binary"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// buildIPSPatch creates a valid IPS patch with the given records.
func buildIPSPatch(records []ipsRecord) []byte {
	var patch []byte
	patch = append(patch, []byte("PATCH")...)
	for _, r := range records {
		// 3-byte offset
		patch = append(patch, byte(r.offset>>16), byte(r.offset>>8), byte(r.offset))
		if r.rle {
			// size = 0 for RLE
			patch = append(patch, 0, 0)
			// RLE size (2 bytes) + value (1 byte)
			size := make([]byte, 2)
			binary.BigEndian.PutUint16(size, uint16(r.rleSize))
			patch = append(patch, size...)
			patch = append(patch, r.rleValue)
		} else {
			// size (2 bytes) + data
			size := make([]byte, 2)
			binary.BigEndian.PutUint16(size, uint16(len(r.data)))
			patch = append(patch, size...)
			patch = append(patch, r.data...)
		}
	}
	patch = append(patch, []byte("EOF")...)
	return patch
}

type ipsRecord struct {
	offset   int
	data     []byte
	rle      bool
	rleSize  int
	rleValue byte
}

func TestApplyIPS_SimpleReplace(t *testing.T) {
	rom := []byte("Hello, World!")
	patch := buildIPSPatch([]ipsRecord{
		{offset: 7, data: []byte("Go!!!")},
	})

	result, err := ApplyIPS(rom, patch)
	require.NoError(t, err)
	assert.Equal(t, "Hello, Go!!!!", string(result))
}

func TestApplyIPS_MultipleRecords(t *testing.T) {
	rom := make([]byte, 20)
	for i := range rom {
		rom[i] = 'A'
	}

	patch := buildIPSPatch([]ipsRecord{
		{offset: 0, data: []byte("BB")},
		{offset: 10, data: []byte("CC")},
		{offset: 18, data: []byte("DD")},
	})

	result, err := ApplyIPS(rom, patch)
	require.NoError(t, err)
	assert.Equal(t, byte('B'), result[0])
	assert.Equal(t, byte('B'), result[1])
	assert.Equal(t, byte('A'), result[2])
	assert.Equal(t, byte('C'), result[10])
	assert.Equal(t, byte('C'), result[11])
	assert.Equal(t, byte('D'), result[18])
	assert.Equal(t, byte('D'), result[19])
}

func TestApplyIPS_RLERecord(t *testing.T) {
	rom := make([]byte, 10)

	patch := buildIPSPatch([]ipsRecord{
		{offset: 2, rle: true, rleSize: 5, rleValue: 0xFF},
	})

	result, err := ApplyIPS(rom, patch)
	require.NoError(t, err)
	assert.Equal(t, byte(0), result[0])
	assert.Equal(t, byte(0), result[1])
	for i := 2; i < 7; i++ {
		assert.Equal(t, byte(0xFF), result[i], "byte at offset %d", i)
	}
	assert.Equal(t, byte(0), result[7])
}

func TestApplyIPS_ExpandsROM(t *testing.T) {
	rom := make([]byte, 5)

	patch := buildIPSPatch([]ipsRecord{
		{offset: 8, data: []byte("XYZ")},
	})

	result, err := ApplyIPS(rom, patch)
	require.NoError(t, err)
	assert.Equal(t, 11, len(result))
	assert.Equal(t, byte('X'), result[8])
	assert.Equal(t, byte('Y'), result[9])
	assert.Equal(t, byte('Z'), result[10])
}

func TestApplyIPS_InvalidHeader(t *testing.T) {
	rom := []byte("test")
	patch := []byte("WRONG")

	_, err := ApplyIPS(rom, patch)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "invalid IPS header")
}

func TestApplyIPS_TooShort(t *testing.T) {
	rom := []byte("test")
	patch := []byte("PAT")

	_, err := ApplyIPS(rom, patch)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "too short")
}

func TestApplyIPS_EmptyPatch(t *testing.T) {
	rom := []byte("Hello, World!")
	patch := buildIPSPatch(nil)

	result, err := ApplyIPS(rom, patch)
	require.NoError(t, err)
	assert.Equal(t, rom, result)
}

func TestApplyIPS_RLEExpandsROM(t *testing.T) {
	rom := make([]byte, 3)

	patch := buildIPSPatch([]ipsRecord{
		{offset: 5, rle: true, rleSize: 4, rleValue: 0xAB},
	})

	result, err := ApplyIPS(rom, patch)
	require.NoError(t, err)
	assert.Equal(t, 9, len(result))
	for i := 5; i < 9; i++ {
		assert.Equal(t, byte(0xAB), result[i])
	}
}
