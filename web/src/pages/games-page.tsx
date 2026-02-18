import { useState } from "react";
import { Library } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameGrid } from "@/components/game-grid";
import { GameCardSkeleton, EmptyState } from "@/components/ui";
import { useGames, useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import { useConsoles } from "@/hooks/use-consoles";
import { GamesFilterBar } from "@/features/games/components/games-filter-bar";
import { GameListRow } from "@/features/games/components/game-list-row";
import { Pagination } from "@/components/pagination";
import type { GameFilters } from "@/types/api";

type ViewMode = "grid" | "list";

export function GamesPage() {
  const [viewMode, setViewMode] = useState<ViewMode>("grid");
  const [filters, setFilters] = useState<GameFilters>({
    sortBy: "title",
    sortOrder: "asc",
    pageSize: 48,
  });

  const { data, isLoading } = useGames(filters);
  const { data: consoles } = useConsoles();
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  const games = data?.data ?? [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Games</h1>
        <p className="mt-1 text-surface-400">
          {data
            ? `${data.total} games in your library`
            : "Browse your game library"}
        </p>
      </div>

      <GamesFilterBar
        filters={filters}
        onFiltersChange={setFilters}
        viewMode={viewMode}
        onViewModeChange={setViewMode}
        consoles={consoles}
      />

      {/* Content */}
      {isLoading ? (
        viewMode === "grid" ? (
          <GameGrid>
            {Array.from({ length: 18 }, (_, i) => (
              <GameCardSkeleton key={i} />
            ))}
          </GameGrid>
        ) : (
          <div className="space-y-2">
            {Array.from({ length: 10 }, (_, i) => (
              <div
                key={i}
                className="h-16 rounded-xl bg-surface-900/50 animate-pulse"
              />
            ))}
          </div>
        )
      ) : games.length === 0 ? (
        <EmptyState
          icon={Library}
          title="No games found"
          description={
            filters.search
              ? "No games match your search. Try different keywords."
              : "Your library is empty. Scan for games to get started."
          }
        />
      ) : viewMode === "grid" ? (
        <GameGrid>
          {games.map((game) => (
            <GameCard
              key={game.id}
              game={game}
              onToggleFavorite={handleToggleFavorite}
              onTogglePlayLater={handleTogglePlayLater}
            />
          ))}
        </GameGrid>
      ) : (
        <div className="space-y-1">
          {games.map((game) => (
            <GameListRow key={game.id} game={game} />
          ))}
        </div>
      )}

      {data && (
        <Pagination
          total={data.total}
          pageSize={filters.pageSize ?? 48}
          currentPage={filters.page ?? 1}
          onPageChange={(page) => setFilters((f) => ({ ...f, page }))}
        />
      )}
    </div>
  );
}
