package patcher

import (
	"encoding/binary"
	"fmt"
)

// IPS format specification:
// Header: "PATCH" (5 bytes)
// Records: [offset(3 bytes)][size(2 bytes)][data(size bytes)]
//   If size == 0 (RLE): [rle_size(2 bytes)][rle_value(1 byte)]
// EOF marker: "EOF" (3 bytes) at offset position
//
// IPS32 extends offsets to 4 bytes and uses "IPS32" header / "EEOF" marker.

const (
	ipsHeader = "PATCH"
	ipsEOF    = "EOF"

	ips32Header = "IPS32"
	ips32EOF    = "EEOF"
)

// ApplyIPS applies an IPS or IPS32 patch to ROM data.
func ApplyIPS(romData, patchData []byte) ([]byte, error) {
	if len(patchData) < 5 {
		return nil, fmt.Errorf("IPS patch too short")
	}

	header := string(patchData[:5])
	switch header {
	case ipsHeader:
		return applyIPSStandard(romData, patchData)
	case ips32Header:
		return applyIPS32(romData, patchData)
	default:
		return nil, fmt.Errorf("invalid IPS header: expected %q or %q, got %q", ipsHeader, ips32Header, header)
	}
}

func applyIPSStandard(romData, patchData []byte) ([]byte, error) {
	result := make([]byte, len(romData))
	copy(result, romData)

	pos := 5 // skip "PATCH"
	for {
		if pos+3 > len(patchData) {
			return nil, fmt.Errorf("IPS patch truncated at offset %d", pos)
		}

		// Check for EOF marker
		if string(patchData[pos:pos+3]) == ipsEOF {
			break
		}

		// Read 3-byte offset
		offset := int(patchData[pos])<<16 | int(patchData[pos+1])<<8 | int(patchData[pos+2])
		pos += 3

		if pos+2 > len(patchData) {
			return nil, fmt.Errorf("IPS patch truncated reading size at offset %d", pos)
		}

		// Read 2-byte size
		size := int(binary.BigEndian.Uint16(patchData[pos : pos+2]))
		pos += 2

		if size == 0 {
			// RLE record
			if pos+3 > len(patchData) {
				return nil, fmt.Errorf("IPS patch truncated reading RLE at offset %d", pos)
			}
			rleSize := int(binary.BigEndian.Uint16(patchData[pos : pos+2]))
			pos += 2
			rleValue := patchData[pos]
			pos++

			// Expand result if needed
			if offset+rleSize > MaxOutputSize {
				return nil, fmt.Errorf("IPS patch output would exceed maximum size (%d bytes)", MaxOutputSize)
			}
			if offset+rleSize > len(result) {
				expanded := make([]byte, offset+rleSize)
				copy(expanded, result)
				result = expanded
			}
			for i := 0; i < rleSize; i++ {
				result[offset+i] = rleValue
			}
		} else {
			// Normal record
			if pos+size > len(patchData) {
				return nil, fmt.Errorf("IPS patch truncated reading data at offset %d", pos)
			}

			// Expand result if needed
			if offset+size > MaxOutputSize {
				return nil, fmt.Errorf("IPS patch output would exceed maximum size (%d bytes)", MaxOutputSize)
			}
			if offset+size > len(result) {
				expanded := make([]byte, offset+size)
				copy(expanded, result)
				result = expanded
			}
			copy(result[offset:], patchData[pos:pos+size])
			pos += size
		}
	}

	// Check for optional truncation marker after EOF
	pos += 3 // skip "EOF"
	if pos+3 <= len(patchData) {
		truncSize := int(patchData[pos])<<16 | int(patchData[pos+1])<<8 | int(patchData[pos+2])
		if truncSize < len(result) {
			result = result[:truncSize]
		}
	}

	return result, nil
}

func applyIPS32(romData, patchData []byte) ([]byte, error) {
	result := make([]byte, len(romData))
	copy(result, romData)

	pos := 5 // skip "IPS32"
	for {
		if pos+4 > len(patchData) {
			return nil, fmt.Errorf("IPS32 patch truncated at offset %d", pos)
		}

		// Check for EEOF marker
		if string(patchData[pos:pos+4]) == ips32EOF {
			break
		}

		// Read 4-byte offset
		offset := int(binary.BigEndian.Uint32(patchData[pos : pos+4]))
		pos += 4

		if pos+2 > len(patchData) {
			return nil, fmt.Errorf("IPS32 patch truncated reading size at offset %d", pos)
		}

		// Read 2-byte size
		size := int(binary.BigEndian.Uint16(patchData[pos : pos+2]))
		pos += 2

		if size == 0 {
			// RLE record
			if pos+3 > len(patchData) {
				return nil, fmt.Errorf("IPS32 patch truncated reading RLE at offset %d", pos)
			}
			rleSize := int(binary.BigEndian.Uint16(patchData[pos : pos+2]))
			pos += 2
			rleValue := patchData[pos]
			pos++

			if offset+rleSize > MaxOutputSize {
				return nil, fmt.Errorf("IPS32 patch output would exceed maximum size (%d bytes)", MaxOutputSize)
			}
			if offset+rleSize > len(result) {
				expanded := make([]byte, offset+rleSize)
				copy(expanded, result)
				result = expanded
			}
			for i := 0; i < rleSize; i++ {
				result[offset+i] = rleValue
			}
		} else {
			if pos+size > len(patchData) {
				return nil, fmt.Errorf("IPS32 patch truncated reading data at offset %d", pos)
			}

			if offset+size > MaxOutputSize {
				return nil, fmt.Errorf("IPS32 patch output would exceed maximum size (%d bytes)", MaxOutputSize)
			}
			if offset+size > len(result) {
				expanded := make([]byte, offset+size)
				copy(expanded, result)
				result = expanded
			}
			copy(result[offset:], patchData[pos:pos+size])
			pos += size
		}
	}

	return result, nil
}
