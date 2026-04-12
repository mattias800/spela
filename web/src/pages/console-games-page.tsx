import { useState, useEffect, useCallback } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { Library } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameGrid } from "@/components/game-grid";
import { ConsoleHeroBanner } from "@/components/console-hero-banner";
import {
  GameCardSkeleton,
  GameListRowSkeleton,
  EmptyState,
} from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import { useConsoles } from "@/hooks/use-consoles";
import { useGames, useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import { useBiosStatus } from "@/hooks/use-bios";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { useThemes, useKeywords } from "@/hooks/use-explore";
import {
  useSavedSearches,
  useCreateSavedSearch,
  useDeleteSavedSearch,
} from "@/hooks/use-saved-searches";
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
import { BiosWarningBanner } from "@/features/bios/components/bios-warning-banner";
import { useAuth } from "@/hooks/use-auth";
import { useDefaultRegionFilters } from "@/hooks/use-default-region-filters";
import type { GameFilters } from "@/types/api";

type ViewMode = "grid" | "list";

/** Parse URL search params into GameFilters (excluding consoleId) */
function parseUrlFilters(params: URLSearchParams): Partial<GameFilters> {
  const f: Partial<GameFilters> = {};
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

/** Serialize GameFilters to URL search params (excluding consoleId) */
function filtersToUrl(filters: GameFilters): URLSearchParams {
  const params = new URLSearchParams();
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
  if (filters.hidePreRelease === false) params.set("hidePreRelease", "false");
  if (filters.grouped === false) params.set("grouped", "false");
  if (filters.letter) params.set("letter", filters.letter);
  if (filters.sortBy && filters.sortBy !== "title") params.set("sortBy", filters.sortBy);
  if (filters.sortOrder && filters.sortOrder !== "asc") params.set("sortOrder", filters.sortOrder);
  if (filters.search) params.set("search", filters.search);
  return params;
}

export function ConsoleGamesPage() {
  const { id } = useParams<{ id: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [viewMode, setViewMode] = useState<ViewMode>("grid");
  const [searchInput, setSearchInput] = useState(searchParams.get("search") ?? "");
  const debouncedSearch = useDebouncedValue(searchInput, 300);
  const [filtersOpen, setFiltersOpen] = useState(false);

  const { data: consoles } = useConsoles();
  const { data: themes } = useThemes();
  const { data: keywords } = useKeywords(50);
  const { data: savedSearches } = useSavedSearches();
  const createSearch = useCreateSavedSearch();
  const deleteSearch = useDeleteSavedSearch();
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();
  const { data: biosData } = useBiosStatus();
  const { isAdmin } = useAuth();
  const console = consoles?.find((c) => c.id === id);
  const consoleName = console?.name ?? "Console";

  // Initialize filters from URL
  const [filters, setFilters] = useState<GameFilters>(() => ({
    sortBy: "title",
    sortOrder: "asc",
    pageSize: 48,
    ...parseUrlFilters(searchParams),
  }));

  const { saveDefaultRegions } = useDefaultRegionFilters(searchParams, setFilters);

  // Sync filters to URL (excluding consoleId)
  useEffect(() => {
    const params = filtersToUrl({ ...filters, search: debouncedSearch || undefined });
    const current = searchParams.toString();
    const next = params.toString();
    if (current !== next) {
      setSearchParams(params, { replace: true });
    }
  }, [filters, debouncedSearch, searchParams, setSearchParams]);

  // Always lock consoleId to URL param
  const { data, isLoading } = useGames({
    ...filters,
    consoleId: id,
    search: debouncedSearch || undefined,
  });

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

  const biosConsole = biosData?.consoles.find((c) => c.consoleId === id);
  const showBiosWarning =
    biosConsole?.status === "missing" && biosConsole.biosRequired;
  const missingBiosFiles =
    biosConsole?.files
      .filter((f) => f.status === "missing" && f.required)
      .map((f) => f.fileName) ?? [];

  return (
    <PageLayout backButtonVariant="standard" backTo={`/consoles/${id}`} backLabel="Console" data-testid="console-games-page">
      <SectionList>

      <ConsoleHeroBanner console={console} gameCount={data?.total} />

      {showBiosWarning && (
        <BiosWarningBanner
          message={`Missing BIOS: ${consoleName} requires firmware files to play games.`}
          isAdmin={isAdmin}
          missingFiles={missingBiosFiles}
        />
      )}

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
        hideConsoleFilter
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
          onDeleteSearch={(deleteId) => deleteSearch.mutate(deleteId)}
          onApplySearch={handleApplySearch}
          totalResults={data?.total}
          isOpen={filtersOpen}
          onToggle={() => setFiltersOpen((o) => !o)}
          hideConsoleFilter
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
              <GameCardSkeleton
                key={i}
                aspectRatio={console?.coverAspectRatio}
              />
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
              : "No games have been detected for this console yet."
          }
        />
      ) : viewMode === "grid" ? (
        <GameGrid>
          {games.map((game) => (
            <GameCard
              key={game.id}
              game={game}
              aspectRatio={console?.coverAspectRatio}
              hideConsoleName
              onToggleFavorite={handleToggleFavorite}
              onTogglePlayLater={handleTogglePlayLater}
            />
          ))}
        </GameGrid>
      ) : (
        <div className="space-y-1">
          {games.map((game) => (
            <GameListRow key={game.id} game={game} hideConsoleName />
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
