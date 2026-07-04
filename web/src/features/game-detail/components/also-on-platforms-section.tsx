import { Link } from "react-router-dom";
import { Gamepad2 } from "lucide-react";
import { ConsoleBadge } from "@/components/console-badge";
import { TitledSection } from "@/components/layout";
import { Button } from "@/components/ui";
import { useSetTitlePlatformPreference } from "@/hooks/use-games";
import { getGamePlatformTargets } from "@/lib/game-platforms";
import type { Game } from "@/types/api";

interface AlsoOnPlatformsSectionProps {
  game: Game;
}

export function AlsoOnPlatformsSection({
  game,
}: AlsoOnPlatformsSectionProps) {
  const targets = getGamePlatformTargets(game);
  const preference = useSetTitlePlatformPreference();
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
          const isCurrent = target.isCurrent === true;
          const isSaving =
            preference.isPending && preference.variables === target.gameId;

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
                {target.isPreferred && (
                  <span className="text-xs font-medium text-brand-200">
                    Preferred
                  </span>
                )}
                {!target.isPreferred && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    loading={isSaving}
                    onClick={() => preference.mutate(target.gameId)}
                    className="h-7 px-2 text-xs"
                  >
                    Prefer
                  </Button>
                )}
              </li>
            );
          }

          return (
            <li
              key={target.gameId}
              className="inline-flex items-center gap-2 rounded-full border border-surface-700/60 bg-surface-900/60 px-3 py-2"
            >
              <Link
                to={`/games/${target.gameId}`}
                aria-label={`Open ${game.title} on ${target.consoleName}`}
                className="inline-flex items-center rounded-full transition-colors hover:opacity-80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950"
              >
                <ConsoleBadge
                  code={target.consoleId}
                  label={target.consoleName}
                  className="transition-colors"
                />
              </Link>
              {target.isPreferred ? (
                <span className="text-xs font-medium text-brand-300">
                  Preferred
                </span>
              ) : (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  loading={isSaving}
                  onClick={() => preference.mutate(target.gameId)}
                  className="h-7 px-2 text-xs"
                >
                  Prefer
                </Button>
              )}
            </li>
          );
        })}
      </ul>
      {preference.isError && (
        <p className="mt-3 text-sm text-danger-400">
          Could not save preferred platform.
        </p>
      )}
    </TitledSection>
  );
}
