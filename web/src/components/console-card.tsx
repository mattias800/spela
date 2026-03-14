import { Link } from "react-router-dom";
import { Check, Globe } from "lucide-react";
import { cn } from "@/lib/cn";
import { getConsoleStyle } from "@/lib/console-metadata";
import type { Console } from "@/types/api";

interface ConsoleCardProps {
  console: Console;
}

export function ConsoleCard({ console: c }: ConsoleCardProps) {
  const style = getConsoleStyle(c.abbreviation);

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
          className={cn("absolute inset-0 bg-gradient-to-br", style.gradient)}
        />

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
        {(c.saveStateSupport || c.browserPlayable) && (
          <div className="absolute top-2 right-2 flex gap-1">
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
