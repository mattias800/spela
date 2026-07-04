import { useMemo } from "react";
import { Search } from "lucide-react";
import { FilterChip, FilterPanel, Input } from "@/components/ui";
import { SYSTEM_EVENT_META } from "./system-event-meta";
import type {
  SystemEventType,
  SystemEventCategoryCode,
  SystemEventTypeInfo,
} from "@/types/api";

export type SinceOption = "1h" | "24h" | "7d" | "30d" | "all";

export const DEFAULT_SYSTEM_EVENTS_SINCE: SinceOption = "24h";

const SINCE_LABELS: Record<SinceOption, string> = {
  "1h": "Last hour",
  "24h": "Last 24 hours",
  "7d": "Last 7 days",
  "30d": "Last 30 days",
  all: "All time",
};

const SINCE_OPTIONS: SinceOption[] = ["1h", "24h", "7d", "30d", "all"];

interface SystemEventsFiltersProps {
  eventTypes: SystemEventType[];
  category: SystemEventCategoryCode | null;
  username: string;
  ip: string;
  since: SinceOption;
  showDismissed: boolean;
  typeInfos: SystemEventTypeInfo[] | undefined;
  onEventTypesChange: (types: SystemEventType[]) => void;
  onCategoryChange: (category: SystemEventCategoryCode | null) => void;
  onUsernameChange: (v: string) => void;
  onIpChange: (v: string) => void;
  onSinceChange: (v: SinceOption) => void;
  onShowDismissedChange: (v: boolean) => void;
  onClear: () => void;
}

export function SystemEventsFilters({
  eventTypes,
  category,
  username,
  ip,
  since,
  showDismissed,
  typeInfos,
  onEventTypesChange,
  onCategoryChange,
  onUsernameChange,
  onIpChange,
  onSinceChange,
  onShowDismissedChange,
  onClear,
}: SystemEventsFiltersProps) {
  function toggleEventType(t: SystemEventType) {
    if (eventTypes.includes(t)) {
      onEventTypesChange(eventTypes.filter((x) => x !== t));
    } else {
      onEventTypesChange([...eventTypes, t]);
    }
  }

  const filteredEventTypes = useMemo(() => {
    if (!typeInfos) return Object.keys(SYSTEM_EVENT_META) as SystemEventType[];
    const filtered = category
      ? typeInfos.filter((ti) => ti.category === category)
      : typeInfos;
    return filtered.map((ti) => ti.type as SystemEventType);
  }, [typeInfos, category]);

  const hasFilters =
    eventTypes.length > 0 ||
    category !== null ||
    username !== "" ||
    ip !== "" ||
    showDismissed ||
    since !== DEFAULT_SYSTEM_EVENTS_SINCE;

  return (
    <FilterPanel
      data-comp="SystemEventsFilters"
      hasFilters={hasFilters}
      onClear={onClear}
    >
      {/* Category row */}
      <div>
        <p className="mb-2 text-xs font-medium text-surface-500">Category</p>
        <div className="flex flex-wrap gap-2">
          <FilterChip
            label="All"
            isSelected={category === null}
            onClick={() => onCategoryChange(null)}
          />
          <FilterChip
            label="Security"
            isSelected={category === "security"}
            onClick={() => onCategoryChange("security")}
          />
          <FilterChip
            label="Operational"
            isSelected={category === "operational"}
            onClick={() => onCategoryChange("operational")}
          />
        </div>
      </div>

      {/* Time range row */}
      <div className="flex flex-wrap items-center gap-2">
        {SINCE_OPTIONS.map((opt) => (
          <FilterChip
            key={opt}
            label={SINCE_LABELS[opt]}
            isSelected={since === opt}
            onClick={() => onSinceChange(opt)}
          />
        ))}
        <label className="ml-4 flex items-center gap-2 text-sm text-surface-400 cursor-pointer">
          <input
            type="checkbox"
            checked={showDismissed}
            onChange={(e) => onShowDismissedChange(e.target.checked)}
            className="rounded border-surface-600 bg-surface-800 text-brand-500 focus:ring-brand-500"
          />
          Show dismissed
        </label>
      </div>

      {/* Username + IP search row */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Input
          type="text"
          placeholder="Username contains..."
          value={username}
          onChange={(e) => onUsernameChange(e.target.value)}
          leftIcon={<Search aria-hidden="true" className="h-4 w-4" />}
          aria-label="Filter by username"
        />
        <Input
          type="text"
          placeholder="IP starts with..."
          value={ip}
          onChange={(e) => onIpChange(e.target.value)}
          leftIcon={<Search aria-hidden="true" className="h-4 w-4" />}
          aria-label="Filter by IP"
        />
      </div>

      {/* Event type chips — filtered by selected category */}
      <div>
        <p className="mb-2 text-xs font-medium text-surface-500">Event type</p>
        <div className="flex flex-wrap gap-2">
          {filteredEventTypes.map((t) => (
            <FilterChip
              key={t}
              label={SYSTEM_EVENT_META[t]?.label ?? t}
              isSelected={eventTypes.includes(t)}
              onClick={() => toggleEventType(t)}
            />
          ))}
        </div>
      </div>
    </FilterPanel>
  );
}
