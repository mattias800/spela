import { useMutation, useQueryClient } from "@tanstack/react-query";
import { multipart, typedApi, unwrap } from "@/lib/api-client";

interface CreateRomHackParams {
  baseGameId: string;
  patchFile: File;
  mode: "variant" | "standalone";
  label?: string;
  title?: string;
}

export function useCreateRomHack() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params: CreateRomHackParams) => {
      const formData = new FormData();
      formData.append("baseGameId", params.baseGameId);
      formData.append("patchFile", params.patchFile);
      formData.append("mode", params.mode);
      if (params.mode === "variant" && params.label) {
        formData.append("label", params.label);
      }
      if (params.mode === "standalone" && params.title) {
        formData.append("title", params.title);
      }
      return unwrap(
        typedApi.POST("/api/admin/rom-hacks", multipart(formData)),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["game"] });
    },
  });
}
