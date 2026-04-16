import { useState, useEffect, useCallback } from "react";
import { useSearchParams } from "react-router-dom";
import { Library } from "lucide-react";
import { PageLayout, SectionList } from "@/components/layout";
import { GameCard } from "@/components/game-card";
import { GameGrid } from "@/components/game-grid";
import { GameCardSkeleton, GameListRowSkeleton, EmptyState } from "@/components/ui";
import { useGames, useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import { useConsoles } from "@/hooks/use-consoles";
import { GamesFilterBar } from "@/features/games/components/games-filter-bar";
import {
  AdvancedFilterPanel,
  savedSearchToFilters,
} from "@/features/games/components/advanced-filter-panel";
import { ActiveFilterPills } from "@/features/games/components/active-filter-pills";
import { BestVersionsButton } from "@/features/games/components/best-versions-button";
import { GameListRow } from "@/features/games/components/game-list-row";
import { AlphabetBar } from "@/components/alphabet-bar";
import { Pagination } from "@/components/pagination";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { useThemes, useKeywords } from "@/hooks/use-explore";
import {
  useSavedSearches,
  useCreateSavedSearch,
  useDeleteSavedSearch,
} from "@/hooks/use-saved-searches";
import { useDefaultRegionFilters } from "@/hooks/use-default-region-filters";
import type { GameFilters } from "@/types/api";

type ViewMode = "grid" | "list";

/** Parse URL search params into GameFilters */
function parseUrlFilters(params: URLSearchParams): Partial<GameFilters> {
  const f: Partial<GameFilters> = {};
  const consoles = params.get("consoles");
  if (consoles) f.consoles = consoles.split(",");
  const regions = params.get("regions");
  if (regions) f.regions = regions.split(",");
  const genres = params.get("genres");
  if (genres) f.genres = genres.split(",");
  const themes = params.get("themes");
  if (themes) f.themes = themes.split(",");
  const keywords = params.get("keywords");
  if (keywords) f.keywords = keywords.split(",");
  if (params.get("developer")) f.developer = params.get("developer")!;
  if (params.get("publisher")) f.publisher = params.get("publisher")!;
  if (params.get("yearMin")) f.yearMin = Number(params.get("yearMin"));
  if (params.get("yearMax")) f.yearMax = Number(params.get("yearMax"));
  if (params.get("ratingMin")) f.ratingMin = Number(params.get("ratingMin"));
  if (params.get("ratingMax")) f.ratingMax = Number(params.get("ratingMax"));
  const ps = params.get("playStatus");
  if (ps) f.playStatus = ps as GameFilters["playStatus"];
  // Only serialize hidePreRelease when explicitly false (default is true/hidden)
  if (params.get("hidePreRelease") === "false") f.hidePreRelease = false;
  if (params.get("grouped") === "false") f.grouped = false;
  if (params.get("letter")) f.letter = params.get("letter")!;
  const sortBy = params.get("sortBy");
  if (sortBy) f.sortBy = sortBy as GameFilters["sortBy"];
  const sortOrder = params.get("sortOrder");
  if (sortOrder) f.sortOrder = sortOrder as GameFilters["sortOrder"];
  if (params.get("search")) f.search = params.get("search")!;
  return f;
}

/** Serialize GameFilters to URL search params */
function filtersToUrl(filters: GameFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.consoles?.length) params.set("consoles", filters.consoles.join(","));
  if (filters.regions?.length) params.set("regions", filters.regions.join(","));
  if (filters.genres?.length) params.set("genres", filters.genres.join(","));
  if (filters.themes?.length) params.set("themes", filters.themes.join(","));
  if (filters.keywords?.length) params.set("keywords", filters.keywords.join(","));
  if (filters.developer) params.set("developer", filters.developer);
  if (filters.publisher) params.set("publisher", filters.publisher);
  if (filters.yearMin != null) params.set("yearMin", String(filters.yearMin));
  if (filters.yearMax != null) params.set("yearMax", String(filters.yearMax));
  if (filters.ratingMin != null) params.set("ratingMin", String(filters.ratingMin));
  if (filters.ratingMax != null) params.set("ratingMax", String(filters.ratingMax));
  if (filters.playStatus) params.set("playStatus", filters.playStatus);
  // Only serialize when non-default (false)
  if (filters.hidePreRelease === false) params.set("hidePreRelease", "false");
  if (filters.grouped === false) params.set("grouped", "false");
  if (filters.letter) params.set("letter", filters.letter);
  if (filters.sortBy && filters.sortBy !== "title") params.set("sortBy", filters.sortBy);
  if (filters.sortOrder && filters.sortOrder !== "asc") params.set("sortOrder", filters.sortOrder);
  if (filters.search) params.set("search", filters.search);
  return params;
}

