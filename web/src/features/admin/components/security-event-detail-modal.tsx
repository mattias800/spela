import { Modal } from "@/components/ui";
import { cn } from "@/lib/cn";
import { formatDateTime } from "@/lib/format";
import {
  SecurityEventBadge,
  SECURITY_EVENT_META,
} from "./security-event-badge";
import type { SecurityEvent } from "@/types/api";

interface SecurityEventDetailModalProps {
  event: SecurityEvent | null;
  onClose: () => void;
}

export function SecurityEventDetailModal({
  event,
  onClose,
}: SecurityEventDetailModalProps) {
  const title = event
    ? (SECURITY_EVENT_META[event.eventType]?.label ?? "Security event")
    : "Security event";

  return (
    <Modal open={event !== null} onClose={onClose} title={title} size="lg">
      {event && (
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <SecurityEventBadge type={event.eventType} />
            {event.reason && (
              <span className="text-xs text-surface-400">
                reason: <span className="text-surface-300">{event.reason}</span>
              </span>
            )}
          </div>

          <DetailGrid>
            <DetailRow
              label="Time"
              value={formatDateTime(event.createdAt)}
              secondary={event.createdAt}
            />
            <DetailRow label="Event type" value={event.eventType} mono />
            <DetailRow label="Username" value={event.username ?? "—"} />
            <DetailRow
              label="User ID"
              value={event.userId?.toString() ?? "—"}
            />
            <DetailRow label="IP address" value={event.ip ?? "—"} mono />
            <DetailRow label="Request path" value={event.path ?? "—"} mono />
          </DetailGrid>

          {event.metadata && Object.keys(event.metadata).length > 0 && (
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-surface-400">
                Metadata
              </p>
              <pre className="max-h-64 overflow-auto rounded-xl border border-surface-800/50 bg-surface-950 px-4 py-3 text-xs text-surface-300">
                {JSON.stringify(event.metadata, null, 2)}
              </pre>
            </div>
          )}

          {event.metadataRaw && (
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-warning-500">
                Metadata (raw — failed to parse)
              </p>
              <pre className="max-h-64 overflow-auto rounded-xl border border-warning-500/40 bg-surface-950 px-4 py-3 text-xs text-surface-300">
                {event.metadataRaw}
              </pre>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}

function DetailGrid({ children }: { children: React.ReactNode }) {
  return <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">{children}</dl>;
}

interface DetailRowProps {
  label: string;
  value: string;
  mono?: boolean;
  secondary?: string;
}

function DetailRow({ label, value, mono, secondary }: DetailRowProps) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wider text-surface-500">
        {label}
      </dt>
      <dd className={cn("text-sm text-surface-200", mono && "font-mono tabular-nums")}>
        {value}
      </dd>
      {secondary && (
        <dd className="text-[11px] text-surface-500 font-mono">{secondary}</dd>
      )}
    </div>
  );
}
