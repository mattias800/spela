import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import type {
  ChallengeAttemptResponse,
  ChallengeResponse,
  PaginatedResponseChallengeResponse,
} from "@/generated/schemas";
import type {
  Challenge,
  ChallengesResponse,
  ChallengeFilters,
  ChallengeLeaderboardEntry,
  ChallengeAttempt,
} from "@/types/api";
import {
  asAttemptStatus,
  asChallengeDifficulty,
  asChallengeStatus,
  asChallengeType,
} from "@/types/view-model-narrowing";

// Map wire shapes (type/difficulty/status emitted as `string`) to the
// view-model shapes (literal unions). If the server ever emits a value
// outside the claimed union, the narrowing helper throws — loud, not silent.
function toChallenge(wire: ChallengeResponse): Challenge {
  return {
    ...wire,
    type: asChallengeType(wire.type),
    difficulty: asChallengeDifficulty(wire.difficulty),
    status: asChallengeStatus(wire.status),
  };
}

function toChallengesResponse(
  wire: PaginatedResponseChallengeResponse,
): ChallengesResponse {
  return {
    ...wire,
    data: wire.data?.map(toChallenge) ?? null,
  };
}

function toChallengeAttempt(wire: ChallengeAttemptResponse): ChallengeAttempt {
  return {
    ...wire,
    status: asAttemptStatus(wire.status),
  };
}

const sortMapping: Record<string, string> = {
  most_attempted: "popular",
  newest: "newest",
  ending_soon: "newest",
};

function buildChallengeQuery(filters: ChallengeFilters) {
  const q: Record<string, string | number | undefined> = {};
  if (filters.page) q.page = filters.page;
  if (filters.pageSize) q.pageSize = filters.pageSize;
  if (filters.gameId) q.gameId = filters.gameId;
  if (filters.consoleId) q.consoleId = filters.consoleId;
  if (filters.difficulty) q.difficulty = filters.difficulty;
  if (filters.type) q.type = filters.type;
  if (filters.status) q.status = filters.status;
  if (filters.sortBy) q.sort = sortMapping[filters.sortBy] ?? filters.sortBy;
  return q;
}

export function useChallenges(filters: ChallengeFilters = {}) {
  const query = buildChallengeQuery(filters);
  return useQuery({
    queryKey: ["challenges", filters],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/challenges", { params: { query } }),
      );
      return data && toChallengesResponse(data);
    },
  });
}

export function useChallenge(id: string) {
  return useQuery({
    queryKey: ["challenge", id],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/challenges/{id}", {
          params: { path: { id } },
        }),
      );
      return data && toChallenge(data);
    },
    enabled: !!id,
  });
}

export function useGameChallenges(
  gameId: string,
  page: number = 1,
  pageSize: number = 5,
) {
  return useQuery({
    queryKey: ["challenges", "game", gameId, page, pageSize],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/games/{id}/challenges", {
          params: { path: { id: gameId }, query: { page, pageSize } },
        }),
      );
      return data && toChallengesResponse(data);
    },
    enabled: !!gameId,
  });
}

export function useMyChallenges(page: number = 1, pageSize: number = 20) {
  return useQuery({
    queryKey: ["challenges", "mine", page, pageSize],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/user/challenges", {
          params: { query: { page, pageSize } },
        }),
      );
      return data && toChallengesResponse(data);
    },
  });
}

export const useUserChallenges = useMyChallenges;

export function useChallengeLeaderboard(
  challengeId: string,
  page: number = 1,
  pageSize: number = 50,
) {
  return useQuery({
    queryKey: ["challenge", challengeId, "leaderboard", page, pageSize],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/challenges/{id}/leaderboard", {
          params: { path: { id: challengeId }, query: { page, pageSize } },
        }),
      ),
    enabled: !!challengeId,
  });
}

export function useMyAttempts(challengeId: string) {
  return useQuery({
    queryKey: ["challenge", challengeId, "my-attempts"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/challenges/{id}/attempts/mine", {
          params: { path: { id: challengeId } },
        }),
      );
      return data?.map(toChallengeAttempt);
    },
    enabled: !!challengeId,
  });
}

export function useDeleteChallenge() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      unwrap(
        typedApi.DELETE("/api/challenges/{id}", {
          params: { path: { id } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["challenges"] });
    },
  });
}

export function useStartAttempt() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (challengeId: string) => {
      const data = await unwrap(
        typedApi.POST("/api/challenges/{id}/attempts/start", {
          params: { path: { id: challengeId } },
        }),
      );
      return data;
    },
    onSuccess: (_, challengeId) => {
      // Match useCompleteAttempt's invalidation set so the UI moves out
      // of the pre-attempt state immediately instead of waiting for the
      // next background refetch.
      queryClient.invalidateQueries({ queryKey: ["challenge", challengeId, "my-attempts"] });
      queryClient.invalidateQueries({ queryKey: ["challenge", challengeId] });
    },
  });
}

export function useCompleteAttempt() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      challengeId,
      attemptId,
    }: {
      challengeId: string;
      attemptId: string;
    }) => {
      const data = await unwrap(
        typedApi.POST("/api/challenges/{id}/attempts/{aid}/complete", {
          params: { path: { id: challengeId, aid: attemptId } },
        }),
      );
      return data;
    },
    onSuccess: (_, { challengeId }) => {
      queryClient.invalidateQueries({
        queryKey: ["challenge", challengeId, "leaderboard"],
      });
      queryClient.invalidateQueries({
        queryKey: ["challenge", challengeId, "my-attempts"],
      });
      queryClient.invalidateQueries({ queryKey: ["challenge", challengeId] });
    },
  });
}

export function useAbandonAttempt() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      challengeId,
      attemptId,
    }: {
      challengeId: string;
      attemptId: string;
    }) =>
      unwrap(
        typedApi.POST("/api/challenges/{id}/attempts/{aid}/abandon", {
          params: { path: { id: challengeId, aid: attemptId } },
        }),
      ),
    onSuccess: (_, { challengeId }) => {
      queryClient.invalidateQueries({ queryKey: ["challenge", challengeId, "my-attempts"] });
      queryClient.invalidateQueries({ queryKey: ["challenge", challengeId] });
    },
  });
}

export function useChallengeLeaderboardRealtime(challengeId: string) {
  const queryClient = useQueryClient();

  useWebSocketEvent(
    "challenge_leaderboard_update",
    (payload: { challengeId: string; entry: ChallengeLeaderboardEntry }) => {
      if (payload.challengeId !== challengeId) return;
      queryClient.invalidateQueries({
        queryKey: ["challenge", challengeId, "leaderboard"],
      });
      queryClient.invalidateQueries({
        queryKey: ["challenge", challengeId],
      });
    },
  );
}
