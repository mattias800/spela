import { Grid3X3, List, ArrowUpDown } from "lucide-react";
import { SearchInput, Select } from "@/components/ui";
import { cn } from "@/lib/cn";
import type { GameFilters, Console } from "@/types/api";

type ViewMode = "grid" | "list";

interface GamesFilterBarProps {
  filters: GameFilters;
  onFiltersChange: (updater: (prev: GameFilters) => GameFilters) => void;
  searchValue: string;
  onSearchChange: (value: string) => void;
  viewMode: ViewMode;
  onViewModeChange: (mode: ViewMode) => void;
  consoles: Console[] | undefined;
}

export function GamesFilterBar({
  filters,
  onFiltersChange,
  searchValue,
  onSearchChange,
  viewMode,
  onViewModeChange,
  consoles,
}: GamesFilterBarProps) {
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
    <div className="flex flex-wrap items-center gap-3">
      <div className="flex-1 min-w-[240px] max-w-md">
        <SearchInput
          placeholder="Search games..."
          value={searchValue}
          onChange={(e) => onSearchChange(e.target.value)}
        />
      </div>

      <Select
        options={consoleOptions}
        value={filters.consoleId ?? ""}
        onChange={(e) =>
          onFiltersChange((f) => ({
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
          onFiltersChange((f) => ({
            ...f,
            sortBy: e.target.value as GameFilters["sortBy"],
          }))
        }
        className="w-40"
      />

      <button
        onClick={() =>
          onFiltersChange((f) => ({
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
          onClick={() => onViewModeChange("grid")}
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
          onClick={() => onViewModeChange("list")}
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
  );
}
