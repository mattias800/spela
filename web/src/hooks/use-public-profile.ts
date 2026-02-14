import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { PublicProfile } from "@/types/api";

export function usePublicProfile(userId: string) {
  return useQuery({
    queryKey: ["users", userId, "profile"],
    queryFn: () => api.get<PublicProfile>(`/users/${userId}/profile`),
    enabled: !!userId,
  });
}
