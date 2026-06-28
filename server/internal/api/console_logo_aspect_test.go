package api

import (
	"math"
	"testing"
)

// TestParseViewBoxAspectRatio walks the same SVG shapes the seeded
// logos actually use (Adobe Illustrator + CorelDRAW exporters, both
// represented in the embedded asset directory) and checks the
// recovered aspect ratio. This is the function the backfill relies on
// — if it ever silently fails to parse a particular vendor's SVG, the
// player app would fall back to the square-guess for that console.
func TestParseViewBoxAspectRatio(t *testing.T) {
	tests := []struct {
		name      string
		svg       string
		wantRatio float64
		wantOK    bool
	}{
		{
			name:      "Adobe-Illustrator wide-banner format",
			svg:       `<svg version="1.1" viewBox="0 0 3840 1134.2"></svg>`,
			wantRatio: 3840.0 / 1134.2,
			wantOK:    true,
		},
		{
			name:      "CorelDRAW compact format",
			svg:       `<svg xmlns="..." viewBox="0 0 198.0769 55.8952"></svg>`,
			wantRatio: 198.0769 / 55.8952,
			wantOK:    true,
		},
		{
			name:      "square (placeholder) viewBox",
			svg:       `<svg viewBox="0 0 100 100"></svg>`,
			wantRatio: 1.0,
			wantOK:    true,
		},
		{
			name:      "non-zero min coordinates (legal)",
			svg:       `<svg viewBox="-50 -50 200 100"></svg>`,
			wantRatio: 2.0,
			wantOK:    true,
		},
		{
			name:      "whitespace around equals sign",
			svg:       `<svg viewBox = "0 0 400 200"></svg>`,
			wantRatio: 2.0,
			wantOK:    true,
		},
		{
			name:   "no viewBox attribute",
			svg:    `<svg width="100" height="50"></svg>`,
			wantOK: false,
		},
		{
			name:   "zero height — invalid",
			svg:    `<svg viewBox="0 0 100 0"></svg>`,
			wantOK: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ratio, ok := parseViewBoxAspectRatio([]byte(tt.svg))
			if ok != tt.wantOK {
				t.Fatalf("ok = %v, want %v", ok, tt.wantOK)
			}
			if tt.wantOK && math.Abs(ratio-tt.wantRatio) > 1e-6 {
				t.Fatalf("ratio = %v, want %v", ratio, tt.wantRatio)
			}
		})
	}
}

// TestConsoleLogoAspectRatio covers the SVG-derived cache that replaced the
// stored logo_aspect_ratio column (#1443). That value-flow had no coverage
// before: handler tests never ran the old startup backfill, so the field was
// always null in tests.
func TestConsoleLogoAspectRatio(t *testing.T) {
	// A real console with a bundled logo resolves to a positive ratio,
	// case-insensitively on abbreviation.
	nes := consoleLogoAspectRatio("NES")
	if nes == nil || *nes <= 0 {
		t.Fatalf("consoleLogoAspectRatio(NES) = %v, want positive ratio", nes)
	}
	if lower := consoleLogoAspectRatio("nes"); lower == nil || *lower != *nes {
		t.Fatalf("lookup not case-insensitive: NES=%v nes=%v", nes, lower)
	}

	// Unknown consoles resolve to nil (client falls back to fluid sizing).
	if got := consoleLogoAspectRatio("NOSUCHCONSOLE"); got != nil {
		t.Fatalf("consoleLogoAspectRatio(unknown) = %v, want nil", got)
	}

	// Child platforms inherit their parent's logo asset (ADEMO → AMIGA), so
	// their derived ratio must match the parent's.
	amiga := consoleLogoAspectRatio("AMIGA")
	ademo := consoleLogoAspectRatio("ADEMO")
	if amiga == nil || ademo == nil {
		t.Fatalf("AMIGA=%v ADEMO=%v, want both non-nil", amiga, ademo)
	}
	if *amiga != *ademo {
		t.Fatalf("ADEMO ratio %v != parent AMIGA ratio %v", *ademo, *amiga)
	}
}
