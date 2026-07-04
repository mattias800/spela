package bios

import "path/filepath"

// Entry represents a known BIOS file in the registry.
type Entry struct {
	ConsoleID   string // lowercase abbreviation, e.g. "psx"
	FileName    string // expected filename, e.g. "scph5501.bin"
	Description string // human-readable label
	MD5         string // expected MD5 checksum (lowercase hex)
	Required    bool   // true if the console cannot function without it
	OverrideURL string // if set, download from this URL instead of the default repo
	SubDir      string // subdirectory within system_dir, e.g. "same_cdi/bios" (empty = flat)

	// Bundle indicates that OverrideURL points at a .zip archive
	// containing a directory tree rather than a single file. When
	// true, the downloader fetches the archive, extracts every file
	// into <biosDir>/<SubDir>/, then deletes the archive. FileName is
	// interpreted as a *sentinel path* (relative to SubDir) used to
	// detect whether the bundle has been installed — pick a file
	// that's guaranteed to exist after extraction (e.g. for PPSSPP,
	// "flash0/font/jpn0.pgf" or similar).
	//
	// Bundle entries skip MD5 validation on the archive bytes (the
	// archive's own integrity is checked by the unzip step; the
	// extracted files don't have individual MD5 checks). Set MD5 on
	// a bundle entry only if you've pinned the archive's hash.
	//
	// See #911 for the PPSSPP flash0/lang/assets driver.
	Bundle bool

	// StripPrefix, when non-empty on a Bundle entry, drops that
	// leading path prefix from each archive entry before joining
	// it onto the extract root. Used for archives whose contents
	// already sit under a wrapper directory matching SubDir — the
	// libretro PPSSPP buildbot zip is rooted at `PPSSPP/`, and
	// our SubDir is also `PPSSPP`, so without stripping we'd get
	// `<biosDir>/PPSSPP/PPSSPP/...`. Archive entries that don't
	// start with the prefix are skipped (a defensive choice — an
	// archive with mixed contents would otherwise leak files
	// outside SubDir).
	StripPrefix string
}

// FilePath returns the path of this entry relative to the BIOS directory,
// taking SubDir into account.
func (e Entry) FilePath(biosDir string) string {
	if e.SubDir != "" {
		return filepath.Join(biosDir, e.SubDir, e.FileName)
	}
	return filepath.Join(biosDir, e.FileName)
}

