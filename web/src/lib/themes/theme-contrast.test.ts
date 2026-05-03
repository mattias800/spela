// Locks in WCAG contrast ratios for the semantic text-emphasis tokens
// across each theme. If a future palette change accidentally pushes a
// token below its target ratio, this test fails before the regression
// ships. See #919.
//
// We hard-code the token values rather than parsing CSS (vitest doesn't
// have a real DOM with `:root` :where()-style cascade resolution that
// matches Tailwind 4's @theme block). The values must stay in sync with
// the corresponding CSS files under web/src/lib/themes/.

import { describe, expect, it } from "vitest";

// --- WCAG contrast helpers --------------------------------------------------

function relativeLuminance(hex: string): number {
  const m = hex.replace("#", "").match(/.{2}/g);
  if (!m || m.length !== 3) throw new Error(`bad hex: ${hex}`);
  const [r, g, b] = m.map((p) => {
    const v = parseInt(p, 16) / 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  }) as [number, number, number];
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrastRatio(fg: string, bg: string): number {
  const lf = relativeLuminance(fg);
  const lb = relativeLuminance(bg);
  const [light, dark] = lf > lb ? [lf, lb] : [lb, lf];
  return (light + 0.05) / (dark + 0.05);
}

const AA_BODY = 4.5;

// --- Token values per theme -------------------------------------------------
//
// Mirrors the CSS in web/src/lib/themes/. Update both sides together.

const TOKENS = {
  "default-dark": {
    bg: "#16191d", // --color-body
    primary: "#f1f3f5", // --color-text-primary
    secondary: "#cbd5e1", // --color-text-secondary
    tertiary: "#94a3b8", // --color-text-tertiary
    disabled: "#64748b", // --color-text-disabled
  },
  "default-light": {
    bg: "#ffffff",
    primary: "#212529",
    secondary: "#475569",
    tertiary: "#64748b",
    disabled: "#94a3b8",
  },
  "nintendo-colorful": {
    bg: "#faf8f4", // --color-body
    primary: "#2d2d44",
    secondary: "#3d3d5c",
    tertiary: "#5a5a72",
    disabled: "#9090a8",
  },
  "sunset-warm": {
    bg: "#fdf8f4",
    primary: "#3d2218",
    secondary: "#5c3525",
    tertiary: "#7a5040",
    disabled: "#a07a68",
  },
  "ocean-dark": {
    bg: "#04111f",
    primary: "#b3d9f2",
    secondary: "#80bfe0",
    tertiary: "#5599c4",
    disabled: "#3a7da8",
  },
} as const;

describe("theme contrast — semantic text tokens (#919)", () => {
  for (const [theme, t] of Object.entries(TOKENS)) {
    describe(theme, () => {
      it("text-primary clears AA body on its own bg", () => {
        expect(contrastRatio(t.primary, t.bg)).toBeGreaterThanOrEqual(AA_BODY);
      });

      it("text-secondary clears AA body on its own bg", () => {
        expect(contrastRatio(t.secondary, t.bg)).toBeGreaterThanOrEqual(
          AA_BODY,
        );
      });

      it("text-tertiary clears AA body on its own bg", () => {
        expect(contrastRatio(t.tertiary, t.bg)).toBeGreaterThanOrEqual(
          AA_BODY,
        );
      });

      // text-disabled is intentionally below AA body — that's the
      // semantic of disabled. We just verify it's perceptibly distinct
      // from the background, not bumping into "almost invisible".
      // Issue #919 documents 2.95:1 on white for default-light.
      it("text-disabled is perceptibly distinct from its bg (>= 2.5:1)", () => {
        expect(contrastRatio(t.disabled, t.bg)).toBeGreaterThanOrEqual(2.5);
      });
    });
  }
});

// --- Spot checks for the surface-N retunes mentioned in the issue ----------
//
// These are the safety-net values: surface-400 / surface-500 / muted on the
// light-theme body bgs that ~1000 unmigrated call sites still use as
// "muted text" classes.

describe("light-theme surface-N retune locks (#919)", () => {
  it("default-light text-surface-400 (#64748b) on white passes AA body", () => {
    expect(contrastRatio("#64748b", "#ffffff")).toBeGreaterThanOrEqual(
      AA_BODY,
    );
  });

  it("default-light text-surface-500 (#475569) on white passes AA body", () => {
    expect(contrastRatio("#475569", "#ffffff")).toBeGreaterThanOrEqual(
      AA_BODY,
    );
  });

  it("default-light text-muted (#475569) on white passes AA body", () => {
    expect(contrastRatio("#475569", "#ffffff")).toBeGreaterThanOrEqual(
      AA_BODY,
    );
  });

  it("default-light card-border (#cbd5e1) is visible on white (>= 1.4:1)", () => {
    // Borders don't need AA — they need to be perceptible. The original
    // #e9ecef was 1.18:1 (basically invisible). 1.4 is a soft floor.
    expect(contrastRatio("#cbd5e1", "#ffffff")).toBeGreaterThanOrEqual(1.4);
  });
});
