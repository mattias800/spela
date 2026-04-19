import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useSavedSearches() {
  return useQuery({
    queryKey: ["saved-searches"],
    queryFn: () => unwrap(typedApi.GET("/api/user/saved-searches")),
  });
}

export function useCreateSavedSearch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: {
      name: string;
      filters: Record<string, string | number>;
    }) =>
      unwrap(
        typedApi.POST("/api/user/saved-searches", { body: data }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["saved-searches"] });
    },
  });
}

export function useDeleteSavedSearch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      unwrap(
        typedApi.DELETE("/api/user/saved-searches/{id}", {
          params: { path: { id } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["saved-searches"] });
    },
  });
}
