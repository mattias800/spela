import { useRef, useId } from "react";
import { cn } from "@/lib/cn";

// DESIGN component — a view-switcher over a closed set of
// mutually-exclusive options.
//
// Use this whenever the user picks one of N "shapes" the same screen
// can take (group by generation vs manufacturer, list vs grid view,
// week vs month range, …). Do NOT use it for selecting an item from a
// long list — that's a `<Select>`. Do NOT use it for multi-select
// filters — that's `<ChipPicker>`.
//
// The previous pattern of "two adjacent `<Chip>` toggles" looked
// passive (chips read as metadata) and didn't carry the right ARIA —
// see #1176 for the writeup.
//
// Accessibility:
// - The whole control is a single radiogroup with one tab stop.
// - Arrow keys / Home / End move *roving focus* between segments and
//   simultaneously change the selected value (instant-apply — there's
//   nowhere "applied later" for this control to live).
// - `aria-checked` on each segment reflects the selected value.
// - When [label] is provided we render a visible <span> and link via
//   `aria-labelledby`. Otherwise [ariaLabel] is set via `aria-label`.

interface SegmentedControlOption<T extends string> {
  value: T;
  label: React.ReactNode;
  /** Test id forwarded to the segment button. */
  testId?: string;
}

export interface SegmentedControlProps<T extends string> {
  options: SegmentedControlOption<T>[];
  value: T;
  onChange: (value: T) => void;
  /** Visible label, e.g. "Group by:". Rendered alongside the control. */
  label?: React.ReactNode;
  /**
   * Programmatic label for screen readers when no visible label is shown.
   * One of [label] or [ariaLabel] must be supplied.
   */
  ariaLabel?: string;
  testId?: string;
  className?: string;
}

export function SegmentedControl<T extends string>({
  options,
  value,
  onChange,
  label,
  ariaLabel,
  testId,
  className,
}: SegmentedControlProps<T>) {
  const labelId = useId();
  const containerRef = useRef<HTMLDivElement>(null);
  const selectedIndex = Math.max(
    0,
    options.findIndex((o) => o.value === value),
  );

  const moveBy = (delta: number) => {
    if (options.length === 0) return;
    const next = (selectedIndex + delta + options.length) % options.length;
    onChange(options[next].value);
    // Move actual DOM focus to keep the visible focus ring in sync.
    const buttons = containerRef.current?.querySelectorAll<HTMLButtonElement>(
      "[role=radio]",
    );
    buttons?.[next]?.focus();
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    switch (event.key) {
      case "ArrowLeft":
      case "ArrowUp":
        event.preventDefault();
        moveBy(-1);
        break;
      case "ArrowRight":
      case "ArrowDown":
        event.preventDefault();
        moveBy(1);
        break;
      case "Home":
        event.preventDefault();
        if (options[0]) onChange(options[0].value);
        break;
      case "End":
        event.preventDefault();
        if (options.length > 0) onChange(options[options.length - 1].value);
        break;
    }
  };

  return (
    <div
      data-comp="SegmentedControl"
      data-testid={testId}
      className={cn("inline-flex items-center gap-2", className)}
    >
      {label && (
        <span
          id={labelId}
          className="text-sm font-medium text-surface-300"
        >
          {label}
        </span>
      )}
      <div
        ref={containerRef}
        role="radiogroup"
        aria-labelledby={label ? labelId : undefined}
        aria-label={!label ? ariaLabel : undefined}
        onKeyDown={handleKeyDown}
        className={cn(
          // Connected track — single rounded container with internal
          // separators handled by the segment buttons themselves
          // (rounded corners only on the ends, border between
          // segments).
          "inline-flex items-stretch rounded-full bg-surface-900 p-0.5 border border-surface-800",
        )}
      >
        {options.map((opt, i) => {
          const isSelected = opt.value === value;
          // Only the selected segment is a tab stop — arrow keys move
          // roving focus across the others. This is the WAI-ARIA
          // "radiogroup" pattern.
          const tabIndex = isSelected ? 0 : -1;
          return (
            <button
              key={opt.value}
              type="button"
              role="radio"
              aria-checked={isSelected}
              tabIndex={tabIndex}
              data-testid={opt.testId}
              onClick={() => onChange(opt.value)}
              className={cn(
                "relative px-4 py-2 rounded-full text-sm font-medium transition-colors cursor-pointer",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950",
                // ≥44px tall comes from py-2 (8px) + text-sm line-height
                // (20px) + the parent track's p-0.5 = 0.5+8+20+8+0.5 ≈
                // 37px button, 41-42px outer including padding. Bump if
                // tooling reports below WCAG AA touch-target on the
                // narrowest viewport.
                isSelected
                  ? "bg-brand-600 text-white shadow-sm"
                  : "text-surface-300 hover:text-surface-100 hover:bg-surface-800",
                // Subtle visual separation between adjacent segments
                // when neither is selected — a 1px divider that fades
                // when the segment becomes the selected one.
                !isSelected && i > 0 && "before:absolute before:left-0 before:top-1/4 before:h-1/2 before:w-px before:bg-surface-700",
              )}
            >
              {opt.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
