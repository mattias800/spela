import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { CollectionsResponse, CollectionDetail } from "@/types/api";

export function useMyCollections(page = 1, pageSize = 20, search = "") {
  const params = new URLSearchParams();
  params.set("page", String(page));
  params.set("pageSize", String(pageSize));
  if (search) params.set("search", search);

  return useQuery({
    queryKey: ["collections", "mine", page, pageSize, search],
    queryFn: () => api.get<CollectionsResponse>(`/collections?${params}`),
  });
}

export function usePublicCollections(page = 1, pageSize = 20, search = "") {
  const params = new URLSearchParams();
  params.set("page", String(page));
  params.set("pageSize", String(pageSize));
  if (search) params.set("search", search);

  return useQuery({
    queryKey: ["collections", "public", page, pageSize, search],
    queryFn: () =>
      api.get<CollectionsResponse>(`/collections/public?${params}`),
  });
}

export function useCollection(id: string) {
  return useQuery({
    queryKey: ["collection", id],
    queryFn: () => api.get<CollectionDetail>(`/collections/${id}`),
    enabled: !!id,
  });
}

export function useCreateCollection() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: {
      name: string;
      description?: string;
      isPublic: boolean;
    }) => api.post<CollectionDetail>("/collections", data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["collections"] });
    },
  });
}

export function useUpdateCollection() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      ...data
    }: {
      id: string;
      name: string;
      description?: string;
      isPublic: boolean;
    }) => api.put<CollectionDetail>(`/collections/${id}`, data),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["collections"] });
      queryClient.invalidateQueries({ queryKey: ["collection", id] });
    },
  });
}

export function useDeleteCollection() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => api.delete(`/collections/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["collections"] });
    },
  });
}

export function useAddGameToCollection() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      collectionId,
      gameId,
    }: {
      collectionId: string;
      gameId: string;
    }) => api.post(`/collections/${collectionId}/games`, { gameId }),
    onSuccess: (_, { collectionId }) => {
      queryClient.invalidateQueries({ queryKey: ["collection", collectionId] });
      queryClient.invalidateQueries({ queryKey: ["collections"] });
    },
  });
}

export function useRemoveGameFromCollection() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      collectionId,
      gameId,
    }: {
      collectionId: string;
      gameId: string;
    }) => api.delete(`/collections/${collectionId}/games/${gameId}`),
    onSuccess: (_, { collectionId }) => {
      queryClient.invalidateQueries({ queryKey: ["collection", collectionId] });
      queryClient.invalidateQueries({ queryKey: ["collections"] });
    },
  });
}
