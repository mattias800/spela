package patcher

import (
	"fmt"
	"hash/crc32"
)

// BPS format specification:
// Header: "BPS1" (4 bytes)
// Source size: variable-length encoded
// Target size: variable-length encoded
// Metadata size: variable-length encoded
// Metadata: (metadata size bytes)
// Actions (until 12 bytes before end):
//   Each action: data (variable-length encoded)
//     action type = data & 3
//     length = (data >> 2) + 1
//     0 = SourceRead, 1 = TargetRead, 2 = SourceCopy, 3 = TargetCopy
// Footer (last 12 bytes):
//   Source CRC32 (4 bytes, little-endian)
//   Target CRC32 (4 bytes, little-endian)
//   Patch CRC32  (4 bytes, little-endian, covers everything before it)

const bpsHeader = "BPS1"

// ApplyBPS applies a BPS patch to ROM data with CRC32 verification.
func ApplyBPS(romData, patchData []byte) ([]byte, error) {
	if len(patchData) < 16 {
		return nil, fmt.Errorf("BPS patch too short")
	}

	// Verify patch CRC32 (covers everything except the last 4 bytes)
	patchCRC := readLE32(patchData[len(patchData)-4:])
	actualPatchCRC := crc32.ChecksumIEEE(patchData[:len(patchData)-4])
	if patchCRC != actualPatchCRC {
		return nil, fmt.Errorf("BPS patch CRC32 mismatch: expected %08x, got %08x", patchCRC, actualPatchCRC)
	}

	// Verify header
	if string(patchData[:4]) != bpsHeader {
		return nil, fmt.Errorf("invalid BPS header: expected %q, got %q", bpsHeader, string(patchData[:4]))
	}

	pos := 4

	// Read source size
	sourceSize, n, ok := decodeVLQ(patchData[pos:])
	if !ok {
		return nil, fmt.Errorf("BPS patch truncated reading source size")
	}
	pos += n

	// Verify source size matches
	if int(sourceSize) != len(romData) {
		return nil, fmt.Errorf("BPS source size mismatch: patch expects %d bytes, ROM is %d bytes", sourceSize, len(romData))
	}

	// Read target size
	targetSize, n, ok := decodeVLQ(patchData[pos:])
	if !ok {
		return nil, fmt.Errorf("BPS patch truncated reading target size")
	}
	pos += n

	// Read and skip metadata
	metadataSize, n, ok := decodeVLQ(patchData[pos:])
	if !ok {
		return nil, fmt.Errorf("BPS patch truncated reading metadata size")
	}
	pos += n
	if pos+int(metadataSize) > len(patchData) {
		return nil, fmt.Errorf("BPS metadata size %d overruns patch buffer", metadataSize)
	}
	pos += int(metadataSize)

	// Verify source CRC32
	sourceCRC := readLE32(patchData[len(patchData)-12:])
	actualSourceCRC := crc32.ChecksumIEEE(romData)
	if sourceCRC != actualSourceCRC {
		return nil, fmt.Errorf("BPS source CRC32 mismatch: expected %08x, got %08x (wrong base ROM?)", sourceCRC, actualSourceCRC)
	}

	// Validate target size
	if targetSize > MaxOutputSize {
		return nil, fmt.Errorf("BPS target size %d exceeds maximum allowed size (%d bytes)", targetSize, MaxOutputSize)
	}

	// Create target buffer
	target := make([]byte, targetSize)
	targetPos := 0

	sourceRelOffset := 0
	targetRelOffset := 0

	// Process actions (stop 12 bytes before end = 3 CRC32 values)
	endPos := len(patchData) - 12
	for pos < endPos {
		data, n, ok := decodeVLQ(patchData[pos:])
		if !ok {
			return nil, fmt.Errorf("BPS patch truncated reading action header at offset %d", pos)
		}
		pos += n

		actionType := data & 3
		length := int((data >> 2) + 1)

		// Every BPS action writes exactly `length` bytes into the target, so
		// an action whose length runs past the target buffer is malformed.
		// Reject it up front (#1324) — without this, a crafted VLQ length
		// (up to ~2^62) spins the copy loop for billions of no-op iterations
		// even though the bounded writes below produce nothing.
		if targetPos+length > len(target) {
			return nil, fmt.Errorf("BPS action would write past end of target buffer at offset %d", targetPos)
		}

		switch actionType {
		case 0: // SourceRead
			for i := 0; i < length; i++ {
				if targetPos < len(target) && targetPos < len(romData) {
					target[targetPos] = romData[targetPos]
				}
				targetPos++
			}

		case 1: // TargetRead
			if pos+length > endPos {
				return nil, fmt.Errorf("BPS patch truncated during TargetRead at offset %d", pos)
			}
			if targetPos+length > len(target) {
				return nil, fmt.Errorf("BPS TargetRead would write past end of target buffer at offset %d", targetPos)
			}
			copy(target[targetPos:targetPos+length], patchData[pos:pos+length])
			pos += length
			targetPos += length

		case 2: // SourceCopy
			offset, n, ok := decodeSignedVLQ(patchData[pos:])
			if !ok {
				return nil, fmt.Errorf("BPS patch truncated reading SourceCopy offset at %d", pos)
			}
			pos += n
			sourceRelOffset += offset
			for i := 0; i < length; i++ {
				if targetPos < len(target) && sourceRelOffset >= 0 && sourceRelOffset < len(romData) {
					target[targetPos] = romData[sourceRelOffset]
				}
				sourceRelOffset++
				targetPos++
			}

		case 3: // TargetCopy
			offset, n, ok := decodeSignedVLQ(patchData[pos:])
			if !ok {
				return nil, fmt.Errorf("BPS patch truncated reading TargetCopy offset at %d", pos)
			}
			pos += n
			targetRelOffset += offset
			for i := 0; i < length; i++ {
				if targetPos < len(target) && targetRelOffset >= 0 && targetRelOffset < len(target) {
					target[targetPos] = target[targetRelOffset]
				}
				targetRelOffset++
				targetPos++
			}
		}
	}

	// Verify target CRC32
	targetCRC := readLE32(patchData[len(patchData)-8:])
	actualTargetCRC := crc32.ChecksumIEEE(target)
	if targetCRC != actualTargetCRC {
		return nil, fmt.Errorf("BPS target CRC32 mismatch: expected %08x, got %08x", targetCRC, actualTargetCRC)
	}

	return target, nil
}

