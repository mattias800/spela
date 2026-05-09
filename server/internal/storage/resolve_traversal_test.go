package storage

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// TestResolveGamePath_RejectsTraversal verifies issue #1125: a stored
// Game.FilePath containing `..` segments or absolute paths is rejected
// before stat, so callers that don't follow up with ValidateROMPath
// (e.g. HumaCreateRomHack pre-fix) cannot read arbitrary host files.
func TestResolveGamePath_RejectsTraversal(t *testing.T) {
	tmp := t.TempDir()
	gameDir := filepath.Join(tmp, "games")
	if err := os.MkdirAll(filepath.Join(gameDir, "nes"), 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	if err := os.WriteFile(filepath.Join(gameDir, "nes", "smb.nes"), []byte("rom"), 0o644); err != nil {
		t.Fatalf("write: %v", err)
	}
	// "Secret" outside the gameDir.
	if err := os.WriteFile(filepath.Join(tmp, "secret.txt"), []byte("PWNED"), 0o644); err != nil {
		t.Fatalf("write secret: %v", err)
	}

	cases := []struct {
		name    string
		path    string
		wantErr string
	}{
		{"absolute", "/etc/passwd", "must be relative"},
		{"backslash", "\\etc\\passwd", "must be relative"},
		{"traversal segment", "../secret.txt", "traversal"},
		{"deep traversal", "nes/../../secret.txt", "traversal"},
		{"empty", "", "empty"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, err := ResolveGamePath(tc.path, []string{gameDir})
			if err == nil {
				t.Fatalf("expected error for %q, got nil", tc.path)
			}
			if !strings.Contains(err.Error(), tc.wantErr) {
				t.Fatalf("error %q does not mention %q", err, tc.wantErr)
			}
		})
	}

	// Sanity: a legitimate relative path still resolves.
	got, err := ResolveGamePath("nes/smb.nes", []string{gameDir})
	if err != nil {
		t.Fatalf("legit path: %v", err)
	}
	if !strings.HasSuffix(got, filepath.Join("nes", "smb.nes")) {
		t.Fatalf("unexpected resolved path: %s", got)
	}
}
