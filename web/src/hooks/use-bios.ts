import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { BiosFile } from "@/types/api";

export function useBiosFiles() {
  return useQuery({
    queryKey: ["bios"],
    queryFn: () => api.get<BiosFile[]>("/bios"),
  });
}

export function getBiosFileUrl(filename: string): string {
  return `/api/bios/${encodeURIComponent(filename)}`;
}
