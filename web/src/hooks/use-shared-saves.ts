import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useSharedSaves(
  gameId: string,
  page: number = 1,
  pageSize: number = 20,
) {
  return useQuery({
    queryKey: ["shared-saves", gameId, page, pageSize],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/games/{id}/shared-saves", {
          params: { path: { id: gameId }, query: { page, pageSize } },
        }),
      ),
    enabled: !!gameId,
  });
}

export function useCreateSessionFromSharedSave() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ gameId, saveId }: { gameId: string; saveId: string }) =>
      unwrap(
        typedApi.POST("/api/games/{id}/sessions/from-shared-save/{saveId}", {
          params: { path: { id: gameId, saveId } },
        }),
      ),
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["game-sessions", gameId] });
      queryClient.invalidateQueries({ queryKey: ["shared-saves", gameId] });
    },
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
      await unwrap(
        typedApi.DELETE("/api/games/{id}/shared-saves/{saveId}", {
          params: { path: { id: gameId, saveId } },
        }),
      );
    },
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["shared-saves", gameId] });
    },
  });
}
