import { Check, Globe } from "lucide-react";
import { cn } from "@/lib/cn";
import type { Console } from "@/types/api";

interface ConsoleHeroBannerProps {
  console: Console | undefined;
  gameCount?: number;
}

// Hero gradient derives from `Console.colorTheme` (same source the
// Explore strip + Consoles list use) so the three console-identity
// surfaces stay in sync — see #1167.
const FALLBACK_THEME = "#6366f1";

export function ConsoleHeroBanner({
  console: consoleData,
  gameCount,
}: ConsoleHeroBannerProps) {
  const consoleName = consoleData?.name ?? "Console";
  const count = gameCount ?? consoleData?.gameCount ?? 0;
  const theme = consoleData?.colorTheme || FALLBACK_THEME;

  return (
    <div data-comp="ConsoleHeroBanner"
      className={cn(
        "relative overflow-hidden rounded-2xl border border-white/[0.06]",
      )}
      // Hero is the biggest console-coloured surface in the app; we
      // push the alpha higher than the card list so the brand reads
      // clearly even from the back of a couch. The far-end fade to
      // black keeps the metadata row legible regardless of theme
      // luminance.
      style={{
        background: `linear-gradient(135deg, ${theme}dd, ${theme}66 60%, #000000aa)`,
      }}
      data-testid="console-hero-banner"
    >
      {/* Background watermark icon for depth */}
      {consoleData?.iconUrl && (
        <div className="absolute -right-8 -top-8 opacity-[0.07] pointer-events-none">
          <img
            src={consoleData.iconUrl}
            alt=""
            aria-hidden="true"
            className="h-56 w-56 object-contain"
            style={{ imageRendering: "pixelated" }}
          />
        </div>
      )}

      {/* Subtle noise/texture overlay */}
      <div className="absolute inset-0 bg-gradient-to-t from-black/30 via-transparent to-white/[0.04] pointer-events-none" />

      {/* Content */}
      <div className="relative px-6 py-8 md:py-10">
        <div className="flex flex-col items-center">
        {/* Logo / Title */}
        {consoleData?.logoUrl ? (
          <img
            src={consoleData.logoUrl}
            alt={consoleName}
            className="max-h-20 md:max-h-24 w-auto object-contain drop-shadow-[0_2px_12px_rgba(0,0,0,0.4)]"
            onError={(e) => {
              const target = e.currentTarget;
              target.style.display = "none";
              const fallback = target.nextElementSibling;
              if (fallback) fallback.classList.remove("hidden");
            }}
          />
        ) : null}
        <h1
          className={cn(
            "text-4xl md:text-5xl font-bold text-white tracking-tight drop-shadow-lg",
            consoleData?.logoUrl && "hidden",
          )}
        >
          {consoleName}
        </h1>

        {/* Metadata row */}
        <div className="flex flex-wrap items-center justify-center gap-3 mt-4">
          <span className="text-sm font-medium text-white/70">
            {count} {count === 1 ? "game" : "games"}
          </span>
          {consoleData?.maker && (
            <span className="text-sm font-medium text-white/70">
              {consoleData.maker.name}
            </span>
          )}
          {consoleData?.releaseYear && (
            <span className="text-sm font-medium text-white/70">
              {consoleData.releaseYear}
            </span>
          )}
          {consoleData?.mediaType && (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 backdrop-blur-sm px-3 py-1 text-xs font-medium text-white/90">
              {consoleData.mediaType.name}
            </span>
          )}
          {consoleData?.saveStateSupport && (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 backdrop-blur-sm px-3 py-1 text-xs font-medium text-white/90">
              <Check className="h-3 w-3" />
              Save states
            </span>
          )}
          {consoleData?.browserPlayable && (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 backdrop-blur-sm px-3 py-1 text-xs font-medium text-white/90">
              <Globe className="h-3 w-3" />
              Browser play
            </span>
          )}
        </div>
        </div>
      </div>
    </div>
  );
}
