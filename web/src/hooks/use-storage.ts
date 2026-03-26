import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { StorageInfo, CompactSavesResult } from "@/types/api";

export function useStorage() {
  return useQuery({
    queryKey: ["user", "storage"],
    queryFn: () => api.get<StorageInfo>("/user/storage"),
  });
}

export function useCompactSaves() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => api.post<CompactSavesResult>("/user/saves/compact"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["user", "storage"] });
    },
  });
}
