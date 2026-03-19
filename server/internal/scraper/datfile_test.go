package scraper

import (
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

const sampleDAT = `clrmamepro (
	name "Nintendo - Nintendo Entertainment System"
	description "Nintendo - Nintendo Entertainment System"
)

game (
	name "Castlevania (USA)"
	description "Castlevania (USA)"
	rom ( name "Castlevania (USA).nes" size 131088 crc 03225522 md5 B7B82ED1E8C79FBC6B87696AE5FF03A8 sha1 3ABED4C57B7C5A1E731735CC47B5FA1EBECA878D )
)

game (
	name "Super Mario Bros. (World)"
	description "Super Mario Bros. (World)"
	rom ( name "Super Mario Bros. (World).nes" size 40976 crc D445F698 md5 3B47B7B246B3D0E0A47EB5EE7B7D8E2E sha1 EA343F4E445A9050D4B4FBAC2C77D0693B1D0922 )
)

game (
	name "Mega Man 2 (USA)"
	description "Mega Man 2 (USA)"
	rom ( name "Mega Man 2 (USA).nes" size 262160 crc 0FCFC04D md5 B6B7C5B02A0F1F0FD36BFF2C0FD0CF78 sha1 E7A2E226DAF5E42F21CF3A5D88DE76E8C90E4D1F )
)
`

func TestParseDAT(t *testing.T) {
	idx, err := ParseDAT(strings.NewReader(sampleDAT))
	require.NoError(t, err)

	assert.Len(t, idx.byCRC, 3)

	// Verify Castlevania entry
	entry, ok := idx.LookupCRC("03225522")
	assert.True(t, ok)
	assert.Equal(t, "Castlevania (USA)", entry.GameName)
	assert.Equal(t, "Castlevania (USA).nes", entry.ROMName)
	assert.Equal(t, "03225522", entry.CRC)
	assert.Equal(t, int64(131088), entry.Size)

	// Verify Super Mario Bros entry (uppercase CRC)
	entry, ok = idx.LookupCRC("D445F698")
	assert.True(t, ok)
	assert.Equal(t, "Super Mario Bros. (World)", entry.GameName)
	assert.Equal(t, "Super Mario Bros. (World).nes", entry.ROMName)

	// Verify Mega Man 2 entry
	entry, ok = idx.LookupCRC("0FCFC04D")
	assert.True(t, ok)
	assert.Equal(t, "Mega Man 2 (USA)", entry.GameName)
}

func TestLookupCRC_CaseInsensitive(t *testing.T) {
	idx, err := ParseDAT(strings.NewReader(sampleDAT))
	require.NoError(t, err)

	// Lowercase input should still match
	entry, ok := idx.LookupCRC("d445f698")
	assert.True(t, ok)
	assert.Equal(t, "Super Mario Bros. (World)", entry.GameName)
}

func TestLookupCRC_NotFound(t *testing.T) {
	idx, err := ParseDAT(strings.NewReader(sampleDAT))
	require.NoError(t, err)

	_, ok := idx.LookupCRC("DEADBEEF")
	assert.False(t, ok)
}

func TestParseDAT_EmptyInput(t *testing.T) {
	idx, err := ParseDAT(strings.NewReader(""))
	require.NoError(t, err)
	assert.Empty(t, idx.byCRC)
}

func TestParseDAT_MalformedInput(t *testing.T) {
	// Header-only DAT with no game blocks
	input := `clrmamepro (
	name "Some System"
)
`
	idx, err := ParseDAT(strings.NewReader(input))
	require.NoError(t, err)
	assert.Empty(t, idx.byCRC)
}

func TestParseDAT_GameWithoutROM(t *testing.T) {
	input := `game (
	name "Incomplete Entry"
	description "No ROM line"
)
`
	idx, err := ParseDAT(strings.NewReader(input))
	require.NoError(t, err)
	assert.Empty(t, idx.byCRC)
}

func TestParseDAT_LowercaseCRCInSource(t *testing.T) {
	input := `game (
	name "Test (USA)"
	rom ( name "Test (USA).nes" size 100 crc abcd1234 md5 x sha1 y )
)
`
	idx, err := ParseDAT(strings.NewReader(input))
	require.NoError(t, err)

	// CRC should be stored uppercase regardless of source format
	entry, ok := idx.LookupCRC("ABCD1234")
	assert.True(t, ok)
	assert.Equal(t, "ABCD1234", entry.CRC)
	assert.Equal(t, "Test (USA)", entry.GameName)
}

func TestParseDAT_MultipleGamesWithSameFormatting(t *testing.T) {
	// Verify parser handles back-to-back game blocks correctly
	input := `game (
	name "Game A (USA)"
	rom ( name "Game A (USA).nes" size 100 crc 11111111 md5 a sha1 b )
)

game (
	name "Game B (Europe)"
	rom ( name "Game B (Europe).nes" size 200 crc 22222222 md5 c sha1 d )
)

game (
	name "Game C (Japan)"
	rom ( name "Game C (Japan).nes" size 300 crc 33333333 md5 e sha1 f )
)
`
	idx, err := ParseDAT(strings.NewReader(input))
	require.NoError(t, err)
	assert.Len(t, idx.byCRC, 3)

	_, ok := idx.LookupCRC("11111111")
	assert.True(t, ok)
	_, ok = idx.LookupCRC("22222222")
	assert.True(t, ok)
	_, ok = idx.LookupCRC("33333333")
	assert.True(t, ok)
}

func TestParseDAT_GameWithoutCRC(t *testing.T) {
	// ROM line present but missing CRC field — should be skipped
	input := `game (
	name "No CRC (USA)"
	rom ( name "No CRC (USA).nes" size 100 md5 abc sha1 def )
)
`
	idx, err := ParseDAT(strings.NewReader(input))
	require.NoError(t, err)
	assert.Empty(t, idx.byCRC)
}

func TestParseDAT_GameWithoutName(t *testing.T) {
	// ROM has CRC but game has no name — still indexable by CRC
	input := `game (
	description "Only description"
	rom ( name "mystery.nes" size 100 crc FFFFFFFF md5 a sha1 b )
)
`
	idx, err := ParseDAT(strings.NewReader(input))
	require.NoError(t, err)

	// Should still be indexed since ROM name and CRC are present
	entry, ok := idx.LookupCRC("FFFFFFFF")
	assert.True(t, ok)
	assert.Equal(t, "mystery.nes", entry.ROMName)
	assert.Equal(t, "", entry.GameName) // no game name line
}

func TestParseDAT_DuplicateCRC_LastWins(t *testing.T) {
	// If two games share a CRC (unlikely but possible), last one wins
	input := `game (
	name "Version A (USA)"
	rom ( name "Version A (USA).nes" size 100 crc AABBCCDD md5 a sha1 b )
)

game (
	name "Version B (USA)"
	rom ( name "Version B (USA).nes" size 100 crc AABBCCDD md5 c sha1 d )
)
`
	idx, err := ParseDAT(strings.NewReader(input))
	require.NoError(t, err)
	assert.Len(t, idx.byCRC, 1)

	entry, ok := idx.LookupCRC("AABBCCDD")
	assert.True(t, ok)
	assert.Equal(t, "Version B (USA)", entry.GameName)
}

func TestParseDAT_MAMEROMNameLookup(t *testing.T) {
	input := `clrmamepro (
	name "MAME"
	version "2017-02-14"
)

game (
	name "Ali Baba and 40 Thieves"
	year "1982"
	developer "Sega"
	rom ( name alibaba.zip size 20184 crc A5EDCB7A md5 fdb65ceadc7d9362bf56e5e2e4e40436 sha1 test )
)

game (
	name "Akka Arrh (prototype)"
	year "2022"
	developer "Atari"
	rom ( name akkaarrh.zip size 12345 crc DEADBEEF md5 abc123 sha1 test )
)
`
	idx, err := ParseDAT(strings.NewReader(input))
	require.NoError(t, err)

	// Should find by ROM zip name (without extension)
	entry, ok := idx.LookupName("alibaba")
	assert.True(t, ok, "should find alibaba by ROM name")
	assert.Equal(t, "Ali Baba and 40 Thieves", entry.Description)

	entry2, ok2 := idx.LookupName("akkaarrh")
	assert.True(t, ok2, "should find akkaarrh by ROM name")
	assert.Equal(t, "Akka Arrh (prototype)", entry2.Description)

	// Should also find by full game name
	entry3, ok3 := idx.LookupName("ali baba and 40 thieves")
	assert.True(t, ok3, "should find by full game name")
	assert.Equal(t, "Ali Baba and 40 Thieves", entry3.GameName)
}
