import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { multipart, typedApi, unwrap } from "@/lib/api-client";

export function useUploadWritable() {
  return useQuery({
    queryKey: ["admin", "uploads", "writable"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/uploads/writable")),
  });
}

export function useStagedUploads() {
  return useQuery({
    queryKey: ["admin", "uploads"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/uploads")),
  });
}

export function useUploadRoms() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (files: File[]) => {
      const formData = new FormData();
      for (const file of files) {
        formData.append("files", file);
      }
      return unwrap(typedApi.POST("/api/admin/uploads", multipart(formData)));
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}

export function useSetUploadConsole() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      consoleId,
    }: {
      id: string;
      consoleId: string;
    }) =>
      unwrap(
        typedApi.POST("/api/admin/uploads/{id}/console", {
          params: { path: { id } },
          body: { consoleId },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}

export function useScrapeUpload() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      unwrap(
        typedApi.POST("/api/admin/uploads/{id}/scrape", {
          params: { path: { id } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}

export function useScrapeAllUploads() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => unwrap(typedApi.POST("/api/admin/uploads/scrape")),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}

export function useAcceptUpload() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      unwrap(
        typedApi.POST("/api/admin/uploads/{id}/accept", {
          params: { path: { id } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
    },
  });
}

export function useRejectUpload() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      unwrap(
        typedApi.POST("/api/admin/uploads/{id}/reject", {
          params: { path: { id } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}

export function useAcceptAllUploads() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => unwrap(typedApi.POST("/api/admin/uploads/accept-all")),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
    },
  });
}

export function useRejectAllUploads() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => unwrap(typedApi.POST("/api/admin/uploads/reject-all")),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}

export function useClearStaging() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => unwrap(typedApi.DELETE("/api/admin/uploads")),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "uploads"] });
    },
  });
}
