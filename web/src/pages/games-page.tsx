import { useState } from "react";
import { Library, Grid3X3, List, ArrowUpDown } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameGrid } from "@/components/game-grid";
import { GameCardSkeleton, SearchInput, Select, EmptyState, Badge } from "@/components/ui";
import { useGames, useToggleFavorite } from "@/hooks/use-games";
import { useConsoles } from "@/hooks/use-consoles";
import { cn } from "@/lib/cn";
import { formatFileSize } from "@/lib/format";
import type { GameFilters } from "@/types/api";
import { Link } from "react-router-dom";

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

  const games = data?.data ?? [];

  const consoleOptions = [
    { value: "", label: "All Consoles" },
    ...(consoles?.map((c) => ({ value: String(c.id), label: c.name })) ?? []),
  ];

  const sortOptions = [
    { value: "title", label: "Title" },
    { value: "created_at", label: "Recently Added" },
    { value: "file_size", label: "File Size" },
    { value: "rating", label: "Rating" },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Games</h1>
        <p className="mt-1 text-surface-400">
          {data ? `${data.total} games in your library` : "Browse your game library"}
        </p>
      </div>

      {/* Filters bar */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex-1 min-w-[240px] max-w-md">
          <SearchInput
            placeholder="Search games..."
            value={filters.search ?? ""}
            onChange={(e) =>
              setFilters((f) => ({ ...f, search: e.target.value, page: 1 }))
            }
          />
        </div>

        <Select
          options={consoleOptions}
          value={filters.consoleId ?? ""}
          onChange={(e) =>
            setFilters((f) => ({
              ...f,
              consoleId: e.target.value || undefined,
              page: 1,
            }))
          }
          className="w-44"
        />

        <Select
          options={sortOptions}
          value={filters.sortBy ?? "title"}
          onChange={(e) =>
            setFilters((f) => ({
              ...f,
              sortBy: e.target.value as GameFilters["sortBy"],
            }))
          }
          className="w-40"
        />

        <button
          onClick={() =>
            setFilters((f) => ({
              ...f,
              sortOrder: f.sortOrder === "asc" ? "desc" : "asc",
            }))
          }
          className="p-2.5 rounded-lg bg-surface-900 border border-surface-700 text-surface-300 hover:text-surface-100 hover:border-surface-600 transition-all"
          title={`Sort ${filters.sortOrder === "asc" ? "descending" : "ascending"}`}
        >
          <ArrowUpDown className="h-4 w-4" />
        </button>

        {/* View toggle */}
        <div className="flex rounded-lg overflow-hidden border border-surface-700">
          <button
            onClick={() => setViewMode("grid")}
            className={cn(
              "p-2.5 transition-colors",
              viewMode === "grid"
                ? "bg-brand-600 text-white"
                : "bg-surface-900 text-surface-400 hover:text-surface-100",
            )}
          >
            <Grid3X3 className="h-4 w-4" />
          </button>
          <button
            onClick={() => setViewMode("list")}
            className={cn(
              "p-2.5 transition-colors",
              viewMode === "list"
                ? "bg-brand-600 text-white"
                : "bg-surface-900 text-surface-400 hover:text-surface-100",
            )}
          >
            <List className="h-4 w-4" />
          </button>
        </div>
      </div>

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
              <div key={i} className="h-16 rounded-xl bg-surface-900/50 animate-pulse" />
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
            />
          ))}
        </GameGrid>
      ) : (
        <div className="space-y-1">
          {games.map((game) => {
            const consoleName = game.consoleName ?? "";
            return (
              <Link
                key={game.id}
                to={`/games/${game.id}`}
                className="flex items-center gap-4 px-4 py-3 rounded-xl hover:bg-surface-900/50 transition-colors group"
              >
                <div className="h-12 w-9 rounded-lg overflow-hidden bg-surface-800 flex-shrink-0">
                  {game.coverUrl ? (
                    <img
                      src={game.coverUrl}
                      alt={game.title}
                      className="h-full w-full object-cover"
                    />
                  ) : (
                    <div className="h-full w-full flex items-center justify-center text-xs font-bold text-surface-600">
                      {game.title.charAt(0)}
                    </div>
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-surface-100 truncate group-hover:text-brand-400 transition-colors">
                    {game.title}
                  </p>
                  {consoleName && (
                    <p className="text-xs text-surface-500">{consoleName}</p>
                  )}
                </div>
                {consoleName && <Badge>{consoleName}</Badge>}
                <span className="text-xs text-surface-500 w-16 text-right">
                  {formatFileSize(game.fileSize)}
                </span>
              </Link>
            );
          })}
        </div>
      )}

      {/* Pagination */}
      {data && data.total > (filters.pageSize ?? 48) && (
        <div className="flex justify-center gap-2 pt-4">
          {Array.from(
            { length: Math.ceil(data.total / (filters.pageSize ?? 48)) },
            (_, i) => (
              <button
                key={i}
                onClick={() => setFilters((f) => ({ ...f, page: i + 1 }))}
                className={cn(
                  "h-9 w-9 rounded-lg text-sm font-medium transition-colors",
                  (filters.page ?? 1) === i + 1
                    ? "bg-brand-600 text-white"
                    : "bg-surface-900 text-surface-400 hover:text-surface-100 hover:bg-surface-800",
                )}
              >
                {i + 1}
              </button>
            ),
          )}
        </div>
      )}
    </div>
  );
}
