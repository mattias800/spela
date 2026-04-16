import { ShieldAlert, X } from "lucide-react";
import {
  EmptyState,
  Section,
  TableRowSkeleton,
} from "@/components/ui";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/cn";
import { SystemEventBadge, getSystemEventMeta } from "./system-event-badge";
import type { SystemEvent } from "@/types/api";

interface SystemEventsTableProps {
  events: SystemEvent[] | undefined;
  isLoading: boolean;
  onRowClick: (event: SystemEvent) => void;
  onDismiss: (id: number) => void;
}

export function SystemEventsTable({
  events,
  isLoading,
  onRowClick,
  onDismiss,
}: SystemEventsTableProps) {
  return (
    <Section>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-surface-800">
              <th
                scope="col"
                className="text-left px-5 py-3 text-xs font-semibold uppercase tracking-wider text-surface-400"
              >
                Time
              </th>
              <th
                scope="col"
                className="text-left px-5 py-3 text-xs font-semibold uppercase tracking-wider text-surface-400"
              >
                Event
              </th>
              <th
                scope="col"
                className="text-left px-5 py-3 text-xs font-semibold uppercase tracking-wider text-surface-400"
              >
                Username
              </th>
              <th
                scope="col"
                className="text-left px-5 py-3 text-xs font-semibold uppercase tracking-wider text-surface-400"
              >
                IP
              </th>
              <th
                scope="col"
                className="text-left px-5 py-3 text-xs font-semibold uppercase tracking-wider text-surface-400"
              >
                Details
              </th>
              <th scope="col" className="w-12" />
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              Array.from({ length: 5 }, (_, i) => (
                <TableRowSkeleton key={i} columns={6} />
              ))
            ) : !events || events.length === 0 ? (
              <tr>
                <td colSpan={6}>
                  <EmptyState
                    icon={ShieldAlert}
                    title="No system events"
                    description="No events match the current filters. Try broadening your time range or clearing filters."
                  />
                </td>
              </tr>
            ) : (
              events.map((e) => {
                const meta = getSystemEventMeta(e.eventType);
                const isAlert = meta?.severity === "alert";
                const handleRowActivate = () => {
                  if (window.getSelection()?.toString()) return;
                  onRowClick(e);
                };
                return (
                  <tr
                    key={e.id}
                    data-comp="SystemEventRow"
                    onClick={handleRowActivate}
                    className={cn(
                      "border-b border-surface-800/50 cursor-pointer transition-colors hover:bg-surface-800/30",
                      "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-500",
                      isAlert && "bg-danger-500/5",
                      e.dismissedAt && "opacity-50",
                    )}
                    tabIndex={0}
                    onKeyDown={(ev) => {
                      if (ev.key === "Enter" || ev.key === " ") {
                        ev.preventDefault();
                        onRowClick(e);
                      }
                    }}
                  >
                    <td
                      className="px-5 py-3 text-sm text-surface-300 whitespace-nowrap tabular-nums"
                      title={e.createdAt}
                    >
                      {formatDateTime(e.createdAt)}
                    </td>
                    <td className="px-5 py-3">
                      <SystemEventBadge type={e.eventType} />
                    </td>
                    <td className="px-5 py-3 text-sm text-surface-200">
                      {e.username || (
                        <span className="text-surface-500">—</span>
                      )}
                    </td>
                    <td className="px-5 py-3 text-sm text-surface-400 font-mono tabular-nums">
                      {e.ip || <span className="text-surface-500">—</span>}
                    </td>
                    <td className="px-5 py-3 text-sm text-surface-400">
                      {summarizeDetails(e)}
                    </td>
                    <td className="px-5 py-3 text-right">
                      {!e.dismissedAt ? (
                        <button
                          data-testid={`dismiss-event-${e.id}`}
                          onClick={(ev) => {
                            ev.stopPropagation();
                            onDismiss(e.id);
                          }}
                          className="text-surface-500 hover:text-surface-300 transition-colors"
                          title="Dismiss"
                        >
                          <X className="h-4 w-4" />
                        </button>
                      ) : (
                        <span className="text-xs text-surface-600">
                          Dismissed
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </Section>
  );
}

function summarizeDetails(e: SystemEvent): string {
  if (e.reason) return humanizeReason(e.reason);
  if (e.metadata) {
    if (typeof e.metadata.failedCount === "number") {
      return `${e.metadata.failedCount} failed attempt${e.metadata.failedCount === 1 ? "" : "s"}`;
    }
    if (typeof e.metadata.lockedUntil === "string") {
      return `Locked until ${formatDateTime(e.metadata.lockedUntil)}`;
    }
    if (typeof e.metadata.consecutiveFailures === "number") {
      return `${e.metadata.consecutiveFailures} consecutive failures`;
    }
    if (typeof e.metadata.service === "string") {
      return `Service: ${e.metadata.service as string}`;
    }
    if (typeof e.metadata.gameTitle === "string") {
      return e.metadata.gameTitle as string;
    }
  }
  if (e.path) return e.path;
  return "";
}

function humanizeReason(reason: string): string {
  switch (reason) {
    case "bad_password":
      return "Bad password";
    case "unknown_user":
      return "Unknown user";
    case "disabled":
      return "Disabled account";
    case "pending_approval":
      return "Pending approval";
    default:
      return reason;
  }
}
