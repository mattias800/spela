import { useCallback, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { AlertTriangle } from "lucide-react";
import { PageLayout, SectionList } from "@/components/layout";
import { Button, Section } from "@/components/ui";
import { Pagination } from "@/components/pagination";
import {
  SystemEventsFilters,
  type SinceOption,
  DEFAULT_SYSTEM_EVENTS_SINCE,
} from "@/features/admin/components/system-events-filters";
import { SystemEventsTable } from "@/features/admin/components/system-events-table";
import { SystemEventDetailModal } from "@/features/admin/components/system-event-detail-modal";
import {
  useSystemEvents,
  useSystemEventTypes,
  useDismissSystemEvent,
} from "@/hooks/use-system-events";
import type {
  SystemEvent,
  SystemEventType,
  SystemEventCategoryCode,
} from "@/types/api";

const PAGE_SIZE = 50;
const DEFAULT_SINCE: SinceOption = DEFAULT_SYSTEM_EVENTS_SINCE;

export function AdminSystemEventsPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const eventTypes = useMemo(
    () => searchParams.getAll("eventType") as SystemEventType[],
    [searchParams],
  );
  const category = (searchParams.get("category") as SystemEventCategoryCode) ?? null;
  const username = searchParams.get("username") ?? "";
  const ip = searchParams.get("ip") ?? "";
  const since = (searchParams.get("since") ?? DEFAULT_SINCE) as SinceOption;
  const showDismissed = searchParams.get("dismissed") === "true";
  const page = Number(searchParams.get("page") ?? "1") || 1;

  const [detailEvent, setDetailEvent] = useState<SystemEvent | null>(null);

  const { data, isLoading, isError, error, refetch } = useSystemEvents({
    page,
    pageSize: PAGE_SIZE,
    eventType: eventTypes,
    category: category || undefined,
    username: username || undefined,
    ip: ip || undefined,
    since,
    dismissed: showDismissed,
  });

  const { data: typesData } = useSystemEventTypes();
  const dismissMutation = useDismissSystemEvent();

  const updateParams = useCallback(
    (updates: Record<string, string | string[] | null>) => {
      const next = new URLSearchParams(searchParams);
      const isFilterChange = Object.keys(updates).some((k) => k !== "page");
      for (const [key, value] of Object.entries(updates)) {
        next.delete(key);
        if (value === null || value === "" || (Array.isArray(value) && value.length === 0)) {
          continue;
        }
        if (Array.isArray(value)) {
          for (const v of value) next.append(key, v);
        } else {
          next.set(key, value);
        }
      }
      if (isFilterChange) next.delete("page");
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  return (
    <PageLayout
      title="System Events"
      subtitle="Audit log of system events. Security events track authentication activity; operational events surface infrastructure issues like scraper failures and missing ROMs."
    >
      <SectionList>
      <SystemEventsFilters
        eventTypes={eventTypes}
        category={category}
        username={username}
        ip={ip}
        since={since}
        showDismissed={showDismissed}
        typeInfos={typesData?.types}
        onEventTypesChange={(t) => updateParams({ eventType: t })}
        onCategoryChange={(c) => updateParams({ category: c })}
        onUsernameChange={(v) => updateParams({ username: v })}
        onIpChange={(v) => updateParams({ ip: v })}
        onSinceChange={(v) =>
          updateParams({ since: v === DEFAULT_SINCE ? null : v })
        }
        onShowDismissedChange={(v) =>
          updateParams({ dismissed: v ? "true" : null })
        }
        onClear={() =>
          updateParams({
            eventType: [],
            category: null,
            username: null,
            ip: null,
            since: null,
            dismissed: null,
          })
        }
      />

      {isError ? (
        <Section>
          <div
            data-testid="system-events-error"
            className="flex flex-col items-center gap-3 px-6 py-10 text-center"
          >
            <AlertTriangle className="h-10 w-10 text-danger-500" />
            <div>
              <p className="text-sm font-semibold text-surface-100">
                Failed to load system events
              </p>
              <p className="mt-1 text-sm text-surface-400">
                {error instanceof Error
                  ? error.message
                  : "An unknown error occurred while fetching the audit log."}
              </p>
            </div>
            <Button variant="secondary" size="sm" onClick={() => refetch()}>
              Try again
            </Button>
          </div>
        </Section>
      ) : (
        <>
          {data && data.total > 0 && (
            <div className="flex items-center justify-between text-sm text-surface-400">
              <span>
                {data.total} event{data.total === 1 ? "" : "s"}
                {data.total > PAGE_SIZE && (
                  <>
                    {" "}
                    · Showing {(page - 1) * PAGE_SIZE + 1}–
                    {Math.min(page * PAGE_SIZE, data.total)}
                  </>
                )}
              </span>
            </div>
          )}

          <SystemEventsTable
            events={data?.data}
            isLoading={isLoading}
            onRowClick={setDetailEvent}
            onDismiss={(id) => dismissMutation.mutate(id)}
          />

          <Pagination
            total={data?.total ?? 0}
            pageSize={PAGE_SIZE}
            currentPage={page}
            onPageChange={(p) => updateParams({ page: String(p) })}
          />
        </>
      )}

      <SystemEventDetailModal
        event={detailEvent}
        onClose={() => setDetailEvent(null)}
        onPivotToUsername={(u) => {
          updateParams({
            eventType: [],
            username: u,
            ip: null,
            since: "all",
          });
          setDetailEvent(null);
        }}
        onPivotToIp={(addr) => {
          updateParams({
            eventType: [],
            username: null,
            ip: addr,
            since: "all",
          });
          setDetailEvent(null);
        }}
      />
    </SectionList>
    </PageLayout>
  );
}

export default AdminSystemEventsPage;
