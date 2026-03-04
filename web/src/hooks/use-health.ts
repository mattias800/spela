import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";

interface HealthResponse {
  status: string;
  version: string;
}

export function useHealth() {
  return useQuery({
    queryKey: ["health"],
    queryFn: () => api.get<HealthResponse>("/health"),
    staleTime: 5 * 60 * 1000,
  });
}
