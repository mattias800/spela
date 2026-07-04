package scanner

import (
	"bytes"
	"encoding/binary"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestPublicBootFixturesExist(t *testing.T) {
	root := filepath.Join("..", "..", "..", "testdata-public")
	anchors := []string{
		filepath.Join(root, "tic80", "spela-hello.tic"),
		filepath.Join(root, "zxspectrum", "spela-hello.tap"),
		filepath.Join(root, "amstradcpc", "spela-hello.dsk"),
	}

	for _, path := range anchors {
		t.Run(filepath.Base(path), func(t *testing.T) {
			info, err := os.Stat(path)
			require.NoError(t, err)
			assert.Greater(t, info.Size(), int64(0), "boot fixture must contain real bytes, not a scanner-only stub")
		})
	}
}

func TestPublicAmstradCPCDiskFixtureHasExpectedStructure(t *testing.T) {
	path := filepath.Join("..", "..", "..", "testdata-public", "amstradcpc", "spela-hello.dsk")
	data, err := os.ReadFile(path)
	require.NoError(t, err)
	require.Len(t, data, 0x1400)

	assert.Equal(t, []byte("EXTENDED CPC DSK File\r\nDisk-Info\r\n"), data[0:34])
	assert.Equal(t, byte(40), data[0x30], "standard CPC data disk track count")
	assert.Equal(t, byte(1), data[0x31], "single-sided CPC disk")
	assert.Equal(t, byte(0x13), data[0x34], "track 0 should be 0x1300 bytes")

	track := data[0x100:]
	assert.Equal(t, []byte("Track-Info\r\n"), track[0:12])
	assert.Equal(t, []byte{0, 0, 0, 0}, track[0x0C:0x10], "track info padding")
	assert.Equal(t, byte(2), track[0x14], "512-byte sectors")
	assert.Equal(t, byte(9), track[0x15], "CPC data format sectors per track")
	assert.Equal(t, []byte{0, 0}, track[0x12:0x14], "extended DSK data rate / recording mode unknown")
	firstSector := track[0x18 : 0x18+8]
	assert.Equal(t, byte(0xC1), firstSector[2], "CPC data format first sector id")
	assert.Equal(t, byte(2), firstSector[3], "512-byte sector descriptor")
	assert.Zero(t, firstSector[4], "sector status register 1")
	assert.Zero(t, firstSector[5], "sector status register 2")
	assert.Equal(t, uint16(512), binary.LittleEndian.Uint16(firstSector[6:8]), "extended DSK actual sector length")

	directoryEntry := track[0x100 : 0x100+32]
	assert.Equal(t, byte(0), directoryEntry[0])
	assert.Equal(t, []byte("SPELA   "), directoryEntry[1:9])
	assert.Equal(t, []byte("BAS"), directoryEntry[9:12])
	assert.Equal(t, byte(2), directoryEntry[16], "file data should start after the two directory blocks")

	fileStart := 0x100 + 4*512
	header := track[fileStart : fileStart+128]
	assert.Zero(t, header[0], "AMSDOS user number")
	assert.Equal(t, []byte("SPELA   "), header[1:9])
	assert.Equal(t, []byte("BAS"), header[9:12])
	assert.Equal(t, byte(0), header[18], "unprotected BASIC")
	assert.Equal(t, uint16(0x0170), binary.LittleEndian.Uint16(header[21:23]), "BASIC load address")

	storedChecksum := binary.LittleEndian.Uint16(header[67:69])
	var computed uint16
	for _, b := range header[0:67] {
		computed += uint16(b)
	}
	assert.Equal(t, storedChecksum, computed, "AMSDOS checksum must cover bytes 0..66")
	assert.True(t, bytes.Contains(track[fileStart+128:fileStart+256], []byte("SPELA CPC OK")))
}
