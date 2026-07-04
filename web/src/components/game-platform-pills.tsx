import { Link } from "react-router-dom";
import { ConsoleBadge } from "@/components/console-badge";
import { Badge } from "@/components/ui";
import { cn } from "@/lib/cn";
import {
  compactPlatformLabel,
  getGamePlatformTargets,
  type GamePlatformTarget,
} from "@/lib/game-platforms";

interface GamePlatformPillsProps<T extends GamePlatformTarget> {
  gameId: string;
  title: string;
  consoleId: string;
  consoleName: string;
  platforms: readonly T[];
  align?: "start" | "end";
  className?: string;
  showSingle?: boolean;
  maxVisible?: number;
  testId?: string;
  onNavigate?: (path: string) => void;
}

export function GamePlatformPills<T extends GamePlatformTarget>({
  gameId,
  title,
  consoleId,
  consoleName,
  platforms,
  align = "start",
  className,
  showSingle = false,
  maxVisible,
  testId,
  onNavigate,
}: GamePlatformPillsProps<T>) {
  const targets = preferredFirst(
    getGamePlatformTargets({
      id: gameId,
      consoleId,
      consoleName,
      platforms,
    }),
  );

  if (targets.length <= 1 && !showSingle) return null;

  const visibleLimit =
    maxVisible !== undefined ? Math.max(1, maxVisible) : targets.length;
  const visibleTargets = targets.slice(0, visibleLimit);
  const hiddenCount = targets.length - visibleTargets.length;

  return (
    <div
      className={cn(
        "flex flex-wrap gap-1.5",
        align === "end" && "justify-end",
        className,
      )}
      data-testid={testId}
    >
      {visibleTargets.map((platform) => {
        const isCurrent = platform.isPreferred;
        const label = compactPlatformLabel(platform);
        if (isCurrent) {
          return (
            <span
              key={platform.gameId}
              aria-label={`Current platform ${platform.consoleName}`}
            >
              <ConsoleBadge
                code={platform.consoleId}
                label={label}
                className="text-[10px]"
              />
            </span>
          );
        }

        const path = `/games/${platform.gameId}`;
        const actionClassName =
          "rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950";
        const ariaLabel = `Open ${title} on ${platform.consoleName}`;
        const badge = (
          <ConsoleBadge
            code={platform.consoleId}
            label={label}
            className="text-[10px] transition-colors hover:border-white/60"
          />
        );

        if (onNavigate) {
          return (
            <button
              key={platform.gameId}
              type="button"
              className={actionClassName}
              aria-label={ariaLabel}
              onClick={(event) => {
                event.stopPropagation();
                onNavigate(path);
              }}
              onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") {
                  event.stopPropagation();
                }
              }}
            >
              {badge}
            </button>
          );
        }

        return (
          <Link
            key={platform.gameId}
            to={path}
            className={actionClassName}
            aria-label={ariaLabel}
            onClick={(event) => event.stopPropagation()}
          >
            {badge}
          </Link>
        );
      })}
      {hiddenCount > 0 && (
        <Badge
          aria-label={`${hiddenCount} more ${hiddenCount === 1 ? "platform" : "platforms"}`}
          className="text-[10px] px-2"
        >
          +{hiddenCount}
        </Badge>
      )}
    </div>
  );
}

function preferredFirst(targets: GamePlatformTarget[]): GamePlatformTarget[] {
  const preferredIndex = targets.findIndex((platform) => platform.isPreferred);
  if (preferredIndex <= 0) return targets;

  const ordered = [...targets];
  const [preferred] = ordered.splice(preferredIndex, 1);
  return [preferred, ...ordered];
}