export function GamesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [viewMode, setViewMode] = useState<ViewMode>("grid");
  const [searchInput, setSearchInput] = useState(searchParams.get("search") ?? "");
  const debouncedSearch = useDebouncedValue(searchInput, 300);
  const [filtersOpen, setFiltersOpen] = useState(false);

  // Initialize filters from URL
  const [filters, setFilters] = useState<GameFilters>(() => ({
    sortBy: "title",
    sortOrder: "asc",
    pageSize: 48,
    ...parseUrlFilters(searchParams),
  }));

  const { saveDefaultRegions } = useDefaultRegionFilters(searchParams, setFilters);

  // Sync filters to URL
  useEffect(() => {
    const params = filtersToUrl({ ...filters, search: debouncedSearch || undefined });
    const current = searchParams.toString();
    const next = params.toString();
    if (current !== next) {
      setSearchParams(params, { replace: true });
    }
  }, [filters, debouncedSearch, searchParams, setSearchParams]);

  const { data, isLoading } = useGames({
    ...filters,
    search: debouncedSearch || undefined,
  });
  const { data: consoles } = useConsoles();
  const { data: themes } = useThemes();
  const { data: keywords } = useKeywords(50);
  const { data: savedSearches } = useSavedSearches();
  const createSearch = useCreateSavedSearch();
  const deleteSearch = useDeleteSavedSearch();
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  const handleSaveSearch = useCallback(
    (name: string, filterRecord: Record<string, string | number>) => {
      createSearch.mutate({ name, filters: filterRecord });
    },
    [createSearch],
  );

  const handleApplySearch = useCallback(
    (filterRecord: Record<string, string | number>) => {
      setFilters((prev) => savedSearchToFilters(filterRecord, prev));
      if (filterRecord.search) {
        setSearchInput(String(filterRecord.search));
      }
    },
    [],
  );

  const games = data?.data ?? [];

  return (
    <PageLayout title="Games" subtitle={data ? `${data.total} games in your library` : "Browse your game library"}>
      <SectionList>
      <GamesFilterBar
        filters={filters}
        onFiltersChange={setFilters}
        searchValue={searchInput}
        onSearchChange={(value) => {
          setSearchInput(value);
          setFilters((f) => ({ ...f, page: 1 }));
        }}
        viewMode={viewMode}
        onViewModeChange={setViewMode}
        consoles={consoles}
        showHideBetas
      />

      <div className="flex flex-wrap items-start gap-3">
        <AdvancedFilterPanel
          filters={filters}
          onFiltersChange={setFilters}
          consoles={consoles}
          themes={themes}
          keywords={keywords}
          savedSearches={savedSearches}
          onSaveSearch={handleSaveSearch}
          onDeleteSearch={(id) => deleteSearch.mutate(id)}
          onApplySearch={handleApplySearch}
          totalResults={data?.total}
          isOpen={filtersOpen}
          onToggle={() => setFiltersOpen((o) => !o)}
          onSaveDefaultRegions={saveDefaultRegions}
        />
        <BestVersionsButton
          filters={filters}
          onFiltersChange={setFilters}
        />
      </div>

      <ActiveFilterPills
        filters={filters}
        onFiltersChange={setFilters}
        consoles={consoles}
      />

      <AlphabetBar
        activeLetter={filters.letter}
        onLetterClick={(letter) =>
          setFilters((f) => ({ ...f, letter, page: 1 }))
        }
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
          <div className="space-y-1">
            {Array.from({ length: 10 }, (_, i) => (
              <GameListRowSkeleton key={i} />
            ))}
          </div>
        )
      ) : games.length === 0 ? (
        <EmptyState
          icon={Library}
          title="No games found"
          description={
            searchInput
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
    </SectionList>
    </PageLayout>
  );
}

export default GamesPage;
