// Package patcher applies ROM patches in IPS, BPS, UPS, and xdelta formats.
package patcher

import (
	"fmt"
	"path/filepath"
	"strings"
)

// MaxOutputSize is the maximum allowed patched ROM size (256 MB).
// This prevents denial-of-service via malicious patches that specify
// arbitrarily large output sizes.
const MaxOutputSize = 256 * 1024 * 1024

// Apply applies a patch to ROM data and returns the patched result.
// The format is auto-detected from the patch file extension.
func Apply(romData, patchData []byte, patchFilename string) ([]byte, error) {
	ext := strings.ToLower(filepath.Ext(patchFilename))
	switch ext {
	case ".ips":
		return ApplyIPS(romData, patchData)
	case ".bps":
		return ApplyBPS(romData, patchData)
	case ".ups":
		return ApplyUPS(romData, patchData)
	case ".xdelta", ".vcdiff":
		return ApplyXDelta(romData, patchData)
	default:
		return nil, fmt.Errorf("unsupported patch format: %s", ext)
	}
}
