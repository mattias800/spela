import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

// Poll federation data so health + activity stay live while the admin watches.
const POLL_MS = 10000;

export function useFederationPeers() {
  return useQuery({
    queryKey: ["admin", "federation", "peers"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/federation/peers")),
    refetchInterval: POLL_MS,
    refetchIntervalInBackground: false,
  });
}

export function useFederationExchanges(limit = 50) {
  return useQuery({
    queryKey: ["admin", "federation", "exchanges", limit],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/admin/federation/exchanges", {
          params: { query: { limit } },
        }),
      ),
    refetchInterval: POLL_MS,
    refetchIntervalInBackground: false,
  });
}

export function useTestFederationPeer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (fingerprint: string) =>
      unwrap(
        typedApi.POST("/api/admin/federation/peers/{fingerprint}/test", {
          params: { path: { fingerprint } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "federation", "peers"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "federation", "exchanges"] });
    },
  });
}
