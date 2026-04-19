import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useHealth() {
  return useQuery({
    queryKey: ["health"],
    queryFn: () => unwrap(typedApi.GET("/api/health")),
    staleTime: 5 * 60 * 1000,
  });
}
