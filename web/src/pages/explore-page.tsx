import { useState } from "react";
import { Library } from "lucide-react";
import { Link } from "react-router-dom";
import { EmptyState, Button } from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import { HeroCarousel } from "@/features/explore/components/hero-carousel";
import { GameShelf } from "@/features/explore/components/game-shelf";
import { ThemeGrid } from "@/features/explore/components/theme-grid";
import { KeywordChips } from "@/features/explore/components/keyword-chips";
import { SeriesShelf } from "@/features/explore/components/series-shelf";
import { ArtworkShowcase } from "@/features/explore/components/artwork-showcase";
import { DeveloperSpotlight } from "@/features/explore/components/developer-spotlight";
import { MoodPicker } from "@/features/explore/components/mood-picker";
import { ForYouSection } from "@/features/explore/components/for-you-section";
import { PlayersLikeYouShelf } from "@/features/explore/components/players-like-you-shelf";
import { ConsoleQuickJump } from "@/features/explore/components/console-quick-jump";
import {
  TrendingShelf,
  CommunityTopShelf,
  CultClassicsShelf,
  RecentlyReviewedShelf,
  ActiveNowShelf,
} from "@/features/explore/components/social-shelves";
import {
  OnThisDayShelf,
  BestOfYearSection,
  AnniversariesShelf,
  DecadeSpotlight,
  DEFAULT_YEAR,
} from "@/features/explore/components/temporal-shelves";
import {
  EasyToCompleteShelf,
  HardestGamesShelf,
  AlmostDoneShelf,
  FreshChallengesShelf,
  ActiveChallengesShelf,
} from "@/features/explore/components/achievement-shelves";
import { WildFeaturesSection } from "@/features/explore/components/wild-features";
import {
  useExploreFeatured,
  useExploreRows,
  useThemes,
  useKeywords,
  useFeaturedSeries,
  useMoods,
  useForYou,
  usePlayersLikeYou,
  useDeveloperSpotlight,
  useConsoleHighlights,
  useArtworkGallery,
  useTrending,
  useCommunityTop,
  useCultClassics,
  useRecentlyReviewed,
  useActiveNow,
  useOnThisDay,
  useBestOfYear,
  useYourAnniversaries,
  useDecade,
  useEasyToComplete,
  useHardestGames,
  useAlmostDone,
  useFreshChallenges,
  useActiveChallenges,
} from "@/hooks/use-explore";
import { useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import { useAuth } from "@/hooks/use-auth";
import { useInView } from "@/hooks/use-in-view";

export function ExplorePage() {
  const { isAdmin } = useAuth();
  const [bestOfYear, setBestOfYear] = useState(DEFAULT_YEAR);

  // Lazy-loading sentinels: sections only fetch when scrolled into view.
  // rootMargin 400px = start loading before the section is visible.
  const social = useInView({ rootMargin: "400px" });
  const temporal = useInView({ rootMargin: "400px" });
  const achievements = useInView({ rootMargin: "400px" });
  const catalogBottom = useInView({ rootMargin: "400px" });

  // --- Tier 1: Always loaded (above-the-fold) ---
  const {
    data: featuredGames,
    isLoading: isFeaturedLoading,
  } = useExploreFeatured();
  const {
    data: rowsData,
    isLoading: isRowsLoading,
  } = useExploreRows();
  const {
    data: consoleHighlightsData,
    isLoading: isConsoleHighlightsLoading,
  } = useConsoleHighlights();
  const {
    data: moods,
    isLoading: isMoodsLoading,
  } = useMoods();

  // --- Tier 2: Lazy-loaded when scrolled near ---
  const {
    data: forYouData,
    isLoading: isForYouLoading,
  } = useForYou(social.isInView);
  const {
    data: playersLikeYouData,
    isLoading: isPlayersLoading,
  } = usePlayersLikeYou(social.isInView);

  // Social section
  const {
    data: trendingData,
    isLoading: isTrendingLoading,
  } = useTrending(social.isInView);
  const {
    data: communityTopData,
    isLoading: isCommunityTopLoading,
  } = useCommunityTop(social.isInView);
  const {
    data: cultClassicsData,
    isLoading: isCultClassicsLoading,
  } = useCultClassics(social.isInView);
  const {
    data: activeNowData,
    isLoading: isActiveNowLoading,
  } = useActiveNow(social.isInView);
  const {
    data: recentlyReviewedData,
    isLoading: isRecentlyReviewedLoading,
  } = useRecentlyReviewed(social.isInView);

  // Temporal section
  const {
    data: onThisDayData,
    isLoading: isOnThisDayLoading,
  } = useOnThisDay(temporal.isInView);
  const {
    data: anniversariesData,
    isLoading: isAnniversariesLoading,
  } = useYourAnniversaries(temporal.isInView);
  const {
    data: bestOfYearData,
    isLoading: isBestOfYearLoading,
  } = useBestOfYear(bestOfYear, temporal.isInView);
  const {
    data: decadeData,
    isLoading: isDecadeLoading,
  } = useDecade("90s", temporal.isInView);

  // Achievement section
  const {
    data: easyToCompleteData,
    isLoading: isEasyToCompleteLoading,
  } = useEasyToComplete(achievements.isInView);
  const {
    data: hardestGamesData,
    isLoading: isHardestGamesLoading,
  } = useHardestGames(achievements.isInView);
  const {
    data: almostDoneData,
    isLoading: isAlmostDoneLoading,
  } = useAlmostDone(achievements.isInView);
  const {
    data: freshChallengesData,
    isLoading: isFreshChallengesLoading,
  } = useFreshChallenges(achievements.isInView);
  const {
    data: activeChallengesData,
    isLoading: isActiveChallengesLoading,
  } = useActiveChallenges(achievements.isInView);

  // Catalog bottom section (themes, keywords, series, spotlight, artwork)
  const {
    data: themes,
    isLoading: isThemesLoading,
  } = useThemes(catalogBottom.isInView);
  const {
    data: keywords,
    isLoading: isKeywordsLoading,
  } = useKeywords(30, catalogBottom.isInView);
  const {
    data: featuredSeries,
    isLoading: isSeriesLoading,
  } = useFeaturedSeries(catalogBottom.isInView);
  const {
    data: spotlightData,
    isLoading: isSpotlightLoading,
  } = useDeveloperSpotlight(catalogBottom.isInView);
  const {
    data: artworkData,
    isLoading: isArtworkLoading,
  } = useArtworkGallery(1, catalogBottom.isInView);

  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  const rows = rowsData?.rows ?? [];
  const isInitialLoading = isFeaturedLoading && isRowsLoading;
  const hasNoData =
    !isInitialLoading &&
    (!featuredGames || featuredGames.length === 0) &&
    rows.length === 0;

  // Empty library state
  if (hasNoData && !isFeaturedLoading && !isRowsLoading) {
    return (
      <PageLayout title="Explore" subtitle="Discover games in your library.">
        <EmptyState
          icon={Library}
          title="Nothing to explore yet"
          description={
            isAdmin
              ? "Your library is empty. Scan for games to start building your collection."
              : "No games have been added to the library yet. Contact an admin to get started."
          }
          action={
            isAdmin ? (
              <Link to="/admin/scan">
                <Button variant="primary">Scan Library</Button>
              </Link>
            ) : undefined
          }
        />
      </PageLayout>
    );
  }

  // Split rows: show first row, then themes/keywords, then remaining rows
  const firstRow = rows[0];
  const remainingRows = rows.slice(1);

  return (
    <PageLayout
      data-testid="explore-page"
      title="Explore"
      subtitle="Discover games in your library."
    >
      <SectionList>
      {/* Hero Carousel */}
      <HeroCarousel games={featuredGames} isLoading={isFeaturedLoading} />

      {/* Console Quick-Jump */}
      <ConsoleQuickJump
        consoles={consoleHighlightsData?.consoles ?? undefined}
        isLoading={isConsoleHighlightsLoading}
      />

      {/* Mood Picker */}
      <MoodPicker moods={moods} isLoading={isMoodsLoading} />

      {/* Wild Features — Lucky & Wizard */}
      <WildFeaturesSection />

      {/* Personalized & Social Discovery — lazy-loaded */}
      <div ref={social.ref}  />
      <ForYouSection
        rows={forYouData?.rows ?? undefined}
        isLoading={isForYouLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      {/* Players Like You */}
      <PlayersLikeYouShelf
        games={playersLikeYouData?.games ?? undefined}
        isLoading={isPlayersLoading}
        similarUsersCount={playersLikeYouData?.similarUsersCount ?? 0}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      <TrendingShelf
        games={trendingData?.games}
        isLoading={isTrendingLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      <CommunityTopShelf
        games={communityTopData?.games}
        isLoading={isCommunityTopLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      <CultClassicsShelf
        games={cultClassicsData?.games}
        isLoading={isCultClassicsLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      <ActiveNowShelf
        games={activeNowData?.games}
        isLoading={isActiveNowLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      <RecentlyReviewedShelf
        reviews={recentlyReviewedData?.reviews}
        isLoading={isRecentlyReviewedLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      {/* Temporal Discovery — lazy-loaded */}
      <div ref={temporal.ref}  />
      <OnThisDayShelf
        data={onThisDayData}
        isLoading={isOnThisDayLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      <AnniversariesShelf
        anniversaries={anniversariesData?.anniversaries}
        isLoading={isAnniversariesLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      <BestOfYearSection
        year={bestOfYear}
        onYearChange={setBestOfYear}
        data={bestOfYearData}
        isLoading={isBestOfYearLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      <DecadeSpotlight
        data={decadeData}
        isLoading={isDecadeLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      {/* Achievement & Challenge Discovery — lazy-loaded */}
      <div ref={achievements.ref}  />
      <EasyToCompleteShelf
        data={easyToCompleteData}
        isLoading={isEasyToCompleteLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      <HardestGamesShelf
        data={hardestGamesData}
        isLoading={isHardestGamesLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      <AlmostDoneShelf
        data={almostDoneData}
        isLoading={isAlmostDoneLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      <FreshChallengesShelf
        data={freshChallengesData}
        isLoading={isFreshChallengesLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      <ActiveChallengesShelf
        data={activeChallengesData}
        isLoading={isActiveChallengesLoading}
      />

      {/* First shelf row */}
      {isRowsLoading ? (
        <GameShelf
          title="Top Rated"
          games={undefined}
          isLoading={true}
        />
      ) : firstRow ? (
        <GameShelf
          key={firstRow.id}
          title={firstRow.title}
          games={firstRow.games}
          isLoading={false}
          onToggleFavorite={handleToggleFavorite}
          onTogglePlayLater={handleTogglePlayLater}
        />
      ) : null}

      {/* Catalog bottom — lazy-loaded */}
      <div ref={catalogBottom.ref}  />
      <ThemeGrid themes={themes} isLoading={isThemesLoading} />

      <KeywordChips keywords={keywords} isLoading={isKeywordsLoading} />

      {/* Series shelf */}
      <SeriesShelf series={featuredSeries} isLoading={isSeriesLoading} />

      <DeveloperSpotlight
        spotlight={spotlightData}
        isLoading={isSpotlightLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      <ArtworkShowcase
        artworks={artworkData?.artworks ?? undefined}
        isLoading={isArtworkLoading}
      />

      {/* Remaining shelf rows */}
      {isRowsLoading ? (
        <GameShelf
          title="Recently Added"
          games={undefined}
          isLoading={true}
        />
      ) : (
        remainingRows.map((row) => (
          <GameShelf
            key={row.id}
            title={row.title}
            games={row.games}
            isLoading={false}
            onToggleFavorite={handleToggleFavorite}
            onTogglePlayLater={handleTogglePlayLater}
          />
        ))
      )}

    </SectionList>
    </PageLayout>
  );
}

export default ExplorePage;
