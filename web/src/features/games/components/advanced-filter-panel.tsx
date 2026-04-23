import { useState } from "react";
import {
  SlidersHorizontal,
  X,
  Save,
  Bookmark,
  Trash2,
} from "lucide-react";
import { Button, Input, Badge, Chip, ChipPicker } from "@/components/ui";
import { cn } from "@/lib/cn";
import type { GameFilters, Console, Theme, Keyword, SavedSearch } from "@/types/api";
import {
  countActiveFilters,
  filtersToRecord,
  GENRE_OPTIONS,
  PLAY_STATUS_OPTIONS,
  REGION_OPTIONS,
} from "../lib/filter-helpers";

export { savedSearchToFilters } from "../lib/filter-helpers";

// Range input — two numeric `Input`s with a "to" separator. Kept
// local since it's only used inside this panel, but delegates
// styling to the shared `Input` component instead of duplicating
// focus-ring / border Tailwind.
function RangeInput({
  label,
  min,
  max,
  valueMin,
  valueMax,
  onChangeMin,
  onChangeMax,
  testId,
}: {
  label: string;
  min: number;
  max: number;
  valueMin: number | undefined;
  valueMax: number | undefined;
  onChangeMin: (v: number | undefined) => void;
  onChangeMax: (v: number | undefined) => void;
  testId: string;
}) {
  return (
    <div data-testid={testId}>
      <span className="block text-sm font-medium text-surface-300 mb-2">
        {label}
      </span>
      <div className="flex items-center gap-2">
        <Input
          type="number"
          min={min}
          max={max}
          placeholder={String(min)}
          value={valueMin ?? ""}
          onChange={(e) =>
            onChangeMin(e.target.value ? Number(e.target.value) : undefined)
          }
          aria-label={`${label} minimum`}
          className="w-24"
          data-testid={`${testId}-min`}
        />
        <span className="text-surface-500 text-sm">to</span>
        <Input
          type="number"
          min={min}
          max={max}
          placeholder={String(max)}
          value={valueMax ?? ""}
          onChange={(e) =>
            onChangeMax(e.target.value ? Number(e.target.value) : undefined)
          }
          aria-label={`${label} maximum`}
          className="w-24"
          data-testid={`${testId}-max`}
        />
      </div>
    </div>
  );
}

