import { useState } from "react";
import { Activity } from "lucide-react";
import { Button, Skeleton, EmptyState } from "@/components/ui";
import { ActivityEventItem } from "@/components/social/activity-event-item";
import { useActivityFeed, useActivityRealtime } from "@/hooks/use-social";
import { OnlineUsers } from "@/components/social/online-users";

function ActivityPageSkeleton() {
  return (
    <div className="space-y-2">
      {Array.from({ length: 10 }, (_, i) => (
        <div key={i} className="flex items-start gap-3 px-3 py-3">
          <Skeleton className="h-8 w-8 rounded-full flex-shrink-0" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-56" />
            <Skeleton className="h-3 w-20" />
          </div>
          <Skeleton className="h-12 w-9 rounded-lg flex-shrink-0" />
        </div>
      ))}
    </div>
  );
}

export function ActivityPage() {
  const [page, setPage] = useState(1);
  const pageSize = 20;
  const { data, isLoading } = useActivityFeed(page, pageSize);
  useActivityRealtime();

  const hasMore = data ? page * pageSize < data.total : false;

  return (
    <div className="max-w-5xl">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-surface-100 flex items-center gap-3">
          <Activity className="h-8 w-8 text-brand-400" />
          Activity
        </h1>
        <p className="mt-1 text-surface-400">
          See what everyone has been up to.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Main feed */}
        <div className="lg:col-span-2">
          {isLoading && page === 1 && <ActivityPageSkeleton />}

          {!isLoading && (!data?.data || data.data.length === 0) && (
            <EmptyState
              icon={Activity}
              title="No activity yet"
              description="Activity from you and other players will appear here."
            />
          )}

          {data?.data && data.data.length > 0 && (
            <div className="space-y-1">
              {data.data.map((event) => (
                <ActivityEventItem key={event.id} event={event} />
              ))}
            </div>
          )}

          {/* Pagination */}
          {data && data.data.length > 0 && (
            <div className="flex items-center justify-center gap-3 mt-8">
              <Button
                variant="secondary"
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={page === 1}
              >
                Previous
              </Button>
              <span className="text-sm text-surface-400">
                Page {page} of {Math.ceil(data.total / pageSize)}
              </span>
              <Button
                variant="secondary"
                onClick={() => setPage((p) => p + 1)}
                disabled={!hasMore}
              >
                Next
              </Button>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div className="lg:col-span-1">
          <div className="rounded-2xl bg-surface-900/50 p-5">
            <OnlineUsers />
          </div>
        </div>
      </div>
    </div>
  );
}
