import { Link } from "react-router-dom";
import { AlertTriangle, Check, Globe } from "lucide-react";
import { cn } from "@/lib/cn";
import type { Console } from "@/types/api";

interface ConsoleCardProps {
  console: Console;
  /**
   * Render an amber ⚠ in the top-right indicator group when the
   * console has missing required BIOS files. Mirrors the player app's
   * `ConsoleComponents.kt` behaviour so both clients show the same
   * "this console can't play games yet" cue at-a-glance, before the
   * detail-page banner. See #933.
   */
  hasMissingBios?: boolean;
}

// Card backgrounds derive from `Console.colorTheme` (the server-seeded
// brand hex) rather than a hard-coded Tailwind gradient table. This
// keeps the Consoles list, the Explore "Browse by Console" strip, and
// the console-detail hero on one source of truth — admins / seed
// updates change the colour in one place and every surface follows
// (#1167). The previous per-console `console-metadata` gradient table
// drifted from `colorTheme` (e.g. NES `red-600 → red-900` vs the
// server's `#e60012`) and produced visibly different palettes for the
// same library across the two pages.
const FALLBACK_THEME = "#6366f1";

export function ConsoleCard({ console: c, hasMissingBios = false }: ConsoleCardProps) {
  const theme = c.colorTheme || FALLBACK_THEME;

  return (
    <Link to={`/consoles/${c.id}`} className="group block">
      <div
        className={cn(
          "relative aspect-[16/10] rounded-2xl overflow-hidden transition-all duration-300",
          "group-hover:shadow-xl group-hover:shadow-black/30 group-hover:-translate-y-1",
          "border border-surface-800/50 group-hover:border-surface-700/50",
        )}
      >
        <div
          className="absolute inset-0"
          // Two-stop brand gradient mixed in sRGB: top-left is the
          // brand hex at full saturation; bottom-right is the same
          // hex mixed 60% with black so light themes (NES red, Game
          // Boy olive) get a vivid-to-shaded sweep, and dark themes
          // (Genesis #171717, Atari #1e293b) still grade visibly into
          // near-black rather than fading flat. Mirrors the spirit of
          // the previous Tailwind `from-X-600 to-X-900` pairs but
          // derives both stops from the single server-seeded
          // colorTheme — see #1167.
          style={{
            background: `linear-gradient(135deg, ${theme}, color-mix(in srgb, ${theme}, black 60%))`,
          }}
        />
        {/* Toward-bottom darken keeps text + indicators readable on
            light-saturation themes (e.g. Game Boy green). */}
        <div className="absolute inset-0 bg-gradient-to-b from-transparent via-transparent to-black/30 pointer-events-none" />


        <div className="absolute inset-0 flex flex-col items-center justify-center gap-3">
          <img
            src={c.iconUrl}
            alt={`${c.name} icon`}
            className="h-16 w-16 object-contain opacity-80 drop-shadow-lg transition-transform duration-300 group-hover:scale-110"
            style={{ imageRendering: "pixelated" }}
          />
          <div className="text-center">
            <h3 className="text-lg font-bold text-white">{c.name}</h3>
            <p className="text-sm text-white/60 mt-0.5">
              {c.gameCount} {c.gameCount === 1 ? "game" : "games"}
            </p>
          </div>
        </div>

        {/* Feature indicators */}
        {(hasMissingBios || c.saveStateSupport || c.browserPlayable) && (
          <div className="absolute top-2 right-2 flex gap-1">
            {hasMissingBios && (
              <div
                data-testid="console-card-bios-warning"
                className="flex h-6 w-6 items-center justify-center rounded-full bg-black/40 backdrop-blur-sm"
                title={`BIOS missing for ${c.name}`}
                aria-label={`BIOS missing for ${c.name}`}
              >
                <AlertTriangle className="h-3.5 w-3.5 text-amber-400" />
              </div>
            )}
            {c.saveStateSupport && (
              <div className="flex h-6 w-6 items-center justify-center rounded-full bg-black/40 backdrop-blur-sm" title="Save states supported">
                <Check className="h-3.5 w-3.5 text-emerald-400" />
              </div>
            )}
            {c.browserPlayable && (
              <div className="flex h-6 w-6 items-center justify-center rounded-full bg-black/40 backdrop-blur-sm" title="Browser playable">
                <Globe className="h-3.5 w-3.5 text-blue-400" />
              </div>
            )}
          </div>
        )}

        {/* Hover glow */}
        <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 bg-white/5" />
      </div>
    </Link>
  );
}
