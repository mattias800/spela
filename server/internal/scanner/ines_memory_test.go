package scanner

import (
	"bytes"
	"testing"
)

func TestNormalizeINESHeaderBytes(t *testing.T) {
	// A canonical Super Mario Bros (USA) iNES 1.0 header with a tool-
	// provenance "NI2.1" ASCII trailer in bytes 11-15. nestopia rejects
	// this; lenient cores accept it.
	smbDirty := func() []byte {
		b := make([]byte, 16)
		copy(b, []byte{0x4e, 0x45, 0x53, 0x1a, 0x02, 0x01, 0x01, 0x00})
		copy(b[11:], []byte("NI2.1"))
		return b
	}
	smbClean := func() []byte {
		b := make([]byte, 16)
		copy(b, []byte{0x4e, 0x45, 0x53, 0x1a, 0x02, 0x01, 0x01, 0x00})
		return b
	}

	tests := []struct {
		name     string
		input    []byte
		modified bool
		want     []byte
	}{
		{
			name:     "dirty SMB header is sanitized",
			input:    smbDirty(),
			modified: true,
			want:     smbClean(),
		},
		{
			name:     "already-clean header is unchanged",
			input:    smbClean(),
			modified: false,
			want:     smbClean(),
		},
		{
			name: "NES 2.0 header is left untouched",
			input: func() []byte {
				b := smbDirty()
				b[7] = 0x08 // bits 2-3 = 0b10 marks NES 2.0
				return b
			}(),
			modified: false,
			want: func() []byte {
				b := smbDirty()
				b[7] = 0x08
				return b
			}(),
		},
		{
			name:     "non-iNES buffer is left untouched",
			input:    bytes.Repeat([]byte{0xff}, 16),
			modified: false,
			want:     bytes.Repeat([]byte{0xff}, 16),
		},
		{
			name:     "buffer shorter than 16 bytes is a no-op",
			input:    []byte{0x4e, 0x45, 0x53, 0x1a},
			modified: false,
			want:     []byte{0x4e, 0x45, 0x53, 0x1a},
		},
		{
			name: "DiskDude! marker is sanitized",
			input: func() []byte {
				b := make([]byte, 16)
				copy(b, []byte{0x4e, 0x45, 0x53, 0x1a, 0x02, 0x01, 0x01, 0x00})
				copy(b[7:], []byte("DiskDude!"))
				// byte 7 is overwritten by the copy above; reset it.
				b[7] = 0x00
				return b
			}(),
			modified: true,
			want:     smbClean(),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := tt.modified
			gotMod := NormalizeINESHeaderBytes(tt.input)
			if gotMod != got {
				t.Fatalf("modified: got=%v want=%v", gotMod, got)
			}
			if !bytes.Equal(tt.input, tt.want) {
				t.Fatalf("buffer mismatch:\n got:  %x\n want: %x", tt.input, tt.want)
			}
		})
	}
}

func TestNormalizeINESHeaderBytes_Idempotent(t *testing.T) {
	b := make([]byte, 16)
	copy(b, []byte{0x4e, 0x45, 0x53, 0x1a, 0x02, 0x01, 0x01, 0x00})
	copy(b[11:], []byte("NI2.1"))

	if !NormalizeINESHeaderBytes(b) {
		t.Fatal("first call should report modified=true")
	}
	if NormalizeINESHeaderBytes(b) {
		t.Fatal("second call on already-clean buffer should report modified=false")
	}
}
