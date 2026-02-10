import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { UserPreferences } from "@/types/api";

export function useUserPreferences() {
  return useQuery({
    queryKey: ["user", "preferences"],
    queryFn: () => api.get<UserPreferences>("/user/preferences"),
  });
}

export function useUpdatePreferences() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: Partial<UserPreferences>) => {
      await api.put("/user/preferences", data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["user", "preferences"] });
    },
  });
}
