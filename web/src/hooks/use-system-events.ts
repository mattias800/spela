import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import type { SystemEventsListFilters } from "@/types/api";

export function useSystemEvents(filters: SystemEventsListFilters) {
  return useQuery({
    queryKey: ["admin", "system-events", filters],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/admin/system-events", {
          params: {
            query: {
              page: filters.page,
              pageSize: filters.pageSize,
              username: filters.username,
              ip: filters.ip,
              category: filters.category,
              dismissed: filters.dismissed ? "true" : undefined,
              since:
                filters.since && filters.since !== "all"
                  ? filters.since
                  : undefined,
              eventType: filters.eventType,
            },
          },
        }),
      ),
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  });
}

export function useSystemEvent(id: number | null) {
  return useQuery({
    queryKey: ["admin", "system-events", id],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/admin/system-events/{id}", {
          params: { path: { id: String(id) } },
        }),
      ),
    enabled: id !== null,
  });
}

export function useSystemEventTypes() {
  return useQuery({
    queryKey: ["admin", "system-events", "types"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/system-events/types")),
    staleTime: Infinity,
  });
}

export function useSystemEventCategories() {
  return useQuery({
    queryKey: ["admin", "system-events", "categories"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/system-events/categories")),
    staleTime: Infinity,
  });
}

export function useDismissSystemEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) =>
      unwrap(
        typedApi.PUT("/api/admin/system-events/{id}/dismiss", {
          params: { path: { id: String(id) } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "system-events"] });
    },
  });
}
