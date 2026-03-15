package scraper

import (
	"bufio"
	"io"
	"regexp"
	"strconv"
	"strings"
)

// DATEntry represents a single game entry from a No-Intro DAT file.
type DATEntry struct {
	GameName    string // e.g., "Castlevania (USA)" (the `name` field)
	Description string // e.g., "Metal Slug - Super Vehicle-001" (MAME/FBNeo `description` field)
	ROMName     string // e.g., "Castlevania (USA).nes"
	CRC         string // uppercase hex, e.g., "03225522"
	Size        int64
}

// DATIndex holds a parsed DAT file indexed by CRC for fast lookups.
// Also supports name-based lookup for MAME/FBNeo DAT files where
// the `name` field is the short ROM name and `description` is the full title.
type DATIndex struct {
	byCRC  map[string]DATEntry
	byName map[string]DATEntry // short ROM name → entry (for MAME DATs)
}

var (
	reQuotedValue = regexp.MustCompile(`"([^"]+)"`)
	reROMSize     = regexp.MustCompile(`\bsize\s+(\d+)`)
	reROMCRC      = regexp.MustCompile(`\bcrc\s+([0-9A-Fa-f]+)`)
)

// ParseDAT parses a CLRMamePro format DAT file and returns a CRC-indexed lookup.
// LookupName looks up a game entry by its short ROM name (e.g., "mslug").
// Returns the entry and true if found. Used for MAME/FBNeo DATs where
// the `name` field is the ZIP filename and `description` is the full title.
func (idx *DATIndex) LookupName(name string) (DATEntry, bool) {
	entry, ok := idx.byName[strings.ToLower(name)]
	return entry, ok
}

// ParseDAT parses a CLRMamePro format DAT file and returns a CRC-indexed lookup.
func ParseDAT(r io.Reader) (*DATIndex, error) {
	idx := &DATIndex{byCRC: make(map[string]DATEntry), byName: make(map[string]DATEntry)}

	scanner := bufio.NewScanner(r)
	// Increase buffer for potentially long lines
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)

	var inGame bool
	var entry DATEntry

	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())

		if strings.HasPrefix(line, "game (") || line == "game (" {
			inGame = true
			entry = DATEntry{}
			continue
		}

		if inGame && line == ")" {
			// End of game block — store if we have a valid entry
			if entry.CRC != "" && entry.ROMName != "" {
				idx.byCRC[entry.CRC] = entry
			}
			// Also index by name for MAME/FBNeo DATs (name → description lookup)
			if entry.GameName != "" {
				idx.byName[strings.ToLower(entry.GameName)] = entry
			}
			inGame = false
			continue
		}

		if !inGame {
			continue
		}

		// Inside a game block
		if strings.HasPrefix(line, "name ") && entry.GameName == "" {
			if m := reQuotedValue.FindStringSubmatch(line); m != nil {
				entry.GameName = m[1]
			}
		} else if strings.HasPrefix(line, "description ") && entry.Description == "" {
			if m := reQuotedValue.FindStringSubmatch(line); m != nil {
				entry.Description = m[1]
			}
		} else if strings.HasPrefix(line, "rom (") || strings.HasPrefix(line, "rom(") {
			if m := reQuotedValue.FindStringSubmatch(line); m != nil {
				entry.ROMName = m[1]
			}
			if m := reROMCRC.FindStringSubmatch(line); m != nil {
				entry.CRC = strings.ToUpper(m[1])
			}
			if m := reROMSize.FindStringSubmatch(line); m != nil {
				entry.Size, _ = strconv.ParseInt(m[1], 10, 64)
			}
		}
	}

	if err := scanner.Err(); err != nil {
		return nil, err
	}

	return idx, nil
}

// LookupCRC returns the DAT entry matching the given CRC (uppercase hex), if any.
func (idx *DATIndex) LookupCRC(crc string) (DATEntry, bool) {
	entry, ok := idx.byCRC[strings.ToUpper(crc)]
	return entry, ok
}
