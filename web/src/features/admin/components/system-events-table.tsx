import { ShieldAlert, X } from "lucide-react";
import { EmptyState, Section, TableRowSkeleton } from "@/components/ui";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/cn";
import { SystemEventBadge } from "./system-event-badge";
import { getSystemEventMeta } from "./system-event-meta";
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
  if (e.eventType.startsWith("federation_")) {
    const federationDetails = summarizeFederationDetails(e);
    if (federationDetails) return federationDetails;
  }
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

function summarizeFederationDetails(e: SystemEvent): string {
  const reason = humanizeReason(e.reason);
  if (!e.metadata) return reason;

  const peerName =
    typeof e.metadata.peerName === "string" ? e.metadata.peerName : "";
  const peerFingerprint =
    typeof e.metadata.peerFingerprint === "string"
      ? e.metadata.peerFingerprint
      : "";
  const peerBaseUrl =
    typeof e.metadata.peerBaseUrl === "string" ? e.metadata.peerBaseUrl : "";
  const requestId =
    typeof e.metadata.requestId === "string" ? e.metadata.requestId : "";

  const peer =
    peerName ||
    (peerFingerprint ? `Peer ${shortFingerprint(peerFingerprint)}` : "");
  const parts = [
    reason,
    peer,
    peerBaseUrl,
    requestId ? `Request ${requestId}` : "",
  ].filter(Boolean);

  if (parts.length > 0) {
    return parts.join(" · ");
  }
  return humanizeReason(e.reason);
}

function shortFingerprint(fp: string): string {
  return fp.length > 10 ? fp.slice(0, 10) : fp;
}

function humanizeReason(reason: string): string {
  const baseReason = reason.split(":")[0];
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
      break;
  }
  switch (baseReason) {
    case "pair_accepted":
      return "Pair accepted";
    case "invalid_invite":
      return "Invalid invite";
    case "invite_verification_failed":
      return "Invite verification failed";
    case "pair_callback_failed":
      return "Pair callback failed";
    case "pair_callback_unreachable":
      return "Peer unreachable during pairing";
    case "pair_fingerprint_mismatch":
      return "Pair fingerprint mismatch";
    case "peer_revoked":
      return "Peer revoked";
    case "diagnostic_unreachable":
      return "Diagnostic could not reach peer";
    case "diagnostic_failed":
      return "Diagnostic failed";
    case "unknown or inactive peer":
      return "Unknown or inactive peer";
    case "missing federation auth headers":
      return "Missing federation auth headers";
    case "stale or invalid timestamp":
      return "Stale or invalid timestamp";
    case "corrupt peer key":
      return "Corrupt peer key";
    case "invalid signature encoding":
      return "Invalid signature encoding";
    case "signature verification failed":
      return "Signature verification failed";
    default:
      return reason;
  }
}
