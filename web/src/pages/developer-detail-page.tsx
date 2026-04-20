import { useState } from "react";
import { useParams, Link } from "react-router-dom";
import { Skeleton, Badge, FilterChip } from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton } from "@/components/ui";
import { GameShelf } from "@/features/explore/components/game-shelf";
import { DeveloperHeroBanner } from "@/features/explore/components/developer-hero-banner";
import { DeveloperStatsCard } from "@/features/explore/components/developer-stats-card";
import { CompanyInfoSection } from "@/features/explore/components/company-info-section";
import { AtAGlanceRow } from "@/features/explore/components/at-a-glance-row";
import { ReleaseTimeline } from "@/features/explore/components/release-timeline";
import { RatingDistribution } from "@/features/explore/components/rating-distribution";
import { useDeveloperDetail } from "@/hooks/use-explore";
import { useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import type { Game } from "@/types/api";

function DeveloperPageSkeleton() {
  return (
    <div className="space-y-8" data-testid="developer-detail-skeleton">
      {/* Hero banner skeleton */}
      <Skeleton className="w-full h-48 rounded-2xl" />

      {/* At a Glance skeleton */}
      <div className="flex flex-wrap gap-3" data-testid="skeleton-at-a-glance">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} className="w-36 h-14 rounded-xl" />
        ))}
      </div>

      {/* Top rated skeleton */}
      <div>
        <Skeleton className="w-32 h-7 mb-4" />
        <div className="flex gap-5 overflow-hidden">
          {Array.from({ length: 6 }, (_, i) => (
            <div key={i} className="w-40 sm:w-44 lg:w-48 flex-shrink-0">
              <GameCardSkeleton />
            </div>
          ))}
        </div>
      </div>

      {/* Timeline skeleton */}
      <div>
        <Skeleton className="w-40 h-7 mb-4" />
        <div className="flex gap-4 overflow-hidden">
          {Array.from({ length: 5 }, (_, i) => (
            <div key={i} className="flex-shrink-0 space-y-2">
              <Skeleton className="w-12 h-5 rounded" />
              <Skeleton className="w-28 h-36 rounded-lg" />
            </div>
          ))}
        </div>
      </div>

      {/* Genre chips skeleton */}
      <div className="flex gap-2">
        {Array.from({ length: 5 }, (_, i) => (
          <Skeleton key={i} className="w-24 h-8 rounded-full" />
        ))}
      </div>

      {/* Game grid skeleton */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
        {Array.from({ length: 12 }, (_, i) => (
          <GameCardSkeleton key={i} />
        ))}
      </div>
    </div>
  );
}

