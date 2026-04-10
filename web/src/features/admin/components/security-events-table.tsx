import { ShieldAlert } from "lucide-react";
import {
  EmptyState,
  Section,
  TableRowSkeleton,
} from "@/components/ui";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/cn";
import { SecurityEventBadge, getSecurityEventMeta } from "./security-event-badge";
import type { SecurityEvent } from "@/types/api";

interface SecurityEventsTableProps {
  events: SecurityEvent[] | undefined;
  isLoading: boolean;
  onRowClick: (event: SecurityEvent) => void;
}

export function SecurityEventsTable({
  events,
  isLoading,
  onRowClick,
}: SecurityEventsTableProps) {
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
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              Array.from({ length: 5 }, (_, i) => (
                <TableRowSkeleton key={i} columns={5} />
              ))
            ) : !events || events.length === 0 ? (
              <tr>
                <td colSpan={5}>
                  <EmptyState
                    icon={ShieldAlert}
                    title="No security events"
                    description="No events match the current filters. Try broadening your time range or clearing filters."
                  />
                </td>
              </tr>
            ) : (
              events.map((e) => {
                const meta = getSecurityEventMeta(e.eventType);
                const isAlert = meta?.severity === "alert";
                // handleRowActivate opens the detail modal, but only when the
                // click is not finishing a text-selection gesture. Admins
                // routinely copy IPs, usernames, and timestamps out of rows to
                // paste into tickets — swallowing selection on click would
                // make that flow painful.
                const handleRowActivate = () => {
                  if (window.getSelection()?.toString()) return;
                  onRowClick(e);
                };
                return (
                  <tr
                    key={e.id}
                    data-comp="SecurityEventRow"
                    onClick={handleRowActivate}
                    className={cn(
                      "border-b border-surface-800/50 cursor-pointer transition-colors hover:bg-surface-800/30",
                      "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-500",
                      isAlert && "bg-danger-500/5",
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
                      <SecurityEventBadge type={e.eventType} />
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

// summarizeDetails picks the most useful fragment from the event to show
// inline in the row, so admins can scan without opening every row.
function summarizeDetails(e: SecurityEvent): string {
  if (e.reason) return humanizeReason(e.reason);
  if (e.metadata) {
    if (typeof e.metadata.failedCount === "number") {
      return `${e.metadata.failedCount} failed attempt${e.metadata.failedCount === 1 ? "" : "s"}`;
    }
    if (typeof e.metadata.lockedUntil === "string") {
      return `Locked until ${formatDateTime(e.metadata.lockedUntil)}`;
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
