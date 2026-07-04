import { Link } from "react-router-dom";
import { Gamepad2 } from "lucide-react";
import { ConsoleBadge } from "@/components/console-badge";
import { TitledSection } from "@/components/layout";
import { getGamePlatformTargets } from "@/lib/game-platforms";
import type { Game } from "@/types/api";

interface AlsoOnPlatformsSectionProps {
  game: Game;
}

export function AlsoOnPlatformsSection({
  game,
}: AlsoOnPlatformsSectionProps) {
  const targets = getGamePlatformTargets(game);
  if (targets.length <= 1) return null;

  return (
    <TitledSection
      title="Also on"
      icon={Gamepad2}
      contained
      data-testid="also-on-platforms-section"
    >
      <ul className="flex flex-wrap gap-2">
        {targets.map((target) => {
          const isCurrent = target.isPreferred;

          if (isCurrent) {
            return (
              <li
                key={target.gameId}
                aria-current="page"
                className="inline-flex items-center gap-2 rounded-full border border-brand-500/40 bg-brand-500/10 px-3 py-2"
              >
                <ConsoleBadge
                  code={target.consoleId}
                  label={target.consoleName}
                />
                <span className="text-xs font-medium text-brand-300">
                  Current
                </span>
              </li>
            );
          }

          return (
            <li key={target.gameId}>
              <Link
                to={`/games/${target.gameId}`}
                aria-label={`Open ${game.title} on ${target.consoleName}`}
                className="inline-flex items-center rounded-full border border-surface-700/60 bg-surface-900/60 px-3 py-2 transition-colors hover:border-brand-400/70 hover:bg-surface-800/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950"
              >
                <ConsoleBadge
                  code={target.consoleId}
                  label={target.consoleName}
                  className="transition-colors"
                />
              </Link>
            </li>
          );
        })}
      </ul>
    </TitledSection>
  );
}
