import { formatFileSize } from "@/lib/format";

interface StorageBarProps {
  usedBytes: number;
  quotaBytes: number;
}

function getBarColor(percentage: number): string {
  if (percentage >= 100) return "bg-danger-500";
  if (percentage >= 80) return "bg-warning-500";
  return "bg-brand-500";
}

function getTextColor(percentage: number): string {
  if (percentage >= 100) return "text-danger-400";
  if (percentage >= 80) return "text-warning-400";
  return "text-brand-400";
}

export function StorageBar({ usedBytes, quotaBytes }: StorageBarProps) {
  const percentage = quotaBytes > 0 ? (usedBytes / quotaBytes) * 100 : 0;
  const clampedWidth = Math.min(percentage, 100);

  return (
    <div>
      <div className="flex items-baseline justify-between mb-2">
        <p className="text-sm text-surface-300">
          <span className={getTextColor(percentage)}>
            {formatFileSize(usedBytes)}
          </span>{" "}
          of {formatFileSize(quotaBytes)} used
        </p>
        <p className={`text-sm font-medium ${getTextColor(percentage)}`}>
          {percentage.toFixed(1)}%
        </p>
      </div>
      <div className="h-3 w-full rounded-full bg-surface-800 overflow-hidden">
        <div
          className={`h-full rounded-full transition-all duration-500 ${getBarColor(percentage)}`}
          style={{ width: `${clampedWidth}%` }}
          role="progressbar"
          aria-valuenow={Math.round(percentage)}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label={`Storage usage: ${percentage.toFixed(1)}%`}
        />
      </div>
    </div>
  );
}
