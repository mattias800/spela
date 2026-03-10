import { useState } from "react";
import {
  SlidersHorizontal,
  X,
  ChevronDown,
  ChevronUp,
  Save,
  Bookmark,
  Trash2,
} from "lucide-react";
import { Button, Input, Badge } from "@/components/ui";
import { cn } from "@/lib/cn";
import type { GameFilters, Console, Theme, Keyword, SavedSearch } from "@/types/api";

// --- Multi-select chip picker ---

function ChipPicker({
  label,
  options,
  selected,
  onChange,
  testId,
}: {
  label: string;
  options: Array<{ value: string; label: string }>;
  selected: string[];
  onChange: (selected: string[]) => void;
  testId: string;
}) {
  const [expanded, setExpanded] = useState(false);
  const visibleOptions = expanded ? options : options.slice(0, 12);
  const labelId = `${testId}-label`;

  return (
    <div data-testid={testId}>
      <span id={labelId} className="block text-sm font-medium text-surface-300 mb-2">
        {label}
      </span>
      <div className="flex flex-wrap gap-1.5" role="group" aria-labelledby={labelId}>
        {visibleOptions.map((opt) => {
          const isSelected = selected.includes(opt.value);
          return (
            <button
              key={opt.value}
              aria-pressed={isSelected}
              onClick={() =>
                onChange(
                  isSelected
                    ? selected.filter((v) => v !== opt.value)
                    : [...selected, opt.value],
                )
              }
              className={cn(
                "px-2.5 py-1 rounded-full text-xs font-medium transition-colors",
                isSelected
                  ? "bg-brand-600 text-white"
                  : "bg-surface-800 text-surface-300 hover:bg-surface-700 hover:text-surface-100",
              )}
            >
              {opt.label}
            </button>
          );
        })}
      </div>
      {options.length > 12 && (
        <button
          onClick={() => setExpanded(!expanded)}
          className="mt-1.5 text-xs text-surface-400 hover:text-surface-200 flex items-center gap-1"
        >
          {expanded ? (
            <>
              <ChevronUp className="h-3 w-3" /> Show less
            </>
          ) : (
            <>
              <ChevronDown className="h-3 w-3" /> Show all ({options.length})
            </>
          )}
        </button>
      )}
    </div>
  );
}

// --- Range input ---

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
        <input
          type="number"
          min={min}
          max={max}
          placeholder={String(min)}
          value={valueMin ?? ""}
          onChange={(e) => onChangeMin(e.target.value ? Number(e.target.value) : undefined)}
          aria-label={`${label} minimum`}
          className="w-24 rounded-lg bg-surface-900 border border-surface-700 px-3 py-1.5 text-sm text-surface-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40 focus:border-brand-500"
          data-testid={`${testId}-min`}
        />
        <span className="text-surface-500 text-sm">to</span>
        <input
          type="number"
          min={min}
          max={max}
          placeholder={String(max)}
          value={valueMax ?? ""}
          onChange={(e) => onChangeMax(e.target.value ? Number(e.target.value) : undefined)}
          aria-label={`${label} maximum`}
          className="w-24 rounded-lg bg-surface-900 border border-surface-700 px-3 py-1.5 text-sm text-surface-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40 focus:border-brand-500"
          data-testid={`${testId}-max`}
        />
      </div>
    </div>
  );
}

// --- Play status chips ---

const PLAY_STATUS_OPTIONS: Array<{
  value: GameFilters["playStatus"];
  label: string;
}> = [
  { value: undefined, label: "Any" },
  { value: "unplayed", label: "Unplayed" },
  { value: "played", label: "Played" },
  { value: "favorited", label: "Favorited" },
  { value: "play-later", label: "Play Later" },
];

// --- Saved search item ---

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

