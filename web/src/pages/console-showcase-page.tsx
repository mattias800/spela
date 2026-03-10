import { useParams, useNavigate, Link } from "react-router-dom";
import { Star } from "lucide-react";
import { BackButton, Skeleton, GameCardSkeleton } from "@/components/ui";
import { GameShelf } from "@/features/explore/components/game-shelf";
import { useConsoleShowcase } from "@/hooks/use-explore";
import { useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import type { GenreCount, DeveloperSummary } from "@/types/api";

function ShowcasePageSkeleton() {
  return (
    <div className="space-y-10" data-testid="console-showcase-skeleton">
      {/* Hero skeleton */}
      <div className="rounded-2xl overflow-hidden">
        <Skeleton className="w-full h-56" />
      </div>
      {/* Essentials shelf skeleton */}
      <div className="space-y-4">
        <Skeleton className="w-48 h-7" />
        <div className="flex gap-5 overflow-hidden">
          {Array.from({ length: 6 }, (_, i) => (
            <div key={i} className="w-40 sm:w-44 lg:w-48 flex-shrink-0">
              <GameCardSkeleton />
            </div>
          ))}
        </div>
      </div>
      {/* Hidden gems shelf skeleton */}
      <div className="space-y-4">
        <Skeleton className="w-40 h-7" />
        <div className="flex gap-5 overflow-hidden">
          {Array.from({ length: 6 }, (_, i) => (
            <div key={i} className="w-40 sm:w-44 lg:w-48 flex-shrink-0">
              <GameCardSkeleton />
            </div>
          ))}
        </div>
      </div>
      {/* Genre breakdown skeleton */}
      <div className="space-y-4">
        <Skeleton className="w-48 h-7" />
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {Array.from({ length: 8 }, (_, i) => (
            <Skeleton key={i} className="h-24 rounded-xl" />
          ))}
        </div>
      </div>
      {/* Developers skeleton */}
      <div className="space-y-4">
        <Skeleton className="w-64 h-7" />
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
      </div>
    </div>
  );
}

export function ConsoleShowcasePage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: showcase, isLoading } = useConsoleShowcase(id ?? "");
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  if (isLoading) {
    return (
      <div
        className="max-w-6xl space-y-6"
        data-testid="console-showcase-page"
      >
        <BackButton onClick={() => navigate(-1)}>Back to Explore</BackButton>
        <ShowcasePageSkeleton />
      </div>
    );
  }

  if (!showcase) {
    return (
      <div
        className="max-w-6xl space-y-6"
        data-testid="console-showcase-page"
      >
        <BackButton onClick={() => navigate(-1)}>Back to Explore</BackButton>
        <p className="text-surface-400 text-center py-20">
          Console not found
        </p>
      </div>
    );
  }

  const { console: con } = showcase;
  const colorTheme = con.colorTheme || "#6366f1";

  return (
    <div className="max-w-6xl space-y-10" data-testid="console-showcase-page">
      <BackButton onClick={() => navigate(-1)}>Back to Explore</BackButton>

      {/* Hero Section */}
      <div
        className="relative rounded-2xl overflow-hidden border border-white/[0.06]"
        data-testid="console-showcase-hero"
      >
        {/* Color gradient background */}
        <div
          className="absolute inset-0"
          style={{
            background: `linear-gradient(135deg, ${colorTheme}40, ${colorTheme}10, transparent)`,
          }}
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/30 to-transparent" />

        <div className="relative p-8 sm:p-10 flex items-center gap-6">
          {/* Console logo */}
          {con.logoUrl && (
            <img
              src={con.logoUrl}
              alt={`${con.name} logo`}
              className="h-16 sm:h-20 object-contain drop-shadow-lg"
              data-testid="console-logo"
            />
          )}
          <div>
            <h1
              className="text-3xl sm:text-4xl font-bold text-white"
              data-testid="console-name"
            >
              {con.name}
            </h1>
            <p
              className="mt-2 text-sm text-white/70"
              data-testid="console-stats"
            >
              {con.gameCount} {con.gameCount === 1 ? "game" : "games"} in
              library
            </p>
          </div>
        </div>
      </div>

      {/* Essentials Shelf */}
      {showcase.essentials.length > 0 && (
        <GameShelf
          title="Essentials"
          games={showcase.essentials}
          isLoading={false}
          onToggleFavorite={handleToggleFavorite}
          onTogglePlayLater={handleTogglePlayLater}
        />
      )}

      {/* Hidden Gems Shelf */}
      {showcase.hiddenGems.length > 0 && (
        <GameShelf
          title="Hidden Gems"
          games={showcase.hiddenGems}
          isLoading={false}
          onToggleFavorite={handleToggleFavorite}
          onTogglePlayLater={handleTogglePlayLater}
        />
      )}

      {/* Genre Breakdown */}
      {showcase.genreBreakdown.length > 0 && (
        <section data-testid="genre-breakdown" aria-labelledby="genre-breakdown-heading">
          <h2 id="genre-breakdown-heading" className="text-xl font-bold text-surface-100 mb-5">
            Genre Breakdown
          </h2>
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
                  {genre.gameCount}{" "}
                  {genre.gameCount === 1 ? "game" : "games"}
                </p>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Studios That Defined This Console */}
      {showcase.topDevelopers.length > 0 && (
        <section data-testid="top-developers" aria-labelledby="top-developers-heading">
          <h2 id="top-developers-heading" className="text-xl font-bold text-surface-100 mb-5">
            Studios That Defined This Console
          </h2>
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
                  {dev.gameCount}{" "}
                  {dev.gameCount === 1 ? "game" : "games"}
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
      )}

      {/* Recently Played */}
      {showcase.recentlyPlayed.length > 0 && (
        <div data-testid="recently-played-section">
          <GameShelf
            title="Recently Played"
            games={showcase.recentlyPlayed}
            isLoading={false}
            onToggleFavorite={handleToggleFavorite}
            onTogglePlayLater={handleTogglePlayLater}
          />
        </div>
      )}
    </div>
  );
}
