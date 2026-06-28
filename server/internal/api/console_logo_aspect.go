package api

import (
	"regexp"
	"strconv"
	"strings"
	"sync"

	"github.com/spela/server/internal/db"
)

// viewBoxRe matches an SVG viewBox attribute and captures width + height.
// Spec: viewBox = "min-x min-y width height" (whitespace-separated, can
// be negative or decimal).
var viewBoxRe = regexp.MustCompile(
	`viewBox\s*=\s*"\s*[-0-9.]+\s+[-0-9.]+\s+([-0-9.]+)\s+([-0-9.]+)\s*"`,
)

var (
	logoAspectOnce   sync.Once
	logoAspectByAbbr map[string]float64 // keyed by lowercase abbreviation
)

// buildLogoAspectCache computes every console's intrinsic logo aspect ratio
// from its embedded SVG's viewBox, once. Child platforms (ADEMO → AMIGA,
// DDEMO → DOS) inherit their parent's asset via consoleLogoFallbacks, the
// same way HumaGetConsoleLogo serves them.
func buildLogoAspectCache() {
	logoAspectByAbbr = make(map[string]float64)
	for _, spec := range db.ConsoleRegistry() {
		abbr := strings.ToLower(spec.Abbreviation)
		lookup := abbr
		if parent, ok := consoleLogoFallbacks[abbr]; ok {
			lookup = parent
		}
		data, err := consoleLogos.ReadFile("static/console-logos/" + lookup + ".svg")
		if err != nil {
			continue
		}
		if ratio, ok := parseViewBoxAspectRatio(data); ok {
			logoAspectByAbbr[abbr] = ratio
		}
	}
}

// consoleLogoAspectRatio returns a console's intrinsic logo width/height
// ratio, derived (and cached) from its SVG viewBox. Nil when the asset is
// missing or has no usable viewBox — the client then falls back to legacy
// fluid sizing.
//
// Why this exists: shipping the ratio with the Console DTO lets the player
// app size the console-detail hero logo correctly on first render instead
// of guessing square and re-laying-out once the image decodes (#1166). It
// is derived from the bundled asset rather than stored on the row (#1443).
func consoleLogoAspectRatio(abbr string) *float64 {
	logoAspectOnce.Do(buildLogoAspectCache)
	if r, ok := logoAspectByAbbr[strings.ToLower(abbr)]; ok {
		return &r
	}
	return nil
}

// parseViewBoxAspectRatio extracts width/height from an SVG's viewBox
// attribute and returns width / height. Returns (0, false) when no
// usable viewBox is present (no match, zero/negative height, etc.).
func parseViewBoxAspectRatio(svg []byte) (float64, bool) {
	m := viewBoxRe.FindSubmatch(svg)
	if len(m) != 3 {
		return 0, false
	}
	w, err1 := strconv.ParseFloat(string(m[1]), 64)
	h, err2 := strconv.ParseFloat(string(m[2]), 64)
	if err1 != nil || err2 != nil || h <= 0 || w <= 0 {
		return 0, false
	}
	return w / h, true
}
