import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useCheatStats() {
  return useQuery({
    queryKey: ["admin", "cheat-stats"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/cheats/stats")),
  });
}

export function useImportCheats() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => unwrap(typedApi.POST("/api/admin/cheats/import")),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "cheat-stats"] });
    },
  });
}

export function useGameCheats(gameId: string) {
  return useQuery({
    queryKey: ["game", gameId, "cheats"],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/games/{id}/cheats", {
          params: { path: { id: gameId } },
        }),
      ),
    enabled: !!gameId,
  });
}
