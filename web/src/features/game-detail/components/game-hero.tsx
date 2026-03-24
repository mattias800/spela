import { useState } from "react";
import { Link } from "react-router-dom";
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
  ImageIcon,
  Search,
  Download,
  Monitor,
  Replace,
  Award,
} from "lucide-react";
import { Button, Badge, ActionsMenu } from "@/components/ui";
import { VerificationBadge } from "./verification-badge";
import { CoverArtSelector } from "./cover-art-selector";
import { MetaItem } from "@/components/meta-item";
import {
  formatFileSize,
  formatPlayTime,
  formatRelativeTime,
} from "@/lib/format";
import { cn } from "@/lib/cn";
import { UserRating } from "./user-rating";
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
  achievementCount?: number;
  achievementUnlocked?: number;
  biosMissing?: boolean;
  isDemo?: boolean;
  onPlay: () => void;
  onScrape: () => void;
  onToggleFavorite: () => void;
  onTogglePlayLater: () => void;
  onAddToCollection?: () => void;
  onFixMatch?: () => void;
  onDownloadRom?: () => void;
  onReplaceRom?: () => void;
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
  achievementCount,
  achievementUnlocked,
  biosMissing,
  isDemo,
  onPlay,
  onScrape,
  onToggleFavorite,
  onTogglePlayLater,
  onAddToCollection,
  onFixMatch,
  onDownloadRom,
  onReplaceRom,
}: GameHeroProps) {
  const [showCoverModal, setShowCoverModal] = useState(false);
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
    ...(onFixMatch
      ? [
          {
            label: "Fix Match",
            icon: <Search className="h-4 w-4" />,
            onClick: onFixMatch,
          },
        ]
      : []),
    ...(onReplaceRom
      ? [
          {
            label: "Replace ROM",
            icon: <Replace className="h-4 w-4" />,
            onClick: onReplaceRom,
          },
        ]
      : []),
  ];

  return (
    <div className="flex flex-col items-center gap-6 md:flex-row md:items-start md:gap-8">
      {/* Cover art */}
      <div className="w-48 flex-shrink-0 md:w-64 flex flex-col items-center">
        <div
          className="w-full rounded-2xl overflow-hidden bg-surface-900 border border-surface-800 shadow-2xl"
        >
          {game.coverUrl ? (
            <img
              src={game.coverUrl}
              alt={game.title}
              className="w-full"
            />
          ) : (
            <div className="flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900" style={{ aspectRatio: aspectRatio ?? 3 / 4 }}>
              <span className="text-5xl font-bold text-surface-700">
                {game.title.charAt(0)}
              </span>
            </div>
          )}
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setShowCoverModal(true)}
          className="mt-3"
          data-testid="change-cover-btn"
        >
          <ImageIcon className="h-4 w-4" />
          Change cover
        </Button>
        <CoverArtSelector
          open={showCoverModal}
          onClose={() => setShowCoverModal(false)}
          gameId={game.id}
          aspectRatio={aspectRatio}
        />
        <div className="mt-4">
          <UserRating gameId={game.id} />
        </div>
      </div>

      {/* Info */}
      <div className="w-full min-w-0 flex-1 space-y-5 pt-2">
        <div className="space-y-4">
          <div>
            <h1 className="text-2xl font-bold text-surface-100 md:text-3xl">
              {game.title}
            </h1>
            <div className="flex flex-wrap items-center gap-3 mt-2">
              {consoleName && <Badge variant="brand">{consoleName}</Badge>}
              {!game.playable && (
                <Badge variant="warning" data-testid="external-emulator-badge">
                  <Monitor className="h-3 w-3 mr-1" />
                  External Emulator
                </Badge>
              )}
              <VerificationBadge game={game} isAdmin={isAdmin} />
              {game.region && <RegionBadge region={game.region} />}
              {hasAchievements && achievementCount != null && achievementCount > 0 && (
                <Link
                  to={`/games/${game.id}/achievements`}
                  data-testid="achievements-badge"
                >
                  <Badge variant="warning">
                    <Trophy className="h-3 w-3 mr-1" />
                    {achievementUnlocked != null
                      ? `${achievementUnlocked} / ${achievementCount}`
                      : `${achievementCount}`}
                  </Badge>
                </Link>
              )}
              {(game.rating ?? 0) > 0 ? (
                <IgdbRatingStars rating={game.rating!} />
              ) : (game.igdbUserRating ?? 0) > 0 ? (
                <IgdbRatingStars rating={game.igdbUserRating!} />
              ) : null}
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
            {game.playable ? (
              <Button
                variant="primary"
                size="sm"
                onClick={onPlay}
                disabled={!canPlayInBrowser || biosMissing}
                title={
                  biosMissing
                    ? "Missing required BIOS files"
                    : canPlayInBrowser
                      ? "Play in Browser"
                      : `${game.consoleName} is not supported for browser play`
                }
                data-testid="play-in-browser-btn"
              >
                <Play className="h-5 w-5" />
                Play in Browser
              </Button>
            ) : (
              <Button
                variant="primary"
                size="sm"
                onClick={onDownloadRom}
                title="Download ROM for use with an external emulator"
                data-testid="download-rom-btn"
              >
                <Download className="h-5 w-5" />
                Download ROM
              </Button>
            )}
            <ActionsMenu items={actionsMenuItems} />
            {isScraping && (
              <span className="flex items-center gap-1.5 text-sm text-brand-400 ml-1">
                <RefreshCw className="h-4 w-4 animate-spin" />
                Scraping metadata…
              </span>
            )}
            <span className="flex items-center gap-1 text-sm text-surface-400 ml-2">
              <Clock className="h-4 w-4 text-surface-500" />
              {game.totalPlayTime > 0
                ? formatPlayTime(game.totalPlayTime)
                : "Not played yet"}
            </span>
            {game.lastPlayedAt && (
              <span className="flex items-center gap-1 text-sm text-surface-400">
                <Calendar className="h-4 w-4 text-surface-500" />
                {formatRelativeTime(game.lastPlayedAt)}
              </span>
            )}
          </div>
        </div>

        {/* Description */}
        {game.description && (
          <p className="text-sm text-surface-300 leading-relaxed">
            {game.description}
          </p>
        )}

        {/* Metadata grid */}
        <div className="grid grid-cols-2 gap-3">
          {game.developer && (
            <MetaItem
              icon={Building2}
              label={isDemo ? "Group" : "Developer"}
              value={game.developer}
              href={isDemo ? undefined : `/explore/developers/${encodeURIComponent(game.developer)}`}
            />
          )}
          {!isDemo && game.publisher && (
            <MetaItem
              icon={Building2}
              label="Publisher"
              value={game.publisher}
              href={`/explore/publishers/${encodeURIComponent(game.publisher)}`}
            />
          )}
          {game.releaseDate && (
            <MetaItem
              icon={Calendar}
              label={isDemo ? "Year" : "Released"}
              value={game.releaseDate}
            />
          )}
          {game.genre && (
            <MetaItem icon={Star} label={isDemo ? "Type" : "Genre"} value={game.genre} />
          )}
          {isDemo && game.partyInfo && (
            <MetaItem icon={Award} label="Party" value={game.partyInfo} />
          )}
          {!isDemo && game.players != null && (
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
          {achievementCount != null && achievementCount > 0 && (
            <MetaItem
              icon={Trophy}
              label="Achievements"
              value={
                achievementUnlocked != null
                  ? `${achievementUnlocked} / ${achievementCount}`
                  : `${achievementCount}`
              }
              href={`/games/${game.id}/achievements`}
            />
          )}
        </div>
      </div>
    </div>
  );
}

