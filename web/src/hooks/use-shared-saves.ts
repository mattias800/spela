import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { SharedSave, SharedSavesResponse } from "@/types/api";

export function useSharedSaves(
  gameId: string,
  page: number = 1,
  pageSize: number = 20,
) {
  return useQuery({
    queryKey: ["shared-saves", gameId, page, pageSize],
    queryFn: () =>
      api.get<SharedSavesResponse>(
        `/games/${gameId}/shared-saves?page=${page}&pageSize=${pageSize}`,
      ),
    enabled: !!gameId,
  });
}

export function useShareSave() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      gameId,
      saveId,
      name,
      description,
    }: {
      gameId: string;
      saveId: number;
      name: string;
      description: string;
    }) => {
      // First download the save file, then re-upload as shared save
      const res = await fetch(`/api/games/${gameId}/saves/${saveId}`, {
        headers: {
          Authorization: `Bearer ${api.getAccessToken()}`,
        },
      });
      if (!res.ok) throw new Error("Failed to download save file");
      const blob = await res.blob();

      const formData = new FormData();
      formData.append("save", blob, name);
      formData.append("name", name);
      formData.append("description", description);

      return api.upload<SharedSave>(`/games/${gameId}/shared-saves`, formData);
    },
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["shared-saves", gameId] });
    },
  });
}

export function useDeleteSharedSave() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      gameId,
      saveId,
    }: {
      gameId: string;
      saveId: string;
    }) => {
      await api.delete(`/games/${gameId}/shared-saves/${saveId}`);
    },
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["shared-saves", gameId] });
    },
  });
}
