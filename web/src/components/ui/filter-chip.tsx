import { cn } from "@/lib/cn";

interface FilterChipProps {
  label: string;
  isSelected: boolean;
  onClick: () => void;
  count?: number;
}

export function FilterChip({
  label,
  isSelected,
  onClick,
  count,
}: FilterChipProps) {
  return (
    <button
      data-comp="FilterChip"
      aria-pressed={isSelected}
      onClick={onClick}
      className={cn(
        "inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
        isSelected
          ? "bg-brand-500/15 text-brand-400 border-brand-500/30"
          : "bg-surface-800 text-surface-300 border-surface-700 hover:bg-surface-700",
      )}
    >
      {label}
      {count != null && ` (${count})`}
    </button>
  );
}
