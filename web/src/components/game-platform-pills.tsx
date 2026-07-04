import { Link } from "react-router-dom";
import { ConsoleBadge } from "@/components/console-badge";
import { Badge } from "@/components/ui";
import { cn } from "@/lib/cn";

export interface GamePlatformTarget {
  gameId: string;
  consoleId: string;
  consoleName: string;
  isPreferred: boolean;
}

const COMPACT_LABEL_MAX_LENGTH = 12;
const COMPACT_ID_MIN_LENGTH = 2;
const COMPACT_ID_MAX_LENGTH = 6;

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
  const targets = getPlatformTargets({
    gameId,
    consoleId,
    consoleName,
    platforms,
  });

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
        const isCurrent = isCurrentPlatform(platform, gameId);
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

function getPlatformTargets<T extends GamePlatformTarget>({
  gameId,
  consoleId,
  consoleName,
  platforms,
}: {
  gameId: string;
  consoleId: string;
  consoleName: string;
  platforms: readonly T[];
}): GamePlatformTarget[] {
  const fallback: GamePlatformTarget = {
    gameId,
    consoleId,
    consoleName,
    isPreferred: true,
  };
  const source = platforms.length > 0 ? platforms : [fallback];
  const hasCurrentGame = source.some((platform) => platform.gameId === gameId);
  const seen = new Set<string>();
  const targets = source.reduce<GamePlatformTarget[]>((result, platform) => {
    if (seen.has(platform.gameId)) return result;
    seen.add(platform.gameId);
    result.push({
      ...platform,
      isPreferred: hasCurrentGame
        ? platform.gameId === gameId
        : platform.isPreferred,
    });
    return result;
  }, []);

  const currentIndex = targets.findIndex((platform) =>
    isCurrentPlatform(platform, gameId),
  );
  if (currentIndex <= 0) return targets;

  const [current] = targets.splice(currentIndex, 1);
  return [current, ...targets];
}

function isCurrentPlatform(platform: GamePlatformTarget, gameId: string) {
  return platform.gameId === gameId || platform.isPreferred;
}

function compactPlatformLabel(platform: GamePlatformTarget): string {
  const name = platform.consoleName || platform.consoleId.toUpperCase();
  if (name.length <= COMPACT_LABEL_MAX_LENGTH) return name;

  const id = platform.consoleId.toUpperCase();
  const compactId =
    id.length >= COMPACT_ID_MIN_LENGTH &&
    id.length <= COMPACT_ID_MAX_LENGTH &&
    /^[A-Z0-9-]+$/.test(id);
  return compactId ? id : name;
}
