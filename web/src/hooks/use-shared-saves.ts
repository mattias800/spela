import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { SharedSavesResponse } from "@/types/api";

export function useSharedSaves(
  gameId: string,
  page: number = 1,
  pageSize: number = 20,
) {
  return useQuery({
    queryKey: ["shared-saves", gameId, page, pageSize],
    queryFn: () =>
      api.get<SharedSavesResponse>(
        `/games/${gameId}/shared-saves?page=${page}&pageSize=${pageSize}`,
      ),
    enabled: !!gameId,
  });
}

export function useDeleteSharedSave() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      gameId,
      saveId,
    }: {
      gameId: string;
      saveId: string;
    }) => {
      await api.delete(`/games/${gameId}/shared-saves/${saveId}`);
    },
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["shared-saves", gameId] });
    },
  });
}
