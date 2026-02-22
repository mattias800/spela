import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { BiosFile, BiosResponse } from "@/types/api";

export function useBiosStatus() {
  return useQuery({
    queryKey: ["bios"],
    queryFn: () => api.get<BiosResponse>("/bios"),
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
      await api.delete(`/admin/bios/${encodeURIComponent(filename)}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["bios"] });
    },
  });
}

export function getBiosFileUrl(filename: string): string {
  return `/api/bios/${encodeURIComponent(filename)}`;
}
