import { Sparkles } from "lucide-react";
import { cn } from "@/lib/cn";
import type { GameFilters } from "@/types/api";

interface BestVersionsButtonProps {
  filters: GameFilters;
  onFiltersChange: (updater: (prev: GameFilters) => GameFilters) => void;
}

function isBestVersionsActive(filters: GameFilters): boolean {
  // Active when grouped is default (true/undefined) AND hidePreRelease is default (true/undefined)
  return filters.grouped !== false && filters.hidePreRelease !== false;
}

export function BestVersionsButton({
  filters,
  onFiltersChange,
}: BestVersionsButtonProps) {
  const isActive = isBestVersionsActive(filters);

  const handleClick = () => {
    if (isActive) {
      // Turn off: show all variants and betas
      onFiltersChange((f) => ({
        ...f,
        grouped: false,
        hidePreRelease: false,
        page: 1,
      }));
    } else {
      // Turn on: grouped and hide pre-release (defaults)
      onFiltersChange((f) => ({
        ...f,
        grouped: undefined,
        hidePreRelease: undefined,
        page: 1,
      }));
    }
  };

  return (
    <button
      onClick={handleClick}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-lg border px-3 py-2.5 text-sm font-medium transition-all",
        isActive
          ? "bg-brand-600/20 border-brand-500 text-brand-400"
          : "bg-surface-900 border-surface-700 text-surface-300 hover:text-surface-100 hover:border-surface-600",
      )}
      data-testid="best-versions-button"
      aria-pressed={isActive}
      aria-label="Best versions only"
    >
      <Sparkles className="h-4 w-4" />
      Best versions
    </button>
  );
}