// --- Main panel ---

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
}: AdvancedFilterPanelProps) {
  const [saveName, setSaveName] = useState("");
  const [showSaveInput, setShowSaveInput] = useState(false);

  const consoleOptions = (consoles ?? []).map((c) => ({
    value: c.abbreviation,
    label: c.name,
  }));

  const genreOptions = [
    "Action", "Adventure", "RPG", "Platformer", "Puzzle",
    "Racing", "Shooter", "Sports", "Strategy", "Fighting",
    "Simulation", "Beat 'em up", "Arcade", "Horror",
  ].map((g) => ({ value: g, label: g }));

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

  const filtersToRecord = (f: GameFilters): Record<string, string | number> => {
    const rec: Record<string, string | number> = {};
    if (f.consoles?.length) rec.consoles = f.consoles.join(",");
    if (f.genres?.length) rec.genres = f.genres.join(",");
    if (f.themes?.length) rec.themes = f.themes.join(",");
    if (f.keywords?.length) rec.keywords = f.keywords.join(",");
    if (f.developer) rec.developer = f.developer;
    if (f.publisher) rec.publisher = f.publisher;
    if (f.yearMin != null) rec.yearMin = f.yearMin;
    if (f.yearMax != null) rec.yearMax = f.yearMax;
    if (f.ratingMin != null) rec.ratingMin = f.ratingMin;
    if (f.ratingMax != null) rec.ratingMax = f.ratingMax;
    if (f.playStatus) rec.playStatus = f.playStatus;
    if (f.search) rec.search = f.search;
    return rec;
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
          <ChipPicker
            label="Consoles"
            options={consoleOptions}
            selected={filters.consoles ?? []}
            onChange={(consoles) =>
              onFiltersChange((f) => ({ ...f, consoles, page: 1 }))
            }
            testId="console-filter"
          />

          {/* Genre picker */}
          <ChipPicker
            label="Genres"
            options={genreOptions}
            selected={filters.genres ?? []}
            onChange={(genres) =>
              onFiltersChange((f) => ({ ...f, genres, page: 1 }))
            }
            testId="genre-filter"
          />

          {/* Theme picker */}
          {themeOptions.length > 0 && (
            <ChipPicker
              label="Themes"
              options={themeOptions}
              selected={filters.themes ?? []}
              onChange={(themes) =>
                onFiltersChange((f) => ({ ...f, themes, page: 1 }))
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
              onChange={(keywords) =>
                onFiltersChange((f) => ({ ...f, keywords, page: 1 }))
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

          {/* Play status */}
          <div data-testid="play-status-filter">
            <span id="play-status-label" className="block text-sm font-medium text-surface-300 mb-2">
              Play Status
            </span>
            <div className="flex flex-wrap gap-1.5" role="group" aria-labelledby="play-status-label">
              {PLAY_STATUS_OPTIONS.map((opt) => (
                <button
                  key={opt.label}
                  aria-pressed={filters.playStatus === opt.value}
                  onClick={() =>
                    onFiltersChange((f) => ({
                      ...f,
                      playStatus: opt.value,
                      page: 1,
                    }))
                  }
                  className={cn(
                    "px-2.5 py-1 rounded-full text-xs font-medium transition-colors",
                    filters.playStatus === opt.value
                      ? "bg-brand-600 text-white"
                      : "bg-surface-800 text-surface-300 hover:bg-surface-700 hover:text-surface-100",
                  )}
                >
                  {opt.label}
                </button>
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

function countActiveFilters(filters: GameFilters): number {
  let count = 0;
  if (filters.consoles?.length) count++;
  if (filters.genres?.length) count++;
  if (filters.themes?.length) count++;
  if (filters.keywords?.length) count++;
  if (filters.perspectives?.length) count++;
  if (filters.developer) count++;
  if (filters.publisher) count++;
  if (filters.yearMin != null) count++;
  if (filters.yearMax != null) count++;
  if (filters.ratingMin != null) count++;
  if (filters.ratingMax != null) count++;
  if (filters.playStatus) count++;
  return count;
}

/** Convert a saved-search filter record back to GameFilters */
export function savedSearchToFilters(
  record: Record<string, string | number>,
  base: GameFilters,
): GameFilters {
  return {
    ...base,
    consoles: record.consoles ? String(record.consoles).split(",") : undefined,
    genres: record.genres ? String(record.genres).split(",") : undefined,
    themes: record.themes ? String(record.themes).split(",") : undefined,
    keywords: record.keywords ? String(record.keywords).split(",") : undefined,
    developer: record.developer ? String(record.developer) : undefined,
    publisher: record.publisher ? String(record.publisher) : undefined,
    yearMin: record.yearMin != null ? Number(record.yearMin) : undefined,
    yearMax: record.yearMax != null ? Number(record.yearMax) : undefined,
    ratingMin: record.ratingMin != null ? Number(record.ratingMin) : undefined,
    ratingMax: record.ratingMax != null ? Number(record.ratingMax) : undefined,
    playStatus: record.playStatus as GameFilters["playStatus"],
    search: record.search ? String(record.search) : base.search,
    page: 1,
  };
}
