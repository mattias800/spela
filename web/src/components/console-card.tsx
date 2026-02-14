import { Link } from "react-router-dom";
import { cn } from "@/lib/cn";
import { getConsoleStyle } from "@/lib/console-metadata";
import type { Console } from "@/types/api";

interface ConsoleCardProps {
  console: Console;
}

export function ConsoleCard({ console: c }: ConsoleCardProps) {
  const style = getConsoleStyle(c.abbreviation);
  const Icon = style.icon;

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
          <div className="h-14 w-14 rounded-2xl bg-white/10 backdrop-blur-sm flex items-center justify-center transition-transform duration-300 group-hover:scale-110">
            <Icon className="h-7 w-7 text-white" />
          </div>
          <div className="text-center">
            <h3 className="text-lg font-bold text-white">{c.name}</h3>
            <p className="text-sm text-white/60 mt-0.5">
              {c.gameCount} {c.gameCount === 1 ? "game" : "games"}
            </p>
          </div>
        </div>

        {/* Hover glow */}
        <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 bg-white/5" />
      </div>
    </Link>
  );
}
