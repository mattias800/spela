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
		{"Saturn BIOS", "saturn_bios.bin", 1, "sat"},
		{"Dreamcast boot", "dc_boot.bin", 1, "dc"},
		{"GBA BIOS", "gba_bios.bin", 1, "gba"},
		{"PC Engine syscard", "syscard3.pce", 1, "pce"},
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
		wantMin   int
	}{
		{"PSX has 3 entries", "psx", 3},
		{"Saturn has 1 entry", "sat", 1},
		{"Dreamcast has 2 entries", "dc", 2},
		{"NDS has 3 entries", "nds", 3},
		{"Unknown console returns empty", "unknown", 0},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			entries := ByConsole(tt.consoleID)
			assert.Len(t, entries, tt.wantMin)
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
	for _, expected := range []string{"psx", "sat", "scd", "dc", "gba", "nds", "pce"} {
		assert.True(t, idSet[expected], "expected console %s in registry", expected)
	}
}

func TestRegistryEntries_HaveRequiredFields(t *testing.T) {
	for _, e := range All() {
		assert.NotEmpty(t, e.ConsoleID, "ConsoleID must not be empty")
		assert.NotEmpty(t, e.FileName, "FileName must not be empty")
		assert.NotEmpty(t, e.Description, "Description must not be empty")
		assert.NotEmpty(t, e.MD5, "MD5 must not be empty")
		assert.Len(t, e.MD5, 32, "MD5 must be 32 hex chars for %s", e.FileName)
	}
}