// registry is the built-in list of known BIOS files.
// MD5 checksums sourced from libretro core-info:
// https://github.com/libretro/libretro-core-info
var registry = []Entry{
	// PlayStation (PSX) — mednafen_psx_hw_libretro.info
	{ConsoleID: "psx", FileName: "scph5500.bin", Description: "PlayStation BIOS (Japan)", MD5: "8dd7d5296a650fac7319bce665a6a53c", Required: false},
	{ConsoleID: "psx", FileName: "scph5501.bin", Description: "PlayStation BIOS (North America)", MD5: "490f666e1afb15b7362b406ed1cea246", Required: true},
	{ConsoleID: "psx", FileName: "scph5502.bin", Description: "PlayStation BIOS (Europe)", MD5: "32736f17079d0b2b7024407c39bd3050", Required: false},

	// PlayStation 2 (PS2) — Play! core
	// Spela accepts common PS2 BIOS names here; no strict filename or MD5 is enforced.
	// Common BIOS models listed for user guidance.
	{ConsoleID: "ps2", FileName: "SCPH-39001.bin", Description: "PS2 BIOS v7 (North America)", MD5: "", Required: true},
	{ConsoleID: "ps2", FileName: "SCPH-70012.bin", Description: "PS2 BIOS v12 (North America)", MD5: "", Required: false},
	{ConsoleID: "ps2", FileName: "SCPH-70004.bin", Description: "PS2 BIOS v12 (Europe)", MD5: "", Required: false},
	{ConsoleID: "ps2", FileName: "SCPH-70000.bin", Description: "PS2 BIOS v12 (Japan)", MD5: "", Required: false},

	// Sega Saturn (SAT) — mednafen_saturn_libretro.info
	{ConsoleID: "sat", FileName: "sega_101.bin", Description: "Saturn BIOS (Japan)", MD5: "85ec9ca47d8f6807718151cbcca8b964", Required: false, OverrideURL: "https://archive.org/download/mame-0.221-roms-merged/saturn.zip/saturnjp%2Fsega_101.bin"},
	{ConsoleID: "sat", FileName: "mpr-17933.bin", Description: "Saturn BIOS (North America/Europe)", MD5: "3240872c70984b6cbfda1586cab68dbe", Required: true, OverrideURL: "https://archive.org/download/mame-0.221-roms-merged/saturn.zip/mpr-17933.bin"},

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

	// Neo Geo (NEOGEO) — fbneo_libretro.info
	// neogeo.zip must contain the individual BIOS ROMs (sp-s2.sp1, etc.)
	{ConsoleID: "neogeo", FileName: "neogeo.zip", Description: "Neo Geo BIOS (arcade/AES/MVS)", MD5: "", Required: true, OverrideURL: "https://archive.org/download/real-bout-fatal-fury-world-cdz-patched/neogeo.zip"},

	// Arcade (ARCADE) — fbneo_libretro.info
	// FBNeo needs the Neo Geo BIOS for Neo Geo arcade games.
	{ConsoleID: "arcade", FileName: "neogeo.zip", Description: "Neo Geo BIOS (for Neo Geo arcade games)", MD5: "", Required: false, OverrideURL: "https://archive.org/download/real-bout-fatal-fury-world-cdz-patched/neogeo.zip"},

	// Neo Geo CD (NEOCD) — neocd_libretro.info
	// NeoCD core looks for BIOS in a "neocd" subdirectory under system_dir
	{ConsoleID: "neocd", FileName: "neocdz.zip", Description: "Neo Geo CDZ BIOS", MD5: "", Required: true, OverrideURL: "https://archive.org/download/real-bout-fatal-fury-world-cdz-patched/neocdz.zip", SubDir: "neocd"},
	{ConsoleID: "neocd", FileName: "neocd.zip", Description: "Neo Geo CD Front Loader BIOS", MD5: "", Required: false, OverrideURL: "https://archive.org/download/real-bout-fatal-fury-world-cdz-patched/neocd.zip", SubDir: "neocd"},

	// Atari Lynx (LYNX) — handy_libretro.info
	{ConsoleID: "lynx", FileName: "lynxboot.img", Description: "Atari Lynx Boot ROM", MD5: "fcd403db69f54290b51035d82f835e7b", Required: false},

	// 3DO — opera_libretro.info
	{ConsoleID: "3do", FileName: "panafz10.bin", Description: "Panasonic FZ-10 3DO BIOS", MD5: "51f2f43ae2f3508a14d9f56597e2d3ce", Required: true},
	{ConsoleID: "3do", FileName: "panafz1.bin", Description: "Panasonic FZ-1 3DO BIOS", MD5: "f47264dd47fe30f73ab3c010015c155b", Required: false},
	{ConsoleID: "3do", FileName: "goldstar.bin", Description: "Goldstar 3DO BIOS", MD5: "8639fd5e549bd6238cfee79e3e749114", Required: false},

	// Commodore Amiga (AMIGA) — puae_libretro.info
	{ConsoleID: "amiga", FileName: "kick34005.A500", Description: "Amiga 500 Kickstart v1.3 (required)", MD5: "82a21c1890cae844b3df741f2762d48d", Required: true, OverrideURL: "https://archive.org/download/batov39/kick34005.A500"},
	{ConsoleID: "amiga", FileName: "kick40068.A1200", Description: "Amiga 1200 Kickstart v3.1", MD5: "646773759326fbac3b2311fd8c8793ee", Required: false, OverrideURL: "https://archive.org/download/batov39/kick40068.A1200"},
	{ConsoleID: "amiga", FileName: "kick40060.CD32", Description: "Amiga CD32 Kickstart v3.1", MD5: "5f8924d013dd57a89cf349f4cdedc6b1", Required: false, OverrideURL: "https://archive.org/download/batov39/kick40060.CD32"},

	// Amiga Demos (ADEMO) — same core (puae), same BIOS as Amiga
	{ConsoleID: "ademo", FileName: "kick34005.A500", Description: "Amiga 500 Kickstart v1.3 (required)", MD5: "82a21c1890cae844b3df741f2762d48d", Required: true, OverrideURL: "https://archive.org/download/batov39/kick34005.A500"},
	{ConsoleID: "ademo", FileName: "kick40068.A1200", Description: "Amiga 1200 Kickstart v3.1", MD5: "646773759326fbac3b2311fd8c8793ee", Required: false, OverrideURL: "https://archive.org/download/batov39/kick40068.A1200"},
	{ConsoleID: "ademo", FileName: "kick40060.CD32", Description: "Amiga CD32 Kickstart v3.1", MD5: "5f8924d013dd57a89cf349f4cdedc6b1", Required: false, OverrideURL: "https://archive.org/download/batov39/kick40060.CD32"},

	// PC-FX (PCFX) — mednafen_pcfx_libretro.info
	{ConsoleID: "pcfx", FileName: "pcfx.rom", Description: "PC-FX BIOS v1.00", MD5: "08e36edbea28a017f79f8d4f7ff9b6d7", Required: true},

	// ColecoVision (CV) — gearcoleco_libretro.info / bluemsx_libretro.info
	// Repo has BIOS.col; we download and save as colecovision.rom (expected by cores).
	{ConsoleID: "cv", FileName: "colecovision.rom", Description: "ColecoVision BIOS", MD5: "2c66f5911e5b42b8ebe113403548eee7", Required: true, OverrideURL: "https://raw.githubusercontent.com/Abdess/retrobios/main/bios/Coleco/ColecoVision/BIOS.col"},

	// Philips CD-i (CDI) — same_cdi_libretro.info (MAME-based, needs subdirectory)
	// cdimono1 must contain 5 files: 3 main BIOS .rom dumps plus the
	// SERVO and SLAVE MC68HC705C8A microcontroller ROMs (added to MAME
	// in 0.222). Without the 8KB chip ROMs SAME_CDI loads but stays on
	// a black screen — the core can't initialise the disc-drive servo
	// or the front-panel slave processor, so games like Myst never
	// reach the title screen (#939).
	//
	// The Abdess/retrobios mirror only has the 3 main .rom files. The
	// MAME 0.221 merged archive predates 0.222 so it's also incomplete.
	// archive.org's user-uploaded `cdimono1` item has the full 5-file
	// torrentzipped set; pinning the MD5 ensures any older 3-file zip
	// already cached on a server gets re-downloaded.
	{ConsoleID: "cdi", FileName: "cdimono1.zip", Description: "CD-i Mono-I BIOS", MD5: "cfca9b8a96ed810bb3cd5ac11d7d1dda", Required: true, OverrideURL: "https://archive.org/download/cdimono1/cdimono1.zip", SubDir: "same_cdi/bios"},
	{ConsoleID: "cdi", FileName: "cdimono2.zip", Description: "CD-i Mono-II BIOS", MD5: "", Required: false, OverrideURL: "https://archive.org/download/MAME208RomsOnlyMerged/cdimono2.zip", SubDir: "same_cdi/bios"},
	{ConsoleID: "cdi", FileName: "cdibios.zip", Description: "CD-i BIOS (generic)", MD5: "", Required: false, OverrideURL: "https://archive.org/download/MAME208RomsOnlyMerged/cdibios.zip", SubDir: "same_cdi/bios"},

	// Famicom Disk System (FDS) — nestopia_libretro.info. NES proper
	// doesn't need a BIOS, but disksys.rom is mandatory for any .fds
	// disk image to boot. Without this entry registered, the missing-
	// BIOS UI couldn't fire and FDS launches surfaced a generic
	// "Failed to start emulation" error (#891).
	//
	// Source URL was missing originally (#891 only added the entry, not
	// a download path) — caught by the Required→Downloadable invariant
	// test added alongside the #934/#935 fixes.
	{ConsoleID: "fds", FileName: "disksys.rom", Description: "Famicom Disk System BIOS", MD5: "ca30b50f880eb660a320674ed365ef7a", Required: true, OverrideURL: "https://github.com/Abdess/retrobios/raw/main/bios/Nintendo/Famicom%20Disk%20System/disksys.rom"},

	// PlayStation Portable (PSP) — ppsspp_libretro.info.
	//
	// PPSSPP wants a directory tree under `<system_dir>/PPSSPP/`:
	//   - `ppge_atlas.zim` — UI overlay atlas (without it, in-engine
	//     pause / settings overlays render missing glyphs)
	//   - `flash0/` — PSP system fonts (every game with text uses these)
	//   - `lang/` — UI localisation strings
	//   - `vfpu/`, `shaders/`, atlas .zim/.meta pairs, gamecontrollerdb,
	//     compat.ini, etc. — runtime assets the core looks up
	//
	// Source: libretro's buildbot publishes a single canonical zip at
	// `https://buildbot.libretro.com/assets/system/PPSSPP.zip` (this
	// is what RetroArch's "Core System Files Downloader" pulls).
	// Archive contents are rooted at `PPSSPP/`, which already matches
	// the SubDir we want to extract into; StripPrefix drops the
	// wrapper so we don't end up at `<biosDir>/PPSSPP/PPSSPP/...`.
	// Sentinel is the atlas file, which lands at
	// `<biosDir>/PPSSPP/ppge_atlas.zim` after extraction.
	//
	// MD5 is intentionally unset on bundle entries: the archive's
	// contents shift between PPSSPP releases (font tweaks, new
	// languages); pinning the hash would lock us to one buildbot
	// snapshot. The archive's own integrity is implicitly checked by
	// the unzip step.
	//
	// Closes #911.
	{ConsoleID: "psp", FileName: "ppge_atlas.zim", Description: "PPSSPP system assets (atlas, fonts, lang)", Required: true, SubDir: "PPSSPP", OverrideURL: "https://buildbot.libretro.com/assets/system/PPSSPP.zip", Bundle: true, StripPrefix: "PPSSPP/"},

	// Magnavox Odyssey 2 / Philips Videopac (O2) — o2em_libretro.info.
	// o2rom.bin is required by the core to boot any cartridge; the
	// other three are regional / variant BIOSes the core can use when
	// present. MD5s are from o2em_libretro.info's notes block (the
	// canonical libretro reference). #889 added the entries; #934
	// wired up the source URLs and pinned the MD5s.
	//
	// Source: archive.org's MAME 0.221 merged ROM dump. The MAME zip
	// stores the US BIOS as o2bios.rom (root) — we fetch that file
	// and save it under the libretro-expected name o2rom.bin. Variant
	// BIOSes live under per-machine subdirs.
	{ConsoleID: "o2", FileName: "o2rom.bin", Description: "Odyssey 2 BIOS (US, G7000)", MD5: "562d5ebf9e030a40d6fabfc2f33139fd", Required: true, OverrideURL: "https://archive.org/download/mame-0.221-roms-merged/odyssey2.zip/o2bios.rom"},
	{ConsoleID: "o2", FileName: "c52.bin", Description: "Videopac French BIOS (G7000)", MD5: "f1071cdb0b6b10dde94d3bc8a6146387", Required: false, OverrideURL: "https://archive.org/download/mame-0.221-roms-merged/odyssey2.zip/videopac%2Fc52.bin"},
	{ConsoleID: "o2", FileName: "g7400.bin", Description: "Videopac+ European BIOS (G7400)", MD5: "c500ff71236068e0dc0d0603d265ae76", Required: false, OverrideURL: "https://archive.org/download/mame-0.221-roms-merged/odyssey2.zip/g7400%2Fg7400.bin"},
	{ConsoleID: "o2", FileName: "jopac.bin", Description: "JOPAC BIOS (G7400 variant)", MD5: "279008e4a0db2dc5f1c048853b033828", Required: false, OverrideURL: "https://archive.org/download/mame-0.221-roms-merged/odyssey2.zip/jopac%2Fjopac.bin"},

	// Fairchild Channel F (CHAF) — freechaf_libretro.info. Both
	// sl31253.bin and sl31254.bin are required — Channel F can't
	// start without either chip ROM, and the symptom on
	// missing-BIOS-without-registry-entry is a silent black screen
	// (no error, no missing-BIOS prompt). sl90025.bin is technically
	// optional: per the freechaf core notes, when present it
	// supersedes sl31253.bin. #890 added the entries; #934 wired
	// up the source URLs.
	//
	// Source: archive.org's MAME 0.221 merged dump. The MAME zip
	// names the chip ROMs with .rom extension; libretro expects
	// .bin. Same content (MD5 verified), the downloader saves under
	// FileName so the rename happens transparently.
	{ConsoleID: "chaf", FileName: "sl31253.bin", Description: "Channel F system ROM 1 (chip 1)", MD5: "ac9804d4c0e9d07e33472e3726ed15c3", Required: true, OverrideURL: "https://archive.org/download/mame-0.221-roms-merged/channelf.zip/sl31253.rom"},
	{ConsoleID: "chaf", FileName: "sl31254.bin", Description: "Channel F system ROM 2 (chip 2)", MD5: "da98f4bb3242ab80d76629021bb27585", Required: true, OverrideURL: "https://archive.org/download/mame-0.221-roms-merged/channelf.zip/sl31254.rom"},
	{ConsoleID: "chaf", FileName: "sl90025.bin", Description: "Channel F II system ROM", MD5: "95d339631d867c8f1d15a5f2ec26069d", Required: false, OverrideURL: "https://archive.org/download/mame-0.221-roms-merged/channelf.zip/sl90025.rom"},

	// Mattel Intellivision (INTV) — freeintv_libretro.info. Both
	// exec.bin (Executive ROM, 8KB) and grom.bin (Graphics ROM, 2KB)
	// are required — FreeIntv silently fails to boot any cartridge
	// without either, with no error surfaced to the player. ivoice.bin
	// is the SP0256-012 voice synthesis ROM, only used by ~10
	// Intellivoice-enabled titles (B-17 Bomber, Bomb Squad, ...);
	// optional, omitted here because retrobios does not host it.
	// MD5s are from freeintv_libretro.info's notes block. #935.
	//
	// Source: Abdess/retrobios — the canonical libretro mirror that
	// already hosts our ColecoVision and CD-i fallback BIOS files.
	{ConsoleID: "intv", FileName: "exec.bin", Description: "Intellivision Executive ROM", MD5: "62e761035cb657903761800f4437b8af", Required: true, OverrideURL: "https://github.com/Abdess/retrobios/raw/main/bios/Mattel/Intellivision/exec.bin"},
	{ConsoleID: "intv", FileName: "grom.bin", Description: "Intellivision Graphics ROM", MD5: "0cd5946c6473e42e8e4c2137785e427f", Required: true, OverrideURL: "https://github.com/Abdess/retrobios/raw/main/bios/Mattel/Intellivision/grom.bin"},

	// ScummVM — Roland MT-32 / CM-32L MIDI ROMs. The libretro scummvm core
	// hardcodes its "extrapath" to <system_dir>/scummvm/extra/, so that's
	// where ScummVM picks up these ROMs to enable MT-32 sound emulation
	// (vastly better than AdLib for late-80s/early-90s adventure games like
	// Monkey Island, Indiana Jones, Loom, …). With auto music_driver
	// (default), ScummVM prefers MT-32 over AdLib when ROMs are present;
	// CM-32L is preferred over MT-32 when both are available.
	//
	// All four are Optional — most games sound fine on AdLib, and not every
	// user wants the ROMs. Sources are archive.org's MAME-versioned dump.
	// CM-32L's source filenames are MAME-style lowercase; FileName/SubDir
	// rename them to ScummVM's expected uppercase target names on download.
	{ConsoleID: "scummvm", FileName: "MT32_CONTROL.ROM", Description: "Roland MT-32 Control ROM (firmware v1.07)", MD5: "5626206284b22c2734f3e9efefcd2675", Required: false, SubDir: "scummvm/extra", OverrideURL: "https://archive.org/download/mame-versioned-roland-mt-32-and-cm-32l-rom-files/MT-32_v1.07_legacy_ROM_files.zip/MT32_CONTROL.ROM"},
	{ConsoleID: "scummvm", FileName: "MT32_PCM.ROM", Description: "Roland MT-32 PCM ROM", MD5: "89e42e386e82e0cacb4a2704a03706ca", Required: false, SubDir: "scummvm/extra", OverrideURL: "https://archive.org/download/mame-versioned-roland-mt-32-and-cm-32l-rom-files/MT-32_v1.07_legacy_ROM_files.zip/MT32_PCM.ROM"},
	{ConsoleID: "scummvm", FileName: "CM32L_CONTROL.ROM", Description: "Roland CM-32L Control ROM (v1.02) — preferred over MT-32 when present", MD5: "bfff32b6144c1d706109accb6e6b1113", Required: false, SubDir: "scummvm/extra", OverrideURL: "https://archive.org/download/mame-versioned-roland-mt-32-and-cm-32l-rom-files/MT-32_and_CM-32L_MAME-Versioned_ROM_files..zip/cm32l_ctrl_1_02.rom"},
	{ConsoleID: "scummvm", FileName: "CM32L_PCM.ROM", Description: "Roland CM-32L PCM ROM (1MB, expanded sample bank)", MD5: "08cdcfa0ed93e9cb16afa76e6ac5f0a4", Required: false, SubDir: "scummvm/extra", OverrideURL: "https://archive.org/download/mame-versioned-roland-mt-32-and-cm-32l-rom-files/MT-32_and_CM-32L_MAME-Versioned_ROM_files..zip/cm32l_pcm.rom"},

	// Sharp X68000 (X68K) — px68k_libretro.info.
	//
	// The px68k core looks for these BIOS files at <system_dir>/keropi/.
	// IPLROM30.DAT is the boot ROM; CGROM.DAT is the character generator
	// (font tables) used during the BIOS boot animation and by some
	// games. Both are technically required to boot any cartridge.
	//
	// Source URLs are intentionally not set: Sharp does not distribute
	// these files publicly, and the existing PD redistributions (e.g.
	// from MAME ROM dumps) live under licenses Spela can't reliably
	// honour for auto-download. Operators must supply the files via the
	// admin BIOS upload UI. Required is therefore left at false to
	// satisfy the Required→Downloadable invariant; the description
	// flags them as functionally required.
	{ConsoleID: "x68k", FileName: "IPLROM30.DAT", Description: "Sharp X68000 IPL Boot ROM v3.0 (functionally required — operator must upload manually)", MD5: "", Required: false, SubDir: "keropi"},
	{ConsoleID: "x68k", FileName: "CGROM.DAT", Description: "Sharp X68000 Character Generator ROM (functionally required — operator must upload manually)", MD5: "", Required: false, SubDir: "keropi"},
}

