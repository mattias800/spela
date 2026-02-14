import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/cn";

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description?: string;
  className?: string;
  action?: React.ReactNode;
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  className,
  action,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center py-16 text-center",
        className,
      )}
    >
      <div className="h-16 w-16 rounded-2xl bg-surface-800/50 flex items-center justify-center mb-4">
        <Icon className="h-8 w-8 text-surface-500" />
      </div>
      <h3 className="text-lg font-semibold text-surface-300">{title}</h3>
      {description && (
        <p className="mt-1.5 text-sm text-surface-500 max-w-sm">
          {description}
        </p>
      )}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
