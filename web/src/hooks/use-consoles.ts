import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useConsoles() {
  return useQuery({
    queryKey: ["consoles"],
    queryFn: () => unwrap(typedApi.GET("/api/consoles")),
  });
}

export function useConsoleGames(consoleId: string) {
  return useQuery({
    queryKey: ["consoles", consoleId, "games"],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/consoles/{id}/games", {
          params: { path: { id: consoleId } },
        }),
      ),
    enabled: !!consoleId,
  });
}
