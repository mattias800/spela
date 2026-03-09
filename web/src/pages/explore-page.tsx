import { Library } from "lucide-react";
import { Link } from "react-router-dom";
import { EmptyState, Button } from "@/components/ui";
import { HeroCarousel } from "@/features/explore/components/hero-carousel";
import { GameShelf } from "@/features/explore/components/game-shelf";
import { useExploreFeatured, useExploreRows } from "@/hooks/use-explore";
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

      {/* Shelf rows */}
      {isRowsLoading ? (
        <>
          <GameShelf
            title="Top Rated"
            games={undefined}
            isLoading={true}
          />
          <GameShelf
            title="Recently Added"
            games={undefined}
            isLoading={true}
          />
        </>
      ) : (
        rows.map((row) => (
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
