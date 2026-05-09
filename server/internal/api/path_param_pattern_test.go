package api

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
)

// TestAllPathParamsHavePattern is a structural lint that prevents new handlers
// from regressing on issue #1127 (project-wide numeric path-param IDs accepted
// as strings without pattern validation, which gave rise to the SQLi class
// in #1115).
//
// Every `path:"<name>"` struct tag in this package must also carry a
// `pattern:` constraint so huma rejects malformed input at the API edge
// before it can reach a GORM expression-fallback path or any other parser.
func TestAllPathParamsHavePattern(t *testing.T) {
	pathTagRE := regexp.MustCompile(`path:"[^"]+"`)

	entries, err := os.ReadDir(".")
	if err != nil {
		t.Fatalf("ReadDir: %v", err)
	}

	var offenders []string
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		if !strings.HasSuffix(name, ".go") || strings.HasSuffix(name, "_test.go") {
			continue
		}

		body, err := os.ReadFile(filepath.Join(".", name))
		if err != nil {
			t.Fatalf("ReadFile %s: %v", name, err)
		}

		for i, line := range strings.Split(string(body), "\n") {
			if !pathTagRE.MatchString(line) {
				continue
			}
			if strings.Contains(line, "pattern:") {
				continue
			}
			offenders = append(offenders, fmt.Sprintf("%s:%d: %s", name, i+1, strings.TrimSpace(line)))
		}
	}

	if len(offenders) > 0 {
		t.Fatalf("path-param tags without `pattern:` constraint (issue #1127):\n  %s",
			strings.Join(offenders, "\n  "))
	}
}
