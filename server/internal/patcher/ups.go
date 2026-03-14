package patcher

import (
	"fmt"
	"hash/crc32"
)

// UPS format specification:
// Header: "UPS1" (4 bytes)
// Source size: variable-length encoded
// Target size: variable-length encoded
// XOR diffs until 12 bytes before end:
//   Relative offset to next change (variable-length encoded)
//   XOR data bytes (terminated by 0x00)
// Footer (last 12 bytes):
//   Source CRC32 (4 bytes, little-endian)
//   Target CRC32 (4 bytes, little-endian)
//   Patch CRC32  (4 bytes, little-endian)

const upsHeader = "UPS1"

// ApplyUPS applies a UPS patch to ROM data with CRC32 verification.
func ApplyUPS(romData, patchData []byte) ([]byte, error) {
	if len(patchData) < 16 {
		return nil, fmt.Errorf("UPS patch too short")
	}

	// Verify patch CRC32
	patchCRC := readLE32(patchData[len(patchData)-4:])
	actualPatchCRC := crc32.ChecksumIEEE(patchData[:len(patchData)-4])
	if patchCRC != actualPatchCRC {
		return nil, fmt.Errorf("UPS patch CRC32 mismatch: expected %08x, got %08x", patchCRC, actualPatchCRC)
	}

	// Verify header
	if string(patchData[:4]) != upsHeader {
		return nil, fmt.Errorf("invalid UPS header: expected %q, got %q", upsHeader, string(patchData[:4]))
	}

	pos := 4

	// Read source size
	sourceSize, n := decodeVLQ(patchData[pos:])
	pos += n

	if int(sourceSize) != len(romData) {
		return nil, fmt.Errorf("UPS source size mismatch: patch expects %d bytes, ROM is %d bytes", sourceSize, len(romData))
	}

	// Read target size
	targetSize, n := decodeVLQ(patchData[pos:])
	pos += n

	// Verify source CRC32
	sourceCRC := readLE32(patchData[len(patchData)-12:])
	actualSourceCRC := crc32.ChecksumIEEE(romData)
	if sourceCRC != actualSourceCRC {
		return nil, fmt.Errorf("UPS source CRC32 mismatch: expected %08x, got %08x (wrong base ROM?)", sourceCRC, actualSourceCRC)
	}

	// Validate target size
	if targetSize > MaxOutputSize {
		return nil, fmt.Errorf("UPS target size %d exceeds maximum allowed size (%d bytes)", targetSize, MaxOutputSize)
	}

	// Create target as copy of source (padded/truncated to target size)
	target := make([]byte, targetSize)
	copy(target, romData)

	// Apply XOR diffs
	targetPos := 0
	endPos := len(patchData) - 12

	for pos < endPos {
		// Read relative offset to next change
		relOffset, n := decodeVLQ(patchData[pos:])
		pos += n
		targetPos += int(relOffset)

		// Read XOR data until 0x00 terminator
		for pos < endPos {
			xorByte := patchData[pos]
			pos++
			if xorByte == 0x00 {
				targetPos++
				break
			}
			if targetPos < len(target) {
				// XOR with source byte (0 if beyond source)
				sourceByte := byte(0)
				if targetPos < len(romData) {
					sourceByte = romData[targetPos]
				}
				target[targetPos] = sourceByte ^ xorByte
			}
			targetPos++
		}
	}

	// Verify target CRC32
	targetCRC := readLE32(patchData[len(patchData)-8:])
	actualTargetCRC := crc32.ChecksumIEEE(target)
	if targetCRC != actualTargetCRC {
		return nil, fmt.Errorf("UPS target CRC32 mismatch: expected %08x, got %08x", targetCRC, actualTargetCRC)
	}

	return target, nil
}
