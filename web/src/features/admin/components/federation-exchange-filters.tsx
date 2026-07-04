import { FilterPanel, Input, Select } from "@/components/ui";
import type { FederationPeer } from "@/generated/schemas";

export interface FederationExchangeFilterValues {
  peer: string;
  direction: string;
  operation: string;
  status: string;
  startedAfter: string;
  startedBefore: string;
}

interface FederationExchangeFiltersProps {
  filters: FederationExchangeFilterValues;
  peers: FederationPeer[] | undefined;
  onChange: (updates: Partial<FederationExchangeFilterValues>) => void;
  onClear: () => void;
}

const DIRECTION_OPTIONS = [
  { value: "", label: "Any direction" },
  { value: "outbound", label: "Outbound" },
  { value: "inbound", label: "Inbound" },
];

const STATUS_OPTIONS = [
  { value: "", label: "Any status" },
  { value: "ok", label: "OK" },
  { value: "error", label: "Error" },
  { value: "rejected", label: "Rejected" },
];

const OPERATION_OPTIONS = [
  { value: "", label: "Any operation" },
  { value: "pair", label: "Pair" },
  { value: "ping", label: "Ping" },
  { value: "stats_export", label: "Stats export" },
  { value: "stats_pull", label: "Stats pull" },
  { value: "catalog_export", label: "Catalog export" },
  { value: "catalog_pull", label: "Catalog pull" },
  { value: "achievements_export", label: "Achievements export" },
  { value: "achievements_pull", label: "Achievements pull" },
  { value: "presence_export", label: "Presence export" },
  { value: "presence_pull", label: "Presence pull" },
  { value: "download_serve", label: "Download serve" },
  { value: "download_fetch", label: "Download fetch" },
  { value: "download_relay", label: "Download relay" },
];

function shortFingerprint(value: string): string {
  return value.length > 12 ? `${value.slice(0, 12)}...` : value;
}

function humanize(value: string): string {
  const spaced = value.replace(/_/g, " ");
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

function includeCurrentOption(
  options: Array<{ value: string; label: string }>,
  value: string,
) {
  if (!value || options.some((option) => option.value === value))
    return options;
  return [...options, { value, label: humanize(value) }];
}

export function FederationExchangeFilters({
  filters,
  peers,
  onChange,
  onClear,
}: FederationExchangeFiltersProps) {
  const peerOptions = [
    { value: "", label: "Any server" },
    ...(peers ?? []).map((peer) => ({
      value: peer.fingerprint,
      label: peer.name || shortFingerprint(peer.fingerprint),
    })),
  ];
  if (
    filters.peer &&
    !peerOptions.some((option) => option.value === filters.peer)
  ) {
    peerOptions.push({
      value: filters.peer,
      label: shortFingerprint(filters.peer),
    });
  }

  const hasFilters = Object.values(filters).some(Boolean);

  return (
    <FilterPanel
      hasFilters={hasFilters}
      onClear={onClear}
      clearButtonTestId="clear-exchange-filters"
      data-comp="FederationExchangeFilters"
    >
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
        <Select
          id="exchange-peer"
          label="Server"
          value={filters.peer}
          options={peerOptions}
          onChange={(event) => onChange({ peer: event.target.value })}
          data-testid="exchange-peer-filter"
        />
        <Select
          id="exchange-direction"
          label="Direction"
          value={filters.direction}
          options={includeCurrentOption(DIRECTION_OPTIONS, filters.direction)}
          onChange={(event) => onChange({ direction: event.target.value })}
          data-testid="exchange-direction-filter"
        />
        <Select
          id="exchange-operation"
          label="Operation"
          value={filters.operation}
          options={includeCurrentOption(OPERATION_OPTIONS, filters.operation)}
          onChange={(event) => onChange({ operation: event.target.value })}
          data-testid="exchange-operation-filter"
        />
        <Select
          id="exchange-status"
          label="Status"
          value={filters.status}
          options={includeCurrentOption(STATUS_OPTIONS, filters.status)}
          onChange={(event) => onChange({ status: event.target.value })}
          data-testid="exchange-status-filter"
        />
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <Input
          id="exchange-started-after"
          label="Started after"
          type="datetime-local"
          value={filters.startedAfter}
          onChange={(event) => onChange({ startedAfter: event.target.value })}
          data-testid="exchange-started-after-filter"
        />
        <Input
          id="exchange-started-before"
          label="Started before"
          type="datetime-local"
          value={filters.startedBefore}
          onChange={(event) => onChange({ startedBefore: event.target.value })}
          data-testid="exchange-started-before-filter"
        />
      </div>
    </FilterPanel>
  );
}
