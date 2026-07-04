import { Link } from "react-router-dom";
import { Heart, Clock, Loader2 } from "lucide-react";
import { RatingDisplay } from "@/components/rating-display";
import { cn } from "@/lib/cn";
import { ConsoleBadge } from "@/components/console-badge";
import { useAutoScrape } from "@/hooks/use-auto-scrape";
import { getReleaseYear } from "@/lib/date-utils";
import type { Game } from "@/types/api";

type GamePlatform = Game["platforms"][number];

const COMPACT_LABEL_MAX_LENGTH = 12;
const COMPACT_ID_MIN_LENGTH = 2;
const COMPACT_ID_MAX_LENGTH = 6;

interface GameCardProps {
  game: Game;
  aspectRatio?: number;
  coverHeight?: number;
  showConsoleBadge?: boolean;
  hideConsoleName?: boolean;
  subtitle?: string;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function GameCard({
  game,
  aspectRatio,
  coverHeight,
  showConsoleBadge,
  hideConsoleName,
  subtitle,
  onToggleFavorite,
  onTogglePlayLater,
}: GameCardProps) {
  const { ref, isScraping } = useAutoScrape(game);
  const platformTargets = getPlatformTargets(game);

  return (
    <div
      ref={ref}
      data-comp="GameCard"
      className={cn(
        "group block space-y-3",
        coverHeight && "flex-shrink-0 inline-block",
      )}
    >
      <div
        className="relative rounded-2xl overflow-hidden bg-surface-900 border border-surface-800/50 transition-all duration-300 group-hover:border-surface-700/50 group-hover:shadow-xl group-hover:shadow-black/30 group-hover:-translate-y-1"
        style={
          coverHeight
            ? { height: coverHeight, width: "fit-content" }
            : undefined
        }
      >
        <Link
          to={`/games/${game.id}`}
          className="block"
          aria-label={`Open ${game.title}`}
        >
          {game.coverUrl ? (
            <img
              src={game.coverUrl}
              alt={game.title}
              className={
                coverHeight
                  ? "h-full w-auto transition-transform duration-500 group-hover:scale-105"
                  : "w-full transition-transform duration-500 group-hover:scale-105"
              }
              loading="lazy"
            />
          ) : (
            <div
              className="flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900"
              style={
                coverHeight
                  ? {
                      height: coverHeight,
                      aspectRatio:
                        game.coverAspectRatio ?? aspectRatio ?? 3 / 4,
                    }
                  : {
                      aspectRatio:
                        game.coverAspectRatio ?? aspectRatio ?? 3 / 4,
                    }
              }
            >
              {isScraping ? (
                <Loader2 className="h-6 w-6 animate-spin text-surface-500" />
              ) : (
                <span className="text-3xl font-bold text-surface-700">
                  {game.title.charAt(0)}
                </span>
              )}
            </div>
          )}

          {/* Hover overlay */}
          <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />

          {/* Pre-release badge */}
          {game.isPreRelease && (
            <div className="absolute top-2.5 left-2.5 z-10">
              <span className="inline-flex items-center rounded-full bg-warning-500/80 backdrop-blur-sm px-2 py-0.5 text-[10px] font-medium text-white">
                Pre-release
              </span>
            </div>
          )}

          {/* Console badge */}
          {game.consoleName && (
            <div
              className={cn(
                "absolute bottom-2.5 left-2.5 transition-opacity duration-300",
                showConsoleBadge
                  ? "opacity-100"
                  : "opacity-0 group-hover:opacity-100",
              )}
            >
              <ConsoleBadge code={game.consoleId} label={game.consoleName} />
            </div>
          )}

          {/* Variant count badge */}
          {game.variantCount != null && game.variantCount > 1 && (
            <div className="absolute bottom-2.5 right-2.5 z-10">
              <span className="inline-flex items-center rounded-full bg-surface-500/80 backdrop-blur-sm px-2 py-0.5 text-[10px] font-medium text-white">
                {game.variantCount - 1}{" "}
                {game.variantCount === 2 ? "version" : "versions"}
              </span>
            </div>
          )}
        </Link>

        {/* Favorite button */}
        {onToggleFavorite && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              onToggleFavorite(game);
            }}
            className={cn(
              "absolute top-2.5 right-2.5 p-2 rounded-full transition-all duration-200 z-20",
              "opacity-0 group-hover:opacity-100",
              game.isFavorite
                ? "bg-danger-500/20 text-danger-500 opacity-100"
                : "bg-black/40 text-white/70 hover:text-white hover:bg-black/60",
            )}
            aria-label={
              game.isFavorite ? "Remove from favorites" : "Add to favorites"
            }
          >
            <Heart
              className={cn("h-4 w-4", game.isFavorite && "fill-current")}
            />
          </button>
        )}

        {/* Play Later button — hidden if already in queue (use game detail to remove) */}
        {onTogglePlayLater && !game.isInPlayLater && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              onTogglePlayLater(game);
            }}
            className={cn(
              "absolute top-2.5 right-12 p-2 rounded-full transition-all duration-200 z-20",
              "opacity-0 group-hover:opacity-100",
              "bg-black/40 text-white/70 hover:text-white hover:bg-black/60",
            )}
            aria-label="Add to play later"
          >
            <Clock className="h-4 w-4" />
          </button>
        )}
        {/* Show filled clock icon when already in queue (not clickable) */}
        {game.isInPlayLater && (
          <div className="absolute top-2.5 right-12 p-2 rounded-full bg-brand-500/20 text-brand-400 z-20">
            <Clock className="h-4 w-4 fill-current" />
          </div>
        )}
      </div>

      <div
        className={cn(
          "px-1 space-y-1",
          coverHeight && "w-0 min-w-full overflow-hidden",
        )}
      >
        <div className="space-y-1">
          <h3 className="text-sm font-semibold text-surface-100 truncate group-hover:text-brand-400 transition-colors">
            {game.title}
          </h3>
          {game.discCount > 1 && (
            <span className="inline-block whitespace-nowrap text-xs bg-purple-600 text-white rounded-full px-1.5 py-0.5">
              {game.discCount} Discs
            </span>
          )}
          <div className="flex items-center gap-2">
            {hideConsoleName
              ? (game.releaseDate || game.developer) && (
                  <p className="text-xs text-surface-500">
                    {getReleaseYear(game.releaseDate) ?? game.developer}
                  </p>
                )
              : game.consoleName && (
                  <p className="text-xs text-surface-500">{game.consoleName}</p>
                )}
            {game.averageRating > 0 && (
              <RatingDisplay value={game.averageRating} />
            )}
          </div>
          {subtitle && (
            <p className="text-xs text-surface-400" data-testid="release-year">
              {subtitle}
            </p>
          )}
        </div>
        {platformTargets.length > 1 && (
          <div
            className="flex flex-wrap gap-1.5 pt-0.5"
            data-testid={`game-platform-pills-${game.id}`}
          >
            {platformTargets.map((platform) => {
              const isCurrent =
                platform.gameId === game.id || platform.isPreferred;
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

              return (
                <Link
                  key={platform.gameId}
                  to={`/games/${platform.gameId}`}
                  className="rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950"
                  aria-label={`Open ${game.title} on ${platform.consoleName}`}
                >
                  <ConsoleBadge
                    code={platform.consoleId}
                    label={label}
                    className="text-[10px] transition-colors hover:border-white/60"
                  />
                </Link>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

function getPlatformTargets(game: Game): GamePlatform[] {
  const fallback: GamePlatform = {
    gameId: game.id,
    consoleId: game.consoleId,
    consoleName: game.consoleName,
    isPreferred: true,
  };
  const source = game.platforms.length > 0 ? game.platforms : [fallback];
  const seen = new Set<string>();
  return source.reduce<GamePlatform[]>((targets, platform) => {
    if (seen.has(platform.gameId)) return targets;
    seen.add(platform.gameId);
    targets.push(
      platform.gameId === game.id && !platform.isPreferred
        ? { ...platform, isPreferred: true }
        : platform,
    );
    return targets;
  }, []);
}

function compactPlatformLabel(platform: GamePlatform): string {
  const name = platform.consoleName || platform.consoleId.toUpperCase();
  if (name.length <= COMPACT_LABEL_MAX_LENGTH) return name;

  const id = platform.consoleId.toUpperCase();
  const compactId =
    id.length >= COMPACT_ID_MIN_LENGTH &&
    id.length <= COMPACT_ID_MAX_LENGTH &&
    /^[A-Z0-9-]+$/.test(id);
  return compactId ? id : name;
}
