import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/cn";

type ProgressBarTone = "default" | "onHero";
type ProgressBarSize = "sm" | "md";

interface ProgressBarProps extends Omit<HTMLAttributes<HTMLDivElement>, "role" | "aria-valuenow" | "aria-valuemin" | "aria-valuemax"> {
  value: number;
  max: number;
  label?: ReactNode;
  showPercentage?: boolean;
  tone?: ProgressBarTone;
  size?: ProgressBarSize;
  ariaLabel?: string;
}

const toneStyles: Record<ProgressBarTone, { track: string; fill: string; label: string }> = {
  default: {
    track: "bg-surface-800",
    fill: "bg-brand-500",
    label: "text-surface-400",
  },
  onHero: {
    track: "bg-white/20 backdrop-blur-sm",
    fill: "bg-brand-400",
    label: "text-white/80",
  },
};

const sizeStyles: Record<ProgressBarSize, string> = {
  sm: "h-1.5",
  md: "h-2",
};

export function ProgressBar({
  value,
  max,
  label,
  showPercentage = false,
  tone = "default",
  size = "md",
  ariaLabel,
  className,
  ...rest
}: ProgressBarProps) {
  const safeMax = max > 0 ? max : 1;
  const clampedValue = Math.max(0, Math.min(value, safeMax));
  const percent = Math.round((clampedValue / safeMax) * 100);
  const styles = toneStyles[tone];

  return (
    <div data-comp="ProgressBar" className={cn("w-full", className)} {...rest}>
      {(label || showPercentage) && (
        <div className={cn("flex items-center justify-between gap-2 text-xs mb-1", styles.label)}>
          {label && <span>{label}</span>}
          {showPercentage && <span>{percent}%</span>}
        </div>
      )}
      <div className={cn("w-full rounded-full overflow-hidden", sizeStyles[size], styles.track)}>
        <div
          className={cn("h-full rounded-full transition-all duration-500", styles.fill)}
          style={{ width: `${percent}%` }}
          role="progressbar"
          aria-valuenow={clampedValue}
          aria-valuemin={0}
          aria-valuemax={safeMax}
          aria-label={ariaLabel}
        />
      </div>
    </div>
  );
}
