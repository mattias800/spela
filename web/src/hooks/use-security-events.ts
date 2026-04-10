import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  SecurityEvent,
  SecurityEventType,
  SecurityEventsListFilters,
  SecurityEventsListResponse,
} from "@/types/api";

// buildSecurityEventsQuery serializes the filter object into a URLSearchParams
// instance. Empty/undefined fields are omitted so the query string stays
// clean and shareable.
function buildSecurityEventsQuery(filters: SecurityEventsListFilters): string {
  const params = new URLSearchParams();
  if (filters.page) params.set("page", String(filters.page));
  if (filters.pageSize) params.set("pageSize", String(filters.pageSize));
  if (filters.username) params.set("username", filters.username);
  if (filters.ip) params.set("ip", filters.ip);
  if (filters.since && filters.since !== "all") {
    params.set("since", filters.since);
  }
  if (filters.eventType?.length) {
    for (const t of filters.eventType) params.append("eventType", t);
  }
  return params.toString();
}

export function useSecurityEvents(filters: SecurityEventsListFilters) {
  const qs = buildSecurityEventsQuery(filters);
  // Each branch narrows to a literal type ApiGetPath accepts. A single
  // template literal like `/admin/security-events${qs ? `?${qs}` : ""}`
  // widens to `/admin/security-events${string}`, which would match paths
  // like `/admin/security-events-extra` and so is correctly rejected.
  const path = qs
    ? (`/admin/security-events?${qs}` as const)
    : ("/admin/security-events" as const);
  return useQuery({
    queryKey: ["admin", "security-events", filters],
    queryFn: () => api.get<SecurityEventsListResponse>(path),
    // Poll every 60s so admins see new events without manual refresh.
    // Events are also mirrored to slog, so we don't need sub-minute latency
    // here — the page is for investigation, not live monitoring.
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  });
}

export function useSecurityEvent(id: number | null) {
  return useQuery({
    queryKey: ["admin", "security-events", id],
    queryFn: () => {
      const path = `/admin/security-events/${id}` as const;
      return api.get<SecurityEvent>(path);
    },
    enabled: id !== null,
  });
}

export function useSecurityEventTypes() {
  return useQuery({
    queryKey: ["admin", "security-events", "types"],
    queryFn: () =>
      api.get<{ types: SecurityEventType[] }>(
        "/admin/security-events/types",
      ),
    staleTime: Infinity,
  });
}
