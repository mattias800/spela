import { useQuery } from "@tanstack/react-query";

export interface DiagnosticCheck {
  id: string;
  label: string;
  status: "ok" | "warning" | "error";
  detail?: string;
  fix?: string;
}

interface DiagnosticsResponse {
  checks: DiagnosticCheck[];
}

export function useSetupDiagnostics(enabled = true) {
  return useQuery({
    queryKey: ["setup", "diagnostics"],
    queryFn: async (): Promise<DiagnosticsResponse> => {
      const res = await fetch("/api/setup/diagnostics");
      if (!res.ok) throw new Error("Failed to fetch diagnostics");
      return res.json();
    },
    enabled,
  });
}
