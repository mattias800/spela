import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useMyCollections(page = 1, pageSize = 20, search = "") {
  return useQuery({
    queryKey: ["collections", "mine", page, pageSize, search],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/collections", {
          params: { query: { page, pageSize, search: search || undefined } },
        }),
      ),
  });
}

export function usePublicCollections(page = 1, pageSize = 20, search = "") {
  return useQuery({
    queryKey: ["collections", "public", page, pageSize, search],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/collections/public", {
          params: { query: { page, pageSize, search: search || undefined } },
        }),
      ),
  });
}

export function useCollection(id: string) {
  return useQuery({
    queryKey: ["collection", id],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/collections/{id}", { params: { path: { id } } }),
      ),
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
    }) => unwrap(typedApi.POST("/api/collections", { body: data })),
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
    }) =>
      unwrap(
        typedApi.PUT("/api/collections/{id}", {
          params: { path: { id } },
          body: data,
        }),
      ),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["collections"] });
      queryClient.invalidateQueries({ queryKey: ["collection", id] });
    },
  });
}

export function useDeleteCollection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      unwrap(
        typedApi.DELETE("/api/collections/{id}", { params: { path: { id } } }),
      ),
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
    }) =>
      unwrap(
        typedApi.POST("/api/collections/{id}/games", {
          params: { path: { id: collectionId } },
          body: { gameId: Number(gameId) },
        }),
      ),
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
    }) =>
      unwrap(
        typedApi.DELETE("/api/collections/{id}/games/{gameId}", {
          params: { path: { id: collectionId, gameId } },
        }),
      ),
    onSuccess: (_, { collectionId }) => {
      queryClient.invalidateQueries({ queryKey: ["collection", collectionId] });
      queryClient.invalidateQueries({ queryKey: ["collections"] });
    },
  });
}
