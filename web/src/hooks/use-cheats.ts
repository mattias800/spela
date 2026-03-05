import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";

interface CheatStats {
  totalCheats: number;
  gamesWithCheats: number;
  totalGames: number;
  verifiedGames: number;
}

interface ImportResult {
  totalGames: number;
  cheatsImported: number;
  gamesImported: number;
  skipped: number;
  failed: number;
}

interface CheatCode {
  id: number;
  index: number;
  description: string;
  code: string;
}

export function useCheatStats() {
  return useQuery({
    queryKey: ["admin", "cheat-stats"],
    queryFn: () => api.get<CheatStats>("/admin/cheats/stats"),
  });
}

export function useImportCheats() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<ImportResult>("/admin/cheats/import"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "cheat-stats"] });
    },
  });
}

export function useGameCheats(gameId: string) {
  return useQuery({
    queryKey: ["game", gameId, "cheats"],
    queryFn: () => api.get<CheatCode[]>(`/games/${gameId}/cheats`),
    enabled: !!gameId,
  });
}
