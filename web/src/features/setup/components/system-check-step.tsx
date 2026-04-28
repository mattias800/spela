import { CheckCircle2, AlertTriangle, XCircle, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui";
import {
  useSetupDiagnostics,
  type DiagnosticCheck,
} from "@/hooks/use-setup-diagnostics";

interface SystemCheckStepProps {
  onNext: () => void;
}

function StatusIcon({ status }: { status: DiagnosticCheck["status"] }) {
  switch (status) {
    case "ok":
      return <CheckCircle2 className="h-5 w-5 text-success-500" />;
    case "warning":
      return <AlertTriangle className="h-5 w-5 text-warning-500" />;
    case "error":
      return <XCircle className="h-5 w-5 text-danger-500" />;
  }
}

export function SystemCheckStep({ onNext }: SystemCheckStepProps) {
  const { data, isLoading, refetch, isRefetching } = useSetupDiagnostics();
  const checks = data?.checks ?? [];
  const hasErrors = checks.some((c) => c.status === "error");

  return (
    <div data-comp="SystemCheckStep" className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-surface-100">
          System Check
        </h2>
        <p className="mt-1 text-sm text-surface-400">
          Verifying your server configuration before getting started.
        </p>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div
              key={i}
              className="h-12 rounded-xl bg-surface-800/50 animate-pulse"
            />
          ))}
        </div>
      ) : (
        <div className="space-y-2">
          {checks.map((check) => (
            <div
              key={check.id}
              className="flex items-start gap-3 rounded-xl bg-surface-800/30 border border-surface-800/50 px-4 py-3"
            >
              <div className="mt-0.5">
                <StatusIcon status={check.status} />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-surface-200">
                    {check.label}
                  </span>
                </div>
                {check.detail && (
                  <p className="text-xs text-surface-400 mt-0.5">
                    {check.detail}
                  </p>
                )}
                {check.fix && check.status !== "ok" && (
                  <p className="text-xs text-warning-400 mt-1">{check.fix}</p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="flex items-center justify-between pt-2">
        <Button
          variant="secondary"
          onClick={() => refetch()}
          loading={isRefetching}
          disabled={isLoading}
        >
          <RefreshCw className="h-4 w-4 mr-2" />
          Re-check
        </Button>
        <Button onClick={onNext} disabled={isLoading || hasErrors}>
          Continue
        </Button>
      </div>
    </div>
  );
}
