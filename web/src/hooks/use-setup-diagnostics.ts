import { useQuery } from "@tanstack/react-query";

import { typedApi, unwrap } from "@/lib/api-client";
import type { components } from "@/generated/api";

export type DiagnosticCheck = components["schemas"]["DiagnosticCheck"];

export function useSetupDiagnostics(enabled = true) {
  return useQuery({
    queryKey: ["setup", "diagnostics"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/setup/diagnostics"));
      return { checks: data?.checks ?? [] };
    },
    enabled,
  });
}
