import { X } from "lucide-react";
import type { GameFilters, Console } from "@/types/api";

interface FilterPill {
  key: string;
  label: string;
  onDismiss: () => void;
}

interface ActiveFilterPillsProps {
  filters: GameFilters;
  onFiltersChange: (updater: (prev: GameFilters) => GameFilters) => void;
  consoles?: Console[];
}

function buildPills(
  filters: GameFilters,
  onFiltersChange: ActiveFilterPillsProps["onFiltersChange"],
  consoles?: Console[],
): FilterPill[] {
  const pills: FilterPill[] = [];

  // Console pills
  for (const c of filters.consoles ?? []) {
    const consoleName = consoles?.find((con) => con.abbreviation === c)?.name ?? c;
    pills.push({
      key: `console-${c}`,
      label: consoleName,
      onDismiss: () =>
        onFiltersChange((f) => ({
          ...f,
          consoles: f.consoles?.filter((v) => v !== c),
          page: 1,
        })),
    });
  }

  // Region pills
  for (const r of filters.regions ?? []) {
    pills.push({
      key: `region-${r}`,
      label: `Region: ${r}`,
      onDismiss: () =>
        onFiltersChange((f) => ({
          ...f,
          regions: f.regions?.filter((v) => v !== r),
          page: 1,
        })),
    });
  }

  // Genre pills
  for (const g of filters.genres ?? []) {
    pills.push({
      key: `genre-${g}`,
      label: g,
      onDismiss: () =>
        onFiltersChange((f) => ({
          ...f,
          genres: f.genres?.filter((v) => v !== g),
          page: 1,
        })),
    });
  }

  // Theme pills
  for (const t of filters.themes ?? []) {
    pills.push({
      key: `theme-${t}`,
      label: `Theme: ${t}`,
      onDismiss: () =>
        onFiltersChange((f) => ({
          ...f,
          themes: f.themes?.filter((v) => v !== t),
          page: 1,
        })),
    });
  }

  // Keyword pills
  for (const k of filters.keywords ?? []) {
    pills.push({
      key: `keyword-${k}`,
      label: `Keyword: ${k}`,
      onDismiss: () =>
        onFiltersChange((f) => ({
          ...f,
          keywords: f.keywords?.filter((v) => v !== k),
          page: 1,
        })),
    });
  }

  // Developer
  if (filters.developer) {
    pills.push({
      key: "developer",
      label: `Dev: ${filters.developer}`,
      onDismiss: () =>
        onFiltersChange((f) => ({ ...f, developer: undefined, page: 1 })),
    });
  }

  // Publisher
  if (filters.publisher) {
    pills.push({
      key: "publisher",
      label: `Pub: ${filters.publisher}`,
      onDismiss: () =>
        onFiltersChange((f) => ({ ...f, publisher: undefined, page: 1 })),
    });
  }

  // Year range
  if (filters.yearMin != null || filters.yearMax != null) {
    const min = filters.yearMin ?? "...";
    const max = filters.yearMax ?? "...";
    pills.push({
      key: "year",
      label: `Year: ${min}-${max}`,
      onDismiss: () =>
        onFiltersChange((f) => ({
          ...f,
          yearMin: undefined,
          yearMax: undefined,
          page: 1,
        })),
    });
  }

  // Rating range
  if (filters.ratingMin != null || filters.ratingMax != null) {
    const min = filters.ratingMin ?? 0;
    const max = filters.ratingMax ?? 100;
    pills.push({
      key: "rating",
      label: `Rating: ${min}-${max}`,
      onDismiss: () =>
        onFiltersChange((f) => ({
          ...f,
          ratingMin: undefined,
          ratingMax: undefined,
          page: 1,
        })),
    });
  }

  // Play status
  if (filters.playStatus) {
    pills.push({
      key: "playStatus",
      label: `Status: ${filters.playStatus}`,
      onDismiss: () =>
        onFiltersChange((f) => ({ ...f, playStatus: undefined, page: 1 })),
    });
  }

  // Show betas (only when hidePreRelease is explicitly false, since default is hidden)
  if (filters.hidePreRelease === false) {
    pills.push({
      key: "hidePreRelease",
      label: "Showing betas",
      onDismiss: () =>
        onFiltersChange((f) => ({
          ...f,
          hidePreRelease: undefined,
          page: 1,
        })),
    });
  }

  // Showing all variants (when grouped is explicitly false)
  if (filters.grouped === false) {
    pills.push({
      key: "grouped",
      label: "All variants",
      onDismiss: () =>
        onFiltersChange((f) => ({
          ...f,
          grouped: undefined,
          page: 1,
        })),
    });
  }

  return pills;
}

export function ActiveFilterPills({
  filters,
  onFiltersChange,
  consoles,
}: ActiveFilterPillsProps) {
  const pills = buildPills(filters, onFiltersChange, consoles);

  if (pills.length === 0) return null;

  const handleClearAll = () => {
    onFiltersChange((prev) => ({
      search: prev.search,
      sortBy: prev.sortBy,
      sortOrder: prev.sortOrder,
      page: 1,
      pageSize: prev.pageSize,
    }));
  };

  return (
    <div className="flex flex-wrap items-center gap-2" data-testid="active-filter-pills">
      {pills.map((pill) => (
        <button
          key={pill.key}
          onClick={pill.onDismiss}
          className="inline-flex items-center gap-1.5 rounded-full bg-brand-500/15 text-brand-400 border border-brand-500/30 px-2.5 py-1 text-xs font-medium hover:bg-brand-500/25 transition-colors"
          data-testid={`filter-pill-${pill.key}`}
          aria-label={`Remove filter: ${pill.label}`}
        >
          {pill.label}
          <X className="h-3 w-3" />
        </button>
      ))}
      {pills.length >= 2 && (
        <button
          onClick={handleClearAll}
          className="text-xs text-surface-400 hover:text-surface-200 underline underline-offset-2"
          data-testid="clear-all-filters"
        >
          Clear all
        </button>
      )}
    </div>
  );
}
