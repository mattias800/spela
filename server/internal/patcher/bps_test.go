package patcher

import (
	"hash/crc32"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// encodeVLQ encodes a value using BPS/UPS variable-length encoding.
func encodeVLQ(value uint64) []byte {
	var result []byte
	for {
		x := byte(value & 0x7F)
		value >>= 7
		if value == 0 {
			result = append(result, x|0x80)
			return result
		}
		result = append(result, x)
		value--
	}
}

// encodeSignedVLQ encodes a signed value for BPS SourceCopy/TargetCopy offsets.
func encodeSignedVLQ(value int) []byte {
	if value < 0 {
		return encodeVLQ(uint64((-value-1)*2 + 1))
	}
	return encodeVLQ(uint64(value * 2))
}

// writeLE32 writes a 32-bit value in little-endian.
func writeLE32(v uint32) []byte {
	return []byte{byte(v), byte(v >> 8), byte(v >> 16), byte(v >> 24)}
}

// buildBPSPatch creates a minimal valid BPS patch that transforms source into target
// using TargetRead actions (the simplest approach for testing).
func buildBPSPatch(source, target []byte) []byte {
	var patch []byte
	patch = append(patch, []byte("BPS1")...)
	patch = append(patch, encodeVLQ(uint64(len(source)))...)
	patch = append(patch, encodeVLQ(uint64(len(target)))...)
	patch = append(patch, encodeVLQ(0)...) // no metadata

	// Single TargetRead action for all target data
	// action type 1 (TargetRead), length = len(target)
	// data = (length-1) << 2 | 1
	actionData := uint64(len(target)-1)<<2 | 1
	patch = append(patch, encodeVLQ(actionData)...)
	patch = append(patch, target...)

	// Footer: source CRC32, target CRC32, patch CRC32
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(source))...)
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(target))...)
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(patch))...)

	return patch
}

func TestApplyBPS_Simple(t *testing.T) {
	source := []byte("Hello, World!")
	target := []byte("Hello, Go!!!")

	patch := buildBPSPatch(source, target)

	result, err := ApplyBPS(source, patch)
	require.NoError(t, err)
	assert.Equal(t, target, result)
}

func TestApplyBPS_DifferentSizes(t *testing.T) {
	source := []byte("Short")
	target := []byte("A much longer target string here")

	patch := buildBPSPatch(source, target)

	result, err := ApplyBPS(source, patch)
	require.NoError(t, err)
	assert.Equal(t, target, result)
}

func TestApplyBPS_InvalidHeader(t *testing.T) {
	source := []byte("test")
	// Build a patch with wrong header but valid CRC so header check is reached
	patch := []byte("XXXX")
	patch = append(patch, encodeVLQ(4)...)  // source size
	patch = append(patch, encodeVLQ(4)...)  // target size
	patch = append(patch, encodeVLQ(0)...)  // metadata size
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(source))...)
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(source))...)
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(patch))...)

	_, err := ApplyBPS(source, patch)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "invalid BPS header")
}

func TestApplyBPS_WrongSourceCRC(t *testing.T) {
	source := []byte("Hello, World!")
	target := []byte("Hello, Go!!!")

	patch := buildBPSPatch(source, target)

	// Use a different source
	wrongSource := []byte("Wrong source!!")
	_, err := ApplyBPS(wrongSource, patch)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "source size mismatch")
}

func TestApplyBPS_CorruptPatchCRC(t *testing.T) {
	source := []byte("Hello, World!")
	target := []byte("Hello, Go!!!")

	patch := buildBPSPatch(source, target)
	// Corrupt the last byte (patch CRC)
	patch[len(patch)-1] ^= 0xFF

	_, err := ApplyBPS(source, patch)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "patch CRC32 mismatch")
}

func TestApplyBPS_TooShort(t *testing.T) {
	source := []byte("test")
	patch := []byte("BPS1")

	_, err := ApplyBPS(source, patch)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "too short")
}

func TestApplyBPS_SourceRead(t *testing.T) {
	// Build a BPS patch using SourceRead action (copies from source at same position)
	source := []byte("ABCDEFGH")
	target := []byte("ABCDEFGH") // same content

	var patch []byte
	patch = append(patch, []byte("BPS1")...)
	patch = append(patch, encodeVLQ(uint64(len(source)))...)
	patch = append(patch, encodeVLQ(uint64(len(target)))...)
	patch = append(patch, encodeVLQ(0)...) // no metadata

	// SourceRead for all bytes: action type 0, length = 8
	actionData := uint64(len(target)-1)<<2 | 0
	patch = append(patch, encodeVLQ(actionData)...)

	// Footer
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(source))...)
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(target))...)
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(patch))...)

	result, err := ApplyBPS(source, patch)
	require.NoError(t, err)
	assert.Equal(t, target, result)
}

func TestVLQRoundtrip(t *testing.T) {
	tests := []uint64{0, 1, 127, 128, 255, 256, 1000, 65535, 16777215}
	for _, v := range tests {
		encoded := encodeVLQ(v)
		decoded, n, ok := decodeVLQ(encoded)
		assert.True(t, ok, "value %d", v)
		assert.Equal(t, v, decoded, "value %d", v)
		assert.Equal(t, len(encoded), n, "bytes consumed for value %d", v)
	}
}

func TestSignedVLQRoundtrip(t *testing.T) {
	tests := []int{0, 1, -1, 127, -128, 1000, -1000}
	for _, v := range tests {
		encoded := encodeSignedVLQ(v)
		decoded, n, ok := decodeSignedVLQ(encoded)
		assert.True(t, ok, "value %d", v)
		assert.Equal(t, v, decoded, "value %d", v)
		assert.Equal(t, len(encoded), n, "bytes consumed for value %d", v)
	}
}

// TestVLQTruncated covers issue #1134(B): decodeVLQ on a truncated
// (no-terminator) input must return ok=false rather than the
// (consumed=len(data)) value that previously caused index-out-of-range
// panics in callers' next read.
func TestVLQTruncated(t *testing.T) {
	// Three continuation bytes (high bit clear), no terminator.
	bad := []byte{0x00, 0x00, 0x00}
	_, _, ok := decodeVLQ(bad)
	assert.False(t, ok, "truncated VLQ must report ok=false")

	_, _, ok = decodeSignedVLQ(bad)
	assert.False(t, ok, "truncated signed VLQ must report ok=false")
}
