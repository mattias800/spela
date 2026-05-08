import { Check, Globe } from "lucide-react";
import { getConsoleStyle } from "@/lib/console-metadata";
import { cn } from "@/lib/cn";
import type { Console } from "@/types/api";

interface ConsoleHeroBannerProps {
  console: Console | undefined;
  gameCount?: number;
}

export function ConsoleHeroBanner({
  console: consoleData,
  gameCount,
}: ConsoleHeroBannerProps) {
  const consoleName = consoleData?.name ?? "Console";
  const consoleAbbr = consoleData?.abbreviation ?? "";
  const count = gameCount ?? consoleData?.gameCount ?? 0;
  const style = getConsoleStyle(consoleAbbr);
  const Icon = style.icon;

  return (
    <div data-comp="ConsoleHeroBanner"
      className={cn(
        "relative overflow-hidden rounded-2xl border border-white/[0.06]",
        "bg-gradient-to-br",
        style.gradient,
      )}
      data-testid="console-hero-banner"
    >
      {/* Background watermark icon for depth */}
      <div className="absolute -right-8 -top-8 opacity-[0.07] pointer-events-none">
        {consoleData?.iconUrl ? (
          <img
            src={consoleData.iconUrl}
            alt=""
            aria-hidden="true"
            className="h-56 w-56 object-contain"
            style={{ imageRendering: "pixelated" }}
          />
        ) : (
          <Icon className="h-56 w-56 text-white" />
        )}
      </div>

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
