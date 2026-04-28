import { useNavigate } from "react-router-dom";
import { CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui";

interface CompleteStepProps {
  igdbConfigured: boolean;
  gamesScanned: boolean;
}

export function CompleteStep({
  igdbConfigured,
  gamesScanned,
}: CompleteStepProps) {
  const navigate = useNavigate();

  return (
    <div data-comp="CompleteStep" className="space-y-6 text-center">
      <div className="flex justify-center">
        <div className="h-16 w-16 rounded-full bg-success-500/20 flex items-center justify-center">
          <CheckCircle2 className="h-8 w-8 text-success-500" />
        </div>
      </div>

      <div>
        <h2 className="text-xl font-semibold text-surface-100">
          You're all set!
        </h2>
        <p className="mt-1 text-sm text-surface-400">
          Your Spela server is configured and ready to use.
        </p>
      </div>

      <div className="space-y-2 text-left max-w-sm mx-auto">
        <div className="flex items-center gap-2 text-sm">
          <CheckCircle2 className="h-4 w-4 text-success-500 flex-shrink-0" />
          <span className="text-surface-300">Owner account created</span>
        </div>
        <div className="flex items-center gap-2 text-sm">
          <CheckCircle2
            className={`h-4 w-4 flex-shrink-0 ${igdbConfigured ? "text-success-500" : "text-surface-600"}`}
          />
          <span
            className={igdbConfigured ? "text-surface-300" : "text-surface-500"}
          >
            {igdbConfigured
              ? "IGDB metadata configured"
              : "IGDB metadata skipped"}
          </span>
        </div>
        <div className="flex items-center gap-2 text-sm">
          <CheckCircle2
            className={`h-4 w-4 flex-shrink-0 ${gamesScanned ? "text-success-500" : "text-surface-600"}`}
          />
          <span
            className={gamesScanned ? "text-surface-300" : "text-surface-500"}
          >
            {gamesScanned ? "Game library scanned" : "Game scan skipped"}
          </span>
        </div>
      </div>

      <div className="pt-2">
        <Button size="lg" onClick={() => navigate("/")}>
          Go to Dashboard
        </Button>
      </div>
    </div>
  );
}
