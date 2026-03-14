package patcher

import (
	"fmt"
	"os"
	"os/exec"
)

// ApplyXDelta applies an xdelta/VCDIFF patch by shelling out to xdelta3.
// This requires xdelta3 to be installed on the system.
func ApplyXDelta(romData, patchData []byte) ([]byte, error) {
	// Check if xdelta3 is available
	xdeltaPath, err := exec.LookPath("xdelta3")
	if err != nil {
		return nil, fmt.Errorf("xdelta3 not found in PATH: install xdelta3 to apply .xdelta patches")
	}

	// Write source and patch to temp files
	srcFile, err := os.CreateTemp("", "xdelta-src-*")
	if err != nil {
		return nil, fmt.Errorf("creating temp source file: %w", err)
	}
	defer os.Remove(srcFile.Name())

	if _, err := srcFile.Write(romData); err != nil {
		srcFile.Close()
		return nil, fmt.Errorf("writing temp source file: %w", err)
	}
	srcFile.Close()

	patchFile, err := os.CreateTemp("", "xdelta-patch-*")
	if err != nil {
		return nil, fmt.Errorf("creating temp patch file: %w", err)
	}
	defer os.Remove(patchFile.Name())

	if _, err := patchFile.Write(patchData); err != nil {
		patchFile.Close()
		return nil, fmt.Errorf("writing temp patch file: %w", err)
	}
	patchFile.Close()

	// Create output temp file
	outFile, err := os.CreateTemp("", "xdelta-out-*")
	if err != nil {
		return nil, fmt.Errorf("creating temp output file: %w", err)
	}
	defer os.Remove(outFile.Name())
	outFile.Close()

	// Run xdelta3
	cmd := exec.Command(xdeltaPath, "-d", "-s", srcFile.Name(), patchFile.Name(), outFile.Name())
	output, err := cmd.CombinedOutput()
	if err != nil {
		return nil, fmt.Errorf("xdelta3 failed: %s: %w", string(output), err)
	}

	// Read result
	result, err := os.ReadFile(outFile.Name())
	if err != nil {
		return nil, fmt.Errorf("reading xdelta3 output: %w", err)
	}

	return result, nil
}
