package scraper

import (
	"fmt"
	"hash/crc32"
	"io"
	"os"
	"strings"
)

// computeFileCRC32 computes the CRC32 (IEEE polynomial) of the file at path.
// Returns the CRC as an uppercase hex string (e.g., "03225522").
func computeFileCRC32(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", fmt.Errorf("opening file: %w", err)
	}
	defer f.Close()

	h := crc32.NewIEEE()
	if _, err := io.Copy(h, f); err != nil {
		return "", fmt.Errorf("computing CRC32: %w", err)
	}

	return strings.ToUpper(fmt.Sprintf("%08x", h.Sum32())), nil
}
