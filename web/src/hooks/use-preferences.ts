import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import type { UpdatePreferencesRequest } from "@/generated/schemas";
import type { UserPreferences } from "@/types/api";

export function useUserPreferences() {
  return useQuery({
    queryKey: ["user", "preferences"],
    queryFn: () => unwrap(typedApi.GET("/api/user/preferences")),
  });
}

export function useUpdatePreferences() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: UpdatePreferencesRequest) => {
      await unwrap(
        typedApi.PUT("/api/user/preferences", {
          body: data,
        }),
      );
    },
    onMutate: async (newData: UpdatePreferencesRequest) => {
      await queryClient.cancelQueries({ queryKey: ["user", "preferences"] });
      const previousPreferences = queryClient.getQueryData<UserPreferences>([
        "user",
        "preferences",
      ]);
      queryClient.setQueryData<UserPreferences>(
        ["user", "preferences"],
        (old) => (old ? { ...old, ...newData } : undefined),
      );
      return { previousPreferences };
    },
    onError: (_err, _newData, context) => {
      if (context?.previousPreferences) {
        queryClient.setQueryData(
          ["user", "preferences"],
          context.previousPreferences,
        );
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["user", "preferences"] });
    },
  });
}
