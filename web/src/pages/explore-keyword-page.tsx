import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Library } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameGrid } from "@/components/game-grid";
import {
  BackButton,
  GameCardSkeleton,
  EmptyState,
  Select,
} from "@/components/ui";
import { Pagination } from "@/components/pagination";
import { useKeywords, useKeywordGames } from "@/hooks/use-explore";
import { useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";

const PAGE_SIZE = 48;

const SORT_OPTIONS = [
  { value: "rating-desc", label: "Highest Rated" },
  { value: "rating-asc", label: "Lowest Rated" },
  { value: "title-asc", label: "Title A-Z" },
  { value: "title-desc", label: "Title Z-A" },
  { value: "release-desc", label: "Newest First" },
  { value: "release-asc", label: "Oldest First" },
];

export function ExploreKeywordPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const [sortBy, setSortBy] = useState("rating-desc");
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  const { data: keywords } = useKeywords(200);
  const { data, isLoading } = useKeywordGames(id, page, PAGE_SIZE);

  const keyword = keywords?.find((k) => k.id === id);
  const keywordName = keyword?.name ?? "Keyword";
  const games = data?.data ?? [];
  const totalGames = data?.total ?? keyword?.gameCount ?? 0;

  // Client-side sort (the API returns by rating DESC by default)
  const sortedGames = [...games].sort((a, b) => {
    switch (sortBy) {
      case "rating-desc":
        return (b.igdbCriticsRating ?? 0) - (a.igdbCriticsRating ?? 0);
      case "rating-asc":
        return (a.igdbCriticsRating ?? 0) - (b.igdbCriticsRating ?? 0);
      case "title-asc":
        return a.title.localeCompare(b.title);
      case "title-desc":
        return b.title.localeCompare(a.title);
      case "release-desc":
        return (b.releaseDate ?? "").localeCompare(a.releaseDate ?? "");
      case "release-asc":
        return (a.releaseDate ?? "").localeCompare(b.releaseDate ?? "");
      default:
        return 0;
    }
  });

  return (
    <div className="space-y-6" data-testid="keyword-detail-page">
      {/* Back button */}
      <BackButton onClick={() => navigate("/explore")}>
        Back to Explore
      </BackButton>

      {/* Page header */}
      <div>
        <h1 className="text-3xl font-bold text-surface-100">
          {keywordName}
        </h1>
        <p className="mt-1 text-surface-400">
          {totalGames} {totalGames === 1 ? "game" : "games"}
        </p>
      </div>

      {/* Sort controls */}
      <div className="flex items-center gap-4">
        <Select
          options={SORT_OPTIONS}
          value={sortBy}
          onChange={(e) => setSortBy(e.target.value)}
          className="w-48"
          aria-label="Sort games"
        />
      </div>

      {/* Games grid */}
      {isLoading ? (
        <GameGrid>
          {Array.from({ length: 12 }, (_, i) => (
            <GameCardSkeleton key={i} />
          ))}
        </GameGrid>
      ) : sortedGames.length === 0 ? (
        <EmptyState
          icon={Library}
          title="No games found"
          description={`No games found for the "${keywordName}" keyword.`}
        />
      ) : (
        <GameGrid>
          {sortedGames.map((game) => (
            <GameCard
              key={game.id}
              game={game}
              showConsoleBadge
              onToggleFavorite={handleToggleFavorite}
              onTogglePlayLater={handleTogglePlayLater}
            />
          ))}
        </GameGrid>
      )}

      {data && (
        <Pagination
          total={data.total}
          pageSize={PAGE_SIZE}
          currentPage={page}
          onPageChange={setPage}
        />
      )}
    </div>
  );
}
