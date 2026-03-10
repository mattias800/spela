import { Library } from "lucide-react";
import { Link } from "react-router-dom";
import { EmptyState, Button } from "@/components/ui";
import { HeroCarousel } from "@/features/explore/components/hero-carousel";
import { GameShelf } from "@/features/explore/components/game-shelf";
import { ThemeGrid } from "@/features/explore/components/theme-grid";
import { KeywordChips } from "@/features/explore/components/keyword-chips";
import { SeriesShelf } from "@/features/explore/components/series-shelf";
import { DeveloperSpotlight } from "@/features/explore/components/developer-spotlight";
import { MoodPicker } from "@/features/explore/components/mood-picker";
import { ForYouSection } from "@/features/explore/components/for-you-section";
import { PlayersLikeYouShelf } from "@/features/explore/components/players-like-you-shelf";
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
} from "@/hooks/use-explore";
import { useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import { useAuth } from "@/hooks/use-auth";

export function ExplorePage() {
  const { isAdmin } = useAuth();
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
