package bios

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestAll_ReturnsAllEntries(t *testing.T) {
	entries := All()
	assert.NotEmpty(t, entries)
	assert.Equal(t, len(registry), len(entries))
}

func TestAll_ReturnsCopy(t *testing.T) {
	entries := All()
	entries[0].FileName = "modified.bin"
	assert.NotEqual(t, "modified.bin", registry[0].FileName, "All() should return a copy")
}

func TestByFileName_KnownFile(t *testing.T) {
	tests := []struct {
		name     string
		fileName string
		wantLen  int
		wantID   string
	}{
		{"PSX BIOS NA", "scph5501.bin", 1, "psx"},
		{"Saturn BIOS US/EU", "mpr-17933.bin", 1, "sat"},
		{"Dreamcast boot", "dc_boot.bin", 1, "dc"},
		{"GBA BIOS", "gba_bios.bin", 1, "gba"},
		{"PC Engine syscard", "syscard3.pce", 2, "pce"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			matches := ByFileName(tt.fileName)
			assert.Len(t, matches, tt.wantLen)
			if tt.wantLen > 0 {
				assert.Equal(t, tt.wantID, matches[0].ConsoleID)
			}
		})
	}
}

func TestByFileName_Unknown(t *testing.T) {
	matches := ByFileName("nonexistent.bin")
	assert.Empty(t, matches)
}

func TestByConsole(t *testing.T) {
	tests := []struct {
		name      string
		consoleID string
		wantLen   int
	}{
		{"PSX has 3 entries", "psx", 3},
		{"Saturn has 2 entries", "sat", 2},
		{"Dreamcast has 2 entries", "dc", 2},
		{"NDS has 3 entries", "nds", 3},
		// Newly registered missing-BIOS consoles — see #889 / #890 / #891 / #906.
		{"FDS has 1 entry (disksys.rom)", "fds", 1},
		{"Odyssey 2 has 4 entries", "o2", 4},
		{"Channel F has 3 entries", "chaf", 3},
		{"PSP has 1 entry (ppge_atlas.zim)", "psp", 1},
		{"Unknown console returns empty", "unknown", 0},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			entries := ByConsole(tt.consoleID)
			assert.Len(t, entries, tt.wantLen)
		})
	}
}

func TestConsoleIDs(t *testing.T) {
	ids := ConsoleIDs()
	assert.NotEmpty(t, ids)

	// Should include all expected consoles
	idSet := make(map[string]bool)
	for _, id := range ids {
		idSet[id] = true
	}
	for _, expected := range []string{"psx", "sat", "scd", "dc", "gba", "nds", "pce", "pcecd", "neogeo", "neocd", "lynx", "3do", "amiga", "pcfx", "cv", "cdi", "fds", "o2", "chaf", "psp"} {
		assert.True(t, idSet[expected], "expected console %s in registry", expected)
	}
}

// Regression — #889 / #890 / #891. Newly registered consoles must
// flag their core-mandatory ROMs as Required so the missing-BIOS UI
// fires; without this the player surfaces a generic emulation error
// (FDS, O2) or a silent black screen (Channel F) instead.
func TestNewlyRegisteredConsoles_RequiredFlags(t *testing.T) {
	tests := []struct {
		consoleID    string
		fileName     string
		wantRequired bool
	}{
		{"fds", "disksys.rom", true},
		{"o2", "o2rom.bin", true},
		{"o2", "c52.bin", false},
		{"o2", "g7400.bin", false},
		{"o2", "jopac.bin", false},
		{"chaf", "sl31253.bin", true},
		{"chaf", "sl31254.bin", true},
		{"chaf", "sl90025.bin", false},
	}
	for _, tt := range tests {
		t.Run(tt.consoleID+"/"+tt.fileName, func(t *testing.T) {
			var match *Entry
			for _, e := range ByConsole(tt.consoleID) {
				if e.FileName == tt.fileName {
					cp := e
					match = &cp
					break
				}
			}
			if assert.NotNil(t, match, "registry must contain %s/%s", tt.consoleID, tt.fileName) {
				assert.Equal(t, tt.wantRequired, match.Required)
			}
		})
	}
}

// FDS publishes a known MD5 in nestopia_libretro.info; lock it.
func TestFds_DisksysMD5(t *testing.T) {
	for _, e := range ByConsole("fds") {
		if e.FileName == "disksys.rom" {
			assert.Equal(t, "ca30b50f880eb660a320674ed365ef7a", e.MD5)
			return
		}
	}
	t.Fatal("fds/disksys.rom not found")
}

