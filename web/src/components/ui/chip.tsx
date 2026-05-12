import { useState } from "react";
import { ChevronDown, ChevronUp } from "lucide-react";
import { cn } from "@/lib/cn";

// Pill-shaped toggle chip. Single source of truth for the selected /
// unselected visual states so `ChipPicker` (multi-select) and any
// single-select chip rows share one implementation.

export interface ChipProps
  extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, "children"> {
  selected: boolean;
  children: React.ReactNode;
}

export function Chip({ selected, className, children, ...rest }: ChipProps) {
  return (
    <button data-comp="Chip"
      aria-pressed={selected}
      className={cn(
        // Tailwind preflight resets <button>'s native cursor to default;
        // every chip in the app needs to opt back in so it doesn't look
        // non-clickable to a mouse user. See #1176 Issue A.
        "px-2.5 py-1 rounded-full text-xs font-medium transition-colors",
        "cursor-pointer disabled:cursor-not-allowed",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950",
        selected
          ? "bg-brand-600 text-white"
          : "bg-surface-800 text-surface-300 hover:bg-surface-700 hover:text-surface-100",
        className,
      )}
      {...rest}
    >
      {children}
    </button>
  );
}

// Multi-select picker built on top of `Chip`. Collapses to the first
// 12 options with a "Show all ({n})" toggle when there are more — the
// threshold is tuned for the game-filter panel but fits any picker
// with a long tail (e.g. keywords, themes).

export interface ChipPickerOption {
  value: string;
  label: string;
}

export interface ChipPickerProps {
  label: string;
  options: ChipPickerOption[];
  selected: string[];
  onChange: (selected: string[]) => void;
  testId: string;
  /** Number of options visible before the "Show all" toggle appears. */
  initiallyVisible?: number;
}

export function ChipPicker({
  label,
  options,
  selected,
  onChange,
  testId,
  initiallyVisible = 12,
}: ChipPickerProps) {
  const [expanded, setExpanded] = useState(false);
  const visibleOptions = expanded ? options : options.slice(0, initiallyVisible);
  const labelId = `${testId}-label`;
  const hasOverflow = options.length > initiallyVisible;

  return (
    <div data-comp="ChipPicker" data-testid={testId}>
      <span
        id={labelId}
        className="block text-sm font-medium text-surface-300 mb-2"
      >
        {label}
      </span>
      <div
        className="flex flex-wrap gap-1.5"
        role="group"
        aria-labelledby={labelId}
      >
        {visibleOptions.map((opt) => {
          const isSelected = selected.includes(opt.value);
          return (
            <Chip
              key={opt.value}
              selected={isSelected}
              onClick={() =>
                onChange(
                  isSelected
                    ? selected.filter((v) => v !== opt.value)
                    : [...selected, opt.value],
                )
              }
            >
              {opt.label}
            </Chip>
          );
        })}
      </div>
      {hasOverflow && (
        <button
          onClick={() => setExpanded(!expanded)}
          // Same cursor + focus hygiene as Chip itself — this toggle
          // sits visually adjacent to a row of chips and was the only
          // interactive element in ChipPicker that didn't pick those
          // up. See #1176 Issue A.
          className="mt-1.5 text-xs text-surface-400 hover:text-surface-200 flex items-center gap-1 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950 rounded"
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