const regionFlags: Record<string, string> = {
  USA: "\u{1F1FA}\u{1F1F8}",
  Japan: "\u{1F1EF}\u{1F1F5}",
  Europe: "\u{1F1EA}\u{1F1FA}",
  World: "\u{1F30D}",
  Korea: "\u{1F1F0}\u{1F1F7}",
  Brazil: "\u{1F1E7}\u{1F1F7}",
  France: "\u{1F1EB}\u{1F1F7}",
  Germany: "\u{1F1E9}\u{1F1EA}",
  Spain: "\u{1F1EA}\u{1F1F8}",
  Italy: "\u{1F1EE}\u{1F1F9}",
  Australia: "\u{1F1E6}\u{1F1FA}",
  China: "\u{1F1E8}\u{1F1F3}",
  Canada: "\u{1F1E8}\u{1F1E6}",
  UK: "\u{1F1EC}\u{1F1E7}",
  Sweden: "\u{1F1F8}\u{1F1EA}",
  Netherlands: "\u{1F1F3}\u{1F1F1}",
  Russia: "\u{1F1F7}\u{1F1FA}",
  Taiwan: "\u{1F1F9}\u{1F1FC}",
  Asia: "\u{1F30F}",
};

function getRegionFlag(region: string): string | null {
  for (const [key, flag] of Object.entries(regionFlags)) {
    if (region.includes(key)) return flag;
  }
  return null;
}

function RegionBadge({ region }: { region: string }) {
  const flag = getRegionFlag(region);
  return (
    <span className="inline-flex items-center gap-1 rounded-md bg-surface-800 px-2 py-0.5 text-xs font-medium text-surface-300">
      {flag && <span>{flag}</span>}
      {region}
    </span>
  );
}

function IgdbRatingStars({ rating }: { rating: number }) {
  const normalized = rating / 10;
  const fullStars = Math.floor(normalized / 2);
  const halfStar = normalized / 2 - fullStars >= 0.5;
  const emptyStars = 5 - fullStars - (halfStar ? 1 : 0);

  return (
    <div className="flex items-center gap-1.5">
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
