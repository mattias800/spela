package api

import (
	"log/slog"
	"math"
	"regexp"
	"strconv"
	"strings"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// viewBoxRe matches an SVG viewBox attribute and captures width + height.
// Spec: viewBox = "min-x min-y width height" (whitespace-separated, can
// be negative or decimal).
var viewBoxRe = regexp.MustCompile(
	`viewBox\s*=\s*"\s*[-0-9.]+\s+[-0-9.]+\s+([-0-9.]+)\s+([-0-9.]+)\s*"`,
)

// BackfillConsoleLogoAspectRatios reads each console's logo SVG from
// the embedded assets, parses its viewBox to determine intrinsic
// width/height, and stores the ratio on the console row's
// LogoAspectRatio field. Runs after [db.SeedConsoles] from cmd/server.
//
// Why this exists — without an intrinsic ratio cached server-side,
// the player app's console-detail hero shows the logo at a square-
// equivalent fallback size on first render, then re-layouts to the
// correct dimensions once the image bytes have decoded and the
// SubcomposeAsyncImage onSuccess callback fires. The visible
// size-A → size-B "jump" is exactly what shipping the ratio with
// the Console DTO removes (#1166).
//
// Idempotent — only writes when the stored value differs from the
// freshly-computed one (within 0.001 tolerance for floating-point
// noise). Consoles whose SVG can't be read or doesn't have a viewBox
// silently keep LogoAspectRatio nil; the client falls back to the
// legacy fluid sizing for them.
//
// Honours consoleLogoFallbacks the same way HumaGetConsoleLogo does
// so child platforms (ADEMO → AMIGA, DDEMO → DOS) inherit their
// parent's aspect ratio when they have no asset of their own.
func BackfillConsoleLogoAspectRatios(database *gorm.DB) error {
	var consoles []db.Console
	if err := database.Find(&consoles).Error; err != nil {
		return err
	}

	updated := 0
	for _, c := range consoles {
		abbr := strings.ToLower(c.Abbreviation)
		lookup := abbr
		if parent, ok := consoleLogoFallbacks[abbr]; ok {
			lookup = parent
		}

		data, err := consoleLogos.ReadFile("static/console-logos/" + lookup + ".svg")
		if err != nil {
			continue
		}

		ratio, ok := parseViewBoxAspectRatio(data)
		if !ok {
			continue
		}

		if c.LogoAspectRatio != nil && math.Abs(*c.LogoAspectRatio-ratio) < 0.001 {
			continue
		}

		if err := database.Model(&c).Update("logo_aspect_ratio", ratio).Error; err != nil {
			slog.Warn("failed to set logo_aspect_ratio",
				"console", c.Abbreviation, "ratio", ratio, "error", err)
			continue
		}
		updated++
	}

	if updated > 0 {
		slog.Info("backfilled console logo aspect ratios", "count", updated)
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
