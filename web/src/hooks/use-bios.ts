import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api, typedApi, unwrap } from "@/lib/api-client";
import type { BiosFile } from "@/types/api";

export function useBiosStatus() {
  return useQuery({
    queryKey: ["bios"],
    queryFn: () => unwrap(typedApi.GET("/api/bios")),
  });
}

/** Legacy hook kept for PlayPage compatibility -- returns just the files array. */
export function useBiosFiles() {
  const query = useBiosStatus();
  return {
    ...query,
    data: query.data?.files,
  };
}

// Multipart upload still goes through api.upload — typedApi multipart needs
// a custom bodySerializer; tracked in #518.
export function useUploadBiosFile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData();
      formData.append("file", file);
      return api.upload<BiosFile>("/admin/bios", formData);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["bios"] });
    },
  });
}

export function useDeleteBiosFile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (filename: string) => {
      await unwrap(
        typedApi.DELETE("/api/admin/bios/{filename}", {
          params: { path: { filename } },
        }),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["bios"] });
    },
  });
}

export function useDownloadBios() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => unwrap(typedApi.POST("/api/admin/bios/download")),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["bios"] });
    },
  });
}

export function getBiosFileUrl(filename: string): string {
  return `/api/bios/${encodeURIComponent(filename)}`;
}
