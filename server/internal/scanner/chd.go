package scanner

import (
	"encoding/binary"
	"fmt"
	"os"
)

// CHD file format constants.
const (
	chdV5HeaderSize    = 124
	chdV5MetaOffset    = 104 // offset of the metadata offset field in a v5 header
	chdMetaEntrySize   = 16  // 4-byte tag + 4-byte flags/length + 8-byte next
	chdMetaNone        = 0   // sentinel: no more metadata entries
)

// CD metadata tags that indicate the CHD was created with createcd.
var cdMetadataTags = map[[4]byte]bool{
	{'C', 'H', 'C', 'D'}: true, // CHCD  - CD metadata
	{'C', 'H', 'T', 'R'}: true, // CHTR  - CD track metadata
	{'C', 'H', 'T', '2'}: true, // CHT2  - CD track v2 metadata
	{'C', 'H', 'G', 'D'}: true, // CHGD  - GD-ROM metadata
}

// DetectCHDMode inspects a CHD file and returns the creation mode:
//   - "createcd"  if CD metadata tags are present
//   - "createdvd" if it is a valid CHD with no CD metadata
//   - ""          if the file is not a CHD or cannot be read
func DetectCHDMode(filePath string) string {
	f, err := os.Open(filePath)
	if err != nil {
		return ""
	}
	defer f.Close()

	// Read and verify the CHD signature ("MComprHD").
	var sig [8]byte
	if _, err := f.Read(sig[:]); err != nil {
		return ""
	}
	if string(sig[:]) != "MComprHD" {
		return ""
	}

	// Read the header length and version (both big-endian uint32).
	var headerLen, version uint32
	if err := binary.Read(f, binary.BigEndian, &headerLen); err != nil {
		return ""
	}
	if err := binary.Read(f, binary.BigEndian, &version); err != nil {
		return ""
	}

	// Only CHD v5 is handled (v5 is the current format used by chdman).
	if version != 5 {
		return ""
	}

	// Read the metadata offset from the v5 header.
	if _, err := f.Seek(int64(chdV5MetaOffset), 0); err != nil {
		return ""
	}
	var metaOffset uint64
	if err := binary.Read(f, binary.BigEndian, &metaOffset); err != nil {
		return ""
	}

	// Walk the metadata linked list.
	for metaOffset != 0 {
		if _, err := f.Seek(int64(metaOffset), 0); err != nil {
			return ""
		}

		var tag [4]byte
		if _, err := f.Read(tag[:]); err != nil {
			return ""
		}

		if cdMetadataTags[tag] {
			return "createcd"
		}

		// Read flags+length (4 bytes, not used) then the next pointer.
		var flagsLen uint32
		if err := binary.Read(f, binary.BigEndian, &flagsLen); err != nil {
			return ""
		}
		var next uint64
		if err := binary.Read(f, binary.BigEndian, &next); err != nil {
			return ""
		}

		metaOffset = next
	}

	return "createdvd"
}

// PSPCHDAchievementsWarning returns the warning message for PSP CHD files
// created with createdvd mode (or empty string if no warning is needed).
func PSPCHDAchievementsWarning(filePath, consoleAbbrev string) string {
	if consoleAbbrev != "PSP" {
		return ""
	}

	ext := fileExt(filePath)
	if ext == ".cso" {
		return "This PSP game is in CSO format, which does not support RetroAchievements. " +
			"Convert it to ISO format for achievement compatibility."
	}
	if ext != ".chd" {
		return ""
	}

	mode := DetectCHDMode(filePath)
	if mode == "createdvd" {
		return fmt.Sprintf(
			"This PSP CHD was created with 'createdvd' mode, which is incompatible with RetroAchievements. " +
				"Re-create it with 'chdman createcd' or convert the original to ISO format for achievement support.",
		)
	}

	return ""
}

// fileExt returns the lowercase extension of a file path.
func fileExt(path string) string {
	for i := len(path) - 1; i >= 0; i-- {
		if path[i] == '.' {
			ext := path[i:]
			// lowercase
			b := make([]byte, len(ext))
			for j := 0; j < len(ext); j++ {
				c := ext[j]
				if c >= 'A' && c <= 'Z' {
					c += 32
				}
				b[j] = c
			}
			return string(b)
		}
		if path[i] == '/' || path[i] == '\\' {
			break
		}
	}
	return ""
}
