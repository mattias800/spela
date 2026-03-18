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
	for _, expected := range []string{"psx", "sat", "scd", "dc", "gba", "nds", "pce", "pcecd", "neogeo", "neocd", "lynx", "3do", "amiga", "pcfx", "cv", "cdi"} {
		assert.True(t, idSet[expected], "expected console %s in registry", expected)
	}
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
