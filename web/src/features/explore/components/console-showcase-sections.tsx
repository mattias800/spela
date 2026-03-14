import { Link } from "react-router-dom";
import {
  Star,
  Trophy,
  Gem,
  LayoutGrid,
  Building2,
  Sparkles,
  Play,
} from "lucide-react";
import { GameShelf } from "@/features/explore/components/game-shelf";
import { SectionHeader } from "@/components/section-header";
import { useConsoleShowcase } from "@/hooks/use-explore";
import { useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import type { GenreCount, DeveloperSummary } from "@/types/api";

interface ConsoleShowcaseSectionProps {
  consoleId: string;
}

export function ConsoleRecentlyPlayed({
  consoleId,
}: ConsoleShowcaseSectionProps) {
  const { data: showcase } = useConsoleShowcase(consoleId);
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  if (!showcase || showcase.recentlyPlayed.length === 0) return null;

  return (
    <div data-testid="recently-played-section">
      <GameShelf
        title="Continue Playing"
        icon={Play}
        games={showcase.recentlyPlayed}
        isLoading={false}
        hideConsoleName
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />
    </div>
  );
}

export function ConsoleEssentials({ consoleId }: ConsoleShowcaseSectionProps) {
  const { data: showcase } = useConsoleShowcase(consoleId);
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  if (!showcase || showcase.essentials.length === 0) return null;

  return (
    <GameShelf
      title="Essentials"
      icon={Trophy}
      games={showcase.essentials}
      isLoading={false}
      hideConsoleName
      onToggleFavorite={handleToggleFavorite}
      onTogglePlayLater={handleTogglePlayLater}
    />
  );
}

export function ConsoleHiddenGems({ consoleId }: ConsoleShowcaseSectionProps) {
  const { data: showcase } = useConsoleShowcase(consoleId);
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  if (!showcase || showcase.hiddenGems.length === 0) return null;

  return (
    <GameShelf
      title="Hidden Gems"
      icon={Gem}
      games={showcase.hiddenGems}
      isLoading={false}
      hideConsoleName
      onToggleFavorite={handleToggleFavorite}
      onTogglePlayLater={handleTogglePlayLater}
    />
  );
}

export function ConsoleGenreBreakdown({
  consoleId,
}: ConsoleShowcaseSectionProps) {
  const { data: showcase } = useConsoleShowcase(consoleId);

  if (!showcase || showcase.genreBreakdown.length === 0) return null;

  const colorTheme = showcase.console.colorTheme || "#6366f1";

  return (
    <section
      data-testid="genre-breakdown"
      aria-labelledby="genre-breakdown-heading"
    >
      <SectionHeader title="Genre Breakdown" icon={LayoutGrid} id="genre-breakdown-heading" />
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        {showcase.genreBreakdown.map((genre: GenreCount) => (
          <div
            key={genre.name}
            className="rounded-xl border border-white/[0.06] p-4"
            style={{
              background: `linear-gradient(135deg, ${colorTheme}15, transparent)`,
            }}
            data-testid={`genre-card-${genre.name}`}
          >
            <p className="text-sm font-semibold text-surface-100">
              {genre.name}
            </p>
            <p className="text-xs text-surface-400 mt-1">
              {genre.gameCount} {genre.gameCount === 1 ? "game" : "games"}
            </p>
          </div>
        ))}
      </div>
    </section>
  );
}

export function ConsoleTopDevelopers({
  consoleId,
}: ConsoleShowcaseSectionProps) {
  const { data: showcase } = useConsoleShowcase(consoleId);

  if (!showcase || showcase.topDevelopers.length === 0) return null;

  const colorTheme = showcase.console.colorTheme || "#6366f1";

  return (
    <section
      data-testid="top-developers"
      aria-labelledby="top-developers-heading"
    >
      <SectionHeader title="Studios That Defined This Console" icon={Building2} id="top-developers-heading" />
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
        {showcase.topDevelopers.map((dev: DeveloperSummary) => (
          <Link
            key={dev.name}
            to={`/explore/developers/${encodeURIComponent(dev.name)}`}
            className="group/dev rounded-xl border border-white/[0.06] p-4 transition-colors hover:border-white/[0.12]"
            style={{
              background: `linear-gradient(135deg, ${colorTheme}10, transparent)`,
            }}
            data-testid={`developer-card-${dev.name}`}
          >
            <p className="text-sm font-semibold text-surface-100 group-hover/dev:text-brand-400 transition-colors">
              {dev.name}
            </p>
            <p className="text-xs text-surface-400 mt-1">
              {dev.gameCount} {dev.gameCount === 1 ? "game" : "games"}
            </p>
            {dev.avgRating > 0 && (
              <span className="inline-flex items-center gap-1 text-xs text-surface-400 mt-1">
                <Star className="h-3 w-3 text-amber-400 fill-amber-400" />
                {dev.avgRating.toFixed(1)}
              </span>
            )}
          </Link>
        ))}
      </div>
    </section>
  );
}

export function ConsoleRecentlyAdded({
  consoleId,
}: ConsoleShowcaseSectionProps) {
  const { data: showcase } = useConsoleShowcase(consoleId);
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  if (!showcase || showcase.recentlyAdded.length === 0) return null;

  return (
    <GameShelf
      title="Recently Added"
      icon={Sparkles}
      games={showcase.recentlyAdded}
      isLoading={false}
      hideConsoleName
      onToggleFavorite={handleToggleFavorite}
      onTogglePlayLater={handleTogglePlayLater}
    />
  );
}
