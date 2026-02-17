import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import type {
  Challenge,
  ChallengesResponse,
  ChallengeFilters,
  ChallengeLeaderboardResponse,
  ChallengeLeaderboardEntry,
  ChallengeAttempt,
  StartAttemptResponse,
  CompleteAttemptResponse,
} from "@/types/api";

const sortMapping: Record<string, string> = {
  most_attempted: "popular",
  newest: "newest",
  ending_soon: "newest",
};

function buildChallengeParams(filters: ChallengeFilters): string {
  const params = new URLSearchParams();
  if (filters.page) params.set("page", String(filters.page));
  if (filters.pageSize) params.set("pageSize", String(filters.pageSize));
  if (filters.gameId) params.set("gameId", filters.gameId);
  if (filters.consoleId) params.set("consoleId", filters.consoleId);
  if (filters.difficulty) params.set("difficulty", filters.difficulty);
  if (filters.type) params.set("type", filters.type);
  if (filters.status) params.set("status", filters.status);
  if (filters.sortBy) params.set("sort", sortMapping[filters.sortBy] ?? filters.sortBy);
  return params.toString();
}

export function useChallenges(filters: ChallengeFilters = {}) {
  const queryString = buildChallengeParams(filters);
  return useQuery({
    queryKey: ["challenges", filters],
    queryFn: () => api.get<ChallengesResponse>(`/challenges?${queryString}`),
  });
}

export function useChallenge(id: string) {
  return useQuery({
    queryKey: ["challenge", id],
    queryFn: () => api.get<Challenge>(`/challenges/${id}`),
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
    queryFn: () =>
      api.get<ChallengesResponse>(
        `/games/${gameId}/challenges?page=${page}&pageSize=${pageSize}`,
      ),
    enabled: !!gameId,
  });
}

export function useMyChallenges(page: number = 1, pageSize: number = 20) {
  return useQuery({
    queryKey: ["challenges", "mine", page, pageSize],
    queryFn: () =>
      api.get<ChallengesResponse>(
        `/user/challenges?page=${page}&pageSize=${pageSize}`,
      ),
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
      api.get<ChallengeLeaderboardResponse>(
        `/challenges/${challengeId}/leaderboard?page=${page}&pageSize=${pageSize}`,
      ),
    enabled: !!challengeId,
  });
}

export function useMyAttempts(challengeId: string) {
  return useQuery({
    queryKey: ["challenge", challengeId, "my-attempts"],
    queryFn: () =>
      api.get<ChallengeAttempt[]>(`/challenges/${challengeId}/attempts/mine`),
    enabled: !!challengeId,
  });
}

export function useCreateChallenge() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      gameId,
      saveId,
      name,
      description,
      type,
      difficulty,
    }: {
      gameId: string;
      saveId: number;
      name: string;
      description?: string;
      type: string;
      difficulty: string;
    }) => {
      // Download the save file, then upload as challenge
      const res = await fetch(`/api/games/${gameId}/saves/${saveId}`, {
        headers: {
          Authorization: `Bearer ${api.getAccessToken()}`,
        },
      });
      if (!res.ok) throw new Error("Failed to download save file");
      const blob = await res.blob();

      const formData = new FormData();
      formData.append("save", blob, "save");
      formData.append("gameId", gameId);
      formData.append("name", name);
      if (description) formData.append("description", description);
      formData.append("type", type);
      formData.append("difficulty", difficulty);

      return api.upload<Challenge>("/challenges", formData);
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["challenges"] });
      queryClient.invalidateQueries({
        queryKey: ["challenges", "game", data.gameId],
      });
    },
  });
}

export function useDeleteChallenge() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => api.delete(`/challenges/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["challenges"] });
    },
  });
}

export function useStartAttempt() {
  return useMutation({
    mutationFn: (challengeId: string) =>
      api.post<StartAttemptResponse>(
        `/challenges/${challengeId}/attempts/start`,
      ),
  });
}

export function useCompleteAttempt() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      challengeId,
      attemptId,
    }: {
      challengeId: string;
      attemptId: string;
    }) =>
      api.post<CompleteAttemptResponse>(
        `/challenges/${challengeId}/attempts/${attemptId}/complete`,
      ),
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
      api.post(
        `/challenges/${challengeId}/attempts/${attemptId}/abandon`,
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
