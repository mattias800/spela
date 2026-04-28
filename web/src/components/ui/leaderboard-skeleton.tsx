import { Skeleton } from "@/components/ui";

export function LeaderboardSkeleton() {
  return (
    <div data-comp="LeaderboardSkeleton" className="space-y-2">
      {Array.from({ length: 5 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-3 rounded-xl px-3 py-2.5 bg-surface-900/50"
        >
          <Skeleton className="h-7 w-7 rounded-full" />
          <Skeleton className="h-8 w-8 rounded-full" />
          <Skeleton className="h-4 w-28 flex-shrink-0" />
          <Skeleton className="h-3 flex-1" />
          <Skeleton className="h-4 w-14" />
        </div>
      ))}
    </div>
  );
}