// decodeVLQ decodes a variable-length quantity from BPS/UPS format.
// Returns the value, the number of bytes consumed, and ok=false if the
// input is truncated (no terminator byte found).
//
// Issue #1134(B): the previous (value, n) signature returned len(data)
// as the consumed-byte count when no terminator was present, leaving
// the caller's `pos += n` legitimately past end-of-buffer. The next
// indexed read panicked with index-out-of-range; huma's middleware
// recovered that into a 500. Switching to (value, n, ok) lets callers
// surface a clean parser error and avoids any panic-recovery branch.
func decodeVLQ(data []byte) (uint64, int, bool) {
	var value uint64
	shift := uint(0)
	for i, b := range data {
		value += uint64(b&0x7F) << shift
		if b&0x80 != 0 {
			return value, i + 1, true
		}
		value += 1 << (shift + 7)
		shift += 7
	}
	return value, len(data), false
}

// decodeSignedVLQ decodes a signed variable-length quantity.
// The lowest bit indicates sign (1 = negative).
func decodeSignedVLQ(data []byte) (int, int, bool) {
	value, n, ok := decodeVLQ(data)
	if value&1 != 0 {
		return -int(value>>1) - 1, n, ok
	}
	return int(value >> 1), n, ok
}

// readLE32 reads a 32-bit little-endian value.
func readLE32(data []byte) uint32 {
	return uint32(data[0]) | uint32(data[1])<<8 | uint32(data[2])<<16 | uint32(data[3])<<24
}
