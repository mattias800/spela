import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useConsolePhotoCredits() {
  return useQuery({
    queryKey: ["console-photo-credits"],
    queryFn: () => unwrap(typedApi.GET("/api/console-photo-credits")),
    staleTime: Infinity,
  });
}
