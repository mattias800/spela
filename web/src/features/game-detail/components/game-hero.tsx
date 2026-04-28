import { useState } from "react";
import { Link } from "react-router-dom";
import {
  Heart,
  Calendar,
  Star,
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
} from "lucide-react";
import { AreaSizedImage } from "@/components/area-sized-image";
import { Button, Badge, ActionsMenu } from "@/components/ui";
import { ConsoleBadge } from "@/components/console-badge";
import { VerificationBadge } from "./verification-badge";
import { CoverArtSelector } from "./cover-art-selector";
import { HeroArtSelector } from "./hero-art-selector";
import {
  formatPlayTime,
  formatRelativeTime,
} from "@/lib/format";
import { cn } from "@/lib/cn";
import type { Game } from "@/types/api";

interface GameHeroProps {
  game: Game;
  canPlayInBrowser: boolean;
  isAdmin: boolean;
  isFavorite: boolean;
  isInPlayLater: boolean;
  isPlayLaterPending?: boolean;
  isScraping: boolean;
  isRefreshingAchievements?: boolean;
  hasAchievements?: boolean;
  achievementCount?: number;
  achievementUnlocked?: number;
  biosMissing?: boolean;
  onPlay: () => void;
  onScrape: () => void;
  onRefreshAchievements?: () => void;
  onToggleFavorite: () => void;
  onTogglePlayLater: () => void;
  onAddToCollection?: () => void;
  onFixMatch?: () => void;
  onDownloadRom?: () => void;
  onReplaceRom?: () => void;
}

