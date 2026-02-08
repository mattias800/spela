import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { Console, ConsoleGamesResponse } from "@/types/api";

export function useConsoles() {
  return useQuery({
    queryKey: ["consoles"],
    queryFn: () => api.get<Console[]>("/consoles"),
  });
}

export function useConsoleGames(consoleId: string) {
  return useQuery({
    queryKey: ["consoles", consoleId, "games"],
    queryFn: () => api.get<ConsoleGamesResponse>(`/consoles/${consoleId}/games`),
    enabled: !!consoleId,
  });
}
