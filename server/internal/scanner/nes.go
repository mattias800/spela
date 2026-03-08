package scanner

import (
	"fmt"
	"log/slog"
	"os"
)

// iNES header constants.
const (
	iNESHeaderSize  = 16
	iNESMagicSize   = 4
	prgROMBankSize  = 16384 // 16 KB
	chrROMBankSize  = 8192  // 8 KB
	trainerSize     = 512
)

// iNES magic bytes: "NES\x1a"
var iNESMagic = [4]byte{'N', 'E', 'S', 0x1A}

// SanitizeNESHeader checks an NES ROM file for a dirty iNES header and
// sanitizes it in-place by zeroing out padding bytes 8-15 when they contain
// non-zero values (tool signatures like "DiskDude!", "NI2.1", etc.).
//
// Dirty headers are the #1 cause of EmulatorJS/libretro NES ROM load failures
// because non-zero padding bytes can be misinterpreted as NES 2.0 extended
// fields, causing incorrect mapper/PRG/CHR size calculations.
//
// Returns:
//   - "sanitized" if the header was dirty and has been cleaned
//   - "clean" if the header was already valid
//   - "" if the file is not an NES ROM or could not be processed
//
// NES 2.0 headers (identified by bits 2-3 of byte 7 == 0b10) are left untouched.
func SanitizeNESHeader(filePath string) string {
	f, err := os.OpenFile(filePath, os.O_RDWR, 0)
	if err != nil {
		return ""
	}
	defer f.Close()

	// Read the 16-byte header.
	var header [iNESHeaderSize]byte
	if _, err := f.ReadAt(header[:], 0); err != nil {
		return ""
	}

	// Verify iNES magic.
	if header[0] != iNESMagic[0] || header[1] != iNESMagic[1] ||
		header[2] != iNESMagic[2] || header[3] != iNESMagic[3] {
		return ""
	}

	// NES 2.0 check: bits 2-3 of byte 7 == 0b10.
	// NES 2.0 uses bytes 8-15 for extended fields — do not sanitize.
	if header[7]&0x0C == 0x08 {
		return "clean"
	}

	// Check if padding bytes 8-15 are dirty.
	dirty := false
	for i := 8; i < 16; i++ {
		if header[i] != 0 {
			dirty = true
			break
		}
	}

	if !dirty {
		return "clean"
	}

	// Zero out bytes 8-15.
	slog.Info("sanitizing dirty iNES header", "file", filePath,
		"padding", fmt.Sprintf("%x", header[8:16]))
	for i := 8; i < 16; i++ {
		header[i] = 0
	}
	if _, err := f.WriteAt(header[8:16], 8); err != nil {
		slog.Warn("failed to write sanitized iNES header", "file", filePath, "error", err)
		return ""
	}

	return "sanitized"
}

// ValidateNESHeader performs structural validation on an NES ROM file.
// Returns an error describing why the ROM is structurally invalid, or nil if valid.
//
// Checks performed:
//   - Magic bytes present
//   - PRG ROM bank count > 0
//   - File size is at least large enough to contain the declared PRG+CHR data
func ValidateNESHeader(filePath string) error {
	f, err := os.Open(filePath)
	if err != nil {
		return fmt.Errorf("opening file: %w", err)
	}
	defer f.Close()

	info, err := f.Stat()
	if err != nil {
		return fmt.Errorf("stat file: %w", err)
	}

	if info.Size() < iNESHeaderSize {
		return fmt.Errorf("file too small for iNES header (%d bytes)", info.Size())
	}

	var header [iNESHeaderSize]byte
	if _, err := f.Read(header[:]); err != nil {
		return fmt.Errorf("reading header: %w", err)
	}

	if header[0] != iNESMagic[0] || header[1] != iNESMagic[1] ||
		header[2] != iNESMagic[2] || header[3] != iNESMagic[3] {
		return fmt.Errorf("invalid iNES magic bytes")
	}

	prgBanks := int64(header[4])
	if prgBanks == 0 {
		return fmt.Errorf("PRG ROM bank count is zero")
	}

	chrBanks := int64(header[5])
	hasTrainer := header[6]&0x04 != 0

	expectedMinSize := int64(iNESHeaderSize) + prgBanks*prgROMBankSize + chrBanks*chrROMBankSize
	if hasTrainer {
		expectedMinSize += trainerSize
	}

	if info.Size() < expectedMinSize {
		return fmt.Errorf("file too small: %d bytes, expected at least %d (header=%d + PRG=%dx16KB + CHR=%dx8KB)",
			info.Size(), expectedMinSize, iNESHeaderSize, prgBanks, chrBanks)
	}

	return nil
}