// repoFolders maps console IDs to their folder path in the
// Abdess/retrobios GitHub repository (under the bios/ directory).
var repoFolders = map[string]string{
	"psx":   "Sony/PlayStation",
	"sat":   "Sega/Saturn",
	"scd":   "Sega/Mega CD",
	"dc":    "Sega/Dreamcast",
	"gba":   "Nintendo/Game Boy Advance",
	"pce":   "NEC/PC Engine",
	"pcecd": "NEC/PC Engine",
	"lynx":  "Atari/Lynx",
	"3do":   "3DO Company/3DO",
	"pcfx":  "NEC/PC-FX",
	"ps2":  "Sony/PlayStation 2",
	// nds — repo uses different filenames (nds7.bin/nds9.bin vs bios7.bin/bios9.bin)
	// cv — uses OverrideURL (repo has BIOS.col, cores expect colecovision.rom)
	// neogeo, neocd, amiga, cdi — use OverrideURL
}

// RepoFolder returns the repository folder name for the given console ID,
// or an empty string if the console has no downloadable BIOS files.
func RepoFolder(consoleID string) string {
	return repoFolders[consoleID]
}

// Downloadable returns all registry entries that can be auto-downloaded,
// i.e. entries whose console has a known repository folder OR that have
// an explicit OverrideURL.
func Downloadable() []Entry {
	var out []Entry
	for _, e := range registry {
		if repoFolders[e.ConsoleID] != "" || e.OverrideURL != "" {
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