export function DeveloperDetailPage() {
  const { name: rawName } = useParams<{ name: string }>();
  const name = rawName ? decodeURIComponent(rawName) : "";
  const { data: developer, isLoading } = useDeveloperDetail(name);
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();
  const [genreFilter, setGenreFilter] = useState<string | null>(null);
  const [consoleFilter, setConsoleFilter] = useState<string | null>(null);

  if (isLoading) {
    return (
      <PageLayout backButtonVariant="standard" data-testid="developer-detail-page">
        <SectionList className="max-w-6xl">
          <DeveloperPageSkeleton />
        </SectionList>
      </PageLayout>
    );
  }

  if (!developer) {
    return (
      <PageLayout backButtonVariant="standard" data-testid="developer-detail-page">
        <SectionList className="max-w-6xl">
          <p className="text-surface-400 text-center py-20">
            No games found for this developer
          </p>
        </SectionList>
      </PageLayout>
    );
  }

  // Show top rated row when >4 games total and topGames has entries
  const showTopRated =
    developer.gameCount > 4 &&
    developer.topGames &&
    developer.topGames.length > 0;

  // Show genre breakdown when 2+ genres exist
  const showGenreBreakdown =
    developer.genreBreakdown && developer.genreBreakdown.length >= 2;

  // Build platform-grouped games
  const platformBreakdown = developer.platformBreakdown ?? [];
  const sortedPlatforms = [...platformBreakdown].sort(
    (a, b) => b.count - a.count,
  );

  // Apply genre + console filter to the all-games list
  let filteredGames = developer.games ?? [];
  if (genreFilter) {
    filteredGames = filteredGames.filter((g: Game) =>
      g.genre
        ?.split(",")
        .map((s) => s.trim())
        .includes(genreFilter),
    );
  }
  if (consoleFilter) {
    filteredGames = filteredGames.filter(
      (g: Game) => g.consoleName === consoleFilter,
    );
  }

  // Group filtered games by platform for display
  const gamesByPlatform: Record<string, Game[]> = {};
  for (const game of filteredGames) {
    const key = game.consoleName;
    if (!gamesByPlatform[key]) {
      gamesByPlatform[key] = [];
    }
    gamesByPlatform[key].push(game);
  }

  // Order platform sections by total game count (desc)
  const platformOrder = sortedPlatforms
    .map((p) => p.consoleName)
    .filter((name) => gamesByPlatform[name]?.length);

  // Include any platforms that appear in filtered games but not in platformBreakdown
  for (const key of Object.keys(gamesByPlatform)) {
    if (!platformOrder.includes(key)) {
      platformOrder.push(key);
    }
  }

  const showGroupedByPlatform =
    !consoleFilter && platformOrder.length > 1;

  return (
    <PageLayout backButtonVariant="standard" data-testid="developer-detail-page">
      <SectionList className="max-w-6xl">

      {/* Hero Banner */}
      <DeveloperHeroBanner
        name={developer.name}
        gameCount={developer.gameCount}
        avgRating={developer.avgRating}
        consoleCount={(developer.consoles ?? []).length}
        heroUrl={developer.heroUrl}
        logoUrl={developer.companyInfo?.logoUrl}
      />

      {/* Company Info */}
      {developer.companyInfo && (
        <CompanyInfoSection companyInfo={developer.companyInfo} />
      )}

      {/* At a Glance Stats Row */}
      <AtAGlanceRow
        gameCount={developer.gameCount}
        activeYears={developer.activeYears}
        primaryGenre={developer.primaryGenre}
        platformCount={(developer.consoles ?? []).length}
        avgRating={developer.avgRating}
      />

      {/* Top Rated Row */}
      {showTopRated && (
        <GameShelf
          title="Top Rated"
          games={developer.topGames!}
          isLoading={false}
          onToggleFavorite={handleToggleFavorite}
          onTogglePlayLater={handleTogglePlayLater}
        />
      )}

      {/* Release Timeline */}
      {developer.timeline && developer.timeline.length > 0 && (
        <ReleaseTimeline timeline={developer.timeline} />
      )}

      {/* Rating Distribution */}
      {developer.ratingDistribution && (
        <RatingDistribution distribution={developer.ratingDistribution} />
      )}

      {/* Genre Breakdown (filter chips) */}
      {showGenreBreakdown && (
        <section data-testid="developer-genre-breakdown">
          <h2 className="text-lg font-bold text-surface-100 mb-3">Genres</h2>
          <div className="flex flex-wrap gap-2">
            <FilterChip
              label="All"
              isSelected={genreFilter === null}
              onClick={() => setGenreFilter(null)}
            />
            {developer.genreBreakdown!.map((genre) => (
              <FilterChip
                key={genre.name}
                label={genre.name}
                isSelected={genreFilter === genre.name}
                onClick={() =>
                  setGenreFilter(
                    genreFilter === genre.name ? null : genre.name,
                  )
                }
                count={genre.gameCount}
              />
            ))}
          </div>
        </section>
      )}

      {/* User Stats Card */}
      {developer.userStats && (
        <DeveloperStatsCard
          userStats={developer.userStats}
          totalGames={developer.gameCount}
        />
      )}

      {/* Console filter chips */}
      {(developer.consoles ?? []).length > 1 && (
        <div
          className="flex flex-wrap gap-2"
          data-testid="developer-console-filters"
        >
          <FilterChip
            label="All"
            isSelected={consoleFilter === null}
            onClick={() => setConsoleFilter(null)}
            count={(developer.games ?? []).length}
          />
          {(developer.consoles ?? []).map((consoleName: string) => {
            const count = (developer.games ?? []).filter(
              (g: Game) => g.consoleName === consoleName,
            ).length;
            return (
              <FilterChip
                key={consoleName}
                label={consoleName}
                isSelected={consoleFilter === consoleName}
                onClick={() =>
                  setConsoleFilter(
                    consoleFilter === consoleName ? null : consoleName,
                  )
                }
                count={count}
              />
            );
          })}
        </div>
      )}

      {/* Games grouped by platform (or flat list if only one platform / console filter active) */}
      {filteredGames.length > 0 ? (
        showGroupedByPlatform ? (
          <div className="space-y-8" data-testid="developer-platform-sections">
            {platformOrder.map((platformName) => {
              const platformGames = gamesByPlatform[platformName];
              if (!platformGames || platformGames.length === 0) return null;
              return (
                <section key={platformName} data-testid={`platform-section-${platformName}`}>
                  <div className="flex items-center gap-2 mb-4">
                    <h2 className="text-lg font-bold text-surface-100">
                      {platformName}
                    </h2>
                    <Badge variant="default">{platformGames.length}</Badge>
                  </div>
                  <div
                    className="flex gap-5 overflow-x-auto scrollbar-hide pb-2"
                    style={{
                      scrollbarWidth: "none",
                      msOverflowStyle: "none",
                    }}
                  >
                    {platformGames.map((game: Game) => (
                      <div
                        key={game.id}
                        className="w-40 sm:w-44 lg:w-48 flex-shrink-0"
                      >
                        <GameCard
                          game={game}
                          showConsoleBadge
                          onToggleFavorite={handleToggleFavorite}
                          onTogglePlayLater={handleTogglePlayLater}
                        />
                      </div>
                    ))}
                  </div>
                </section>
              );
            })}
          </div>
        ) : (
          <div
            className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5"
            data-testid="developer-game-grid"
          >
            {filteredGames.map((game: Game) => (
              <GameCard
                key={game.id}
                game={game}
                showConsoleBadge
                onToggleFavorite={handleToggleFavorite}
                onTogglePlayLater={handleTogglePlayLater}
              />
            ))}
          </div>
        )
      ) : (
        <p className="text-surface-400 text-center py-12">
          No games match the selected filter.
        </p>
      )}

      {/* Related Developers */}
      {developer.relatedDevelopers && developer.relatedDevelopers.length > 0 && (
        <section data-testid="related-developers">
          <h2 className="text-lg font-bold text-surface-100 mb-3">
            Related Developers
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
            {developer.relatedDevelopers.map((rel) => (
              <Link
                key={rel.name}
                to={`/explore/developers/${encodeURIComponent(rel.name)}`}
                className="flex flex-col gap-1 rounded-xl border border-surface-700 bg-surface-800/60 px-4 py-3 hover:bg-surface-700/80 hover:border-surface-600 transition-colors"
                data-testid={`related-developer-${rel.name}`}
              >
                <span className="text-sm font-semibold text-surface-100">
                  {rel.name}
                </span>
                <span className="text-xs text-surface-400">
                  {rel.gameCount} {rel.gameCount === 1 ? "game" : "games"}
                </span>
                {rel.sharedPublishers && rel.sharedPublishers.length > 0 && (
                  <span className="text-xs text-surface-500">
                    via {rel.sharedPublishers.join(", ")}
                  </span>
                )}
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* Publishers Section */}
      {developer.publishers && developer.publishers.length > 0 && (
        <section data-testid="developer-publishers">
          <h2 className="text-lg font-bold text-surface-100 mb-3">
            Publishers
          </h2>
          <div className="flex flex-wrap gap-2">
            {developer.publishers.map((pub) => (
              <Link
                key={pub.name}
                to={`/explore/publishers/${encodeURIComponent(pub.name)}`}
                className="inline-flex items-center gap-1.5 rounded-full border border-surface-700 bg-surface-800 px-3 py-1.5 text-xs font-medium text-surface-300 hover:bg-surface-700 hover:text-surface-100 transition-colors"
              >
                {pub.name}
                <span className="text-surface-500">({pub.count})</span>
              </Link>
            ))}
          </div>
        </section>
      )}
    </SectionList>
    </PageLayout>
  );
}

export default DeveloperDetailPage;
