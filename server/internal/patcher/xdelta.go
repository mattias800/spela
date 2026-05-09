package patcher

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"time"
)

// xdeltaTimeout caps how long the xdelta3 child process is allowed to run.
// xdelta3 is a complex C parser of attacker-supplied patch bytes
// (issue #1129); a malformed patch could trigger an infinite loop or
// runaway memory growth in older versions of the binary. 30 seconds is
// generous for legitimate ROM hacks (which decode in milliseconds) and
// short enough to bound DoS impact.
const xdeltaTimeout = 30 * time.Second

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

	// Run xdelta3 under a timeout (issue #1129). The argv form already
	// makes shell metacharacters inert so command injection isn't a
	// concern; the remaining attack surface is the C parser inside
	// xdelta3 itself, which we bound with the context deadline so a
	// hostile patch can't pin the server's CPU/memory indefinitely.
	ctx, cancel := context.WithTimeout(context.Background(), xdeltaTimeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, xdeltaPath, "-d", "-s", srcFile.Name(), patchFile.Name(), outFile.Name())
	output, err := cmd.CombinedOutput()
	if ctx.Err() == context.DeadlineExceeded {
		return nil, fmt.Errorf("xdelta3 timed out after %s: patch may be malformed", xdeltaTimeout)
	}
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
