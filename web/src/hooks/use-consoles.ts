import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import type { Console, Game } from "@/types/api";

export function useConsoles() {
  return useQuery({
    queryKey: ["consoles"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/consoles"));
      return data as Console[] | undefined;
    },
  });
}

export function useConsoleGames(consoleId: string) {
  return useQuery({
    queryKey: ["consoles", consoleId, "games"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/consoles/{id}/games", {
          params: { path: { id: consoleId } },
        }),
      );
      return data as Game[] | undefined;
    },
    enabled: !!consoleId,
  });
}
