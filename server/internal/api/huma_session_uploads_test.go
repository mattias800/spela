package api

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

// sanitizeCompression normalises the client-supplied save-compression
// value to the closed set the server stores. Anything outside that
// set collapses to "" so a typo or future codec doesn't tag a save
// with a value players can't decompress (#804 phase 2).
func TestSanitizeCompression(t *testing.T) {
	tests := []struct {
		name string
		in   string
		want string
	}{
		{"empty", "", ""},
		{"gzip", "gzip", "gzip"},
		{"gzip uppercase", "GZIP", "gzip"},
		{"gzip mixed case", "GzIp", "gzip"},
		{"gzip with whitespace", "  gzip  ", "gzip"},
		{"unknown codec collapses to empty", "zstd", ""},
		{"garbage collapses to empty", "asdf", ""},
		{"whitespace-only collapses to empty", "   ", ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.want, sanitizeCompression(tt.in))
		})
	}
}
