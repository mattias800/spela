import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  SystemEvent,
  SystemEventCategory,
  SystemEventTypeInfo,
  SystemEventsListFilters,
  SystemEventsListResponse,
} from "@/types/api";

function buildSystemEventsQuery(filters: SystemEventsListFilters): string {
  const params = new URLSearchParams();
  if (filters.page) params.set("page", String(filters.page));
  if (filters.pageSize) params.set("pageSize", String(filters.pageSize));
  if (filters.username) params.set("username", filters.username);
  if (filters.ip) params.set("ip", filters.ip);
  if (filters.category) params.set("category", filters.category);
  if (filters.dismissed) params.set("dismissed", "true");
  if (filters.since && filters.since !== "all") {
    params.set("since", filters.since);
  }
  if (filters.eventType?.length) {
    for (const t of filters.eventType) params.append("eventType", t);
  }
  return params.toString();
}

export function useSystemEvents(filters: SystemEventsListFilters) {
  const qs = buildSystemEventsQuery(filters);
  const path = qs
    ? (`/admin/system-events?${qs}` as const)
    : ("/admin/system-events" as const);
  return useQuery({
    queryKey: ["admin", "system-events", filters],
    queryFn: () => api.get<SystemEventsListResponse>(path),
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  });
}

export function useSystemEvent(id: number | null) {
  return useQuery({
    queryKey: ["admin", "system-events", id],
    queryFn: () => {
      const path = `/admin/system-events/${id}` as const;
      return api.get<SystemEvent>(path);
    },
    enabled: id !== null,
  });
}

export function useSystemEventTypes() {
  return useQuery({
    queryKey: ["admin", "system-events", "types"],
    queryFn: () =>
      api.get<{ types: SystemEventTypeInfo[] }>(
        "/admin/system-events/types",
      ),
    staleTime: Infinity,
  });
}

export function useSystemEventCategories() {
  return useQuery({
    queryKey: ["admin", "system-events", "categories"],
    queryFn: () =>
      api.get<SystemEventCategory[]>(
        "/admin/system-events/categories",
      ),
    staleTime: Infinity,
  });
}

export function useDismissSystemEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) =>
      api.put(`/admin/system-events/${id}/dismiss` as const, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "system-events"] });
    },
  });
}
