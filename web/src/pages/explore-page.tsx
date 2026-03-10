import { useState } from "react";
import { Library } from "lucide-react";
import { Link } from "react-router-dom";
import { EmptyState, Button } from "@/components/ui";
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

export function ExplorePage() {
  const { isAdmin } = useAuth();
  const [bestOfYear, setBestOfYear] = useState(DEFAULT_YEAR);
  const {
    data: featuredGames,
    isLoading: isFeaturedLoading,
  } = useExploreFeatured();
  const {
    data: rowsData,
    isLoading: isRowsLoading,
  } = useExploreRows();
  const {
    data: themes,
    isLoading: isThemesLoading,
  } = useThemes();
  const {
    data: keywords,
    isLoading: isKeywordsLoading,
  } = useKeywords(30);
  const {
    data: featuredSeries,
    isLoading: isSeriesLoading,
  } = useFeaturedSeries();
  const {
    data: moods,
    isLoading: isMoodsLoading,
  } = useMoods();
  const {
    data: forYouData,
    isLoading: isForYouLoading,
  } = useForYou();
  const {
    data: playersLikeYouData,
    isLoading: isPlayersLoading,
  } = usePlayersLikeYou();
  const {
    data: spotlightData,
    isLoading: isSpotlightLoading,
  } = useDeveloperSpotlight();
  const {
    data: consoleHighlightsData,
    isLoading: isConsoleHighlightsLoading,
  } = useConsoleHighlights();
  const {
    data: artworkData,
    isLoading: isArtworkLoading,
  } = useArtworkGallery(1);
  const {
    data: trendingData,
    isLoading: isTrendingLoading,
  } = useTrending();
  const {
    data: communityTopData,
    isLoading: isCommunityTopLoading,
  } = useCommunityTop();
  const {
    data: cultClassicsData,
    isLoading: isCultClassicsLoading,
  } = useCultClassics();
  const {
    data: recentlyReviewedData,
    isLoading: isRecentlyReviewedLoading,
  } = useRecentlyReviewed();
  const {
    data: activeNowData,
    isLoading: isActiveNowLoading,
  } = useActiveNow();
  const {
    data: onThisDayData,
    isLoading: isOnThisDayLoading,
  } = useOnThisDay();
  const {
    data: bestOfYearData,
    isLoading: isBestOfYearLoading,
  } = useBestOfYear(bestOfYear);
  const {
    data: anniversariesData,
    isLoading: isAnniversariesLoading,
  } = useYourAnniversaries();
  const {
    data: decadeData,
    isLoading: isDecadeLoading,
  } = useDecade("90s");
  const {
    data: easyToCompleteData,
    isLoading: isEasyToCompleteLoading,
  } = useEasyToComplete();
  const {
    data: hardestGamesData,
    isLoading: isHardestGamesLoading,
  } = useHardestGames();
  const {
    data: almostDoneData,
    isLoading: isAlmostDoneLoading,
  } = useAlmostDone();
  const {
    data: freshChallengesData,
    isLoading: isFreshChallengesLoading,
  } = useFreshChallenges();
  const {
    data: activeChallengesData,
    isLoading: isActiveChallengesLoading,
  } = useActiveChallenges();
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
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold text-surface-100">Explore</h1>
          <p className="mt-1 text-surface-400">
            Discover games in your library.
          </p>
        </div>
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
      </div>
    );
  }

  // Split rows: show first row, then themes/keywords, then remaining rows
  const firstRow = rows[0];
  const remainingRows = rows.slice(1);

  return (
    <div className="space-y-10" data-testid="explore-page">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Explore</h1>
        <p className="mt-1 text-surface-400">
          Discover games in your library.
        </p>
      </div>

      {/* Hero Carousel */}
      <HeroCarousel games={featuredGames} isLoading={isFeaturedLoading} />

      {/* Console Quick-Jump */}
      <ConsoleQuickJump
        consoles={consoleHighlightsData?.consoles}
        isLoading={isConsoleHighlightsLoading}
      />

      {/* Mood Picker */}
      <MoodPicker moods={moods} isLoading={isMoodsLoading} />

      {/* For You — personalized recommendations */}
      <ForYouSection
        rows={forYouData?.rows}
        isLoading={isForYouLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      {/* Players Like You */}
      <PlayersLikeYouShelf
        games={playersLikeYouData?.games}
        isLoading={isPlayersLoading}
        similarUsersCount={playersLikeYouData?.similarUsersCount ?? 0}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      {/* Trending */}
      <TrendingShelf
        games={trendingData?.games}
        isLoading={isTrendingLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      {/* Community Favorites */}
      <CommunityTopShelf
        games={communityTopData?.games}
        isLoading={isCommunityTopLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      {/* Cult Classics */}
      <CultClassicsShelf
        games={cultClassicsData?.games}
        isLoading={isCultClassicsLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      {/* Active Right Now */}
      <ActiveNowShelf
        games={activeNowData?.games}
        isLoading={isActiveNowLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      {/* Recently Reviewed */}
      <RecentlyReviewedShelf
        reviews={recentlyReviewedData?.reviews}
        isLoading={isRecentlyReviewedLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      {/* Temporal Discovery */}
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

      {/* Achievement & Challenge Discovery */}
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

      {/* Theme Grid */}
      <ThemeGrid themes={themes} isLoading={isThemesLoading} />

      {/* Keyword Chips */}
      <KeywordChips keywords={keywords} isLoading={isKeywordsLoading} />

      {/* Series shelf */}
      <SeriesShelf series={featuredSeries} isLoading={isSeriesLoading} />

      {/* Developer Spotlight */}
      <DeveloperSpotlight
        spotlight={spotlightData}
        isLoading={isSpotlightLoading}
        onToggleFavorite={handleToggleFavorite}
        onTogglePlayLater={handleTogglePlayLater}
      />

      {/* Artwork Showcase */}
      <ArtworkShowcase
        artworks={artworkData?.artworks}
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

    </div>
  );
}
