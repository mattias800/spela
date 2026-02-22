package bios

// Entry represents a known BIOS file in the registry.
type Entry struct {
	ConsoleID   string // lowercase abbreviation, e.g. "psx"
	FileName    string // expected filename, e.g. "scph5501.bin"
	Description string // human-readable label
	MD5         string // expected MD5 checksum (lowercase hex)
	Required    bool   // true if the console cannot function without it
}

// registry is the built-in list of known BIOS files.
var registry = []Entry{
	// PlayStation (PSX)
	{ConsoleID: "psx", FileName: "scph5500.bin", Description: "PlayStation BIOS (Japan)", MD5: "8dd7d5296a650fac7319bce665a6a53c", Required: false},
	{ConsoleID: "psx", FileName: "scph5501.bin", Description: "PlayStation BIOS (North America)", MD5: "924e392ed05558ffdb115408c263dccf", Required: true},
	{ConsoleID: "psx", FileName: "scph5502.bin", Description: "PlayStation BIOS (Europe)", MD5: "e56ec1b027e00571a0e4cd3e0aadb4c0", Required: false},

	// Sega Saturn (SAT)
	{ConsoleID: "sat", FileName: "saturn_bios.bin", Description: "Sega Saturn BIOS", MD5: "af5828fdfc0d3f41e2f8b2edc4c6f9e8", Required: true},

	// Sega CD (SCD)
	{ConsoleID: "scd", FileName: "bios_CD_U.bin", Description: "Sega CD BIOS (North America)", MD5: "2efd74e3232ff260e371b99f84024f7f", Required: true},
	{ConsoleID: "scd", FileName: "bios_CD_E.bin", Description: "Sega CD BIOS (Europe)", MD5: "e66fa1dc5820d254611fdcdba0662372", Required: false},
	{ConsoleID: "scd", FileName: "bios_CD_J.bin", Description: "Sega CD BIOS (Japan)", MD5: "278a9397d192149e84e820ac621a8edd", Required: false},

	// Dreamcast (DC)
	{ConsoleID: "dc", FileName: "dc_boot.bin", Description: "Dreamcast BIOS", MD5: "e10c53c2f8b90bab96ead2d368858623", Required: true},
	{ConsoleID: "dc", FileName: "dc_flash.bin", Description: "Dreamcast Flash ROM", MD5: "0a93f7940c455905bea6e392dfde92a4", Required: false},

	// Game Boy Advance (GBA)
	{ConsoleID: "gba", FileName: "gba_bios.bin", Description: "Game Boy Advance BIOS", MD5: "a860e8c0b6d573d191e4ec7db1b1e4f6", Required: false},

	// Nintendo DS (NDS)
	{ConsoleID: "nds", FileName: "bios7.bin", Description: "Nintendo DS ARM7 BIOS", MD5: "df692a80a5b1bc90728bc3dfc76cd948", Required: false},
	{ConsoleID: "nds", FileName: "bios9.bin", Description: "Nintendo DS ARM9 BIOS", MD5: "a392174eb3e572fed6447e956bde4b25", Required: false},
	{ConsoleID: "nds", FileName: "firmware.bin", Description: "Nintendo DS Firmware", MD5: "145eaef5bd3037cbc247c213bb3da1b3", Required: false},

	// PC Engine / TurboGrafx-16 (PCE)
	{ConsoleID: "pce", FileName: "syscard3.pce", Description: "PC Engine CD System Card 3.0", MD5: "38179df8f4ac870017db21ebcbf53114", Required: true},
}

// All returns every entry in the registry.
func All() []Entry {
	out := make([]Entry, len(registry))
	copy(out, registry)
	return out
}

// ByFileName returns all entries matching the given filename.
func ByFileName(name string) []Entry {
	var matches []Entry
	for _, e := range registry {
		if e.FileName == name {
			matches = append(matches, e)
		}
	}
	return matches
}

// ByConsole returns all entries for a given console ID (lowercase abbreviation).
func ByConsole(consoleID string) []Entry {
	var matches []Entry
	for _, e := range registry {
		if e.ConsoleID == consoleID {
			matches = append(matches, e)
		}
	}
	return matches
}

// ConsoleIDs returns the unique set of console IDs present in the registry.
func ConsoleIDs() []string {
	seen := make(map[string]bool)
	var ids []string
	for _, e := range registry {
		if !seen[e.ConsoleID] {
			seen[e.ConsoleID] = true
			ids = append(ids, e.ConsoleID)
		}
	}
	return ids
}
