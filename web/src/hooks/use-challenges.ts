import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import type {
  Challenge,
  ChallengesResponse,
  ChallengeFilters,
  ChallengeLeaderboardResponse,
  ChallengeLeaderboardEntry,
  ChallengeAttempt,
} from "@/types/api";

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
      return data as ChallengesResponse | undefined;
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
      return data as Challenge | undefined;
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
      return data as ChallengesResponse | undefined;
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
      return data as ChallengesResponse | undefined;
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
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/challenges/{id}/leaderboard", {
          params: { path: { id: challengeId }, query: { page, pageSize } },
        }),
      );
      return data as ChallengeLeaderboardResponse | undefined;
    },
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
      return data as ChallengeAttempt[] | undefined;
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
  return useMutation({
    mutationFn: async (challengeId: string) => {
      const data = await unwrap(
        typedApi.POST("/api/challenges/{id}/attempts/start", {
          params: { path: { id: challengeId } },
        }),
      );
      return data;
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
