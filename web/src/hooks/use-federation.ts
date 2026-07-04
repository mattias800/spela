import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

// Poll federation data so health + activity stay live while the admin watches.
const POLL_MS = 10000;

export interface FederationExchangeFilters {
  peer?: string;
  direction?: string;
  operation?: string;
  status?: string;
  startedAfter?: string;
  startedBefore?: string;
  limit?: number;
}

export function useFederationPeers() {
  return useQuery({
    queryKey: ["admin", "federation", "peers"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/federation/peers")),
    refetchInterval: POLL_MS,
    refetchIntervalInBackground: false,
  });
}

export function useFederationExchanges(
  filters: FederationExchangeFilters = {},
) {
  const limit = filters.limit ?? 50;
  const query = {
    limit,
    ...(filters.peer ? { peer: filters.peer } : {}),
    ...(filters.direction ? { direction: filters.direction } : {}),
    ...(filters.operation ? { operation: filters.operation } : {}),
    ...(filters.status ? { status: filters.status } : {}),
    ...(filters.startedAfter ? { startedAfter: filters.startedAfter } : {}),
    ...(filters.startedBefore ? { startedBefore: filters.startedBefore } : {}),
  };

  return useQuery({
    queryKey: ["admin", "federation", "exchanges", query],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/admin/federation/exchanges", {
          params: { query },
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
      queryClient.invalidateQueries({
        queryKey: ["admin", "federation", "peers"],
      });
      queryClient.invalidateQueries({
        queryKey: ["admin", "federation", "exchanges"],
      });
    },
  });
}

// Issue a one-time pairing invite to hand to a friend's admin out-of-band.
// Does not change our peer list — the friend appears only after they accept
// and call our /pair callback (picked up by the 10s peer poll).
export function useIssueFederationInvite() {
  return useMutation({
    mutationFn: () => unwrap(typedApi.POST("/api/admin/federation/invite")),
  });
}

// Accept a friend's invite: verifies it, calls them back, and stores them as
// an active peer immediately — so refresh peers + activity on success.
export function useAcceptFederationInvite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { invite: string; name: string }) =>
      unwrap(typedApi.POST("/api/admin/federation/peers/accept", { body })),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["admin", "federation", "peers"],
      });
      queryClient.invalidateQueries({
        queryKey: ["admin", "federation", "exchanges"],
      });
    },
  });
}

// Revoke a peer. The server also drops the peer's cached stat/catalog
// snapshots, so its mesh contribution vanishes immediately.
export function useRevokeFederationPeer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (fingerprint: string) =>
      unwrap(
        typedApi.DELETE("/api/admin/federation/peers/{fingerprint}", {
          params: { path: { fingerprint } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["admin", "federation", "peers"],
      });
      queryClient.invalidateQueries({
        queryKey: ["admin", "federation", "exchanges"],
      });
    },
  });
}

// Set a peer's per-class share/consume policy (what we expose to / accept
// from them). Refresh peers so the table reflects the new policy.
export function useUpdateFederationPolicy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      fingerprint: string;
      sharePolicy: Record<string, boolean>;
      consumePolicy: Record<string, boolean>;
    }) =>
      unwrap(
        typedApi.PUT("/api/admin/federation/peers/{fingerprint}/policy", {
          params: { path: { fingerprint: vars.fingerprint } },
          body: {
            sharePolicy: vars.sharePolicy,
            consumePolicy: vars.consumePolicy,
          },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["admin", "federation", "peers"],
      });
    },
  });
}
