import { Search, X } from "lucide-react";
import { Button, FilterChip, Input } from "@/components/ui";
import { SECURITY_EVENT_META } from "./security-event-badge";
import type { SecurityEventType } from "@/types/api";

export type SinceOption = "1h" | "24h" | "7d" | "30d" | "all";

// DEFAULT_SECURITY_EVENTS_SINCE is the preset applied on first load. Both the
// page and the filters component reference this so "is the default active?"
// checks never drift.
export const DEFAULT_SECURITY_EVENTS_SINCE: SinceOption = "24h";

const SINCE_LABELS: Record<SinceOption, string> = {
  "1h": "Last hour",
  "24h": "Last 24 hours",
  "7d": "Last 7 days",
  "30d": "Last 30 days",
  all: "All time",
};

const SINCE_OPTIONS: SinceOption[] = ["1h", "24h", "7d", "30d", "all"];

// Event types rendered as filter chips, ordered alert → notice → info so
// the most urgent events are always at the front of the row.
const EVENT_TYPE_ORDER: SecurityEventType[] = [
  "account_locked",
  "revoked_token_used",
  "disabled_account_token",
  "token_user_missing",
  "login_failed",
  "login_locked",
  "login_blocked",
  "stale_token_version",
  "login_success",
];

interface SecurityEventsFiltersProps {
  eventTypes: SecurityEventType[];
  username: string;
  ip: string;
  since: SinceOption;
  onEventTypesChange: (types: SecurityEventType[]) => void;
  onUsernameChange: (v: string) => void;
  onIpChange: (v: string) => void;
  onSinceChange: (v: SinceOption) => void;
  onClear: () => void;
}

export function SecurityEventsFilters({
  eventTypes,
  username,
  ip,
  since,
  onEventTypesChange,
  onUsernameChange,
  onIpChange,
  onSinceChange,
  onClear,
}: SecurityEventsFiltersProps) {
  function toggleEventType(t: SecurityEventType) {
    if (eventTypes.includes(t)) {
      onEventTypesChange(eventTypes.filter((x) => x !== t));
    } else {
      onEventTypesChange([...eventTypes, t]);
    }
  }

  const hasFilters =
    eventTypes.length > 0 ||
    username !== "" ||
    ip !== "" ||
    since !== DEFAULT_SECURITY_EVENTS_SINCE;

  return (
    <div className="space-y-4 rounded-2xl border border-surface-800/50 bg-surface-900/50 p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-surface-400">
          Filters
        </h2>
        {hasFilters && (
          <Button variant="ghost" size="sm" onClick={onClear}>
            <X className="h-3.5 w-3.5 mr-1" />
            Clear
          </Button>
        )}
      </div>

      {/* Time range row — segmented buttons feel more like a toggle than a select. */}
      <div className="flex flex-wrap gap-2">
        {SINCE_OPTIONS.map((opt) => (
          <FilterChip
            key={opt}
            label={SINCE_LABELS[opt]}
            isSelected={since === opt}
            onClick={() => onSinceChange(opt)}
          />
        ))}
      </div>

      {/* Username + IP search row */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div className="relative">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-surface-500"
          />
          <Input
            type="text"
            placeholder="Username contains..."
            value={username}
            onChange={(e) => onUsernameChange(e.target.value)}
            className="pl-9"
            aria-label="Filter by username"
          />
        </div>
        <div className="relative">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-surface-500"
          />
          <Input
            type="text"
            placeholder="IP starts with..."
            value={ip}
            onChange={(e) => onIpChange(e.target.value)}
            className="pl-9"
            aria-label="Filter by IP"
          />
        </div>
      </div>

      {/* Event type chips */}
      <div>
        <p className="mb-2 text-xs font-medium text-surface-500">Event type</p>
        <div className="flex flex-wrap gap-2">
          {EVENT_TYPE_ORDER.map((t) => (
            <FilterChip
              key={t}
              label={SECURITY_EVENT_META[t].label}
              isSelected={eventTypes.includes(t)}
              onClick={() => toggleEventType(t)}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
