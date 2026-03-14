import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";

interface CreateRomHackParams {
  baseGameId: string;
  patchFile: File;
  mode: "variant" | "standalone";
  label?: string;
  title?: string;
}

interface CreateRomHackResponse {
  id: string;
  title: string;
}

export function useCreateRomHack() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (params: CreateRomHackParams) => {
      const formData = new FormData();
      formData.append("base_game_id", params.baseGameId);
      formData.append("patch_file", params.patchFile);
      formData.append("mode", params.mode);
      if (params.mode === "variant" && params.label) {
        formData.append("label", params.label);
      }
      if (params.mode === "standalone" && params.title) {
        formData.append("title", params.title);
      }
      return api.upload<CreateRomHackResponse>("/admin/rom-hacks", formData);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["game"] });
    },
  });
}