function SavedSearchItem({
  search,
  onApply,
  onDelete,
}: {
  search: SavedSearch;
  onApply: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="flex items-center gap-2 group" data-testid="saved-search-item">
      <button
        onClick={onApply}
        className="flex-1 text-left px-3 py-2 rounded-lg bg-surface-800 hover:bg-surface-700 text-sm text-surface-200 transition-colors truncate"
        data-testid="saved-search-apply"
      >
        <span className="font-medium">{search.name}</span>
      </button>
      <button
        onClick={onDelete}
        className="p-1.5 rounded text-surface-500 hover:text-red-400 opacity-0 group-hover:opacity-100 focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 transition-all"
        aria-label={`Delete ${search.name}`}
        data-testid="saved-search-delete"
      >
        <Trash2 className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

interface AdvancedFilterPanelProps {
  filters: GameFilters;
  onFiltersChange: (updater: (prev: GameFilters) => GameFilters) => void;
  consoles: Console[] | undefined;
  themes: Theme[] | undefined;
  keywords: Keyword[] | undefined;
  savedSearches: SavedSearch[] | undefined;
  onSaveSearch: (name: string, filters: Record<string, string | number>) => void;
  onDeleteSearch: (id: string) => void;
  onApplySearch: (filters: Record<string, string | number>) => void;
  totalResults?: number;
  isOpen: boolean;
  onToggle: () => void;
  hideConsoleFilter?: boolean;
  onSaveDefaultRegions?: (regions: string[]) => void;
}

export function AdvancedFilterPanel({
  filters,
  onFiltersChange,
  consoles,
  themes,
  keywords,
  savedSearches,
  onSaveSearch,
  onDeleteSearch,
  onApplySearch,
  totalResults,
  isOpen,
  onToggle,
  hideConsoleFilter = false,
  onSaveDefaultRegions,
}: AdvancedFilterPanelProps) {
  const [saveName, setSaveName] = useState("");
  const [showSaveInput, setShowSaveInput] = useState(false);

  const consoleOptions = (consoles ?? []).map((c) => ({
    value: c.abbreviation,
    label: c.name,
  }));

  const themeOptions = (themes ?? []).map((t) => ({
    value: String(t.id),
    label: `${t.name} (${t.gameCount})`,
  }));

  const keywordOptions = (keywords ?? []).map((k) => ({
    value: String(k.id),
    label: `${k.name} (${k.gameCount})`,
  }));

  const activeFilterCount = countActiveFilters(filters);

  const handleClearAll = () => {
    onFiltersChange((prev) => ({
      search: prev.search,
      sortBy: prev.sortBy,
      sortOrder: prev.sortOrder,
      page: 1,
      pageSize: prev.pageSize,
    }));
  };

  const handleSave = () => {
    if (!saveName.trim()) return;
    onSaveSearch(saveName.trim(), filtersToRecord(filters));
    setSaveName("");
    setShowSaveInput(false);
  };

  return (
    <div data-testid="advanced-filter-panel">
      {/* Toggle button */}
      <button
        onClick={onToggle}
        aria-expanded={isOpen}
        className={cn(
          "flex items-center gap-2 px-3 py-2.5 rounded-lg border text-sm font-medium transition-all",
          isOpen
            ? "bg-brand-600/20 border-brand-500 text-brand-400"
            : "bg-surface-900 border-surface-700 text-surface-300 hover:text-surface-100 hover:border-surface-600",
        )}
        data-testid="advanced-filter-toggle"
      >
        <SlidersHorizontal className="h-4 w-4" />
        Filters
        {activeFilterCount > 0 && (
          <Badge className="bg-brand-600 text-white text-xs">
            {activeFilterCount}
          </Badge>
        )}
      </button>

      {/* Panel */}
      {isOpen && (
        <div className="mt-3 p-5 rounded-xl bg-surface-900/80 border border-surface-700 space-y-5">
          {/* Header */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <h3 className="text-sm font-semibold text-surface-100">
                Advanced Filters
              </h3>
              {totalResults != null && (
                <span className="text-xs text-surface-400" data-testid="match-count">
                  {totalResults} games match
                </span>
              )}
            </div>
            <div className="flex items-center gap-2">
              {activeFilterCount > 0 && (
                <button
                  onClick={handleClearAll}
                  className="text-xs text-surface-400 hover:text-surface-200 flex items-center gap-1"
                  data-testid="clear-filters"
                >
                  <X className="h-3 w-3" /> Clear all
                </button>
              )}
            </div>
          </div>

          {/* Console picker */}
          {!hideConsoleFilter && (
            <ChipPicker
              label="Consoles"
              options={consoleOptions}
              selected={filters.consoles ?? []}
              onChange={(next) =>
                onFiltersChange((f) => ({ ...f, consoles: next, page: 1 }))
              }
              testId="console-filter"
            />
          )}

          {/* Region picker */}
          <div>
            <ChipPicker
              label="Regions"
              options={REGION_OPTIONS}
              selected={filters.regions ?? []}
              onChange={(next) =>
                onFiltersChange((f) => ({ ...f, regions: next, page: 1 }))
              }
              testId="region-filter"
            />
            {onSaveDefaultRegions && (filters.regions?.length ?? 0) > 0 && (
              <button
                onClick={() => onSaveDefaultRegions(filters.regions ?? [])}
                className="mt-1.5 text-xs text-surface-500 hover:text-brand-400 transition-colors"
                data-testid="save-default-regions"
              >
                Set as default regions
              </button>
            )}
          </div>

          {/* Genre picker */}
          <ChipPicker
            label="Genres"
            options={GENRE_OPTIONS}
            selected={filters.genres ?? []}
            onChange={(next) =>
              onFiltersChange((f) => ({ ...f, genres: next, page: 1 }))
            }
            testId="genre-filter"
          />

          {/* Theme picker */}
          {themeOptions.length > 0 && (
            <ChipPicker
              label="Themes"
              options={themeOptions}
              selected={filters.themes ?? []}
              onChange={(next) =>
                onFiltersChange((f) => ({ ...f, themes: next, page: 1 }))
              }
              testId="theme-filter"
            />
          )}

          {/* Keyword picker */}
          {keywordOptions.length > 0 && (
            <ChipPicker
              label="Keywords"
              options={keywordOptions}
              selected={filters.keywords ?? []}
              onChange={(next) =>
                onFiltersChange((f) => ({ ...f, keywords: next, page: 1 }))
              }
              testId="keyword-filter"
            />
          )}

          {/* Developer / Publisher */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="filter-developer" className="block text-sm font-medium text-surface-300 mb-2">
                Developer
              </label>
              <Input
                id="filter-developer"
                placeholder="e.g. Nintendo"
                value={filters.developer ?? ""}
                onChange={(e) =>
                  onFiltersChange((f) => ({
                    ...f,
                    developer: e.target.value || undefined,
                    page: 1,
                  }))
                }
                data-testid="developer-filter"
              />
            </div>
            <div>
              <label htmlFor="filter-publisher" className="block text-sm font-medium text-surface-300 mb-2">
                Publisher
              </label>
              <Input
                id="filter-publisher"
                placeholder="e.g. Konami"
                value={filters.publisher ?? ""}
                onChange={(e) =>
                  onFiltersChange((f) => ({
                    ...f,
                    publisher: e.target.value || undefined,
                    page: 1,
                  }))
                }
                data-testid="publisher-filter"
              />
            </div>
          </div>

          {/* Year range */}
          <RangeInput
            label="Release Year"
            min={1970}
            max={2010}
            valueMin={filters.yearMin}
            valueMax={filters.yearMax}
            onChangeMin={(yearMin) =>
              onFiltersChange((f) => ({ ...f, yearMin, page: 1 }))
            }
            onChangeMax={(yearMax) =>
              onFiltersChange((f) => ({ ...f, yearMax, page: 1 }))
            }
            testId="year-filter"
          />

          {/* Rating range */}
          <RangeInput
            label="Rating"
            min={0}
            max={100}
            valueMin={filters.ratingMin}
            valueMax={filters.ratingMax}
            onChangeMin={(ratingMin) =>
              onFiltersChange((f) => ({ ...f, ratingMin, page: 1 }))
            }
            onChangeMax={(ratingMax) =>
              onFiltersChange((f) => ({ ...f, ratingMax, page: 1 }))
            }
            testId="rating-filter"
          />

          {/* Play status (single-select) */}
          <div data-testid="play-status-filter">
            <span id="play-status-label" className="block text-sm font-medium text-surface-300 mb-2">
              Play Status
            </span>
            <div className="flex flex-wrap gap-1.5" role="group" aria-labelledby="play-status-label">
              {PLAY_STATUS_OPTIONS.map((opt) => (
                <Chip
                  key={opt.label}
                  selected={filters.playStatus === opt.value}
                  onClick={() =>
                    onFiltersChange((f) => ({
                      ...f,
                      playStatus: opt.value,
                      page: 1,
                    }))
                  }
                >
                  {opt.label}
                </Chip>
              ))}
            </div>
          </div>

          {/* Save / Saved searches section */}
          <div className="border-t border-surface-700 pt-4 space-y-3">
            <div className="flex items-center justify-between">
              <h4 className="text-sm font-medium text-surface-300 flex items-center gap-1.5">
                <Bookmark className="h-3.5 w-3.5" /> Saved Searches
              </h4>
              {activeFilterCount > 0 && !showSaveInput && (
                <button
                  onClick={() => setShowSaveInput(true)}
                  className="text-xs text-brand-400 hover:text-brand-300 flex items-center gap-1"
                  data-testid="save-search-button"
                >
                  <Save className="h-3 w-3" /> Save current
                </button>
              )}
            </div>

            {showSaveInput && (
              <div className="flex items-center gap-2" data-testid="save-search-form">
                <Input
                  placeholder="Search name..."
                  value={saveName}
                  onChange={(e) => setSaveName(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && handleSave()}
                  className="flex-1"
                  autoFocus
                  data-testid="save-search-name"
                />
                <Button
                  variant="primary"
                  onClick={handleSave}
                  disabled={!saveName.trim()}
                >
                  Save
                </Button>
                <button
                  onClick={() => {
                    setShowSaveInput(false);
                    setSaveName("");
                  }}
                  className="p-2 text-surface-400 hover:text-surface-200"
                  aria-label="Cancel"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            )}

            {savedSearches && savedSearches.length > 0 ? (
              <div className="space-y-1.5" data-testid="saved-searches-list">
                {savedSearches.map((s) => (
                  <SavedSearchItem
                    key={s.id}
                    search={s}
                    onApply={() => onApplySearch(s.filters)}
                    onDelete={() => onDeleteSearch(s.id)}
                  />
                ))}
              </div>
            ) : (
              <p className="text-xs text-surface-500">
                No saved searches yet. Apply filters and save them for quick access.
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
