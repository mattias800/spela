package cheats

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestParseChtContent_basic(t *testing.T) {
	content := `cheat0_desc = "Infinite Health"
cheat0_code = "ABCD-1234"
cheat0_enable = false
cheat1_desc = "Max Money"
cheat1_code = "EFGH+5678"`

	entries, err := ParseChtContent(content)
	require.NoError(t, err)
	require.Len(t, entries, 2)
	assert.Equal(t, 0, entries[0].Index)
	assert.Equal(t, "Infinite Health", entries[0].Description)
	assert.Equal(t, "ABCD-1234", entries[0].Code)
	assert.Equal(t, 1, entries[1].Index)
	assert.Equal(t, "Max Money", entries[1].Description)
	assert.Equal(t, "EFGH+5678", entries[1].Code)
}

func TestParseChtContent_empty(t *testing.T) {
	entries, err := ParseChtContent("")
	require.NoError(t, err)
	assert.Empty(t, entries)
}

func TestParseChtContent_missingCode(t *testing.T) {
	content := `cheat0_desc = "No Code Cheat"`
	entries, err := ParseChtContent(content)
	require.NoError(t, err)
	assert.Empty(t, entries)
}

func TestParseChtContent_missingDesc(t *testing.T) {
	content := `cheat0_code = "ABCD1234"`
	entries, err := ParseChtContent(content)
	require.NoError(t, err)
	assert.Empty(t, entries)
}

func TestSystemFolders_knownConsoles(t *testing.T) {
	assert.Equal(t, "Nintendo - Nintendo Entertainment System", systemFolders["nes"])
	assert.Equal(t, "Nintendo - Super Nintendo Entertainment System", systemFolders["snes"])
	assert.Equal(t, "Sega - Mega Drive - Genesis", systemFolders["genesis"])
	assert.Equal(t, "Nintendo - Game Boy", systemFolders["gb"])
}

func TestSystemFolders_unknownConsole(t *testing.T) {
	_, ok := systemFolders["nonexistent"]
	assert.False(t, ok)
}
