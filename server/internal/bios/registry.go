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
// MD5 checksums sourced from libretro core-info:
// https://github.com/libretro/libretro-core-info
var registry = []Entry{
	// PlayStation (PSX) — mednafen_psx_hw_libretro.info
	{ConsoleID: "psx", FileName: "scph5500.bin", Description: "PlayStation BIOS (Japan)", MD5: "8dd7d5296a650fac7319bce665a6a53c", Required: false},
	{ConsoleID: "psx", FileName: "scph5501.bin", Description: "PlayStation BIOS (North America)", MD5: "490f666e1afb15b7362b406ed1cea246", Required: true},
	{ConsoleID: "psx", FileName: "scph5502.bin", Description: "PlayStation BIOS (Europe)", MD5: "32736f17079d0b2b7024407c39bd3050", Required: false},

	// PlayStation 2 (PS2) — pcsx2_libretro.info
	// PCSX2 auto-detects BIOS files; no strict filename or MD5 enforced by core.
	// Common BIOS models listed for user guidance.
	{ConsoleID: "ps2", FileName: "SCPH-70012.bin", Description: "PS2 BIOS v12 (North America)", MD5: "", Required: true},
	{ConsoleID: "ps2", FileName: "SCPH-39001.bin", Description: "PS2 BIOS v7 (North America)", MD5: "", Required: false},
	{ConsoleID: "ps2", FileName: "SCPH-70004.bin", Description: "PS2 BIOS v12 (Europe)", MD5: "", Required: false},
	{ConsoleID: "ps2", FileName: "SCPH-70000.bin", Description: "PS2 BIOS v12 (Japan)", MD5: "", Required: false},

	// Sega Saturn (SAT) — mednafen_saturn_libretro.info
	{ConsoleID: "sat", FileName: "sega_101.bin", Description: "Saturn BIOS (Japan)", MD5: "85ec9ca47d8f6807718151cbcca8b964", Required: false},
	{ConsoleID: "sat", FileName: "mpr-17933.bin", Description: "Saturn BIOS (North America/Europe)", MD5: "3240872c70984b6cbfda1586cab68dbe", Required: true},

	// Sega CD (SCD) — genesis_plus_gx_libretro.info (no MD5 provided by core-info)
	{ConsoleID: "scd", FileName: "bios_CD_U.bin", Description: "Sega CD BIOS (North America)", MD5: "", Required: true},
	{ConsoleID: "scd", FileName: "bios_CD_E.bin", Description: "Sega CD BIOS (Europe)", MD5: "", Required: false},
	{ConsoleID: "scd", FileName: "bios_CD_J.bin", Description: "Sega CD BIOS (Japan)", MD5: "", Required: false},

	// Dreamcast (DC) — flycast_libretro.info
	{ConsoleID: "dc", FileName: "dc_boot.bin", Description: "Dreamcast BIOS", MD5: "e10c53c2f8b90bab96ead2d368858623", Required: true},
	{ConsoleID: "dc", FileName: "dc_flash.bin", Description: "Dreamcast Flash ROM", MD5: "", Required: false},

	// Game Boy Advance (GBA) — mgba_libretro.info
	{ConsoleID: "gba", FileName: "gba_bios.bin", Description: "Game Boy Advance BIOS", MD5: "a860e8c0b6d573d191e4ec7db1b1e4f6", Required: false},

	// Nintendo DS (NDS) — desmume_libretro.info
	{ConsoleID: "nds", FileName: "bios7.bin", Description: "Nintendo DS ARM7 BIOS", MD5: "df692a80a5b1bc90728bc3dfc76cd948", Required: false},
	{ConsoleID: "nds", FileName: "bios9.bin", Description: "Nintendo DS ARM9 BIOS", MD5: "a392174eb3e572fed6447e956bde4b25", Required: false},
	{ConsoleID: "nds", FileName: "firmware.bin", Description: "Nintendo DS Firmware", MD5: "145eaef5bd3037cbc247c213bb3da1b3", Required: false},

	// PC Engine / TurboGrafx-16 (PCE) — mednafen_pce_libretro.info
	{ConsoleID: "pce", FileName: "syscard3.pce", Description: "PC Engine CD System Card 3.0", MD5: "38179df8f4ac870017db21ebcbf53114", Required: true},

	// TurboGrafx-CD (PCECD) — same BIOS as PCE, required for CD games
	{ConsoleID: "pcecd", FileName: "syscard3.pce", Description: "PC Engine CD System Card 3.0", MD5: "38179df8f4ac870017db21ebcbf53114", Required: true},
}

// repoFolders maps console IDs to their folder name in the
// Abdess/retroarch_system GitHub repository.
var repoFolders = map[string]string{
	"psx": "Sony - PlayStation",
	"sat": "Sega - Saturn",
	"scd": "Sega - Mega CD - Sega CD",
	"dc":  "Sega - Dreamcast",
	"gba": "Nintendo - Game Boy Advance",
	"nds": "Nintendo - Nintendo DS",
	"pce":  "NEC - PC Engine - TurboGrafx 16 - SuperGrafx",
	"pcecd": "NEC - PC Engine - TurboGrafx 16 - SuperGrafx",
	// ps2 is not available in the repository
}

// RepoFolder returns the repository folder name for the given console ID,
// or an empty string if the console has no downloadable BIOS files.
func RepoFolder(consoleID string) string {
	return repoFolders[consoleID]
}

// Downloadable returns all registry entries whose console has a known
// repository folder, i.e. entries that can be auto-downloaded.
func Downloadable() []Entry {
	var out []Entry
	for _, e := range registry {
		if repoFolders[e.ConsoleID] != "" {
			out = append(out, e)
		}
	}
	return out
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

// ByMD5 returns the first entry matching the given MD5 checksum, or nil if none.
func ByMD5(md5 string) *Entry {
	if md5 == "" {
		return nil
	}
	for _, e := range registry {
		if e.MD5 != "" && e.MD5 == md5 {
			cp := e
			return &cp
		}
	}
	return nil
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