// PSP — system assets are delivered as a single libretro buildbot
// archive (`PPSSPP/` wrapper containing atlas + flash0/font + lang/
// + vfpu/ + fonts + ini). The wrapper matches our SubDir, so
// StripPrefix drops it during extraction; FileName names a clean
// sentinel that bios.ByFileName() can match for upload routing.
// See #911.
func TestPsp_AssetsBundle(t *testing.T) {
	for _, e := range ByConsole("psp") {
		if e.FileName == "ppge_atlas.zim" {
			assert.True(t, e.Required, "PSP assets bundle must be required")
			assert.True(t, e.Bundle, "PSP entry must be a Bundle")
			assert.Equal(t, "https://buildbot.libretro.com/assets/system/PPSSPP.zip", e.OverrideURL)
			assert.Equal(t, "PPSSPP", e.SubDir)
			assert.Equal(t, "PPSSPP/", e.StripPrefix)
			return
		}
	}
	t.Fatal("psp assets bundle not found")
}

func TestRepoFolder(t *testing.T) {
	tests := []struct {
		consoleID string
		want      string
	}{
		{"psx", "Sony/PlayStation"},
		{"sat", "Sega/Saturn"},
		{"scd", "Sega/Mega CD"},
		{"dc", "Sega/Dreamcast"},
		{"gba", "Nintendo/Game Boy Advance"},
		{"nds", ""},
		{"pce", "NEC/PC Engine"},
		{"pcecd", "NEC/PC Engine"},
		{"lynx", "Atari/Lynx"},
		{"3do", "3DO Company/3DO"},
		{"pcfx", "NEC/PC-FX"},
		{"cv", ""},
		{"ps2", "Sony/PlayStation 2"},
		{"neogeo", ""},
		{"amiga", ""},
		{"unknown", ""},
	}
	for _, tt := range tests {
		t.Run(tt.consoleID, func(t *testing.T) {
			assert.Equal(t, tt.want, RepoFolder(tt.consoleID))
		})
	}
}

func TestDownloadable(t *testing.T) {
	entries := Downloadable()
	assert.NotEmpty(t, entries)

	// Every downloadable entry must have a repo folder mapping OR an OverrideURL
	for _, e := range entries {
		hasFolder := RepoFolder(e.ConsoleID) != ""
		hasOverride := e.OverrideURL != ""
		assert.True(t, hasFolder || hasOverride, "entry %s should have a repo folder or OverrideURL", e.FileName)
	}

	// NDS entries should NOT be in the downloadable list (different filenames in repo)
	for _, e := range entries {
		assert.NotEqual(t, "nds", e.ConsoleID, "nds entries should not be downloadable (repo uses different filenames)")
	}
	assert.Less(t, len(entries), len(All()))
}

func TestDownloadable_IncludesOverrideURLEntries(t *testing.T) {
	entries := Downloadable()

	// All consoles with OverrideURL should be in the downloadable list
	want := map[string][]string{
		"neogeo": {"neogeo.zip"},
		"neocd":  {"neocdz.zip", "neocd.zip"},
		"amiga":  {"kick34005.A500", "kick40068.A1200", "kick40060.CD32"},
		"cdi":    {"cdimono1.zip", "cdimono2.zip", "cdibios.zip"},
	}

	found := make(map[string]bool)
	for _, e := range entries {
		if e.OverrideURL != "" {
			found[e.ConsoleID+"/"+e.FileName] = true
		}
	}

	for consoleID, files := range want {
		for _, f := range files {
			key := consoleID + "/" + f
			assert.True(t, found[key], "%s should be in downloadable list with OverrideURL", key)
		}
	}
}

// TestCDIMono1_PointsAtCompleteSet locks in the MD5 of the cdimono1.zip
// download. The Mono-I BIOS needs 5 files: 3 main BIOS dumps plus the
// SERVO and SLAVE microcontroller ROMs. Earlier we shipped a 3-file zip
// from Abdess/retrobios; SAME_CDI loaded it but couldn't initialise the
// hardware so games like Myst showed only a black screen (#939). Pinning
// the MD5 forces stale 3-file zips to be re-downloaded.
func TestCDIMono1_PointsAtCompleteSet(t *testing.T) {
	matches := ByFileName("cdimono1.zip")
	if assert.Len(t, matches, 1) {
		e := matches[0]
		assert.Equal(t, "cdi", e.ConsoleID)
		assert.True(t, e.Required, "cdimono1 is required for CD-i emulation")
		assert.Equal(t, "cfca9b8a96ed810bb3cd5ac11d7d1dda", e.MD5,
			"MD5 must match the 5-file (BIOS + SERVO + SLAVE) zip; if this trips, "+
				"verify the new zip contains zx405037p_*.7201 and zx405042p_*.7206 chip ROMs")
		assert.NotEmpty(t, e.OverrideURL, "must have a download URL")
		assert.Equal(t, "same_cdi/bios", e.SubDir)
	}
}

func TestRegistryEntries_HaveRequiredFields(t *testing.T) {
	for _, e := range All() {
		assert.NotEmpty(t, e.ConsoleID, "ConsoleID must not be empty")
		assert.NotEmpty(t, e.FileName, "FileName must not be empty")
		assert.NotEmpty(t, e.Description, "Description must not be empty")
		// MD5 may be empty when libretro core-info does not provide a checksum
		if e.MD5 != "" {
			assert.Len(t, e.MD5, 32, "MD5 must be 32 hex chars for %s", e.FileName)
		}
	}
}
