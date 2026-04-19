import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  multipartBodySerializer,
  typedApi,
  unwrap,
} from "@/lib/api-client";
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

export function useUploadBiosFile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData();
      formData.append("file", file);
      const data = await unwrap(
        typedApi.POST("/api/admin/bios", {
          body: formData as unknown as never,
          bodySerializer: multipartBodySerializer,
        }),
      );
      return data as BiosFile | undefined;
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
