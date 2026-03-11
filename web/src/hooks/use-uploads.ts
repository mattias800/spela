import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { StagedUpload, Game } from "@/types/api";

export function useUploadWritable() {
  return useQuery({
    queryKey: ["admin", "uploads", "writable"],
    queryFn: () =>
      api.get<{ writable: boolean; reason?: string }>(
        "/admin/uploads/writable",
      ),
  });
}

export function useStagedUploads() {
  return useQuery({
    queryKey: ["admin", "uploads"],
    queryFn: () => api.get<StagedUpload[]>("/admin/uploads"),
  });
}

export function useUploadRoms() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (files: File[]) => {
      const formData = new FormData();
      for (const file of files) {
        formData.append("files", file);
      }
      return api.upload<StagedUpload[]>("/admin/uploads", formData);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}

export function useSetUploadConsole() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      id,
      consoleId,
    }: {
      id: string;
      consoleId: string;
    }) => {
      return api.post<StagedUpload>(`/admin/uploads/${id}/console`, {
        consoleId,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}

export function useScrapeUpload() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      return api.post<StagedUpload>(`/admin/uploads/${id}/scrape`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}

export function useScrapeAllUploads() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => api.post<StagedUpload[]>("/admin/uploads/scrape"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}

export function useAcceptUpload() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      return api.post<Game>(`/admin/uploads/${id}/accept`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
    },
  });
}

export function useRejectUpload() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      return api.post<{ message: string }>(`/admin/uploads/${id}/reject`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}

export function useAcceptAllUploads() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      api.post<{ accepted: number }>("/admin/uploads/accept-all"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
    },
  });
}

export function useRejectAllUploads() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      api.post<{ rejected: number }>("/admin/uploads/reject-all"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}

export function useClearStaging() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => api.delete<{ cleared: number }>("/admin/uploads"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}
