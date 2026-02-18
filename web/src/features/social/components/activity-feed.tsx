import { Link } from "react-router-dom";
import { Activity, ChevronRight } from "lucide-react";
import { Skeleton, EmptyState } from "@/components/ui";
import { ActivityEventItem } from "@/features/social/components/activity-event-item";
import { useActivityFeed, useActivityRealtime } from "@/hooks/use-social";

function ActivityFeedSkeleton({ count = 5 }: { count?: number }) {
  return (
    <div className="space-y-2">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="flex items-start gap-3 px-3 py-3">
          <Skeleton className="h-8 w-8 rounded-full flex-shrink-0" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-48" />
            <Skeleton className="h-3 w-20" />
          </div>
        </div>
      ))}
    </div>
  );
}

interface ActivityFeedProps {
  compact?: boolean;
  maxItems?: number;
  showHeader?: boolean;
}

export function ActivityFeed({
  compact = false,
  maxItems,
  showHeader = true,
}: ActivityFeedProps) {
  const { data, isLoading } = useActivityFeed(1, maxItems ?? 20);
  useActivityRealtime();

  const events = maxItems ? data?.data.slice(0, maxItems) : data?.data;

  return (
    <div data-testid="activity-feed">
      {showHeader && (
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2.5">
            <Activity className="h-5 w-5 text-brand-400" />
            <h2 className="text-lg font-bold text-surface-100">
              Recent Activity
            </h2>
          </div>
          {compact && (
            <Link
              to="/activity"
              className="flex items-center gap-1 text-sm text-surface-400 hover:text-brand-400 transition-colors"
            >
              View all
              <ChevronRight className="h-4 w-4" />
            </Link>
          )}
        </div>
      )}

      {isLoading && <ActivityFeedSkeleton count={compact ? 5 : 10} />}

      {!isLoading && (!events || events.length === 0) && (
        <EmptyState
          icon={Activity}
          title="No activity yet"
          description="Activity from you and other players will appear here."
          className="py-8"
        />
      )}

      {!isLoading && events && events.length > 0 && (
        <div className="space-y-1">
          {events.map((event) => (
            <ActivityEventItem key={event.id} event={event} compact={compact} />
          ))}
        </div>
      )}
    </div>
  );
}
