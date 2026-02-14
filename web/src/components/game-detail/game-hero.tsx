import { type ReactNode } from "react";
import {
  Heart,
  Calendar,
  Users,
  Building2,
  Star,
  HardDrive,
  RefreshCw,
  Play,
  Trophy,
  Clock,
} from "lucide-react";
import { Button, Badge } from "@/components/ui";
import { MetaItem } from "@/components/meta-item";
import { formatFileSize, formatPlayTime, formatRelativeTime } from "@/lib/format";
import { cn } from "@/lib/cn";
import type { Game } from "@/types/api";

interface GameHeroProps {
  game: Game;
  canPlayInBrowser: boolean;
  isAdmin: boolean;
  isFavorite: boolean;
  isInPlayLater: boolean;
  isScraping: boolean;
  hasAchievements?: boolean;
  extraButtons?: ReactNode;
  onPlay: () => void;
  onScrape: () => void;
  onToggleFavorite: () => void;
  onTogglePlayLater: () => void;
}

export function GameHero({
  game,
  canPlayInBrowser,
  isAdmin,
  isFavorite,
  isInPlayLater,
  isScraping,
  hasAchievements,
  extraButtons,
  onPlay,
  onScrape,
  onToggleFavorite,
  onTogglePlayLater,
}: GameHeroProps) {
  const consoleName = game.consoleName ?? "";

  return (
    <div className="flex gap-8">
      {/* Cover art */}
      <div className="w-64 flex-shrink-0">
        <div className="aspect-[3/4] rounded-2xl overflow-hidden bg-surface-900 border border-surface-800 shadow-2xl">
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
      <div className="flex-1 space-y-5 pt-2">
        <div>
          <div className="flex items-start justify-between gap-4">
            <h1 className="text-3xl font-bold text-surface-100 flex items-center gap-2">
              {game.title}
              {hasAchievements && (
                <Trophy
                  className="h-6 w-6 text-amber-400"
                  data-testid="achievements-trophy"
                />
              )}
            </h1>
            <div className="flex items-center gap-2">
              <Button
                variant="primary"
                size="sm"
                onClick={onPlay}
                disabled={!canPlayInBrowser}
                title={canPlayInBrowser ? "Play in Browser" : `${game.consoleName} is not supported for browser play`}
                data-testid="play-in-browser-btn"
              >
                <Play className="h-4 w-4" />
                Play in Browser
              </Button>
              {isAdmin && (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={onScrape}
                  loading={isScraping}
                >
                  <RefreshCw className="h-4 w-4" />
                  Scrape Metadata
                </Button>
              )}
              <Button
                variant={isFavorite ? "danger" : "secondary"}
                size="sm"
                onClick={onToggleFavorite}
              >
                <Heart
                  className={cn(
                    "h-4 w-4",
                    isFavorite && "fill-current",
                  )}
                />
                {isFavorite ? "Unfavorite" : "Favorite"}
              </Button>
              <Button
                variant={isInPlayLater ? "primary" : "secondary"}
                size="sm"
                onClick={onTogglePlayLater}
              >
                <Clock
                  className={cn(
                    "h-4 w-4",
                    isInPlayLater && "fill-current",
                  )}
                />
                {isInPlayLater ? "In Queue" : "Play Later"}
              </Button>
              {extraButtons}
            </div>
          </div>
          <div className="flex items-center gap-3 mt-2">
            {consoleName && (
              <Badge variant="brand">{consoleName}</Badge>
            )}
            {game.averageRating > 0 && (
              <span className="flex items-center gap-1 text-sm text-surface-400">
                <Star className="h-4 w-4 text-amber-400 fill-amber-400" />
                {game.averageRating.toFixed(1)}
                <span className="text-surface-500">
                  ({game.ratingCount})
                </span>
              </span>
            )}
          </div>
        </div>

        {/* Metadata grid */}
        <div className="grid grid-cols-2 gap-3">
          {game.developer && (
            <MetaItem icon={Building2} label="Developer" value={game.developer} />
          )}
          {game.publisher && (
            <MetaItem icon={Building2} label="Publisher" value={game.publisher} />
          )}
          {game.releaseDate && (
            <MetaItem icon={Calendar} label="Released" value={game.releaseDate} />
          )}
          {game.genre && (
            <MetaItem icon={Star} label="Genre" value={game.genre} />
          )}
          {game.players && (
            <MetaItem icon={Users} label="Players" value={`${game.players}`} />
          )}
          <MetaItem icon={HardDrive} label="Size" value={formatFileSize(game.fileSize)} />
          {game.rating !== undefined && game.rating > 0 && (
            <MetaItem icon={Star} label="Rating" value={`${game.rating}/10`} />
          )}
          <MetaItem
            icon={Clock}
            label="Play Time"
            value={game.totalPlayTime > 0 ? formatPlayTime(game.totalPlayTime) : "Not played yet"}
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
