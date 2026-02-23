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
  FolderPlus,
} from "lucide-react";
import { Button, Badge, SplitButton, ActionsMenu } from "@/components/ui";
import { VerificationBadge } from "./verification-badge";
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
  onPlay: () => void;
  onPlayFresh?: () => void;
  onScrape: () => void;
  onToggleFavorite: () => void;
  onTogglePlayLater: () => void;
  onAddToCollection?: () => void;
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
  onPlay,
  onPlayFresh,
  onScrape,
  onToggleFavorite,
  onTogglePlayLater,
  onAddToCollection,
}: GameHeroProps) {
  const consoleName = game.consoleName ?? "";

  const actionsMenuItems = [
    ...(isAdmin
      ? [
          {
            label: "Scrape Metadata",
            icon: <RefreshCw className="h-4 w-4" />,
            onClick: onScrape,
            loading: isScraping,
          },
        ]
      : []),
    {
      label: isFavorite ? "Unfavorite" : "Favorite",
      icon: (
        <Heart
          className={cn(
            "h-4 w-4",
            isFavorite && "fill-current text-danger-500",
          )}
        />
      ),
      onClick: onToggleFavorite,
    },
    {
      label: isInPlayLater ? "In Queue" : "Play Later",
      icon: (
        <Clock
          className={cn(
            "h-4 w-4",
            isInPlayLater && "fill-current text-brand-500",
          )}
        />
      ),
      onClick: onTogglePlayLater,
      disabled: isPlayLaterPending,
    },
    ...(onAddToCollection
      ? [
          {
            label: "Add to Collection",
            icon: <FolderPlus className="h-4 w-4" />,
            onClick: onAddToCollection,
          },
        ]
      : []),
  ];

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
              <VerificationBadge game={game} isAdmin={isAdmin} />
              {game.averageRating > 0 && (
                <span className="flex items-center gap-1 text-sm text-surface-400">
                  <Star className="h-4 w-4 text-amber-400 fill-amber-400" />
                  {game.averageRating.toFixed(1)}
                  <span className="text-surface-500">({game.ratingCount})</span>
                </span>
              )}
            </div>
            {game.rating != null && game.rating > 0 && (
              <IgdbRatingStars rating={game.rating} />
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {hasSaves ? (
              <SplitButton
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
                menuItems={[
                  ...(onPlayFresh
                    ? [
                        {
                          label: "New Game",
                          icon: <Play className="h-4 w-4" />,
                          onClick: onPlayFresh,
                        },
                      ]
                    : []),
                ]}
              >
                <Play className="h-5 w-5" />
                Resume
              </SplitButton>
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
            <ActionsMenu items={actionsMenuItems} />
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

function IgdbRatingStars({ rating }: { rating: number }) {
  const normalized = rating / 10;
  const fullStars = Math.floor(normalized / 2);
  const halfStar = normalized / 2 - fullStars >= 0.5;
  const emptyStars = 5 - fullStars - (halfStar ? 1 : 0);

  return (
    <div className="flex items-center gap-1.5 mt-1">
      <div className="flex items-center">
        {Array.from({ length: fullStars }, (_, i) => (
          <Star
            key={`full-${i}`}
            className="h-4 w-4 text-amber-400 fill-amber-400"
          />
        ))}
        {halfStar && (
          <div className="relative h-4 w-4">
            <Star className="absolute h-4 w-4 text-amber-400" />
            <div className="absolute inset-0 overflow-hidden w-[50%]">
              <Star className="h-4 w-4 text-amber-400 fill-amber-400" />
            </div>
          </div>
        )}
        {Array.from({ length: emptyStars }, (_, i) => (
          <Star key={`empty-${i}`} className="h-4 w-4 text-surface-600" />
        ))}
      </div>
      <span className="text-sm font-medium text-surface-300">
        {normalized.toFixed(1)}
        <span className="text-surface-500">/10</span>
      </span>
    </div>
  );
}
