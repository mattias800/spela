import { type ReactNode } from "react";
import {
  Heart,
  Calendar,
  Users,
  Building2,
  Star,
  HardDrive,
  Disc,
  RefreshCw,
  Play,
  Trophy,
  Clock,
  Ellipsis,
} from "lucide-react";
import { Button, Badge, DropdownMenu } from "@/components/ui";
import { MetaItem } from "@/components/meta-item";
import {
  formatFileSize,
  formatPlayTime,
  formatRelativeTime,
} from "@/lib/format";
import { cn } from "@/lib/cn";
import type { Game } from "@/types/api";

interface GameHeroProps {
  game: Game;
  aspectRatio?: number;
  canPlayInBrowser: boolean;
  isAdmin: boolean;
  isFavorite: boolean;
  isInPlayLater: boolean;
  isPlayLaterPending?: boolean;
  isScraping: boolean;
  hasAchievements?: boolean;
  hasSaves?: boolean;
  extraButtons?: ReactNode;
  extraMenuButtons?: ReactNode;
  onPlay: () => void;
  onPlayFresh?: () => void;
  onScrape: () => void;
  onToggleFavorite: () => void;
  onTogglePlayLater: () => void;
}

function OverflowMenu({
  isAdmin,
  isFavorite,
  isInPlayLater,
  isPlayLaterPending,
  isScraping,
  extraMenuButtons,
  onScrape,
  onToggleFavorite,
  onTogglePlayLater,
}: Pick<
  GameHeroProps,
  | "isAdmin"
  | "isFavorite"
  | "isInPlayLater"
  | "isPlayLaterPending"
  | "isScraping"
  | "extraMenuButtons"
  | "onScrape"
  | "onToggleFavorite"
  | "onTogglePlayLater"
>) {
  return (
    <DropdownMenu
      align="right"
      className="w-56"
      trigger={
        <Button variant="secondary" size="sm" data-testid="overflow-menu-btn">
          <Ellipsis className="h-5 w-5" />
        </Button>
      }
    >
      {isAdmin && (
        <Button
          variant="ghost"
          size="sm"
          onClick={onScrape}
          loading={isScraping}
          className="w-full justify-start rounded-none"
        >
          <RefreshCw className="h-4 w-4" />
          Scrape Metadata
        </Button>
      )}
      <Button
        variant="ghost"
        size="sm"
        onClick={onToggleFavorite}
        className="w-full justify-start rounded-none"
      >
        <Heart
          className={cn(
            "h-4 w-4",
            isFavorite && "fill-current text-danger-500",
          )}
        />
        {isFavorite ? "Unfavorite" : "Favorite"}
      </Button>
      <Button
        variant="ghost"
        size="sm"
        onClick={onTogglePlayLater}
        disabled={isPlayLaterPending}
        className="w-full justify-start rounded-none"
      >
        <Clock
          className={cn(
            "h-4 w-4",
            isInPlayLater && "fill-current text-brand-500",
          )}
        />
        {isInPlayLater ? "In Queue" : "Play Later"}
      </Button>
      {extraMenuButtons}
    </DropdownMenu>
  );
}

