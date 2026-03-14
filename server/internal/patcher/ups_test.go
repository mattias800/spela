package patcher

import (
	"hash/crc32"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// buildUPSPatch creates a valid UPS patch that transforms source into target.
func buildUPSPatch(source, target []byte) []byte {
	var patch []byte
	patch = append(patch, []byte("UPS1")...)
	patch = append(patch, encodeVLQ(uint64(len(source)))...)
	patch = append(patch, encodeVLQ(uint64(len(target)))...)

	// Generate XOR diffs
	maxLen := len(source)
	if len(target) > maxLen {
		maxLen = len(target)
	}

	lastDiffEnd := 0
	i := 0
	for i < maxLen {
		// Get source and target bytes (0 if beyond length)
		var sb, tb byte
		if i < len(source) {
			sb = source[i]
		}
		if i < len(target) {
			tb = target[i]
		}

		if sb != tb {
			// Found a difference: write relative offset
			relOffset := i - lastDiffEnd
			patch = append(patch, encodeVLQ(uint64(relOffset))...)

			// Write XOR bytes until we hit a match (or end)
			for i < maxLen {
				sb = 0
				tb = 0
				if i < len(source) {
					sb = source[i]
				}
				if i < len(target) {
					tb = target[i]
				}
				xorByte := sb ^ tb
				if xorByte == 0 {
					// Terminate the diff block
					patch = append(patch, 0x00)
					i++
					lastDiffEnd = i
					break
				}
				patch = append(patch, xorByte)
				i++
				if i >= maxLen {
					// End of data, add terminator
					patch = append(patch, 0x00)
					lastDiffEnd = i + 1
				}
			}
		} else {
			i++
		}
	}

	// Footer: source CRC32, target CRC32, patch CRC32
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(source))...)
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(target))...)
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(patch))...)

	return patch
}

func TestApplyUPS_Simple(t *testing.T) {
	source := []byte("Hello, World!")
	target := []byte("Hello, Go!!!")

	patch := buildUPSPatch(source, target)

	result, err := ApplyUPS(source, patch)
	require.NoError(t, err)
	assert.Equal(t, string(target), string(result))
}

func TestApplyUPS_NoChanges(t *testing.T) {
	source := []byte("Identical")
	target := []byte("Identical")

	patch := buildUPSPatch(source, target)

	result, err := ApplyUPS(source, patch)
	require.NoError(t, err)
	assert.Equal(t, target, result)
}

func TestApplyUPS_InvalidHeader(t *testing.T) {
	source := []byte("test")
	// Build a patch with wrong header but valid CRC so header check is reached
	patch := []byte("XXXX")
	patch = append(patch, encodeVLQ(4)...)  // source size
	patch = append(patch, encodeVLQ(4)...)  // target size
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(source))...)
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(source))...)
	patch = append(patch, writeLE32(crc32.ChecksumIEEE(patch))...)

	_, err := ApplyUPS(source, patch)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "invalid UPS header")
}

func TestApplyUPS_WrongSource(t *testing.T) {
	source := []byte("Hello, World!")
	target := []byte("Hello, Go!!!")

	patch := buildUPSPatch(source, target)

	wrongSource := []byte("Wrong source!!")
	_, err := ApplyUPS(wrongSource, patch)
	assert.Error(t, err)
	// Could be size mismatch or CRC mismatch depending on length
}

func TestApplyUPS_CorruptPatchCRC(t *testing.T) {
	source := []byte("Hello, World!")
	target := []byte("Hello, Go!!!")

	patch := buildUPSPatch(source, target)
	// Corrupt the last byte
	patch[len(patch)-1] ^= 0xFF

	_, err := ApplyUPS(source, patch)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "patch CRC32 mismatch")
}

func TestApplyUPS_TooShort(t *testing.T) {
	source := []byte("test")
	patch := []byte("UPS1")

	_, err := ApplyUPS(source, patch)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "too short")
}

func TestApplyUPS_SingleByteDiff(t *testing.T) {
	source := []byte("AAAA")
	target := []byte("ABAA")

	patch := buildUPSPatch(source, target)

	result, err := ApplyUPS(source, patch)
	require.NoError(t, err)
	assert.Equal(t, target, result)
}
