import { Link } from "react-router-dom";
import { AlertTriangle, Check, Globe } from "lucide-react";
import { cn } from "@/lib/cn";
import type { Console } from "@/types/api";

interface ConsoleCardProps {
  console: Console;
  /**
   * Render an amber ⚠ next to the game count when the console has missing
   * required BIOS files. Mirrors the player app's `ConsoleComponents.kt`
   * behaviour so both clients show the same "this console can't play games
   * yet" cue at-a-glance, before the detail-page banner. See #933.
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
      {/* The card is split into three stacked regions with fixed proportions —
          photo, logo, footer. Each region centers its own content, so a photo's
          (and a logo's) vertical centre lands at the same height on every card
          in a row regardless of the image's aspect ratio. The footer is a fixed
          height and bottom-aligned, so the optional tag never shifts the photo /
          logo regions and the game count lines up across cards too. The brand
          gradient is the card's own background and the hover lift is a plain
          transform — no absolute positioning. */}
      <div
        className={cn(
          "flex aspect-[4/5] flex-col overflow-hidden rounded-2xl p-4 text-center",
          "border border-surface-800/50 transition-all duration-300",
          "group-hover:-translate-y-1 group-hover:border-surface-700/50 group-hover:shadow-xl group-hover:shadow-black/30 group-hover:brightness-105",
        )}
        style={{
          background: `linear-gradient(135deg, ${theme}, color-mix(in srgb, ${theme}, black 60%))`,
        }}
      >
        {/* Photo region (or pixel-icon fallback) */}
        <div className="flex min-h-0 flex-[3] items-center justify-center">
          <img
            src={c.photoUrl ?? c.iconUrl}
            alt={c.name}
            className={cn(
              "object-contain drop-shadow-xl transition-transform duration-300 group-hover:scale-105",
              c.photoUrl ? "max-h-full max-w-full" : "max-h-[72%] max-w-[60%] opacity-80",
            )}
            style={c.photoUrl ? undefined : { imageRendering: "pixelated" }}
          />
        </div>

        {/* Logo region (or name fallback) */}
        <div className="flex min-h-0 flex-[2] items-center justify-center">
          {c.logoUrl ? (
            <img
              src={c.logoUrl}
              alt={c.name}
              className="max-h-full max-w-[92%] object-contain drop-shadow-md"
            />
          ) : (
            <h3 className="text-xl font-bold text-white">{c.name}</h3>
          )}
        </div>

        {/* Footer: fixed height, bottom-aligned. Holds the optional qualifier
            chip (e.g. "Demos", data-driven via Console.tag) above the game
            count + feature indicators. */}
        <div className="flex h-[18%] flex-none flex-col items-center justify-end gap-1">
          {c.tag && (
            <span className="rounded-full bg-black/30 px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-white/90 backdrop-blur-sm">
              {c.tag}
            </span>
          )}
          <div className="flex items-center gap-2 text-white/70">
            <span className="text-xs font-medium">
              {c.gameCount} {c.gameCount === 1 ? "game" : "games"}
            </span>
            {hasMissingBios && (
              <AlertTriangle
                data-testid="console-card-bios-warning"
                className="h-3.5 w-3.5 text-amber-400"
                aria-label={`BIOS missing for ${c.name}`}
              />
            )}
            {c.saveStateSupport && (
              <Check className="h-3.5 w-3.5 text-emerald-400" aria-label="Save states supported" />
            )}
            {c.browserPlayable && (
              <Globe className="h-3.5 w-3.5 text-blue-400" aria-label="Browser playable" />
            )}
          </div>
        </div>
      </div>
    </Link>
  );
}