export function GameHero({
  game,
  canPlayInBrowser,
  isAdmin,
  isFavorite,
  isInPlayLater,
  isPlayLaterPending,
  isScraping,
  isRefreshingAchievements,
  hasAchievements,
  achievementCount,
  achievementUnlocked,
  biosMissing,
  onPlay,
  onScrape,
  onRefreshAchievements,
  onToggleFavorite,
  onTogglePlayLater,
  onAddToCollection,
  onFixMatch,
  onDownloadRom,
  onReplaceRom,
}: GameHeroProps) {
  const [showCoverModal, setShowCoverModal] = useState(false);
  const [showHeroModal, setShowHeroModal] = useState(false);
  const consoleName = game.consoleName;

  // Prefer hero art (from SteamGridDB), fall back to first screenshot
  const heroImage = game.heroUrl || game.screenshotUrls?.[0] || null;

  const actionsMenuItems = [
    ...(isAdmin
      ? [
          {
            label: "Change Cover",
            icon: <ImageIcon className="h-4 w-4" />,
            onClick: () => setShowCoverModal(true),
          },
          {
            label: "Change Hero Art",
            icon: <ImageIcon className="h-4 w-4" />,
            onClick: () => setShowHeroModal(true),
          },
          {
            label: "Scrape Metadata",
            icon: <RefreshCw className="h-4 w-4" />,
            onClick: onScrape,
            loading: isScraping,
          },
          ...(onRefreshAchievements
            ? [
                {
                  label: "Update Achievements",
                  icon: <Trophy className="h-4 w-4" />,
                  onClick: onRefreshAchievements,
                  loading: isRefreshingAchievements,
                },
              ]
            : []),
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
    <div className="relative" data-comp={"GameHero"}>
      {/* Hero banner background — absolute so the wrapper height is driven
          by the content below. On narrow screens the cover + title box
          stack into a column that's taller than the 400px the row layout
          uses; the background needs to grow with them so the title box
          (which is positioned over the background) doesn't bleed onto
          the game description rendered after this component. */}
      {heroImage ? (
        <img
          src={heroImage}
          alt=""
          className="absolute inset-0 w-full h-full object-cover"
        />
      ) : (
        <div className="absolute inset-0 bg-gradient-to-br from-surface-800 via-surface-900 to-surface-950" />
      )}

      {/* Subtle bottom gradient — just enough for transition to page background */}
      <div className="absolute inset-0 bg-gradient-to-t from-surface-950 via-transparent to-transparent" />

      {/* Content — in-flow so it sizes the wrapper. Dropdowns aren't
          clipped because no ancestor has overflow-hidden. */}
      <div className="relative z-10 flex flex-col items-center md:flex-row md:items-end min-h-[320px] md:min-h-[400px] p-6 md:p-8 gap-6 md:gap-8">
        {/* Cover art */}
          <div className="flex-shrink-0 self-center md:self-end">
            <div className="rounded-xl overflow-hidden bg-surface-900/80 border border-white/10 shadow-2xl backdrop-blur-sm">
              {game.coverUrl ? (
                <AreaSizedImage
                  src={game.coverUrl}
                  alt={game.title}
                  targetArea={55000}
                  maxHeight={360}
                  maxWidth={320}
                  minHeight={160}
                  className="rounded-xl"
                />
              ) : (
                <div className="w-48 flex items-center justify-center bg-surface-800" style={{ aspectRatio: "3/4" }}>
                  <span className="text-3xl font-bold text-surface-600">{game.title.charAt(0).toUpperCase()}</span>
                </div>
              )}
            </div>
            <CoverArtSelector
              open={showCoverModal}
              onClose={() => setShowCoverModal(false)}
              gameId={game.id}
            />
            <HeroArtSelector
              open={showHeroModal}
              onClose={() => setShowHeroModal(false)}
              gameId={game.id}
            />
          </div>

          {/* Title, badges, buttons — semi-transparent container */}
          <div className="flex-1 min-w-0 rounded-2xl bg-black/40 backdrop-blur-sm p-5 space-y-3">
            <div>
              <h1 className="text-2xl font-bold text-white md:text-3xl">
                {game.title}
              </h1>
              <div className="flex flex-wrap items-center gap-2 mt-2">
                {consoleName && <ConsoleBadge code={game.consoleId} label={consoleName} />}
                {game.isPreRelease && (
                  <Badge variant="warning" data-testid="pre-release-badge">
                    Pre-release
                  </Badge>
                )}
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
                {(game.igdbCriticsRating ?? 0) > 0 ? (
                  <IgdbRatingStars rating={game.igdbCriticsRating!} />
                ) : (game.igdbUserRating ?? 0) > 0 ? (
                  <IgdbRatingStars rating={game.igdbUserRating!} />
                ) : null}
                {game.averageRating > 0 && (
                  <span className="flex items-center gap-1 text-sm text-white/70">
                    <Star className="h-4 w-4 text-amber-400 fill-amber-400" />
                    {game.averageRating.toFixed(1)}
                    <span className="text-white/50">({game.ratingCount})</span>
                  </span>
                )}
              </div>
            </div>

            {/* Action buttons + play stats */}
            <div className="flex flex-wrap items-center gap-3">
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
                <span className="flex items-center gap-1.5 text-sm text-brand-400">
                  <RefreshCw className="h-4 w-4 animate-spin" />
                  Scraping…
                </span>
              )}
              <div className="flex items-center gap-3 text-sm text-white/60">
                <span className="flex items-center gap-1">
                  <Clock className="h-4 w-4" />
                  {game.totalPlayTime > 0
                    ? formatPlayTime(game.totalPlayTime)
                    : "Not played yet"}
                </span>
                {game.lastPlayedAt && (
                  <span className="flex items-center gap-1">
                    <Calendar className="h-4 w-4" />
                    {formatRelativeTime(game.lastPlayedAt)}
                  </span>
                )}
              </div>
            </div>
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
    <span data-comp="RegionBadge" className="inline-flex items-center gap-1 rounded-md bg-black/30 backdrop-blur-sm px-2 py-0.5 text-xs font-medium text-white/80">
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
    <div data-comp="IgdbRatingStars" className="flex items-center gap-1.5">
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
          <Star key={`empty-${i}`} className="h-4 w-4 text-white/30" />
        ))}
      </div>
      <span className="text-sm font-medium text-white/80">
        {normalized.toFixed(1)}
        <span className="text-white/50">/10</span>
      </span>
    </div>
  );
}
