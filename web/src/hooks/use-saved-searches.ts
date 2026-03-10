import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { SavedSearch } from "@/types/api";

export function useSavedSearches() {
  return useQuery({
    queryKey: ["saved-searches"],
    queryFn: () => api.get<SavedSearch[]>("/user/saved-searches"),
  });
}

export function useCreateSavedSearch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { name: string; filters: Record<string, string | number> }) =>
      api.post<SavedSearch>("/user/saved-searches", data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["saved-searches"] });
    },
  });
}

export function useDeleteSavedSearch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      api.delete(`/user/saved-searches/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["saved-searches"] });
    },
  });
}
