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
	GameName string // e.g., "Castlevania (USA)"
	ROMName  string // e.g., "Castlevania (USA).nes"
	CRC      string // uppercase hex, e.g., "03225522"
	Size     int64
}

// DATIndex holds a parsed DAT file indexed by CRC for fast lookups.
type DATIndex struct {
	byCRC map[string]DATEntry
}

var (
	reQuotedValue = regexp.MustCompile(`"([^"]+)"`)
	reROMSize     = regexp.MustCompile(`\bsize\s+(\d+)`)
	reROMCRC      = regexp.MustCompile(`\bcrc\s+([0-9A-Fa-f]+)`)
)

// ParseDAT parses a CLRMamePro format DAT file and returns a CRC-indexed lookup.
func ParseDAT(r io.Reader) (*DATIndex, error) {
	idx := &DATIndex{byCRC: make(map[string]DATEntry)}

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
