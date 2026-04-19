import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useStorage() {
  return useQuery({
    queryKey: ["user", "storage"],
    queryFn: () => unwrap(typedApi.GET("/api/user/storage")),
  });
}

export function useCompactSaves() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => unwrap(typedApi.POST("/api/user/saves/compact")),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["user", "storage"] });
    },
  });
}
