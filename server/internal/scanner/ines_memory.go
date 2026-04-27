package scanner

// NormalizeINESHeaderBytes inspects the first 16 bytes of an iNES ROM
// buffer and zeroes the reserved bytes (8-15) when they're polluted by
// tool-provenance markers (e.g. "DiskDude!", "NI2.1"). Returns true if
// any byte was modified.
//
// The on-disk file is never touched; this operates purely on the buffer
// the caller provides. Use this on the serve path so strict cores like
// nestopia accept ROMs whose dumpers wrote ASCII trailers into the
// iNES 1.0 reserved bytes — without rewriting the user's ROM library.
//
// No-op cases (returns false, leaves buffer untouched):
//   - buffer shorter than 16 bytes
//   - bytes 0-3 are not the iNES magic ("NES\x1a")
//   - byte 7 bits 2-3 == 0b10 (NES 2.0 — bytes 8-15 are meaningful)
//   - bytes 8-15 are already all zero
func NormalizeINESHeaderBytes(header []byte) bool {
	if len(header) < iNESHeaderSize {
		return false
	}
	if header[0] != iNESMagic[0] || header[1] != iNESMagic[1] ||
		header[2] != iNESMagic[2] || header[3] != iNESMagic[3] {
		return false
	}
	if header[7]&0x0C == 0x08 {
		return false
	}
	dirty := false
	for i := 8; i < 16; i++ {
		if header[i] != 0 {
			dirty = true
			break
		}
	}
	if !dirty {
		return false
	}
	for i := 8; i < 16; i++ {
		header[i] = 0
	}
	return true
}