export function GameHero({
  game,
  aspectRatio,
  canPlayInBrowser,
  isAdmin,
  isFavorite,
  isInPlayLater,
  isPlayLaterPending,
  isScraping,
  hasAchievements,
  hasSaves,
  extraButtons,
  extraMenuButtons,
  onPlay,
  onPlayFresh,
  onScrape,
  onToggleFavorite,
  onTogglePlayLater,
}: GameHeroProps) {
  const consoleName = game.consoleName ?? "";

  return (
    <div className="flex flex-col items-center gap-6 md:flex-row md:items-start md:gap-8">
      {/* Cover art */}
      <div className="w-48 flex-shrink-0 md:w-64">
        <div
          className="rounded-2xl overflow-hidden bg-surface-900 border border-surface-800 shadow-2xl"
          style={{ aspectRatio: aspectRatio ?? 3 / 4 }}
        >
          {game.coverUrl ? (
            <img
              src={game.coverUrl}
              alt={game.title}
              className="h-full w-full object-cover"
            />
          ) : (
            <div className="h-full w-full flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900">
              <span className="text-5xl font-bold text-surface-700">
                {game.title.charAt(0)}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Info */}
      <div className="w-full min-w-0 flex-1 space-y-5 pt-2">
        <div className="space-y-4">
          <div>
            <h1 className="text-2xl font-bold text-surface-100 flex items-center gap-2 md:text-3xl">
              {game.title}
              {hasAchievements && (
                <Trophy
                  className="h-6 w-6 text-amber-400"
                  data-testid="achievements-trophy"
                />
              )}
            </h1>
            <div className="flex items-center gap-3 mt-2">
              {consoleName && <Badge variant="brand">{consoleName}</Badge>}
              {game.averageRating > 0 && (
                <span className="flex items-center gap-1 text-sm text-surface-400">
                  <Star className="h-4 w-4 text-amber-400 fill-amber-400" />
                  {game.averageRating.toFixed(1)}
                  <span className="text-surface-500">({game.ratingCount})</span>
                </span>
              )}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {hasSaves ? (
              <>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={onPlay}
                  disabled={!canPlayInBrowser}
                  title={
                    canPlayInBrowser
                      ? "Resume game with latest save"
                      : `${game.consoleName} is not supported for browser play`
                  }
                  data-testid="resume-btn"
                >
                  <Play className="h-5 w-5" />
                  Resume
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={onPlayFresh}
                  disabled={!canPlayInBrowser}
                  title="Start a new game without loading saves"
                  data-testid="new-game-btn"
                >
                  <Play className="h-5 w-5" />
                  New Game
                </Button>
              </>
            ) : (
              <Button
                variant="primary"
                size="sm"
                onClick={onPlay}
                disabled={!canPlayInBrowser}
                title={
                  canPlayInBrowser
                    ? "Play in Browser"
                    : `${game.consoleName} is not supported for browser play`
                }
                data-testid="play-in-browser-btn"
              >
                <Play className="h-5 w-5" />
                Play in Browser
              </Button>
            )}
            {/* Desktop: show all buttons inline */}
            <div className="hidden lg:contents">
              {isAdmin && (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={onScrape}
                  loading={isScraping}
                >
                  <RefreshCw className="h-5 w-5" />
                  Scrape Metadata
                </Button>
              )}
              <Button
                variant={isFavorite ? "danger" : "secondary"}
                size="sm"
                onClick={onToggleFavorite}
              >
                <Heart
                  className={cn("h-5 w-5", isFavorite && "fill-current")}
                />
                {isFavorite ? "Unfavorite" : "Favorite"}
              </Button>
              <Button
                variant={isInPlayLater ? "primary" : "secondary"}
                size="sm"
                onClick={onTogglePlayLater}
                disabled={isPlayLaterPending}
              >
                <Clock
                  className={cn("h-5 w-5", isInPlayLater && "fill-current")}
                />
                {isInPlayLater ? "In Queue" : "Play Later"}
              </Button>
              {extraButtons}
            </div>
            {/* Mobile/tablet: overflow menu */}
            <div className="lg:hidden">
              <OverflowMenu
                isAdmin={isAdmin}
                isFavorite={isFavorite}
                isInPlayLater={isInPlayLater}
                isPlayLaterPending={isPlayLaterPending}
                isScraping={isScraping}
                extraMenuButtons={extraMenuButtons}
                onScrape={onScrape}
                onToggleFavorite={onToggleFavorite}
                onTogglePlayLater={onTogglePlayLater}
              />
            </div>
          </div>
        </div>

        {/* Metadata grid */}
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
          {game.developer && (
            <MetaItem
              icon={Building2}
              label="Developer"
              value={game.developer}
            />
          )}
          {game.publisher && (
            <MetaItem
              icon={Building2}
              label="Publisher"
              value={game.publisher}
            />
          )}
          {game.releaseDate && (
            <MetaItem
              icon={Calendar}
              label="Released"
              value={game.releaseDate}
            />
          )}
          {game.genre && (
            <MetaItem icon={Star} label="Genre" value={game.genre} />
          )}
          {game.players != null && (
            <MetaItem icon={Users} label="Players" value={`${game.players}`} />
          )}
          <MetaItem
            icon={HardDrive}
            label="Size"
            value={formatFileSize(game.fileSize)}
          />
          {game.discCount > 1 && (
            <MetaItem
              icon={Disc}
              label="Discs"
              value={`${game.discCount}`}
            />
          )}
          {game.rating !== undefined && game.rating > 0 && (
            <MetaItem icon={Star} label="Rating" value={`${game.rating}/10`} />
          )}
          <MetaItem
            icon={Clock}
            label="Play Time"
            value={
              game.totalPlayTime > 0
                ? formatPlayTime(game.totalPlayTime)
                : "Not played yet"
            }
          />
          {game.lastPlayedAt && (
            <MetaItem
              icon={Calendar}
              label="Last Played"
              value={formatRelativeTime(game.lastPlayedAt)}
            />
          )}
        </div>

        {/* Description */}
        {game.description && (
          <p className="text-sm text-surface-300 leading-relaxed">
            {game.description}
          </p>
        )}
      </div>
    </div>
  );
}
