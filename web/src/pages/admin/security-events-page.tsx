import { useCallback, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { AlertTriangle } from "lucide-react";
import { PageLayout, SectionList } from "@/components/layout";
import { Button, Section } from "@/components/ui";
import { Pagination } from "@/components/pagination";
import {
  SecurityEventsFilters,
  type SinceOption,
  DEFAULT_SECURITY_EVENTS_SINCE,
} from "@/features/admin/components/security-events-filters";
import { SecurityEventsTable } from "@/features/admin/components/security-events-table";
import { SecurityEventDetailModal } from "@/features/admin/components/security-event-detail-modal";
import { useSecurityEvents } from "@/hooks/use-security-events";
import type { SecurityEvent, SecurityEventType } from "@/types/api";

const PAGE_SIZE = 50;
const DEFAULT_SINCE: SinceOption = DEFAULT_SECURITY_EVENTS_SINCE;

// AdminSecurityEventsPage shows an auditable log of authentication events.
// Filter state lives in the URL query string so views are bookmarkable and
// shareable between admins during an incident.
export function AdminSecurityEventsPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const eventTypes = useMemo(
    () => searchParams.getAll("eventType") as SecurityEventType[],
    [searchParams],
  );
  const username = searchParams.get("username") ?? "";
  const ip = searchParams.get("ip") ?? "";
  const since = (searchParams.get("since") ?? DEFAULT_SINCE) as SinceOption;
  const page = Number(searchParams.get("page") ?? "1") || 1;

  const [detailEvent, setDetailEvent] = useState<SecurityEvent | null>(null);

  const { data, isLoading, isError, error, refetch } = useSecurityEvents({
    page,
    pageSize: PAGE_SIZE,
    eventType: eventTypes,
    username: username || undefined,
    ip: ip || undefined,
    since,
  });

  // updateParams merges a partial set of filter changes back into the URL,
  // resetting the page counter whenever a filter other than `page` changes
  // so the user never lands on an empty page after a filter change.
  //
  // Deletion convention: pass `null`, `""`, or `[]` to remove a key from
  // the URL entirely. This is how callers clear a filter — e.g.
  // `{ username: null }` removes the `?username=...` param rather than
  // setting it to the literal string "null".
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
    <PageLayout title="Security Events" subtitle="Audit log of authentication events. Failed logins, lockouts, and token misuse are recorded here so you can investigate suspicious activity without tailing container logs.">
      <SectionList>
      <SecurityEventsFilters
        eventTypes={eventTypes}
        username={username}
        ip={ip}
        since={since}
        onEventTypesChange={(t) => updateParams({ eventType: t })}
        onUsernameChange={(v) => updateParams({ username: v })}
        onIpChange={(v) => updateParams({ ip: v })}
        onSinceChange={(v) =>
          updateParams({ since: v === DEFAULT_SINCE ? null : v })
        }
        onClear={() =>
          updateParams({
            eventType: [],
            username: null,
            ip: null,
            since: null,
          })
        }
      />

      {isError ? (
        <Section>
          <div
            data-testid="security-events-error"
            className="flex flex-col items-center gap-3 px-6 py-10 text-center"
          >
            <AlertTriangle className="h-10 w-10 text-danger-500" />
            <div>
              <p className="text-sm font-semibold text-surface-100">
                Failed to load security events
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

          <SecurityEventsTable
            events={data?.data}
            isLoading={isLoading}
            onRowClick={setDetailEvent}
          />

          <Pagination
            total={data?.total ?? 0}
            pageSize={PAGE_SIZE}
            currentPage={page}
            onPageChange={(p) => updateParams({ page: String(p) })}
          />
        </>
      )}

      <SecurityEventDetailModal
        event={detailEvent}
        onClose={() => setDetailEvent(null)}
        onPivotToUsername={(u) => {
          // Pivot semantics: drop every other active filter and open the
          // time window wide. During an incident investigation the admin
          // almost always wants "everything this user has ever done", not
          // "their actions in the current 24h slice", so we intentionally
          // forget the current `since` and event-type selections. Clearing
          // the complementary `ip` filter prevents a stale IP from a
          // previous search from silently AND-ing with the new view.
          updateParams({
            eventType: [],
            username: u,
            ip: null,
            since: "all",
          });
          setDetailEvent(null);
        }}
        onPivotToIp={(addr) => {
          // Same pivot semantics as onPivotToUsername — widen to all-time
          // and drop every other filter so the admin gets a clean "all
          // events from this IP" view.
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
