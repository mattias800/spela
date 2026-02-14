import { cn } from "@/lib/cn";

interface SkeletonProps {
  className?: string;
}

export function Skeleton({ className }: SkeletonProps) {
  return (
    <div
      className={cn(
        "animate-pulse rounded-lg bg-surface-800/60",
        className,
      )}
    />
  );
}

export function GameCardSkeleton() {
  return (
    <div className="space-y-3">
      <Skeleton className="aspect-[3/4] w-full rounded-2xl" />
      <div className="space-y-2 px-1">
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-3 w-1/2" />
      </div>
    </div>
  );
}

export function ConsoleCardSkeleton() {
  return (
    <div className="space-y-3">
      <Skeleton className="aspect-[16/10] w-full rounded-2xl" />
      <div className="space-y-2 px-1">
        <Skeleton className="h-5 w-2/3" />
        <Skeleton className="h-3 w-1/3" />
      </div>
    </div>
  );
}

export function GameDetailSkeleton() {
  return (
    <div className="space-y-8">
      <div className="flex flex-col items-center gap-6 md:flex-row md:items-start md:gap-8">
        <Skeleton className="w-48 md:w-64 aspect-[3/4] rounded-2xl flex-shrink-0" />
        <div className="w-full flex-1 space-y-4 pt-2">
          <Skeleton className="h-8 w-2/3" />
          <Skeleton className="h-4 w-1/3" />
          <Skeleton className="h-4 w-1/4" />
          <div className="pt-4 space-y-2">
            <Skeleton className="h-3 w-full" />
            <Skeleton className="h-3 w-full" />
            <Skeleton className="h-3 w-4/5" />
          </div>
        </div>
      </div>
    </div>
  );
}

export function TableRowSkeleton({ columns = 5 }: { columns?: number }) {
  return (
    <tr>
      {Array.from({ length: columns }, (_, i) => (
        <td key={i} className="px-4 py-3">
          <Skeleton className="h-4 w-full" />
        </td>
      ))}
    </tr>
  );
}
