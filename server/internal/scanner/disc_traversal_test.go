package scanner

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// TestDiscCompanionFiles_RejectsTraversal verifies that a malicious .cue
// file referencing files outside the disc directory has those entries
// silently dropped rather than included in the companion list (issue #1116).
func TestDiscCompanionFiles_RejectsTraversal(t *testing.T) {
	tmp := t.TempDir()

	discDir := filepath.Join(tmp, "psx")
	if err := os.MkdirAll(discDir, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	// Plant a sibling that legitimately should be included.
	if err := os.WriteFile(filepath.Join(discDir, "game.bin"), []byte("disc data"), 0o644); err != nil {
		t.Fatalf("write game.bin: %v", err)
	}
	// Plant a "secret" outside the disc dir to ensure it never appears.
	secret := filepath.Join(tmp, "secret.txt")
	if err := os.WriteFile(secret, []byte("PWNED"), 0o644); err != nil {
		t.Fatalf("write secret: %v", err)
	}

	cuePath := filepath.Join(discDir, "game.cue")
	cue := strings.Join([]string{
		// Legitimate entry — should be included.
		`FILE "game.bin" BINARY`,
		`  TRACK 01 MODE2/2352`,
		`    INDEX 01 00:00:00`,
		// Traversal entry — must be dropped.
		`FILE "../secret.txt" BINARY`,
		`  TRACK 02 AUDIO`,
		`    INDEX 01 00:00:00`,
		// Absolute-path entry — must be dropped.
		`FILE "/etc/passwd" BINARY`,
		`  TRACK 03 AUDIO`,
		`    INDEX 01 00:00:00`,
		// Backslash-prefixed absolute (Windows-style) — must be dropped.
		`FILE "\Windows\System32\config\SAM" BINARY`,
	}, "\n") + "\n"
	if err := os.WriteFile(cuePath, []byte(cue), 0o644); err != nil {
		t.Fatalf("write cue: %v", err)
	}

	files, _, err := DiscCompanionFiles(cuePath)
	if err != nil {
		t.Fatalf("DiscCompanionFiles: %v", err)
	}

	for _, f := range files {
		if strings.Contains(f, "secret.txt") {
			t.Fatalf("traversal entry leaked into result: %s\n  full: %v", f, files)
		}
		if strings.Contains(f, "passwd") || strings.Contains(f, "SAM") {
			t.Fatalf("absolute-path entry leaked into result: %s\n  full: %v", f, files)
		}
		if !strings.HasPrefix(f, discDir) {
			t.Fatalf("entry escapes disc dir: %s\n  full: %v", f, files)
		}
	}

	// We should still have the disc + the legitimate companion (game.bin).
	if len(files) != 2 {
		t.Fatalf("expected 2 entries (cue + game.bin), got %d: %v", len(files), files)
	}
}
