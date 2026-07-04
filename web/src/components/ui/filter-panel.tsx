import type { HTMLAttributes, ReactNode } from "react";
import { X } from "lucide-react";
import { cn } from "@/lib/cn";
import { Button } from "./button";

interface FilterPanelProps extends HTMLAttributes<HTMLDivElement> {
  title?: string;
  hasFilters: boolean;
  onClear: () => void;
  clearButtonTestId?: string;
  children: ReactNode;
}

export function FilterPanel({
  title = "Filters",
  hasFilters,
  onClear,
  clearButtonTestId,
  className,
  children,
  ...props
}: FilterPanelProps) {
  return (
    <div
      data-comp="FilterPanel"
      className={cn(
        "space-y-4 rounded-2xl border border-surface-800/50 bg-surface-900/50 p-5",
        className,
      )}
      {...props}
    >
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold uppercase tracking-wider text-surface-400">
          {title}
        </h3>
        {hasFilters && (
          <Button
            variant="ghost"
            size="sm"
            onClick={onClear}
            data-testid={clearButtonTestId}
          >
            <X aria-hidden="true" className="h-3.5 w-3.5" />
            Clear
          </Button>
        )}
      </div>
      {children}
    </div>
  );
}
